package com.mmwtl.atlasmediawidget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LauncherMediaCommandReceiverTest {
    @Test public void acceptsOnlyContractCommands() {
        assertEquals("PLAY_PAUSE", LauncherMediaCommandReceiver.command("PLAY_PAUSE"));
        assertEquals("NEXT", LauncherMediaCommandReceiver.command("NEXT"));
        assertEquals("PREVIOUS", LauncherMediaCommandReceiver.command("PREVIOUS"));
        assertNull(LauncherMediaCommandReceiver.command(null));
        assertNull(LauncherMediaCommandReceiver.command(""));
        assertNull(LauncherMediaCommandReceiver.command("next"));
        assertNull(LauncherMediaCommandReceiver.command("PLAY"));
        assertNull(LauncherMediaCommandReceiver.command("SEEK_TO"));
        assertNull(LauncherMediaCommandReceiver.command("SET_SOURCE"));
    }

    @Test public void reportsTheCommandActuallySent() {
        assertEquals("PAUSE", LauncherMediaCommandReceiver.reported("TOGGLE", true));
        assertEquals("PLAY", LauncherMediaCommandReceiver.reported("TOGGLE", false));
        assertEquals("PLAY", LauncherMediaCommandReceiver.reported("PLAY", true));
        assertEquals("PAUSE", LauncherMediaCommandReceiver.reported("PAUSE", false));
        assertEquals("NEXT", LauncherMediaCommandReceiver.reported("NEXT", true));
        assertEquals("PREVIOUS", LauncherMediaCommandReceiver.reported("PREVIOUS", false));
    }
}
