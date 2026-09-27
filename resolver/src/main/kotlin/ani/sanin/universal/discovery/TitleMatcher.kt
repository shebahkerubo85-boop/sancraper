package ani.sanin.universal.discovery

import ani.sanin.universal.util.EpisodeParser

/**
 * Title similarity used to decide whether a crawled page describes the anime the user asked for.
 *
 * The score is asymmetric on purpose. Discovery searches *for* a title inside crawled pages, and
 * real pages decorate that title with season/episode/quality noise ("One Piece Episode 1",
 * "Attack on Titan Season 4"). Scoring purely on Jaccard similarity punished exactly the pages we
 * want, so the score is dominated by how much of the shorter (more specific) title is covered by
 * the longer one, with Jaccard as a secondary signal to keep unrelated titles apart.
 */
object TitleMatcher {
    private const val COVERAGE_WEIGHT = 0.75
    private const val JACCARD_WEIGHT = 0.25

    fun score(a: String, b: String): Double {
        val x = tokens(a)
        val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0

        val intersection = x.intersect(y).size
        if (intersection == 0) {
            // No shared tokens, but a whole-token containment still means one title is a
            // prefix/substring of the other ("One Piece" inside "OnePieceFT").
            val na = norm(a)
            val nb = norm(b)
            if (na.isNotBlank() && nb.isNotBlank() && (na.contains(nb) || nb.contains(na))) return 0.6
            return 0.0
        }

        val union = x.union(y).size
        val coverage = intersection.toDouble() / minOf(x.size, y.size)
        val jaccard = intersection.toDouble() / union
        val base = COVERAGE_WEIGHT * coverage + JACCARD_WEIGHT * jaccard

        // Mild bonus when one normalised title fully contains the other: the decorated page is
        // describing the same show, just with extra words around it.
        val na = norm(a)
        val nb = norm(b)
        val containsBonus = if (na.isNotBlank() && nb.isNotBlank() && (na.contains(nb) || nb.contains(na))) 0.05 else 0.0
        return minOf(1.0, base + containsBonus)
    }

    /** Significant tokens, with episode/quality noise removed. */
    private fun tokens(s: String): Set<String> =
        norm(s).split(' ').filter { it.isNotBlank() && !EpisodeParser.isNoiseToken(it) }.toSet()

    private fun norm(s: String) = s.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
