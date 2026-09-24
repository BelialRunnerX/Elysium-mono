package com.elysium.flora.block;

import com.elysium.flora.rules.FloraRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

/**
 * The wild form: where the seeds come from.
 *
 * <h2>What it is for, and what it is not for</h2>
 *
 * It exists so that a player who has never seen this crop finds one within an
 * afternoon's walking, picks it, and can then plant a field. It is not a
 * harvest: it gives seeds and no fibre at all, and it is rare enough that
 * nobody will try to live off finding it.
 *
 * <p>That is the whole design, and it is the reason the number of seeds is
 * two rather than one — {@link FloraRules#WILD_SEEDS}. One seed from one
 * plant means a player who finds a single specimen still cannot start, which
 * turns a discovery into an errand.
 */
public class WildPlantBlock extends BushBlock {

    private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block
            .box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    private final Supplier<ItemLike> seed;

    public WildPlantBlock(Properties properties, Supplier<ItemLike> seed) {
        super(properties);
        this.seed = seed;
    }

    public static final com.mojang.serialization.MapCodec<WildPlantBlock> CODEC =
            net.minecraft.world.level.block.Block.simpleCodec(
                    properties -> new WildPlantBlock(properties, () -> net.minecraft.world.item.Items.WHEAT_SEEDS));

    /**
     * The codec exists because the game asks every block for one; this block is
     * never written to data, so the seed the codec would rebuild it with is a
     * placeholder and the real ones are the two registered in FloraBlocks.
     */
    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        net.minecraft.world.phys.Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, 0.0D, offset.z);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(seed.get(), FloraRules.WILD_SEEDS));
    }
}
