package com.elysium.arsenal.item;

import com.elysium.arsenal.rules.RangedRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.function.Predicate;

/**
 * One class for thirty-six bows; which one it is, it is told.
 *
 * <p>Everything that varies — how fast it draws, how hard it hits, how far it
 * carries, how many it looses and how straight — is read off
 * {@link RangedRules} at the moment it is used, not baked in at registration.
 * That is what makes retuning the ladder change every bow already in a world
 * rather than only the ones made afterwards.
 */
public class BowItem extends ProjectileWeaponItem {

    private final RangedRules.Bow bow;

    public BowItem(Properties properties, RangedRules.Bow bow) {
        super(properties);
        this.bow = bow;
    }

    /** Which bow this is, at its own tier. */
    public RangedRules.Bow bow() {
        return bow;
    }

    /** Whether it takes bolts rather than arrows. */
    public boolean bolts() {
        return RangedRules.takesBolts(bow);
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> stack.is(net.minecraft.tags.ItemTags.ARROWS);
    }

    @Override
    public int getDefaultProjectileRange() {
        return Math.max(1, Math.round(15.0F * bow.range()));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity holder) {
        return 72_000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                  InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean hasAmmo = !player.getProjectile(held).isEmpty();
        if (!player.hasInfiniteMaterials() && !hasAmmo) {
            return InteractionResultHolder.fail(held);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void releaseUsing(ItemStack held, Level level, LivingEntity holder,
                             int left) {
        if (!(holder instanceof Player player)) {
            return;
        }
        int used = getUseDuration(held, holder) - left;
        float pull = drawn(used);
        if (pull < 0.1F) {
            return;
        }
        ItemStack ammo = player.getProjectile(held);
        if (ammo.isEmpty() && !player.hasInfiniteMaterials()) {
            return;
        }
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW);
        }
        if (!level.isClientSide) {
            for (int shot = 0; shot < bow.shots(); shot++) {
                loose(level, player, held, ammo, pull, shot);
            }
            held.hurtAndBreak(1, player, LivingEntity.getSlotForHand(
                    player.getUsedItemHand()));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + pull * 0.5F);
        if (!player.hasInfiniteMaterials()) {
            ammo.shrink(bow.shots());
        }
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    private void loose(Level level, Player player, ItemStack held, ItemStack ammo,
                       float pull, int shot) {
        AbstractArrow arrow = new Arrow(level, player, ammo.copyWithCount(1), held);
        // Spread is the other side of accuracy: a perfectly accurate bow puts
        // every arrow where the crosshair was, and a sling does not.
        float spread = (1.0F - bow.accuracy()) * 6.0F + shot * 0.8F;
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                pull * 3.0F * bow.range(), spread);
        arrow.setBaseDamage(arrow.getBaseDamage()
                * (bow.damage() / RangedRules.VANILLA_BOW_DAMAGE));
        if (pull >= 1.0F) {
            arrow.setCritArrow(true);
        }
        arrow.pickup = player.hasInfiniteMaterials()
                ? AbstractArrow.Pickup.CREATIVE_ONLY
                : AbstractArrow.Pickup.ALLOWED;
        level.addFreshEntity(arrow);
    }

    /**
     * The game's own hook for firing one projectile.
     *
     * <p>Present because {@code ProjectileWeaponItem} requires it, and used by
     * whatever else in the game fires a bow — a dispenser, a mob. It applies
     * the same accuracy and the same range this bow's own firing does, so a
     * skeleton with a sling is as wrong about where it aimed as a player is.
     */
    @Override
    protected void shootProjectile(LivingEntity holder,
                                   net.minecraft.world.entity.projectile.Projectile
                                           projectile,
                                   int index, float velocity, float spread,
                                   float angle, LivingEntity target) {
        projectile.shootFromRotation(holder, holder.getXRot(),
                holder.getYRot() + angle, 0.0F, velocity * bow.range(),
                spread + (1.0F - bow.accuracy()) * 6.0F + index * 0.8F);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(arrow.getBaseDamage()
                    * (bow.damage() / RangedRules.VANILLA_BOW_DAMAGE));
        }
    }

    /** How far through a draw a given number of ticks is, from nothing to one. */
    public float drawn(int ticks) {
        float whole = bow.drawTicks();
        float through = ticks / whole;
        through = (through * through + through * 2.0F) / 3.0F;
        return Math.min(1.0F, through);
    }
}
