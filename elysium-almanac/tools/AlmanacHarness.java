package com.elysium.almanac.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The quest graph, executed.
 *
 * <p>Two properties, and both of them are invisible to whoever wrote the book:
 * no step is its own prerequisite, and no step is one a player could never
 * arrive at. An author sees every quest in their editor; a player sees only
 * the ones the graph actually leads to.
 *
 * <p>And a third thing, which is the point of a quest book at all: that a
 * player who does every quest the book offers, in the order it offers them,
 * ends up having built the pack. So the whole book is <em>played</em> here —
 * repeatedly taking whatever is open and marking it done — and checked to
 * finish.
 */
public final class AlmanacHarness {

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
        theShape();
        theGraph();
        theChapters();
        playedThrough();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: no quest waits on itself, none is one a "
                + "player could never arrive at, every chapter follows one a player has "
                + "already started, and playing the whole book in the order it offers "
                + "finishes it");
    }

    // ==================================================================

    private static void theShape() {
        check(AlmanacRules.QUESTS.size() >= 30,
                "there are fewer than thirty quests, which is not a guide to a pack of "
                        + "this size, it is a tutorial");
        check(AlmanacRules.CHAPTERS.size() >= 5,
                "there are fewer than five chapters, so the book has no structure to "
                        + "read it by");

        Set<String> ids = new HashSet<>();
        for (AlmanacRules.Quest quest : AlmanacRules.QUESTS) {
            check(ids.add(quest.id()),
                    quest.id() + " is declared twice, so one of the two is unreachable "
                            + "and which one depends on declaration order");
            check(AlmanacRules.chapter(quest.chapter()) != null,
                    quest.id() + " is in chapter " + quest.chapter() + ", which the book "
                            + "does not have — so it is in no chapter and is never shown");
            check(!quest.tasks().isEmpty(), quest.id() + " asks for nothing");
            for (AlmanacRules.Task task : quest.tasks()) {
                if (task.kind() != AlmanacRules.Kind.READ) {
                    check(task.what().contains(":"),
                            quest.id() + " asks for " + task.what() + ", which is not a "
                                    + "namespaced id and can therefore never be matched");
                }
                check(task.count() >= 1, quest.id() + " asks for none of something");
            }
        }

        boolean refused = false;
        try {
            new AlmanacRules.Task(AlmanacRules.Kind.HAVE, "", 1);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a task that asks for nothing in particular can be declared");
        refused = false;
        try {
            AlmanacRules.Task.have("minecraft:stone", 0);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a task that wants none of something can be declared");
        refused = false;
        try {
            new AlmanacRules.Quest("x", "first_light", List.of(), List.of());
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a quest that asks for nothing can be declared");
    }

    private static void theGraph() {
        Set<String> missing = AlmanacRules.danglingNeeds();
        check(missing.isEmpty(),
                "these prerequisites are named by a quest and are not quests: " + missing
                        + " — every quest behind one of them is unreachable");

        Set<String> looped = AlmanacRules.cycles();
        check(looped.isEmpty(),
                "these quests wait on themselves: " + looped + " — a player concludes "
                        + "the chapter is not finished yet and stops looking");

        Set<String> stuck = AlmanacRules.unreachable();
        check(stuck.isEmpty(),
                "these quests can never be arrived at: " + stuck + " — content the "
                        + "author can see and the player cannot");

        // The walker itself has to work, or all three checks above are vacuous.
        check(AlmanacRules.waitsOn("millstone", "welcome"),
                "the millstone is not seen to wait on the first page, three steps back");
        check(!AlmanacRules.waitsOn("welcome", "millstone"),
                "the first page is seen to wait on the millstone, which is the book "
                        + "backwards");
        check(!AlmanacRules.waitsOn("welcome", "welcome"),
                "the first page waits on itself, and it has no prerequisites at all");
        check(!AlmanacRules.waitsOn("no_such_quest", "welcome"),
                "a quest that does not exist has prerequisites");

        // Exactly one place to start, or a new player is given a choice before
        // they have anything to choose with.
        List<String> openings = new ArrayList<>();
        for (AlmanacRules.Quest quest : AlmanacRules.QUESTS) {
            if (quest.needs().isEmpty()) {
                openings.add(quest.id());
            }
        }
        check(openings.size() == 1,
                "the book opens at " + openings + "; a player on their first join should "
                        + "be looking at one page, not choosing between several");
        check(AlmanacRules.depthOf(openings.get(0)) == 0,
                "the page the book opens at is already several steps deep");

        // And it has to be a book rather than a corridor.
        int widest = 0;
        for (AlmanacRules.Quest quest : AlmanacRules.QUESTS) {
            int leadsTo = 0;
            for (AlmanacRules.Quest other : AlmanacRules.QUESTS) {
                if (other.needs().contains(quest.id())) {
                    leadsTo++;
                }
            }
            widest = Math.max(widest, leadsTo);
        }
        check(widest >= 3,
                "no quest in the book leads to more than " + widest + " others, so it is "
                        + "one long corridor and a player has no choice about what to do "
                        + "next");

        int deepest = 0;
        for (AlmanacRules.Quest quest : AlmanacRules.QUESTS) {
            deepest = Math.max(deepest, AlmanacRules.depthOf(quest.id()));
        }
        check(deepest >= 8,
                "the longest chain in the book is " + deepest + " steps, which is not a "
                        + "progression through four ages, it is a checklist");
        check(deepest <= 30,
                "the longest chain in the book is " + deepest + " steps, which is more "
                        + "of a queue than anybody will follow");
    }

    private static void theChapters() {
        Set<String> seen = new HashSet<>();
        int openings = 0;
        for (AlmanacRules.Chapter chapter : AlmanacRules.CHAPTERS) {
            check(seen.add(chapter.id()), chapter.id() + " is declared twice");
            check(!AlmanacRules.inChapter(chapter.id()).isEmpty(),
                    chapter.id() + " has no quests in it, so it is a heading with "
                            + "nothing under it");
            if (chapter.first()) {
                openings++;
            } else {
                for (String after : chapter.after()) {
                    check(AlmanacRules.chapter(after) != null,
                            chapter.id() + " follows " + after + ", which is not a "
                                    + "chapter");
                }
            }
        }
        check(openings == 1,
                "there are " + openings + " chapters that follow nothing; a book with "
                        + "two beginnings has no beginning");

        // A chapter may not need something from a chapter that comes later.
        List<String> order = new ArrayList<>();
        for (AlmanacRules.Chapter chapter : AlmanacRules.CHAPTERS) {
            order.add(chapter.id());
        }
        for (AlmanacRules.Quest quest : AlmanacRules.QUESTS) {
            for (String need : quest.needs()) {
                AlmanacRules.Quest before = AlmanacRules.quest(need);
                if (before == null) {
                    continue;
                }
                check(order.indexOf(before.chapter()) <= order.indexOf(quest.chapter()),
                        quest.id() + " in " + quest.chapter() + " waits on " + need
                                + " in " + before.chapter() + ", which the book lists "
                                + "later — so a player reads the chapters in an order "
                                + "they cannot do them in");
            }
        }

        // Every chapter has to be openable by somebody who has done the one
        // before it, or the book has a chapter nobody ever starts.
        Set<String> done = new LinkedHashSet<>();
        for (AlmanacRules.Chapter chapter : AlmanacRules.CHAPTERS) {
            check(AlmanacRules.through(chapter.id(), Set.of()) == 0.0F,
                    chapter.id() + " is partly finished before a player has done "
                            + "anything");
        }
        check(AlmanacRules.started("first_light", Set.of()),
                "the first chapter cannot be started by somebody who has done nothing");
        check(!AlmanacRules.started("foundry", Set.of()),
                "the last chapter can be started on the first day, so the order the book "
                        + "is written in means nothing");
    }

    /**
     * The whole book, played.
     *
     * Repeatedly take everything the book currently offers and mark it done.
     * If that does not finish the book, some quest is behind something that
     * never opens — which is exactly the failure a player would meet as "this
     * chapter seems to be broken" and would never report as a bug.
     */
    private static void playedThrough() {
        Set<String> done = new LinkedHashSet<>();
        int rounds = 0;
        int mostOpenAtOnce = 0;
        while (rounds++ < AlmanacRules.QUESTS.size() + 2) {
            List<AlmanacRules.Quest> open = AlmanacRules.openTo(done);
            mostOpenAtOnce = Math.max(mostOpenAtOnce, open.size());
            if (open.isEmpty()) {
                break;
            }
            for (AlmanacRules.Quest quest : open) {
                done.add(quest.id());
            }
        }
        check(done.size() == AlmanacRules.QUESTS.size(),
                "playing the whole book in the order it offers finishes " + done.size()
                        + " of " + AlmanacRules.QUESTS.size() + " quests; the rest are "
                        + "behind something that never opens");
        check(rounds <= AlmanacRules.QUESTS.size() + 2,
                "playing the book did not settle, which means the graph is not a graph");
        check(mostOpenAtOnce >= 2,
                "at no point does the book offer a player more than one thing to do, so "
                        + "there is never a decision in it");

        // And nothing may be offered twice, or completed by accident.
        check(AlmanacRules.openTo(done).isEmpty(),
                "the book still offers quests after every one of them is done");
        check(AlmanacRules.through("first_light", done) == 1.0F,
                "the first chapter is not finished after everything is done");
        for (AlmanacRules.Chapter chapter : AlmanacRules.CHAPTERS) {
            check(AlmanacRules.started(chapter.id(), done),
                    chapter.id() + " is never started even after the whole book is done");
        }
    }
}
