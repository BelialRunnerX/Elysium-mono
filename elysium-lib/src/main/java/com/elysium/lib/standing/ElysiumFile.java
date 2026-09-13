package com.elysium.lib.standing;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The file the Empire keeps: what moved your meters, and when.
 *
 * <h2>Why a mod with two working meters still needed this</h2>
 *
 * Everything built against §14 so far changes a number. Killing an enforcer,
 * mining rich ore, ascending a weapon, planting a beacon, clearing a register
 * action, and simply standing still for two minutes all move Suspicion, by
 * different amounts, some of them silently. A player is told when a <em>band</em>
 * changes — {@code announce} deliberately says nothing on the individual points,
 * because a meter that talks constantly stops being read — and is told nothing
 * else at all.
 *
 * The result is a system that is legible in its documentation and opaque in
 * play. §14.5 asks for the fix by name:
 *
 * <blockquote>Regional archives that reveal how much the Empire currently knows
 * about a system.</blockquote>
 *
 * and §21's HUD notes spell out the contents: <blockquote>Favor, current-system
 * Suspicion, bands, recent causes, claim floors.</blockquote> Three of those
 * five already exist and can be read off the meters. "Recent causes" is this.
 *
 * <h2>A ledger, not a log</h2>
 *
 * Bounded to {@link #DEPTH} entries, oldest dropped. That is a design choice
 * and not a storage one: an unbounded history would be a wall of text nobody
 * reads, and it would grow inside a player's save forever. Twelve entries is
 * roughly a session's worth of things that mattered, which is the window in
 * which a player is actually asking "why am I Marked".
 *
 * <h2>Causes are keys, not sentences</h2>
 *
 * Each entry stores a translation key suffix — {@code "ore"}, {@code "kill"},
 * {@code "claim"} — rather than a rendered string. Storing the sentence would
 * put English in the save file and break the moment anybody translates the mod
 * or renames a cause. It also keeps this in the library's budget: the library
 * ships no content and has no idea what a "rich ore" is, so it records the word
 * the caller handed it and lets the language file say what that means.
 *
 * <h2>Not copyOnDeath</h2>
 *
 * Unlike the meters themselves. The meters survive death because the Empire's
 * opinion of you is not carried in your pockets; the <em>ledger</em> is a
 * convenience for reading the last twenty minutes, and a player who has just
 * died has a new twenty minutes to read. Keeping it would also mean the first
 * thing a respawning player sees in an archive is the sequence of events that
 * got them killed, which is a worse readout than an empty one.
 */
public record ElysiumFile(List<Entry> entries) {

    /** How many causes are remembered. */
    public static final int DEPTH = 12;

    /**
     * One movement of one meter.
     *
     * @param tick      game time it happened, so the archive can say how long ago
     * @param delta     signed, and never zero — a cause that changed nothing is
     *                  not a cause
     * @param cause     translation key suffix under {@code elysium.cause.}
     * @param suspicion true if this moved Suspicion, false for Favor
     */
    public record Entry(long tick, int delta, String cause, boolean suspicion) {

        public static final Codec<Entry> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Codec.LONG.fieldOf("tick").forGetter(Entry::tick),
                        Codec.INT.fieldOf("delta").forGetter(Entry::delta),
                        Codec.STRING.fieldOf("cause").forGetter(Entry::cause),
                        Codec.BOOL.fieldOf("suspicion").forGetter(Entry::suspicion)
                ).apply(instance, Entry::new));

        public Component describe(long now) {
            long ago = Math.max(0L, now - tick);
            Component what = Component.translatable("elysium.cause." + cause);
            return Component.translatable("elysium.file.entry",
                            Component.literal((delta > 0 ? "+" : "") + delta)
                                    .withStyle(delta > 0
                                            ? ChatFormatting.RED
                                            : ChatFormatting.GREEN),
                            Component.translatable(suspicion
                                    ? "elysium.file.suspicion"
                                    : "elysium.file.favor"),
                            what,
                            minutes(ago))
                    .withStyle(ChatFormatting.GRAY);
        }

        /** "4 minutes ago", floored, with "just now" under a minute. */
        private static Component minutes(long ticks) {
            long mins = ticks / (20L * 60L);
            return mins <= 0
                    ? Component.translatable("elysium.file.just_now")
                    : Component.translatable("elysium.file.minutes_ago", mins);
        }
    }

    public static final ElysiumFile NONE = new ElysiumFile(List.of());

    public static final Codec<ElysiumFile> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Entry.CODEC.listOf().fieldOf("entries").forGetter(ElysiumFile::entries)
            ).apply(instance, ElysiumFile::new));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    public static final Supplier<AttachmentType<ElysiumFile>> ACTIVE =
            ATTACHMENTS.register("file",
                    () -> AttachmentType.<ElysiumFile>builder(() -> NONE)
                            .serialize(CODEC)
                            .build());

    public static ElysiumFile get(Player player) {
        return player.getData(ACTIVE.get());
    }

    /**
     * Records a cause, newest first, dropping the oldest past {@link #DEPTH}.
     *
     * Newest first because that is the order it is read in — an archive is
     * consulted to answer "what just happened", and putting the answer on the
     * last line means scrolling past eleven things that are not it.
     *
     * Called from {@link ElysiumStanding} rather than by each caller, so a
     * handler that grants points cannot forget to file them. That is the same
     * reasoning {@code addFavor} gives for applying the Presence scale in one
     * place.
     */
    public static void record(Player player, long tick, int delta, String cause,
                              boolean suspicion) {
        if (delta == 0 || cause == null || cause.isEmpty()) {
            return;
        }
        List<Entry> next = new ArrayList<>(DEPTH);
        next.add(new Entry(tick, delta, cause, suspicion));
        for (Entry entry : get(player).entries()) {
            if (next.size() >= DEPTH) {
                break;
            }
            next.add(entry);
        }
        player.setData(ACTIVE.get(), new ElysiumFile(List.copyOf(next)));
    }

    public static void clear(Player player) {
        player.setData(ACTIVE.get(), NONE);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
