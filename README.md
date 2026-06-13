# rv-monitor

Watches RV-rental providers for units available on **specific dates** and emails
you when a new match appears. Built as a sibling to `banff-monitor` — same Spring
Boot / Gradle / Docker shape, same polite-polling + dedup + email-alert design.

## How it works

Each **watch** (in `application.yml` under `rv.watches`) is a saved search:
location + pickup/drop dates + filters (max nightly price, radius, pet-friendly).
A scheduled poller runs every enabled watch through its provider each cycle,
applies the filters, and emails when a listing shows up that it hasn't already
alerted on. If a listing disappears and later returns, it re-alerts.

### Providers

| key         | status        | how it reads availability |
|-------------|---------------|---------------------------|
| `rvezy`     | **implemented** | Discovery via the public server-side-rendered search page (`www.rvezy.com/rv-search?...&DateStart=&DateEnd=`), parsed from the Nuxt `__NUXT_DATA__` payload — no auth. Optional authenticated **enrichment**: if `RVEZY_BEARER_TOKEN` is set, each *filtered match* is enriched via `api.rvezy.com/api/rvlistings/{id}` with fields the SSR omits — **Length**, Make/Model — auto-refreshing via `RVEZY_REFRESH_TOKEN`. Verified live. |
| `outdoorsy` | **implemented** | Public JSON API `api.outdoorsy.com/v0/rentals` (`near=lat,lng&radius=&date_from=&date_to=&currency=CAD`). Clean JSON, prices in cents, distance computed via haversine. |
| `canadream` | **experimental** | Wired to THL's "cosmos" REST platform (`cosmos-alb-prod-2.aws.thlonline.com`, base discovered via `canadream.sci.thlonline.com/config.json`). `/products` needs an auth header issued in a live, reCAPTCHA-gated booking session, so it activates only when `CANADREAM_API_TOKEN` is supplied; otherwise it no-ops. Parser is defensive (name + price ⇒ listing) pending a captured response to confirm the schema. |

### RVezy authenticated mode (optional)

RVezy has no password/client-credentials grant (only interactive OIDC), so the app
**cannot** log in head-less — and you shouldn't automate it (bot-detection risks an
account lock). The SSR path already handles discovery + pagination unauthenticated;
the authenticated token only adds per-listing **enrichment** (Length, Make/Model)
that the public payload omits. To enable it, capture a token once from a logged-in
browser (DevTools → Network → any `api.rvezy.com` request → copy the
`Authorization: Bearer …` value, and the access_token cookie also works) into
`RVEZY_BEARER_TOKEN`. These are **tokens, never your password**, and they expire —
add `RVEZY_REFRESH_TOKEN` to auto-renew. Without them the SSR path works fine.

> Note: the authenticated *search* endpoint is built dynamically client-side and
> isn't reachable from static analysis, so enrichment uses the verified
> get-by-id route against listings discovered via SSR.

Adding a provider = implement `RvProvider` (one method, `search(Watch)`), annotate
`@Component`. It's auto-registered by its `key()`.

## Run locally

```bash
./gradlew bootRun          # starts on :8080
```

Ad-hoc search (no waiting for the schedule):

```bash
curl "http://localhost:8080/api/search?address=Richmond,BC&lat=49.1666&lon=-123.1336\
&start=2026-08-26&end=2026-09-04&maxPrice=250&pet=true&rvType=ClassC"
```

Or run a configured watch by index: `GET /api/search/watch/0`.

Health: `GET /actuator/health`.

## Run in Docker

```bash
cd docker
docker compose up --build    # exposes :8090 -> container :8080
```

Configure SMTP and watch toggles via env vars (see `.env.example`). With
`MAIL_USERNAME` blank, alerts are logged instead of emailed — useful for testing.

## Notes / limitations

- The SSR page embeds the first results page (~30 units) plus a few featured
  ones; `RvezyProvider.maxPages` pulls a couple of pages. Tight filters (radius,
  price) keep relevant matches near the top, so this is plenty for a personal
  watch. Widen `maxPages` if you watch a broad area.
- This reads only public, already-rendered data and polls politely (jitter +
  inter-watch spacing). Keep the interval reasonable.
