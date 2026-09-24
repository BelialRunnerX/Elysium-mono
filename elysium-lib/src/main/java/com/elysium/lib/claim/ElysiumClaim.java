package com.elysium.lib.claim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * A claim: a Registry Beacon, the ground it speaks for, and the file the Empire
 * opens because of it.
 *
 * <h2>What the specification asks for</h2>
 *
 * Fourth Edition §14.2 gives claiming one row in the table of things that raise
 * Imperial attention, and the row is unlike every other row in it:
 *
 * <pre>
 *   ACTION   FAVOR   SUSPICION          NOTES
 *   Claim    -       permanent floor    territorial structure
 * </pre>
 *
 * Everything else on that table adds points that then decay. A claim does not
 * add points at all — it puts a <b>floor</b> under them. §8.4 says why in one
 * sentence: <blockquote>Claiming is still a permanent Suspicion floor.</blockquote>
 * You cannot be quietly forgotten while you are standing on something with your
 * name on it.
 *
 * §A.2 gives the number:
 *
 * <pre>
 *   Claim floor   min(70, 8 + 2 x sqrt(totalStructureVoxels/100))
 * </pre>
 *
 * and §8.4 explains the square root rather than leaving it as a curve someone
 * picked: it <blockquote>makes the first meaningful base politically expensive
 * while preventing a huge fortress from being punished linearly forever.</blockquote>
 * The first hundred blocks cost you ten points; the ninety-thousandth costs
 * almost nothing. The cap at seventy matters too — it is inside the Hunted
 * band's floor of 75, so <em>building alone can never make you Hunted</em>. You
 * have to have done something.
 *
 * <h2>What counts as structure, and why only placements</h2>
 *
 * The count is <b>placements inside the claim, and nothing else</b>. It never
 * falls when blocks are broken, and it is not a survey of what is standing.
 *
 * The alternative — net edits, plus for a place and minus for a break — was
 * tried on paper and is wrong twice over. It makes hollowing out a mountain
 * inside your own claim <em>lower</em> your Imperial file, when excavation is
 * the one activity §14.2 elsewhere taxes most heavily. And it makes demolishing
 * a wall a way to be forgotten, which is the opposite of what a file is.
 *
 * A count that only rises is also the only one that matches the word
 * "permanent" in the specification's own row. The Empire records that you
 * built; it does not un-record it when you knock it down.
 *
 * <h2>The file opens when the beacon is planted</h2>
 *
 * A claim counts what is built <em>after</em> it exists, not a retroactive
 * survey of what was already there. That is partly because surveying a
 * hundred-block radius for player-placed blocks is not a thing that can be done
 * cheaply or even correctly — nothing in a Minecraft world remembers who placed
 * it — and mostly because it is the better reading of §B.2's own description of
 * the block: a Registry Beacon <blockquote>claims planet and anchors Imperial
 * file.</blockquote> The file starts when it is opened.
 *
 * A player who builds a fortress and then plants a beacon therefore starts at
 * the minimum floor and climbs as they extend it. That is not an exploit to
 * close: the beacon is what grants the claim's <em>benefits</em>, and a player
 * who never plants one has no claim, which §2 already treats as an ordinary way
 * to play — most worlds are expedition targets.
 */
