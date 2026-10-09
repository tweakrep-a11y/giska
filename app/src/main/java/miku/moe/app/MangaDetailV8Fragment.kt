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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.google.android.material.color.MaterialColors
import java.util.ArrayList
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

class MangaDetailV8Fragment : Fragment() {

    private var initialManga: MangaPost? = null
    private var systemBarsApplied = false

    companion object {
        @JvmStatic
        fun newInstance(manga: MangaPost): MangaDetailV8Fragment {
            return MangaDetailV8Fragment().apply {
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
        applyV8SystemBars()

        val viewModel = ViewModelProvider(requireActivity())
            .get(MangaDetailViewModel::class.java)

        val swipeRefreshLayout = SwipeRefreshLayout(requireContext()).apply {
            setColorSchemeColors(
                MaterialColors.getColor(
                    requireContext(),
                    androidx.appcompat.R.attr.colorPrimary,
                    android.graphics.Color.rgb(103, 80, 164)
                )
            )
            setProgressBackgroundColorSchemeColor(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorSurface,
                    android.graphics.Color.rgb(28, 27, 32)
                )
            )
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

            DetailV8Theme {
                DetailV8Screen(
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

    override fun onResume() {
        super.onResume()
        applyV8SystemBars()
    }

    override fun onPause() {
        restoreV8SystemBars()
        super.onPause()
    }

    override fun onDestroyView() {
        restoreV8SystemBars()
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            restoreV8SystemBars()
        } else {
            applyV8SystemBars()
        }
    }

    private fun applyV8SystemBars() {
        val host = activity ?: return
        ThemeManager.applySystemBars(host)
        systemBarsApplied = true
    }

    private fun restoreV8SystemBars() {
        val host = activity ?: return
        if (!systemBarsApplied) return
        ThemeManager.applySystemBars(host)
        systemBarsApplied = false
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
private fun DetailV8Theme(content: @Composable () -> Unit) {
    val context = LocalContext.current

    fun themeColor(attr: Int, fallback: Int): Color {
        return Color(MaterialColors.getColor(context, attr, fallback))
    }

    val surface = themeColor(
        com.google.android.material.R.attr.colorSurface,
        0xFF1C1B20.toInt()
    )

    val onSurface = themeColor(
        com.google.android.material.R.attr.colorOnSurface,
        0xFFF2EEF5.toInt()
    )

    val colors = darkColorScheme(
        primary = themeColor(
            androidx.appcompat.R.attr.colorPrimary,
            0xFFFF78C8.toInt()
        ),
        onPrimary = themeColor(
            com.google.android.material.R.attr.colorOnPrimary,
            0xFF31111F.toInt()
        ),
        primaryContainer = themeColor(
            com.google.android.material.R.attr.colorPrimaryContainer,
            0xFF5B2F4B.toInt()
        ),
        onPrimaryContainer = themeColor(
            com.google.android.material.R.attr.colorOnPrimaryContainer,
            0xFFFFD7EC.toInt()
        ),
        secondary = themeColor(
            com.google.android.material.R.attr.colorSecondary,
            0xFFB7C7FF.toInt()
        ),
        onSecondary = themeColor(
            com.google.android.material.R.attr.colorOnSecondary,
            0xFF1F293D.toInt()
        ),
        secondaryContainer = themeColor(
            com.google.android.material.R.attr.colorSecondaryContainer,
            0xFF3D4563.toInt()
        ),
        onSecondaryContainer = themeColor(
            com.google.android.material.R.attr.colorOnSecondaryContainer,
            0xFFE0E6FF.toInt()
        ),
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = themeColor(
            com.google.android.material.R.attr.colorSurfaceVariant,
            0xFF302830.toInt()
        ),
        surfaceContainer = themeColor(
            com.google.android.material.R.attr.colorSurfaceContainer,
            0xFF262429.toInt()
        ),
        surfaceContainerHigh = themeColor(
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            0xFF2E2B31.toInt()
        ),
        onSurfaceVariant = themeColor(
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            0xFFD0C7D2.toInt()
        ),
        outline = themeColor(
            com.google.android.material.R.attr.colorOutline,
            0xFF938F99.toInt()
        ),
        outlineVariant = themeColor(
            com.google.android.material.R.attr.colorOutlineVariant,
            0xFF4A4450.toInt()
        )
    )

    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun DetailV8Screen(
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
    val clipboardManager = LocalClipboardManager.current
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
    var relatedPage by remember(sourceId, slug) { mutableIntStateOf(1) }
    var relatedLoadingMore by remember(sourceId, slug) { mutableStateOf(false) }
    var relatedEndReached by remember(sourceId, slug) { mutableStateOf(false) }
    var loading by remember(sourceId, slug) { mutableStateOf(true) }
    var error by remember(sourceId, slug) { mutableStateOf<String?>(null) }
    var favorite by remember(sourceId, slug) {
        mutableStateOf(
            initial?.let {
                MangaFavoriteManager.isFavorite(context, it)
            } ?: false
        )
    }
    var historyVersion by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable(sourceId, slug) { mutableIntStateOf(0) }

    val headerState = rememberScrollState()
    val infoState = rememberLazyListState()
    val browserState = rememberLazyListState()
    val relatedState = rememberLazyListState()

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
        relatedPage = 1
        relatedLoadingMore = false
        relatedEndReached = false
        related = emptyList()

        try {
            val result = viewModel.loadDetailData(base).awaitDetailV8()
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
            }.distinctBy {
                it.getSourceId() to it.slug
            }

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

    LaunchedEffect(selected, loading, relatedState, sourceId, slug) {
        if (selected != 2 || loading) return@LaunchedEffect

        snapshotFlow {
            val info = relatedState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to info.totalItemsCount
        }.collect { (lastVisible, totalItems) ->
            if (totalItems == 0 || lastVisible < totalItems - 5) return@collect
            if (loading || relatedLoadingMore || relatedEndReached) return@collect

            val active = manga ?: return@collect
            relatedLoadingMore = true
            try {
                val nextPage = relatedPage + 1
                val incoming = viewModel.loadMoreRelated(
                    active,
                    genres,
                    nextPage
                ).awaitDetailV8()
                val known = related.map { it.getSourceId() to it.slug }.toHashSet()
                val newItems = incoming.filter { item ->
                    item.slug != active.slug && known.add(item.getSourceId() to item.slug)
                }
                if (newItems.isEmpty()) {
                    relatedEndReached = true
                } else {
                    related = related + newItems
                    relatedPage = nextPage
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                relatedEndReached = true
            } finally {
                relatedLoadingMore = false
            }
        }
    }

    LaunchedEffect(
        selected,
        headerState,
        infoState,
        browserState,
        relatedState
    ) {
        snapshotFlow {
            val contentAtTop = when (selected) {
                0 -> !infoState.canScrollBackward
                1 -> !browserState.canScrollBackward
                else -> !relatedState.canScrollBackward
            }

            !headerState.canScrollBackward && contentAtTop
        }.collect(onScrollState)
    }

    val active = manga

    val lastIndex = remember(active, historyVersion) {
        active?.let {
            MangaHistoryManager.getLastReadChapterIndex(context, it)
        } ?: -1f
    }

    val resumeChapter = remember(chapters, lastIndex) {
        chapters.firstOrNull {
            abs(it.index - lastIndex) < 0.001f
        } ?: chapters.minByOrNull {
            it.index
        }
    }

    val hasResume = resumeChapter != null &&
        lastIndex >= 0f &&
        abs(resumeChapter.index - lastIndex) < 0.001f

    val toggleFavorite: () -> Unit = {
        active?.let {
            try {
                val snapshot = favoriteSnapshotV8(it, chapters)
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
                    putExtra(Intent.EXTRA_TEXT, detailShareUrlV8(context, it))
                }

                context.startActivity(
                    Intent.createChooser(intent, "Bagikan manga")
                )
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
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            "Detail manga",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = toggleFavorite,
                            enabled = active != null
                        ) {
                            val scale by animateFloatAsState(
                                targetValue = if (favorite) 1.12f else 1f,
                                label = "favoriteScaleV8"
                            )

                            Icon(
                                if (favorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
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

                        IconButton(
                            onClick = share,
                            enabled = active != null
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Bagikan manga",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor =
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (active != null && resumeChapter != null) {
                        onChapter(active, resumeChapter, chapters)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        hasResume -> "Lanjut membaca Chapter ${MangaChapter.formatIndex(resumeChapter!!.index)}"
                        resumeChapter != null -> "Mulai membaca"
                        loading -> "Memuat chapter…"
                        else -> "Chapter belum tersedia"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier.padding(padding).fillMaxSize()
        ) {
            val headerMaxHeight = maxHeight * 0.45f

            Column(Modifier.fillMaxSize()) {
                if (active == null) {
                    LazyColumn(
                        state = infoState,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        item {
                            DetailV8Message(
                                title = "Manga tidak tersedia",
                                message = error
                                    ?: "Buka kembali manga dari daftar.",
                                onRetry = if (initial != null) {
                                    onRefresh
                                } else {
                                    null
                                }
                            )
                        }
                    }
                } else {
                    DetailV8Header(
                        manga = active,
                        chapterCount = chapters.size,
                        loading = loading,
                        onCopyTitle = {
                            val titleText = active.title.orEmpty()
                                .ifBlank { "Tanpa judul" }

                            clipboardManager.setText(
                                AnnotatedString(titleText)
                            )

                            Toast.makeText(
                                context,
                                "Judul disalin",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = headerMaxHeight)
                            .verticalScroll(headerState)
                    )

                    DetailV8Tabs(
                        titles = listOf("Tentang", "Chapter", "Serupa"),
                        selected = selected,
                        onSelect = { selected = it }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    if (selected == 0) {
                        DetailV8Info(
                            manga = active,
                            genres = genres,
                            loading = loading,
                            error = error,
                            onRetry = onRefresh,
                            onGenre = onGenre,
                            state = infoState,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        DetailV8Browser(
                            manga = active,
                            chapters = chapters,
                            related = related,
                            relatedLoadingMore = relatedLoadingMore,
                            showRelated = selected == 2,
                            loading = loading,
                            error = error,
                            historyVersion = historyVersion,
                            onRetry = onRefresh,
                            onChapter = onChapter,
                            onRelated = onRelated,
                            state = if (selected == 2) {
                                relatedState
                            } else {
                                browserState
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailV8Header(
    manga: MangaPost,
    chapterCount: Int,
    loading: Boolean,
    onCopyTitle: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailV8Image(
                manga,
                Modifier.width(52.dp).aspectRatio(0.68f)
                    .clip(RoundedCornerShape(5.dp))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    manga.title.orEmpty().ifBlank { "Tanpa judul" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.clickable(onClick = onCopyTitle)
                )
                Text(
                    listOf(
                        manga.author.orEmpty(),
                        manga.status.orEmpty(),
                        manga.getTypeLabel(),
                        manga.getSourceLabel(),
                        if (loading && chapterCount == 0) "Memuat…" else "$chapterCount chapter"
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailV8Tabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            titles.forEachIndexed { index, title ->
                val isSelected = selected == index

                Button(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 6.dp,
                        vertical = 12.dp
                    ),
                    modifier = Modifier.weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics {
                            this.selected = isSelected
                        }
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailV8Info(
    manga: MangaPost,
    genres: List<KomikcastClient.GenreItem>,
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

    val labels = remember(manga.genre) {
        manga.genre.orEmpty()
            .split(",", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (error != null) {
            item {
                DetailV8Message(
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
            Column(
                Modifier.animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailV8Section("02", "Sinopsis")

                SelectionContainer {
                    Text(
                        manga.synopsis.orEmpty().ifBlank {
                            if (loading) {
                                "Sinopsis sedang dimuat…"
                            } else {
                                "Sinopsis belum tersedia."
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!manga.synopsis.isNullOrBlank()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            if (expanded) "Ringkas" else "Baca selengkapnya"
                        )
                    }
                }
            }
        }

        if (labels.isNotEmpty()) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    DetailV8Section("03", "Genre")

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
                                        MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor =
                                        MaterialTheme.colorScheme.primary
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
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    DetailV8Section("04", "Catatan & informasi")

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
private fun DetailV8Browser(
    manga: MangaPost,
    chapters: List<MangaChapter>,
    related: List<MangaPost>,
    relatedLoadingMore: Boolean,
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
        context.getSharedPreferences(
            "miku_detail_chapter_prefs",
            Context.MODE_PRIVATE
        )
    }

    var descending by remember {
        mutableStateOf(
            orderPrefs.getBoolean(
                "global_chapter_order_newest_first",
                false
            )
        )
    }

    var chapterGrid by remember {
        mutableStateOf(MangaSettingsManager.isChapterGrid2(context))
    }

    val shown = remember(chapters, descending) {
        if (descending) {
            chapters.sortedByDescending { it.index }
        } else {
            chapters.sortedBy { it.index }
        }
    }

    val chapterGroups = remember(shown, chapterGrid) {
        shown.groupBy { chapter ->
            val date = chapter.date.orEmpty().trim()
            when {
                date.isBlank() -> "Tanggal tidak tersedia"
                date.contains("week", true) || date.contains("hari", true) -> "This week"
                else -> "Earlier · $date"
            }
        }.mapValues { (_, items) -> items.chunked(if (chapterGrid) 2 else 1) }
    }

    val relatedRows = remember(related) {
        related.chunked(3)
    }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DetailV8Section(
                            if (showRelated) "06" else "05",
                            if (showRelated) {
                                "Bacaan berikutnya"
                            } else {
                                "Daftar chapter"
                            }
                        )

                        Text(
                            if (showRelated) {
                                "${related.size} judul"
                            } else {
                                "${shown.size} dari ${chapters.size} chapter"
                            },
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
                                    )
                                    .apply()
                            }
                        ) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = if (descending) {
                                    "Urutkan chapter terlama terlebih dahulu"
                                } else {
                                    "Urutkan chapter terbaru terlebih dahulu"
                                },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                chapterGrid = !chapterGrid
                                MangaSettingsManager.setChapterLayout(
                                    context,
                                    if (chapterGrid) {
                                        MangaSettingsManager.CHAPTER_LAYOUT_GRID_2
                                    } else {
                                        MangaSettingsManager.CHAPTER_LAYOUT_DEFAULT
                                    }
                                )
                            }
                        ) {
                            Icon(
                                if (chapterGrid) {
                                    Icons.Default.ViewList
                                } else {
                                    Icons.Default.GridView
                                },
                                contentDescription = if (chapterGrid) {
                                    "Tampilkan chapter sebagai daftar"
                                } else {
                                    "Tampilkan chapter dalam grid dua kolom"
                                },
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (!showRelated) {
                    Text(
                        "Tarik layar ke bawah untuk memperbarui.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        if (error != null) {
            item {
                DetailV8Message(
                    "Tidak dapat memuat data",
                    error,
                    onRetry
                )
            }
        }

        val hasNoData = if (showRelated) {
            related.isEmpty()
        } else {
            chapters.isEmpty()
        }

        if (loading && hasNoData) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        if (showRelated) {
                            "Memuat manga serupa…"
                        } else {
                            "Memuat daftar chapter…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else if (showRelated) {
            if (related.isEmpty()) {
                item {
                    DetailV8Message(
                        "Belum ada rekomendasi",
                        "Sumber ini belum menyediakan manga serupa."
                    )
                }
            } else {
                itemsIndexed(
                    relatedRows,
                    key = { index, row ->
                        val first = row.first()
                        "related:$index:${first.getSourceId()}:${first.slug}"
                    }
                ) { _, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        row.forEach { post ->
                            DetailV8RelatedCard(
                                post = post,
                                onClick = { onRelated(post) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (relatedLoadingMore) {
                item(key = "related_loading_more") {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        } else if (chapters.isEmpty()) {
            item {
                DetailV8Message(
                    "Belum ada chapter",
                    "Tarik layar ke bawah untuk memperbarui."
                )
            }
        } else {
            chapterGroups.forEach { (dateHeader, rows) ->
                item(key = "date:$dateHeader") {
                    Text(
                        dateHeader,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = 6.dp
                        )
                    )
                }
                itemsIndexed(
                    rows,
                    key = { index, row ->
                        val first = row.first()
                        "chapter:$dateHeader:$index:${first.slug}:${first.index}"
                    }
                ) { _, row ->
                    if (chapterGrid) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            row.forEach { chapter ->
                                DetailV8ChapterRow(
                                    manga = manga,
                                    chapter = chapter,
                                    historyVersion = historyVersion,
                                    onClick = { onChapter(manga, chapter, chapters) },
                                    grid = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    } else {
                        DetailV8ChapterRow(
                            manga = manga,
                            chapter = row.first(),
                            historyVersion = historyVersion,
                            onClick = { onChapter(manga, row.first(), chapters) },
                            grid = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailV8RelatedCard(
    post: MangaPost,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier.clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DetailV8Image(
            post,
            Modifier.fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(6.dp))
        )

        Text(
            post.title.orEmpty().ifBlank { "Tanpa judul" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun DetailV8ChapterRow(
    manga: MangaPost,
    chapter: MangaChapter,
    historyVersion: Int,
    onClick: () -> Unit,
    grid: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress = remember(historyVersion, manga.slug, manga.getSourceId(), chapter.index) {
        MangaHistoryManager.getProgress(context, manga, chapter.index)
    }
    val hasProgress = progress != null && progress.totalPages > 0
    val fraction = if (hasProgress && progress != null) {
        ((progress.page + 1).toFloat() / progress.totalPages).coerceIn(0f, 1f)
    } else 0f
    val page = if (hasProgress && progress != null) {
        (progress.page + 1).coerceAtMost(progress.totalPages)
    } else 0
    val lastIndex = remember(historyVersion, manga.slug, manga.getSourceId()) {
        MangaHistoryManager.getLastReadChapterIndex(context, manga)
    }
    val isLastRead = lastIndex >= 0f && abs(lastIndex - chapter.index) < 0.001f
    val finished = hasProgress && progress != null && page >= progress.totalPages
    Column(
        modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLastRead) {
                Box(
                    Modifier.padding(end = 8.dp).size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Text(
                chapter.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isLastRead) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (finished) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selesai dibaca",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(17.dp)
                )
            }
        }
        if (chapter.date.isNotBlank() || hasProgress) {
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    chapter.date.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasProgress && progress != null) {
                    Text(
                        "Hal. $page/${progress.totalPages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (hasProgress) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun DetailV8Section(number: String, title: String) {
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
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false)
                .semantics { heading() }
        )
    }
}

@Composable
private fun DetailV8Fact(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailV8Message(
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
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
private fun DetailV8Image(
    post: MangaPost,
    modifier: Modifier
) {
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

        headers.names().forEach { name ->
            headers[name]?.let {
                builder.setHeader(name, it)
            }
        }

        builder.build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Sampul ${post.title.orEmpty()}",
        contentScale = ContentScale.Crop,
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceContainer
        ),
        loading = {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        error = {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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

private fun detailShareUrlV8(
    context: Context,
    manga: MangaPost
): String {
    val raw = manga.slug.orEmpty().trim()

    if (
        raw.startsWith("http://", true) ||
        raw.startsWith("https://", true)
    ) {
        return raw
    }

    val source = manga.getSourceId()
    val domain = MangaSettingsManager.getSourceDomain(
        context,
        source
    ).trimEnd('/')

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

private fun favoriteSnapshotV8(
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
    ).withSource(
        manga.getSourceId(),
        manga.getSourceLabel()
    )

    copy.info = manga.info
    copy.totalChapters = maxOf(
        manga.totalChapters,
        chapters.size
    )

    chapters.maxByOrNull {
        it.index
    }?.let {
        copy.latestChapter = MangaChapter.formatIndex(it.index)
        copy.latestChapterDate = it.date.orEmpty()
    }

    return copy
}

private suspend fun <T> CompletableFuture<T>.awaitDetailV8(): T {
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
