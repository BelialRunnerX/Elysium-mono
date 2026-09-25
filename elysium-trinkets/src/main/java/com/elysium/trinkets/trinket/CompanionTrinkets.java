package com.elysium.trinkets.trinket;

import com.elysium.lib.element.ElysiumElement;
import com.elysium.lib.element.ElysiumElements;
import com.elysium.lib.nihil.ElysiumNull;
import com.elysium.lib.trinket.ElysiumTrinket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The companion slot, and the four things that hover in it
 * (docs/SPLITS.md "Valoria: charms"; docs/SPEC_VALORIA.md).
 *
 * <h2>What Valoria had</h2>
 *
 * One slot, {@code pet}, and one thing to put in it: a pixie that followed the
 * player and did nothing at all. A cosmetic. This module has no grammar for a
 * cosmetic — a slot is scarce, and a thing that costs a slot and changes
 * nothing is not a decision — so the slot comes across and what goes in it is
 * rewritten in the module's own terms: found, never crafted, and each one a
 * rule with a price.
 *
 * <h2>What hovers there now</h2>
 *
 * Four companions, each a small machine or anomaly from the Cordoned Planet,
 * each doing one thing for you and one thing to you. The mote lights the
 * dark and is seen doing it; the carrion drone finds more and is frowned on;
 * the lattice shard guards a player who holds still and hinders one who does
 * not; the purge drone bleeds the Null gauge and draws on the body to do it.
 * None ascends: a companion that is 30% more true is not a thing.
 */
public final class CompanionTrinkets {

    private CompanionTrinkets() {
    }

    /** The slot, which is Valoria's pet slot under its own name. */
    public static final String SLOT = "companion";

    /** How often a companion acts, in ticks: every five seconds. */
    public static final int EVERY = 100;

    /** How long the mote's light lasts after each act, comfortably past the next. */
    public static final int LIGHT_TICKS = 300;

    /** What the purge drone takes off the gauge each time it acts. */
    public static final float PURGE = 1.0F;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("elysiumtrinkets", path);
    }

    /** Registers one companion. Level requirement, not tier: these do not ascend. */
    private static ElysiumTrinket companion(String path, ElysiumElement element, String slot,
                                            int levelRequirement, TrinketPassive passive) {
        return ElysiumTrinket.register(id(path), element, slot, levelRequirement,
                ElysiumTrinket.unique(passive));
    }

    /** Whether a player is holding still enough for something to settle on them. */
    static boolean still(Player player) {
        return player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4D;
    }

    /** Lights the dark for you, and for everything looking for you. */
    public static final ElysiumTrinket MOTE = companion(
            "mote", ElysiumElements.NEURAL, "companion", 5,
            new TrinketPassive("mote") {
                @Override
                public void onServerTick(Player player) {
                    if (player.tickCount % EVERY == 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, LIGHT_TICKS,
                                0, true, false, true));
                    }
                }

                @Override
                public float suspicionScale(Player player) {
                    return 1.5F;
                }
            });

    /** Picks over what you kill, and the Empire notices the company you keep. */
    public static final ElysiumTrinket CARRION_DRONE = companion(
            "carrion_drone", ElysiumElements.KINETIC, "companion", 10,
            new TrinketPassive("carrion_drone") {
                @Override
                public float extraDropChance(Player player) {
                    return 0.25F;
                }

                @Override
                public float favorScale(Player player) {
                    return 0.75F;
                }
            });

    /** Settles on you when you hold still, gets in the way when you do not, and is a guard and not a weapon. */
    public static final ElysiumTrinket LATTICE_SHARD = companion(
            "lattice_shard", ElysiumElements.VOID, "companion", 15,
            new TrinketPassive("lattice_shard") {
                @Override
                public float defenceScale(Player defender, DamageSource source) {
                    return still(defender) ? 0.6F : 1.1F;
                }

                @Override
                public float attackScale(Player attacker, LivingEntity victim) {
                    return 0.9F;
                }
            });

    /** Bleeds the Null field out of you, and bleeds you a little to do it. */
    public static final ElysiumTrinket PURGE_DRONE = companion(
            "purge_drone", ElysiumElements.VOID, "companion", 20,
            new TrinketPassive("purge_drone") {
                @Override
                public void onServerTick(Player player) {
                    if (player.tickCount % EVERY == 0 && ElysiumNull.saturation(player) > 0.0F) {
                        ElysiumNull.add(player, -PURGE);
                    }
                }

                @Override
                public float regenScale(Player player) {
                    return 0.8F;
                }
            });

    public static final ElysiumTrinket[] ALL = {
            MOTE, CARRION_DRONE, LATTICE_SHARD, PURGE_DRONE,
    };
}
