package com.elysium.arsenal.item;

import com.elysium.arsenal.rules.ArsenalRules;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A scythe: the broad tool that also harvests.
 *
 * <h2>The one way a harvest tool makes food out of nothing</h2>
 *
 * It takes the crop and puts a seed back, and if it takes the crop's drops
 * <em>and</em> puts back a seed it did not take, it is a seed farm that needs
 * no field. Nobody notices, because a wheat farm producing slightly too much
 * wheat looks exactly like a wheat farm.
 *
 * <p>So the accounting is one line and it is in {@link ArsenalRules}: one seed
 * comes back per crop, and that one goes straight into the ground. The player
 * keeps <b>none</b> of them, and keeps everything else the crop dropped.
 */
public class ScytheItem extends BroadToolItem {

    public ScytheItem(Tier tier, Properties properties) {
        super(tier, BlockTags.MINEABLE_WITH_HOE, properties, "scythe", true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos middle = context.getClickedPos();
        int reach = ArsenalRules.REACH;
        int reaped = 0;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                if (reap(level, middle.offset(x, 0, z), player)) {
                    reaped++;
                }
            }
        }
        if (reaped == 0) {
            return InteractionResult.PASS;
        }
        ItemStack held = context.getItemInHand();
        held.hurtAndBreak(ArsenalRules.costOf(reaped), player, EquipmentSlot.MAINHAND);
        level.playSound(null, middle, SoundEvents.CROP_BREAK, SoundSource.BLOCKS,
                0.8F, 1.1F);
        return InteractionResult.CONSUME;
    }

    /**
     * Take one grown crop and put it straight back at age nothing.
     *
     * <p>The drops are rolled by the game and then one seed is taken out of
     * them, because that seed is the one going back in the ground. Taking it
     * out is the whole of the accounting: leave it in and the field grows.
     */
    private boolean reap(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return false;
        }
        List<ItemStack> dropped = Block.getDrops(state, (net.minecraft.server.level.ServerLevel) level,
                pos, null, player, player.getMainHandItem());
        ItemStack seed = new ItemStack(crop.asItem());
        int held = ArsenalRules.replanted(1);
        for (ItemStack stack : dropped) {
            if (held <= 0) {
                break;
            }
            if (ItemStack.isSameItem(stack, seed)) {
                int taken = Math.min(held, stack.getCount());
                stack.shrink(taken);
                held -= taken;
            }
        }
        for (ItemStack stack : dropped) {
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
        // Back to nothing, through the crop's own "this is what age zero looks
        // like" rather than by naming its age property — which is protected,
        // and which a modded crop is free to spell differently anyway.
        level.setBlock(pos, crop.getStateForAge(0), 3);
        return true;
    }
}
