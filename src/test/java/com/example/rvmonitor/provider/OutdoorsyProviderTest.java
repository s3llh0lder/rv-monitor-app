package com.example.rvmonitor.provider;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Outdoorsy JSON mapping against a real API response captured for
 * Richmond, BC, Aug 26 → Sep 4 2026 (CAD).
 */
class OutdoorsyProviderTest {

    private OutdoorsyProvider provider() {
        return new OutdoorsyProvider(new MonitorConfig(), null); // RestTemplate unused for parse/URL
    }

    private Watch watch() {
        Watch w = new Watch();
        w.setName("t");
        w.setAddress("Richmond, BC");
        w.setLatitude(49.1666);
        w.setLongitude(-123.1336);
        w.setStartDate("2026-08-26");
        w.setEndDate("2026-09-04");
        w.setRadiusKm(60.0);
        return w;
    }

    private String fixture() throws Exception {
        try (var in = getClass().getResourceAsStream("/outdoorsy-search-fixture.json")) {
            assertNotNull(in, "fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void buildsUrlWithFlatParamsAndMilesRadius() {
        String url = provider().buildUrl(watch(), 0);
        assertTrue(url.contains("near=49.1666%2C-123.1336"), url);
        assertTrue(url.contains("date_from=2026-08-26"), url);
        assertTrue(url.contains("date_to=2026-09-04"), url);
        assertTrue(url.contains("currency=CAD"), url);
        assertTrue(url.contains("radius=38"), url); // 60 km ≈ 38 mi
        assertTrue(url.contains("offset=0"), url);
    }

    @Test
    void parsesListingsWithCentsToDollarsAndDistance() throws Exception {
        List<RvListing> listings = provider().parse(fixture(), watch());
        assertFalse(listings.isEmpty(), "no listings parsed");

        RvListing first = listings.get(0);
        assertEquals("outdoorsy", first.getProvider());
        assertNotNull(first.getId());
        assertNotNull(first.getName());
        assertNotNull(first.getUrl());
        assertTrue(first.getUrl().startsWith("https://www.outdoorsy.com"), first.getUrl());

        // Prices arrive in cents; mapped value must be a sane nightly dollar amount.
        boolean reasonablePrices = listings.stream()
                .filter(l -> l.getNightlyPrice() != null)
                .allMatch(l -> l.getNightlyPrice() >= 10 && l.getNightlyPrice() <= 2000);
        assertTrue(reasonablePrices, "prices not converted from cents");

        // Distance computed for any listing exposing home coordinates.
        assertTrue(listings.stream().anyMatch(l -> l.getDistanceKm() != null));
    }

    @Test
    void capturesClassCUnits() throws Exception {
        List<RvListing> listings = provider().parse(fixture(), watch());
        assertTrue(listings.stream().anyMatch(l -> "Class C".equals(l.getRvType())),
                "expected at least one Class C in the fixture");
    }

    @Test
    void haversineIsRoughlyCorrect() {
        // Richmond ~ Vancouver downtown is ~12 km.
        double km = OutdoorsyProvider.haversineKm(49.1666, -123.1336, 49.2827, -123.1207);
        assertTrue(km > 8 && km < 18, "got " + km);
    }
}
