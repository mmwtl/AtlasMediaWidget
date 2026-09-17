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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
        File stagingDir = new File(context.getFilesDir(), "staging_inspect_" + UUID.randomUUID());
        if (!stagingDir.mkdirs()) throw new IOException("Не удалось подготовить импорт");
        try {
            return inspectStaged(context, sourceUri, stagingDir);
        } catch (IOException | RuntimeException error) {
            deleteRecursively(stagingDir);
            throw error;
        }
    }

    private static Preview inspectStaged(Context context, Uri sourceUri, File stagingDir) throws IOException {
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
        unpackZip(incomingFile, extractedDir);

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

        // Older archives may lack hashes; every declared hash must be valid.
        if (manifest.has("hashes")) {
            try {
                JSONObject hashes = manifest.getJSONObject("hashes");
                java.util.Iterator<String> keys = hashes.keys();
                while (keys.hasNext()) {
                    String relativePath = keys.next();
                    String expectedHash = hashes.getString(relativePath);
                    File f = safeEntryFile(extractedDir, relativePath);
                    if (!f.isFile() || !expectedHash.matches("[0-9a-fA-F]{64}")) {
                        throw new IOException("Отсутствует или повреждён файл " + relativePath);
                    }
                    {
                        String actualHash = computeFileSha256(f);
                        if (!actualHash.equalsIgnoreCase(expectedHash)) {
                            throw new IOException("Нарушена целостность файла " + relativePath);
                        }
                    }
                }
            } catch (JSONException e) {
                throw new IOException("Повреждённый список контрольных сумм", e);
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
                if (!FORMAT_MEDIA.equals(mediaJson.optString("format"))
                        || mediaJson.optInt("schemaVersion", 0) != SCHEMA_VERSION) {
                    throw new IOException("Неподдерживаемый формат или версия media.json");
                }
                catalogMode = mediaJson.optString("catalogMode", "builtin");
                if (!"builtin".equals(catalogMode) && !"custom".equals(catalogMode)) {
                    throw new IOException("Неверный режим каталога радио");
                }
                mediaData = new MediaSettingsSnapshot(
                        0L,
                        mediaJson.optString("defaultAudioSource", ""),
                        mediaJson.optInt("defaultAudioSourceDelaySec", 0),
                        mediaJson.optBoolean("defaultAudioSourceAutoplayOnStartup", true),
                        mediaJson.optBoolean("autoSwitchToDefaultOnSourceLost", false),
                        mediaJson.optBoolean("autoSwitchToDefaultAutoplayOnSourceLost", true),
                        mediaJson.optString("defaultMediaPackage", ""),
                        mediaJson.optBoolean("minimizeOnlinePlayerAfterAutostart", false),
                        mediaJson.optBoolean("switchToOnlineBeforeSessionPlay", false),
                        mediaJson.optBoolean("radioWidgetBroadcastEnabled", true),
                        mediaJson.optBoolean("clusterCoversEnabled", true),
                        mediaJson.optBoolean("clusterOnlineEnabled", false),
                        mediaJson.optBoolean("clusterOnlineProgressEnabled", false)
                                || mediaJson.optBoolean("clusterOnlineFacadeProgressEnabled", false),
                        mediaJson.optLong("clusterWatchdogIntervalMs", 1250L),
                        mediaJson.optLong("clusterReassertBurstIntervalMs", 100L),
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
            if (!stationsCsv.isFile()) throw new IOException("Отсутствует radio/stations.csv");
            if (stationsCsv.isFile()) {
                hasRadio = true;
                // Count lines / stations
                stationCount = countCsvStations(stationsCsv);
            }
        }

        if (!hasWidget && !hasMedia) throw new IOException("Архив не содержит настроек");
        if (manifest.has("sections")) {
            try {
                JSONArray sections = manifest.getJSONArray("sections");
                Set<String> declared = new HashSet<>();
                for (int i = 0; i < sections.length(); i++) {
                    String section = sections.getString(i);
                    if (!declared.add(section) || !Set.of("widget", "media", "radio").contains(section)) {
                        throw new IOException("Неверный список секций архива");
                    }
                }
                if (declared.contains("widget") != hasWidget || declared.contains("media") != hasMedia
                        || declared.contains("radio") != hasRadio) {
                    throw new IOException("Состав архива не соответствует manifest.json");
                }
            } catch (JSONException error) {
                throw new IOException("Повреждённый список секций", error);
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

        if (mediaPartZip != null && mediaPartZip.isFile()) {
            extractedMediaDir = new File(exportDir, "extracted_media_" + System.currentTimeMillis());
            extractedMediaDir.mkdirs();
            unpackZip(mediaPartZip, extractedMediaDir);
            File mediaFile = new File(extractedMediaDir, "media.json");
            if (mediaFile.isFile()) {
                sections.put("media");
                try {
                    JSONObject mediaSettings = new JSONObject(readFileToString(mediaFile));
                    mediaSettings.remove("catalogMode");
                    mediaBytes = mediaSettings.toString(2).getBytes(StandardCharsets.UTF_8);
                } catch (JSONException error) {
                    throw new IOException("Повреждённый media.json", error);
                }
                try {
                    hashes.put("media.json", computeBytesSha256(mediaBytes));
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


        } finally {
            if (extractedMediaDir != null) {
                deleteRecursively(extractedMediaDir);
            }
        }

        return zipFile;
    }

    private static void unpackZip(File zipFile, File targetDir) throws IOException {
        Set<String> entries = new HashSet<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > MAX_ENTRIES) throw new IOException("Слишком много файлов в архиве");
                File dest = safeEntryFile(targetDir, entry.getName());
                if (!entries.add(dest.getCanonicalPath())) throw new IOException("Дублирующийся путь в архиве");
                if (entry.isDirectory()) {
                    dest.mkdirs();
                } else {
                    dest.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = zis.read(buf)) != -1) {
                            totalBytes += len;
                            if (totalBytes > MAX_UNCOMPRESSED_BYTES) throw new IOException("Архив слишком большой");
                            fos.write(buf, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static File safeEntryFile(File directory, String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\")) {
            throw new SecurityException("Небезопасный путь в архиве: " + name);
        }
        File target = new File(directory, name);
        if (!target.getCanonicalPath().startsWith(directory.getCanonicalPath() + File.separator)) {
            throw new SecurityException("Небезопасный путь в архиве: " + name);
        }
        return target;
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
        if (file.length() > 256L * 1024L) throw new IOException("Файл настроек слишком большой");
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
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
