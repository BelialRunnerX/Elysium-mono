package com.elysium.lib.elite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One thing that can be wrong with a creature (Apotheosis' AP-21).
 *
 * <pre>
 * {
 *   "weight": 10,
 *   "min_tier": 1,
 *   "attributes": { "minecraft:generic.movement_speed": 1.3 },
 *   "effects": [ { "effect": "minecraft:fire_resistance" } ]
 * }
 * </pre>
 *
 * <h2>Multipliers, not additions</h2>
 *
 * An augment states what it multiplies an attribute by, because a creature's
 * base values vary by four orders of magnitude between a chicken and a warden
 * and a flat bonus is either nothing to one or everything to the other. A
 * multiplier means the same thing on both.
 *
 * <h2>Effects last as long as the creature does</h2>
 *
 * An augment that granted a potion effect for a minute would be a creature
 * that stops being augmented while the player is still fighting it. They are
 * applied for effectively forever and stated without a duration at all.
 */
public record ElysiumMobAugment(String id, int weight, int minTier,
                                List<Attribute> attributes, List<String> effects) {

    /** One attribute an augment multiplies. */
    public record Attribute(String attribute, double factor) {
    }

    public static ElysiumMobAugment parse(String id, JsonObject json) {
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 10;
        int minTier = json.has("min_tier") ? json.get("min_tier").getAsInt() : 0;
        List<Attribute> attributes = new ArrayList<>();
        if (json.has("attributes")) {
            JsonObject stated = json.getAsJsonObject("attributes");
            for (String key : stated.keySet()) {
                attributes.add(new Attribute(key, stated.get(key).getAsDouble()));
            }
        }
        List<String> effects = new ArrayList<>();
        if (json.has("effects")) {
            for (JsonElement element : json.getAsJsonArray("effects")) {
                effects.add(element.getAsJsonObject().get("effect").getAsString());
            }
        }
        return new ElysiumMobAugment(id, weight, Math.max(0, minTier),
                List.copyOf(attributes), List.copyOf(effects));
    }

    /** Whether a world at this tier may produce this augment. */
    public boolean allowedAt(int worldTier) {
        return com.elysium.lib.tier.ElysiumWorldTier.allows(worldTier, minTier);
    }

    /** Whether it does anything at all. */
    public boolean isEmpty() {
        return attributes.isEmpty() && effects.isEmpty();
    }

    /** The translation key for the word that goes in the creature's name. */
    public String nameKey() {
        return "elysium.augment." + id.replace(':', '.');
    }
}
