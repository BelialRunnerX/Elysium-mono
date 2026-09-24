package com.elysium.lib.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything Elysium stores on an individual item stack.
 *
 * 1.20.5 removed free-form NBT from item stacks, so the old
 * {@code stack.getOrCreateTag()} calls are gone. This record is registered as a
 * data component instead ({@link ElysiumComponents#GEAR_DATA}), which gives
 * proper save/load, network sync, stack comparison and tooltip behaviour for
 * free.
 *
 * @param runes         socketed rune type names, in socket order
 * @param armorBonus    flat armour granted by the last reforge
 * @param healthBonus   flat max health granted by the last reforge
 * @param speedBonus    movement speed percentage granted by the last reforge
 * @param ascendedTier  tier from ascension, or -1 when the item is still at its
 *                      registered base tier
 * @param reforgeCount  how many times this piece has been reforged; the
 *                      equipment archive caps a piece at
 *                      {@link #MAX_REFORGES} ("Reforging Potential: up to 3
 *                      times")
 */
public record ElysiumGearData(List<String> runes,
                              int armorBonus,
                              int healthBonus,
                              int speedBonus,
                              int ascendedTier,
                              int reforgeCount,
                              List<Integer> runeTiers,
                              int extraSockets) {

    /**
     * The shape this record had before a Sigil of Socketing could add a slot.
     * Every piece made this way has none, which is what every piece had.
     */
    public ElysiumGearData(List<String> runes, int armorBonus, int healthBonus, int speedBonus,
                           int ascendedTier, int reforgeCount, List<Integer> runeTiers) {
        this(runes, armorBonus, healthBonus, speedBonus, ascendedTier, reforgeCount,
                runeTiers, 0);
    }

    /** "Reforging Potential: Up to 3 times for random stat improvements." */
    public static final int MAX_REFORGES = 3;

    public static final ElysiumGearData EMPTY = new ElysiumGearData(List.of(), 0, 0, 0, -1, 0);

    /**
     * The shape this record had before runes had tiers. Every socketed rune
     * made this way is tier 1, which is exactly what every rune was.
     */
    public ElysiumGearData(List<String> runes, int armorBonus, int healthBonus,
                           int speedBonus, int ascendedTier, int reforgeCount) {
        this(runes, armorBonus, healthBonus, speedBonus, ascendedTier,
                reforgeCount, List.of());
    }

    public static final Codec<ElysiumGearData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("runes", List.of()).forGetter(ElysiumGearData::runes),
            Codec.INT.optionalFieldOf("armor_bonus", 0).forGetter(ElysiumGearData::armorBonus),
            Codec.INT.optionalFieldOf("health_bonus", 0).forGetter(ElysiumGearData::healthBonus),
            Codec.INT.optionalFieldOf("speed_bonus", 0).forGetter(ElysiumGearData::speedBonus),
            Codec.INT.optionalFieldOf("ascended_tier", -1).forGetter(ElysiumGearData::ascendedTier),
            Codec.INT.optionalFieldOf("reforge_count", 0).forGetter(ElysiumGearData::reforgeCount),
            // Parallel to runes, and optional: every piece socketed before
            // runes had tiers has no such field, reads as an empty list, and
            // so reads every rune as tier 1 — which is what they were.
            Codec.INT.listOf().optionalFieldOf("rune_tiers", List.of())
                    .forGetter(ElysiumGearData::runeTiers),
            // Sockets a sigil added, beyond what the piece's tier allows.
            // Optional for the same reason: a piece from before sigils existed
            // has none, and reads as none.
            Codec.INT.optionalFieldOf("extra_sockets", 0).forGetter(ElysiumGearData::extraSockets)
    ).apply(instance, ElysiumGearData::new));

    public static final StreamCodec<ByteBuf, ElysiumGearData> STREAM_CODEC =
            ByteBufCodecs.fromCodec(CODEC);

    public ElysiumGearData {
        // Defensive copy: components must be immutable and safely shareable
        // between stacks.
        runes = List.copyOf(runes);
        runeTiers = List.copyOf(runeTiers);
    }

    /** The tier of the rune in this socket. Tier 1 where nothing was recorded. */
    public int tierAt(int socket) {
        if (socket < 0 || socket >= runeTiers.size()) {
            return ElysiumRuneTiers.BASE;
        }
        Integer tier = runeTiers.get(socket);
        return ElysiumRuneTiers.clamp(tier == null ? ElysiumRuneTiers.BASE : tier);
    }

    /** This data with one more socket than it had. */
    public ElysiumGearData withExtraSocket() {
        return new ElysiumGearData(runes, armorBonus, healthBonus, speedBonus, ascendedTier,
                reforgeCount, runeTiers, extraSockets + 1);
    }

    /** This data with the rune in one socket taken back out. */
    public ElysiumGearData withRuneRemoved(int socket) {
        if (socket < 0 || socket >= runes.size()) {
            return this;
        }
        List<String> left = new ArrayList<>(runes);
        left.remove(socket);
        List<Integer> tiers = new ArrayList<>(runeTiers);
        if (socket < tiers.size()) {
            tiers.remove(socket);
        }
        return new ElysiumGearData(left, armorBonus, healthBonus, speedBonus, ascendedTier,
                reforgeCount, tiers, extraSockets);
    }

    public boolean isReforged() {
        return armorBonus > 0 || healthBonus > 0 || speedBonus > 0;
    }

    public int reforgesRemaining() {
        return Math.max(0, MAX_REFORGES - reforgeCount);
    }

    public boolean canReforge() {
        return reforgesRemaining() > 0;
    }

    public ElysiumGearData withRuneAdded(String rune) {
        return withRuneAdded(rune, ElysiumRuneTiers.BASE);
    }

    /**
     * A rune socketed at a tier.
     *
     * The tier list is padded to the rune list first, so a piece that already
     * carried untiered runes keeps them at tier 1 and the new rune's tier lands
     * in its own socket rather than in the first empty index of a shorter list.
     */
    public ElysiumGearData withRuneAdded(String rune, int tier) {
        List<String> updated = new ArrayList<>(this.runes);
        updated.add(rune);
        List<Integer> tiers = new ArrayList<>();
        for (int i = 0; i < this.runes.size(); i++) {
            tiers.add(tierAt(i));
        }
        tiers.add(ElysiumRuneTiers.clamp(tier));
        return new ElysiumGearData(updated, armorBonus, healthBonus, speedBonus,
                ascendedTier, reforgeCount, tiers);
    }

    /** Spending a reforge charge is part of applying the roll, never separate. */
    public ElysiumGearData withReforgedStats(int armor, int health, int speed) {
        return new ElysiumGearData(runes, armor, health, speed,
                ascendedTier, reforgeCount + 1, runeTiers);
    }

    /**
     * Ascending refills the reforge charges.
     *
     * This is the whole engine of infinite progression: reforge three times,
     * ascend to spend those results on a higher tier, reforge three more times
     * against the better numbers, and so on. Charges stay scarce within a
     * tier — the archive's "up to 3 times" still holds for any given tier —
     * while the piece as a whole can improve forever. Without the refill,
     * ascension would raise a ceiling that nothing could reach.
     */
    public ElysiumGearData withAscendedTier(int tier) {
        return new ElysiumGearData(runes, armorBonus, healthBonus, speedBonus,
                tier, 0, runeTiers);
    }
}
