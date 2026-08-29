package com.elysium.mobs.entity;

import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.mobs.ElysiumMobs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The six families, three to a side.
 *
 * <h2>What a family is</h2>
 *
 * A silhouette, a set of base attributes, and a way of fighting. Everything
 * else — how tough this particular one is, what it can do that the others
 * cannot — belongs to the sub-variant, which is why these six classes are
 * short and {@code ElysiumVariants} is long.
 *
 * <h2>The two sides fight differently, on purpose</h2>
 *
 * <b>The Unsworn</b> are people who were not counted: scavengers, chain-gang
 * reavers, and things that learned to move quietly. They are quick, fragile and
 * numerous, and their families overlap — a Scavenger and a Whisper are both
 * trying to reach you before you are ready.
 *
 * <b>The Empire</b> fields equipment. Drones hover and shoot, Lictors stand in
 * the way and do not move, Adepts make everything around them worse to fight.
 * They are slower, better armoured, and they work as a unit — which is the
 * whole reason the Adept's aura exists.
 *
 * A player should be able to tell which side a room belongs to from the
 * doorway, before anything has attacked them.
 */
public final class ElysiumFamilies {

    private ElysiumFamilies() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumMobs.MODID, path);
    }

    // ==================================================================
    // Family ids — the key sub-variants are registered against
    // ==================================================================

    public static final ResourceLocation SCAVENGER_ID = id("scavenger");
    public static final ResourceLocation REAVER_ID = id("reaver");
    public static final ResourceLocation WHISPER_ID = id("whisper");
    public static final ResourceLocation DRONE_ID = id("drone");
    public static final ResourceLocation LICTOR_ID = id("lictor");
    public static final ResourceLocation ADEPT_ID = id("adept");

    /** Every family id, in the order they are shown and generated. */
    public static final ResourceLocation[] ALL = {
            SCAVENGER_ID, REAVER_ID, WHISPER_ID, DRONE_ID, LICTOR_ID, ADEPT_ID};

    // ==================================================================
    // Unsworn
    // ==================================================================

    /**
     * Scavenger — fast, frail, and never alone.
     *
     * The weakest thing in the mod, and the one a player meets most. Its job is
     * to make a room feel occupied and to punish standing still; a single one
     * is barely a fight, which is the point.
     */
    public static class Scavenger extends ElysiumMob {

        public Scavenger(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 12.0D)
                    .add(Attributes.ATTACK_DAMAGE, 2.5D)
                    .add(Attributes.MOVEMENT_SPEED, 0.32D)
                    .add(Attributes.FOLLOW_RANGE, 24.0D)
                    .add(Attributes.ARMOR, 0.0D);
        }

        @Override
        protected void registerGoals() {
            standardGoals(this, 1.25D);
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.UNSWORN;
        }

        @Override
        public ResourceLocation getFamilyId() {
            return SCAVENGER_ID;
        }
    }

    /**
     * Reaver — slow, heavy, and difficult to move.
     *
     * The Unsworn's answer to armour: no technique at all, and enough mass that
     * technique stops mattering. Where a Scavenger wants you distracted, a
     * Reaver wants you cornered.
     */
    public static class Reaver extends ElysiumMob {

        public Reaver(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 34.0D)
                    .add(Attributes.ATTACK_DAMAGE, 6.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.21D)
                    .add(Attributes.FOLLOW_RANGE, 20.0D)
                    .add(Attributes.ARMOR, 4.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
        }

        @Override
        protected void registerGoals() {
            standardGoals(this, 1.0D);
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.UNSWORN;
        }

        @Override
        public ResourceLocation getFamilyId() {
            return REAVER_ID;
        }
    }

    /**
     * Whisper — quiet, quick, and gone again.
     *
     * Low health and high damage, so it is dangerous exactly until it is
     * noticed. The family that makes a player check corners.
     */
    public static class Whisper extends ElysiumMob {

        public Whisper(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 16.0D)
                    .add(Attributes.ATTACK_DAMAGE, 7.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.34D)
                    .add(Attributes.FOLLOW_RANGE, 32.0D)
                    .add(Attributes.ARMOR, 1.0D);
        }

        @Override
        protected void registerGoals() {
            standardGoals(this, 1.35D);
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.UNSWORN;
        }

        @Override
        public ResourceLocation getFamilyId() {
            return WHISPER_ID;
        }
    }

    // ==================================================================
    // Imperial
    // ==================================================================

    /**
     * Drone — sanctioned equipment, not a person.
     *
     * Light, fast and deliberately not tough: a drone is a thing the Empire
     * spends rather than a soldier it deploys, and it reads that way in a
     * fight.
     */
    public static class Drone extends ElysiumMob {

        public Drone(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 18.0D)
                    .add(Attributes.ATTACK_DAMAGE, 4.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.30D)
                    .add(Attributes.FOLLOW_RANGE, 28.0D)
                    .add(Attributes.ARMOR, 6.0D);
        }

        @Override
        protected void registerGoals() {
            standardGoals(this, 1.2D);
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.EMPIRE;
        }

        @Override
        public ResourceLocation getFamilyId() {
            return DRONE_ID;
        }
    }

    /**
     * Lictor — the Code, standing in a doorway.
     *
     * The most armoured thing in the mod and the slowest. A Lictor is not
     * chasing anybody; it is making a room expensive to cross.
     */
    public static class Lictor extends ElysiumMob {

        public Lictor(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 40.0D)
                    .add(Attributes.ATTACK_DAMAGE, 7.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.23D)
                    .add(Attributes.FOLLOW_RANGE, 22.0D)
                    .add(Attributes.ARMOR, 12.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D);
        }

        @Override
        protected void registerGoals() {
            standardGoals(this, 1.0D);
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.EMPIRE;
        }

        @Override
        public ResourceLocation getFamilyId() {
            return LICTOR_ID;
        }
    }

    /**
     * Adept — the reason the others are worse.
     *
     * Weak alone and the correct thing to kill first, which is exactly the
     * decision an Adept exists to force: reach it through a Lictor, or fight
     * everything else at a disadvantage.
     */
    public static class Adept extends ElysiumMob {

        public Adept(EntityType<? extends Monster> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 20.0D)
                    .add(Attributes.ATTACK_DAMAGE, 3.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.26D)
                    .add(Attributes.FOLLOW_RANGE, 30.0D)
                    .add(Attributes.ARMOR, 3.0D);
        }

        @Override
        protected void registerGoals() {
            standardGoals(this, 1.1D);
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.EMPIRE;
        }

        @Override
        public ResourceLocation getFamilyId() {
            return ADEPT_ID;
        }
    }

    // ==================================================================

    /**
     * The goal set every family shares.
     *
     * Written once because all six do the same four things — swim, chase,
     * wander, look around — and differ only in how fast they close. A family
     * that needs genuinely different behaviour should override
     * {@code registerGoals} rather than adding a parameter here; the moment
     * this method grows a second knob it stops being shared code and becomes a
     * configuration language.
     */
    private static void standardGoals(ElysiumMob mob, double chaseSpeed) {
        mob.goalSelector.addGoal(0, new FloatGoal(mob));
        mob.goalSelector.addGoal(2, new MeleeAttackGoal(mob, chaseSpeed, false));
        mob.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(mob, 0.8D));
        mob.goalSelector.addGoal(8, new LookAtPlayerGoal(mob, Player.class, 12.0F));
        mob.goalSelector.addGoal(8, new RandomLookAroundGoal(mob));

        mob.targetSelector.addGoal(1, new HurtByTargetGoal(mob));
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Player.class, true));
    }
}
