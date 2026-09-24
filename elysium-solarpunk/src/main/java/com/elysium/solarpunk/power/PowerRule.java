package com.elysium.solarpunk.power;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * One rule: <em>when this, hold that and everything below it</em>.
 *
 * <h2>Why the only action is "hold"</h2>
 *
 * The obvious vocabulary has an enable and a disable, and the obvious
 * vocabulary is a trap. Two opposing actions means two rules can contradict
 * each other, which means an order of evaluation, which means a player has to
 * know which governor the game looks at first — and there is no honest way to
 * tell them, because it is a walk order over blocks.
 *
 * One action that only ever subtracts has no such problem. Rules combine by
 * union: if any governor says hold Industry, Industry is held. Order cannot
 * matter, because union is commutative. A player who wants a machine to run
 * again deletes the rule that stopped it, which is the same mental model as
 * every other block in Minecraft.
 *
 * <h2>Why holding a class holds everything below it</h2>
 *
 * Because that is what shedding load means, and because the alternative is
 * worse in a specific way: a player who holds Logistics alone, expecting to
 * save power, finds their comfort lighting still drawing while their harvesters
 * are stopped — they have made the base <em>less</em> sensible and the game
 * let them. Holding downward makes every rule a statement about a floor: "keep
 * nothing below this running", which is the sentence somebody actually means.
 *
 * A rule holding {@link PowerClass#CRITICAL} therefore holds everything. That
 * is the base's main switch, and it is reachable rather than special-cased.
 *
 * <h2>No Minecraft here</h2>
 *
 * The rules are the part most worth executing and the part hardest to check by
 * reading, so they are kept where a harness can reach them.
 *
 * @param when what has to be true
 * @param hold the highest-priority class to stop, together with all below it
 */
public record PowerRule(PowerCondition when, PowerClass hold) {

    public PowerRule {
        if (when == null || hold == null) {
            throw new IllegalArgumentException(
                    "a rule with a missing half is a rule nobody can read");
        }
    }

    /** The classes this one rule stops, if it is in force. */
    public Set<PowerClass> heldClasses() {
        EnumSet<PowerClass> held = EnumSet.noneOf(PowerClass.class);
        for (PowerClass kind : PowerClass.values()) {
            if (kind.rank() >= hold.rank()) {
                held.add(kind);
            }
        }
        return held;
    }

    /**
     * Everything stopped by a set of rules, in a given state.
     *
     * Union, so the order of the rules cannot change the answer. That is a
     * property worth stating out loud: the caller finds governors by walking
     * blocks, and a walk order is not something a player can see or predict.
     */
    public static Set<PowerClass> held(Collection<PowerRule> rules, GridState state) {
        EnumSet<PowerClass> held = EnumSet.noneOf(PowerClass.class);
        if (rules == null || rules.isEmpty()) {
            return held;
        }
        for (PowerRule rule : rules) {
            if (rule != null && rule.when().holds(state)) {
                held.addAll(rule.heldClasses());
            }
        }
        return held;
    }
}
