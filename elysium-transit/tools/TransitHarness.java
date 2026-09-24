package com.elysium.transit.rules;

import java.util.Random;

/**
 * Every number in {@link TransitRules}, executed.
 *
 * Run by validate.py, with a clock on it. The claim this file exists for is
 * that a teleport network has no arbitrage in it — that going by way of
 * somewhere never costs less than going direct — and that is a property over
 * every triple of places, which is not a thing anybody checks by reading.
 */
public final class TransitHarness {

    private static int failures;
    private static int checks;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            System.out.println("  FAIL: " + complaint);
            failures++;
        }
    }

    public static void main(String[] args) {
        thePrice();
        theArbitrage();
        theBuffer();
        theSurfaces();
        theVehicles();
        theRoads();
        theTank();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: no chain of jumps is ever cheaper than "
                + "the jump it replaces, a waystone with no power still gets you home, "
                + "a road is never slower than the ground beside it, and a load never "
                + "makes a vehicle faster or thriftier");
    }

    // ==================================================================

    private static TransitRules.Waypoint at(String world, int x, int y, int z) {
        return new TransitRules.Waypoint(world, x, y, z);
    }

    private static void thePrice() {
        TransitRules.Waypoint home = at("overworld", 0, 64, 0);
        TransitRules.Waypoint near = at("overworld", 100, 64, 0);
        TransitRules.Waypoint far = at("overworld", 10_000, 64, 0);
        TransitRules.Waypoint away = at("luna", 0, 64, 0);

        check(TransitRules.PER_JUMP > 0.0F,
                "a jump has no fixed price, so a chain of short hops ties with the long "
                        + "one it replaces and the network is free to anybody patient");
        check(TransitRules.cost(home, home) == TransitRules.PER_JUMP,
                "jumping to where you already are is not the fixed price");
        check(TransitRules.cost(home, near) < TransitRules.cost(home, far),
                "a longer jump does not cost more");
        check(TransitRules.cost(home, away) == TransitRules.cost(away, home),
                "crossing between two worlds costs different amounts in each direction");
        check(TransitRules.cost(home, away) > TransitRules.cost(home, near),
                "leaving the world is cheaper than crossing a hundred blocks of it");

        // Distance across worlds is meaningless, and a meaningless number in a
        // price is worse than no number.
        check(TransitRules.distance(home, away) == 0.0,
                "two worlds' coordinates are being subtracted from each other");
        check(TransitRules.distance(home, near) == TransitRules.distance(near, home),
                "distance is not the same in both directions");

        Random random = new Random(20260924L);
        for (int round = 0; round < 100_000; round++) {
            TransitRules.Waypoint a = random(random, "overworld");
            TransitRules.Waypoint b = random(random, "overworld");
            check(TransitRules.cost(a, b) >= TransitRules.PER_JUMP,
                    "a jump came to less than the fixed price");
            check(Math.abs(TransitRules.cost(a, b) - TransitRules.cost(b, a)) < 1.0F,
                    "a jump costs a different amount each way");
            check(Float.isFinite(TransitRules.cost(a, b)), "a jump costs infinity");
        }
        // A waypoint is remembered on a player and sent over the wire as one
        // string. Written in one place and read in another it would drift;
        // written once and round-tripped here it cannot.
        Random packing = new Random(4242L);
        for (int round = 0; round < 100_000; round++) {
            TransitRules.Waypoint made = random(packing,
                    round % 3 == 0 ? "overworld" : "some_mod:a_world_1");
            TransitRules.Waypoint back = TransitRules.Waypoint.unpack(made.pack());
            check(made.equals(back), made.pack() + " did not survive being packed");
        }
        check(TransitRules.Waypoint.unpack("nonsense") == null,
                "nonsense unpacks into a place a player can be sent to");
        check(TransitRules.Waypoint.unpack("overworld|1|2") == null,
                "a waypoint missing a coordinate unpacks anyway");
        check(TransitRules.Waypoint.unpack("overworld|1|two|3") == null,
                "a waypoint whose coordinates are not numbers unpacks anyway");
        check(TransitRules.Waypoint.unpack("|1|2|3") == null,
                "a waypoint in no world unpacks anyway");
        check(TransitRules.Waypoint.unpack(null) == null, "nothing unpacks into a place");
        check(TransitRules.Waypoint.unpack("overworld|1|2|3|My Base") != null,
                "a packed waypoint with a name after it stops being a waypoint");

        System.out.println("  price: symmetric, always at least the fixed price, and "
                + "never infinite, over 100,000 pairs; and 100,000 waypoints that "
                + "survive the wire");
    }

    private static TransitRules.Waypoint random(Random random, String world) {
        return at(world, random.nextInt(60_000) - 30_000, random.nextInt(320) - 64,
                random.nextInt(60_000) - 30_000);
    }

    /**
     * The one that matters.
     *
     * <p>If going by way of a third place is ever cheaper than going direct,
     * the network has an arbitrage in it and every long jump in the pack is
     * free to anybody who notices. The first version of these rules had one —
     * a "free under 256 blocks" allowance, which lets you chain 256-block hops
     * across a world for nothing — and this is what found it.
     */
    private static void theArbitrage() {
        Random random = new Random(777_777L);
        String[] worlds = {"overworld", "luna", "the_nether", "mars"};
        int sameWorld = 0;
        int crossing = 0;
        for (int round = 0; round < 1_000_000; round++) {
            TransitRules.Waypoint a = random(random, worlds[random.nextInt(worlds.length)]);
            TransitRules.Waypoint b = random(random, worlds[random.nextInt(worlds.length)]);
            TransitRules.Waypoint c = random(random, worlds[random.nextInt(worlds.length)]);
            float direct = TransitRules.cost(a, c);
            float byWayOf = TransitRules.cost(a, b) + TransitRules.cost(b, c);
            if (direct > byWayOf + 0.5F) {
                check(false, "going from " + a + " to " + c + " by way of " + b
                        + " costs " + byWayOf + " and going direct costs " + direct
                        + ", so the network can be arbitraged");
            }
            if (a.sameWorldAs(c)) {
                sameWorld++;
            } else {
                crossing++;
            }
        }
        checks++;
        check(sameWorld > 100_000 && crossing > 100_000,
                "the million triples were nearly all of one shape, so the arbitrage "
                        + "check is only testing half the price");

        // And the shape that broke it, written out on purpose so that anybody
        // reintroducing a free allowance meets it immediately.
        TransitRules.Waypoint start = at("overworld", 0, 64, 0);
        TransitRules.Waypoint middle = at("overworld", 200, 64, 0);
        TransitRules.Waypoint end = at("overworld", 400, 64, 0);
        check(TransitRules.cost(start, end)
                        <= TransitRules.cost(start, middle) + TransitRules.cost(middle, end),
                "two two-hundred-block hops are cheaper than one four-hundred-block "
                        + "jump, which is the free-allowance bug");

        // Twenty hops of twenty blocks, against one jump of four hundred.
        float chained = 0.0F;
        for (int hop = 0; hop < 20; hop++) {
            chained += TransitRules.cost(at("overworld", hop * 20, 64, 0),
                    at("overworld", (hop + 1) * 20, 64, 0));
        }
        check(chained >= TransitRules.cost(start, end),
                "twenty short hops cost " + chained + " and the jump they replace costs "
                        + TransitRules.cost(start, end));
        System.out.println("  arbitrage: 1,000,000 random triples across four worlds, "
                + "and not one of them cheaper by way of somewhere else");
    }

    private static void theBuffer() {
        TransitRules.Waypoint home = at("overworld", 0, 64, 0);
        TransitRules.Waypoint away = at("luna", 0, 64, 0);

        check(TransitRules.BUFFER >= TransitRules.cost(home, away),
                "a waystone cannot hold enough for a crossing between worlds, so there "
                        + "are places in the network nobody can reach");
        check(TransitRules.reach(TransitRules.BUFFER) > 10_000.0,
                "a full waystone reaches " + TransitRules.reach(TransitRules.BUFFER)
                        + " blocks, which is not a way home from anywhere");

        // The shape itself: it climbs, it never reaches the cap, and the
        // reach it implies agrees with the price it charges.
        double climbing = -1.0;
        for (double blocks = 0.0; blocks < 2_000_000.0; blocks += 137.0) {
            float price = TransitRules.forDistance(blocks);
            check(price >= climbing, "the price of distance fell between "
                    + (blocks - 137.0) + " and " + blocks);
            check(price < TransitRules.MOST_FOR_DISTANCE,
                    "the price of distance reached its cap at " + blocks + " blocks");
            climbing = price;
            double back = TransitRules.reach(TransitRules.PER_JUMP + price);
            check(Double.isInfinite(back) || Math.abs(back - blocks) < 1.0 + blocks / 1000.0,
                    "a waystone holding exactly the price of " + blocks + " blocks says "
                            + "it reaches " + back);
        }
        check(TransitRules.forDistance(TransitRules.HALF_PRICE_AT)
                        == TransitRules.MOST_FOR_DISTANCE / 2.0F,
                "the half-price distance does not charge half the cap");
        check(TransitRules.reach(0.0F) == 0.0, "an empty waystone jumps somewhere");
        check(TransitRules.reach(TransitRules.PER_JUMP - 1.0F) == 0.0,
                "a waystone that cannot pay the fixed price jumps anyway");

        // The trickle is a floor, not an alternative: wiring a waystone to a
        // browned-out grid must never make it slower than leaving it alone.
        check(TransitRules.fillRate(0.0F) == TransitRules.TRICKLE,
                "an unwired waystone does not fill at the trickle");
        for (int step = 0; step <= 100; step++) {
            float share = step / 100.0F;
            check(TransitRules.fillRate(share) >= TransitRules.TRICKLE,
                    "at share " + share + " a wired waystone fills slower than an "
                            + "unwired one, so wiring it made it worse");
        }
        check(TransitRules.fillRate(1.0F) > TransitRules.fillRate(0.0F),
                "a fully fed waystone fills no faster than one with no power at all");

        // It fills, it settles at the buffer, and it never exceeds it.
        float held = 0.0F;
        for (int tick = 0; tick < 200_000; tick++) {
            float was = held;
            held = TransitRules.after(held, 0.0F);
            check(held >= was, "a waystone lost charge on a tick where nothing spent it");
            check(held <= TransitRules.BUFFER, "a waystone holds more than its buffer");
        }
        check(held == TransitRules.BUFFER, "a waystone never fills");

        // And the readout a player is given.
        check(TransitRules.readyIn(TransitRules.BUFFER, 0.0F, home, away) == 0,
                "a full waystone says it is not ready for a crossing it can pay for");
        int patience = TransitRules.readyIn(0.0F, 0.0F,
                home, at("overworld", 256, 64, 0));
        check(patience > 0 && patience < 20 * 60 * 5,
                "an empty, unwired waystone takes " + (patience / 20) + " seconds to "
                        + "afford the short way home, which is either free or a chore");

        // Counted from the same arithmetic it describes, or it is a readout of
        // something else: fill for exactly as long as it said, and it is ready.
        float waited = 0.0F;
        for (int tick = 0; tick < patience; tick++) {
            waited = TransitRules.after(waited, 0.0F);
        }
        check(TransitRules.affordable(waited, home, at("overworld", 256, 64, 0)),
                "waiting exactly as long as the waystone said still did not pay for the "
                        + "jump, so the countdown describes something else");

        // Nothing anywhere may cost more than a waystone can hold, because a
        // place in the network nobody can reach is worse than an expensive one.
        TransitRules.Waypoint absurd = at("overworld", 30_000_000, 64, 30_000_000);
        check(TransitRules.cost(home, absurd) <= TransitRules.BUFFER,
                "a jump to the world border costs more than a waystone holds");
        check(TransitRules.readyIn(0.0F, 0.0F, home, absurd) < Integer.MAX_VALUE,
                "a jump to the world border is reported as impossible");
        check(TransitRules.MOST_ANY_JUMP <= TransitRules.BUFFER,
                "the dearest jump in the network costs more than a waystone can hold");
        check(TransitRules.cost(home, absurd) < TransitRules.MOST_IN_A_WORLD,
                "the price of distance reached its cap, so it is not the shape the "
                        + "subadditivity proof depends on");
        check(Double.isInfinite(TransitRules.reach(TransitRules.MOST_IN_A_WORLD)),
                "a waystone holding the cap cannot reach everywhere, though nothing "
                        + "costs more than the cap");
        System.out.println("  waystones: a buffer that covers every jump in the network, "
                + "a trickle that is a floor rather than an alternative, and a countdown "
                + "that agrees with the clock");
    }

    private static void theSurfaces() {
        TransitRules.Surface[] surfaces = TransitRules.Surface.values();
        for (TransitRules.Surface surface : surfaces) {
            check(surface.grip() > 0.0F && surface.grip() <= 1.0F,
                    surface + " has a grip of " + surface.grip());
            check(surface.thirst() >= 1.0F,
                    surface + " burns less fuel than a road, so the road is a penalty");
        }
        for (int index = 1; index < surfaces.length; index++) {
            check(surfaces[index - 1].grip() > surfaces[index].grip(),
                    surfaces[index - 1] + " is no faster than " + surfaces[index]
                            + ", so the ladder has a rung that does nothing");
            check(surfaces[index - 1].thirst() < surfaces[index].thirst(),
                    surfaces[index - 1] + " is no thriftier than " + surfaces[index]);
        }
        check(TransitRules.Surface.ROAD.grip() == 1.0F,
                "a road is not full speed, so nothing in the game is");
        check(TransitRules.Surface.ROAD.thirst() == 1.0F,
                "a road is not the cheapest thing to drive on");
        System.out.println("  surfaces: four rungs, each strictly slower and thirstier "
                + "than the one above it");
    }

    private static void theVehicles() {
        TransitRules.Vehicle[] vehicles = {TransitRules.CART, TransitRules.TRUCK,
                TransitRules.HAULER};
        for (int index = 1; index < vehicles.length; index++) {
            check(vehicles[index].topSpeed() > vehicles[index - 1].topSpeed(),
                    vehicles[index].name() + " is no faster than "
                            + vehicles[index - 1].name());
            check(vehicles[index].slots() > vehicles[index - 1].slots(),
                    vehicles[index].name() + " carries no more than "
                            + vehicles[index - 1].name());
            check(vehicles[index].tier() > vehicles[index - 1].tier(),
                    "two vehicles belong to the same age, so one of them is a strict "
                            + "improvement on the other with nothing to earn");
        }

        // The first rung has to beat walking on a road and lose to it nowhere
        // that matters, or nobody builds one.
        check(TransitRules.speedOn(TransitRules.CART, TransitRules.Surface.ROAD, 0.0F)
                        > TransitRules.ON_FOOT,
                "the first vehicle is slower than walking on a road, so there is no "
                        + "reason to build it");
        check(TransitRules.speedOn(TransitRules.CART, TransitRules.Surface.ROUGH, 1.0F)
                        < TransitRules.ON_FOOT,
                "a fully laden cart over rough ground still beats walking, so neither "
                        + "roads nor loads mean anything");

        Random random = new Random(31_415_926L);
        for (int round = 0; round < 200_000; round++) {
            TransitRules.Vehicle vehicle = vehicles[random.nextInt(vehicles.length)];
            TransitRules.Surface surface = TransitRules.Surface.values()[
                    random.nextInt(TransitRules.Surface.values().length)];
            float load = random.nextFloat();
            float more = Math.min(1.0F, load + random.nextFloat() * (1.0F - load));

            float light = TransitRules.speedOn(vehicle, surface, load);
            float heavy = TransitRules.speedOn(vehicle, surface, more);
            check(heavy <= light + 1.0e-4F,
                    "loading " + vehicle.name() + " made it faster");
            check(light > 0.0F, vehicle.name() + " stops dead at a load of " + load);

            float thin = TransitRules.thirstOn(vehicle, surface, load);
            float thick = TransitRules.thirstOn(vehicle, surface, more);
            check(thick >= thin - 1.0e-4F,
                    "loading " + vehicle.name() + " made it thriftier");

            check(TransitRules.speedOn(vehicle, TransitRules.Surface.ROAD, load)
                            >= TransitRules.speedOn(vehicle, surface, load) - 1.0e-4F,
                    "a road is slower than " + surface + " for " + vehicle.name());
            check(TransitRules.range(vehicle, 100.0F, TransitRules.Surface.ROAD, load)
                            >= TransitRules.range(vehicle, 100.0F, surface, load)
                            - 1.0e-4,
                    "a tank goes further over " + surface + " than over a road");

            double far = TransitRules.range(vehicle, 100.0F, surface, load);
            double nearer = TransitRules.range(vehicle, 100.0F, surface, more);
            check(nearer <= far + 1.0e-4, "loading " + vehicle.name()
                    + " made a tankful go further");
            check(far > 0.0, vehicle.name() + " goes nowhere on a full tank");
        }
        check(TransitRules.range(TransitRules.CART, 0.0F, TransitRules.Surface.ROAD,
                0.0F) == 0.0, "an empty tank goes somewhere");
        check(TransitRules.ticksFor(TransitRules.CART, TransitRules.Surface.ROAD, 0.0F,
                0.0) == 0, "going nowhere takes time");

        // The journey and the fuel have to be the same journey.
        double blocks = 1_000.0;
        for (TransitRules.Vehicle vehicle : vehicles) {
            for (TransitRules.Surface surface : TransitRules.Surface.values()) {
                double burnt = blocks * TransitRules.thirstOn(vehicle, surface, 0.5F);
                double reach = TransitRules.range(vehicle, (float) burnt, surface, 0.5F);
                check(Math.abs(reach - blocks) < 1.0,
                        vehicle.name() + " over " + surface + " burns enough for "
                                + reach + " blocks on a journey of " + blocks);
            }
        }
        System.out.println("  vehicles: three rungs, and over 200,000 random loads not "
                + "one where carrying more was faster, thriftier or further");
    }

    /**
     * Fuel, load and the weight of the thing.
     *
     * <p>Three properties that are each one sign away from a vehicle that is
     * better empty than full, refuels to more than it can hold, or handles
     * like a cursor.
     */
    private static void theTank() {
        check(TransitRules.fuelFrom(0) == 0.0F, "burning nothing is worth fuel");
        check(TransitRules.fuelFrom(-100) == 0.0F,
                "an item with negative burn time refuels a vehicle");
        check(TransitRules.fuelFrom(1600) > 0.0F, "a lump of coal is worth nothing");

        Random random = new Random(8_675_309L);
        for (int round = 0; round < 200_000; round++) {
            float held = random.nextFloat() * TransitRules.TANK * 1.5F;
            float taken = random.nextFloat() * TransitRules.TANK - 10.0F;
            float after = TransitRules.fuelled(held, taken);
            check(after <= TransitRules.TANK,
                    "a tank holding " + held + " took " + taken + " and now holds "
                            + after);
            check(after >= Math.min(held, TransitRules.TANK) - 1.0e-3F,
                    "refuelling took fuel out of the tank");
            check(after >= 0.0F, "a tank holds a negative amount");
        }
        check(TransitRules.fuelled(TransitRules.TANK, 1000.0F) == TransitRules.TANK,
                "a full tank takes more anyway, which is fuel destroyed without anybody "
                        + "asking");

        // How full it is has to be a fraction, whatever it is asked about.
        check(TransitRules.loadOf(0, 27) == 0.0F, "an empty vehicle is not empty");
        check(TransitRules.loadOf(27, 27) == 1.0F, "a full vehicle is not full");
        check(TransitRules.loadOf(99, 27) == 1.0F, "a vehicle is more than full");
        check(TransitRules.loadOf(5, 0) == 0.0F,
                "a vehicle with no slots is carrying something");
        for (int filled = 0; filled <= 54; filled++) {
            float load = TransitRules.loadOf(filled, 54);
            check(load >= 0.0F && load <= 1.0F, filled + " of 54 is a load of " + load);
        }

        // And the weight of it: it climbs towards its target, never past it,
        // and never in one tick.
        check(TransitRules.ACCELERATION > 0.0F && TransitRules.ACCELERATION < 1.0F,
                "a vehicle reaches its top speed in one tick, so it handles like a "
                        + "cursor rather than a vehicle");
        float speed = 0.0F;
        float target = TransitRules.speedOn(TransitRules.TRUCK,
                TransitRules.Surface.ROAD, 0.0F);
        int ticks = 0;
        while (speed < target - 0.01F && ticks < 20_000) {
            float next = TransitRules.approach(speed, target);
            check(next > speed, "a vehicle stopped accelerating at " + speed);
            check(next <= target, "a vehicle overshot its top speed");
            speed = next;
            ticks++;
        }
        check(ticks > 5 && ticks < 200,
                "a truck takes " + ticks + " ticks to reach its top speed, which is "
                        + "either instant or a wait");
        float slowing = target;
        for (int tick = 0; tick < 500; tick++) {
            float next = TransitRules.approach(slowing, 0.0F);
            check(next < slowing + 1.0e-4F, "a vehicle sped up while slowing down");
            check(next >= 0.0F, "a vehicle slowed past a standstill and went backwards");
            slowing = next;
        }
        check(slowing < 0.1F, "a vehicle never comes to rest");
        check(TransitRules.TURN_RATE > 0.0F && TransitRules.TURN_RATE < 90.0F,
                "a vehicle turns " + TransitRules.TURN_RATE + " degrees a tick, which "
                        + "is either no steering at all or a spin");
        System.out.println("  tank: 200,000 random refuellings, none over the tank and "
                + "none that took fuel out; and a vehicle with weight in both "
                + "directions");
    }

    private static void theRoads() {
        // A road has to pay for itself, and the better the vehicle the sooner.
        int cart = TransitRules.tripsToPayOff(TransitRules.CART,
                TransitRules.Surface.GROUND, 500.0);
        int hauler = TransitRules.tripsToPayOff(TransitRules.HAULER,
                TransitRules.Surface.GROUND, 500.0);
        check(cart > 0 && cart < Integer.MAX_VALUE,
                "laying a road never pays for itself with a cart, so the first vehicle "
                        + "and the first road do not belong to each other");
        // The arithmetic's own answer, and not the one that was expected. A
        // road saves a fixed fraction of the time a journey takes, so it saves
        // *more absolute time* to whatever is slowest: the worse your vehicle,
        // the sooner laying a road pays for itself. Which makes a road early
        // infrastructure rather than a reward, and is worth stating on purpose
        // now that it has been found rather than quietly matching whichever
        // way the numbers happened to fall.
        check(cart < hauler,
                "a road pays for itself no sooner behind a cart than behind a hauler; "
                        + "a road saves a share of the journey, so it is worth most to "
                        + "whatever is slowest, and a pack where that is not true has "
                        + "made roads a reward for already being fast");
        check(TransitRules.tripsToPayOff(TransitRules.CART, TransitRules.Surface.ROAD,
                500.0) == Integer.MAX_VALUE,
                "laying a road over a road pays for itself, so the arithmetic is "
                        + "counting something that is not there");
        check(TransitRules.tripsToPayOff(TransitRules.CART, TransitRules.Surface.GROUND,
                0.0) == 0, "a road of no length takes trips to pay off");

        for (double blocks = 50.0; blocks <= 5_000.0; blocks += 50.0) {
            int trips = TransitRules.tripsToPayOff(TransitRules.TRUCK,
                    TransitRules.Surface.GROUND, blocks);
            check(trips > 0, "a road of " + blocks + " blocks pays off before it is laid");
            check(trips < 200, "a road of " + blocks + " blocks takes " + trips
                    + " round trips to pay off, which nobody will ever do");
        }
        System.out.println("  roads: every length from 50 to 5,000 blocks pays for "
                + "itself, and the worse the vehicle the sooner");
    }
}
