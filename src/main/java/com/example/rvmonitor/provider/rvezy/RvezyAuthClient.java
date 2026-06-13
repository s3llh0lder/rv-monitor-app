package com.example.rvmonitor.provider.rvezy;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * Optional authenticated enrichment against RVezy's JSON API
 * ({@code api.rvezy.com/api/rvlistings/{id}}). Active only when a bearer token is
 * configured (captured from a logged-in browser — never a password).
 *
 * <p>Why enrichment, not search: the public SSR page already handles discovery +
 * pagination, but it omits fields a logged-in API call returns — notably
 * <b>Length</b>, Make/Model and slide-outs. So for each discovered listing we
 * GET the authenticated detail and fold those fields in. On a 401 we mint a
 * fresh access token from the refresh token via the public OIDC client and retry
 * once. (RVezy's search endpoint is built dynamically client-side and isn't
 * reachable from static analysis; the verified get-by-id route powers this.)
 */
@Component
public class RvezyAuthClient {

    private static final Logger logger = LoggerFactory.getLogger(RvezyAuthClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String LISTING_PATH = "/api/rvlistings/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";

    private final MonitorConfig config;
    private final RestTemplate restTemplate;

    private volatile String accessToken;
    private volatile String refreshToken;

    public RvezyAuthClient(MonitorConfig config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
        this.accessToken = config.getRvezy().getBearerToken();
        this.refreshToken = config.getRvezy().getRefreshToken();
    }

    /** True when a bearer token is configured and enrichment should run. */
    public boolean isEnabled() {
        return accessToken != null && !accessToken.isBlank();
    }

    /**
     * Folds authenticated detail (length, make, model, and any missing
     * price/rating) into the given listing. Best-effort: logs and leaves the
     * listing unchanged on failure so discovery still succeeds.
     */
    public void enrich(RvListing rv) {
        if (rv.getId() == null) {
            return;
        }
        JsonNode detail = fetchListing(rv.getId(), true);
        if (detail == null) {
            return;
        }
        if (detail.hasNonNull("Length")) rv.setLengthFt(detail.get("Length").asDouble());
        if (detail.hasNonNull("Make")) rv.setMake(detail.get("Make").asText());
        if (detail.hasNonNull("Model")) rv.setModel(detail.get("Model").asText());
        if (rv.getYear() == null && detail.hasNonNull("Year")) rv.setYear(detail.get("Year").asInt());
        if (rv.getGuests() == null && detail.hasNonNull("Guests")) rv.setGuests(detail.get("Guests").asInt());
        if (rv.getRating() == null && detail.hasNonNull("Rating")) rv.setRating(detail.get("Rating").asDouble());
        if (rv.getNumReviews() == null && detail.hasNonNull("NumberOfReviews")) {
            rv.setNumReviews(detail.get("NumberOfReviews").asInt());
        }
        if (rv.getNightlyPrice() == null && detail.hasNonNull("DefaultPrice")) {
            rv.setNightlyPrice(detail.get("DefaultPrice").asDouble());
        }
    }

    private JsonNode fetchListing(String id, boolean allowRefresh) {
        String url = config.getRvezy().getApiUrl() + LISTING_PATH + id;
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
            return MAPPER.readTree(resp.getBody());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401 && allowRefresh && tryRefresh()) {
                logger.info("RVezy token refreshed — retrying enrichment for {}", id);
                return fetchListing(id, false);
            }
            logger.warn("RVezy enrichment HTTP {} for listing {}", e.getStatusCode(), id);
            return null;
        } catch (Exception e) {
            logger.warn("RVezy enrichment failed for listing {}: {}", id, e.toString());
            return null;
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + accessToken);
        h.set("Accept", "application/json, text/plain, */*");
        h.set("Accept-Language", "en-CA,en;q=0.9");
        h.set("App-Language", "en-CA");
        h.set("rentals-api-version", String.valueOf(config.getRvezy().getRentalsApiVersion()));
        h.set("Origin", "https://www.rvezy.com");
        h.set("Referer", "https://www.rvezy.com/");
        h.set("User-Agent", USER_AGENT);
        return h;
    }

    /** Mint a new access token from the refresh token. Returns false if unavailable. */
    boolean tryRefresh() {
        if (refreshToken == null || refreshToken.isBlank()) {
            logger.warn("RVezy access token rejected (401) and no refresh token set — recapture token.");
            return false;
        }
        try {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            h.set("User-Agent", USER_AGENT);
            h.set("Origin", "https://www.rvezy.com");
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "refresh_token");
            form.add("client_id", config.getRvezy().getOidcClientId());
            form.add("client_secret", config.getRvezy().getOidcClientSecret());
            form.add("refresh_token", refreshToken);

            ResponseEntity<String> resp = restTemplate.exchange(
                    URI.create(config.getRvezy().getOidcAuthority() + "/connect/token"),
                    HttpMethod.POST, new HttpEntity<>(form, h), String.class);
            JsonNode body = MAPPER.readTree(resp.getBody());
            String newAccess = body.path("access_token").asText(null);
            if (newAccess == null) {
                logger.warn("Refresh response had no access_token");
                return false;
            }
            this.accessToken = newAccess;
            String newRefresh = body.path("refresh_token").asText(null);
            if (newRefresh != null) this.refreshToken = newRefresh; // rotate
            return true;
        } catch (Exception e) {
            logger.warn("RVezy token refresh failed: {}", e.toString());
            return false;
        }
    }
}
