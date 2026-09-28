package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.registry.KineticBlocks;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * A windmill bearing: however many sails you bolted to it.
 *
 * <h2>The shape of it</h2>
 *
 * Sails are placed in the plane the bearing turns in, touching it or touching
 * each other. Count them, multiply by what one sail is worth, and that is the
 * source. So a windmill is a thing a player <em>builds</em> rather than a
 * block they place, and a bigger one is straightforwardly better — up to
 * {@link KineticRules#MOST_SAILS}, past which the bearing would tear off.
 *
 * <h2>Why it is not simply a better water wheel</h2>
 *
 * A full windmill carries more than a water wheel and is no faster, because
 * sails add stall torque and not free speed. It is the answer to "there is no
 * river here", and to "I need to drive eleven millstones", and it costs a
 * great deal of timber and a great deal of open sky to get there.
 */
public class WindmillBearingBlock extends KineticBlock {

    public static final MapCodec<WindmillBearingBlock> CODEC =
            simpleCodec(WindmillBearingBlock::new);

    /** How far from the bearing a sail may be and still be part of the sail. */
    public static final int REACH = 8;

    public WindmillBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    /**
     * Prodding a bearing counts the sail rather than reading the line.
     *
     * A player standing at a windmill that will not turn is asking one
     * question, and it is not what the rpm is: it is whether the thing they
     * built counts as a sail. So the bearing answers that one, and the shaft
     * two blocks along still answers the other.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof WindmillBearingBlockEntity bearing) {
            player.displayClientMessage(bearing.saying(), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindmillBearingBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.WINDMILL_BEARING.get());
    }

    /**
     * Every sail joined to the bearing, in the plane it turns in.
     *
     * <p>Flood filled rather than counted in a box: a sail has to be connected
     * to the rest of the sail, so a player cannot scatter loose blocks around
     * the field and have them count. Capped, and the cap is checked against
     * the count rather than the search, so a sail one block over the limit is
     * a sail at the limit and not a sail that stopped working.
     */
    public static int sailsAround(Level level, BlockPos bearing, Direction.Axis axis) {
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(bearing);
        Set<BlockPos> seen = new HashSet<>();
        seen.add(bearing.immutable());

        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction face : Direction.values()) {
                if (face.getAxis() == axis) {
                    continue;                  // the sail is a disc, not a drum
                }
                BlockPos next = at.relative(face);
                if (!seen.add(next.immutable())) {
                    continue;
                }
                if (next.distManhattan(bearing) > REACH * 2) {
                    continue;
                }
                if (!level.isLoaded(next)
                        || !level.getBlockState(next).is(KineticBlocks.SAIL.get())) {
                    continue;
                }
                found.add(next.immutable());
                queue.add(next.immutable());
            }
        }
        return Math.min(KineticRules.MOST_SAILS, found.size());
    }

    /** The bearing, which is as strong as the sail somebody built on it. */
    public static class WindmillBearingBlockEntity extends KineticBlockEntity {

        /** Sails do not appear on their own; no need to look every tick. */
        private static final int LOOK_EVERY = 40;

        private int sails;
        private int sinceLooked;

        public WindmillBearingBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.WINDMILL_BEARING.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        /** How many sails are turning it, for the readout. */
        public int sails() {
            return sails;
        }

        /** What the bearing says when somebody asks it. */
        public Component saying() {
            if (sails <= 0) {
                return Component.translatable("elysiumkinetics.windmill.bare");
            }
            return Component.translatable("elysiumkinetics.windmill.sails", sails,
                    String.format("%.0f", KineticRules.SAIL.stallTorque() * sails));
        }

        @Nullable
        @Override
        public KineticRules.Source source() {
            if (sails <= 0) {
                return null;
            }
            KineticRules.Source one = KineticRules.SAIL;
            // Sails add force, not speed: ten sails pull ten times as hard and
            // still stop at the same rpm. That is what keeps a windmill from
            // quietly becoming a steam engine made of wool.
            return new KineticRules.Source(one.freeSpeed(), one.stallTorque() * sails);
        }

        @Override
        protected String hum() {
            return "shaft";
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (++sinceLooked < LOOK_EVERY) {
                return;
            }
            sinceLooked = 0;
            int now = sailsAround(level, pos, axis());
            if (now != sails) {
                sails = now;
                unsettle();
            }
        }
    }
}
