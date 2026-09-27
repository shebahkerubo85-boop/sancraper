# Sanin WebView contract

Sanin owns the Android WebView. The resolver is the server-side discovery
and normalization layer.

## Flow

1. Sanin receives a website URL, anime title and episode.
2. Call `POST /v1/resolve`.
3. If normal crawling succeeds, use the returned `ResolvedVideo`.
4. If browser execution is needed, Sanin loads the site in its WebView.
5. Sanin collects publicly exposed media/network URLs.
6. POST those observations to `/v1/browser-observation`.
7. Map the returned URL/type/headers into Sanin's existing `Video` model.

## Future registry flow

A hosted `site-registry.json` can be periodically refreshed by the resolver.
Registry entries describe domains, aliases and discovery hints. This lets
site support evolve independently of the resolver core.

The registry is deliberately data-driven: site-specific behavior belongs
in adapters, not in the generic crawler.
