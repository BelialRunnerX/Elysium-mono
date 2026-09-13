package com.elysium.lib.imperial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * An inspection post: the Empire watching somebody dig.
 *
 * <h2>What the specification asks for</h2>
 *
 * Fourth Edition §14.5, among the expanded Imperial infrastructure:
 *
 * <blockquote>Inspection beacons that report nearby high-volume extraction
 * until disabled.</blockquote>
 *
 * and §27.1 gives it a row of its own, with the responses it should admit:
 *
 * <pre>
 *   ACTION       TRIGGER                                    PLAYER RESPONSE
 *   Inspection   registered trade, claim, station activity,  prepare records,
 *                suspicion band                             bribe/bargain,
 *                                                           hide goods,
 *                                                           cooperate, resist
 * </pre>
 *
 * Of those five responses this implements two: <b>cooperate</b> — do nothing,
 * pay the higher rate, and let it lapse — and <b>resist</b>, which is breaking
 * it and being seen to. Bargaining is the Writ of Amnesty and already exists;
 * records and hidden cargo need a cargo system this mod has no version of, and
 * a shallow stand-in for each would be three half-mechanics instead of one
 * whole one.
 *
 * <h2>The shape it completes</h2>
 *
 * A claim is the player's structure that the Empire attacks. This is the
 * Empire's structure that the player attacks — the same relationship, pointed
 * the other way, and the pair is what makes Suspicion something happening
 * <em>in a place</em> rather than a number attached to a person.
 *
 * <h2>Why it expires</h2>
 *
 * §14.5 says "until disabled", which read alone is forever. Forever is wrong
 * here for a reason that has nothing to do with the fiction: these are planted
 * automatically, near a player who is mining, and a player mines for hours.
 * Without a lifetime the world accumulates one every time the threshold is
 * crossed, none of them ever goes away, and the map fills with Imperial
 * furniture the player has no reason to walk back to.
 *
 * So a post lapses on its own, and "until disabled" means what it says for the
 * period it is watching. The choice it poses stays intact: pay the rate for
 * twenty minutes, or break it now and be seen doing it.
 */
public record ElysiumInspection(UUID watching, BlockPos anchor, long plantedTick,
                                long lapsesTick) {

    public static final Codec<ElysiumInspection> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("watching")
                            .forGetter(ElysiumInspection::watching),
                    BlockPos.CODEC.fieldOf("anchor")
                            .forGetter(ElysiumInspection::anchor),
                    Codec.LONG.fieldOf("planted")
                            .forGetter(ElysiumInspection::plantedTick),
                    Codec.LONG.fieldOf("lapses")
                            .forGetter(ElysiumInspection::lapsesTick)
            ).apply(instance, ElysiumInspection::new));

    /**
     * How far a post reports.
     *
     * Generous — wider than the claim radius is tight — because what it is
     * watching is a mine, and a mine is mostly underneath and off to one side
     * of wherever a beacon could be planted on the surface. A post that only
     * covered the shaft head would be dodged by walking forty blocks and
     * digging a second entrance, which is not the interesting response §27.1
     * lists.
     */
    public static final int RANGE = 48;

    /** How long a post watches before lapsing. Twenty minutes. */
    public static final int LIFETIME = 20 * 60 * 20;

    /**
     * What extraction costs while a post is reporting it.
     *
     * Doubled. §14.2 already prices ordinary ore at 2 and rich ore at 4, so
     * this makes them 4 and 8 — a rich vein mined under inspection is worth as
     * much Suspicion as killing an enforcer. That is the whole point of the
     * row: it does not forbid anything, it reprices it, and the player decides
     * whether the vein is worth the file.
     */
    public static final int MULTIPLIER = 2;

    public boolean covers(BlockPos pos) {
        long dx = (long) pos.getX() - anchor.getX();
        long dy = (long) pos.getY() - anchor.getY();
        long dz = (long) pos.getZ() - anchor.getZ();
        // Spherical, unlike a claim's cylinder. A claim is a footprint you own;
        // this is a sensor, and a sensor that reached the bottom of the world
        // would report a mine nobody could see it from.
        return dx * dx + dy * dy + dz * dz <= (long) RANGE * RANGE;
    }

    public boolean hasLapsed(long now) {
        return now >= lapsesTick;
    }

    /** Minutes left, for a readout. */
    public long minutesLeft(long now) {
        return Math.max(0L, (lapsesTick - now) / (20L * 60L));
    }
}
