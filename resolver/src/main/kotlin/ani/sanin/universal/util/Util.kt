package ani.sanin.universal.util

import ani.sanin.universal.model.VideoType
import java.net.URI
import java.net.URLDecoder

/**
 * Host scoping for crawling.
 *
 * The previous implementation took the last two labels of a hostname, which is wrong for
 * multi-part public suffixes (`example.co.uk` scoped to `co.uk`, so the crawl escaped the site)
 * and for IP literals. Sites are also frequently served from a `www.` or a regional subdomain,
 * both of which must stay inside the crawl scope.
 */
object HostScope {
    /** Second-level labels that behave as public suffixes. */
    private val MULTI_PART_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk", "sch.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
        "com.br", "net.br", "org.br", "gov.br",
        "co.kr", "or.kr", "ne.kr",
        "com.mx", "com.ar", "com.tr", "com.tw", "com.hk", "com.sg", "com.my",
        "co.in", "net.in", "org.in", "co.za", "co.nz", "com.pl", "com.ua", "com.ph", "com.vn"
    )

    private fun isIpv4(host: String): Boolean =
        Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)

    /**
     * Registrable domain ("example.co.uk", "example.com"). IP literals and single-label hosts
     * are returned unchanged. Trailing dots and `www.` are normalised away.
     */
    fun registrable(input: String): String {
        var host = input.trim().lowercase().removeSuffix(".")
        if (host.isEmpty()) return host
        if (host.startsWith("[")) return host // IPv6 literal
        if (isIpv4(host)) return host
        host = host.removePrefix("www.")
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 1) return host
        if (labels.size == 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        // Multi-part public suffixes must not collapse to the suffix itself: for `a.b.example.co.uk`
        // the crawl scope is `example.co.uk`, not `co.uk`.
        if (lastTwo in MULTI_PART_SUFFIXES) return labels.takeLast(3).joinToString(".")
        return lastTwo
    }

    /** True when [candidate] is the same site as [reference] (or a subdomain of it). */
    fun sameSite(candidate: String, reference: String): Boolean {
        val a = registrable(candidate)
        val b = registrable(reference)
        if (a.isEmpty() || b.isEmpty()) return false
        return a == b
    }
}

object EpisodeParser {
    private val patterns = listOf(
        // "Episode 12", "ep-7", "E04", "episode_1080p"
        Regex("(?i)(?:episode|ep|e)[\\s._-]*(\\d{1,4})(?:\\b|$)"),
        // "/12", "-7", "_4" as a path or query segment
        Regex("(?i)(?:/|[-_ ])(\\d{1,4})(?:[/?#._-]|$)")
    )

    /** Noise words that are frequently part of an episode label but never the episode number. */
    private val NOISE = setOf(
        "episode", "ep", "e", "eps", "part", "pt", "new", "final", "movie", "ova", "ona", "special"
    )

    fun extract(s: String): Int? =
        patterns.asSequence().mapNotNull { it.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull() }.firstOrNull()

    /**
     * Episode number carried in a query parameter, e.g. `?ep=4402` or `?episode=12`.
     * Many sites use an opaque internal episode id in the query and the real ordinal in the path,
     * so the query value is only trusted when the parameter is explicitly episode-named.
     */
    fun fromQuery(url: String): Int? {
        val q = url.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return null
        for (pair in q.split('&')) {
            val key = pair.substringBefore('=').lowercase()
            val value = pair.substringAfter('=', "").trim()
            if (key.isEmpty() || value.isEmpty()) continue
            val numeric = value.toIntOrNull()
            if (numeric == null || numeric <= 0) continue
            if (key in setOf("ep", "eps", "episode", "epnum", "ep_number", "e")) return numeric
        }
        return null
    }

    /**
     * Best-effort episode number for a link, preferring an explicit query parameter and
     * otherwise falling back to the anchor text and then the URL.
     */
    fun best(url: String, anchorText: String? = null): EpisodeHint? {
        fromQuery(url)?.let { return EpisodeHint(it, "query") }
        anchorText?.takeIf { it.isNotBlank() }?.let { text ->
            extract(text)?.let { return EpisodeHint(it, "text") }
        }
        extract(url)?.let { return EpisodeHint(it, "url") }
        return null
    }

