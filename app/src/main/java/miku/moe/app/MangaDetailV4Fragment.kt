@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package miku.moe.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

class MangaDetailV4Fragment : Fragment() {

    private var initialManga: MangaPost? = null

    companion object {
        @JvmStatic
        fun newInstance(manga: MangaPost): MangaDetailV4Fragment {
            return MangaDetailV4Fragment().apply {
                arguments = Bundle().apply {
                    putSerializable("manga", manga)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialManga = arguments?.getSerializable("manga") as? MangaPost
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewModel = ViewModelProvider(requireActivity())
            .get(MangaDetailViewModel::class.java)

        val swipeRefreshLayout = SwipeRefreshLayout(requireContext()).apply {
            setColorSchemeColors(android.graphics.Color.rgb(0, 107, 95))
        }

        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
        }

        swipeRefreshLayout.addView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        composeView.setContent {
            var refreshKey by remember { mutableIntStateOf(0) }

            val refresh: () -> Unit = {
                initialManga?.let {
                    MangaSourceFactory.invalidateSourceCaches(it.getSourceId())
                }
                refreshKey++
            }

            DisposableEffect(swipeRefreshLayout) {
                swipeRefreshLayout.setOnRefreshListener(refresh)
                onDispose {
                    swipeRefreshLayout.setOnRefreshListener(null)
                    swipeRefreshLayout.setOnChildScrollUpCallback(null)
                    swipeRefreshLayout.isRefreshing = false
                }
            }

            DetailV4Theme {
                DetailV4Screen(
                    initial = initialManga,
                    viewModel = viewModel,
                    lifecycleOwner = viewLifecycleOwner,
                    refreshKey = refreshKey,
                    onRefresh = refresh,
                    onRefreshFinished = {
                        swipeRefreshLayout.isRefreshing = false
                    },
                    onScrollState = { canRefresh ->
                        swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
                            !canRefresh
                        }
                    },
                    onBack = {
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    },
                    onChapter = ::openChapter,
                    onRelated = ::openRelated,
                    onGenre = ::openGenre
                )
            }
        }

        return swipeRefreshLayout
    }

    private fun openChapter(
        manga: MangaPost,
        chapter: MangaChapter,
        chapters: List<MangaChapter>
    ) {
        val list = ArrayList(chapters)
        val exactPosition = list.indexOfFirst { it === chapter }
        val position = if (exactPosition >= 0) {
            exactPosition
        } else {
            list.indexOfFirst {
                it.slug == chapter.slug &&
                    abs(it.index - chapter.index) < 0.001f
            }
        }

        if (position < 0) return

        when (val host = activity) {
            is MainActivity -> host.openMangaReader(manga, list, position)
            is MikuAll -> host.openMangaReader(manga, list, position)
        }
    }

    private fun openRelated(manga: MangaPost) {
        when (val host = activity) {
            is MainActivity -> host.openMangaDetail(manga)
            is MikuAll -> host.openMangaDetail(manga)
        }
    }

    private fun openGenre(
        sourceId: String,
        sourceLabel: String,
        title: String,
        value: String
    ) {
        when (val host = activity) {
            is MainActivity ->
                host.openMangaGenreResult(sourceId, sourceLabel, title, value)
            is MikuAll ->
                host.openMangaGenreResult(sourceId, sourceLabel, title, value)
        }
    }
}

@Composable
private fun DetailV4Theme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF80D5C5),
            onPrimary = Color(0xFF00382F),
            primaryContainer = Color(0xFF005047),
            onPrimaryContainer = Color(0xFFA0F2E2),
            secondary = Color(0xFFE3C48E),
            onSecondary = Color(0xFF402D09),
            background = Color(0xFF111715),
            onBackground = Color(0xFFE1E9E3),
            surface = Color(0xFF111715),
            onSurface = Color(0xFFE1E9E3),
            surfaceVariant = Color(0xFF3F4944),
            onSurfaceVariant = Color(0xFFBFC9C2),
            surfaceContainer = Color(0xFF1C2420),
            surfaceContainerHigh = Color(0xFF26302B),
            outline = Color(0xFF89958D),
            outlineVariant = Color(0xFF3F4944)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF006B5F),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF9BF2DE),
            onPrimaryContainer = Color(0xFF002019),
            secondary = Color(0xFF725A2D),
            onSecondary = Color.White,
            background = Color(0xFFFAFAF4),
            onBackground = Color(0xFF191D19),
            surface = Color(0xFFFAFAF4),
            onSurface = Color(0xFF191D19),
            surfaceVariant = Color(0xFFDCE5DC),
            onSurfaceVariant = Color(0xFF404942),
            surfaceContainer = Color(0xFFEEF2E9),
            surfaceContainerHigh = Color(0xFFE5EAE1),
            outline = Color(0xFF707B73),
            outlineVariant = Color(0xFFC0C9C0)
        )
    }

    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun DetailV4Screen(
    initial: MangaPost?,
    viewModel: MangaDetailViewModel,
    lifecycleOwner: LifecycleOwner,
    refreshKey: Int,
    onRefresh: () -> Unit,
    onRefreshFinished: () -> Unit,
    onScrollState: (Boolean) -> Unit,
    onBack: () -> Unit,
    onChapter: (MangaPost, MangaChapter, List<MangaChapter>) -> Unit,
    onRelated: (MangaPost) -> Unit,
    onGenre: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val sourceId = initial?.getSourceId()
    val slug = initial?.slug

    var manga by remember(sourceId, slug) { mutableStateOf(initial) }
    var chapters by remember(sourceId, slug) {
        mutableStateOf<List<MangaChapter>>(emptyList())
    }
    var genres by remember(sourceId, slug) {
        mutableStateOf<List<KomikcastClient.GenreItem>>(emptyList())
    }
    var related by remember(sourceId, slug) {
        mutableStateOf<List<MangaPost>>(emptyList())
    }
    var loading by remember(sourceId, slug) { mutableStateOf(true) }
    var error by remember(sourceId, slug) { mutableStateOf<String?>(null) }
    var favorite by remember(sourceId, slug) {
        mutableStateOf(initial?.let {
            MangaFavoriteManager.isFavorite(context, it)
        } ?: false)
    }
    var historyVersion by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable(sourceId, slug) { mutableIntStateOf(0) }

    val infoState = rememberLazyListState()
    val browserState = rememberLazyListState()

    DisposableEffect(context, lifecycleOwner, sourceId, slug) {
        val prefs = context.getSharedPreferences(
            "miku_manga_history",
            Context.MODE_PRIVATE
        )
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "items" || key == "chapter_progress") {
                historyVersion++
            }
        }
        val roomListener = MangaRoomEvents.Listener {
            historyVersion++
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                historyVersion++
                manga?.let {
                    favorite = MangaFavoriteManager.isFavorite(context, it)
                }
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        MangaRoomEvents.addListener(roomListener)
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            MangaRoomEvents.removeListener(roomListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(sourceId, slug, refreshKey) {
        val base = initial
        if (base == null) {
            loading = false
            error = "Data manga tidak tersedia."
            onRefreshFinished()
            return@LaunchedEffect
        }

        loading = true
        error = null

        try {
            val result = viewModel.loadDetailData(base).awaitDetailV4()
            val loaded = result.detail ?: base
            loaded.totalChapters = maxOf(
                loaded.totalChapters,
                result.chapters.size
            )
            manga = loaded
            chapters = result.chapters.toList()
            genres = result.genres.toList()
            related = result.related.filterNot {
                it.getSourceId() == loaded.getSourceId() &&
                    it.slug == loaded.slug
            }.distinctBy { it.getSourceId() to it.slug }
            favorite = MangaFavoriteManager.isFavorite(context, loaded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.cause?.message
                ?: failure.message
                ?: "Detail gagal dimuat. Periksa koneksi lalu coba lagi."
        } finally {
            loading = false
            onRefreshFinished()
        }
    }

    val active = manga
    val lastIndex = remember(active, historyVersion) {
        active?.let {
            MangaHistoryManager.getLastReadChapterIndex(context, it)
        } ?: -1f
    }
    val resumeChapter = remember(chapters, lastIndex) {
        chapters.firstOrNull { abs(it.index - lastIndex) < 0.001f }
            ?: chapters.minByOrNull { it.index }
    }
    val hasResume = resumeChapter != null &&
        lastIndex >= 0f &&
        abs(resumeChapter.index - lastIndex) < 0.001f

    val toggleFavorite: () -> Unit = {
        active?.let {
            try {
                val snapshot = favoriteSnapshotV4(it, chapters)
                MangaFavoriteManager.toggle(context, snapshot)
                favorite = MangaFavoriteManager.isFavorite(context, snapshot)
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "Favorit gagal diperbarui.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val share: () -> Unit = {
        active?.let {
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, detailShareUrlV4(context, it))
                }
                context.startActivity(Intent.createChooser(intent, "Bagikan manga"))
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "Tidak ada aplikasi untuk berbagi.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "MIKU MOE / LIBRARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Detail manga",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        IconButton(
                            onClick = toggleFavorite,
                            enabled = active != null
                        ) {
                            val scale by animateFloatAsState(
                                targetValue = if (favorite) 1.12f else 1f,
                                label = "favoriteScaleV4"
                            )
                            Icon(
                                if (favorite) Icons.Default.Favorite
                                else Icons.Default.FavoriteBorder,
                                contentDescription = if (favorite) {
                                    "Hapus dari favorit"
                                } else {
                                    "Tambahkan ke favorit"
                                },
                                tint = if (favorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.scale(scale)
                            )
                        }
                        IconButton(onClick = share, enabled = active != null) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Bagikan manga"
                            )
                        }
                    }
                    HorizontalDivider()
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            if (active != null && resumeChapter != null) {
                                onChapter(active, resumeChapter, chapters)
                            }
                        },
                        enabled = active != null && resumeChapter != null,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 14.dp
                        ),
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                hasResume ->
                                    "Lanjut membaca Chapter ${
                                        MangaChapter.formatIndex(resumeChapter!!.index)
                                    }"
                                resumeChapter != null -> "Mulai membaca"
                                loading -> "Memuat chapter…"
                                else -> "Chapter belum tersedia"
                            },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier.padding(padding).fillMaxSize()
        ) {
            val wide = maxWidth >= 720.dp

            LaunchedEffect(wide, selected, infoState, browserState) {
                snapshotFlow {
                    if (wide) {
                        !infoState.canScrollBackward &&
                            !browserState.canScrollBackward
                    } else if (selected == 0) {
                        !infoState.canScrollBackward
                    } else {
                        !browserState.canScrollBackward
                    }
                }.collect(onScrollState)
            }

            if (active == null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        DetailV4Message(
                            title = "Manga tidak tersedia",
                            message = error ?: "Buka kembali manga dari daftar.",
                            onRetry = if (initial != null) onRefresh else null
                        )
                    }
                }
            } else if (wide) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    DetailV4Info(
                        manga = active,
                        genres = genres,
                        chapterCount = chapters.size,
                        loading = loading,
                        error = error,
                        onRetry = onRefresh,
                        onGenre = onGenre,
                        state = infoState,
                        modifier = Modifier.weight(0.43f).fillMaxSize()
                    )
                    Box(
                        Modifier.width(1.dp).fillMaxSize()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Column(Modifier.weight(0.57f).fillMaxSize()) {
                        DetailV4Tabs(
                            titles = listOf(
                                "Chapter (${chapters.size})",
                                "Serupa (${related.size})"
                            ),
                            selected = if (selected == 2) 1 else 0,
                            onSelect = { selected = if (it == 1) 2 else 1 }
                        )
                        DetailV4Browser(
                            manga = active,
                            chapters = chapters,
                            related = related,
                            showRelated = selected == 2,
                            loading = loading,
                            error = error,
                            historyVersion = historyVersion,
                            onRetry = onRefresh,
                            onChapter = onChapter,
                            onRelated = onRelated,
                            state = browserState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    DetailV4Tabs(
                        titles = listOf(
                            "Tentang",
                            "Chapter",
                            "Serupa"
                        ),
                        selected = selected,
                        onSelect = { selected = it }
                    )
                    if (selected == 0) {
                        DetailV4Info(
                            manga = active,
                            genres = genres,
                            chapterCount = chapters.size,
                            loading = loading,
                            error = error,
                            onRetry = onRefresh,
                            onGenre = onGenre,
                            state = infoState,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        DetailV4Browser(
                            manga = active,
                            chapters = chapters,
                            related = related,
                            showRelated = selected == 2,
                            loading = loading,
                            error = error,
                            historyVersion = historyVersion,
                            onRetry = onRefresh,
                            onChapter = onChapter,
                            onRelated = onRelated,
                            state = browserState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailV4Tabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    TabRow(selectedTabIndex = selected) {
        titles.forEachIndexed { index, title ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun DetailV4Info(
    manga: MangaPost,
    genres: List<KomikcastClient.GenreItem>,
    chapterCount: Int,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onGenre: (String, String, String, String) -> Unit,
    state: LazyListState,
    modifier: Modifier
) {
    var expanded by rememberSaveable(manga.slug, manga.getSourceId()) {
        mutableStateOf(false)
    }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DetailV4Section("01", "The story")
                Text(
                    manga.title.orEmpty().ifBlank { "Tanpa judul" },
                    fontFamily = FontFamily.Serif,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    manga.author.orEmpty().ifBlank { "Penulis belum tersedia" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val coverWidth = (maxWidth * 0.42f).coerceAtMost(180.dp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        DetailV4Image(
                            manga,
                            Modifier.width(coverWidth).aspectRatio(0.68f)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DetailV4Fact("FORMAT", manga.getTypeLabel())
                            DetailV4Fact("STATUS", manga.status.orEmpty())
                            DetailV4Fact("SUMBER", manga.getSourceLabel())
                            DetailV4Fact(
                                "CHAPTER",
                                if (loading && chapterCount == 0) "Memuat…"
                                else chapterCount.toString()
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            item {
                DetailV4Message(
                    "Gagal memperbarui detail",
                    error,
                    onRetry
                )
            }
        } else if (loading) {
            item {
                Text(
                    "Menyiapkan sinopsis, genre, dan daftar chapter…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            HorizontalDivider()
        }

        item {
            Column(
                Modifier.animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailV4Section("02", "Sinopsis")
                SelectionContainer {
                    Text(
                        manga.synopsis.orEmpty().ifBlank {
                            if (loading) "Sinopsis sedang dimuat…"
                            else "Sinopsis belum tersedia."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!manga.synopsis.isNullOrBlank()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Ringkas" else "Baca selengkapnya")
                    }
                }
            }
        }

        val labels = manga.genre.orEmpty()
            .split(",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (labels.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailV4Section("03", "Genre")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        labels.forEach { label ->
                            val genre = genres.firstOrNull {
                                it.title.equals(label, true)
                            }
                            TextButton(
                                onClick = {
                                    onGenre(
                                        manga.getSourceId(),
                                        manga.getSourceLabel(),
                                        label,
                                        genre?.value ?: label
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }

        if (!manga.info.isNullOrBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider()
                    DetailV4Section("04", "Catatan & informasi")
                    SelectionContainer {
                        Text(
                            manga.info.orEmpty().replace("|", "\n"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailV4Browser(
    manga: MangaPost,
    chapters: List<MangaChapter>,
    related: List<MangaPost>,
    showRelated: Boolean,
    loading: Boolean,
    error: String?,
    historyVersion: Int,
    onRetry: () -> Unit,
    onChapter: (MangaPost, MangaChapter, List<MangaChapter>) -> Unit,
    onRelated: (MangaPost) -> Unit,
    state: LazyListState,
    modifier: Modifier
) {
    val context = LocalContext.current
    val orderPrefs = remember(context) {
        context.getSharedPreferences("miku_detail_chapter_prefs", Context.MODE_PRIVATE)
    }
    var descending by remember {
        mutableStateOf(
            orderPrefs.getBoolean("global_chapter_order_newest_first", false)
        )
    }
    var query by rememberSaveable(manga.slug, manga.getSourceId()) {
        mutableStateOf("")
    }

    val shown = remember(chapters, descending, query) {
        val filtered = chapters.filter {
            query.isBlank() ||
                it.title.orEmpty().contains(query.trim(), true) ||
                MangaChapter.formatIndex(it.index).contains(query.trim(), true)
        }
        if (descending) filtered.sortedByDescending { it.index }
        else filtered.sortedBy { it.index }
    }

    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    DetailV4Section(
                        if (showRelated) "06" else "05",
                        if (showRelated) "Bacaan berikutnya" else "Daftar chapter"
                    )
                    Text(
                        if (showRelated) "${related.size} judul"
                        else "${shown.size} dari ${chapters.size} chapter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!showRelated) {
                    IconButton(
                        onClick = {
                            descending = !descending
                            orderPrefs.edit()
                                .putBoolean(
                                    "global_chapter_order_newest_first",
                                    descending
                                ).apply()
                        }
                    ) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = if (descending) {
                                "Urutkan chapter terlama terlebih dahulu"
                            } else {
                                "Urutkan chapter terbaru terlebih dahulu"
                            }
                        )
                    }
                }
            }

            if (!showRelated) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cari nomor atau judul chapter") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Hapus pencarian"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (descending) "TERBARU → TERLAMA"
                    else "TERLAMA → TERBARU",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider()

        LazyColumn(
            state = state,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (error != null) {
                item {
                    DetailV4Message("Tidak dapat memuat data", error, onRetry)
                }
            }

            if (loading && if (showRelated) related.isEmpty() else chapters.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(
                            if (showRelated) "Memuat manga serupa…"
                            else "Memuat daftar chapter…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (showRelated) {
                if (related.isEmpty()) {
                    item {
                        DetailV4Message(
                            "Belum ada rekomendasi",
                            "Sumber ini belum menyediakan manga serupa."
                        )
                    }
                } else {
                    itemsIndexed(
                        related,
                        key = { index, post ->
                            "${post.getSourceId()}:${post.slug}:$index"
                        }
                    ) { _, post ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onRelated(post) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DetailV4Image(
                                post,
                                Modifier.width(72.dp).aspectRatio(0.68f)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    post.title.orEmpty().ifBlank { "Tanpa judul" },
                                    fontFamily = FontFamily.Serif,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    listOf(
                                        post.getTypeLabel(),
                                        post.status.orEmpty(),
                                        post.getSourceLabel()
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.NavigateNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            } else if (shown.isEmpty()) {
                item {
                    DetailV4Message(
                        if (query.isNotBlank()) "Chapter tidak ditemukan"
                        else "Belum ada chapter",
                        if (query.isNotBlank()) {
                            "Coba nomor atau kata kunci yang berbeda."
                        } else {
                            "Tarik layar ke bawah untuk memperbarui."
                        }
                    )
                }
            } else {
                itemsIndexed(
                    shown,
                    key = { index, chapter ->
                        "${chapter.slug}:${chapter.index}:$index"
                    }
                ) { _, chapter ->
                    DetailV4ChapterRow(
                        manga,
                        chapter,
                        historyVersion,
                        onClick = { onChapter(manga, chapter, chapters) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailV4ChapterRow(
    manga: MangaPost,
    chapter: MangaChapter,
    historyVersion: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val progress = remember(
        historyVersion,
        manga.slug,
        manga.getSourceId(),
        chapter.index
    ) {
        MangaHistoryManager.getProgress(context, manga, chapter.index)
    }
    val total = progress?.totalPages ?: 0
    val page = if (total > 0 && progress != null) {
        (progress.page + 1).coerceIn(0, total)
    } else {
        0
    }
    val fraction = if (total > 0) page.toFloat() / total else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        label = "chapterProgressV4"
    )
    val finished = total > 0 && page >= total
    val hasProgress = total > 0
    val last = remember(historyVersion, manga.slug, manga.getSourceId()) {
        MangaHistoryManager.getLastReadChapterIndex(context, manga)
    }
    val current = last >= 0f && abs(last - chapter.index) < 0.001f

    Column(
        Modifier.fillMaxWidth()
            .background(
                if (current) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .heightIn(min = 80.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                MangaChapter.formatIndex(chapter.index),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = if (current) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.widthIn(min = 40.dp, max = 92.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    chapter.title.orEmpty().ifBlank {
                        "Chapter ${MangaChapter.formatIndex(chapter.index)}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (current) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    listOf(
                        chapter.date.orEmpty(),
                        when {
                            finished -> "Selesai"
                            hasProgress -> "Hal. $page/$total"
                            current -> "Terakhir dibaca"
                            else -> "Belum dibaca"
                        }
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Icon(
                if (finished) Icons.Default.CheckCircle
                else Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = if (current) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        AnimatedVisibility(visible = hasProgress) {
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = if (current) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = if (current) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun DetailV4Section(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            number,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }
        )
    }
}

@Composable
private fun DetailV4Fact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DetailV4Message(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text("Coba lagi")
            }
        }
    }
}

@Composable
private fun DetailV4Image(post: MangaPost, modifier: Modifier) {
    val context = LocalContext.current
    val url = post.coverImage.orEmpty()
    val sourceId = post.getSourceId()

    val request = remember(context, url, sourceId) {
        val resolved = MangaImageLoader.resolveImageUrl(url, sourceId)
        val key = MangaImageLoader.imageCacheKey(url, sourceId)
        val builder = ImageRequest.Builder(context)
            .data(resolved)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .crossfade(true)

        val headers = MangaImageLoader.headersFor(resolved, sourceId)
        headers.names.forEach { name ->
            headers[name]?.let { builder.setHeader(name, it) }
        }
        builder.build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Sampul ${post.title.orEmpty()}",
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        error = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = "Sampul tidak tersedia",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    )
}

private fun detailShareUrlV4(context: Context, manga: MangaPost): String {
    val raw = manga.slug.orEmpty().trim()
    if (
        raw.startsWith("http://", true) ||
        raw.startsWith("https://", true)
    ) {
        return raw
    }

    val source = manga.getSourceId()
    val domain = MangaSettingsManager.getSourceDomain(context, source).trimEnd('/')
    var path = raw.substringAfter("::", raw).trim()

    if (path.isBlank()) return domain

    if (!path.startsWith("/")) {
        path = when {
            path.contains("/") -> "/$path"
            source == MangaSettingsManager.MANGA_SOURCE_KOMIKCAST ->
                "/komik/$path/"
            source == MangaSettingsManager.MANGA_SOURCE_KIRYUU_OFFICIAL ||
                source == MangaSettingsManager.MANGA_SOURCE_NATSU ->
                "/manga/$path/"
            else -> "/$path/"
        }
    }

    return domain + path
}

private fun favoriteSnapshotV4(
    manga: MangaPost,
    chapters: List<MangaChapter>
): MangaPost {
    val compactLatest = manga.latestChapter.orEmpty()
        .trim()
        .replace(Regex("(?i)chapter\\s*"), "")
        .replace(Regex("(?i)ch\\.?\\s*"), "")
        .trim()

    val copy = MangaPost(
        manga.slug,
        manga.title,
        manga.coverImage,
        manga.author,
        manga.status,
        manga.synopsis,
        manga.genre,
        manga.getTypeLabel(),
        compactLatest,
        manga.latestChapterDate
    ).withSource(manga.getSourceId(), manga.getSourceLabel())

    copy.info = manga.info
    copy.totalChapters = maxOf(manga.totalChapters, chapters.size)

    chapters.maxByOrNull { it.index }?.let {
        copy.latestChapter = MangaChapter.formatIndex(it.index)
        copy.latestChapterDate = it.date.orEmpty()
    }

    return copy
}

private suspend fun <T> CompletableFuture<T>.awaitDetailV4(): T {
    return suspendCancellableCoroutine { continuation ->
        whenComplete { value, failure ->
            if (continuation.isActive) {
                if (failure != null) {
                    continuation.resumeWithException(failure)
                } else {
                    continuation.resume(value)
                }
            }
        }
    }
}
