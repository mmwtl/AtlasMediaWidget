package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.os.Message;

import java.io.File;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class MediaBridgeClientTest {
    @Test
    public void settingsRequestWhileStoppedFailsOnMainAndIsRemoved() {
        MediaBridgeClient client = newClient();
        AtomicInteger errors = new AtomicInteger();
        AtomicReference<Looper> callbackLooper = new AtomicReference<>();

        client.getSettings(new MediaBridgeClient.SettingsCallback() {
            @Override public void onSettings(MediaSettingsSnapshot snapshot) {
            }

            @Override public void onError(int status, String message) {
                errors.incrementAndGet();
                callbackLooper.set(Looper.myLooper());
                assertEquals(MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE, status);
            }
        });

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(1, errors.get());
        assertEquals(Looper.getMainLooper(), callbackLooper.get());
        assertTrue(pendingCallbacks(client).isEmpty());
    }

    @Test
    public void pendingCallbackTimesOutExactlyOnce() throws Exception {
        MediaBridgeClient client = newClient();
        AtomicInteger errors = new AtomicInteger();
        MediaBridgeClient.SettingsCallback callback = new MediaBridgeClient.SettingsCallback() {
            @Override public void onSettings(MediaSettingsSnapshot snapshot) {
            }

            @Override public void onError(int status, String message) {
                errors.incrementAndGet();
                assertEquals(MediaBridgeContract.STATUS_FAILED, status);
                assertEquals(Looper.getMainLooper(), Looper.myLooper());
            }
        };

        Method addPending = MediaBridgeClient.class.getDeclaredMethod(
                "addPendingCallback", String.class, Object.class);
        addPending.setAccessible(true);
        addPending.invoke(client, "timeout-test", callback);

        ShadowLooper.getShadowMainLooper().idleFor(Duration.ofSeconds(15));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        ShadowLooper.getShadowMainLooper().idleFor(Duration.ofSeconds(15));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(1, errors.get());
        assertTrue(pendingCallbacks(client).isEmpty());
    }

    @Test
    public void stopFailsPendingCallbackAndClosesTransferredDescriptors() throws Exception {
        MediaBridgeClient client = newClient();
        AtomicInteger errors = new AtomicInteger();
        MediaBridgeClient.BackupCallback callback = new MediaBridgeClient.BackupCallback() {
            @Override public void onBackupExported() {
            }

            @Override public void onError(int status, String message) {
                errors.incrementAndGet();
                assertEquals(MediaBridgeContract.STATUS_BACKEND_UNAVAILABLE, status);
            }
        };
        Method addPending = MediaBridgeClient.class.getDeclaredMethod(
                "addPendingCallback", String.class, Object.class);
        addPending.setAccessible(true);
        addPending.invoke(client, "stop-test", callback);

        File file = File.createTempFile("media-bridge-client", ".zip");
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_READ_ONLY);
        client.exportMediaBackup(pfd, null);
        assertFalse(pfd.getFileDescriptor().valid());

        client.stop();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(1, errors.get());
        assertTrue(pendingCallbacks(client).isEmpty());
        assertTrue(file.delete());
    }

    @Test
    public void radioCatalogExportResponseCompletesCallbackWithStationCount() throws Exception {
        MediaBridgeClient client = newClient();
        AtomicInteger count = new AtomicInteger(-1);
        MediaBridgeClient.RadioCatalogExportCallback callback =
                new MediaBridgeClient.RadioCatalogExportCallback() {
                    @Override public void onCatalogExported(int stationCount) {
                        count.set(stationCount);
                    }

                    @Override public void onError(int status, String message) {
                        count.set(-2);
                    }
                };
        Method addPending = MediaBridgeClient.class.getDeclaredMethod(
                "addPendingCallback", String.class, Object.class);
        addPending.setAccessible(true);
        addPending.invoke(client, "radio-export-test", callback);

        Message response = Message.obtain(null, MediaBridgeContract.RADIO_CATALOG_EXPORTED);
        Bundle data = new Bundle();
        data.putInt(MediaBridgeContract.K_VERSION, MediaBridgeContract.VERSION);
        data.putString(MediaBridgeContract.K_REQUEST_ID, "radio-export-test");
        data.putInt(MediaBridgeContract.K_STATUS, MediaBridgeContract.STATUS_OK);
        data.putInt(MediaBridgeContract.K_CATALOG_STATION_COUNT, 7);
        response.setData(data);
        Method incoming = MediaBridgeClient.class.getDeclaredMethod("handleIncoming", Message.class);
        incoming.setAccessible(true);
        incoming.invoke(client, response);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals(7, count.get());
    }

    private static MediaBridgeClient newClient() {
        Context context = RuntimeEnvironment.getApplication();
        return new MediaBridgeClient(context, new MediaBridgeClient.Listener() {
            @Override public void onBridgeState(MediaBridgeClient.State state, String detail) {
            }

            @Override public void onSnapshot(MediaSnapshot snapshot) {
            }

            @Override public void onCommandResult(String requestId, int status, String message,
                    long generation) {
            }

            @Override public void onRadioStations(RadioStationLists lists) {
            }

            @Override public void onRadioStationsError(int status, String message) {
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> pendingCallbacks(MediaBridgeClient client) {
        try {
            java.lang.reflect.Field field = MediaBridgeClient.class.getDeclaredField(
                    "pendingCallbacks");
            field.setAccessible(true);
            return (java.util.Map<String, Object>) field.get(client);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
