package com.elysium.bestiary.entity.act;

import com.elysium.bestiary.entity.MythEntity;
import com.elysium.bestiary.rules.MythRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The one place a myth's way past armour happens.
 *
 * <h2>One branch per bane, and no creature named</h2>
 *
 * The same rule {@link BeastActs} follows: a branch per <em>way past armour</em>
 * rather than per creature. Eight branches is a thing somebody can read in one
 * sitting; eight classes is eight places for the same bug, and a ninth myth is
 * a ninth file.
 *
 * <p>No myth is named anywhere below. If one ever is, the module has started
 * growing a branch per creature and the whole roster will follow it there —
 * which is why {@code validate.py} reads this file and fails if it finds one.
 *
 * <h2>Why these are effects and not damage</h2>
 *
 * Because the whole point of a bane is that more armour is not the answer.
 * Every branch here either gives the player something to solve or takes
 * something armour does not protect — and none of them is simply a bigger hit,
 * which is what the beasts already do better.
 */
public final class MythBanes {

    private MythBanes() {
    }

    /** How long between one of these and the next, in ticks. */
    public static final int BETWEEN = 100;

    /** How far a bane that does not need contact reaches. */
    public static final double REACHES = 8.0D;

    /** How long an effect one of these leaves lasts, in ticks. */
    public static final int LINGERS = 140;

    /** How much of a health bar a drain takes and keeps. */
    public static final float DRAINS = 2.0F;

    /** How many of a kind stand together before the numbers stop counting. */
    public static final int A_CROWD = 4;

    /**
     * The bane that happens on its own clock.
     *
     * <p>Handed the row rather than reading it, so a control can hand one
     * built to be caught.
     */
    public static void happen(MythEntity self, MythRules.Myth myth) {
        Level level = self.level();
        switch (myth.bane()) {
            case MythRules.PETRIFY -> {
                // What looks at it slows to nothing. Armour does not answer
                // being unable to step back.
                for (Player near : nearby(self)) {
                    if (looking(near, self)) {
                        near.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                                LINGERS, 3));
                        near.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                                LINGERS, 2));
                    }
                }
            }
            case MythRules.BREATH -> {
                // It goes through what you are holding up.
                for (Player near : nearby(self)) {
                    if (self.hasLineOfSight(near)) {
                        near.igniteForSeconds(4);
                        near.stopUsingItem();
                    }
                }
            }
            case MythRules.NUMBERS -> {
                // Standing together is the danger, so standing together is
                // what it is paid for.
                int together = kin(self);
                if (together >= 2) {
                    self.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                            LINGERS, Math.min(together, A_CROWD) - 2));
                }
            }
            case MythRules.HEIGHT -> {
                // It goes where you are not, which is up.
                if (self.getTarget() != null && self.onGround()) {
                    self.setDeltaMovement(self.getDeltaMovement().add(0.0D, 0.55D, 0.0D));
                    self.hasImpulse = true;
                }
            }
            case MythRules.SPORES -> {
                // What it leaves behind grows, and standing in it is the cost
                // of taking your time.
                for (Player near : nearby(self)) {
                    near.addEffect(new MobEffectInstance(MobEffects.CONFUSION, LINGERS, 0));
                }
            }
            case MythRules.PLATING -> {
                // Nothing but the right metal marks it, which is a fight you
                // win by having prepared rather than by swinging harder.
                self.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                        LINGERS, 1));
            }
            case MythRules.DRAIN, MythRules.THIEVERY -> {
                // Both of these land on contact rather than on a clock.
            }
            default -> {
            }
        }
    }

    /**
     * The bane that lands when it hits you.
     *
     * <p>Kept apart from the one above because the two happen at different
     * moments and a single method holding both would have to be given a reason
     * for which — and "which one the caller wanted" is not a reason a validator
     * can check.
     */
    public static void onContact(MythEntity self, LivingEntity hurt,
                                 MythRules.Myth myth) {
        switch (myth.bane()) {
            case MythRules.DRAIN -> {
                // It takes the top off the bar and keeps it, which healing
                // does not give back.
                AttributeInstance most = hurt.getAttribute(Attributes.MAX_HEALTH);
                if (most != null && most.getBaseValue() > DRAINS * 2.0F) {
                    most.setBaseValue(most.getBaseValue() - DRAINS);
                    hurt.setHealth(Math.min(hurt.getHealth(), hurt.getMaxHealth()));
                    self.heal(DRAINS);
                }
            }
            case MythRules.THIEVERY -> {
                // It takes what it came for and goes. Nothing is destroyed:
                // what it took is on it, and killing it gets it back.
                if (hurt instanceof Player robbed && !robbed.getInventory().isEmpty()) {
                    net.minecraft.world.item.ItemStack took =
                            robbed.getInventory().removeItem(
                                    robbed.getInventory().selected, 1);
                    if (!took.isEmpty()) {
                        self.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                                took);
                        self.setGuaranteedDrop(
                                net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Everybody near enough to be reached. */
    private static List<Player> nearby(MythEntity self) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(REACHES);
        return self.level().getEntitiesOfClass(Player.class, around,
                player -> !player.isSpectator() && !player.isCreative());
    }

    /** Whether that one is looking at this one. */
    private static boolean looking(Player who, Entity at) {
        return who.getLookAngle().normalize().dot(
                at.position().subtract(who.getEyePosition()).normalize()) > 0.6D;
    }

    /** How many of its own kind are standing with it. */
    private static int kin(MythEntity self) {
        AABB around = new AABB(BlockPos.containing(self.position())).inflate(REACHES);
        return self.level().getEntitiesOfClass(MythEntity.class, around,
                other -> other != self
                        && other.myth().bane().equals(self.myth().bane())).size();
    }
}
