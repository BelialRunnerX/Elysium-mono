package com.elysium.space.gate;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.network.SpacePayloads;
import com.elysium.space.world.GateRules;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The anchor: the block you talk to.
 *
 * <h2>What it does</h2>
 *
 * It stands in the bottom-centre of a ring. When the ring is finished it puts
 * itself on the network; when it is broken it takes itself off. Right-clicking
 * it offers every other gate anybody has finished, and choosing one puts you
 * there.
 *
 * <h2>What it does not do</h2>
 *
 * Charge. There is no fuel, no wear and no cooldown worth the name, because a
 * shortcut a player has to think about is a shortcut they stop using. The cost
 * of the gate was the ring, and it was paid once.
 *
 * <h2>Why the ring is checked on use and not only on placement</h2>
 *
 * Because a builder can knock a block out of the frame with a stray pickaxe
 * and would otherwise find out by arriving nowhere. Checking on every click is
 * sixteen block reads, which is nothing, and it means the gate's answer is
 * always about the gate as it is now.
 */
public class WaygateAnchorBlock extends Block {

    public WaygateAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)
                || !(player instanceof ServerPlayer asking)) {
            return InteractionResult.SUCCESS;
        }
        WaygateRing.Reading ring = WaygateRing.read(server, pos);
        if (!ring.complete()) {
            complain(asking, ring);
            return InteractionResult.CONSUME;
        }

        ResourceLocation here = server.dimension().location();
        WaygateNetwork network = WaygateNetwork.of(server);
        WaygateNetwork.Gate mine = network.at(here, pos);
        if (mine == null) {
            // Finished but never recorded — the ring was completed by placing
            // a frame block rather than the anchor, or this is a world from
            // before the network existed. Record it now rather than telling the
            // player to break something and put it back.
            mine = new WaygateNetwork.Gate(here, pos, "", ring.acrossX());
            network.record(mine);
        } else if (mine.acrossX() != ring.acrossX()) {
            mine = new WaygateNetwork.Gate(here, pos, mine.name(), ring.acrossX());
            network.record(mine);
        }

        List<WaygateNetwork.Gate> destinations = network.others(here, pos);
        if (destinations.isEmpty()) {
            asking.displayClientMessage(Component.translatable("elysiumspace.waygate.alone")
                    .withStyle(ChatFormatting.GRAY), false);
            return InteractionResult.CONSUME;
        }

        List<String> worlds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (WaygateNetwork.Gate gate : destinations) {
            worlds.add(gate.dimension().toString());
            names.add(gate.shown());
            keys.add(gate.key());
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(asking,
                new SpacePayloads.OpenWaygates(worlds, names, keys));
        return InteractionResult.CONSUME;
    }

    /** Says what is wrong with the ring, and where. */
    private static void complain(ServerPlayer asking, WaygateRing.Reading ring) {
        if (!ring.blocked().isEmpty()) {
            BlockPos at = ring.blocked().get(0);
            asking.displayClientMessage(Component.translatable(
                            "elysiumspace.waygate.blocked", ring.blocked().size(),
                            at.getX(), at.getY(), at.getZ())
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        BlockPos at = ring.missing().isEmpty() ? null : ring.missing().get(0);
        asking.displayClientMessage(at == null
                        ? Component.translatable("elysiumspace.waygate.unbuilt")
                        : Component.translatable("elysiumspace.waygate.missing",
                                ring.missing().size(), GateRules.frameBlocks(),
                                at.getX(), at.getY(), at.getZ())
                .withStyle(ChatFormatting.RED), false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        WaygateRing.Reading ring = WaygateRing.read(server, pos);
        if (!ring.complete()) {
            return;
        }
        WaygateNetwork.of(server).record(new WaygateNetwork.Gate(
                server.dimension().location(), pos, "", ring.acrossX()));
        ElysiumSpace.LOGGER.info("a waygate opened at {} in {}", pos,
                server.dimension().location());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState replacement, boolean moving) {
        // Guarded on the block actually changing: a state update on the same
        // block would otherwise take a working gate off the network.
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server) {
            WaygateNetwork.of(server).forget(server.dimension().location(), pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
