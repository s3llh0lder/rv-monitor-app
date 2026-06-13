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
     * Optionally add expensive per-listing detail to the FINAL matches (after the
     * monitor has filtered them), mutating each in place. Default is a no-op;
     * RVezy uses it for authenticated enrichment (length, make/model). Called only
     * on the filtered set so we never spend calls on listings we'd discard.
     */
    default void enrich(List<RvListing> matches) {
        // no-op by default
    }
}
