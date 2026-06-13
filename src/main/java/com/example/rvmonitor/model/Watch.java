package com.example.rvmonitor.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A saved search the monitor polls each cycle. Maps to one entry under
 * {@code rv.watches} in application.yml.
 */
public class Watch {

    private String name;
    private String provider = "rvezy";
    private String address;
    private double latitude;
    private double longitude;
    private String startDate;   // YYYY-MM-DD (pickup)
    private String endDate;     // YYYY-MM-DD (drop-off)
    private Double radiusKm;    // optional: drop results farther than this from lat/lon
    private Double maxPrice;    // optional: CAD nightly ceiling (AveragePrice)
    private String rvType;      // optional: e.g. "ClassC" — enforced client-side (RVezy's
                                // own filter is unreliable), matched against RVListing.rvType
    private boolean petFriendly = false;
    private boolean enabled = true;

    /** Provider-specific query params merged into the search URL (e.g. RVType=ClassC). */
    private Map<String, String> extraParams = new LinkedHashMap<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public Double getRadiusKm() { return radiusKm; }
    public void setRadiusKm(Double radiusKm) { this.radiusKm = radiusKm; }

    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }

    public String getRvType() { return rvType; }
    public void setRvType(String rvType) { this.rvType = rvType; }

    public boolean isPetFriendly() { return petFriendly; }
    public void setPetFriendly(boolean petFriendly) { this.petFriendly = petFriendly; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, String> getExtraParams() { return extraParams; }
    public void setExtraParams(Map<String, String> extraParams) { this.extraParams = extraParams; }
}
