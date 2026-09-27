package com.elysium.lib.run;

import java.util.List;

/**
 * The run rules, executed: the plate is the large square asked for, the dye
 * rule is "undyed joins anything, a dye joins itself, a device takes any",
 * and the tints keep an undyed run looking like itself.
 */
public final class RunHarness {
    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    public static void main(String[] args) {
        List<String> faults = RunRules.faults();
        check(faults.isEmpty(), "the rules find fault with themselves: " + faults);

        // The plate is large, centred and thin.
        check(RunRules.PLATE >= 8, "a plate of " + RunRules.PLATE + " pixels is not large");
        check(RunRules.PLATE_FROM + RunRules.PLATE == RunRules.PLATE_TO, "the plate's edges do not add up");
        check(16 - RunRules.PLATE_TO == RunRules.PLATE_FROM, "the plate is off centre");
        check(RunRules.PLATE > RunRules.ARM_TO - RunRules.ARM_FROM,
                "the plate is not wider than the arm, so it does not read as a cue");

        // The dye rule, every way round.
        for (String dye : RunRules.DYE_RGB.keySet()) {
            check(RunRules.mayJoin(RunRules.UNDYED, dye), "an undyed run refuses " + dye);
            check(RunRules.mayJoin(dye, RunRules.UNDYED), dye + " refuses an undyed run");
            check(RunRules.mayJoin(dye, dye), dye + " refuses itself");
            for (String other : RunRules.DYE_RGB.keySet()) {
                if (!other.equals(dye)) {
                    check(!RunRules.mayJoin(dye, other), dye + " joins " + other);
                }
            }
            check(RunRules.isDye(dye), dye + " is not a dye");
            check(RunRules.bodyTint(dye) == RunRules.DYE_RGB.get(dye), dye + " does not colour the body");
            check(RunRules.plateTint(dye, 0x123456) == RunRules.DYE_RGB.get(dye), dye + " does not colour the plate");
        }
        check(RunRules.mayJoin(null, null), "two undyed runs, read as null, refuse each other");
        check(!RunRules.isDye("chartreuse"), "a made-up dye counts");
        check(!RunRules.isDye(RunRules.UNDYED), "undyed counts as a dye");

        // An undyed run keeps its own look.
        check(RunRules.bodyTint(RunRules.UNDYED) == 0xFFFFFF, "an undyed body is tinted");
        check(RunRules.plateTint(RunRules.UNDYED, 0xB87333) == 0xB87333, "an undyed plate loses the run's colour");
        check(RunRules.BODY_TINT != RunRules.PLATE_TINT, "the body and the plate share a tint index");

        System.out.println(checked + " checks, " + failures + " failure(s)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
