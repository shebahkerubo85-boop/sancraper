package ani.sanin.universal.registry

import kotlinx.serialization.Serializable
@Serializable data class SiteDefinition(val id:String,val domains:List<String>,val searchUrl:String?=null,val aliases:List<String> = emptyList(),val enabled:Boolean=true)
@Serializable data class SiteRegistryDocument(val version:Int=1,val sites:List<SiteDefinition> = emptyList())
