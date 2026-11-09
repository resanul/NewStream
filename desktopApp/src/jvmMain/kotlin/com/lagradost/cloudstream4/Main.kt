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
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"

// ==========================================
// NAVIGATION SECTIONS
// ==========================================
enum class NavSection(val title: String, val icon: String) {
    Home("Home", "🏠"),
    Movies("Movies", "🎬"),
    Series("TV Series", "📺"),
    Bdix("BDIX Bangla", "🇧🇩"),
    LiveTv("Live TV", "🔴"),
    MyList("Watchlist", "⭐"),
    Downloads("Downloads", "📥"),
    Extensions("Extensions", "🧩"),
    Settings("Settings", "⚙️")
}

// ==========================================
// DATA MODELS
// ==========================================
data class StreamSource(
    val name: String,
    val quality: String,
    val url: String,
    val isDirectVideo: Boolean = true // true = MPV/VLC native stream; false = Web Embed
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
    val tmdbId: Int? = null,
    val sources: List<StreamSource> = emptyList()
)

@Serializable
private data class RepositoryManifest(
    val name: String = "Repository",
    val pluginLists: List<String> = emptyList()
)

@Serializable
data class Provider(
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

@Serializable
private data class TmdbPageResponse(
    val page: Int = 1,
    val results: List<TmdbResultItem> = emptyList(),
    val total_pages: Int = 1
)

@Serializable
private data class TmdbResultItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val vote_average: Double? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val media_type: String? = null
)

data class DownloadTask(
    val id: String,
    val title: String,
    val url: String,
    val status: String,
    val progress: Float,
    val destinationFile: File,
    val errorMessage: String? = null
)

data class DownloadedFile(
    val name: String,
    val sizeFormatted: String,
    val dateFormatted: String,
    val file: File
)

// ==========================================
// PREFERENCES CONSTANTS
// ==========================================
private val preferences = Preferences.userRoot().node("NewStream")
private const val KeyRepositories = "repositories"
private const val KeyWatchlist = "watchlist_ids"
private const val KeyInstalledPlugins = "installed_plugins"
private const val KeyPlayerBackend = "player_backend"
private const val KeyHwdec = "hwdec_enabled"
private const val KeySubtitleLang = "subtitle_language"
private const val KeySubtitleSize = "subtitle_size"
private const val KeyAutoplayNext = "autoplay_next"
private const val KeyDohProvider = "doh_provider"
private const val KeyNsfwFilter = "nsfw_filter"
private const val KeyAppLanguage = "app_language"
private const val KeyDownloadDir = "download_dir"
private const val KeyDownloadQuality = "download_quality"

private val jsonSerializer = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private val DefaultDownloadDirectory: String
    get() = File(System.getProperty("user.home"), "Downloads/NewStream").absolutePath

// ==========================================
// CURATED BASE CATALOG WITH REAL STREAMS & TRAILERS
// ==========================================
private val CuratedBaseCatalog = listOf(
    MediaItem(
        id = "bd_m1",
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
        tmdbId = 562719,
        sources = listOf(
            StreamSource("Official 4K Trailer (MPV)", "4K", "https://www.youtube.com/watch?v=F0K0qR2fUaA", isDirectVideo = true),
            StreamSource("VidSrc HD Stream 1", "1080p", "https://vidsrc.to/embed/movie/562719", isDirectVideo = false),
            StreamSource("2Embed Cinema Stream", "1080p", "https://2embed.cc/embed/562719", isDirectVideo = false)
        )
    ),
    MediaItem(
        id = "bd_m2",
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
        tmdbId = 1032048,
        sources = listOf(
            StreamSource("Official Trailer HD (MPV)", "1080p", "https://www.youtube.com/watch?v=jW2r5W3jU9k", isDirectVideo = true),
            StreamSource("VidSrc HD Player", "1080p", "https://vidsrc.to/embed/movie/1032048", isDirectVideo = false)
        )
    ),
    MediaItem(
        id = "bd_s1",
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
        tmdbId = 207305,
        sources = listOf(
            StreamSource("Official Trailer & Teaser (MPV)", "1080p", "https://www.youtube.com/watch?v=s0K5Z3o0zN8", isDirectVideo = true),
            StreamSource("VidSrc TV Series Stream", "1080p", "https://vidsrc.to/embed/tv/207305", isDirectVideo = false)
        )
    ),
    MediaItem(
        id = "bd_s2",
        title = "Mohanagar (মহানগর)",
        type = "TV Series",
        year = "2023",
        rating = "8.6",
        duration = "Season 1 & 2",
        qualityBadge = "1080p HD",
        genres = listOf("Crime", "Drama", "Thriller"),
        overview = "One fateful night inside the Kotwali police station intertwines the lives of OC Harun and corrupt powerbrokers in a tense political chess game.",
        posterUrl = "https://image.tmdb.org/t/p/w500/u3vY0lTspN0yK2E0G0F7YQ9m9R.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
        provider = "BdixCircleftp",
        tmdbId = 127116,
        sources = listOf(
            StreamSource("Official Trailer (MPV)", "1080p", "https://www.youtube.com/watch?v=n784P3Wd6Zg", isDirectVideo = true),
            StreamSource("VidSrc TV Stream", "1080p", "https://vidsrc.to/embed/tv/127116", isDirectVideo = false)
        )
    ),
    MediaItem(
        id = "bd_m3",
        title = "Surongo (সুড়ঙ্গ)",
        type = "Movie",
        year = "2023",
        rating = "8.3",
        duration = "2h 30m",
        qualityBadge = "1080p HD",
        genres = listOf("Crime", "Thriller"),
        overview = "A dedicated electrician plans an audacious underground heist to win back love, only to find himself in a web of deceit.",
        posterUrl = "https://image.tmdb.org/t/p/w500/z0TfU6f8w5qG1o9y2b7x8u3.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
        provider = "BanglaPlex",
        tmdbId = 1126938,
        sources = listOf(
            StreamSource("Official Trailer (MPV)", "1080p", "https://www.youtube.com/watch?v=Z3W6F88Lp30", isDirectVideo = true),
            StreamSource("VidSrc Movie Stream", "1080p", "https://vidsrc.to/embed/movie/1126938", isDirectVideo = false)
        )
    ),
    MediaItem(
        id = "hol_m1",
        title = "Deadpool & Wolverine",
        type = "Movie",
        year = "2024",
        rating = "8.1",
        duration = "2h 08m",
        qualityBadge = "4K UHD",
        genres = listOf("Action", "Comedy", "Sci-Fi"),
        overview = "A listless Wade Wilson toils away in civilian life until a catastrophic threat forces him to team up with an alternate universe Wolverine.",
        posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
        provider = "TheMoviesFlix",
        tmdbId = 533535,
        sources = listOf(
            StreamSource("Official 4K Final Trailer (MPV)", "4K", "https://www.youtube.com/watch?v=Idh8n5XuYIA", isDirectVideo = true),
            StreamSource("VidSrc HD Full Movie", "1080p", "https://vidsrc.to/embed/movie/533535", isDirectVideo = false),
            StreamSource("2Embed Cinema Player", "1080p", "https://2embed.cc/embed/533535", isDirectVideo = false)
        )
    ),
    MediaItem(
        id = "hol_m2",
        title = "Dune: Part Two",
        type = "Movie",
        year = "2024",
        rating = "8.9",
        duration = "2h 46m",
        qualityBadge = "4K IMAX",
        genres = listOf("Sci-Fi", "Adventure", "Action"),
        overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
        posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
        provider = "9kMovies",
        tmdbId = 438631,
        sources = listOf(
            StreamSource("Official 4K Trailer (MPV)", "4K", "https://www.youtube.com/watch?v=Way9Dexny3w", isDirectVideo = true),
            StreamSource("VidSrc HD Full Movie", "1080p", "https://vidsrc.to/embed/movie/438631", isDirectVideo = false)
        )
    ),
    MediaItem(
        id = "hol_s1",
        title = "Stranger Things: Season 5",
        type = "TV Series",
        year = "2025",
        rating = "9.1",
        duration = "Season 1-5",
        qualityBadge = "4K Dolby",
        genres = listOf("Drama", "Fantasy", "Horror", "Mystery"),
        overview = "The final battle for Hawkins begins as Eleven and her friends venture deep into the Upside Down to confront Vecna and seal the rift forever.",
        posterUrl = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
        backdropUrl = "https://image.tmdb.org/t/p/original/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
        provider = "TheMoviesFlix",
        tmdbId = 66732,
        sources = listOf(
            StreamSource("Official 4K Trailer (MPV)", "4K", "https://www.youtube.com/watch?v=b9EkMc79ZSU", isDirectVideo = true),
            StreamSource("VidSrc Series Stream", "1080p", "https://vidsrc.to/embed/tv/66732", isDirectVideo = false)
        )
    ),
    // REAL LIVE TV CHANNELS (LIVE BROADCAST M3U8 STREAMS)
    MediaItem(
        id = "live_tv_1",
        title = "BTL TV HD (Bangladesh)",
        type = "Live TV",
        year = "LIVE",
        rating = "LIVE",
        duration = "24/7",
        qualityBadge = "1080p HD",
        genres = listOf("Live", "Bangla", "News", "Broadcast"),
        overview = "Live broadcast streaming directly from Bangladeshi satellite television network.",
        posterUrl = "https://yt3.googleusercontent.com/ytc/APkrFKZYgQTsDhkbaqmPRCPZIf0UgZqF1wa6aG2eo8ti=s176-c-k-c0x00ffffff-no-rj-mo",
        backdropUrl = "https://image.tmdb.org/t/p/original/b82qNpKeq5FvWkUu6O1E3x0x4fX.jpg",
        provider = "BDIX Live TV",
        sources = listOf(
            StreamSource("Live HLS Master Stream (MPV)", "1080p", "https://cdn.liveplustv.com/v1/live/btl-tv/master.m3u8", isDirectVideo = true)
        )
    ),
    MediaItem(
        id = "live_tv_2",
        title = "ACC Sports Network HD",
        type = "Live TV",
        year = "LIVE",
        rating = "LIVE",
        duration = "24/7",
        qualityBadge = "1080p 60FPS",
        genres = listOf("Live", "Sports", "Football", "Basketball"),
        overview = "24/7 live sports network broadcasting championship leagues, tournament matches, and sports news.",
        posterUrl = "https://i.imgur.com/V6Kaqha.png",
        backdropUrl = "https://image.tmdb.org/t/p/original/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
        provider = "Sports Live CDN",
        sources = listOf(
            StreamSource("Live Sports HLS Master (MPV)", "1080p", "https://raycom-accdn-firetv.amagi.tv/playlist.m3u8", isDirectVideo = true)
        )
    ),
    MediaItem(
        id = "live_tv_3",
        title = "30A Golf Sports HD",
        type = "Live TV",
        year = "LIVE",
        rating = "LIVE",
        duration = "24/7",
        qualityBadge = "720p HD",
        genres = listOf("Live", "Sports", "Golf", "Tournament"),
        overview = "Live golf coverage, championship highlights, and professional tournament broadcast feed.",
        posterUrl = "https://i.imgur.com/Lv53nh4.png",
        backdropUrl = "https://image.tmdb.org/t/p/original/xOMo8BRK7PfcJv9JCnx7s520DRq.jpg",
        provider = "Sports Live CDN",
        sources = listOf(
            StreamSource("Live HLS Stream (MPV)", "720p", "https://30a-tv.com/feeds/vidaa/golf.m3u8", isDirectVideo = true)
        )
    ),
    MediaItem(
        id = "live_tv_4",
        title = "Duronto TV Kids (Bangladesh)",
        type = "Live TV",
        year = "LIVE",
        rating = "LIVE",
        duration = "24/7",
        qualityBadge = "1080p HD",
        genres = listOf("Live", "Kids", "Bangla", "Animation"),
        overview = "Popular children's and family entertainment television channel in Bangladesh.",
        posterUrl = "https://i.imgur.com/gXsddRK.png",
        backdropUrl = "https://image.tmdb.org/t/p/original/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
        provider = "BDIX Live TV",
        sources = listOf(
            StreamSource("Live HLS Stream (MPV)", "1080p", "https://livetv.shamon-cable.net:8443/Duronto/index.m3u8", isDirectVideo = true)
        )
    )
)

