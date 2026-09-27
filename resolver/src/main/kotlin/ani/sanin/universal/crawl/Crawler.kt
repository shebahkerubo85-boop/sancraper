package ani.sanin.universal.crawl

import ani.sanin.universal.network.HttpFetcher
import ani.sanin.universal.util.HostScope
import ani.sanin.universal.util.UrlUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

data class CrawledPage(
    val url: HttpUrl,
    val html: String,
    val depth: Int,
    val status: Int = 200,
    val contentType: String? = null,
    val server: String? = null
) {
    val isJson: Boolean get() = contentType?.contains("json", ignoreCase = true) == true
}

/**
 * Breadth-first crawler over a single site.
 *
 * The previous implementation walked the frontier one blocking request at a time inside a 25 second
 * budget, so a moderately slow site exhausted the budget before reaching the episode list and the
 * resolver reported a timeout. Pages at the same depth are now fetched concurrently, the walk
 * stops as soon as the deadline passes, and host scoping understands multi-part public suffixes so
 * the crawl no longer escapes sites like `example.co.uk`.
 */
class SiteCrawler(
    private val fetcher: HttpFetcher,
    private val maxDepth: Int = 2,
    private val maxPages: Int = 36,
    private val parallelism: Int = 6,
    private val deadlineMillis: Long = 20_000
) {
    suspend fun crawl(start: HttpUrl): List<CrawledPage> {
        val started = System.currentTimeMillis()
        val domain = HostScope.registrable(start.host)
        val seen = mutableSetOf<String>()
        val out = mutableListOf<CrawledPage>()
        var frontier = listOf(start)
        seen += key(start)

        for (depth in 0..maxDepth) {
            if (frontier.isEmpty() || out.size >= maxPages) break
            if (System.currentTimeMillis() - started > deadlineMillis) break

            val remaining = maxPages - out.size
            val batch = frontier.take(remaining)

            val results = coroutineScope {
                batch.map { u -> async { runCatching { fetcher.getResult(u) }.getOrNull() } }.awaitAll()
            }

            val next = LinkedHashSet<HttpUrl>()
            for ((index, result) in results.withIndex()) {
                val u = batch[index]
                val body = result?.body
                if (result == null || body == null) continue
                out += CrawledPage(u, body, depth, result.status, result.contentType, result.server)
                if (depth >= maxDepth) continue
                collectLinks(u, body, domain)?.forEach { link ->
                    val k = key(link)
                    if (seen.add(k)) next += link
                }
            }
            frontier = next.toList()
        }
        return out
    }

    private fun collectLinks(page: HttpUrl, html: String, domain: String): List<HttpUrl>? {
        if (!html.trimStart().startsWith("<")) return null
        val out = LinkedHashSet<HttpUrl>()
        runCatching {
            Jsoup.parse(html, page.toString()).select("a[href]").forEach { a ->
                val href = a.attr("href")
                if (href.isBlank()) return@forEach
                val absolute = UrlUtil.resolve(page.toString(), href) ?: return@forEach
                val parsed = absolute.toHttpUrlOrNull() ?: return@forEach
                if (parsed.host.isBlank()) return@forEach
                if (!HostScope.sameSite(parsed.host, domain)) return@forEach
                val text = (a.text() + " " + absolute).lowercase()
                if (isUseful(text)) out += parsed
            }
        }
        return out.toList()
    }

    private fun key(u: HttpUrl) = u.toString().removeSuffix("/").lowercase()

    /**
     * Link filter. The original ten keywords missed the shapes most sites actually use for episode
     * lists, so a crawl that started on an anime page frequently never reached `/episode-3`.
     */
    private fun isUseful(s: String): Boolean = MARKERS.any { s.contains(it) }

    companion object {
        private val MARKERS = listOf(
            "anime", "watch", "episode", "episodes", "ep-", "ep/", "search",
            "season", "play", "stream", "detail", "details",
            "catalog", "sitemap", "episode-list", "watchlist"
        )
    }
}
