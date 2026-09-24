package com.elysium.lib.affix;

import com.elysium.lib.effect.ElysiumEffectKinds;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One affix, as its file states it.
 *
 * <pre>
 * {
 *   "type": "suffix",
 *   "on": ["melee_weapon"],
 *   "min_tier": 2,
 *   "weight": 10,
 *   "max_level": 4,
 *   "effects": [ { "type": "extra_damage", ... } ]
 * }
 * </pre>
 *
 * The effects are stated in the shared vocabulary — the same words a trait
 * uses, read by the same handlers — so an affix does not need a line of code
 * of its own to do anything a trait can already do.
 *
 * <p>Only live kinds are allowed. A build kind such as {@code property} is
 * read once while a piece of gear is computed from its parts, and by the time
 * an affix is rolled onto an item the parts have long since been added up: an
 * affix stating one would be a promise nothing keeps.
 */
public record ElysiumAffixDefinition(String id, String type, List<String> on, int minTier,
                                     int weight, int maxLevel, List<JsonObject> effects) {

    public static ElysiumAffixDefinition parse(String id, JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : ElysiumAffixRules.SUFFIX;
        List<String> on = new ArrayList<>();
        if (json.has("on")) {
            for (JsonElement element : json.getAsJsonArray("on")) {
                on.add(element.getAsString());
            }
        }
        int minTier = json.has("min_tier") ? json.get("min_tier").getAsInt() : 0;
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 10;
        int maxLevel = json.has("max_level") ? json.get("max_level").getAsInt() : 1;
        List<JsonObject> effects = new ArrayList<>();
        JsonArray stated = json.has("effects") ? json.getAsJsonArray("effects") : new JsonArray();
        for (JsonElement element : stated) {
            effects.add(element.getAsJsonObject());
        }
        return new ElysiumAffixDefinition(id, type, List.copyOf(on), Math.max(0, minTier),
                weight, Math.max(1, maxLevel), List.copyOf(effects));
    }

    /** Every effect of one kind this affix states. */
    public List<JsonObject> effects(String kind) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject effect : effects) {
            if (effect.has("type") && effect.get("type").getAsString().equals(kind)) {
                out.add(effect);
            }
        }
        return out;
    }

    /** The kinds this affix states, whether or not anything reads them. */
    public List<String> kinds() {
        List<String> out = new ArrayList<>();
        for (JsonObject effect : effects) {
            if (effect.has("type")) {
                String kind = effect.get("type").getAsString();
                if (!out.contains(kind)) {
                    out.add(kind);
                }
            }
        }
        return out;
    }

    /** Whether every kind this affix states is one that can be read off a finished item. */
    public boolean live() {
        for (String kind : kinds()) {
            if (!ElysiumEffectKinds.live(kind)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a world at this tier may produce this affix.
     *
     * A gate rather than a weight: an affix a player has not earned should not
     * be rare, it should be absent, so that reaching the tier is felt as
     * something new appearing rather than as a number moving.
     */
    public boolean allowedAt(int worldTier) {
        return com.elysium.lib.tier.ElysiumWorldTier.allows(worldTier, minTier);
    }

    /** The translation key for this affix's half of an item's name. */
    public String nameKey() {
        return "elysium.affix." + id.replace(':', '.');
    }
}
