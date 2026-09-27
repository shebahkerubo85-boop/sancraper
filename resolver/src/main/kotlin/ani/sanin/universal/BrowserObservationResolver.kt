package ani.sanin.universal

import ani.sanin.universal.extractor.AdFilter
import ani.sanin.universal.model.BrowserObservation
import ani.sanin.universal.model.ResolutionStage
import ani.sanin.universal.model.ResolveDiagnostics
import ani.sanin.universal.model.ResolveResult
import ani.sanin.universal.model.ResolvedVideo
import ani.sanin.universal.model.VideoType
import ani.sanin.universal.util.EpisodeParser
import ani.sanin.universal.util.QualityParser
import ani.sanin.universal.util.UrlUtil

/**
 * Second half of the Sanin WebView contract: the app loads a page the resolver could not finish
 * statically, captures the media its network stack sees, and posts those observations here.
 *
 * The observations arrive as raw URL lists, so this is where the filtering that the HTML scanner
 * does has to happen. It mirrors the app's own request interception: keep media extensions, drop
 * adverts, group by resource so the same stream seen twice is one candidate, and prefer the
 * highest quality rendition. Iframes and script URLs are also mined, because a WebView often
 * reports the embed host rather than the final playlist.
 */
object BrowserObservationResolver {

    fun resolve(o: BrowserObservation): ResolveResult {
        val pageUrl = o.finalUrl ?: o.pageUrl
        val pool = LinkedHashSet<String>()
        pool += o.mediaUrls
        pool += o.requestUrls
        pool += o.iframeUrls
        pool += o.scriptUrls
        o.domText?.let { dom ->
            MEDIA_URL.findAll(dom).forEach { pool += it.value }
        }

        val byResource = LinkedHashMap<String, ResolvedVideo>()
        for (raw in pool) {
            val url = UrlUtil.canonical(UrlUtil.unescapeHtml(raw))
            if (!url.startsWith("http", true)) continue
            if (AdFilter.isAd(url)) continue
            val type = UrlUtil.mediaType(url) ?: continue
            val key = UrlUtil.resourceKey(url)
            val score = when (type) {
                VideoType.HLS -> 1.0
                VideoType.DASH -> 0.98
                VideoType.CONTAINER -> 0.9
            }
            val candidate = ResolvedVideo(
                url = url,
                type = type,
                quality = QualityParser.extract(url),
                headers = o.headers,
                referer = pageUrl,
                episodeNumber = EpisodeParser.extract(url),
                sourceUrl = o.pageUrl,
                provider = UrlUtil.host(url),
                score = score
            )
            val existing = byResource[key]
            if (existing == null || candidate.score > existing.score ||
                (candidate.quality ?: 0) > (existing.quality ?: 0)
            ) {
                byResource[key] = candidate
            }
        }

        val best = byResource.values
            .sortedWith(compareByDescending<ResolvedVideo> { it.score }.thenByDescending { it.quality ?: 0 })
            .take(8)

        return if (best.isEmpty()) {
            ResolveResult.VideoNotFound(
                ResolveDiagnostics(
                    stage = ResolutionStage.BROWSER, site = UrlUtil.host(pageUrl), attempts = listOf("browser-observation"),
                    warnings = listOf(
                        "No media request was observed. The page may still be loading, may require sign-in, " +
                            "or may serve media from a WebSocket/DRM pipeline this resolver does not handle."
                    )
                )
            )
        } else {
            ResolveResult.Success(
                best,
                ResolveDiagnostics(
                    stage = ResolutionStage.COMPLETE, site = UrlUtil.host(pageUrl),
                    attempts = listOf("browser-observation"), warnings = emptyList()
                )
            )
        }
    }

    private val MEDIA_URL = Regex("https?://[^\"'\\s<>\\\\]+\\.(?:m3u8|mpd|mp4|webm)(?:\\?[^\"'\\s<>\\\\]*)?", RegexOption.IGNORE_CASE)
}
