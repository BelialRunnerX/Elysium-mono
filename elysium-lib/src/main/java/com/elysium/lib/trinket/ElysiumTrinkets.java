package com.elysium.lib.trinket;

import com.elysium.lib.character.ElysiumCharacter;
import com.elysium.lib.character.ElysiumPassive;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * The one seam between Elysium and whatever accessory mod is installed.
 *
 * <h2>What lives here and what does not</h2>
 *
 * Exactly one question crosses this boundary: <em>what is this player
 * wearing?</em> A content mod that depends on Curios (or Accessories, or
 * nothing at all) installs a {@link Provider} during setup, and from that point
 * the engine can ask. The library imports nothing from any accessory mod and
 * builds without one.
 *
 * With no provider installed, {@link #equipped} returns an empty list and every
 * trinket hook is silently absent. That is the correct behaviour for a library
 * running on its own, and it is also what a player sees if they remove the
 * accessory mod but keep their save: their trinkets stop working and nothing
 * else does.
 *
 * <h2>This is called from combat, and that matters</h2>
 *
 * {@code ElysiumCharacter.passives} runs on every hit taken, every hit dealt,
 * and every server tick. A provider that walks a Curios inventory on each call
 * would put an inventory scan in the damage path several times per swing.
 *
 * <b>So a provider must be cheap and is expected to cache.</b> The natural
 * shape is a map from player to equipped list, refreshed on the accessory mod's
 * own equip and unequip events rather than rebuilt on demand. This is stated as
 * a requirement rather than enforced, because the library cannot see the events
 * that would let it do the caching itself — but a provider that ignores it will
 * show up as a frame-time problem in combat, not as a crash.
 *
 * <h2>Level requirements are checked here</h2>
 *
 * A trinket above the wearer's character level contributes nothing, and it is
 * filtered at this single point rather than inside each of forty behaviours.
 * The item stays equipped and its tooltip still says what it would do — the
 * same treatment armour above your level already gets — because silently
 * unequipping a player's gear when they respec is worse than an inert slot.
 */
public final class ElysiumTrinkets {

    private ElysiumTrinkets() {
    }

    /** One trinket on one player, at the tier it has been ascended to. */
    public record Equipped(ElysiumTrinket trinket, int ascension) {

        public Equipped {
            if (trinket == null) {
                throw new IllegalArgumentException("equipped trinket cannot be null");
            }
            ascension = Math.max(0, ascension);
        }
    }

    /**
     * Answers what a player is wearing.
     *
     * Implemented by whichever mod owns the accessory slots. See the class
     * javadoc on caching — this is called from the damage path.
     */
    @FunctionalInterface
    public interface Provider {
        List<Equipped> equipped(Player player);
    }

    private static volatile Provider provider;

    /**
     * Installs the accessory adapter.
     *
     * Last one wins, and that is deliberate: two accessory mods installed at
     * once is a pack configuration problem, and picking a winner is better than
     * consulting both and applying every trinket twice.
     */
    public static void setProvider(Provider newProvider) {
        provider = newProvider;
    }

    public static boolean hasProvider() {
        return provider != null;
    }

    /**
     * What this player is wearing that the engine should act on.
     *
     * Filtered by level requirement. Never null.
     */
    public static List<Equipped> equipped(Player player) {
        Provider current = provider;
        if (current == null || player == null) {
            return List.of();
        }

        List<Equipped> reported = current.equipped(player);
        if (reported == null || reported.isEmpty()) {
            return List.of();
        }

        int level = ElysiumCharacter.getLevel(player);
        List<Equipped> allowed = new ArrayList<>(reported.size());
        for (Equipped entry : reported) {
            if (entry != null && entry.trinket().getLevelRequirement() <= level) {
                allowed.add(entry);
            }
        }
        return allowed;
    }

    /**
     * The passives of everything this player is wearing.
     *
     * Appended to the race's and the class's by
     * {@code ElysiumCharacter.passives}, which is the only caller — every
     * combinator in the engine goes through that one method, so trinkets reach
     * all fifteen hooks without a single one of them being changed.
     */
    public static List<ElysiumPassive> passives(Player player) {
        List<Equipped> worn = equipped(player);
        if (worn.isEmpty()) {
            return List.of();
        }
        List<ElysiumPassive> passives = new ArrayList<>(worn.size());
        for (Equipped entry : worn) {
            ElysiumPassive passive = entry.trinket().passiveAt(entry.ascension());
            if (passive != null) {
                passives.add(passive);
            }
        }
        return passives;
    }
}
