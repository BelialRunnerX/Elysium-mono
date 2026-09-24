package com.elysium.space.world;

/**
 * Every machine number, from Stellaris 1.4.25's bytecode and recipes. Pure;
 * run by WorldHarness. Energy is NeoForge FE and fluids are mB, so other mods'
 * cables and pipes connect as-is.
 */
public final class MachineRules {

    private MachineRules() {
    }

    // Generators (Stellaris: 12 800 FE buffers; the solar panel makes 10 FE/t).
    public static final int GENERATOR_BUFFER = 12_800;
    public static final int COAL_FE_PER_TICK = 20;
    public static final int SOLAR_FE_PER_TICK = 10;
    /** On an airless world the sun is not filtered: panels make twice as much. */
    public static final int SOLAR_AIRLESS_MULTIPLIER = 2;
    /** How much a generator hands to each neighbour a tick. */
    public static final int PUSH_PER_TICK = 200;

    // Fuel Refinery (recipe fuel_refining: 10 oil -> 5 fuel for 100 FE; 10 000 mB tanks).
    public static final int REFINERY_TANK = 10_000;
    public static final int REFINERY_BUFFER = 10_000;
    public static final int OIL_PER_BATCH = 10;
    public static final int FUEL_PER_BATCH = 5;
    /** The refinery's second product, burnt by the Diesel Generator. */
    public static final int DIESEL_PER_BATCH = 5;
    public static final int REFINERY_FE_PER_BATCH = 100;

    // Water Separator (recipe water_separation: 5 water -> 10 hydrogen + 5 oxygen for 50 FE).
    public static final int SEPARATOR_WATER_TANK = 3_000;
    public static final int SEPARATOR_GAS_TANK = 6_000;
    public static final int SEPARATOR_BUFFER = 10_000;
    public static final int WATER_PER_BATCH = 5;
    public static final int HYDROGEN_PER_BATCH = 10;
    public static final int OXYGEN_PER_BATCH = 5;
    public static final int SEPARATOR_FE_PER_BATCH = 50;

    /** Conduits (Stellaris's first-tier cable carries 250 FE a tick). */
    public static final int CABLE_FE_PER_TICK = 250;
    public static final int PIPE_MB_PER_TICK = 250;

    /** Machines run one batch every this many ticks. */
    public static final int BATCH_TICKS = 5;

    // Oxygen Distributor (Stellaris: 20 000 FE buffer; each newly filled position costs air and power).
    public static final int DISTRIBUTOR_BUFFER = 20_000;
    public static final int DISTRIBUTOR_OXYGEN_TANK = 3_000;
    public static final int OXYGEN_PER_NEW_BLOCK = 1;
    public static final int FE_PER_NEW_BLOCK = 5;
    /** Keeping a room full: 1 mB of oxygen per this many positions each scan (a slow leak). */
    public static final int UPKEEP_BLOCKS_PER_MB = 400;

    /** How many batches a refinery can run with what it has; both products need room. */
    public static int refineryBatches(int oil, int fuelSpace, int dieselSpace, int energy) {
        return Math.max(0, Math.min(min3(oil / OIL_PER_BATCH, fuelSpace / FUEL_PER_BATCH,
                dieselSpace / DIESEL_PER_BATCH), energy / REFINERY_FE_PER_BATCH));
    }

    // Diesel Generator (Stellaris: 12 800 FE buffer). 5 mB every 10 ticks for 400 FE: 40 FE/t, twice coal.
    public static final int DIESEL_MB_PER_BURN = 5;
    public static final int DIESEL_BURN_TICKS = 10;
    public static final int DIESEL_FE_PER_BURN = 400;
    public static final int DIESEL_TANK = 10_000;

    // Pumpjack (Stellaris: 10 mB a tick into a 10 000 mB tank, from the chunk's oil).
    public static final int PUMP_MB_PER_TICK = 10;
    public static final int PUMP_FE_PER_TICK = 10;
    public static final int PUMP_TANK = 10_000;
    public static final int PUMP_BUFFER = 10_000;
    /** A chunk's oil when first pumped: Stellaris's range. */
    public static final int OIL_MIN = 1_000;
    public static final int OIL_MAX = 40_000;

    /** Portable Fluid Tank: one fluid, kept when the block is broken. */
    public static final int FLUID_TANK = 16_000;

    /**
     * The oil under a chunk, from the world seed and the chunk's position,
     * so the same chunk always has the same deposit. Half of all chunks have
     * none; the rest hold between OIL_MIN and OIL_MAX.
     */
    public static int oilDeposit(long seed, int chunkX, int chunkZ) {
        long h = seed ^ (chunkX * 0x9E3779B97F4A7C15L) ^ (chunkZ * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        if ((h & 1L) == 0L) {
            return 0;
        }
        long span = (long) OIL_MAX - OIL_MIN + 1;
        return (int) (OIL_MIN + Math.floorMod(h >>> 1, span));
    }

    /** What one pumpjack tick takes: limited by the deposit, tank room and power. */
    public static int pumped(int deposit, int tankSpace, int energy) {
        if (energy < PUMP_FE_PER_TICK) {
            return 0;
        }
        return Math.max(0, Math.min(PUMP_MB_PER_TICK, Math.min(deposit, tankSpace)));
    }

    public static int separatorBatches(int water, int hydrogenSpace, int oxygenSpace, int energy) {
        return Math.max(0, Math.min(min3(water / WATER_PER_BATCH, hydrogenSpace / HYDROGEN_PER_BATCH,
                oxygenSpace / OXYGEN_PER_BATCH), energy / SEPARATOR_FE_PER_BATCH));
    }

    /** Oxygen a distributor spends on one scan: new positions plus the leak on the rest. */
    public static int distributorOxygen(int newBlocks, int keptBlocks) {
        return Math.max(0, newBlocks) * OXYGEN_PER_NEW_BLOCK
                + (Math.max(0, keptBlocks) + UPKEEP_BLOCKS_PER_MB - 1) / UPKEEP_BLOCKS_PER_MB;
    }

    public static int distributorEnergy(int newBlocks) {
        return Math.max(0, newBlocks) * FE_PER_NEW_BLOCK;
    }

    public static int solarPerTick(boolean sunVisible, boolean airless) {
        return sunVisible ? SOLAR_FE_PER_TICK * (airless ? SOLAR_AIRLESS_MULTIPLIER : 1) : 0;
    }

    private static int min3(int a, int b, int c) {
        return Math.min(a, Math.min(b, c));
    }
}
