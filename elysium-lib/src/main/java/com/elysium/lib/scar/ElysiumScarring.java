package com.elysium.lib.scar;

/**
 * Whether a blow is heavy enough to mark the world, and what it may mark.
 *
 * <h2>The one thing this feature must not become</h2>
 *
 * A grief machine. "Attacks damage terrain" is two words away from "a skeleton
 * eventually deletes your house", and every mod that has shipped it carelessly
 * has shipped that. So the rule is written here, on its own, where it can be
 * executed against every hardness and every damage value the game can produce —
 * and it is written to say <em>no</em> by default.
 *
 * <h2>Why it is tied to the telegraph</h2>
 *
 * Only a blow that was announced can scar anything. That is not a balance
 * decision, it is a readability one: {@code ElysiumTelegraph} already promises
 * that nothing hits hard without warning, so a player who sees the wind-up has
 * been told the wall might not survive either. A blow that could quietly take a
 * block out of the floor would be the one thing in the combat system with no
 * warning at all.
 *
 * It also bounds the feature for free. Announced blows are rare, they are the
 * heaviest things in the game, and they already have a cooldown.
 *
 * <h2>Two gates, and the tag is the first one</h2>
 *
 * A block is breakable only if a datapack has <em>named</em> it breakable, and
 * only then is its hardness considered. Both, not either.
 *
 * The tag first is the important half. Hardness alone is a rule that
 * automatically includes every soft block any mod ever adds, which is how a
 * feature like this ends up eating somebody's modded crop field the week they
 * install something unrelated. Requiring the tag means the answer for anything
 * nobody has thought about is no, and a pack that wants more says so.
 *
 * Hardness then narrows it, so that a pack which tags a whole category cannot
 * accidentally include the one block in it that holds a roof up.
 *
 * <h2>No Minecraft here</h2>
 *
 * Numbers in, a yes or a no out.
 */
public final class ElysiumScarring {

    private ElysiumScarring() {
    }

    /**
     * The hardest thing a blow can break, in hardness.
     *
     * 0.6 is dirt, sand, gravel, leaves, glass, wool, snow, crops, most plants.
     * It is below planks (2.0), below stone (1.5), and below every block a
     * player builds with on purpose. That is the line: cover that grew there
     * can be blown apart, and cover somebody built stays up.
     */
    public static final float MAX_HARDNESS = 0.6F;

    /**
     * Blocks that cannot be broken at any hardness.
     *
     * Unbreakable blocks report a negative hardness, which would otherwise pass
     * the test above — bedrock is "softer" than dirt by that arithmetic. The
     * check is a range rather than a ceiling for exactly this reason, and it is
     * the sort of thing that is obvious once written down and catastrophic
     * until it is.
     */
    public static final float MIN_HARDNESS = 0.0F;

    /** How far from the impact a blow will look for something to break. */
    public static final int RADIUS = 1;

    /** How many blocks one blow may ever take. */
    public static final int MOST_PER_BLOW = 1;

    /**
     * How likely an announced blow is to scar anything at all.
     *
     * Well under half. Cover that reliably disappeared would be cover nobody
     * bothers with; cover that sometimes does is cover you have to think about,
     * which is the point. It is also the difference between a fight leaving
     * marks and a fight levelling the ground.
     */
    public static final float CHANCE = 0.25F;

    /**
     * Whether a blow of this size is heavy enough to mark anything.
     *
     * The same threshold the telegraph uses, deliberately: the set of blows
     * that scar is exactly the set of blows a player was warned about, and
     * reading it off the same constant is what keeps that true when somebody
     * retunes one of them.
     */
    public static boolean heavyEnough(float damage) {
        return com.elysium.lib.telegraph.ElysiumTelegraph.isDangerous(damage);
    }

    /**
     * Whether a block of this hardness may be broken.
     *
     * @param hardness the block's destroy time; negative means unbreakable
     * @param fragile  whether a datapack has named it breakable cover. Without
     *                 this nothing breaks, whatever its hardness — see the note
     *                 above on why the tag is the first gate and not the
     *                 override.
     */
    public static boolean breakable(float hardness, boolean fragile) {
        if (!(hardness >= MIN_HARDNESS)) {
            return false;               // unbreakable, or NaN
        }
        if (hardness > MAX_HARDNESS) {
            return false;
        }
        return fragile;
    }
}
