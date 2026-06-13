package com.example.rvmonitor.model;

/**
 * A single available RV returned by a provider for a watch's dates.
 * Field set is the lowest common denominator across providers; RVezy fills
 * all of them, stub providers fill what they can.
 */
public class RvListing {

    private String provider;       // "rvezy", "outdoorsy", ...
    private String id;             // provider listing id (dedup key)
    private String name;           // RVName
    private String rvType;         // e.g. ClassC
    private Integer year;
    private Integer guests;        // sleeps
    private Double lengthFt;       // enrichment only (authenticated get-by-id)
    private String make;           // enrichment only
    private String model;          // enrichment only
    private Double nightlyPrice;   // CAD (AveragePrice)
    private Double rating;         // AverageRating
    private Integer numReviews;
    private boolean superHost;
    private boolean instantBook;
    private String city;
    private String region;         // State/province
    private Double distanceKm;     // from the watch's lat/lon, if known
    private String url;            // direct booking link

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRvType() { return rvType; }
    public void setRvType(String rvType) { this.rvType = rvType; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getGuests() { return guests; }
    public void setGuests(Integer guests) { this.guests = guests; }

    public Double getLengthFt() { return lengthFt; }
    public void setLengthFt(Double lengthFt) { this.lengthFt = lengthFt; }

    public String getMake() { return make; }
    public void setMake(String make) { this.make = make; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Double getNightlyPrice() { return nightlyPrice; }
    public void setNightlyPrice(Double nightlyPrice) { this.nightlyPrice = nightlyPrice; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getNumReviews() { return numReviews; }
    public void setNumReviews(Integer numReviews) { this.numReviews = numReviews; }

    public boolean isSuperHost() { return superHost; }
    public void setSuperHost(boolean superHost) { this.superHost = superHost; }

    public boolean isInstantBook() { return instantBook; }
    public void setInstantBook(boolean instantBook) { this.instantBook = instantBook; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    /** Compact one-line summary for emails / logs. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(name == null ? "(unnamed)" : name);
        if (year != null) sb.append(" '").append(year % 100);
        if (lengthFt != null) sb.append(" — ").append(stripDecimal(lengthFt)).append("ft");
        if (nightlyPrice != null) sb.append(" — $").append(stripDecimal(nightlyPrice)).append("/night");
        if (rating != null && rating > 0) {
            sb.append(" — ").append(rating).append("★");
            if (numReviews != null) sb.append(" (").append(numReviews).append(")");
        }
        if (superHost) sb.append(" — Superhost");
        if (instantBook) sb.append(" — Instant Book");
        if (city != null) sb.append(" — ").append(city);
        return sb.toString();
    }

    private static String stripDecimal(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
