package com.elysium.bestiary.entity;

import com.elysium.bestiary.registry.SummonEntities;
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
        setBaseDamage(4.0D);
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

    /** How long one lives before it goes out, in ticks. */
    public static final int LASTS = 60;

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        discard();
    }
}
