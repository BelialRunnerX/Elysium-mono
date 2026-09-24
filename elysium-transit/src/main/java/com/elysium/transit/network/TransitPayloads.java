package com.elysium.transit.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Two packets: here is the menu, and here is the row I picked.
 *
 * <p>Both are lists of types {@code ByteBufCodecs} defines outright, for the
 * same reason every other packet in this pack is: a hand-written codec is a
 * field order that has to match on two sides, and a networking mismatch is the
 * kind of bug that only appears on somebody else's server three weeks later.
 */
public final class TransitPayloads {

    private TransitPayloads() {
    }

    /**
     * Server → client: every place this player may jump to from here.
     *
     * @param keys   the packed waypoints, which is what comes back
     * @param names  what each is called
     * @param worlds which world each is in
     * @param prices what each costs from where the player is standing
     * @param waits  ticks until each becomes affordable; 0 when it already is
     */
    public record OpenWaystones(List<String> keys, List<String> names,
                                List<String> worlds, List<Integer> prices,
                                List<Integer> waits) implements CustomPacketPayload {

        public static final Type<OpenWaystones> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("elysiumtransit", "open_waystones"));

        public static final StreamCodec<ByteBuf, OpenWaystones> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenWaystones::keys,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenWaystones::names,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenWaystones::worlds,
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                        OpenWaystones::prices,
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()),
                        OpenWaystones::waits,
                        OpenWaystones::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: from this stone, to that place.
     *
     * <p>Carries no price and no name. Everything that made the row legal is
     * decided again on the server, because a menu is a convenience and never
     * an authority.
     */
    public record ChooseWaystone(BlockPos from, String key)
            implements CustomPacketPayload {

        public static final Type<ChooseWaystone> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("elysiumtransit",
                        "choose_waystone"));

        public static final StreamCodec<ByteBuf, ChooseWaystone> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VECTOR3F.map(
                                v -> new BlockPos((int) v.x(), (int) v.y(), (int) v.z()),
                                p -> new org.joml.Vector3f(p.getX(), p.getY(), p.getZ())),
                        ChooseWaystone::from,
                        ByteBufCodecs.STRING_UTF8, ChooseWaystone::key,
                        ChooseWaystone::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
