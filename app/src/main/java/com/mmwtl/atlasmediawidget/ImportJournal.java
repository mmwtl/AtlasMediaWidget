package com.mmwtl.atlasmediawidget;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class ImportJournal {
    private static final String JOURNAL_FILE = "import_journal.json";
    private static final String PRE_IMPORT_WIDGET_FILE = "pre_import_widget.json";

    private static final String STATE_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATE_COMMITTED = "COMMITTED";
    private static final String STATE_FAILED = "FAILED";

    static final class RecoveryInfo {
        final String id;
        final long timestamp;
        final boolean hasWidget;
        final boolean hasMedia;
        final boolean canRestoreWidget;

        RecoveryInfo(String id, long timestamp, boolean hasWidget, boolean hasMedia, boolean canRestoreWidget) {
            this.id = id;
            this.timestamp = timestamp;
            this.hasWidget = hasWidget;
            this.hasMedia = hasMedia;
            this.canRestoreWidget = canRestoreWidget;
        }
    }

    private ImportJournal() {}

    static String startImport(Context context, Prefs prefs, boolean hasWidget, boolean hasMedia) throws IOException {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        if (hasWidget) {
            try {
                String widgetJson = SettingsBackup.encode(context, prefs);
                writeAtomic(new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE),
                        widgetJson.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                AppLog.warn("Failed to save pre-import widget settings", e);
                throw new IOException("Не удалось создать точку восстановления настроек виджета", e);
            }
        }

        JSONObject journal = new JSONObject();
        try {
            journal.put("id", id);
            journal.put("state", STATE_IN_PROGRESS);
            journal.put("hasWidget", hasWidget);
            journal.put("hasMedia", hasMedia);
            journal.put("timestamp", now);
        } catch (JSONException e) {
            throw new IOException("Ошибка формирования журнала импорта", e);
        }

        writeAtomic(new File(context.getFilesDir(), JOURNAL_FILE),
                journal.toString().getBytes(StandardCharsets.UTF_8));
        return id;
    }

    static void markCommitted(Context context) {
        new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE).delete();
        new File(context.getFilesDir(), JOURNAL_FILE).delete();
    }

    static boolean rollback(Context context, Prefs prefs) {
        boolean rolledBack = false;
        File preImportFile = new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE);
        if (preImportFile.isFile()) {
            try {
                String json = readFileToString(preImportFile);
                SettingsBackup.Data data = SettingsBackup.decode(json);
                rolledBack = prefs.replacePortableSettings(data);
            } catch (Exception e) {
                AppLog.warn("Failed to rollback widget settings from pre-import backup", e);
            }
        }
        preImportFile.delete();
        new File(context.getFilesDir(), JOURNAL_FILE).delete();
        return rolledBack;
    }

    static RecoveryInfo checkPendingRecovery(Context context) {
        File journalFile = new File(context.getFilesDir(), JOURNAL_FILE);
        if (!journalFile.isFile()) return null;

        try {
            String json = readFileToString(journalFile);
            JSONObject obj = new JSONObject(json);
            String state = obj.optString("state", "");
            if (STATE_IN_PROGRESS.equals(state)) {
                String id = obj.optString("id", "");
                long timestamp = obj.optLong("timestamp", 0L);
                boolean hasWidget = obj.optBoolean("hasWidget", false);
                boolean hasMedia = obj.optBoolean("hasMedia", false);
                File preImportFile = new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE);
                boolean canRestore = hasWidget && preImportFile.isFile();
                return new RecoveryInfo(id, timestamp, hasWidget, hasMedia, canRestore);
            }
        } catch (Exception e) {
            AppLog.warn("Failed to read import journal", e);
        }
        return null;
    }

    static void dismissPending(Context context) {
        new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE).delete();
        new File(context.getFilesDir(), JOURNAL_FILE).delete();
    }

    private static void writeAtomic(File target, byte[] bytes) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.flush();
        }
        if (target.exists()) target.delete();
        if (!tmp.renameTo(target)) {
            throw new IOException("Не удалось записать файл " + target.getName());
        }
    }

    private static String readFileToString(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
