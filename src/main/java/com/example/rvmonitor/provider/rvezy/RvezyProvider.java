package com.example.rvmonitor.provider.rvezy;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import com.example.rvmonitor.provider.RvProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads RVezy availability from its PUBLIC server-side-rendered search page.
 *
 * <p>RVezy's JSON API ({@code api.rvezy.com}) sits behind Cloudflare and an
 * interactive-OIDC bearer token, so we don't call it. Instead we request the
 * same search page a browser loads — {@code www.rvezy.com/rv-search?...} with
 * the trip dates — which returns HTTP 200 with the date-specific results
 * embedded in a Nuxt {@code __NUXT_DATA__} payload. {@link NuxtDataParser}
 * turns that payload into listing maps which we map to {@link RvListing}.
 *
 * <p>The page embeds the first results page (~30 listings) plus a few featured
 * units. {@code maxPages} pulls additional pages ({@code &Page=N}) for searches
 * whose matches spill past the first page.
 */
@Component
public class RvezyProvider implements RvProvider {

    private static final Logger logger = LoggerFactory.getLogger(RvezyProvider.class);
    private static final String LISTING_BASE = "https://www.rvezy.com/rv-rental/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    private final MonitorConfig config;
    private final RestTemplate restTemplate;
    private final RvezyAuthClient authClient;

    /** How many result pages to pull per search. The first page holds ~30 units. */
    private int maxPages = 2;
    /** Cap on authenticated per-listing enrichment calls per search (politeness). */
    private int maxEnrich = 12;

    @Autowired
    public RvezyProvider(MonitorConfig config, RestTemplate restTemplate, RvezyAuthClient authClient) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.authClient = authClient;
    }

    // Test constructor: no authenticated path.
    RvezyProvider(MonitorConfig config, RestTemplate restTemplate) {
        this(config, restTemplate, null);
    }

    void setMaxPages(int maxPages) { this.maxPages = maxPages; }

    @Override
    public String key() {
        return "rvezy";
    }

    @Override
    public List<RvListing> search(Watch watch) {
        // Discovery is always via the public SSR page; authenticated enrichment
        // (Length, Make/Model) happens later in enrich(), on the filtered matches.
        return scrapeSsr(watch);
    }

    /**
     * Authenticated enrichment of the final matches: folds in Length / Make /
     * Model from the get-by-id API. Bounded by {@link #maxEnrich} for politeness.
     */
    @Override
    public void enrich(List<RvListing> matches) {
        if (authClient == null || !authClient.isEnabled() || matches.isEmpty()) {
            return;
        }
        int n = 0;
        for (RvListing rv : matches) {
            if (n >= maxEnrich) break;
            authClient.enrich(rv);
            n++;
        }
        logger.info("RVezy enriched {} of {} match(es) via authenticated API", n, matches.size());
    }

    private List<RvListing> scrapeSsr(Watch watch) {
        Map<Object, RvListing> byId = new LinkedHashMap<>();
        for (int page = 1; page <= maxPages; page++) {
            List<Map<String, Object>> raw = fetchPage(watch, page);
            if (raw.isEmpty()) {
                break; // no more results
            }
            int before = byId.size();
            for (Map<String, Object> m : raw) {
                RvListing rv = toListing(m);
                if (rv.getId() != null) {
                    byId.putIfAbsent(rv.getId(), rv);
                }
            }
            // If a page added nothing new, further pages won't either — stop early.
            if (byId.size() == before) {
                break;
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<Map<String, Object>> fetchPage(Watch watch, int page) {
        String url = buildSearchUrl(watch, page);
        logger.debug("GET {}", url);
        try {
            HttpEntity<String> entity = new HttpEntity<>(buildHeaders());
            ResponseEntity<String> resp = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, entity, String.class);
            String html = resp.getBody();
            if (html == null || html.isEmpty()) {
                logger.warn("Empty body from RVezy for watch '{}' page {}", watch.getName(), page);
                return List.of();
            }
            return NuxtDataParser.extractListings(html);
        } catch (Exception e) {
            logger.error("RVezy fetch failed for '{}' page {}: {}", watch.getName(), page, e.toString());
            return List.of();
        }
    }

    String buildSearchUrl(Watch watch, int page) {
        StringBuilder sb = new StringBuilder(config.getRvezy().getSearchBaseUrl()).append('?');
        Map<String, String> params = new LinkedHashMap<>();
        params.put("SearchAddress", watch.getAddress());
        params.put("Latitude", String.valueOf(watch.getLatitude()));
        params.put("Longitude", String.valueOf(watch.getLongitude()));
        params.put("DateStart", watch.getStartDate());
        params.put("DateEnd", watch.getEndDate());
        if (watch.isPetFriendly()) {
            params.put("PetFriendly", "true");   // verified: trims results to pet-allowed units
        }
        if (watch.getRvType() != null && !watch.getRvType().isBlank()) {
            params.put("RVType", watch.getRvType());  // best-effort; authoritative filter is client-side
        }
        if (page > 1) {
            params.put("Page", String.valueOf(page));
        }
        // Caller-supplied provider params (e.g. RVType=ClassC) — applied last so
        // a watch can override any default above.
        if (watch.getExtraParams() != null) {
            params.putAll(watch.getExtraParams());
        }

        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        h.set("Accept-Language", "en-CA,en;q=0.9");
        h.set("Cache-Control", "no-cache");
        h.set("Pragma", "no-cache");
        h.set("Sec-Fetch-Dest", "document");
        h.set("Sec-Fetch-Mode", "navigate");
        h.set("Sec-Fetch-Site", "none");
        h.set("Upgrade-Insecure-Requests", "1");
        h.set("User-Agent", USER_AGENT);
        return h;
    }

    /** Maps one resolved Nuxt listing object to an {@link RvListing}. */
    RvListing toListing(Map<String, Object> m) {
        RvListing rv = new RvListing();
        rv.setProvider("rvezy");
        rv.setId(str(m.get("Id")));
        rv.setName(str(m.get("RVName")));
        rv.setRvType(str(m.get("RVType")));
        rv.setYear(toInt(m.get("Year")));
        rv.setGuests(toInt(m.get("Guests")));
        Double price = toDouble(m.get("AveragePrice"));
        if (price == null) price = toDouble(m.get("DefaultPrice"));
        rv.setNightlyPrice(price);
        rv.setRating(toDouble(m.get("AverageRating")));
        rv.setNumReviews(toInt(m.get("NumberOfReview")));
        rv.setSuperHost(toBool(m.get("IsSuperHostActive")));
        rv.setInstantBook(toBool(m.get("InstabookOwnerOptedIn")));
        rv.setCity(str(m.get("City")));
        rv.setRegion(str(m.get("State")));
        rv.setDistanceKm(toDouble(m.get("Distance")));
        String alias = str(m.get("AliasName"));
        if (alias != null) {
            rv.setUrl(LISTING_BASE + alias);
        }
        return rv;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer toInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try {
            return o == null ? null : (int) Math.round(Double.parseDouble(o.toString()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? null : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean toBool(Object o) {
        return o instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(o));
    }
}
