package ani.sanin.universal.extractor

import ani.sanin.universal.model.*
import ani.sanin.universal.network.HttpFetcher
import ani.sanin.universal.util.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

interface MediaExtractor { fun extract(client:OkHttpClient,pageUrl:String,episode:Int?):List<ResolvedVideo> }
class MediaExtractorRegistry(private val xs:List<MediaExtractor>){ fun extract(c:OkHttpClient,u:String,e:Int?)=xs.flatMap{runCatching{it.extract(c,u,e)}.getOrDefault(emptyList())}.groupBy{it.url}.values.map{it.maxBy{v->v.score}}.sortedWith(compareByDescending<ResolvedVideo>{it.score}.thenByDescending{it.quality?:0}); companion object{fun default()=MediaExtractorRegistry(listOf(DirectUrlExtractor(),HtmlMediaExtractor(),EmbedExtractor()))} }
class DirectUrlExtractor:MediaExtractor{override fun extract(c:OkHttpClient,u:String,e:Int?):List<ResolvedVideo>{val t=UrlUtil.mediaType(u)?:return emptyList();return listOf(ResolvedVideo(UrlUtil.canonical(u),t,QualityParser.extract(u),episodeNumber=e,sourceUrl=u,referer=u,score=1.0))}}
class HtmlMediaExtractor:MediaExtractor{override fun extract(c:OkHttpClient,u:String,e:Int?):List<ResolvedVideo>{val page=u.toHttpUrlOrNull()?:return emptyList();val h=HttpFetcher(c).get(page)?:return emptyList();return extractHtml(h,u,e,1.0)}}
class EmbedExtractor:MediaExtractor{override fun extract(c:OkHttpClient,u:String,e:Int?):List<ResolvedVideo>{val page=u.toHttpUrlOrNull()?:return emptyList();val h=HttpFetcher(c).get(page)?:return emptyList();val d=Jsoup.parse(h,u);return d.select("iframe[src],iframe[data-src],video[src],source[src]").flatMap{el->val src=(el.absUrl("src").ifBlank{el.absUrl("data-src")}); if(src.isBlank())emptyList() else { val t=UrlUtil.mediaType(src); if(t!=null)listOf(ResolvedVideo(src,t,QualityParser.extract(src),episodeNumber=e,sourceUrl=u,referer=u,score=.92)) else { val inner=HttpFetcher(c).get(src.toHttpUrlOrNull()!!); if(inner!=null)extractHtml(inner,src,e,.82) else emptyList() } }} }}
fun extractHtml(html:String,source:String,e:Int?,baseScore:Double):List<ResolvedVideo>{ val d=Jsoup.parse(html,source); val candidates=linkedSetOf<String>(); d.select("video[src],source[src],video source[src],a[href],iframe[src],iframe[data-src]").forEach{el-> listOf(el.absUrl("src"),el.absUrl("data-src"),el.absUrl("href")).filter{it.isNotBlank()}.forEach(candidates::add)}; val regex=Regex("https?://[^\"'\\s<>]+",RegexOption.IGNORE_CASE); regex.findAll(html).map{it.value.replace("&amp;","&")}.filter{UrlUtil.mediaType(it)!=null}.forEach(candidates::add); return candidates.mapNotNull{u->val t=UrlUtil.mediaType(u)?:return@mapNotNull null;ResolvedVideo(UrlUtil.canonical(u),t,QualityParser.extract(u),episodeNumber=e,sourceUrl=source,referer=source,score=baseScore+if(t!=VideoType.CONTAINER).05 else 0.0)} }
