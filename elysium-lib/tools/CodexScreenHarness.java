package com.elysium.lib.screen;

import com.elysium.lib.character.ElysiumClass;
import com.elysium.lib.character.ElysiumRace;
import com.elysium.lib.network.CharacterSheet;
import com.elysium.lib.research.ElysiumResearchField;
import com.elysium.lib.research.ElysiumResearchRank;
import com.elysium.lib.research.ElysiumUnlock;

import com.elysium.lib.character.ElysiumPassive;
import com.elysium.lib.stats.ElysiumStatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proves that spending a point does not throw the player off the page.
 *
 * <h2>The bug</h2>
 *
 * Clicking a stat's upgrade stepper sent a packet, the server spent the point
 * and pushed the sheet back, and the client answered by constructing a new
 * {@link ElysiumCharacterScreen}. A new screen starts with every field at its
 * initial value — including the tab, which starts on the character page. So the
 * point was always spent correctly and the player was always thrown back to the
 * first page, every single time.
 *
 * <h2>Why this is worth executing rather than reading</h2>
 *
 * The fix is one branch in {@code ElysiumClientHooks} and one method here, and
 * reading them makes it look obvious that the tab survives. It looked obvious
 * before, too. What actually decides it is whether {@code refresh} touches the
 * fields that belong to the player's session with the screen, and that is a
 * property of a method body rather than of a design — the sort of thing a later
 * edit adding "and reset to the overview, it looks tidier" would break with
 * nothing to say so.
 *
 * So this drives the real screen object: sets a tab, hands it a newer sheet the
 * way the packet handler does, and reads the private field back. Reflection is
 * the right tool for once — the field is private because nothing outside the
 * screen should write it, which is exactly the invariant being tested.
 *
 * Compile and run:
 *   javac -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib" \
 *         -d /tmp/ch elysium-lib/tools/CodexScreenHarness.java
 *   java  -cp "$(cat /tmp/mc/cp.txt):/tmp/elysium-classes/elysium-lib:/tmp/ch" \
 *         com.elysium.lib.screen.CodexScreenHarness
 */
public final class CodexScreenHarness {

    private static int failures = 0;
    private static int checked = 0;

    private static void check(boolean condition, String message) {
        checked++;
        if (!condition) {
            System.out.println("  FAIL: " + message);
            failures++;
        }
    }

    /**
     * A race and a class that exist only here.
     *
     * The two picker tests used to print "skipped" outside a game, because
     * nothing registers content on a bare classpath — and a skipped assertion
     * is one that stops being run long before anybody notices it stopped being
     * true. Registering a throwaway pair costs four lines and makes them real.
     */
    private static final ElysiumPassive NOTHING = new ElysiumPassive() {
        @Override
        public Component getDisplayName() {
            return Component.literal("Test");
        }

        @Override
        public Component getDescription() {
            return Component.literal("Registered by the harness only.");
        }
    };

    private static ElysiumRace testRace;
    private static ElysiumClass testClass;

    private static void registerTestContent() {
        ElysiumStatBlock empty = ElysiumStatBlock.of();
        testRace = ElysiumRace.register(
                ResourceLocation.fromNamespaceAndPath("elysiumtest", "race"),
                ChatFormatting.WHITE, empty, empty, NOTHING);
        testClass = ElysiumClass.register(
                ResourceLocation.fromNamespaceAndPath("elysiumtest", "job"),
                ChatFormatting.WHITE, empty, NOTHING);
    }

    private static Field field(String name) throws Exception {
        Field found = ElysiumCharacterScreen.class.getDeclaredField(name);
        found.setAccessible(true);
        return found;
    }

    private static CharacterSheet.Parsed sheet(ElysiumRace race, ElysiumClass job,
                                               int level, int favour) {
        Map<com.elysium.lib.stats.ElysiumStat, Integer> stats = new LinkedHashMap<>();
        return new CharacterSheet.Parsed(race, job, level, 0, 100, stats, favour, 0,
                new java.util.LinkedHashMap<>(), 0);
    }

