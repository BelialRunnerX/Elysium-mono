package com.elysium.almanac.network;

import com.elysium.almanac.ElysiumAlmanac;
import com.elysium.almanac.event.AlmanacRewards;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The one message the book sends: "pay me for this step".
 *
 * <p>Everything else the book shows is already on the client — the graph is in
 * the jar and what the player has done is a synced attachment — so this is the
 * only round trip in the module. The answer is not a packet either: the server
 * marks the reward taken, and the attachment sync carries that back.
 */
@EventBusSubscriber(modid = ElysiumAlmanac.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AlmanacNetwork {

    private AlmanacNetwork() {
    }

    /** Client to server: take the reward for a finished quest. */
    public record AlmanacClaim(String quest) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<AlmanacClaim> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(ElysiumAlmanac.MODID, "claim"));

        public static final StreamCodec<ByteBuf, AlmanacClaim> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AlmanacClaim::quest,
                        AlmanacClaim::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AlmanacClaim.TYPE, AlmanacClaim.STREAM_CODEC,
                AlmanacNetwork::onClaim);
    }

    private static void onClaim(AlmanacClaim payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        String why = AlmanacRewards.claim(player, payload.quest());
        if (why != null) {
            player.displayClientMessage(Component.translatable(why)
                    .withStyle(ChatFormatting.RED), true);
        }
    }
}
