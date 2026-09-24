package com.elysium.core.menu;

import com.elysium.core.Elysium;
import com.elysium.core.item.ElysiumRuneItem;
import com.elysium.core.item.SigilItem;
import com.elysium.lib.item.ElysiumAugments;
import com.elysium.lib.item.ElysiumGear;
import com.elysium.lib.item.ElysiumRuneStacks;
import com.elysium.lib.item.ElysiumSocketable;
import com.elysium.lib.tier.ElysiumTiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Augmenting Table's menu: a piece, a sigil, and the piece again — changed.
 *
 * <h2>Nothing is spent on nothing</h2>
 *
 * A sigil that would not change the piece produces no result at all, so the
 * slot stays empty and there is nothing to take. That is the whole of the
 * safety here: a player cannot spend a Sigil of Withdrawal on a piece with no
 * runes in it, because the bench never offers them anything for it.
 *
 * <h2>The roll is fixed while the inputs are</h2>
 *
 * Enhancement and Rebirth both roll. The seed comes from the inputs, so the
 * result a player sees is the result they get, and closing the screen does not
 * reroll it.
 */
public class AugmentingMenu extends TwoInputBenchMenu {

    public AugmentingMenu(int id, Inventory inventory) {
        super(Elysium.AUGMENTING_MENU.get(), id, inventory);
    }

    /** The sigil in the second slot, or null. */
    public String sigil() {
        ItemStack stack = inputs.getItem(1);
        return stack.getItem() instanceof SigilItem item ? item.augment() : null;
    }

    @Override
    protected boolean acceptsFirst(ItemStack stack) {
        return ElysiumGear.is(stack);
    }

    @Override
    protected boolean acceptsSecond(ItemStack stack) {
        return stack.getItem() instanceof SigilItem;
    }

    @Override
    protected void assemble() {
        String sigil = sigil();
        ItemStack gear = inputs.getItem(0);
        if (sigil == null || gear.isEmpty()) {
            return;
        }
        ElysiumSocketable described = ElysiumGear.of(gear);
        int gearTier = described == null ? 0 : described.getElysiumTier();
        if (!ElysiumAugments.applies(sigil, gear, gearTier)) {
            return;
        }
        ItemStack worked = gear.copy();
        // Seeded from what is on the bench: the same inputs give the same
        // result, so looking at it twice cannot reroll it.
        RandomSource random = RandomSource.create(seedOf(gear, sigil));
        ElysiumAugments.Result result = ElysiumAugments.apply(sigil, worked,
                ElysiumTiers.of(owner), random);
        if (!result.changed()) {
            return;
        }
        outputs.setItem(0, worked);
        if (result.hasRune()) {
            ItemStack rune = runeItem(result.rune(), result.runeTier());
            if (!rune.isEmpty()) {
                outputs.setItem(1, rune);
            }
        }
    }

    private static long seedOf(ItemStack gear, String sigil) {
        return (long) gear.getDescriptionId().hashCode() * 31L
                + gear.getDamageValue() * 7L
                + sigil.hashCode();
    }

    /**
     * The item a withdrawn rune comes back as.
     *
     * Found by asking the registry which rune item carries that rune, rather
     * than by a table: a rune added by another mod comes back out as its own
     * item, and one whose item is gone comes back as nothing rather than as
     * the wrong thing.
     */
    private static ItemStack runeItem(String rune, int tier) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ElysiumRuneItem holder
                    && holder.getRune().getSerialisedName().equals(rune)) {
                ItemStack stack = new ItemStack(item);
                ElysiumRuneStacks.setTier(stack, tier);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void pay() {
        inputs.removeItem(0, 1);
        inputs.removeItem(1, 1);
    }
}
