package ani.sanin.universal.adapter

import ani.sanin.universal.crawl.SiteCrawler
import ani.sanin.universal.discovery.*
import ani.sanin.universal.extractor.MediaExtractorRegistry
import ani.sanin.universal.model.*
import ani.sanin.universal.network.HttpFetcher
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
interface SiteAdapter { fun canHandle(url:HttpUrl):Boolean; suspend fun searchAnime(c:OkHttpClient,start:HttpUrl,title:String):List<AnimeCandidate>; suspend fun findEpisodes(c:OkHttpClient,anime:AnimeCandidate):List<EpisodeCandidate>; suspend fun extractEpisode(c:OkHttpClient,episode:EpisodeCandidate):List<ResolvedVideo> }
class SiteAdapterRegistry(private val adapters:List<SiteAdapter> = emptyList()){ fun find(u:HttpUrl)=adapters.firstOrNull{it.canHandle(u)} }
class GenericSiteAdapter(private val depth:Int=2,private val pages:Int=36):SiteAdapter { override fun canHandle(u:HttpUrl)=true; override suspend fun searchAnime(c:OkHttpClient,s:HttpUrl,t:String)=AnimeDiscovery.find(SiteCrawler(HttpFetcher(c),depth,pages).crawl(s),t); override suspend fun findEpisodes(c:OkHttpClient,a:AnimeCandidate):List<EpisodeCandidate>{val u=a.url.toHttpUrlOrNull()?:return emptyList();val h=HttpFetcher(c).get(u)?:return emptyList();return EpisodeDiscovery.find(h,u.toString())}; override suspend fun extractEpisode(c:OkHttpClient,e:EpisodeCandidate)=MediaExtractorRegistry.default().extract(c,e.url,e.number)}
