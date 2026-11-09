package com.mmwtl.atlasmediawidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The city list of a radio pack as the Media Bridge writes it, with search and region groups. */
final class RadioPackCities {
    static final int MAX_SEARCH_RESULTS = 100;

    static final class City {
        final String key;
        final String name;
        final String region;
        final int stationCount;
        private final String searchName;

        City(String key, String name, String region, int stationCount) {
            this.key = key;
            this.name = name;
            this.region = region;
            this.stationCount = stationCount;
            this.searchName = normalize(name);
        }
    }

    private RadioPackCities() {}

    /** Parses `[{"key","name","region","stations"}]`. */
    static List<City> parse(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<City> cities = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            cities.add(new City(item.getString("key"), item.getString("name"),
                    item.optString("region", ""), item.optInt("stations", 0)));
        }
        return Collections.unmodifiableList(cities);
    }

    /**
     * Cities whose name contains [query], case- and «ё»-insensitive; names starting with it come
     * first. At most [limit] results.
     */
    static List<City> search(List<City> cities, String query, int limit) {
        String needle = normalize(query);
        List<City> prefix = new ArrayList<>();
        List<City> inside = new ArrayList<>();
        if (needle.isEmpty()) return prefix;
        for (City city : cities) {
            if (city.searchName.startsWith(needle)) {
                prefix.add(city);
            } else if (city.searchName.contains(needle)) {
                inside.add(city);
            }
        }
        prefix.addAll(inside);
        return prefix.size() > limit ? new ArrayList<>(prefix.subList(0, limit)) : prefix;
    }

    /** Cities grouped by region in order of first appearance; "" holds cities without a region. */
    static Map<String, List<City>> byRegion(List<City> cities) {
        Map<String, List<City>> regions = new LinkedHashMap<>();
        for (City city : cities) {
            List<City> group = regions.get(city.region);
            if (group == null) {
                group = new ArrayList<>();
                regions.put(city.region, group);
            }
            group.add(city);
        }
        return regions;
    }

    static String normalize(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ");
    }
}
