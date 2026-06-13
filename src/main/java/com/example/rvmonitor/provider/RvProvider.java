package com.example.rvmonitor.provider;

import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;

import java.util.List;

/**
 * A rental source. Implementations turn a {@link Watch} (location + dates +
 * filters) into the list of RVs that are AVAILABLE for those dates.
 *
 * Keep implementations stateless and side-effect free — filtering, dedup and
 * notification all live in the monitor service so providers stay swappable.
 */
public interface RvProvider {

    /** Lower-case key matched against {@link Watch#getProvider()} (e.g. "rvezy"). */
    String key();

    /** Available RVs for the watch's dates. Never null; empty on no matches. */
    List<RvListing> search(Watch watch) throws Exception;

    /**
     * Optionally verify and enrich the FINAL matches (after the monitor has
     * filtered them), returning the listings to actually report. This is where a
     * provider does expensive per-listing work it shouldn't spend on discarded
     * candidates: RVezy verifies each listing's real calendar (dropping ones
     * booked for the watch dates — the SSR search doesn't guarantee availability)
     * and folds in length/make/model. Default returns the matches unchanged.
     */
    default List<RvListing> refine(Watch watch, List<RvListing> matches) {
        return matches;
    }
}
