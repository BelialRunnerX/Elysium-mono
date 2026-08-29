package com.elysium.trinkets.item;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.item.ElysiumRarities;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.trinket.ElysiumTrinket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * The item a trinket travels on.
 *
 * <h2>One class for all forty</h2>
 *
 * A trinket's behaviour is an {@code ElysiumPassive} held by the
 * {@link ElysiumTrinket} in the library, not code on the item — so the item has
 * nothing to specialise and there is no reason for forty classes. What differs
 * between two trinkets is entirely data: which trinket object, which slot,
 * which element, what tier.
 *
 * <h2>Why it is ElysiumSocketable</h2>
 *
 * Because everything worn is. That is the whole of what makes a trinket
 * reforgeable and ascendable at the reforge table — {@code ElysiumGearAscension}
 * and {@code ElysiumReforgeHandler} both ask for this interface and neither
 * knows this mod exists. It also means a trinket takes runes, gains armour and
 * toughness as it ascends if it declares any, grants character stats from its
 * element, and asks for a character level to work: all of it inherited, none of
 * it written here.
 *
 * <h2>Why it is ICurioItem, and why that interface is empty here</h2>
 *
 * Curios needs to know an item is wearable. It does not need to know what
 * wearing it does — every effect is an {@code ElysiumPassive} the library
 * applies, so there is no {@code curioTick} to write and no attribute hook to
 * override. Implementing the marker and overriding nothing is the smallest true
 * statement: this is a curio, and Curios decides nothing about it.
 */
public class ElysiumTrinketItem extends Item implements ElysiumSocketable, ICurioItem {

    private final ElysiumTrinket trinket;
    private final int tier;

    /**
     * Armour a trinket contributes while worn. Most contribute none.
     *
     * Declared rather than assumed zero because {@link ElysiumSocketable}
     * scales whatever is declared as the piece ascends, and a gorget that gives
     * a point of armour should gain from being ascended exactly as a helmet
     * does. A trinket with none simply never adds a modifier.
     */
    private final float armour;
    private final float toughness;

    public ElysiumTrinketItem(ElysiumTrinket trinket, int tier, float armour, float toughness,
                              Item.Properties properties) {
        super(properties.stacksTo(1).rarity(ElysiumRarities.getRarityFromTier(tier)));
        this.trinket = trinket;
        this.tier = tier;
        this.armour = armour;
        this.toughness = toughness;
    }

    public ElysiumTrinket getTrinket() {
        return trinket;
    }

    @Override
    public ElysiumElement getElement() {
        return trinket.getElement();
    }

    @Override
    public int getElysiumTier() {
        return tier;
    }

    /**
     * True: a trinket is worn.
     *
     * Not cosmetic. The engine asks this in three places — the elemental
     * counter matrix reads worn affinity, gear stats pay armour extra
     * Fortitude, and the combat handler must not mistake a held trinket for a
     * weapon. Answering "no" would leave a trinket contributing nothing to the
     * first two and being swung as a weapon by the third.
     */
    @Override
    public boolean isArmour() {
        return true;
    }

    @Override
    public float getBaseArmour() {
        return armour;
    }

    @Override
    public float getBaseToughness() {
        return toughness;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getEffectiveTier(stack) >= ElysiumRarities.EPIC;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        tooltip.add(Component.translatable("elysiumtrinkets.tooltip.slot." + trinket.getSlot())
                .withStyle(ChatFormatting.DARK_GRAY));

        // What it does, in its own words. The passive is asked at the stack's
        // own tier so an ascended trinket describes what it currently does
        // rather than what it did when it was found.
        tooltip.add(trinket.passiveAt(getEffectiveTier(stack)).getDescription()
                .copy().withStyle(ChatFormatting.GRAY));

        appendSocketTooltip(stack, tooltip);
    }
}
