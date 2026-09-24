package com.elysium.armory.event;

import com.elysium.armory.ElysiumArmory;
import com.elysium.armory.item.ArmoryGear;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;


/**
 * When the traits act.
 *
 * <p>Every rule here is read off the gear's own traits, so nothing in this file
 * knows what "Sharp" or "Aquatic" is. Ticking work is kept cheap: the effects
 * that only matter now and then run on a slow beat rather than every tick, and
 * everything checks the cheap question (does this stack have any trait of this
 * kind at all) before the expensive one.
 */
@EventBusSubscriber(modid = ElysiumArmory.MODID)
public final class ArmoryTraitEvents {

    private ArmoryTraitEvents() {
    }

    /*
     * The effect events that used to live here — extra damage and on-hit,
     * negated damage, the worn and held ticks, mining speed and bonus drops —
     * are now ElysiumEffectEvents in the library.
     *
     * Every one of them opened with `instanceof ArmoryGear`, which is a test
     * only modular gear can pass. An affix rolled onto a vanilla iron sword
     * stated its effects, registered them with the engine, showed them in the
     * tooltip, and did nothing, because no event ever asked that sword
     * anything. The library asks a question any item can answer instead.
     *
     * What is left here is the coded traits: behaviours of particular traits
     * rather than kinds of effect, reading things only this module knows.
     */

    /** The traits whose behaviour is code (SG-63), where each of them acts. */
    @SubscribeEvent
    public static void onCodedTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.level().getGameTime() % 10 != 0) {
            return;
        }
        com.elysium.armory.trait.ArmoryCodedTraits.turtle(player);
        com.elysium.armory.trait.ArmoryCodedTraits.voidWard(player);
        com.elysium.armory.trait.ArmoryCodedTraits.flammable(player);
    }

    /** Ancient: more experience from what is killed and what is broken. */
    @SubscribeEvent
    public static void onExperience(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player != null) {
            event.setDroppedExperience(com.elysium.armory.trait.ArmoryCodedTraits
                    .experience(player.getMainHandItem(), event.getDroppedExperience()));
        }
    }

    /** Lustrous: faster in the light. */
    @SubscribeEvent
    public static void onLightBonus(PlayerEvent.BreakSpeed event) {
        ItemStack held = event.getEntity().getMainHandItem();
        if (held.getItem() instanceof ArmoryGear) {
            event.setNewSpeed(event.getNewSpeed()
                    + com.elysium.armory.trait.ArmoryCodedTraits.lightBonus(held, event.getEntity()));
        }
    }

    /** Magmatic: drops come out smelted. Jabberwocky: diamond ore is not always diamond ore. */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ArmoryGear)) {
            return;
        }
        event.setDroppedExperience(com.elysium.armory.trait.ArmoryCodedTraits
                .experience(held, event.getDroppedExperience()));
        com.elysium.armory.trait.ArmoryCodedTraits.jabberwocky(level, event.getPos(), held, event.getState());
        if (com.elysium.armory.trait.ArmoryTraitEffects.level(held, "magmatic") <= 0) {
            return;
        }
        for (net.minecraft.world.entity.item.ItemEntity entity : event.getDrops()) {
            ItemStack smelted = com.elysium.armory.trait.ArmoryCodedTraits.smelt(level, entity.getItem());
            if (!smelted.isEmpty()) {
                entity.setItem(smelted);
            }
        }
    }

    /** Bounce and Moonwalker: what a fall costs whoever is wearing them. */
    @SubscribeEvent
    public static void onFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (com.elysium.armory.trait.ArmoryCodedTraits.bounce(entity)) {
            event.setCanceled(true);
            entity.setDeltaMovement(entity.getDeltaMovement().x,
                    -entity.getDeltaMovement().y * 0.75D, entity.getDeltaMovement().z);
            entity.hurtMarked = true;
            return;
        }
        float reduced = com.elysium.armory.trait.ArmoryCodedTraits
                .moonwalkerFall(entity, event.getDistance());
        if (reduced != event.getDistance()) {
            event.setDistance(reduced);
        }
    }

    /** Confetti: a kill with it goes off. */
    @SubscribeEvent
    public static void onKill(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)
                || !(event.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        com.elysium.armory.trait.ArmoryCodedTraits.confetti(level, event.getEntity(), player.getMainHandItem());
        com.elysium.armory.trait.ArmoryCodedTraits.confetti(level, event.getEntity(), player.getOffhandItem());
    }

    /** Nothing here reads a hit after the fact; this is the hook that says so. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        // Traits act on the blow as it arrives (onAttack, onIncoming), not once
        // it has landed: a trait that turned damage aside has to do it before
        // the health is gone. Kept as a deliberate no-op rather than absent, so
        // the next person to look does not add one here by accident.
    }
}
