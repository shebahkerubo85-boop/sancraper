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
    val warnings: List<String> = emptyList()
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
}
@Serializable data class ResolveRequest(val url:String,val animeTitle:String,val episode:Int?=null)
@Serializable data class ApiResponse(val ok:Boolean,val result:ResolveResult)
