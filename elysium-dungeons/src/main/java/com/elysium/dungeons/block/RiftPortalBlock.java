package com.elysium.dungeons.block;

import com.elysium.dungeons.level.DungeonTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The portal surface. Standing in it takes you somewhere.
 *
 * <h2>Why travel happens here and not on a tick handler</h2>
 *
 * {@code entityInside} fires for the entity that is actually touching the
 * block, which is exactly the question being asked. A tick handler would have
 * to scan every player every tick and test them against every portal in the
 * world, which is more code doing more work to answer the same thing less
 * accurately.
 *
 * The block has no collision, so a player walks into it rather than onto it,
 * and the axis property only exists so the texture faces the right way.
 */
public class RiftPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    public RiftPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    /**
     * Sends a player through.
     *
     * Everything interesting is delegated: which dungeon, whether it needs
     * building, and whether this is a way in or a way out are all decided by
     * DungeonTravel, because they depend on state this block does not have and
     * should not learn.
     *
     * Server side only. Running it on the client would move the player locally
     * and be corrected a moment later, which reads as a rubber-band and a
     * flicker of the wrong dimension.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        DungeonTravel.use(player, pos);
    }
}
