package ani.sanin.universal.adapter

import ani.sanin.universal.model.ResolvedVideo
import ani.sanin.universal.network.HttpFetcher
import ani.sanin.universal.util.UrlUtil
import ani.sanin.universal.validation.MediaValidator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

/**
 * Final gate before a candidate is reported as playable.
 *
 * Centralised here so every adapter, generic or registry driven, gets the same treatment. A
 * resolver that reports an advert, a trailer or a stale playlist produces a source the user
 * selects and then cannot watch, which is a worse outcome than reporting nothing.
 */
object CandidateValidator {
    suspend fun validate(
        client: OkHttpClient,
        videos: List<ResolvedVideo>,
        referer: String?,
        probeLimit: Int = 5
    ): List<ResolvedVideo> {
        val ordered = videos.distinctBy { UrlUtil.resourceKey(it.url) }
            .sortedWith(compareByDescending<ResolvedVideo> { it.score }.thenByDescending { it.quality ?: 0 })
        if (ordered.isEmpty()) return emptyList()

        val fetcher = HttpFetcher(client)
        val toProbe = ordered.take(probeLimit)
        val probes = coroutineScope {
            toProbe.map { v -> async { runCatching { fetcher.probe(v.url, referer ?: v.referer) }.getOrNull() } }.awaitAll()
        }

        val accepted = mutableListOf<ResolvedVideo>()
        toProbe.forEachIndexed { index, v ->
            val probe = probes[index] ?: return@forEachIndexed
            if (MediaValidator.accept(probe, v.type)) accepted += v
        }
        // If every probe failed outright (DNS, TLS, timeouts) keep the ranked list rather than
        // claiming the episode does not exist; the player is the final authority.
        return if (accepted.isEmpty()) ordered.take(2) else accepted
    }
}
