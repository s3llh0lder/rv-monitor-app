package com.example.rvmonitor.provider;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Outdoorsy availability from its PUBLIC JSON API
 * ({@code api.outdoorsy.com/v0/rentals}). Unlike RVezy this is a clean,
 * unauthenticated JSON endpoint — no SSR/devalue parsing needed.
 *
 * <p>Honored query params (flat, not JSON:API bracket form):
 * {@code near=lat,lng}, {@code radius} (miles), {@code date_from}/{@code date_to},
 * {@code limit}/{@code offset}, {@code currency}. Prices come back in cents.
 */
@Component
public class OutdoorsyProvider implements RvProvider {

    private static final Logger logger = LoggerFactory.getLogger(OutdoorsyProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LISTING_BASE = "https://www.outdoorsy.com";
    private static final int PAGE_SIZE = 20;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    private final MonitorConfig config;
    private final RestTemplate restTemplate;

    /** How many pages of {@value #PAGE_SIZE} to pull per search. */
    private int maxPages = 2;

    @Autowired
    public OutdoorsyProvider(MonitorConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    void setMaxPages(int maxPages) { this.maxPages = maxPages; }

    /** Cap on per-listing quote/availability checks per search (politeness). */
    private int maxVerify = 20;

    void setMaxVerify(int maxVerify) { this.maxVerify = maxVerify; }

    @Override
    public String key() {
        return "outdoorsy";
    }

    /**
     * Verify availability for the final matches. Outdoorsy's search returns
     * relevance/location matches, NOT an availability filter — booked listings
     * (e.g. Yatra 1) still appear. The quote endpoint is the authoritative,
     * no-auth check: a quote is created (2xx) only when the rental is bookable
     * for the dates; otherwise it 400s. We drop the 400s. On other errors we keep
     * the listing (can't disprove) rather than risk a miss.
     */
    @Override
    public List<RvListing> refine(Watch watch, List<RvListing> matches) {
        if (matches.isEmpty()) {
            return matches;
        }
        List<RvListing> kept = new ArrayList<>();
        int checked = 0, dropped = 0;
        for (RvListing rv : matches) {
            if (checked >= maxVerify) {
                kept.add(rv);
                continue;
            }
            checked++;
            Boolean bookable = isBookable(rv.getId(), watch.getStartDate(), watch.getEndDate());
            if (bookable == null || bookable) {
                kept.add(rv);
            } else {
                dropped++;
                logger.info("Outdoorsy dropped '{}' — not bookable for {}→{}",
                        rv.getName(), watch.getStartDate(), watch.getEndDate());
            }
        }
        logger.info("Outdoorsy verified {} match(es): {} available, {} dropped",
                checked, kept.size() - Math.max(0, matches.size() - checked), dropped);
        return kept;
    }

    /** null = couldn't determine; true = quote created (available); false = 400 (unavailable). */
    private Boolean isBookable(String rentalId, String from, String to) {
        if (rentalId == null) {
            return null;
        }
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            h.set("Accept", "application/json");
            h.set("User-Agent", USER_AGENT);
            String body = String.format("{\"rental_id\":%s,\"from\":\"%s\",\"to\":\"%s\"}", rentalId, from, to);
            ResponseEntity<String> resp = restTemplate.exchange(
                    URI.create(config.getOutdoorsy().getQuoteUrl()),
                    HttpMethod.POST, new HttpEntity<>(body, h), String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                return false; // "not available to be booked for the selected dates"
            }
            return null; // auth/other → can't determine
        } catch (Exception e) {
            logger.warn("Outdoorsy quote check failed for {}: {}", rentalId, e.toString());
            return null;
        }
    }

    @Override
    public List<RvListing> search(Watch watch) {
        Map<String, RvListing> byId = new LinkedHashMap<>();
        for (int page = 0; page < maxPages; page++) {
            List<RvListing> pageResults = fetchPage(watch, page * PAGE_SIZE);
            if (pageResults.isEmpty()) {
                break;
            }
            int before = byId.size();
            for (RvListing rv : pageResults) {
                if (rv.getId() != null) {
                    byId.putIfAbsent(rv.getId(), rv);
                }
            }
            if (byId.size() == before || pageResults.size() < PAGE_SIZE) {
                break; // nothing new, or last (partial) page
            }
        }
        return new ArrayList<>(byId.values());
    }

    private List<RvListing> fetchPage(Watch watch, int offset) {
        String url = buildUrl(watch, offset);
        logger.debug("GET {}", url);
        try {
            HttpHeaders h = new HttpHeaders();
            h.set("Accept", "application/json");
            h.set("User-Agent", USER_AGENT);
            ResponseEntity<String> resp = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(h), String.class);
            return parse(resp.getBody(), watch);
        } catch (Exception e) {
            logger.error("Outdoorsy fetch failed for '{}' offset {}: {}", watch.getName(), offset, e.toString());
            return List.of();
        }
    }

    String buildUrl(Watch watch, int offset) {
        int radiusMiles = watch.getRadiusKm() != null
                ? (int) Math.ceil(watch.getRadiusKm() * 0.621371)
                : config.getOutdoorsy().getDefaultRadiusMiles();

        Map<String, String> p = new LinkedHashMap<>();
        p.put("near", watch.getLatitude() + "," + watch.getLongitude());
        p.put("radius", String.valueOf(radiusMiles));
        p.put("date_from", watch.getStartDate());
        p.put("date_to", watch.getEndDate());
        p.put("limit", String.valueOf(PAGE_SIZE));
        p.put("offset", String.valueOf(offset));
        p.put("currency", config.getOutdoorsy().getCurrency());

        StringBuilder sb = new StringBuilder(config.getOutdoorsy().getApiUrl()).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    /** The response root is a JSON array of rental objects. */
    List<RvListing> parse(String body, Watch watch) throws Exception {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        JsonNode root = MAPPER.readTree(body);
        JsonNode arr = root.isArray() ? root : root.path("data");
        if (!arr.isArray()) {
            return List.of();
        }
        List<RvListing> out = new ArrayList<>(arr.size());
        for (JsonNode n : arr) {
            out.add(toListing(n, watch));
        }
        return out;
    }

    RvListing toListing(JsonNode n, Watch watch) {
        RvListing rv = new RvListing();
        rv.setProvider("outdoorsy");
        rv.setId(text(n, "id"));
        rv.setName(text(n, "name"));
        rv.setRvType(text(n, "display_vehicle_type")); // e.g. "Class C"; filter normalizes spacing
        rv.setGuests(intOrNull(n, "sleeps"));
        if (n.hasNonNull("score")) rv.setRating(n.get("score").asDouble());
        rv.setNumReviews(intOrNull(n, "score_count"));
        rv.setInstantBook(n.path("instant_book").asBoolean(false));

        JsonNode price = n.path("price");
        if (price.hasNonNull("day")) {
            rv.setNightlyPrice(price.get("day").asDouble() / 100.0); // cents → dollars
        }

        JsonNode home = n.path("home");
        rv.setCity(text(home, "city"));
        rv.setRegion(text(home, "state"));
        if (home.hasNonNull("lat") && home.hasNonNull("lng")) {
            rv.setDistanceKm(haversineKm(
                    watch.getLatitude(), watch.getLongitude(),
                    home.get("lat").asDouble(), home.get("lng").asDouble()));
        }
        String slug = text(n, "slug");
        if (slug != null) {
            rv.setUrl(slug.startsWith("http") ? slug : LISTING_BASE + slug);
        }
        return rv;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }

    /** Great-circle distance in km, so radius filtering works for Outdoorsy too. */
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
