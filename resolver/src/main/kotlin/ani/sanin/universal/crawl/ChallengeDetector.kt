package ani.sanin.universal.crawl

/**
 * Detects anti-bot interstitials.
 *
 * When a request is answered with a challenge page rather than the site, the resolver used to
 * report "no media found", which is indistinguishable from "this episode does not exist". A
 * challenge is actionable: a real browser solves it. Recognising it lets the resolver escalate
 * with an accurate reason instead of a misleading one.
 */
object ChallengeDetector {
    private val MARKERS = listOf(
        "just a moment",
        "attention required",
        "checking your browser",
        "enable javascript and cookies to continue",
        "cf-browser-verification",
        "cf_chl_opt",
        "__cf_bm",
        "ddos-guard",
        "captcha-delivery",
        "px-captcha",
        "please verify you are a human",
        "access denied",
        "you have been blocked"
    )

    fun isChallenge(status: Int, body: String?, server: String? = null): Boolean {
        // The Server header is a hint, not a requirement: plenty of WAFs omit it, and some serve the
        // interstitial with a 200.
        val serverHeader = server?.lowercase() ?: ""
        val cloudflareish = serverHeader.contains("cloudflare") || serverHeader.contains("ddos-guard")
        val text = body?.take(4000)?.lowercase() ?: ""
        val marked = MARKERS.any { text.contains(it) }
        if (marked && (status == 403 || status == 503 || status == 429 || status == 200 || cloudflareish)) return true
        return cloudflareish && (status == 403 || status == 503)
    }

    /** Short label for diagnostics. */
    fun kind(server: String?): String {
        val s = server?.lowercase() ?: return "challenge"
        return when {
            s.contains("ddos-guard") -> "ddos-guard"
            s.contains("cloudflare") -> "cloudflare"
            else -> "challenge"
        }
    }
}

/** Raised when every attempt to read a site was answered with a challenge page. */
class SiteChallengeException(val url: String, val challengeKind: String) : Exception("site challenge: $challengeKind")
