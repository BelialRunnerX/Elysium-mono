package com.elysium.lib.network;

import com.elysium.lib.ElysiumLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The three packets the character system needs.
 *
 * Every one of them is two fields, both of a type {@code ByteBufCodecs}
 * defines outright, because {@code StreamCodec.composite} is the one shape of
 * this API that is documented and stable. A wider packet would mean either a
 * hand-written codec or a field order that has to match on both sides — and a
 * networking mismatch is the kind of bug that only appears on someone else's
 * server, three weeks later.
 *
 * The character sheet therefore travels as one packed string rather than as
 * fifteen typed fields. {@link CharacterSheet} owns that format so exactly one
 * place has to agree with itself.
 */
public final class ElysiumPayloads {

    private ElysiumPayloads() {
    }

    /**
     * Server → client: open the character screen, with everything it needs to
     * draw itself.
     *
     * @param sheet   packed by {@link CharacterSheet#pack}
     * @param unspent free points the player has yet to assign
     */
    public record OpenCharacter(String sheet, int unspent) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<OpenCharacter> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "open_character"));

        public static final StreamCodec<ByteBuf, OpenCharacter> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, OpenCharacter::sheet,
                        ByteBufCodecs.VAR_INT, OpenCharacter::unspent,
                        OpenCharacter::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the same sheet, without opening anything.
     *
     * <h2>Why this is not just {@code OpenCharacter}</h2>
     *
     * The HUD needs the sheet continuously — a Favor meter that only refreshes
     * when a player opens their character screen is a meter showing whatever
     * was true the last time they looked, which is worse than showing nothing,
     * because it looks live. But a packet that pushes the sheet <em>and</em>
     * takes over the screen cannot be sent on a timer: it would fling the
     * character screen open in the middle of a fight.
     *
     * So: same payload shape, same handler on the way in, one difference on
     * arrival. {@code OpenCharacter} updates the cache and opens the screen;
     * this updates the cache and stops.
     *
     * @param sheet   packed by {@link CharacterSheet#pack}
     * @param unspent free points the player has yet to assign
     */
    public record SyncCharacter(String sheet, int unspent) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SyncCharacter> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "sync_character"));

        public static final StreamCodec<ByteBuf, SyncCharacter> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SyncCharacter::sheet,
                        ByteBufCodecs.VAR_INT, SyncCharacter::unspent,
                        SyncCharacter::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: this is who I am.
     *
     * Both names are validated server-side before anything is stored. A packet
     * is a claim, never an instruction — a client that sends "race=emperor"
     * gets ignored, not obeyed.
     */
    public record ChooseCharacter(String race, String job) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ChooseCharacter> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "choose_character"));

        public static final StreamCodec<ByteBuf, ChooseCharacter> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ChooseCharacter::race,
                        ByteBufCodecs.STRING_UTF8, ChooseCharacter::job,
                        ChooseCharacter::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client → server: spend free points.
     *
     * The amount is sent rather than assumed, and clamped server-side against
     * what the player actually has.
     */
    public record SpendPoints(String stat, int amount) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<SpendPoints> TYPE =
                new CustomPacketPayload.Type<>(
                        ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, "spend_points"));

        public static final StreamCodec<ByteBuf, SpendPoints> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, SpendPoints::stat,
                        ByteBufCodecs.VAR_INT, SpendPoints::amount,
                        SpendPoints::new);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
