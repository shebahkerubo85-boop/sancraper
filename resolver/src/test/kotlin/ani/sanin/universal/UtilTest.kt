package ani.sanin.universal

import ani.sanin.universal.discovery.TitleMatcher
import ani.sanin.universal.util.EpisodeParser
import ani.sanin.universal.util.HostScope
import ani.sanin.universal.util.UrlUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilTest {
    @Test
    fun episode() {
        assertEquals(12, EpisodeParser.extract("Episode 12"))
        assertEquals(7, EpisodeParser.extract("/ep-7"))
        assertEquals(4, EpisodeParser.extract("E04"))
    }

    @Test
    fun title() {
        assertEquals(1.0, TitleMatcher.score("One Piece", "One Piece"))
        assertTrue(TitleMatcher.score("One Piece", "One Piece Episode 1") > 0.8)
    }

    @Test
    fun unrelatedTitlesScoreLow() {
        assertEquals(0.0, TitleMatcher.score("One Piece", "Bleach"))
        assertTrue(TitleMatcher.score("Naruto", "Bleach: Thousand Year Blood War") < 0.3)
    }

    @Test
    fun decoratedTitlesStillMatch() {
        assertTrue(TitleMatcher.score("Attack on Titan", "Attack on Titan Season 4") > 0.8)
        assertTrue(TitleMatcher.score("Jujutsu Kaisen", "Watch Jujutsu Kaisen Online HD") > 0.8)
    }

    @Test
    fun episodeFromQueryParameter() {
        // hianime.at/watch/attack-on-titan-240?ep=4402 : 240 is the anime id, 4402 the episode.
        assertEquals(4402, EpisodeParser.fromQuery("https://hianime.at/watch/attack-on-titan-240?ep=4402"))
        assertEquals(12, EpisodeParser.fromQuery("https://x.test/watch/show?episode=12"))
        assertNull(EpisodeParser.fromQuery("https://x.test/watch/show?ep=abc"))
        assertNull(EpisodeParser.fromQuery("https://x.test/watch/show"))
    }

    @Test
    fun episodeHintPrefersQueryOverPath() {
        val hint = EpisodeParser.best("https://hianime.at/watch/attack-on-titan-240?ep=4402")
        assertEquals(4402, hint?.number)
        assertEquals("query", hint?.origin)
    }

    @Test
    fun registrableDomain() {
        assertEquals("example.com", HostScope.registrable("www.example.com"))
        assertEquals("example.com", HostScope.registrable("cdn.eu.example.com"))
        // Multi-part public suffixes must not collapse to the suffix itself.
        assertEquals("example.co.uk", HostScope.registrable("www.example.co.uk"))
        assertEquals("example.com.au", HostScope.registrable("example.com.au"))
        assertEquals("192.168.0.1", HostScope.registrable("192.168.0.1"))
    }

    @Test
    fun sameSite() {
        assertTrue(HostScope.sameSite("www.hianime.at", "hianime.at"))
        assertTrue(HostScope.sameSite("cdn.hianime.at", "hianime.at"))
        assertFalse(HostScope.sameSite("hianime.at", "hianime.tv"))
        assertTrue(HostScope.sameSite("a.b.example.co.uk", "example.co.uk"))
    }

    @Test
    fun relativeUrlResolution() {
        assertEquals(
            "https://x.test/watch/ep-1",
            UrlUtil.resolve("https://x.test/anime/one-piece", "/watch/ep-1")
        )
        assertEquals(
            "https://cdn.test/a.mp4",
            UrlUtil.resolve("https://x.test/", "https://cdn.test/a.mp4")
        )
        assertNull(UrlUtil.resolve("https://x.test/", "javascript:void(0)"))
    }
}
