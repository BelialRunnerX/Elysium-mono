package com.elysium.npcs.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The court's rules, executed.
 *
 * <p>elysium-npcs/validate.py compiles this with rules/ and runs it with the
 * court as EnvoyKind declares it and the meters as ElysiumStanding draws them:
 *
 * <pre>
 *   java com.elysium.npcs.rules.CourtHarness 100 25,50,75 \
 *        emperor:SUSPICION:3 architect:SUSPICION:1 sentinel:SUSPICION:0 \
 *        commander:FAVOR:1 queen:FAVOR:3
 * </pre>
 *
 * <p>The top of a meter, the band edges, then one member per argument: id,
 * the meter they read, and the band they deal from. Handed over rather than
 * copied here, so the proof that somebody can always come is a proof about the
 * real court. A copy would go on passing after the original changed, which is
 * the one time the check matters.
 */
public final class CourtHarness {

    private static int checks;
    private static int failures;

    /** How much of one complaint is worth reading. */
    private static final int ENOUGH = 220;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + (complaint.length() <= ENOUGH ? complaint
                    : complaint.substring(0, ENOUGH) + " ... (" + complaint.length()
                    + " characters in all)"));
        }
    }

    public static void main(String[] args) {
        shape();
        audience();
        harvest();
        int standings = everyStanding(args);

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: all " + standings + " standings have somebody "
                + "to send, a half with nobody willing gives way to whoever is, and a visit "
                + "sells one writ of audience");
    }

    /**
     * The rule's sentences, on every small court there is.
     *
     * Three members, every way they can be split between the meters and every
     * way they can be willing or not, with either meter ahead: 8 x 8 x 2 = 128
     * cases, each held to the rule as CourtRules.arrivals states it.
     */
    private static void shape() {
        int size = 3;
        List<String> wrong = new ArrayList<>();
        for (int split = 0; split < (1 << size); split++) {
            for (int mood = 0; mood < (1 << size); mood++) {
                for (int lead = 0; lead < 2; lead++) {
                    boolean[] readsFavor = new boolean[size];
                    boolean[] willDeal = new boolean[size];
                    for (int i = 0; i < size; i++) {
                        readsFavor[i] = (split & (1 << i)) != 0;
                        willDeal[i] = (mood & (1 << i)) != 0;
                    }
                    boolean favorLeads = lead == 1;
                    int favor = favorLeads ? 30 : 10;
                    int suspicion = 20;

                    List<Integer> half = new ArrayList<>();
                    List<Integer> all = new ArrayList<>();
                    for (int i = 0; i < size; i++) {
                        if (willDeal[i]) {
                            all.add(i);
                            if (readsFavor[i] == favorLeads) {
                                half.add(i);
                            }
                        }
                    }
                    List<Integer> want = half.isEmpty() ? all : half;
                    int[] got = CourtRules.arrivals(favor, suspicion, readsFavor, willDeal);
                    if (!Arrays.equals(got, want.stream().mapToInt(Integer::intValue).toArray())) {
                        wrong.add("Favor " + favor + " and Suspicion " + suspicion + ", reading Favor "
                                + Arrays.toString(readsFavor) + ", willing " + Arrays.toString(willDeal)
                                + ": sent " + Arrays.toString(got) + ", not " + want);
                    }
                }
            }
        }
        check(wrong.isEmpty(), wrong.size() + " of 128 small courts are sent the wrong envoys, the "
                + "first at " + (wrong.isEmpty() ? "" : wrong.get(0)));

        // The dead zone, by name: the Favor half has nobody willing, so the
        // Sentinel comes instead of nobody.
        int[] deadZone = CourtRules.arrivals(10, 0,
                new boolean[] {false, true}, new boolean[] {true, false});
        check(Arrays.equals(deadZone, new int[] {0}),
                "Favor 10 over Suspicion 0 sends " + Arrays.toString(deadZone) + " though the "
                + "Sentinel would deal: a player below the Commander's gate is visited by nobody");

        // A tie goes to Suspicion.
        int[] tie = CourtRules.arrivals(20, 20,
                new boolean[] {true, false}, new boolean[] {true, true});
        check(Arrays.equals(tie, new int[] {1}),
                "at Favor 20 and Suspicion 20 the rule sends " + Arrays.toString(tie)
                + "; a tie goes to Suspicion");

        // Nobody who would refuse is ever sent.
        int[] nobody = CourtRules.arrivals(90, 0,
                new boolean[] {true, false}, new boolean[] {false, false});
        check(nobody.length == 0, "with nobody willing the rule still sends "
                + Arrays.toString(nobody) + ", an envoy who arrives to refuse you");
    }

    /** One writ of audience a visit. */
    private static void audience() {
        check(CourtRules.AUDIENCE_USES == 1, "a visit sells " + CourtRules.AUDIENCE_USES
                + " writs of audience, not one: none makes the writs creative-only again, and "
                + "more lets one visit stock a player with standing invitations");
    }

    /**
     * What mines a bench: the rules answer once for every bench they are
     * handed, and the rule that reads them reports the three ways a list can
     * be wrong. A bench in no tool's tag breaks at a fist's pace.
     */
    private static void harvest() {
        List<String> benches = List.of("signal_mast", "splicing_vat", "void_crucible",
                "hydroponics_rack", "gear_bench", "salvage_rig");
        check(HarvestRules.TOOLS.contains(HarvestRules.BENCH_TOOL), "the benches are mined by "
                + HarvestRules.BENCH_TOOL + ", which is no tool the game keeps a list for");
        List<String> shipped = HarvestRules.untooled(benches, HarvestRules.minedBy(benches),
                HarvestRules.BY_HAND);
        check(shipped.isEmpty(), "a bench is given no tool, or two: " + shipped);
        // The controls: a block no tool is decided for, one decided twice, and
        // a name that is no block of ours each have to be reported.
        List<String> unlisted = new ArrayList<>(benches);
        unlisted.add("loom");
        check(!HarvestRules.untooled(unlisted, HarvestRules.minedBy(benches),
                HarvestRules.BY_HAND).isEmpty(), "a block no tool is decided for is not reported");
        check(!HarvestRules.untooled(benches, HarvestRules.minedBy(benches),
                List.of("gear_bench")).isEmpty(),
                "a block mined by a tool and broken by hand is not reported");
        check(!HarvestRules.untooled(benches, java.util.Map.of("axe", List.of("loom")),
                HarvestRules.BY_HAND).isEmpty(),
                "a tool given to a block nobody registers is not reported");
    }

    /**
     * Every Favor and Suspicion a player can have, on the real court.
     *
     * @return how many standings were tried
     */
    private static int everyStanding(String[] args) {
        if (args.length < 3) {
            check(false, "no court was handed over. Run this through elysium-npcs/validate.py, "
                    + "which reads it out of EnvoyKind and the meters out of ElysiumStanding");
            return 0;
        }
        int max = Integer.parseInt(args[0].trim());
        String[] edges = args[1].split(",");
        int[] thresholds = new int[edges.length];
        for (int i = 0; i < edges.length; i++) {
            thresholds[i] = Integer.parseInt(edges[i].trim());
        }
        int size = args.length - 2;
        String[] ids = new String[size];
        boolean[] readsFavor = new boolean[size];
        int[] gate = new int[size];
        for (int i = 0; i < size; i++) {
            String[] part = args[i + 2].split(":");
            ids[i] = part[0];
            readsFavor[i] = part[1].equals("FAVOR");
            gate[i] = Integer.parseInt(part[2]);
        }
        check(max > 0, "the meters top out at " + max + ", so there are no standings to try");

        List<String> nobody = new ArrayList<>();
        List<String> refusing = new ArrayList<>();
        List<String> otherHalf = new ArrayList<>();
        int standings = 0;
        for (int favor = 0; favor <= max; favor++) {
            for (int suspicion = 0; suspicion <= max; suspicion++) {
                standings++;
                boolean favorLeads = favor > suspicion;
                boolean[] willDeal = new boolean[size];
                boolean halfWilling = false;
                for (int i = 0; i < size; i++) {
                    willDeal[i] = band(readsFavor[i] ? favor : suspicion, thresholds) >= gate[i];
                    halfWilling |= willDeal[i] && readsFavor[i] == favorLeads;
                }
                int[] come = CourtRules.arrivals(favor, suspicion, readsFavor, willDeal);
                String where = "Favor " + favor + ", Suspicion " + suspicion;
                if (come.length == 0) {
                    nobody.add(where);
                }
                for (int chosen : come) {
                    if (!willDeal[chosen]) {
                        refusing.add(ids[chosen] + " at " + where);
                    }
                    if (halfWilling && readsFavor[chosen] != favorLeads) {
                        otherHalf.add(ids[chosen] + " at " + where);
                    }
                }
            }
        }
        check(nobody.isEmpty(), "nobody can arrive at " + nobody.size() + " of " + standings
                + " standings, the first " + first(nobody) + ": a player can stay there as long "
                + "as they like and is never visited");
        check(refusing.isEmpty(), "an envoy is sent to a player they will not deal with ("
                + first(refusing) + "), which reads as the mod being broken");
        check(otherHalf.isEmpty(), "the other half of the court is sent while the player's own "
                + "half has somebody willing (" + first(otherHalf) + ")");
        return standings;
    }

    /** The band a meter's value falls in: how many edges it has reached. */
    private static int band(int value, int[] thresholds) {
        int band = 0;
        for (int edge : thresholds) {
            if (value >= edge) {
                band++;
            }
        }
        return band;
    }

    private static String first(List<String> found) {
        return found.isEmpty() ? "none" : found.get(0);
    }
}
