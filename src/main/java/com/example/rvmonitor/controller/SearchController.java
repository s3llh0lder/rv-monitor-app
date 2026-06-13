package com.example.rvmonitor.controller;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import com.example.rvmonitor.service.RvMonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * On-demand search — handy for testing and ad-hoc queries.
 *
 * <pre>
 * # By the dates/filters of a configured watch (by index):
 * GET /api/search/watch/0
 *
 * # Or fully ad-hoc:
 * GET /api/search?provider=rvezy&address=Richmond,BC&lat=49.1666&lon=-123.1336
 *     &start=2026-08-26&end=2026-09-04&maxPrice=250&pet=true&rvType=ClassC
 * </pre>
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final RvMonitorService monitor;
    private final MonitorConfig config;

    public SearchController(RvMonitorService monitor, MonitorConfig config) {
        this.monitor = monitor;
        this.config = config;
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(defaultValue = "rvezy") String provider,
            @RequestParam String address,
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "false") boolean pet,
            @RequestParam(required = false) String rvType) {

        Watch w = new Watch();
        w.setName("ad-hoc");
        w.setProvider(provider);
        w.setAddress(address);
        w.setLatitude(lat);
        w.setLongitude(lon);
        w.setStartDate(start);
        w.setEndDate(end);
        w.setMaxPrice(maxPrice);
        w.setRadiusKm(radiusKm);
        w.setPetFriendly(pet);
        w.setRvType(rvType);
        return runAndRespond(w);
    }

    @GetMapping("/watch/{index}")
    public ResponseEntity<?> searchWatch(@org.springframework.web.bind.annotation.PathVariable int index) {
        List<Watch> watches = config.getWatches();
        if (index < 0 || index >= watches.size()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "watch index out of range",
                    "watchCount", watches.size()));
        }
        return runAndRespond(watches.get(index));
    }

    private ResponseEntity<?> runAndRespond(Watch w) {
        try {
            List<RvListing> results = monitor.searchNow(w);
            return ResponseEntity.ok(Map.of(
                    "watch", w.getName(),
                    "provider", w.getProvider(),
                    "dates", w.getStartDate() + " → " + w.getEndDate(),
                    "count", results.size(),
                    "results", results));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
