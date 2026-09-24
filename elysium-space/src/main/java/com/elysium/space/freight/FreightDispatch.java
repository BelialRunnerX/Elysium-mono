package com.elysium.space.freight;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.registry.SpaceBlocks;
import com.elysium.space.registry.SpaceFluids;
import com.elysium.space.world.FreightRules;
import com.elysium.space.world.SpaceRoute;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/**
 * Launching a cargo rocket, and landing one.
 *
 * <h2>The one clock</h2>
 *
 * Every world runs its own game time, and a shipment crosses worlds — so the
 * only honest clock is one of them, and it is the overworld's. Everything
 * about a flight is measured against it and the landing sweep runs there, once
 * a second, whatever world the cargo is actually going to.
 *
 * <h2>Nothing is ever lost</h2>
 *
 * Three things can go wrong on arrival and none of them destroys anything. A
 * destination whose chunk is not loaded is loaded for the moment it takes to
 * put the crate down. A destination with no room holds the rocket in orbit and
 * tries again. A destination that is no longer a terminal — somebody mined it
 * — sends the rocket home with its cargo still aboard.
 */
@EventBusSubscriber(modid = ElysiumSpace.MODID)
public final class FreightDispatch {

    private FreightDispatch() {
    }

    /** How often the sky is swept for arrivals. Once a second is ample. */
    public static final int SWEEP = 20;

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // The overworld's tick and nobody else's: one clock, one sweep, and no
        // shipment landed five times because five worlds were loaded.
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        long now = level.getGameTime();
        if (now % SWEEP != 0L) {
            return;
        }
        MinecraftServer server = level.getServer();
        FreightNetwork network = FreightNetwork.of(server);
        for (FreightNetwork.Shipment shipment : network.flying()) {
            if (!FreightRules.landed(shipment.launchedAt(), now, shipment.flight())) {
                continue;
            }
            land(server, network, shipment, now);
        }
    }

    /** Puts one shipment down, or decides what to do instead. */
    private static void land(MinecraftServer server, FreightNetwork network,
                             FreightNetwork.Shipment shipment, long now) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                shipment.to().dimension()));
        if (level == null) {
            // That world is not on this server any more. Home it goes.
            network.replace(shipment, shipment.returned(now));
            return;
        }
        BlockPos at = shipment.to().at();
        // Loaded on purpose, for the moment it takes: cargo that only arrives
        // when somebody is standing there is cargo a supply line cannot rely on.
        level.getChunk(at);
        if (!(level.getBlockEntity(at) instanceof FreightTerminalBlockEntity terminal)) {
            network.forget(shipment.to().dimension(), at);
            network.replace(shipment, shipment.returned(now));
            ElysiumSpace.LOGGER.info("a freight terminal at {} is gone; its cargo is "
                    + "going home", at);
            return;
        }
        List<ItemStack> over = terminal.land(shipment.cargo());
        if (!over.isEmpty()) {
            // No room. It holds rather than spilling, and tries again shortly.
            network.replace(shipment, new FreightNetwork.Shipment(shipment.from(),
                    shipment.to(), over, now - shipment.flight() + FreightRules.HOLD_TICKS,
                    shipment.flight()));
            return;
        }
        network.settle(shipment);
        arrive(level, at);
    }

    /**
     * Sends a terminal's load somewhere, if it can.
     *
     * @return null when the rocket went, or the reason it did not
     */
    public static Component launch(ServerLevel level, BlockPos pos, String address,
                                   ServerPlayer sender) {
        if (!(level.getBlockEntity(pos) instanceof FreightTerminalBlockEntity terminal)) {
            return Component.translatable("elysiumspace.freight.gone");
        }
        MinecraftServer server = level.getServer();
        FreightNetwork network = FreightNetwork.of(server);
        FreightNetwork.Terminal here =
                new FreightNetwork.Terminal(level.dimension().location(), pos);
        network.record(here);
        FreightNetwork.Terminal there = network.terminal(address);
        if (there == null) {
            return Component.translatable("elysiumspace.freight.nowhere");
        }
        if (there.key().equals(here.key())) {
            return Component.translatable("elysiumspace.freight.itself");
        }
        if (terminal.empty()) {
            return Component.translatable("elysiumspace.freight.nothing");
        }
        if (network.inFlightFrom(here) >= FreightRules.IN_FLIGHT) {
            return Component.translatable("elysiumspace.freight.busy",
                    FreightRules.IN_FLIGHT);
        }
        int route = SpaceRoute.fuelFor(here.world(), there.world(),
                SpaceRoute.Fuel.FUEL);
        if (route < 0) {
            return Component.translatable("elysiumspace.freight.unknown");
        }
        int need = FreightRules.fuelFor(route, terminal.stacksAboard());
        if (terminal.fuelTank().getFluidAmount() < need) {
            return Component.translatable("elysiumspace.freight.fuel", need,
                    terminal.fuelTank().getFluidAmount());
        }
        terminal.fuelTank().drain(need, IFluidHandler.FluidAction.EXECUTE);
        int flight = FreightRules.flightTicks(
                SpaceRoute.gap(here.world(), there.world()));
        network.launch(new FreightNetwork.Shipment(here, there, terminal.unload(),
                server.overworld().getGameTime(), flight));
        terminal.setChanged();
        depart(level, pos);
        if (sender != null) {
            sender.displayClientMessage(Component.translatable(
                            "elysiumspace.freight.away", there.shown(),
                            FreightRules.flightSeconds(
                                    SpaceRoute.gap(here.world(), there.world())), need)
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return null;
    }

    /** What leaving looks and sounds like from the pad. */
    private static void depart(ServerLevel level, BlockPos pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                40, 0.3D, 0.4D, 0.3D, 0.02D);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                25, 0.2D, 0.1D, 0.2D, 0.05D);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    /** And what arriving looks like, for anybody who happens to be there. */
    private static void arrive(ServerLevel level, BlockPos pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                30, 0.4D, 0.2D, 0.4D, 0.01D);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    /** Whether this is the fluid a terminal will fly on. */
    public static boolean isFuel(net.minecraft.world.level.material.Fluid fluid) {
        return fluid.isSame(SpaceFluids.FUEL.source.get());
    }

    /** Kept so the block can be named from one place. */
    public static net.minecraft.world.level.block.Block terminalBlock() {
        return SpaceBlocks.FREIGHT_TERMINAL.get();
    }
}
