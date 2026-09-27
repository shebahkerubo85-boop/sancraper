package ani.sanin.universal.model

import kotlinx.serialization.Serializable

@Serializable enum class VideoType { CONTAINER, HLS, DASH }
@Serializable enum class ResolutionStage { INPUT, DISCOVERY, EPISODE, PLAYER, JAVASCRIPT, BROWSER, VALIDATION, COMPLETE }

@Serializable
data class ResolvedVideo(
    val url: String, val type: VideoType, val quality: Int? = null,
    val headers: Map<String,String> = emptyMap(), val referer: String? = null,
    val title: String? = null, val episodeNumber: Int? = null, val sourceUrl: String,
    val provider: String? = null, val score: Double = 0.0
)
@Serializable data class AnimeCandidate(val title:String,val url:String,val score:Double)
@Serializable data class EpisodeCandidate(val number:Int?,val title:String?=null,val url:String,val score:Double)
@Serializable data class ResolveDiagnostics(
    val stage: ResolutionStage, val site: String? = null, val attempts: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** True when server-side crawling could not finish the job and a browser is needed. */
    val requiresBrowser: Boolean = false,
    /** The page the app should load in its WebView to continue. */
    val escalateUrl: String? = null,
    /** Machine readable hints for the client, e.g. `load-in-webview`. */
    val nextActions: List<String> = emptyList()
)
@Serializable sealed class ResolveResult {
    @Serializable data class Success(val videos:List<ResolvedVideo>, val diagnostics: ResolveDiagnostics?=null):ResolveResult()
    @Serializable data class AnimeFound(val candidates:List<AnimeCandidate>, val diagnostics: ResolveDiagnostics?=null):ResolveResult()
    @Serializable data class EpisodeNotFound(val diagnostics: ResolveDiagnostics?=null):ResolveResult()
    @Serializable data class VideoNotFound(val diagnostics: ResolveDiagnostics?=null):ResolveResult()
    @Serializable data object InvalidUrl:ResolveResult()
    @Serializable data class AccessDenied(val status:Int, val diagnostics:ResolveDiagnostics?=null):ResolveResult()
    @Serializable data object TimedOut:ResolveResult()
    @Serializable data class NetworkError(val message:String,val diagnostics:ResolveDiagnostics?=null):ResolveResult()
    /**
     * Server-side resolution could not complete because the page needs a real browser: the player is
     * injected by JavaScript, or a challenge intercepted the request. The app should load
     * [pageUrl] in its WebView, collect the media it observes and post it to
     * `/v1/browser-observation`. Without this the client had no way to tell "no such episode" apart
     * from "this site needs JavaScript", and simply reported failure.
     */
    @Serializable data class BrowserRequired(
        val pageUrl: String, val reason: String, val diagnostics: ResolveDiagnostics? = null
    ):ResolveResult()
}
@Serializable data class ResolveRequest(val url:String,val animeTitle:String,val episode:Int?=null)
@Serializable data class ApiResponse(val ok:Boolean,val result:ResolveResult)
