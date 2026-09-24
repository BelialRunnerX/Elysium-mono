package com.elysium.transit.rules;

import com.elysium.industry.rules.IndustryRules;

/**
 * Getting somewhere. The JDK and Age II's grid, and nothing else.
 *
 * <h2>The one sentence</h2>
 *
 * A player who has built a factory should not have to walk, and the pack
 * should charge them for that in a currency the factory produces. Everything
 * below follows from it: a waystone is a machine on the grid, a road is worth
 * laying because the arithmetic says how many trips it takes to pay for
 * itself, and a vehicle burns something to go faster than legs.
 *
 * <h2>The property that makes a teleport network honest</h2>
 *
 * A jump has to cost <b>at least</b> what the jumps that would replace it
 * cost. Get that wrong and the network has an arbitrage in it: chain three
 * short hops instead of one long one and cross a world for nothing. That is
 * not a hypothetical — it is what a "free under N blocks" rule does, and it
 * is the first thing that was written here and the first thing the harness
 * threw out.
 *
 * <p>So there is no free range. There is a fixed price per jump and a price
 * per block, which together are <em>subadditive</em>: for any three places,
 * going by way of the middle one never costs less than going direct. The
 * harness proves it over a million random triples rather than by argument.
 *
 * <h2>And how a player with no grid still gets home</h2>
 *
 * Not by a special case. A waystone holds a charge and fills from nothing at a
 * trickle, so the short way home costs a minute of patience when it is
 * unpowered and nothing at all when it is wired — which is the same lesson
 * every other module in this pack teaches, in the same currency.
 */
public final class TransitRules {

    private TransitRules() {
    }

    // ==================================================================
    // Waystones
    // ==================================================================

    /**
     * What any jump costs before distance is counted.
     *
     * <p>Strictly positive, and that is the whole of what stops the network
     * being free: it is what makes the price of going by way of somewhere
     * never less than the price of going direct. A fixed cost of zero and a
     * price per block would only just satisfy that — every chain would tie
     * with the direct jump — and any free allowance at all breaks it outright.
     */
    public static final float PER_JUMP = 2_000.0F;

    /**
     * The most distance can add to a jump, however far it is.
     *
     * <h2>Why the price flattens out instead of climbing forever</h2>
     *
     * A price that rises with distance forever looks obviously right and is
     * obviously wrong, and the harness found out how: with a flat charge for
     * changing world, a jump of two hundred thousand blocks inside one world
     * cost 197,000 while bouncing off the moon and back cost 44,000. Any
     * distance at all, for a flat fee, by way of somewhere else. That is the
     * arbitrage this file exists to prevent, and it was in the first draft.
     *
     * <p>The fix is not a special case. The price of distance <b>saturates</b>
     * — it rises steeply at first and flattens towards this number — which
     * makes the whole price a concave function of distance, and a concave
     * function with a positive value at zero is subadditive. Going by way of
     * anywhere is then never cheaper than going direct, for every pair of
     * places in every world, and the harness proves it over a million triples
     * rather than over this paragraph.
     *
     * <p>It also gives a player a number they can hold: <em>a jump costs at
     * most twenty thousand, however far it is</em>. Which is the point of
     * having waystones at all.
     */
    public static final float MOST_FOR_DISTANCE = 18_000.0F;

    /**
     * The distance at which half of {@link #MOST_FOR_DISTANCE} is charged.
     *
     * Four thousand blocks: far enough that everything in a starting base is
     * cheap and a trip across a world is not.
     */
    public static final float HALF_PRICE_AT = 4_000.0F;

    /**
     * What arriving in a different world costs, instead of a distance.
     *
     * <p>Instead of, because two worlds' coordinates are not comparable and
     * pretending they are gives the Nether's eight-to-one a meaning it does
     * not have. A flat surcharge keeps the subadditivity intact — two crossings
     * cost twice one, and a crossing by way of a detour costs the detour as
     * well — and it is a number a player can hold.
     */
    public static final float BETWEEN_WORLDS = 20_000.0F;

    /** Somewhere a player has bound: a world and a place in it. */
    public record Waypoint(String world, int x, int y, int z) {

        public Waypoint {
            if (world == null || world.isBlank()) {
                throw new IllegalArgumentException("a waypoint in no world is not "
                        + "anywhere");
            }
        }

        public boolean sameWorldAs(Waypoint other) {
            return world.equals(other.world());
        }

