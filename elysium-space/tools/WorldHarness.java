package com.elysium.space.world;

import java.util.HashSet;
import java.util.Set;

/**
 * The world table, executed without a game.
 *
 * <ol>
 *   <li>Five worlds, each once, the paradise among them.</li>
 *   <li>No air means full vacuum; breathable air means none.</li>
 *   <li>The Emerald Bastion has no hazards and no hostile spawns.</li>
 *   <li>Every intensity is in (0, 1]; every hazard is one the pipeline has.</li>
 *   <li>Gravity: the attribute modifier turns vanilla's 0.08 into exactly the
 *       world's gravity, and no world is weightless or crushing.</li>
 *   <li>A filled room clears vacuum everywhere and corrosion only where the air
 *       is toxic; never heat, cold, radiation or pressure.</li>
 * </ol>
 */
public final class WorldHarness {

    private static int checks;
    private static int failures;

    private static boolean FLOOR_Y_OK(StationRules.Size size) {
        return StationRules.FLOOR_Y + size.sizeY < 320 && StationRules.FLOOR_Y >= 0;
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL: " + what);
        }
    }

    public static void main(String[] args) {
        Set<String> known = Set.of(SpaceWorld.VACUUM, SpaceWorld.THERMAL, SpaceWorld.CRYOGENIC,
                SpaceWorld.CORROSIVE, SpaceWorld.RADIOLOGICAL, SpaceWorld.PRESSURE);
        Set<String> names = new HashSet<>();
        for (SpaceWorld w : SpaceWorld.ALL) {
            check(names.add(w.path()), "duplicate world " + w.path());
            check(w.path().matches("[a-z_]+"), "bad id " + w.path());
            check(w.gravity() >= 0.05D && w.gravity() <= 1.5D, w.path() + " gravity " + w.gravity());
            double applied = SpaceWorld.BASE_GRAVITY * (1.0D + w.gravityModifier());
            check(Math.abs(applied - SpaceWorld.BASE_GRAVITY * w.gravity()) < 1e-12,
                    w.path() + " modifier gives " + applied);
            for (var e : w.hazards().entrySet()) {
                check(known.contains(e.getKey()), w.path() + " names unknown hazard " + e.getKey());
                check(e.getValue() > 0.0F && e.getValue() <= 1.0F,
                        w.path() + " " + e.getKey() + " intensity " + e.getValue());
            }
            if (w.air() == SpaceWorld.Air.NONE) {
                check(w.intensity(SpaceWorld.VACUUM) == 1.0F, w.path() + " has no air but not full vacuum");
            }
            if (w.air() == SpaceWorld.Air.BREATHABLE) {
                check(w.intensity(SpaceWorld.VACUUM) == 0.0F, w.path() + " is breathable but has vacuum");
            }
            check(w.clearedByAir(SpaceWorld.VACUUM), "a filled room does not clear vacuum on " + w.path());
            check(w.clearedByAir(SpaceWorld.CORROSIVE) == (w.air() == SpaceWorld.Air.TOXIC),
                    "corrosion cleared wrongly on " + w.path());
            for (String through : new String[] {SpaceWorld.THERMAL, SpaceWorld.CRYOGENIC,
                    SpaceWorld.RADIOLOGICAL, SpaceWorld.PRESSURE}) {
                check(!w.clearedByAir(through), through + " cleared by air on " + w.path());
            }
            check(w.intensity("elysiumlib:nothing") == 0.0F, "absent hazard is not zero");
        }
        check(SpaceWorld.ALL.size() == 5, "five worlds, not " + SpaceWorld.ALL.size());
        SpaceWorld bastion = SpaceWorld.named("emerald_bastion");
        check(bastion != null, "the Emerald Bastion is missing");
        if (bastion != null) {
            check(bastion.hazards().isEmpty(), "the paradise has hazards");
            check(!bastion.hostileSpawns(), "the paradise spawns monsters");
            check(bastion.air() == SpaceWorld.Air.BREATHABLE && bastion.gravity() == 1.0D,
                    "the paradise is not Earthlike");
        }
        check(SpaceWorld.named("pale_moon") != null && SpaceWorld.named("high_orbit") != null
                && SpaceWorld.named("cinderveil") != null && SpaceWorld.named("frostfall") != null,
                "a placeholder world is missing");
        check(SpaceWorld.named("nowhere") == null, "an unknown world was found");
        // Routes: Stellaris's line-distance rule and its fuel factors.
        for (SpaceWorld w : SpaceWorld.ALL) {
            check(SpaceRoute.DISTANCE.containsKey(w.path()), w.path() + " has no distance");
        }
        check(SpaceRoute.DISTANCE.get("overworld") == 0.0D, "home is not at 0");
        check(SpaceRoute.gap("pale_moon", "cinderveil") == 41_000.0D - 384.4D, "gap is not the difference");
        check(SpaceRoute.gap("cinderveil", "pale_moon") == SpaceRoute.gap("pale_moon", "cinderveil"), "gap not symmetric");
        check(SpaceRoute.gap("nowhere", "pale_moon") < 0, "unknown world has a gap");
        check(SpaceRoute.fuelFor("overworld", "pale_moon", SpaceRoute.Fuel.FUEL) == 20, "moon costs 20 mB, got "
                + SpaceRoute.fuelFor("overworld", "pale_moon", SpaceRoute.Fuel.FUEL));
        check(SpaceRoute.canTravel("overworld", "frostfall", SpaceRoute.BASIC_TANK_MB, SpaceRoute.Fuel.FUEL),
                "a basic tank of fuel does not reach Frostfall");
        check(!SpaceRoute.canTravel("overworld", "emerald_bastion", SpaceRoute.BASIC_TANK_MB, SpaceRoute.Fuel.FUEL),
                "a basic tank reaches the Bastion; it is meant to need an upgrade");
        check(SpaceRoute.canTravel("overworld", "emerald_bastion", SpaceRoute.BASIC_TANK_MB, SpaceRoute.Fuel.URANIUM)
                || SpaceRoute.canTravel("overworld", "emerald_bastion", SpaceRoute.BASIC_TANK_MB * 2, SpaceRoute.Fuel.FUEL),
                "the Bastion is unreachable by any upgrade");
        check(!SpaceRoute.canTravel("pale_moon", "pale_moon", 99999, SpaceRoute.Fuel.FUEL), "a trip to where you are");
        check(!SpaceRoute.canTravel("overworld", "pale_moon", 19, SpaceRoute.Fuel.FUEL), "19 mB reached the moon");
        java.util.List<String> near = SpaceRoute.reachable("overworld", SpaceRoute.BASIC_TANK_MB, SpaceRoute.Fuel.FUEL);
        check(near.equals(java.util.List.of("high_orbit", "pale_moon", "cinderveil", "frostfall")),
                "reachable from home is " + near);
        double last = 0.0D;
        for (SpaceRoute.Fuel f : SpaceRoute.Fuel.values()) {
            check(f.megametresPerMb > last, "fuels not ordered by strength at " + f);
            last = f.megametresPerMb;
        }
        for (String a : SpaceRoute.DISTANCE.keySet()) {
            for (String b : SpaceRoute.DISTANCE.keySet()) {
                int need = SpaceRoute.fuelFor(a, b, SpaceRoute.Fuel.FUEL);
                check(need * SpaceRoute.Fuel.FUEL.megametresPerMb >= SpaceRoute.gap(a, b),
                        "fuel for " + a + " to " + b + " falls short");
                check(a.equals(b) || !SpaceRoute.canTravel(a, b, need - 1, SpaceRoute.Fuel.FUEL),
                        "one mB less still reaches " + b + " from " + a);
            }
        }

        // Suit air.
        check(SuitAir.CAPACITY == 3000, "suit capacity is not Stellaris's 3000");
        check(SuitAir.step(3000, true, false) == 3000 - SuitAir.DRAIN_PER_SECOND, "exposure does not drain");
        check(SuitAir.step(100, false, false) == 100, "safe air changed the tank");
        check(SuitAir.step(0, true, false) == 0, "a tank went below empty");
        check(SuitAir.step(2990, true, true) == 3000, "a filled room does not refill to the top");
        check(SuitAir.step(-50, false, false) == 0 && SuitAir.step(99999, false, false) == 3000, "bad air not clamped");
        check(SuitAir.addCanister(2500) == 3000 && SuitAir.addCanister(0) == SuitAir.CANISTER, "canister arithmetic");
        check(SuitAir.suffocating(0, true, false), "an empty tank in vacuum does not suffocate");
        check(!SuitAir.suffocating(0, true, true), "suffocating inside a filled room");
        check(!SuitAir.suffocating(0, false, false), "suffocating where there is no vacuum");
        check(!SuitAir.suffocating(1, true, false), "suffocating with air left");
        int air = SuitAir.CAPACITY, seconds = 0;
        while (!SuitAir.suffocating(air, true, false) && seconds < 100000) {
            air = SuitAir.step(air, true, false);
            seconds++;
        }
        check(seconds == SuitAir.CAPACITY / SuitAir.DRAIN_PER_SECOND, "a full suit lasts " + seconds + " s");
        check(seconds >= 300, "a full suit lasts under five minutes");

        // Machines.
        check(MachineRules.refineryBatches(100, 10_000, 10_000, 10_000) == 10, "100 oil is ten batches");
        check(MachineRules.refineryBatches(100, 10_000, 10_000, 250) == 2, "power limits the refinery");
        check(MachineRules.refineryBatches(100, 4, 10_000, 10_000) == 0, "a full fuel tank stops the refinery");
        check(MachineRules.refineryBatches(9, 10_000, 10_000, 10_000) == 0, "less than a batch of oil ran");
        check(MachineRules.OIL_PER_BATCH == 10 && MachineRules.FUEL_PER_BATCH == 5
                && MachineRules.REFINERY_FE_PER_BATCH == 100, "refinery recipe is not Stellaris's");
        check(MachineRules.WATER_PER_BATCH == 5 && MachineRules.HYDROGEN_PER_BATCH == 10
                && MachineRules.OXYGEN_PER_BATCH == 5 && MachineRules.SEPARATOR_FE_PER_BATCH == 50,
                "separator recipe is not Stellaris's");
        check(MachineRules.separatorBatches(50, 6000, 6000, 10_000) == 10, "50 water is ten batches");
        check(MachineRules.separatorBatches(50, 6000, 4, 10_000) == 0, "a full oxygen tank stops the separator");
        check(MachineRules.separatorBatches(50, 6000, 6000, 99) == 1, "power limits the separator");
        check(MachineRules.distributorOxygen(0, 0) == 0, "an empty room costs air");
        check(MachineRules.distributorOxygen(0, 1) == 1 && MachineRules.distributorOxygen(0, 400) == 1
                && MachineRules.distributorOxygen(0, 401) == 2, "upkeep does not round up");
        check(MachineRules.distributorOxygen(100, 0) == 100, "each new position costs 1 mB");
        check(MachineRules.distributorEnergy(100) == 500 && MachineRules.distributorEnergy(-3) == 0,
                "distributor power");
        check(MachineRules.solarPerTick(false, true) == 0, "solar at night");
        check(MachineRules.solarPerTick(true, false) == 10 && MachineRules.solarPerTick(true, true) == 20,
                "solar rates");
        int scans = MachineRules.DISTRIBUTOR_OXYGEN_TANK / MachineRules.distributorOxygen(0, 4096);
        check(scans * 2 >= 60 * 5, "a full distributor keeps a large room under five minutes (" + scans + " scans)");
        check(!SpaceRoute.canTravel("overworld", "emerald_bastion", SpaceRoute.BASIC_TANK_MB, SpaceRoute.Fuel.FUEL)
                && SpaceRoute.canTravel("overworld", "emerald_bastion", SpaceRoute.LARGE_TANK_MB, SpaceRoute.Fuel.FUEL),
                "the large tank does not open the Bastion");

        // Motors: hydrogen goes further on the same tank, and still needs the large tank for the Bastion.
        check(SpaceRoute.fuelFor("overworld", "frostfall", SpaceRoute.Fuel.HYDROGEN)
                < SpaceRoute.fuelFor("overworld", "frostfall", SpaceRoute.Fuel.FUEL), "hydrogen is no better than fuel");
        check(!SpaceRoute.canTravel("overworld", "emerald_bastion", SpaceRoute.BASIC_TANK_MB, SpaceRoute.Fuel.HYDROGEN),
                "a basic tank of hydrogen reaches the Bastion; the tank upgrade would be pointless");
        check(SpaceRoute.canTravel("overworld", "emerald_bastion", SpaceRoute.LARGE_TANK_MB, SpaceRoute.Fuel.HYDROGEN),
                "a large tank of hydrogen does not reach the Bastion");

        // Diesel, pumping and deposits.
        check(MachineRules.refineryBatches(100, 10_000, 4, 10_000) == 0, "a full diesel tank does not stop the refinery");
        check(MachineRules.DIESEL_FE_PER_BURN / MachineRules.DIESEL_BURN_TICKS == 2 * MachineRules.COAL_FE_PER_TICK,
                "diesel is not twice coal");
        check(MachineRules.pumped(5000, 10_000, 100) == 10, "a pump tick is not 10 mB");
        check(MachineRules.pumped(3, 10_000, 100) == 3, "pumping more than the deposit holds");
        check(MachineRules.pumped(5000, 4, 100) == 4, "pumping past the tank");
        check(MachineRules.pumped(5000, 10_000, 9) == 0, "pumping without power");
        check(MachineRules.pumped(0, 10_000, 100) == 0, "pumping a dry chunk");
        int dry = 0, wet = 0;
        for (int x = -60; x < 60; x++) {
            for (int z = -60; z < 60; z++) {
                int d = MachineRules.oilDeposit(12345L, x, z);
                check(d == MachineRules.oilDeposit(12345L, x, z), "the same chunk changed its deposit");
                if (d == 0) {
                    dry++;
                } else {
                    wet++;
                    check(d >= MachineRules.OIL_MIN && d <= MachineRules.OIL_MAX, "deposit out of range: " + d);
                }
            }
        }
        check(dry > 6000 && wet > 6000, "deposits are not about half the chunks: " + wet + " of 14400");
        check(MachineRules.oilDeposit(1L, 5, 5) != MachineRules.oilDeposit(2L, 5, 5)
                || MachineRules.oilDeposit(1L, 6, 5) != MachineRules.oilDeposit(2L, 6, 5), "the seed changes nothing");

        // Jet suit.
        check(JetRules.next(JetRules.Mode.OFF) == JetRules.Mode.NORMAL && JetRules.next(JetRules.Mode.HOVER) == JetRules.Mode.OFF,
                "modes do not cycle off, normal, hover");
        double vy = -0.3D;
        for (int i = 0; i < 40; i++) {
            vy = JetRules.vertical(JetRules.Mode.NORMAL, vy, true, false, 100);
        }
        check(vy == JetRules.MAX_CLIMB, "thrust does not settle at the climb limit: " + vy);
        check(JetRules.vertical(JetRules.Mode.HOVER, -0.8D, false, false, 100) == JetRules.HOVER_SINK, "hover does not hold the fall");
        check(JetRules.vertical(JetRules.Mode.NORMAL, -0.8D, false, false, 100) == -0.8D, "normal mode hovers");
        check(JetRules.vertical(JetRules.Mode.NORMAL, -0.8D, true, false, 0) == -0.8D, "thrust without fuel");
        check(JetRules.vertical(JetRules.Mode.OFF, -0.8D, true, false, 100) == -0.8D, "thrust while off");
        check(JetRules.burn(JetRules.Mode.NORMAL, true, false, 100, 1) == 1 && JetRules.burn(JetRules.Mode.NORMAL, false, false, 100, 1) == 0,
                "thrust burn");
        int hoverBurn = 0;
        for (long tk = 0; tk < 100; tk++) {
            hoverBurn += JetRules.burn(JetRules.Mode.HOVER, false, false, 100, tk);
        }
        check(hoverBurn == 50, "hovering does not burn one mB every two ticks: " + hoverBurn);
        check(JetRules.burn(JetRules.Mode.HOVER, false, true, 100, 0) == 0, "hovering on the ground burns");
        check(!JetRules.flying(JetRules.Mode.NORMAL, false, false, 100) && JetRules.flying(JetRules.Mode.HOVER, false, false, 100)
                && !JetRules.flying(JetRules.Mode.HOVER, false, false, 0), "which states cancel falls");
        check(JetRules.TANK / JetRules.THRUST_MB_PER_TICK >= 20 * 60, "a full jet tank thrusts under a minute");

        // The mobs: Stellaris's numbers, each at home on a world that exists, none on the Bastion.
        java.util.Set<String> mobIds = new java.util.HashSet<>();
        for (MobRules.Mob m : MobRules.ALL) {
            check(mobIds.add(m.id()), "duplicate mob " + m.id());
            check(m.id().matches("[a-z_]+") && !m.id().equals(m.portedFrom()), m.id() + " keeps its old name");
            check(SpaceWorld.named(m.home()) != null, m.id() + " lives on " + m.home() + ", which is not a world");
            check(!m.home().equals("emerald_bastion") || !m.hostile(), m.id() + " hunts on the paradise world");
            check(m.health() > 0 && m.speed() > 0 && m.followRange() >= 16, m.id() + " has impossible stats");
            check(m.hostile() == (m.damage() > 0), m.id() + ": only hunters deal damage");
            check(m.width() >= 0.3F && m.width() <= 4.0F && m.height() >= 0.5F && m.height() <= 4.0F,
                    m.id() + " size " + m.width() + "x" + m.height());
            check(m.knockbackResistance() >= 0 && m.knockbackResistance() <= 1, m.id() + " knockback resistance");
            check(MobRules.named(m.id()) == m, "MobRules.named(" + m.id() + ")");
        }
        check(MobRules.ALL.size() == 7, "Stellaris ported seven mobs, found " + MobRules.ALL.size());
        check(MobRules.named("alien") == null, "a Stellaris name finds a mob");
        check(MobRules.VOID_CRAWLER.health() == 40 && MobRules.VOID_CRAWLER.damage() == 9
                && MobRules.CINDER_BOAR.knockbackResistance() == 0.6D, "mob stats are not Stellaris's");
        check(MobRules.crawlerDeflects("Arrow") && MobRules.crawlerDeflects("SpectralArrow")
                && !MobRules.crawlerDeflects("ThrownTrident") && !MobRules.crawlerDeflects("RimeSpit"),
                "the crawler turns arrows and only arrows");
        check(MobRules.SPIT_LIFETIME > 0 && MobRules.SPIT_LIFETIME * MobRules.SPIT_SPEED >= MobRules.SPIT_RANGE,
                "a rime spit melts before it can reach the range it is spat from");
        // Stations: each slot distinct, far enough apart, clear of the origin; upgrades only grow.
        java.util.Set<String> slots = new java.util.HashSet<>();
        int biggest = 0;
        for (StationRules.Size size : StationRules.Size.values()) {
            biggest = Math.max(biggest, Math.max(size.sizeX, size.sizeZ));
            check(StationRules.Size.named(size.id) == size, "station size " + size.id);
            check(FLOOR_Y_OK(size), size.id + " station does not fit between the floor and the build limit");
        }
        check(StationRules.SPACING >= 4 * biggest, "stations could stand in each other's way");
        for (int i = 0; i < 4096; i++) {
            int[] c = StationRules.slot(i);
            check(slots.add(c[0] + "," + c[1]), "slot " + i + " repeats " + c[0] + "," + c[1]);
            check(Math.abs(c[1]) >= StationRules.SPACING, "slot " + i + " is at the arrival origin");
        }
        check(SpaceWorld.named(StationRules.WORLD) != null, "stations stand in a world that does not exist");
        check(StationRules.build(null, StationRules.Size.SMALL) == StationRules.Build.NEW
                && StationRules.build(StationRules.Size.SMALL, StationRules.Size.BIG) == StationRules.Build.UPGRADE
                && StationRules.build(StationRules.Size.BIG, StationRules.Size.SMALL) == StationRules.Build.ALREADY
                && StationRules.build(StationRules.Size.MEDIUM, StationRules.Size.MEDIUM) == StationRules.Build.ALREADY,
                "station upgrades must only grow");
        for (StationRules.Size a : StationRules.Size.values()) {
            for (StationRules.Size b : StationRules.Size.values()) {
                if (b.ordinal() > a.ordinal()) {
                    check(b.steel > a.steel && b.lunite > a.lunite, b.id + " costs no more than " + a.id);
                    check(b.sizeX >= a.sizeX && b.sizeZ >= a.sizeZ, b.id + " does not cover " + a.id + "'s footprint");
                }
            }
        }
        check(MobRules.SPIT_DAMAGE > 0 && MobRules.SPIT_CHILL_TICKS > 0 && MobRules.SPIT_INTERVAL > 0, "spit numbers");

        // --- waygates ---------------------------------------------------
        //
        // The ring is walked by position rather than drawn, so the things
        // worth pinning are that it closes, that it covers each square once,
        // and that the opening a player walks through is really open.
        check(GateRules.SIZE % 2 == 1, "a ring with no middle has nowhere for the anchor");
        check(GateRules.SIZE >= 5, "a ring smaller than five has an opening nobody can see through");
        check(GateRules.OPENING == GateRules.SIZE - 2, "the opening does not fit the ring");
        check(GateRules.perimeter() == 4 * GateRules.SIZE - 4, "the ring's perimeter is wrong");
        check(GateRules.frameBlocks() == GateRules.perimeter() - 1,
                "the anchor is counted as frame, so a finished gate is one block short");
        check(GateRules.anchorAcross() == GateRules.SIZE / 2, "the anchor is not centred");

        Set<String> squares = new HashSet<>();
        int anchors = 0;
        for (GateRules.Offset at : GateRules.ring()) {
            check(squares.add(at.across() + "," + at.up()),
                    "the ring visits (" + at.across() + "," + at.up() + ") twice");
            check(Math.abs(at.across()) <= GateRules.SIZE / 2 && at.up() >= 0
                            && at.up() < GateRules.SIZE,
                    "the ring leaves its own square at (" + at.across() + "," + at.up() + ")");
            boolean edge = Math.abs(at.across()) == GateRules.SIZE / 2
                    || at.up() == 0 || at.up() == GateRules.SIZE - 1;
            check(edge, "(" + at.across() + "," + at.up() + ") is on the ring and not on its edge");
            if (at.isAnchor()) {
                anchors++;
            }
        }
        check(squares.size() == GateRules.perimeter(), "the ring does not close");
        check(anchors == 1, "a ring with " + anchors + " anchors");
        check(GateRules.frame().size() == GateRules.frameBlocks(),
                "the frame list and the frame count disagree");
        for (GateRules.Offset at : GateRules.frame()) {
            check(!at.isAnchor(), "the anchor's own square is required to be frame");
        }

        Set<String> inside = new HashSet<>();
        for (GateRules.Offset at : GateRules.opening()) {
            check(inside.add(at.across() + "," + at.up()),
                    "the opening counts a square twice");
            check(!squares.contains(at.across() + "," + at.up()),
                    "a square is both frame and opening at (" + at.across() + "," + at.up() + ")");
            check(Math.abs(at.across()) < GateRules.SIZE / 2 && at.up() > 0
                            && at.up() < GateRules.SIZE - 1,
                    "the opening reaches the frame");
        }
        check(inside.size() == GateRules.OPENING * GateRules.OPENING,
                "the opening is not " + GateRules.OPENING + " by " + GateRules.OPENING);

        check(GateRules.ARRIVAL_UP >= 1, "a traveller arrives inside the anchor block");
        check(GateRules.ARRIVAL_UP < GateRules.SIZE - 1, "a traveller arrives above the gate");
        check(GateRules.SETTLE > 0 && GateRules.SETTLE <= 20,
                "the gate charges a toll; it is paid for once, in the building of it");
        check(!GateRules.ready(0L, 0L), "a gate answered twice in the same tick");
        check(GateRules.ready(GateRules.SETTLE, 0L), "a gate never answers again");
        for (long now = 0; now < 200; now++) {
            check(GateRules.ready(now, now - GateRules.SETTLE), "a settled gate refused at " + now);
            check(!GateRules.ready(now, now), "an unsettled gate answered at " + now);
        }

        check(GateRules.nameOr("  ", "fallback").equals("fallback"), "a blank name was kept");
        check(GateRules.nameOr(null, "fallback").equals("fallback"), "a null name was kept");
        check(GateRules.nameOr("  Home  ", "x").equals("Home"), "a name was not trimmed");
        check(GateRules.nameOr("x".repeat(200), "y").length() == GateRules.NAME_LIMIT,
                "a name was not cut to fit, so one gate can push the list off the screen");
        check(GateRules.NAME_LIMIT >= 8, "a gate cannot be given a usable name");

        // ---- freight: what it costs to send a crate somewhere -------------
        //
        // The three claims the logistics layer makes, executed: a full rocket
        // is cheaper per stack than an empty one, distance always costs time
        // and never costs unbounded time, and nothing is free.
        for (String from : SpaceRoute.DISTANCE.keySet()) {
            for (String to : SpaceRoute.DISTANCE.keySet()) {
                if (from.equals(to)) {
                    continue;
                }
                int route = SpaceRoute.fuelFor(from, to, SpaceRoute.Fuel.FUEL);
                check(route > 0, "a route with no fuel cost: " + from + " to " + to);
                int empty = FreightRules.fuelFor(route, 0);
                int full = FreightRules.fuelFor(route, FreightRules.SLOTS);
                check(empty >= route,
                        "a cargo rocket flies cheaper than the same rocket with a "
                                + "pilot in it, " + from + " to " + to);
                check(full > empty, "cargo rides free, " + from + " to " + to);
                check(FreightRules.perStack(route, FreightRules.SLOTS)
                                < FreightRules.perStack(route, 1),
                        "a full rocket costs more per stack than a nearly empty one, "
                                + "so there is no reason to fill one: " + from + " to "
                                + to);
                int dearer = -1;
                for (int stacks = 0; stacks <= FreightRules.SLOTS; stacks++) {
                    int cost = FreightRules.fuelFor(route, stacks);
                    check(cost >= dearer,
                            "loading one more stack made a run cheaper at " + stacks);
                    dearer = cost;
                }
                check(FreightRules.fuelFor(route, 500)
                                == FreightRules.fuelFor(route, FreightRules.SLOTS),
                        "more cargo than the rocket holds was charged for");
                check(FreightRules.fuelFor(route, -5) == empty,
                        "a negative load was charged for");

                double gap = SpaceRoute.gap(from, to);
                int ticks = FreightRules.flightTicks(gap);
                check(ticks >= FreightRules.LEAST_TICKS,
                        "a run arrived before it left, " + from + " to " + to);
                check(ticks <= FreightRules.MOST_TICKS,
                        "a run takes longer than the stated longest, " + from + " to "
                                + to);
            }
        }
        check(FreightRules.fuelFor(-1, 4) == -1, "a route nobody knows was priced");
        check(FreightRules.flightTicks(0.0D) == FreightRules.LEAST_TICKS,
                "a trip to where you already are took longer than the shortest run");
        check(FreightRules.flightTicks(-50.0D) == FreightRules.LEAST_TICKS,
                "a negative distance took a negative time");
        int slower = -1;
        for (double gap = 0.0D; gap < 200_000.0D; gap += 137.0D) {
            int ticks = FreightRules.flightTicks(gap);
            check(ticks >= slower, "a further run arrived sooner, at " + gap);
            check(ticks < FreightRules.MOST_TICKS + 1,
                    "the flight time is unbounded, at " + gap);
            slower = ticks;
        }
        // Strictly, not merely never backwards: a table that returned the same
        // number for every distance would satisfy "no further run is sooner"
        // and would mean distance had stopped existing.
        check(FreightRules.flightTicks(0.0D) < FreightRules.flightTicks(1_000.0D)
                        && FreightRules.flightTicks(1_000.0D)
                        < FreightRules.flightTicks(50_000.0D),
                "every world is the same distance away, so there is nothing to plan");
        check(FreightRules.flightTicks(FreightRules.HALFWAY)
                        == (FreightRules.LEAST_TICKS + FreightRules.MOST_TICKS) / 2,
                "the halfway distance is not halfway between the shortest run and the "
                        + "longest, so the number a reader is given does not mean what "
                        + "it says");
        check(FreightRules.MOST_TICKS > FreightRules.LEAST_TICKS * 4,
                "the far worlds are barely slower than the near ones, so distance "
                        + "stops being a thing to plan around");
        check(FreightRules.IN_FLIGHT >= 1 && FreightRules.IN_FLIGHT <= 10,
                "a terminal is either blocked or an infinite pipe");
        check(FreightRules.TANK_MB >= SpaceRoute.fuelFor("overworld", "pale_moon",
                        SpaceRoute.Fuel.FUEL),
                "a terminal cannot hold enough fuel to reach the nearest world");
        check(FreightRules.SLOTS == 27, "a terminal is not a chest, so a player has to "
                + "learn a new amount for no reason");
        for (long now = 0L; now < 4000L; now += 97L) {
            check(!FreightRules.landed(now, now, FreightRules.LEAST_TICKS),
                    "a shipment landed the moment it left, at " + now);
            check(FreightRules.landed(now, now + FreightRules.LEAST_TICKS,
                            FreightRules.LEAST_TICKS),
                    "a shipment never landed, at " + now);
            check(FreightRules.remaining(now, now + FreightRules.LEAST_TICKS,
                            FreightRules.LEAST_TICKS) == 0,
                    "a landed shipment still says it is coming, at " + now);
            check(FreightRules.remaining(now, now, FreightRules.LEAST_TICKS) > 0,
                    "a shipment that just left says it has arrived, at " + now);
        }

        if (failures > 0) {
            System.out.println(failures + " of " + checks + " checks failed");
            System.exit(1);
        }
        System.out.println(checks + " checks: 5 worlds, air and vacuum agree, the Bastion is a paradise, "
                + "gravity exact, a filled room clears only what air can; routes cost the gap, the Bastion needs an upgrade; a full suit lasts ten minutes; machines follow the Stellaris recipes; seven mobs with Stellaris's stats; station slots never meet; "
                + "a waygate ring closes, opens and charges nothing; and freight is "
                + "cheaper by the crate, slower by the parsec and never free");
    }
}
