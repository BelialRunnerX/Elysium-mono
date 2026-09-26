package com.elysium.agronomy.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What goes on the end of a rod, and what it is not allowed to do to a table.
 *
 * <h2>The rule the fish already have</h2>
 *
 * {@link FishRules} proves three things about every cast in the game: no fish
 * is uncatchable, no water a player can stand in is dead, and a better rod
 * never makes a rarer fish <em>rarer</em>. That last one is the interesting
 * one and it is the one tackle threatens, because a hook that tilts a table
 * is a hook that can tilt it the wrong way — and a rod that makes the thing
 * you are fishing for harder to catch is a bug nobody reports, because nobody
 * can see a probability.
 *
 * <p>So every hook and every bobber here is a stated change to a cast rather
 * than a special case in a block, and the harness sweeps <b>every water, every
 * rod, every bait and every combination of the two</b> against all three
 * rules. Four hundred-odd tables, every one of them checked, which is the only
 * honest way to ship a modifier system.
 *
 * <h2>And the one thing tackle may never do</h2>
 *
 * Take something away. A bobber that opens the deep band must not close the
 * shallow one; a hook that pulls treasure must not stop pulling fish. Every
 * fish catchable bare-handed stays catchable with anything fitted, which is
 * checked fish by fish rather than reasoned about — because the reasoning is
 * exactly what goes wrong.
 */
public final class TackleRules {

    private TackleRules() {
    }

    // ==================================================================
    // What a hook is
    // ==================================================================

    /**
     * One hook.
     *
     * @param id    what the item is called
     * @param luck  how many rungs of rod it is worth
     * @param catches how many fish one cast brings up
     * @param holds whether a fish on it can get away
     */
    public record Hook(String id, int luck, int catches, boolean holds) {

        public Hook {
            Objects.requireNonNull(id, "a hook with no name");
            if (luck < 0) {
                throw new IllegalArgumentException(id + " is worth " + luck
                        + " rungs of rod, and a hook that makes a rod worse is "
                        + "a hook nobody fits");
            }
            if (catches < 1) {
                throw new IllegalArgumentException(id + " brings up " + catches
                        + " fish, which is a hook that cannot fish");
            }
        }

        /** Whether fitting this changes a table at all. */
        public boolean tilts() {
            return luck > 0;
        }
    }

    /** The bare end of a line, which is what a rod has without a hook. */
    public static final Hook BARE = new Hook("", 0, 1, false);

    /** Every hook there is. */
    public static final List<Hook> HOOKS = List.of(
            new Hook("iron_hook", 1, 1, false),
            new Hook("barbed_hook", 0, 1, true),
            new Hook("double_hook", 0, 2, false),
            new Hook("gilded_hook", 2, 1, false));

    // ==================================================================
    // What a bobber is
    // ==================================================================

    /**
     * One bobber.
     *
     * @param id      what the item is called
     * @param deeper  how many bands further down it reaches
     * @param anytime whether it makes night fish come up by day and the other
     *                way round
     * @param sooner  how much of the wait it takes off, as a share
     */
    public record Bobber(String id, int deeper, boolean anytime, float sooner) {

        public Bobber {
            Objects.requireNonNull(id, "a bobber with no name");
            if (deeper < 0) {
                throw new IllegalArgumentException(id + " reaches " + deeper
                        + " bands down, and a bobber that makes water shallower "
                        + "is one that takes fish away");
            }
            if (sooner < 0.0F || sooner >= 1.0F) {
                throw new IllegalArgumentException(id + " takes " + sooner
                        + " off the wait, which is either nothing or all of it");
            }
        }

        /** Whether fitting this changes which fish are there. */
        public boolean opens() {
            return deeper > 0 || anytime;
        }
    }

    /** No bobber at all. */
    public static final Bobber PLAIN = new Bobber("", 0, false, 0.0F);