        /**
         * As one string, which is how a waypoint crosses the wire and how it
         * is remembered on a player.
         *
         * <p>Here rather than in the block that writes it, because a format
         * written in one place and read in another is a format that drifts,
         * and the harness can round-trip this one a hundred thousand times.
         */
        public String pack() {
            return world + "|" + x + "|" + y + "|" + z;
        }

        /** Back again, or null if it is not one. */
        public static Waypoint unpack(String packed) {
            if (packed == null) {
                return null;
            }
            String[] parts = packed.split("\\|");
            if (parts.length < 4 || parts[0].isBlank()) {
                return null;
            }
            try {
                return new Waypoint(parts[0], Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (NumberFormatException notAWaypoint) {
                return null;
            }
        }
    }

    /**
     * How far apart two places are, in blocks.
     *
     * <p>Zero across worlds, because the number would be meaningless and a
     * meaningless number in a price is worse than no number.
     */
    public static double distance(Waypoint from, Waypoint to) {
        if (!from.sameWorldAs(to)) {
            return 0.0;
        }
        double dx = (double) from.x() - to.x();
        double dy = (double) from.y() - to.y();
        double dz = (double) from.z() - to.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * What one jump costs, in the same FE everything else in the pack is
     * priced in.
     *
     * <p>Subadditive by construction, which is the property the whole network
     * rests on. The harness checks it over a million random triples anyway,
     * because "by construction" is a claim and not a test.
     */
    public static float cost(Waypoint from, Waypoint to) {
        if (!from.sameWorldAs(to)) {
            return PER_JUMP + BETWEEN_WORLDS;
        }
        return PER_JUMP + forDistance(distance(from, to));
    }

    /**
     * What distance alone adds, which is where the concavity lives.
     *
     * <p>{@code MOST_FOR_DISTANCE * d / (d + HALF_PRICE_AT)}: zero at zero,
     * rising steeply, never reaching the cap. Written as its own function so
     * that the one shape everything else depends on is in one place and the
     * harness can sweep it directly.
     */
    public static float forDistance(double blocks) {
        double far = Math.max(0.0, blocks);
        return (float) (MOST_FOR_DISTANCE * far / (far + HALF_PRICE_AT));
    }

    /** The most any jump inside one world can ever cost. */
    public static final float MOST_IN_A_WORLD = PER_JUMP + MOST_FOR_DISTANCE;

    /** The most any jump can cost at all. */
    public static final float MOST_ANY_JUMP =
            Math.max(MOST_IN_A_WORLD, PER_JUMP + BETWEEN_WORLDS);

    /**
     * What a waystone will hold.
     *
     * <p>Comfortably more than {@link #MOST_ANY_JUMP}, which matters: a buffer
     * smaller than the dearest jump would be a network with places in it
     * nobody could ever reach, and the harness checks that no jump anywhere
     * costs more than a waystone can hold.
     */
    public static final int BUFFER = 64_000;

    /**
     * What a waystone fills at with nothing wired to it, per tick.
     *
     * <p>The early game's answer, and deliberately not a special case in the
     * price: the jump costs exactly what it costs, and an unpowered stone
     * simply takes a while to afford it. A two-hundred-and-fifty-six block hop
     * comes to a little over a minute of standing about; wiring it removes the
     * wait entirely, which is the pack's whole argument in miniature.
     */
    public static final float TRICKLE = 2.0F;

    /** What a wired waystone asks the grid for. */
    public static final IndustryRules.Machine WAYSTONE =
            new IndustryRules.Machine(64.0F, IndustryRules.Voltage.LOW);

    /**
     * How fast a waystone fills, given what the grid is giving it.
     *
     * <p>The trickle is a floor and not an alternative: a waystone on a
     * browned-out grid fills at least as fast as one on no grid at all, which
     * is what stops wiring something ever making it worse.
     */
    public static float fillRate(float share) {
        if (share <= 0.0F) {
            return TRICKLE;
        }
        return Math.max(TRICKLE, WAYSTONE.draw() * Math.min(1.0F, share));
    }

    /** What a waystone holds after a tick at this rate. */
    public static float after(float held, float share) {
        return Math.min(BUFFER, Math.max(0.0F, held) + fillRate(share));
    }

    /** Whether a jump can be paid for out of what is held. */
    public static boolean affordable(float held, Waypoint from, Waypoint to) {
        return held >= cost(from, to);
    }

    /**
     * How many ticks until a jump becomes affordable.
     *
     * <p>{@code 0} when it already is, and {@link Integer#MAX_VALUE} when it
     * never will be — which can only mean the jump costs more than a waystone
     * will hold, and is a sentence rather than a spinner.
     */
    public static int readyIn(float held, float share, Waypoint from, Waypoint to) {
        float want = cost(from, to);
        if (held >= want) {
            return 0;
        }
        if (want > BUFFER) {
            return Integer.MAX_VALUE;
        }
        float rate = fillRate(share);
        return (int) Math.ceil((want - held) / rate);
    }

    /** The furthest jump a waystone holding this much can pay for. */
    public static double reach(float held) {
        if (held < PER_JUMP) {
            return 0.0;
        }
        float forDistance = held - PER_JUMP;
        if (forDistance >= MOST_FOR_DISTANCE) {
            // The price of distance never reaches its cap, so anything that
            // can pay the cap can pay for any distance there is. Saying so is
            // better than a very large number that is not the truth.
            return Double.POSITIVE_INFINITY;
        }
        return HALF_PRICE_AT * forDistance / (MOST_FOR_DISTANCE - forDistance);
    }

    // ==================================================================
    // Roads
    // ==================================================================

    /**
     * What you are travelling over, and what it does to you.
     *
     * <p>Four, because that is as many as a player can tell apart by looking
     * and every one of them already exists in the world: a laid road, a worn
     * path, whatever the ground happens to be, and the stuff that slows you
     * down. Anything finer would be a number nobody could feel.
     */
    public enum Surface {

        /** Laid on purpose. The only one that is faster than it is wide. */
        ROAD(1.0F, 1.0F),

        /** Trodden, or laid badly. */
        PATH(0.72F, 1.15F),

        /** Grass, sand, stone: whatever was there. */
        GROUND(0.5F, 1.4F),

        /** Ice, mud, scree, a slope. */
        ROUGH(0.3F, 1.9F);

        private final float grip;
        private final float thirst;

        Surface(float grip, float thirst) {
            this.grip = grip;
            this.thirst = thirst;
        }

        /** What fraction of a vehicle's top speed this surface allows. */
        public float grip() {
            return grip;
        }

        /** What it multiplies fuel use by. */
        public float thirst() {
            return thirst;
        }
    }

    /** How fast a player walks, in blocks a second. The number to beat. */
    public static final float ON_FOOT = 4.3F;

    /**
     * How long it takes to lay one block of road, in ticks.
     *
     * <p>Not a crafting time — the whole cost of having a road block in your
     * hand and in the ground, amortised. It is here because it is the other
     * half of the only question that matters about roads: whether laying one
     * is worth it, and after how many trips.
     */
    public static final int LAYING_TICKS = 60;

    // ==================================================================
    // Vehicles
    // ==================================================================

    /**
     * Something you ride.
     *
     * @param name      what it is called
     * @param tier      which age builds it
     * @param topSpeed  blocks a second on a road, empty
     * @param thirst    fuel units per block, on a road, empty
     * @param seats     how many people
     * @param slots     how much it carries, in inventory slots
     */
    public record Vehicle(String name, int tier, float topSpeed, float thirst,
                          int seats, int slots) {

        public Vehicle {
            if (topSpeed <= 0.0F || thirst <= 0.0F) {
                throw new IllegalArgumentException("a vehicle that does not move or "
                        + "does not burn anything is not a vehicle");
            }
            if (seats < 1 || slots < 0) {
                throw new IllegalArgumentException("a vehicle nobody can sit in is "
                        + "scenery");
            }
        }
    }

    /**
     * How much a full load costs you.
     *
     * <p>A loaded vehicle is slower and thirstier, and never the other way
     * round. Stated as one number so that both consequences come from the same
     * place and cannot drift apart.
     */
    public static final float FULLY_LADEN = 0.35F;

    /** Three, one per age that can build one. */
    public static final Vehicle CART = new Vehicle("cart", 1, 6.0F, 0.06F, 1, 9);
    public static final Vehicle TRUCK = new Vehicle("truck", 2, 11.0F, 0.11F, 2, 27);
    public static final Vehicle HAULER = new Vehicle("hauler", 3, 18.0F, 0.20F, 2, 54);

    /**
     * How fast a vehicle goes, in blocks a second.
     *
     * @param load how full it is, from nothing to one
     */
    public static float speedOn(Vehicle vehicle, Surface surface, float load) {
        float carrying = 1.0F - FULLY_LADEN * clamp(load);
        return vehicle.topSpeed() * surface.grip() * carrying;
    }

    /** What it burns per block, on that surface, that full. */
    public static float thirstOn(Vehicle vehicle, Surface surface, float load) {
        float carrying = 1.0F + FULLY_LADEN * clamp(load);
        return vehicle.thirst() * surface.thirst() * carrying;
    }

    /** How far a tankful goes. */
    public static double range(Vehicle vehicle, float fuel, Surface surface, float load) {
        if (fuel <= 0.0F) {
            return 0.0;
        }
        return fuel / thirstOn(vehicle, surface, load);
    }

    /** How long a journey of this many blocks takes, in ticks. */
    public static int ticksFor(Vehicle vehicle, Surface surface, float load,
                               double blocks) {
        if (blocks <= 0.0) {
            return 0;
        }
        double seconds = blocks / speedOn(vehicle, surface, load);
        return (int) Math.ceil(seconds * 20.0);
    }

    /**
     * How many round trips before laying a road has paid for itself.
     *
     * <h2>Why this number is in the rules and not in a wiki</h2>
     *
     * It is the only question worth asking about a road, and a pack that
     * cannot answer it has built a decoration. Laying costs
     * {@link #LAYING_TICKS} a block once; every round trip after that saves
     * twice the difference between crossing it on the ground and crossing it
     * on a road. A road that never pays off is a road nobody should lay, and
     * this says so rather than letting a player find out over an evening.
     *
     * @return how many round trips, or {@link Integer#MAX_VALUE} if never
     */
    public static int tripsToPayOff(Vehicle vehicle, Surface without, double blocks) {
        if (blocks <= 0.0) {
            return 0;
        }
        int onRoad = ticksFor(vehicle, Surface.ROAD, 0.0F, blocks);
        int otherwise = ticksFor(vehicle, without, 0.0F, blocks);
        int savedPerTrip = 2 * (otherwise - onRoad);
        if (savedPerTrip <= 0) {
            return Integer.MAX_VALUE;
        }
        long laying = (long) Math.ceil(blocks) * LAYING_TICKS;
        return (int) Math.min(Integer.MAX_VALUE,
                (laying + savedPerTrip - 1) / savedPerTrip);
    }

    /**
     * How much fuel a tank holds.
     *
     * <p>One number for all three, because a bigger tank on a bigger vehicle
     * would be a second axis doing the same job as thirst and would make the
     * ladder harder to read rather than deeper. What differs between the rungs
     * is how fast they go, how much they carry and how much they drink —
     * three things, which is already as many as anybody holds at once.
     */
    public static final float TANK = 200.0F;

    /**
     * What a tick of furnace burn time is worth as fuel.
     *
     * <p>Anything that burns, drives: a vehicle with its own fuel item would
     * be a crafting chain nobody asked for, and "it runs on coal" is a
     * sentence that needs no wiki. A lump of coal is sixteen units, which is
     * a couple of hundred blocks in a cart.
     */
    public static final float FUEL_PER_BURN_TICK = 0.01F;

    /** What burning this for that long is worth. */
    public static float fuelFrom(int burnTicks) {
        return Math.max(0.0F, burnTicks) * FUEL_PER_BURN_TICK;
    }

    /** What is left after taking some in, never more than the tank. */
    public static float fuelled(float held, float taken) {
        return Math.min(TANK, Math.max(0.0F, held) + Math.max(0.0F, taken));
    }

    /** How full a vehicle is, as the fraction the speed and thirst rules want. */
    public static float loadOf(int filled, int slots) {
        if (slots <= 0) {
            return 0.0F;
        }
        return clamp((float) filled / slots);
    }

    /** How far the wheel turns in a tick, at full lock, in degrees. */
    public static final float TURN_RATE = 4.5F;

    /**
     * How much of the gap to its target speed a vehicle closes in a tick.
     *
     * <p>Under one, so that a vehicle has weight: something that reached its
     * top speed on the first tick would handle like a cursor. Over zero by
     * enough that it is not a chore. Used for slowing down as well as speeding
     * up, so a vehicle that runs onto rough ground sinks into its new speed
     * rather than stopping dead.
     */
    public static final float ACCELERATION = 0.08F;

    /** The speed a vehicle is doing next tick, given what it is doing now. */
    public static float approach(float now, float target) {
        return now + (target - now) * ACCELERATION;
    }

    private static float clamp(float fraction) {
        return Math.max(0.0F, Math.min(1.0F, fraction));
    }
}
