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

    private String listingFixture() throws Exception {
        try (var in = getClass().getResourceAsStream("/rvezy-listing-fixture.html")) {
            assertNotNull(in, "listing fixture missing");
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @Test
    void extractsCalendarBlockedRangesFromListingPage() throws Exception {
        // Real Cultus listing — booked across the trip window (Aug 29–Sep 4 2026).
        List<String[]> ranges = NuxtDataParser.extractCalendarRanges(listingFixture());
        assertFalse(ranges.isEmpty(), "no calendar ranges parsed");
        boolean blocksTripWindow = ranges.stream().anyMatch(r ->
                r[0].startsWith("2026-08-29") || r[0].startsWith("2026-09-0"));
        assertTrue(blocksTripWindow, "expected blocked dates in the Aug29–Sep trip window");
    }

    @Test
    void cultusIsCorrectlyUnavailableForTheTrip() throws Exception {
        // End-to-end: the listing the monitor false-positived on must be rejected.
        List<String[]> ranges = NuxtDataParser.extractCalendarRanges(listingFixture());
        assertFalse(RvezyAvailability.isAvailable(ranges,
                java.time.LocalDate.parse("2026-08-26"), java.time.LocalDate.parse("2026-09-04")));
    }

    @Test
    void searchPageHasNoCalendar() throws Exception {
        // The search fixture isn't a listing page → no Calendars, returns empty (not error).
        assertTrue(NuxtDataParser.extractCalendarRanges(fixture()).isEmpty());
    }

    @Test
    void extractListingPageReturnsCalendarAndDetailTogether() throws Exception {
        // One parse of the real Cultus page yields both the blocked calendar and detail.
        NuxtDataParser.ListingPage page = NuxtDataParser.extractListingPage(listingFixture());
        assertFalse(page.blocked().isEmpty(), "expected blocked ranges");
        assertEquals("Cultus", page.detail().get("RVName"));
        assertEquals(19L, ((Number) page.detail().get("Length")).longValue());
        assertNotNull(page.detail().get("Make"));
        // And it's correctly unavailable for the trip.
        assertFalse(RvezyAvailability.isAvailable(page.blocked(),
                java.time.LocalDate.parse("2026-08-26"), java.time.LocalDate.parse("2026-09-04")));
    }
}
