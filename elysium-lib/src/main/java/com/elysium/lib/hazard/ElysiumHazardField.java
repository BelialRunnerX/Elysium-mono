package com.elysium.lib.hazard;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.Holder;

/**
 * How strong each hazard is, here.
 *
 * <h2>Read from the world, not stored in it</h2>
 *
 * Intensity is a function of a position and the moment, computed when asked.
 * Nothing is written into chunks, nothing is saved, and a world that was
 * generated before this existed is hazardous the instant it loads. The
 * alternative — a hazard field baked into chunk data — would have made this
 * feature a save-format change and made every pre-existing world safe forever.
 *
 * <h2>Why vanilla's own signals</h2>
 *
 * Biome temperature, depth, sky access and weather are all already there, all
 * already meaningful to players, and all already varied by whatever terrain
 * mods are installed. Inventing an Elysium hazard map over the top would mean a
 * desert that is not hot and a cave that is not deep, and would ignore every
 * biome the player actually has.
 *
 * So: deserts and the Nether burn, snow and high altitude freeze, deep stone
 * crushes, and the corrosive and radiological hazards — which have no vanilla
 * analogue — stay at zero until something in the world produces them. That last
 * pair is deliberate. A hazard with no source yet is not a bug: it is the slot
 * a later feature fills, and filling it is a content change rather than an
 * engine one.
 */
public final class ElysiumHazardField {

    private ElysiumHazardField() {
    }

    /** Below this Y, pressure begins. */
    public static final int PRESSURE_DEPTH = 0;

    /** Where pressure reaches full intensity. */
    public static final int PRESSURE_FLOOR = -56;

    /** Above this Y, the cold of altitude begins. */
    public static final int COLD_ALTITUDE = 140;

    /** Where altitude cold reaches full intensity. */
    public static final int COLD_CEILING = 256;

    /**
     * Above this Y, a scorching biome's sun begins to be a hazard.
     *
     * <h3>Why heat needs a reason now</h3>
     *
     * It did not have one. Any biome above 1.5 base temperature burned, at any
     * height, all day — which is every desert, badlands and savanna plateau in
     * the world, at the altitude players actually walk around at. The hazard
     * fired constantly, and a hazard that is always on is not a hazard, it is a
     * tax with a message attached.
     *
     * So heat is local now, and it has two sources: altitude and a fire. Below
     * this height, in the open, in a desert, at noon, there is no thermal
     * hazard at all — which is the common case, and the common case should be
     * ordinary.
     */
    public static final int THERMAL_ALTITUDE = 200;

    /** Where the sun alone reaches full intensity. The build limit. */
    public static final int THERMAL_CEILING = 320;

    /** Base temperature at or above which a biome counts as scorching. */
    public static final float SCORCHING = 1.5F;

    /** How far a fire is felt, in blocks. */
    public static final int HEAT_RADIUS = 8;

    /** ...and how far above and below. Heat rises; a lava lake two floors down
     * is not what is cooking you. */
    public static final int HEAT_HEIGHT = 4;

    /**
     * The lattice the scan walks.
     *
     * Every other block in each axis. A 17x9x17 box is 2601 reads, which is a
     * cost that only shows up on somebody else's server; every other block is
     * 405, and a lava lake large enough to matter cannot hide between the
     * samples.
     */
    public static final int HEAT_STEP = 2;

    /** How many sampled heat blocks make the hazard full strength. */
    public static final int HEAT_FOR_FULL = 12;

