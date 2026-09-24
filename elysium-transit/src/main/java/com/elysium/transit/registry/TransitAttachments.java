package com.elysium.transit.registry;

import com.elysium.transit.ElysiumTransit;
import com.elysium.transit.rules.TransitRules;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Where a player has been, kept on the player.
 *
 * <h2>Why the network is per player and not per world</h2>
 *
 * A shared list of every waystone anybody has ever bound is a map handed to
 * somebody who has not explored. Binding one is the reward for having walked
 * there once, and it is the only thing in this module that cannot be bought
 * with power.
 *
 * <h2>And why it survives death</h2>
 *
 * Because the alternative is walking back to every waystone you have ever
 * found, which is a punishment aimed squarely at the thing the module exists
 * to remove. The same reasoning, and the same one line of code, as the
 * almanac's record of what you have done.
 */
public final class TransitAttachments {

    private TransitAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    ElysiumTransit.MODID);

    /**
     * Every waystone this player has bound: {@code world|x|y|z|name}.
     *
     * <p>The name is carried along rather than read back off the block,
     * because a waystone in an unloaded chunk in another world has no block
     * entity to ask and a menu that said "(unknown)" for half its rows would
     * be a menu nobody could use.
     */
    public static final Supplier<AttachmentType<List<String>>> KNOWN =
            ATTACHMENTS.register("known", () -> AttachmentType
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

    public static List<String> known(Player player) {
        return player.getData(KNOWN.get());
    }

    /** The place part of a remembered row. */
    public static TransitRules.Waypoint placeOf(String entry) {
        return TransitRules.Waypoint.unpack(entry);
    }

    /** The name part, or the coordinates if it was never given one. */
    public static String nameOf(String entry) {
        String[] parts = entry.split("\\|");
        if (parts.length >= 5 && !parts[4].isBlank()) {
            return parts[4];
        }
        TransitRules.Waypoint place = placeOf(entry);
        return place == null ? entry : place.x() + ", " + place.z();
    }

    /** One remembered row, out of a place and a name. */
    public static String entry(TransitRules.Waypoint place, String name) {
        return place.pack() + "|" + (name == null ? "" : name.replace('|', ' '));
    }

    /**
     * Remember a waystone. True when it was not already remembered.
     *
     * <p>The list is replaced rather than added to: an attachment mutated in
     * place is one the sync never hears about, and the client's menu would
     * silently stop growing while nothing looked wrong on the server.
     */
    public static boolean learn(Player player, TransitRules.Waypoint place, String name) {
        String row = entry(place, name);
        List<String> was = known(player);
        String packed = place.pack();
        List<String> now = new ArrayList<>();
        boolean had = false;
        for (String old : was) {
            TransitRules.Waypoint where = placeOf(old);
            if (where != null && where.pack().equals(packed)) {
                had = true;
                now.add(row);            // the name may have changed since
            } else {
                now.add(old);
            }
        }
        if (!had) {
            now.add(row);
        }
        player.setData(KNOWN.get(), List.copyOf(now));
        return !had;
    }

    /** Forget one, for when the stone is gone. */
    public static void forget(Player player, TransitRules.Waypoint place) {
        String packed = place.pack();
        List<String> now = new ArrayList<>();
        for (String old : known(player)) {
            TransitRules.Waypoint where = placeOf(old);
            if (where == null || !where.pack().equals(packed)) {
                now.add(old);
            }
        }
        player.setData(KNOWN.get(), List.copyOf(now));
    }
}
