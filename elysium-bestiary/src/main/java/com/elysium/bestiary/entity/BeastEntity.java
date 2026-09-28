package com.elysium.bestiary.entity;

import com.elysium.bestiary.rules.BeastRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
import com.elysium.lib.sound.ElysiumVoices;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

/**
 * One class for everything in the dark.
 *
 * <h2>Why there is one entity class and not nine</h2>
 *
 * Because nine beasts differ in eleven numbers and a set of goals, and nine
 * classes would be nine places for the same bug — and a tenth beast would be a
 * tenth file rather than a line in {@link BeastRules#BEASTS}. Which family a
 * beast belongs to decides how it behaves; everything else it is comes out of
 * its own row.
 *
 * <h2>And why the numbers are looked up rather than passed in</h2>
 *
 * An entity is rebuilt from its type every time a chunk loads, and a
 * constructor argument would have to be stored and read back. Its registered
 * name <em>is</em> its row, so there is nothing to store and nothing that can
 * be stored wrong.
 */
public class BeastEntity extends Monster {

    /** How far a beast will follow you before it gives up. */
    public static final double FOLLOWS_FOR = 24.0D;

    /** Whether this one came off another of its kind rather than out of the dark. */
    private boolean piece;

    /** How many of its family it has called up and not outlived. */
    private int called;

    /** Ticks until it may do what it does again. */
    private int waits;

    public BeastEntity(EntityType<? extends BeastEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.xpReward = BeastRules.earned(beast()).experience();
    }

    /** Whether this one is a piece of something rather than a whole one. */
    public boolean piece() {
        return piece;
    }

    /**
     * Say so, and become half of what it came off.
     *
     * <p>Both halves of that matter: a piece that is a whole beast is a
     * bestiary where one mob is worth two, and a piece that can itself come
     * apart is a room that fills up until the server stops.
     */
    public void becomeAPiece() {
        piece = true;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(
                Math.max(1.0D, beast().health() / 2.0D));
        setHealth(getMaxHealth());
        this.xpReward = 0;
    }

    /** How many of its family it has called up. */
    public int called() {
        return called;
    }

    /** One more. */
    public void calledOneMore() {
        called++;
    }