    /**
     * Blocks that are a fire even though they are not lava.
     *
     * A tag rather than a list, so a content mod's forge or reactor can be a
     * heat source without this class knowing it exists — which is the same
     * extension point the rooms use, for the same reason.
     */
    public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("elysiumlib", "heat_source"));

    /**
     * How intense one hazard is at one place, from 0 to 1.
     *
     * Total: every hazard answers for every position, and most answer zero.
     */
    public static float intensity(Level level, BlockPos pos, ElysiumHazard hazard) {
        if (level == null || hazard == null) {
            return 0.0F;
        }
        return Math.max(builtIn(level, pos, hazard), fromSources(level, pos, hazard));
    }

    private static float builtIn(Level level, BlockPos pos, ElysiumHazard hazard) {
        if (hazard == ElysiumHazards.THERMAL) {
            return thermal(level, pos);
        }
        if (hazard == ElysiumHazards.CRYOGENIC) {
            return cryogenic(level, pos);
        }
        if (hazard == ElysiumHazards.PRESSURE) {
            return pressure(pos);
        }
        // Corrosive and Radiological have no vanilla source. See the class note.
        return 0.0F;
    }

    // ------------------------------------------------------------------
    // Other sources: a world's atmosphere, a reactor leak, a spore bloom.
    // ------------------------------------------------------------------

    /**
     * Something outside the library that makes a hazard happen somewhere. The
     * space module's planet atmospheres are one of these. A source only says
     * how intense a hazard is; everything after that (wards, armour sets,
     * rooms, claims, resistance) is the pipeline every hazard goes through, so
     * a planet's heat is answered by exactly what answers a desert's.
     */
    @FunctionalInterface
    public interface Source {
        /** From 0 to 1. Anything else is read as the nearest end, and NaN as 0. */
        float intensity(Level level, BlockPos pos, ElysiumHazard hazard);
    }

    private static final com.elysium.lib.attribute.ElysiumContributorTable<Source> SOURCES =
            new com.elysium.lib.attribute.ElysiumContributorTable<>();
    private static final java.util.Set<String> BROKEN =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /** Register a source as {@code namespace:path}. Names are unique; see ElysiumContributorTable. */
    public static void registerSource(String name, Source source) {
        SOURCES.register(name, source);
    }

    /**
     * The worst any source says. The worst rather than the sum, the same rule
     * heat and cold use: two reasons a place is hot do not make it twice as
     * hot. A source that throws is logged once and ignored; a broken planet
     * must not stop every other hazard from ticking.
     */
    private static float fromSources(Level level, BlockPos pos, ElysiumHazard hazard) {
        float worst = 0.0F;
        for (java.util.Map.Entry<String, Source> entry : SOURCES.ordered()) {
            try {
                worst = Math.max(worst, unit(entry.getValue().intensity(level, pos, hazard)));
            } catch (RuntimeException | LinkageError e) {
                if (BROKEN.add(entry.getKey())) {
                    com.elysium.lib.ElysiumLib.LOGGER.error("hazard source {} failed: {}",
                            entry.getKey(), e.toString());
                }
            }
        }
        return worst;
    }

    /** A source's answer read into 0..1: NaN is nothing, and the ends hold. */
    static float unit(float value) {
        if (!(value > 0.0F)) {
            return 0.0F;
        }
        return value >= 1.0F ? 1.0F : value;
    }

    /**
     * Heat, from biome and from the sky.
     *
     * The sky term is what makes "travel at night" a real answer rather than
     * advice: a desert at noon is the full hazard and the same desert at
     * midnight is half of it. That is the specification's cheap counter, and it
     * has to be in the intensity rather than in the damage, or shelter and
     * nightfall would stack into nothing.
     */
    private static float thermal(Level level, BlockPos pos) {
        // The worse of the two, not the sum — the same rule cold uses. A
        // mountaintop over a lava lake is dangerous, not twice as dangerous,
        // and adding them would make the one place both apply the only place
        // the hazard is ever lethal.
        return Math.max(fromSun(level, pos), fromFire(level, pos));
    }

    /**
     * The sun, in a scorching biome, high enough up to be worth minding.
     *
     * All three conditions, and the altitude is the one that was missing. The
     * daylight term is what keeps "travel at night" a real answer rather than
     * advice, and it has to be in the intensity rather than in the damage, or
     * shelter and nightfall would stack into nothing.
     */
    private static float fromSun(Level level, BlockPos pos) {
        if (!level.isDay() || !level.canSeeSky(pos)) {
            return 0.0F;                 // a desert at night is not a hazard
        }
        Holder<Biome> biome = level.getBiomeManager().getBiome(pos);
        float temperature = biome.value().getBaseTemperature();
        return sunIntensity(temperature, true, pos.getY());
    }

    /**
     * The sun rule itself, with no world around it.
     *
     * Separated so it can be executed. It is the rule the owner asked to be
     * changed — heat used to fire in any hot biome at any height at any hour —
     * and a change to how often a hazard triggers is exactly the kind that is
     * only visible by playing, unless it is written as arithmetic.
     *
     * @param temperature the biome's base temperature
     * @param daylight    whether the sun is up and can see this position
     * @param y           how high up it is
     */
    public static float sunIntensity(float temperature, boolean daylight, int y) {
        if (temperature < SCORCHING || !daylight) {
            return 0.0F;
        }
        // 1.5 is a desert, 2.0 is the Nether's basalt deltas and the like.
        float fromBiome = Math.min(1.0F, (temperature - SCORCHING) / 0.5F);
        float fromAltitude = ramp(y, THERMAL_ALTITUDE, THERMAL_CEILING);
        // Multiplied rather than maxed: the biome decides whether the sun can
        // hurt you at all, the height decides how much. A temperate mountain is
        // not hot, and a desert at the altitude people walk around at is not a
        // hazard — which is the whole of the change.
        return Math.max(0.2F, fromBiome) * fromAltitude;
    }

    /** How much hazard a count of nearby burning blocks is worth. */
    public static float fireIntensity(int burning) {
        if (burning <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) burning / (float) HEAT_FOR_FULL);
    }

    /** How many positions one fire scan reads. Reported, and checked. */
    public static int fireSamples() {
        int across = (2 * HEAT_RADIUS) / HEAT_STEP + 1;
        int up = (2 * HEAT_HEIGHT) / HEAT_STEP + 1;
        return across * across * up;
    }

    /**
     * A fire nearby, which is the other half of what the owner asked for.
     *
     * Counted rather than measured to the nearest block. One lava source in a
     * cave is a twelfth of the hazard and a lake is all of it, which is both
     * the honest answer — heat is a quantity — and the one that keeps ordinary
     * mining survivable while making a player think twice about digging out a
     * lava lake in an ordinary shirt.
     *
     * <p>The result is cached for {@link ElysiumHeatCache#TTL} ticks. The scan
     * is 405 block reads and the damage tick asks twice a second per player;
     * without the cache that is a cost that appears on a full server and
     * nowhere else.
     */
    private static float fromFire(Level level, BlockPos pos) {
        return ElysiumHeatCache.at(level, pos);
    }

    /**
     * The scan itself. Called only by the cache.
     *
     * @return 0..1, from how many sampled blocks in reach are burning
     */
    static float measureFire(Level level, BlockPos pos) {
        int found = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -HEAT_HEIGHT; dy <= HEAT_HEIGHT; dy += HEAT_STEP) {
            for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx += HEAT_STEP) {
                for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz += HEAT_STEP) {
                    at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    // isLoaded, because a scan that reaches into an unloaded
                    // chunk generates one — at the edge of a player's view, once
                    // a second, forever.
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    var state = level.getBlockState(at);
                    if (state.getFluidState().is(FluidTags.LAVA)
                            || state.is(HEAT_SOURCES)) {
                        found++;
                        if (found >= HEAT_FOR_FULL) {
                            return 1.0F;       // no point counting a whole lake
                        }
                    }
                }
            }
        }
        return fireIntensity(found);
    }

    /** Cold, from biome and from altitude, whichever is worse. */
    private static float cryogenic(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiomeManager().getBiome(pos);
        float fromBiome = 0.0F;
        if (biome.is(BiomeTags.SPAWNS_SNOW_FOXES) || biome.value().getBaseTemperature() <= 0.0F) {
            fromBiome = 1.0F;
        } else if (biome.value().getBaseTemperature() < 0.25F) {
            fromBiome = 0.5F;
        }
        // Altitude is only cold where the biome is not scorching. Without this
        // a desert mesa at 250 is both freezing and burning, which is two
        // messages, two damage sources and one very confused player.
        float fromAltitude = biome.value().getBaseTemperature() < SCORCHING
                ? ramp(pos.getY(), COLD_ALTITUDE, COLD_CEILING)
                : 0.0F;
        // The worse of the two rather than the sum: a mountain in a snowfield
        // is cold, not twice as cold, and adding them would make the one place
        // both apply the only place the hazard is lethal.
        float worst = Math.max(fromBiome, fromAltitude);
        if (worst <= 0.0F) {
            return 0.0F;
        }
        // Night and weather make it worse, on the same principle as thermal.
        boolean exposed = level.canSeeSky(pos);
        if (exposed && (level.isNight() || level.isRaining())) {
            return Math.min(1.0F, worst * 1.5F);
        }
        return worst;
    }

    /** Depth. The only hazard that is purely a function of where you are. */
    private static float pressure(BlockPos pos) {
        return ramp(pos.getY(), PRESSURE_DEPTH, PRESSURE_FLOOR);
    }

    /**
     * A 0..1 ramp between two marks, in either direction.
     *
     * Clamped at both ends, which is the half that is easy to leave out: an
     * unclamped ramp makes the void below the world infinitely crushing and the
     * build limit infinitely cold.
     */
    private static float ramp(int value, int start, int full) {
        if (start == full) {
            return value == start ? 1.0F : 0.0F;
        }
        float t = (float) (value - start) / (float) (full - start);
        return Math.max(0.0F, Math.min(1.0F, t));
    }
}
