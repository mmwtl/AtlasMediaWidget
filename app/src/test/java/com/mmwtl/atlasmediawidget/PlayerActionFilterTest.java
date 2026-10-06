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
public final class PlayerActionFilterTest {
    private static final List<MediaCustomAction> AIMP = List.of(
            action("repeat"), action("prevGroup"), action("toggleLiked"),
            action("nextGroup"), action("shuffle"));

    private static MediaCustomAction action(String id) {
        return new MediaCustomAction(id, id, 1, "com.aimp.player");
    }

    private static List<String> ids(List<MediaCustomAction> actions) {
        return actions.stream().map(action -> action.action).toList();
    }

    @Test public void playerWithoutHiddenActionsShowsItsFirstActions() {
        assertEquals(List.of("repeat", "prevGroup"),
                ids(PlayerActionFilter.choose(AIMP, 2, null)));
        assertTrue(PlayerActionFilter.choose(AIMP, 0, null).isEmpty());
    }

    @Test public void hiddenActionsAreSkippedBeforeTheLimitApplies() {
        assertEquals(List.of("toggleLiked", "shuffle"), ids(PlayerActionFilter.choose(AIMP, 2,
                List.of("repeat", "prevGroup", "nextGroup"))));
        assertEquals(List.of("prevGroup", "toggleLiked", "nextGroup", "shuffle"),
                ids(PlayerActionFilter.choose(AIMP, 5, List.of("repeat", "gone"))));
    }

    @Test public void storedHiddenActionsRoundTripAndSkipMalformedEntries() throws Exception {
        Map<String, List<String>> stored = Map.of("com.aimp.player", List.of("toggleLiked"));
        assertEquals(stored, PlayerActionFilter.decode(PlayerActionFilter.encode(stored)));
        assertEquals(Map.of(), PlayerActionFilter.decode("not json"));
        assertEquals(Map.of("ok", List.of("a")), PlayerActionFilter.decode(
                "{\"ok\":[\"a\",\"a\"],\"bad\":[1],\"notArray\":\"x\"}"));
        assertThrows(org.json.JSONException.class, () -> PlayerActionFilter.fromJson(
                new JSONObject("{\"bad\":[1]}"), true));
    }

    @Test public void prefsKeepHiddenActionsPerPlayer() {
        Prefs prefs = new Prefs(org.robolectric.RuntimeEnvironment.getApplication());
        prefs.putHiddenPlayerActions("com.aimp.player", List.of("repeat"));
        prefs.putHiddenPlayerActions("ru.yandex.music", List.of("actionDislike"));
        assertEquals(List.of("repeat"), prefs.hiddenPlayerActions().get("com.aimp.player"));
        prefs.putHiddenPlayerActions("com.aimp.player", List.of());
        assertEquals("nothing hidden removes the player entry",
                Map.of("ru.yandex.music", List.of("actionDislike")), prefs.hiddenPlayerActions());
    }
}
