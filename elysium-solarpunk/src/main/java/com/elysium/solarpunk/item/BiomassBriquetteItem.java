package com.elysium.solarpunk.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/**
 * Dried algae, pressed. The one thing in this mod a machine makes.
 *
 * <h2>Why it burns for exactly as long as coal</h2>
 *
 * 1600 ticks, which is coal's number to the tick. It is deliberately not
 * better: a briquette costs a solar condenser, a bioreactor, a body of water
 * and twenty seconds of power, and if it also out-performed coal there would be
 * no reason to ever mine again. Matching coal makes the machines a choice about
 * <em>renewability</em> — you stop needing a mine — rather than a strictly
 * larger number, which is the only version of this that leaves both options
 * standing.
 *
 * <h2>The fuel hook</h2>
 *
 * {@code getBurnTime} is NeoForge's extension on Item, and answering it is all
 * a fuel has to do: vanilla furnaces, blast furnaces, smokers and every modded
 * machine that asks the same question will take one. The recipe type is passed
 * so a fuel can refuse a particular furnace; this one burns anywhere, because
 * "it burns in a furnace but not a smoker" is a rule nobody would guess.
 */
public class BiomassBriquetteItem extends Item {

    /** Coal's burn time, to the tick. */
    public static final int BURN_TICKS = 1600;

    private final String tooltipKey;

    public BiomassBriquetteItem(Item.Properties properties, String tooltipKey) {
        super(properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public int getBurnTime(ItemStack stack, RecipeType<?> recipeType) {
        return BURN_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
    }
}
