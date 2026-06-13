package com.example.rvmonitor.provider.rvezy;

import java.time.LocalDate;
import java.util.List;

/**
 * Availability arithmetic for RVezy listings.
 *
 * <p>RVezy's SSR search returns location/type/relevance matches, NOT a strict
 * full-range availability filter — a listing can appear in results while booked
 * for the requested dates. The authoritative source is the per-listing
 * {@code Calendars}: a set of blocked (booked/owner-held) date ranges. This
 * checks whether a trip's nights are entirely free of those blocks.
 */
public final class RvezyAvailability {

    private RvezyAvailability() {}

    /**
     * True iff none of the {@code blocked} ranges overlaps the trip's nights.
     *
     * <p>A trip from {@code start} (pickup) to {@code end} (drop-off) occupies
     * the nights {@code start .. end-1}; the drop-off day itself is free for the
     * next renter. A blocked inclusive range [bs, be] conflicts when
     * {@code bs < end && be >= start}.
     */
    public static boolean isAvailable(List<String[]> blocked, LocalDate start, LocalDate end) {
        if (start == null || end == null || !start.isBefore(end)) {
            return false; // nonsensical range → treat as not bookable
        }
        for (String[] range : blocked) {
            LocalDate bs = parse(range[0]);
            LocalDate be = parse(range[1]);
            if (bs == null || be == null) {
                continue;
            }
            if (be.isBefore(bs)) {
                LocalDate t = bs; bs = be; be = t; // tolerate reversed
            }
            if (bs.isBefore(end) && !be.isBefore(start)) {
                return false; // overlap with a booked night
            }
        }
        return true;
    }

    private static LocalDate parse(String s) {
        if (s == null || s.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(s.substring(0, 10)); // tolerate trailing time/offset
        } catch (Exception e) {
            return null;
        }
    }
}
