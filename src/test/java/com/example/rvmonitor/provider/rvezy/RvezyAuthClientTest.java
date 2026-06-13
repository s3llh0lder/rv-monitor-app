package com.example.rvmonitor.provider.rvezy;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RvezyAuthClientTest {

    private static final ObjectMapper M = new ObjectMapper();

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
    void refreshFailsClosedWithoutRefreshToken() {
        MonitorConfig cfg = new MonitorConfig();
        cfg.getRvezy().setBearerToken("expired");
        assertFalse(new RvezyAuthClient(cfg, null).tryRefresh());
    }

    @Test
    void appliesEnrichmentFromDetailJson() throws Exception {
        var detail = M.readTree("""
            {"Length":24.0,"Make":"Ford","Model":"Adventurer","Year":2017,
             "Guests":6,"Rating":4.8,"NumberOfReviews":12}
            """);
        RvListing rv = new RvListing();
        RvezyAuthClient.applyEnrichment(rv, detail);
        assertEquals(24.0, rv.getLengthFt());
        assertEquals("Ford", rv.getMake());
        assertEquals("Adventurer", rv.getModel());
        assertEquals(2017, rv.getYear());
    }

    @Test
    void parsesCalendarsFromDetailJson() throws Exception {
        var detail = M.readTree("""
            {"Calendars":[
              {"StartDate":"2026-08-29","EndDate":"2026-08-29"},
              {"StartDate":"2026-09-01","EndDate":"2026-09-03"}
            ]}
            """);
        List<String[]> ranges = RvezyAuthClient.calendarsFromDetail(detail);
        assertEquals(2, ranges.size());
        assertEquals("2026-08-29", ranges.get(0)[0]);
        assertEquals("2026-09-03", ranges.get(1)[1]);
    }

    @Test
    void calendarsFromNullOrEmptyIsSafe() {
        assertTrue(RvezyAuthClient.calendarsFromDetail(null).isEmpty());
    }
}
