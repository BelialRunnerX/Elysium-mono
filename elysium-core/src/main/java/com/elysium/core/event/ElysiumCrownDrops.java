package com.elysium.core.event;

import com.elysium.core.Elysium;
import com.elysium.core.ElysiumContent;
import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.lib.standing.ElysiumStanding;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * The Emperor's Crown is the one piece with no recipe.
 *
 * It is Elysomnion's own — a bench cannot produce it, so it comes off the body
 * of someone the Empire sent, and only once Suspicion has reached Hunted. That
 * is the top of the loop the Crown belongs to: you get it by being worth
 * sending enforcers after, and by killing enough of them.
 *
 * At {@value ElysiumContent#CROWN_CHANCE} per named enforcer this is a chase,
 * not a reward, which is what a Unique-tier item should be. It is also the
 * reason Suspicion decay matters — park the meter and the drop stops.
 *
 * <h2>Why this is not a reward provider</h2>
 *
 * The library's reward system asks "what does tier 3 pay out", and answers on
 * every qualifying kill. The Crown is not a payout for a tier; it is a rare
 * drop from one specific mob under one specific condition, and expressing that
 * as a provider would mean a provider that declines 96% of the time and quietly
 * starves every other provider of its turn. A separate listener says what it
 * means.
 */
@EventBusSubscriber(modid = Elysium.MODID)
public final class ElysiumCrownDrops {

    private ElysiumCrownDrops() {
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

        if (ElysiumFaction.of(victim) != ElysiumFaction.EMPIRE
                || !ElysiumFaction.isNamedCombatant(victim)) {
            return;
        }

        int suspicion = ElysiumStanding.getSuspicion(player);
        if (ElysiumStanding.bandOf(suspicion) < ElysiumStanding.BAND_HUNTED
                || player.getRandom().nextFloat() >= ElysiumContent.CROWN_CHANCE) {
            return;
        }

        event.getDrops().add(new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(),
                new ItemStack(Elysium.EMPEROR_CROWN.get())));
    }
}
