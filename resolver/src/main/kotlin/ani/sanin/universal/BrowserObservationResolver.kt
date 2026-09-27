package ani.sanin.universal

import ani.sanin.universal.model.*
import ani.sanin.universal.util.*

object BrowserObservationResolver {
 fun resolve(o:BrowserObservation):ResolveResult {
   val candidates=(o.mediaUrls+o.requestUrls).map(UrlUtil::canonical).distinct().mapNotNull{u-> val t=UrlUtil.mediaType(u)?:return@mapNotNull null; val provider=UrlUtil.host(u); val score=when(t){VideoType.HLS->1.0;VideoType.DASH->.98;VideoType.CONTAINER->.9}; ResolvedVideo(u,t,QualityParser.extract(u),o.headers,o.finalUrl?:o.pageUrl,episodeNumber=EpisodeParser.extract(u),sourceUrl=o.pageUrl,provider=provider,score=score)}
   val best=candidates.sortedWith(compareByDescending<ResolvedVideo>{it.score}.thenByDescending{it.quality?:0}).take(8); return if(best.isEmpty())ResolveResult.VideoNotFound(ResolveDiagnostics(ResolutionStage.BROWSER,o.pageUrl,emptyList(),listOf("No media candidate was observed."))) else ResolveResult.Success(best,ResolveDiagnostics(ResolutionStage.COMPLETE,o.pageUrl,listOf("browser-observation")))
 }
}
