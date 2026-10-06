package com.mmwtl.atlasmediawidget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public final class PlayerActionSelectionTest {
    private static final List<MediaCustomAction> AIMP = List.of(
            action("repeat"), action("prevGroup"), action("toggleLiked"),
            action("nextGroup"), action("shuffle"));

    private static MediaCustomAction action(String id) {
        return new MediaCustomAction(id, id, 1, "com.aimp.player");
    }

    private static List<String> ids(List<MediaCustomAction> actions) {
        return actions.stream().map(action -> action.action).toList();
    }

    @Test public void unconfiguredPlayerShowsItsFirstActions() {
        assertEquals(List.of("repeat", "prevGroup"),
                ids(PlayerActionSelection.choose(AIMP, 2, null)));
        assertTrue(PlayerActionSelection.choose(AIMP, 0, null).isEmpty());
    }

    @Test public void configuredPlayerShowsChosenActionsInPlayerOrder() {
        assertEquals(List.of("toggleLiked", "shuffle"),
                ids(PlayerActionSelection.choose(AIMP, 0, List.of("shuffle", "toggleLiked"))));
        assertTrue("an explicit empty choice hides all buttons",
                PlayerActionSelection.choose(AIMP, 5, List.of()).isEmpty());
        assertTrue("ids the player no longer publishes are skipped",
                PlayerActionSelection.choose(AIMP, 5, List.of("gone")).isEmpty());
    }

    @Test public void storedSelectionsRoundTripAndSkipMalformedEntries() throws Exception {
        Map<String, List<String>> stored = Map.of("com.aimp.player", List.of("toggleLiked"));
        assertEquals(stored, PlayerActionSelection.decode(PlayerActionSelection.encode(stored)));
        assertEquals(Map.of(), PlayerActionSelection.decode("not json"));
        assertEquals(Map.of("ok", List.of("a")), PlayerActionSelection.decode(
                "{\"ok\":[\"a\",\"a\"],\"bad\":[1],\"notArray\":\"x\"}"));
        assertThrows(org.json.JSONException.class, () -> PlayerActionSelection.fromJson(
                new JSONObject("{\"bad\":[1]}"), true));
    }

    @Test public void prefsKeepOneSelectionPerPlayer() {
        Prefs prefs = new Prefs(org.robolectric.RuntimeEnvironment.getApplication());
        prefs.putPlayerActionSelection("com.aimp.player", List.of("toggleLiked"));
        prefs.putPlayerActionSelection("ru.yandex.music", List.of("actionLike", "actionDislike"));
        assertEquals(List.of("toggleLiked"), prefs.playerActionSelections().get("com.aimp.player"));
        prefs.putPlayerActionSelection("com.aimp.player", null);
        assertEquals(Map.of("ru.yandex.music", List.of("actionLike", "actionDislike")),
                prefs.playerActionSelections());
    }
}
