package ani.sanin.universal.network

import okhttp3.*

data class FetchResult(val status:Int,val body:String?)
class HttpFetcher(private val client:OkHttpClient){
    fun getResult(url:HttpUrl,referer:String?=null):FetchResult? { val b=Request.Builder().url(url).header("User-Agent",UA).header("Accept","text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8"); if(referer!=null)b.header("Referer",referer); return runCatching{client.newCall(b.build()).execute().use{r->FetchResult(r.code,r.body?.string()?.takeIf{r.isSuccessful})}}.getOrNull() }
    fun get(url:HttpUrl,referer:String?=null)=getResult(url,referer)?.body
    companion object { const val UA="Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36" }
}
