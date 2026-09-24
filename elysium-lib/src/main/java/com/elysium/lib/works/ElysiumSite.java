package com.elysium.lib.works;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One unregistered works: a {@link ElysiumConspicuity#CELL}-sided square of
 * the world that somebody has been building industry in.
 *
 * @param cellX    the site's column of cells
 * @param cellZ    the site's row of cells
 * @param weight   how loud it was at {@code lastTick}
 * @param lastTick the game time {@code weight} was measured at
 * @param notices  how many times it has been noticed since it last went quiet
 */
public record ElysiumSite(int cellX, int cellZ, int weight, long lastTick,
                          int notices) {

    public static final Codec<ElysiumSite> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("x").forGetter(ElysiumSite::cellX),
                    Codec.INT.fieldOf("z").forGetter(ElysiumSite::cellZ),
                    Codec.INT.fieldOf("weight").forGetter(ElysiumSite::weight),
                    Codec.LONG.fieldOf("last").forGetter(ElysiumSite::lastTick),
                    Codec.INT.optionalFieldOf("notices", 0)
                            .forGetter(ElysiumSite::notices)
            ).apply(instance, ElysiumSite::new));

    /** A cell with nothing built in it yet. */
    public static ElysiumSite quiet(int cellX, int cellZ, long now) {
        return new ElysiumSite(cellX, cellZ, 0, now, 0);
    }

    /**
     * This site as it stands now, after the silence since it was last touched.
     *
     * A site that has decayed all the way to nothing has gone quiet, and its
     * notice count goes with it: the escalation is for a works the Empire is
     * still watching, not a grudge held against a field that used to have a
     * furnace in it.
     */
    public ElysiumSite at(long now) {
        // The subtraction can overflow too, for a timestamp from a corrupted
        // or foreign save. Overflow here means "a very long time ago", so it
        // saturates rather than wrapping into "no time at all" — which would
        // leave the site loud forever.
        long elapsed = now - lastTick;
        if (now > lastTick && elapsed < 0) {
            elapsed = Long.MAX_VALUE;
        }
        int left = ElysiumConspicuity.decayed(weight, elapsed);
        return new ElysiumSite(cellX, cellZ, left, Math.max(lastTick, now),
                left == 0 ? 0 : notices);
    }

    /**
     * What one placement did to a site.
     *
     * @param site      the site after the placement, and after any notice
     * @param suspicion what being noticed costs the builder; zero if not noticed
     */
    public record Placed(ElysiumSite site, int suspicion) {
        public boolean noticed() {
            return suspicion > 0;
        }
    }

    /**
     * This site after a placement of {@code weight} at {@code now}.
     *
     * The whole rule, here rather than in the level data, so that a harness
     * can execute it without a world. At most one notice per placement,
     * whatever the weight: a single block is a single moment, and a placement
     * heavy enough to cross the threshold twice is still one survey.
     */
    public Placed placed(int weight, long now) {
        ElysiumSite site = at(now);
        if (weight <= 0) {
            return new Placed(site, 0);
        }
        int grown = ElysiumConspicuity.added(site.weight, weight);
        int notices = site.notices;
        int cost = 0;
        if (ElysiumConspicuity.noticed(grown)) {
            cost = ElysiumConspicuity.suspicionFor(notices);
            notices++;
            grown = ElysiumConspicuity.RESIDUAL;
        }
        return new Placed(new ElysiumSite(cellX, cellZ, grown, site.lastTick,
                notices), cost);
    }

    /** The centre of this site, in block coordinates. */
    public int centreX() {
        return cellX * ElysiumConspicuity.CELL + ElysiumConspicuity.CELL / 2;
    }

    public int centreZ() {
        return cellZ * ElysiumConspicuity.CELL + ElysiumConspicuity.CELL / 2;
    }

    /** The key a site is stored under. Packed so the map needs no key object per lookup. */
    public static long key(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    public long key() {
        return key(cellX, cellZ);
    }
}
