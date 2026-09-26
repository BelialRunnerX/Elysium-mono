package com.elysium.colonies.rules;

/**
 * The sentence the two mods have in common, executed.
 *
 * The failures worth pinning are the ones that would make a colony either
 * pointless or overwhelming: a floor that never rises, and a raid that scales
 * without limit.
 */
public final class ColonyHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + trimmed(complaint));
        }
    }

    /**
     * One failure, cut to something a reader gets through.
     *
     * <p>A check that lists every place it went wrong can run to thousands of
     * characters. Three failures then show as one and a half, and the most
     * informative of them — usually the short one saying a constant is zero —
     * is the one that falls off the end.
     */
    private static String trimmed(String complaint) {
        if (complaint.length() <= ENOUGH) {
            return complaint;
        }
        return complaint.substring(0, ENOUGH) + " … (" + complaint.length()
                + " characters in all)";
    }

    public static void main(String[] args) {
        progress();
        notice();
        raids();
        charter();
        isolation();
        people();
        wounds();
        practice();
        growth();
        exchange();
        sites();
        stewardship();
        membership();
        relief();
        market();
        spaceport();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a town is progress, conspicuous and worth "
                + "raiding; a world decides what working it is like; a citizen's day is "
                + "their stress, their wounds and their practice; and a town gets on "
                + "with it while you are away, trading and siting its own buildings "
                + "without anybody standing there - "
                + "except where its owner is standing there, "
                + "with a grant while it is new and a watchdog for when it stops, "
                + "trading on a market where knowing the frontier is worth something, "
                + "through a space elevator that pays better the taller it is");
    }

    private static void progress() {
        // --- towns that found themselves: siting, cadence and names -------
        check(SettleRules.SPACING > SettleRules.SEPARATION && SettleRules.SEPARATION > 0,
                "a structure set with separation at or above spacing places nothing");
        check(SettleRules.FROM_SPAWN > 0, "towns may found themselves on the world spawn");
        check(SettleRules.farEnoughFromSpawn(SettleRules.FROM_SPAWN, 0)
                && !SettleRules.farEnoughFromSpawn(SettleRules.FROM_SPAWN - 1, 0)
                && SettleRules.farEnoughFromSpawn(0, -SettleRules.FROM_SPAWN),
                "the distance from spawn is not a square of FROM_SPAWN chunks");
        check(SettleRules.buildDays(0) == 1 && SettleRules.buildDays(1) == 1,
                "a first building takes other than a day");
        for (int level = 1; level < 6; level++) {
            check(SettleRules.buildDays(level + 1) >= SettleRules.buildDays(level),
                    "a taller building went up faster at level " + level);
        }
        check(!SettleRules.finished(0, 0, 1) && SettleRules.finished(1, 0, 1)
                && !SettleRules.finished(2, 0, 3) && SettleRules.finished(3, 0, 3),
                "an order finishes on the wrong day");
        check(!SettleRules.mayFound(1, 0) && SettleRules.mayFound(SettleRules.FOUND_EVERY, 0)
                && SettleRules.mayFound(0, -1000),
                "the founding cadence is not FOUND_EVERY days");
        String[] styles = {null, "Bastion", "Pale Reach", "Cinderhold", "Frostfall", "High Orbit"};
        for (String style : styles) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (long seed = 0; seed < 10_000; seed++) {
                String name = SettleRules.name(seed, style);
                check(!name.isEmpty() && name.length() <= SettleRules.NAME_LIMIT,
                        "a town name is empty or over the host's limit: " + name);
                check(name.equals(SettleRules.name(seed, style)),
                        "the same seed named a town twice differently");
                seen.add(name);
            }
            check(seen.size() >= Math.min(100, SettleRules.namesIn(style)),
                    "too few distinct names for style " + style + ": " + seen.size());
            check(style == null || SettleRules.hasNaming(style),
                    "a styled world has no naming of its own: " + style);
        }
        check(!SettleRules.hasNaming("Medieval Oak") && !SettleRules.name(7L, null).contains(" "),
                "the plain world's names are not run together");
        check(SettleRules.name(7L, "Bastion").contains(" "),
                "a styled world's name is not two words");
        check(SettleRules.mix(1L) != SettleRules.mix(2L) && SettleRules.mix(0L) == SettleRules.mix(0L),
                "the mixer does not mix");

        check(ColonyRules.worldFloor(0) == 0, "a colony nobody lives in was worth a tier");
        check(ColonyRules.worldFloor(-10) == 0, "a negative citizen count was worth a tier");
        check(ColonyRules.worldFloor(ColonyRules.CITIZENS_PER_TIER) == 1,
                "the first tier does not arrive at the citizen count it says it does");
        int previous = -1;
        for (int citizens = 0; citizens < 500; citizens++) {
            int floor = ColonyRules.worldFloor(citizens);
            check(floor >= previous, "the floor fell at " + citizens + " citizens");
            check(floor <= ColonyRules.MAX_COLONY_TIER,
                    "a colony alone passed its own cap at " + citizens + " citizens");
            previous = floor;
        }
        check(ColonyRules.worldFloor(10_000) == ColonyRules.MAX_COLONY_TIER,
                "an enormous colony did not reach the cap, so the cap is unreachable "
                        + "rather than a limit");
        check(ColonyRules.worldFloor(ColonyRules.CITIZENS_PER_TIER * 3) == 3,
                "the floor no longer climbs one tier per CITIZENS_PER_TIER citizens");
    }

    private static void notice() {
        check(ColonyRules.noticeFloor(0) == 0,
                "a player with no colony was on file for having one");
        check(ColonyRules.noticeFloor(1) > 0, "one citizen was not noticed at all");
        check(ColonyRules.noticeFloor(5) > ColonyRules.noticeFloor(4),
                "a larger settlement was not more noticed than a smaller one");
        check(ColonyRules.noticeFloor(10_000) == ColonyRules.MAX_NOTICE,
                "a settlement's mark on a file has no ceiling, so a large town is a "
                        + "permanent maximum-Suspicion sentence");
        check(ColonyRules.MAX_NOTICE < 100,
                "a settlement alone can fill the whole file, which leaves nothing for "
                        + "anything a player actually did");
    }

    private static void raids() {
        check(!ColonyRules.promotes(0),
                "a new player's first raid is not MineColonies' own raid, so the two "
                        + "systems meet before there is anything to meet about");
        check(ColonyRules.promotes(1), "a raid never promotes anything at any tier");
        check(ColonyRules.raiderTier(0) == 0, "tier zero promoted anything");
        check(ColonyRules.raiderTier(99) == ColonyRules.MAX_RAIDER_TIER,
                "a raid scales without limit, which eventually sends something that "
                        + "walks through a town of level-five guards");
        check(ColonyRules.raiderTier(-5) == 0, "a negative tier came out negative");
        for (int tier = 0; tier < 100; tier++) {
            check(ColonyRules.raiderTier(tier) <= tier,
                    "a raid was built at a higher tier than its owner has reached, at "
                            + tier);
        }
        check(ColonyRules.MAX_RAIDER_TIER < ColonyRules.MAX_COLONY_TIER,
                "raiders scale as far as the colony itself does, so growing a town is "
                        + "purely a way to be attacked harder");

        // A colony is a second road to a tier, not a shortcut past the first.
        // Six tiers is sixty character levels' worth, which is a long evening
        // of colony work; a cap much above that would make building huts the
        // fastest way to the deepest content in the game.
        check(ColonyRules.MAX_COLONY_TIER <= 10,
                "a colony alone can reach a tier no amount of adventuring is needed "
                        + "for, so building huts becomes the fastest road to the "
                        + "deepest content in the game");
        check(ColonyRules.worldFloor(Integer.MAX_VALUE) == ColonyRules.MAX_COLONY_TIER,
                "an enormous colony did not stop at the cap");
    }

    // ------------------------------------------------------------------
    // The charter: what a world does to the town on it
    // ------------------------------------------------------------------

    private static void charter() {
        check(CharterRules.localShare(0) == 0.0F,
                "a world that produces nothing still counted as partly local");
        float previous = -1.0F;
        for (int families = 0; families <= 2000; families++) {
            float share = CharterRules.localShare(families);
            check(share >= 0.0F && share < 1.0F,
                    "a charter became wholly local at " + families + " families, so a "
                            + "rich world needs nothing imported ever again");
            check(share >= previous, "the local share fell as the charter grew, at "
                    + families);
            previous = share;
        }
        check(CharterRules.localShare((int) CharterRules.LOCAL_PACE) == 0.5F,
                "the pace does not mean what it says: half at the pace");

        // The work factor lives strictly between its two ends.
        check(CharterRules.workFactor(0) == 1.0F - CharterRules.FOREIGN_PENALTY,
                "a world with nothing local did not cost the whole penalty");
        for (int families = 0; families <= 500; families++) {
            float factor = CharterRules.workFactor(families);
            check(factor >= 1.0F - CharterRules.FOREIGN_PENALTY,
                    "a charter came out worse than having nothing, at " + families);
            check(factor < 1.0F + CharterRules.LOCAL_BONUS,
                    "a charter reached the ceiling it was supposed to approach, at "
                            + families);
        }
        check(CharterRules.workFactor(0) < 1.0F && CharterRules.workFactor(400) > 1.0F,
                "the worst and best worlds are on the same side of neutral, so siting a "
                        + "town somewhere is not a decision");
        check(CharterRules.FOREIGN_PENALTY > CharterRules.LOCAL_BONUS,
                "being somewhere with nothing is not the harder story, so a bare world "
                        + "costs less than a rich one gains");

        // Hazards.
        check(CharterRules.burden(0.0F, 0.0F) == 0,
                "a world with no hazards was still a burden to live on");
        check(CharterRules.burden(1.0F, 0.0F) == CharterRules.BURDEN_PER_HAZARD,
                "a full hazard with nothing sealed did not cost what it says");
        check(CharterRules.burden(1.0F, 1.0F) > 0,
                "a fully sealed town cancelled its world entirely, which is the colony "
                        + "that is the same everywhere again");
        check(CharterRules.burden(1.0F, 1.0F)
                        < CharterRules.burden(1.0F, 0.0F),
                "sealing a town did nothing for the people in it");
        for (float sealed = 0.0F; sealed <= 1.0F; sealed += 0.05F) {
            check(CharterRules.burden(2.0F, sealed) >= 0, "a burden came out negative");
            check(CharterRules.burden(2.0F, sealed)
                            <= CharterRules.burden(2.0F, 0.0F),
                    "sealing made a world worse, at " + sealed);
        }
        check(CharterRules.burden(-3.0F, 0.0F) == 0, "a negative hazard was a burden");
        check(CharterRules.burden(1.0F, 5.0F) == CharterRules.burden(1.0F, 1.0F),
                "sealing past everything kept helping");
        check(CharterRules.MOST_SEALED < 1.0F,
                "sealing can remove a whole hazard, so a roof is a different planet");

        check(CharterRules.airBurden(true, false) == 0, "breathable air was a burden");
        check(CharterRules.airBurden(false, true) == CharterRules.AIRLESS_BURDEN,
                "vacuum did not cost the whole air burden");
        check(CharterRules.airBurden(false, false) > 0,
                "thin or toxic air cost nothing");
        check(CharterRules.airBurden(false, false)
                        < CharterRules.airBurden(false, true),
                "bad air is as bad as no air, so there is no difference between "
                        + "Cinderveil and open space");
    }

    private static void isolation() {
        check(CharterRules.isolation(0) == 0.0F, "home was already isolated");
        float last = -1.0F;
        for (int away = 0; away <= 500; away++) {
            float cut = CharterRules.isolation(away);
            check(cut >= 0.0F && cut < 1.0F,
                    "a world became wholly cut off at " + away + " hops, so nothing can "
                            + "ever reach it");
            check(cut >= last, "isolation fell as a town got further away, at " + away);
            last = cut;
        }
        // Distance makes the charter matter more, in both directions.
        float goodNear = CharterRules.weighted(CharterRules.workFactor(400), 0);
        float goodFar = CharterRules.weighted(CharterRules.workFactor(400), 8);
        float badNear = CharterRules.weighted(CharterRules.workFactor(0), 0);
        float badFar = CharterRules.weighted(CharterRules.workFactor(0), 8);
        check(goodFar > goodNear,
                "a well-sited far colony is no better off than a well-sited near one, "
                        + "so distance decides nothing");
        check(badFar < badNear,
                "a badly sited far colony is no worse off than a near one, so the "
                        + "charter can always be imported around");
        check(CharterRules.weighted(1.0F, 9) == 1.0F,
                "a neutral world stopped being neutral when it moved");
    }

    // ------------------------------------------------------------------
    // People
    // ------------------------------------------------------------------

    private static void people() {
        check(CitizenRules.happiness(0) == 1.0F, "an untroubled citizen was unhappy");
        check(CitizenRules.happiness(-20) == 1.0F, "negative stress made somebody glad");
        float previous = 2.0F;
        for (int stress = 0; stress <= 5000; stress++) {
            float factor = CitizenRules.happiness(stress);
            check(factor > 0.0F, "happiness reached nothing at " + stress
                    + ", so no amount of fixing it could help");
            check(factor >= CitizenRules.HAPPINESS_FLOOR,
                    "happiness fell through its floor at " + stress);
            check(factor <= previous, "more stress made somebody happier, at " + stress);
            previous = factor;
        }
        check(CitizenRules.happiness((int) CitizenRules.HAPPINESS_PACE) == 0.5F,
                "the pace does not mean what it says: halved at the pace");
        check(CitizenRules.happiness(100000) == CitizenRules.HAPPINESS_FLOOR,
                "unbounded misery did not settle on the floor");

        check(CitizenRules.pace(0) == 1.0F, "an untroubled citizen worked slowly");
        for (int stress = 0; stress <= 2000; stress++) {
            check(CitizenRules.pace(stress) >= CitizenRules.WORK_FLOOR,
                    "a citizen stopped working entirely at " + stress + ", so the "
                            + "colony cannot dig itself out");
            check(CitizenRules.pace(stress) <= 1.0F,
                    "stress made somebody work faster, at " + stress);
        }
        check(CitizenRules.WORK_FLOOR > 0.0F,
                "the work floor is nothing, which is a colony a player cannot recover");
    }

    private static void wounds() {
        check(CitizenRules.wounded(1.0F, 0) == 1.0F, "an unhurt citizen was slowed");
        float last = 2.0F;
        for (int hurt = 0; hurt <= 40; hurt++) {
            float rate = CitizenRules.wounded(1.0F, hurt);
            check(rate >= CitizenRules.WORK_FLOOR,
                    "wounds stopped a citizen entirely at " + hurt);
            check(rate <= last, "another wound made somebody faster, at " + hurt);
            last = rate;
        }
        check(CitizenRules.wounded(1.0F, 1) < 1.0F, "one wound cost nothing");
        check(CitizenRules.wounded(1.0F, -5) == 1.0F,
                "a negative number of wounds was a penalty");
        check(CitizenRules.healRate(false) == 1, "wounds healed fast with no hospital");
        check(CitizenRules.healRate(true) > CitizenRules.healRate(false),
                "a hospital did nothing for the wounded");
        check(CitizenRules.HOSPITAL_RATE > 1, "a hospital is not worth building");
    }

    private static void practice() {
        check(CitizenRules.mastery(0) == 0.0F, "somebody was born practised");
        float previous = -1.0F;
        for (int done = 0; done <= 100000; done += 7) {
            float known = CitizenRules.mastery(done);
            check(known >= 0.0F && known < 1.0F,
                    "a citizen became perfectly practised at " + done);
            check(known >= previous, "practice fell with repetition, at " + done);
            previous = known;
        }
        check(CitizenRules.mastery((int) CitizenRules.PRACTICE_PACE) == 0.5F,
                "the pace does not mean what it says");
        check(CitizenRules.practised(0) == 1.0F, "an apprentice already had a bonus");
        for (int done = 0; done <= 20000; done += 13) {
            float worth = CitizenRules.practised(done);
            check(worth >= 1.0F, "practice made somebody worse, at " + done);
            check(worth < 1.0F + CitizenRules.PRACTICE_BONUS,
                    "practice reached the ceiling it was to approach, at " + done);
        }
        check(CitizenRules.PRACTICE_PACE > 100.0F,
                "mastery is a fact about an afternoon rather than about a save");

        // Everything at once. The three factors multiply and none of them can
        // turn a working citizen into nothing.
        for (int stress = 0; stress <= 200; stress += 10) {
            for (int hurt = 0; hurt <= 6; hurt++) {
                for (int done = 0; done <= 2000; done += 500) {
                    float made = CitizenRules.output(stress, hurt, done, 1.0F);
                    check(made > 0.0F, "a citizen's day came out worthless");
                    check(made <= 1.0F + CitizenRules.PRACTICE_BONUS,
                            "a citizen's day was worth more than practice alone allows");
                }
            }
        }
        check(CitizenRules.output(0, 0, 0, 0.0F) == 0.0F,
                "a world worth nothing still produced");
        check(CitizenRules.output(0, 0, 0, -3.0F) == 0.0F,
                "a negative world factor produced something");
        check(CitizenRules.output(0, 0, 5000, 1.0F)
                        > CitizenRules.output(0, 0, 0, 1.0F),
                "a lifetime of practice was worth nothing");
        check(CitizenRules.output(200, 0, 0, 1.0F)
                        < CitizenRules.output(0, 0, 0, 1.0F),
                "misery was worth nothing");
    }

    // ------------------------------------------------------------------
    // Growth
    // ------------------------------------------------------------------

    private static void growth() {
        check(GrowthRules.perDay(0, 1.0F) == 0, "an empty town earned development");
        check(GrowthRules.perDay(10, 0.0F) == 0, "a town doing nothing earned anyway");
        check(GrowthRules.perDay(-4, 1.0F) == 0, "a negative town earned");
        check(GrowthRules.perDay(1, 1.0F) == GrowthRules.PER_CITIZEN_DAY,
                "one content citizen did not earn one citizen-day");
        int earlier = -1;
        for (int citizens = 0; citizens <= 500; citizens++) {
            int earned = GrowthRules.perDay(citizens, 1.0F);
            check(earned >= earlier, "a bigger town earned less, at " + citizens);
            earlier = earned;
        }
        check(GrowthRules.working(10, 4) == 6, "the idle were counted as working");
        check(GrowthRules.working(10, 40) == 0, "more idle than people left a surplus");
        check(GrowthRules.working(10, -3) == 10, "a negative idle count added workers");

        check(GrowthRules.costOf(1) == GrowthRules.FIRST_UPGRADE,
                "the first upgrade did not cost what it says");
        long before = -1L;
        for (int level = 1; level <= 40; level++) {
            long cost = GrowthRules.costOf(level);
            check(cost > before, "a higher level cost no more, at " + level);
            before = cost;
        }
        check(GrowthRules.costOf(4) > GrowthRules.costOf(1) * 4L,
                "the cost climbs linearly, so a town slides from hamlet to capital "
                        + "without pausing");
        check(GrowthRules.costOf(0) == GrowthRules.costOf(1),
                "a level below one cost something different");

        // The limit: a town raises itself to working, not to great.
        check(GrowthRules.UNAIDED_LEVEL >= 2,
                "a town cannot raise anything at all on its own");
        check(!GrowthRules.willRaise(0, 5),
                "a town built something its owner never placed");
        check(GrowthRules.willRaise(1, 5), "a town would not raise its first level");
        check(GrowthRules.willRaise(GrowthRules.UNAIDED_LEVEL - 1, 5),
                "a town stopped one level short of what it is allowed");
        check(!GrowthRules.willRaise(GrowthRules.UNAIDED_LEVEL, 5),
                "a town raises itself past the unaided level, so its owner has nothing "
                        + "left to come home for");
        check(!GrowthRules.willRaise(5, 5), "a finished building was raised again");
        check(!GrowthRules.willRaise(2, 2),
                "a building at its own maximum was raised past it");

        check(!GrowthRules.affords(0L, 1), "a town with nothing afforded an upgrade");
        check(GrowthRules.affords(GrowthRules.costOf(2), 2),
                "a town with exactly enough could not afford it");
        check(GrowthRules.spend(GrowthRules.costOf(2), 2) == 0L,
                "spending everything left something");
        check(GrowthRules.spend(0L, 3) == 0L, "spending nothing went negative");

        check(GrowthRules.patient(10, 0), "a town waited when it had waited long enough");
        check(!GrowthRules.patient(1, 0),
                "a town ordered two upgrades in consecutive days, so a returning player "
                        + "meets a builder with a queue and an empty warehouse");
        check(GrowthRules.DAYS_BETWEEN >= 1, "a town orders upgrades every tick");

        check(GrowthRules.ceiling() == GrowthRules.costOf(GrowthRules.UNAIDED_LEVEL),
                "the bank holds a different amount from the thing it is saving for");
        check(GrowthRules.banked(0L, 0) == 0L, "an idle day banked something");
        for (long held = 0; held < GrowthRules.ceiling() * 2; held += 137) {
            long after = GrowthRules.banked(held, 50);
            check(after <= GrowthRules.ceiling(),
                    "a town banked past its ceiling, so ignoring a colony pays as well "
                            + "as tending it");
            check(after >= Math.min(held, GrowthRules.ceiling()),
                    "banking a day's work lost some of what was already there");
        }
        check(GrowthRules.banked(-500L, 10) == 10L, "a negative bank stayed negative");
    }

    // ------------------------------------------------------------------
    // The Frontier Exchange
    // ------------------------------------------------------------------

    private static void exchange() {
        check(ExchangeRules.surplus(0) == 0, "an empty warehouse had surplus");
        check(ExchangeRules.surplus(ExchangeRules.KEEP) == 0,
                "a town sold the stock it was told to keep");
        check(ExchangeRules.surplus(ExchangeRules.KEEP + 7) == 7,
                "the surplus above the keep was counted wrong");
        check(ExchangeRules.surplus(-40) == 0, "a negative holding was surplus");
        check(ExchangeRules.KEEP > 0,
                "a town keeps nothing back, so the exchange can sell the last plank "
                        + "a builder was about to want");

        // Being noticed for it, and not ruinously.
        check(ExchangeRules.noticeFor(0) == 0, "a town that traded nothing was noticed");
        check(ExchangeRules.noticeFor(1) == ExchangeRules.NOTICE_PER_TRADE,
                "one trade earned the wrong notice");
        check(ExchangeRules.noticeFor(10000) == ExchangeRules.MOST_NOTICE_PER_DAY,
                "a busy day of trading was not capped, so unsticking a builder can "
                        + "bring the Empire down on a player");
        check(ExchangeRules.noticeFor(-4) == 0, "negative trades earned notice");
        check(ExchangeRules.MOST_NOTICE_PER_DAY < 20,
                "a day's trading is worth more notice than most things a player does "
                        + "on purpose");
    }

    // ------------------------------------------------------------------
    // Siting
    // ------------------------------------------------------------------

    private static void sites() {
        check(SiteRules.SPACING > 1, "buildings are placed on top of one another");
        check(SiteRules.RINGS >= 1, "the search never leaves the town hall's square");
        check(SiteRules.furthest() == SiteRules.SPACING * SiteRules.RINGS,
                "the furthest a building goes is not what the grid implies");
        check(SiteRules.squaresIn(0) == 1, "the centre is not one square");
        for (int ring = 1; ring <= SiteRules.RINGS; ring++) {
            check(SiteRules.squaresIn(ring) == 8 * ring,
                    "ring " + ring + " has the wrong number of squares");
        }
        check(SiteRules.squaresSearched() > 100,
                "the search looks at too few places to ever find one");

        // Every square of every ring is visited exactly once, and every one of
        // them really is on that ring. A search that repeated a square could
        // put two buildings in one place given the right timing.
        for (int ring = 0; ring <= SiteRules.RINGS; ring++) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int index = 0; index < SiteRules.squaresIn(ring); index++) {
                int x = SiteRules.offsetX(ring, index);
                int z = SiteRules.offsetZ(ring, index);
                check(seen.add(x + "," + z),
                        "ring " + ring + " visits (" + x + "," + z + ") twice");
                check(Math.max(Math.abs(x), Math.abs(z)) == ring,
                        "ring " + ring + " index " + index + " is not on the ring: ("
                                + x + "," + z + ")");
                check(SiteRules.blocksX(ring, index) == x * SiteRules.SPACING,
                        "a square's block position does not match its grid position");
                check(SiteRules.blocksZ(ring, index) == z * SiteRules.SPACING,
                        "a square's block position does not match its grid position");
            }
            check(seen.size() == SiteRules.squaresIn(ring),
                    "ring " + ring + " does not cover itself");
        }
        // Out-of-range indices wrap rather than escaping the ring.
        for (int ring = 1; ring <= 4; ring++) {
            for (int index = -40; index < 80; index++) {
                int x = SiteRules.offsetX(ring, index);
                int z = SiteRules.offsetZ(ring, index);
                check(Math.max(Math.abs(x), Math.abs(z)) == ring,
                        "an index outside the ring escaped it at " + ring + "/" + index);
            }
        }
        check(SiteRules.offsetX(0, 17) == 0 && SiteRules.offsetZ(0, 99) == 0,
                "the centre ring moved");

        check(SiteRules.levelEnough(64, 64), "flat ground was refused");
        check(SiteRules.levelEnough(64, 64 + SiteRules.LEVEL_TOLERANCE),
                "ground exactly at the tolerance was refused");
        check(!SiteRules.levelEnough(64, 64 + SiteRules.LEVEL_TOLERANCE + 1),
                "ground past the tolerance was accepted, so a town builds up a cliff "
                        + "face and nobody can walk between the huts");
        check(SiteRules.levelEnough(64, 64 - SiteRules.LEVEL_TOLERANCE),
                "the tolerance only works upwards");
        check(SiteRules.LEVEL_TOLERANCE > 0,
                "a town insists on a billiard table and never builds in hills");
    }

    // ------------------------------------------------------------------
    // Who decides where a building goes
    // ------------------------------------------------------------------

    private static void stewardship() {
        check(StewardRules.Steward.named("owner") == StewardRules.Steward.OWNER,
                "a steward could not be named");
        check(StewardRules.Steward.named("TOWN") == StewardRules.Steward.TOWN,
                "naming a steward is case-sensitive");
        check(StewardRules.Steward.named("nonsense") == StewardRules.Steward.ATTENDANCE,
                "an unknown steward did not fall back to attendance, so a typo in a "
                        + "command silently changes who runs a town");
        check(StewardRules.Steward.named(null) == StewardRules.Steward.ATTENDANCE,
                "a null steward did not fall back to attendance");
        for (StewardRules.Steward steward : StewardRules.Steward.values()) {
            check(StewardRules.Steward.named(steward.id()) == steward,
                    "steward " + steward + " does not round-trip through its own id");
        }

        // The owner, always.
        for (int away = 0; away <= 400; away++) {
            check(!StewardRules.maySite(StewardRules.Steward.OWNER, away, 0),
                    "a town whose owner reserved placement built anyway, after " + away
                            + " days");
            check(StewardRules.shouldSuggest(StewardRules.Steward.OWNER, away, 0),
                    "a town whose owner reserved placement stopped telling them what "
                            + "it wanted, after " + away + " days");
        }

        // The town, always.
        for (int away = 0; away <= 400; away++) {
            check(StewardRules.maySite(StewardRules.Steward.TOWN, away, away),
                    "a town told to decide for itself deferred anyway, at " + away);
            check(!StewardRules.shouldSuggest(StewardRules.Steward.TOWN, away, away),
                    "a town told to decide for itself asked permission anyway");
        }

        // Attendance: the interesting case.
        check(!StewardRules.maySite(StewardRules.Steward.ATTENDANCE, 10, 10),
                "a town built while its owner was standing in it");
        check(!StewardRules.maySite(StewardRules.Steward.ATTENDANCE,
                        StewardRules.ABSENCE - 1, 0),
                "a town started building one day before it was supposed to, which is "
                        + "the morning after an owner logged off for the night");
        check(StewardRules.maySite(StewardRules.Steward.ATTENDANCE,
                        StewardRules.ABSENCE, 0),
                "a town never started building, so the whole autonomy is unreachable "
                        + "for anybody who ever visits their colony");
        for (int day = 0; day <= 500; day++) {
            for (int seen = 0; seen <= day; seen++) {
                boolean building = StewardRules.maySite(StewardRules.Steward.ATTENDANCE,
                        day, seen);
                check(building == (day - seen >= StewardRules.ABSENCE),
                        "attendance was decided by something other than how long the "
                                + "owner has been away, at day " + day + " seen " + seen);
                check(building != StewardRules.shouldSuggest(
                                StewardRules.Steward.ATTENDANCE, day, seen),
                        "a town both built and asked, or did neither, at day " + day);
            }
        }
        check(StewardRules.ABSENCE >= 2,
                "a town takes over the moment its owner turns their back, so logging "
                        + "off for the night is enough to lose control of placement");
        check(StewardRules.ABSENCE <= 10,
                "a town waits so long before helping that a player who has genuinely "
                        + "gone away comes back to nothing");

        // A town nobody has ever visited gets on with it from the start.
        check(!StewardRules.everVisited(-1), "a never-visited town claimed a visit");
        check(StewardRules.everVisited(0), "a visit on day zero did not count");
        check(StewardRules.maySite(StewardRules.Steward.ATTENDANCE, 0, -1),
                "a town founded and abandoned immediately waited three days from a "
                        + "visit that never happened");

        // Suggestions are paced.
        check(StewardRules.BETWEEN_SUGGESTIONS > StewardRules.ABSENCE,
                "a tended town is reminded what it wants more often than it would have "
                        + "built the thing, which is a notification a player turns off");
        check(!StewardRules.dueToSuggest(0, 0), "a town suggested twice in one day");
        check(StewardRules.dueToSuggest(StewardRules.BETWEEN_SUGGESTIONS, 0),
                "a town never suggested anything again");
        for (int day = 0; day <= 200; day++) {
            check(StewardRules.dueToSuggest(day, day - StewardRules.BETWEEN_SUGGESTIONS),
                    "a suggestion exactly at the interval was withheld, at " + day);
            check(!StewardRules.dueToSuggest(day, day),
                    "a suggestion was repeated the same day, at " + day);
        }
    }

    // ------------------------------------------------------------------
    // Holding a stake in more than one place
    // ------------------------------------------------------------------

    private static void membership() {
        check(ColonyRules.worldFloor(java.util.List.<Integer>of()) == 0,
                "a player who belongs to nothing got a floor");
        check(ColonyRules.noticeFloor(java.util.List.<Integer>of()) == 0,
                "a player who belongs to nothing was noticed");

        // The largest, never the sum. Ten hamlets must not beat one town.
        java.util.List<Integer> hamlets = java.util.List.of(4, 4, 4, 4, 4, 4, 4, 4, 4, 4);
        java.util.List<Integer> oneTown = java.util.List.of(40);
        check(ColonyRules.worldFloor(hamlets) < ColonyRules.worldFloor(oneTown),
                "ten hamlets are worth as much as one town, so the frontier fills with "
                        + "four-hut farms whose only purpose is arithmetic");
        check(ColonyRules.worldFloor(oneTown) == ColonyRules.worldFloor(40),
                "one town in a list is worth something different from one town");
        check(ColonyRules.noticeFloor(hamlets) < ColonyRules.noticeFloor(oneTown),
                "the Empire files a player under the total rather than the most "
                        + "conspicuous thing they hold");

        // Order does not matter, and nulls do not crash it.
        check(ColonyRules.worldFloor(java.util.List.of(5, 30, 12))
                        == ColonyRules.worldFloor(java.util.List.of(30, 12, 5)),
                "what a player is worth depends on the order their colonies were found");
        check(ColonyRules.worldFloor(java.util.Arrays.asList(null, 30, null))
                        == ColonyRules.worldFloor(30),
                "a colony that could not be read changed what the others were worth");
        check(ColonyRules.worldFloor(java.util.Arrays.asList(new Integer[]{null})) == 0,
                "a list of nothing but unreadable colonies was worth something");

        // Joining is never worse than not joining.
        for (int mine = 0; mine <= 60; mine += 3) {
            for (int theirs = 0; theirs <= 60; theirs += 3) {
                int alone = ColonyRules.worldFloor(mine);
                int together = ColonyRules.worldFloor(java.util.List.of(mine, theirs));
                check(together >= alone,
                        "joining a friend's colony made a player worse off, at " + mine
                                + " and " + theirs);
                check(together == Math.max(ColonyRules.worldFloor(mine),
                                ColonyRules.worldFloor(theirs)),
                        "two stakes were not worth the better of them, at " + mine
                                + " and " + theirs);
            }
        }
    }

    // ------------------------------------------------------------------
    // The grant and the watchdog
    // ------------------------------------------------------------------

    private static void relief() {
        check(!ReliefRules.granted(0, -1),
                "a town whose founding was never recorded claimed a grant, so every "
                        + "old colony in an existing world is entitled to free supplies");
        check(ReliefRules.granted(0, 0), "a town was not supplied on the day it was founded");
        check(ReliefRules.granted(ReliefRules.GRANT_DAYS - 1, 0),
                "the grant ended one day early");
        check(!ReliefRules.granted(ReliefRules.GRANT_DAYS, 0),
                "the grant did not end when it said it would");
        for (int day = ReliefRules.GRANT_DAYS; day <= 5000; day += 7) {
            check(!ReliefRules.granted(day, 0),
                    "an old town was still being supplied, at day " + day);
        }
        check(ReliefRules.GRANT_DAYS >= 5,
                "the grant is too short to get a builder's hut up, which is the one "
                        + "thing it exists for");
        // The ceiling is on the grant's *whole life*, not on a fortnight: a
        // founding charter is meant to outlast the bootstrap. What keeps it
        // from being a materials tap is that it ends and never comes back, and
        // that nothing is handed over to a town that is not stuck.
        check(ReliefRules.GRANT_DAYS <= 400,
                "the grant outlasts any colony anybody plays, so it never ends in "
                        + "practice and 'it runs out' stops being what pays for it");
        check(ReliefRules.GRANT_DAYS >= 60,
                "the grant is sized for the bootstrap rather than for founding a "
                        + "settlement on a world with no infrastructure at all");

        check(ReliefRules.granting(0, 0) == 0, "a grant covered something nobody wanted");
        check(ReliefRules.granting(10, 0) == 10, "a grant covered less than was asked");
        check(ReliefRules.granting(1000000, 0) == ReliefRules.GRANT_PER_DAY,
                "a day's grant was not capped, so the grant years are the best years "
                        + "a colony ever has");
        check(ReliefRules.GRANT_PER_DAY
                        == ReliefRules.GRANT_STACKS_PER_DAY * 64,
                "the grant's two ways of saying how much disagree, so the number in "
                        + "the documentation is not the number in the arithmetic");
        check(ReliefRules.GRANT_STACKS_PER_DAY >= 16,
                "a day's grant cannot finish a building, so a grant that runs for a "
                        + "hundred days still never puts a hut up");
        check(ReliefRules.GRANT_PER_DAY < 64 * 64 * 10,
                "a day's grant is a warehouse, which is a town built by nobody");
        check(ReliefRules.granting(10, ReliefRules.GRANT_PER_DAY) == 0,
                "a town that had its whole day's grant kept taking");
        check(ReliefRules.granting(-5, 0) == 0, "a negative want was granted");
        for (int given = 0; given <= ReliefRules.GRANT_PER_DAY + 40; given += 5) {
            int more = ReliefRules.granting(1000000, given);
            if (given >= ReliefRules.GRANT_PER_DAY) {
                check(more == 0, "a town already at its cap was granted more, at "
                        + given);
            } else {
                check(given + more == ReliefRules.GRANT_PER_DAY,
                        "a day's grant did not run exactly to its cap, at " + given);
            }
        }

        // Progress, and the absence of it.
        check(!ReliefRules.progressed(3, 3, 100L, 100L, 5, 5),
                "a town that did nothing counted as having progressed");
        check(ReliefRules.progressed(3, 4, 100L, 100L, 5, 5), "a new building was not progress");
        check(ReliefRules.progressed(3, 3, 100L, 110L, 5, 5), "earning was not progress");
        check(ReliefRules.progressed(3, 3, 100L, 100L, 5, 6), "a new citizen was not progress");
        check(ReliefRules.progressed(3, 3, 110L, 100L, 5, 5),
                "spending development was not progress, so a town that just built "
                        + "something reads as stuck");

        check(ReliefRules.stuckFor(10, -1) == 0, "a town that has never been measured was stuck");
        check(ReliefRules.stuckFor(10, 10) == 0, "a town that moved today was stuck");
        check(ReliefRules.stuckFor(10, 4) == 6, "how long a town has been stuck was counted wrong");
        check(!ReliefRules.stuck(ReliefRules.PATIENCE - 1, 0),
                "a town was declared stuck while its builder was still walking to the site");
        check(ReliefRules.stuck(ReliefRules.PATIENCE, 0), "a town is never declared stuck");
        check(ReliefRules.PATIENCE >= 3,
                "the watchdog cries wolf while a builder is still gathering");

        // The escalation, in order, and only in order.
        check(ReliefRules.reliefFor(0, 0, 0) == ReliefRules.Relief.NONE,
                "a healthy town was given relief");
        check(ReliefRules.reliefFor(ReliefRules.PATIENCE, 0, 0)
                        == ReliefRules.Relief.MARKET,
                "the first remedy was not the one that costs the town something");
        check(ReliefRules.reliefFor(ReliefRules.PATIENCE * 2, 0, 0)
                        == ReliefRules.Relief.GRANT,
                "a new town still stuck after two rounds was not given anything");
        check(ReliefRules.reliefFor(ReliefRules.PATIENCE * 2, 0, -1)
                        == ReliefRules.Relief.REPORT,
                "a town with no grant left was given one anyway");
        check(ReliefRules.reliefFor(ReliefRules.GRANT_DAYS + ReliefRules.PATIENCE * 2,
                        0, 0) == ReliefRules.Relief.REPORT,
                "a town past its grant kept being supplied instead of being reported");
        // The report is parallel to the grant rather than after it: a colony
        // on a hundred-day charter that has finished nothing for a fortnight
        // is a colony whose owner needs telling, however much is being poured
        // into it.
        check(!ReliefRules.alsoReport(0, 0),
                "a town that has just been founded is reported as stuck");
        check(!ReliefRules.alsoReport(ReliefRules.PATIENCE, 0),
                "a town stuck for one round is already being complained about");
        check(ReliefRules.alsoReport(ReliefRules.PATIENCE * ReliefRules.REPORT_AFTER,
                        0),
                "a town stuck for four rounds is still silent");
        check(ReliefRules.alsoReport(ReliefRules.GRANT_DAYS * 2, 0),
                "a town stuck for twice its charter says nothing");
        check(ReliefRules.REPORT_AFTER * ReliefRules.PATIENCE < ReliefRules.GRANT_DAYS,
                "the owner is told only after the grant is spent, which on a long "
                        + "charter is months of silence - the exact failure the "
                        + "watchdog exists to prevent");
        check(ReliefRules.alsoReport(ReliefRules.PATIENCE * ReliefRules.REPORT_AFTER,
                        0)
                        && ReliefRules.reliefFor(
                                ReliefRules.PATIENCE * ReliefRules.REPORT_AFTER, 0, 0)
                        == ReliefRules.Relief.GRANT,
                "a town cannot be helped and reported at the same time, so being "
                        + "given something buys silence");
        boolean seenMarket = false;
        boolean seenReport = false;
        for (int day = 0; day <= 400; day++) {
            ReliefRules.Relief relief = ReliefRules.reliefFor(day, 0, 0);
            if (relief == ReliefRules.Relief.MARKET) {
                seenMarket = true;
                check(!seenReport, "the escalation went backwards, at day " + day);
            }
            if (relief == ReliefRules.Relief.REPORT) {
                seenReport = true;
            }
            check(relief != ReliefRules.Relief.NONE || day < ReliefRules.PATIENCE,
                    "a long-stuck town stopped being treated as stuck, at day " + day);
        }
        check(seenMarket && seenReport, "not every remedy is reachable");

        check(!ReliefRules.dueToReport(0, 0), "a town was reported twice in a day");
        check(ReliefRules.dueToReport(ReliefRules.BETWEEN_REPORTS, 0),
                "a stuck town is never reported again");
        check(ReliefRules.BETWEEN_REPORTS > ReliefRules.PATIENCE,
                "a stuck town is reported more often than the watchdog even checks, "
                        + "which is a message that stops being read on the third day");
    }

    // ------------------------------------------------------------------
    // The market
    // ------------------------------------------------------------------

    private static void market() {
        check(MarketRules.BASE_VALUE > 0, "everything is free");
        check(MarketRules.priceOf(MarketRules.BASE_VALUE, false, 0.0F)
                        == MarketRules.BASE_VALUE,
                "a thing nobody here wants was not worth its base value");
        check(MarketRules.priceOf(MarketRules.BASE_VALUE, true, 1.0F)
                        < MarketRules.BASE_VALUE,
                "a world that produces something does not sell it cheaply, so the "
                        + "charter says nothing about prices");
        check(MarketRules.priceOf(MarketRules.BASE_VALUE, false, 1.0F)
                        > MarketRules.BASE_VALUE,
                "a world that badly wants something does not pay more for it");
        check(MarketRules.priceOf(MarketRules.BASE_VALUE, false, 1.0F)
                        > MarketRules.priceOf(MarketRules.BASE_VALUE, true, 1.0F) * 3,
                "the spread between the cheapest and dearest world is too small for "
                        + "carrying cargo between them to be worth the trip");
        for (float demand = 0.0F; demand <= 1.0F; demand += 0.02F) {
            check(MarketRules.priceOf(100, false, demand) >= 1, "a price fell to nothing");
            check(MarketRules.priceOf(100, false, demand)
                            <= Math.round(100 * (1.0F + MarketRules.SWING)),
                    "a price rose past the swing, at demand " + demand);
            check(MarketRules.priceOf(100, true, demand)
                            == MarketRules.priceOf(100, true, 0.0F),
                    "demand changed the price of something the world produces itself");
        }
        check(MarketRules.priceOf(0, false, 1.0F) >= 1, "a worthless thing priced at zero");
        check(MarketRules.priceOf(-50, false, 1.0F) >= 1, "a negative base gave a price");
        check(MarketRules.priceOf(100, false, 5.0F) == MarketRules.priceOf(100, false, 1.0F),
                "demand past certainty kept raising the price");
        check(MarketRules.priceOf(100, false, -3.0F) == MarketRules.priceOf(100, false, 0.0F),
                "negative demand lowered a price below its base");

        // Drift wanders and returns, and never inverts a price.
        float lowest = 1.0F;
        float highest = -1.0F;
        for (int day = 0; day <= 2000; day++) {
            for (int seed = 0; seed < 12; seed++) {
                float drift = MarketRules.driftAt(day, seed);
                check(drift >= -MarketRules.DRIFT - 0.0001F
                                && drift <= MarketRules.DRIFT + 0.0001F,
                        "drift wandered past its bound on day " + day);
                lowest = Math.min(lowest, drift);
                highest = Math.max(highest, drift);
                check(MarketRules.drifted(100, day, seed) >= 1,
                        "drift took a price to nothing on day " + day);
            }
        }
        check(highest > MarketRules.DRIFT * 0.9F && lowest < -MarketRules.DRIFT * 0.9F,
                "drift never reaches its own bounds, so the wave is smaller than it says");
        check(MarketRules.driftAt(0, 3)
                        == MarketRules.driftAt(MarketRules.DRIFT_PERIOD, 3),
                "the drift does not repeat, so a player cannot learn a route");
        check(MarketRules.driftAt(5, 1) != MarketRules.driftAt(5, 2),
                "every commodity drifts together, which is one price wearing many hats");
        check(MarketRules.DRIFT < MarketRules.SWING,
                "drift moves a price more than scarcity does, so where a thing is "
                        + "matters less than when");

        // The spread: round-tripping must always lose.
        for (int price = 1; price <= 5000; price++) {
            check(MarketRules.asking(price) >= MarketRules.offering(price),
                    "the market paid more than it charged, at " + price);
            check(!MarketRules.roundTripPays(price),
                    "selling something straight back to the market turned a profit, at "
                            + price);
            check(MarketRules.offering(price) >= 1, "the market offered nothing at all");
            check(MarketRules.asking(price) >= 1, "the market gave something away");
        }
        check(MarketRules.asking(1000) > MarketRules.offering(1000),
                "there is no spread at all");
        check(MarketRules.SPREAD > 0.0F && MarketRules.SPREAD < 1.0F,
                "the spread is not a share");

        // A colony and a player get the same numbers.
        check(MarketRules.purseFrom(10, 100) == 10L * MarketRules.offering(100),
                "a colony was paid something other than the market's own offer");
        check(MarketRules.purseFrom(0, 100) == 0L, "selling nothing paid something");
        check(MarketRules.purseFrom(-4, 100) == 0L, "selling a negative amount paid");
        check(MarketRules.buys(0L, 100) == 0, "an empty purse bought something");
        check(MarketRules.buys(MarketRules.costOf(3, 100), 100) == 3,
                "a purse that cost exactly three bought a different number");
        check(MarketRules.costOf(0, 100) == 0L, "buying nothing cost something");
        for (int price = 1; price <= 400; price++) {
            for (int many = 0; many <= 20; many++) {
                long cost = MarketRules.costOf(many, price);
                check(MarketRules.buys(cost, price) >= many,
                        "a purse that paid for " + many + " could not buy them back");
                check(cost >= MarketRules.purseFrom(many, price),
                        "buying was cheaper than selling, at " + price);
            }
        }
    }

    // ------------------------------------------------------------------
    // The spaceport
    // ------------------------------------------------------------------

    private static void spaceport() {
        check(PortRules.MINIMUM_TETHER >= 40,
                "a spaceport is a radio mast rather than a space elevator");
        check(!PortRules.tall(PortRules.MINIMUM_TETHER - 1),
                "a column one block short counted as a spaceport");
        check(PortRules.tall(PortRules.MINIMUM_TETHER),
                "a column of exactly the minimum was refused");
        check(PortRules.throughput(0) == 0, "a port with no tether shipped something");
        check(PortRules.throughput(PortRules.MINIMUM_TETHER - 1) == 0,
                "a column too short to be a port still shipped");
        check(PortRules.throughput(-40) == 0, "a negative tether shipped");
        check(PortRules.throughput(PortRules.MINIMUM_TETHER)
                        == PortRules.BASE_THROUGHPUT,
                "the shortest legal port did not move the base amount");

        // Linear, uncapped, and always worth building higher.
        int previous = -1;
        for (int tether = PortRules.MINIMUM_TETHER; tether <= 4000; tether++) {
            int moved = PortRules.throughput(tether);
            check(moved > previous, "a taller elevator moved no more, at " + tether);
            check(moved == PortRules.BASE_THROUGHPUT
                            + PortRules.PER_BLOCK * (tether - PortRules.MINIMUM_TETHER),
                    "throughput is not linear in the height, at " + tether);
            previous = moved;
        }
        check(PortRules.throughput(3000) > PortRules.throughput(300),
                "throughput stops climbing, so there is a height past which building "
                        + "costs material and buys nothing");
        check(PortRules.BASE_THROUGHPUT > 128,
                "a spaceport moves no more than the emergency exchange, so the whole "
                        + "structure is a more expensive way to be desperate");

        // Split between sending and receiving, exactly.
        for (int tether = PortRules.MINIMUM_TETHER; tether <= 2000; tether += 7) {
            check(PortRules.outbound(tether) + PortRules.inbound(tether)
                            == PortRules.throughput(tether),
                    "the two halves of a port's day do not add up, at " + tether);
            check(PortRules.outbound(tether) > 0 && PortRules.inbound(tether) > 0,
                    "a port only sends or only receives, at " + tether);
            check(Math.abs(PortRules.outbound(tether) - PortRules.inbound(tether)) <= 1,
                    "a port's day is not split evenly, at " + tether);
            check(PortRules.spareFor(tether) < PortRules.outbound(tether),
                    "a port would give a sister colony everything it had to send, at "
                            + tether);
            check(PortRules.spareFor(tether) > 0,
                    "a port has nothing to spare for freight, so a second one is never "
                            + "worth building, at " + tether);
        }

        // Freight between two ports beats the market, always.
        for (int price = 2; price <= 4000; price++) {
            int asking = MarketRules.asking(price);
            int offering = MarketRules.offering(price);
            int mid = PortRules.midPrice(asking, offering);
            check(mid <= asking, "shipping direct cost more than buying, at " + price);
            check(mid >= offering, "shipping direct paid less than selling, at " + price);
            check(mid >= 1, "freight was free, at " + price);
        }
        check(PortRules.midPrice(0, 0) >= 1, "a freight price of nothing");
        check(PortRules.midPrice(-5, -9) >= 1, "a negative freight price");
        check(PortRules.REACHES_EVERY_WORLD,
                "freight stops at a world boundary, which is a rule invented to create "
                        + "difficulty rather than one that follows from anything");

        // The treasury: real, capped, and never negative.
        for (int tether = PortRules.MINIMUM_TETHER; tether <= 1000; tether += 11) {
            long ceiling = PortRules.ceiling(tether);
            check(ceiling > 0L, "a port can hold no money at all, at " + tether);
            check(PortRules.banked(0L, 0L, tether) == 0L, "an idle day banked something");
            check(PortRules.banked(ceiling, 1000000L, tether) == ceiling,
                    "a treasury banked past its ceiling, so a decade of exports buys a "
                            + "tech tree in an afternoon");
            check(PortRules.banked(-500L, 10L, tether) == 10L,
                    "a negative treasury stayed negative");
            check(PortRules.banked(100L, -50L, tether) == 100L,
                    "a negative day's takings robbed the treasury");
        }
        check(PortRules.ceiling(2000) > PortRules.ceiling(PortRules.MINIMUM_TETHER),
                "a bigger port cannot hold more, so building higher buys only speed");
        check(PortRules.UPKEEP == 0.0F,
                "the elevator charges rent; it was the cost, and running it is not");

        // The flare: the curve the legs follow, which three separate things
        // have to agree about — the blueprint, the reading and the validator.
        check(Math.abs(PortRules.legRadius(0) - PortRules.FOOT) < 0.001F,
                "a leg does not start where the deck is wide, so the flare does not "
                        + "reach the edge of the platform it stands on");
        check(Math.abs(PortRules.legRadius(PortRules.GATHER) - PortRules.WAIST)
                        < 0.001F,
                "the legs have not gathered by the top, so they meet the shaft at a "
                        + "corner rather than a bundle");
        check(PortRules.legRadius(PortRules.GATHER + 50) == PortRules.WAIST,
                "the curve carries on past the top of the flare, so a leg wanders "
                        + "back out again above where it stopped");
        check(PortRules.legRadius(-10) <= PortRules.FOOT,
                "a leg below the deck stands further out than the deck is wide");
        float outward = PortRules.FOOT + 1.0F;
        for (int height = 0; height <= PortRules.GATHER; height++) {
            float radius = PortRules.legRadius(height);
            check(radius <= outward + 0.001F,
                    "a leg bulges back outward at " + height + ", so the silhouette "
                            + "is a vase rather than a flare");
            check(radius >= PortRules.WAIST - 0.001F
                            && radius <= PortRules.FOOT + 0.001F,
                    "a leg leaves the space between the deck and the shaft at "
                            + height);
            outward = radius;
        }
        // Most of the bend happens low down: that is what makes it a flare.
        // At the halfway height a cone would be exactly midway between the
        // foot and the waist, and this must be well inside that.
        float midway = (PortRules.FOOT + PortRules.WAIST) / 2.0F;
        check(PortRules.legRadius(PortRules.GATHER / 2) < midway,
                "the legs bend evenly, so the base is a cone and reads as a tent "
                        + "rather than as something holding a weight up");
        check(PortRules.BEND > 1.0F,
                "the bend is linear or inverted, which is the same cone again");

        // Four legs, on the diagonals, all the same distance out.
        check(PortRules.LEGS >= 3, "a flare with fewer than three legs is a lean");
        check(PortRules.GATHER >= PortRules.FOOT * 2,
                "the flare is as wide as it is tall, so it reads as a tent rather "
                        + "than as something holding a weight up");
        for (int height = 0; height <= PortRules.GATHER; height += 3) {
            java.util.Set<String> places = new java.util.HashSet<>();
            for (int leg = 0; leg < PortRules.LEGS; leg++) {
                int x = PortRules.legX(leg, height);
                int z = PortRules.legZ(leg, height);
                places.add(x + "," + z);
                double away = Math.sqrt((double) x * x + (double) z * z);
                check(Math.abs(away - PortRules.legRadius(height)) <= 1.5D,
                        "leg " + leg + " at " + height + " is not on its own curve");
            }
            check(places.size() == PortRules.LEGS || height >= PortRules.GATHER - 3,
                    "two legs stand in the same place at " + height + ", so the flare "
                            + "is missing a side");
        }
        check(PortRules.legSamples().length >= 3,
                "a brace is decided from too few heights, so a stub at the bottom "
                        + "counts as a leg");
        for (int sample : PortRules.legSamples()) {
            check(sample >= 0 && sample <= PortRules.GATHER,
                    "a brace is looked for at " + sample + ", where there is no leg");
        }
        check(PortRules.legSamples()[PortRules.legSamples().length - 1]
                        > PortRules.GATHER / 2,
                "every height a brace is checked at is in the bottom half, so half a "
                        + "leg is a whole brace");
        check(PortRules.LEG_TOLERANCE >= 1,
                "a hand-built leg has to land on the exact block the arithmetic "
                        + "chose, which nobody will do");
        check(PortRules.LEG_TOLERANCE * 2 < PortRules.FOOT,
                "the tolerance is wider than the flare, so any two blocks anywhere "
                        + "near the base count as all four legs");

        // What a brace is worth: real, linear, and never the efficient way to
        // buy capacity.
        for (int tether = PortRules.MINIMUM_TETHER; tether <= 2000; tether += 13) {
            int bare = PortRules.throughput(tether, 0);
            check(bare == PortRules.throughput(tether),
                    "an unbraced port and a port asked about without legs disagree, "
                            + "at " + tether);
            int full = PortRules.throughput(tether, PortRules.LEGS);
            check(full == bare + PortRules.PER_LEG * PortRules.LEGS,
                    "a full brace is not worth what it says it is, at " + tether);
            check(PortRules.throughput(tether, 99) == full,
                    "more legs than there are legs bought more capacity, at " + tether);
            check(PortRules.throughput(tether, -3) == bare,
                    "a negative brace changed the capacity, at " + tether);
            check(full - bare < PortRules.throughput(tether + 100, 0) - bare,
                    "two thousand blocks of flare beat a hundred blocks of tether, so "
                            + "nobody would ever build height again, at " + tether);
            check(PortRules.outbound(tether, PortRules.LEGS)
                            + PortRules.inbound(tether, PortRules.LEGS) == full,
                    "a braced port's day does not add up, at " + tether);
            check(PortRules.spareFor(tether, PortRules.LEGS)
                            > PortRules.spareFor(tether, 0),
                    "a braced port has no more to spare for a sister colony, at "
                            + tether);
        }
        check(PortRules.throughput(PortRules.MINIMUM_TETHER - 1, PortRules.LEGS) == 0,
                "a column too short to be a port shipped because it had legs, so the "
                        + "flare replaces the elevator rather than bracing it");
        check(PortRules.PER_LEG > 0, "a brace is worth nothing, so it is scenery");

        // Paying for freight: the cost and what a purse buys are the same
        // sentence read in two directions, and a colony that could order more
        // than it can pay for would take a sister colony's goods for nothing.
        for (int mid = 1; mid <= 600; mid++) {
            for (int count = 0; count <= 600; count += 7) {
                long cost = PortRules.freightCost(count, mid);
                check(cost == (long) count * mid,
                        "freight was not priced at the mid price, at " + mid);
                check(PortRules.freightBuys(cost, mid) >= count,
                        "a colony could not afford exactly what it just paid for, at "
                                + mid);
                check(PortRules.freightCost(PortRules.freightBuys(cost, mid), mid)
                                <= cost,
                        "a purse bought more freight than it could pay for, at " + mid);
            }
            check(PortRules.freightCost(-5, mid) == 0L, "a negative run cost something");
            check(PortRules.freightBuys(-100L, mid) == 0,
                    "a colony in debt went on shipping, at " + mid);
            check(PortRules.freightBuys(mid - 1L, mid) == 0,
                    "a purse short of one unit bought one anyway, at " + mid);
        }
        check(PortRules.freightBuys(1000L, 0) >= 0, "a free price divided by nothing");

        // And freight is the better deal it claims to be: a colony that ships
        // direct rather than buying from the market always pays less.
        for (int price = 2; price <= 2000; price++) {
            int mid = PortRules.midPrice(MarketRules.asking(price),
                    MarketRules.offering(price));
            check(PortRules.freightCost(64, mid) <= MarketRules.costOf(64, price),
                    "a full crate cost more shipped direct than bought, at " + price);
        }
    }
}
