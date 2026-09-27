package ani.sanin.universal.registry

import kotlinx.serialization.Serializable

/**
 * Declarative description of a site's JSON API.
 *
 * The resolver core stays generic on purpose, but "generic" cannot mean "ignore the fact that some
 * sites expose their catalogue as JSON". Encoding that knowledge as data rather than code means a
 * new site can be supported by refreshing the registry document, and the remote registry can add
 * support without a resolver release. Every field is optional and an absent template simply means
 * "no API, use the crawler".
 */
@Serializable
data class ApiHints(
    /** Episode list endpoint. Supports `{id}` for the numeric id taken from the pasted URL. */
    val catalogUrlTemplate: String? = null,
    /** Path to the episode array inside the catalog response. `$` is the document root. */
    val listPath: String? = null,
    val numberKey: String? = null,
    val titleKey: String? = null,
    val urlKey: String? = null,
    /** Media endpoint for one episode. Supports `{id}` and `{episode}`. */
    val embedUrlTemplate: String? = null,
    /** Response keys that may hold a media URL, checked in order. */
    val mediaKeys: List<String> = emptyList(),
    /** Sent as `Referer` when calling the API. */
    val referer: String? = null
)

@Serializable
data class SiteDefinition(
    val id: String,
    val domains: List<String>,
    val searchUrl: String? = null,
    val aliases: List<String> = emptyList(),
    val enabled: Boolean = true,
    /** True when the site only lists/catalouges anime and does not host playable media itself. */
    val discoveryOnly: Boolean = false,
    val api: ApiHints? = null
)

@Serializable
data class SiteRegistryDocument(val version: Int = 1, val sites: List<SiteDefinition> = emptyList())
