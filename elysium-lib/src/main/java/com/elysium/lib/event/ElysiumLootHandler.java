package com.elysium.lib.event;

import com.elysium.lib.ElysiumLib;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.standing.ElysiumRewards;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * What the two meters are worth, in items.
 *
 * The split is the whole design:
 *
 * <ul>
 *   <li><b>Favor sets the tier.</b> Which shelf the reward comes off. What is
 *       on each shelf is decided by whichever content mod registered a
 *       provider — see {@link ElysiumRewards} — because the library ships no
 *       items and has no opinion about what a reward is.</li>
 *   <li><b>Suspicion sets the amount.</b> How many of it, one through five.</li>
 * </ul>
 *
 * So the two loops pay differently and a player can feel which one they are on.
 * Pure Favor is a trickle of good things. Pure Suspicion is a pile of cheap
 * ones. Both at once is the jackpot, and also four enforcers and four raiders
 * converging on you at the same time.
 */
@EventBusSubscriber(modid = ElysiumLib.MODID)
public final class ElysiumLootHandler {

    private ElysiumLootHandler() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (level.isClientSide()) {
            return;
        }

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player player)) {
            return;
        }

        ElysiumFaction faction = ElysiumFaction.of(victim);
        if (faction == ElysiumFaction.NEUTRAL) {
            return;
        }

        int favor = ElysiumStanding.getFavor(player);
        int suspicion = ElysiumStanding.getSuspicion(player);
        RandomSource random = player.getRandom();

        // The mod's own faction mobs always pay. Ordinary hostiles roll for it,
        // and only once either meter is above notice.
        if (!ElysiumFaction.isNamedCombatant(victim)
                && random.nextFloat() >= ElysiumStanding.incidentalDropChance(favor, suspicion)) {
            return;
        }

        int tier = ElysiumStanding.lootTier(favor);
        int amount = ElysiumStanding.lootAmount(suspicion);

        // Luck is a chance at more of what Suspicion already decided you get.
        // It multiplies the amount rather than the tier so that the two meters
        // keep their distinct jobs: Favor still owns quality, Suspicion still
        // owns quantity, and Luck simply makes quantity go further.
        float extra = ElysiumPassives.extraDropChance(player);
        for (int i = 0; i < amount; i++) {
            addDrop(event, victim, level, ElysiumRewards.roll(tier, random));
            if (random.nextFloat() < extra) {
                addDrop(event, victim, level, ElysiumRewards.roll(tier, random));
            }
        }

    }

    /**
     * The Emperor's Crown is the one piece with no recipe.
     *
     * It is Elysomnion's own — a bench cannot produce it, so it comes off the
     * body of someone the Empire sent, and only once Suspicion has reached
     * Hunted. That is the top of the loop the Crown belongs to: you get it by
     * being worth sending enforcers after, and by killing enough of them.
     *
     * At {@value #CROWN_CHANCE} per named enforcer this is a chase, not a
     * reward, which is what a Unique-tier item should be. It is also the reason
     * Suspicion decay matters — park the meter and the drop stops.
     */

    /**
     * One item off the shelf Favor has unlocked.
     *
     * Each tier keeps a chance of the tier below it, so the reward still varies
     * once a player is parked at the top — a fixed table stops being a reward
     * and becomes a rate.
     */
    private static void addDrop(LivingDropsEvent event, LivingEntity victim,
                                Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        event.getDrops().add(new ItemEntity(level,
                victim.getX(), victim.getY(), victim.getZ(), stack));
    }
}
