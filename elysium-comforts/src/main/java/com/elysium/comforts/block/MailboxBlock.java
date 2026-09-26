package com.elysium.comforts.block;

import com.elysium.comforts.item.AddressTagItem;
import com.elysium.comforts.item.MailItem;
import com.elysium.comforts.registry.ComfortComponents;
import com.elysium.comforts.rules.PostRules;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Where mail lands, and the only thing in the module that knows your name.
 *
 * <h2>What an empty hand does, and what a tag does</h2>
 *
 * An empty hand takes whatever has arrived. An address tag names the box, and
 * costs levels, because a name is the scarce thing in a postal system: if
 * naming is free then every box is called the same and the post is a broadcast.
 * Anything else that is mail is handed to the post.
 *
 * <p>The block never destroys what it is given. Every path through
 * {@link #useItemOn} either keeps the stack where it was or moves it somewhere
 * that will hand it back, and {@link #onRemove} drops everything including what
 * is still in the air. That is the discipline the rest of this module was built
 * with — sixty of its negative tests are ways a container could have lost what
 * was in it — and there is no reason a postbox should be the exception.
 */
public class MailboxBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Whether there is anything in it. */
    public static final BooleanProperty FULL = BooleanProperty.create("full");

    public static final MapCodec<MailboxBlock> CODEC = simpleCodec(MailboxBlock::new);

    public MailboxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FULL, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FULL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MailboxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (world, pos, blockState, entity) -> {
            if (entity instanceof MailboxBlockEntity box
                    && box.landIfDue(world.getGameTime())) {
                world.setBlock(pos, blockState.setValue(FULL, true), Block.UPDATE_ALL);
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
                                              Level level, BlockPos pos,
                                              Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MailboxBlockEntity box)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (held.isEmpty()) {
            return collect(level, pos, state, player, box);
        }
        if (held.getItem() instanceof AddressTagItem) {
            return name(level, player, held, box);
        }
        if (held.getItem() instanceof MailItem mail && mail.mail().posted()) {
            return post(level, player, held, box);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Hand over what has arrived. */
    private ItemInteractionResult collect(Level level, BlockPos pos, BlockState state,
                                          Player player, MailboxBlockEntity box) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        boolean gave = false;
        NonNullList<ItemStack> inside = box.contents();
        for (int slot = 0; slot < inside.size(); slot++) {
            ItemStack one = inside.get(slot);
            if (one.isEmpty()) {
                continue;
            }
            if (!player.getInventory().add(one)) {
                player.drop(one, false);
            }
            inside.set(slot, ItemStack.EMPTY);
            gave = true;
        }
        if (gave) {
            box.setChanged();
            level.setBlock(pos, state.setValue(FULL, false), Block.UPDATE_ALL);
            return ItemInteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable(
                "block.elysiumcomforts.mailbox.empty"), true);
        return ItemInteractionResult.CONSUME;
    }

    /**
     * Write a name on it.
     *
     * <p>The tag is spent and the levels are spent, and neither happens unless
     * the name was one the post will carry — a tag eaten by a refused address is
     * the cheapest possible way to make a player stop using a feature.
     */
    private ItemInteractionResult name(Level level, Player player, ItemStack tag,
                                       MailboxBlockEntity box) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        String written = AddressTagItem.written(tag);
        if (!PostRules.addressable(written)) {
            player.displayClientMessage(Component.translatable(
                    "block.elysiumcomforts.mailbox.unaddressable"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.isCreative() && player.experienceLevel < PostRules.ADDRESS_LEVELS) {
            player.displayClientMessage(Component.translatable(
                    "block.elysiumcomforts.mailbox.levels",
                    PostRules.ADDRESS_LEVELS), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!box.nameIt(written)) {
            return ItemInteractionResult.CONSUME;
        }
        if (!player.isCreative()) {
            player.giveExperienceLevels(-PostRules.ADDRESS_LEVELS);
            tag.shrink(1);
        }
        player.displayClientMessage(Component.translatable(
                "block.elysiumcomforts.mailbox.named", written), true);
        return ItemInteractionResult.CONSUME;
    }

    /**
     * Take a parcel in.
     *
     * <p>The stack leaves the player's hand only once the box has said it took
     * it, and the box refuses while something else is in flight. There is
     * deliberately no branch here that shrinks the stack and then finds out the
     * send failed.
     */
    private ItemInteractionResult post(Level level, Player player, ItemStack parcel,
                                       MailboxBlockEntity box) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (box.address().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "block.elysiumcomforts.mailbox.unnamed"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (box.expecting()) {
            player.displayClientMessage(Component.translatable(
                    "block.elysiumcomforts.mailbox.busy"), true);
            return ItemInteractionResult.CONSUME;
        }
        ItemStack one = parcel.copyWithCount(1);
        if (!box.send(one, level.getGameTime(), PostRules.UNKNOWN_DISTANCE)) {
            return ItemInteractionResult.CONSUME;
        }
        parcel.shrink(1);
        player.displayClientMessage(Component.translatable(
                "block.elysiumcomforts.mailbox.posted",
                PostRules.travelTicks(PostRules.UNKNOWN_DISTANCE) / 20), true);
        return ItemInteractionResult.CONSUME;
    }

    /**
     * Broken.
     *
     * <p>Everything comes out, including the parcel still in the air. A box
     * that kept one would be a box a player could be robbed by.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState now, boolean moved) {
        if (!state.is(now.getBlock())
                && level.getBlockEntity(pos) instanceof MailboxBlockEntity box) {
            NonNullList<ItemStack> all = box.drops();
            if (!all.isEmpty()) {
                Containers.dropContents(level, pos, all);
            }
        }
        super.onRemove(state, level, pos, now, moved);
    }
}
