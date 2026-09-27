package ani.sanin.universal.adapter

import ani.sanin.universal.crawl.ChallengeDetector
import ani.sanin.universal.crawl.SiteChallengeException
import ani.sanin.universal.crawl.SiteCrawler
import ani.sanin.universal.discovery.AnimeDiscovery
import ani.sanin.universal.discovery.EpisodeDiscovery
import ani.sanin.universal.extractor.HlsResolver
import ani.sanin.universal.extractor.MediaExtractorRegistry
import ani.sanin.universal.model.AnimeCandidate
import ani.sanin.universal.model.EpisodeCandidate
import ani.sanin.universal.model.ResolvedVideo
import ani.sanin.universal.network.HttpFetcher
import ani.sanin.universal.util.UrlUtil
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

interface SiteAdapter {
    fun canHandle(url: HttpUrl): Boolean
    suspend fun searchAnime(c: OkHttpClient, start: HttpUrl, title: String): List<AnimeCandidate>
    suspend fun findEpisodes(c: OkHttpClient, anime: AnimeCandidate): List<EpisodeCandidate>
    suspend fun extractEpisode(c: OkHttpClient, episode: EpisodeCandidate): List<ResolvedVideo>
}

class SiteAdapterRegistry(private val adapters: List<SiteAdapter> = emptyList()) {
    fun find(u: HttpUrl) = adapters.firstOrNull { it.canHandle(u) }
}

/**
 * The adapter used for any site the user pastes.
 *
 * Site specific behaviour belongs in the registry, not here, so this stays generic: crawl, match
 * the requested title, locate the episode list, extract, then verify what was found. The one
 * substantive addition over the previous version is that candidates are verified before being
 * reported, because a resolver that hands back an advert or a trailer produces a source the user
 * selects and then cannot watch.
 */
class GenericSiteAdapter(
    private val depth: Int = 2,
    private val pages: Int = 36,
    private val deadlineMillis: Long = 20_000
) : SiteAdapter {

    override fun canHandle(u: HttpUrl) = true

    override suspend fun searchAnime(c: OkHttpClient, s: HttpUrl, t: String): List<AnimeCandidate> {
        val crawled = SiteCrawler(HttpFetcher(c), depth, pages, deadlineMillis = deadlineMillis).crawl(s)
        // A crawl that only ever saw challenge pages means the site is refusing the request, not
        // that the anime is missing. That distinction decides whether escalation is worthwhile.
        val readable = crawled.filter { it.status == 200 && it.body.isNotBlank() }
        if (readable.isEmpty() && crawled.any { it.status == 403 || it.status == 503 }) {
            val challenged = crawled.first { it.status == 403 || it.status == 503 }
            throw SiteChallengeException(s.toString(), ChallengeDetector.kind(challenged.server))
        }
        return AnimeDiscovery.find(crawled, t)
    }

    override suspend fun findEpisodes(c: OkHttpClient, a: AnimeCandidate): List<EpisodeCandidate> {
        val u = a.url.toHttpUrlOrNull() ?: return emptyList()
        val fetcher = HttpFetcher(c)
        val html = fetcher.get(u) ?: return emptyList()
        val direct = EpisodeDiscovery.find(html, u.toString())
        if (direct.isNotEmpty()) return direct

        // The anime page often links to a dedicated episode list. Follow the first plausible one
        // rather than reporting "no episodes" when the data is one click away.
        val episodePage = findEpisodePageLink(html, u.toString()) ?: return emptyList()
        val pageHtml = fetcher.getText(episodePage) ?: return emptyList()
        return EpisodeDiscovery.find(pageHtml, episodePage)
    }

    override suspend fun extractEpisode(c: OkHttpClient, e: EpisodeCandidate): List<ResolvedVideo> {
        val raw = MediaExtractorRegistry.default().extract(c, e.url, e.number)
        if (raw.isEmpty()) return emptyList()
        return HlsResolver.expand(c, raw, referer = e.url)
    }

    private fun findEpisodePageLink(html: String, base: String): String? {
        var best: String? = null
        runCatching {
            org.jsoup.Jsoup.parse(html, base).select("a[href]").forEach { a ->
                val href = a.attr("href")
                if (href.isBlank()) return@forEach
                val absolute = UrlUtil.resolve(base, href) ?: return@forEach
                if (!absolute.startsWith("http", true)) return@forEach
                val lower = absolute.lowercase()
                val text = a.text().lowercase()
                val looksRight = lower.contains("/episode") || lower.contains("/episodes") ||
                    lower.contains("episode-list") || text.contains("episode list") ||
                    text.contains("all episode")
                if (!looksRight) return@forEach
                if (best == null) best = absolute
            }
        }
        return best
    }
}
