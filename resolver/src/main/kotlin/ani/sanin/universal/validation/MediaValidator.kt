package ani.sanin.universal.validation

import ani.sanin.universal.model.VideoType

/**
 * Decides whether a discovered URL is really a playable media resource.
 *
 * Crawl-based extraction produces a lot of URLs that merely *look* like media: ad tags, tracking
 * pixels, trailer assets and player pages. Reporting those as playable produces a source the user
 * selects and then cannot watch, which is worse than reporting nothing. Validation is expressed as
 * a pure function over a [Probe] so it can be unit tested without a network, with the HTTP call
 * supplied by the caller.
 */
object MediaValidator {
    /** Anything smaller than this is a stub, placeholder or tracking response, not an episode. */
    const val MIN_PLAYABLE_BYTES: Long = 64L * 1024

    data class Probe(
        val status: Int,
        val contentType: String? = null,
        val contentLength: Long? = null,
        val bodyPrefix: String? = null
    )

    private val PLAYABLE_TYPES = listOf(
        "video/",
        "audio/mpegurl",
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "application/dash+xml",
        "video/vnd.mpeg.dash.mpd",
        "application/octet-stream",
        "binary/octet-stream"
    )

    fun isPlayableContentType(contentType: String?): Boolean {
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        if (ct.isEmpty()) return false
        return PLAYABLE_TYPES.any { ct.startsWith(it) }
    }

    /** A response that is really an HTML page, i.e. the URL points at a player, not at media. */
    fun looksLikeHtml(bodyPrefix: String?): Boolean {
        val head = bodyPrefix?.trimStart()?.lowercase() ?: return false
        return head.startsWith("<!doctype html") || head.startsWith("<html") ||
            (head.startsWith("<") && head.contains("<body"))
    }

    /**
     * @param expected the media type inferred from the URL, used to sanity check the body when the
     *   server sends no useful content type. Null when the URL had no media extension.
     */
    fun accept(probe: Probe, expected: VideoType?): Boolean {
        if (probe.status != 200 && probe.status != 206) return false

        val ct = probe.contentType?.substringBefore(';')?.trim()?.lowercase()
        val hasPlayableType = ct != null && isPlayableContentType(ct)
        val hasHtmlType = ct != null && (ct == "text/html" || ct == "application/xhtml+xml")

        if (hasHtmlType) return false
        if (looksLikeHtml(probe.bodyPrefix)) return false

        if (ct != null && ct.isNotEmpty() && !hasPlayableType) {
            // Server was explicit and it is not media.
            return false
        }

        // Content length is a strong signal when present: stubs and ad bodies are tiny.
        // It must not be applied to manifests, which are legitimately a few kilobytes.
        val len = probe.contentLength
        val isManifest = expected == VideoType.HLS || expected == VideoType.DASH
        if (!isManifest && len != null && len in 1 until MIN_PLAYABLE_BYTES) return false

        // When the URL claims to be a playlist, confirm the body really is one.
        if (expected == VideoType.HLS) {
            val head = probe.bodyPrefix?.trimStart()
            if (head != null && head.isNotEmpty() && !head.startsWith("#EXTM3U")) return false
        }

        // Nothing to go on: accept and let the player's own error handling deal with it.
        return hasPlayableType || ct.isNullOrEmpty() || expected != null
    }
}
