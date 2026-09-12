package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class FullSettingsBackup {
    static final String ZIP_FILE_NAME = "AtlasMediaWidget-backup.zip";
    static final String LEGACY_FILE_NAME = "AtlasMediaWidget-settings.json";

    static final String FORMAT_BACKUP = "atlas-media-backup";
    static final String FORMAT_MEDIA = "atlas-media-settings";
    static final int SCHEMA_VERSION = 1;

    static final long MAX_ZIP_BYTES = 70L * 1024L * 1024L; // 70 MB
    static final long MAX_UNCOMPRESSED_BYTES = 65L * 1024L * 1024L; // 65 MB
    static final int MAX_ENTRIES = 305;

    static final class Preview {
        final boolean isZip;
        final boolean hasWidget;
        final boolean hasMedia;
        final boolean hasRadio;
        final SettingsBackup.Data widgetData;
        final MediaSettingsSnapshot mediaData;
        final String catalogMode;
        final int stationCount;
        final List<String> warnings;
        final File stagedFile;
        final File stagedDir;

        Preview(
                boolean isZip,
                boolean hasWidget,
                boolean hasMedia,
                boolean hasRadio,
                SettingsBackup.Data widgetData,
                MediaSettingsSnapshot mediaData,
                String catalogMode,
                int stationCount,
                List<String> warnings,
                File stagedFile,
                File stagedDir) {
            this.isZip = isZip;
            this.hasWidget = hasWidget;
            this.hasMedia = hasMedia;
            this.hasRadio = hasRadio;
            this.widgetData = widgetData;
            this.mediaData = mediaData;
            this.catalogMode = catalogMode != null ? catalogMode : "builtin";
            this.stationCount = stationCount;
            this.warnings = warnings != null ? warnings : Collections.emptyList();
            this.stagedFile = stagedFile;
            this.stagedDir = stagedDir;
        }
    }

    private FullSettingsBackup() {}

    static Preview inspect(Context context, Uri sourceUri) throws IOException {
        File stagingDir = new File(context.getFilesDir(), "staging_inspect_" + System.currentTimeMillis());
        stagingDir.delete();
        stagingDir.mkdirs();
        File incomingFile = new File(stagingDir, "incoming.bin");

        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(incomingFile)) {
            if (in == null) throw new IOException("Не удалось открыть входной файл");
            byte[] buf = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_ZIP_BYTES) {
                    throw new IOException("Размер файла превышает допустимый лимит (70 МБ)");
                }
                out.write(buf, 0, read);
            }
        }

        // Check if ZIP (magic PK 0x50, 0x4B, 0x03, 0x04)
        boolean isZip = false;
        try (InputStream in = new FileInputStream(incomingFile)) {
            byte[] magic = new byte[4];
            int r = in.read(magic);
            if (r == 4 && magic[0] == 0x50 && magic[1] == 0x4B) {
                isZip = true;
            }
        }

        if (!isZip) {
            // Try legacy JSON schema 1-9
            SettingsBackup.Data legacyData;
            try {
                legacyData = SettingsBackup.read(context, Uri.fromFile(incomingFile));
            } catch (Exception e) {
                stagingDir.delete();
                throw new IOException("Файл не является корректным ZIP-архивом или JSON-файлом настроек", e);
            }
            List<String> warnings = new ArrayList<>();
            warnings.add("Резервная копия содержит только настройки карточки виджета (JSON). Медианастройки и радио не изменятся.");
            return new Preview(
                    false,
                    true,
                    false,
                    false,
                    legacyData,
                    null,
                    "builtin",
                    0,
                    warnings,
                    incomingFile,
                    stagingDir);
        }

        // Unpack ZIP and validate
        File extractedDir = new File(stagingDir, "extracted");
        extractedDir.mkdirs();
        int entriesCount = 0;
        long totalUncompressed = 0;
        String stagingCanonical = extractedDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(incomingFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entriesCount++;
                if (entriesCount > MAX_ENTRIES) {
                    throw new IOException("Превышено максимальное количество файлов в архиве (" + MAX_ENTRIES + ")");
                }
                String name = entry.getName().replace('\\', '/').replaceAll("^/+", "");
                File target = new File(extractedDir, name);
                String targetCanonical = target.getCanonicalPath();
                if (!targetCanonical.equals(stagingCanonical) && !targetCanonical.startsWith(stagingCanonical + File.separator)) {
                    throw new SecurityException("Небезопасный путь в ZIP архиве: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    target.mkdirs();
                } else {
                    target.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) != -1) {
                            totalUncompressed += len;
                            if (totalUncompressed > MAX_UNCOMPRESSED_BYTES) {
                                throw new IOException("Превышен допустимый размер распакованного архива (65 МБ)");
                            }
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        File manifestFile = new File(extractedDir, "manifest.json");
        if (!manifestFile.isFile()) {
            throw new IOException("В архиве отсутствует manifest.json");
        }

        String manifestJsonStr = readFileToString(manifestFile);
        JSONObject manifest;
        try {
            manifest = new JSONObject(manifestJsonStr);
            String format = manifest.optString("format", "");
            if (!FORMAT_BACKUP.equals(format)) {
                throw new IOException("Неверный формат архива: " + format);
            }
            int schemaVersion = manifest.optInt("schemaVersion", 0);
            if (schemaVersion != SCHEMA_VERSION) {
                throw new IOException("Неподдерживаемая версия схемы архива: " + schemaVersion);
            }
        } catch (JSONException e) {
            throw new IOException("Повреждённый manifest.json", e);
        }

        // Verify hashes if present
        if (manifest.has("hashes")) {
            try {
                JSONObject hashes = manifest.getJSONObject("hashes");
                java.util.Iterator<String> keys = hashes.keys();
                while (keys.hasNext()) {
                    String relativePath = keys.next();
                    String expectedHash = hashes.getString(relativePath);
                    File f = new File(extractedDir, relativePath);
                    if (f.isFile()) {
                        String actualHash = computeFileSha256(f);
                        if (!actualHash.equalsIgnoreCase(expectedHash)) {
                            throw new IOException("Нарушена целостность файла " + relativePath);
                        }
                    }
                }
            } catch (JSONException e) {
                AppLog.warn("Failed to check hashes in manifest", e);
            }
        }

        boolean hasWidget = false;
        SettingsBackup.Data widgetData = null;
        File widgetFile = new File(extractedDir, "widget.json");
        if (widgetFile.isFile()) {
            hasWidget = true;
            String widgetJson = readFileToString(widgetFile);
            widgetData = SettingsBackup.decode(widgetJson);
        }

        boolean hasMedia = false;
        MediaSettingsSnapshot mediaData = null;
        String catalogMode = "builtin";
        int stationCount = 0;
        List<String> warnings = new ArrayList<>();
        File mediaFile = new File(extractedDir, "media.json");
        if (mediaFile.isFile()) {
            hasMedia = true;
            String mediaJsonStr = readFileToString(mediaFile);
            try {
                JSONObject mediaJson = new JSONObject(mediaJsonStr);
                catalogMode = mediaJson.optString("catalogMode", "builtin");
                mediaData = new MediaSettingsSnapshot(
                        0L,
                        mediaJson.optString("defaultAudioSource", ""),
                        mediaJson.optInt("defaultAudioSourceDelaySec", 0),
                        mediaJson.optBoolean("defaultAudioSourceAutoplayOnStartup", true),
                        mediaJson.optBoolean("autoSwitchToDefaultOnSourceLost", false),
                        mediaJson.optBoolean("autoSwitchToDefaultAutoplayOnSourceLost", true),
                        mediaJson.optString("defaultMediaPackage", ""),
                        mediaJson.optBoolean("switchToOnlineBeforeSessionPlay", false),
                        mediaJson.optBoolean("radioWidgetBroadcastEnabled", true),
                        mediaJson.optBoolean("clusterCoversEnabled", true),
                        mediaJson.optLong("clusterWatchdogIntervalMs", 1250L),
                        catalogMode.toUpperCase(),
                        0,
                        "",
                        15);

                String defaultMediaPkg = mediaJson.optString("defaultMediaPackage", "");
                if (!defaultMediaPkg.isEmpty()) {
                    try {
                        context.getPackageManager().getPackageInfo(defaultMediaPkg, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        warnings.add("Проигрыватель '" + defaultMediaPkg + "' не установлен на устройстве");
                    }
                }
            } catch (JSONException e) {
                throw new IOException("Повреждённый media.json", e);
            }
        }

        boolean hasRadio = false;
        if ("custom".equalsIgnoreCase(catalogMode)) {
            File radioDir = new File(extractedDir, "radio");
            File stationsCsv = new File(radioDir, "stations.csv");
            if (stationsCsv.isFile()) {
                hasRadio = true;
                // Count lines / stations
                stationCount = countCsvStations(stationsCsv);
            }
        }

        warnings.add("Системные разрешения и избранное OneOS не переносятся.");

        return new Preview(
                true,
                hasWidget,
                hasMedia,
                hasRadio,
                widgetData,
                mediaData,
                catalogMode,
                stationCount,
                warnings,
                incomingFile,
                stagingDir);
    }

    static File createFullBackupZip(Context context, Prefs prefs, File mediaPartZip) throws IOException {
        File exportDir = new File(context.getFilesDir(), "staging_export");
        exportDir.mkdirs();
        File zipFile = new File(exportDir, ZIP_FILE_NAME);
        zipFile.delete();

        String widgetJson = SettingsBackup.encode(context, prefs);
        byte[] widgetBytes = widgetJson.getBytes(StandardCharsets.UTF_8);
        String widgetHash = computeBytesSha256(widgetBytes);

        JSONObject hashes = new JSONObject();
        JSONArray sections = new JSONArray();
        sections.put("widget");
        try {
            hashes.put("widget.json", widgetHash);
        } catch (JSONException ignored) {}

        byte[] mediaBytes = null;
        File extractedMediaDir = null;
        boolean isCustomCatalog = false;

        if (mediaPartZip != null && mediaPartZip.isFile()) {
            extractedMediaDir = new File(exportDir, "extracted_media_" + System.currentTimeMillis());
            extractedMediaDir.mkdirs();
            unpackZip(mediaPartZip, extractedMediaDir);
            File mediaFile = new File(extractedMediaDir, "media.json");
            if (mediaFile.isFile()) {
                sections.put("media");
                mediaBytes = readFileToBytes(mediaFile);
                try {
                    hashes.put("media.json", computeBytesSha256(mediaBytes));
                } catch (JSONException ignored) {}
            }
            File radioDir = new File(extractedMediaDir, "radio");
            File stationsCsv = new File(radioDir, "stations.csv");
            if (stationsCsv.isFile()) {
                isCustomCatalog = true;
                sections.put("radio");
                try {
                    hashes.put("radio/stations.csv", computeFileSha256(stationsCsv));
                } catch (JSONException ignored) {}
            }
        }

        JSONObject manifest = new JSONObject();
        byte[] manifestBytes;
        try {
            manifest.put("format", FORMAT_BACKUP);
            manifest.put("schemaVersion", SCHEMA_VERSION);
            manifest.put("originPackage", context.getPackageName());
            manifest.put("createdAt", System.currentTimeMillis());
            manifest.put("sections", sections);
            manifest.put("hashes", hashes);
            manifestBytes = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException("Не удалось сформировать manifest.json", e);
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            // 1. manifest.json
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            // 2. widget.json
            zos.putNextEntry(new ZipEntry("widget.json"));
            zos.write(widgetBytes);
            zos.closeEntry();

            // 3. media.json
            if (mediaBytes != null) {
                zos.putNextEntry(new ZipEntry("media.json"));
                zos.write(mediaBytes);
                zos.closeEntry();
            }

            // 4. radio/
            if (isCustomCatalog && extractedMediaDir != null) {
                File radioDir = new File(extractedMediaDir, "radio");
                addDirectoryToZip(zos, radioDir, "radio/");
            }
        } finally {
            if (extractedMediaDir != null) {
                deleteRecursively(extractedMediaDir);
            }
        }

        return zipFile;
    }

    private static void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, prefix + file.getName() + "/");
            } else if (file.isFile()) {
                zos.putNextEntry(new ZipEntry(prefix + file.getName()));
                try (InputStream in = new FileInputStream(file)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        zos.write(buf, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static void unpackZip(File zipFile, File targetDir) throws IOException {
        String canonicalTarget = targetDir.getCanonicalPath();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/').replaceAll("^/+", "");
                File dest = new File(targetDir, name);
                String destCanonical = dest.getCanonicalPath();
                if (!destCanonical.equals(canonicalTarget) && !destCanonical.startsWith(canonicalTarget + File.separator)) {
                    throw new SecurityException("Небезопасный путь: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    dest.mkdirs();
                } else {
                    dest.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static int countCsvStations(File csvFile) {
        int count = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // Header
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    private static String readFileToString(File file) throws IOException {
        return new String(readFileToBytes(file), StandardCharsets.UTF_8);
    }

    private static byte[] readFileToBytes(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                out.write(buf, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String computeFileSha256(File file) throws IOException {
        return computeBytesSha256(readFileToBytes(file));
    }

    private static String computeBytesSha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursively(c);
            }
        }
        file.delete();
    }
}
