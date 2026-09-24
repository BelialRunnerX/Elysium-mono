package com.elysium.armory.property;

import java.util.List;
import java.util.Set;

/**
 * What a material can be made to be worth: mixing, grading and charging.
 *
 * <h2>Synergy — why mixing pays</h2>
 *
 * A part made of several materials averages their properties, which means
 * mixing has always been a way to get a middle number. Synergy is the reason
 * to do it on purpose: metals that have something in common work together, and
 * the more of them there are the more that is worth.
 *
 * <pre>
 *   synergy = 1 + RATE · (distinct − 1) · harmony
 *   harmony = shared categories / all categories between them
 * </pre>
 *
 * Three materials that are all metals gain the most; three with nothing in
 * common gain nothing at all and are simply the average they always were. That
 * is the whole rule, and it is stated as a number a player can read rather
 * than a curve they have to trust.
 *
 * <p>Synergy is the Alloy Forge's reason to exist. A mixed part made in the
 * crafting grid is the average; the same part forged is the average times its
 * synergy. Nothing is taken away from the grid — something is added to the
 * bench.
 *
 * <h2>Grades — why the same metal is not always the same part</h2>
 *
 * A grade is how well the material was worked. It runs from Crude, which is
 * worse than plain, up through five grades that are better, and it multiplies
 * everything the part contributes. The Material Grader raises it; a bad roll
 * lowers it, which is what makes grading a decision.
 *
 * <h2>Starcharged</h2>
 *
 * A separate mark from the grade, and a bigger one: a part left under the open
 * sky at night takes the charge, once. It stacks with a grade because they are
 * different things — how well it was made, and what it was left out in.
 *
 * <p>Pure: the JDK and nothing else.
 */
public final class ArmoryMaterialScience {

    private ArmoryMaterialScience() {
    }

    // ------------------------------------------------------------------
    // Synergy
    // ------------------------------------------------------------------

    /** How much each material past the first is worth, at perfect harmony. */
    public static final double SYNERGY_RATE = 0.10D;

    /**
     * The synergy of a set of materials.
     *
     * @param categories one set of category names per material, in any order
     * @return a multiplier at or above 1
     */
    public static double synergy(List<Set<String>> categories) {
        int distinct = categories.size();
        if (distinct <= 1) {
            return 1.0D;
        }
        java.util.Set<String> union = new java.util.LinkedHashSet<>();
        java.util.Set<String> shared = null;
        for (Set<String> one : categories) {
            union.addAll(one);
            if (shared == null) {
                shared = new java.util.LinkedHashSet<>(one);
            } else {
                shared.retainAll(one);
            }
        }
        if (union.isEmpty()) {
            return 1.0D;                    // nothing said about any of them
        }
        double harmony = (double) shared.size() / union.size();
        return 1.0D + SYNERGY_RATE * (distinct - 1) * harmony;
    }

    // ------------------------------------------------------------------
    // Grades
    // ------------------------------------------------------------------

    /** Worse than plain: what a bad grading leaves behind. */
    public static final int CRUDE = -1;

    /** Plain: what a part is when nobody has graded it. */
    public static final int UNGRADED = 0;

    /** The best grade a part can be worked to. */
    public static final int MAX_GRADE = 5;

    /** What each grade above plain is worth. */
    public static final double GRADE_STEP = 0.06D;

    /** What being Crude costs. */
    public static final double CRUDE_PENALTY = 0.10D;

    /** The untranslated name of a grade. */
    public static String gradeName(int grade) {
        return switch (clampGrade(grade)) {
            case CRUDE -> "Crude";
            case 1 -> "E";
            case 2 -> "D";
            case 3 -> "C";
            case 4 -> "B";
            case 5 -> "A";
            default -> "";
        };
    }

    /** A grade read from a save, kept inside the range the rules are written for. */
    public static int clampGrade(int grade) {
        return Math.max(CRUDE, Math.min(MAX_GRADE, grade));
    }

    /** What a grade multiplies the part's contribution by. */
    public static double gradeFactor(int grade) {
        int at = clampGrade(grade);
        if (at == CRUDE) {
            return 1.0D - CRUDE_PENALTY;
        }
        return 1.0D + GRADE_STEP * at;
    }

    /**
     * What grading a part at this grade produces.
     *
     * A grade up on a good roll, and Crude on a bad one — but only from
     * ungraded. A part that has already been graded well is not made Crude by
     * trying again; it simply does not improve, because losing hours of work
     * to one roll is a punishment rather than a risk.
     *
     * @param roll anywhere in [0, 1)
     */
    public static int graded(int grade, double roll) {
        int at = clampGrade(grade);
        if (at >= MAX_GRADE) {
            return MAX_GRADE;
        }
        if (roll < failureChance(at)) {
            return at == UNGRADED ? CRUDE : at;
        }
        return at == CRUDE ? UNGRADED : at + 1;
    }

    /**
     * How likely grading is to fail, which rises with the grade already there.
     *
     * A share, so there is no grade that cannot be attempted and none that is
     * free. The first attempt can fail too — that is what makes Crude a real
     * risk rather than a state nothing produces — and it is the only attempt
     * that can leave a part worse than it started.
     */
    public static double failureChance(int grade) {
        double at = Math.max(0, clampGrade(grade)) + 1.0D;
        return at / (at + 8.0D);
    }

    // ------------------------------------------------------------------
    // Starcharged
    // ------------------------------------------------------------------

    /** What a starcharged part multiplies its contribution by, on top of its grade. */
    public static final double STARCHARGE_FACTOR = 1.15D;

    /** How long a part must lie under the open sky, in ticks: one night. */
    public static final int CHARGE_TICKS = 20 * 60 * 5;

    /** Everything a part's marks come to, together. */
    public static double factor(int grade, boolean starcharged) {
        return gradeFactor(grade) * (starcharged ? STARCHARGE_FACTOR : 1.0D);
    }
}
