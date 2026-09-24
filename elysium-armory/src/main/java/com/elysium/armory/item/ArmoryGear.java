package com.elysium.armory.item;

import com.elysium.armory.material.ArmoryGearBuild;
import net.minecraft.world.item.ItemStack;

/**
 * A piece of modular gear, whatever vanilla class it had to extend.
 *
 * <p>Armour has to be an {@code ArmorItem} for the game to equip it, a shield a
 * {@code ShieldItem} for the game to raise it, a bow a {@code BowItem} for the
 * game to draw it — and Java has one superclass to give. So what makes a piece
 * of gear modular is this interface and {@link ArmoryGearLogic}, not a shared
 * base class: every item delegates the same three questions to the same code.
 */
public interface ArmoryGear extends com.elysium.lib.item.ElysiumKinded {

    /** Which gear type this is: sword, hammer, chestplate. */
    String gearType();

    /**
     * The same answer, in the library's vocabulary.
     *
     * The gear type <em>is</em> a kind in {@code ElysiumGearTree} — the armory
     * reads its tree from there — so affixes, gems and runes that name a kind
     * find modular gear without knowing this module exists. Without it a
     * modular sword is a plain {@code Item} to the library and nothing that
     * says "only on melee weapons" could ever see it.
     */
    @Override
    default String gearKind() {
        return gearType();
    }

    /** The armour slot this piece is worn in, or null if it is not armour. */
    default String armourSlot() {
        return ArmoryGearLogic.armourSlot(gearType());
    }

    /** What this stack is built from, or null if the data it names is gone. */
    default ArmoryGearBuild build(ItemStack stack) {
        return ArmoryGearLogic.build(gearType(), stack);
    }

    /** Computes the stack's properties into the components the game reads. */
    default void recompute(ItemStack stack) {
        ArmoryGearLogic.recompute(this, stack);
    }
}
