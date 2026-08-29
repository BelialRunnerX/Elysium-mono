package com.elysium.lib.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Which side of the Empire something is on.
 *
 * <h2>Why classification is pluggable</h2>
 *
 * This used to be a chain of {@code instanceof} against two named mob classes,
 * which meant only the mod that defined those mobs could ever add to a faction.
 * A content mod adding its own Imperial patrol had no way to say so, and its
 * mob counted as Unsworn along with every zombie.
 *
 * So the rules are a list. Each is asked in turn and the first non-null answer
 * wins, which makes ordering meaningful: the library's own fallback — anything
 * hostile is Unsworn — registers last, so a specific rule always beats it.
 */
public enum ElysiumFaction {

    /** The Empire's own. Killing these raises Suspicion. */
    EMPIRE,

    /** Everything hostile that is not the Empire's. Killing these raises Favor. */
    UNSWORN,

    /** Everything else. Moves neither meter. */
    NEUTRAL;

    private static final List<Function<Entity, ElysiumFaction>> RULES = new ArrayList<>();
    private static final List<Function<Entity, Boolean>> NAMED = new ArrayList<>();

    static {
        // The library's own fallback, registered first and therefore consulted
        // last: see the ordering note below.
        RULES.add(entity -> entity instanceof Monster ? UNSWORN : NEUTRAL);
    }

    /**
     * Adds a classification rule.
     *
     * Rules are consulted <b>most recently registered first</b>, so a rule
     * added by a content mod always takes precedence over the library's
     * catch-all. Return null to pass.
     */
    public static void addRule(Function<Entity, ElysiumFaction> rule) {
        RULES.add(rule);
    }

    /**
     * Marks entities as named combatants — the faction mobs that always pay
     * standing and loot, as against an ordinary hostile that rolls for it.
     */
    public static void addNamedCombatantRule(Function<Entity, Boolean> rule) {
        NAMED.add(rule);
    }

    public static ElysiumFaction of(Entity entity) {
        if (entity == null) {
            return NEUTRAL;
        }
        for (int i = RULES.size() - 1; i >= 0; i--) {
            ElysiumFaction answer = RULES.get(i).apply(entity);
            if (answer != null) {
                return answer;
            }
        }
        return NEUTRAL;
    }

    /** True for a faction's own mobs, which always pay out. */
    public static boolean isNamedCombatant(Entity entity) {
        if (entity == null) {
            return false;
        }
        for (Function<Entity, Boolean> rule : NAMED) {
            if (Boolean.TRUE.equals(rule.apply(entity))) {
                return true;
            }
        }
        return false;
    }
}
