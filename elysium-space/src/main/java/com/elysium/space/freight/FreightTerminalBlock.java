package com.elysium.space.freight;

import com.elysium.space.registry.SpaceBlockEntities;
import com.elysium.space.world.FreightRules;
import com.elysium.space.world.SpaceRoute;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The block you load, fuel and point at another world.
 *
 * <h2>Three ways to talk to it</h2>
 *
 * <ul>
 *   <li><b>With a bucket</b> — pour rocket fuel in, or take it out.</li>
 *   <li><b>With an empty hand</b> — the chest screen: load it, or take back
 *       what has arrived.</li>
 *   <li><b>Crouching</b> — the destination list: every other terminal in the
 *       game, what the run would cost in fuel and how long it would take, and
 *       choosing one launches.</li>
 * </ul>
 *
 * There is deliberately no fourth. Everything else a logistics system needs —
 * filling it, emptying it, scheduling it — is done by the hoppers and pipes
 * and redstone a player already has, because a terminal is a container and
 * everything in the game already knows what to do with one of those.
 */
public class FreightTerminalBlock extends BaseEntityBlock {

    public FreightTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FreightTerminalBlock::new);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FreightTerminalBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FreightTerminalBlockEntity terminal)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FluidUtil.getFluidHandler(held).isPresent()) {
            boolean moved = FluidUtil.interactWithFluidHandler(player, hand,
                    terminal.fuelTank());
            if (moved) {
                terminal.setChanged();
            }
            return moved ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof FreightTerminalBlockEntity terminal)
                || !(player instanceof ServerPlayer server)
                || !(level instanceof ServerLevel here)) {
            return InteractionResult.PASS;
        }
        FreightNetwork network = FreightNetwork.of(here.getServer());
        // Every time somebody talks to it, it is in the network: a terminal
        // that had to be registered by some separate act would be a terminal
        // players forget to register.
        network.record(new FreightNetwork.Terminal(here.dimension().location(), pos));
        if (player.isSecondaryUseActive()) {
            offer(server, here, pos, terminal, network);
        } else {
            server.openMenu(terminal);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Sends the player the list of places this load could go.
     *
     * Everything is worked out here, on the server, and the client is sent
     * words: the cost and the time are facts about the world and a client that
     * computed them would be a client that could be wrong about them.
     */
    private static void offer(ServerPlayer player, ServerLevel level, BlockPos pos,
                              FreightTerminalBlockEntity terminal,
                              FreightNetwork network) {
        FreightNetwork.Terminal here =
                new FreightNetwork.Terminal(level.dimension().location(), pos);
        List<String> names = new ArrayList<>();
        List<String> costs = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        int stacks = terminal.stacksAboard();
        for (FreightNetwork.Terminal other : network.all()) {
            if (other.key().equals(here.key())) {
                continue;
            }
            int route = SpaceRoute.fuelFor(here.world(), other.world(),
                    SpaceRoute.Fuel.FUEL);
            if (route < 0) {
                continue;
            }
            int need = FreightRules.fuelFor(route, Math.max(1, stacks));
            names.add(other.shown());
            costs.add(need + " mB, " + FreightRules.flightSeconds(
                    SpaceRoute.gap(here.world(), other.world())) + "s");
            keys.add(other.key());
        }
        PacketDistributor.sendToPlayer(player,
                new com.elysium.space.network.SpacePayloads.OpenFreight(
                        terminal.fuelTank().getFluidAmount(), stacks, names, costs, keys));
    }

    /**
     * Breaking it drops what is in it.
     *
     * What is already in the air is not dropped and is not lost either: it is
     * addressed to a terminal that no longer exists, and the sweep sends it
     * home when it gets there.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next,
                            boolean moved) {
        if (!state.is(next.getBlock())) {
            if (level.getBlockEntity(pos) instanceof FreightTerminalBlockEntity terminal) {
                Containers.dropContents(level, pos, terminal);
            }
            if (level instanceof ServerLevel here) {
                FreightNetwork.of(here.getServer())
                        .forget(here.dimension().location(), pos);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    /** Placing it puts it on the map, so it can be sent to before it is ever used. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel here) {
            FreightNetwork.of(here.getServer()).record(
                    new FreightNetwork.Terminal(here.dimension().location(), pos));
            if (placer instanceof Player player) {
                player.displayClientMessage(Component.translatable(
                        "elysiumspace.freight.placed"), true);
            }
        }
    }

    /** Comparators read how full it is, so a supply line can be built out of redstone. */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof FreightTerminalBlockEntity terminal) {
            return net.minecraft.world.inventory.AbstractContainerMenu
                    .getRedstoneSignalFromContainer(terminal);
        }
        return 0;
    }

    /** The type this block's entity is, for the registry that needs both. */
    public static java.util.function.Supplier<?> type() {
        return SpaceBlockEntities.FREIGHT_TERMINAL;
    }
}
