package com.elysium.arsenal.forge;

import com.elysium.arsenal.item.BowItem;
import com.elysium.arsenal.rules.ForgeRules;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;

/**
 * Which of the five kinds a piece of gear is.
 *
 * <h2>Why this is decided by what the item can do, not by who made it</h2>
 *
 * A pack with a hundred and thirty mods in it has hundreds of swords, and
 * almost none of them are ours. A grade system that only graded our own gear
 * would be a mechanic nobody ever saw, because the sword a player is actually
 * carrying came from somewhere else. So the question asked here is what the
 * item <em>is</em> — does it fire projectiles, does it go in an armour slot,
 * does it dig — and every mod in the pack answers it the same way, because
 * they all answer it to the game.
 *
 * <h2>Why the order matters</h2>
 *
 * Our own paxels are digging tools that are also weapons, and our own bows are
 * ranged weapons that are also tiered. An item that matches two tests has to
 * land in one kind, deterministically, or the same paxel is a tool on one
 * machine and a weapon on another and the two disagree about what it is
 * worth. Most specific first, and the first match wins.
 */
public final class ForgeKinds {

    private ForgeKinds() {
    }

    /** Which kind this stack is, or null if it is not gear at all. */
    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        // Shields and armour before anything else: a shield is not a weapon
        // even though it is held, and armour is never anything else.
        if (stack.getItem() instanceof ShieldItem) {
            return "shield";
        }
        if (stack.getItem() instanceof ArmorItem) {
            return "armour";
        }
        // Ranged before melee, because ours are tiered and a tiered check
        // would swallow them.
        if (stack.getItem() instanceof BowItem
                || stack.getItem() instanceof ProjectileWeaponItem) {
            return "bow";
        }
        // Digging before swording, because a paxel is both and a paxel is a
        // tool: it is the thing a player reaches for to mine with.
        if (stack.getItem() instanceof DiggerItem) {
            return "tool";
        }
        if (stack.getItem() instanceof SwordItem) {
            return "melee";
        }
        // Anything else with a tier and a durability is a weapon of some sort
        // another mod made. Left last so nothing above is stolen from.
        if (stack.getItem() instanceof TieredItem && stack.isDamageableItem()) {
            return "melee";
        }
        return null;
    }

    /** Whether a grade could ever sit on this stack. */
    public static boolean gradeable(ItemStack stack) {
        String kind = of(stack);
        return kind != null && !ForgeRules.of(kind).isEmpty();
    }

    /**
     * Where a grade's modifiers apply.
     *
     * <p>Armour's bonus has to apply in the slot the armour is worn in and
     * nowhere else, or a chestplate held in a hand gives its armour. Everything
     * else applies in the hand it is held in.
     */
    public static EquipmentSlotGroup where(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armour) {
            return EquipmentSlotGroup.bySlot(armour.getEquipmentSlot());
        }
        if (stack.getItem() instanceof ShieldItem) {
            return EquipmentSlotGroup.HAND;
        }
        return EquipmentSlotGroup.MAINHAND;
    }

    /** The slot a stack of this kind is used in, for the durability charge. */
    public static EquipmentSlot slotFor(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armour) {
            return armour.getEquipmentSlot();
        }
        return EquipmentSlot.MAINHAND;
    }
}
