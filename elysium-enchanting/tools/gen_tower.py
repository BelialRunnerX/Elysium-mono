#!/usr/bin/env python3
"""
The Tome Tower: a structure built rather than drawn.

Apotheosis puts its tomes in a surface tower. Elysium has the tomes, the
shelves and the benches already; what it did not have was anywhere to find
them that was not a dungeon.

<h2>Why the tower is generated</h2>

A structure is an NBT file, and an NBT file is normally made by building the
thing in a world and saving it with a structure block. That is a fine way to
make one tower and a bad way to make three, because the three then have no
relationship to each other except that somebody remembers building them the
same way. Here the tower is a function of its height: the same walls, the same
window rhythm, the same floor spacing, and the ladder always in the same
corner, so a player who has climbed one knows where the stairs are in all of
them.

<h2>What is inside</h2>

Shelves, which is the point: the tower is where a player first sees the
enchanting module's shelves doing what they do, standing in a room that is
already built to the seven-by-seven shape the Arcane Table reads. The top
floor holds a table and a Scriptorium; the bottom holds a chest.
"""
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumenchanting"
RES = os.path.join(MOD, "src/main/resources")
DATA = os.path.join(RES, "data", NS)

#: 1.21.1. A template saved with a different number still loads, but the game
#: runs its data fixers over it, and a fixer that guesses wrong about one of
#: this mod's own blocks would quietly change the tower.
DATA_VERSION = 3955

WIDTH = 7           # the footprint, walls included
INNER = WIDTH - 2
FLOOR_HEIGHT = 5    # from one floor's boards to the next
BATTLEMENT = 2      # how far the wall stands above the top floor

#: Three heights, and the difference between them is the only difference
#: between the three templates. A tower a player can see from a distance and
#: guess the size of is a tower worth walking to.
FLOORS = {"tome_tower_small": 3, "tome_tower": 4, "tome_tower_great": 5}

WALL = ["minecraft:stone_bricks"] * 6 + ["minecraft:cracked_stone_bricks"] * 2 \
       + ["minecraft:mossy_stone_bricks"] * 2

#: The shelves this mod adds, so the tower is where they are first seen. Each
#: appears on a different floor, so a player climbing meets them one at a time
#: rather than all at once on the ground floor.
SHELVES = [f"{NS}:deepshelf", f"{NS}:sightshelf", f"{NS}:hellshelf",
           f"{NS}:endshelf", f"{NS}:keepers_shelf", f"{NS}:archive_column"]


def compound(pairs):
    return nbt.Tag(nbt.COMPOUND, dict(pairs))


def string(value):
    return nbt.Tag(nbt.STRING, value)


def integer(value):
    return nbt.Tag(nbt.INT, value)


def int_list(values):
    return nbt.Tag(nbt.LIST, [integer(v) for v in values], nbt.INT)


class Template:
    """A grid of block ids that knows how to become an NBT structure."""

    def __init__(self, width, height, depth):
        self.size = (width, height, depth)
        self.blocks = {}
        self.palette = []
        self.index = {}

    def state(self, name, properties=None):
        key = (name, tuple(sorted((properties or {}).items())))
        if key not in self.index:
            self.index[key] = len(self.palette)
            entry = {"Name": string(name)}
            if properties:
                entry["Properties"] = compound(
                    (k, string(v)) for k, v in sorted(properties.items()))
            self.palette.append(compound(entry.items()))
        return self.index[key]

    def put(self, x, y, z, name, properties=None, data=None):
        if not (0 <= x < self.size[0] and 0 <= y < self.size[1]
                and 0 <= z < self.size[2]):
            raise ValueError(f"({x},{y},{z}) is outside {self.size}")
        self.blocks[(x, y, z)] = (self.state(name, properties), data)

    def save(self, path):
        entries = []
        for (x, y, z), (state, data) in sorted(self.blocks.items()):
            pairs = [("pos", int_list([x, y, z])), ("state", integer(state))]
            if data is not None:
                pairs.append(("nbt", data))
            entries.append(compound(pairs))
        root = compound([
            ("size", int_list(list(self.size))),
            ("palette", nbt.Tag(nbt.LIST, self.palette, nbt.COMPOUND)),
            ("blocks", nbt.Tag(nbt.LIST, entries, nbt.COMPOUND)),
            # An empty list is written with the END element type, which is
            # what vanilla writes; the game reads either, but a template
            # that is byte-comparable with one it made is one fewer thing
            # to wonder about when something does not place.
            ("entities", nbt.Tag(nbt.LIST, [], nbt.END)),
            ("DataVersion", integer(DATA_VERSION)),
        ])
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbt.save(path, "", root)