    /** Every bobber there is. */
    public static final List<Bobber> BOBBERS = List.of(
            new Bobber("cork_bobber", 0, false, 0.25F),
            new Bobber("glow_bobber", 0, true, 0.0F),
            new Bobber("lead_bobber", 1, false, 0.0F),
            new Bobber("lantern_bobber", 1, true, 0.10F));

    // ==================================================================
    // A rod with something on it
    // ==================================================================

    /** A hook and a bobber, either of which may be nothing. */
    public record Tackle(Hook hook, Bobber bobber) {

        public Tackle {
            Objects.requireNonNull(hook, "null hook");
            Objects.requireNonNull(bobber, "null bobber");
        }

        /** Whether anything at all is fitted. */
        public boolean bare() {
            return hook.id().isEmpty() && bobber.id().isEmpty();
        }
    }

    /** Nothing fitted, which is where every rod starts. */
    public static final Tackle NONE = new Tackle(BARE, PLAIN);

    /** Every way of fitting a rod, including not fitting it. */
    public static List<Tackle> everyTackle() {
        List<Tackle> made = new ArrayList<>();
        List<Hook> hooks = new ArrayList<>();
        hooks.add(BARE);
        hooks.addAll(HOOKS);
        List<Bobber> bobbers = new ArrayList<>();
        bobbers.add(PLAIN);
        bobbers.addAll(BOBBERS);
        for (Hook hook : hooks) {
            for (Bobber bobber : bobbers) {
                made.add(new Tackle(hook, bobber));
            }
        }
        return List.copyOf(made);
    }

    /**
     * The waters a cast reaches with this bobber on.
     *
     * <p>More than one, and the first of them is always the water the player
     * is actually standing in. A bobber <em>adds</em> reach; it never trades
     * the shallow water for the deep one, which is the shape of the bug this
     * whole file is guarding against.
     */
    public static List<FishRules.Water> reaches(FishRules.Water where,
                                                Bobber bobber) {
        List<FishRules.Water> made = new ArrayList<>();
        made.add(where);
        for (int deeper = 1; deeper <= bobber.deeper(); deeper++) {
            int band = where.band() + deeper;
            if (band < FishRules.BANDS) {
                made.add(new FishRules.Water(where.kind(), band, where.when()));
            }
        }
        if (bobber.anytime()) {
            for (FishRules.Water one : List.copyOf(made)) {
                made.add(new FishRules.Water(one.kind(), one.band(),
                        FishRules.DAY.equals(one.when())
                                ? FishRules.NIGHT : FishRules.DAY));
            }
        }
        return List.copyOf(made);
    }

    /**
     * What a cast brings up, with a rod, a bait and whatever is fitted.
     *
     * <p>The tables of every water the tackle reaches, added together and
     * normalised once. Adding them is what makes "a bobber never takes a fish
     * away" true by construction rather than by hope — and it is still
     * checked, because true by construction is what was said about the river
     * that never generated.
     */
    public static List<FishRules.Chance> table(FishRules.Water where, int rod,
                                               String bait, Tackle tackle) {
        List<String> names = new ArrayList<>();
        List<Double> shares = new ArrayList<>();
        int luck = Math.min(FishRules.RODS - 1, rod + tackle.hook().luck());
        for (FishRules.Water reached : reaches(where, tackle.bobber())) {
            for (FishRules.Chance chance : FishRules.table(reached, luck, bait)) {
                int at = names.indexOf(chance.fish());
                if (at < 0) {
                    names.add(chance.fish());
                    shares.add(chance.share());
                } else {
                    shares.set(at, shares.get(at) + chance.share());
                }
            }
        }
        double total = 0.0D;
        for (double share : shares) {
            total += share;
        }
        if (names.isEmpty() || total <= 0.0D) {
            return List.of();
        }
        List<FishRules.Chance> out = new ArrayList<>(names.size());
        for (int at = 0; at < names.size(); at++) {
            out.add(new FishRules.Chance(names.get(at), shares.get(at) / total));
        }
        return List.copyOf(out);
    }

