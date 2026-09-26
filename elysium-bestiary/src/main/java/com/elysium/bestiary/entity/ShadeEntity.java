package com.elysium.bestiary.entity;

import com.elysium.bestiary.registry.SummonEntities;
import com.elysium.bestiary.rules.SummonRules;
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

    /**
     * How long one stands before it goes out, in ticks.
     *
     * <p>Asked of the rules, which hold it strictly under the cooldown of the
     * ability that calls one up. A summon that outlives its own cooldown is a
     * summon a player can have two of, and then three, and the cost stops being
     * a limit — invisible in every screenshot, and never reported by the player
     * who finds it.
     */
    public static final int LASTS = SummonRules.summon(SummonRules.SHADE).lasts();

    public ShadeEntity(EntityType<? extends ShadeEntity> type, Level level) {
        super(type, level);
    }

    public ShadeEntity(Level level) {
        this(SummonEntities.SHADE.get(), level);
    }

    /**
     * What it is worth, out of the rules.
     *
     * <p>Every one of these four is banded there against the beasts it was
     * called to help with: under the weakest of them on damage, so it helps
     * rather than replaces, and over it on health, so it does not die on
     * contact. Typing them here is how the first draft ended up with a shade
     * that out-hit every beast in the game and nothing said so.
     */
    public static AttributeSupplier.Builder attributes() {
        SummonRules.Summon shade = SummonRules.summon(SummonRules.SHADE);
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, shade.health())
                .add(Attributes.MOVEMENT_SPEED, SummonRules.SHADE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, shade.damage())
                .add(Attributes.FOLLOW_RANGE, SummonRules.SHADE_NOTICES);
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