def build(floors, seed):
    """
    One tower.

    The seed only picks which of the three stone-brick variants each wall block
    is, so two towers of the same height are the same tower with different
    weathering. Everything structural is a function of the height.
    """
    random.seed(seed)
    height = floors * FLOOR_HEIGHT + BATTLEMENT + 1
    tower = Template(WIDTH, height, WIDTH)

    def wall_block():
        return random.choice(WALL)

    for y in range(height):
        for x in range(WIDTH):
            for z in range(WIDTH):
                edge = x in (0, WIDTH - 1) or z in (0, WIDTH - 1)
                if edge:
                    tower.put(x, y, z, wall_block())
                else:
                    tower.put(x, y, z, "minecraft:air")

    # The floors, and the hole each one leaves for the ladder.
    for floor in range(floors + 1):
        y = floor * FLOOR_HEIGHT
        for x in range(1, WIDTH - 1):
            for z in range(1, WIDTH - 1):
                if floor > 0 and (x, z) == (1, 1):
                    continue                     # the ladder comes up here
                tower.put(x, y, z, "minecraft:spruce_planks")

    # The ladder, corner to corner, all the way up.
    for y in range(1, floors * FLOOR_HEIGHT + 1):
        tower.put(1, y, 1, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})

    # The door, and the two blocks of air behind it.
    door_z = WIDTH // 2
    tower.put(0, 1, door_z, "minecraft:oak_door",
              {"facing": "east", "half": "lower", "hinge": "left",
               "open": "false", "powered": "false"})
    tower.put(0, 2, door_z, "minecraft:oak_door",
              {"facing": "east", "half": "upper", "hinge": "left",
               "open": "false", "powered": "false"})

    # Windows: one per wall per floor, at eye height, and a lantern beside the
    # ladder so a climbing player can see the next floor before reaching it.
    for floor in range(floors):
        base = floor * FLOOR_HEIGHT
        eye = base + 2
        middle = WIDTH // 2
        for x, z in ((middle, 0), (middle, WIDTH - 1), (0, middle), (WIDTH - 1, middle)):
            if (x, z) == (0, middle) and floor == 0:
                continue                         # that is the doorway
            tower.put(x, eye, z, "minecraft:glass_pane",
                      {"north": "false", "east": "false", "south": "false",
                       "west": "false", "waterlogged": "false"})
        tower.put(2, base + 4, 1, "minecraft:lantern",
                  {"hanging": "true", "waterlogged": "false"})

    # Shelves. One kind per floor, set against the inside of the walls, which
    # is also the ring the Arcane Table reads - so the top floor is a working
    # enchanting room rather than a picture of one.
    for floor in range(floors):
        shelf = SHELVES[floor % len(SHELVES)]
        base = floor * FLOOR_HEIGHT
        for y in (base + 1, base + 2):
            for x in range(1, WIDTH - 1):
                for z in range(1, WIDTH - 1):
                    against = x in (1, WIDTH - 2) or z in (1, WIDTH - 2)
                    if not against or (x, z) == (1, 1):
                        continue
                    if (x, z) == (WIDTH - 2, WIDTH - 2) and floor == 0:
                        continue                 # the chest stands here
                    if floor == floors - 1 and (x, z) in (
                            (WIDTH - 2, WIDTH - 2), (WIDTH - 2, WIDTH - 3)):
                        continue                 # the benches stand here
                    tower.put(x, y, z, shelf if y == base + 1 else "minecraft:bookshelf")

    # The chest on the ground floor, and the two benches at the top.
    tower.put(WIDTH - 2, 1, WIDTH - 2, "minecraft:chest",
              {"facing": "west", "type": "single", "waterlogged": "false"},
              compound([("id", string("minecraft:chest")),
                        ("LootTable", string(f"{NS}:chests/tome_tower"))]))

    top = (floors - 1) * FLOOR_HEIGHT
    tower.put(WIDTH - 2, top + 1, WIDTH - 2, f"{NS}:arcane_table")
    tower.put(WIDTH - 2, top + 1, WIDTH - 3, f"{NS}:scriptorium")

    # The battlements: a gap every other block, which is what makes a tower
    # read as a tower from the ground.
    crown = floors * FLOOR_HEIGHT + BATTLEMENT
    for x in range(WIDTH):
        for z in range(WIDTH):
            edge = x in (0, WIDTH - 1) or z in (0, WIDTH - 1)
            if edge and (x + z) % 2 == 1:
                tower.put(x, crown, z, "minecraft:air")
    return tower


