package ani.sanin.universal

import ani.sanin.universal.discovery.EpisodeDiscovery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for false-positive episode links observed against live sites.
 *
 * Anime pages embed "you might also like" carousels. Two distinct failure modes came out of testing
 * that layout, and both produced confidently wrong episode URLs:
 *
 *  - a series page (`/attack-on-titan-final-season-1372`) advertised with the link text
 *    "Episode 1" was reported as episode 1, so the client was sent to a different show;
 *  - unrelated watch pages in the same carousels (`/watch/colorful-the-motion-picture-1045`)
 *    were reported as episodes of the requested title.
 */
class EpisodeDiscoveryTest {

    private fun numbersFor(html: String, base: String) =
        EpisodeDiscovery().find(html, base).map { it.number to it.url }.toSet()

    @Test
    fun `series page link labelled Episode 1 is not an episode`() {
        val html = """
            <a href="/attack-on-titan-final-season-1372">Episode 1</a>
        """.trimIndent()
        val found = numbersFor(html, "https://hianime.at/attack-on-titan-240")
        assertTrue(
            found.none { it.second.contains("final-season-1372") },
            "a bare trailing path number must not qualify a series page as an episode, got $found"
        )
    }

    @Test
    fun `episode query parameter is accepted regardless of route shape`() {
        val html = """<a href="/watch/attack-on-titan-240?ep=4402">Watch</a>"""
        val found = numbersFor(html, "https://hianime.at/attack-on-titan-240")
        assertTrue(found.any { it.first == 4402 }, "expected episode 4402, got $found")
    }

    @Test
    fun `episode shaped routes are accepted`() {
        val html = """
            <a href="/one-piece-episode-5">One Piece</a>
            <a href="/watch/bleach/episode-12">Bleach</a>
            <a href="/naruto/episode-2200">Naruto</a>
        """.trimIndent()
        val found = numbersFor(html, "https://anikoto.me/")
        assertTrue(found.any { it.first == 5 }, "path episode route should parse, got $found")
        assertTrue(found.any { it.first == 12 }, "nested episode route should parse, got $found")
        assertTrue(found.any { it.first == 2200 }, "numeric episode route should parse, got $found")
    }

    @Test
    fun `unrelated related carousel entries are dropped`() {
        val html = """
            <a href="/watch/attack-on-titan-240?ep=4402">Episode 1</a>
            <a href="/watch/colorful-the-motion-picture-1045">Movie</a>
            <a href="/watch/noblesse-pamyeol-ui-sijak-3095">Episode 2</a>
            <a href="/watch/hunter-x-hunter-1-1393">Episode 3</a>
        """.trimIndent()
        val found = numbersFor(html, "https://hianime.at/attack-on-titan-240")
        assertTrue(found.any { it.first == 4402 }, "the real episode must survive, got $found")
        assertTrue(
            found.none { it.second.contains("hunter-x-hunter") },
            "a different show in the carousel must be dropped, got $found"
        )
        assertTrue(
            found.none { it.second.contains("colorful") },
            "a movie in the carousel must be dropped, got $found"
        )
    }

    @Test
    fun `sibling seasons of the same show are kept as fallbacks`() {
        val html = """
            <a href="/watch/attack-on-titan-240?ep=4402">Episode 1</a>
            <a href="/watch/attack-on-titan-season-2-865">Episode 2</a>
        """.trimIndent()
        val found = numbersFor(html, "https://hianime.at/attack-on-titan-240")
        assertEquals(2, found.size, "same-franchise entries should remain, got $found")
    }

    @Test
    fun `scoping fails open when the page slug has no usable tokens`() {
        // Numeric ids give no slug tokens to compare against; the full list must be kept
        // rather than silently dropping every episode.
        val html = """
            <a href="/watch/xyz-1?ep=1001">Episode 1</a>
            <a href="/watch/abc-2?ep=2002">Episode 2</a>
        """.trimIndent()
        val found = numbersFor(html, "https://example.test/anime/1")
        assertEquals(2, found.size, "expected both candidates kept, got $found")
    }
}
