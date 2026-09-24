package com.elysium.lib.social;

import com.elysium.lib.ElysiumLib;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What an individual remembers, and what it makes of you.
 *
 * <h2>The thing this is for</h2>
 *
 * Vanilla villagers already have an opinion of you. It is a number per player,
 * built out of five gossip types, and it works — but it is only ever a number.
 * A villager who charges you more cannot tell you why, you cannot find out
 * which of the things you did is still counting against you, and two villagers
 * who dislike you for completely different reasons are indistinguishable.
 *
 * This is the other half: the <b>episodes</b>. Each one records what happened,
 * who it involved, where, and when. That is what makes a relationship legible —
 * a villager can be asked what they remember, and the answer is "you killed
 * someone here, four days ago", not "-40".
 *
 * <h2>What it is not</h2>
 *
 * Not a needs simulation. There is no hunger, no sleep quality, no
 * satisfaction-from-a-well-appointed-workshop. Those would be a second
 * simulation loop running on every villager in every loaded chunk, and the
 * game already has a schedule and a brain doing that work. What was missing was
 * not a richer inner life, it was any record of <em>the player</em> — so that is
 * what this stores.
 *
 * A mood is derived from the episodes rather than accumulated beside them, for
 * the same reason weights live on the kind: one source of truth. Ask twice and
 * you cannot get two answers.
 *
 * <h2>Bounded on purpose</h2>
 *
 * {@link #DEPTH} episodes, newest first, oldest dropped — the same argument the
 * standing ledger makes. An unbounded history grows inside a save forever and
 * is a wall of text nobody reads. Sixteen is enough to hold a grudge and not
 * enough to hold a diary.
 *
 * <h2>Server-side only</h2>
 *
 * Nothing here is synced. The client never needs it: the readout is a chat
 * message the server sends, and the price effect goes through vanilla gossip,
 * which is already synced as part of the trade offers.
 */
public final class ElysiumMemories {

    private ElysiumMemories() {
    }

    /** How many episodes one individual keeps. */
    public static final int DEPTH = 16;

    /**
     * The opinion below which somebody will not deal with you at all.
     *
     * Deliberately well below a single bad incident. One hit in a misclick
     * should make a villager wary and still willing to sell you bread; it takes
     * something sustained, or something serious like a killing witnessed, to
     * close the shop.
     */
    public static final int REFUSAL = -60;

    /** The opinion above which somebody is pleased to see you. */
    public static final int WARMTH = 40;

    // ------------------------------------------------------------------
    // One episode
    // ------------------------------------------------------------------

    /**
     * @param kind  the registered {@link ElysiumMemoryKind} id, as a string
     * @param who   the player it was about
     * @param name  their name at the time, so a readout can say it without the
     *              player needing to be online
     * @param where where it happened
     * @param when  the game time it happened
     */
    public record Episode(String kind, UUID who, String name, BlockPos where, long when) {

        public static final Codec<Episode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("kind").forGetter(Episode::kind),
                UUIDUtil.CODEC.fieldOf("who").forGetter(Episode::who),
                Codec.STRING.fieldOf("name").forGetter(Episode::name),
                BlockPos.CODEC.fieldOf("where").forGetter(Episode::where),
                Codec.LONG.fieldOf("when").forGetter(Episode::when)
        ).apply(instance, Episode::new));

        /** The kind, or null if nothing installed still registers it. */
        public ElysiumMemoryKind resolve() {
            return ElysiumMemoryKind.REGISTRY.get(kind);
        }

        /**
         * What this is worth now, faded by its age.
         *
         * Halves every half-life. Floored at zero rather than allowed to
         * approach it forever, because an episode contributing a fraction of a
         * point is an episode that is only still there to make the arithmetic
         * hard to follow.
         */
        public int weightAt(long now) {
            ElysiumMemoryKind resolved = resolve();
            if (resolved == null) {
                return 0;
            }
            long age = Math.max(0L, now - when);
            double faded = resolved.getWeight()
                    * Math.pow(0.5D, (double) age / resolved.getHalfLife());
            return (int) (faded < 0 ? Math.floor(faded) : Math.ceil(faded));
        }
    }

    public static final Codec<List<Episode>> LIST_CODEC = Episode.CODEC.listOf();

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ElysiumLib.MODID);

    /**
     * The episodes one individual is holding.
     *
     * On the entity rather than on the player, which is the whole point: two
     * villagers who watched the same thing remember it separately, and one who
     * was somewhere else does not remember it at all.
     */
    public static final Supplier<AttachmentType<List<Episode>>> EPISODES =
            ATTACHMENTS.register("memories",
                    () -> AttachmentType.<List<Episode>>builder(() -> List.of())
                            .serialize(LIST_CODEC)
                            .build());

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /** Everything this individual remembers, newest first. */
    public static List<Episode> all(LivingEntity subject) {
        return subject.getData(EPISODES.get());
    }

    /** What it remembers about one player, newest first. */
    public static List<Episode> about(LivingEntity subject, UUID player) {
        List<Episode> found = new ArrayList<>();
        for (Episode episode : all(subject)) {
            if (episode.who().equals(player)) {
                found.add(episode);
            }
        }
        return found;
    }

    /**
     * The sum of what it remembers about you, faded by age.
     *
     * Computed rather than stored. A stored total would have to be recomputed
     * on every decay tick to stay honest, and a total that disagreed with the
     * episodes behind it would be the worst of both — a villager refusing to
     * trade with no reason in its own record.
     */
    public static int opinionOf(LivingEntity subject, UUID player, long now) {
        int total = 0;
        for (Episode episode : about(subject, player)) {
            total += episode.weightAt(now);
        }
        return total;
    }

    public static int opinionOf(LivingEntity subject, Player player) {
        return opinionOf(subject, player.getUUID(), player.level().getGameTime());
    }

    public static boolean refuses(LivingEntity subject, Player player) {
        return opinionOf(subject, player) <= REFUSAL;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * Record something, and let vanilla's gossip know about it.
     *
     * @return false when the kind is not registered, so a caller naming a kind
     *         nothing declared fails visibly rather than storing an episode
     *         that is worth zero forever
     */
    public static boolean remember(LivingEntity subject, ElysiumMemoryKind kind,
                                   Player about, BlockPos where) {
        if (kind == null || subject.level().isClientSide()) {
            return false;
        }

        List<Episode> current = new ArrayList<>(all(subject));
        current.add(0, new Episode(kind.getId().toString(), about.getUUID(),
                about.getGameProfile().getName(), where,
                subject.level().getGameTime()));
        while (current.size() > DEPTH) {
            current.remove(current.size() - 1);
        }
        subject.setData(EPISODES.get(), List.copyOf(current));

        // The price half. See ElysiumMemoryKind for why this is a push rather
        // than a second opinion: vanilla already knows how to turn a reputation
        // into a discount, and two systems both deciding what you are charged
        // would eventually disagree in front of a player.
        if (kind.getGossip() != null && subject instanceof Villager villager) {
            villager.getGossips().add(about.getUUID(), kind.getGossip(),
                    kind.getGossipAmount());
        }
        return true;
    }

    /** Convenience: remember something that happened where the subject stands. */
    public static boolean remember(LivingEntity subject, ElysiumMemoryKind kind,
                                   Player about) {
        return remember(subject, kind, about, subject.blockPosition());
    }
}
