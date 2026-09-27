package ani.sanin.universal.extractor

import ani.sanin.universal.util.HostScope

/**
 * Filters out advertising and tracking URLs.
 *
 * The raw-regex sweep in [extractHtml][ani.sanin.universal.extractor.extractHtml] matches every
 * URL in a page that looks like media, which reliably picks up ad tags alongside the real stream.
 * The list below is deliberately conservative: a false positive here means losing the only
 * playable candidate, so entries are limited to unambiguous ad/tracker shapes.
 */
object AdFilter {
    private val HOST_MARKERS = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google",
        "adnxs.com", "rubiconproject.com", "pubmatic.com", "criteo.com", "taboola.com",
        "outbrain.com", "popads.net", "propellerads.com", "adsterra.com", "exoclick.com",
        "juicyads.com", "hilltopads.net", "onclickads.net", "adcash.com", "revcontent.com",
        "mgid.com", "plista.com", "zedo.com", "adskeeper.com", "teads.tv", "smartadserver.com"
    )

    private val PATH_MARKERS = listOf(
        "/ads/", "/ad/", "/adservice/", "/advert", "/banner", "/popunder", "/popunder-",
        "/clicktrack", "/track-click", "/beacon", "/pixel", "/spacer", "/1x1", "/filler",
        "/adserver", "/adsdk", "/adframe", "/prebid"
    )

    private val AD_QUERY = Regex("(^|&)(ad|ads|adid|ad_id|adtype|ad_type|adsrc|advert|advertising|adslot)=")

    /** True when the URL looks like advertising or tracking rather than content. */
    fun isAd(url: String): Boolean {
        val lower = url.lowercase()
        val host = ani.sanin.universal.util.UrlUtil.host(lower) ?: return false
        if (HOST_MARKERS.any { host == it || host.endsWith(".$it") || host.contains(it) }) return true
        val path = lower.substringBefore('?')
        if (PATH_MARKERS.any { path.contains(it) }) return true
        val query = lower.substringAfter('?', "")
        if (query.isNotEmpty() && AD_QUERY.containsMatchIn(query)) return true
        // Tracking redirects carry a destination but no media extension.
        if (query.contains("url=") && ani.sanin.universal.util.UrlUtil.mediaType(lower) == null) return true
        return false
    }

    /** True when the URL is served from a host unrelated to the page that referenced it. */
    fun isOffSite(url: String, pageHost: String?): Boolean {
        if (pageHost.isNullOrBlank()) return false
        val host = ani.sanin.universal.util.UrlUtil.host(url) ?: return false
        return !HostScope.sameSite(host, pageHost)
    }
}
