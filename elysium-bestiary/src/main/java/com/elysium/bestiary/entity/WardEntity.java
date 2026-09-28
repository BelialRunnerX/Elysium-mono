package com.elysium.bestiary.entity;

import com.elysium.bestiary.entity.act.WardActs;
import com.elysium.bestiary.rules.WardRules;
import net.minecraft.world.damagesource.DamageSource;
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
import com.elysium.lib.sound.ElysiumVoices;
import net.minecraft.sounds.SoundEvent;

/**
 * One class for all thirteen, driven by its row.
 *
 * <p>What is different here from every other roster in this module is where the
 * row is read: not once when the creature is built, but every time it lands a
 * hit, because what it takes off depends on what the thing it hit is carrying.
 *
 * <p>The attack attribute is therefore the <em>unwarded</em> number — the worst
 * case — and {@link #doHurtTarget} replaces it with whichever of the row's two
 * numbers applies. Setting the attribute to something in between would be a
 * third number nothing proved anything about, which is exactly the arrangement
 * the rules exist to stop.
 */
public class WardEntity extends Monster {

    /** How far one notices you. */
    public static final double FOLLOWS_FOR = 24.0D;

    public WardEntity(EntityType<? extends WardEntity> type, Level level) {
        super(type, level);
    }

    /** The row this one is, by the name it was registered under. */
    public WardRules.Warded warded() {
        String name = EntityType.getKey(getType()).getPath();
        WardRules.Warded found = WardRules.warded(name);
        return found == null ? WardRules.WARDED.get(0) : found;
    }

    /**
     * What the game needs to know about one of these before it exists.
     *
     * <p>The attack value is the row's {@code bite} — what it does to somebody
     * with nothing on. That is the worst case, and starting from the worst case
     * means a bug in the warding makes the fight harder rather than free.
     */
    public static AttributeSupplier.Builder attributes(WardRules.Warded row) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, row.health())
                .add(Attributes.ATTACK_DAMAGE, row.bite())
                .add(Attributes.MOVEMENT_SPEED, row.speed())
                .add(Attributes.FOLLOW_RANGE, FOLLOWS_FOR);
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

    /**
     * The hit, which is the whole feature.
     *
     * <p>Which of the row's two numbers applies is asked of {@link WardActs},
     * and the amount is the row's own either way. A number worked out here
     * would leave the proof that every creature lands between a third and four
     * fifths passing while the game did something else.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity hurt) || level().isClientSide()) {
            return super.doHurtTarget(target);
        }
        WardRules.Warded row = warded();
        float takes = WardActs.bites(this, hurt, row);
        DamageSource from = damageSources().mobAttack(this);
        boolean landed = hurt.hurt(from, takes);
        if (landed) {
            setLastHurtMob(hurt);
        }
        return landed;
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return WardRules.shapeOf(warded());
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
