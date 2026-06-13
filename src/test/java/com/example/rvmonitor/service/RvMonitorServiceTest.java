package com.example.rvmonitor.service;

import com.example.rvmonitor.config.MonitorConfig;
import com.example.rvmonitor.model.RvListing;
import com.example.rvmonitor.model.Watch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RvMonitorServiceTest {

    private RvMonitorService service() {
        // EmailService and providers are unused by passesFilters().
        return new RvMonitorService(new MonitorConfig(), null, List.of());
    }

    private RvListing rv(String type, double price) {
        RvListing rv = new RvListing();
        rv.setProvider("rvezy");
        rv.setId("x");
        rv.setRvType(type);
        rv.setNightlyPrice(price);
        return rv;
    }

    @Test
    void enforcesRvTypeClientSide() {
        Watch w = new Watch();
        w.setRvType("ClassC");
        assertTrue(service().passesFilters(w, rv("ClassC", 200)));
        assertFalse(service().passesFilters(w, rv("TravelTrailer", 200)));
    }

    @Test
    void enforcesMaxPrice() {
        Watch w = new Watch();
        w.setMaxPrice(250.0);
        assertTrue(service().passesFilters(w, rv("ClassC", 250)));
        assertFalse(service().passesFilters(w, rv("ClassC", 251)));
    }

    @Test
    void noFiltersPassesEverything() {
        assertTrue(service().passesFilters(new Watch(), rv("Campervan", 999)));
    }
}
