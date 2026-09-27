package ani.sanin.universal.model

import kotlinx.serialization.Serializable

@Serializable data class BrowserObservation(
    val pageUrl:String, val finalUrl:String?=null, val title:String?=null,
    val mediaUrls:List<String> = emptyList(), val requestUrls:List<String> = emptyList(),
    val iframeUrls:List<String> = emptyList(), val scriptUrls:List<String> = emptyList(),
    val domText:String?=null, val headers:Map<String,String> = emptyMap()
)
