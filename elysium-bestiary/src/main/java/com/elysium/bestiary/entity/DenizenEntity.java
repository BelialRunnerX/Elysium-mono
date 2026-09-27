package com.elysium.bestiary.entity;

import com.elysium.bestiary.rules.DenizenRules;
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
 * One class for all twenty-eight, driven by its row.
 *
 * <p>The plainest of this module's entity classes, and deliberately so. What
 * makes the Vale's roster interesting is not what any one of its creatures
 * does, it is <em>where</em> each of them is — so the interesting part is the
 * spawn rule the generator writes, and this class is the three attributes the
 * row already carries.
 *
 * <p>The one thing it does have is {@link #ringsOut}, which the renderer reads.
 * How far out a player has walked is the whole claim of this roster, and a
 * player who cannot tell has to work it out from how hard the fight was —
 * which is the coin flip the rules exist to remove.
 */
public class DenizenEntity extends Monster {

    /** How far one notices you. */
    public static final double FOLLOWS_FOR = 24.0D;

    public DenizenEntity(EntityType<? extends DenizenEntity> type, Level level) {
        super(type, level);
    }

    /** The row this one is, by the name it was registered under. */
    public DenizenRules.Denizen denizen() {
        String name = EntityType.getKey(getType()).getPath();
        DenizenRules.Denizen found = DenizenRules.denizen(name);
        return found == null ? DenizenRules.DENIZENS.get(0) : found;
    }

    /**
     * What the game needs to know about one of these before it exists.
     *
     * <p>Three numbers off the row and a follow range this class decides, which
     * is the one number here that is not a row's own — because how far a thing
     * notices you is a property of the dimension rather than of the creature,
     * and twenty-eight copies of it would be twenty-eight chances to disagree.
     */
    public static AttributeSupplier.Builder attributes(DenizenRules.Denizen row) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, row.health())
                .add(Attributes.ATTACK_DAMAGE, row.damage())
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

    /** How far out this one lives, which is what the renderer draws. */
    public int ringsOut() {
        return denizen().ring();
    }
}
