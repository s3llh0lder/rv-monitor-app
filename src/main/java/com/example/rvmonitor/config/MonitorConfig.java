package com.example.rvmonitor.config;

import com.example.rvmonitor.model.Watch;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the {@code rv.*} block in application.yml: the RVezy endpoint, the list
 * of saved {@link Watch}es, and the politeness / scheduling knobs.
 */
@Configuration
@ConfigurationProperties(prefix = "rv")
public class MonitorConfig {

    private Rvezy rvezy = new Rvezy();
    private Outdoorsy outdoorsy = new Outdoorsy();
    private Canadream canadream = new Canadream();
    private List<Watch> watches = new ArrayList<>();
    private Polite polite = new Polite();
    private Monitor monitor = new Monitor();

    public Rvezy getRvezy() { return rvezy; }
    public void setRvezy(Rvezy rvezy) { this.rvezy = rvezy; }

    public Outdoorsy getOutdoorsy() { return outdoorsy; }
    public void setOutdoorsy(Outdoorsy outdoorsy) { this.outdoorsy = outdoorsy; }

    public Canadream getCanadream() { return canadream; }
    public void setCanadream(Canadream canadream) { this.canadream = canadream; }

    public List<Watch> getWatches() { return watches; }
    public void setWatches(List<Watch> watches) { this.watches = watches; }

    public Polite getPolite() { return polite; }
    public void setPolite(Polite polite) { this.polite = polite; }

    public Monitor getMonitor() { return monitor; }
    public void setMonitor(Monitor monitor) { this.monitor = monitor; }

    public static class Rvezy {
        private String searchBaseUrl = "https://www.rvezy.com/rv-search";

        // Optional authenticated mode. When bearerToken is set, the provider calls
        // RVezy's JSON API for full paginated results; otherwise it scrapes the
        // public SSR page. Tokens are captured from a logged-in browser — NEVER a
        // password. Supply via env (RVEZY_BEARER_TOKEN / RVEZY_REFRESH_TOKEN).
        private String apiUrl = "https://api.rvezy.com";
        private int rentalsApiVersion = 9;
        private String bearerToken = "";
        private String refreshToken = "";
        // Public OIDC client embedded in rvezy.com's frontend — used only to mint a
        // fresh access token from the refresh token. Not a user secret.
        private String oidcAuthority = "https://identityserver.rvezy.com";
        private String oidcClientId = "rvezy-rentals";
        private String oidcClientSecret = "88A28809-F9DA-4B39-AE5A-24260ADC4D71";

        public String getSearchBaseUrl() { return searchBaseUrl; }
        public void setSearchBaseUrl(String v) { this.searchBaseUrl = v; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String v) { this.apiUrl = v; }
        public int getRentalsApiVersion() { return rentalsApiVersion; }
        public void setRentalsApiVersion(int v) { this.rentalsApiVersion = v; }
        public String getBearerToken() { return bearerToken; }
        public void setBearerToken(String v) { this.bearerToken = v; }
        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String v) { this.refreshToken = v; }
        public String getOidcAuthority() { return oidcAuthority; }
        public void setOidcAuthority(String v) { this.oidcAuthority = v; }
        public String getOidcClientId() { return oidcClientId; }
        public void setOidcClientId(String v) { this.oidcClientId = v; }
        public String getOidcClientSecret() { return oidcClientSecret; }
        public void setOidcClientSecret(String v) { this.oidcClientSecret = v; }
    }

    public static class Outdoorsy {
        private String apiUrl = "https://api.outdoorsy.com/v0/rentals";
        // Quote endpoint doubles as the authoritative availability check: 201 =
        // bookable for the dates, 400 = not available. The search API does NOT
        // filter by availability, so each match is verified here.
        private String quoteUrl = "https://api.outdoorsy.com/v0/quotes";
        private String currency = "CAD";
        private int defaultRadiusMiles = 40;
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getQuoteUrl() { return quoteUrl; }
        public void setQuoteUrl(String quoteUrl) { this.quoteUrl = quoteUrl; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public int getDefaultRadiusMiles() { return defaultRadiusMiles; }
        public void setDefaultRadiusMiles(int v) { this.defaultRadiusMiles = v; }
    }

    public static class Canadream {
        // THL "cosmos" REST platform (discovered via canadream.sci.thlonline.com/config.json).
        // /products and /rentals require auth (401) and the flow is reCAPTCHA-protected, so
        // this provider is EXPERIMENTAL: it activates only when an auth header captured from a
        // live browser booking session is supplied. apiPath/params are tunable without a rebuild.
        private String apiBaseUrl = "https://cosmos-alb-prod-2.aws.thlonline.com";
        private String apiPath = "/products";
        private String authHeaderName = "Authorization";
        private String authToken = "";   // env CANADREAM_API_TOKEN
        private String brand = "canadream";
        private java.util.Map<String, String> extraParams = new java.util.LinkedHashMap<>();

        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String v) { this.apiBaseUrl = v; }
        public String getApiPath() { return apiPath; }
        public void setApiPath(String v) { this.apiPath = v; }
        public String getAuthHeaderName() { return authHeaderName; }
        public void setAuthHeaderName(String v) { this.authHeaderName = v; }
        public String getAuthToken() { return authToken; }
        public void setAuthToken(String v) { this.authToken = v; }
        public String getBrand() { return brand; }
        public void setBrand(String v) { this.brand = v; }
        public java.util.Map<String, String> getExtraParams() { return extraParams; }
        public void setExtraParams(java.util.Map<String, String> v) { this.extraParams = v; }
    }

    public static class Polite {
        private long interWatchDelayMs = 3000;
        private long jitterMaxMs = 30000;
        public long getInterWatchDelayMs() { return interWatchDelayMs; }
        public void setInterWatchDelayMs(long v) { this.interWatchDelayMs = v; }
        public long getJitterMaxMs() { return jitterMaxMs; }
        public void setJitterMaxMs(long v) { this.jitterMaxMs = v; }
    }

    public static class Monitor {
        private long checkInterval = 900000;
        public long getCheckInterval() { return checkInterval; }
        public void setCheckInterval(long v) { this.checkInterval = v; }
    }
}
