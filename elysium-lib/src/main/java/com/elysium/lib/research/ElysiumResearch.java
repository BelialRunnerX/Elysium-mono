package com.elysium.lib.research;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a player knows, and how they came to know it.
 *
 * <h2>Two attachments, not one</h2>
 *
 * The totals are one map; what has already been counted is another set. They
 * are separate because they have different lifetimes and different failure
 * modes. The totals are the player's progress and must survive anything; the
 * novelty set is bookkeeping, and if it were ever lost the worst case is that
 * a player can re-earn some points they already had.
 *
 * Keeping them separate also means the novelty set can be pruned later without
 * touching the thing a player would notice losing.
 *
 * <h2>Why strings and not enums in the save</h2>
 *
 * Same reason the catalogue is strings: a field id in a save file outlives the
 * code that wrote it. An unknown id read back is skipped, not thrown on. A
 * player who removes a content mod loses that mod's contribution, not their
 * world.
 */
public final class ElysiumResearch {

    private ElysiumResearch() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    private static final Codec<Map<String, Integer>> TOTALS_CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT);

    private static final Codec<Set<String>> COUNTED_CODEC =
            Codec.STRING.listOf().xmap(
                    list -> (Set<String>) new LinkedHashSet<>(list),
                    set -> new java.util.ArrayList<>(set));

    /** Field id to points. Absent means none. */
    public static final Supplier<AttachmentType<Map<String, Integer>>> TOTALS =
            ATTACHMENTS.register("research", () ->
                    AttachmentType.builder(
                                    (Supplier<Map<String, Integer>>) LinkedHashMap::new)
                            .serialize(TOTALS_CODEC)
                            .copyOnDeath()
                            .build());

    /** "deed/subject" for every novel deed already paid for. */
    public static final Supplier<AttachmentType<Set<String>>> COUNTED =
            ATTACHMENTS.register("research_counted", () ->
                    AttachmentType.builder(
                                    (Supplier<Set<String>>) LinkedHashSet::new)
                            .serialize(COUNTED_CODEC)
                            .copyOnDeath()
                            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public static int points(Player player, ElysiumResearchField field) {
        Integer held = player.getData(TOTALS.get()).get(field.id());
        return held == null ? 0 : held;
    }

    public static ElysiumResearchRank rank(Player player, ElysiumResearchField field) {
        return ElysiumResearchRank.of(points(player, field));
    }

    /** Whether this player has earned a particular capability. */
    public static boolean has(Player player, ElysiumUnlock unlock) {
        return unlock.metBy(points(player, unlock.field()));
    }

    /** Everything across all six, for a screen to draw. */
    public static Map<ElysiumResearchField, Integer> all(Player player) {
        Map<ElysiumResearchField, Integer> out =
                new LinkedHashMap<>(ElysiumResearchField.values().length);
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            out.put(field, points(player, field));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Earning
    // ------------------------------------------------------------------

    /** What one deed did, so the caller can report it without recomputing. */
    public record Gain(boolean counted, ElysiumResearchField field, int points,
                       ElysiumResearchRank before, ElysiumResearchRank after) {

        /** Public so ElysiumExposure can return it on the other fifty-nine
         *  seconds without inventing a second empty value. */
        public static final Gain NOTHING = new Gain(false, null, 0, null, null);

        /** Whether this gain crossed into a new band. */
        public boolean promoted() {
            return counted && after != before;
        }
    }

    /**
     * Credit a deed.
     *
     * @param subject what the deed was done to — a mob id, a block id, a rift's
     *                position. Only read for {@linkplain ElysiumDeed#novel()
     *                novel} deeds, where it is what "again" means. Pass
     *                anything stable for a repeatable deed; it is ignored.
     */
    public static Gain credit(Player player, ElysiumDeed deed, String subject) {
        if (player == null || player.level().isClientSide()) {
            return Gain.NOTHING;
        }
        if (deed.novel()) {
            String key = deed.name().toLowerCase(java.util.Locale.ROOT) + "/" + subject;
            Set<String> counted = new LinkedHashSet<>(player.getData(COUNTED.get()));
            if (!counted.add(key)) {
                return Gain.NOTHING;
            }
            player.setData(COUNTED.get(), counted);
        }
        return award(player, deed.field(), deed.points());
    }

    /**
     * Add points directly.
     *
     * Public because a content mod's own deed is as valid as one of these, and
     * making it go through {@link ElysiumDeed} would mean a mod cannot add one
     * without editing this mod. The novelty rule is the caller's problem in
     * that case, which is the honest trade.
     */
    public static Gain award(Player player, ElysiumResearchField field, int points) {
        if (player == null || field == null || points <= 0
                || player.level().isClientSide()) {
            return Gain.NOTHING;
        }
        Map<String, Integer> totals = new LinkedHashMap<>(player.getData(TOTALS.get()));
        int was = totals.getOrDefault(field.id(), 0);
        ElysiumResearchRank before = ElysiumResearchRank.of(was);

        // Capped at the top band's threshold. Counting past the end of the
        // track would be a number that grows forever and buys nothing, which is
        // the exact shape of the grind this system was built to avoid.
        int now = Math.min(CEILING, was + points);
        if (now == was) {
            return Gain.NOTHING;
        }
        totals.put(field.id(), now);
        player.setData(TOTALS.get(), totals);

        ElysiumResearchRank after = ElysiumResearchRank.of(now);
        Gain gain = new Gain(true, field, now - was, before, after);
        if (gain.promoted()) {
            announce(player, field, after);
        }
        return gain;
    }

    /** The end of every track. Lives on the rank, which loads without a game. */
    public static final int CEILING = ElysiumResearchRank.CEILING;

    /**
     * Say so when a band is crossed, and name what it opened.
     *
     * A promotion nobody is told about is a promotion that did not happen as
     * far as the player is concerned — and since every unlock here is quiet by
     * nature (a readout gets longer; a number appears) there is nothing else
     * that would announce it.
     */
    private static void announce(Player player, ElysiumResearchField field,
                                 ElysiumResearchRank rank) {
        player.displayClientMessage(Component.translatable(
                        "elysium.research.promoted",
                        Component.translatable(field.translationKey()),
                        Component.translatable(rank.translationKey()))
                .withStyle(ChatFormatting.AQUA), false);
        for (ElysiumUnlock unlock : ElysiumUnlock.of(field)) {
            if (unlock.rank() == rank) {
                player.displayClientMessage(Component.translatable(
                                "elysium.research.unlocked",
                                Component.translatable(unlock.translationKey()))
                        .withStyle(ChatFormatting.GREEN), false);
            }
        }
    }
}
