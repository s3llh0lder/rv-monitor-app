package com.example.rvmonitor.service;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import com.example.rvmonitor.provider.RvProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduled poller. Each cycle it runs every enabled {@link Watch} through its
 * provider, applies the universal filters (radius, max price), and emails when a
 * listing appears that we haven't already alerted on. Dedup and politeness
 * (jitter + inter-watch spacing) follow banff-monitor.
 */
@Service
public class RvMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(RvMonitorService.class);

    private final MonitorConfig config;
    private final EmailService emailService;
    private final Map<String, RvProvider> providers;

    /** Keys ("watch|provider|listingId") we've already alerted on, to avoid spam. */
    private final Set<String> notifiedKeys = new HashSet<>();
    private final Random jitter = new Random();

    public RvMonitorService(MonitorConfig config, EmailService emailService, List<RvProvider> providerList) {
        this.config = config;
        this.emailService = emailService;
        this.providers = providerList.stream()
                .collect(Collectors.toMap(p -> p.key().toLowerCase(), p -> p));
        logger.info("Registered providers: {}", providers.keySet());
    }

    @Scheduled(fixedRateString = "${rv.monitor.check-interval}")
    public void poll() {
        List<Watch> enabled = config.getWatches().stream().filter(Watch::isEnabled).toList();
        if (enabled.isEmpty()) {
            logger.warn("No enabled watches configured.");
            return;
        }

        long jitterMs = config.getPolite().getJitterMaxMs() > 0
                ? jitter.nextLong(config.getPolite().getJitterMaxMs() + 1) : 0;
        long interDelayMs = config.getPolite().getInterWatchDelayMs();
        logger.info("Polling {} watch(es) — jitter {}ms, inter-delay {}ms", enabled.size(), jitterMs, interDelayMs);
        sleepQuietly(jitterMs);

        for (int i = 0; i < enabled.size(); i++) {
            Watch w = enabled.get(i);
            try {
                processWatch(w);
            } catch (Exception e) {
                logger.error("Error processing watch '{}': {}", w.getName(), e.toString());
            }
            if (i < enabled.size() - 1) {
                sleepQuietly(interDelayMs);
            }
        }
    }

    private void processWatch(Watch w) throws Exception {
        List<RvListing> matches = searchNow(w);
        logger.info("Watch '{}' [{} → {}]: {} match(es) after filters",
                w.getName(), w.getStartDate(), w.getEndDate(), matches.size());

        String prefix = w.getName() + "|";
        Set<String> currentKeys = new HashSet<>();
        List<RvListing> fresh = new ArrayList<>();
        for (RvListing rv : matches) {
            String k = keyFor(w, rv);
            currentKeys.add(k);
            if (!notifiedKeys.contains(k)) {
                fresh.add(rv);
            }
        }

        if (!fresh.isEmpty()) {
            logger.info("  {} NEW listing(s) for '{}' — notifying", fresh.size(), w.getName());
            emailService.sendNewListings(w, fresh);
            notifiedKeys.addAll(currentKeys);
        }
        // Drop keys for listings that disappeared so they re-alert if they return.
        notifiedKeys.removeIf(k -> k.startsWith(prefix) && !currentKeys.contains(k));
    }

    /**
     * Runs a watch through its provider and applies universal filters, without
     * touching alert state. Used by the scheduler and the on-demand controller.
     */
    public List<RvListing> searchNow(Watch w) throws Exception {
        RvProvider provider = providers.get(w.getProvider() == null ? "rvezy" : w.getProvider().toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("No provider registered for '" + w.getProvider()
                    + "'. Known: " + providers.keySet());
        }
        List<RvListing> all = provider.search(w);
        List<RvListing> matches = all.stream().filter(rv -> passesFilters(w, rv)).toList();
        // Verify + enrich the final matches: e.g. RVezy drops listings booked for
        // the dates (the search doesn't guarantee availability) and adds length/make/model.
        return provider.refine(w, matches);
    }

    boolean passesFilters(Watch w, RvListing rv) {
        if (w.getMaxPrice() != null && rv.getNightlyPrice() != null
                && rv.getNightlyPrice() > w.getMaxPrice()) {
            return false;
        }
        if (w.getRadiusKm() != null && rv.getDistanceKm() != null
                && rv.getDistanceKm() > w.getRadiusKm()) {
            return false;
        }
        // Enforce RV type here: RVezy's query filter is unreliable, and providers
        // spell the type differently ("ClassC" vs "Class C"), so compare normalized.
        if (w.getRvType() != null && !w.getRvType().isBlank() && rv.getRvType() != null
                && !normalizeType(w.getRvType()).equals(normalizeType(rv.getRvType()))) {
            return false;
        }
        return true;
    }

    /** Lower-case and strip non-alphanumerics so "Class C" == "ClassC" == "class_c". */
    static String normalizeType(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String keyFor(Watch w, RvListing rv) {
        return w.getName() + "|" + rv.getProvider() + "|" + rv.getId();
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // exposed for tests
    Set<String> getNotifiedKeys() { return notifiedKeys; }
    Map<String, RvProvider> getProviders() { return providers; }
}
