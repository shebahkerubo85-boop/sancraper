package ani.sanin.universal.extractor

import ani.sanin.universal.model.ResolvedVideo
import ani.sanin.universal.model.VideoType
import ani.sanin.universal.network.HttpFetcher
import ani.sanin.universal.util.EpisodeParser
import ani.sanin.universal.util.QualityParser
import ani.sanin.universal.util.UrlUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * A media reference found on a page, with the confidence that it came from a real player slot
 * rather than from a stray URL in an ad or a comment block.
 */
data class MediaRef(val url: String, val score: Double, val kind: String)

/**
 * Collects media references out of raw HTML.
 *
 * The previous implementation only looked at `a[href]`, `iframe[src]` and a raw regex sweep, and it
 * returned everything that looked like media. That missed the structured declarations modern sites
 * use (JSON-LD `VideoObject`, `og:video`, `<source type=...>`) and the inline JSON that AJAX players
 * embed, while accepting adverts. This scanner reads the structured forms first, then falls back
 * to the sweep, and filters adverts throughout.
 */
object MediaRefs {

    private val PLAYER_SLOTS = "video[src],video[poster],source[src],source[data-src],iframe[src],iframe[data-src],[data-video-url],[data-video],[data-player-url],[data-src]"

    private val META_KEYS = listOf(
        "og:video", "og:video:url", "og:video:secure_url", "og:video:iframe_url",
        "twitter:player:stream", "twitter:player:iframe", "video", "m3u8", "hls"
    )

    /** Inline-JSON keys that players use to hand the stream to their own loader. */
    private val JSON_KEYS = listOf("file", "src", "source", "sources", "hls", "dash", "playlist", "master", "url", "embed_url", "video_url", "stream")

    fun scan(html: String, base: String, episode: Int?): List<MediaRef> {
        val out = LinkedHashMap<String, MediaRef>()
        fun add(raw: String?, score: Double, kind: String) {
            if (raw.isNullOrBlank()) return
            val cleaned = UrlUtil.unescapeHtml(UrlUtil.canonical(raw))
            val absolute = UrlUtil.resolve(base, cleaned) ?: return
            if (!absolute.startsWith("http", true)) return
            if (UrlUtil.mediaType(absolute) == null) return
            if (AdFilter.isAd(absolute)) return
            val key = UrlUtil.resourceKey(absolute)
            val existing = out[key]
            if (existing == null || score > existing.score) {
                out[key] = MediaRef(absolute, score, kind)
            }
        }

        runCatching {
            val d = Jsoup.parse(html, base)

            // 1. Real player slots.
            d.select(PLAYER_SLOTS).forEach { el ->
                val tag = el.tagName().lowercase()
                val score = when (tag) {
                    "video" -> 0.95
                    "source" -> 0.93
                    "iframe" -> 0.9
                    else -> 0.7
                }
                val raw = el.attr("src").ifBlank { el.attr("data-src") }
                    .ifBlank { el.attr("data-video-url") }.ifBlank { el.attr("data-video") }
                    .ifBlank { el.attr("data-player-url") }
                add(raw, score, tag)
            }

            // 2. Structured metadata.
            META_KEYS.forEach { key ->
                d.select("meta[property=$key],meta[name=$key]").forEach { m ->
                    add(m.attr("content"), 0.88, "meta:$key")
                }
            }

            // 3. JSON-LD.
            d.select("script[type=application/ld+json]").forEach { script ->
                val raw = script.data().trim()
                if (raw.isBlank()) return@forEach
                runCatching { json.parseToJsonElement(raw) }.getOrNull()?.let { collectJson(it, base, ::add) }
            }

            // 4. Inline player JSON inside ordinary scripts.
            d.select("script").forEach { script ->
                val raw = script.data()
                if (raw.isBlank() || raw.length > 2_000_000) return@forEach
                collectInlineJson(raw, base, ::add)
            }

            // 5. Direct links, and finally a raw sweep for anything media-shaped.
            d.select("a[href]").forEach { a -> add(a.attr("href"), 0.72, "a") }

            val sweep = Regex("https?://[^\"'\\s<>\\\\]+", RegexOption.IGNORE_CASE)
            sweep.findAll(html).forEach { m -> add(m.value, 0.6, "sweep") }
        }

        return out.values.sortedByDescending { it.score }
    }

