"""
What elysium-agronomy's Java hands out that no data file does.

Loaded by check_obtainable.py through OBTAINABLE_SOURCES_EXTRA. The rosters
(fish and their families, bees and their parents, seeds and their tiers, the
rods, the essences) are read out of the rules' own Java each run, so a fish or
bee added to a roster is declared the day it is added, and a renamed one is not
left behind here.

Creatures stand in for species. A bee of a species in hand (a jar, a bee item
in a hive) and a bee of that species in the air are one jar apart (BeeJarItem
catches and releases), so "creature:<species>_bee" in needs means "has this
species".

gaps_agronomy.md is what was unreached with this file loaded on 29 September
2026, and why. The six gaps it names are fixed in the module itself (recipes for
the rods, the fillet knife, the measuring line and the four bee tools; the first
rung of seeds paid in wheat seeds; a nest generated in every home; an end fish
bucketed at a table), so nothing here had to change for them but two comments.
"""
import os
import re
import sys

NS = "elysiumagronomy"
JAVA = "elysium-agronomy/src/main/java/com/elysium/agronomy"


def _root():
    import __main__
    for guess in (getattr(__main__, "ROOT", None),
                  os.path.dirname(os.path.abspath(sys.argv[0])) if sys.argv and sys.argv[0] else None,
                  os.environ.get("ELYSIUM_MONO"),
                  "C:/Users/weaks/AppData/Local/Temp/Elysium-mono"):
        if guess and os.path.isdir(os.path.join(guess, JAVA)):
            return guess
    raise RuntimeError("sources_agronomy.py cannot find the monorepo (set ELYSIUM_MONO)")


def _java(rel):
    return open(os.path.join(_root(), JAVA, rel), encoding="utf-8").read()


def _between(text, start, end):
    """From start up to and including the first end after it."""
    at = text.index(start)
    return text[at:text.index(end, at) + len(end)]


def _a(word):
    return ("an " if word[:1] in "aeiou" else "a ") + word


# ---------------------------------------------------------------------------
# The rosters, read off the rules
# ---------------------------------------------------------------------------

def fish_roster():
    """[(fish id, family name, water)] in roster order, from FishRules.java."""
    text = _java("rules/FishRules.java")
    families = {const: (name, water.lower()) for const, name, water in re.findall(
        r'public static final Family ([A-Z]+) = new Family\("([a-z_]+)", ([A-Z]+),', text)}
    roster = _between(text, "public static final List<Fish> FISH = List.of(", "));")
    return [(fid, families[const][0], families[const][1])
            for fid, const in re.findall(r'fish\("([a-z_]+)", ([A-Z]+),', roster)]


def bee_roster():
    """{bee id: (kind, [parents])} in roster order, from BeeRules.java."""
    text = _between(_java("rules/BeeRules.java"),
                    "public static final List<Bee> BEES = List.of(", "));")
    bees = {}
    for kind, args in re.findall(r'\b(wild|resource|place|product)\(((?:"[a-z_]+",?\s*)+)\)', text):
        names = re.findall(r'"([a-z_]+)"', args)
        parents = {"wild": [], "resource": names[1:3], "place": names[3:5],
                   "product": names[2:4]}[kind]
        bees[names[0]] = (kind, parents)
    return bees


def seed_roster():
    """[(material, tier)] from SeedRules.java."""
    text = _between(_java("rules/SeedRules.java"),
                    "public static final List<Seed> SEEDS = List.of(", "));")
    return [(m, int(t)) for m, t in re.findall(r'mined\("([a-z_]+)", (\d),', text)]


def rods():
    """The rod ladder, rung 0 first, from FishItems.java."""
    text = _java("registry/FishItems.java")
    return re.findall(r'"([a-z_]+)"', re.search(r'String\[\] names = \{([^}]*)\}', text).group(1))


def essences():
    """The essence ladder, tier 1 first, from AgronomyItems.java."""
    text = _java("registry/AgronomyItems.java")
    return re.findall(r'"([a-z_]+)"', _between(text, "ESSENCE_NAMES = List.of(", ");"))


# ---------------------------------------------------------------------------
# The declarations
# ---------------------------------------------------------------------------

