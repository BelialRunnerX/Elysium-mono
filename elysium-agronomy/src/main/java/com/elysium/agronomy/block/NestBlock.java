package com.elysium.agronomy.block;

import com.elysium.agronomy.event.Keeping;
import com.elysium.agronomy.registry.AgronomyComponents;
import com.elysium.agronomy.registry.BeeItems;
import com.elysium.agronomy.rules.BeeRules;
import com.elysium.agronomy.rules.KeepingRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A nest: where a wild bee is taken from.
 *
 * <p>One per home. A home with a wild bee ({@link KeepingRules#wildAt}) is
 * generated in its biome and holds {@link KeepingRules#NEST_BEES} of that
 * bee; an empty jar used on it takes one, and the nest grows one back every
 * {@link KeepingRules#NEST_REFILL_TICKS}, which the harness holds under the
 * rate a hive works at. A nest for a home only bred bees live in is a
 * decoration, and says so.
 */
public class NestBlock extends Block {

    public static final IntegerProperty BEES =
            IntegerProperty.create("bees", 0, KeepingRules.NEST_BEES);

    public static final MapCodec<NestBlock> CODEC =
            simpleCodec(properties -> new NestBlock(properties, ""));

    private final String home;

    public NestBlock(Properties properties, String home) {
        super(properties);
        this.home = home;
        registerDefaultState(stateDefinition.any().setValue(BEES, KeepingRules.NEST_BEES));
    }

    /** The home this nest is for. */
    public String home() {
        return home;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BEES);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!held.is(BeeItems.JAR.get()) || AgronomyComponents.speciesIn(held) != null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        BeeRules.Bee wild = KeepingRules.wildAt(home);
        if (wild == null) {
            player.displayClientMessage(Component.translatable("elysiumagronomy.nest.bred"), true);
            return ItemInteractionResult.CONSUME;
        }
        int left = state.getValue(BEES);
        if (left <= 0) {
            player.displayClientMessage(Component.translatable("elysiumagronomy.nest.empty",
                    String.valueOf(KeepingRules.NEST_REFILL_TICKS / 20)), true);
            return ItemInteractionResult.CONSUME;
        }
        held.set(AgronomyComponents.SPECIES.get(), wild.id());
        level.setBlock(pos, state.setValue(BEES, left - 1), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, KeepingRules.NEST_REFILL_TICKS);
        Keeping.robbed(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    /** One bee grown back, and the next one started if the nest is still not full. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int now = state.getValue(BEES);
        if (now >= KeepingRules.NEST_BEES) {
            return;
        }
        level.setBlock(pos, state.setValue(BEES, now + 1), Block.UPDATE_ALL);
        if (now + 1 < KeepingRules.NEST_BEES) {
            level.scheduleTick(pos, this, KeepingRules.NEST_REFILL_TICKS);
        }
    }
}
