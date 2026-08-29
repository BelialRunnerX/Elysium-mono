package com.elysium.core.item;

import com.elysium.core.Elysium;
import com.elysium.lib.affix.ElysiumAffixes;
import com.elysium.lib.affix.ElysiumPsionicAffix;
import com.elysium.lib.element.ElysiumElement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import java.util.Locale;
import com.elysium.lib.item.ElysiumGearData;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.item.ElysiumSockets;

/**
 * Elysium armour.
 *
 * A single class covers every element and tier. Per-stack state (socketed
 * runes, reforge rolls, reforge charges spent, ascension tier) lives in the
 * {@link ElysiumComponents#GEAR_DATA} data component rather than loose NBT,
 * which is what 1.20.5+ requires.
 *
 * All the gear's bonuses are surfaced through
 * {@link #getDefaultAttributeModifiers(ItemStack)}. That is the supported way
 * to give an item stack-sensitive attributes in 1.21.1, and it fixes a real bug
 * in the old code: reforge bonuses used to be applied as transient modifiers
 * with a fresh random UUID on every armour tick, so they stacked without limit
 * for as long as the piece was worn.
 */
public class ElysiumArmorItem extends ArmorItem implements ElysiumSocketable {

    private final ElysiumElement element;
    private final int tier;

    public ElysiumArmorItem(Holder<ArmorMaterial> material,
                            ArmorItem.Type type,
                            Item.Properties properties,
                            ElysiumElement element,
                            int tier) {
        super(material, type, properties.rarity(ElysiumRarities.getRarityFromTier(tier)));
        this.element = element;
        this.tier = tier;
    }

    @Override
    public ElysiumElement getElement() {
        return element;
    }

    /** The tier this item was registered with, ignoring ascension. */
    @Override
    public int getElysiumTier() {
        return tier;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getEffectiveTier(stack) >= ElysiumRarities.EPIC;
    }

    // ------------------------------------------------------------------
    // Per-stack data
    // ------------------------------------------------------------------

    // Kept as statics because the reforge and ascension handlers work on a bare
    // stack, with no item instance in hand. Sockets and slot counts now come
    // from ElysiumSocketable, shared with weapons and tools.

    public static ElysiumGearData gearData(ItemStack stack) {
        return ElysiumSockets.gearData(stack);
    }

    public static void setGearData(ItemStack stack, ElysiumGearData data) {
        ElysiumSockets.setGearData(stack, data);
    }

    // ------------------------------------------------------------------
    // Ascension
    // ------------------------------------------------------------------

    // canAscend and getNextTier moved to ElysiumSocketable when weapons and
    // tools became ascendable; there is nothing armour-specific in either.

    /**
     * What the piece is worth before ascension, read off the armour material.
     *
     * The library scales these; it deliberately does not know how to find them,
     * because a trinket has no {@code ArmorMaterial} to look in and the seam has
     * to work for both.
     */
    @Override
    public float getBaseArmour() {
        return getDefense();
    }

    @Override
    public float getBaseToughness() {
        return getToughness();
    }

    @Override
    public boolean isArmour() {
        return true;
    }

    // ------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        ItemAttributeModifiers modifiers = super.getDefaultAttributeModifiers(stack);
        EquipmentSlotGroup group = slotGroup();
        ElysiumGearData data = gearData(stack);
        int effectiveTier = getEffectiveTier(stack);

        // The roll interpolates across the named tiers; the ascendant scale is
        // what carries it past them, since a 0..1 interpolation cannot express
        // a tier of forty on its own.
        float level = Math.min(1.0F,
                (float) effectiveTier / ElysiumRarities.MAX_NAMED_TIER);
        float ascendant = ElysiumRarities.getAscendantScale(effectiveTier);

        // Psionic affix from the piece's element, scaled by tier.
        ElysiumPsionicAffix psionic = ElysiumAffixes.forElement(element);
        if (psionic != null) {
            modifiers = modifiers.withModifierAdded(
                    psionic.getAttribute(), psionic.createModifier(level, ascendant, group), group);
        }

        // One affix per socketed rune, and the tier's share of armour and
        // toughness on top of the material's own. Both in one call: see
        // ElysiumSocketable#elysiumModifiers for why they are not two.
        modifiers = elysiumModifiers(stack, modifiers, group);

        // Reforge rolls deliberately do NOT appear here. They are read as
        // character stats instead — armour becomes Fortitude, health becomes
        // Vitality, speed becomes Agility — by ElysiumGearStats, and applied
        // once by ElysiumStatHandler. Leaving the old attribute block in place
        // as well paid every reforge twice, on the same three attributes, with
        // only half of it visible on the tooltip.

        return modifiers;
    }

    private EquipmentSlotGroup slotGroup() {
        return switch (this.getType()) {
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
            default -> EquipmentSlotGroup.ARMOR;
        };
    }

    private static ResourceLocation modifierId(String path, EquipmentSlotGroup group) {
        return ResourceLocation.fromNamespaceAndPath(Elysium.MODID,
                path + "/" + group.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack,
                                Item.TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        appendIdentityTooltip(stack, tooltip);

        // What this set answers, so the counter matrix is discoverable in game
        // rather than only in the archive.
        // counters() is a set now, because an element added by another mod
        // may answer any number of others; the tooltip still only has room
        // to name two.
        java.util.List<ElysiumElement> countered =
                new java.util.ArrayList<>(element.counters());
        if (countered.size() == 2) {
            tooltip.add(Component.translatable("elysium.tooltip.resists",
                            countered.get(0).getDisplayName(),
                            countered.get(1).getDisplayName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        appendStatTooltip(stack, tooltip);
        appendRuneTooltip(stack, tooltip);

        ElysiumGearData data = gearData(stack);
        if (data.isReforged()) {
            tooltip.add(Component.translatable("elysium.tooltip.reforged",
                            data.reforgesRemaining(), ElysiumGearData.MAX_REFORGES)
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
