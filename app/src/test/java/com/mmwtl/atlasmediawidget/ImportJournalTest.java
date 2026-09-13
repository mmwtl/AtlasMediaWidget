package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class ImportJournalTest {

    private Context context;
    private Prefs prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        context.getSharedPreferences("atlas_media_widget", Context.MODE_PRIVATE).edit().clear().commit();
        new File(context.getFilesDir(), "import_journal.json").delete();
        new File(context.getFilesDir(), "pre_import_widget.json").delete();
        prefs = new Prefs(context);
    }

    @Test
    public void startImportRecordsJournalAndSnapshot() throws Exception {
        prefs.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 18);
        prefs.putBoolean(Prefs.KEY_AUTO_START, true);

        String opId = ImportJournal.startImport(context, prefs, true, true);
        assertNotNull(opId);
        assertFalse(opId.isEmpty());

        File journalFile = new File(context.getFilesDir(), "import_journal.json");
        File preImportFile = new File(context.getFilesDir(), "pre_import_widget.json");

        assertTrue(journalFile.isFile());
        assertTrue(preImportFile.isFile());

        ImportJournal.RecoveryInfo recovery = ImportJournal.checkPendingRecovery(context);
        assertNotNull(recovery);
        assertEquals(opId, recovery.id);
        assertTrue(recovery.hasWidget);
        assertTrue(recovery.hasMedia);
        assertTrue(recovery.canRestoreWidget);
    }

    @Test
    public void rollbackRestoresWidgetPreferencesAfterFailure() throws Exception {
        // 1. Initial configuration
        prefs.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 13);
        prefs.putBoolean(Prefs.KEY_AUTO_START, false);

        // 2. Start import journal
        ImportJournal.startImport(context, prefs, true, false);

        // 3. Simulate corrupt state / partial update
        prefs.putInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 19);
        prefs.putBoolean(Prefs.KEY_AUTO_START, true);
        assertEquals(19, prefs.getInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 15));
        assertTrue(prefs.getBoolean(Prefs.KEY_AUTO_START, false));

        // 4. Rollback
        boolean restored = ImportJournal.rollback(context, prefs);
        assertTrue(restored);

        // 5. Verify original settings restored
        assertEquals(13, prefs.getInt(Prefs.KEY_APP_UI_SCALE_TENTHS, 15));
        assertFalse(prefs.getBoolean(Prefs.KEY_AUTO_START, true));

        // Snapshot is cleaned up, recovery should now say cannot restore widget
        ImportJournal.RecoveryInfo recovery = ImportJournal.checkPendingRecovery(context);
        if (recovery != null) {
            assertFalse(recovery.canRestoreWidget);
        }
    }

    @Test
    public void markCommittedCleansUpJournalAndPendingState() throws Exception {
        ImportJournal.startImport(context, prefs, true, true);
        assertNotNull(ImportJournal.checkPendingRecovery(context));

        ImportJournal.markCommitted(context);

        assertNull(ImportJournal.checkPendingRecovery(context));
        assertFalse(new File(context.getFilesDir(), "import_journal.json").exists());
        assertFalse(new File(context.getFilesDir(), "pre_import_widget.json").exists());
    }
    @Test
    public void commitBoundarySurvivesJournalReload() throws Exception {
        String id = ImportJournal.startImport(context, prefs, true, true);
        ImportJournal.markMediaCommitRequested(context);
        ImportJournal.RecoveryInfo info = ImportJournal.checkPendingRecovery(context);
        assertEquals(id, info.id);
        assertTrue(info.mediaCommitRequested);
        assertThrowsPendingImport();
    }

    private void assertThrowsPendingImport() {
        org.junit.Assert.assertThrows(java.io.IOException.class,
                () -> ImportJournal.startImport(context, prefs, true, true));
    }

    @Test
    public void failedRollbackPreservesRecoveryFiles() throws Exception {
        ImportJournal.startImport(context, prefs, true, false);
        File snapshot = new File(context.getFilesDir(), "pre_import_widget.json");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(snapshot)) {
            out.write("invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        assertFalse(ImportJournal.rollback(context, prefs));
        assertNotNull(ImportJournal.checkPendingRecovery(context));
        assertTrue(snapshot.isFile());
    }

}
