# UniversalAnimeResolver v3

A public-media anime discovery and resolution service designed for Sanin. It accepts a site homepage, search page, anime page, episode page, or direct media URL and escalates through generic discovery, episode matching, media extraction, and Sanin WebView observations.

## Scope
Supports public, non-DRM media. It does not bypass DRM, CAPTCHA, authentication, paywalls, or other access controls. MKissa is treated as a discovery/catalog target; if a site does not expose playable media, the resolver reports that instead of inventing a stream.

## Pipeline
`input URL -> site fingerprint -> generic discovery -> anime identity -> episode matching -> embed/provider discovery -> media extraction -> candidate scoring -> MP4/HLS/DASH`

For browser-required sites: `Sanin WebView -> /v1/browser-observation -> candidate scoring -> ResolvedVideo`.

Rhino is a bounded JavaScript calculation layer for deterministic page/player logic. It is not a browser.

## API
- `GET /health`
- `GET /v1/capabilities`
- `POST /v1/resolve`
- `POST /v1/browser-observation`

## Compatibility suite
MKissa, Kurono, Anikura, Senshi, AniKoto, HiAnime.at.

## Build
Requires JDK 17+. Run `./gradlew test` and `./gradlew build` in GitHub Actions or a Gradle-enabled environment. The current development environment does not have network access to download Gradle/dependencies, so local compilation was not falsely claimed as verified here.