public record ElysiumClaim(UUID owner, String ownerName, BlockPos anchor,
                           int radius, int structures, int ascension) {

    public static final Codec<ElysiumClaim> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("owner").forGetter(ElysiumClaim::owner),
                    Codec.STRING.fieldOf("owner_name").forGetter(ElysiumClaim::ownerName),
                    BlockPos.CODEC.fieldOf("anchor").forGetter(ElysiumClaim::anchor),
                    Codec.INT.fieldOf("radius").forGetter(ElysiumClaim::radius),
                    Codec.INT.fieldOf("structures").forGetter(ElysiumClaim::structures),
                    // Optional, defaulting to zero, because every claim written
                    // before beacons could be ascended has no such field. A
                    // required field here would make those saves fail to parse
                    // and silently drop every existing claim in the world —
                    // which is the one bug in this file nobody would recover
                    // from.
                    Codec.INT.optionalFieldOf("ascension", 0)
                            .forGetter(ElysiumClaim::ascension)
            ).apply(instance, ElysiumClaim::new));

    /**
     * A claim at its starting size, with no ascension.
     *
     * The five-argument shape this record used to have. Kept as a factory
     * rather than a second constructor so that every call site is a place
     * somebody decided the ascension is zero, rather than a place the compiler
     * filled it in.
     */
    public static ElysiumClaim staked(UUID owner, String ownerName, BlockPos anchor,
                                      int radius, int structures) {
        return new ElysiumClaim(owner, ownerName, anchor, radius, structures, 0);
    }

    /**
     * How far a claim reaches, in blocks.
     *
     * Measured horizontally and unbounded vertically — a cylinder, not a
     * sphere. Territory is a footprint. A sphere centred on a beacon at sea
     * level would leave a mine two hundred blocks below it outside the owner's
     * own claim, which no player would read as anything but a bug.
     */
    public static final int DEFAULT_RADIUS = 64;

    /**
     * What one ascension tier adds to the radius.
     *
     * The same sixty-four the claim starts at, so a tier is always worth
     * exactly one more claim's worth of ground and a player can count tiers by
     * looking at the number. The reward is larger than it reads: radius is
     * linear and area is not, so the second tier triples the ground the first
     * one covered.
     */
    public static final int RADIUS_PER_ASCENSION = 64;

    /**
     * What one ascension tier adds to the Suspicion floor.
     *
     * A bigger claim is a louder statement, and §14.2's floor is the Empire
     * noticing. Without this, ascension would be the one consequential act in
     * the mod that costs materials and nothing else.
     *
     * Still clamped by {@link #MAX_FLOOR}, so no amount of ascending can push a
     * player into the Hunted band — see that constant's note, where the gap is
     * deliberate.
     */
    public static final int FLOOR_PER_ASCENSION = 4;

    /** The radius a claim of this ascension reaches. */
    public static int radiusFor(int ascension) {
        int tiers = Math.max(0, ascension);
        // Long arithmetic and a clamp: a hand-edited save with a huge ascension
        // would otherwise overflow into a negative radius, and a negative
        // radius contains nothing, so a player's whole base would silently
        // stop being theirs.
        long radius = (long) DEFAULT_RADIUS + (long) RADIUS_PER_ASCENSION * tiers;
        return (int) Math.min(Integer.MAX_VALUE / 2, radius);
    }

    /** §A.2's floor of the floor: what an empty claim is already worth. */
    public static final int BASE_FLOOR = 8;

    /**
     * §A.2's cap.
     *
     * Seventy is below the Hunted band's own floor of seventy-five, and that
     * gap is load-bearing: it means no amount of building can push a player
     * into the band that sends five-wave sieges ending in a Praetor. Buildings
     * make you <em>Marked</em>. Being Hunted is something you do.
     */
    public static final int MAX_FLOOR = 70;

    /** §A.2: min(70, 8 + 2 x sqrt(structures / 100)). */
    public static int floorFor(int structures) {
        if (structures <= 0) {
            return BASE_FLOOR;
        }
        double scaled = BASE_FLOOR + 2.0D * Math.sqrt(structures / 100.0D);
        return (int) Math.min(MAX_FLOOR, Math.floor(scaled));
    }

    /** The Suspicion this claim holds its owner at. */
    public int floor() {
        int built = floorFor(structures);
        long withAscension = (long) built
                + (long) FLOOR_PER_ASCENSION * Math.max(0, ascension);
        return (int) Math.min(MAX_FLOOR, withAscension);
    }

    /** The same claim, one tier larger. */
    public ElysiumClaim ascended() {
        int next = ascension + 1;
        return new ElysiumClaim(owner, ownerName, anchor, radiusFor(next),
                structures, next);
    }

    /**
     * Whether a position is inside this claim.
     *
     * Squared distances, because a claim is tested against every block a player
     * places and a square root there would be a million calls a session for a
     * number that is only ever compared.
     */
    public boolean contains(BlockPos pos) {
        long dx = (long) pos.getX() - anchor.getX();
        long dz = (long) pos.getZ() - anchor.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /** The same claim, having grown. */
    public ElysiumClaim grown(int by) {
        if (by <= 0) {
            return this;
        }
        // Clamped rather than allowed to wrap. A claim cannot realistically
        // reach two billion placements, but "cannot realistically" is how
        // negative structure counts get into save files.
        long total = (long) structures + by;
        return new ElysiumClaim(owner, ownerName, anchor, radius,
                (int) Math.min(Integer.MAX_VALUE, total), ascension);
    }
}
