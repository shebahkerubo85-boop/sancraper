package ani.sanin.universal

import ani.sanin.universal.discovery.TitleMatcher
import kotlin.test.Test
import kotlin.test.assertTrue

class TitleMatcherTest {
    @Test
    fun normalizesAndMatchesTitles() {
        assertTrue(
            TitleMatcher.score("Attack-the-Titan", "Attack the Titan") > 0.8
        )
    }
}