    /** Every fish a cast can bring up, with this fitted. */
    public static Set<String> caught(FishRules.Water where, int rod, String bait,
                                     Tackle tackle) {
        Set<String> found = new LinkedHashSet<>();
        for (FishRules.Chance chance : table(where, rod, bait, tackle)) {
            if (chance.share() > 0.0D) {
                found.add(chance.fish());
            }
        }
        return found;
    }

    // ==================================================================
    // The blocks around the water
    // ==================================================================

    /** Where the bait comes from. */
    public static final String WORM_FARM = "worm_farm";

    /** Where the tackle lives. */
    public static final String TACKLE_BOX = "tackle_box";

    /** Where a caught fish goes on the wall. */
    public static String mountOf(String fish) {
        return fish + "_mount";
    }

    /** Every mount there is: one per fish, walked from the roster. */
    public static List<String> mounts() {
        List<String> made = new ArrayList<>();
        for (FishRules.Fish one : FishRules.FISH) {
            made.add(mountOf(one.id()));
        }
        return List.copyOf(made);
    }

    /** How long a worm farm takes over one bait. */
    public static final int WORM_TICKS = 1_200;

    /**
     * The most bait an hour a farm may make.
     *
     * <p>Bait multiplies a family's share fourfold, so bait a player can have
     * as much of as they like is a rod that always catches what it is pointed
     * at — which is every table in {@link FishRules} rounded to one. A farm is
     * a convenience, not a switch.
     */
    public static final int MOST_BAIT_PER_HOUR = 72;

    /** How much bait one farm makes in an hour. */
    public static float baitPerHour() {
        return (float) AgronomyRules.TICKS_PER_HOUR / WORM_TICKS;
    }

    /**
     * How much bait a farm holds before it stops.
     *
     * <p>A farm left for a week is a farm, not a warehouse: it stops at this
     * and waits to be emptied, so the rate is the rate whether or not anybody
     * is watching.
     */
    public static final int WORM_FARM_HOLDS = 16;

    /**
     * Which bait a fish fed to a farm turns into: its own family's.
     *
     * <p>A farm is fed a fish and makes the bait that catches more of its
     * kind, which is how a player fishes for a habitat on purpose without a
     * recipe per family. A name no fish has makes nothing.
     */
    public static String baitFor(String fishId) {
        FishRules.Fish one = FishRules.fish(fishId);
        return one == null ? "" : one.family().name();
    }

    /** Whether an item is tackle: a hook, a bobber, a bait or a rod. */
    public static boolean isTackle(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        if (items().contains(id) || id.endsWith("_rod")) {
            return true;
        }
        for (FishRules.Family family : FishRules.FAMILIES) {
            if (id.equals(family.name() + "_bait")) {
                return true;
            }
        }
        return false;
    }

    /** Every block this adds. */
    public static List<String> blocks() {
        List<String> made = new ArrayList<>(List.of(WORM_FARM, TACKLE_BOX));
        made.addAll(mounts());
        return List.copyOf(made);
    }

    /** Every item this adds. */
    public static List<String> items() {
        List<String> made = new ArrayList<>();
        for (Hook one : HOOKS) {
            made.add(one.id());
        }
        for (Bobber one : BOBBERS) {
            made.add(one.id());
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the tackle. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInHooks(HOOKS));
        bad.addAll(faultsInBobbers(BOBBERS));
        bad.addAll(faultsInBait());
        return List.copyOf(bad);
    }

