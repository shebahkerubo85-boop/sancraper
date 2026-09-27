package ani.sanin.universal

import ani.sanin.universal.adapter.*
import ani.sanin.universal.model.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

class UniversalResolver(private val client:OkHttpClient=defaultClient(),private val registry:SiteAdapterRegistry=SiteAdapterRegistry()){
 suspend fun resolve(startUrl:String,animeTitle:String,episodeNumber:Int?=null):ResolveResult {
   val u=startUrl.toHttpUrlOrNull()?:return ResolveResult.InvalidUrl
   val attempts=mutableListOf<String>(); val site=u.host
   return try { withTimeout(25_000) {
     attempts+="generic-discovery"; val adapter=registry.find(u)?:GenericSiteAdapter(); val candidates=adapter.searchAnime(client,u,animeTitle)
     val anime=if(candidates.isNotEmpty())candidates else listOf(AnimeCandidate(animeTitle,u.toString(),.5))
     if(episodeNumber==null)return@withTimeout ResolveResult.AnimeFound(anime,ResolveDiagnostics(ResolutionStage.DISCOVERY,site,attempts))
     for(a in anime.take(5)){ attempts+="episode-discovery:${a.url}"; val eps=adapter.findEpisodes(client,a); val matching=eps.filter{it.number==episodeNumber}.sortedByDescending{it.score}; for(ep in matching.take(4)){ attempts+="media-extraction:${ep.url}"; val videos=adapter.extractEpisode(client,ep); if(videos.isNotEmpty()) return@withTimeout ResolveResult.Success(videos.map{it.copy(title=a.title,episodeNumber=episodeNumber)}.take(8),ResolveDiagnostics(ResolutionStage.COMPLETE,site,attempts)) } }
     ResolveResult.VideoNotFound(ResolveDiagnostics(ResolutionStage.VALIDATION,site,attempts,listOf("No validated public MP4/HLS/DASH candidate was found; use Sanin WebView observation for JS-heavy pages.")))
   }} catch(_:TimeoutCancellationException){ResolveResult.TimedOut}catch(e:CancellationException){throw e}catch(e:Exception){ResolveResult.NetworkError(e.message?:"Unknown error",ResolveDiagnostics(ResolutionStage.INPUT,site,attempts))} }
 companion object { fun defaultClient()=OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).connectTimeout(6,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build() }
}
