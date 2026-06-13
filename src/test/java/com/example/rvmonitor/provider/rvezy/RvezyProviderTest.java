package com.example.rvmonitor.provider.rvezy;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RvezyProviderTest {

    private RvezyProvider provider() {
        MonitorConfig cfg = new MonitorConfig();
        cfg.getRvezy().setSearchBaseUrl("https://www.rvezy.com/rv-search");
        return new RvezyProvider(cfg, null); // RestTemplate unused by URL build / mapping
    }

    private Watch richmondWatch() {
        Watch w = new Watch();
        w.setName("t");
        w.setAddress("Richmond, BC");
        w.setLatitude(49.1666);
        w.setLongitude(-123.1336);
        w.setStartDate("2026-08-26");
        w.setEndDate("2026-09-04");
        w.setPetFriendly(true);
        w.setRvType("ClassC");
        return w;
    }

    @Test
    void buildsSearchUrlWithDatesPetAndExtraParams() {
        String url = provider().buildSearchUrl(richmondWatch(), 1);
        assertTrue(url.contains("DateStart=2026-08-26"), url);
        assertTrue(url.contains("DateEnd=2026-09-04"), url);
        assertTrue(url.contains("PetFriendly=true"), url);
        assertTrue(url.contains("RVType=ClassC"), url);
        assertTrue(url.contains("SearchAddress=Richmond%2C+BC") || url.contains("Richmond%2CBC"), url);
        assertFalse(url.contains("Page="), "page 1 should not add a Page param");
    }

    @Test
    void addsPageParamForLaterPages() {
        assertTrue(provider().buildSearchUrl(richmondWatch(), 3).contains("Page=3"));
    }

    @Test
    void mapsResolvedListingObject() {
        Map<String, Object> m = Map.of(
                "Id", "abc-123",
                "RVName", "Toby d' RV",
                "RVType", "ClassC",
                "Year", 2016L,
                "Guests", 6L,
                "AveragePrice", 250L,
                "AverageRating", 4.9,
                "NumberOfReview", 30L,
                "IsSuperHostActive", true,
                "City", "Vancouver");
        RvListing rv = provider().toListing(m);
        assertEquals("abc-123", rv.getId());
        assertEquals("Toby d' RV", rv.getName());
        assertEquals(2016, rv.getYear());
        assertEquals(6, rv.getGuests());
        assertEquals(250.0, rv.getNightlyPrice());
        assertEquals(4.9, rv.getRating());
        assertTrue(rv.isSuperHost());
        assertEquals("rvezy", rv.getProvider());
    }

    @Test
    void buildsListingUrlFromAlias() {
        RvListing rv = provider().toListing(Map.of(
                "Id", "x", "RVName", "n",
                "AliasName", "british-columbia_vancouver_motorhome_classc_ford_e350"));
        assertEquals("https://www.rvezy.com/rv-rental/"
                + "british-columbia_vancouver_motorhome_classc_ford_e350", rv.getUrl());
    }
}
