package com.elysium.bestiary.entity;

import com.elysium.bestiary.entity.act.KinTraits;
import com.elysium.bestiary.registry.KinEntities;
import com.elysium.bestiary.rules.KindredRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import com.elysium.lib.sound.ElysiumVoices;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

/**
 * One class for all twenty-seven, driven by its row.
 *
 * <p>The same argument {@link BeastEntity} and {@link MythEntity} make: these
 * creatures differ in five numbers, one trait and one place, and twenty-seven
 * classes would be twenty-seven places for one bug. It matters more here than
 * anywhere else in the module, because this is the roster that grows by
 * variant — the shape that would have produced twenty-seven classes is exactly
 * the shape that produces four scorpions who are one scorpion.
 *
 * <h2>What this class knows and what it does not</h2>
 *
 * It knows how to find its own row, how to carry the three pieces of state a
 * trait might need, and how to hand itself to {@link KinTraits}. It does not
 * know what any trait does. That is deliberate: the day this class contains a
 * {@code switch} on {@code trait()} is the day there are two of them, and they
 * will disagree.
 */
public class KinEntity extends Monster {

    /** How far one notices you. */
    public static final double FOLLOWS_FOR = 26.0D;

    /** How long one stays under before it comes back up, in ticks. */
    public static final int STAYS_UNDER = 60;

    /** How many a caller may have out at once. */
    public static final int CALLS_UP_TO = 3;

    /** Whether this one is currently pretending to be something else. */
    private static final EntityDataAccessor<Boolean> DISGUISED =
            SynchedEntityData.defineId(KinEntity.class, EntityDataSerializers.BOOLEAN);

    private int sinceTrait;
    private int under;
    private int called;

    public KinEntity(EntityType<? extends KinEntity> type, Level level) {
        super(type, level);
    }

    /** The row this one is, by the name it was registered under. */
    public KindredRules.Kin creature() {
        String name = EntityType.getKey(getType()).getPath();
        KindredRules.Kin found = KindredRules.creature(name);
        return found == null ? KindredRules.KINDRED.get(0) : found;
    }

    /** What the game needs to know about one of these before it exists. */
    public static AttributeSupplier.Builder attributes(KindredRules.Kin row) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, row.health())
                .add(Attributes.ATTACK_DAMAGE, row.damage())
                .add(Attributes.MOVEMENT_SPEED, row.speed())
                .add(Attributes.FOLLOW_RANGE, FOLLOWS_FOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DISGUISED, false);
    }

    /**
     * What it does, which is one set of goals and not twenty-seven.
     *
     * <p>Everything here closes and fights. What differs between them is the
     * trait, and the trait is not a goal — it is a thing that happens on the
     * clock in {@link #aiStep} or on contact. That keeps the goal list one
     * thing instead of eleven, and keeps every difference between two kin in
     * the one file that is checked for naming them.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this,
                Player.class, true));
    }

    /** How long since this one last did the thing that makes it itself. */
    public int sinceTrait() {
        return sinceTrait;
    }

    /** Whether it is pretending to be something else at the moment. */
    public boolean disguised() {
        return entityData.get(DISGUISED);
    }

    /** Say so, which the client reads to decide whether to draw it at all. */
    public void setDisguised(boolean hiding) {
        entityData.set(DISGUISED, hiding);
    }

    /** Whether a player can currently see it, which three traits turn on. */
    public boolean hasVisiblePlayer() {
        Player near = level().getNearestPlayer(this, KinTraits.REACHES);
        return near != null && hasLineOfSight(near);
    }

    /** Go under, which is what a burrower does instead of being hit. */
    public void goUnder() {
        under = STAYS_UNDER;
        setInvulnerable(true);
        setInvisible(true);
    }

    /** Whether it is under at the moment. */
    public boolean under() {
        return under > 0;
    }

    /**
     * Call up one more of itself, up to a limit.
     *
     * <p>The limit is the whole reason a caller is a decision rather than a
     * defeat: without it, "kill it first" is not advice, it is the only move,
     * and a fight with one move is not a fight.
     */
    public void callOne() {
        if (called >= CALLS_UP_TO || level().isClientSide()) {
            return;
        }
        EntityType<?> mine = getType();
        Entity made = mine.create(level());
        if (made instanceof KinEntity fresh) {
            fresh.moveTo(getX() + (random.nextDouble() - 0.5D) * 3.0D, getY(),
                    getZ() + (random.nextDouble() - 0.5D) * 3.0D, getYRot(), 0.0F);
            fresh.called = CALLS_UP_TO;
            level().addFreshEntity(fresh);
            called++;
        }
    }

    /** Throw something at somebody, which is what a thrower does at range. */
    public void throwSomething(LivingEntity at) {
        if (level().isClientSide()) {
            return;
        }
        Snowball thrown = new Snowball(level(), this);
        double up = at.getEyeY() - 1.1D - thrown.getY();
        double along = at.getX() - getX();
        double across = at.getZ() - getZ();
        double flat = Math.sqrt(along * along + across * across);
        thrown.shoot(along, up + flat * 0.2D, across, 1.4F, 6.0F);
        level().addFreshEntity(thrown);
    }

    /** What it takes to get on the one whose answer is to get on it. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!creature().trait().equals(KindredRules.CARRIES)) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide() && getPassengers().isEmpty()) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        if (under > 0) {
            under--;
            if (under == 0) {
                setInvulnerable(false);
                setInvisible(false);
            }
        }
        sinceTrait++;
        if (sinceTrait >= KinTraits.BETWEEN) {
            sinceTrait = 0;
            KinTraits.happen(this, creature());
        }
    }

    /** And the four that land when it reaches you. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean landed = super.doHurtTarget(target);
        if (landed && target instanceof LivingEntity hurt) {
            KinTraits.onContact(this, hurt, creature());
        }
        return landed;
    }

    /**
     * What a young one is, for the three that have them.
     *
     * <p>A smaller one of the same row rather than a row of its own, which is
     * what stops "the babies" being three more creatures nothing holds to
     * anything.
     */
    public boolean young() {
        return creature().young() && isBaby();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ElysiumSinceTrait", sinceTrait);
        tag.putInt("ElysiumUnder", under);
        tag.putInt("ElysiumCalled", called);
        tag.putBoolean("ElysiumDisguised", disguised());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sinceTrait = tag.getInt("ElysiumSinceTrait");
        under = tag.getInt("ElysiumUnder");
        called = tag.getInt("ElysiumCalled");
        setDisguised(tag.getBoolean("ElysiumDisguised"));
    }

    /** Where one may stand, which is the game's own answer for everything hostile. */
    public static boolean canStandHere(EntityType<KinEntity> type, Level level,
                                       MobSpawnType why, BlockPos where,
                                       net.minecraft.util.RandomSource random) {
        return Monster.checkMonsterSpawnRules(type, (net.minecraft.world.level.ServerLevelAccessor)
                level, why, where, random);
    }

    /** The registry this one came out of, kept here so the renderer can ask. */
    public static int registered() {
        return KinEntities.ALL.size();
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return KindredRules.shapeOf(creature());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ElysiumVoices.ambient(voice(), super.getAmbientSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ElysiumVoices.hurt(voice(), super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ElysiumVoices.death(voice(), super.getDeathSound());
    }

    @Override
    public float getVoicePitch() {
        return ElysiumVoices.pitchOf(this);
    }
}
