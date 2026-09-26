package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The machines a mob farm is made of, and the proof that none of them changes
 * which mob is worth farming.
 *
 * <h2>Why this is in the bestiary and not with the blocks</h2>
 *
 * {@link BeastRules} spends most of its length proving one thing: that reward
 * per unit of danger stays inside {@link BeastRules#WIDEST_SPREAD} across the
 * whole roster, so that a player picks what to farm for some reason other than
 * arithmetic. Everything else in that file — the tilt per tier, the drop share,
 * the loop that refuses to round two beasts onto the same number — exists to
 * keep that one ratio true.
 *
 * <p>A mob farm is a multiplier on exactly that ratio, and a multiplier can
 * break it without touching a single line of the bestiary. So the machines are
 * described here, beside the arithmetic they multiply, rather than with
 * whichever module ends up holding the blocks. The blocks are the delivery.
 * This is the piece.
 *
 * <h2>The thing that actually breaks</h2>
 *
 * It is not looting. Looting multiplies drops, and a reward that is mostly
 * drops for one beast and mostly drops for another comes out of the wash about
 * even: at four times drops with the orbs left alone, the spread goes from
 * 1.148 to 1.328 and the band holds.
 *
 * <p>What breaks it is a killer that takes the <em>player's credit</em> away.
 * The game gives no experience for a mob nothing player-ish killed, so such a
 * farm pays in drops alone — and the drop share is not uniform, because
 * {@link BeastRules#derive} rounds each reward into a whole number of items and
 * a whole number of orbs. A shardling comes out at a third drops and a husk ox
 * at a fifth. Multiply the orbs by nothing and those two rounding artefacts
 * become the whole of the answer: the spread goes to <b>1.618</b>, past the
 * band the entire bestiary is balanced on, and the best thing in the pack to
 * farm quietly changes from the mourner to the shardling.
 *
 * <p>Nothing in the game reports that. The farm works, the items arrive, and
 * the only symptom is that one mob is now the one everybody builds for.
 *
 * <h2>The three rules</h2>
 *
 * <b>Every killer leaves the orbs.</b> {@link #faultsInCredit} — a machine that
 * kills keeps at least {@link #KEEPS_ORBS} of what a player kill would give.
 *
 * <b>No machine multiplies drops past {@link #MOST_LOOTING}.</b>
 * {@link #faultsInReaping}.
 *
 * <b>And every combination of them together still lands inside the band.</b>
 * {@link #faultsInSpread} takes the bestiary and the whole machine list and
 * recomputes {@link BeastRules#spread} through them, because two machines that
 * each pass alone are not two machines that pass together — which is the only
 * way to find out, and is why the harness sweeps the product rather than the
 * list.
 */
public final class FarmRules {

    private FarmRules() {
    }

    // ==================================================================
    // The five things a machine can be
    // ==================================================================

    /** It makes something move without touching what it is worth. */
    public static final String MOVES = "moves";

    /** It kills, which is the only kind that can change the split. */
    public static final String KILLS = "kills";

    /** It picks up what is on the floor and in the air. */
    public static final String GATHERS = "gathers";

    /** It turns collected experience into something that can be carried. */
    public static final String KEEPS = "keeps";

    /** It changes where or whether something spawns, and nothing about reward. */
    public static final String SETTLES = "settles";

    /** The five, in the order everything walks them. */
    public static final List<String> KINDS = List.of(
            MOVES, KILLS, GATHERS, KEEPS, SETTLES);

    /**
     * One machine.
     *
     * @param id      what it is called
     * @param kind    which of the five it is
     * @param orbs    how much of a player kill's experience it leaves, as a share
     * @param drops   how much it multiplies what falls on the floor
     * @param needs   the block the pack really makes that it is built from
     * @param powered whether it wants anything beyond being placed
     */
    public record Machine(String id, String kind, double orbs, double drops,
                          String needs, boolean powered) {

        public Machine {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("a machine with no name");
            }
            if (orbs < 0.0D || drops < 0.0D) {
                throw new IllegalArgumentException(id + " multiplies by a negative "
                        + "number, which is a farm that takes reward off the player "
                        + "for using it");
            }
            if (needs == null || needs.isEmpty()) {
                throw new IllegalArgumentException(id + " is built out of nothing, "
                        + "so it is a block with no place on any ladder of cost");
            }
            if (kind == null || kind.isEmpty()) {
                throw new IllegalArgumentException(id + " is not any kind of "
                        + "machine, so nothing decides what it does");
            }
        }
    }

    /**
     * How much of a player kill's experience a killer has to leave.
     *
     * <p>Half. The arithmetic says the floor is about a sixteenth — below that
     * the spread crosses {@link BeastRules#WIDEST_SPREAD} — and a floor set at
     * the edge of a cliff is a floor that moves the first time anybody edits a
     * beast. Half leaves the whole allowed rectangle at 1.423 or better, which
     * is margin somebody can edit inside without reading this comment.
     */
    public static final double KEEPS_ORBS = 0.5D;

    /**
     * How much anything may multiply what falls on the floor.
     *
     * <p>Four, which is vanilla looting three plus a little. Past that the drop
     * half of the reward starts to be the whole of it, and a reward that is
     * effectively all drops is a reward whose rounding artefacts are the
     * ordering — which is the same failure as taking the orbs away, arrived at
     * from the other side.
     */
    public static final double MOST_LOOTING = 4.0D;

    /**
     * The machines.
     *
     * <p>Read down the {@code orbs} and {@code drops} columns: only the killers
     * have anything other than one in either. That is not a coincidence, it is
     * the design — a fan that changed what a mob was worth would be a fan
     * nobody could reason about.
     */
    public static final List<Machine> MACHINES = List.of(
            //           id                 kind      orbs  drops  needs                       powered
            new Machine("mob_fan", MOVES, 1.0D, 1.0D, "minecraft:iron_ingot", true),
            new Machine("mob_conveyor", MOVES, 1.0D, 1.0D, "minecraft:copper_ingot", false),
            new Machine("grinder_saw", KILLS, 1.0D, 2.0D, "minecraft:iron_block", true),
            new Machine("grinder_spikes", KILLS, 0.5D, 1.0D, "minecraft:iron_ingot", false),
            new Machine("absorption_hopper", GATHERS, 1.0D, 1.0D, "minecraft:hopper", false),
            new Machine("experience_tap", KEEPS, 1.0D, 1.0D, "minecraft:glass_bottle", false),
            new Machine("experience_press", KEEPS, 1.0D, 1.0D, "minecraft:piston", true),
            new Machine("quiet_casing", SETTLES, 1.0D, 1.0D, "minecraft:white_wool", false),
            new Machine("dread_soil", SETTLES, 1.0D, 1.0D, "minecraft:soul_sand", false));

    /** The machine of this name, or null. */
    public static Machine machine(String id) {
        for (Machine one : MACHINES) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Only the ones that can change what a kill is worth. */
    public static List<Machine> killers(List<Machine> machines) {
        List<Machine> found = new ArrayList<>();
        for (Machine one : machines) {
            if (one.kind().equals(KILLS)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /**
     * What a whole farm multiplies by, from the machines it holds.
     *
     * <p>Multiplied rather than added, and the orb shares multiply too — two
     * killers in one farm is a mob killed twice as far from a player, not a mob
     * killed half as far. Handed the list rather than reading
     * {@link #MACHINES}, so a control can hand one built to be caught.
     */
    public static double[] together(List<Machine> machines) {
        double orbs = 1.0D;
        double drops = 1.0D;
        for (Machine one : machines) {
            orbs *= one.orbs();
            drops *= one.drops();
        }
        return new double[]{orbs, drops};
    }

    /**
     * Reward per unit of danger, seen through a farm.
     *
     * <p>The same number {@link BeastRules#rate} works out, with the farm's two
     * multipliers applied to the two halves of the reward separately — which is
     * the whole point, because a farm that treats them differently is a farm
     * that reorders the bestiary.
     */
    public static double rateThrough(BeastRules.Beast beast, BeastRules.Reward reward,
                                     double orbs, double drops) {
        double worth = reward.experience() * orbs
                + (double) reward.drops() * BeastRules.WORTH_OF_A_DROP * drops;
        return beast.threat() <= 0.0D ? Double.MAX_VALUE : worth / beast.threat();
    }

    /**
     * How far apart the best and worst thing to farm are, through a farm.
     *
     * <p>Takes the roster, the rewards and the two multipliers in rather than
     * reading any of them, because a rule that reads the constants it judges
     * re-solves itself against a broken value and reports nothing — which this
     * module has now had to fix six times.
     */
    public static double spreadThrough(List<BeastRules.Beast> beasts,
                                       Map<String, BeastRules.Reward> rewards,
                                       double orbs, double drops) {
        double least = Double.MAX_VALUE;
        double most = 0.0D;
        for (BeastRules.Beast beast : beasts) {
            BeastRules.Reward reward = rewards.get(beast.id());
            if (reward == null) {
                continue;
            }
            double rate = rateThrough(beast, reward, orbs, drops);
            least = Math.min(least, rate);
            most = Math.max(most, rate);
        }
        return least <= 0.0D || least == Double.MAX_VALUE ? Double.MAX_VALUE
                : most / least;
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the farm. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(MACHINES));
        bad.addAll(faultsInKinds(MACHINES, KINDS));
        bad.addAll(faultsInGaps(MACHINES, KINDS));
        bad.addAll(faultsInCredit(MACHINES, KEEPS_ORBS));
        bad.addAll(faultsInReaping(MACHINES, MOST_LOOTING));
        bad.addAll(faultsInIdleness(MACHINES));
        bad.addAll(faultsInSpread(MACHINES, BeastRules.BEASTS,
                BeastRules.derive(BeastRules.BEASTS), BeastRules.WIDEST_SPREAD));
        return List.copyOf(bad);
    }

    /** No two machines written under one name. */
    public static List<String> faultsInNames(List<Machine> machines) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Machine one : machines) {
            if (!seen.add(one.id())) {
                bad.add("two machines are called " + one.id() + ", and whichever the "
                        + "game registers second is a block nobody can place");
            }
        }
        return bad;
    }

    /** Every machine is one of the kinds this module makes happen. */
    public static List<String> faultsInKinds(List<Machine> machines, List<String> kinds) {
        List<String> bad = new ArrayList<>();
        for (Machine one : machines) {
            if (!kinds.contains(one.kind())) {
                bad.add(one.id() + " is a " + one.kind() + ", which is not one of the "
                        + kinds.size() + " kinds this module makes happen — so it is "
                        + "a block that sits there");
            }
        }
        return bad;
    }

    /** Every kind is something in the roster, so none is a word nothing uses. */
    public static List<String> faultsInGaps(List<Machine> machines, List<String> kinds) {
        List<String> bad = new ArrayList<>();
        for (String kind : kinds) {
            boolean built = false;
            for (Machine one : machines) {
                if (one.kind().equals(kind)) {
                    built = true;
                }
            }
            if (!built) {
                bad.add("nothing in the farm " + kind + ", so it is a branch written, "
                        + "compiled and never reached by anything a player can place");
            }
        }
        return bad;
    }

    /**
     * Every killer leaves the orbs.
     *
     * <p>The rule this whole file exists for. Two independent guards rather
     * than one with an else: a killer that takes all the experience and one
     * that takes most of it are different mistakes, and a control that makes
     * one of them has to be able to reach it.
     */
    public static List<String> faultsInCredit(List<Machine> machines, double floor) {
        List<String> bad = new ArrayList<>();
        for (Machine one : killers(machines)) {
            if (one.orbs() <= 0.0D) {
                bad.add(one.id() + " kills without the player's credit, so everything "
                        + "it farms pays in drops alone — and because the drop share "
                        + "is a rounding artefact rather than a constant, that is not "
                        + "a smaller reward, it is a different order of which mob is "
                        + "worth farming");
            }
        }
        for (Machine one : killers(machines)) {
            if (one.orbs() > 0.0D && one.orbs() < floor) {
                bad.add(one.id() + " leaves " + one.orbs() + " of the experience a "
                        + "player kill gives against a floor of " + floor + ", which "
                        + "is inside the margin this module keeps so that the band "
                        + "survives somebody editing a beast");
            }
        }
        return bad;
    }

    /** Nothing multiplies what falls on the floor past what the band can take. */
    public static List<String> faultsInReaping(List<Machine> machines, double most) {
        List<String> bad = new ArrayList<>();
        for (Machine one : machines) {
            if (one.drops() > most) {
                bad.add(one.id() + " multiplies drops by " + one.drops()
                        + " against a ceiling of " + most + ", and a reward that is "
                        + "effectively all drops has its rounding artefacts for an "
                        + "ordering — which is the same failure as taking the orbs "
                        + "away, reached from the other side");
            }
        }
        return bad;
    }

    /** Nothing that is not a killer touches what a kill is worth. */
    public static List<String> faultsInIdleness(List<Machine> machines) {
        List<String> bad = new ArrayList<>();
        for (Machine one : machines) {
            if (!one.kind().equals(KILLS) && one.orbs() != 1.0D) {
                bad.add(one.id() + " is not a killer and changes the experience by "
                        + one.orbs() + ", so what a mob is worth depends on a block "
                        + "somewhere else in the room");
            }
        }
        for (Machine one : machines) {
            if (!one.kind().equals(KILLS) && one.drops() != 1.0D) {
                bad.add(one.id() + " is not a killer and changes the drops by "
                        + one.drops() + ", so a player reasoning about their farm has "
                        + "to know what every block in it multiplies");
            }
        }
        return bad;
    }

    /**
     * Every farm anybody can build still lands inside the band.
     *
     * <p>Not the machines one at a time. Two that each pass alone are not two
     * that pass together, and a farm is a room full of them — so this takes the
     * product of the whole list. The harness walks every subset; this is the
     * one that matters most, which is all of them at once.
     */
    public static List<String> faultsInSpread(List<Machine> machines,
                                              List<BeastRules.Beast> beasts,
                                              Map<String, BeastRules.Reward> rewards,
                                              double widest) {
        List<String> bad = new ArrayList<>();
        double[] both = together(machines);
        double through = spreadThrough(beasts, rewards, both[0], both[1]);
        if (through > widest) {
            bad.add("a farm holding every machine this module ships spreads the "
                    + "reward per unit of danger to " + Math.round(through * 1000.0D)
                    / 1000.0D + " against a band of " + widest + ", so the thing the "
                    + "whole bestiary is balanced on stops being true the moment "
                    + "somebody automates it");
        }
        return bad;
    }
}