// ==========================================
// MEDIA PLAYER LAUNCHER
// ==========================================
private fun playMediaWithPlayer(
    title: String,
    streamUrl: String,
    preferredPlayer: String = preferences.get(KeyPlayerBackend, "mpv"),
    hwdec: Boolean = preferences.getBoolean(KeyHwdec, true),
    onStatusMessage: (String) -> Unit
) {
    Thread {
        try {
            val env = mutableMapOf<String, String>()
            env.putAll(System.getenv())
            if (env["DISPLAY"].isNullOrBlank()) {
                env["DISPLAY"] = ":0.0"
            }
            val xauth = File(System.getProperty("user.home"), ".Xauthority")
            if (xauth.exists() && env["XAUTHORITY"].isNullOrBlank()) {
                env["XAUTHORITY"] = xauth.absolutePath
            }

            // Web embed check: if the stream is an embed URL requiring full browser DOM
            if (streamUrl.contains("/embed/")) {
                onStatusMessage("🌐 Opening '$title' Web Stream...")
                ProcessBuilder("xdg-open", streamUrl).start()
                return@Thread
            }

            val mpvPath = File("/usr/bin/mpv")
            val vlcPath = File("/usr/bin/vlc")

            val cmd = mutableListOf<String>()
            val useMpv = (preferredPlayer == "mpv" && mpvPath.exists()) || (!vlcPath.exists() && mpvPath.exists())
            val useVlc = preferredPlayer == "vlc" && vlcPath.exists()

            if (useMpv) {
                onStatusMessage("▶ Launching '$title' in MPV Player...")
                cmd.addAll(
                    listOf(
                        "/usr/bin/mpv",
                        "--force-window=yes",
                        "--title=NewStream: $title",
                        if (hwdec) "--hwdec=auto" else "--hwdec=no",
                        "--ytdl=yes",
                        "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        streamUrl
                    )
                )
            } else if (useVlc || vlcPath.exists()) {
                onStatusMessage("▶ Launching '$title' in VLC Player...")
                cmd.addAll(
                    listOf(
                        "/usr/bin/vlc",
                        "--meta-title=NewStream: $title",
                        streamUrl
                    )
                )
            } else {
                onStatusMessage("Opening '$title'...")
                cmd.addAll(listOf("xdg-open", streamUrl))
            }

            val pb = ProcessBuilder(cmd)
            pb.environment().putAll(env)
            pb.redirectErrorStream(true)
            val proc = pb.start()

            val reader = proc.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line.orEmpty()
                if (l.contains("403 Forbidden") || l.contains("Failed to open")) {
                    onStatusMessage("⚠️ Player stream error: $l")
                }
            }
            proc.waitFor()
        } catch (e: Exception) {
            onStatusMessage("⚠️ Playback launch error: ${e.message}")
        }
    }.start()
}

