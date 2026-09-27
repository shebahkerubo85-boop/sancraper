package ani.sanin.universal.discovery

import ani.sanin.universal.crawl.CrawledPage
import ani.sanin.universal.model.AnimeCandidate
import ani.sanin.universal.model.EpisodeCandidate
import ani.sanin.universal.util.EpisodeParser
import ani.sanin.universal.util.UrlUtil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

object AnimeDiscovery {
    fun find(pages: List<CrawledPage>, title: String): List<AnimeCandidate> {
        val out = mutableListOf<AnimeCandidate>()
        for (p in pages) {
            val base = p.url.toString()
            val candidates = if (p.isJson || !p.html.trimStart().startsWith("<")) {
                titlesFromJson(p.html)
            } else {
                titlesFromHtml(p.html, base)
            }
            for ((text, weight) in candidates) {
                if (text.isBlank()) continue
                val score = TitleMatcher.score(title, text) * weight
                if (score >= 0.45) out += AnimeCandidate(clean(text), base, score)
            }
        }
        return out
            .groupBy { UrlUtil.resourceKey(it.url) }
            .values
            .map { it.maxBy { c -> c.score } }
            .sortedByDescending { it.score }
            .take(12)
    }

    private fun titlesFromHtml(html: String, base: String): List<Pair<String, Double>> {
        val out = mutableListOf<Pair<String, Double>>()
        runCatching {
            val d = Jsoup.parse(html, base)
            d.title().takeIf { it.isNotBlank() }?.let { out += it to 1.0 }
            d.select("h1,h2,h3,[itemprop=name],[class*=title],[class*=name]")
                .forEach { e -> e.text().takeIf { it.isNotBlank() && it.length < 160 }?.let { out += it to 0.9 } }
            d.select("meta[property=og:title],meta[name=twitter:title]")
                .forEach { e -> e.attr("content").takeIf { it.isNotBlank() }?.let { out += it to 0.96 } }
        }
        return out
    }

    /** Title-ish strings inside a JSON document, for sites that only expose a JSON view. */
    private fun titlesFromJson(body: String): List<Pair<String, Double>> {
        val out = mutableListOf<Pair<String, Double>>()
        TITLE_KEYS.forEach { key ->
            Regex("\"$key\"\\s*:\\s*\"([^\"\\\\]{2,120})\"", RegexOption.IGNORE_CASE)
                .findAll(body)
                .forEach { m ->
                    val v = m.groupValues[1].replace("\\\\/", "/").replace("\\\\\"", "\"")
                    out += v to 0.9
                }
        }
        return out
    }

    private val TITLE_KEYS = listOf("title", "name", "anime_title", "animeTitle", "slug")

    fun clean(s: String) = s.replace(Regex("(?i)\\s*[-|]\\s*(watch|stream|online|free|hd).*?$"), "").trim()
}

object EpisodeDiscovery {

    /**
     * Finds the episode list on a page.
     *
     * The previous implementation only read `a[href]` and derived the number from the path, which
     * silently mis-numbered the most common modern layout: an opaque internal id in the path and
     * the real episode in a query parameter (`/watch/show-240?ep=4402` returned episode 240).
     * Query parameters are now preferred, and `<select>` lists, data attributes and JSON-LD are
     * read as well.
     */
    fun find(html: String, base: String): List<EpisodeCandidate> {
        val found = mutableListOf<EpisodeCandidate>()
        runCatching {
            val d = Jsoup.parse(html, base)

            d.select("a[href]").forEach { a ->
                val href = a.attr("href")
                if (href.isBlank()) return@forEach
                val absolute = UrlUtil.resolve(base, href) ?: return@forEach
                val text = a.text().trim()
                val hint = EpisodeParser.best(absolute, text.ifBlank { null }) ?: return@forEach
                found += EpisodeCandidate(hint.number, text.ifBlank { null }, absolute, score(text, absolute, hint))
            }

            d.select("select option[value], option[value]").forEach { o ->
                val value = o.attr("value")
                if (value.isBlank()) return@forEach
                val absolute = UrlUtil.resolve(base, value) ?: return@forEach
                if (!absolute.startsWith("http", true)) return@forEach
                val text = o.text().trim()
                val hint = EpisodeParser.best(absolute, text.ifBlank { null }) ?: return@forEach
                found += EpisodeCandidate(hint.number, text.ifBlank { null }, absolute, score(text, absolute, hint))
            }

            d.select("[data-episode],[data-ep],[data-episode-number],[data-number]").forEach { e ->
                val raw = e.attr("data-episode").ifBlank { e.attr("data-ep") }
                    .ifBlank { e.attr("data-episode-number") }.ifBlank { e.attr("data-number") }
                val number = raw.trim().toIntOrNull() ?: return@forEach
                val target = e.attr("data-href").ifBlank { e.attr("href") }
                if (target.isBlank()) return@forEach
                val absolute = UrlUtil.resolve(base, target) ?: return@forEach
                val text = e.text().trim()
                found += EpisodeCandidate(number, text.ifBlank { null }, absolute, score(text, absolute, EpisodeParser.EpisodeHint(number, "attr")))
            }
        }
        found += fromJsonLd(html, base)
        return dedupe(found)
    }

    /** `application/ld+json` episode lists, used by WordPress and CMS based anime sites. */
    private fun fromJsonLd(html: String, base: String): List<EpisodeCandidate> {
        val out = mutableListOf<EpisodeCandidate>()
        runCatching {
            Jsoup.parse(html, base).select("script[type=application/ld+json]").forEach { script ->
                val raw = script.data().trim()
                if (raw.isBlank()) return@forEach
                val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return@forEach
                walkLd(root, base, out)
            }
        }
        return out
    }

    private fun walkLd(el: JsonElement, base: String, out: MutableList<EpisodeCandidate>) {
        when (el) {
            is JsonArray -> el.forEach { walkLd(it, base, out) }
            is JsonObject -> {
                val type = el["@type"]?.asString()?.lowercase()
                if (type != null && (type.contains("episode") || type.contains("tvseries")
                            || type.contains("season") || type.contains("series"))) {
                    el.string("url")?.let { u ->
                        val absolute = UrlUtil.resolve(base, u)
                        val number = el.int("episodeNumber") ?: el.int("position")
                        if (absolute != null && number != null && number > 0) {
                            val name = el.string("name")
                            out += EpisodeCandidate(number, name, absolute, 0.8)
                        }
                    }
                }
                el.values.forEach { walkLd(it, base, out) }
            }
            else -> Unit
        }
    }

    private fun dedupe(all: List<EpisodeCandidate>): List<EpisodeCandidate> =
        all.filter { it.url.startsWith("http", true) }
            .groupBy { UrlUtil.resourceKey(it.url) }
            .values
            .map { it.maxBy { c -> c.score } }
            .sortedWith(compareByDescending<EpisodeCandidate> { it.score }.thenBy { it.number ?: Int.MAX_VALUE })

    private fun score(text: String, url: String, hint: EpisodeParser.EpisodeHint): Double {
        var s = 0.6
        if (hint.origin == "query") s += 0.25
        if (text.isNotBlank() && Regex("(?i)\\b(?:episode|ep|e)\\s*${hint.number}\\b").containsMatchIn(text)) s += 0.2
        if (text.contains(hint.number.toString())) s += 0.05
        if (url.contains("/episode", true) || url.contains("/watch", true) || url.contains("/ep", true)) s += 0.05
        return s
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.toDoubleOrNull()?.toInt()

    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.content
}
