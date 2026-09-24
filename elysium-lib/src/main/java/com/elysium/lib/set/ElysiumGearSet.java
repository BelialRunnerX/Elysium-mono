package com.elysium.lib.set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A gear set, as its file states it (Placebo's PL-1, Apotheosis' AP-22).
 *
 * <pre>
 * {
 *   "items": ["elysium:elysium_helmet", "elysium:plasma_chestplate"],
 *   "bonuses": [
 *     { "pieces": 2, "effects": [ { "type": "attribute", ... } ] },
 *     { "pieces": 4, "effects": [ ... ] }
 *   ]
 * }
 * </pre>
 *
 * <h2>Effects, not a special case</h2>
 *
 * A set bonus is a bundle of item effects that happens to depend on how many
 * pieces are worn, so it is stated in the shared vocabulary and read by the
 * same handlers as a trait or an affix. There is no set-bonus code beyond
 * counting what is worn.
 *
 * <p>The counting is the only thing that is new, and it is deliberately
 * cumulative: wearing four pieces of a set gives the two-piece bonus <em>and</em>
 * the four-piece one, because the alternative is a fourth piece that takes
 * something away.
 */
public record ElysiumGearSet(String id, List<String> items, Map<Integer, List<JsonObject>> bonuses) {

    public static ElysiumGearSet parse(String id, JsonObject json) {
        List<String> items = new ArrayList<>();
        if (json.has("items")) {
            for (JsonElement element : json.getAsJsonArray("items")) {
                items.add(element.getAsString());
            }
        }
        Map<Integer, List<JsonObject>> bonuses = new LinkedHashMap<>();
        JsonArray stated = json.has("bonuses") ? json.getAsJsonArray("bonuses") : new JsonArray();
        for (JsonElement element : stated) {
            JsonObject bonus = element.getAsJsonObject();
            int pieces = bonus.has("pieces") ? bonus.get("pieces").getAsInt() : 2;
            List<JsonObject> effects = new ArrayList<>();
            if (bonus.has("effects")) {
                for (JsonElement effect : bonus.getAsJsonArray("effects")) {
                    effects.add(effect.getAsJsonObject());
                }
            }
            bonuses.put(Math.max(1, pieces), List.copyOf(effects));
        }
        return new ElysiumGearSet(id, List.copyOf(items), Map.copyOf(bonuses));
    }

    /** Every effect of one kind this set grants for this many pieces worn. */
    public List<JsonObject> effects(int worn, String kind) {
        List<JsonObject> out = new ArrayList<>();
        for (Map.Entry<Integer, List<JsonObject>> bonus : bonuses.entrySet()) {
            if (worn < bonus.getKey()) {
                continue;                   // not enough of the set on
            }
            for (JsonObject effect : bonus.getValue()) {
                if (effect.has("type") && effect.get("type").getAsString().equals(kind)) {
                    out.add(effect);
                }
            }
        }
        return out;
    }

    /** The kinds this set states, whether or not anything reads them. */
    public List<String> kinds() {
        List<String> out = new ArrayList<>();
        for (List<JsonObject> effects : bonuses.values()) {
            for (JsonObject effect : effects) {
                if (effect.has("type")) {
                    String kind = effect.get("type").getAsString();
                    if (!out.contains(kind)) {
                        out.add(kind);
                    }
                }
            }
        }
        return out;
    }

    /** The translation key for the set's name. */
    public String nameKey() {
        return "elysium.gear_set." + id.replace(':', '.');
    }

    /** The largest bonus this set has, for a tooltip that wants to say how far it goes. */
    public int largestBonus() {
        int best = 0;
        for (int pieces : bonuses.keySet()) {
            best = Math.max(best, pieces);
        }
        return best;
    }
}
