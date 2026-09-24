package com.elysium.lib.stats;

import com.elysium.lib.registry.ElysiumRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A character stat: a number that persists, displays, and can be spent into.
 *
 * <h2>What the library owns, and what it does not</h2>
 *
 * This is the honest division, and it is worth stating plainly because it is
 * the whole shape of the extension point.
 *
 * <b>The library owns the number.</b> A registered stat is summed from race,
 * class, level, spent points and gear; it survives death; it appears on the
 * character sheet and in {@code /elysium stats}; free points can be assigned to
 * it; and its curve is available to anyone who asks.
 *
 * <b>Whoever registers a stat owns what it does.</b> There is no way to express
 * "reduce incoming damage" as data. The twelve canonical stats have their
 * effects applied by this library's own combat and tick handlers; an add-on's
 * stat gets its effect the same way, from that add-on's own event handler,
 * reading its value through {@link ElysiumStats#get}.
 *
 * Pretending otherwise would mean inventing a scripting language, and a stat
 * system that can only do what its author anticipated is worse than one that
 * hands you the number and gets out of the way.
 *
 * <h2>Shapes</h2>
 *
 * A stat is either {@link Shape#FLAT} — read straight, grows linearly forever —
 * or {@link Shape#CURVE}, read through {@code value / (value + halfway)} and
 * scaled to a ceiling it approaches without reaching. Every proportional
 * quantity in Elysium uses the second, so that gear can grant arbitrarily many
 * points without any percentage ever arriving at 100.
 */
public final class ElysiumStat {

    /** Every stat in the game. Add-ons register here during construction. */
    public static final ElysiumRegistry<ElysiumStat> REGISTRY = new ElysiumRegistry<>("stat");

    public enum Shape {
        /** Grows without limit, linearly. */
        FLAT,
        /** {@code value / (value + halfway) * ceiling} — always rising, never arriving. */
        CURVE
    }

    private final String translationKey;
    private final ChatFormatting colour;
    private final Shape shape;
    private final float halfway;
    private final float ceiling;

    private ElysiumStat(String translationKey, ChatFormatting colour,
                        Shape shape, float halfway, float ceiling) {
        this.translationKey = translationKey;
        this.colour = colour;
        this.shape = shape;
        this.halfway = halfway;
        this.ceiling = ceiling;
    }

    /**
     * Registers a stat that grows linearly and without limit.
     *
     * Use for anything the game already treats as an unbounded quantity —
     * damage, armour points, health.
     */
    public static ElysiumStat flat(ResourceLocation id, ChatFormatting colour) {
        return REGISTRY.register(id, new ElysiumStat(key(id), colour, Shape.FLAT, 0.0F, 0.0F));
    }

    /**
     * Registers a proportional stat.
     *
     * @param halfway the value at which the stat reaches half its ceiling —
     *                the only number worth tuning, and the one that decides
     *                whether the stat feels generous or grudging
     * @param ceiling the fraction it approaches and never reaches, 0..1
     */
    public static ElysiumStat curve(ResourceLocation id, ChatFormatting colour,
                                    float halfway, float ceiling) {
        if (halfway <= 0.0F) {
            throw new IllegalArgumentException(
                    "Stat '" + id + "': halfway must be positive, or the curve divides by zero "
                    + "at value 0 and every point of the stat is worth the same.");
        }
        if (ceiling <= 0.0F || ceiling > 1.0F) {
            throw new IllegalArgumentException(
                    "Stat '" + id + "': ceiling must be in (0, 1]. A proportional stat that can "
                    + "exceed 1.0 is a stat that can reflect more damage than it took, or dodge "
                    + "more often than it is hit.");
        }
        return REGISTRY.register(id, new ElysiumStat(key(id), colour, Shape.CURVE, halfway, ceiling));
    }

    private static String key(ResourceLocation id) {
        return "elysium.stat." + (id.getNamespace().equals("elysium")
                || id.getNamespace().equals("elysiumlib")
                ? id.getPath()
                : id.getNamespace() + "." + id.getPath());
    }

    // ------------------------------------------------------------------

    /**
     * How much a raw point total is actually worth: linear, plus a square.
     *
     * <pre>{@code   effective = stat + stat² / DIVISOR   }</pre>
     *
     * <h3>Why two terms</h3>
     *
     * A purely linear stat has the same slope at point one and at point five
     * hundred, so a level in the late game is worth exactly what a level in the
     * first hour was worth — which is the moment levelling stops being
     * interesting. The square term is the opposite failure on its own: it makes
     * the first few points worthless and the last few absurd.
     *
     * Together they are the shape that works. Below {@link #SCALING_DIVISOR}
     * the linear term dominates and the stat behaves exactly as it always did;
     * above it the square takes over and investment starts compounding. The
     * divisor <em>is</em> the handover point — at a stat of {@code DIVISOR} the
     * two terms are equal — so raising it delays the takeover and lowering it
     * brings it forward. That is the one number to tune here.
     *
     * <h3>Why this is not the soft cap</h3>
     *
     * It is the opposite of one: unbounded and accelerating. The soft cap is
     * the curve below, which this feeds. A proportional stat takes the
     * accelerating value and puts it through {@code v / (v + halfway)}, so the
     * compounding is real and still arrives nowhere — and because the values
     * going in are now in the thousands, the halfway points are too, which is
     * what makes the cap a distant thing rather than a wall.
     */
    // Was 512. Halved by the owner's ruling — "we want the player to scale
    // really hard" — so the square term arrives twice as early and a late-game
    // flat stat is roughly doubled. No ceiling either way.
    public static final float SCALING_DIVISOR = 256.0F;

    /** The linear-plus-square value a point total is really worth. */
    public static float effective(int points) {
        if (points <= 0) {
            return 0.0F;
        }
        float raw = points;
        return raw + raw * raw / SCALING_DIVISOR;
    }

    /**
     * This stat's value as a proportion, for a raw point total.
     *
     * The point total is passed through {@link #effective} first, so every
     * caller — this library's handlers, the character sheet, an add-on reading
     * its own stat — sees the same scaling, and there is exactly one place the
     * two-term function is applied.
     *
     * Flat stats return that value straight: the caller decides what a point is
     * worth. Curved stats return a fraction below {@link #getCeiling()}.
     */
    public float proportionOf(int points) {
        return curveOf(effective(points));
    }

    /**
     * The curve itself, for a value that has already been scaled.
     *
     * Separated so the halfway point can be checked against the curve rather
     * than against a point total — {@code proportionOf(halfway)} has not meant
     * "half the ceiling" since the square term arrived, because the number
     * reaching the curve is no longer the number that was passed in.
     */
    public float curveOf(float value) {
        if (shape == Shape.FLAT) {
            return value;
        }
        if (!(value > 0.0F)) {
            return 0.0F;
        }
        // The one rule, ElysiumShares.share, so a curve stat cannot round to
        // 100% in a float either. Elysium's own stats pass a ceiling of 1
        // (ElysiumStats.UNCAPPED); the parameter stays for add-ons.
        return ElysiumShares.share(value, halfway) * ceiling;
    }

    public Shape getShape() {
        return shape;
    }

    public float getHalfway() {
        return halfway;
    }

    public float getCeiling() {
        return ceiling;
    }

    public ChatFormatting getColour() {
        return colour;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey).withStyle(colour);
    }

    public Component getDescription() {
        return Component.translatable(translationKey + ".desc").withStyle(ChatFormatting.DARK_GRAY);
    }

    public ResourceLocation getId() {
        return REGISTRY.idOf(this);
    }

    /** The id as a string, which is how a stat is stored on a player. */
    public String getSerialisedName() {
        ResourceLocation id = getId();
        return id == null ? "" : id.toString();
    }

    @Override
    public String toString() {
        return getSerialisedName();
    }
}