    data class EpisodeHint(val number: Int, val origin: String)

    /**
     * True when the URL itself denotes an episode rather than a detail/catalogue page.
     *
     * A bare trailing number is not enough: anime sites routinely use `/show-slug-1372` for a
     * *series* page, so accepting the path number alone made the resolver report series pages as
     * episodes and send the client to the wrong URL.
     */
    private val EPISODE_ROUTE = Regex(
        "(?i)(?:^|[/_-])(?:episode|episodes|ep|watch)(?=[-/_?#]|$)|\\?ep=|\\?eps=|\\?episode=|\\?ep_number=|\\?epnum="
    )
    private val EPISODE_TEXT = Regex("(?i)\\b(?:episode|episodes|eps|ep\\.?|part)\\s*#?\\d{1,4}\\b")

    fun isEpisodeRoute(url: String) = EPISODE_ROUTE.containsMatchIn(url)

    /** True when the visible link text names an episode explicitly. */
    fun hasEpisodeText(text: String) = EPISODE_TEXT.containsMatchIn(text)

    /** Tokens that carry no identifying information when matching a candidate title. */
    fun isNoiseToken(token: String): Boolean = token.lowercase() in NOISE
}

object QualityParser {
    fun extract(s: String): Int? =
        Regex("(?i)(?:^|[._-])(\\d{3,4})p(?:[._-]|$)").find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("(?i)(?:^|[._-])(4k|2160|1440|1080|720|480)(?:[._-]|$)").find(s)?.groupValues?.getOrNull(1)
                ?.let { if (it.equals("4k", true)) 2160 else it.toIntOrNull() }
}

object UrlUtil {
    fun canonical(s: String) = s.trim().trim('"', '\'', ')', ']', '}', ',', '\\')

    fun mediaType(s: String): VideoType? {
        val p = s.substringBefore('?').substringBefore('#').lowercase()
        return when {
            p.endsWith(".m3u8") || p.contains(".m3u8/") -> VideoType.HLS
            p.endsWith(".mpd") || p.contains(".mpd/") -> VideoType.DASH
            p.endsWith(".mp4") || p.endsWith(".webm") || p.endsWith(".mkv") || p.endsWith(".mov") -> VideoType.CONTAINER
            else -> null
        }
    }

    fun host(s: String) = runCatching { URI(s).host?.lowercase() }.getOrNull()

    /** Path plus query, used when comparing two URLs that should be treated as the same resource. */
    fun resourceKey(s: String): String {
        val noFragment = s.substringBefore('#')
        val path = runCatching { URI(noFragment).path }.getOrNull() ?: noFragment
        val query = runCatching { URI(noFragment).rawQuery }.getOrNull()
        return (path + (query?.let { "?$it" } ?: "")).lowercase()
    }

    /** Absolute URL for [href] interpreted relative to [base], or null when unparseable. */
    fun resolve(base: String, href: String): String? {
        val h = canonical(href)
        if (h.isEmpty()) return null
        if (h.startsWith("http://", true) || h.startsWith("https://", true)) return h
        if (h.startsWith("//")) return runCatching { URI("https:" + h).toString() }.getOrNull()
        if (h.startsWith("data:", true) || h.startsWith("javascript:", true) || h.startsWith("mailto:", true)) return null
        return runCatching { URI(base).resolve(h).toString() }.getOrNull()
    }

    /** Decode HTML entities that appear inside inline JSON blobs. */
    fun unescapeHtml(s: String): String =
        s.replace("&amp;", "&").replace("&#38;", "&").replace("&quot;", "\"").replace("&#39;", "'")

    /** Query parameter value, decoded. */
    fun queryParam(url: String, name: String): String? {
        val q = url.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return null
        for (pair in q.split('&')) {
            val key = pair.substringBefore('=')
            if (!key.equals(name, true)) continue
            val value = pair.substringAfter('=', "")
            return runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }
        return null
    }
}
