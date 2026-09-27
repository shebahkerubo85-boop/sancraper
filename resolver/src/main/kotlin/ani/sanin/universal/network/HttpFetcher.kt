package ani.sanin.universal.network

import ani.sanin.universal.validation.MediaValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

data class FetchResult(
    val status: Int,
    val body: String?,
    val contentType: String? = null,
    val contentLength: Long? = null,
    val finalUrl: String? = null,
    val retryAfterSeconds: Long? = null,
    val server: String? = null
) {
    val ok: Boolean get() = status == 200 || status == 206
    val isJson: Boolean get() = contentType?.contains("json", ignoreCase = true) == true
}

/**
 * Network access for the resolver.
 *
 * Two problems made the previous synchronous fetcher unreliable against real sites. It blocked the
 * calling thread inside a coroutine, and it treated a single failure as final: a 429 or a 503 from
 * a CDN was reported as "no media" rather than retried. Every call is now a suspending IO
 * operation with bounded retries and `Retry-After` support.
 */
class HttpFetcher(private val client: OkHttpClient) {

    private fun build(url: HttpUrl, referer: String?, extra: Map<String, String>): Request.Builder {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
        if (referer != null) b.header("Referer", referer)
        extra.forEach { (k, v) -> b.header(k, v) }
        return b
    }

    private suspend fun attempt(
        url: HttpUrl,
        referer: String?,
        extra: Map<String, String>,
        maxAttempts: Int
    ): FetchResult? {
        var last: FetchResult? = null
        for (i in 0 until maxAttempts) {
            if (i > 0) delay(backoffMillis(i, last?.retryAfterSeconds))
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(build(url, referer, extra).build()).execute().use { r ->
                        FetchResult(
                            status = r.code,
                            body = if (r.isSuccessful) r.body.string() else null,
                            contentType = r.header("Content-Type"),
                            contentLength = r.header("Content-Length")?.toLongOrNull(),
                            finalUrl = r.request.url.toString(),
                            retryAfterSeconds = parseRetryAfter(r.header("Retry-After")),
                            server = r.header("Server")
                        )
                    }
                }.getOrNull()
            }
            // A null result is a transport failure, which is always worth another attempt.
            if (result == null) continue
            if (result.ok) return result
            if (!isRetryable(result.status)) return result
            last = result
        }
        return last
    }

    suspend fun getResult(url: HttpUrl, referer: String? = null, extra: Map<String, String> = emptyMap()): FetchResult? =
        attempt(url, referer, extra, maxAttempts = 3)

    suspend fun get(url: HttpUrl, referer: String? = null): String? =
        getResult(url, referer)?.takeIf { it.ok }?.body

    suspend fun getText(url: String, referer: String? = null): String? =
        url.toHttpUrlOrNull()?.let { get(it, referer) }

    /**
     * Cheap verification that a candidate URL really serves media.
     *
     * Keeps the real body so the reported `Content-Length` is the full asset size, but reads only
     * a small prefix through `peekBody`, so validating a large file does not download it.
     */
    suspend fun probe(url: String, referer: String? = null): MediaValidator.Probe? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(build(parsed, referer, emptyMap()).build()).execute().use { r ->
                    MediaValidator.Probe(
                        status = r.code,
                        contentType = r.header("Content-Type"),
                        contentLength = r.body.contentLength().takeIf { it >= 0 },
                        bodyPrefix = runCatching { r.peekBody(PROBE_PREFIX_BYTES).string() }.getOrNull()
                    )
                }
            }.getOrNull()
        }
    }

    /** Reads a bounded amount of a resource as text, used for player scripts. */
    suspend fun bytes(url: String, referer: String? = null, maxBytes: Long = 2_000_000): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(build(parsed, referer, emptyMap()).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use null
                    val source = r.body.byteStream()
                    val buffer = ByteArray(16 * 1024)
                    val out = java.io.ByteArrayOutputStream()
                    var total = 0L
                    while (total < maxBytes) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                    out.toString("UTF-8")
                }
            }.getOrNull()
        }
    }

    private fun isRetryable(status: Int) = status == 429 || status == 408 || status in 500..599

    private fun backoffMillis(attempt: Int, retryAfterSeconds: Long?): Long {
        retryAfterSeconds?.let { return (it * 1000).coerceIn(0, 5_000) }
        return (300L * (1L shl (attempt - 1))).coerceAtMost(2_000)
    }

    private fun parseRetryAfter(header: String?): Long? {
        val seconds = header?.trim()?.toLongOrNull() ?: return null
        return seconds.coerceIn(0, 5)
    }

    companion object {
        const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36"
        private const val PROBE_PREFIX_BYTES = 2048L
    }
}
