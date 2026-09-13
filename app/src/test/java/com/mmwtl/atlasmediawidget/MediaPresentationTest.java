package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MediaPresentationTest {
    @Test public void activeRadioIsContentEvenWithoutTrackMetadata() {
        assertTrue(MediaPresentation.hasContent(MediaSource.Id.RADIO,
                true, true, "", "", "", -1L));
        assertEquals("Радио", MediaPresentation.title(MediaSource.Id.RADIO, ""));
        assertEquals("Штатный радиоприёмник",
                MediaPresentation.subtitle(MediaSource.Id.RADIO, "", ""));
    }

    @Test public void disconnectedRadioDoesNotHideBackendFailureBehindFallback() {
        assertFalse(MediaPresentation.hasContent(MediaSource.Id.RADIO,
                false, false, "", "", "", -1L));
    }

    @Test public void ordinarySourceStillRequiresRealMetadata() {
        assertFalse(MediaPresentation.hasContent(MediaSource.Id.BT,
                true, true, "", "", "", -1L));
        assertTrue(MediaPresentation.hasContent(MediaSource.Id.BT,
                true, true, "Track", "", "", -1L));
    }

    @Test public void onlineSourceDoesNotRequireOneOsBackendConnection() {
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.ONLINE, false, false));
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.ONLINE, true, false));
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.ONLINE, false, true));
    }

    @Test public void nativeSourcesRequireOneOsBackendConnection() {
        assertFalse(MediaPresentation.isBackendAvailable(MediaSource.Id.RADIO, false, false));
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.RADIO, true, false));
        assertFalse(MediaPresentation.isBackendAvailable(MediaSource.Id.BT, false, false));
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.BT, true, false));
        assertFalse(MediaPresentation.isBackendAvailable(MediaSource.Id.USB, false, false));
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.USB, true, false));
    }

    @Test public void activeMediaOnUnknownOrOtherSourceTreatsBackendAvailable() {
        assertFalse(MediaPresentation.isBackendAvailable(MediaSource.Id.UNKNOWN, false, false));
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.UNKNOWN, false, true));
        assertFalse(MediaPresentation.isBackendAvailable(MediaSource.Id.OTHER, false, false));
        assertTrue(MediaPresentation.isBackendAvailable(MediaSource.Id.OTHER, false, true));
    }
}
