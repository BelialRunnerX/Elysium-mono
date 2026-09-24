package com.elysium.core.event;

import com.elysium.core.Elysium;
import com.elysium.core.item.ElysiumRuneItem;
import com.elysium.lib.item.ElysiumPurity;
import com.elysium.lib.item.ElysiumRuneStacks;
import com.elysium.lib.tier.ElysiumTiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Where runes come from (Apotheosis' AP-10).
 *
 * <h2>Mobs, not chests</h2>
 *
 * The affix injector deliberately adds nothing to a loot table: a chest's
 * contents are its author's decision and the mod has no business padding them.
 * A kill is different. Nobody wrote what a zombie drops on the fourth world
 * tier, because until P5 there were no world tiers — so this is the world
 * answering, not a pack being overruled.
 *
 * <h2>What the world tier buys</h2>
 *
 * Both halves: how often a rune falls, and how good it is when it does. Both
 * through the tier's share, so the Frontier drops none and no amount of
 * climbing makes one certain. A rune is meant to be a find.
 */
@EventBusSubscriber(modid = Elysium.MODID)
public final class RuneDropHandler {

    private RuneDropHandler() {
    }

    /** Every rune item that could drop, found once and kept. */
    private static List<Item> runes;

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        int tier = ElysiumTiers.of(player);
        if (player.getRandom().nextDouble() >= ElysiumPurity.dropChance(tier)) {
            return;
        }
        List<Item> pool = pool();
        if (pool.isEmpty()) {
            return;
        }
        ItemStack rune = new ItemStack(pool.get(player.getRandom().nextInt(pool.size())));
        ElysiumRuneStacks.setTier(rune,
                ElysiumPurity.foundPurity(tier, player.getRandom()::nextDouble));
        event.getDrops().add(new ItemEntity(event.getEntity().level(),
                event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(),
                rune));
    }

    /**
     * Every registered rune item.
     *
     * Read from the registry rather than a list, so a rune another mod adds
     * drops exactly as this mod's do — and cached, because a kill is not the
     * moment to walk the item registry.
     */
    private static List<Item> pool() {
        if (runes == null) {
            List<Item> found = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof ElysiumRuneItem) {
                    found.add(item);
                }
            }
            runes = List.copyOf(found);
        }
        return runes;
    }
}
