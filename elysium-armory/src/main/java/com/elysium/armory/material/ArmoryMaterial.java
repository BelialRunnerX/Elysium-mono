package com.elysium.armory.material;

import com.elysium.armory.property.ArmoryModifier;
import com.elysium.armory.property.ArmoryOperation;
import com.elysium.armory.property.ArmoryProperty;
import com.elysium.armory.property.ArmoryPropertyKey;
import com.elysium.armory.property.ArmoryTier;
import com.google.gson.JsonArray;
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
 * One material, as its file states it (Silent Gear's SG-10).
 *
 * <p>A file may name a {@code parent}, and then states only what differs — the
 * sixteen wools differ from wool in their ingredient, colour and name and in
 * nothing else. {@link #inherit} is that rule, applied once at load so nothing
 * downstream has to walk a chain.
 *
 * <h2>What a material holds</h2>
 *
 * <ul>
 *   <li>the ingredient that is this material (an item id, or a {@code #tag});</li>
 *   <li>its categories, which decide what will accept it;</li>
 *   <li>per part type, the property modifiers it contributes, its harvest tier
 *       and the traits it grants;</li>
 *   <li>part substitutes: an item that counts as a finished part of this
 *       material, so a stick is a wooden rod.</li>
 * </ul>
 *
 * <p>Parsing is by hand rather than by codec so that a broken file names
 * itself and the field, which is the difference between a five-second fix and
 * an afternoon.
 */
public record ArmoryMaterial(String id, Kind kind, String parent, String ingredient,
                             Set<ArmoryCategory> categories, boolean canSalvage,
                             Map<String, String> partSubstitutes, Set<String> gearTypeBlacklist,
                             int colour, String name,
                             Map<String, PartProperties> properties) {

    public enum Kind { SIMPLE, COMPOUND, CUSTOM_COMPOUND, PROCESSED }

    /** What a material gives one part type. */
    public record PartProperties(Map<ArmoryPropertyKey, List<ArmoryModifier>> modifiers,
                                 ArmoryTier tier, List<ArmoryTrait> traits) {

        public static final PartProperties EMPTY = new PartProperties(Map.of(), null, List.of());

        public PartProperties {
            modifiers = Map.copyOf(modifiers);
            traits = List.copyOf(traits);
        }
    }

    /** Whether this material can be put into a part of this type at all. */
    public boolean supports(String partType) {
        return properties.containsKey(partType);
    }

    public PartProperties forPart(String partType) {
        return properties.getOrDefault(partType, PartProperties.EMPTY);
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    /** Thrown with the file's name and the field that is wrong. */
    public static final class Malformed extends RuntimeException {

        public Malformed(String message) {
            super(message);
        }
    }

    public static ArmoryMaterial parse(String id, JsonObject json) {
        Kind kind = kind(id, json);
        String parent = json.has("parent") ? json.get("parent").getAsString() : null;
        JsonObject crafting = json.has("crafting") ? json.getAsJsonObject("crafting") : new JsonObject();
        String ingredient = crafting.has("ingredient") ? crafting.get("ingredient").getAsString() : null;
        Set<ArmoryCategory> categories = new LinkedHashSet<>();
        if (crafting.has("categories")) {
            for (JsonElement el : crafting.getAsJsonArray("categories")) {
                ArmoryCategory category = ArmoryCategory.byId(el.getAsString());
                if (category == null) {
                    throw new Malformed(id + ": no such category " + el.getAsString());
                }
                categories.add(category);
            }
        }
        boolean salvage = !crafting.has("can_salvage") || crafting.get("can_salvage").getAsBoolean();
        Map<String, String> substitutes = new LinkedHashMap<>();
        if (crafting.has("part_substitutes")) {
            for (Map.Entry<String, JsonElement> e : crafting.getAsJsonObject("part_substitutes").entrySet()) {
                if (ArmoryPartType.named(e.getKey()) == null) {
                    throw new Malformed(id + ": substitute for no such part type " + e.getKey());
                }
                substitutes.put(e.getKey(), e.getValue().getAsString());
            }
        }
        Set<String> blacklist = new LinkedHashSet<>();
        if (crafting.has("gear_type_blacklist")) {
            for (JsonElement el : crafting.getAsJsonArray("gear_type_blacklist")) {
                blacklist.add(el.getAsString());
            }
        }
        JsonObject display = json.has("display") ? json.getAsJsonObject("display") : new JsonObject();
        int colour = display.has("color") ? (int) Long.parseLong(display.get("color").getAsString(), 16) : 0xFFFFFF;
        String name = display.has("name") ? display.get("name").getAsString() : null;
        Map<String, PartProperties> properties = new LinkedHashMap<>();
        if (json.has("properties")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("properties").entrySet()) {
                if (ArmoryPartType.named(e.getKey()) == null) {
                    throw new Malformed(id + ": properties for no such part type " + e.getKey());
                }
                properties.put(e.getKey(), part(id, e.getKey(), e.getValue().getAsJsonObject()));
            }
        }
        return new ArmoryMaterial(id, kind, parent, ingredient, categories, salvage, substitutes, blacklist,
                colour, name, properties);
    }

    private static Kind kind(String id, JsonObject json) {
        String text = json.has("type") ? json.get("type").getAsString() : "simple";
        try {
            return Kind.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new Malformed(id + ": no such material type " + text);
        }
    }

    private static PartProperties part(String id, String partType, JsonObject json) {
        Map<ArmoryPropertyKey, List<ArmoryModifier>> modifiers = new LinkedHashMap<>();
        ArmoryTier tier = null;
        List<ArmoryTrait> traits = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            String key = e.getKey();
            if (key.equals("harvest_tier")) {
                tier = ArmoryTier.named(e.getValue().getAsString());
                if (tier == null) {
                    throw new Malformed(id + "/" + partType + ": no such harvest tier " + e.getValue());
                }
                continue;
            }
            if (key.equals("traits")) {
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
            ArmoryPropertyKey parsed = ArmoryPropertyKey.parse(key);
            if (parsed == null || ArmoryProperty.byId(parsed.property()) == null) {
                throw new Malformed(id + "/" + partType + ": no such property " + key);
            }
            modifiers.put(parsed, modifiers(id, partType, key, e.getValue()));
        }
        return new PartProperties(modifiers, tier, traits);
    }

    private static List<ArmoryModifier> modifiers(String id, String partType, String key, JsonElement value) {
        List<ArmoryModifier> out = new ArrayList<>();
        if (value.isJsonArray()) {
            for (JsonElement el : (JsonArray) value) {
                out.add(modifier(id, partType, key, el));
            }
        } else {
            out.add(modifier(id, partType, key, value));
        }
        return out;
    }

    private static ArmoryModifier modifier(String id, String partType, String key, JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return ArmoryModifier.average(value.getAsDouble());       // a plain number is an average
        }
        JsonObject o = value.getAsJsonObject();
        String op = o.get("op").getAsString().toUpperCase(Locale.ROOT);
        try {
            return new ArmoryModifier(ArmoryOperation.valueOf(op), o.get("value").getAsDouble());
        } catch (IllegalArgumentException e) {
            throw new Malformed(id + "/" + partType + "/" + key + ": no such operation " + op);
        }
    }

    // ------------------------------------------------------------------
    // Inheritance
    // ------------------------------------------------------------------

    /**
     * This material with everything it did not state taken from {@code from}.
     *
     * <p>Inheritance is per property, not per part: Crimson Wood states one
     * trait on its main part and keeps every one of Wood's numbers. A child
     * that states a property replaces that property alone; a child that states
     * any trait replaces the whole trait list for that part, because a trait
     * list is a statement about the material as a whole rather than a bag of
     * numbers.
     */
    public ArmoryMaterial inherit(ArmoryMaterial from) {
        Map<String, PartProperties> merged = new LinkedHashMap<>(from.properties());
        for (Map.Entry<String, PartProperties> entry : properties.entrySet()) {
            PartProperties parent = merged.get(entry.getKey());
            PartProperties child = entry.getValue();
            if (parent == null) {
                merged.put(entry.getKey(), child);
                continue;
            }
            Map<ArmoryPropertyKey, List<ArmoryModifier>> modifiers = new LinkedHashMap<>(parent.modifiers());
            modifiers.putAll(child.modifiers());
            merged.put(entry.getKey(), new PartProperties(modifiers,
                    child.tier() != null ? child.tier() : parent.tier(),
                    child.traits().isEmpty() ? parent.traits() : child.traits()));
        }
        Map<String, String> substitutes = new LinkedHashMap<>(from.partSubstitutes());
        substitutes.putAll(partSubstitutes);
        return new ArmoryMaterial(id, kind, null,
                ingredient != null ? ingredient : from.ingredient(),
                categories.isEmpty() ? from.categories() : categories,
                canSalvage,
                substitutes,
                gearTypeBlacklist.isEmpty() ? from.gearTypeBlacklist() : gearTypeBlacklist,
                colour, name != null ? name : from.name(), merged);
    }
}
