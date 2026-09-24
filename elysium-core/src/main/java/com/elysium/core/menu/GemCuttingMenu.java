package com.elysium.core.menu;

import com.elysium.core.Elysium;
import com.elysium.core.item.ElysiumRuneItem;
import com.elysium.lib.item.ElysiumPurity;
import com.elysium.lib.item.ElysiumRuneStacks;
import com.elysium.lib.item.ElysiumRuneTiers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Gem Cutting Table's menu: a rune and a pile of dust, one purity higher.
 *
 * <p>The price is what a second rune of the same purity would have been worth,
 * so cutting and merging come to the same thing and a player picks between
 * them on what they have.
 */
public class GemCuttingMenu extends TwoInputBenchMenu {

    public GemCuttingMenu(int id, Inventory inventory) {
        super(Elysium.GEM_CUTTING_MENU.get(), id, inventory);
    }

    /** What cutting the rune in the first slot would cost, or 0 if it cannot be cut. */
    public int cost() {
        ItemStack rune = inputs.getItem(0);
        if (!(rune.getItem() instanceof ElysiumRuneItem)) {
            return 0;
        }
        int tier = ElysiumRuneStacks.tierOf(rune);
        return ElysiumPurity.canCut(tier) ? ElysiumPurity.cutCost(tier) : 0;
    }

    @Override
    protected boolean acceptsFirst(ItemStack stack) {
        return stack.getItem() instanceof ElysiumRuneItem;
    }

    @Override
    protected boolean acceptsSecond(ItemStack stack) {
        return stack.is(Elysium.GEM_DUST.get());
    }

    @Override
    protected void assemble() {
        int cost = cost();
        if (cost <= 0 || inputs.getItem(1).getCount() < cost) {
            return;
        }
        ItemStack rune = inputs.getItem(0);
        ItemStack cut = rune.copy();
        cut.setCount(1);
        ElysiumRuneStacks.setTier(cut,
                ElysiumRuneTiers.clamp(ElysiumRuneStacks.tierOf(rune) + 1));
        outputs.setItem(0, cut);
    }

    @Override
    protected void pay() {
        int cost = cost();
        inputs.removeItem(0, 1);
        if (cost > 0) {
            inputs.removeItem(1, cost);
        }
    }
}
