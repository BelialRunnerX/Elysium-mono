package com.elysium.lib.affix;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.item.ElysiumSocketable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * An Elysium affix: a named, rollable attribute bonus that can be applied to
 * Elysium gear.
 *
 * This used to extend Apotheosis' {@code Affix} class, which meant the mod
 * could not compile - let alone run - without Apotheosis installed. It is now
 * a self-contained implementation, so affixes work standalone.
 */
public class ElysiumAffix {

    private final String name;
    private final Holder<Attribute> attribute;
    private final AttributeModifier.Operation operation;
    private final float min;
    private final float max;

    public ElysiumAffix(String name,
                        Holder<Attribute> attribute,
                        AttributeModifier.Operation operation,
                        float min,
                        float max) {
        this.name = name;
        this.attribute = attribute;
        this.operation = operation;
        this.min = min;
        this.max = max;
    }

    public String getName() {
        return name;
    }

    public Holder<Attribute> getAttribute() {
        return attribute;
    }

    public AttributeModifier.Operation getOperation() {
        return operation;
    }

    public float getMin() {
        return min;
    }

    public float getMax() {
        return max;
    }

    /** Affixes only ever apply to Elysium gear — armour, weapons or tools. */
    public boolean canApplyTo(ItemStack stack) {
        return stack.getItem() instanceof ElysiumSocketable;
    }

    /**
     * Interpolates between {@link #getMin()} and {@link #getMax()}.
     *
     * @param level 0.0 for the weakest possible roll, 1.0 for the strongest
     */
    public double roll(float level) {
        float clamped = Math.max(0.0F, Math.min(1.0F, level));
        return min + (max - min) * clamped;
    }

    /**
     * A stable identifier for this affix's attribute modifier, scoped to the
     * slot it is applied from.
     *
     * Two things depend on this. Stability: in 1.21 modifiers are keyed by
     * {@link ResourceLocation} rather than a random UUID, so re-applying an
     * affix replaces the previous value instead of stacking a fresh copy every
     * tick. And uniqueness per slot: without the slot in the id, a Voidward
     * rune in a helmet and another in a chestplate would produce two modifiers
     * with identical ids on the same attribute, and only one would survive.
     */
    public ResourceLocation modifierId(EquipmentSlotGroup group) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID,
                "affix/" + name.toLowerCase(Locale.ROOT)
                        + "/" + group.name().toLowerCase(Locale.ROOT));
    }

    /**
     * @param level the roll strength, 0.0 to 1.0
     * @param scale 1.0 for an ordinary application, higher when the rune is
     *              aligned to the item's element
     */
    public AttributeModifier createModifier(float level, float scale, EquipmentSlotGroup group) {
        return new AttributeModifier(modifierId(group), roll(level) * scale, operation);
    }

    public Component describe(float level) {
        double amount = roll(level);
        String formatted = operation == AttributeModifier.Operation.ADD_VALUE
                ? String.format("+%.1f", amount)
                : String.format("+%.0f%%", amount * 100.0D);
        return Component.translatable("elysium.affix." + name.toLowerCase(Locale.ROOT), formatted);
    }
}
