package com.elysium.kinetics.block;

import com.elysium.kinetics.registry.KineticBlockEntities;
import com.elysium.kinetics.rules.KineticRules;
import com.elysium.kinetics.rules.SeamRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * A dynamo: the one place Age I turns rotation into Forge Energy.
 *
 * <h2>Deliberately a bad bargain</h2>
 *
 * It is a load like any other and its exchange rate is poor on purpose — a
 * whole water wheel spent on one lights a lamp. {@link SeamRules} says how
 * poor, in a number the later ages read rather than choose: a round trip out
 * through a dynamo and back through Age II's motor comes home with half of
 * what it left with. That is what stops the pack having a generator made out
 * of two blocks and a shaft.
 *
 * <p>The point of the block existing at the bottom of the tree at all is that
 * a player should meet the unit before the age that is about it.
 *
 * <h2>Gear it</h2>
 *
 * A dynamo bolted straight onto a water wheel makes nothing: the wheel does
 * not reach the speed the dynamo needs to start. Gearing it up is the whole
 * puzzle, and it has an answer — {@link SeamRules#bestRatio} — which is the
 * ratio that holds the source at half its free speed, where a source makes
 * the most power it is ever going to make. Gear past that and the line slows
 * more than the dynamo gains.
 */
public class DynamoBlock extends KineticBlock {

    public static final MapCodec<DynamoBlock> CODEC = simpleCodec(DynamoBlock::new);

    /** What it is called in the language file, and in this class's messages. */
    public static final String NAME = "dynamo";

    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 14, 16);

    public DynamoBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /**
     * Prodding it says what it is making, not what the line is doing.
     *
     * <p>The line's own reading is one prod away on any shaft next to it, and
     * "13 FE/t" is the number somebody stood in front of a dynamo wants. A
     * dynamo that is turning and making nothing — which is what a dynamo
     * under its least speed looks like — is the single most confusing thing
     * in this block, so it is the thing the message is about.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(
                    level.getBlockEntity(pos) instanceof DynamoBlockEntity dynamo
                            ? dynamo.saying() : reading(level, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DynamoBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return ticker(type, KineticBlockEntities.DYNAMO.get());
    }

    /**
     * A load on the line, and an FE store off it.
     *
     * <p>It makes what {@link SeamRules#output} says it makes at the speed it
     * is turning, and hands it to whatever is next to it. Nothing here
     * decides how much that is: a block that multiplied a constant itself
     * would be a block the harness has never run.
     */
    public static class DynamoBlockEntity extends KineticBlockEntity {

        /**
         * A store that refuses to be charged from outside.
         *
         * <p>A dynamo that a cable can fill is a dynamo that can be filled by
         * a second dynamo, and the pair of them is a loop the seam's
         * arithmetic says nothing about.
         */
        private final EnergyStorage energy =
                new EnergyStorage(SeamRules.buffer(), 0, SeamRules.buffer()) {
                    @Override
                    public boolean canReceive() {
                        return false;
                    }
                };

        /** What it made last tick, for the message and for nothing else. */
        private float making;

        public DynamoBlockEntity(BlockPos pos, BlockState state) {
            super(KineticBlockEntities.DYNAMO.get(), pos, state);
        }

        @Override
        public Direction.Axis axis() {
            return getBlockState().getValue(AXIS);
        }

        @Override
        public KineticRules.Shaft material() {
            return KineticRules.Shaft.BRASS;
        }

        @Override
        public KineticRules.Load load() {
            return KineticRules.DYNAMO;
        }

        public IEnergyStorage energy() {
            return energy;
        }

        /** FE per tick it is making right now. */
        public float making() {
            return making;
        }

        @Override
        protected void serverTick(Level level, BlockPos pos, BlockState state) {
            making = running() ? SeamRules.output(rpm()) : 0.0F;
            int made = Math.round(making);
            if (made > 0) {
                energy.receiveEnergy(made, false);
            }
            push(level, pos);
        }

        /**
         * Hand what is in the tank to whatever will take it.
         *
         * <p>A dynamo with nowhere to put its power fills its tank and stops,
         * which is the right behaviour and is also why the tank is a second
         * of the best dynamo anywhere rather than one tick of it: a cable
         * that is momentarily full should not cost a player the whole second.
         */
        private void push(Level level, BlockPos pos) {
            if (energy.getEnergyStored() <= 0) {
                return;
            }
            for (Direction side : Direction.values()) {
                IEnergyStorage next = level.getCapability(
                        Capabilities.EnergyStorage.BLOCK, pos.relative(side),
                        side.getOpposite());
                if (next == null || !next.canReceive() || next == energy) {
                    continue;
                }
                int taken = next.receiveEnergy(energy.getEnergyStored(), false);
                if (taken > 0) {
                    energy.extractEnergy(taken, false);
                    setChanged();
                }
            }
        }

        /** One line: what it is making, or why it is making nothing. */
        public Component saying() {
            if (!running()) {
                return Component.translatable("elysiumkinetics.dynamo.still");
            }
            if (!KineticRules.DYNAMO.working(rpm())) {
                return Component.translatable("elysiumkinetics.dynamo.slow",
                        String.format("%.0f", Math.abs(rpm())),
                        String.format("%.0f", KineticRules.DYNAMO.leastRpm()));
            }
            return Component.translatable("elysiumkinetics.dynamo.making",
                    String.format("%.0f", making),
                    String.valueOf(energy.getEnergyStored()),
                    String.valueOf(energy.getMaxEnergyStored()));
        }

        @Override
        protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                                      net.minecraft.core.HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            energy.deserializeNBT(registries, tag.get("energy"));
        }

        @Override
        protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                                      net.minecraft.core.HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("energy", energy.serializeNBT(registries));
        }
    }
}