    /** The whole point: the tab a player is on survives a point being spent. */
    private static void tabSurvivesASpend() throws Exception {
        CharacterSheet.Parsed before = sheet(null, null, 4, 10);
        ElysiumCharacterScreen screen = new ElysiumCharacterScreen(before, 3);

        Field tab = field("tab");
        Field unspent = field("unspent");
        Field held = field("sheet");

        // The player clicks through to the stats page. 1 is TAB_STATS; the
        // constant is private and its value is what the click handler stores.
        tab.setInt(screen, 1);

        // ...and spends a point. This is exactly what the packet handler does.
        screen.refresh(sheet(null, null, 4, 10), 2);

        check(tab.getInt(screen) == 1,
                "spending a point moved the player from tab 1 to tab "
                        + tab.getInt(screen) + " — this is the reported bug: "
                        + "the upgrade button throws you back to the first page");
        check(unspent.getInt(screen) == 2,
                "the screen still shows " + unspent.getInt(screen)
                        + " unspent points after one was spent, so the refresh "
                        + "kept the page and dropped the numbers");
        check(held.get(screen) != before,
                "the screen is still holding the sheet it was built with, so "
                        + "nothing on it will ever update");
    }

    /** Every tab, not just the one the bug was noticed on. */
    private static void everyTabSurvives() throws Exception {
        Field tab = field("tab");
        // Every tab the label strip offers, not a number typed in here. A
        // fourth tab added without this loop growing would be a page nobody
        // ever checked survives a spend, and it would look exactly like the
        // three that do.
        int pages = tabCount();
        for (int page = 0; page < pages; page++) {
            ElysiumCharacterScreen screen =
                    new ElysiumCharacterScreen(sheet(null, null, 1, 0), 5);
            tab.setInt(screen, page);
            screen.refresh(sheet(null, null, 1, 0), 4);
            check(tab.getInt(screen) == page,
                    "tab " + page + " became " + tab.getInt(screen)
                            + " after a refresh");
        }
    }

    /**
     * A refresh arriving mid-choice must not overwrite a tentative pick.
     *
     * The quieter half of the same bug. Before a character is chosen, the
     * screen's race and class fields hold a selection the server has never
     * heard of — so a sheet arriving while the picker is open (a Codex click, a
     * resend) has nothing better to put there and must leave it alone.
     */
    private static void tentativePickSurvives() throws Exception {
        CharacterSheet.Parsed unchosen = sheet(null, null, 1, 0);
        ElysiumCharacterScreen screen = new ElysiumCharacterScreen(unchosen, 0);

        Field race = field("pickedRace");
        race.set(screen, testRace);

        screen.refresh(sheet(null, null, 1, 0), 0);
        check(race.get(screen) == testRace,
                "a refresh while the player was mid-choice cleared the race "
                        + "they had selected but not confirmed");
    }

    /**
     * ...and once the choice is made, the server's answer wins.
     *
     * The other direction, which the same branch decides. If this stopped
     * holding, confirming a character would leave the screen drawing whatever
     * the player last hovered rather than what they actually are.
     */
    private static void confirmedChoiceIsAdopted() throws Exception {
        ElysiumRace race = testRace;
        ElysiumClass job = testClass;
        ElysiumCharacterScreen screen =
                new ElysiumCharacterScreen(sheet(null, null, 1, 0), 0);
        screen.refresh(sheet(race, job, 1, 0), 0);

        check(field("pickedRace").get(screen) == race
                        && field("pickedClass").get(screen) == job,
                "the screen did not adopt the race and class the server "
                        + "confirmed, so a freshly made character draws as "
                        + "somebody else");
    }

    /** How many tabs the screen actually offers. Read, not assumed. */
    private static int tabCount() throws Exception {
        Field labels = ElysiumCharacterScreen.class.getDeclaredField("TAB_LABELS");
        labels.setAccessible(true);
        return ((Object[]) labels.get(null)).length;
    }

    // ======================================================================
    // The sheet format
    // ======================================================================
    //
    // The whole argument for a delimited string over a typed packet is that it
    // survives one side being older than the other. That argument has now been
    // relied on twice — Favor and Suspicion were appended, then research and
    // the catalogue — and until now nothing executed it.

