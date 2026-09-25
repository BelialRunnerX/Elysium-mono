package com.elysium.bestiary.entity;

import com.elysium.bestiary.registry.SummonEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What a summoning ability calls up.
 *
 * <p>It fights what is nearby and then it goes, and the going is the point:
 * a summon that stayed would be a mob a player farms rather than an ability
 * with a cooldown, and a world with thirty of them in it is the performance
 * problem every ability system eventually has.
 */
public class ShadeEntity extends Monster {

    /** How long one stands before it goes out, in ticks. */
    public static final int LASTS = 600;

    public ShadeEntity(EntityType<? extends ShadeEntity> type, Level level) {
        super(type, level);
    }

    public ShadeEntity(Level level) {
        this(SummonEntities.SHADE.get(), level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, false));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        // It hunts what hunts you, and never you: a summon that turns on its
        // summoner is the one bug in this kind of ability nobody forgives.
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this,
                Monster.class, 10, true, false,
                other -> !(other instanceof ShadeEntity)));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LASTS) {
            discard();
        }
    }

    /** Nothing may pick one up, and nothing may breed one. */
    @Override
    public boolean removeWhenFarAway(double away) {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public LivingEntity getTarget() {
        return super.getTarget();
    }
}
