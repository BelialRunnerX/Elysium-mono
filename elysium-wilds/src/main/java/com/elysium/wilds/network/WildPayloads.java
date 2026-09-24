package com.elysium.wilds.network;

import com.elysium.wilds.ElysiumWilds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Three messages: here is the list, find me this, here it is. */
public final class WildPayloads {

    private WildPayloads() {
    }

    /** Server to client: every place this world knows about. */
    public record OfferPlaces(List<String> places) implements CustomPacketPayload {

        public static final Type<OfferPlaces> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, "offer_places"));

        public static final StreamCodec<RegistryFriendlyByteBuf, OfferPlaces> STREAM =
                StreamCodec.composite(
                        ByteBufCodecs.collection(java.util.ArrayList::new,
                                ByteBufCodecs.STRING_UTF8), OfferPlaces::places,
                        OfferPlaces::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: look for this one. */
    public record LookFor(String place) implements CustomPacketPayload {

        public static final Type<LookFor> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, "look_for"));

        public static final StreamCodec<RegistryFriendlyByteBuf, LookFor> STREAM =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, LookFor::place,
                        LookFor::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: what came of it.
     *
     * <p>{@code found} is separate from the position rather than implied by a
     * sentinel, because "there is none within the distance I am allowed to
     * look" is a real answer a player needs to be told plainly — and a compass
     * that answers a failed search with a position is a compass that sends
     * somebody walking to nothing.
     */
    public record Found(String place, boolean found, BlockPos where,
                        int blocks) implements CustomPacketPayload {

        public static final Type<Found> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumWilds.MODID, "found"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Found> STREAM =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Found::place,
                        ByteBufCodecs.BOOL, Found::found,
                        BlockPos.STREAM_CODEC, Found::where,
                        ByteBufCodecs.VAR_INT, Found::blocks,
                        Found::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
