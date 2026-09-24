package com.elysium.lib.elite;

import com.elysium.lib.ElysiumLib;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * What a promoted creature is worth to whoever put it down (Silent Gear's
 * SG-93).
 *
 * <h2>More of what it already dropped</h2>
 *
 * Not a second loot table. An elite zombie drops what a zombie drops, more of
 * it — so a pack that changed what zombies drop has changed what elite zombies
 * drop too, without knowing this exists.
 *
 * <p>The extra is rolled per stack rather than multiplied outright, so the
 * multiplier reads as a chance at seconds rather than as a guarantee, and a
 * rare drop stays rare: an invader is three times as likely to give you the
 * thing, not certain to give you three of them.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumEliteDrops {

    private ElysiumEliteDrops() {
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        double multiplier = ElysiumElites.lootMultiplier(mob);
        if (multiplier <= 1.0D) {
            return;
        }
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity dropped : event.getDrops()) {
            double left = multiplier - 1.0D;
            while (left > 0.0D) {
                if (player.getRandom().nextDouble() >= Math.min(1.0D, left)) {
                    break;
                }
                ItemStack copy = dropped.getItem().copy();
                extra.add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), copy));
                left -= 1.0D;
            }
        }
        event.getDrops().addAll(extra);
    }

    /**
     * And the experience, which is not rolled: experience is a number rather
     * than a thing, and a number has no reason to arrive in whole copies.
     */
    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        double multiplier = ElysiumElites.lootMultiplier(mob);
        if (multiplier > 1.0D) {
            event.setDroppedExperience(
                    (int) Math.round(event.getDroppedExperience() * multiplier));
        }
    }
}
