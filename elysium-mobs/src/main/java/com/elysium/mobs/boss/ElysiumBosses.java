package com.elysium.mobs.boss;

import com.elysium.lib.entity.ElysiumFaction;
import com.elysium.mobs.ElysiumMobs;
import com.elysium.mobs.entity.ElysiumFamilies;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * One boss for each side.
 *
 * <h2>They are not the same fight with different textures</h2>
 *
 * Each boss is the argument its faction makes, taken to its conclusion.
 *
 * <b>The Choir</b> is what the Unsworn are: a great many people the Empire did
 * not count, and the answer to being ignored is to be too many to ignore. It
 * fights by refusing to be a single target — as it loses health it calls more
 * of them, and the room fills.
 *
 * <b>The Praetor</b> is what the Empire is: one thing, sanctioned, correct, and
 * extremely difficult to get through. It never calls for help. As it loses
 * health it stops defending and starts answering, which is the Code's position
 * on being struck stated as a fight.
 *
 * A player should be able to say which one they fought without naming it.
 */
public final class ElysiumBosses {

    private ElysiumBosses() {
    }

    public static final ResourceLocation CHOIR_ID =
            ResourceLocation.fromNamespaceAndPath(ElysiumMobs.MODID, "choir");
    public static final ResourceLocation PRAETOR_ID =
            ResourceLocation.fromNamespaceAndPath(ElysiumMobs.MODID, "praetor");

    // ==================================================================
    // The Choir of the Uncounted — Unsworn
    // ==================================================================

    /**
     * Three phases, each one louder.
     *
     * The Choir's whole mechanic is that killing it is not the same as
     * stopping it: every phase it summons more Unsworn, so a player who ignores
     * the room to focus the boss is fighting the room by phase three.
     */
    public static class Choir extends ElysiumBoss {

        public Choir(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.RED);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 120.0D)
                    .add(Attributes.ATTACK_DAMAGE, 9.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.27D)
                    .add(Attributes.FOLLOW_RANGE, 40.0D)
                    .add(Attributes.ARMOR, 6.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, false));
            goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
            targetSelector.addGoal(1,
                    new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
            targetSelector.addGoal(2,
                    new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                            this, Player.class, true));
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.UNSWORN;
        }

        @Override
        public ResourceLocation getFamilyId() {
            // Wears Scavenger variants: the Choir is made of them, so its
            // sub-variant deciding its stats and ability is the fiction and the
            // mechanic agreeing for once.
            return ElysiumFamilies.SCAVENGER_ID;
        }

        @Override
        protected int phaseCount() {
            return 3;
        }

        @Override
        protected Component getBossName() {
            return Component.translatable("elysiummobs.boss.choir")
                    .withStyle(ChatFormatting.DARK_RED);
        }

        @Override
        protected void onPhaseStart(int phase) {
            if (phase == 0 || level().isClientSide()) {
                return;
            }
            // More of them, and faster, every phase.
            summon(phase * 2);
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                    20 * 60 * 5, phase - 1, false, false));
        }

        @Override
        protected void onBossTick(int phase) {
            // A slow trickle between phases, capped by the crowd already
            // present - so a long fight fills the room and a short one does
            // not, without the count running away while the player is winning.
            if (phase > 0 && getRandom().nextFloat() < 0.10F * phase && crowd() < 4 + phase * 2) {
                summon(1);
            }
        }

        private int crowd() {
            return level().getEntitiesOfClass(Mob.class,
                    new AABB(blockPosition()).inflate(16.0D)).size();
        }

        private void summon(int count) {
            if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Mob spawned = com.elysium.lib.entity.ElysiumBestiary.spawn(
                        server, blockPosition(), ElysiumFaction.UNSWORN,
                        com.elysium.lib.entity.ElysiumBestiary.Role.GRUNT, getRandom());
                if (spawned == null) {
                    // No grunts registered - the Choir is still a fight, just a
                    // lonelier one. Not an error: another pack may have removed
                    // them, and a boss that crashed rather than fighting alone
                    // would be far worse.
                    return;
                }
                spawned.moveTo(getX() + getRandom().nextDouble() * 4.0D - 2.0D, getY(),
                        getZ() + getRandom().nextDouble() * 4.0D - 2.0D,
                        getRandom().nextFloat() * 360.0F, 0.0F);
                spawned.setTarget(getTarget());
                server.addFreshEntity(spawned);
            }
        }
    }

    // ==================================================================
    // The Praetor — Imperial
    // ==================================================================

    /**
     * Two phases: defending, then answering.
     *
     * The Praetor is the mod's argument about the Empire compressed into one
     * fight. In phase one it is a wall — armoured, shielded, slow to hurt. In
     * phase two the shield is gone and it returns a share of everything it
     * takes, which is the same idea as the Imperial racial passive and should
     * feel like it.
     */
    public static class Praetor extends ElysiumBoss {

        public Praetor(EntityType<? extends Monster> type, Level level) {
            super(type, level, BossEvent.BossBarColor.PURPLE);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 150.0D)
                    .add(Attributes.ATTACK_DAMAGE, 12.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.24D)
                    .add(Attributes.FOLLOW_RANGE, 36.0D)
                    .add(Attributes.ARMOR, 16.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
            goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
            targetSelector.addGoal(1,
                    new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(this));
            targetSelector.addGoal(2,
                    new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                            this, Player.class, true));
        }

        @Override
        public ElysiumFaction getFaction() {
            return ElysiumFaction.EMPIRE;
        }

        @Override
        public ResourceLocation getFamilyId() {
            return ElysiumFamilies.LICTOR_ID;
        }

        @Override
        protected int phaseCount() {
            return 2;
        }

        @Override
        protected Component getBossName() {
            return Component.translatable("elysiummobs.boss.praetor")
                    .withStyle(ChatFormatting.GOLD);
        }

        @Override
        protected void onPhaseStart(int phase) {
            if (level().isClientSide()) {
                return;
            }
            if (phase == 0) {
                setAbsorptionAmount(getMaxHealth() * 0.25F);
                return;
            }
            // The shield goes, and it stops holding back.
            setAbsorptionAmount(0.0F);
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
                    20 * 60 * 5, 1, false, true));
        }

        @Override
        protected void onBossTick(int phase) {
            if (phase == 0) {
                // Rebuilds its field while it still has one.
                float cap = getMaxHealth() * 0.25F;
                if (getAbsorptionAmount() < cap) {
                    setAbsorptionAmount(Math.min(cap, getAbsorptionAmount() + cap * 0.02F));
                }
            }
        }

        /**
         * Phase two returns a share of every blow.
         *
         * A share rather than a flat amount, so it scales with how hard the
         * player hits — the Code's position is that what you dealt is what you
         * are answered with, and a flat number would make a careful player and
         * a reckless one take the same punishment.
         */
        @Override
        public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
            boolean hurt = super.hurt(source, amount);
            if (hurt && !level().isClientSide() && getPhase() > 0
                    && source.getEntity() instanceof LivingEntity attacker) {
                attacker.hurt(damageSources().magic(), amount * 0.35F);
            }
            return hurt;
        }
    }

    /** The two boss family ids, for the generator and the validator. */
    public static final ResourceLocation[] ALL = {CHOIR_ID, PRAETOR_ID};
}
