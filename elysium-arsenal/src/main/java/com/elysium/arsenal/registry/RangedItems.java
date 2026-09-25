package com.elysium.arsenal.registry;

import com.elysium.arsenal.ElysiumArsenal;
import com.elysium.arsenal.item.BowItem;
import com.elysium.arsenal.rules.RangedRules;
import com.elysium.arsenal.rules.WeaponRules;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thirty-six bows and twelve kinds of ammunition, walked out of the rules.
 *
 * <p>Nothing in this module was ranged before this file. Six kinds across six
 * tiers is the same shape the melee weapons come in, and for the same reason:
 * a seventh kind would have to be a copy of one of the six with a different
 * name.
 */
public final class RangedItems {

    private RangedItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ElysiumArsenal.MODID);

    /** How many uses each tier of bow takes before it breaks. */
    public static final Map<String, Integer> USES = uses();

    private static Map<String, Integer> uses() {
        Map<String, Integer> made = new LinkedHashMap<>();
        made.put("wooden", 120);
        made.put("stone", 190);
        made.put("golden", 96);
        made.put("iron", 320);
        made.put("diamond", 780);
        made.put("netherite", 1_400);
        // The Cordon's: a bow lasts as long as Valoria's tool of the same
        // tier did, which is the one number for it anybody has played.
        for (WeaponRules.Rung rung : WeaponRules.RUNGS) {
            if (rung.cordon()) {
                made.put(rung.name(), rung.uses());
            }
        }
        return Map.copyOf(made);
    }

    public static final Map<String, DeferredItem<BowItem>> BOWS = registerBows();
    public static final Map<String, DeferredItem<ArrowItem>> SHOTS = registerShots();

    private static Map<String, DeferredItem<BowItem>> registerBows() {
        Map<String, DeferredItem<BowItem>> made = new LinkedHashMap<>();
        for (String tier : RangedRules.TIERS) {
            for (RangedRules.Bow kind : RangedRules.KINDS) {
                RangedRules.Bow at = RangedRules.at(kind, tier);
                String name = tier + "_" + kind.name();
                Integer durability = USES.get(tier);
                if (durability == null) {
                    throw new IllegalStateException(tier + " is a tier with no "
                            + "durability, so every bow of it would be unbreakable");
                }
                made.put(name, ITEMS.register(name, () -> new BowItem(
                        new Item.Properties().durability(durability), at)));
            }
        }
        if (made.size() != RangedRules.catalogue().size()) {
            throw new IllegalStateException("the catalogue has "
                    + RangedRules.catalogue().size() + " bows and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }

    private static Map<String, DeferredItem<ArrowItem>> registerShots() {
        Map<String, DeferredItem<ArrowItem>> made = new LinkedHashMap<>();
        for (RangedRules.Shot shot : RangedRules.SHOTS) {
            made.put(shot.name(), ITEMS.register(shot.name(),
                    () -> new ArrowItem(new Item.Properties())));
        }
        if (made.size() != RangedRules.SHOTS.size()) {
            throw new IllegalStateException("there are " + RangedRules.SHOTS.size()
                    + " kinds of ammunition and " + made.size()
                    + " were registered");
        }
        return Map.copyOf(made);
    }
}
