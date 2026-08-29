package com.elysium.lib.standing;

import com.elysium.lib.entity.ElysiumFaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Who the world sends after you, and on whose behalf.
 *
 * <h2>The loop this serves</h2>
 *
 * Standing summons. Kill the Unsworn and Favor rises and more Unsworn appear;
 * anger the Empire and Suspicion rises and enforcers arrive. The engine owns
 * the timing, the placement, the crowd cap and the dice; a dispatcher owns the
 * one thing the engine cannot know, which is what a member of your faction
 * actually is.
 *
 * <h2>Registering one</h2>
 *
 * <pre>{@code
 * ElysiumDispatch.register(new ElysiumDispatch.Dispatcher() {
 *     public ElysiumFaction faction() { return ElysiumFaction.EMPIRE; }
 *     public Class<? extends Mob> type() { return MyPatrol.class; }
 *     public Mob create(ServerLevel level, int band) {
 *         MyPatrol patrol = MY_PATROL.get().create(level);
 *         if (patrol != null) patrol.equipForBand(band);
 *         return patrol;
 *     }
 * });
 * }</pre>
 *
 * More than one dispatcher may serve a faction; the engine picks among them at
 * random, so two mods can both contribute Imperial mobs without either having
 * to know about the other.
 */
public final class ElysiumDispatch {

    private ElysiumDispatch() {
    }

    public interface Dispatcher {

        /** Which meter summons this. */
        ElysiumFaction faction();

        /**
         * The class the engine counts when enforcing the crowd cap. Return the
         * mob's own class; two dispatchers sharing a class share a cap.
         */
        Class<? extends Mob> type();

        /**
         * Builds one, already kitted out for the standing band.
         *
         * The engine positions it, calls {@code finalizeSpawn}, targets the
         * player and adds it to the world — so do not do any of that here, and
         * in particular do not equip anything that {@code finalizeSpawn} would
         * then overwrite.
         *
         * @param band 1, 2 or 3 — how far up the meter the player is
         * @return the mob, or null to decline this dispatch
         */
        Mob create(ServerLevel level, int band);

        /** Called after the engine has placed the mob. Equip gear here. */
        default void afterPlaced(Mob mob, ServerLevel level, Player player, BlockPos pos, int band) {
        }
    }

    private static final List<Dispatcher> DISPATCHERS = new ArrayList<>();

    public static void register(Dispatcher dispatcher) {
        DISPATCHERS.add(dispatcher);
    }

    /** Every dispatcher serving a faction, in registration order. */
    public static List<Dispatcher> forFaction(ElysiumFaction faction) {
        List<Dispatcher> found = new ArrayList<>();
        for (Dispatcher dispatcher : DISPATCHERS) {
            if (dispatcher.faction() == faction) {
                found.add(dispatcher);
            }
        }
        return found;
    }

    public static boolean isEmpty() {
        return DISPATCHERS.isEmpty();
    }
}
