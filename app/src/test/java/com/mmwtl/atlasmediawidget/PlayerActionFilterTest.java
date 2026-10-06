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

    private static PlayerActionFilter.Rules rules(List<String> published, List<String> hidden,
            List<String> order) {
        String aimp = "com.aimp.player";
        return new PlayerActionFilter.Rules(Map.of(aimp, hidden), Map.of(aimp, order),
                Map.of(aimp, published));
    }

    private static final List<String> AIMP_IDS = List.of(
            "repeat", "prevGroup", "toggleLiked", "nextGroup", "shuffle");

    @Test public void playerWithoutRulesShowsItsFirstActions() {
        assertEquals(List.of("repeat", "prevGroup"),
                ids(PlayerActionFilter.Rules.NONE.choose(AIMP, 2)));
        assertTrue(PlayerActionFilter.Rules.NONE.choose(AIMP, 0).isEmpty());
    }

    @Test public void hiddenActionsAreSkippedBeforeTheLimitApplies() {
        var rules = rules(AIMP_IDS, List.of("repeat", "prevGroup", "nextGroup"), AIMP_IDS);
        assertEquals(List.of("toggleLiked", "shuffle"), ids(rules.choose(AIMP, 2)));
    }

    @Test public void userOrderWinsAndNewActionsFollowIt() {
        var rules = rules(AIMP_IDS, List.of(),
                List.of("toggleLiked", "shuffle", "repeat", "prevGroup", "nextGroup"));
        assertEquals(List.of("toggleLiked", "shuffle", "repeat"), ids(rules.choose(AIMP, 3)));

        List<MediaCustomAction> grown = new java.util.ArrayList<>(AIMP);
        grown.add(1, action("newOne"));
        assertEquals("a new button keeps its place after the arranged ones",
                List.of("toggleLiked", "shuffle", "repeat", "prevGroup", "nextGroup", "newOne"),
                ids(rules.arrange(grown)));
    }

    @Test public void replacedIdInheritsPlaceAndHiddenFlag() {
        var yandex = List.of(new MediaCustomAction("actionDislike", "Dislike", 1, "ru.yandex.music"),
                new MediaCustomAction("actionLike", "Like", 2, "ru.yandex.music"));
        var rules = new PlayerActionFilter.Rules(Map.of(), Map.of("ru.yandex.music",
                List.of("actionLike", "actionDislike")), Map.of("ru.yandex.music",
                List.of("actionDislike", "actionLike")));
        assertEquals(List.of("actionLike", "actionDislike"), ids(rules.arrange(yandex)));

        var liked = List.of(new MediaCustomAction("actionDislike", "Dislike", 1, "ru.yandex.music"),
                new MediaCustomAction("actionUnlike", "Liked", 3, "ru.yandex.music"));
        assertEquals("the like button that changed its id stays first",
                List.of("actionUnlike", "actionDislike"), ids(rules.arrange(liked)));

        var hidesDislike = new PlayerActionFilter.Rules(Map.of("ru.yandex.music",
                List.of("actionDislike")), Map.of(), Map.of("ru.yandex.music",
                List.of("actionDislike", "actionLike")));
        var undisliked = List.of(new MediaCustomAction("actionUndislike", "Disliked", 4,
                        "ru.yandex.music"),
                new MediaCustomAction("actionLike", "Like", 2, "ru.yandex.music"));
        assertEquals("a hidden button stays hidden after changing its id",
                List.of("actionLike"), ids(hidesDislike.choose(undisliked, 5)));
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

    @Test public void prefsKeepRulesPerPlayerAndClearUnchangedOnes() {
        Prefs prefs = new Prefs(org.robolectric.RuntimeEnvironment.getApplication());
        prefs.putPlayerActionRules("com.aimp.player", AIMP_IDS, List.of("repeat"), AIMP_IDS);
        prefs.putPlayerActionRules("ru.yandex.music", List.of("a", "b"), List.of(),
                List.of("b", "a"));
        var rules = prefs.playerActionRules();
        assertEquals(List.of("repeat"), rules.hidden.get("com.aimp.player"));
        assertEquals(List.of("b", "a"), rules.order.get("ru.yandex.music"));
        assertTrue(rules.configured("ru.yandex.music"));

        prefs.putPlayerActionRules("com.aimp.player", AIMP_IDS, List.of(), AIMP_IDS);
        assertTrue("nothing hidden in player order clears the player",
                !prefs.playerActionRules().configured("com.aimp.player"));
        assertTrue(prefs.playerActionRules().configured("ru.yandex.music"));
    }
}