def worldgen(names):
    import json

    def write(path, body):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(body, indent=2) + "\n")

    for name, floors in names.items():
        write(os.path.join(DATA, f"worldgen/template_pool/{name}.json"), {
            "fallback": "minecraft:empty",
            "elements": [{
                "weight": 1,
                "element": {
                    "location": f"{NS}:{name}",
                    "processors": "minecraft:empty",
                    "projection": "rigid",
                    "element_type": "minecraft:single_pool_element"}}]})

    write(os.path.join(DATA, "worldgen/structure/tome_tower.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/tome_tower",
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
        "start_pool": f"{NS}:tome_tower_start",
        "size": 1,
        "start_height": {"absolute": 0},
        "max_distance_from_center": 80,
        "use_expansion_hack": False,
        "project_start_to_heightmap": "WORLD_SURFACE_WG"})

    # One start pool with the three heights in it, weighted so the tall one is
    # the one a player remembers finding.
    write(os.path.join(DATA, "worldgen/template_pool/tome_tower_start.json"), {
        "fallback": "minecraft:empty",
        "elements": [
            {"weight": weight, "element": {
                "location": f"{NS}:{name}",
                "processors": "minecraft:empty",
                "projection": "rigid",
                "element_type": "minecraft:single_pool_element"}}
            for name, weight in (("tome_tower_small", 5), ("tome_tower", 3),
                                 ("tome_tower_great", 1))]})

    write(os.path.join(DATA, "worldgen/structure_set/tome_tower.json"), {
        "structures": [{"structure": f"{NS}:tome_tower", "weight": 1}],
        "placement": {
            "type": "minecraft:random_spread",
            "salt": 671194422,
            "spacing": 44,
            "separation": 16,
            "frequency": 0.6}})

    write(os.path.join(DATA, "tags/worldgen/biome/has_structure/tome_tower.json"),
          {"replace": False, "values": ["#minecraft:is_overworld"]})

    # What is in the chest. Tomes are the point; the rest is what a scholar
    # who lived in a tower would have had.
    write(os.path.join(DATA, "loot_table/chests/tome_tower.json"), {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": f"{NS}:transcription_tome", "weight": 3},
                {"type": "minecraft:item", "name": f"{NS}:patience_tome", "weight": 3},
                {"type": "minecraft:item", "name": f"{NS}:concentration_tome", "weight": 2},
                {"type": "minecraft:item", "name": f"{NS}:scrapping_tome", "weight": 2}]},
            {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4},
             "entries": [
                 {"type": "minecraft:item", "name": "minecraft:book", "weight": 10,
                  "functions": [{"function": "minecraft:set_count",
                                 "count": {"type": "minecraft:uniform",
                                           "min": 1, "max": 5}}]},
                 {"type": "minecraft:item", "name": "minecraft:paper", "weight": 10,
                  "functions": [{"function": "minecraft:set_count",
                                 "count": {"type": "minecraft:uniform",
                                           "min": 3, "max": 9}}]},
                 {"type": "minecraft:item", "name": "minecraft:lapis_lazuli", "weight": 8,
                  "functions": [{"function": "minecraft:set_count",
                                 "count": {"type": "minecraft:uniform",
                                           "min": 4, "max": 12}}]},
                 {"type": "minecraft:item", "name": "minecraft:enchanted_book",
                  "weight": 6,
                  "functions": [{"function": "minecraft:enchant_randomly"}]},
                 {"type": "minecraft:item", "name": "minecraft:experience_bottle",
                  "weight": 4,
                  "functions": [{"function": "minecraft:set_count",
                                 "count": {"type": "minecraft:uniform",
                                           "min": 2, "max": 6}}]},
                 {"type": "minecraft:empty", "weight": 4}]}]})


def main():
    made = {}
    for index, (name, floors) in enumerate(FLOORS.items()):
        tower = build(floors, seed=1000 + index)
        tower.save(os.path.join(DATA, f"structure/{name}.nbt"))
        made[name] = floors
        print(f"{name:20s} {floors} floors, {tower.size[1]:2d} high, "
              f"{len(tower.blocks)} blocks, {len(tower.palette)} states")
    worldgen(made)
    print("worldgen            : structure, 4 pools, a set, a biome tag and a chest")


if __name__ == "__main__":
    main()
