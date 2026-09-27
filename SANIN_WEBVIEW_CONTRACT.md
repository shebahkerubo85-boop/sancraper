# Sanin WebView contract

Sanin owns the Android WebView. The resolver is the server-side discovery, extraction and
normalization layer. This split exists because most anime sites build their player in JavaScript:
the resolver can read the catalogue, the episode list and any server-rendered media far more cheaply
than a WebView, but it cannot execute the page.

## Flow

1. Sanin receives a pasted website URL, the anime title and the episode.
2. `POST /v1/resolve`.
3. On `Success`, register the returned videos as a source for that anime, exactly like an
   extension-provided source, and stop.
4. On `BrowserRequired`, Sanin loads `diagnostics.escalateUrl` (or `pageUrl`) in its WebView.
5. Sanin collects the publicly exposed media and network URLs it sees.
6. `POST` those observations to `/v1/browser-observation`.
7. Map the returned URL/type/headers into Sanin's existing `Video` model.

`VideoNotFound` is a real negative result: candidates were extracted and then rejected by
validation. It is not a request to retry in a WebView.

## Observation payload

```json
{
  "pageUrl": "https://hianime.at/watch/attack-on-titan-240?ep=4402",
  "finalUrl": "https://megacloud.tv/embed-2/e-1/xYzAbC",
  "title": "Attack on Titan Episode 1",
  "mediaUrls":  ["https://cdn.test/hls/4402/master.m3u8"],
  "requestUrls":["https://cdn.test/hls/4402/1080p.m3u8", "https://ads.test/banner.mp4"],
  "iframeUrls": ["https://megacloud.tv/embed-2/e-1/xYzAbC"],
  "scriptUrls": ["https://cdn.test/player.js?v=3"],
  "domText":    "optional visible text, mined for media URLs",
  "headers":    {"Referer": "https://megacloud.tv/"}
}
```

Only `pageUrl` is required. Send whatever the WebView observed; the resolver filters it. Keeping
the capture broad is the point: `requestUrls` containing adverts and trackers is normal and
expected, and filtering is the resolver's job.

The resolver groups observations by resource so a stream seen several times stays one candidate,
drops adverts, prefers HLS then DASH then containers, and ranks by quality. An HLS **master**
playlist is returned as observed; if the highest-bandwidth variant is visible in the capture it
will be preferred automatically, otherwise the app's own player handles the master as usual.

`Referer` and `Origin` are frequently required for playback and are not always derivable, so pass
them in `headers` when the capture knows them.

## Type mapping

The resolver uses `CONTAINER | HLS | DASH`. Sanin's `VideoType` uses `CONTAINER | M3U8 | DASH`, so
`HLS` maps to `M3U8`.

## Escalation contract

`BrowserRequired.diagnostics` carries `requiresBrowser`, `escalateUrl` and `nextActions`
(`load-in-webview`, `solve-challenge`, `observe-media-requests`, `post-browser-observation`). The
app should treat `nextActions` as hints rather than a strict script, and should not re-run
`/v1/resolve` for the same URL after escalating: the WebView path is strictly more capable.

## Registry

A hosted `site-registry.json` can be refreshed by the resolver at runtime. Entries describe
domains, aliases, discovery hints and optional JSON API templates. The registry is deliberately
data-driven: site-specific behavior belongs in the registry document, not in the generic crawler.
