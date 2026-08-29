package com.elysium.lib.entity;

import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Every creature any Elysium mod can put in front of a player.
 *
 * <h2>The problem this solves</h2>
 *
 * A dungeon has to fill its rooms with something. A mob mod has creatures. If
 * the dungeon names them, the two mods are welded together and the dungeon
 * stops working on its own; if the mob mod registers dungeon rooms, only that
 * one mod can ever populate a dungeon and the dependency points backwards for
 * a content mod.
 *
 * So neither knows about the other. Mobs are registered here; anything that
 * wants a creature asks here. A dungeon with no mob mod installed asks and gets
 * nothing, which is an empty room rather than a crash, and a fourth mod can
 * contribute mobs without either of the first two changing.
 *
 * <h2>Roles</h2>
 *
 * A role is what a spawn is <em>for</em>, not what it is. The dungeon asking
 * for a {@link Role#BOSS} does not care which faction answers or what it looks
 * like; it cares that one arrives and that it is worth the walk.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * ElysiumBestiary.register(ResourceLocation.fromNamespaceAndPath("mymod", "husk_reaver"),
 *         new ElysiumBestiary.Entry(ElysiumFaction.UNSWORN, Role.ELITE, 2,
 *                 (level, pos, mobLevel) -> {
 *                     MyReaver reaver = MY_REAVER.get().create(level);
 *                     if (reaver != null) {
 *                         reaver.scaleTo(mobLevel);
 *                     }
 *                     return reaver;
 *                 }));
 * }</pre>
 *
 * The factory is handed the level the creature should be built for, already
 * worked out by {@link ElysiumScaling} — so every mod's mobs scale by the same
 * rule and the same mob is the same fight wherever you meet it.
 */
public final class ElysiumBestiary {

    private ElysiumBestiary() {
    }

    /** What a spawn is for. */
    public enum Role {
        /** Ordinary opposition. Most of what fills a room. */
        GRUNT,
        /** Rarer and harder. One or two in a room, not a crowd. */
        ELITE,
        /** The end of the walk. One per dungeon, and never in a filler room. */
        BOSS
    }

    /** Builds one creature, already scaled. */
    @FunctionalInterface
    public interface Factory {
        /**
         * @param mobLevel the level this creature should be built for, from
         *                 {@link ElysiumScaling#levelFor}
         * @return the mob, or null to decline — a factory that cannot build
         *         right now is normal, and the caller tries another
         */
        Mob create(ServerLevel level, BlockPos where, int mobLevel);
    }

    /**
     * One registered creature.
     *
     * @param weight how often this is picked relative to others of the same
     *               faction and role. Relative only; there is no total to keep
     *               to and no scale.
     */
    public record Entry(ElysiumFaction faction, Role role, int weight, Factory factory) {

        public Entry {
            if (weight <= 0) {
                throw new IllegalArgumentException(
                        "A bestiary entry at weight " + weight + " can never be picked, which is "
                        + "a creature that does not exist. Remove it or give it a weight.");
            }
        }
    }

    public static final ElysiumRegistry<Entry> REGISTRY = new ElysiumRegistry<>("bestiary entry");

    public static Entry register(ResourceLocation id, Entry entry) {
        return REGISTRY.register(id, entry);
    }

    // ------------------------------------------------------------------

    /**
     * Picks one entry of a faction and role, by weight.
     *
     * @param faction pass null to accept any faction, which is what a dungeon
     *                does — a dungeon is not anybody's territory, so both
     *                sides turn up in it
     * @return null when nothing matches, which callers must handle as "leave
     *         the room empty" rather than as an error
     */
    public static Entry pick(ElysiumFaction faction, Role role, RandomSource random) {
        List<Entry> candidates = matching(faction, role);
        if (candidates.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Entry entry : candidates) {
            total += entry.weight();
        }
        int roll = random.nextInt(total);
        for (Entry entry : candidates) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry;
            }
        }
        return candidates.get(0);
    }

    /**
     * Builds one creature of a faction and role, scaled to where it is going.
     *
     * The whole point of the class in one method: a caller says what it wants
     * and where, and gets something appropriate without knowing what mods are
     * installed.
     *
     * @return the mob, or null when nothing could be built
     */
    public static Mob spawn(ServerLevel level, BlockPos where, ElysiumFaction faction,
                            Role role, RandomSource random) {
        return spawn(level, where, faction, role, random, 0);
    }

    /**
     * The same, at a level the caller has already worked out.
     *
     * The proximity rule the other overload uses is right for a mob spawning
     * into a world somebody is walking through, and wrong for content generated
     * ahead of the player: a dungeon is built the instant its portal is used,
     * with the player who opened it still standing in another dimension, so
     * every creature in it found nobody nearby and came out at level 1. A
     * caller that knows who the content is for passes the level instead of
     * letting the library guess.
     *
     * @param mobLevel the level to build for; below 1 means "unknown" and falls
     *                 back to the proximity rule, so an older save with no
     *                 level recorded still spawns something sensible rather
     *                 than something built for level zero
     */
    public static Mob spawn(ServerLevel level, BlockPos where, ElysiumFaction faction,
                            Role role, RandomSource random, int mobLevel) {
        Entry entry = pick(faction, role, random);
        if (entry == null) {
            return null;
        }
        int built = mobLevel >= 1
                ? mobLevel
                : ElysiumScaling.levelFor(level, where, entry.faction());
        return entry.factory().create(level, where, built);
    }

    public static List<Entry> matching(ElysiumFaction faction, Role role) {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : REGISTRY.all()) {
            if (entry.role() != role) {
                continue;
            }
            if (faction != null && entry.faction() != faction) {
                continue;
            }
            found.add(entry);
        }
        return found;
    }

    /** True when nothing at all has been registered — no mob mod is installed. */
    public static boolean isEmpty() {
        return REGISTRY.size() == 0;
    }

    public static int size() {
        return REGISTRY.size();
    }
}
