package ani.sanin.universal.crawl

import ani.sanin.universal.network.HttpFetcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

data class CrawledPage(val url:HttpUrl,val html:String,val depth:Int,val status:Int=200)
class SiteCrawler(private val fetcher:HttpFetcher,private val maxDepth:Int=2,private val maxPages:Int=36){
    fun crawl(start:HttpUrl):List<CrawledPage>{
        val q=ArrayDeque<Pair<HttpUrl,Int>>(); val seen=mutableSetOf<String>(); val out=mutableListOf<CrawledPage>(); val domain=registrable(start.host)
        q.add(start to 0)
        while(q.isNotEmpty()&&out.size<maxPages){ val (u,d)=q.removeFirst(); val k=u.toString().removeSuffix("/").lowercase(); if(!seen.add(k))continue; val result=fetcher.getResult(u)?:continue; if(result.body==null)continue; out+=CrawledPage(u,result.body,d,result.status); if(d>=maxDepth)continue
            Jsoup.parse(result.body,u.toString()).select("a[href]").forEach{a-> val n=a.absUrl("href").toHttpUrlOrNull()?:return@forEach; if(registrable(n.host)!=domain)return@forEach; val text=(a.text()+" "+n.toString()).lowercase(); if(isUseful(text))q.add(n to d+1) }
        }; return out
    }
    private fun isUseful(s:String)=listOf("anime","watch","episode","episodes","ep-","search","show","series","season","title").any(s::contains)
    private fun registrable(h:String):String { val p=h.split('.'); return if(p.size>=2)p.takeLast(2).joinToString(".") else h }
}