// ==========================================
// DOWNLOAD HANDLER (FFMPEG & HTTP STREAMING)
// ==========================================
private fun startDownloadItem(
    title: String,
    streamUrl: String,
    onStatusMessage: (String) -> Unit,
    onProgressUpdate: (Float) -> Unit,
    onFinished: (Boolean, File?) -> Unit
) {
    Thread {
        try {
            val downloadFolder = File(preferences.get(KeyDownloadDir, DefaultDownloadDirectory)).apply { mkdirs() }
            val sanitized = title.replace(Regex("[^a-zA-Z0-9.-]"), "_").trim('_')
            val destFile = File(downloadFolder, "$sanitized.mp4")

            onStatusMessage("⬇ Starting download: $title")

            val ffmpegPath = File("/usr/bin/ffmpeg")
            val ytDlpPath = File("/usr/bin/yt-dlp")

            if (streamUrl.contains("youtube.com") && ytDlpPath.exists()) {
                // Download YouTube video stream via yt-dlp
                val pb = ProcessBuilder(
                    "/usr/bin/yt-dlp",
                    "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                    "-o", destFile.absolutePath,
                    streamUrl
                )
                pb.redirectErrorStream(true)
                val proc = pb.start()
                proc.inputStream.bufferedReader().use { r -> while (r.readLine() != null) { onProgressUpdate(0.5f) } }
                val code = proc.waitFor()
                if (code == 0 && destFile.exists() && destFile.length() > 0) {
                    onProgressUpdate(1.0f)
                    onStatusMessage("✓ Download complete: ${destFile.name} (${formatBytes(destFile.length())})")
                    onFinished(true, destFile)
                    return@Thread
                }
            }

            if (ffmpegPath.exists()) {
                val pb = ProcessBuilder(
                    "/usr/bin/ffmpeg",
                    "-y",
                    "-i",
                    streamUrl,
                    "-c",
                    "copy",
                    destFile.absolutePath
                )
                pb.redirectErrorStream(true)
                val proc = pb.start()
                proc.inputStream.bufferedReader().use { r -> while (r.readLine() != null) { onProgressUpdate(0.5f) } }
                val code = proc.waitFor()
                if (code == 0 && destFile.exists() && destFile.length() > 0) {
                    onProgressUpdate(1.0f)
                    onStatusMessage("✓ Download complete: ${destFile.name} (${formatBytes(destFile.length())})")
                    onFinished(true, destFile)
                } else {
                    onStatusMessage("⚠️ Download failed for $title")
                    onFinished(false, null)
                }
            } else {
                val conn = URL(streamUrl).openConnection().apply {
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val totalBytes = conn.contentLengthLong
                var bytesReadTotal = 0L
                conn.getInputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesReadTotal += bytesRead
                            if (totalBytes > 0) {
                                onProgressUpdate((bytesReadTotal.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                onProgressUpdate(1.0f)
                onStatusMessage("✓ Download complete: ${destFile.name}")
                onFinished(true, destFile)
            }
        } catch (e: Exception) {
            onStatusMessage("⚠️ Download error: ${e.message}")
            onFinished(false, null)
        }
    }.start()
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun scanDownloadedFiles(folderPath: String): List<DownloadedFile> {
    val dir = File(folderPath)
    if (!dir.exists()) return emptyList()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return dir.listFiles { f ->
        f.isFile && (f.name.endsWith(".mp4", true) || f.name.endsWith(".mkv", true) || f.name.endsWith(".ts", true))
    }?.sortedByDescending { it.lastModified() }?.map { file ->
        DownloadedFile(
            name = file.name,
            sizeFormatted = formatBytes(file.length()),
            dateFormatted = dateFormat.format(Date(file.lastModified())),
            file = file
        )
    } ?: emptyList()
}

// Convert TMDB item into rich MediaItem with real streaming sources
private fun tmdbItemToMediaItem(r: TmdbResultItem, defaultType: String = "Movie"): MediaItem {
    val title = r.title ?: r.name ?: "Untitled"
    val isTv = r.name != null || r.media_type == "tv" || defaultType == "TV Series"
    val type = if (isTv) "TV Series" else "Movie"
    val date = r.release_date ?: r.first_air_date.orEmpty()
    val year = if (date.length >= 4) date.substring(0, 4) else "2024"
    val rating = String.format(Locale.US, "%.1f", r.vote_average ?: 7.5)
    val poster = if (!r.poster_path.isNullOrBlank()) "https://image.tmdb.org/t/p/w500${r.poster_path}" else "https://image.tmdb.org/t/p/w500/y9xvdW1o7a1BfXm4i0X6w0bWq7u.jpg"
    val backdrop = if (!r.backdrop_path.isNullOrBlank()) "https://image.tmdb.org/t/p/original${r.backdrop_path}" else poster

    val sources = mutableListOf<StreamSource>()
    if (isTv) {
        sources.add(StreamSource("VidSrc TV HD (1080p)", "1080p", "https://vidsrc.to/embed/tv/${r.id}", isDirectVideo = false))
        sources.add(StreamSource("VidSrc Pro Mirror", "1080p", "https://vidsrc.pm/embed/tv/${r.id}", isDirectVideo = false))
        sources.add(StreamSource("2Embed Cinema TV", "1080p", "https://2embed.cc/embed/tv/${r.id}/1/1", isDirectVideo = false))
    } else {
        sources.add(StreamSource("VidSrc Movie HD (1080p)", "1080p", "https://vidsrc.to/embed/movie/${r.id}", isDirectVideo = false))
        sources.add(StreamSource("VidSrc Pro Mirror", "1080p", "https://vidsrc.pm/embed/movie/${r.id}", isDirectVideo = false))
        sources.add(StreamSource("2Embed Cinema Player", "1080p", "https://2embed.cc/embed/${r.id}", isDirectVideo = false))
    }

    return MediaItem(
        id = "${type.lowercase()}_${r.id}",
        title = title,
        type = type,
        year = year,
        rating = rating,
        duration = if (isTv) "Episodes" else "2h 10m",
        qualityBadge = "4K UHD",
        genres = listOf(type, "CloudStream"),
        overview = r.overview ?: "No synopsis available.",
        posterUrl = poster,
        backdropUrl = backdrop,
        provider = "CloudStream TMDB",
        tmdbId = r.id,
        sources = sources
    )
}

// ==========================================
// MAIN COMPOSE ENTRY POINT
// ==========================================
fun main() = application {
    val windowState = rememberWindowState(width = 1260.dp, height = 820.dp)
    Window(
        onCloseRequest = ::exitApplication,
        title = "NewStream - Cinematic Media & Extension Center",
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
            NewStreamDesktopApp()
        }
    }
}

// ==========================================
// MAIN APPLICATION COMPOSABLE
// ==========================================
@Composable
private fun NewStreamDesktopApp() {
    var activeSection by remember { mutableStateOf(NavSection.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<MediaItem?>(null) }
    var statusNotification by remember { mutableStateOf<String?>(null) }

    // DYNAMIC MEDIA CATALOG (STARTS WITH CURATED BASE, EXPANDS DYNAMICALLY FROM PROVIDERS)
    val mediaCatalog = remember { mutableStateListOf<MediaItem>().apply { addAll(CuratedBaseCatalog) } }

    // DYNAMIC SEARCH RESULTS
    val dynamicSearchResults = remember { mutableStateListOf<MediaItem>() }
    var isSearching by remember { mutableStateOf(false) }

    // Active download tasks & local files
    val downloadTasks = remember { mutableStateListOf<DownloadTask>() }
    var downloadedFiles by remember { mutableStateOf(scanDownloadedFiles(preferences.get(KeyDownloadDir, DefaultDownloadDirectory))) }

    val watchlist = remember {
        mutableStateListOf<String>().apply {
            addAll(preferences.get(KeyWatchlist, "").split(",").filter(String::isNotBlank))
        }
    }

    val repositories = remember {
        mutableStateListOf<String>().apply {
            val saved = preferences.get(KeyRepositories, "")
            if (saved.isNotBlank()) {
                addAll(saved.split("\n").filter(String::isNotBlank))
            } else {
                val defaultRepo = "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json"
                add(defaultRepo)
                preferences.put(KeyRepositories, defaultRepo)
            }
        }
    }

    val installedPlugins = remember {
        mutableStateListOf<String>().apply {
            val saved = preferences.get(KeyInstalledPlugins, "")
            if (saved.isNotBlank()) {
                addAll(saved.split(",").filter(String::isNotBlank))
            }
        }
    }

    val providerLists = remember { mutableStateMapOf<String, List<Provider>>() }
    val scope = rememberCoroutineScope()

    // REFRESH COMMUNITY EXTENSION REPOSITORY
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
                statusNotification = "Updated repository: ${providers.size} provider(s) loaded"
            }.onFailure { err ->
                statusNotification = "Failed to load repo: ${err.message}"
            }
        }
    }

    // DYNAMIC PROVIDER FETCHING (REAL MOVIES & SERIES FROM TMDB / 1MOVIESFLIX / BDIX)
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                // 1. Fetch Trending Blockbuster Movies
                val trendingMoviesUrl = "https://api.themoviedb.org/3/trending/movie/week?api_key=$TMDB_API_KEY"
                val movieData = fetchJson<TmdbPageResponse>(trendingMoviesUrl)
                val newMovies = movieData.results.map { tmdbItemToMediaItem(it, "Movie") }

                // 2. Fetch Trending TV Series
                val trendingTvUrl = "https://api.themoviedb.org/3/trending/tv/week?api_key=$TMDB_API_KEY"
                val tvData = fetchJson<TmdbPageResponse>(trendingTvUrl)
                val newTvSeries = tvData.results.map { tmdbItemToMediaItem(it, "TV Series") }

                // 3. Fetch Popular Bangla Content
                val banglaUrl = "https://api.themoviedb.org/3/discover/movie?api_key=$TMDB_API_KEY&with_original_language=bn&sort_by=popularity.desc"
                val banglaData = fetchJson<TmdbPageResponse>(banglaUrl)
                val newBangla = banglaData.results.map { tmdbItemToMediaItem(it, "Movie") }

                withContext(Dispatchers.Main) {
                    // Merge dynamically fetched provider content without duplicates
                    val existingIds = mediaCatalog.map { it.id }.toSet()
                    val toAdd = (newMovies + newTvSeries + newBangla).filterNot { existingIds.contains(it.id) }
                    mediaCatalog.addAll(toAdd)
                }
            }
        }
    }

    // SEARCH PROVIDERS IN REAL-TIME WHEN QUERY CHANGES
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().length >= 2) {
            isSearching = true
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val encoded = URLEncoder.encode(searchQuery.trim(), "UTF-8")
                    val searchUrl = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&query=$encoded"
                    val resp = fetchJson<TmdbPageResponse>(searchUrl)
                    val results = resp.results
                        .filter { it.poster_path != null }
                        .map { tmdbItemToMediaItem(it) }

                    withContext(Dispatchers.Main) {
                        dynamicSearchResults.clear()
                        dynamicSearchResults.addAll(results)
                        isSearching = false
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) { isSearching = false }
                }
            }
        } else {
            dynamicSearchResults.clear()
            isSearching = false
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
        preferences.put(KeyWatchlist, watchlist.joinToString(","))
    }

    fun togglePluginInstall(name: String) {
        if (installedPlugins.contains(name)) {
            installedPlugins.remove(name)
            statusNotification = "Disabled provider: $name"
        } else {
            installedPlugins.add(name)
            statusNotification = "Installed provider: $name ✓"
        }
        preferences.put(KeyInstalledPlugins, installedPlugins.joinToString(","))
    }

    fun triggerDownload(media: MediaItem, source: StreamSource) {
        val downloadFolder = File(preferences.get(KeyDownloadDir, DefaultDownloadDirectory))
        val sanitized = media.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").trim('_')
        val targetFile = File(downloadFolder, "$sanitized.mp4")

        val task = DownloadTask(
            id = media.id + System.currentTimeMillis(),
            title = media.title,
            url = source.url,
            status = "Downloading",
            progress = 0.1f,
            destinationFile = targetFile
        )
        downloadTasks.add(task)
        statusNotification = "⬇ Download started: ${media.title}"

        startDownloadItem(
            title = media.title,
            streamUrl = source.url,
            onStatusMessage = { statusNotification = it },
            onProgressUpdate = { prog ->
                val index = downloadTasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    downloadTasks[index] = downloadTasks[index].copy(progress = prog)
                }
            },
            onFinished = { success, file ->
                val index = downloadTasks.indexOfFirst { it.id == task.id }
                if (index != -1) {
                    downloadTasks[index] = downloadTasks[index].copy(
                        status = if (success) "Completed" else "Failed",
                        progress = if (success) 1f else 0f
                    )
                }
                downloadedFiles = scanDownloadedFiles(preferences.get(KeyDownloadDir, DefaultDownloadDirectory))
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP HEADER BAR (BRAND + TABS + QUICK SEARCH + REPO PILL)
            TopAppHeader(
                activeSection = activeSection,
                onSectionSelect = {
                    activeSection = it
                    if (it != NavSection.Home) searchQuery = ""
                },
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                providerCount = providerLists.values.sumOf { it.size },
                onOpenRepoPage = { activeSection = NavSection.Extensions }
            )

            // BODY CONTENT BY SECTION
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (searchQuery.isNotBlank()) {
                    SearchResultsView(
                        query = searchQuery,
                        allMedia = mediaCatalog,
                        dynamicOnlineResults = dynamicSearchResults,
                        isLoading = isSearching,
                        providers = providerLists.values.flatten(),
                        onSelectMedia = { selectedMedia = it },
                        onPlayMedia = { item ->
                            val stream = item.sources.firstOrNull()?.url ?: ""
                            playMediaWithPlayer(item.title, stream) { statusNotification = it }
                        },
                        onDownloadMedia = { item ->
                            val stream = item.sources.firstOrNull() ?: return@SearchResultsView
                            triggerDownload(item, stream)
                        }
                    )
                } else {
                    when (activeSection) {
                        NavSection.Home -> NetflixHomeFeed(
                            catalog = mediaCatalog,
                            watchlist = watchlist,
                            onSelectMedia = { selectedMedia = it },
                            onPlayMedia = { item ->
                                val stream = item.sources.firstOrNull()?.url ?: ""
                                playMediaWithPlayer(item.title, stream) { statusNotification = it }
                            },
                            onDownloadMedia = { item ->
                                val stream = item.sources.firstOrNull() ?: return@NetflixHomeFeed
                                triggerDownload(item, stream)
                            },
                            onToggleWatchlist = ::toggleWatchlist
                        )
                        NavSection.Movies -> MediaGridView(
                            title = "Blockbuster Movies & Dual Audio (${mediaCatalog.count { it.type == "Movie" }})",
                            items = mediaCatalog.filter { it.type == "Movie" },
                            onSelectMedia = { selectedMedia = it },
                            onPlayMedia = { item ->
                                val stream = item.sources.firstOrNull()?.url ?: ""
                                playMediaWithPlayer(item.title, stream) { statusNotification = it }
                            },
                            onDownloadMedia = { item ->
                                val stream = item.sources.firstOrNull() ?: return@MediaGridView
                                triggerDownload(item, stream)
                            }
                        )
                        NavSection.Series -> MediaGridView(
                            title = "TV Series & Binge Shows (${mediaCatalog.count { it.type == "TV Series" }})",
                            items = mediaCatalog.filter { it.type == "TV Series" },
                            onSelectMedia = { selectedMedia = it },
                            onPlayMedia = { item ->
                                val stream = item.sources.firstOrNull()?.url ?: ""
                                playMediaWithPlayer(item.title, stream) { statusNotification = it }
                            },
                            onDownloadMedia = { item ->
                                val stream = item.sources.firstOrNull() ?: return@MediaGridView
                                triggerDownload(item, stream)
                            }
                        )
                        NavSection.Bdix -> MediaGridView(
                            title = "BDIX & Bangla Fast Servers 🇧🇩",
                            items = mediaCatalog.filter {
                                it.provider.contains("BDIX", true) ||
                                it.provider.contains("Bangla", true) ||
                                it.id.startsWith("bd_")
                            },
                            onSelectMedia = { selectedMedia = it },
                            onPlayMedia = { item ->
                                val stream = item.sources.firstOrNull()?.url ?: ""
                                playMediaWithPlayer(item.title, stream) { statusNotification = it }
                            },
                            onDownloadMedia = { item ->
                                val stream = item.sources.firstOrNull() ?: return@MediaGridView
                                triggerDownload(item, stream)
                            }
                        )
                        NavSection.LiveTv -> MediaGridView(
                            title = "Live Broadcast & Sports Channels 🔴",
                            items = mediaCatalog.filter { it.type == "Live TV" },
                            onSelectMedia = { selectedMedia = it },
                            onPlayMedia = { item ->
                                val stream = item.sources.firstOrNull()?.url ?: ""
                                playMediaWithPlayer(item.title, stream) { statusNotification = it }
                            },
                            onDownloadMedia = { item ->
                                val stream = item.sources.firstOrNull() ?: return@MediaGridView
                                triggerDownload(item, stream)
                            }
                        )
                        NavSection.MyList -> {
                            val savedItems = mediaCatalog.filter { watchlist.contains(it.id) }
                            if (savedItems.isEmpty()) {
                                EmptyStateView(
                                    title = "Your Watchlist is empty",
                                    subtitle = "Browse movies or shows and click '+ Watchlist' to save them here for quick access."
                                )
                            } else {
                                MediaGridView(
                                    title = "My Watchlist ⭐",
                                    items = savedItems,
                                    onSelectMedia = { selectedMedia = it },
                                    onPlayMedia = { item ->
                                        val stream = item.sources.firstOrNull()?.url ?: ""
                                        playMediaWithPlayer(item.title, stream) { statusNotification = it }
                                    },
                                    onDownloadMedia = { item ->
                                        val stream = item.sources.firstOrNull() ?: return@MediaGridView
                                        triggerDownload(item, stream)
                                    }
                                )
                            }
                        }
                        NavSection.Downloads -> DownloadsPageView(
                            activeTasks = downloadTasks,
                            downloadedFiles = downloadedFiles,
                            onRefreshFiles = {
                                downloadedFiles = scanDownloadedFiles(preferences.get(KeyDownloadDir, DefaultDownloadDirectory))
                            },
                            onPlayFile = { file, player ->
                                playMediaWithPlayer(file.nameWithoutExtension, file.absolutePath, preferredPlayer = player) {
                                    statusNotification = it
                                }
                            },
                            onDeleteFile = { file ->
                                file.delete()
                                downloadedFiles = scanDownloadedFiles(preferences.get(KeyDownloadDir, DefaultDownloadDirectory))
                                statusNotification = "Deleted: ${file.name}"
                            },
                            onCancelTask = { task ->
                                downloadTasks.remove(task)
                                statusNotification = "Cancelled download: ${task.title}"
                            }
                        )
                        NavSection.Extensions -> ExtensionsPageView(
                            repositories = repositories,
                            providerLists = providerLists,
                            installedPlugins = installedPlugins,
                            onRefreshRepo = ::refreshRepo,
                            onToggleInstall = ::togglePluginInstall,
                            onNotification = { statusNotification = it }
                        )
                        NavSection.Settings -> MobileStyleSettingsPageView(
                            onNotification = { statusNotification = it },
                            onNavigateToExtensions = { activeSection = NavSection.Extensions }
                        )
                    }
                }
            }
        }

        // FLOATING STATUS TOAST
        statusNotification?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(4000)
                statusNotification = null
            }
            Card(
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                colors = CardDefaults.cardColors(containerColor = NetflixDarkRed),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    text = msg,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }

        // QUICK-VIEW DETAIL MODAL WITH REAL STREAMS
        selectedMedia?.let { media ->
            NetflixDetailModal(
                media = media,
                isSaved = watchlist.contains(media.id),
                onDismiss = { selectedMedia = null },
                onToggleWatchlist = { toggleWatchlist(media.id) },
                onPlay = { source, player ->
                    playMediaWithPlayer(media.title, source.url, preferredPlayer = player) {
                        statusNotification = it
                    }
                },
                onDownload = { source ->
                    triggerDownload(media, source)
                }
            )
        }
    }
}

