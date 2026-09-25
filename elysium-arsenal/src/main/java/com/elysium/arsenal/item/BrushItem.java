package com.elysium.arsenal.item;

import com.elysium.arsenal.block.SiteBlock;
import com.elysium.arsenal.block.entity.SiteBlockEntity;
import com.elysium.arsenal.rules.DigRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A brush, at one of three speeds.
 *
 * <h2>Why every brush is still slow</h2>
 *
 * The ladder exists so the hundredth site is less tedious than the first, not
 * so that sites stop being dug. {@link DigRules#FASTEST} is the floor and the
 * rules refuse a brush below it, because a brush fast enough to be convenient
 * is a block that drops its loot when you hit it — and then there was no
 * reason for any of this to exist.
 */
public class BrushItem extends Item {

    private final DigRules.Brush brush;

    public BrushItem(DigRules.Brush brush, Properties properties) {
        super(properties);
        this.brush = brush;
    }

    /** Which brush this is, in the rules' own terms. */
    public DigRules.Brush brush() {
        return brush;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity holder) {
        return brush.aWholeSite() * 2;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!(level.getBlockState(context.getClickedPos())
                .getBlock() instanceof SiteBlock)) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onUseTick(Level level, LivingEntity holder, ItemStack stack,
                          int left) {
        if (!(holder instanceof Player player) || !(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos pos = aimedAt(player);
        if (pos == null) {
            holder.releaseUsingItem();
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SiteBlock site)
                || !(level.getBlockEntity(pos) instanceof SiteBlockEntity entity)) {
            holder.releaseUsingItem();
            return;
        }
        if (!entity.brushFor(brush.ticks())) {
            return;
        }
        level.playSound(null, pos, SoundEvents.BRUSH_GRAVEL, SoundSource.BLOCKS,
                1.0F, 1.0F);
        if (!entity.finished()) {
            level.setBlock(pos, state.setValue(SiteBlock.DUSTED,
                    Math.min(DigRules.STAGES - 1, entity.stage())), Block.UPDATE_ALL);
            return;
        }
        // Everything it had, and then it is the block underneath. Dropping
        // before replacing, because replacing removes the block entity and
        // with it the only thing that knows the site was ever here.
        ItemStack buried = entity.whatIsBuried(server);
        if (!buried.isEmpty()) {
            Block.popResourceFromFace(level, pos,
                    player.getDirection().getOpposite(), buried);
        }
        level.setBlock(pos, site.leavesBehind(), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BRUSH_GRAVEL_COMPLETED,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(
                player.getUsedItemHand()));
        holder.releaseUsingItem();
    }

    /**
     * The block the player is pointing at, or null.
     *
     * <p>Recomputed every tick rather than remembered from the click, so
     * looking away stops the brushing. Remembering it would let a player start
     * on a site, walk off and keep brushing it from across the room.
     */
    private BlockPos aimedAt(Player player) {
        var hit = player.pick(brush.reach(), 0.0F, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult block) {
            return block.getBlockPos();
        }
        return null;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack with) {
        return with.is(net.minecraft.world.item.Items.FEATHER);
    }
}
