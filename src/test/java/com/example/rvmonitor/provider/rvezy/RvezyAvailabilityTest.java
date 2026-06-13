package com.example.rvmonitor.provider.rvezy;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RvezyAvailabilityTest {

    private static String[] r(String s, String e) { return new String[]{s, e}; }
    private static LocalDate d(String s) { return LocalDate.parse(s); }

    @Test
    void bookedNightInRangeMakesItUnavailable() {
        // The real Cultus case: blocked Aug 29–Sep 4, trip Aug 26 → Sep 4.
        List<String[]> blocked = List.of(
                r("2026-08-29", "2026-08-29"), r("2026-08-30", "2026-08-30"),
                r("2026-09-01", "2026-09-01"), r("2026-09-03", "2026-09-03"));
        assertFalse(RvezyAvailability.isAvailable(blocked, d("2026-08-26"), d("2026-09-04")));
    }

    @Test
    void fullyFreeRangeIsAvailable() {
        List<String[]> blocked = List.<String[]>of(r("2026-07-01", "2026-07-10"), r("2026-10-01", "2026-10-05"));
        assertTrue(RvezyAvailability.isAvailable(blocked, d("2026-08-26"), d("2026-09-04")));
    }

    @Test
    void blockOnDropoffDayOnlyIsStillAvailable() {
        // You return on the end date, so a block starting that day doesn't conflict.
        List<String[]> blocked = List.<String[]>of(r("2026-09-04", "2026-09-06"));
        assertTrue(RvezyAvailability.isAvailable(blocked, d("2026-08-26"), d("2026-09-04")));
    }

    @Test
    void blockOnPickupDayConflicts() {
        List<String[]> blocked = List.<String[]>of(r("2026-08-26", "2026-08-26"));
        assertFalse(RvezyAvailability.isAvailable(blocked, d("2026-08-26"), d("2026-09-04")));
    }

    @Test
    void multiDayBlockSpanningRangeConflicts() {
        List<String[]> blocked = List.<String[]>of(r("2026-08-20", "2026-08-28"));
        assertFalse(RvezyAvailability.isAvailable(blocked, d("2026-08-26"), d("2026-09-04")));
    }

    @Test
    void emptyCalendarIsAvailable() {
        assertTrue(RvezyAvailability.isAvailable(List.of(), d("2026-08-26"), d("2026-09-04")));
    }
}
