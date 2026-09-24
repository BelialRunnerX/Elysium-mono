package com.elysium.space.network;

import com.elysium.space.ElysiumSpace;
import com.elysium.space.client.SpaceClientHooks;
import com.elysium.space.entity.RocketEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ElysiumSpace.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class SpaceNetwork {

    private SpaceNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SpacePayloads.OpenDestinations.TYPE, SpacePayloads.OpenDestinations.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> SpaceClientHooks.openDestinations(payload)));
        registrar.playToServer(SpacePayloads.ChooseDestination.TYPE, SpacePayloads.ChooseDestination.STREAM_CODEC,
                SpaceNetwork::onChoose);
        registrar.playToClient(SpacePayloads.OpenWaygates.TYPE, SpacePayloads.OpenWaygates.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SpaceClientHooks.openWaygates(payload)));
        registrar.playToServer(SpacePayloads.ChooseWaygate.TYPE, SpacePayloads.ChooseWaygate.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        net.minecraft.network.chat.Component refused =
                                com.elysium.space.gate.WaygateTravel.send(player,
                                        payload.key());
                        if (refused != null) {
                            player.displayClientMessage(refused.copy().withStyle(
                                    net.minecraft.ChatFormatting.RED), false);
                        }
                    }
                }));
        registrar.playToClient(SpacePayloads.OpenFreight.TYPE,
                SpacePayloads.OpenFreight.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SpaceClientHooks.openFreight(payload)));
        registrar.playToServer(SpacePayloads.ChooseFreight.TYPE,
                SpacePayloads.ChooseFreight.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                        // Re-checked in full on the server: the packet says
                        // which terminal and where to, and everything else -
                        // that it is a terminal, that it is loaded, that the
                        // fuel covers it - is decided here.
                        if (player.blockPosition().distSqr(payload.from()) > 64.0D * 64.0D) {
                            return;
                        }
                        net.minecraft.network.chat.Component refused =
                                com.elysium.space.freight.FreightDispatch.launch(
                                        level, payload.from(), payload.key(), player);
                        if (refused != null) {
                            player.displayClientMessage(refused.copy().withStyle(
                                    net.minecraft.ChatFormatting.RED), false);
                        }
                    }
                }));
        registrar.playToServer(SpacePayloads.Launch.TYPE, SpacePayloads.Launch.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player
                            && player.getVehicle() instanceof RocketEntity rocket) {
                        rocket.tryLaunch(player);
                    }
                }));
        registrar.playToServer(SpacePayloads.JetThrust.TYPE, SpacePayloads.JetThrust.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        com.elysium.space.event.JetEvents.setThrusting(player,
                                payload.on() && !com.elysium.space.item.JetSuitItem.worn(player).isEmpty());
                    }
                }));
        registrar.playToServer(SpacePayloads.JetMode.TYPE, SpacePayloads.JetMode.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        net.minecraft.world.item.ItemStack jet = com.elysium.space.item.JetSuitItem.worn(player);
                        if (!jet.isEmpty()) {
                            com.elysium.space.world.JetRules.Mode next =
                                    com.elysium.space.world.JetRules.next(com.elysium.space.item.JetSuitItem.mode(jet));
                            com.elysium.space.item.JetSuitItem.setMode(jet, next);
                            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                    "elysiumspace.jet.mode", net.minecraft.network.chat.Component.translatable(
                                            "elysiumspace.jet.mode." + next.name().toLowerCase(java.util.Locale.ROOT))), true);
                        }
                    }
                }));
    }

    /** The rocket re-checks everything; this only finds it. */
    private static void onChoose(SpacePayloads.ChooseDestination payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.getVehicle() instanceof RocketEntity rocket) {
                rocket.travelTo(player, payload.destination());
            }
        });
    }
}
