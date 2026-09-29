"""Declared sources for elysiumspace, elysiumwilds and elysiumtransit.

What only Java code (or data the checker does not read: noise settings, the
world preset) hands out in these three modules, each pinned to the file and the
words that do it. Loaded by check_obtainable.py through OBTAINABLE_SOURCES_EXTRA.
"""

SPACE = "elysium-space/src/main/java/com/elysium/space/"
SPACE_DATA = "elysium-space/src/main/resources/data/elysiumspace/"
WILDS = "elysium-wilds/src/main/java/com/elysium/wilds/"
WILDS_DATA = "elysium-wilds/src/main/resources/data/"
TRANSIT = "elysium-transit/src/main/java/com/elysium/transit/"

# A rocket stands on a Launch Pad (RocketItem) and burns fuel put in by hand
# (RocketEntity.fuelIn): canisters, bottled at a Fuel Refinery.
ROCKET = ["elysiumspace:rocket", "elysiumspace:launch_pad", "elysiumspace:fuel_canister"]
# Vacuum is 3 damage a second at full strength (SpaceHazards, damagePerSecond):
# seven seconds kills. A full Space Suit seals it (SuitAir), so the airless
# worlds need one.
SUIT = ["elysiumspace:space_suit_helmet", "elysiumspace:space_suit_chestplate",
        "elysiumspace:space_suit_leggings", "elysiumspace:space_suit_boots"]
# A pumpjack pumps its chunk's deposit on FE from a coal generator beside it
# (MachineBlockEntity.pushEnergy).
OIL_AND_POWER = ["elysiumspace:pumpjack", "elysiumspace:coal_generator"]

# The overworld biomes the wilds put in the overworld: the world preset
# (data/minecraft/worldgen/world_preset/normal.json, generated from
# OverworldRules) lists them ahead of vanilla's entries. 25 surface families in
# five reliefs, and 15 underground ones.
WILD_FAMILIES = ["acacia", "amber", "ash", "bayou", "boreal", "chalk", "cinder", "clover", "ember", "fen",
                 "frost", "glacier", "gold", "green", "heath", "hoar", "marsh", "mire", "palm", "pine",
                 "reed", "rime", "sleet", "steam", "sun"]
WILD_RELIEFS = ["crag", "scarp", "hill", "wold", "flat"]
WILD_UNDERGROUND = ["basaltcavern", "chalkcavern", "dripwell", "emberhollow", "frostvault", "geyserwell",
                    "icewell", "mossgrotto", "ochrecavern", "pinehollow", "rimegrotto", "rootgrotto",
                    "slatecavern", "springvault", "steamwell"]


