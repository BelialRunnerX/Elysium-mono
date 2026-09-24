package com.elysium.space.entity.mob;

import com.elysium.space.registry.SpaceEntities;
import com.elysium.space.registry.SpaceItems;
import com.elysium.space.world.MobRules;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Hollow moonfolk's ranged attack (Stellaris's IceSpit): a shard of rime shot
 * like an arrow. It melts in water, in the ground and after a short flight, so
 * shards never litter the moon, and cannot be picked up. A hit chills: a moment
 * of slowness.
 */
public class RimeSpit extends AbstractArrow {

    private int life;

    public RimeSpit(EntityType<? extends RimeSpit> type, Level level) {
        super(type, level);
    }

    private RimeSpit(LivingEntity shooter, Level level) {
        super(SpaceEntities.RIME_SPIT.get(), shooter, level, new ItemStack(SpaceItems.RIME_SHARD.get()), null);
    }

    /** Spits a shard from {@code shooter} at {@code target}. */
    public static RimeSpit shoot(LivingEntity shooter, LivingEntity target) {
        Level level = shooter.level();
        RimeSpit spit = new RimeSpit(shooter, level);
        double dx = target.getX() - shooter.getX();
        double dy = target.getY(0.3333D) - spit.getY();
        double dz = target.getZ() - shooter.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        spit.shoot(dx, dy + flat * MobRules.SPIT_ARC, dz, MobRules.SPIT_SPEED, MobRules.SPIT_SPREAD);
        spit.setSilent(true);
        spit.setBaseDamage(MobRules.SPIT_DAMAGE);
        spit.setCritArrow(false);
        level.addFreshEntity(spit);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.GLASS_BREAK,
                SoundSource.HOSTILE, 1.0F, 1.0F / (shooter.getRandom().nextFloat() * 0.5F + 1.0F));
        return spit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && (isInWater() || inGround || ++life > MobRules.SPIT_LIFETIME)) {
            discard();
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MobRules.SPIT_CHILL_TICKS, 0), getEffectSource());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(SpaceItems.RIME_SHARD.get());
    }
}
