package com.elysium.atelier.rules;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * The fittings, executed.
 *
 * <p>Four thousand blocks in the jars this replaces, and underneath them
 * twelve shapes. What is proved here is what those twelve are as a <em>set</em>
 * rather than one at a time: that the three things you can put on a wall get
 * deeper and dearer together, that the things you are meant to see through
 * have a hole in them, that the things along an edge meet the thing beside
 * them, and that nothing soft is made of deepslate.
 *
 * <p>Every one of those is invisible in any single block. A frame with no
 * hole renders perfectly. A cushion of blackstone renders perfectly. The only
 * place they are wrong is in the set.
 */
public final class TrimHarness {

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
        theKinds();
        theLadder();
        theHollows();
        theEdges();
        theGrain();
        thePrices();
        theJoining();
        theTags();
        theGeometry();
        theControls();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: a ladder of facings that gets deeper "
                + "and dearer together and never as deep as a slab, every hollow "
                + "one with a hole you can see through, every edge meeting the one "
                + "beside it, nothing soft made of stone, and not one recipe that "
                + "makes more than it takes");
    }

    // ==================================================================

    private static void theKinds() {
        check(!TrimRules.KINDS.isEmpty(), "there are no fittings at all");
        check(TrimRules.repeated().isEmpty(),
                "a fitting is declared twice, or shares a name with something this "
                        + "module already has: " + TrimRules.repeated());
        check(TrimRules.sameShape().isEmpty(),
                "two fittings fill exactly the same sixteenths: "
                        + TrimRules.sameShape());

        Set<String> families = new LinkedHashSet<>();
        for (TrimRules.Kind kind : TrimRules.KINDS) {
            families.add(kind.family());
            check(TrimRules.kind(kind.name()) == kind,
                    kind.name() + " cannot be looked up by its own name");
            check(!TrimRules.boxesFor(kind.name()).isEmpty(),
                    kind.name() + " is made of no boxes at all");
            check(TrimRules.volume(kind.name()) > 0, kind.name() + " fills nothing");
            check(TrimRules.volume(kind.name())
                            < TrimRules.SPAN * TrimRules.SPAN * TrimRules.SPAN,
                    kind.name() + " fills the whole block, and the game has one of "
                            + "those in every material already");
        }
        check(families.equals(new LinkedHashSet<>(TrimRules.FAMILIES)),
                "the families in use are " + families + " and the families are "
                        + TrimRules.FAMILIES + ", so one of them is a list nothing "
                        + "walks");
        System.out.println("  the kinds: " + TrimRules.KINDS.size() + " fitting(s) "
                + "in " + families.size() + " families, none of them the same shape "
                + "as another and none of them a whole block");
    }

    // ==================================================================

    private static void theLadder() {
        check(TrimRules.unladdered().isEmpty(),
                "the ladder of facings is not one: " + TrimRules.unladdered());
        int below = 0;
        int paid = 0;
        for (String name : TrimRules.FACINGS) {
            int deep = TrimRules.standsOut(name);
            int price = TrimRules.units(name);
            check(deep > below, name + " stands " + deep + " out and the one below "
                    + "it stands " + below);
            check(price > paid, name + " costs " + price + " and the one below it "
                    + "costs " + paid);
            check(deep <= TrimRules.DEEPEST_FACING, name + " stands " + deep
                    + " out, which is a slab");
            below = deep;
            paid = price;
        }
        check(TrimRules.FACINGS.size() >= 3, "a ladder of "
                + TrimRules.FACINGS.size() + " rungs is not a ladder");
        check(TrimRules.DEEPEST_FACING < TrimRules.SPAN / 2,
                "a facing is allowed to stand " + TrimRules.DEEPEST_FACING
                        + " of " + TrimRules.SPAN + " sixteenths out, which is a "
                        + "slab, so the ceiling would allow any wall at all");
        System.out.println("  the ladder: " + TrimRules.FACINGS.size() + " facing(s) "
                + "standing " + TrimRules.standsOut(TrimRules.FACINGS.get(0)) + " to "
                + below + " sixteenths out, each deeper and dearer than the last "
                + "and none of them as deep as a slab");
    }

    // ==================================================================

    private static void theHollows() {
        check(TrimRules.notHollow().isEmpty(),
                "something you are meant to see through and cannot: "
                        + TrimRules.notHollow());
        int hollows = 0;
        for (TrimRules.Kind kind : TrimRules.KINDS) {
            if (!TrimRules.HOLLOW.equals(kind.family())) {
                continue;
            }
            hollows++;
            check(TrimRules.seeThrough(kind.name()) > 0,
                    kind.name() + " has no line of sight through it");
            check(TrimRules.footprint(kind.name()) * 2
                            <= TrimRules.SPAN * TrimRules.SPAN,
                    kind.name() + " is more wall than hole");
        }
        check(hollows > 0, "nothing here is meant to be seen through, so the check "
                + "above is about a family with no members");
        // And the counter itself: a solid block has no line of sight through it
        // and a facing one sixteenth deep leaves all but its own face clear.
        check(TrimRules.seeThrough(TrimRules.CUSHION)
                        < TrimRules.SPAN * TrimRules.SPAN,
                "something is seen through everywhere, so the counter counts "
                        + "nothing");
        System.out.println("  the hollows: " + hollows + " fitting(s) with a line "
                + "of sight straight through, the widest of them clear across "
                + TrimRules.seeThrough(TrimRules.FRAME) + " of "
                + TrimRules.SPAN * TrimRules.SPAN + " squares");
    }

    // ==================================================================

    private static void theEdges() {
        check(TrimRules.misedged().isEmpty(),
                "something along an edge that is not: " + TrimRules.misedged());
        int edges = 0;
        int joining = 0;
        for (TrimRules.Kind kind : TrimRules.KINDS) {
            if (!TrimRules.EDGE.equals(kind.family())) {
                continue;
            }
            edges++;
            if (kind.joins()) {
                joining++;
            }
        }
        check(edges > 0 && joining > 0, edges + " edge piece(s) of which "
                + joining + " join, and an edge nothing meets is a fence post");
        System.out.println("  the edges: " + edges + " piece(s) along an edge, "
                + joining + " of them reaching the piece beside them");
    }

    // ==================================================================

    private static void theGrain() {
        check(TrimRules.misgrained().isEmpty(),
                "something soft made of something that is not: "
                        + TrimRules.misgrained());
        int soft = 0;
        int hard = 0;
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            if (material.soft()) {
                soft++;
            } else {
                hard++;
            }
            for (TrimRules.Kind kind : TrimRules.KINDS) {
                boolean wants = TrimRules.SOFT.equals(kind.family());
                check(TrimRules.fits(kind.name(), material)
                                == (!wants || material.soft()),
                        kind.name() + " in " + material.name() + " is allowed or "
                                + "refused for the wrong reason");
            }
        }
        check(soft > 0 && hard > 0, "every material is soft, or none is, so the "
                + "rule above never refuses anything");
        int expected = 0;
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (TrimRules.Kind kind : TrimRules.KINDS) {
                if (TrimRules.fits(kind.name(), material)) {
                    expected++;
                }
            }
        }
        check(TrimRules.howMany() == expected, "the catalogue holds "
                + TrimRules.howMany() + " fittings and the crossing allows "
                + expected);
        check(TrimRules.howMany()
                        < AtelierRules.MATERIALS.size() * TrimRules.KINDS.size(),
                "every kind is made in every material, so the soft rule takes "
                        + "nothing out and refuses nothing");
        System.out.println("  the grain: " + soft + " soft material(s) and " + hard
                + " hard, and every soft fitting made only out of the soft ones — "
                + TrimRules.howMany() + " block(s) rather than "
                + AtelierRules.MATERIALS.size() * TrimRules.KINDS.size());
    }

    // ==================================================================

    private static void thePrices() {
        check(TrimRules.profitable().isEmpty(),
                "a recipe that makes more than it takes: " + TrimRules.profitable());
        check(TrimRules.recoverable().isEmpty(),
                "a recipe you could craft and break for a profit: "
                        + TrimRules.recoverable());
        check(TrimRules.wasteful().isEmpty(),
                "a recipe nobody would ever use: " + TrimRules.wasteful());

        for (TrimRules.Kind kind : TrimRules.KINDS) {
            int volume = TrimRules.volume(kind.name());
            int units = kind.units();
            check(units * TrimRules.EIGHTH >= volume, kind.name() + " is priced at "
                    + units + " eighths and fills " + volume
                    + " sixteenths, which is more than it costs");
            check((units - 1) * TrimRules.EIGHTH < volume, kind.name()
                    + " is priced at " + units + " eighths and would fit in "
                    + (units - 1) + ", so it is dearer than its own shape");
            check(kind.yields() <= kind.spends(), kind.name() + " yields "
                    + kind.yields() + " out of " + kind.spends());
            check(kind.spends() - kind.yields() <= TrimRules.MOST_WASTED,
                    kind.name() + " wastes " + (kind.spends() - kind.yields()));
        }

        Random random = new Random(1_732_050L);
        int walks = 0;
        for (int walk = 0; walk < 200_000; walk++) {
            int blocks = 1 + random.nextInt(64);
            int started = blocks;
            for (int step = 0; step < 8; step++) {
                TrimRules.Kind kind = TrimRules.KINDS.get(
                        random.nextInt(TrimRules.KINDS.size()));
                int crafts = blocks / kind.of();
                if (crafts == 0) {
                    break;
                }
                blocks -= crafts * kind.of();
                blocks += crafts * kind.made() * kind.recovers();
            }
            walks++;
            check(blocks <= started, "a chain of crafting and breaking started with "
                    + started + " blocks and came back with " + blocks);
        }
        System.out.println("  the prices: every fitting priced at its own volume "
                + "rounded up, and " + walks + " random chains of crafting and "
                + "breaking not one of which came back with more than it started "
                + "with");
    }

    // ==================================================================

    private static void theJoining() {
        check(TrimRules.lopsided().isEmpty(),
                "two fittings disagree about whether they meet: "
                        + TrimRules.lopsided());
        check(TrimRules.crossJoined().isEmpty(),
                "a fitting joins a different kind of thing: "
                        + TrimRules.crossJoined());
        List<TrimRules.Fitting> all = TrimRules.catalogue();
        int pairs = 0;
        int joining = 0;
        for (TrimRules.Fitting one : all) {
            for (TrimRules.Fitting other : all) {
                pairs++;
                boolean here = TrimRules.reaches(one, other);
                check(here == TrimRules.reaches(other, one),
                        one.id() + " and " + other.id() + " disagree");
                if (here) {
                    joining++;
                }
            }
        }
        check(joining > 0, "nothing in the catalogue joins anything");
        check(joining < pairs, "everything joins everything");
        System.out.println("  the joining: " + pairs + " ordered pair(s), " + joining
                + " of which meet, none of them one way only and none across kinds");
    }

    // ==================================================================

    private static void theTags() {
        check(TrimRules.disagreeing().isEmpty(),
                "a fitting in a tag it has no business in: "
                        + TrimRules.disagreeing());
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (TrimRules.Fitting fitting : TrimRules.catalogue()) {
            Set<String> tags = TrimRules.tagsFor(fitting);
            check(!tags.isEmpty(), fitting.id() + " is in no tag at all");
            StringBuilder line = new StringBuilder("TTAG " + fitting.id());
            for (String tag : new TreeSet<>(tags)) {
                line.append(' ').append(tag);
                counted.merge(tag, 1, Integer::sum);
            }
            System.out.println(line);
        }
        Map<String, List<String>> files = TrimRules.tagFiles();
        check(files.keySet().equals(counted.keySet()),
                "the tag files and the tags the fittings claim are different sets");
        for (Map.Entry<String, List<String>> entry : files.entrySet()) {
            check(entry.getValue().size() == counted.get(entry.getKey()),
                    entry.getKey() + " holds " + entry.getValue().size()
                            + " entries and " + counted.get(entry.getKey())
                            + " fittings claim it");
        }
        check(counted.containsKey("minecraft:climbable"),
                "nothing here is climbable, and a rope you slide down is a rope "
                        + "with one use taken out of it");
        System.out.println("  the tags: " + files.size() + " tag file(s) derived "
                + "from what each fitting actually is");
    }

    // ==================================================================

    private static void theGeometry() {
        for (TrimRules.Kind kind : TrimRules.KINDS) {
            StringBuilder line = new StringBuilder("TRIM " + kind.name());
            for (ArchRules.Box box : TrimRules.boxesFor(kind.name())) {
                line.append(' ').append(box.x1()).append(',').append(box.y1())
                        .append(',').append(box.z1()).append(',').append(box.x2())
                        .append(',').append(box.y2()).append(',').append(box.z2());
            }
            System.out.println(line);
        }
        System.out.println("  the geometry: " + TrimRules.KINDS.size()
                + " fitting(s) printed, for the generator's table to be checked "
                + "against");
    }

    // ==================================================================

    /** The checks, checked: each one handed something built to be caught. */
    private static void theControls() {
        check(!TrimRules.faultsInRung("control", 2, 2, 3, 1).isEmpty(),
                "a facing shallower than the one below it is not reported, so the "
                        + "ladder check cannot say yes");
        check(!TrimRules.faultsInRung("control", 4, 1, 3, 1).isEmpty(),
                "a deeper facing costing no more than the one below it is not "
                        + "reported, so the price half of the ladder cannot say yes");
        check(!TrimRules.faultsInRung("control", TrimRules.SPAN, 9, 3, 1).isEmpty(),
                "a facing as deep as the whole block is not reported, so the depth "
                        + "ceiling cannot say yes");
        check(TrimRules.faultsInRung("control", 4, 2, 3, 1).isEmpty(),
                "an ordinary rung is reported as a fault, so the check says yes to "
                        + "everything");

        check(!TrimRules.faultsInHollow("control", 0, 8).isEmpty(),
                "something with no line of sight through it is not reported, so the "
                        + "hollow check cannot say yes");
        check(!TrimRules.faultsInHollow("control", 4, 200).isEmpty(),
                "something filling most of its block is not reported as more wall "
                        + "than hole");
        check(TrimRules.faultsInHollow("control", 4, 8).isEmpty(),
                "an ordinary frame is reported as solid");

        AtelierRules.Material hard = new AtelierRules.Material("control_hard",
                "minecraft:stone", "minecraft:block/stone", false);
        AtelierRules.Material soft = new AtelierRules.Material("control_soft",
                "minecraft:oak_planks", "minecraft:block/oak_planks", true);
        check(!TrimRules.fits(TrimRules.CUSHION, hard),
                "a cushion of stone is allowed, so the soft rule refuses nothing");
        check(TrimRules.fits(TrimRules.CUSHION, soft),
                "a cushion of wood is refused, so the soft rule refuses everything");
        check(TrimRules.fits(TrimRules.PANEL, hard),
                "panelling of stone is refused, so the soft rule is applied to "
                        + "things it is not about");
        // And the list itself, which run over the catalogue can only ever
        // answer no: the catalogue is built with the same rule.
        check(!TrimRules.misgrainedIn(List.of(
                        new TrimRules.Fitting(TrimRules.CUSHION, hard))).isEmpty(),
                "a cushion of stone handed straight to the check is not reported, "
                        + "so the soft check cannot say yes");
        check(TrimRules.misgrainedIn(List.of(
                        new TrimRules.Fitting(TrimRules.CUSHION, soft))).isEmpty(),
                "a cushion of wood is reported as being made of the wrong thing, "
                        + "so the soft check says yes to everything");

        check(TrimRules.voxelKey(TrimRules.PANEL)
                        .equals(TrimRules.voxelKey(TrimRules.PANEL)),
                "a shape is not the same shape as itself, so the comparison that "
                        + "finds two blocks with one shape compares nothing");
        check(!TrimRules.voxelKey(TrimRules.PANEL)
                        .equals(TrimRules.voxelKey(TrimRules.BOARD)),
                "panelling and a board wall are reported as one shape, so that "
                        + "comparison says everything is everything");

        // The counter itself, on two shapes whose answer is known by hand:
        // panelling covers the whole wall, so nothing is seen through it, and
        // a rope hangs down two sixteenths of it, so nearly everything is.
        check(TrimRules.seeThrough(TrimRules.PANEL) == 0,
                "panelling covering the whole of its wall is said to be seen "
                        + "through, so the counter is not counting");
        check(TrimRules.seeThrough(TrimRules.ROPE)
                        == TrimRules.SPAN * TrimRules.SPAN - 2 * TrimRules.SPAN,
                "a rope two sixteenths wide hanging the height of its block "
                        + "blocks the wrong number of lines of sight, so the "
                        + "counter is counting something else");

        check(CourseRules.pays(8, 4, 2), "four things worth a block each out of two "
                + "blocks is not reported as paying");
        check(!CourseRules.pays(1, 12, 2), "twelve sixteenths of a block out of two "
                + "blocks is reported as paying, so the check says yes to "
                + "everything");

        Random random = new Random(3_162_277L);
        int reported = 0;
        int rounds = 0;
        for (int round = 0; round < 20_000; round++) {
            int units = 1 + random.nextInt(TrimRules.WHOLE * 2);
            int made = 1 + random.nextInt(8);
            int of = 1 + random.nextInt(4);
            rounds++;
            check(CourseRules.pays(units, made, of)
                            == (made * units > of * TrimRules.WHOLE),
                    "profitability disagreed with the arithmetic at " + units + "/"
                            + made + "/" + of);
            if (CourseRules.pays(units, made, of)) {
                reported++;
            }
        }
        check(reported > rounds / 10, "only " + reported + " of " + rounds
                + " built recipes were reported as paying");
        check(reported < rounds, "every built recipe was reported as paying");
        check(CourseRules.spent(4) - CourseRules.yielded(1, 1)
                        > TrimRules.MOST_WASTED,
                "a recipe eating four blocks and giving back one eighth is inside "
                        + "the waste ceiling, so the ceiling would allow anything");

        System.out.println("  the controls: " + reported + " of " + rounds
                + " built recipes reported as paying, a shallower facing caught, a "
                + "solid frame caught, and a cushion of stone caught");
    }
}
