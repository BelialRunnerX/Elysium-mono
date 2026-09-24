package com.elysium.arcana.network;

import com.elysium.arcana.ElysiumArcana;
import com.elysium.arcana.client.ArcanaClientHooks;
import com.elysium.arcana.transmute.Transmuting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Where the two packets are wired up. */
@EventBusSubscriber(modid = ElysiumArcana.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ArcanaNetwork {

    private ArcanaNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ArcanaPayloads.OpenTransmutations.TYPE,
                ArcanaPayloads.OpenTransmutations.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ArcanaClientHooks.openTransmutations(payload)));
        registrar.playToServer(ArcanaPayloads.ChooseTransmutation.TYPE,
                ArcanaPayloads.ChooseTransmutation.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Component said = Transmuting.condense(player, payload.key(),
                                payload.count());
                        player.displayClientMessage(said, true);
                    }
                }));
    }
}
