package com.elysium.bestiary.entity;

import com.elysium.bestiary.entity.act.CourtRoles;
import com.elysium.bestiary.rules.CourtRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
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

/**
 * One class for all fifty-eight, driven by its row.
 *
 * <p>The argument every other roster in this module makes, and the one place it
 * would have been most tempting to break: a mini-boss feels like it wants a
 * class of its own. It does not. What makes a head a head is three numbers and
 * a role, all of which {@link CourtRules} proves something about — and a
 * bespoke boss class is a boss whose numbers are its own, which is exactly the
 * arrangement in which the proof passes and the fight is a wall anyway.
 *
 * <h2>The second phase</h2>
 *
 * A head with two phases does not become a second entity. It crosses
 * {@link #TURNS_AT} of its health and the flag flips, which the renderer reads
 * and {@link CourtRoles} reads. That is deliberate: a phase two that is a
 * separate entity is a separate set of numbers, and the rule that says the two
 * phases must be answered differently would be about the first one only.
 */
public class CourtEntity extends Monster {

    /** How far one notices you. A head notices further; that is what a head is. */
    public static final double FOLLOWS_FOR = 28.0D;

    /** And how much further. */
    public static final double HEADS_SEE = 1.5D;

    /** The share of its health at which a two-phased head turns. */
    public static final float TURNS_AT = 0.5F;

    /** Whether this one has turned into its second phase. */
    private static final EntityDataAccessor<Boolean> TURNED =
            SynchedEntityData.defineId(CourtEntity.class, EntityDataSerializers.BOOLEAN);

    private int sinceRole;
    private int called;

    public CourtEntity(EntityType<? extends CourtEntity> type, Level level) {
        super(type, level);
    }

    /** The row this one is, by the name it was registered under. */
    public CourtRules.Member member() {
        String name = EntityType.getKey(getType()).getPath();
        CourtRules.Member found = CourtRules.member(name);
        return found == null ? CourtRules.MEMBERS.get(0) : found;
    }

    /** What the game needs to know about one of these before it exists. */
    public static AttributeSupplier.Builder attributes(CourtRules.Member row) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, row.health())
                .add(Attributes.ATTACK_DAMAGE, row.damage())
                .add(Attributes.MOVEMENT_SPEED, row.speed())
                .add(Attributes.FOLLOW_RANGE,
                        row.head() ? FOLLOWS_FOR * HEADS_SEE : FOLLOWS_FOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TURNED, false);
    }

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

    /** Whether it has crossed into its second phase. */
    public boolean turned() {
        return entityData.get(TURNED);
    }

    /** What answers it right now, which is the row's own and changes when it turns. */
    public String answeredBy() {
        CourtRules.Member row = member();
        return turned() && row.twoPhased() ? row.then() : row.first();
    }

    /** How many of its court this one has called up. */
    public int called() {
        return called;
    }

    /** Say that one more is standing, which is how the cap is kept. */
    public void counted() {
        called++;
    }

    /** How many more it may have, which is its row's own number and not this class's. */
    public int roomFor() {
        return Math.max(0, member().most() - called);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        CourtRules.Member row = member();
        // A head with two phases turns once, at half its health, and never
        // turns back. Turning back would let a player who had spent the second
        // answer be asked for the first one again.
        if (row.head() && row.twoPhased() && !turned()
                && getHealth() <= getMaxHealth() * TURNS_AT) {
            entityData.set(TURNED, true);
            CourtRoles.turns(this, row);
        }
        sinceRole++;
        if (sinceRole >= CourtRoles.BETWEEN) {
            sinceRole = 0;
            CourtRoles.happen(this, row);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean landed = super.doHurtTarget(target);
        if (landed && target instanceof LivingEntity hurt) {
            CourtRoles.onContact(this, hurt, member());
        }
        return landed;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ElysiumSinceRole", sinceRole);
        tag.putInt("ElysiumCalled", called);
        tag.putBoolean("ElysiumTurned", turned());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sinceRole = tag.getInt("ElysiumSinceRole");
        called = tag.getInt("ElysiumCalled");
        entityData.set(TURNED, tag.getBoolean("ElysiumTurned"));
    }
}
