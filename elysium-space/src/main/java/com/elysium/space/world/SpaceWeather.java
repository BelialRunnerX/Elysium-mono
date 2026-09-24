package com.elysium.space.world;

import com.elysium.lib.hazard.ElysiumHazards;
import com.elysium.lib.weather.ElysiumWeather;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What the sky does on each of the frontier worlds.
 *
 * <h2>Why weather is a hazard rather than a mood</h2>
 *
 * Because these worlds are already trying to kill you and the weather is the
 * part of that you can see coming. An ash storm on Cinderveil raises the
 * corrosion that world already has, halves what a solar panel makes of a sky
 * full of ash, and is answered by being indoors — which is the answer the
 * hazard engine already gives for everything else, so <em>storm shutters</em>
 * turns out to be a wall.
 *
 * <h2>Why the Overworld and the Bastion have none</h2>
 *
 * The Overworld is the player's own world and was here before this mod was:
 * adding acid rain to it would change every existing save's weather, which is
 * the one kind of change this project has refused consistently. The Emerald
 * Bastion is the paradise — the one world with no hazards at all — and a
 * paradise with storms is just another frontier.
 *
 * <p>Both are decisions rather than omissions, which is why they are written
 * down here rather than being the absence of a line. In the ids the rest of
 * this file speaks in: {@code overworld} and {@code emerald_bastion} are
 * declared calm, deliberately, and the validator fails if either quietly
 * acquires a sky.
 */
public final class SpaceWeather {

    private SpaceWeather() {
    }

    // ------------------------------------------------------------------
    // The storms
    // ------------------------------------------------------------------

    /** Cinderveil's ash: the corrosion it already has, thickened, and a dark sky. */
    public static final ElysiumWeather.Kind ASHFALL =
            new ElysiumWeather.Kind("ashfall", ElysiumHazards.CORROSIVE, 0.55F, 0.50F);

    /** And its worse half: the air itself burning. */
    public static final ElysiumWeather.Kind FIRESTORM =
            new ElysiumWeather.Kind("firestorm", ElysiumHazards.THERMAL, 0.70F, 0.30F);

    /** Frostfall's whiteout: cold, and nothing to see. */
    public static final ElysiumWeather.Kind WHITEOUT =
            new ElysiumWeather.Kind("whiteout", ElysiumHazards.CRYOGENIC, 0.65F, 0.55F);

    /** The Pale Moon has no air to storm with, so its weather is the sun's. */
    public static final ElysiumWeather.Kind ION_STORM =
            new ElysiumWeather.Kind("ion_storm", ElysiumHazards.RADIOLOGICAL, 0.60F, 0.45F);

    /** And the dust it kicks up, which has nothing to settle through. */
    public static final ElysiumWeather.Kind REGOLITH_GUST =
            new ElysiumWeather.Kind("regolith_gust", ElysiumHazards.CORROSIVE, 0.35F, 0.35F);

    /** In orbit there is only one kind of weather, and it comes from the star. */
    public static final ElysiumWeather.Kind SOLAR_FLARE =
            new ElysiumWeather.Kind("solar_flare", ElysiumHazards.RADIOLOGICAL, 0.75F, 0.40F);

    // ------------------------------------------------------------------

    /**
     * Tells the library what each world's sky does.
     *
     * Called from the mod constructor: the weather registry is a plain map in
     * the library rather than a game registry, and nothing it holds needs the
     * game to exist.
     */
    public static void declare() {
        ElysiumWeather.declare(world("cinderveil"),
                new ElysiumWeather.Climate(List.of(ASHFALL, FIRESTORM), 4));
        ElysiumWeather.declare(world("frostfall"),
                new ElysiumWeather.Climate(List.of(WHITEOUT), 3));
        ElysiumWeather.declare(world("pale_moon"),
                new ElysiumWeather.Climate(List.of(ION_STORM, REGOLITH_GUST), 3));
        ElysiumWeather.declare(world("high_orbit"),
                new ElysiumWeather.Climate(List.of(SOLAR_FLARE), 2));
    }

    private static ResourceLocation world(String path) {
        return ResourceLocation.fromNamespaceAndPath("elysiumspace", path);
    }
}
