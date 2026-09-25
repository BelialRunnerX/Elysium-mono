package com.elysium.arsenal.block;

import com.elysium.arsenal.block.entity.SiteBlockEntity;
import com.elysium.arsenal.rules.DigRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A block that gives up what is in it only to a brush.
 *
 * <p>Ours rather than the game's, for two reasons that are really one. The
 * game's version reads a loot table, which is rolled the first time anybody
 * touches it and stored — so two blocks side by side hold different things and
 * a player who breaks one and puts it back gets a fresh roll. Ours works out
 * what is in it from where it is, every time it is asked, so a site is a place.
 *
 * <p>The other reason is the ladder: the game's brush has one speed and no
 * way to have another, and a dig site that is exactly as slow on the hundredth
 * visit as on the first is a mechanic people do once.
 */
public class SiteBlock extends BaseEntityBlock {

    public static final MapCodec<SiteBlock> CODEC = simpleCodec(properties ->
            new SiteBlock(properties, "dirt", "minecraft:dirt"));

    /** How far through the brushing this block looks. */
    public static final IntegerProperty DUSTED =
            IntegerProperty.create("dusted", 0, DigRules.STAGES - 1);

    private final String pool;
    private final String below;

    public SiteBlock(Properties properties, String pool, String below) {
        super(properties);
        this.pool = pool;
        this.below = below;
        registerDefaultState(stateDefinition.any().setValue(DUSTED, 0));
    }

    /** Which pool this site draws from. */
    public String pool() {
        return pool;
    }

    /** What it leaves behind once it has given everything up. */
    public BlockState leavesBehind() {
        ResourceLocation id = ResourceLocation.tryParse(below);
        if (id == null) {
            return Blocks.DIRT.defaultBlockState();
        }
        return BuiltInRegistries.BLOCK.getOptional(id)
                .orElse(Blocks.DIRT).defaultBlockState();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DUSTED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        SiteBlockEntity made = new SiteBlockEntity(pos, state);
        made.setPool(pool);
        return made;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos,
                        BlockState was, boolean moving) {
        super.onPlace(state, level, pos, was, moving);
        // The pool has to be on the block entity as well as on the block,
        // because the block entity is what the brush talks to and a block
        // entity that does not know its pool would draw from the first one.
        if (level.getBlockEntity(pos) instanceof SiteBlockEntity site) {
            site.setPool(pool);
        }
    }
}
