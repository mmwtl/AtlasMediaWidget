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

/**
 * The city list of a radio pack as the Media Bridge writes it, with search and country and region
 * groups.
 */
final class RadioPackCities {
    static final int MAX_SEARCH_RESULTS = 100;

    static final class City {
        final String key;
        final String name;
        final String region;
        final int stationCount;
        /** Optional; "" when the pack does not name countries. */
        final String country;
        private final String searchName;

        City(String key, String name, String region, int stationCount) {
            this(key, name, region, stationCount, "");
        }

        City(String key, String name, String region, int stationCount, String country) {
            this.key = key;
            this.name = name;
            this.region = region;
            this.stationCount = stationCount;
            this.country = country == null ? "" : country;
            this.searchName = normalize(name);
        }
    }

    private RadioPackCities() {}

    /** Parses `[{"key","name","country","region","stations"}]`; country and region are optional. */
    static List<City> parse(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<City> cities = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            cities.add(new City(item.getString("key"), item.getString("name"),
                    item.optString("region", ""), item.optInt("stations", 0),
                    item.optString("country", "")));
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

    /**
     * Cities grouped by country, then by region, both in order of first appearance; "" holds
     * cities without a country.
     */
    static Map<String, Map<String, List<City>>> byCountry(List<City> cities) {
        Map<String, List<City>> countries = new LinkedHashMap<>();
        for (City city : cities) {
            List<City> group = countries.get(city.country);
            if (group == null) {
                group = new ArrayList<>();
                countries.put(city.country, group);
            }
            group.add(city);
        }
        Map<String, Map<String, List<City>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<City>> country : countries.entrySet()) {
            result.put(country.getKey(), byRegion(country.getValue()));
        }
        return result;
    }

    /** Distinct country and region pairs that name a region. */
    static int regionCount(List<City> cities) {
        java.util.Set<String> regions = new java.util.HashSet<>();
        for (City city : cities) {
            if (!city.region.isEmpty()) regions.add(city.country + '\0' + city.region);
        }
        return regions.size();
    }

    static String normalize(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ");
    }
}