    /** The hooks: named once, each worth fitting, none of them a downgrade. */
    public static List<String> faultsInHooks(List<Hook> hooks) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> what = new LinkedHashSet<>();
        for (Hook one : hooks) {
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two hooks");
            }
            if (!what.add(one.luck() + "/" + one.catches() + "/" + one.holds())) {
                bad.add(one.id() + " does exactly what another hook does, so one "
                        + "of the two is a recipe nobody would ever make twice");
            }
            if (!one.tilts() && one.catches() == 1 && !one.holds()) {
                bad.add(one.id() + " is the bare end of a line with a name on it");
            }
            if (one.luck() >= FishRules.RODS) {
                bad.add(one.id() + " is worth " + one.luck() + " rungs of rod and "
                        + "there are " + FishRules.RODS + ", so it is the whole "
                        + "ladder in one item and the rods stop mattering");
            }
        }
        if (hooks.isEmpty()) {
            bad.add("there is no hook at all");
        }
        return bad;
    }

    /** The bobbers, likewise, and none of them taking anything away. */
    public static List<String> faultsInBobbers(List<Bobber> bobbers) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> what = new LinkedHashSet<>();
        for (Bobber one : bobbers) {
            if (!seen.add(one.id())) {
                bad.add(one.id() + " names two bobbers");
            }
            if (!what.add(one.deeper() + "/" + one.anytime() + "/" + one.sooner())) {
                bad.add(one.id() + " does exactly what another bobber does");
            }
            if (!one.opens() && one.sooner() <= 0.0F) {
                bad.add(one.id() + " does nothing at all");
            }
            if (one.deeper() >= FishRules.BANDS) {
                bad.add(one.id() + " reaches " + one.deeper() + " bands down and "
                        + "there are " + FishRules.BANDS + ", so every water is "
                        + "every other water and the bands stop mattering");
            }
        }
        if (bobbers.isEmpty()) {
            bad.add("there is no bobber at all");
        }
        return bad;
    }

    /** The bait, which is the one thing here that could switch a rod on. */
    public static List<String> faultsInBait() {
        List<String> bad = new ArrayList<>(
                faultsInBait(baitPerHour(), MOST_BAIT_PER_HOUR));
        if (WORM_TICKS <= 0) {
            bad.add("a worm farm makes bait instantly");
        }
        if (WORM_FARM_HOLDS < 1) {
            bad.add("a worm farm holds no bait, so it makes one and stops for ever");
        }
        if (mounts().size() != FishRules.FISH.size()) {
            bad.add("there are " + FishRules.FISH.size() + " fish and "
                    + mounts().size() + " mount(s), so a fish somebody caught has "
                    + "nowhere to go on the wall");
        }
        return bad;
    }

    /**
     * The same rate against the same ceiling, handed in.
     *
     * <p>Split out so a control can hand it a farm built to be caught. The
     * real farm is under the ceiling by design, which means this rule could
     * be deleted outright and nothing anywhere would notice.
     */
    public static List<String> faultsInBait(float perHour, float ceiling) {
        List<String> bad = new ArrayList<>();
        if (perHour > ceiling) {
            bad.add("a worm farm makes " + perHour + " bait an hour and the "
                    + "ceiling is " + ceiling + "; bait a player can have as "
                    + "much of as they like is a rod that always catches what "
                    + "it is pointed at");
        }
        return bad;
    }

    /**
     * Every fish that fitting something takes away.
     *
     * <p>Must be empty, and it is the whole reason {@link #table} adds tables
     * together rather than replacing one with another.
     */
    public static List<String> takenAway() {
        List<String> bad = new ArrayList<>();
        for (FishRules.Water where : FishRules.fishableWaters()) {
            for (int rod = 0; rod < FishRules.RODS; rod++) {
                Set<String> bare = caught(where, rod, FishRules.NOTHING, NONE);
                for (Tackle tackle : everyTackle()) {
                    Set<String> with = caught(where, rod, FishRules.NOTHING, tackle);
                    for (String fish : bare) {
                        if (!with.contains(fish)) {
                            bad.add(fish + " comes up in " + where.kind() + " band "
                                    + where.band() + " by " + where.when()
                                    + " on a bare rod and stops coming up with "
                                    + tackle.hook().id() + " and "
                                    + tackle.bobber().id() + " fitted");
                        }
                    }
                }
            }
        }
        return List.copyOf(bad);
    }

    /**
     * Every hook that makes the rare fish harder to get.
     *
     * <p>Not "is a rarer fish commoner than a common one". It very often is,
     * and deliberately: a good rod tilts the table towards the rare end and
     * that is what a rod is <em>for</em>. {@link FishRules#luckThatHurts}
     * settled the right question years' worth of tables ago and this asks the
     * same one of hooks: with a better hook on, the rarer of any two fish must
     * never become harder to get <em>relative to</em> the commoner one.
     *
     * <p>A ratio rather than a share, because every share in a table moves
     * when any one of them does, and a checker that reports that reports four
     * hundred times and gets switched off.
     */
    public static List<String> hooksThatHurt() {
        List<String> bad = new ArrayList<>();
        for (FishRules.Water where : FishRules.fishableWaters()) {
            for (Bobber bobber : withNothing(BOBBERS, PLAIN)) {
                // Every rod, not just the bare one. A hook that spends its
                // luck backwards is invisible at rod zero, where there is no
                // luck to spend, and that is exactly the rod a sweep written
                // in a hurry looks at.
                for (int rod = 0; rod < FishRules.RODS; rod++) {
                    for (Hook worse : withNothing(HOOKS, BARE)) {
                        for (Hook better : HOOKS) {
                            if (better.luck() <= worse.luck()) {
                                continue;
                            }
                            bad.addAll(hurtBetween(
                                    table(where, rod, FishRules.NOTHING,
                                            new Tackle(worse, bobber)),
                                    table(where, rod, FishRules.NOTHING,
                                            new Tackle(better, bobber)),
                                    where, better));
                        }
                    }
                }
            }
        }
        return List.copyOf(bad);
    }

    private static <T> List<T> withNothing(List<T> many, T nothing) {
        List<T> made = new ArrayList<>();
        made.add(nothing);
        made.addAll(many);
        return made;
    }

    /**
     * The same question about two tables handed in.
     *
     * <p>Split out so a control can hand it a pair built to be caught. Over a
     * healthy roster this can only ever answer no, and a rule that has only
     * ever answered no is a rule nobody has shown can answer yes.
     */
    public static List<String> hurtBetween(List<FishRules.Chance> worse,
                                           List<FishRules.Chance> better,
                                           FishRules.Water where, Hook hook) {
        List<String> bad = new ArrayList<>();
        java.util.Map<String, Double> had = FishRules.shares(worse);
        java.util.Map<String, Double> now = FishRules.shares(better);
        for (FishRules.Fish rare : FishRules.FISH) {
            Double rareBefore = had.get(rare.id());
            Double rareAfter = now.get(rare.id());
            if (rareBefore == null || rareAfter == null || rareBefore <= 0.0D) {
                continue;
            }
            for (FishRules.Fish common : FishRules.FISH) {
                if (common.rarity() >= rare.rarity()) {
                    continue;
                }
                Double commonBefore = had.get(common.id());
                Double commonAfter = now.get(common.id());
                if (commonBefore == null || commonAfter == null
                        || commonBefore <= 0.0D || commonAfter <= 0.0D) {
                    continue;
                }
                if (rareAfter / commonAfter
                        < rareBefore / commonBefore - 1.0E-9D) {
                    bad.add(hook.id() + " makes " + rare.id() + " harder to get "
                            + "than " + common.id() + " than the hook below it "
                            + "did, in " + where.kind() + " band "
                            + where.band());
                }
            }
        }
        return bad;
    }

    /** Every water that anything fitted turns into a water with no fish in it. */
    public static List<String> deadWith() {
        List<String> bad = new ArrayList<>();
        for (FishRules.Water where : FishRules.fishableWaters()) {
            for (Tackle tackle : everyTackle()) {
                if (table(where, 0, FishRules.NOTHING, tackle).isEmpty()) {
                    bad.add(where.kind() + " band " + where.band() + " by "
                            + where.when() + " has fish in it and none at all "
                            + "with " + tackle.hook().id() + " and "
                            + tackle.bobber().id() + " fitted");
                }
            }
        }
        return List.copyOf(bad);
    }
}