    /** Pulls media URLs out of any JSON structure, honouring the standard player key names. */
    private fun collectJson(el: JsonElement, base: String, add: (String?, Double, String) -> Unit) {
        when (el) {
            is JsonArray -> el.forEach { collectJson(it, base, add) }
            is JsonObject -> {
                val type = (el["@type"] as? JsonPrimitive)?.content?.lowercase()
                val isVideo = type != null && (type.contains("video") || type.contains("mediainstance"))
                el.forEach { (key, value) ->
                    val primitive = value as? JsonPrimitive
                    if (primitive != null && primitive.isString) {
                        if (key.equals("contentUrl", true) || key.equals("embedUrl", true)) {
                            add(primitive.content, if (isVideo) 0.92 else 0.85, "ld:$key")
                        }
                    } else if (JSON_KEYS.any { it.equals(key, true) }) {
                        collectJson(value, base, add)
                    }
                }
                el.values.forEach { if (it !is JsonPrimitive) collectJson(it, base, add) }
            }
            else -> Unit
        }
    }

    /**
     * Inline JSON is frequently not valid JSON on its own (it is a fragment of a script), so this
     * pulls out quoted strings assigned to the well-known player keys and then filters by shape.
     */
    private fun collectInlineJson(raw: String, base: String, add: (String?, Double, String) -> Unit) {
        JSON_KEYS.forEach { key ->
            Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"(https?://[^\"\\\\]{4,600})\"", RegexOption.IGNORE_CASE)
                .findAll(raw)
                .forEach { m -> add(m.groupValues[1], 0.8, "inline:$key") }
        }
        // Arrays of variant objects, e.g. {"file":"...","label":"1080p"}
        Regex("\"" + Regex.escape("file") + "\"\\s*:\\s*\\{[^}]{0,200}\"file\"\\s*:\\s*\"(https?://[^\"\\\\]{4,600})\"", RegexOption.IGNORE_CASE)
            .findAll(raw)
            .forEach { m -> add(m.groupValues[1], 0.78, "inline:variants") }
    }

    fun toVideos(refs: List<MediaRef>, episode: Int?, sourceUrl: String, referer: String): List<ResolvedVideo> =
        refs.mapNotNull { ref ->
            val type = UrlUtil.mediaType(ref.url) ?: return@mapNotNull null
            ResolvedVideo(
                url = ref.url,
                type = type,
                quality = QualityParser.extract(ref.url),
                referer = referer,
                episodeNumber = episode ?: EpisodeParser.extract(ref.url),
                sourceUrl = sourceUrl,
                provider = UrlUtil.host(ref.url),
                score = ref.score
            )
        }
}

interface MediaExtractor {
    suspend fun extract(client: OkHttpClient, pageUrl: String, episode: Int?): List<ResolvedVideo>
}

/** The page or URL is already the media. */
class DirectUrlExtractor : MediaExtractor {
    override suspend fun extract(client: OkHttpClient, u: String, episode: Int?): List<ResolvedVideo> {
        val t = UrlUtil.mediaType(u) ?: return emptyList()
        return listOf(
            ResolvedVideo(
                url = UrlUtil.canonical(u), type = t, quality = QualityParser.extract(u),
                episodeNumber = episode, sourceUrl = u, referer = u, provider = UrlUtil.host(u), score = 1.0
            )
        )
    }
}

/** Media declared in the page's own HTML. */
class HtmlMediaExtractor : MediaExtractor {
    override suspend fun extract(client: OkHttpClient, u: String, episode: Int?): List<ResolvedVideo> {
        val page = u.toHttpUrlOrNull() ?: return emptyList()
        val fetcher = HttpFetcher(client)
        val html = fetcher.get(page) ?: return emptyList()
        return MediaRefs.toVideos(MediaRefs.scan(html, u, episode), episode, u, u)
    }
}

