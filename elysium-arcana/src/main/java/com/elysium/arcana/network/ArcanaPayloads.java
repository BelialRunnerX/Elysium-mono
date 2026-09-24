package com.elysium.arcana.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Two packets: here is the catalogue, and here is the row I picked. */
public final class ArcanaPayloads {

    private ArcanaPayloads() {
    }

    /**
     * Server → client: everything this player knows, with a price against each.
     *
     * @param keys   the item ids, which is what comes back
     * @param names  each one's translation key
     * @param prices what one of each costs
     * @param motes  what the player has to spend
     */
    public record OpenTransmutations(List<String> keys, List<String> names,
                                     List<Integer> prices, long motes)
            implements CustomPacketPayload {

        public static final Type<OpenTransmutations> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("elysiumarcana",
                        "open_transmutations"));

        public static final StreamCodec<ByteBuf, OpenTransmutations> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenTransmutations::keys,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenTransmutations::names,
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                        OpenTransmutations::prices,
                        ByteBufCodecs.VAR_LONG, OpenTransmutations::motes,
                        OpenTransmutations::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: this one, this many.
     *
     * <p>Carries no price. What a thing is worth is the one number this whole
     * module is about, and it is decided on the server every time.
     */
    public record ChooseTransmutation(String key, int count)
            implements CustomPacketPayload {

        public static final Type<ChooseTransmutation> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("elysiumarcana",
                        "choose_transmutation"));

        public static final StreamCodec<ByteBuf, ChooseTransmutation> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ChooseTransmutation::key,
                        ByteBufCodecs.VAR_INT, ChooseTransmutation::count,
                        ChooseTransmutation::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
