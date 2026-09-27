package com.elysium.comforts.block;

import com.elysium.comforts.rules.PostRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Somewhere for a bird to sit, in eleven woods.
 *
 * <h2>Why it gets dirty</h2>
 *
 * Because a pigeonhole a bird uses and a pigeonhole a bird does not use have
 * to be different to look at, or the block is a decoration and the post is a
 * menu. The waste level is that difference and it is also the reward: scoop it
 * and you get something back, and the more it has been used the more there is.
 * What that comes to is {@link PostRules#wasteAt(int)}, which the rules hold to
 * climbing — a flat one would mean there was no reason to keep a bird anywhere
 * in particular.
 */
public class PigeonholeBlock extends HorizontalDirectionalBlock {

    /** How dirty it is, nought to {@link PostRules#FOULEST}. */
    public static final IntegerProperty WASTE =
            IntegerProperty.create("waste", 0, PostRules.FOULEST);

    public static final com.mojang.serialization.MapCodec<PigeonholeBlock> CODEC =
            simpleCodec(properties -> new PigeonholeBlock(properties, "oak"));

    private final String wood;

    public PigeonholeBlock(Properties properties, String wood) {
        super(properties);
        this.wood = wood;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WASTE, 0));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock>
            codec() {
        return CODEC;
    }

    /** The wood this one is built from. */
    public String wood() {
        return wood;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WASTE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    /**
     * Scooping it out.
     *
     * <p>Only a shovel does it, and only when there is something to scoop, so
     * that a player who right-clicks a clean one with a shovel is told nothing
     * has happened rather than quietly given nothing. What comes out is the
     * rules' own answer and not a number typed here.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
                                              Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        int level_ = state.getValue(WASTE);
        if (level_ <= 0 || !held.is(net.minecraft.tags.ItemTags.SHOVELS)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            int many = PostRules.wasteAt(level_);
            if (many > 0) {
                popResource(level, pos, new ItemStack(Items.BONE_MEAL, many));
            }
            level.setBlock(pos, state.setValue(WASTE, 0), Block.UPDATE_ALL);
            held.hurtAndBreak(1, player,
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * A bird has been and gone.
     *
     * <p>Called by the bird rather than by a tick, because a pigeonhole nothing
     * has used should never get dirty, and a block that dirties itself on a
     * clock is a block that fills the world with bone meal while the player is
     * asleep.
     */
    public static void used(Level level, BlockPos pos, boolean tired) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PigeonholeBlock)) {
            return;
        }
        int now = state.getValue(WASTE);
        if (now >= PostRules.FOULEST) {
            return;
        }
        if (tired || level.getRandom().nextInt(5) == 0) {
            level.setBlock(pos, state.setValue(WASTE, now + 1), Block.UPDATE_ALL);
        }
    }
}
