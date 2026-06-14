package com.example.rvmonitor.provider.rvezy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the {@code <script id="__NUXT_DATA__">} payload that RVezy's Nuxt 3
 * SSR embeds in every search page.
 *
 * <p>The payload is a flat JSON array serialized with <em>devalue</em>: entry 0
 * is the root and every other value is referenced by its integer index into the
 * array, which lets the format share/encode cyclic structures. To read it you
 * pick an index and dereference: scalars are returned as-is, an object's field
 * values are themselves indices, and so are an array's elements.
 *
 * <p>We don't need the whole graph — only the search-result listings. Rather
 * than chase the {@code ListRVs} container (the page embeds more than one — a
 * 3-item "featured" set and the ~30-item results page), {@link
 * #extractListings(String)} detects listings <em>structurally</em>: any object
 * carrying both {@code AliasName} and {@code RVName} is a listing. Each is
 * resolved into plain {@code Map}/{@code List}/scalar values and de-duplicated
 * by its {@code Id}.
 */
public final class NuxtDataParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern NUXT_DATA = Pattern.compile(
            "id=\"__NUXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final int MAX_DEPTH = 24;

    private final List<JsonNode> entries;

    private NuxtDataParser(JsonNode array) {
        this.entries = new ArrayList<>(array.size());
        array.forEach(entries::add);
    }

    /** Returns the resolved, de-duplicated listing objects (empty if none). */
    public static List<Map<String, Object>> extractListings(String html) {
        Matcher m = NUXT_DATA.matcher(html);
        if (!m.find()) {
            return List.of();
        }
        JsonNode array;
        try {
            array = MAPPER.readTree(m.group(1));
        } catch (Exception e) {
            return List.of();
        }
        if (array == null || !array.isArray()) {
            return List.of();
        }
        return new NuxtDataParser(array).collectListings();
    }

    /** A listing page's blocked date ranges plus its scalar detail fields. */
    public record ListingPage(List<String[]> blocked, Map<String, Object> detail) {}

    /**
     * Parses a listing page once and returns BOTH its blocked {@code Calendars}
     * ranges (authoritative availability) and its detail scalars (Length, Make,
     * Model, …). The public listing page is the reliable source for both — the
     * authenticated get-by-id returns {@code Calendars: null}.
     */
    public static ListingPage extractListingPage(String html) {
        JsonNode array = parseNuxtData(html);
        if (array == null) {
            return new ListingPage(List.of(), Map.of());
        }
        NuxtDataParser p = new NuxtDataParser(array);
        return new ListingPage(p.collectCalendarRanges(), p.collectListingDetail());
    }

    /** The listing's scalar detail fields (RVName, Length, Make, Model, …). */
    private Map<String, Object> collectListingDetail() {
        String[] want = {"RVName", "Length", "Make", "Model", "Year", "Guests",
                "RVType", "AverageRating", "NumberOfReviews", "DefaultPrice"};
        for (JsonNode e : entries) {
            if (e.isObject() && e.has("RVName") && e.has("Length")) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (String k : want) {
                    if (e.has(k)) {
                        Object v = scalarRef(e.get(k));
                        if (v != null) m.put(k, v);
                    }
                }
                return m;
            }
        }
        return Map.of();
    }

    /** Resolve a member that is either a literal scalar or an index to a scalar. */
    private Object scalarRef(JsonNode member) {
        if (member.isInt()) {
            int idx = member.intValue();
            return (idx >= 0 && idx < entries.size()) ? scalar(entries.get(idx)) : null;
        }
        return scalar(member);
    }

    private static JsonNode parseNuxtData(String html) {
        Matcher m = NUXT_DATA.matcher(html);
        if (!m.find()) {
            return null;
        }
        try {
            JsonNode array = MAPPER.readTree(m.group(1));
            return array != null && array.isArray() ? array : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the blocked/unavailable date ranges from a listing page's
     * {@code Calendars} array, as {@code [startDate, endDate]} ISO-date string
     * pairs. Empty if the page has no calendar (e.g. a search page).
     */
    public static List<String[]> extractCalendarRanges(String html) {
        Matcher m = NUXT_DATA.matcher(html);
        if (!m.find()) {
            return List.of();
        }
        JsonNode array;
        try {
            array = MAPPER.readTree(m.group(1));
        } catch (Exception e) {
            return List.of();
        }
        if (array == null || !array.isArray()) {
            return List.of();
        }
        return new NuxtDataParser(array).collectCalendarRanges();
    }

    @SuppressWarnings("unchecked")
    private List<String[]> collectCalendarRanges() {
        for (int i = 0; i < entries.size(); i++) {
            JsonNode entry = entries.get(i);
            if (!entry.isObject() || !entry.has("Calendars") || !entry.get("Calendars").isInt()) {
                continue;
            }
            Object resolved = resolve(entry.get("Calendars").intValue(), 0, new HashSet<>());
            if (!(resolved instanceof List<?> list)) {
                continue;
            }
            List<String[]> ranges = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> map) {
                    Object s = ((Map<String, Object>) map).get("StartDate");
                    Object e = ((Map<String, Object>) map).get("EndDate");
                    if (s != null && e != null) {
                        ranges.add(new String[]{String.valueOf(s), String.valueOf(e)});
                    }
                }
            }
            return ranges;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> collectListings() {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Object> seenIds = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            JsonNode entry = entries.get(i);
            if (!entry.isObject() || !entry.has("AliasName") || !entry.has("RVName")) {
                continue;
            }
            Object resolved = resolve(i, 0, new HashSet<>());
            if (!(resolved instanceof Map<?, ?> map)) {
                continue;
            }
            // Dedupe by listing Id; fall back to AliasName when Id is absent.
            Object id = map.get("Id");
            Object key = id != null ? id : map.get("AliasName");
            if (key != null && !seenIds.add(key)) {
                continue;
            }
            out.add((Map<String, Object>) map);
        }
        return out;
    }

    /**
     * Dereferences {@code entries[index]} into plain Java values. Objects and
     * arrays are walked recursively; their members are themselves indices. A
     * visited-set on the current path plus a depth cap guard against the cycles
     * the devalue format is allowed to contain.
     */
    private Object resolve(int index, int depth, Set<Integer> path) {
        if (index < 0 || index >= entries.size() || depth > MAX_DEPTH || !path.add(index)) {
            return null;
        }
        try {
            JsonNode node = entries.get(index);
            if (node.isObject()) {
                Map<String, Object> obj = new LinkedHashMap<>();
                node.fields().forEachRemaining(e ->
                        obj.put(e.getKey(), deref(e.getValue(), depth, path)));
                return obj;
            }
            if (node.isArray()) {
                List<Object> list = new ArrayList<>(node.size());
                node.forEach(child -> list.add(deref(child, depth, path)));
                return list;
            }
            return scalar(node);
        } finally {
            path.remove(index);
        }
    }

    /** A member value: an int is a reference to follow; anything else is literal. */
    private Object deref(JsonNode member, int depth, Set<Integer> path) {
        if (member.isInt()) {
            return resolve(member.intValue(), depth + 1, path);
        }
        return scalar(member);
    }

    private Object scalar(JsonNode node) {
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNull()) return null;
        if (node.isNumber()) {
            return node.isIntegralNumber() ? (Object) node.longValue() : (Object) node.doubleValue();
        }
        return null;
    }
}
