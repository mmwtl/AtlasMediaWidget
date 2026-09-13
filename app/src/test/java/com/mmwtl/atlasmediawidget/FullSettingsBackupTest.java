package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class FullSettingsBackupTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;
    private Prefs prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        context.getSharedPreferences("atlas_media_widget", Context.MODE_PRIVATE).edit().clear().commit();
        prefs = new Prefs(context);
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    public void createFullBackupZipCreatesValidContainerWithHashes() throws Exception {
        File tempMediaZip = temporaryFolder.newFile("temp_media.zip");
        JSONObject mediaJson = new JSONObject();
        mediaJson.put("format", "atlas-media-settings");
        mediaJson.put("schemaVersion", 1);
        mediaJson.put("defaultAudioSource", "BT");
        byte[] mediaBytes = mediaJson.toString(2).getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempMediaZip))) {
            zos.putNextEntry(new ZipEntry("media.json"));
            zos.write(mediaBytes);
            zos.closeEntry();
        }

        File fullZip = FullSettingsBackup.createFullBackupZip(context, prefs, tempMediaZip);
        assertTrue(fullZip.isFile());
        assertTrue(fullZip.length() > 0);

        boolean foundManifest = false;
        boolean foundWidget = false;
        boolean foundMedia = false;
        String manifestContent = null;
        byte[] readWidgetBytes = null;
        byte[] readMediaBytes = null;

        try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(fullZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = zis.read(buf)) != -1) {
                    baos.write(buf, 0, r);
                }
                if ("manifest.json".equals(entry.getName())) {
                    foundManifest = true;
                    manifestContent = baos.toString(StandardCharsets.UTF_8.name());
                } else if ("widget.json".equals(entry.getName())) {
                    foundWidget = true;
                    readWidgetBytes = baos.toByteArray();
                } else if ("media.json".equals(entry.getName())) {
                    foundMedia = true;
                    readMediaBytes = baos.toByteArray();
                }
            }
        }

        assertTrue(foundManifest);
        assertTrue(foundWidget);
        assertTrue(foundMedia);
        assertNotNull(manifestContent);

        JSONObject manifest = new JSONObject(manifestContent);
        assertEquals("atlas-media-backup", manifest.getString("format"));
        assertEquals(1, manifest.getInt("schemaVersion"));
        JSONObject hashes = manifest.getJSONObject("hashes");

        assertEquals(sha256Hex(readWidgetBytes), hashes.getString("widget.json"));
        assertEquals(sha256Hex(readMediaBytes), hashes.getString("media.json"));
    }

    @Test
    public void inspectFullZipParsesWidgetAndMediaSections() throws Exception {
        File zipFile = temporaryFolder.newFile("backup.zip");

        prefs.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 16);
        byte[] widgetBytes = SettingsBackup.encode(context, prefs).getBytes(StandardCharsets.UTF_8);

        JSONObject mediaJson = new JSONObject();
        mediaJson.put("format", "atlas-media-settings");
        mediaJson.put("schemaVersion", 1);
        mediaJson.put("defaultAudioSource", "USB");
        mediaJson.put("defaultAudioSourceDelaySec", 3);
        byte[] mediaBytes = mediaJson.toString(2).getBytes(StandardCharsets.UTF_8);

        JSONObject manifest = new JSONObject();
        manifest.put("format", "atlas-media-backup");
        manifest.put("schemaVersion", 1);
        manifest.put("originPackage", context.getPackageName());
        manifest.put("createdAt", System.currentTimeMillis());
        JSONArray sections = new JSONArray();
        sections.put("widget");
        sections.put("media");
        manifest.put("sections", sections);
        JSONObject hashes = new JSONObject();
        hashes.put("widget.json", sha256Hex(widgetBytes));
        hashes.put("media.json", sha256Hex(mediaBytes));
        manifest.put("hashes", hashes);
        byte[] manifestBytes = manifest.toString(2).getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("widget.json"));
            zos.write(widgetBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("media.json"));
            zos.write(mediaBytes);
            zos.closeEntry();
        }

        FullSettingsBackup.Preview preview = FullSettingsBackup.inspect(context, Uri.fromFile(zipFile));
        assertTrue(preview.isZip);
        assertTrue(preview.hasWidget);
        assertTrue(preview.hasMedia);
        assertFalse(preview.hasRadio);
        assertNotNull(preview.widgetData);
        assertEquals(16, preview.widgetData.appUiScaleTenths);
        assertNotNull(preview.mediaData);
        assertEquals("USB", preview.mediaData.defaultAudioSource);
        assertEquals(3, preview.mediaData.defaultAudioSourceDelaySec);

        // Cleanup staged dir
        FullSettingsBackup.deleteRecursively(preview.stagedDir);
    }

    @Test
    public void inspectRejectsZipWithPathTraversal() throws Exception {
        File zipFile = temporaryFolder.newFile("evil.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("malicious".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        assertThrows(SecurityException.class, () -> {
            FullSettingsBackup.inspect(context, Uri.fromFile(zipFile));
        });
    }

    @Test
    public void inspectRejectsTamperedFileHash() throws Exception {
        File zipFile = temporaryFolder.newFile("tampered.zip");

        byte[] widgetBytes = "{}".getBytes(StandardCharsets.UTF_8);
        JSONObject manifest = new JSONObject();
        manifest.put("format", "atlas-media-backup");
        manifest.put("schemaVersion", 1);
        JSONObject hashes = new JSONObject();
        hashes.put("widget.json", "0000000000000000000000000000000000000000000000000000000000000000"); // wrong hash
        manifest.put("hashes", hashes);
        byte[] manifestBytes = manifest.toString().getBytes(StandardCharsets.UTF_8);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("widget.json"));
            zos.write(widgetBytes);
            zos.closeEntry();
        }

        IOException ex = assertThrows(IOException.class, () -> {
            FullSettingsBackup.inspect(context, Uri.fromFile(zipFile));
        });
        assertTrue(ex.getMessage().contains("целостность"));
    }

    @Test
    public void inspectAcceptsLegacyJsonSettingsBackup() throws Exception {
        File legacyJsonFile = temporaryFolder.newFile("AtlasMediaWidget-settings.json");
        prefs.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 14);
        byte[] legacyBytes = SettingsBackup.encode(context, prefs).getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream fos = new FileOutputStream(legacyJsonFile)) {
            fos.write(legacyBytes);
        }

        FullSettingsBackup.Preview preview = FullSettingsBackup.inspect(context, Uri.fromFile(legacyJsonFile));
        assertFalse(preview.isZip);
        assertTrue(preview.hasWidget);
        assertFalse(preview.hasMedia);
        assertFalse(preview.hasRadio);
        assertNotNull(preview.widgetData);
        assertEquals(14, preview.widgetData.appUiScaleTenths);
        assertFalse(preview.warnings.isEmpty());

        FullSettingsBackup.deleteRecursively(preview.stagedDir);
    }
    @Test
    public void inspectRejectsMissingHashedFileAndCleansStaging() throws Exception {
        File zip = temporaryFolder.newFile("missing.zip");
        JSONObject manifest = new JSONObject().put("format", FullSettingsBackup.FORMAT_BACKUP)
                .put("schemaVersion", 1)
                .put("hashes", new JSONObject().put("media.json", "0".repeat(64)));
        writeEntries(zip, new String[]{"manifest.json"}, new String[]{manifest.toString()});
        long before = stagingCount();
        assertThrows(IOException.class, () -> FullSettingsBackup.inspect(context, Uri.fromFile(zip)));
        assertEquals(before, stagingCount());
    }

    @Test
    public void inspectRejectsCanonicalDuplicateEntries() throws Exception {
        File zip = temporaryFolder.newFile("duplicate.zip");
        writeEntries(zip, new String[]{"media.json", "./media.json"}, new String[]{"{}", "{}"});
        assertThrows(IOException.class, () -> FullSettingsBackup.inspect(context, Uri.fromFile(zip)));
    }

    @Test
    public void inspectRejectsFutureMediaSchema() throws Exception {
        File zip = temporaryFolder.newFile("future.zip");
        JSONObject manifest = new JSONObject().put("format", FullSettingsBackup.FORMAT_BACKUP)
                .put("schemaVersion", 1);
        JSONObject media = new JSONObject().put("format", FullSettingsBackup.FORMAT_MEDIA)
                .put("schemaVersion", 2);
        writeEntries(zip, new String[]{"manifest.json", "media.json"},
                new String[]{manifest.toString(), media.toString()});
        assertThrows(IOException.class, () -> FullSettingsBackup.inspect(context, Uri.fromFile(zip)));
    }

    private long stagingCount() {
        File[] files = context.getFilesDir().listFiles((dir, name) -> name.startsWith("staging_inspect_"));
        return files == null ? 0 : files.length;
    }

    private static void writeEntries(File file, String[] names, String[] contents) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            for (int i = 0; i < names.length; i++) {
                out.putNextEntry(new ZipEntry(names[i]));
                out.write(contents[i].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

}
