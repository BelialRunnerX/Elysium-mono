package com.elysium.bestiary.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Eight mini-bosses, the courts around them, and the proof that a court is a
 * fight rather than a wall.
 *
 * <h2>What the bestiary did not have</h2>
 *
 * {@link BeastRules} is nine creatures on three ladders with a proved reward
 * curve, and no boss tier at all. Everything added since has been held
 * <em>under</em> it: a myth may not out-fight the beast of its rung, a variant
 * may not either, a companion may not do a block's job for free. That was the
 * right default while the pack had no bosses, and it is the wrong one now,
 * because a boss that cannot be harder than an ordinary mob is not a boss.
 *
 * <p>So this is the file that says what a boss is here, and it says it in three
 * numbers rather than in prose.
 *
 * <h2>The three</h2>
 *
 * <b>A head may be harder, by a stated amount.</b> {@link #BOSS_OVER_BEAST} —
 * three times the hardest beast of its own rung, and not more. "As hard as we
 * felt like" is how a boss becomes a wall, and the difference between a hard
 * fight and a wall is invisible from inside one, because both feel like losing.
 * {@link #faultsInStature}.
 *
 * <b>A court is support, not the fight.</b> Everything a head can have standing
 * at once, counted at what each of them is worth, is held under the head's own
 * threat by {@link #COURT_SHARE}. Past that the minions are the fight and the
 * head is scenery with a health bar — which is the single most common way a
 * boss mod goes wrong, and it goes wrong by addition: nobody ever decides it,
 * somebody just adds a ninth kind of skeleton. {@link #faultsInCourt}.
 *
 * <p>Note what that means for a large court: the budget is divided, so a court
 * of thirteen has weaker members than a court of two. Adding to a court makes
 * every member of it weaker, which is the opposite of how rosters usually grow
 * and is the whole reason this rule is arithmetic rather than advice.
 *
 * <b>A second phase changes something.</b> A head with two phases names what
 * answers each of them, and the two may not be the same thing.
 * {@link #faultsInPhases}. A phase two that is the same fight with more health
 * is not a phase, it is the health bar the designer wanted in the first place,
 * and the player has already spent their best answer on phase one.
 */
public final class CourtRules {

    private CourtRules() {
    }

    // ==================================================================
    // The seven things a creature is in a court
    // ==================================================================

    /** The one the court is around. */
    public static final String HEAD = "head";

    /** It calls more of the court up. */
    public static final String HERALD = "herald";

    /** There are always several, and none of them is the problem. */
    public static final String SWARM = "swarm";

    /** It stands between you and the head. */
    public static final String SHIELD = "shield";

    /** It hits you from where the head is not. */
    public static final String ARTILLERY = "artillery";

    /** It puts back what you took off the head. */
    public static final String HEALER = "healer";

    /** The head rides it, and killing it is a decision. */
    public static final String MOUNT = "mount";

    /** The seven, in the order everything walks them. */
    public static final List<String> ROLES = List.of(
            HEAD, HERALD, SWARM, SHIELD, ARTILLERY, HEALER, MOUNT);

    /**
     * One creature, and what it is in its court.
     *
     * @param id     what it is called
     * @param court  the court it belongs to
     * @param role   which of the seven it is
     * @param tier   the rung of the beasts' ladder the whole court stands on
     * @param health what it can take
     * @param damage what it gives on contact
     * @param speed  how fast it closes
     * @param most   how many of it may be standing at once
     * @param weight how often it is chosen when something spawns
     * @param first  what answers it, or its first phase
     * @param then   what answers its second phase, or empty if it has one phase
     */
    public record Member(String id, String court, String role, int tier,
                         double health, double damage, double speed,
                         int most, int weight, String first, String then) {

        public Member {
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("a creature with no name");
            }
            if (court == null || court.isEmpty()) {
                throw new IllegalArgumentException(id + " belongs to no court, so "
                        + "nothing holds it to being worth less than a head");
            }
            if (health <= 0.0D || damage < 0.0D || speed <= 0.0D) {
                throw new IllegalArgumentException(id + " has " + health
                        + " health, " + damage + " damage and " + speed
                        + " speed, and a creature with no health or no legs is "
                        + "not a creature");
            }
            if (most < 1) {
                throw new IllegalArgumentException(id + " may have " + most
                        + " standing at once, which is a row nothing ever places");
            }
            if (weight < 1) {
                throw new IllegalArgumentException(id + " is chosen " + weight
                        + " times when something spawns, so it is a creature "
                        + "nobody ever meets");
            }
            if (tier < 1) {
                throw new IllegalArgumentException(id + " stands on tier " + tier
                        + ", which is below the bottom of the ladder");
            }
        }

        /**
         * How dangerous it is, on one crude number.
         *
         * <p>The same shape every other roster in this module uses, so that all
         * of them compare honestly against each other and against the beasts.
         */
        public double threat() {
            return health * (1.0D + damage) * speed / 0.25D;
        }

        /** Whether it is the one its court is around. */
        public boolean head() {
            return role.equals(HEAD);
        }

        /** Whether it fights twice. */
        public boolean twoPhased() {
            return then != null && !then.isEmpty();
        }

        /** Everything it is worth when every one of it is standing. */
        public double standing() {
            return threat() * most;
        }
    }

    /**
     * How much harder than the hardest beast of its rung a head may be.
     *
     * <p>Three. A boss that cannot be harder than an ordinary mob is not a
     * boss, and a boss that may be arbitrarily harder is a wall — so the
     * difference is a number somebody decided once and wrote down, rather than
     * a feeling each new boss is designed by.
     */
    public static final double BOSS_OVER_BEAST = 3.0D;

    /**
     * How much of the fight the court around a head may be.
     *
     * <p>All of it, and no more. The court may be worth as much as the head —
     * that is what makes it a court rather than a garnish — but the moment it
     * is worth more, the head is the thing you ignore while you deal with the
     * real fight, and every boss in the pack becomes the same boss.
     */
    public static final double COURT_SHARE = 1.0D;

    /** The rarest anything here may be, so a court is met and not read about. */
    public static final int LEAST_WEIGHT = 4;

    /**
     * The fifty-eight.
     *
     * <p>Read down the {@code role} column and each court is a sentence: one
     * head, somebody to call the others up, several of something weak, a wall
     * or two, something at range, and — for three of the eight — somebody
     * putting the head back together.
     */
    public static final List<Member> MEMBERS = List.of(
            new Member("supreme_bonescaller", "bonescaller", HEAD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "minecraft:shield", "minecraft:bow"),
            new Member("bonescaller", "bonescaller", HERALD, 2, 16.0D, 6.0D, 0.28D, 1, 10, "", ""),
            new Member("baby_skeleton", "bonescaller", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("decrepit_skeleton", "bonescaller", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("bone_imp", "bonescaller", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("siamese_skeletons", "bonescaller", SHIELD, 2, 12.0D, 7.5D, 0.22D, 2, 18, "", ""),
            new Member("skeleton_thrasher", "bonescaller", SHIELD, 2, 12.0D, 7.5D, 0.22D, 2, 18, "", ""),
            new Member("door_knight", "bonescaller", SHIELD, 2, 12.0D, 7.5D, 0.22D, 2, 18, "", ""),
            new Member("skeleton_demoman", "bonescaller", ARTILLERY, 2, 14.0D, 4.5D, 0.26D, 2, 14, "", ""),

            new Member("lord_pumpkinhead", "pumpkin", HEAD, 3, 194.0D, 14.0D, 0.26D, 1, 4, "minecraft:water_bucket", "minecraft:shears"),
            new Member("mr_pumpkin", "pumpkin", HERALD, 3, 22.0D, 8.0D, 0.28D, 1, 10, "", ""),
            new Member("pumpkin_spirit", "pumpkin", HERALD, 3, 22.0D, 8.0D, 0.28D, 1, 10, "", ""),
            new Member("pumpkinhead", "pumpkin", SWARM, 3, 8.0D, 4.0D, 0.34D, 3, 40, "", ""),
            new Member("pumpkin_bomb", "pumpkin", SWARM, 3, 8.0D, 4.0D, 0.34D, 3, 40, "", ""),
            new Member("pumpkin_dunce", "pumpkin", SWARM, 3, 8.0D, 4.0D, 0.34D, 3, 40, "", ""),
            new Member("lord_the_headless", "pumpkin", SHIELD, 3, 17.0D, 10.0D, 0.22D, 2, 18, "", ""),
            new Member("sir_pumpkinhead", "pumpkin", SHIELD, 3, 17.0D, 10.0D, 0.22D, 2, 18, "", ""),
            new Member("sir_the_headless", "pumpkin", SHIELD, 3, 17.0D, 10.0D, 0.22D, 2, 18, "", ""),
            new Member("pumpkin_bruiser", "pumpkin", SHIELD, 3, 17.0D, 10.0D, 0.22D, 2, 18, "", ""),
            new Member("senor_pumpkin", "pumpkin", ARTILLERY, 3, 19.0D, 6.0D, 0.26D, 2, 14, "", ""),
            new Member("mrs_pumpkin", "pumpkin", HEALER, 3, 36.0D, 4.0D, 0.24D, 1, 8, "", ""),
            new Member("lords_felsteed", "pumpkin", MOUNT, 3, 18.0D, 6.0D, 0.40D, 1, 8, "", ""),

            new Member("krampus", "krampus", HEAD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "minecraft:snowball", ""),
            new Member("krampus_henchman", "krampus", SWARM, 2, 71.0D, 3.0D, 0.34D, 3, 40, "", ""),

            new Member("zombie_bruiser", "zombie", HEAD, 1, 39.0D, 7.0D, 0.26D, 1, 4, "minecraft:iron_sword", ""),
            new Member("zombie_clown", "zombie", HERALD, 1, 9.0D, 4.0D, 0.28D, 1, 10, "", ""),
            new Member("decaying_zombie", "zombie", SWARM, 1, 4.0D, 2.0D, 0.34D, 3, 40, "", ""),
            new Member("barrel_zombie", "zombie", SHIELD, 1, 7.0D, 5.0D, 0.22D, 2, 18, "", ""),
            new Member("zombie_lumberjack", "zombie", SHIELD, 1, 7.0D, 5.0D, 0.22D, 2, 18, "", ""),
            new Member("zombie_fisherman", "zombie", ARTILLERY, 1, 8.0D, 3.0D, 0.26D, 2, 14, "", ""),

            new Member("spiritof_chaos", "spirit", HEAD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "minecraft:torch", "minecraft:milk_bucket"),
            new Member("dark_vortex", "spirit", HERALD, 2, 28.0D, 6.0D, 0.28D, 1, 10, "", ""),
            new Member("restless_spirit", "spirit", SWARM, 2, 10.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("seared_spirit", "spirit", SWARM, 2, 10.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("firelight", "spirit", SWARM, 2, 10.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("infernal_spirit", "spirit", ARTILLERY, 2, 24.0D, 4.5D, 0.26D, 2, 14, "", ""),
            new Member("spirit_guide", "spirit", HEALER, 2, 43.0D, 3.0D, 0.24D, 1, 8, "", ""),

            new Member("dire_hound_leader", "hound", HEAD, 1, 39.0D, 7.0D, 0.26D, 1, 4, "minecraft:bone", ""),
            new Member("dread_hound", "hound", SWARM, 1, 26.0D, 2.0D, 0.34D, 3, 40, "", ""),

            new Member("mother_spider", "vermin", HEAD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "minecraft:cobweb", ""),
            new Member("swarmer", "vermin", HERALD, 2, 16.0D, 6.0D, 0.28D, 1, 10, "", ""),
            new Member("maggot", "vermin", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("corpse_fly", "vermin", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("corpse_fish", "vermin", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("baby_spider", "vermin", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("diamond_termite", "vermin", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),
            new Member("glutton_fish", "vermin", SHIELD, 2, 12.0D, 7.5D, 0.22D, 2, 18, "", ""),
            new Member("thornshell_crab", "vermin", SHIELD, 2, 12.0D, 7.5D, 0.22D, 2, 18, "", ""),
            new Member("bloody_gadfly", "vermin", ARTILLERY, 2, 14.0D, 4.5D, 0.26D, 2, 14, "", ""),

            new Member("fallen_chaos_knight", "chaos", HEAD, 3, 194.0D, 14.0D, 0.26D, 1, 4, "minecraft:golden_apple", "minecraft:ender_pearl"),
            new Member("missioner", "chaos", HERALD, 3, 36.0D, 8.0D, 0.28D, 1, 10, "", ""),
            new Member("missionary_raider", "chaos", SWARM, 3, 13.0D, 4.0D, 0.34D, 3, 40, "", ""),
            new Member("phantom_creeper", "chaos", SWARM, 3, 13.0D, 4.0D, 0.34D, 3, 40, "", ""),
            new Member("scarlet_persecutor", "chaos", SHIELD, 3, 28.0D, 10.0D, 0.22D, 2, 18, "", ""),
            new Member("lifestealer", "chaos", SHIELD, 3, 28.0D, 10.0D, 0.22D, 2, 18, "", ""),
            new Member("lifestealer_true_form", "chaos", HEALER, 3, 56.0D, 4.0D, 0.24D, 1, 8, "", ""),
            new Member("nightmare_stalker", "chaos", ARTILLERY, 3, 31.0D, 6.0D, 0.26D, 2, 14, "", ""),
            new Member("felsteed", "chaos", MOUNT, 3, 28.0D, 6.0D, 0.40D, 1, 8, "", ""));

    /**
     * What each court is drawn with.
     *
     * <p>Per court rather than per creature, for the reason
     * {@link KindredRules#SHAPES} gives: a court that looked like eight
     * unrelated things would not read as a court, and storing it once means no
     * rule is needed to hold its members to one look.
     */
    public static final Map<String, String> SHAPES = Map.of(
            "bonescaller", BeastRules.WIGHT,
            "pumpkin", BeastRules.BRUTE,
            "krampus", BeastRules.BRUTE,
            "zombie", BeastRules.BRUTE,
            "spirit", BeastRules.WIGHT,
            "hound", BeastRules.PROWLER,
            "vermin", BeastRules.PROWLER,
            "chaos", BeastRules.WIGHT);

    /** The shape one of these is drawn with, or the empty string. */
    public static String shapeOf(Member one) {
        String found = SHAPES.get(one.court());
        return found == null ? "" : found;
    }

    /** The creature of this name, or null. */
    public static Member member(String id) {
        for (Member one : MEMBERS) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** Everything in the given court, out of the given roster. */
    public static List<Member> court(String court, List<Member> roster) {
        List<Member> found = new ArrayList<>();
        for (Member one : roster) {
            if (one.court().equals(court)) {
                found.add(one);
            }
        }
        return List.copyOf(found);
    }

    /** Every court named by the given roster, in the order they appear. */
    public static List<String> courts(List<Member> roster) {
        Set<String> found = new LinkedHashSet<>();
        for (Member one : roster) {
            found.add(one.court());
        }
        return List.copyOf(found);
    }

    /** The head of a court, or null if it has none. */
    public static Member headOf(String court, List<Member> roster) {
        for (Member one : court(court, roster)) {
            if (one.head()) {
                return one;
            }
        }
        return null;
    }

    /**
     * What everything but the head is worth with every one of it standing.
     *
     * <p>Takes the roster in rather than reading {@link #MEMBERS}, so a control
     * can hand it a court built to be caught.
     */
    public static double courtStanding(String court, List<Member> roster) {
        double total = 0.0D;
        for (Member one : court(court, roster)) {
            if (!one.head()) {
                total += one.standing();
            }
        }
        return total;
    }

    /** The hardest beast standing on the same rung, or null. */
    public static BeastRules.Beast beside(Member one, List<BeastRules.Beast> beasts) {
        BeastRules.Beast hardest = null;
        for (BeastRules.Beast beast : beasts) {
            if (beast.tier() != one.tier()) {
                continue;
            }
            if (hardest == null || beast.threat() > hardest.threat()) {
                hardest = beast;
            }
        }
        return hardest;
    }

    /** What one of these leaves behind. */
    public static final List<String> LEAVINGS = List.of(
            "minecraft:bone", "minecraft:rotten_flesh", "minecraft:gunpowder",
            "minecraft:string", "minecraft:pumpkin_seeds", "minecraft:soul_sand",
            "minecraft:iron_nugget", "minecraft:coal", "minecraft:leather",
            "minecraft:redstone");

    /** What this one leaves behind, by where it sits in the roster. */
    public static String leaves(Member one) {
        int at = MEMBERS.indexOf(one);
        return at < 0 ? "" : LEAVINGS.get(at % LEAVINGS.size());
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the courts. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInNames(MEMBERS));
        bad.addAll(faultsInHeads(MEMBERS));
        bad.addAll(faultsInRoles(MEMBERS, ROLES));
        bad.addAll(faultsInGaps(MEMBERS, ROLES));
        bad.addAll(faultsInCrowding(MEMBERS));
        bad.addAll(faultsInRank(MEMBERS));
        bad.addAll(faultsInCourt(MEMBERS, COURT_SHARE));
        bad.addAll(faultsInStature(MEMBERS, BeastRules.BEASTS, BOSS_OVER_BEAST));
        bad.addAll(faultsInLadder(MEMBERS, BeastRules.BEASTS));
        bad.addAll(faultsInTiers(MEMBERS));
        bad.addAll(faultsInPhases(MEMBERS));
        bad.addAll(faultsInRarity(MEMBERS, LEAST_WEIGHT));
        bad.addAll(faultsInShapes(MEMBERS, MythRules.forms()));
        bad.addAll(faultsInLeavings(MEMBERS));
        return List.copyOf(bad);
    }

    /** No two creatures written under one name. */
    public static List<String> faultsInNames(List<Member> roster) {
        List<String> bad = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Member one : roster) {
            if (!seen.add(one.id())) {
                bad.add("two creatures are called " + one.id() + ", and whichever "
                        + "the game registers second is a row nothing ever spawns");
            }
        }
        return bad;
    }

    /**
     * Exactly one of every court is the one it is around.
     *
     * <p>Two independent guards: a court with no head and a court with two are
     * different mistakes, and a control that makes one has to be able to
     * reach it.
     */
    public static List<String> faultsInHeads(List<Member> roster) {
        List<String> bad = new ArrayList<>();
        Map<String, Integer> heads = new LinkedHashMap<>();
        for (Member one : roster) {
            heads.merge(one.court(), one.head() ? 1 : 0, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : heads.entrySet()) {
            if (entry.getValue() == 0) {
                bad.add("the " + entry.getKey() + " court has no head, so nothing "
                        + "in it is the fight and every one of its members is "
                        + "held under nothing");
            }
        }
        for (Map.Entry<String, Integer> entry : heads.entrySet()) {
            if (entry.getValue() > 1) {
                bad.add("the " + entry.getKey() + " court has " + entry.getValue()
                        + " heads, so it is two fights sharing a name and the "
                        + "budget that holds a court under its head is spent twice");
            }
        }
        return bad;
    }

    /** Every creature is one of the things a court has in it. */
    public static List<String> faultsInRoles(List<Member> roster, List<String> roles) {
        List<String> bad = new ArrayList<>();
        for (Member one : roster) {
            if (!roles.contains(one.role())) {
                bad.add(one.id() + " is a " + one.role() + " and a court has no such "
                        + "thing in it, so nothing decides what it does");
            }
        }
        return bad;
    }

    /** Every role is something in the roster, so none is a word nothing uses. */
    public static List<String> faultsInGaps(List<Member> roster, List<String> roles) {
        List<String> bad = new ArrayList<>();
        for (String role : roles) {
            boolean filled = false;
            for (Member one : roster) {
                if (one.role().equals(role)) {
                    filled = true;
                }
            }
            if (!filled) {
                bad.add("no court has a " + role + " in it, so it is a branch "
                        + "written, compiled and never reached by anything a "
                        + "player meets");
            }
        }
        return bad;
    }

    /**
     * The two roles a court may have only one of.
     *
     * <p>Two healers is a head that gains health faster than a player takes it
     * off, which is not a hard fight, it is an unwinnable one that looks like a
     * hard fight for about four minutes. Two mounts is a head in two places.
     */
    public static List<String> faultsInCrowding(List<Member> roster) {
        List<String> bad = new ArrayList<>();
        for (String role : List.of(HEALER, MOUNT)) {
            for (String court : courts(roster)) {
                int found = 0;
                for (Member one : court(court, roster)) {
                    if (one.role().equals(role)) {
                        found += one.most();
                    }
                }
                if (found > 1) {
                    bad.add("the " + court + " court can have " + found + " of its "
                            + role + " standing at once, and one is the most any "
                            + "court may have — two of them is a fight whose "
                            + "difficulty is a number the player cannot affect");
                }
            }
        }
        return bad;
    }

    /** Nothing in a court is worth more than the head it stands behind. */
    public static List<String> faultsInRank(List<Member> roster) {
        List<String> bad = new ArrayList<>();
        for (String court : courts(roster)) {
            Member head = headOf(court, roster);
            if (head == null) {
                continue;
            }
            for (Member one : court(court, roster)) {
                if (!one.head() && one.threat() >= head.threat()) {
                    bad.add(one.id() + " is worth " + Math.round(one.threat())
                            + " against its own head's " + Math.round(head.threat())
                            + ", so the thing the court is named after is not the "
                            + "hardest thing in it");
                }
            }
        }
        return bad;
    }

    /**
     * A court is support, not the fight.
     *
     * <p>The rule this file exists for. Takes the share in rather than reading
     * {@link #COURT_SHARE}, so a control can widen it and be caught by this
     * rule rather than by arithmetic that re-solved itself against the broken
     * number.
     */
    public static List<String> faultsInCourt(List<Member> roster, double share) {
        List<String> bad = new ArrayList<>();
        for (String court : courts(roster)) {
            Member head = headOf(court, roster);
            if (head == null) {
                continue;
            }
            double standing = courtStanding(court, roster);
            if (standing > head.threat() * share) {
                bad.add("everything the " + court + " court can have standing at "
                        + "once is worth " + Math.round(standing) + " against its "
                        + "head's " + Math.round(head.threat()) + ", so the head is "
                        + "the thing you ignore while you deal with the real fight");
            }
        }
        return bad;
    }

    /**
     * A head may be harder than a beast, by a stated amount and no more.
     *
     * <p>Two independent passes, neither returning early: folded into one, the
     * null guard's removal leaves the next line to throw rather than report.
     */
    public static List<String> faultsInStature(List<Member> roster,
                                               List<BeastRules.Beast> beasts,
                                               double over) {
        List<String> bad = new ArrayList<>();
        for (Member one : roster) {
            if (one.head() && beside(one, beasts) == null) {
                bad.add(one.id() + " heads a court on tier " + one.tier()
                        + " and there is no beast there, so nothing holds it to "
                        + "anything at all");
            }
        }
        for (Member one : roster) {
            BeastRules.Beast beside = beside(one, beasts);
            if (one.head() && beside != null
                    && one.threat() > beside.threat() * over) {
                bad.add(one.id() + " is worth " + Math.round(one.threat())
                        + " against " + beside.id() + "'s "
                        + Math.round(beside.threat()) + " times " + over
                        + ", which is past what this pack calls a boss and into "
                        + "what it calls a wall");
            }
        }
        return bad;
    }

    /** A whole court stands on one rung. */
    public static List<String> faultsInTiers(List<Member> roster) {
        List<String> bad = new ArrayList<>();
        for (String court : courts(roster)) {
            List<Member> members = court(court, roster);
            if (members.isEmpty()) {
                continue;
            }
            int rung = members.get(0).tier();
            for (Member one : members) {
                if (one.tier() != rung) {
                    bad.add(one.id() + " stands on tier " + one.tier() + " and the "
                            + "rest of the " + court + " court on tier " + rung
                            + ", so what holds the court under its head is "
                            + "measured against two different ladders");
                }
            }
        }
        return bad;
    }

    /**
     * A second phase changes something.
     *
     * <p>Three independent guards. A head with no answer at all, a second phase
     * answered by the same thing as the first, and something that is not a head
     * having phases — three different mistakes with one symptom, which is a
     * fight the player has already spent their answer on.
     */
    public static List<String> faultsInPhases(List<Member> roster) {
        List<String> bad = new ArrayList<>();
        for (Member one : roster) {
            if (one.head() && (one.first() == null || one.first().isEmpty())) {
                bad.add(one.id() + " heads a court and nothing answers it, which is "
                        + "a wall rather than a fight — and the difference is "
                        + "invisible from inside, because both feel like losing");
            }
        }
        for (Member one : roster) {
            if (one.twoPhased() && one.then().equals(one.first())) {
                bad.add(one.id() + " is answered by " + one.first() + " in both of "
                        + "its phases, so the second is the same fight with more "
                        + "health — and the player has already spent that answer");
            }
        }
        for (Member one : roster) {
            if (one.twoPhased() && !one.head()) {
                bad.add(one.id() + " has two phases and does not head a court, so "
                        + "something the court is meant to be able to clear away "
                        + "is a fight of its own");
            }
        }
        return bad;
    }

    /** Nothing so rare it is read about rather than met. */
    public static List<String> faultsInRarity(List<Member> roster, int leastWeight) {
        List<String> bad = new ArrayList<>();
        for (Member one : roster) {
            if (one.weight() < leastWeight) {
                bad.add(one.id() + " is chosen " + one.weight() + " times against a "
                        + "floor of " + leastWeight + ", so it is a creature most "
                        + "players will read about rather than meet");
            }
        }
        return bad;
    }

    /** Every court is drawn with a shape this module has. */
    public static List<String> faultsInShapes(List<Member> roster, List<String> forms) {
        List<String> bad = new ArrayList<>();
        for (Member one : roster) {
            if (shapeOf(one).isEmpty()) {
                bad.add("the " + one.court() + " court is drawn with nothing, so "
                        + one.id() + " is a creature this module cannot put on the "
                        + "screen");
            }
        }
        for (Member one : roster) {
            String shape = shapeOf(one);
            if (!shape.isEmpty() && !forms.contains(shape)) {
                bad.add("the " + one.court() + " court is drawn as a " + shape
                        + " and this module has no such shape, so " + one.id()
                        + " renders as a black-and-magenta cube for whoever meets "
                        + "it first");
            }
        }
        return bad;
    }

    /** Everything leaves something, and it is something that exists. */
    public static List<String> faultsInLeavings(List<Member> roster) {
        List<String> bad = new ArrayList<>();
        for (Member one : roster) {
            if (leaves(one).isEmpty()) {
                bad.add(one.id() + " leaves nothing, so killing it is a thing the "
                        + "player does for the experience alone");
            }
        }
        for (String left : LEAVINGS) {
            if (!left.startsWith("minecraft:") && !left.startsWith("elysium")) {
                bad.add(left + " is left behind and nothing in this pack or the "
                        + "game makes one of those");
            }
        }
        return bad;
    }

    /**
     * No boss out-fights the ordinary mobs of the rung above it.
     *
     * <p>{@link #faultsInStature} holds a head to {@link #BOSS_OVER_BEAST} times
     * the beast of its own rung, which is a number somebody chose. This is the
     * rule that makes that number load-bearing rather than a preference: a
     * tier-one boss worth more than a tier-two beast means a player who has not
     * geared for tier two can be beaten by something tier one is allowed to
     * contain, and the ladder the whole pack is built on stops predicting
     * anything.
     *
     * <p>Three times is, not by accident, the largest ceiling under which that
     * stays true: the hardest tier-one beast is worth 132 and the hardest
     * tier-two is worth 467, so a tier-one head may reach 396 and not 528.
     * Widening the ceiling without widening the ladder is caught here.
     */
    public static List<String> faultsInLadder(List<Member> roster,
                                              List<BeastRules.Beast> beasts) {
        List<String> bad = new ArrayList<>();
        for (Member one : roster) {
            if (!one.head()) {
                continue;
            }
            BeastRules.Beast above = null;
            for (BeastRules.Beast beast : beasts) {
                if (beast.tier() != one.tier() + 1) {
                    continue;
                }
                if (above == null || beast.threat() > above.threat()) {
                    above = beast;
                }
            }
            if (above != null && one.threat() > above.threat()) {
                bad.add(one.id() + " heads a tier-" + one.tier() + " court and is "
                        + "worth " + Math.round(one.threat()) + " against "
                        + above.id() + "'s " + Math.round(above.threat())
                        + " a whole rung higher, so a player who has not geared "
                        + "for that rung can be beaten by something this one is "
                        + "allowed to contain — and the ladder stops predicting "
                        + "anything");
            }
        }
        return bad;
    }
}
