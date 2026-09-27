package ani.sanin.universal
import ani.sanin.universal.discovery.TitleMatcher
import ani.sanin.universal.util.*
import kotlin.test.*
class UtilTest{@Test fun episode(){assertEquals(12,EpisodeParser.extract("Episode 12"));assertEquals(7,EpisodeParser.extract("/ep-7"));assertEquals(4,EpisodeParser.extract("E04"))}@Test fun title(){assertEquals(1.0,TitleMatcher.score("One Piece","One Piece"));assertTrue(TitleMatcher.score("One Piece","One Piece Episode 1")>.8)}}
