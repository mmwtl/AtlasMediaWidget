package com.mmwtl.atlasmediawidget;

import android.content.Context;
import android.util.AtomicFile;

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
        final boolean mediaCommitRequested;

        RecoveryInfo(String id, long timestamp, boolean hasWidget, boolean hasMedia, boolean canRestoreWidget, boolean mediaCommitRequested) {
            this.id = id;
            this.timestamp = timestamp;
            this.hasWidget = hasWidget;
            this.hasMedia = hasMedia;
            this.canRestoreWidget = canRestoreWidget;
            this.mediaCommitRequested = mediaCommitRequested;
        }
    }

    private ImportJournal() {}

    static String startImport(Context context, Prefs prefs, boolean hasWidget, boolean hasMedia) throws IOException {
        if (checkPendingRecovery(context) != null) {
            throw new IOException("Сначала завершите восстановление предыдущего импорта");
        }
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

        if (!hasWidget) new AtomicFile(new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE)).delete();

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

    static void markMediaCommitRequested(Context context) throws IOException {
        File file = new File(context.getFilesDir(), JOURNAL_FILE);
        try {
            JSONObject journal = new JSONObject(readFileToString(file));
            journal.put("mediaCommitRequested", true);
            writeAtomic(file, journal.toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException error) {
            throw new IOException("Не удалось записать этап импорта", error);
        }
    }

    static void markCommitted(Context context) {
        // Remove the journal first: a crash during cleanup must not offer a rollback of a committed import.
        new AtomicFile(new File(context.getFilesDir(), JOURNAL_FILE)).delete();
        new AtomicFile(new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE)).delete();
    }

    static boolean rollback(Context context, Prefs prefs) {
        boolean rolledBack = false;
        File preImportFile = new File(context.getFilesDir(), PRE_IMPORT_WIDGET_FILE);
        if (preImportFile.isFile() || new File(preImportFile.getPath() + ".bak").isFile()) {
            try {
                String json = readFileToString(preImportFile);
                SettingsBackup.Data data = SettingsBackup.decode(json);
                rolledBack = prefs.replacePortableSettings(data);
            } catch (Exception e) {
                AppLog.warn("Failed to rollback widget settings from pre-import backup", e);
            }
        }
        if (rolledBack || (!preImportFile.isFile()
                && !new File(preImportFile.getPath() + ".bak").isFile())) markCommitted(context);
        return rolledBack;
    }

    static RecoveryInfo checkPendingRecovery(Context context) {
        File journalFile = new File(context.getFilesDir(), JOURNAL_FILE);
        if (!journalFile.isFile() && !new File(journalFile.getPath() + ".bak").isFile()) return null;

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
                boolean canRestore = hasWidget && (preImportFile.isFile()
                        || new File(preImportFile.getPath() + ".bak").isFile());
                return new RecoveryInfo(id, timestamp, hasWidget, hasMedia, canRestore,
                        obj.optBoolean("mediaCommitRequested", false));
            }
        } catch (Exception e) {
            AppLog.warn("Failed to read import journal", e);
        }
        return null;
    }

    static void dismissPending(Context context) {
        markCommitted(context);
    }

    private static void writeAtomic(File target, byte[] bytes) throws IOException {
        AtomicFile file = new AtomicFile(target);
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(bytes);
            file.finishWrite(out);
        } catch (IOException error) {
            if (out != null) file.failWrite(out);
            throw error;
        }
    }

    private static String readFileToString(File file) throws IOException {
        try (InputStream in = new AtomicFile(file).openRead();
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
