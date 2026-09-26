package com.elysium.bestiary.entity;

import com.elysium.bestiary.registry.SummonEntities;
import com.elysium.bestiary.rules.SummonRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * What a thrown ability throws.
 *
 * <p>One entity for every ability of that shape rather than one per ability,
 * because what differs between a hunter's mark and a stormcall is how many
 * there are and how hard they hit, and both of those come out of
 * {@code ElysiumAbilityShapes} rather than out of a class. A second class
 * would be a second place for the same three lines to drift.
 */
public class RelicBoltEntity extends AbstractArrow {

    public RelicBoltEntity(EntityType<? extends RelicBoltEntity> type, Level level) {
        super(type, level);
        setBaseDamage(SummonRules.summon(SummonRules.BOLT).damage());
    }

    public RelicBoltEntity(Level level, LivingEntity owner) {
        super(SummonEntities.BOLT.get(), owner, level, ItemStack.EMPTY, null);
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }

    /**
     * It does not stick in the ground and it does not lie there.
     *
     * <p>A bolt a player can pick up is a bolt a player farms, and a bolt
     * that stays is a bolt that was still there when they came back — which
     * is a way of turning an ability with a cooldown into a pile of arrows.
     */
    @Override
    public void tick() {
        super.tick();
        if (inGround || tickCount > LASTS) {
            discard();
        }
    }

    /**
     * How long one lives before it goes out, in ticks.
     *
     * <p>Asked of the rules rather than typed, because the rules hold it under
     * the cooldown of every ability that throws one. A number here would be a
     * second set of dials beside that, and the two drift the first time either
     * is tuned.
     */
    public static final int LASTS = SummonRules.summon(SummonRules.BOLT).lasts();

    /**
     * What this one hits for, given the ability that threw it.
     *
     * <p>Set when it is thrown rather than fixed at construction: one bolt
     * serves every thrown ability, and what differs between a hunter's mark and
     * a stormcall is exactly this number, which the library already worked out
     * from what each was priced at.
     */
    public void thrownBy(com.elysium.lib.ability.ElysiumAbilityRules.Ability ability) {
        setBaseDamage(SummonRules.hurtOfBolt(ability));
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        discard();
    }
}
