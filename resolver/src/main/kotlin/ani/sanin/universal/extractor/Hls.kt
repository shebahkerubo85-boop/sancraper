package ani.sanin.universal.extractor

/**
 * Minimal HLS playlist parsing.
 *
 * Previously an `.m3u8` URL was returned verbatim and called "HLS". For a *master* playlist that
 * is not playable on its own: the player is handed a list of variant streams with no selection
 * logic. Resolving the master down to its highest-bandwidth variant is what turns a detected
 * playlist into something the app can actually play.
 */
object Hls {
    data class Variant(val url: String, val bandwidth: Long, val resolution: String?, val isIframe: Boolean)

    data class Playlist(val isMaster: Boolean, val variants: List<Variant>, val mediaSegments: Int) {
        val best: Variant? get() = variants.filter { !it.isIframe }.maxByOrNull { it.bandwidth }
            ?: variants.maxByOrNull { it.bandwidth }
    }

    private fun attr(line: String, name: String): String? {
        val key = "$name="
        var i = line.indexOf(key)
        while (i >= 0) {
            if (i == 0 || !line[i - 1].isLetterOrDigit() && line[i - 1] != '_') {
                var j = i + key.length
                if (j < line.length && line[j] == '"') {
                    val end = line.indexOf('"', j + 1)
                    if (end > j) return line.substring(j + 1, end)
                }
                var k = j
                while (k < line.length && line[k] != ',') k++
                return line.substring(j, k).trim().takeIf { it.isNotEmpty() }
            }
            i = line.indexOf(key, i + 1)
        }
        return null
    }

    fun parse(body: String?): Playlist? {
        val text = body ?: return null
        if (!text.trimStart().startsWith("#EXTM3U")) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.none { it.startsWith("#EXT-X-STREAM-INF") }) {
            return Playlist(isMaster = false, variants = emptyList(), mediaSegments = lines.count { !it.startsWith("#") })
        }
        val variants = mutableListOf<Variant>()
        for ((index, line) in lines.withIndex()) {
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val next = lines.drop(index + 1).firstOrNull { !it.startsWith("#") } ?: continue
            val bandwidth = attr(line, "BANDWIDTH")?.toLongOrNull() ?: 0L
            variants += Variant(
                url = next,
                bandwidth = bandwidth,
                resolution = attr(line, "RESOLUTION"),
                isIframe = attr(line, "URI") != null
            )
        }
        return Playlist(isMaster = true, variants = variants, mediaSegments = 0)
    }
}
