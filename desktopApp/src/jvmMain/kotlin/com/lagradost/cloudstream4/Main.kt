package com.lagradost.cloudstream4

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.default_icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.net.URI
import java.net.URL
import java.util.prefs.Preferences

// ==========================================
// THEME & COLOR PALETTE (Netflix / Prime Video)
// ==========================================
private val NetflixRed = Color(0xFFE50914)
private val NetflixDarkRed = Color(0xFFB81D24)
private val PrimeBlue = Color(0xFF00A8E1)
private val BackgroundBlack = Color(0xFF0C0D10)
private val SurfaceDark = Color(0xFF14171E)
private val CardDark = Color(0xFF1C202A)
private val TextWhite = Color(0xFFF5F5F7)
private val TextMuted = Color(0xFF8F96A3)
private val GoldAccent = Color(0xFFFFB800)
private val GreenBadge = Color(0xFF46D369)

// ==========================================
// MODELS
// ==========================================
enum class NavSection(val title: String, val icon: String) {
    Home("Home", "🏠"),
    Movies("Movies", "🎬"),
    Series("TV Series", "📺"),
    Bdix("BDIX Bangla", "🇧🇩"),
    LiveTv("Live TV", "🔴"),
    MyList("My Watchlist", "⭐"),
    Repositories("Repositories", "⚙️")
}

data class StreamSource(
    val name: String,
    val quality: String,
    val url: String
)

data class MediaItem(
    val id: String,
    val title: String,
    val type: String, // "Movie", "TV Series", "Live TV"
    val year: String,
    val rating: String,
    val duration: String,
    val qualityBadge: String,
    val genres: List<String>,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val provider: String,
    val sources: List<StreamSource> = emptyList()
)

@Serializable
private data class RepositoryManifest(
    val name: String = "Repository",
    val pluginLists: List<String> = emptyList()
)

@Serializable
private data class Provider(
    val url: String = "",
    val version: Int = 0,
    val apiVersion: Int = 1,
    val name: String = "Unnamed provider",
    val internalName: String = "",
    val status: Int = 1,
    val description: String? = null,
    val language: String? = null,
    val authors: List<String> = emptyList(),
    val tvTypes: List<String>? = null,
    val iconUrl: String? = null
)

private val repositoryPreferences = Preferences.userRoot().node("NewStream")
private const val repositoriesKey = "repositories"
private const val watchlistKey = "watchlist_ids"
private val repositoryJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

