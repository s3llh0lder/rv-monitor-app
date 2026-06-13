package com.example.rvmonitor.provider;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanaDreamProviderTest {

    private CanaDreamProvider provider() {
        return new CanaDreamProvider(new MonitorConfig(), null); // RestTemplate unused for these
    }

    private Watch watch() {
        Watch w = new Watch();
        w.setName("t");
        w.setStartDate("2026-08-26");
        w.setEndDate("2026-09-04");
        return w;
    }

    @Test
    void noOpsWithoutToken() {
        assertTrue(provider().search(watch()).isEmpty());
    }

    @Test
    void buildsUrlWithDiscoveredBaseAndDates() {
        String url = provider().buildUrl(watch());
        assertTrue(url.startsWith("https://cosmos-alb-prod-2.aws.thlonline.com/products?"), url);
        assertTrue(url.contains("brand=canadream"), url);
        assertTrue(url.contains("pickUpDate=2026-08-26"), url);
        assertTrue(url.contains("dropOffDate=2026-09-04"), url);
    }

    @Test
    void defensiveParseExtractsNamePriceObjects() {
        // Mimics an unknown envelope: pick up anything with a name + price field.
        String json = """
            {"results":{"vehicles":[
              {"id":"MHA","name":"Maxi Motorhome","dailyRate":219.0,"category":"Class C"},
              {"id":"X","unrelated":true}
            ]}}
            """;
        List<RvListing> out = provider().parse(json, watch());
        assertEquals(1, out.size());
        assertEquals("Maxi Motorhome", out.get(0).getName());
        assertEquals(219.0, out.get(0).getNightlyPrice());
        assertEquals("Class C", out.get(0).getRvType());
        assertEquals("canadream", out.get(0).getProvider());
    }

    @Test
    void parseIsSafeOnGarbage() {
        assertTrue(provider().parse("not json", watch()).isEmpty());
        assertTrue(provider().parse("", watch()).isEmpty());
    }
}
