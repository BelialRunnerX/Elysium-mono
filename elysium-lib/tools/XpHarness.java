package com.elysium.lib.character;

import java.util.List;

/**
 * The experience rules, executed: every source pays, every cap holds, the
 * arithmetic of the window lets through what is left and no more, and no
 * combination of everything at once is a level a minute.
 */
public final class XpHarness {

    private static int checks;
    private static int failures;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + complaint);
        }
    }

    public static void main(String[] args) {
        for (String fault : ElysiumXpRules.faults()) {
            check(false, "rules: " + fault);
        }
        List<ElysiumXpRules.Source> sources = List.of(ElysiumXpRules.Source.values());
        check(sources.size() >= 12, "there are " + sources.size() + " sources; every ordinary "
                + "action was asked for, and that is more than a dozen");
        for (ElysiumXpRules.Source source : sources) {
            check(ElysiumXpRules.base(source) >= 1 && ElysiumXpRules.cap(source) >= ElysiumXpRules.base(source),
                    source + " pays nothing or is capped below one of itself");
        }

        // A minute of one thing, and a minute of everything.
        int mined = 0;
        int spent = 0;
        for (int block = 0; block < 100; block++) {
            int paid = ElysiumXpRules.allow(spent, ElysiumXpRules.cap(ElysiumXpRules.Source.MINE),
                    ElysiumXpRules.base(ElysiumXpRules.Source.MINE));
            spent += paid;
            mined += paid;
        }
        check(mined == ElysiumXpRules.cap(ElysiumXpRules.Source.MINE),
                "a hundred blocks of stone in a minute pay " + mined + ", not the cap of "
                        + ElysiumXpRules.cap(ElysiumXpRules.Source.MINE));
        int twenty = 20 * ElysiumXpRules.base(ElysiumXpRules.Source.MINE);
        check(twenty <= ElysiumXpRules.cap(ElysiumXpRules.Source.MINE),
                "twenty blocks of stone in a minute, which is ordinary mining, meets the cap");
        check(ElysiumXpRules.mostPerWindow() < ElysiumLevelling.xpToNext(1) * 6,
                "every cap met at once pays " + ElysiumXpRules.mostPerWindow() + " a minute, which "
                        + "is more than five first levels a minute");

        // The level curve this pays into is the library's own.
        check(ElysiumLevelling.xpToNext(1) == 110, "the first level costs " + ElysiumLevelling.xpToNext(1));
        int evening = 30 * ElysiumXpRules.base(ElysiumXpRules.Source.MINE)
                + 40 * ElysiumXpRules.base(ElysiumXpRules.Source.BUILD)
                + 10 * ElysiumXpRules.base(ElysiumXpRules.Source.CRAFT)
                + 3 * ElysiumXpRules.base(ElysiumXpRules.Source.EXPLORE)
                + 5 * ElysiumXpRules.base(ElysiumXpRules.Source.EAT);
        check(evening >= ElysiumLevelling.xpToNext(1),
                "an evening of building (thirty stone, forty blocks placed, ten crafts, three "
                        + "biomes, five meals) pays " + evening + ", short of the first level's "
                        + ElysiumLevelling.xpToNext(1));
        check(evening <= 3 * ElysiumLevelling.xpToNext(1),
                "an evening of building pays " + evening + ", which is more than three levels");

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + sources.size() + " sources, every one paying and capped; "
                + "a hundred stone in a minute pays the cap, an evening's building pays a level, "
                + "and everything at once is under six levels a minute");
    }
}
