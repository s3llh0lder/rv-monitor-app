package com.example.rvmonitor.provider.rvezy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the devalue parser against a real RVezy search page captured for
 * Richmond, BC, Aug 26 → Sep 4 2026 (pet-friendly).
 */
class NuxtDataParserTest {

    private String fixture() throws Exception {
        try (var in = getClass().getResourceAsStream("/rvezy-search-fixture.html")) {
            assertNotNull(in, "fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void extractsTheFullResultsPageNotJustFeatured() throws Exception {
        List<Map<String, Object>> listings = NuxtDataParser.extractListings(fixture());
        // The page embeds the ~30-item results page plus a few featured units.
        assertTrue(listings.size() >= 20,
                "expected the full results page, got " + listings.size());
    }

    @Test
    void mapsCoreFieldsAndDedupesById() throws Exception {
        List<Map<String, Object>> listings = NuxtDataParser.extractListings(fixture());

        // Every listing must carry the fields the provider maps.
        for (Map<String, Object> m : listings) {
            assertNotNull(m.get("RVName"), "RVName missing");
            assertNotNull(m.get("AliasName"), "AliasName missing");
        }
        // Ids are unique (dedup worked).
        long distinctIds = listings.stream().map(m -> m.get("Id")).distinct().count();
        assertEquals(listings.size(), distinctIds, "duplicate Ids slipped through");

        // At least one listing has a usable nightly price.
        boolean anyPriced = listings.stream()
                .anyMatch(m -> m.get("AveragePrice") instanceof Number n && n.doubleValue() > 0);
        assertTrue(anyPriced, "no priced listings parsed");
    }

    @Test
    void returnsEmptyOnGarbageInsteadOfThrowing() {
        assertTrue(NuxtDataParser.extractListings("<html>no nuxt here</html>").isEmpty());
        assertTrue(NuxtDataParser.extractListings("").isEmpty());
    }
}