def declare(declared):
    fish = fish_roster()
    bees = bee_roster()
    seeds = seed_roster()
    rod_ids = [f"{NS}:{r}" for r in rods()]
    essence_ids = [f"{NS}:{e}" for e in essences()]
    # A roster that reads as empty is a regex that stopped matching the Java,
    # not a module with no fish: fail loudly rather than declare nothing.
    assert fish and bees and seeds and rod_ids and essence_ids, "a roster read as empty"
    assert all(p in bees for kind, parents in bees.values() for p in parents), "a parent is not a bee"

    def alternation(ids):
        return "/^" + NS + ":(" + "|".join(ids) + ")$/"

    # --- Fishing -----------------------------------------------------------
    # Water, fresh or sea: the game's own rod does. FishingEvents cancels the
    # game's catch for every float in water, ours or the game's, and throws a
    # draw from TackleRules.table instead. A bare rod is rung 0 with nothing
    # fitted, and every water family lives within one depth band of some
    # overworld water (FishRules.lives); salt or fresh is the biome's tag.
    watered = [f for f, family, water in fish if water in ("fresh", "salt")]
    declared(alternation(watered), ["minecraft:fishing_rod"],
             f"{JAVA}/event/FishingEvents.java",
             "List<ItemStack> drops = Casts.pick(level,",
             "a rod reeled in over water with a bite gets a draw from TackleRules.table for that "
             "water (sea or fresh by biome, depth band, day or night) in place of the game's catch; "
             "the game's own rod fishes as rung 0")

    # Lava and the void: only our float fishes there. ElysiumHook floats on
    # lava, and 8 blocks under the End's floor, and draws from the same table;
    # only an ElysiumRodItem casts it, so each of the five rods is a way in.
    molten = [f for f, family, water in fish if water == "lava"]
    hollow = [f for f, family, water in fish if water == "void"]
    for rod in rod_ids:
        declared(alternation(molten), [rod],
                 f"{JAVA}/entity/ElysiumHook.java",
                 "Casts.pick(server, judgedAt(), rung, tackle, bait, random)",
                 f"{rod} casts ElysiumHook (ElysiumRodItem.use), which fishes lava for the nether "
                 "family; the game's float does not fish lava")
        declared(alternation(hollow), [rod, "dimension:minecraft:the_end"],
                 f"{JAVA}/entity/ElysiumHook.java",
                 "Casts.pick(server, judgedAt(), rung, tackle, bait, random)",
                 f"{rod} cast off an End island floats at y -8 (WaterRules.VOID_DEPTH) and draws the "
                 "end family; the line breaks past 32 blocks, so the player stands at y 24 or lower")

    # Buckets: the game's Bucketable pickup (a water bucket used on a live
    # fish) hands over what FishEntity.getBucketItemStack names, the species'
    # own bucket; the lava family's bucket holds lava.
    for f, family, water in fish:
        declared(f"{NS}:{f}_bucket", [f"creature:{NS}:{f}", "minecraft:water_bucket"],
                 f"{JAVA}/entity/FishEntity.java",
                 "return new ItemStack(FishItems.bucketOf(species()));",
                 f"a water bucket used on a live {f} picks it up (the game's Bucketable) into the "
                 "bucket FishEntity.getBucketItemStack names")
    # A bucket emptied lets its fish go (MobBucketItem). Every other family
    # swims in the world already (fish_spawn_* biome modifiers); the void
    # family never does (WaterRules.spawnsWild), so for it the bucket is the
    # only way to meet one alive. The bucket no longer needs one alive: a
    # caught one and an empty bucket make it at a table (recipe <fish>_bucket,
    # WaterRules.bucketedAtTable), and this is what reaches the creature.
    for f in hollow:
        declared(f"creature:{NS}:{f}", [f"{NS}:{f}_bucket"],
                 f"{JAVA}/registry/FishItems.java",
                 "ITEMS.register(one.bucket(), () -> new MobBucketItem(",
                 f"emptying {_a(f)} bucket lets the fish go (MobBucketItem)")

    # Bait: a worm farm fed a raw fish makes that fish's family's bait
    # (TackleRules.baitFor), one per WORM_TICKS, up to WORM_FARM_HOLDS. Any
    # fish of the family does; its first in the roster stands for it, since a
    # family is caught by one declaration above and comes up together.
    first_of = {}
    for f, family, water in fish:
        first_of.setdefault(family, f)
    for family, f in first_of.items():
        declared(f"{NS}:{family}_bait", [f"{NS}:worm_farm", f"{NS}:{f}"],
                 f"{JAVA}/block/WormFarmBlock.java",
                 "new ItemStack(FishItems.BAITS.get(family).get(), held)",
                 f"a worm farm fed {_a(family)} fish (e.g. {f}) makes {family} bait, one per 1200 ticks, "
                 "up to 16")

    # --- Bees --------------------------------------------------------------
    hive = f"{NS}:hive"
    jar = f"{NS}:bee_jar"
    treat = f"{NS}:bee_treat"
    scoop = f"{NS}:bee_scoop"
    flowers = "#minecraft:flowers"

    # A hive of plain bees (the crafted bee item, no species) fills with the
    # plain comb. It counts flowers within 5 blocks and makes nothing with
    # none (AgronomyRules.combsPerHour).
    declared(f"{NS}:comb", [hive, f"{NS}:bee", flowers],
             f"{JAVA}/block/HiveBlock.java",
             "return new ItemStack(AgronomyItems.COMB.get());",
             "a hive of plain bees (the crafted bee, no species) with flowers around it makes "
             "the plain comb")

    for bee, (kind, parents) in bees.items():
        me = f"creature:{NS}:{bee}_bee"
        # A hive of one species makes that species' comb. The species gets in
        # by a jar: from a nest, a bee in the air, or another hive.
        declared(f"{NS}:{bee}_comb", [me, hive, jar, flowers],
                 f"{JAVA}/block/HiveBlock.java",
                 "return new ItemStack(BeeItems.COMBS.get(here).get());",
                 f"a hive of {bee} bees (moved in by a bee jar) makes {bee} comb")
        # Or straight off a bee that has finished one, with the scoop.
        declared(f"{NS}:{bee}_comb", [me, scoop],
                 f"{JAVA}/item/BeeScoopItem.java",
                 "new ItemStack(BeeItems.COMBS.get(bee.species()).get())",
                 f"a comb scoop used on {_a(bee)} bee that has finished a comb takes {_a(bee)} comb")
        if parents:
            # Breeding: a hive of one parent, a jar of the other (the mate),
            # and a treat give the child KeepingRules.childOf names, as a bee
            # item; a jar lets it go as a creature (BeeJarItem.useOn). The hive
            # does not look at the child's home biome.
            declared(me, [f"creature:{NS}:{parents[0]}_bee", f"creature:{NS}:{parents[1]}_bee",
                          hive, jar, treat],
                     f"{JAVA}/block/HiveBlock.java",
                     "String child = KeepingRules.childOf(here, mate);",
                     f"a hive of {parents[0]} bees given a jar of {parents[1]} and a bee treat breeds "
                     f"{_a(bee)} bee (HiveBlock.treat), which a jar lets go (BeeJarItem.useOn)")
        if kind == "resource":
            # The centrifuge spins a resource comb into its chunk, 2 wax and a
            # honey bottle (ApiaryRules.spun). A LOW grid machine with no
            # screen: combs go in by hopper, and the power is a steam turbine
            # on copper wire burning furnace fuel.
            declared(f"{NS}:{bee}_chunk",
                     [f"{NS}:{bee}_comb", f"{NS}:centrifuge", "minecraft:hopper",
                      "elysiumindustry:steam_turbine", "elysiumindustry:copper_wire", "minecraft:coal"],
                     f"{JAVA}/block/CentrifugeBlock.java",
                     "put(FIRST_OUT + at, AgronomyItems.thing(made.get(at)),",
                     f"a powered centrifuge fed {_a(bee)} comb by hopper spins it into {_a(bee)} chunk, "
                     "2 wax and a honey bottle")

    # --- Essence -----------------------------------------------------------
    # A pot grows its seed's essence, light 9 or more above it, and keeps the
    # seed. A seed above the first rung costs four essence of its own tier; a
    # first-rung seed costs four wheat seeds (SeedRules.FIRST_RUNG_PRICE,
    # gen_agronomy.py ladder_recipes), which is the way onto the ladder. The
    # rungs above follow by these and by the 4:1 climbing recipes.
    first_seed = {}
    for material, tier in seeds:
        first_seed.setdefault(tier, material)
    for tier, material in sorted(first_seed.items()):
        essence = essence_ids[tier - 1]
        declared(essence, [f"{NS}:pot", f"{NS}:{material}_seed"],
                 f"{JAVA}/block/PotBlock.java",
                 "ItemStack harvest = new ItemStack(sown.essence(), sown.yield());",
                 f"a lit pot growing a tier {tier} seed (e.g. {material}_seed) yields "
                 f"{essence.split(':')[1]} and keeps the seed")
