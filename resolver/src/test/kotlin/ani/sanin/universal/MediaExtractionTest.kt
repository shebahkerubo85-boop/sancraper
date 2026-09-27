package ani.sanin.universal

import ani.sanin.universal.extractor.AdFilter
import ani.sanin.universal.extractor.Hls
import ani.sanin.universal.model.VideoType
import ani.sanin.universal.validation.MediaValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaExtractionTest {
    @Test
    fun advertisesAreFiltered() {
        assertTrue(AdFilter.isAd("https://page.test/ads/banner.mp4"))
        assertTrue(AdFilter.isAd("https://doubleclick.net/pixel.mp4"))
        assertTrue(AdFilter.isAd("https://cdn.test/video.mp4?ad=1"))
        assertTrue(AdFilter.isAd("https://track.test/click?url=https://x.test/a.mp4"))
    }

    @Test
    fun realMediaIsNotFiltered() {
        assertFalse(AdFilter.isAd("https://cdn.test/hls/episode-1/master.m3u8"))
        assertFalse(AdFilter.isAd("https://s.vidcloud.se/_v1/sources?id=46"))
        assertFalse(AdFilter.isAd("https://x.test/download/naruto-episode-12-1080p.mp4"))
    }

    @Test
    fun hlsMasterResolvesToBestVariant() {
        val body = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
            mid/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5800000,RESOLUTION=1920x1080
            hi/index.m3u8
        """.trimIndent()
        val playlist = Hls.parse(body)
        assertNotNull(playlist)
        assertTrue(playlist.isMaster)
        assertEquals(3, playlist.variants.size)
        assertEquals("hi/index.m3u8", playlist.best?.url)
        assertEquals(5800000L, playlist.best?.bandwidth)
    }

    @Test
    fun hlsMediaPlaylistHasNoVariants() {
        val playlist = Hls.parse("#EXTM3U\n#EXTINF:4.0,\nseg1.ts\n#EXTINF:4.0,\nseg2.ts\n")
        assertNotNull(playlist)
        assertFalse(playlist.isMaster)
        assertEquals(2, playlist.mediaSegments)
        assertNull(playlist.best)
    }

    @Test
    fun nonPlaylistBodyIsRejected() {
        assertNull(Hls.parse("<html><body>not a playlist</body></html>"))
        assertNull(Hls.parse(null))
    }

    @Test
    fun validatorAcceptsRealMedia() {
        assertTrue(MediaValidator.accept(
            MediaValidator.Probe(200, "video/mp4", 50_000_000L, null), VideoType.CONTAINER))
        assertTrue(MediaValidator.accept(
            MediaValidator.Probe(206, "application/vnd.apple.mpegurl", 4096L, "#EXTM3U"), VideoType.HLS))
    }

    @Test
    fun validatorRejectsPlayerPagesAndStubs() {
        // The URL had a media extension but the server returned the player page.
        assertFalse(MediaValidator.accept(
            MediaValidator.Probe(200, "text/html", 216_000L, "<!doctype html><html>"), VideoType.CONTAINER))
        // Tracking pixel sized response.
        assertFalse(MediaValidator.accept(
            MediaValidator.Probe(200, "video/mp4", 1024L, null), VideoType.CONTAINER))
        assertFalse(MediaValidator.accept(
            MediaValidator.Probe(404, null, null, null), VideoType.CONTAINER))
        // Claims HLS but is not a playlist.
        assertFalse(MediaValidator.accept(
            MediaValidator.Probe(200, null, 900L, "<html>"), VideoType.HLS))
    }
}