// ==========================================
// TOP HEADER BAR (NETFLIX STYLE)
// ==========================================
@Composable
private fun TopAppHeader(
    activeSection: NavSection,
    onSectionSelect: (NavSection) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    providerCount: Int,
    onOpenRepoPage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(70.dp),
        color = BackgroundBlack.copy(alpha = 0.95f),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // BRAND LOGO & NAV TABS
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // LOGO
                Row(
                    modifier = Modifier.clickable { onSectionSelect(NavSection.Home) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NEW",
                        color = NetflixRed,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                    Text(
                        text = "STREAM",
                        color = TextWhite,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // TABS
                NavSection.values().forEach { section ->
                    val isSelected = activeSection == section && searchQuery.isBlank()
                    Surface(
                        color = if (isSelected) SurfaceDark else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onSectionSelect(section) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(section.icon, fontSize = 13.sp)
                            Text(
                                text = section.title,
                                color = if (isSelected) TextWhite else TextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // RIGHT BAR: REPO STATUS PILL + SEARCH
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            text = if (providerCount > 0) "BDIX ($providerCount)" else "Plugins",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.width(260.dp).height(46.dp),
                    placeholder = { Text("Search movies, series...", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NetflixRed,
                        unfocusedBorderColor = CardDark,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    ),
                    shape = RoundedCornerShape(23.dp)
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
    onDownloadMedia: (MediaItem) -> Unit,
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
                onDownload = { onDownloadMedia(heroItem) },
                onMoreInfo = { onSelectMedia(heroItem) },
                onToggleWatchlist = { onToggleWatchlist(heroItem.id) }
            )
        }

        // 2. TRENDING NOW (TOP 10 TODAY)
        item {
            ContentRail(
                title = "🔥 Trending Now (Top 10 Worldwide)",
                items = catalog.take(10),
                showRank = true,
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia,
                onDownloadClick = onDownloadMedia
            )
        }

        // 3. BDIX & BANGLA FAST SERVERS
        item {
            ContentRail(
                title = "🇧🇩 BDIX & Bangla Blockbusters",
                items = catalog.filter {
                    it.provider.contains("BDIX", true) ||
                    it.provider.contains("Bangla", true) ||
                    it.id.startsWith("bd_")
                },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia,
                onDownloadClick = onDownloadMedia
            )
        }

        // 4. BLOCKBUSTER MOVIES
        item {
            ContentRail(
                title = "🎬 Blockbuster Movies & Dual Audio",
                items = catalog.filter { it.type == "Movie" },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia,
                onDownloadClick = onDownloadMedia
            )
        }

        // 5. TV SERIES & WEB SHOWS
        item {
            ContentRail(
                title = "📺 Binge-Worthy TV Series & Web Shows",
                items = catalog.filter { it.type == "TV Series" },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia,
                onDownloadClick = onDownloadMedia
            )
        }

        // 6. LIVE TV CHANNELS
        item {
            ContentRail(
                title = "🔴 Live Sports & TV Channels",
                items = catalog.filter { it.type == "Live TV" },
                onItemClick = onSelectMedia,
                onPlayClick = onPlayMedia,
                onDownloadClick = onDownloadMedia
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
    onDownload: () -> Unit,
    onMoreInfo: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
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
                        colors = listOf(
                            Color.Transparent,
                            BackgroundBlack.copy(alpha = 0.4f),
                            BackgroundBlack.copy(alpha = 0.85f),
                            BackgroundBlack
                        ),
                        startY = 120f
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
                            BackgroundBlack.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        endX = 750f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 48.dp, vertical = 32.dp)
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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

            Text(
                text = media.title,
                color = TextWhite,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 46.sp
            )

            Text(
                text = media.overview,
                color = TextWhite.copy(alpha = 0.85f),
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text("▶  Play Now", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                }

                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("⬇  Download", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                }

                Button(
                    onClick = onMoreInfo,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text("ℹ  More Info", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextWhite)
                }

                OutlinedButton(
                    onClick = onToggleWatchlist,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Text(if (isSaved) "✓ Saved" else "+ Watchlist", fontSize = 14.sp)
                }
            }
        }
    }
}

// ==========================================
// CONTENT RAIL (HORIZONTAL ROW)
// ==========================================
@Composable
private fun ContentRail(
    title: String,
    items: List<MediaItem>,
    showRank: Boolean = false,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit,
    onDownloadClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return

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
                    onPlay = { onPlayClick(item) },
                    onDownload = { onDownloadClick(item) }
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
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    Row(verticalAlignment = Alignment.Bottom) {
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
                .width(190.dp)
                .height(280.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp))
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = CardDark)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // BOTTOM GRADIENT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, BackgroundBlack.copy(alpha = 0.95f))
                            )
                        )
                )

                // QUALITY BADGE
                Surface(
                    color = BackgroundBlack.copy(alpha = 0.8f),
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

                // BOTTOM TEXT & ACTIONS
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = SurfaceDark,
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp).clickable { onDownload() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⬇", color = TextWhite, fontSize = 10.sp)
                                }
                            }
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
}

