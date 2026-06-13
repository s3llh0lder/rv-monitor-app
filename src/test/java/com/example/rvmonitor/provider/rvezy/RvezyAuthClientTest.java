package com.example.rvmonitor.provider.rvezy;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RvezyAuthClientTest {

    @Test
    void disabledWithoutToken() {
        assertFalse(new RvezyAuthClient(new MonitorConfig(), null).isEnabled());
    }

    @Test
    void enabledWhenTokenConfigured() {
        MonitorConfig cfg = new MonitorConfig();
        cfg.getRvezy().setBearerToken("abc.def.ghi");
        assertTrue(new RvezyAuthClient(cfg, null).isEnabled());
    }

    @Test
    void enrichIsNoOpWithoutListingId() {
        // No id → returns before any HTTP call (RestTemplate is null), leaves listing untouched.
        RvListing rv = new RvListing();
        rv.setName("no-id");
        new RvezyAuthClient(new MonitorConfig(), null).enrich(rv);
        assertNull(rv.getLengthFt());
        assertNull(rv.getMake());
    }

    @Test
    void refreshFailsClosedWithoutRefreshToken() {
        MonitorConfig cfg = new MonitorConfig();
        cfg.getRvezy().setBearerToken("expired");
        // No refresh token configured → tryRefresh returns false before any HTTP call.
        assertFalse(new RvezyAuthClient(cfg, null).tryRefresh());
    }
}
