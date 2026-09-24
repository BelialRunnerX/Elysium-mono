package com.elysium.space.network;

import com.elysium.space.ElysiumSpace;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The rocket's two packets. */
public final class SpacePayloads {

    private SpacePayloads() {
    }

    /** Server to pilot: the rocket is above the world; these are the places the fuel reaches. */
    public record OpenDestinations(String from, int fuel, int tank, String motor, List<String> options) implements CustomPacketPayload {

        public static final Type<OpenDestinations> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "open_destinations"));

        public static final StreamCodec<ByteBuf, OpenDestinations> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenDestinations::from,
                ByteBufCodecs.VAR_INT, OpenDestinations::fuel,
                ByteBufCodecs.VAR_INT, OpenDestinations::tank,
                ByteBufCodecs.stringUtf8(32), OpenDestinations::motor,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)), OpenDestinations::options,
                OpenDestinations::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Pilot to server: go there ("" to land where you are). Checked in full on arrival. */
    /**
     * Every gate a waygate can reach, sent when its anchor is clicked.
     *
     * Three parallel lists rather than a list of records, because the stream
     * codec for a record of a record is a great deal of ceremony for three
     * strings and this packet is read in one place.
     */
    public record OpenWaygates(List<String> worlds, List<String> names,
                               List<String> keys) implements CustomPacketPayload {

        public static final Type<OpenWaygates> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("elysiumspace", "open_waygates"));

        public static final StreamCodec<ByteBuf, OpenWaygates> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenWaygates::worlds,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenWaygates::names,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenWaygates::keys,
                        OpenWaygates::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The gate a traveller picked, by its address. */
    public record ChooseWaygate(String key) implements CustomPacketPayload {

        public static final Type<ChooseWaygate> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("elysiumspace", "choose_waygate"));

        public static final StreamCodec<ByteBuf, ChooseWaygate> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ChooseWaygate::key,
                        ChooseWaygate::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ChooseDestination(String destination) implements CustomPacketPayload {

        public static final Type<ChooseDestination> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "choose_destination"));

        public static final StreamCodec<ByteBuf, ChooseDestination> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), ChooseDestination::destination, ChooseDestination::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server to player: every terminal this load could be sent to.
     *
     * Three parallel lists again, and the costs arrive as words rather than as
     * numbers: what a run costs is a fact about the server's world table, and
     * a client that worked it out itself could disagree with the server that
     * is about to charge for it.
     */
    public record OpenFreight(int fuel, int stacks, List<String> names,
                              List<String> costs,
                              List<String> keys) implements CustomPacketPayload {

        public static final Type<OpenFreight> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID,
                        "open_freight"));

        public static final StreamCodec<ByteBuf, OpenFreight> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, OpenFreight::fuel,
                        ByteBufCodecs.VAR_INT, OpenFreight::stacks,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenFreight::names,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenFreight::costs,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        OpenFreight::keys,
                        OpenFreight::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Player to server: send this terminal's load there.
     *
     * Carries the terminal being sent from as well as the one being sent to,
     * because the player may have walked away from the block by the time they
     * choose, and a server that guessed which terminal they meant would
     * sometimes empty the wrong one.
     */
    public record ChooseFreight(net.minecraft.core.BlockPos from,
                                String key) implements CustomPacketPayload {

        public static final Type<ChooseFreight> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID,
                        "choose_freight"));

        public static final StreamCodec<ByteBuf, ChooseFreight> STREAM_CODEC =
                StreamCodec.composite(
                        net.minecraft.network.codec.ByteBufCodecs.fromCodec(
                                net.minecraft.core.BlockPos.CODEC), ChooseFreight::from,
                        ByteBufCodecs.stringUtf8(128), ChooseFreight::key,
                        ChooseFreight::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Pilot to server: start the countdown. Carries nothing; the server finds the rocket. */
    public record Launch() implements CustomPacketPayload {

        public static final Type<Launch> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "launch"));

        public static final StreamCodec<ByteBuf, Launch> STREAM_CODEC = StreamCodec.unit(new Launch());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the wearer has started or stopped holding jump in a jet suit. */
    public record JetThrust(boolean on) implements CustomPacketPayload {

        public static final Type<JetThrust> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "jet_thrust"));

        public static final StreamCodec<ByteBuf, JetThrust> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, JetThrust::on, JetThrust::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: cycle the worn jet suit's mode. */
    public record JetMode() implements CustomPacketPayload {

        public static final Type<JetMode> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(ElysiumSpace.MODID, "jet_mode"));

        public static final StreamCodec<ByteBuf, JetMode> STREAM_CODEC = StreamCodec.unit(new JetMode());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
