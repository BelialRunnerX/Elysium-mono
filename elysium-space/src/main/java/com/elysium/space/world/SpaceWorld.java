package com.elysium.space.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One world beyond the Overworld, as numbers: gravity, air and hazards. No
 * game types, so {@code tools/WorldHarness} runs it without a game.
 *
 * <p>Hazards are named by id ({@code elysiumlib:thermal},
 * {@code elysiumspace:vacuum}) and fed to Elysium's hazard pipeline through
 * {@code SpaceAtmosphere}. So a planet's heat is answered by exactly what
 * answers a desert's: wards, a matching armour set, a claim, a room's share.
 * Stellaris gave each planet one oxygen flag and a temperature. Here that
 * becomes intensities, which is what the Elysium pipeline reads.
 *
 * @param path          the dimension id under {@code elysiumspace:}
 * @param gravity       in g; 1 is the Overworld. Applied as a multiplier on the
 *                      gravity and fall-damage attributes
 * @param air           whether the air can be breathed
 * @param hazards       hazard id to intensity, each in (0, 1]
 * @param hostileSpawns whether monsters spawn naturally
 */
public record SpaceWorld(String path, double gravity, Air air,
                         Map<String, Float> hazards, boolean hostileSpawns) {

    public enum Air { BREATHABLE, THIN, TOXIC, NONE }

    public static final String VACUUM = "elysiumspace:vacuum";
    public static final String THERMAL = "elysiumlib:thermal";
    public static final String CRYOGENIC = "elysiumlib:cryogenic";
    public static final String CORROSIVE = "elysiumlib:corrosive";
    public static final String RADIOLOGICAL = "elysiumlib:radiological";
    public static final String PRESSURE = "elysiumlib:pressure";

    /** Vanilla's base gravity, blocks per tick squared. */
    public static final double BASE_GRAVITY = 0.08D;

    public SpaceWorld {
        hazards = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(hazards));
    }

    /** The intensity of a hazard here, 0 when this world does not have it. */
    public float intensity(String hazardId) {
        Float f = hazards.get(hazardId);
        return f == null ? 0.0F : f;
    }

    /**
     * The attribute modifier that turns 1 g into this world's gravity:
     * ADD_MULTIPLIED_TOTAL of {@code gravity - 1}, so 0.165 g reads -0.835.
     */
    public double gravityModifier() {
        return gravity - 1.0D;
    }

    /**
     * Whether a room an Oxygen Distributor fills removes this hazard here:
     * vacuum always, and corrosion on a world whose air is toxic, because the
     * distributor replaces the bad air. Heat, cold, radiation and pressure come
     * through walls.
     */
    public boolean clearedByAir(String hazardId) {
        return VACUUM.equals(hazardId) || (air == Air.TOXIC && CORROSIVE.equals(hazardId));
    }

    private static Map<String, Float> of(Object... pairs) {
        Map<String, Float> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], ((Number) pairs[i + 1]).floatValue());
        }
        return m;
    }

    /** The five worlds. See docs/SPEC_SPACE.md. */
    public static final List<SpaceWorld> ALL = List.of(
            new SpaceWorld("high_orbit", 0.07D, Air.NONE,
                    of(VACUUM, 1.0F, RADIOLOGICAL, 0.5F), false),
            new SpaceWorld("pale_moon", 0.165D, Air.NONE,
                    of(VACUUM, 1.0F, CRYOGENIC, 0.6F), true),
            new SpaceWorld("cinderveil", 0.9D, Air.TOXIC,
                    of(THERMAL, 1.0F, CORROSIVE, 1.0F, PRESSURE, 0.5F), true),
            new SpaceWorld("frostfall", 0.38D, Air.THIN,
                    of(CRYOGENIC, 1.0F, RADIOLOGICAL, 1.0F, VACUUM, 0.5F), true),
            new SpaceWorld("emerald_bastion", 1.0D, Air.BREATHABLE, of(), false));

    public static SpaceWorld named(String path) {
        for (SpaceWorld w : ALL) {
            if (w.path.equals(path)) {
                return w;
            }
        }
        return null;
    }
}
