package ani.sanin.universal.util

object EpisodeParser {
    private val patterns=listOf(Regex("(?i)(?:episode|ep|e)[\\s._-]*(\\d{1,4})(?:\\b|$)"), Regex("(?i)(?:/|[-_ ])(\\d{1,4})(?:[/?#._-]|$)"))
    fun extract(s:String):Int?=patterns.asSequence().mapNotNull{it.find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()}.firstOrNull()
}
object QualityParser {
    fun extract(s:String):Int?=Regex("(?i)(?:^|[._-])(\\d{3,4})p(?:[._-]|$)").find(s)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Regex("(?i)(?:^|[._-])(4k|2160|1440|1080|720|480)(?:[._-]|$)").find(s)?.groupValues?.getOrNull(1)?.let{if(it.equals("4k",true))2160 else it.toIntOrNull()}
}
object UrlUtil {
    fun canonical(s:String)=s.trim().trim('"', '\'', ')', ']', '}', ',')
    fun mediaType(s:String):ani.sanin.universal.model.VideoType? { val p=s.substringBefore('?').substringBefore('#').lowercase(); return when { p.endsWith(".m3u8")->ani.sanin.universal.model.VideoType.HLS; p.endsWith(".mpd")->ani.sanin.universal.model.VideoType.DASH; p.endsWith(".mp4")||p.endsWith(".webm")->ani.sanin.universal.model.VideoType.CONTAINER; else->null } }
    fun host(s:String)=runCatching{java.net.URI(s).host?.lowercase()}.getOrNull()
}
