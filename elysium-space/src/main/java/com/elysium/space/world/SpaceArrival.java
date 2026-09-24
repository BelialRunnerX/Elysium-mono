package com.elysium.space.world;

import com.elysium.space.registry.SpaceBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where something arriving in a world stands. The surface at a column, or, in
 * empty space (High Orbit), a small platform built at {@link #VOID_FLOOR} so an
 * arrival does not fall forever. Used by rockets and the travel command.
 */
public final class SpaceArrival {

    private SpaceArrival() {
    }

    public static final int VOID_FLOOR = 100;

    /** The first air block above the ground at (x, z), building a platform if there is no ground. */
    public static BlockPos groundAt(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);           // generate it, so the heightmap is real
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
        if (top.getY() > level.getMinBuildHeight() + 1) {
            return top;
        }
        BlockPos floor = new BlockPos(x, VOID_FLOOR, z);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p = floor.offset(dx, -1, dz);
                if (level.getBlockState(p).isAir()) {
                    level.setBlockAndUpdate(p, SpaceBlocks.LAUNCH_PAD.get().defaultBlockState());
                }
            }
        }
        return floor;
    }
}
