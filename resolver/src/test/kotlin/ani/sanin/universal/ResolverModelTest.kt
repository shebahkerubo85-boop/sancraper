package ani.sanin.universal

import ani.sanin.universal.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolverModelTest {
 @Test fun mediaTypes(){ assertEquals(VideoType.HLS,ani.sanin.universal.util.UrlUtil.mediaType("https://x.test/a.m3u8?token=1")); assertEquals(VideoType.DASH,ani.sanin.universal.util.UrlUtil.mediaType("https://x.test/a.mpd")); assertEquals(VideoType.CONTAINER,ani.sanin.universal.util.UrlUtil.mediaType("https://x.test/a.mp4")) }
 @Test fun episodeParser(){ assertEquals(12,ani.sanin.universal.util.EpisodeParser.extract("Naruto Episode 12")); assertEquals(1080,ani.sanin.universal.util.QualityParser.extract("show-1080p.mp4")) }
}
