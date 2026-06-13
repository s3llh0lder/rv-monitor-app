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
 * CanaDream — runs on THL's "cosmos" booking platform.
 *
 * <p><b>Status: EXPERIMENTAL.</b> Discovery established the platform but not a
 * usable unauthenticated path:
 * <ul>
 *   <li>API base {@code cosmos-alb-prod-2.aws.thlonline.com} (from
 *       {@code canadream.sci.thlonline.com/config.json}).</li>
 *   <li>{@code /products} and {@code /rentals} exist but return <b>401</b> — they
 *       need an auth header issued during a live, reCAPTCHA-gated booking
 *       session, which can't be minted head-less.</li>
 *   <li>The response schema isn't publicly visible, so parsing is defensive:
 *       any JSON object exposing a name-like and price-like field is treated as
 *       a listing.</li>
 * </ul>
 *
 * <p>To activate: capture a {@code /products} request from a logged-in browser
 * booking session (DevTools → Network) and supply its auth header value via
 * {@code CANADREAM_API_TOKEN} (and adjust {@code rv.canadream.api-path}/{@code
 * extra-params} to match). Until then this returns no results and logs why —
 * the monitor keeps working for the other providers.
 */
@Component
public class CanaDreamProvider implements RvProvider {

    private static final Logger logger = LoggerFactory.getLogger(CanaDreamProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    private final MonitorConfig config;
    private final RestTemplate restTemplate;

    @Autowired
    public CanaDreamProvider(MonitorConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    @Override
    public String key() {
        return "canadream";
    }

    @Override
    public List<RvListing> search(Watch watch) {
        MonitorConfig.Canadream cd = config.getCanadream();
        if (cd.getAuthToken() == null || cd.getAuthToken().isBlank()) {
            logger.warn("CanaDream is experimental and needs CANADREAM_API_TOKEN (captured from a "
                    + "live booking session) — skipping watch '{}'", watch.getName());
            return List.of();
        }
        String url = buildUrl(watch);
        logger.debug("GET {}", url);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(headers()), String.class);
            List<RvListing> out = parse(resp.getBody(), watch);
            logger.info("CanaDream returned {} listing(s) for '{}'", out.size(), watch.getName());
            return out;
        } catch (Exception e) {
            logger.warn("CanaDream fetch failed for '{}': {} — verify auth token / api-path / params",
                    watch.getName(), e.toString());
            return List.of();
        }
    }

    String buildUrl(Watch watch) {
        MonitorConfig.Canadream cd = config.getCanadream();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("brand", cd.getBrand());
        // Best-effort param names; tune via rv.canadream.extra-params once the live
        // request is captured.
        p.put("pickUpDate", watch.getStartDate());
        p.put("dropOffDate", watch.getEndDate());
        if (cd.getExtraParams() != null) {
            p.putAll(cd.getExtraParams());
        }
        StringBuilder sb = new StringBuilder(cd.getApiBaseUrl()).append(cd.getApiPath()).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    private HttpHeaders headers() {
        MonitorConfig.Canadream cd = config.getCanadream();
        HttpHeaders h = new HttpHeaders();
        h.set("Accept", "application/json, text/plain, */*");
        h.set("User-Agent", USER_AGENT);
        h.set("Origin", "https://canadream.sci.thlonline.com");
        h.set("Referer", "https://canadream.sci.thlonline.com/");
        h.set(cd.getAuthHeaderName(), cd.getAuthToken());
        return h;
    }

    /** Defensive parse: collect JSON objects that expose a name + price field. */
    List<RvListing> parse(String json, Watch watch) {
        List<RvListing> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            collect(MAPPER.readTree(json), out);
        } catch (Exception e) {
            logger.warn("CanaDream response not parseable as listings: {}", e.toString());
        }
        return out;
    }

    private void collect(JsonNode node, List<RvListing> out) {
        if (node.isObject()) {
            String name = firstText(node, "name", "vehicleName", "productName", "title", "displayName");
            Double price = firstNumber(node, "price", "dailyRate", "totalPrice", "nightlyPrice", "rate");
            if (name != null && price != null) {
                RvListing rv = new RvListing();
                rv.setProvider("canadream");
                rv.setId(firstText(node, "id", "code", "productCode", "sku"));
                rv.setName(name);
                rv.setNightlyPrice(price);
                rv.setRvType(firstText(node, "category", "vehicleType", "class", "type"));
                out.add(rv);
                return;
            }
            node.forEach(child -> collect(child, out));
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, out));
        }
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isTextual() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }

    private static Double firstNumber(JsonNode n, String... fields) {
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isNumber()) return v.asDouble();
        }
        return null;
    }
}
