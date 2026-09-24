package com.elysium.almanac.registry;

import com.elysium.almanac.ElysiumAlmanac;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a player has already done, kept on the player and sent to their client.
 *
 * <h2>Why this is synced rather than asked for</h2>
 *
 * The quest graph itself is in the jar, identical on both sides, so the only
 * thing a client needs in order to draw the whole book is the list of quest
 * ids that player has finished. Syncing that one list means the book opens
 * instantly with no round trip, and means there is no packet to get wrong —
 * the screen cannot show a stale page because there is no page to be stale.
 *
 * <h2>And why it survives death</h2>
 *
 * Because losing your notes on the way to the Nether is not a difficulty
 * setting, it is an insult. A quest book is a record of what you have done and
 * nothing you did stops being true.
 */
public final class AlmanacAttachments {

    private AlmanacAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    ElysiumAlmanac.MODID);

    /** Every quest this player has finished, in the order they finished them. */
    public static final Supplier<AttachmentType<List<String>>> DONE =
            ATTACHMENTS.register("done", () -> AttachmentType
                    .<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .sync(StreamCodec.of(
                            (buffer, value) -> ByteBufCodecs.STRING_UTF8
                                    .apply(ByteBufCodecs.list())
                                    .encode((RegistryFriendlyByteBuf) buffer, value),
                            buffer -> ByteBufCodecs.STRING_UTF8
                                    .apply(ByteBufCodecs.list())
                                    .decode((RegistryFriendlyByteBuf) buffer)))
                    .copyOnDeath()
                    .build());

    /**
     * How many of each thing this player has killed.
     *
     * <p>A tally rather than a watch, for one reason: a quest asking for three
     * of something a player has already killed two of would otherwise need
     * three more, and the player would have no way of knowing why. Killing
     * counts from the first one, whether or not anything was asking at the
     * time.
     *
     * <p>Kept as a flat list of "id" then "count" rather than a map, because
     * a map codec and a map stream codec are two more things to get wrong for
     * a structure that is never more than a few dozen entries long.
     */
    public static final Supplier<AttachmentType<List<String>>> SLAIN =
            ATTACHMENTS.register("slain", () -> AttachmentType
                    .<List<String>>builder(() -> List.of())
                    .serialize(Codec.STRING.listOf())
                    .sync(StreamCodec.of(
                            (buffer, value) -> ByteBufCodecs.STRING_UTF8
                                    .apply(ByteBufCodecs.list())
                                    .encode((RegistryFriendlyByteBuf) buffer, value),
                            buffer -> ByteBufCodecs.STRING_UTF8
                                    .apply(ByteBufCodecs.list())
                                    .decode((RegistryFriendlyByteBuf) buffer)))
                    .copyOnDeath()
                    .build());

    /** How many of this the player has killed. */
    public static int slain(Player player, String what) {
        List<String> tally = player.getData(SLAIN.get());
        for (int at = 0; at + 1 < tally.size(); at += 2) {
            if (tally.get(at).equals(what)) {
                try {
                    return Integer.parseInt(tally.get(at + 1));
                } catch (NumberFormatException broken) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Count one more.
     *
     * <p>Replaced rather than mutated, for the same reason as the list above:
     * an attachment changed in place is an attachment nothing syncs.
     */
    public static void slew(Player player, String what) {
        List<String> was = player.getData(SLAIN.get());
        List<String> now = new ArrayList<>(was);
        for (int at = 0; at + 1 < now.size(); at += 2) {
            if (now.get(at).equals(what)) {
                now.set(at + 1, Integer.toString(slain(player, what) + 1));
                player.setData(SLAIN.get(), List.copyOf(now));
                return;
            }
        }
        now.add(what);
        now.add("1");
        player.setData(SLAIN.get(), List.copyOf(now));
    }

    /** What this player has finished, as a set. */
    public static Set<String> done(Player player) {
        return new LinkedHashSet<>(player.getData(DONE.get()));
    }

    /** Whether they have finished a particular one. */
    public static boolean has(Player player, String quest) {
        return player.getData(DONE.get()).contains(quest);
    }

    /**
     * Mark one finished. True when it was not already.
     *
     * The list is replaced rather than added to, because an attachment that is
     * mutated in place is an attachment the sync never hears about — the
     * holder has no way of knowing the list somebody handed out has changed.
     */
    public static boolean finish(Player player, String quest) {
        List<String> was = player.getData(DONE.get());
        if (was.contains(quest)) {
            return false;
        }
        List<String> now = new ArrayList<>(was);
        now.add(quest);
        player.setData(DONE.get(), List.copyOf(now));
        return true;
    }
}
