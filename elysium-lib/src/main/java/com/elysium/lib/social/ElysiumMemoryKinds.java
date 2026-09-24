package com.elysium.lib.social;

import com.elysium.lib.ElysiumLib;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.gossip.GossipType;

/**
 * The things anybody remembers about a player, and what each is worth.
 *
 * <h2>Why these live in the library</h2>
 *
 * The library ships no content, and these are not content — they are the
 * vocabulary the engine's own handlers speak, in the same way
 * {@code ElysiumStanding}'s causes are. {@link ElysiumSocialHandler} watches
 * damage, death and trade, and it has to have names for what it sees. A content
 * mod that wants a villager to remember something the library never watches for
 * registers its own kind.
 *
 * <h2>The numbers, and the argument for them</h2>
 *
 * <table border="1">
 *   <caption>Base memory kinds</caption>
 *   <tr><th>Kind</th><th>Weight</th><th>Half-life</th><th>Why</th></tr>
 *   <tr><td>hurt by</td><td>-25</td><td>30 min</td>
 *       <td>Below the refusal threshold on its own. One hit is a misclick, and
 *           a villager who stops trading over a misclick is a bug as far as a
 *           player is concerned.</td></tr>
 *   <tr><td>saw a killing</td><td>-45</td><td>2 hours</td>
 *       <td>Still not refusal on its own, but two of them is. The longest
 *           half-life here: the point of a witness is that they do not get over
 *           it by morning.</td></tr>
 *   <tr><td>defended</td><td>+20</td><td>90 min</td>
 *       <td>Killing something that was coming for them. Worth more than a trade
 *           and less than a killing costs.</td></tr>
 *   <tr><td>traded</td><td>+8</td><td>1 hour</td>
 *       <td>Small, and the only one that repeats freely. Trading your way back
 *           into somebody's good books takes a while, which is the point.</td></tr>
 * </table>
 *
 * The asymmetry is deliberate and is the usual one: harm is remembered harder
 * and longer than help. Coming back from one witnessed killing takes six trades
 * or three rescues; left alone, it takes two hours to fade to half and about
 * four to fade to a quarter. Those figures are not asserted here — the library
 * validator derives them from this table and prints them, so a change to a
 * number changes the reported consequence rather than leaving a stale claim in
 * a comment.
 */
public final class ElysiumMemoryKinds {

    private ElysiumMemoryKinds() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ElysiumLib.MODID, path);
    }

    private static final int MINUTE = 20 * 60;

    /** You hit them. */
    public static final ElysiumMemoryKind HURT_BY =
            ElysiumMemoryKind.builder(id("hurt_by"))
                    .weight(-25)
                    .halfLife(30 * MINUTE)
                    .gossip(GossipType.MINOR_NEGATIVE, 15)
                    .register();

    /** You killed one of theirs, and they were close enough to see it. */
    public static final ElysiumMemoryKind SAW_KILLING =
            ElysiumMemoryKind.builder(id("saw_killing"))
                    .weight(-45)
                    .halfLife(120 * MINUTE)
                    .gossip(GossipType.MAJOR_NEGATIVE, 25)
                    .register();

    /** You killed something that was coming for them. */
    public static final ElysiumMemoryKind DEFENDED =
            ElysiumMemoryKind.builder(id("defended"))
                    .weight(20)
                    .halfLife(90 * MINUTE)
                    .gossip(GossipType.MINOR_POSITIVE, 12)
                    .register();

    /** You bought something. */
    public static final ElysiumMemoryKind TRADED =
            ElysiumMemoryKind.builder(id("traded"))
                    .weight(8)
                    .halfLife(60 * MINUTE)
                    .gossip(GossipType.TRADING, 2)
                    .register();

    /**
     * Touching the class runs the registrations above.
     *
     * Called from the mod constructor. Without it these exist in the source and
     * not in the registry, and every handler below would look up a kind that is
     * not there — which {@link ElysiumMemories#remember} turns away rather than
     * storing, so the symptom would be villagers who remember nothing at all.
     */
    public static void bootstrap() {
    }
}
