package com.elysium.atelier.rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Every number in {@link JoinRules}, executed.
 *
 * <p>Two claims, both about all the blocks together rather than about any one
 * of them: joining is symmetric, and what the tags say matches what the blocks
 * do. Neither is visible in a tag file, because a tag file is only ever wrong
 * about what is missing from it.
 */
public final class JoinHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    public static void main(String[] args) {
        theCatalogue();
        theTags();
        theJoining();
        theGrain();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        // Printed so that the tag files this module ships can be compared
        // against the rules directly, rather than against the generator that
        // wrote them — which would only prove the generator agrees with
        // itself.
        for (JoinRules.Opening opening : JoinRules.catalogue()) {
            System.out.println("OPENING " + opening.id());
        }
        for (Map.Entry<String, List<String>> entry : JoinRules.tagFiles().entrySet()) {
            for (String block : entry.getValue()) {
                System.out.println("TAG " + entry.getKey() + " " + block);
            }
        }
        System.out.println(checks + " checks: nothing here that the game already has, "
                + "every opening in the tags its kind needs, every tag agreeing with "
                + "what the block actually does, joining symmetric over every pair, and "
                + "nothing joining across the grain");
    }

    // ==================================================================

    private static void theCatalogue() {
        List<JoinRules.Opening> all = JoinRules.catalogue();
        check(all.size() == JoinRules.howMany(),
                "the catalogue is " + all.size() + " and the count says "
                        + JoinRules.howMany());
        check(JoinRules.KINDS.size() == 5,
                "there are " + JoinRules.KINDS.size() + " kinds of opening");
        Set<String> ids = new HashSet<>();
        for (JoinRules.Opening opening : all) {
            check(ids.add(opening.id()), opening.id() + " is declared twice");
            check(JoinRules.KINDS.contains(opening.kind()),
                    opening.id() + " is a kind of nothing");
        }
        check(all.size() > 20,
                "only " + all.size() + " openings, which is too few for a check over "
                        + "every pair to mean anything");

        // The module's own stated principle, turned into a check: a decoration
        // mod that adds what the game already has adds nothing. The first
        // version of this group added sixteen materials' worth of doors,
        // eleven of which the game has shipped since 2011.
        check(JoinRules.alreadyHad().isEmpty(),
                "blocks the game already has: " + JoinRules.alreadyHad());
        int duplicated = 0;
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (String kind : JoinRules.KINDS) {
                if (JoinRules.alreadyInTheGame(kind, material)) {
                    duplicated++;
                    for (JoinRules.Opening opening : all) {
                        check(!opening.id().equals(material.name() + "_" + kind),
                                material.name() + "_" + kind + " is in the catalogue "
                                        + "and in the base game");
                    }
                }
            }
        }
        check(duplicated > 0,
                "nothing in the grid is something the game already has, so the table "
                        + "saying what it has is describing a game nobody is playing");

        boolean refused = false;
        try {
            new JoinRules.Opening("archway", AtelierRules.MATERIALS.get(0));
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a kind that is not a kind is accepted, so a typo becomes a "
                + "block with no tags and no behaviour");
        System.out.println("  the catalogue: " + all.size() + " opening(s) across "
                + JoinRules.KINDS.size() + " kinds and "
                + AtelierRules.MATERIALS.size() + " materials");
    }

    private static void theTags() {
        check(JoinRules.disagreeing().isEmpty(),
                "blocks whose tags and behaviour disagree: " + JoinRules.disagreeing());

        int tagged = 0;
        for (JoinRules.Opening opening : JoinRules.catalogue()) {
            Set<String> tags = JoinRules.tagsFor(opening);
            tagged += tags.size();
            check(!tags.isEmpty(), opening.id() + " is in no tag at all");

            // The one a villager reads, and the one a raid reads. They are the
            // same tag, and it has to say what the block does.
            if (JoinRules.DOOR.equals(opening.kind())) {
                check(tags.contains("minecraft:doors"),
                        opening.id() + " is not in #doors, so nothing in the game that "
                                + "reasons about doors knows it is one");
                check(tags.contains("minecraft:wooden_doors") == opening.soft(),
                        opening.id() + " is " + (opening.soft() ? "soft" : "hard")
                                + " and " + (tags.contains("minecraft:wooden_doors")
                                ? "in" : "not in") + " #wooden_doors");
            }
            if (JoinRules.FENCE.equals(opening.kind())) {
                check(tags.contains("minecraft:fences"),
                        opening.id() + " is not in #fences, so no fence in the game "
                                + "reaches for it and it stands alone in its own wall");
            }
            if (JoinRules.GATE.equals(opening.kind())) {
                check(tags.contains("minecraft:fence_gates"),
                        opening.id() + " is not in #fence_gates, so a run of fence "
                                + "stops dead on either side of it");
            }
            check(tags.contains("minecraft:mineable/axe")
                            ^ tags.contains("minecraft:mineable/pickaxe"),
                    opening.id() + " is mined by both tools or by neither");
            check(JoinRules.openedByHand(opening)
                            == (opening.soft() && !JoinRules.FENCE.equals(opening.kind())
                            && !JoinRules.PANE.equals(opening.kind())),
                    opening.id() + " opens by hand when it should not, or the other "
                            + "way about");
        }

        // And the files that come out of it have to contain every block that
        // asked for them, which is the direction a hand-written tag fails in.
        Map<String, List<String>> files = JoinRules.tagFiles();
        for (JoinRules.Opening opening : JoinRules.catalogue()) {
            for (String tag : JoinRules.tagsFor(opening)) {
                check(files.containsKey(tag) && files.get(tag).contains(opening.id()),
                        opening.id() + " asks for " + tag + " and is not written into "
                                + "it, which is the exact shape of every missing-tag "
                                + "bug there has ever been");
            }
        }
        System.out.println("  the tags: " + tagged + " membership(s) across "
                + files.size() + " tag file(s), every one of them derived from what the "
                + "block is");
    }

    private static void theJoining() {
        check(JoinRules.lopsided().isEmpty(),
                "joins drawn from one side only: " + JoinRules.lopsided());

        List<JoinRules.Opening> all = JoinRules.catalogue();
        int pairs = 0;
        int joins = 0;
        for (JoinRules.Opening one : all) {
            for (JoinRules.Opening other : all) {
                pairs++;
                boolean there = JoinRules.reaches(one, other);
                boolean back = JoinRules.reaches(other, one);
                check(there == back, one.id() + " and " + other.id() + " disagree "
                        + "about whether they touch");
                if (there) {
                    joins++;
                }
                // A door or a trapdoor joins nothing: it fills a hole rather
                // than making one, and a door that grew fence arms would be a
                // door nobody could stand next to.
                if (JoinRules.DOOR.equals(one.kind())
                        || JoinRules.TRAPDOOR.equals(one.kind())) {
                    check(!there, one.id() + " reaches for " + other.id()
                            + ", and a door that grows fence arms is a door nobody can "
                            + "put a wall beside");
                }
            }
        }
        check(joins > 0, "nothing in the module joins anything, so all five kinds are "
                + "decorations that happen to sit near each other");
        check(joins < pairs, "everything joins everything, so the relation says nothing");
        System.out.println("  the joining: " + pairs + " ordered pairs, " + joins
                + " of them joining, and not one of them drawn from one side only");
    }

    private static void theGrain() {
        check(JoinRules.crossJoined().isEmpty(),
                "fences joining across the grain: " + JoinRules.crossJoined());
        check(JoinRules.joiningTheWrongCrowd().isEmpty(),
                "blocks tagged as the wrong sort of thing: "
                        + JoinRules.joiningTheWrongCrowd());

        List<JoinRules.Opening> fences = new ArrayList<>();
        int hardMaterials = 0;
        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            if (!material.soft()) {
                hardMaterials++;
            }
        }
        for (JoinRules.Opening opening : JoinRules.catalogue()) {
            if (JoinRules.FENCE.equals(opening.kind())) {
                fences.add(opening);
            }
        }
        check(fences.size() == hardMaterials,
                "there are " + fences.size() + " fences and " + hardMaterials
                        + " materials the game has no fence for");

        // Every fence left here is hard, because the game already has every
        // wooden one. So the thing that must never happen is one of ours
        // sitting in #wooden_fences: it would join every oak fence in the
        // world and refuse the deepslate post beside it.
        for (JoinRules.Opening fence : fences) {
            check(!fence.soft(),
                    fence.id() + " is a soft fence, and the game already has one");
            check(!JoinRules.tagsFor(fence).contains("minecraft:wooden_fences"),
                    fence.id() + " is in #wooden_fences, so it joins oak and refuses "
                            + "the fence of its own material beside it");
        }

        // Swept over made-up pairs as well as ours, because the rule that
        // decides it is a tag comparison, and one written the other way round
        // still passes every fixed case above.
        Random random = new Random(773_311L);
        List<AtelierRules.Material> materials = AtelierRules.MATERIALS;
        for (int round = 0; round < 200_000; round++) {
            AtelierRules.Material first = materials.get(random.nextInt(materials.size()));
            AtelierRules.Material second = materials.get(random.nextInt(materials.size()));
            JoinRules.Opening one = new JoinRules.Opening(JoinRules.FENCE, first);
            JoinRules.Opening other = new JoinRules.Opening(JoinRules.FENCE, second);
            check(JoinRules.reaches(one, other) == (one.soft() == other.soft()),
                    one.id() + " and " + other.id() + " join when they are "
                            + (one.soft() == other.soft() ? "alike" : "unalike"));
        }
        System.out.println("  the grain: " + fences.size() + " fences, every one of them "
                + "for a material the game has none for, and 200,000 drawn pairs "
                + "joining exactly when they are made of the same sort of thing");
    }
}
