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
 * Which player buttons the card shows and in what order. Per player the user may hide buttons and
 * reorder them; the card shows the first {@code limit} visible ones.
 *
 * <p>Some players replace a button's id when its state changes (like / remove like). The ids the
 * player published when the rules were saved are kept, so an unknown id that takes the position of
 * a vanished one is treated as the same button: it keeps that button's place and hidden flag.
 * Genuinely new buttons follow the arranged ones in the player's order and stay visible.
 */
final class PlayerActionFilter {
    static final int MAX_PLAYERS = 32;
    static final int MAX_ACTIONS_PER_PLAYER = 16;
    static final int MAX_ID_LENGTH = 128;

    private PlayerActionFilter() {}

    static final class Rules {
        static final Rules NONE = new Rules(Map.of(), Map.of(), Map.of());

        final Map<String, List<String>> hidden;
        final Map<String, List<String>> order;
        final Map<String, List<String>> published;

        Rules(Map<String, List<String>> hidden, Map<String, List<String>> order,
                Map<String, List<String>> published) {
            this.hidden = hidden == null ? Map.of() : hidden;
            this.order = order == null ? Map.of() : order;
            this.published = published == null ? Map.of() : published;
        }

        boolean configured(String packageName) {
            return hidden.containsKey(packageName) || order.containsKey(packageName);
        }

        /** The first {@code limit} visible actions in the user's order. */
        List<MediaCustomAction> choose(List<MediaCustomAction> actions, int limit) {
            List<MediaCustomAction> hiddenActions = hiddenOf(actions);
            List<MediaCustomAction> result = new ArrayList<>();
            for (MediaCustomAction action : arrange(actions)) {
                if (result.size() >= limit) break;
                if (!hiddenActions.contains(action)) result.add(action);
            }
            return result;
        }

        /** All actions in the user's order; the sort is stable, so the rest keep player order. */
        List<MediaCustomAction> arrange(List<MediaCustomAction> actions) {
            List<String> keys = savedIds(actions);
            List<String> saved = order.getOrDefault(owner(actions), List.of());
            List<Integer> indices = new ArrayList<>();
            for (int index = 0; index < actions.size(); index++) indices.add(index);
            indices.sort(java.util.Comparator.comparingInt(index -> {
                int rank = saved.indexOf(keys.get(index));
                return rank < 0 ? Integer.MAX_VALUE : rank;
            }));
            List<MediaCustomAction> result = new ArrayList<>();
            for (int index : indices) result.add(actions.get(index));
            return result;
        }

        List<MediaCustomAction> hiddenOf(List<MediaCustomAction> actions) {
            List<String> keys = savedIds(actions);
            List<String> saved = hidden.getOrDefault(owner(actions), List.of());
            List<MediaCustomAction> result = new ArrayList<>();
            for (int index = 0; index < actions.size(); index++) {
                if (saved.contains(keys.get(index))) result.add(actions.get(index));
            }
            return result;
        }

        /** The saved id each current action stands for, matching replaced ids by position. */
        List<String> savedIds(List<MediaCustomAction> actions) {
            List<String> before = published.getOrDefault(owner(actions), List.of());
            List<String> current = new ArrayList<>();
            for (MediaCustomAction action : actions) current.add(action.action);
            List<String> result = new ArrayList<>();
            for (int index = 0; index < current.size(); index++) {
                String id = current.get(index);
                boolean replaced = !before.contains(id) && index < before.size()
                        && !current.contains(before.get(index));
                result.add(replaced ? before.get(index) : id);
            }
            return result;
        }

        private static String owner(List<MediaCustomAction> actions) {
            return actions.isEmpty() ? "" : actions.get(0).ownerPackage;
        }
    }

    /** Parses stored per-player id lists; malformed entries are dropped rather than failing the card. */
    static Map<String, List<String>> decode(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return fromJson(new JSONObject(json), false);
        } catch (JSONException error) {
            return Map.of();
        }
    }

    static String encode(Map<String, List<String>> hidden) {
        return toJson(hidden).toString();
    }

    static JSONObject toJson(Map<String, List<String>> hidden) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> entry : hidden.entrySet()) {
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
                if (strict) throw new JSONException("invalid hidden player actions");
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
