package com.elysium.armory.trait;

import com.elysium.armory.property.ArmoryProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Locale;
import java.util.Map;

/**
 * The trait effects that change what a piece of gear <em>is</em>, applied while
 * it is being computed: its properties, its attributes, the enchantments it
 * carries, and whether it burns.
 *
 * <p>Everything here happens once, when the piece is built or recomputed, not
 * every tick. The rest of the effects — the ones that happen when the piece is
 * used, worn or swung — are in {@link com.elysium.lib.effect.ElysiumEffectHandlers}.
 */
public final class ArmoryTraitProperties {

    private ArmoryTraitProperties() {
    }

    /**
     * Property modifiers (SG-61's number_property_modifier): a flat bonus of
     * {@code per_level x level}, optionally scaled by how damaged the piece is
     * and by the property's own current value — which is what lets a trait say
     * "sharper as it wears" without knowing what the blade is made of.
     */
    public static void apply(ItemStack stack, Map<String, Double> values, double damageRatio) {
        for (com.elysium.lib.effect.ElysiumItemEffects.Active active : ArmoryTraitEffects.of(stack, "property")) {
            JsonObject modifiers = active.effect().getAsJsonObject("modifiers");
            for (Map.Entry<String, JsonElement> entry : modifiers.entrySet()) {
                ArmoryProperty property = ArmoryProperty.byId(entry.getKey());
                if (property == null) {
                    continue;
                }
                JsonObject spec = entry.getValue().getAsJsonObject();
                double bonus = spec.get("per_level").getAsDouble() * active.level();
                if (spec.has("damage_ratio") && spec.get("damage_ratio").getAsBoolean()) {
                    bonus *= damageRatio;
                }
                double current = values.getOrDefault(property.id(), property.base());
                if (spec.has("of_value") && spec.get("of_value").getAsBoolean()) {
                    bonus *= current;
                }
                values.put(property.id(), current + bonus);
            }
        }
    }

    /** Attribute effects (SG-61's attribute), added to whatever the piece already grants. */
    public static ItemAttributeModifiers attributes(ItemStack stack, ItemAttributeModifiers base) {
        ItemAttributeModifiers out = base;
        for (com.elysium.lib.effect.ElysiumItemEffects.Active active : com.elysium.lib.effect.ElysiumItemEffects.of(stack, "attribute")) {
            for (JsonElement element : active.effect().getAsJsonArray("modifiers")) {
                JsonObject spec = element.getAsJsonObject();
                Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE
                        .getHolder(ResourceKey.create(Registries.ATTRIBUTE,
                                ResourceLocation.parse(spec.get("attribute").getAsString()))).orElse(null);
                if (attribute == null) {
                    continue;                   // an attribute from a mod that is not here
                }
                double value = valueFor(spec, active.level());
                if (value == 0.0D) {
                    continue;
                }
                AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(
                        spec.get("operation").getAsString().toUpperCase(Locale.ROOT));
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                        com.elysium.armory.ElysiumArmory.MODID, "trait/" + active.source());
                out = out.withModifierAdded(attribute, new AttributeModifier(id, value, operation),
                        slot(spec.has("slot") ? spec.get("slot").getAsString() : "any"));
            }
        }
        return out;
    }

    /** The value for this level: the list is one entry per level, and the last stands for anything past it. */
    private static double valueFor(JsonObject spec, int level) {
        var values = spec.getAsJsonArray("values");
        if (values.isEmpty() || level <= 0) {
            return 0.0D;
        }
        int index = Math.min(level, values.size()) - 1;
        return values.get(index).getAsDouble();
    }

    private static EquipmentSlotGroup slot(String name) {
        return switch (name) {
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    /**
     * Enchantment effects (SG-61's enchantment): granted while the trait is
     * there.
     *
     * <p>Applied on the tick rather than when the piece is computed, because
     * enchantments are a datapack registry and the lookup for them only exists
     * where a level does. A piece therefore gains them the first tick it is
     * held or worn, which is soon enough for anything that reads them.
     */
    public static void enchantments(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        var effects = com.elysium.lib.effect.ElysiumItemEffects.of(stack, "enchantment");
        if (effects.isEmpty()) {
            return;
        }
        var registry = registries.lookup(Registries.ENCHANTMENT).orElse(null);
        if (registry == null) {
            return;
        }
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
                stack.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        ItemEnchantments.EMPTY));
        boolean changed = false;
        for (com.elysium.lib.effect.ElysiumItemEffects.Active active : effects) {
            JsonObject spec = active.effect().getAsJsonObject("enchantments");
            for (Map.Entry<String, JsonElement> entry : spec.entrySet()) {
                Holder<Enchantment> enchantment = registry.get(ResourceKey.create(Registries.ENCHANTMENT,
                        ResourceLocation.parse(entry.getKey()))).map(h -> (Holder<Enchantment>) h).orElse(null);
                if (enchantment == null) {
                    continue;
                }
                var levels = entry.getValue().getAsJsonArray();
                int index = Math.min(active.level(), levels.size()) - 1;
                if (index >= 0) {
                    int want = levels.get(index).getAsInt();
                    if (enchantments.getLevel(enchantment) < want) {
                        enchantments.upgrade(enchantment, want);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        }
    }

    /** Fireproof, and any components a trait attaches (food, for the traits that feed you). */
    public static void components(ItemStack stack) {
        if (com.elysium.lib.effect.ElysiumItemEffects.has(stack, "fireproof")) {
            stack.set(net.minecraft.core.component.DataComponents.FIRE_RESISTANT,
                    net.minecraft.util.Unit.INSTANCE);
        }
        for (com.elysium.lib.effect.ElysiumItemEffects.Active active : com.elysium.lib.effect.ElysiumItemEffects.of(stack, "components")) {
            JsonObject components = active.effect().getAsJsonObject("components");
            for (Map.Entry<String, JsonElement> entry : components.entrySet()) {
                var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                        ResourceLocation.parse(entry.getKey()));
                if (type == null || type.codec() == null) {
                    continue;
                }
                type.codec().parse(JsonOps.INSTANCE, entry.getValue())
                        .result().ifPresent(value -> set(stack, type, value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(ItemStack stack, net.minecraft.core.component.DataComponentType<T> type,
                                Object value) {
        stack.set(type, (T) value);
    }
}
