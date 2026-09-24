package com.elysium.space.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How far every world is, and what a rocket needs to get there. Pure, run by
 * WorldHarness.
 *
 * <h2>Stellaris's rule, kept</h2>
 *
 * Every world sits on one line, at a distance from the Overworld in megametres
 * (Stellaris's {@code distanceFromEarth}). A trip costs the gap between the two
 * distances, so the Pale Moon to Cinderveil costs what the Overworld to
 * Cinderveil costs less the Moon's head start. A rocket's range is the fuel in
 * its tank times its fuel's megametres per millibucket (Stellaris's
 * {@code FuelType.getMegametersTraveled}: 19.22 for plain fuel, rising with
 * better fuels). A basic tank holds 3000 mB.
 *
 * <h2>Where our worlds sit</h2>
 *
 * The Pale Moon keeps the real Moon's 384.4. Cinderveil and Frostfall keep
 * Venus's and Mars's (41 000 and 55 700). High Orbit is 0.4, a low orbit. The
 * Emerald Bastion is the far imperial world at 92 000 (Mercury's slot). A basic
 * rocket on plain fuel reaches 57 660, so the Bastion needs a better fuel or a
 * bigger tank. That is the first upgrade a player works towards (S6).
 */
public final class SpaceRoute {

    private SpaceRoute() {
    }

    /** Distance from the Overworld, in megametres. "overworld" is home at 0. */
    public static final Map<String, Double> DISTANCE;

    static {
        Map<String, Double> d = new LinkedHashMap<>();
        d.put("overworld", 0.0D);
        d.put("high_orbit", 0.4D);
        d.put("pale_moon", 384.4D);
        d.put("cinderveil", 41_000.0D);
        d.put("frostfall", 55_700.0D);
        d.put("emerald_bastion", 92_000.0D);
        DISTANCE = java.util.Collections.unmodifiableMap(d);
    }

    /** Fuels, with Stellaris's megametres per millibucket. */
    public enum Fuel {
        FUEL(19.22D), HYDROGEN(21.36D), RADIOACTIVE(23.74D), URANIUM(26.38D), PLUTONIUM(29.3D);

        public final double megametresPerMb;

        Fuel(double megametresPerMb) {
            this.megametresPerMb = megametresPerMb;
        }
    }

    public static final int BASIC_TANK_MB = 3000;
    /** The Large Fuel Tank upgrade: enough for the Emerald Bastion on plain fuel. */
    public static final int LARGE_TANK_MB = 6000;

    /** The gap between two worlds, or -1 if either is unknown. */
    public static double gap(String from, String to) {
        Double a = DISTANCE.get(from);
        Double b = DISTANCE.get(to);
        return a == null || b == null ? -1.0D : Math.abs(a - b);
    }

    /** Millibuckets of {@code fuel} a trip burns, rounded up; -1 if a world is unknown. */
    public static int fuelFor(String from, String to, Fuel fuel) {
        double gap = gap(from, to);
        if (gap < 0.0D) {
            return -1;
        }
        return (int) Math.ceil(gap / fuel.megametresPerMb);
    }

    /** Whether {@code tankMb} of {@code fuel} gets from one world to the other. Never to where you are. */
    public static boolean canTravel(String from, String to, int tankMb, Fuel fuel) {
        if (from.equals(to)) {
            return false;
        }
        int need = fuelFor(from, to, fuel);
        return need >= 0 && need <= tankMb;
    }

    /** Every world reachable from here on what is in the tank, nearest first. */
    public static List<String> reachable(String from, int tankMb, Fuel fuel) {
        List<String> out = new ArrayList<>();
        for (String to : DISTANCE.keySet()) {
            if (canTravel(from, to, tankMb, fuel)) {
                out.add(to);
            }
        }
        out.sort((x, y) -> Double.compare(gap(from, x), gap(from, y)));
        return out;
    }
}
