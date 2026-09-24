package com.elysium.armory.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What a trait is: its name, how high it was written to go, what gear it can
 * appear on, and the effects it has (Silent Gear's SG-60).
 *
 * <p>The effects are kept as the JSON they were written as. Each effect type
 * is read by the code that acts on it ({@link com.elysium.armory.trait
 * ArmoryTraitEffects} and the handlers around it), so an effect type this
 * version does not know yet is carried rather than dropped, and a datapack can
 * add one the moment the code for it lands.
 */
public record ArmoryTraitDefinition(String id, String name, int maxLevel, String description,
                                    List<String> conditions, List<JsonObject> effects) {

    public ArmoryTraitDefinition {
        conditions = List.copyOf(conditions);
        effects = List.copyOf(effects);
    }

    public static ArmoryTraitDefinition parse(String id, JsonObject json) {
        List<String> conditions = new ArrayList<>();
        if (json.has("conditions")) {
            for (JsonElement el : json.getAsJsonArray("conditions")) {
                conditions.add(el.getAsString());
            }
        }
        List<JsonObject> effects = new ArrayList<>();
        if (json.has("effects")) {
            JsonArray array = json.getAsJsonArray("effects");
            for (JsonElement el : array) {
                effects.add(el.getAsJsonObject());
            }
        }
        return new ArmoryTraitDefinition(id, json.get("name").getAsString(),
                json.has("max_level") ? json.get("max_level").getAsInt() : 1,
                json.has("description") ? json.get("description").getAsString() : "",
                conditions, effects);
    }

    /** The effects of one type, in the order the file states them. */
    public List<JsonObject> effects(String type) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject effect : effects) {
            if (effect.has("type") && effect.get("type").getAsString().equals(type)) {
                out.add(effect);
            }
        }
        return out;
    }

    /** Whether this trait's behaviour is in code rather than in its file. */
    public boolean coded() {
        return !effects("code").isEmpty();
    }
}
