package ani.sanin.universal.registry

import okhttp3.OkHttpClient
import okhttp3.Request

class RemoteRegistry(private val client: OkHttpClient = OkHttpClient()) {
    fun fetch(url: String): SiteRegistry {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "SaninUniversalResolver/2")
            .build()

        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Registry HTTP ${response.code}" }
            return SiteRegistry().loadJson(response.body?.string().orEmpty())
        }
    }
}