// ==========================================
// GRID VIEW
// ==========================================
@Composable
private fun MediaGridView(
    title: String,
    items: List<MediaItem>,
    onSelectMedia: (MediaItem) -> Unit,
    onPlayMedia: (MediaItem) -> Unit,
    onDownloadMedia: (MediaItem) -> Unit
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
                            onPlay = { onPlayMedia(item) },
                            onDownload = { onDownloadMedia(item) }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SEARCH RESULTS VIEW (LOCAL + LIVE TMDB/PROVIDER SEARCH)
// ==========================================
@Composable
private fun SearchResultsView(
    query: String,
    allMedia: List<MediaItem>,
    dynamicOnlineResults: List<MediaItem>,
    isLoading: Boolean,
    providers: List<Provider>,
    onSelectMedia: (MediaItem) -> Unit,
    onPlayMedia: (MediaItem) -> Unit,
    onDownloadMedia: (MediaItem) -> Unit
) {
    val localMatches = allMedia.filter {
        it.title.contains(query, true) ||
            it.overview.contains(query, true) ||
            it.genres.any { g -> g.contains(query, true) }
    }

    val combinedMedia = (localMatches + dynamicOnlineResults).distinctBy { it.id }

    val matchedProviders = providers.filter {
        it.name.contains(query, true) ||
            it.description.orEmpty().contains(query, true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Search Results for \"$query\"", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NetflixRed, strokeWidth = 2.dp)
                    Text("Searching online providers...", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (combinedMedia.isEmpty() && matchedProviders.isEmpty() && !isLoading) {
            EmptyStateView(
                title = "No results found for \"$query\"",
                subtitle = "We couldn't find any media or BDIX providers matching that keyword. Try another title."
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (combinedMedia.isNotEmpty()) {
                    item {
                        Text("Movies & Shows (${combinedMedia.size})", color = GoldAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    val chunked = combinedMedia.chunked(5)
                    items(chunked) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowItems.forEach { item ->
                                NetflixCard(
                                    media = item,
                                    onClick = { onSelectMedia(item) },
                                    onPlay = { onPlayMedia(item) },
                                    onDownload = { onDownloadMedia(item) }
                                )
                            }
                        }
                    }
                }

                if (matchedProviders.isNotEmpty()) {
                    item {
                        Text("BDIX Extension Providers (${matchedProviders.size})", color = PrimeBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                                    Text(provider.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    provider.description?.let { Text(it, color = TextMuted, fontSize = 12.sp) }
                                }
                                Surface(color = GreenBadge.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Text("BDIX Active", color = GreenBadge, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
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
// DOWNLOADS PAGE VIEW (OFFLINE LIBRARY)
// ==========================================
@Composable
private fun DownloadsPageView(
    activeTasks: List<DownloadTask>,
    downloadedFiles: List<DownloadedFile>,
    onRefreshFiles: () -> Unit,
    onPlayFile: (File, String) -> Unit,
    onDeleteFile: (File) -> Unit,
    onCancelTask: (DownloadTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Downloads & Offline Library 📥", color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("Saved media at ${preferences.get(KeyDownloadDir, DefaultDownloadDirectory)}", color = TextMuted, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val dir = File(preferences.get(KeyDownloadDir, DefaultDownloadDirectory)).apply { mkdirs() }
                            ProcessBuilder("xdg-open", dir.absolutePath).start()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("📂 Open Folder")
                    }
                    Button(
                        onClick = onRefreshFiles,
                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("🔄 Refresh")
                    }
                }
            }
        }

        // ACTIVE DOWNLOADS SECTION
        if (activeTasks.isNotEmpty()) {
            item {
                Text("Active Downloads (${activeTasks.size})", color = GoldAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            items(activeTasks) { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(task.title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(task.status, color = if (task.status == "Completed") GreenBadge else GoldAccent, fontSize = 12.sp)
                        }
                        LinearProgressIndicator(
                            progress = { task.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = NetflixRed,
                            trackColor = CardDark
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${(task.progress * 100).toInt()}% • Saving to ${task.destinationFile.name}", color = TextMuted, fontSize = 11.sp)
                            if (task.status != "Completed") {
                                TextButton(onClick = { onCancelTask(task) }) {
                                    Text("Cancel", color = NetflixRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // COMPLETED DOWNLOADS SECTION
        item {
            Text("Downloaded Media (${downloadedFiles.size})", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (downloadedFiles.isEmpty()) {
            item {
                EmptyStateView(
                    title = "No downloaded media yet",
                    subtitle = "Click '⬇ Download' on any movie or series to save it locally for high-speed offline playback."
                )
            }
        } else {
            items(downloadedFiles) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Surface(color = NetflixRed, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(44.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🎬", fontSize = 20.sp)
                                }
                            }
                            Column {
                                Text(item.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${item.sizeFormatted} • ${item.dateFormatted}", color = TextMuted, fontSize = 12.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onPlayFile(item.file, "mpv") },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("▶ Play (MPV)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onPlayFile(item.file, "vlc") },
                                colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("▶ Play (VLC)", fontSize = 12.sp)
                            }
                            IconButton(onClick = { onDeleteFile(item.file) }) {
                                Text("🗑", color = NetflixRed, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// EXTENSIONS & REPOSITORIES PAGE
// ==========================================
@Composable
private fun ExtensionsPageView(
    repositories: MutableList<String>,
    providerLists: MutableMap<String, List<Provider>>,
    installedPlugins: MutableList<String>,
    onRefreshRepo: (String) -> Unit,
    onToggleInstall: (String) -> Unit,
    onNotification: (String) -> Unit
) {
    var newRepoUrl by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("Extension & Provider Manager 🧩", color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Add community extension repository manifests to fetch BDIX, Live TV, and on-demand video providers.", color = TextMuted)
        }

        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SurfaceDark,
                contentColor = TextWhite
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Repositories (${repositories.size})", fontWeight = FontWeight.Bold) }
                )
                val totalProviders = providerLists.values.sumOf { it.size }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Available Providers ($totalProviders)", fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (selectedTab == 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Add Custom Repository URL", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newRepoUrl,
                                onValueChange = { newRepoUrl = it },
                                placeholder = { Text("https://example.com/repo.json", color = TextMuted) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = NetflixRed,
                                    unfocusedBorderColor = CardDark,
                                    focusedContainerColor = CardDark,
                                    unfocusedContainerColor = CardDark
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = {
                                    val trimmed = newRepoUrl.trim()
                                    if (trimmed.startsWith("http")) {
                                        if (!repositories.contains(trimmed)) {
                                            repositories.add(trimmed)
                                            preferences.put(KeyRepositories, repositories.joinToString("\n"))
                                            onRefreshRepo(trimmed)
                                            newRepoUrl = ""
                                            onNotification("Added repository!")
                                        } else {
                                            onNotification("Repository already exists")
                                        }
                                    } else {
                                        onNotification("Please enter a valid HTTP/HTTPS URL")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+ Add Repository")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recommended:", color = TextMuted, fontSize = 12.sp)
                            SuggestionChip(
                                onClick = {
                                    val r = "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json"
                                    if (!repositories.contains(r)) {
                                        repositories.add(r)
                                        preferences.put(KeyRepositories, repositories.joinToString("\n"))
                                        onRefreshRepo(r)
                                        onNotification("Added Redowan BDIX Repo!")
                                    }
                                },
                                label = { Text("🇧🇩 Redowan's BDIX Repo", fontSize = 11.sp) }
                            )
                        }
                    }
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
                                TextButton(onClick = { onRefreshRepo(repo) }) {
                                    Text("🔄 Refresh", color = PrimeBlue)
                                }
                                TextButton(onClick = {
                                    repositories.remove(repo)
                                    providerLists.remove(repo)
                                    preferences.put(KeyRepositories, repositories.joinToString("\n"))
                                    onNotification("Removed repository")
                                }) {
                                    Text("🗑 Remove", color = NetflixRed)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val allProviders = providerLists.values.flatten()
            if (allProviders.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "No Providers Loaded",
                        subtitle = "Add a repository in the 'Repositories' tab or click Refresh to fetch available plugins."
                    )
                }
            } else {
                items(allProviders) { provider ->
                    val isInstalled = installedPlugins.contains(provider.name)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(provider.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Surface(color = CardDark, shape = RoundedCornerShape(4.dp)) {
                                        Text("v${provider.version}", color = GoldAccent, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    provider.language?.let { lang ->
                                        Surface(color = PrimeBlue.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                            Text(lang.uppercase(), color = PrimeBlue, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    if (provider.authors.isNotEmpty()) {
                                        Text("by ${provider.authors.joinToString()}", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                                provider.description?.let {
                                    Text(it, color = TextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                provider.tvTypes?.let { types ->
                                    Text("Types: ${types.joinToString()}", color = TextMuted.copy(alpha = 0.7f), fontSize = 11.sp)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onToggleInstall(provider.name) },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isInstalled) GreenBadge else NetflixRed),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(if (isInstalled) "✓ Active" else "⬇ Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                if (provider.url.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            Thread {
                                                try {
                                                    val pluginDir = File(System.getProperty("user.home"), ".config/newstream/plugins").apply { mkdirs() }
                                                    val pluginFile = File(pluginDir, "${provider.name}.cs3")
                                                    URL(provider.url).openStream().use { inp ->
                                                        FileOutputStream(pluginFile).use { out -> inp.copyTo(out) }
                                                    }
                                                    onNotification("Saved ${pluginFile.name} to ${pluginDir.absolutePath}")
                                                } catch (e: Exception) {
                                                    onNotification("Error saving plugin: ${e.message}")
                                                }
                                            }.start()
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("💾 .cs3", fontSize = 12.sp)
                                    }
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
// MOBILE-STYLE SETTINGS PAGE VIEW
// ==========================================
@Composable
private fun MobileStyleSettingsPageView(
    onNotification: (String) -> Unit,
    onNavigateToExtensions: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Player") }
    val categories = listOf("General", "Player", "Providers", "Downloads", "Updates & About")

    var playerBackend by remember { mutableStateOf(preferences.get(KeyPlayerBackend, "mpv")) }
    var hwdecEnabled by remember { mutableStateOf(preferences.getBoolean(KeyHwdec, true)) }
    var subtitleLang by remember { mutableStateOf(preferences.get(KeySubtitleLang, "English")) }
    var subtitleSize by remember { mutableStateOf(preferences.get(KeySubtitleSize, "Normal")) }
    var autoplayNext by remember { mutableStateOf(preferences.getBoolean(KeyAutoplayNext, true)) }
    var dohProvider by remember { mutableStateOf(preferences.get(KeyDohProvider, "Cloudflare (1.1.1.1)")) }
    var nsfwFilter by remember { mutableStateOf(preferences.getBoolean(KeyNsfwFilter, false)) }
    var appLanguage by remember { mutableStateOf(preferences.get(KeyAppLanguage, "English")) }
    var downloadDir by remember { mutableStateOf(preferences.get(KeyDownloadDir, DefaultDownloadDirectory)) }
    var downloadQuality by remember { mutableStateOf(preferences.get(KeyDownloadQuality, "1080p Full HD")) }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Card(
            modifier = Modifier.width(240.dp).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SETTINGS",
                    color = NetflixRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )

                categories.forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        color = if (isSelected) NetflixRed else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { selectedCategory = cat }
                    ) {
                        val icon = when (cat) {
                            "General" -> "📱"
                            "Player" -> "🎬"
                            "Providers" -> "🧩"
                            "Downloads" -> "📥"
                            else -> "ℹ️"
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(icon, fontSize = 16.sp)
                            Text(cat, color = TextWhite, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(24.dp))

        Card(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (selectedCategory) {
                    "General" -> {
                        item {
                            Text("General Settings 📱", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Configure application language, UI behavior, and theme.", color = TextMuted, fontSize = 13.sp)
                        }
                        item {
                            SettingsSectionCard(title = "App Language") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("English", "বাংলা (Bengali)", "Hindi").forEach { lang ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                appLanguage = lang
                                                preferences.put(KeyAppLanguage, lang)
                                                onNotification("Language set to $lang")
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = appLanguage.startsWith(lang.substring(0, 2)),
                                                onClick = {
                                                    appLanguage = lang
                                                    preferences.put(KeyAppLanguage, lang)
                                                }
                                            )
                                            Text(lang, color = TextWhite, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Cache & Storage") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Clear Image & Metadata Cache", color = TextWhite, fontSize = 14.sp)
                                        Text("Frees disk space used by poster thumbnails", color = TextMuted, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { onNotification("Cache cleaned successfully!") },
                                        colors = ButtonDefaults.buttonColors(containerColor = CardDark)
                                    ) {
                                        Text("Clear Cache")
                                    }
                                }
                            }
                        }
                    }

                    "Player" -> {
                        item {
                            Text("Player Settings 🎬", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Select video playback engine, hardware decoding, and subtitle preferences.", color = TextMuted, fontSize = 13.sp)
                        }
                        item {
                            SettingsSectionCard(title = "Default Video Player") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        Triple("mpv", "MPV Player (Hardware Accelerated - Recommended)", "High performance GPU playback with yt-dlp & libplacebo"),
                                        Triple("vlc", "VLC Media Player", "Cross-platform multimedia player"),
                                        Triple("default", "System Default (xdg-open)", "Opens system default media association")
                                    ).forEach { (id, title, desc) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                playerBackend = id
                                                preferences.put(KeyPlayerBackend, id)
                                                onNotification("Player engine set to $title")
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = playerBackend == id, onClick = {
                                                playerBackend = id
                                                preferences.put(KeyPlayerBackend, id)
                                            })
                                            Column {
                                                Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(desc, color = TextMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Hardware Acceleration (GPU)") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Hardware Video Decoding (--hwdec=auto)", color = TextWhite, fontSize = 14.sp)
                                        Text("Uses GPU (VA-API / NVDEC) for smooth 4K and 1080p playback with zero CPU load", color = TextMuted, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = hwdecEnabled,
                                        onCheckedChange = {
                                            hwdecEnabled = it
                                            preferences.putBoolean(KeyHwdec, it)
                                            onNotification("Hardware decoding: ${if (it) "Enabled" else "Disabled"}")
                                        }
                                    )
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Subtitle Preferences") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Preferred Subtitle Language", color = TextMuted, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        listOf("English", "Bengali", "None").forEach { lang ->
                                            FilterChip(
                                                selected = subtitleLang == lang,
                                                onClick = {
                                                    subtitleLang = lang
                                                    preferences.put(KeySubtitleLang, lang)
                                                },
                                                label = { Text(lang) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Player Diagnostics") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Test Live Stream Playback Now", color = TextWhite, fontSize = 14.sp)
                                        Text("Launches live broadcast HD stream in MPV to confirm video engine", color = TextMuted, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = {
                                            playMediaWithPlayer("BTL TV HD Live", "https://cdn.liveplustv.com/v1/live/btl-tv/master.m3u8") {
                                                onNotification(it)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                                    ) {
                                        Text("▶ Test Launch")
                                    }
                                }
                            }
                        }
                    }

                    "Providers" -> {
                        item {
                            Text("Providers & Network Settings 🧩", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Configure DNS-over-HTTPS (DoH), filters, and extension repositories.", color = TextMuted, fontSize = 13.sp)
                        }
                        item {
                            SettingsSectionCard(title = "DNS over HTTPS (DoH)") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(
                                        "Cloudflare (1.1.1.1)" to "Fast secure DNS that bypasses ISP blocking",
                                        "Google (8.8.8.8)" to "Google Public Anycast DNS",
                                        "AdGuard DNS" to "Blocks advertising and tracking domains",
                                        "Off" to "Standard operating system DNS"
                                    ).forEach { (name, desc) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                dohProvider = name
                                                preferences.put(KeyDohProvider, name)
                                                onNotification("DoH set to $name")
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = dohProvider == name, onClick = {
                                                dohProvider = name
                                                preferences.put(KeyDohProvider, name)
                                            })
                                            Column {
                                                Text(name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(desc, color = TextMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Content Filters") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Safe Mode (NSFW Filter)", color = TextWhite, fontSize = 14.sp)
                                        Text("Hide adult content and 18+ provider plugins", color = TextMuted, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = nsfwFilter,
                                        onCheckedChange = {
                                            nsfwFilter = it
                                            preferences.putBoolean(KeyNsfwFilter, it)
                                            onNotification("NSFW filter: ${if (it) "Enabled" else "Disabled"}")
                                        }
                                    )
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = onNavigateToExtensions,
                                colors = ButtonDefaults.buttonColors(containerColor = NetflixRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Go to Full Extension Manager ➜", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    "Downloads" -> {
                        item {
                            Text("Download Settings 📥", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Configure local storage directory and video download quality.", color = TextMuted, fontSize = 13.sp)
                        }
                        item {
                            SettingsSectionCard(title = "Download Storage Folder") {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = downloadDir,
                                        onValueChange = {
                                            downloadDir = it
                                            preferences.put(KeyDownloadDir, it)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextWhite,
                                            unfocusedTextColor = TextWhite,
                                            focusedBorderColor = NetflixRed,
                                            unfocusedBorderColor = CardDark
                                        )
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                File(downloadDir).mkdirs()
                                                onNotification("Folder saved: $downloadDir")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                                        ) {
                                            Text("Save Location")
                                        }
                                        Button(
                                            onClick = {
                                                val dir = File(downloadDir).apply { mkdirs() }
                                                ProcessBuilder("xdg-open", dir.absolutePath).start()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CardDark)
                                        ) {
                                            Text("📂 Open Folder")
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SettingsSectionCard(title = "Download Quality Preference") {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("1080p Full HD", "720p HD", "Highest Available").forEach { q ->
                                        FilterChip(
                                            selected = downloadQuality == q,
                                            onClick = {
                                                downloadQuality = q
                                                preferences.put(KeyDownloadQuality, q)
                                            },
                                            label = { Text(q) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    "Updates & About" -> {
                        item {
                            Text("Updates & System Info ℹ️", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        item {
                            SettingsSectionCard(title = "NewStream Desktop") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Version: v1.0.3 (Linux x86_64 Debian/Kali)", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Framework: Jetpack Compose Multiplatform 1.8.0 • Kotlin 2.1.20 • JDK 21", color = TextMuted, fontSize = 12.sp)
                                    Text("Player Backend: MPV (/usr/bin/mpv) • VLC (/usr/bin/vlc) • FFmpeg (/usr/bin/ffmpeg)", color = TextMuted, fontSize = 12.sp)
                                    Text("GitHub: https://github.com/resanul/NewStream", color = PrimeBlue, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = { onNotification("NewStream is already up to date (v1.0.3) ✓") },
                                        colors = ButtonDefaults.buttonColors(containerColor = NetflixRed)
                                    ) {
                                        Text("Check for Updates")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            content()
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
    onPlay: (StreamSource, String) -> Unit,
    onDownload: (StreamSource) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(780.dp).heightIn(max = 700.dp),
            colors = CardDefaults.cardColors(containerColor = BackgroundBlack),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(NetflixRed, Color.Transparent)))
        ) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                // BACKDROP BANNER
                Box(modifier = Modifier.fillMaxWidth().height(290.dp)) {
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
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Surface(color = BackgroundBlack.copy(alpha = 0.7f), shape = CircleShape) {
                            Text("✕", color = TextWhite, modifier = Modifier.padding(8.dp), fontWeight = FontWeight.Bold)
                        }
                    }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        media.genres.forEach { genre ->
                            Surface(color = CardDark, shape = RoundedCornerShape(12.dp)) {
                                Text(genre, color = TextWhite, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }

                    Text(media.overview, color = TextWhite.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)

                    HorizontalDivider(color = CardDark)

                    Text("Available Streaming Sources & Providers:", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    media.sources.forEach { source ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(color = if (source.isDirectVideo) NetflixRed else PrimeBlue, shape = CircleShape, modifier = Modifier.size(32.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(if (source.isDirectVideo) "▶" else "🌐", color = TextWhite, fontSize = 12.sp)
                                        }
                                    }
                                    Column {
                                        Text(source.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Provider: ${media.provider} • Quality: ${source.quality}", color = TextMuted, fontSize = 11.sp)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            onPlay(source, "mpv")
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (source.isDirectVideo) NetflixRed else PrimeBlue),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(if (source.isDirectVideo) "▶ Watch in MPV" else "🌐 Watch Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (source.isDirectVideo) {
                                        Button(
                                            onClick = {
                                                onPlay(source, "vlc")
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("VLC", fontSize = 12.sp)
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            onDownload(source)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("⬇ Download", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onToggleWatchlist) {
                            Text(if (isSaved) "✓ Saved in Watchlist" else "+ Add to Watchlist", color = GoldAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// EMPTY STATE COMPONENT
// ==========================================
@Composable
private fun EmptyStateView(title: String, subtitle: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextMuted, fontSize = 13.sp)
        }
    }
}

// ==========================================
// UTILITIES (URL RESOLVER & JSON FETCH)
// ==========================================
private fun resolveUrl(baseUrl: String, childUrl: String): String {
    return URI(baseUrl).resolve(childUrl).toString()
}

private inline fun <reified T> fetchJson(url: String): T {
    val connection = URL(url).openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    }
    val response = connection.getInputStream().bufferedReader().use { it.readText() }
    return jsonSerializer.decodeFromString(serializer(), response)
}
