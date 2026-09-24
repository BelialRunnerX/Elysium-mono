package com.elysium.arcana.registry;

import com.elysium.arcana.ElysiumArcana;
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
 * What a player has dissolved, and what they have left over.
 *
 * <h2>Two lists, and why neither is shared</h2>
 *
 * Knowing a thing is the reward for having held one. A server-wide list of
 * everything anybody has ever dissolved is a catalogue handed to somebody who
 * has done nothing, and it turns the first player to find a diamond into
 * everybody's supplier.
 *
 * <p>Both survive death, for the same reason the almanac's record does:
 * nothing you have done stops being true because you fell down a hole.
 */
public final class ArcanaAttachments {

    private ArcanaAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    ElysiumArcana.MODID);

    /** What is left over from dissolving things. */
    public static final Supplier<AttachmentType<Long>> MOTES =
            ATTACHMENTS.register("motes", () -> AttachmentType
                    .<Long>builder(() -> 0L)
                    .serialize(Codec.LONG)
                    .sync(StreamCodec.of(
                            (buffer, value) -> ByteBufCodecs.VAR_LONG
                                    .encode((RegistryFriendlyByteBuf) buffer, value),
                            buffer -> ByteBufCodecs.VAR_LONG
                                    .decode((RegistryFriendlyByteBuf) buffer)))
                    .copyOnDeath()
                    .build());

    /** Every item this player has dissolved at least one of. */
    public static final Supplier<AttachmentType<List<String>>> LEARNED =
            ATTACHMENTS.register("learned", () -> AttachmentType
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

    public static long motes(Player player) {
        return player.getData(MOTES.get());
    }

    /**
     * Change a balance, and refuse to take it below nothing.
     *
     * <p>A balance that can go negative is a balance a player can spend twice
     * by spending it in two places in the same tick, and the one place to stop
     * that is the one place it is written.
     *
     * @return whether it happened
     */
    public static boolean spend(Player player, long amount) {
        long has = motes(player);
        if (amount > 0L && has < amount) {
            return false;
        }
        long now = has - amount;
        if (now < 0L) {
            return false;
        }
        player.setData(MOTES.get(), now);
        return true;
    }

    /** Add to a balance. Never a negative amount; that is what spend is for. */
    public static void gain(Player player, long amount) {
        if (amount <= 0L) {
            return;
        }
        player.setData(MOTES.get(), motes(player) + amount);
    }

    public static Set<String> learned(Player player) {
        return new LinkedHashSet<>(player.getData(LEARNED.get()));
    }

    public static boolean knows(Player player, String id) {
        return player.getData(LEARNED.get()).contains(id);
    }

    /**
     * Learn one. True when it was not already known.
     *
     * <p>The list is replaced rather than added to: an attachment mutated in
     * place is one the sync never hears about, so the client's catalogue would
     * silently stop growing while nothing looked wrong on the server.
     */
    public static boolean learn(Player player, String id) {
        List<String> was = player.getData(LEARNED.get());
        if (was.contains(id)) {
            return false;
        }
        List<String> now = new ArrayList<>(was);
        now.add(id);
        player.setData(LEARNED.get(), List.copyOf(now));
        return true;
    }
}