// ==========================================
// CURATED CONTENT (NETFLIX & BDIX HIT CATALOG)
// ==========================================
private val SampleMediaCatalog = listOf(
    MediaItem(
        id = "m1",
        title = "Toofan (তুফান)",
        type = "Movie",
        year = "2024",
        rating = "8.8",
        duration = "2h 25m",
        qualityBadge = "4K UHD",
        genres = listOf("Action", "Crime", "Thriller"),
        overview = "A ruthless gangster rises from the underworld to dominate the 1990s crime syndicate in Bangladesh, facing high-stakes betrayal and law enforcement in a legendary saga.",
        posterUrl = "https://image.tmdb.org/t/p/w500/y9xvdW1o7a1BfXm4i0X6w0bWq7u.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/b82qNpKeq5FvWkUu6O1E3x0x4fX.jpg",
        provider = "BDIX Circleftp",
        sources = listOf(
            StreamSource("BDIX High Speed Direct (1080p)", "1080p", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
            StreamSource("DhakaFlix Server 2 (4K)", "4K", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
        )
    ),
    MediaItem(
        id = "m2",
        title = "Priyotoma (প্রিয়তমা)",
        type = "Movie",
        year = "2023",
        rating = "8.4",
        duration = "2h 18m",
        qualityBadge = "1080p",
        genres = listOf("Romance", "Drama", "Action"),
        overview = "An epic tale of love, tragedy, and sacrifice spanning decades between two passionate souls divided by societal turmoil and destiny.",
        posterUrl = "https://image.tmdb.org/t/p/w500/r5C9sJq8Ue6D8h2j3n8m4j7y8t4.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
        provider = "BanglaPlex",
        sources = listOf(
            StreamSource("BanglaPlex BDIX (1080p)", "1080p", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
        )
    ),
    MediaItem(
        id = "m3",
        title = "Dune: Part Two",
        type = "Movie",
        year = "2024",
        rating = "8.9",
        duration = "2h 46m",
        qualityBadge = "4K IMAX",
        genres = listOf("Sci-Fi", "Adventure", "Action"),
        overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family in this desert masterpiece.",
        posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
        provider = "9kMovies",
        sources = listOf(
            StreamSource("Fast CDN Stream 1 (4K)", "4K", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
            StreamSource("Dual Audio (Hindi + English)", "1080p", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
        )
    ),
    MediaItem(
        id = "m4",
        title = "Deadpool & Wolverine",
        type = "Movie",
        year = "2024",
        rating = "8.1",
        duration = "2h 08m",
        qualityBadge = "1080p HD",
        genres = listOf("Action", "Comedy", "Sci-Fi"),
        overview = "A listless Wade Wilson toils away in civilian life with his days as the morally flexible mercenary behind him, until a catastrophic threat forces him to team up with Wolverine.",
        posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
        provider = "TheMoviesFlix",
        sources = listOf(
            StreamSource("1080p WEB-DL Dual Audio", "1080p", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4")
        )
    ),
    MediaItem(
        id = "m5",
        title = "Stranger Things: Season 5",
        type = "TV Series",
        year = "2025",
        rating = "9.1",
        duration = "8 Episodes",
        qualityBadge = "4K Dolby",
        genres = listOf("Drama", "Fantasy", "Horror", "Mystery"),
        overview = "The final battle for Hawkins begins as Eleven and her friends venture deep into the Upside Down to confront Vecna and seal the rift forever.",
        posterUrl = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
        provider = "9kMovies",
        sources = listOf(
            StreamSource("Episode 1: The Crawl (4K)", "4K", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
            StreamSource("Episode 2: The Vanishing (4K)", "4K", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
        )
    ),
    MediaItem(
        id = "m6",
        title = "Karagar (কারাগার)",
        type = "TV Series",
        year = "2023",
        rating = "8.7",
        duration = "Season 1 & 2",
        qualityBadge = "1080p HD",
        genres = listOf("Mystery", "Thriller", "Crime"),
        overview = "A mysterious man appears inside cell 50 of Akashnagar Central Jail, which has remained locked for 50 years. He speaks sign language and claims to be a man from 250 years ago.",
        posterUrl = "https://image.tmdb.org/t/p/w500/2G0w39qWq1m5o8b2R7y8u8b7t4.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/b82qNpKeq5FvWkUu6O1E3x0x4fX.jpg",
        provider = "BdixDflix",
        sources = listOf(
            StreamSource("Complete BDIX Stream (1080p)", "1080p", "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
        )
    ),
    MediaItem(
        id = "m7",
        title = "T Sports Live HD",
        type = "Live TV",
        year = "LIVE",
        rating = "LIVE",
        duration = "24/7",
        qualityBadge = "1080p 60FPS",
        genres = listOf("Sports", "Cricket", "Football", "Live"),
        overview = "Live cricket matches, football leagues, BPL, IPL, and international tournament broadcast directly with low latency.",
        posterUrl = "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
        provider = "BdixBdipTV",
        sources = listOf(
            StreamSource("Live HLS Stream (Master)", "1080p", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        )
    ),
    MediaItem(
        id = "m8",
        title = "Gazi TV (GTV) Live",
        type = "Live TV",
        year = "LIVE",
        rating = "LIVE",
        duration = "24/7",
        qualityBadge = "720p HD",
        genres = listOf("Sports", "Entertainment", "News", "Live"),
        overview = "Popular Bangladeshi television station broadcasting live sports, news bulletins, dramas, and entertainment programs.",
        posterUrl = "https://image.tmdb.org/t/p/w500/vQWk5R2Sd4Z2yvC9q8uW4v2X6q8.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
        provider = "BdixCloudTV",
        sources = listOf(
            StreamSource("Live HLS Feed", "720p", "https://test-streams.mux.dev/test_001/stream.m3u8")
        )
    )
)

// ==========================================
// MEDIA PLAYER LAUNCHER
// ==========================================
private fun playMediaWithPlayer(
    title: String,
    streamUrl: String,
    onStatusMessage: (String) -> Unit
) {
    Thread {
        try {
            val mpvPath = File("/usr/bin/mpv")
            val vlcPath = File("/usr/bin/vlc")

            if (mpvPath.exists()) {
                onStatusMessage("Launching '$title' in MPV Player...")
                ProcessBuilder(
                    "/usr/bin/mpv",
                    "--force-window=yes",
                    "--title=NewStream: $title",
                    "--hwdec=auto",
                    streamUrl
                ).inheritIO().start()
            } else if (vlcPath.exists()) {
                onStatusMessage("Launching '$title' in VLC Player...")
                ProcessBuilder(
                    "/usr/bin/vlc",
                    "--meta-title=NewStream: $title",
                    streamUrl
                ).start()
            } else {
                onStatusMessage("Opening stream in default media handler...")
                ProcessBuilder("xdg-open", streamUrl).start()
            }
        } catch (e: Exception) {
            onStatusMessage("Error playing stream: ${e.message}")
        }
    }.start()
}

// ==========================================
// MAIN COMPOSE ENTRY POINT
// ==========================================
fun main() = application {
    val windowState = rememberWindowState(width = 1240.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "NewStream - Cinematic Media Center",
        icon = painterResource(Res.drawable.default_icon),
        state = windowState
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = NetflixRed,
                onPrimary = TextWhite,
                surface = SurfaceDark,
                background = BackgroundBlack,
                onSurface = TextWhite,
                onBackground = TextWhite
            )
        ) {
            NetflixDesktopApp()
        }
    }
}

// ==========================================
// NETFLIX / PRIME VIDEO HOME COMPOSABLE
// ==========================================
@Composable
private fun NetflixDesktopApp() {
    var activeSection by remember { mutableStateOf(NavSection.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var statusNotification by remember { mutableStateOf<String?>(null) }

    val watchlist = remember {
        mutableStateListOf<String>().apply {
            addAll(repositoryPreferences.get(watchlistKey, "").split(",").filter(String::isNotBlank))
        }
    }

    val repositories = remember {
        mutableStateListOf<String>().apply {
            val saved = repositoryPreferences.get(repositoriesKey, "")
            if (saved.isNotBlank()) {
                addAll(saved.split("\n").filter(String::isNotBlank))
            } else {
                // Pre-populate with Redowan's BDIX repository
                add("https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json")
                repositoryPreferences.put(repositoriesKey, "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json")
            }
        }
    }

    val providerLists = remember { mutableStateMapOf<String, List<Provider>>() }
    val scope = rememberCoroutineScope()

    fun refreshRepo(url: String) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val manifest = fetchJson<RepositoryManifest>(url)
                    manifest.pluginLists.flatMap { pluginListUrl ->
                        fetchJson<List<Provider>>(resolveUrl(url, pluginListUrl))
                    }
                }
            }.onSuccess { providers ->
                providerLists[url] = providers
            }
        }
    }

    LaunchedEffect(repositories.toList()) {
        repositories.filterNot { providerLists.containsKey(it) }.forEach(::refreshRepo)
    }

    fun toggleWatchlist(id: String) {
        if (watchlist.contains(id)) {
            watchlist.remove(id)
            statusNotification = "Removed from Watchlist"
        } else {
            watchlist.add(id)
            statusNotification = "Added to Watchlist ⭐"
        }
        repositoryPreferences.put(watchlistKey, watchlist.joinToString(","))
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP NETFLIX HEADER
            NetflixHeader(
                activeSection = activeSection,
                onSectionSelect = {
                    activeSection = it
                    if (it != NavSection.Home) searchQuery = ""
                },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                providerCount = providerLists.values.sumOf { it.size },
                onOpenRepoPage = { activeSection = NavSection.Repositories }
            )

            // BODY CONTENT
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (searchQuery.isNotBlank()) {
                    SearchResultsView(
                        query = searchQuery,
                        allMedia = SampleMediaCatalog,
                        providers = providerLists.values.flatten(),
                        onSelectMedia = { selectedMedia = it },
                        onPlayMedia = { item ->
                            val stream = item.sources.firstOrNull()?.url ?: ""
                            playMediaWithPlayer(item.title, stream) { statusNotification = it }
                        }
                    )
                } else {
                    when (activeSection) {
                        NavSection.Home -> NetflixHomeFeed(
                            catalog = SampleMediaCatalog,
                            watchlist = watchlist,
                            onSelectMedia = { selectedMedia = it },
                            onPlayMedia = { item ->
                                val stream = item.sources.firstOrNull()?.url ?: ""
                                playMediaWithPlayer(item.title, stream) { statusNotification = it }
                            },
                            onToggleWatchlist = ::toggleWatchlist
                        )
                        NavSection.Movies -> MediaGridView(
                            title = "Blockbuster Movies",
                            items = SampleMediaCatalog.filter { it.type == "Movie" },
                            onSelectMedia = { selectedMedia = it }
                        )
                        NavSection.Series -> MediaGridView(
                            title = "TV Series & Binge Shows",
                            items = SampleMediaCatalog.filter { it.type == "TV Series" },
                            onSelectMedia = { selectedMedia = it }
                        )
                        NavSection.Bdix -> MediaGridView(
                            title = "BDIX & Bangla High-Speed Catalog",
                            items = SampleMediaCatalog.filter { it.provider.contains("BDIX", true) || it.provider.contains("Bangla", true) },
                            onSelectMedia = { selectedMedia = it }
                        )
                        NavSection.LiveTv -> MediaGridView(
                            title = "Live TV Broadcast Streams",
                            items = SampleMediaCatalog.filter { it.type == "Live TV" },
                            onSelectMedia = { selectedMedia = it }
                        )
                        NavSection.MyList -> {
                            val savedItems = SampleMediaCatalog.filter { watchlist.contains(it.id) }
                            if (savedItems.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Your Watchlist is empty. Browse movies or shows and click '+ My List'!", color = TextMuted)
                                }
                            } else {
                                MediaGridView(
                                    title = "My Watchlist ⭐",
                                    items = savedItems,
                                    onSelectMedia = { selectedMedia = it }
                                )
                            }
                        }
                        NavSection.Repositories -> RepositoriesManagementPage(
                            repositories = repositories,
                            providerLists = providerLists,
                            onRefresh = ::refreshRepo
                        )
                    }
                }
            }
        }

        // QUICK STATUS NOTIFICATION OVERLAY
        statusNotification?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3500)
                statusNotification = null
            }
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixDarkRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = msg,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // NETFLIX QUICK-VIEW DETAIL MODAL
        selectedMedia?.let { media ->
            NetflixDetailModal(
                media = media,
                isSaved = watchlist.contains(media.id),
                onDismiss = { selectedMedia = null },
                onToggleWatchlist = { toggleWatchlist(media.id) },
                onPlay = { source ->
                    playMediaWithPlayer(media.title, source.url) { statusNotification = it }
                }
            )
        }
    }
}

// ==========================================
// TOP HEADER BAR (NETFLIX / PRIME STYLE)
// ==========================================
@Composable
private fun NetflixHeader(
    activeSection: NavSection,
    onSectionSelect: (NavSection) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    providerCount: Int,
    onOpenRepoPage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        color = BackgroundBlack.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // BRAND LOGO & NAVIGATION TABS
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // LOGO
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NEW",
                        color = NetflixRed,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "STREAM",
                        color = TextWhite,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // TABS
                NavSection.values().forEach { section ->
                    val isSelected = activeSection == section && searchQuery.isBlank()
                    Text(
                        text = section.title,
                        color = if (isSelected) TextWhite else TextMuted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSectionSelect(section) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // RIGHT CONTROLS: REPO BADGE + SEARCH
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // REPOSITORY STATUS PILL
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onOpenRepoPage() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(GreenBadge, CircleShape))
                        Text(
                            text = if (providerCount > 0) "BDIX Online ($providerCount)" else "Plugins",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // SEARCH BAR
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.width(260.dp).height(48.dp),
                    placeholder = { Text("Search titles, BDIX...", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NetflixRed,
                        unfocusedBorderColor = CardDark,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}

// ==========================================
// HOME FEED (HERO + HORIZONTAL RAILS)
// ==========================================
@Composable
private fun NetflixHomeFeed(
    catalog: List<MediaItem>,
    watchlist: List<String>,
    onSelectMedia: (MediaItem) -> Unit,
    onPlayMedia: (MediaItem) -> Unit,
    onToggleWatchlist: (String) -> Unit
) {
    val heroItem = catalog.firstOrNull() ?: return

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // 1. HERO BANNER
        item {
            NetflixHeroBanner(
                media = heroItem,
                isSaved = watchlist.contains(heroItem.id),
                onPlay = { onPlayMedia(heroItem) },
                onMoreInfo = { onSelectMedia(heroItem) },
                onToggleWatchlist = { onToggleWatchlist(heroItem.id) }
            )
        }

        // 2. TRENDING NOW (TOP 10 ROW)
        item {
            ContentRail(
                title = "🔥 Trending Now (Top 10 Today)",
                items = catalog,
                showRank = true,
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia
            )
        }

        // 3. BDIX & BANGLA BLOCKBUSTERS
        item {
            ContentRail(
                title = "🇧🇩 BDIX & Bangla Fast Servers",
                items = catalog.filter { it.provider.contains("BDIX", true) || it.provider.contains("Bangla", true) },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia
            )
        }

        // 4. BLOCKBUSTER MOVIES
        item {
            ContentRail(
                title = "🎬 Popular Movies & Dual Audio",
                items = catalog.filter { it.type == "Movie" },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia
            )
        }

        // 5. TV SERIES & WEB SHOWS
        item {
            ContentRail(
                title = "📺 Binge-Worthy TV Series",
                items = catalog.filter { it.type == "TV Series" },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia
            )
        }

        // 6. LIVE TV CHANNELS
        item {
            ContentRail(
                title = "🔴 Live Sports & TV Channels",
                items = catalog.filter { it.type == "Live TV" },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia
            )
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// ==========================================
// NETFLIX HERO BANNER
// ==========================================
@Composable
private fun NetflixHeroBanner(
    media: MediaItem,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onMoreInfo: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) {
        // BACKDROP IMAGE
        AsyncImage(
            model = media.backdropUrl,
            contentDescription = media.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // CINEMATIC GRADIENT OVERLAY
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            BackgroundBlack.copy(alpha = 0.5f),
                            BackgroundBlack.copy(alpha = 0.9f),
                            BackgroundBlack
                        ),
                        startY = 100f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            BackgroundBlack.copy(alpha = 0.95f),
                            BackgroundBlack.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        endX = 800f
                    )
                )
        )

        // CONTENT OVERLAY
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 48.dp, vertical = 32.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // BADGE
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = NetflixRed, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = "TOP #1 TODAY",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(color = CardDark, shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = media.qualityBadge,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text("★ ${media.rating}", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(media.year, color = TextMuted, fontSize = 13.sp)
                Text(media.duration, color = TextMuted, fontSize = 13.sp)
            }

            // TITLE
            Text(
                text = media.title,
                color = TextWhite,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 48.sp
            )

            // OVERVIEW
            Text(
                text = media.overview,
                color = TextWhite.copy(alpha = 0.85f),
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // ACTION BUTTONS (PLAY NOW & MORE INFO)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("▶  Play Now", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }

                Button(
                    onClick = onMoreInfo,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text("ℹ  More Info", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                }

                OutlinedButton(
                    onClick = onToggleWatchlist,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Text(if (isSaved) "✓ In My List" else "+ My List", fontSize = 14.sp)
                }
            }
        }
    }
}

// ==========================================
// CONTENT RAIL (HORIZONTAL SCROLLING ROW)
// ==========================================
@Composable
private fun ContentRail(
    title: String,
    items: List<MediaItem>,
    showRank: Boolean = false,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(
            text = title,
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 10.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(items) { index, item ->
                NetflixCard(
                    media = item,
                    rank = if (showRank) index + 1 else null,
                    onClick = { onItemClick(item) },
                    onPlay = { onPlayClick(item) }
                )
            }
        }
    }
}

// ==========================================
// NETFLIX MEDIA CARD
// ==========================================
@Composable
private fun NetflixCard(
    media: MediaItem,
    rank: Int? = null,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Bottom) {
        // OPTIONAL GIANT NETFLIX RANK NUMBER (#1, #2, #3...)
        rank?.let {
            Text(
                text = "$it",
                color = NetflixRed,
                fontSize = 90.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.offset(x = 10.dp, y = 10.dp)
            )
        }

        Card(
            modifier = Modifier
                .width(185.dp)
                .height(275.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isHovered) 2.dp else 1.dp,
                    color = if (isHovered) NetflixRed else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // POSTER IMAGE
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // BOTTOM INFO OVERLAY
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, BackgroundBlack.copy(alpha = 0.95f))
                            )
                        )
                )

                // BADGE AT TOP RIGHT
                Surface(
                    color = BackgroundBlack.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = media.qualityBadge,
                        color = GoldAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // BOTTOM TEXT & PLAY ICON
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = media.title,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("★ ${media.rating} • ${media.year}", color = TextMuted, fontSize = 11.sp)
                        Surface(
                            color = NetflixRed,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp).clickable { onPlay() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("▶", color = TextWhite, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// GRID VIEW (FOR CATEGORIES & SEARCH)
// ==========================================
@Composable
private fun MediaGridView(
    title: String,
    items: List<MediaItem>,
    onSelectMedia: (MediaItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Text(title, color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            val chunked = items.chunked(5)
            items(chunked) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        NetflixCard(
                            media = item,
                            onClick = { onSelectMedia(item) },
                            onPlay = {
                                val url = item.sources.firstOrNull()?.url ?: ""
                                playMediaWithPlayer(item.title, url) {}
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SEARCH RESULTS VIEW
// ==========================================
@Composable
private fun SearchResultsView(
    query: String,
    allMedia: List<MediaItem>,
    providers: List<Provider>,
    onSelectMedia: (MediaItem) -> Unit,
    onPlayMedia: (MediaItem) -> Unit
) {
    val matchedMedia = allMedia.filter {
        it.title.contains(query, true) ||
            it.overview.contains(query, true) ||
            it.genres.any { g -> g.contains(query, true) }
    }

    val matchedProviders = providers.filter {
        it.name.contains(query, true) ||
            it.description.orEmpty().contains(query, true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Text("Search Results for \"$query\"", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (matchedMedia.isEmpty() && matchedProviders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No movies, series, or BDIX providers matched your search.", color = TextMuted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (matchedMedia.isNotEmpty()) {
                    item {
                        Text("Movies & TV Series (${matchedMedia.size})", color = GoldAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    val chunked = matchedMedia.chunked(5)
                    items(chunked) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowItems.forEach { item ->
                                NetflixCard(
                                    media = item,
                                    onClick = { onSelectMedia(item) },
                                    onPlay = { onPlayMedia(item) }
                                )
                            }
                        }
                    }
                }

                if (matchedProviders.isNotEmpty()) {
                    item {
                        Text("Matched BDIX Providers (${matchedProviders.size})", color = PrimeBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    items(matchedProviders) { provider ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(provider.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    provider.description?.let { Text(it, color = TextMuted, fontSize = 12.sp) }
                                }
                                Surface(color = GreenBadge.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Text("BDIX Provider", color = GreenBadge, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// NETFLIX QUICK-VIEW DETAIL MODAL
// ==========================================
@Composable
private fun NetflixDetailModal(
    media: MediaItem,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onPlay: (StreamSource) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(760.dp).heightIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundBlack),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(NetflixRed, Color.Transparent)))
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // BACKDROP BANNER
                Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                    AsyncImage(
                        model = media.backdropUrl,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, BackgroundBlack.copy(alpha = 0.9f), BackgroundBlack)
                                )
                            )
                    )
                    // CLOSE BUTTON
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Surface(color = BackgroundBlack.copy(alpha = 0.7f), shape = CircleShape) {
                            Text("✕", color = TextWhite, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        }
                    }

                    // BANNER TITLE
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(media.title, color = TextWhite, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("★ ${media.rating}", color = GoldAccent, fontWeight = FontWeight.Bold)
                            Text(media.year, color = TextMuted)
                            Text(media.duration, color = TextMuted)
                            Surface(color = NetflixRed, shape = RoundedCornerShape(4.dp)) {
                                Text(media.qualityBadge, color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                }

                // DETAILS CONTENT
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // GENRES
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        media.genres.forEach { genre ->
                            Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
                                Text(genre, color = TextWhite, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }

                    // SYNOPSIS
                    Text(media.overview, color = TextWhite.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)

                    HorizontalDivider(color = CardDark)

                    // STREAMING SERVERS / SOURCES
                    Text("Available Streaming Servers & Quality:", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    media.sources.forEach { source ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                onPlay(source)
                                onDismiss()
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(color = NetflixRed, shape = CircleShape, modifier = Modifier.size(32.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("▶", color = TextWhite, fontSize = 12.sp)
                                        }
                                    }
                                    Column {
                                        Text(source.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Source: ${media.provider}", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                                Button(
                                    onClick = {
                                        onPlay(source)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Watch in MPV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // WATCHLIST BUTTON
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onToggleWatchlist) {
                            Text(if (isSaved) "✓ Saved in My Watchlist" else "+ Add to Watchlist", color = GoldAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// REPOSITORIES MANAGEMENT PAGE
// ==========================================
@Composable
private fun RepositoriesManagementPage(
    repositories: MutableList<String>,
    providerLists: MutableMap<String, List<Provider>>,
    onRefresh: (String) -> Unit
) {
    var newUrl by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun addRepo() {
        val normalized = newUrl.trim()
        if (normalized.isBlank() || !normalized.startsWith("http")) {
            errorMessage = "Please enter a valid http/https repository URL."
            return
        }
        if (repositories.contains(normalized)) {
            errorMessage = "Repository is already added."
            return
        }
        repositories.add(normalized)
        repositoryPreferences.put(repositoriesKey, repositories.joinToString("\n"))
        newUrl = ""
        errorMessage = null
        onRefresh(normalized)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Plugin Repositories", color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Add community extension repository URLs to discover BDIX, Live TV, and movie sources.", color = TextMuted)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it; errorMessage = null },
                    placeholder = { Text("https://example.com/repo.json", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NetflixRed,
                        unfocusedBorderColor = CardDark,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = ::addRepo,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+ Add Repository", fontWeight = FontWeight.Bold)
                }
            }
            errorMessage?.let {
                Text(it, color = NetflixRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        items(repositories.toList()) { repo ->
            val providers = providerLists[repo] ?: emptyList()
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(repo, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${providers.size} Provider(s) loaded", color = GreenBadge, fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onRefresh(repo) }) { Text("Refresh", color = PrimeBlue) }
                            TextButton(onClick = {
                                repositories.remove(repo)
                                providerLists.remove(repo)
                                repositoryPreferences.put(repositoriesKey, repositories.joinToString("\n"))
                            }) { Text("Remove", color = NetflixRed) }
                        }
                    }

                    if (providers.isNotEmpty()) {
                        HorizontalDivider(color = CardDark)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            providers.forEach { provider ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• ${provider.name}", color = TextWhite, fontSize = 13.sp)
                                    Text(provider.language?.let { "[$it]" } ?: "", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// UTILITIES
// ==========================================
private fun resolveUrl(baseUrl: String, childUrl: String): String {
    return URI(baseUrl).resolve(childUrl).toString()
}

private inline fun <reified T> fetchJson(url: String): T {
    val connection = URL(url).openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    }
    val response = connection.getInputStream().bufferedReader().use { it.readText() }
    return repositoryJson.decodeFromString(serializer(), response)
}
