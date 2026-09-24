package com.elysium.armory.material;

import com.elysium.armory.property.ArmoryModifier;
import com.elysium.armory.property.ArmoryOperation;
import com.elysium.armory.property.ArmoryProperty;
import com.elysium.armory.property.ArmoryPropertyKey;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One part definition (Silent Gear's SG-21): the shape, as opposed to the
 * material it is made of.
 *
 * <p>A sword blade is +3 attack damage and +1.6 attack speed whether it is
 * stone or netherite; a hammer head is four materials wide, hits harder and
 * mines slower. Those offsets are the part, and they are stated here so that
 * adding a shape is a data file rather than a class.
 *
 * <p>An upgrade part is not built from materials at all: it is an item that
 * can be added to gear of the types it lists.
 */
public record ArmoryPart(String id, Kind kind, String gearType, String partType,
                         Set<String> gearTypeBlacklist, Crafting crafting,
                         Map<ArmoryPropertyKey, List<ArmoryModifier>> modifiers, List<ArmoryTrait> traits,
                         Upgrade upgrade) {

    public enum Kind { CORE, UPGRADE }

    /** How many materials the part takes, what they must be, and what else goes in. */
    public record Crafting(int materials, int count, Set<ArmoryCategory> categories, List<String> extra) {

        public static final Crafting NONE = new Crafting(0, 1, Set.of(), List.of());
    }

    /** What an upgrade part may be applied to. */
    public record Upgrade(List<String> gearTypes, boolean matchChildren) {

        public static final Upgrade NONE = new Upgrade(List.of(), false);
    }

    public boolean isUpgrade() {
        return kind == Kind.UPGRADE;
    }

    /** Whether this part may be put on that gear type (the blacklist wins). */
    public boolean fits(String type) {
        return !gearTypeBlacklist.contains(type);
    }

    public static ArmoryPart parse(String id, JsonObject json) {
        Kind kind = Kind.valueOf(json.get("type").getAsString().toUpperCase(Locale.ROOT));
        String gearType = json.get("gear_type").getAsString();
        String partType = json.get("part_type").getAsString();
        if (ArmoryPartType.named(partType) == null) {
            throw new ArmoryMaterial.Malformed(id + ": no such part type " + partType);
        }
        Set<String> blacklist = new LinkedHashSet<>();
        if (json.has("gear_type_blacklist")) {
            for (JsonElement el : json.getAsJsonArray("gear_type_blacklist")) {
                blacklist.add(el.getAsString());
            }
        }
        Crafting crafting = Crafting.NONE;
        if (json.has("crafting")) {
            JsonObject c = json.getAsJsonObject("crafting");
            Set<ArmoryCategory> categories = new LinkedHashSet<>();
            if (c.has("categories")) {
                for (JsonElement el : c.getAsJsonArray("categories")) {
                    ArmoryCategory category = ArmoryCategory.byId(el.getAsString());
                    if (category == null) {
                        throw new ArmoryMaterial.Malformed(id + ": no such category " + el.getAsString());
                    }
                    categories.add(category);
                }
            }
            List<String> extra = new ArrayList<>();
            if (c.has("extra")) {
                for (JsonElement el : c.getAsJsonArray("extra")) {
                    extra.add(el.getAsString());
                }
            }
            crafting = new Crafting(c.has("materials") ? c.get("materials").getAsInt() : 0,
                    c.has("count") ? c.get("count").getAsInt() : 1, categories, extra);
        }
        Map<ArmoryPropertyKey, List<ArmoryModifier>> modifiers = new LinkedHashMap<>();
        List<ArmoryTrait> traits = new ArrayList<>();
        if (json.has("properties")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("properties").entrySet()) {
                if (e.getKey().equals("traits")) {
                    for (JsonElement t : e.getValue().getAsJsonArray()) {
                        JsonObject o = t.getAsJsonObject();
                        List<String> conditions = new ArrayList<>();
                        if (o.has("conditions")) {
                            for (JsonElement c : o.getAsJsonArray("conditions")) {
                                conditions.add(c.getAsString());
                            }
                        }
                        traits.add(new ArmoryTrait(o.get("trait").getAsString(), o.get("level").getAsInt(), conditions));
                    }
                    continue;
                }
                ArmoryPropertyKey key = ArmoryPropertyKey.parse(e.getKey());
                if (key == null || ArmoryProperty.byId(key.property()) == null) {
                    throw new ArmoryMaterial.Malformed(id + ": no such property " + e.getKey());
                }
                modifiers.put(key, modifiers(id, e.getKey(), e.getValue()));
            }
        }
        Upgrade upgrade = Upgrade.NONE;
        if (json.has("upgrade")) {
            JsonObject u = json.getAsJsonObject("upgrade");
            List<String> types = new ArrayList<>();
            for (JsonElement el : u.getAsJsonArray("gear_types")) {
                types.add(el.getAsString());
            }
            upgrade = new Upgrade(types, u.has("match_children") && u.get("match_children").getAsBoolean());
        }
        return new ArmoryPart(id, kind, gearType, partType, blacklist, crafting,
                Map.copyOf(modifiers), List.copyOf(traits), upgrade);
    }

    private static List<ArmoryModifier> modifiers(String id, String key, JsonElement value) {
        List<ArmoryModifier> out = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement el : value.getAsJsonArray()) {
                out.add(modifier(id, key, el));
            }
        } else {
            out.add(modifier(id, key, value));
        }
        return out;
    }

    private static ArmoryModifier modifier(String id, String key, JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return ArmoryModifier.average(value.getAsDouble());
        }
        JsonObject o = value.getAsJsonObject();
        String op = o.get("op").getAsString().toUpperCase(Locale.ROOT);
        try {
            return new ArmoryModifier(ArmoryOperation.valueOf(op), o.get("value").getAsDouble());
        } catch (IllegalArgumentException e) {
            throw new ArmoryMaterial.Malformed(id + "/" + key + ": no such operation " + op);
        }
    }
}
