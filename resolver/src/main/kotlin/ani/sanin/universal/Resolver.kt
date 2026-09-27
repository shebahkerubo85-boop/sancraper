package ani.sanin.universal

import ani.sanin.universal.adapter.GenericSiteAdapter
import ani.sanin.universal.adapter.SiteAdapterRegistry
import ani.sanin.universal.model.AnimeCandidate
import ani.sanin.universal.model.ResolutionStage
import ani.sanin.universal.model.ResolveDiagnostics
import ani.sanin.universal.model.ResolveResult
import ani.sanin.universal.model.ResolvedVideo
import ani.sanin.universal.util.UrlUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Entry point for a pasted link.
 *
 * The flow is: treat the URL as media if it already is one, otherwise discover the anime, locate
 * the requested episode, extract media, and verify it. When the page turns out to need a real
 * browser the result is [ResolveResult.BrowserRequired] rather than a bare failure, so the app can
 * escalate into its WebView instead of showing a dead source.
 */
class UniversalResolver(
    private val client: OkHttpClient = defaultClient(),
    private val registry: SiteAdapterRegistry = SiteAdapterRegistry(),
    private val timeoutMillis: Long = 60_000
) {
    suspend fun resolve(startUrl: String, animeTitle: String, episodeNumber: Int? = null): ResolveResult {
        val u = startUrl.toHttpUrlOrNull() ?: return ResolveResult.InvalidUrl
        val site = u.host
        val attempts = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Already a media URL: nothing to discover.
        UrlUtil.mediaType(startUrl)?.let { direct ->
            attempts += "direct-media"
            val type = UrlUtil.mediaType(startUrl)
            return ResolveResult.Success(
                listOf(
                    ResolvedVideo(
                        url = UrlUtil.canonical(startUrl), type = type!!, quality = ani.sanin.universal.util.QualityParser.extract(startUrl),
                        referer = "${u.scheme}://${u.host}/", sourceUrl = startUrl, provider = site, score = 1.0
                    )
                ),
                ResolveDiagnostics(ResolutionStage.COMPLETE, site, attempts)
            )
        }

        return try {
            withTimeout(timeoutMillis) {
                val adapter = registry.find(u) ?: GenericSiteAdapter(deadlineMillis = (timeoutMillis * 0.7).toLong())

                attempts += "generic-discovery"
                val candidates = adapter.searchAnime(client, u, animeTitle)
                val anime = if (candidates.isNotEmpty()) candidates else listOf(AnimeCandidate(animeTitle, u.toString(), 0.5))

                if (episodeNumber == null) {
                    return@withTimeout ResolveResult.AnimeFound(
                        anime, ResolveDiagnostics(ResolutionStage.DISCOVERY, site, attempts)
                    )
                }

                var lastPage: String = u.toString()
                for (a in anime.take(5)) {
                    attempts += "episode-discovery:${a.url}"
                    val eps = adapter.findEpisodes(client, a)
                    if (eps.isEmpty()) continue

                    val exact = eps.filter { it.number == episodeNumber }.sortedByDescending { it.score }
                    val chosen = when {
                        exact.isNotEmpty() -> exact
                        eps.size == 1 -> {
                            warnings += "Requested episode $episodeNumber not listed; using the only available episode."
                            eps
                        }
                        else -> {
                            warnings += "Requested episode $episodeNumber not listed on this page."
                            emptyList()
                        }
                    }
                    if (chosen.isEmpty()) continue

                    for (ep in chosen.take(4)) {
                        lastPage = ep.url
                        attempts += "media-extraction:${ep.url}"
                        val videos = adapter.extractEpisode(client, ep)
                        if (videos.isNotEmpty()) {
                            val enriched = videos.map {
                                it.copy(title = a.title, episodeNumber = episodeNumber)
                            }.sortedWith(
                                compareByDescending<ResolvedVideo> { it.score }.thenByDescending { it.quality ?: 0 }
                            ).take(8)
                            return@withTimeout ResolveResult.Success(
                                enriched, ResolveDiagnostics(ResolutionStage.COMPLETE, site, attempts, warnings)
                            )
                        }
                    }
                }

                // Nothing playable server-side. Most often this means the player is injected by
                // JavaScript, which is exactly the case the app can still solve with its WebView.
                ResolveResult.BrowserRequired(
                    pageUrl = lastPage,
                    reason = "No playable media was found in the server-rendered HTML. The player is most likely injected by JavaScript or served behind a challenge; load this page in a WebView and post the observed media to /v1/browser-observation.",
                    diagnostics = ResolveDiagnostics(
                        stage = ResolutionStage.BROWSER, site = site, attempts = attempts, warnings = warnings,
                        requiresBrowser = true, escalateUrl = lastPage,
                        nextActions = listOf("load-in-webview", "observe-media-requests", "post-browser-observation")
                    )
                )
            }
        } catch (_: TimeoutCancellationException) {
            ResolveResult.BrowserRequired(
                pageUrl = u.toString(),
                reason = "Resolution exceeded ${timeoutMillis}ms before a playable candidate was confirmed. Continuing in a WebView is usually faster than another crawl.",
                diagnostics = ResolveDiagnostics(
                    stage = ResolutionStage.BROWSER, site = site, attempts, warnings + "deadline exceeded",
                    requiresBrowser = true, escalateUrl = u.toString(),
                    nextActions = listOf("load-in-webview", "observe-media-requests", "post-browser-observation")
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResolveResult.NetworkError(
                e.message ?: "Unknown error",
                ResolveDiagnostics(ResolutionStage.INPUT, site, attempts, warnings)
            )
        }
    }

    companion object {
        fun defaultClient() = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}
