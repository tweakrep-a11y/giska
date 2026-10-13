package miku.moe.app;

import android.app.Application;
import coil.Coil;
import coil.ImageLoader;
import coil.ComponentRegistry;
import coil.ImageLoaderFactory;
import coil.decode.GifDecoder;
import coil.decode.ImageDecoderDecoder;
import coil.disk.DiskCache;
import coil.memory.MemoryCache;
import android.os.Build;
import com.google.android.material.color.DynamicColors;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class MikuMoeApplication extends Application implements ImageLoaderFactory {
    private static volatile OkHttpClient imageHttpClient;
    private static volatile Cache imageHttpCache;
    private static volatile ImageLoader installedImageLoader;

    static void evictImageConnections() {
        OkHttpClient client = imageHttpClient;
        if (client != null) client.connectionPool().evictAll();
    }

    static void trimImageCaches(android.content.Context context) {
        ImageLoader loader = installedImageLoader;
        if (loader == null) return;
        try {
            MemoryCache memoryCache = loader.getMemoryCache();
            if (memoryCache != null) memoryCache.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
        } catch (Throwable ignored) { }
    }

    static void clearImageCaches(android.content.Context context) {
        ImageLoader loader = installedImageLoader;
        if (loader != null) {
            try {
                MemoryCache memoryCache = loader.getMemoryCache();
                if (memoryCache != null) memoryCache.clear();
            } catch (Throwable ignored) { }
            try {
                DiskCache diskCache = loader.getDiskCache();
                if (diskCache != null) diskCache.clear();
            } catch (Throwable ignored) { }
        }
        Cache httpCache = imageHttpCache;
        if (httpCache != null) {
            try { httpCache.evictAll(); } catch (Throwable ignored) { }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.install(this);
        MikuLifecycleKeepAlive.install(this);
        MangaSettingsManager.init(this);
        NetworkDohManager.init(this);
        ThemeManager.applyNightMode(this);
        CloudflareHelper.init(this);
        MangaHttpClient.init(this);
        Coil.setImageLoader(newImageLoader());
        MangaImageLoader.registerImageContext(this);
        MangaCacheController.cleanOnAppStart(this);
        DynamicColors.applyToActivitiesIfAvailable(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_UI_HIDDEN && MangaCacheController.hasActiveReaderSession()) return;
        if (level >= TRIM_MEMORY_UI_HIDDEN) MangaCacheController.trimMangaCache(this, level >= TRIM_MEMORY_MODERATE);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        MangaCacheController.trimMangaCache(this, true);
    }

    @Override
    public ImageLoader newImageLoader() {
        File imageCache = new File(getCacheDir(), "manga_image_cache");
        File httpCacheDir = new File(getCacheDir(), "manga_http_cache");
        Cache httpCache = new Cache(httpCacheDir, 24L * 1024L * 1024L);
        imageHttpCache = httpCache;
        OkHttpClient client = CloudflareHelper.apply(new OkHttpClient.Builder())
                .cache(httpCache)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        imageHttpClient = client;
        ComponentRegistry.Builder components = new ComponentRegistry.Builder();
        components.add(new AnimatedAvifDecoder.Factory());
        if (Build.VERSION.SDK_INT >= 28) components.add(new ImageDecoderDecoder.Factory());
        else components.add(new GifDecoder.Factory());
        ImageLoader loader = new ImageLoader.Builder(this)
                .components(components.build())
                .okHttpClient(client)
                .memoryCache(new MemoryCache.Builder(this).maxSizePercent(0.14).build())
                .diskCache(new DiskCache.Builder().directory(imageCache).maxSizeBytes(128L * 1024L * 1024L).build())
                .crossfade(false)
                .respectCacheHeaders(false)
                .allowHardware(false)
                .build();
        installedImageLoader = loader;
        return loader;
    }
}
