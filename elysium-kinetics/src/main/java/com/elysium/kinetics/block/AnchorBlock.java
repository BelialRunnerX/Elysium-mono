package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.rules.AnchorRules;
import com.elysium.kinetics.rules.KineticRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * An anchor: a machine whose product is that the world keeps running.
 *
 * <h2>A chunk loader that is a load</h2>
 *
 * In most mods a chunk loader is a block you place and forget, and it
 * therefore costs nothing — which is why a world with a dozen of them in it
 * is a world that runs at a crawl. Here it draws torque in proportion to how
 * much world it is keeping awake, and it lets go the moment the line stops.
 * A 5×5 anchor is a machine hall, not a block.
 *
 * <p>{@link AnchorRules} has the arithmetic. This has the ticket and the
 * three ways it can be let go of, which are the three ways a chunk loader
 * leaks: the line stopped, the block was broken, and the world shut down
 * while it was holding something.
 */
public class AnchorBlock extends KineticBlock {

    public static final MapCodec<AnchorBlock> CODEC =
            simpleCodec(properties -> new AnchorBlock(properties,
                    AnchorRules.ANCHORS.get(0).id()));

    /** How wide a square this one is set to hold. */
    public static final IntegerProperty RADIUS =
            IntegerProperty.create("radius", 0, AnchorRules.WIDEST);

    private final String id;

    public AnchorBlock(Properties properties, String id) {
        super(properties);
        this.id = id;
        registerDefaultState(defaultBlockState().setValue(RADIUS, 0));
    }

    /** Which anchor this is, out of the rules. */
    public AnchorRules.Anchor rules() {
        AnchorRules.Anchor found = AnchorRules.anchor(id);
        return found != null ? found : AnchorRules.ANCHORS.get(0);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RADIUS);
    }

    /**
     * Prodding it widens it; prodding it at its widest takes it back to one.
     *
     * <p>A value panel would be four more classes to say a number between
     * zero and two. Cycling it and saying what that now costs is the same
     * information in one line, and the line is the part that matters: a
     * player who widens an anchor and watches their hall stall has learned
     * the whole block.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        int widest = rules().widest();
        if (player.isSecondaryUseActive() && widest > 0) {
            int next = (state.getValue(RADIUS) + 1) % (widest + 1);
            level.setBlock(pos, state.setValue(RADIUS, next), UPDATE_ALL);
            KineticLine.disturb(level, pos);
        }
        if (level.getBlockEntity(pos) instanceof AnchorBlockEntity anchor) {
            player.displayClientMessage(anchor.saying(), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AnchorBlockEntity anchor
                ? AnchorRules.comparator(anchor.holding(), state.getValue(RADIUS)) : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnchorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.ANCHOR.get());
    }

    /** The load, the ticket, and the grace between the two. */
    public static class AnchorBlockEntity extends KineticBlockEntity {

        /** Ticks left of the grace after the line stopped. Zero is let go. */
        private int grace;

        /** What is currently forced, or -1 for nothing. */
        private int held = -1;

        public AnchorBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.ANCHOR.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        @Override
        public KineticRules.Shaft material() {
            return getBlockState().getBlock() instanceof AnchorBlock anchor
                    ? anchor.rules().made() : KineticRules.Shaft.BRASS;
        }

        @Override
        public KineticRules.Load load() {
            return AnchorRules.loadAt(getBlockState().getValue(RADIUS));
        }

        /** Whether it is keeping anything awake right now. */
        public boolean holding() {
            return held >= 0;
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            if (!(level instanceof net.minecraft.server.level.ServerLevel server)) {
                return;
            }
            int radius = state.getValue(RADIUS);
            boolean turning = running() && load().working(rpm());
            if (turning) {
                grace = AnchorRules.GRACE_TICKS;
            } else if (grace > 0) {
                grace--;
            }
            // The grace exists because a windmill drops below the floor every
            // time the wind turns, and an anchor that let go each time would
            // unload its own farm several times a minute — which is worse
            // than not holding it at all.
            boolean want = turning || grace > 0;
            if (want && held != radius) {
                letGo(server);
                KineticBlockEntities.ANCHORS.forceChunk(server, pos, 0, radius, true, false);
                held = radius;
                setChanged();
            } else if (!want && held >= 0) {
                letGo(server);
                setChanged();
            }
        }

        /** Drop the ticket. Called on stopping, on breaking and on unloading. */
        public void letGo(net.minecraft.server.level.ServerLevel server) {
            if (held < 0) {
                return;
            }
            KineticBlockEntities.ANCHORS.forceChunk(server, getBlockPos(), 0, held,
                    false, false);
            held = -1;
        }

        @Override
        public void setRemoved() {
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                letGo(server);
            }
            super.setRemoved();
        }

        /** One line: what it is holding, or what it is waiting for. */
        public Component saying() {
            int radius = getBlockState().getValue(RADIUS);
            int across = AnchorRules.acrossAt(radius);
            if (holding()) {
                return Component.translatable("elysiumkinetics.anchor.holding",
                        across + "x" + across,
                        String.valueOf(AnchorRules.chunksAt(radius)));
            }
            return Component.translatable("elysiumkinetics.anchor.waiting",
                    across + "x" + across,
                    String.format("%.0f", load().leastRpm()),
                    String.format("%.0f", Math.abs(rpm())));
        }

        @Override
        protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                                      net.minecraft.core.HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            grace = tag.getInt("grace");
            // Not the ticket: a forced chunk is the server's business and is
            // re-asked for on the first tick. A saved one is a ticket that
            // outlives the block that asked for it.
            held = -1;
        }

        @Override
        protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                                      net.minecraft.core.HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("grace", grace);
        }
    }
}