def declare(declared):
    # ------------------------------------------------------------------
    # The rocket and the worlds it reaches
    # ------------------------------------------------------------------
    declared("creature:elysiumspace:rocket", ["elysiumspace:rocket", "elysiumspace:launch_pad"],
             SPACE + "item/RocketItem.java", "RocketEntity rocket = SpaceEntities.ROCKET.get().create(level);",
             "the rocket item, used on a Launch Pad, stands a rocket on it")

    # SpaceRoute: a trip burns (gap in Mm) / 19.22 mB of plain fuel, and a basic
    # tank holds 3000 mB. High Orbit is 1 mB away, the Pale Moon 20, Cinderveil
    # 2134, Frostfall 2899: all inside one tank of canisters. The destination
    # screen offers only what the fuel reaches; travelTo re-checks it.
    # The library's own hazards there (cold, heat, acid, pressure) are answered
    # as anywhere: a complete matching set, a filed claim or a ration's ward.
    route = SPACE + "entity/RocketEntity.java"
    offer = "List<String> options = SpaceRoute.reachable(from, fuel(), motor());"
    declared("dimension:elysiumspace:high_orbit", ROCKET + SUIT, route, offer,
             "a fuelled rocket climbs and offers High Orbit (1 mB of fuel); its vacuum needs the space suit")
    declared("dimension:elysiumspace:pale_moon", ROCKET + SUIT, route, offer,
             "a fuelled rocket offers the Pale Moon (20 mB of fuel); its vacuum needs the space suit")
    declared("dimension:elysiumspace:cinderveil", ROCKET, route, offer,
             "a rocket with 2134 mB (nine canisters) reaches Cinderveil; no vacuum, its heat, acid and "
             "pressure are the library's hazards")
    declared("dimension:elysiumspace:frostfall", ROCKET + SUIT, route, offer,
             "a rocket with 2899 mB (twelve canisters, a full basic tank) reaches Frostfall; its thin air "
             "and radiation need the space suit")
    # 92 000 Mm is 4787 mB of plain fuel: past the basic tank on every motor, so
    # the Large Fuel Tank (6000 mB), fitted by right-clicking the rocket.
    declared("dimension:elysiumspace:emerald_bastion", ROCKET + ["elysiumspace:large_fuel_tank"],
             route, "setTank(SpaceRoute.LARGE_TANK_MB);",
             "the Large Fuel Tank doubles the rocket's tank to 6000 mB, which reaches the Bastion (4787 mB)")

    # ------------------------------------------------------------------
    # Fuel, and the other fluids, taken out of machines by hand
    # ------------------------------------------------------------------
    machine_block = SPACE + "machine/MachineBlock.java"
    fluids = SPACE + "registry/SpaceFluids.java"
    # Oil: a bucket dipped in an oil lake (oil_lake_overworld, in every #c:is_desert
    # biome) picks the source up, as vanilla buckets do water; or a bucket on a
    # powered pumpjack standing on a chunk with a deposit.
    declared("elysiumspace:oil_bucket", ["minecraft:bucket", "elysiumspace:oil"],
             fluids, "fp.block(block).bucket(bucket)",
             "an empty bucket used on an oil source block picks it up as an oil bucket")
    declared("elysiumspace:oil_bucket", ["minecraft:bucket"] + OIL_AND_POWER,
             machine_block, "FluidUtil.interactWithFluidHandler(player, hand, machine.fluids())",
             "an empty bucket on a pumpjack holding 1000 mB of oil fills with it")
    # The refinery: an oil bucket poured into it (MachineTanks fills its input
    # tank), a coal generator beside it for FE; 10 oil makes 5 fuel and 5 diesel.
    refinery = ["elysiumspace:fuel_refinery", "elysiumspace:oil_bucket", "elysiumspace:coal_generator"]
    declared("elysiumspace:fuel_canister", refinery + ["minecraft:glass_bottle"],
             machine_block, "SpaceItems.FUEL_CANISTER.get() : SpaceItems.OXYGEN_CANISTER.get()",
             "a glass bottle on a Fuel Refinery holding 250 mB of fuel bottles a fuel canister")
    declared("elysiumspace:fuel_bucket", refinery + ["minecraft:bucket"],
             machine_block, "FluidUtil.interactWithFluidHandler(player, hand, machine.fluids())",
             "an empty bucket on a Fuel Refinery holding 1000 mB of fuel fills with it")
    # MachineTanks drains its first non-empty output, fuel before diesel: two oil
    # buckets refine to 1000 mB of each, the first bucket takes the fuel and the
    # second the diesel.
    declared("elysiumspace:diesel_bucket", refinery + ["minecraft:bucket"],
             machine_block, "FluidUtil.interactWithFluidHandler(player, hand, machine.fluids())",
             "an empty bucket on a Fuel Refinery whose fuel tank is empty fills with its diesel")
    # Oxygen: a glass bottle on a Water Separator (or a filled Oxygen Distributor)
    # holding 1000 mB bottles a canister (SuitAir.CANISTER).
    declared("elysiumspace:oxygen_canister",
             ["elysiumspace:water_separator", "minecraft:glass_bottle", "minecraft:water_bucket",
              "elysiumspace:coal_generator"],
             machine_block, "SpaceItems.FUEL_CANISTER.get() : SpaceItems.OXYGEN_CANISTER.get()",
             "a glass bottle on a Water Separator holding 1000 mB of oxygen bottles an oxygen canister")
    declared("elysiumspace:hydrogen_canister",
             ["elysiumspace:water_separator", "minecraft:glass_bottle", "minecraft:water_bucket",
              "elysiumspace:coal_generator"],
             machine_block, "ItemStack out = new ItemStack(SpaceItems.HYDROGEN_CANISTER.get());",
             "sneak-using a glass bottle on a Water Separator holding 250 mB of hydrogen bottles a canister")
    # A fluid's bucket pours its source block (the fluid's block binding).
    declared("placed:elysiumspace:fuel", ["elysiumspace:fuel_bucket"],
             fluids, "fp.block(block).bucket(bucket)", "a fuel bucket pours a fuel source block")
    declared("placed:elysiumspace:diesel", ["elysiumspace:diesel_bucket"],
             fluids, "fp.block(block).bucket(bucket)", "a diesel bucket pours a diesel source block")

    # ------------------------------------------------------------------
    # Terrain the noise settings lay down (the checker reads features, not these)
    # ------------------------------------------------------------------
    noise = SPACE_DATA + "worldgen/noise_settings/"
    for world, dim, blocks in (
            ("pale_moon", "pale_moon", ["pale_stone", "pale_regolith"]),
            ("frostfall", "frostfall", ["frost_stone", "frost_regolith"]),
            ("cinderveil", "cinderveil", ["cinder_stone", "cinder_sand", "cinder_sandstone"])):
        for block in blocks:
            declared(f"placed:elysiumspace:{block}", [f"dimension:elysiumspace:{dim}"],
                     noise + world + ".json", f'"Name": "elysiumspace:{block}"',
                     f"the {world} noise settings lay {block} down as terrain")

    # ------------------------------------------------------------------
    # An axe strips emgrove (SpaceEvents.onStrip, over SpaceBlocks.strippable)
    # ------------------------------------------------------------------
    for log, stripped in (("emgrove_log", "stripped_emgrove_log"), ("emgrove_wood", "stripped_emgrove_wood")):
        declared(f"placed:elysiumspace:{stripped}", [f"elysiumspace:{log}", "#minecraft:axes"],
                 SPACE + "event/SpaceEvents.java", "Block stripped = SpaceBlocks.strippable().get(state.getBlock());",
                 f"an axe used on {log} strips it to {stripped}, keeping its axis")

    # ------------------------------------------------------------------
    # Creatures code makes
    # ------------------------------------------------------------------
    declared("creature:elysiumspace:rime_spit", ["creature:elysiumspace:hollow_moonfolk"],
             SPACE + "entity/mob/HollowMoonfolk.java", "RimeSpit.shoot(this, target);",
             "a hollow moonfolk spits rime at what it hunts")

    # ------------------------------------------------------------------
    # Transit: a vehicle item puts its vehicle down
    # ------------------------------------------------------------------
    for rung in ("cart", "truck", "hauler"):
        declared(f"creature:elysiumtransit:{rung}", [f"elysiumtransit:{rung}"],
                 TRANSIT + "item/VehicleItem.java", "VehicleEntity vehicle = type.get().create(level);",
                 f"the {rung} item, used on a block, puts a {rung} down")

    # ------------------------------------------------------------------
    # Wilds: the 140 overworld biomes, written into the overworld's world preset
    # ------------------------------------------------------------------
    preset = WILDS_DATA + "minecraft/worldgen/world_preset/normal.json"
    names = [f"{family}_{relief}" for family in WILD_FAMILIES for relief in WILD_RELIEFS] + WILD_UNDERGROUND
    for name in names:
        declared(f"biome:elysiumwilds:{name}@minecraft:overworld", [],
                 preset, f'"biome":"elysiumwilds:{name}"',
                 "the wilds' world preset lists this biome in the overworld's biome source, ahead of vanilla's")

    # ------------------------------------------------------------------
    # Wilds: the way into the Vale
    # ------------------------------------------------------------------
    # A ring of Vale Frames (four a craft: mossy stone bricks, moss blocks and an
    # ender pearl) round an empty opening 2x3 to 21x21, the Nether portal's shape
    # (PortalRules), lit by using the Surveyor's Compass on any frame of it. The
    # compass is not used up.
    gate = ["elysiumwilds:vale_frame", "elysiumwilds:biome_compass"]
    declared("placed:elysiumwilds:vale_portal", gate,
             WILDS + "item/BiomeCompassItem.java", "ValePortal.light(level, frame);",
             "the Surveyor's Compass used on a closed ring of Vale Frames fills it with the Vale Gate")
    # Stepping in outside the Vale: the first trip through a ring finds the nearest
    # verdant vale (ring nought, the gentlest crossing) within the compass's 12 800
    # blocks and builds a ring there to come back through; every later trip goes to it.
    declared("dimension:elysiumwilds:vale", gate,
             WILDS + "level/ValeTravel.java",
             "teleport(player, server, vale, Vec3.atBottomCenterOf(landing), facing(arrival.axis(), onward));",
             "stepping into a lit Vale Gate outside the Vale puts a player beside a ring in the nearest verdant vale")
