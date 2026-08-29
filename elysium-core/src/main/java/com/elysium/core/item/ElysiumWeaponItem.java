package com.elysium.core.item;

import com.elysium.lib.element.ElysiumElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.item.ElysiumSocketable;

/**
 * An Elysium weapon.
 *
 * Weapons carry an element and a tier, and that pairing is the whole point:
 * {@code ElysiumCombatHandler} reads the element to decide whether the swing
 * has the advantage, and the tier to decide how large it is.
 *
 * Extends {@link SwordItem} so vanilla's sweep, enchantability and
 * block-breaking behaviour come for free. Attack damage and speed are declared
 * through {@code SwordItem.createAttributes}, which is where 1.21.1 expects
 * them — an item's attributes are a data component now, not an override.
 */
public class ElysiumWeaponItem extends SwordItem implements ElysiumSocketable {

    private final ElysiumElement element;
    private final int tier;
    private final float baseAttackDamage;

    public ElysiumWeaponItem(Tier material,
                             ElysiumElement element,
                             int tier,
                             float attackDamage,
                             float attackSpeed) {
        super(material, new Item.Properties()
                .rarity(ElysiumRarities.getRarityFromTier(tier))
                .attributes(SwordItem.createAttributes(material, attackDamage, attackSpeed)));
        this.element = element;
        this.tier = tier;
        this.baseAttackDamage = attackDamage;
    }

    /**
     * What the blade hits for before ascension.
     *
     * Kept from the constructor rather than read back out of the attribute
     * component: {@code createAttributes} folds this together with the tier's
     * own damage bonus into a list, and recovering one number from that list
     * means knowing which of vanilla's modifier ids it landed under.
     */
    @Override
    public float getBaseAttackDamage() {
        return baseAttackDamage;
    }

    @Override
    public ElysiumElement getElement() {
        return element;
    }

    @Override
    public int getElysiumTier() {
        return tier;
    }

    /**
     * Socketed runes ride on the weapon the same way they ride on armour, and
     * a rune matching the blade's element bites harder. Attributes are declared
     * per-stack because the sockets are per-stack.
     */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return elysiumModifiers(stack, super.getDefaultAttributeModifiers(stack),
                EquipmentSlotGroup.MAINHAND);
    }

    /**
     * A blade is what Strength is for. Everything in the Elysium weapon line
     * turns the wielder's base damage into more than an empty hand would.
     */
    @Override
    public float getDamageMultiplier() {
        return 1.6F;
    }

    /**
     * How much extra damage a favourable matchup deals.
     *
     * Takes the stack because it has to read the <em>effective</em> tier. The
     * combat handler has always read the effective one; this side read the
     * registered one, so an ascended blade fought at its real advantage and
     * told the player it had the advantage of the day it was forged.
     */
    public float getAdvantage(ItemStack stack) {
        return ElysiumRarities.getAdvantage(getEffectiveTier(stack));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getEffectiveTier(stack) >= ElysiumRarities.LEGENDARY;
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                Item.TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        appendIdentityTooltip(stack, tooltip);

        // counters() is a set now, because an element added by another mod
        // may answer any number of others; the tooltip still only has room
        // to name two.
        java.util.List<ElysiumElement> countered =
                new java.util.ArrayList<>(element.counters());
        if (countered.size() == 2) {
            int percent = Math.round(getAdvantage(stack) * 100.0F);
            tooltip.add(Component.translatable("elysium.tooltip.advantage",
                            percent,
                            countered.get(0).getDisplayName(),
                            countered.get(1).getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }

        appendStatTooltip(stack, tooltip);
        appendRuneTooltip(stack, tooltip);
    }
}
