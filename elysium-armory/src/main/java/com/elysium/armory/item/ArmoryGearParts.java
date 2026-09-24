package com.elysium.armory.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * What a piece of gear is built from, as it rides on the item: for each part,
 * which part definition it is and which materials went into it, in the order
 * they were put in (the first is the one the piece is named and coloured
 * after).
 *
 * <p>Ids, not objects: materials and parts are datapack data and may be
 * reloaded or removed, and a sword in a chest from last week must still be a
 * sword. A part whose definition has gone is dropped when the piece is
 * computed, with the rest of it left alone.
 */
public record ArmoryGearParts(List<Entry> entries) {

    public static final ArmoryGearParts EMPTY = new ArmoryGearParts(List.of());

    public record Entry(String part, List<String> materials) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("part").forGetter(Entry::part),
                Codec.STRING.listOf().fieldOf("materials").forGetter(Entry::materials)
        ).apply(instance, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::part,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Entry::materials,
                Entry::new);

        public Entry {
            materials = List.copyOf(materials);
        }
    }

    public static final Codec<ArmoryGearParts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().fieldOf("parts").forGetter(ArmoryGearParts::entries)
    ).apply(instance, ArmoryGearParts::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmoryGearParts> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), ArmoryGearParts::entries,
            ArmoryGearParts::new);

    public ArmoryGearParts {
        entries = List.copyOf(entries);
    }

    /** This build with one part replaced (or added if it had none of that part). */
    public ArmoryGearParts with(String partId, List<String> materials) {
        List<Entry> out = new ArrayList<>(entries);
        out.removeIf(e -> e.part().equals(partId));
        out.add(new Entry(partId, materials));
        return new ArmoryGearParts(out);
    }

    /** This build without that part (what a mod kit does). */
    public ArmoryGearParts without(String partId) {
        List<Entry> out = new ArrayList<>(entries);
        out.removeIf(e -> e.part().equals(partId));
        return new ArmoryGearParts(out);
    }

    public Entry find(String partId) {
        for (Entry entry : entries) {
            if (entry.part().equals(partId)) {
                return entry;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
