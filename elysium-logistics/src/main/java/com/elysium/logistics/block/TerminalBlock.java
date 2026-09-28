package com.elysium.logistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * A terminal: the one place a player talks to the factory.
 *
 * <h2>Why it asks for a thing rather than showing a catalogue</h2>
 *
 * Every storage mod this module replaces answers autocrafting with a screen —
 * a searchable grid of everything in the world, a number box and a confirm
 * button. It is a good screen and it is also the reason nobody can explain
 * autocrafting to a new player in one sentence.
 *
 * <p>This asks one question instead: <em>hold up what you want, and it will
 * be made</em>. Right-click with an item for a stack of it, crouch-right-click
 * for one. The item in your hand is the request, so there is nothing to search
 * and nothing to learn, and what comes back is a sentence: how many steps, how
 * many machine-runs, or exactly why not.
 *
 * <p>A screen is a strict addition to this and can be built on top of it later
 * without changing a line of the machinery behind it, which is the point of
 * the terminal being a block of its own rather than a face of the core.
 */
public class TerminalBlock extends Block {

    public static final MapCodec<TerminalBlock> CODEC = simpleCodec(TerminalBlock::new);

    public TerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // An empty hand asks the core what it has; the game only routes it
        // to useWithoutItem when this passes, so anything else here is a
        // terminal that never answers an empty hand.
        if (held.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        AssemblyCoreBlock.AssemblyCoreBlockEntity core = coreNear(level, pos);
        if (core == null) {
            player.displayClientMessage(
                    Component.translatable("elysiumlogistics.terminal.coreless"), true);
            return ItemInteractionResult.CONSUME;
        }
        // A stack of it, or one of it if you crouch. Two gestures, no screen,
        // and the second is the one you want when the thing is expensive.
        long count = player.isShiftKeyDown() ? 1 : held.getMaxStackSize();
        player.displayClientMessage(core.request(level, held, count), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            AssemblyCoreBlock.AssemblyCoreBlockEntity core = coreNear(level, pos);
            player.displayClientMessage(core == null
                    ? Component.translatable("elysiumlogistics.terminal.coreless")
                    : core.says(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The core on this terminal's bus.
     *
     * <p>Found by walking rather than by being adjacent, so a terminal can go
     * where a player wants it — by the door, in the hall, three rooms from the
     * machinery — which is the only reason to have a separate block at all.
     */
    private static AssemblyCoreBlock.AssemblyCoreBlockEntity coreNear(Level level,
                                                                      BlockPos pos) {
        List<BlockPos> found = LogisticNetwork.along(level, pos,
                AssemblyCoreBlock.AssemblyCoreBlockEntity.class);
        for (BlockPos where : found) {
            if (level.getBlockEntity(where)
                    instanceof AssemblyCoreBlock.AssemblyCoreBlockEntity core) {
                return core;
            }
        }
        return null;
    }
}
