# UniversalAnimeResolver v3

A universal anime source resolver for Sanin. Paste a link to an anime site, and the resolver turns
it into a playable source: it discovers the anime, finds the requested episode, extracts the media,
verifies it, and returns a ranked list of stream URLs. The result registers in the app exactly like
a Cloudstream extension, so any site that can be scraped becomes a source for that anime.

## Pipeline

```
pasted URL
  -> media URL? (return directly)
  -> registry API hints? (JSON catalogue / episode -> media)
  -> generic discovery (crawl, match title)
  -> episode matching (query params, selects, data attrs, JSON-LD)
  -> media extraction (player slots, og:video, JSON-LD, inline player JSON, raw sweep)
  -> HLS master expansion (highest bandwidth variant)
  -> candidate validation (reject adverts, stubs, stale playlists)
  -> MP4 / HLS / DASH + headers + referer
```

## When a browser is required

Most modern anime sites inject their player with JavaScript. Verified against the compatibility
targets: `hianime.at` serves a watch page containing **no** media URL, no iframe and no episode
list in its HTML, and the vidcloud sources API answers plain HTTP with a Cloudflare challenge.

The resolver does not pretend otherwise. When it cannot confirm a playable candidate it returns
`BrowserRequired` with the exact page to load, instead of an ambiguous "not found":

```json
{
  "ok": false,
  "result": {
    "type": "BrowserRequired",
    "pageUrl": "https://hianime.at/watch/attack-on-titan-240?ep=4402",
    "reason": "No playable media was found in the server-rendered HTML...",
    "diagnostics": {
      "requiresBrowser": true,
      "escalateUrl": "https://hianime.at/watch/attack-on-titan-240?ep=4402",
      "nextActions": ["load-in-webview", "observe-media-requests", "post-browser-observation"]
    }
  }
}
```

A Cloudflare/DDoS-guard interstitial is reported separately (`reason` mentions the challenge) since
a WebView can solve it.

## API

- `GET  /health`
- `GET  /v1/capabilities`
- `POST /v1/resolve`                 `{ url, animeTitle, episode? }`
- `POST /v1/browser-observation`     browser capture from the app's WebView

### Recommended client flow

1. `POST /v1/resolve` with the pasted URL.
2. On `Success`, register the returned URLs as the source and stop.
3. On `BrowserRequired`, load `escalateUrl` in the app's WebView, collect the media it requests
   (`.m3u8`, `.mpd`, `.mp4`, `.webm`), and `POST /v1/browser-observation`.
4. Treat `VideoNotFound` as a genuine miss: the resolver verified candidates and rejected them.

## Scope and honesty

Supports public, non-DRM media. It does not bypass DRM, CAPTCHA, authentication or paywalls, and it
does not invent streams. `mkissa` is a discovery/catalog target and is marked `discoveryOnly` in the
registry; if a site exposes no playable media, the resolver reports that instead of returning a
candidate that will not play. Candidates are probed before being reported, because a source that
fails to play is worse for the user than no source at all.

## Site registry

`site-registry.json` is data, not code. It ships on the classpath and is refreshable at runtime via
`SANIN_SITE_REGISTRY_URL`, so supporting another API-driven site does not need a resolver release.
The core crawler stays generic: registry entries describe domains, a search URL, whether a site is
discovery-only, and optional `api` templates for sites whose catalogue or episode resolution is
only exposed as JSON.

## Compatibility suite

MKissa, Kurono, Anikura, Senshi, AniKoto, HiAnime.at. `anikura.club` did not respond during
development, so it is unverified beyond registry wiring.

## Build

Requires JDK 17+. `gradle test` and `gradle build` run in GitHub Actions. Local compilation on the
development machine was not available, so correctness is established by the CI run and unit tests
rather than a local build.
