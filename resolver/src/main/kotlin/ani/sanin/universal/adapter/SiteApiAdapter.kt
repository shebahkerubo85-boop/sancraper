package ani.sanin.universal.adapter

import ani.sanin.universal.model.AnimeCandidate
import ani.sanin.universal.model.EpisodeCandidate
import ani.sanin.universal.model.ResolvedVideo
import ani.sanin.universal.model.VideoType
import ani.sanin.universal.network.HttpFetcher
import ani.sanin.universal.registry.ApiHints
import ani.sanin.universal.registry.SiteDefinition
import ani.sanin.universal.util.QualityParser
import ani.sanin.universal.util.UrlUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * Adapter driven by the declarative [ApiHints] in the site registry.
 *
 * Handles the two shapes the generic crawler cannot reach: a site whose catalogue is only exposed
 * as JSON, and a site that resolves an episode number straight to a media URL without a crawlable
 * episode list. Both are described entirely by registry data.
 */
class SiteApiAdapter(
    private val client: OkHttpClient,
    private val definition: SiteDefinition,
    private val hints: ApiHints
) : SiteAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val fetcher = HttpFetcher(client)

    override fun canHandle(url: HttpUrl): Boolean {
        val host = url.host.lowercase()
        return definition.domains.any { host == it.lowercase() || host.endsWith(".$it".lowercase()) }
    }

    override suspend fun searchAnime(c: OkHttpClient, start: HttpUrl, title: String): List<AnimeCandidate> {
        val template = hints.catalogUrlTemplate ?: return emptyList()
        val id = numericId(start.toString()) ?: return emptyList()
        val url = render(template, mapOf("id" to id.toString())) ?: return emptyList()
        val body = fetcher.getText(url, referer = hints.referer) ?: return emptyList()
        val items = selectItems(body, hints.listPath) ?: return emptyList()
        val numberKey = hints.numberKey
        val titleKey = hints.titleKey
        val urlKey = hints.urlKey
        return items.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val number = (numberKey?.let { obj.int(it) })
            val name = (titleKey?.let { obj.string(it) })
            val link = urlKey?.let { obj.string(it) }
            if (name == null && number == null) return@mapNotNull null
            val href = link ?: start.toString()
            AnimeCandidate(name ?: title, href, 0.9)
        }
    }

    override suspend fun findEpisodes(c: OkHttpClient, anime: AnimeCandidate): List<EpisodeCandidate> {
        val template = hints.catalogUrlTemplate ?: return emptyList()
        val id = numericId(anime.url) ?: return emptyList()
        val url = render(template, mapOf("id" to id.toString())) ?: return emptyList()
        val body = fetcher.getText(url, referer = hints.referer) ?: return emptyList()
        val items = selectItems(body, hints.listPath) ?: return emptyList()
        val out = mutableListOf<EpisodeCandidate>()
        for (item in items) {
            val obj = item as? JsonObject ?: continue
            val number = hints.numberKey?.let { obj.int(it) } ?: continue
            if (number <= 0) continue
            val title = hints.titleKey?.let { obj.string(it) }
            val link = hints.urlKey?.let { obj.string(it) }
            val fallback = hints.embedUrlTemplate?.let { t ->
                render(t, mapOf("id" to id.toString(), "episode" to number.toString()))
            }
            val target = link ?: fallback ?: anime.url
            out += EpisodeCandidate(number, title, target, 0.9)
        }
        return out.sortedBy { it.number ?: Int.MAX_VALUE }
    }

    override suspend fun extractEpisode(c: OkHttpClient, episode: EpisodeCandidate): List<ResolvedVideo> {
        val template = hints.embedUrlTemplate
        val number = episode.number
        if (template == null || number == null) return emptyList()
        val id = numericId(episode.url) ?: return emptyList()
        val url = render(template, mapOf("id" to id.toString(), "episode" to number.toString())) ?: return emptyList()
        val body = fetcher.getText(url, referer = hints.referer ?: episode.url) ?: return emptyList()
        return mediaFromJson(body, hints.mediaKeys, episode.url, number, url)
    }

    private suspend fun mediaFromJson(
        body: String,
        mediaKeys: List<String>,
        sourceUrl: String,
        episode: Int,
        endpoint: String
    ): List<ResolvedVideo> {
        val out = mutableListOf<ResolvedVideo>()
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return out
        collectMedia(root, mediaKeys, sourceUrl, episode, out)
        return out.distinctBy { UrlUtil.resourceKey(it.url) }
            .sortedWith(compareByDescending<ResolvedVideo> { it.score })
    }

    private fun collectMedia(
        el: JsonElement,
        mediaKeys: List<String>,
        sourceUrl: String,
        episode: Int,
        out: MutableList<ResolvedVideo>
    ) {
        when (el) {
            is JsonArray -> el.forEach { collectMedia(it, mediaKeys, sourceUrl, episode, out) }
            is JsonObject -> {
                el.forEach { (key, value) ->
                    if (mediaKeys.any { it.equals(key, true) }) {
                        val primitive = value as? JsonPrimitive
                        val raw = when {
                            primitive != null && primitive.isString -> primitive.content
                            value is JsonArray -> value.filterIsInstance<JsonPrimitive>()
                                .firstOrNull { it.isString }?.content
                            else -> null
                        }
                        if (raw != null) {
                            val absolute = UrlUtil.resolve(sourceUrl, UrlUtil.canonical(raw)) ?: return@forEach
                            UrlUtil.mediaType(absolute)?.let { type ->
                                out += ResolvedVideo(
                                    url = absolute, type = type, quality = QualityParser.extract(absolute),
                                    referer = hints.referer ?: sourceUrl, episodeNumber = episode,
                                    sourceUrl = sourceUrl, provider = UrlUtil.host(absolute),
                                    score = if (type == VideoType.HLS) 0.98 else 0.9
                                )
                            }
                        }
                    }
                    if (value !is JsonPrimitive) collectMedia(value, mediaKeys, sourceUrl, episode, out)
                }
            }
            else -> Unit
        }
    }

    /** Array at [path] in the response, where `$` means the root and `.` separates segments. */
    private fun selectItems(body: String, path: String?): JsonArray? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val segments = (path ?: "$").split('.').map { it.trim() }.filter { it.isNotEmpty() && it != "$" }
        var current: JsonElement = root
        for (segment in segments) {
            current = (current as? JsonObject)?.get(segment) ?: return null
        }
        return current as? JsonArray
    }

    private fun render(template: String, vars: Map<String, String>): String? {
        var out = template
        vars.forEach { (k, v) -> out = out.replace("{$k}", v) }
        if (out.contains('{')) return null
        return out.toHttpUrlOrNullSafe()
    }

    private fun String.toHttpUrlOrNullSafe(): String? =
        if (startsWith("http://") || startsWith("https://")) this else null

    private fun numericId(url: String): Int? {
        val path = url.substringBefore('?').substringBefore('#').removeSuffix("/")
        val last = path.substringAfterLast('/')
        Regex("(\\d{1,9})").find(last)?.let { return it.groupValues[1].toIntOrNull() }
        return Regex("(\\d{1,9})").find(path)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()?.toInt()
}
