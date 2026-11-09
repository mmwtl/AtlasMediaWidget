package com.mmwtl.atlasmediawidget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What a picked radio ZIP is, for the confirmation dialog only: a pack (with `pack.json`) or a
 * single catalog. The Media Bridge validates the archive itself on import.
 */
final class RadioPackPreview {
    static final String MANIFEST = "pack.json";
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;

    final String id;
    final String name;
    final int cityCount;
    final int regionCount;

    RadioPackPreview(String id, String name, int cityCount, int regionCount) {
        this.id = id;
        this.name = name;
        this.cityCount = cityCount;
        this.regionCount = regionCount;
    }

    /** Returns the pack described by the ZIP, or null for a single-catalog ZIP. */
    static RadioPackPreview read(File zip) throws IOException {
        try (ZipFile file = new ZipFile(zip)) {
            ZipEntry entry = file.getEntry(MANIFEST);
            if (entry == null || entry.isDirectory()) return null;
            try (InputStream in = file.getInputStream(entry)) {
                byte[] bytes = readBounded(in);
                return parse(new String(bytes, StandardCharsets.UTF_8));
            }
        }
    }

    /** An unreadable manifest still marks a pack; the import then reports what is wrong. */
    static RadioPackPreview parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray catalogs = root.optJSONArray("catalogs");
            int cities = catalogs == null ? 0 : catalogs.length();
            Set<String> regions = new HashSet<>();
            for (int i = 0; i < cities; i++) {
                JSONObject city = catalogs.optJSONObject(i);
                String region = city == null ? "" : city.optString("region", "").trim();
                if (!region.isEmpty()) regions.add(region);
            }
            return new RadioPackPreview(root.optString("id", ""),
                    root.optString("name", "").trim(), cities, regions.size());
        } catch (org.json.JSONException error) {
            return new RadioPackPreview("", "", 0, 0);
        }
    }

    private static byte[] readBounded(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > MAX_MANIFEST_BYTES) throw new IOException("pack.json слишком большой");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
