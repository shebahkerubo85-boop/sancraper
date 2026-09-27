package ani.sanin.universal

import ani.sanin.universal.crawl.ChallengeDetector
import ani.sanin.universal.registry.SiteRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteRegistryTest {

    private val registry: SiteRegistry = SiteRegistry.fromJson(UniversalResolver.bundledRegistryJson())

    @Test
    fun bundledRegistryLoads() {
        val sites = registry.all()
        assertTrue(sites.isNotEmpty(), "bundled site-registry.json must be on the classpath")
        val ids = sites.map { it.id }.toSet()
        listOf("mkissa", "kurono", "anikura", "senshi", "anikoto", "hianime").forEach {
            assertTrue(it in ids, "registry is missing compatibility target: $it")
        }
    }

    @Test
    fun mkissaStaysDiscoveryOnly() {
        val mkissa = registry.all().first { it.id == "mkissa" }
        assertTrue(mkissa.discoveryOnly, "MKissa is a catalog site and must not be treated as a media host")
    }

    @Test
    fun apiHintsDecode() {
        val senshi = registry.all().first { it.id == "senshi" }
        val api = assertNotNull(senshi.api, "senshi declares an API")
        assertEquals("https://senshi.to/episode-embeds/{id}/{episode}", api.embedUrlTemplate)
        assertTrue(api.mediaKeys.contains("url"))
    }

    @Test
    fun domainMatching() {
        assertNotNull(registry.match("https://senshi.to/anything"))
        assertNotNull(registry.match("https://www.hianime.at/watch/x"))
        assertNull(registry.match("https://example.com/"))
    }

    @Test
    fun challengesAreRecognised() {
        assertTrue(ChallengeDetector.isChallenge(403, "<title>Attention Required! | Cloudflare</title>", "cloudflare"))
        assertTrue(ChallengeDetector.isChallenge(503, "<h1>Just a moment...</h1>", "cloudflare"))
        assertTrue(ChallengeDetector.isChallenge(403, "checking your browser before accessing", null))
        assertEquals("cloudflare", ChallengeDetector.kind("cloudflare"))
        // An ordinary 404 or a real page must not be mistaken for a challenge.
        assertFalse(ChallengeDetector.isChallenge(404, "<h1>Not found</h1>", "nginx"))
        assertFalse(ChallengeDetector.isChallenge(200, "<html>Attack on Titan Episode 1</html>", "nginx"))
    }
}