/**
 * Follows embeds one level deep. Many sites serve a player page whose only content is an iframe to
 * a separate host, and the media lives on that host rather than on the site the user pasted.
 */
class EmbedExtractor : MediaExtractor {
    override suspend fun extract(client: OkHttpClient, u: String, episode: Int?): List<ResolvedVideo> {
        val page = u.toHttpUrlOrNull() ?: return emptyList()
        val fetcher = HttpFetcher(client)
        val html = fetcher.get(page, referer = refererFor(u)) ?: return emptyList()
        val direct = MediaRefs.toVideos(MediaRefs.scan(html, u, episode), episode, u, u)
        val embeds = embedUrls(html, u)
        if (embeds.isEmpty()) return direct
        val nested = coroutineScope {
            embeds.take(4).map { embed ->
                async { runCatching { MediaRefs.toVideos(MediaRefs.scan(htmlOf(fetcher, embed) ?: return@async emptyList(), embed, episode), episode, embed, u) }.getOrDefault(emptyList()) }
            }.awaitAll()
        }
        return direct + nested.flatten()
    }

    private suspend fun htmlOf(fetcher: HttpFetcher, url: String): String? = fetcher.getText(url, referer = refererFor(url))

    private fun refererFor(u: String): String {
        val parsed = u.toHttpUrlOrNull() ?: return u
        return "${parsed.scheme}://${parsed.host}/"
    }

    private fun embedUrls(html: String, base: String): List<String> {
        val out = LinkedHashSet<String>()
        runCatching {
            Jsoup.parse(html, base).select("iframe[src],iframe[data-src]").forEach { el ->
                val raw = el.attr("src").ifBlank { el.attr("data-src") }
                val absolute = UrlUtil.resolve(base, raw) ?: return@forEach
                if (absolute.startsWith("http", true)) out += absolute
            }
        }
        return out.toList()
    }
}

class MediaExtractorRegistry(private val xs: List<MediaExtractor>) {
    suspend fun extract(c: OkHttpClient, u: String, e: Int?): List<ResolvedVideo> =
        xs.flatMap { runCatching { it.extract(c, u, e) }.getOrDefault(emptyList()) }

    companion object {
        fun default() = MediaExtractorRegistry(listOf(DirectUrlExtractor(), HtmlMediaExtractor(), EmbedExtractor()))
    }
}

/**
 * Expands an HLS master playlist into its variants.
 *
 * Returning the master URL alone is not playable: it is an index of streams with no selection, and
 * a player given it will either fail or silently pick the lowest rendition. The highest-bandwidth
 * variant is resolved and returned alongside the master.
 */
object HlsResolver {
    suspend fun expand(client: OkHttpClient, videos: List<ResolvedVideo>, referer: String?): List<ResolvedVideo> {
        val targets = videos.filter { it.type == VideoType.HLS }.take(3)
        if (targets.isEmpty()) return videos
        val fetcher = HttpFetcher(client)
        val extra = coroutineScope {
            targets.map { v ->
                async {
                    val body = fetcher.getText(v.url, referer = referer ?: v.referer) ?: return@async emptyList()
                    val playlist = Hls.parse(body) ?: return@async emptyList()
                    if (!playlist.isMaster) return@async emptyList()
                    playlist.variants
                        .sortedByDescending { it.bandwidth }
                        .take(3)
                        .map { variant ->
                            val absolute = UrlUtil.resolve(v.url, variant.url) ?: return@map null
                            v.copy(
                                url = absolute,
                                quality = QualityParser.extract(absolute) ?: variant.resolution?.substringAfter('x')?.toIntOrNull() ?: v.quality,
                                score = v.score - 0.02
                            )
                        }
                        .filterNotNull()
                }
            }.awaitAll()
        }.flatten()
        return if (extra.isEmpty()) videos else videos + extra
    }
}
