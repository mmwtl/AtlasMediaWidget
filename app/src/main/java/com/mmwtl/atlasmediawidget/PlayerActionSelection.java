package com.mmwtl.atlasmediawidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which player buttons the card shows. A player the user configured shows exactly the chosen
 * action ids, in the player's own order; any other player shows its first {@code limit} actions.
 */
final class PlayerActionSelection {
    static final int MAX_PLAYERS = 32;
    static final int MAX_ACTIONS_PER_PLAYER = 8;
    static final int MAX_ID_LENGTH = 128;

    private PlayerActionSelection() {}

    static List<MediaCustomAction> choose(List<MediaCustomAction> actions, int limit,
            List<String> selected) {
        List<MediaCustomAction> result = new ArrayList<>();
        for (MediaCustomAction action : actions) {
            if (selected != null ? selected.contains(action.action) : result.size() < limit) {
                result.add(action);
            }
        }
        return result;
    }

    /** Parses stored selections; malformed entries are dropped rather than failing the card. */
    static Map<String, List<String>> decode(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return fromJson(new JSONObject(json), false);
        } catch (JSONException error) {
            return Map.of();
        }
    }

    static String encode(Map<String, List<String>> selections) {
        return toJson(selections).toString();
    }

    static JSONObject toJson(Map<String, List<String>> selections) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> entry : selections.entrySet()) {
                root.put(entry.getKey(), new JSONArray(entry.getValue()));
            }
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
        return root;
    }

    /** With {@code strict}, an invalid entry throws instead of being skipped (backup import). */
    static Map<String, List<String>> fromJson(JSONObject root, boolean strict) throws JSONException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) {
            String packageName = keys.next();
            JSONArray ids = root.optJSONArray(packageName);
            List<String> values = ids == null ? null : ids(ids);
            boolean valid = !packageName.isBlank() && packageName.length() <= MAX_ID_LENGTH
                    && values != null && values.size() <= MAX_ACTIONS_PER_PLAYER
                    && result.size() < MAX_PLAYERS;
            if (!valid) {
                if (strict) throw new JSONException("invalid player action selection");
                continue;
            }
            result.put(packageName, Collections.unmodifiableList(values));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<String> ids(JSONArray array) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            Object value = array.opt(index);
            if (!(value instanceof String id) || id.isBlank() || id.length() > MAX_ID_LENGTH) {
                return null;
            }
            if (!values.contains(id)) values.add(id);
        }
        return values;
    }
}
