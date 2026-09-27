package ani.sanin.universal.registry

import kotlinx.serialization.json.Json
import java.net.URI
class SiteRegistry(private val definitions:List<SiteDefinition> = emptyList()) {
    private val json=Json{ignoreUnknownKeys=true}
    fun match(url:String):SiteDefinition? { val host=runCatching{URI(url).host?.lowercase()}.getOrNull()?:return null; return definitions.firstOrNull{d->d.enabled&&d.domains.any{host==it.lowercase()||host.endsWith(".${it.lowercase()}")}} }
    fun loadJson(raw:String)=SiteRegistry(json.decodeFromString<SiteRegistryDocument>(raw).sites)
    fun all()=definitions
    companion object { fun fromJson(raw:String)=SiteRegistry().loadJson(raw) }
}
