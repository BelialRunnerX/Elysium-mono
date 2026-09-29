package com.elysium.arsenal.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The digging, executed.
 *
 * <p>Four rosters and one function. The rosters are checked the way everything
 * else in this module is: nothing pointless, nothing that is something else
 * under a different name, nothing so rare it may as well not exist.
 *
 * <p>And two things the rest takes for granted, both of which were false until
 * 29 September 2026: that every site is somewhere in a world, and that every
 * fossil half, totem and rotten log is in some pool. A dig can pass every other
 * check here and still be a mechanic only a command can reach.
 *
 * <p>The function is {@link DigRules#whatIsBuried}, and it gets a sweep of its
 * own because everything about dig sites rests on it being a function of the
 * place. Three separate things have to be true at once and each fails
 * differently: asking twice must give the same answer, or a player can break
 * the block and re-roll; two blocks a step apart must usually differ, or a
 * whole plane of the world holds the same thing; and across a field of sites
 * the spread must match the weights, or "rare" is a word in a table rather
 * than something about the game.
 */
public final class DigHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + trimmed(complaint));
            failures++;
        }
    }

    private static String trimmed(String complaint) {
        return complaint.length() <= 220 ? complaint
                : complaint.substring(0, 217) + "...";
    }

    public static void main(String[] args) {
        theBrushes();
        theSites();
        whereTheSitesAre();
        whatTheDigPromises();
        whatIsBuriedIsAPlace();
        theFossils();
        theArtifacts();
        theTotems();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: three brushes that climb and none of "
                + "them fast, sites that every world holds and whose contents are a "
                + "function of where they are, every fossil half and totem in a pool, "
                + "seven fossils in halves worth pairing, three artefacts none of "
                + "which is a worse version of another, and two sorts of totem that "
                + "stay two sorts");
    }

    // ==================================================================

    private static void theBrushes() {
        check(DigRules.ladderDips().isEmpty(),
                "rungs that buy nothing: " + DigRules.ladderDips());
        check(DigRules.tooFast().isEmpty(),
                "brushes fast enough to make brushing pointless: "
                        + DigRules.tooFast());
        check(DigRules.replacingTheGamesBrush().isEmpty(),
                "the game's own brush deleted: "
                        + DigRules.replacingTheGamesBrush());

        Set<String> names = new HashSet<>();
        for (DigRules.Brush one : DigRules.BRUSHES) {
            check(names.add(one.name()), one.name() + " is declared twice");
            check(one.ticks() >= DigRules.FASTEST,
                    one.name() + " is under the floor");
            check(one.aWholeSite() > 0, one.name() + " finishes a site instantly");
        }
        check(DigRules.BRUSHES.size() >= 2,
                "there is only one brush, so the ladder check has nothing to "
                        + "compare and passes by never running");

        // The floor itself. A brush at the floor must still be slower than
        // simply breaking the block, or the mechanic has no reason to exist.
        check(DigRules.FASTEST * DigRules.STAGES > 10,
                "the fastest possible brush finishes a site in "
                        + DigRules.FASTEST * DigRules.STAGES + " ticks, which is "
                        + "about as long as breaking a block");

        int refused = 0;
        try {
            new DigRules.Brush("instant", 0, 4.0F);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new DigRules.Brush("nowhere", 8, 0.0F);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 2, refused + " of 2 impossible brushes were refused");
        System.out.println("  the brushes: " + DigRules.BRUSHES.size()
                + " rungs, the fastest finishing a site in "
                + DigRules.BRUSHES.get(DigRules.BRUSHES.size() - 1).aWholeSite()
                + " ticks");
    }

    private static void theSites() {
        check(DigRules.emptySites().isEmpty(),
                "sites with nothing in them, or pools nothing holds: "
                        + DigRules.emptySites());
        check(DigRules.repeatedFinds().isEmpty(),
                "pools holding the same thing twice: " + DigRules.repeatedFinds());
        check(DigRules.unreachablyRare().isEmpty(),
                "finds so rare they may as well not exist: "
                        + DigRules.unreachablyRare());

        int refused = 0;
        try {
            new DigRules.Site("dirt", "dirt", "dirt");
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new DigRules.Find("nothing", 0);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 2, refused + " of 2 impossible sites were refused");

        for (DigRules.Site site : DigRules.SITES) {
            check(DigRules.weightOf(site.pool()) > 0,
                    site.block() + "'s pool weighs nothing, so every roll in it "
                            + "divides by zero");
        }
        System.out.println("  the sites: " + DigRules.SITES.size()
                + ", drawing from " + DigRules.POOLS.size() + " pool(s)");
    }

    /**
     * Every site in a world.
     *
     * <p>A site nothing lays down passes every other check in this file: its
     * pool is full, its hash stirs and its spread is the weights, and no
     * player ever sees one.
     */
    private static void whereTheSitesAre() {
        check(DigRules.unplaced().isEmpty(),
                "sites no world holds: " + DigRules.unplaced());
        for (DigRules.Patch one : DigRules.PATCHES) {
            check(one.sitesAChunk() > 0.0D, one.site() + "'s patches lay nothing");
            check(one.most() <= DigRules.WIDEST_PATCH,
                    one.site() + "'s patches are wider than the game will load");
        }
        for (DigRules.Seam one : DigRules.SEAMS) {
            check(one.size() <= DigRules.LARGEST_SEAM,
                    one.site() + "'s veins are larger than the game will load");
            check(one.lowest() >= DigRules.WORLD_FLOOR
                            && one.highest() < DigRules.WORLD_CEILING,
                    one.site() + " lies outside the world");
        }
        check(DigRules.columnsWithin(1) == 5 && DigRules.columnsWithin(2) == 13,
                "a disk's columns are not counted the way the game lays them, so "
                        + "every density printed below is wrong");

        int refused = 0;
        List<String> ground = List.of("minecraft:dirt");
        List<String> plains = List.of("minecraft:plains");
        try {
            new DigRules.Patch("too_wide", 10, 1, DigRules.WIDEST_PATCH + 1, ground,
                    plains);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new DigRules.Patch("never", 0, 1, 2, ground, plains);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new DigRules.Patch("nowhere", 10, 1, 2, ground, List.of());
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new DigRules.Seam("too_big", 3, DigRules.LARGEST_SEAM + 1, 0, 160,
                    "minecraft:dirt", plains);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new DigRules.Seam("upside_down", 3, 6, 160, 0, "minecraft:dirt", plains);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 5, refused + " of 5 impossible placements were refused");

        // The control: with the patches taken away, every patched site is
        // reported. A check that cannot say "no world" says "every world"
        // just as convincingly.
        List<String> bare = DigRules.unplaced(DigRules.SITES, List.of(), DigRules.SEAMS);
        for (DigRules.Patch one : DigRules.PATCHES) {
            boolean named = false;
            for (String complaint : bare) {
                named |= complaint.startsWith(one.site() + " is in no world");
            }
            check(named, "with no patches at all, " + one.site() + " was not reported "
                    + "as in no world, so the check cannot report the thing it exists "
                    + "to report");
        }

        StringBuilder thick = new StringBuilder();
        for (DigRules.Patch one : DigRules.PATCHES) {
            thick.append(", ").append(one.site()).append(" about ")
                    .append(Math.round(one.sitesAChunk() * 10.0D) / 10.0D)
                    .append(" a chunk on flat ground");
        }
        System.out.println("  where the sites are: " + DigRules.PATCHES.size()
                + " patch(es) and " + DigRules.SEAMS.size() + " seam(s), every site "
                + "in a world" + thick);
    }

    /**
     * Everything only a dig hands out, in some pool.
     *
     * <p>The fossil halves and the totems come from brushing and from nothing
     * else in the game. They are walked here one at a time as well as asked of
     * the rules, so a rule that stopped looking would not take this with it.
     */
    private static void whatTheDigPromises() {
        check(DigRules.neverDugUp().isEmpty(),
                "promised by the dig and in no pool: " + DigRules.neverDugUp());

        Set<String> held = new HashSet<>();
        for (List<DigRules.Find> finds : DigRules.POOLS.values()) {
            for (DigRules.Find one : finds) {
                held.add(one.item());
            }
        }
        int halves = 0;
        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            for (String part : List.of(FossilRules.SKULL, FossilRules.BODY)) {
                halves++;
                check(held.contains("elysiumarsenal:" + one.id(part)),
                        one.id(part) + " is in no pool, so the " + one.creature()
                                + " fossil can never be made whole");
            }
        }
        for (TotemRules.Totem one : TotemRules.TOTEMS) {
            check(held.contains("elysiumarsenal:" + one.name()),
                    one.name() + " is a totem no pool holds, so it can never be had");
        }

        // The control: with nothing in any pool, every promise is reported.
        int promised = halves + TotemRules.TOTEMS.size() + DigRules.TURNED_UP.size();
        int reported = DigRules.neverDugUp(Map.of()).size();
        check(reported == promised,
                "an empty set of pools was reported as missing " + reported + " of "
                        + promised + " finds, so the check cannot report the thing "
                        + "it exists to report");
        System.out.println("  what the dig promises: " + halves + " fossil halves, "
                + TotemRules.TOTEMS.size() + " totems and " + DigRules.TURNED_UP.size()
                + " other finds, every one in a pool");
    }

    /**
     * The one function everything about sites rests on.
     *
     * <p>Three properties, and every one of them is a different bug when it
     * goes: the same block must always hold the same thing; two blocks a step
     * apart must usually not; and over a field the spread must be the weights.
     */
    private static void whatIsBuriedIsAPlace() {
        long seed = 8_675_309L;
        int repeated = 0;
        int rounds = 4_000;
        for (int round = 0; round < rounds; round++) {
            DigRules.Find once = DigRules.whatIsBuried("dirt", seed, round, 64, 0);
            DigRules.Find twice = DigRules.whatIsBuried("dirt", seed, round, 64, 0);
            if (once == twice) {
                repeated++;
            }
        }
        check(repeated == rounds, "asking what is buried twice gave a different "
                + "answer " + (rounds - repeated) + " times out of " + rounds
                + ", so a player who brushes halfway and comes back finds something "
                + "else — and one who dislikes what they found can break the block "
                + "and roll again");

        // A different seed has to give a different world. Without this the
        // loot is a function of the coordinates alone and every world in
        // existence has the same thing buried in the same place.
        int differedBySeed = 0;
        for (int round = 0; round < rounds; round++) {
            DigRules.Find here = DigRules.whatIsBuried("dirt", seed, round, 64, 0);
            DigRules.Find there = DigRules.whatIsBuried("dirt", seed + 1, round, 64, 0);
            if (here != there) {
                differedBySeed++;
            }
        }
        check(differedBySeed > rounds / 4, "only " + differedBySeed + " of " + rounds
                + " sites differ between two worlds, so what is buried is barely a "
                + "function of the seed and every world holds the same things in the "
                + "same places");

        // Neighbours. A hash that added its coordinates would give a whole
        // diagonal plane of identical loot, which a player notices in about
        // ten minutes.
        for (int[] step : new int[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 1}}) {
            int differed = 0;
            for (int round = 0; round < rounds; round++) {
                DigRules.Find here = DigRules.whatIsBuried("dirt", seed, round, 64,
                        round / 7);
                DigRules.Find next = DigRules.whatIsBuried("dirt", seed,
                        round + step[0], 64 + step[1], round / 7 + step[2]);
                if (here != next) {
                    differed++;
                }
            }
            check(differed > rounds / 2, "moving by " + step[0] + "," + step[1] + ","
                    + step[2] + " changed what is buried only " + differed
                    + " times in " + rounds + ", so the hash barely stirs and whole "
                    + "runs of blocks hold the same thing");
        }

        // And the spread. A site table that never gives up its rarest entry
        // is a table with one fewer entry than it says.
        for (String pool : DigRules.POOLS.keySet()) {
            TreeMap<String, Integer> seen = new TreeMap<>();
            int total = DigRules.weightOf(pool);
            int field = 240;
            for (int x = 0; x < field; x++) {
                for (int z = 0; z < field; z++) {
                    seen.merge(DigRules.whatIsBuried(pool, seed, x, 64, z).item(),
                            1, Integer::sum);
                }
            }
            int sites = field * field;
            for (DigRules.Find find : DigRules.POOLS.get(pool)) {
                int got = seen.getOrDefault(find.item(), 0);
                check(got > 0, find.item() + " is in the " + pool + " pool and was "
                        + "never once buried in " + sites + " sites");
                double want = (double) find.weight() / total * sites;
                check(Math.abs(got - want) < Math.max(150.0D, want * 0.15D),
                        find.item() + " turned up " + got + " times in " + sites
                                + " sites and should have turned up about "
                                + Math.round(want));
            }
        }

        // Negative coordinates are half the world.
        for (int x : new int[]{-1, -1_000, Integer.MIN_VALUE / 4, 0, 1_000}) {
            for (String pool : DigRules.POOLS.keySet()) {
                DigRules.Find found = DigRules.whatIsBuried(pool, seed, x, -60, x);
                check(found != null, "nothing is buried at x=" + x + " in " + pool);
            }
        }
        System.out.println("  what is buried: the same answer every time, a "
                + "different one in another world, different from its neighbours, "
                + "and spread across 57,600 sites in the proportions the pool "
                + "claims");
    }

    private static void theFossils() {
        check(FossilRules.sharingAnEffect().isEmpty(),
                "two fossils that do the same thing: "
                        + FossilRules.sharingAnEffect());
        check(FossilRules.dominated().isEmpty(),
                "fossils a player digs past: " + FossilRules.dominated());
        check(FossilRules.repeated().isEmpty(),
                "fossils declared twice: " + FossilRules.repeated());
        check(FossilRules.halvesThatDoNotAddUp().isEmpty(),
                "halves that do not add up: " + FossilRules.halvesThatDoNotAddUp());
        check(FossilRules.sortsOfOne().isEmpty(),
                "sorts of fossil with only one fossil in them, so the comparison "
                        + "never runs: " + FossilRules.sortsOfOne());

        for (FossilRules.Fossil one : FossilRules.FOSSILS) {
            check(one.reach() <= FossilRules.FURTHEST,
                    one.creature() + " works further than a room");
            check(FossilRules.given(one, true, true) > FossilRules.given(one, true, false),
                    one.creature() + "'s pair is worth no more than its skull alone");
            check(FossilRules.given(one, false, false) == 0.0F,
                    one.creature() + " works with neither half present");
            check(one.id(FossilRules.WHOLE).equals(one.creature() + "_fossil"),
                    one.creature() + "'s whole fossil is not named the way the "
                            + "generator names it, so it has no model");
        }
        check(FossilRules.catalogue().size()
                        == FossilRules.FOSSILS.size() * FossilRules.PARTS.size(),
                "the catalogue is not every fossil in every part");

        int refused = 0;
        try {
            new FossilRules.Fossil("everywhere", "does", "sort", 1.0F,
                    FossilRules.FURTHEST + 1);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new FossilRules.Fossil("nothing", "does", "sort", 0.0F, 4);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 2, refused + " of 2 impossible fossils were refused");
        System.out.println("  the fossils: " + FossilRules.FOSSILS.size()
                + " creatures in " + FossilRules.PARTS.size() + " parts = "
                + FossilRules.catalogue().size() + " blocks, a pair worth "
                + Math.round(FossilRules.worthOfASet() * 100) / 100.0
                + " times the halves apart");
    }

    private static void theArtifacts() {
        check(ArtifactRules.dominated().isEmpty(),
                "artefacts that are worse versions of another: "
                        + ArtifactRules.dominated());
        check(ArtifactRules.sharingAnEffect().isEmpty(),
                "artefacts that do the same thing: "
                        + ArtifactRules.sharingAnEffect());
        check(ArtifactRules.repeated().isEmpty(),
                "artefacts declared twice: " + ArtifactRules.repeated());
        check(ArtifactRules.brokenChain().isEmpty(),
                "the chain from shard to artefact: " + ArtifactRules.brokenChain());

        // Every artefact has to come out of an identification, in its share.
        TreeMap<String, Integer> seen = new TreeMap<>();
        int rounds = 300_000;
        Random random = new Random(9_001L);
        for (int round = 0; round < rounds; round++) {
            ArtifactRules.Artifact got = ArtifactRules.identify(random.nextInt());
            seen.merge(got.name(), 1, Integer::sum);
        }
        double want = (double) rounds / ArtifactRules.ARTIFACTS.size();
        for (ArtifactRules.Artifact one : ArtifactRules.ARTIFACTS) {
            int got = seen.getOrDefault(one.name(), 0);
            check(got > 0, one.name() + " never came out of " + rounds
                    + " identifications, so it is an artefact nobody can get");
            check(Math.abs(got - want) < want * 0.05D,
                    one.name() + " came out " + got + " times and should have come "
                            + "out about " + Math.round(want));
        }
        // And negative picks, which is what a random int actually is.
        for (int pick : new int[]{Integer.MIN_VALUE, -1, -7, 0}) {
            check(ArtifactRules.identify(pick) != null,
                    "a pick of " + pick + " identified nothing");
        }

        for (ArtifactRules.Artifact one : ArtifactRules.ARTIFACTS) {
            check(one.at(0) == one.at(1), one.name() + " at level nothing is not the "
                    + "same as at level one, so gear that carries it without a level "
                    + "does something nobody chose");
            check(one.at(one.levels() + 5) == one.at(one.levels()),
                    one.name() + " keeps growing past its own cap");
            check(one.at(one.levels()) > one.at(1) || one.levels() == 1,
                    one.name() + " is no better at its cap than at one level, so "
                            + "deepening it buys nothing");
        }
        System.out.println("  the artefacts: " + ArtifactRules.ARTIFACTS.size()
                + ", each one in " + Math.round(1.0D / ArtifactRules.chanceOfEach())
                + " identifications, from " + ArtifactRules.SHARDS_PER_ARTIFACT
                + " shards apiece");
    }

    private static void theTotems() {
        check(TotemRules.placedTooStrong().isEmpty(),
                "placed totems that never stop and do too much: "
                        + TotemRules.placedTooStrong());
        check(TotemRules.heldTooWeak().isEmpty(),
                "held totems nobody would carry: " + TotemRules.heldTooWeak());
        check(TotemRules.missingASort().isEmpty(),
                "a sort of totem with nothing in it: " + TotemRules.missingASort());
        check(TotemRules.repeated().isEmpty(),
                "totems declared twice: " + TotemRules.repeated());

        for (TotemRules.Totem one : TotemRules.of(TotemRules.PLACED)) {
            check(one.every() > 0, one.name() + " acts every tick for ever");
            check(one.perMinute() <= TotemRules.MOST_PER_MINUTE,
                    one.name() + " does " + one.perMinute() + " a minute for ever");
            check(one.reach() <= TotemRules.FURTHEST_PLACED,
                    one.name() + " is a rule about the world rather than a thing in "
                            + "a room");
        }
        for (TotemRules.Totem one : TotemRules.of(TotemRules.HELD)) {
            check(one.every() == 0, one.name() + " is held and has a period");
            check(one.power() >= TotemRules.LEAST_HELD,
                    one.name() + " is spent for less than a placed totem gives away");
        }

        int refused = 0;
        try {
            new TotemRules.Totem("neither", "carried", 1.0F, 1, 0);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new TotemRules.Totem("forever", TotemRules.PLACED, 1.0F, 4, 0);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        try {
            new TotemRules.Totem("confused", TotemRules.HELD, 1.0F, 4, 200);
        } catch (IllegalArgumentException expected) {
            refused++;
        }
        check(refused == 3, refused + " of 3 impossible totems were refused");
        System.out.println("  the totems: "
                + TotemRules.of(TotemRules.PLACED).size() + " placed under "
                + TotemRules.MOST_PER_MINUTE + " a minute, and "
                + TotemRules.of(TotemRules.HELD).size() + " held above "
                + TotemRules.LEAST_HELD);
    }

    /**
     * The checkers, against rosters built to be caught.
     *
     * <p>Every predicate above answers "no" when things are healthy. One that
     * has lost the ability to answer "yes" answers "no" just as convincingly,
     * and this module has shipped that failure twice.
     */
    private static void theControls() {
        FossilRules.Fossil strong = new FossilRules.Fossil("control_strong", "does",
                "sort", 2.0F, 6);
        FossilRules.Fossil weak = new FossilRules.Fossil("control_weak", "does_too",
                "sort", 1.0F, 4);
        check(strong.dominates(weak),
                "a fossil that does less of the same sort of thing and works less "
                        + "far out is not reported, so the domination check cannot "
                        + "report anything");
        check(!weak.dominates(strong), "the weaker fossil dominates the stronger");
        check(!strong.dominates(strong), "a fossil dominates itself");

        FossilRules.Fossil elsewhere = new FossilRules.Fossil("control_other",
                "does_else", "other_sort", 1.0F, 4);
        check(!strong.dominates(elsewhere),
                "a fossil that keeps creepers off is compared against one that "
                        + "grows wheat, so the roster would be told to delete one of "
                        + "them");

        ArtifactRules.Artifact big = new ArtifactRules.Artifact("control_big",
                "same", 1.0F, 3, "melee");
        ArtifactRules.Artifact small = new ArtifactRules.Artifact("control_small",
                "same", 0.5F, 1, "melee");
        check(big.dominates(small),
                "an artefact that does less of the same thing on the same gear is "
                        + "not reported as a worse version");
        ArtifactRules.Artifact other = new ArtifactRules.Artifact("control_other",
                "same", 0.5F, 1, "tool");
        check(!big.dominates(other),
                "an artefact on a sword is compared against one on a pickaxe");

        // And a sweep, so none of the above is right about one case only.
        Random random = new Random(31_415L);
        int reported = 0, rounds = 0;
        for (int round = 0; round < 20_000; round++) {
            float onePower = 0.1F + random.nextFloat();
            float twoPower = 0.1F + random.nextFloat();
            int oneLevels = 1 + random.nextInt(ArtifactRules.MOST_LEVELS);
            int twoLevels = 1 + random.nextInt(ArtifactRules.MOST_LEVELS);
            ArtifactRules.Artifact a = new ArtifactRules.Artifact("a" + round,
                    "same", onePower, oneLevels, "melee");
            ArtifactRules.Artifact b = new ArtifactRules.Artifact("b" + round,
                    "same", twoPower, twoLevels, "melee");
            rounds++;
            boolean aWins = onePower >= twoPower && oneLevels >= twoLevels
                    && (onePower > twoPower || oneLevels > twoLevels);
            check(a.dominates(b) == aWins,
                    "domination disagreed with the numbers at " + onePower + "/"
                            + oneLevels + " against " + twoPower + "/" + twoLevels);
            if (a.dominates(b)) {
                reported++;
            }
        }
        check(reported > rounds / 8,
                "only " + reported + " of " + rounds + " built pairs were reported, "
                        + "so the predicate is nearly always saying no");
        System.out.println("  the controls: " + reported + " of " + rounds
                + " built artefact pairs reported, fossils of different sorts never "
                + "compared, and nothing dominating itself");
    }
}