    /** A sheet from before research existed reads as a player with none. */
    private static void anOlderSheetStillParses() {
        String old = "|" + "|" + "3|10|100|" + "|" + "12|34";
        CharacterSheet.Parsed parsed = CharacterSheet.parse(old);
        check(parsed.level() == 3 && parsed.favor() == 12 && parsed.suspicion() == 34,
                "a seven-field sheet no longer parses: level " + parsed.level()
                        + ", favor " + parsed.favor());
        check(parsed.catalogued() == 0,
                "an older sheet reports " + parsed.catalogued() + " catalogued");
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            check(parsed.points(field) == 0,
                    "an older sheet reports " + parsed.points(field) + " in "
                            + field + "; it cannot know anything about research");
            check(parsed.rank(field) == ElysiumResearchRank.UNSTUDIED,
                    field + " came back as " + parsed.rank(field)
                            + " from a sheet that never mentioned it");
        }
    }

    /** A full sheet round-trips every field and every rank. */
    private static void aFullSheetParses() {
        StringBuilder research = new StringBuilder();
        int points = 0;
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            if (research.length() > 0) {
                research.append(',');
            }
            research.append(field.id()).append('=').append(points);
            points += 150;
        }
        String packed = "||7|5|120||3|9|" + research + "|42";
        CharacterSheet.Parsed parsed = CharacterSheet.parse(packed);
        check(parsed.catalogued() == 42,
                "catalogued came back as " + parsed.catalogued());
        int expected = 0;
        for (ElysiumResearchField field : ElysiumResearchField.values()) {
            check(parsed.points(field) == expected,
                    field + " came back as " + parsed.points(field)
                            + ", not " + expected);
            check(parsed.rank(field) == ElysiumResearchRank.of(expected),
                    field + " ranked " + parsed.rank(field) + " at "
                            + expected + " points");
            expected += 150;
        }
        // And the unlock gate the screen draws with agrees with the enum.
        for (ElysiumUnlock unlock : ElysiumUnlock.values()) {
            check(parsed.has(unlock)
                            == unlock.metBy(parsed.points(unlock.field())),
                    "the sheet and the unlock disagree about " + unlock);
        }
    }

    /** A field from a mod that is no longer installed is skipped, not fatal. */
    private static void anUnknownFieldIsSkipped() {
        CharacterSheet.Parsed parsed = CharacterSheet.parse(
                "||1|0|100||0|0|geology=200,a_removed_mods_field=999|7");
        check(parsed.points(ElysiumResearchField.GEOLOGY) == 200,
                "a known field beside an unknown one was lost: "
                        + parsed.points(ElysiumResearchField.GEOLOGY));
        check(parsed.catalogued() == 7,
                "the fields after an unknown one were lost");
    }

    /** Nothing in a malformed sheet throws; the screen must always draw. */
    private static void rubbishDoesNotThrow() {
        for (String packed : new String[]{
                "", "|||||||||", "||x|y|z||q|w|geology=notanumber|alsonot",
                "||1|0|100||0|0|=5|3", "||1|0|100||0|0|geology|3"}) {
            try {
                CharacterSheet.Parsed parsed = CharacterSheet.parse(packed);
                check(parsed != null, "parse returned null for " + packed);
                check(parsed.points(ElysiumResearchField.GEOLOGY) >= 0,
                        "a malformed sheet produced negative research");
            } catch (RuntimeException failure) {
                check(false, "parsing " + packed + " threw " + failure
                        + "; the one thing worse than a wrong character screen "
                        + "is a client that disconnects drawing one");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        registerTestContent();
        tabSurvivesASpend();
        everyTabSurvives();
        tentativePickSurvives();
        confirmedChoiceIsAdopted();
        anOlderSheetStillParses();
        aFullSheetParses();
        anUnknownFieldIsSkipped();
        rubbishDoesNotThrow();

        System.out.println("codex screen: the page survives a spend, and a "
                + "sheet survives the other side being a different age — "
                + checked + " assertions");
        if (failures > 0) {
            System.out.println(failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("the upgrade button leaves you where you were");
    }
}
