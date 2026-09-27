package ani.sanin.universal.discovery

import ani.sanin.universal.crawl.CrawledPage
import ani.sanin.universal.model.*
import ani.sanin.universal.util.EpisodeParser
import org.jsoup.Jsoup

object AnimeDiscovery {
 fun find(pages:List<CrawledPage>,title:String):List<AnimeCandidate>{
   val out=mutableListOf<AnimeCandidate>()
   for(p in pages){ val d=Jsoup.parse(p.html,p.url.toString()); val vals=mutableListOf<Pair<String,Double>>();
     d.title().takeIf{it.isNotBlank()}?.let{vals+=it to 1.0}; d.select("h1,h2,h3,[itemprop=name],[class*=title],[class*=name]").forEach{it.text().takeIf(String::isNotBlank)?.let{x->vals+=x to .9}}; d.select("meta[property=og:title],meta[name=twitter:title]").forEach{it.attr("content").takeIf(String::isNotBlank)?.let{x->vals+=x to .96}}
     val best=vals.maxByOrNull{TitleMatcher.score(title,it.first)}?:continue; val score=TitleMatcher.score(title,best.first)*best.second; if(score>=.45)out+=AnimeCandidate(clean(best.first),p.url.toString(),score)
   }; return out.groupBy{it.url}.values.map{it.maxBy{c->c.score}}.sortedByDescending{it.score}.take(12)
 }
 private fun clean(s:String)=s.replace(Regex("(?i)\\s*[-|]\\s*(watch|stream|online|free|hd).*?$"),"").trim()
}
object EpisodeDiscovery {
 fun find(html:String,base:String):List<EpisodeCandidate>{ val d=Jsoup.parse(html,base); return d.select("a[href]").mapNotNull{a-> val u=a.absUrl("href").takeIf(String::isNotBlank)?:return@mapNotNull null; val n=EpisodeParser.extract(a.text())?:EpisodeParser.extract(u); n?.let{EpisodeCandidate(it,a.text().ifBlank{null},u,episodeScore(a.text(),u,it))}}.groupBy{it.url}.values.map{it.maxBy{e->e.score}}.sortedByDescending{it.score} }
 private fun episodeScore(text:String,url:String,n:Int)=.65+(if(Regex("(?i)\\b(?:episode|ep|e)\\s*$n\\b").containsMatchIn(text)) .3 else 0.0)+(if(url.contains(n.toString())) .05 else 0.0)
}
