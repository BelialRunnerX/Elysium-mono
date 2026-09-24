package com.elysium.core.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * A rogue spawner (Apotheosis' AP-24): something waiting underground that a
 * player did not put there.
 *
 * <h2>Two of them, and the difference is the whole point</h2>
 *
 * A <b>Brutal</b> spawner sends a few creatures that are each worth being
 * afraid of. A <b>Swarm</b> spawner sends many that are not. They are the same
 * block with one number changed, and they play completely differently: one is
 * a fight you can lose to a single mistake, the other is a fight you lose by
 * standing still.
 *
 * <h2>It ends</h2>
 *
 * A vanilla spawner is a permanent fixture that a player eventually lights up
 * and forgets. This one has a number of waves in it and breaks when they are
 * spent, dropping what it was guarding. That makes clearing it a thing that
 * finishes rather than a chore to suppress.
 */
public class RogueSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<RogueSpawnerBlock> CODEC =
            simpleCodec(properties -> new RogueSpawnerBlock(RogueSpawnerBlockEntity.Mode.BRUTAL));

    private final RogueSpawnerBlockEntity.Mode mode;

    public RogueSpawnerBlock(RogueSpawnerBlockEntity.Mode mode) {
        super(BlockBehaviour.Properties.of()
                .mapColor(mode == RogueSpawnerBlockEntity.Mode.BRUTAL
                        ? MapColor.COLOR_RED : MapColor.COLOR_GREEN)
                .strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .lightLevel(state -> 3));
        this.mode = mode;
    }

    public RogueSpawnerBlockEntity.Mode mode() {
        return mode;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RogueSpawnerBlockEntity(pos, state, mode);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, com.elysium.core.Elysium.ROGUE_SPAWNER_ENTITY.get(),
                        RogueSpawnerBlockEntity::tick);
    }
}
