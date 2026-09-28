package com.elysium.bestiary.entity;

import com.elysium.bestiary.entity.act.MythBanes;
import com.elysium.bestiary.rules.MythRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
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
 * One class for all eight myths, driven by its row.
 *
 * <p>The same argument {@link BeastEntity} makes and for the same reason:
 * eight creatures differ in six numbers and one bane, and eight classes would
 * be eight places for one bug. What is different here is that the bane is the
 * point — so it lives in {@link MythBanes}, one branch per way past armour and
 * no creature named in any of them.
 *
 * <h2>The one that runs</h2>
 *
 * Two of the eight will not stand and finish a fight. That is not a different
 * class either: it is a goal added or not, decided by the row, and the rules
 * hold anything that runs to being worth less than anything on the same rung
 * that stands.
 */
public class MythEntity extends Monster {

    /** How far one notices you. */
    public static final double FOLLOWS_FOR = 24.0D;

    /** How far a runner keeps between you and it. */
    public static final float KEEPS_AWAY = 10.0F;

    private int sinceBane;

    public MythEntity(EntityType<? extends MythEntity> type, Level level) {
        super(type, level);
    }

    /** The row this one is, by the name it was registered under. */
    public MythRules.Myth myth() {
        String name = EntityType.getKey(getType()).getPath();
        MythRules.Myth found = MythRules.myth(name);
        return found == null ? MythRules.MYTHS.get(0) : found;
    }

    /** What the game needs to know about one of these before it exists. */
    public static AttributeSupplier.Builder attributes(MythRules.Myth myth) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, myth.health())
                .add(Attributes.ATTACK_DAMAGE, myth.damage())
                .add(Attributes.MOVEMENT_SPEED, myth.speed())
                .add(Attributes.FOLLOW_RANGE, FOLLOWS_FOR);
    }

    /**
     * What it does, which is two sets of goals and not eight.
     *
     * <p>Something that stands closes and fights. Something that runs keeps its
     * distance and takes what it came for. Which of the two is the row's own
     * {@code flees}, so a ninth myth is a line in the rules and nothing here
     * changes.
     */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (myth().flees()) {
            goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class,
                    KEEPS_AWAY, 1.3D, 1.5D));
        }
        if (!myth().flees()) {
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
            targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this,
                    Player.class, true));
        }
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** How long since this one last did the thing that makes it a myth. */
    public int sinceBane() {
        return sinceBane;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }
        sinceBane++;
        if (sinceBane >= MythBanes.BETWEEN) {
            sinceBane = 0;
            MythBanes.happen(this, myth());
        }
    }

    /** And on contact, which is when most of them land. */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean landed = super.doHurtTarget(target);
        if (landed && target instanceof LivingEntity hurt) {
            MythBanes.onContact(this, hurt, myth());
        }
        return landed;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ElysiumSinceBane", sinceBane);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sinceBane = tag.getInt("ElysiumSinceBane");
    }

    // --- what it says (elysium-lib sound table, POLISH.md P5) ---------------

    /** The voice this one speaks with, a name in ElysiumSoundRules. */
    public String voice() {
        return myth().form();
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
