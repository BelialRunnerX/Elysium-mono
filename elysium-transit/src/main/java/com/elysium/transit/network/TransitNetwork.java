package com.elysium.transit.network;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.client.TransitClientHooks;
import com.elysium.transit.travel.WaystoneTravel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Where the two packets are wired up. */
@EventBusSubscriber(modid = ElysiumTransit.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class TransitNetwork {

    private TransitNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TransitPayloads.OpenWaystones.TYPE,
                TransitPayloads.OpenWaystones.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> TransitClientHooks.openWaystones(payload)));
        registrar.playToServer(TransitPayloads.ChooseWaystone.TYPE,
                TransitPayloads.ChooseWaystone.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Component refused = WaystoneTravel.jump(player, payload.from(),
                                payload.key());
                        if (refused != null) {
                            player.displayClientMessage(
                                    refused.copy().withStyle(ChatFormatting.RED), false);
                        }
                    }
                }));
    }
}
