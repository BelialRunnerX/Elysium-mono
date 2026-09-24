package com.elysium.industry.block;

import com.elysium.industry.rules.IndustryRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * What every block on a grid has in common, on the block side.
 *
 * <p>Prodding any part of a grid says what the whole grid is doing, for the
 * same reason Age I does: the information the model needs a player to have is
 * the information that makes the first hour comprehensible, and putting it
 * behind an item is putting it behind the day they get that item.
 */
public abstract class GridBlock extends BaseEntityBlock {

    protected GridBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        GridLine.disturb(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            GridLine.disturb(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        GridLine.disturb(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(reading(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * What the grid through this block is doing, in one sentence.
     *
     * The order matters. A grid wired across two tiers reads as burned, and
     * has to say <em>why</em> or a player goes looking for an overloaded wire
     * that is not there.
     */
    public static Component reading(Level level, BlockPos pos) {
        GridLine.Found found = GridLine.walk(level, pos);
        IndustryRules.Reading grid = GridLine.solve(level, pos);
        if (found.mixed()) {
            return Component.translatable("elysiumindustry.grid.mixed");
        }
        if (grid.burned()) {
            return Component.translatable("elysiumindustry.grid.burned",
                    String.format("%.0f", Math.min(grid.supply(), grid.demand())),
                    String.valueOf(found.weakest().capacity()));
        }
        if (grid.supply() <= 0.0F) {
            return Component.translatable("elysiumindustry.grid.dark");
        }
        if (grid.share() < 1.0F) {
            return Component.translatable("elysiumindustry.grid.short",
                    String.format("%.0f%%", grid.share() * 100.0F),
                    String.format("%.0f", grid.supply()),
                    String.format("%.0f", grid.demand()));
        }
        return Component.translatable("elysiumindustry.grid.fed",
                String.format("%.0f", grid.supply()),
                String.format("%.0f", grid.demand()));
    }

    @Nullable
    protected static <T extends BlockEntity> BlockEntityTicker<T> ticker(
            BlockEntityType<T> given, BlockEntityType<?> wanted) {
        return given == wanted
                ? (level, pos, state, entity) ->
                        GridBlockEntity.tick(level, pos, state, (GridBlockEntity) entity)
                : null;
    }

    @Override
    protected abstract MapCodec<? extends GridBlock> codec();
}
