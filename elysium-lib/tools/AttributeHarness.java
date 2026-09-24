package com.elysium.lib.attribute;

import com.elysium.lib.stats.ElysiumShares;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The combat attribute table, executed.
 *
 * <ol>
 *   <li><b>Coverage.</b> Every one of Apotheosis's 22 attributes is either a
 *       row here or named as mapped elsewhere, and nothing is both.</li>
 *   <li><b>Uncapped.</b> Every row's ceiling is the shared finite MAX, and
 *       its floor is at or below its default.</li>
 *   <li><b>Chances are odds.</b> The stored default converts back to the
 *       nominal chance; stacking modifiers linearly gives exactly
 *       {@link ElysiumShares#add}; no number of modifiers reaches 100%.</li>
 *   <li><b>The display.</b> The odds of 5% show as "5%" on a tooltip.</li>
 * </ol>
 */
public final class AttributeHarness {

    private static int checks;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }

    private static final List<String> APOTHEOSIS = List.of(
            "armor_pierce", "armor_shred", "arrow_damage", "arrow_velocity", "cold_damage",
            "crit_chance", "crit_damage", "current_hp_damage", "dodge_chance", "draw_speed",
            "experience_gained", "fire_damage", "ghost_health", "healing_received", "life_steal",
            "mining_speed", "overheal", "projectile_damage", "prot_pierce", "prot_shred",
            "elytra_flight", "cooldown_reduction");

    public static void main(String[] args) {
        // 1. coverage
        Set<String> rows = new HashSet<>();
        for (ElysiumAttributeSpec s : ElysiumAttributeSpec.ALL) {
            check(rows.add(s.name()), "duplicate row " + s.name());
            check(s.name().matches("[a-z_]+"), "bad id " + s.name());
        }
        for (String m : ElysiumAttributeSpec.MAPPED_ELSEWHERE) {
            check(!rows.contains(m), m + " is both a row and mapped elsewhere");
        }
        check(APOTHEOSIS.size() == 22, "the reference list has 22 names");
        for (String a : APOTHEOSIS) {
            check(rows.contains(a) || ElysiumAttributeSpec.MAPPED_ELSEWHERE.contains(a),
                    a + " is not covered");
        }
        check(rows.size() + ElysiumAttributeSpec.MAPPED_ELSEWHERE.size() == 22,
                "rows beyond the reference list");

        // 2. uncapped
        for (ElysiumAttributeSpec s : ElysiumAttributeSpec.ALL) {
            check(ElysiumAttributeSpec.MAX >= 1.0E15D, "a ceiling crept back in");
            check(s.storedMin() <= s.storedDefault(), s.name() + " floor above default");
            check(Double.isFinite(s.storedDefault()), s.name() + " default not finite");
            if (s.kind() == ElysiumAttributeSpec.Kind.MULTIPLIER) {
                check(s.nominalDefault() >= 1.0D, s.name() + " multiplier starts below 1");
            }
        }
        check(ElysiumAttributeSpec.named("crit_damage").nominalDefault() == 1.5D, "crit damage 1.5");
        check(ElysiumAttributeSpec.named("crit_damage").min() == 1.0D, "crit never weakens a hit");

        // 3. chances are odds
        ElysiumAttributeSpec crit = ElysiumAttributeSpec.named("crit_chance");
        check(crit.kind() == ElysiumAttributeSpec.Kind.CHANCE, "crit is a chance");
        check(near(ElysiumAttributeSpec.chance(crit.storedDefault()), 0.05D, 1e-6),
                "crit default is 5%: " + ElysiumAttributeSpec.chance(crit.storedDefault()));
        double half = ElysiumAttributeSpec.odds(0.5D);
        check(near(ElysiumAttributeSpec.chance(half + half), 2.0D / 3.0D, 1e-6), "50% on 50% is 2/3");
        float[] ps = {0.05F, 0.1F, 0.25F, 0.5F, 0.9F, 0.99F};
        for (float a : ps) {
            for (float b : ps) {
                double linear = ElysiumAttributeSpec.odds(a) + ElysiumAttributeSpec.odds(b);
                check(near(ElysiumAttributeSpec.chance(linear), ElysiumShares.add(a, b), 1e-6),
                        "linear odds " + a + "+" + b + " is not ElysiumShares.add");
                check(ElysiumAttributeSpec.chance(linear) >= Math.max(a, b) - 1e-6,
                        "adding a chance lowered it");
            }
        }
        double sum = 0.0D;
        double last = 0.0D;
        for (int n = 1; n <= 100000; n++) {
            sum += ElysiumAttributeSpec.odds(0.3D);
            double c = ElysiumAttributeSpec.chance(sum);
            check(c < 1.0D, n + " modifiers of 30% reached certainty");
            check(c >= last, "chance fell as modifiers were added");
            last = c;
        }
        check(ElysiumAttributeSpec.chance(0.0D) == 0.0D, "no odds, no chance");
        check(ElysiumAttributeSpec.chance(-1.0D) == 0.0D, "negative odds are no chance");

        // 4. display (ElysiumChanceAttribute forwards to percentText)
        check(ElysiumAttributeSpec.percentText(ElysiumAttributeSpec.odds(0.05D)).equals("5%"),
                "5% displays as " + ElysiumAttributeSpec.percentText(ElysiumAttributeSpec.odds(0.05D)));
        check(ElysiumAttributeSpec.percentText(half).equals("50%"), "50% displays wrong");
        check(ElysiumAttributeSpec.percentText(-half).equals("50%"), "a malus shows its size");
        check(ElysiumAttributeSpec.percentText(ElysiumAttributeSpec.odds(0.125D)).equals("12.5%"),
                "12.5% displays wrong");

        // 5. the contributor table behind ElysiumStackModifiers
        ElysiumContributorTable<String> table = new ElysiumContributorTable<>();
        table.register("zeta:b", "z");
        table.register("alpha:a", "a");
        table.register("mid:c", "m");
        check(table.ordered().get(0).getKey().equals("alpha:a")
                && table.ordered().get(2).getKey().equals("zeta:b"), "contributors not sorted by name");
        boolean threw = false;
        try { table.register("alpha:a", "again"); } catch (IllegalStateException e) { threw = true; }
        check(threw, "a second contributor under one name was accepted");
        check(table.ordered().get(0).getValue().equals("a"), "the first contributor was replaced");
        for (String bad : new String[] {"noNamespace", "Upper:case", ":x", "x:", null}) {
            threw = false;
            try { table.register(bad, "x"); } catch (IllegalArgumentException e) { threw = true; }
            check(threw, "bad name accepted: " + bad);
        }
        threw = false;
        try { table.register("ok:null", null); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "a null contributor was accepted");
        check(ElysiumContributorTable.owns("elysiumascendancy:affixes", "elysiumascendancy:affix/crit"), "own namespace refused");
        check(!ElysiumContributorTable.owns("elysiumascendancy:affixes", "elysiumarmory:part"), "foreign namespace allowed");
        check(!ElysiumContributorTable.owns("elysium:x", "elysiumlib:x"), "prefix namespace allowed");
        check(!ElysiumContributorTable.owns("elysium:x", "nocolon"), "id without namespace allowed");

        System.out.println(checks + " checks: all 22 Apotheosis attributes covered ("
                + rows.size() + " rows, " + ElysiumAttributeSpec.MAPPED_ELSEWHERE.size()
                + " mapped), uncapped, chances stack as odds and never reach 100%; contributors sorted, unique and namespaced");
    }
}