    /** The row this beast is, by the name it was registered under. */
    public BeastRules.Beast beast() {
        String name = EntityType.getKey(getType()).getPath();
        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            if (beast.id().equals(name)) {
                return beast;
            }
        }
        return BeastRules.BEASTS.get(0);
    }

    /** What the game needs to know about one of these before it exists. */
    public static AttributeSupplier.Builder attributes(BeastRules.Beast beast) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, beast.health())
                .add(Attributes.ATTACK_DAMAGE, beast.damage())
                .add(Attributes.MOVEMENT_SPEED, beast.speed())
                .add(Attributes.ARMOR, beast.armour())
                .add(Attributes.FOLLOW_RANGE, FOLLOWS_FOR)
                .add(Attributes.KNOCKBACK_RESISTANCE,
                        BeastRules.BRUTE.equals(beast.family()) ? 0.6D : 0.0D);
    }

    /**
     * How each family behaves, and nothing else differs.
     *
     * <p>A prowler runs at you, a brute walks at you and will not be pushed,
     * and a wight does neither in daylight. Three sets of goals and nine
     * beasts, because a bestiary where every mob has its own bespoke AI is a
     * bestiary nobody can predict — and being able to predict a mob is what
     * makes fighting it a decision rather than a dice roll.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, chaseSpeed(), false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class,
                true));
    }

    /** How much faster than its stroll a beast moves when it has seen you. */
    private double chaseSpeed() {
        return switch (beast().family()) {
            case BeastRules.PROWLER -> 1.4D;
            case BeastRules.BRUTE -> 1.0D;
            default -> 1.15D;
        };
    }

    /**
     * Wights burn in the sun, and nothing else does.
     *
     * <p>Which is the one thing that makes a family more than a set of
     * numbers: a player who has met one knows where to be at dawn.
     */
    @Override
    public void aiStep() {
        if (BeastRules.WIGHT.equals(beast().family()) && isSunBurnTick()
                && !level().isClientSide) {
            setRemainingFireTicks(8 * 20);
        }
        // What it does, on its own clock. The clock is in the rules: a wait
        // typed here would be a second set of dials beside the first, and the
        // two would disagree the first time anybody edited one.
        if (!level().isClientSide) {
            if (waits > 0) {
                waits--;
            } else if (com.elysium.bestiary.entity.act.BeastActs.perform(this,
                    getTarget())) {
                waits = com.elysium.bestiary.rules.BehaviourRules.waitsOf(beast(),
                        BeastRules.BEASTS);
            }
        }
        super.aiStep();
    }

    /**
     * And what it does when it dies, which only one of them does.
     *
     * <p>Here rather than in {@code aiStep} because a thing that happens on
     * death cannot happen on a clock, and the executor has to be given the
     * chance whatever the beast is — asking "is this the one that splits?"
     * here would be the branch per creature this module is built to avoid.
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        super.die(cause);
        if (!level().isClientSide) {
            com.elysium.bestiary.entity.act.BeastActs.perform(this, getTarget());
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ElysiumPiece", piece);
        tag.putInt("ElysiumCalled", called);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        piece = tag.getBoolean("ElysiumPiece");
        called = tag.getInt("ElysiumCalled");
    }

    /**
     * What killing it gives, from its own row.
     *
     * <p>The game asks for a <em>base</em> reward and then applies its own
     * modifiers; overriding that rather than the final number is what lets
     * looting, difficulty and everything else go on working.
     */
    @Override
    protected int getBaseExperienceReward() {
        return BeastRules.earned(beast()).experience();
    }

    @Override
    public boolean shouldDropExperience() {
        return true;
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && BeastRules.BRUTE.equals(beast().family())
                && target instanceof LivingEntity struck) {
            // A brute hits like a door. The knockback is the family, not a
            // number of its own — a brute that did not throw you is a slow
            // prowler.
            struck.knockback(0.8D, getX() - struck.getX(), getZ() - struck.getZ());
        }
        return hit;
    }

    /** So a beast is the size its row says, not the size of the last one. */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(
            net.minecraft.world.entity.Pose pose) {
        BeastRules.Beast beast = beast();
        return net.minecraft.world.entity.EntityDimensions.scalable(beast.width(),
                beast.height());
    }

    /** Nothing here is a baby, so nothing here shrinks. */
    @Override
    public boolean isBaby() {
        return false;
    }

    /**
     * Where one may appear: the game's own monster rule and nothing else.
     *
     * <p>Inventing a spawn rule is how a mod ends up in somebody's lit base,
     * and there is no version of that which is a good surprise.
     */
    /**
     * Whether one of these may stand here.
     *
     * <p>The game's own monster rule, and then the beast's own haunt: the
     * light it tolerates and the height it lives at. Both used to be nowhere
     * — every beast spawned wherever a zombie would, so a bestiary of nine
     * was nine things in one place, and a band written into a table was a
     * band nothing read.
     */
    public static boolean canSpawn(EntityType<? extends Monster> type,
                                   net.minecraft.world.level.ServerLevelAccessor level,
                                   net.minecraft.world.entity.MobSpawnType reason,
                                   net.minecraft.core.BlockPos pos,
                                   net.minecraft.util.RandomSource random) {
        if (!Monster.checkMonsterSpawnRules(type, level, reason, pos, random)) {
            return false;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(type).getPath();
        com.elysium.bestiary.rules.HauntRules.Haunt haunt =
                com.elysium.bestiary.rules.HauntRules.haunt(id);
        if (haunt == null) {
            return true;
        }
        if (pos.getY() < haunt.lowest() || pos.getY() > haunt.highest()) {
            return false;
        }
        int light = level.getMaxLocalRawBrightness(pos);
        return light >= haunt.darkest() && light <= haunt.brightest();
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return beast().family();
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
