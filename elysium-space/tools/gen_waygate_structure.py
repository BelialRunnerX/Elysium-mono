#!/usr/bin/env python3
"""
A working waygate, standing in the world, for somebody to find.

<h2>Why a structure and not a feature</h2>

Because a structure gets spacing and separation the way vanilla's ruined
portals do, it can be found with {@code /locate}, and it will not stack on
itself. The frequency here is deliberately the ruined portal's own: a player
who has learned how often they trip over one of those knows how often they
will trip over one of these.

<h2>Why it is generated rather than drawn</h2>

Because the ring's shape is {@code GateRules}, and a template drawn by hand is
a second copy of that shape which can drift from the one the game checks. Every
block placed here comes from the same list {@code WaygateRing} reads back, so a
generated gate is working by construction — which is the whole of what was
asked for: *working*, not broken.

    python3 elysium-space/tools/gen_waygate_structure.py
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumspace"
RES = os.path.join(MOD, "src/main/resources")
DATA = os.path.join(RES, "data", NS)
RULES = os.path.join(MOD, "src/main/java/com/elysium/space/world/GateRules.java")

DATA_VERSION = 3955

#: What the pad under the gate is made of. Pale stone: the gates are not from
#: here, and a player who has been to the Pale Moon reads that instantly.
PAD = f"{NS}:pale_stone_bricks"
FRAME = f"{NS}:waygate_frame"
ANCHOR = f"{NS}:waygate_anchor"

#: How much clear air there is around the ring, so a gate found in a forest is
#: a gate you can walk through rather than one full of oak.
MARGIN = 1


def size_from_rules():
    """The ring's width, read from the rules rather than repeated here."""
    found = re.search(r"public static final int SIZE = (\d+);",
                      open(RULES, encoding="utf-8").read())
    if not found:
        raise SystemExit("GateRules no longer declares SIZE; the template and the "
                         "gate the game checks would be different shapes")
    return int(found.group(1))


def ring(size):
    """
    Every square of the ring, in GateRules' own order and by its own arithmetic.

    Transcribed from the Java rather than imported, because this is Python —
    and the validator checks the two agree by reading the template back and
    comparing it with the rules, which is a stronger guarantee than either
    copy being correct on its own.
    """
    half = size // 2
    out = []
    for across in range(-half, half + 1):
        out.append((across, 0))
    for up in range(1, size - 1):
        out.append((half, up))
    for across in range(half, -half - 1, -1):
        out.append((across, size - 1))
    for up in range(size - 2, 0, -1):
        out.append((-half, up))
    return out


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

    def state(self, name):
        if name not in self.index:
            self.index[name] = len(self.palette)
            self.palette.append(compound([("Name", string(name))]))
        return self.index[name]

    def put(self, x, y, z, name):
        if not (0 <= x < self.size[0] and 0 <= y < self.size[1]
                and 0 <= z < self.size[2]):
            raise ValueError(f"({x},{y},{z}) is outside {self.size}")
        self.blocks[(x, y, z)] = self.state(name)

    def save(self, path):
        entries = [compound([("pos", int_list([x, y, z])), ("state", integer(state))])
                   for (x, y, z), state in sorted(self.blocks.items())]
        root = compound([
            ("size", int_list(list(self.size))),
            ("palette", nbt.Tag(nbt.LIST, self.palette, nbt.COMPOUND)),
            ("blocks", nbt.Tag(nbt.LIST, entries, nbt.COMPOUND)),
            ("entities", nbt.Tag(nbt.LIST, [], nbt.END)),
            ("DataVersion", integer(DATA_VERSION)),
        ])
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbt.save(path, "", root)


def build(size):
    """
    One gate, standing on a pad, with room to walk through it.

    The whole box is written — air included — because a template only places
    the blocks it lists, and a gate generated inside a hillside with stone in
    its opening is a gate that does not work. Clearing the box is what makes
    "working, not broken" true whatever it lands in.
    """
    width = size + MARGIN * 2
    depth = 1 + MARGIN * 2
    height = size + 1                       # the pad, then the ring
    centre = width // 2
    middle = depth // 2

    gate = Template(width, height, depth)
    for x in range(width):
        for y in range(height):
            for z in range(depth):
                gate.put(x, y, z, "minecraft:air")
    for x in range(width):
        for z in range(depth):
            gate.put(x, 0, z, PAD)
    for across, up in ring(size):
        block = ANCHOR if (across == 0 and up == 0) else FRAME
        gate.put(centre + across, 1 + up, middle, block)
    return gate


def write(path, body):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(body, indent=2) + "\n")


def main():
    size = size_from_rules()
    gate = build(size)
    gate.save(os.path.join(DATA, "structure/waygate.nbt"))

    write(os.path.join(DATA, "worldgen/template_pool/waygate/waygate.json"), {
        "fallback": "minecraft:empty",
        "elements": [{
            "weight": 1,
            "element": {
                "element_type": "minecraft:single_pool_element",
                "projection": "rigid",
                "location": f"{NS}:waygate",
                "processors": {"processors": []},
            },
        }],
    })
    write(os.path.join(DATA, "worldgen/structure/waygate.json"), {
        "type": "minecraft:jigsaw",
        "biomes": f"#{NS}:has_structure/waygate_biomes",
        "step": "surface_structures",
        "spawn_overrides": {},
        # The ground comes up to meet it, so a gate on a slope stands on its
        # own pad rather than half-buried or on a pillar.
        "terrain_adaptation": "beard_thin",
        "start_pool": f"{NS}:waygate/waygate",
        "size": 1,
        "start_height": {"absolute": 0},
        "project_start_to_heightmap": "WORLD_SURFACE_WG",
        "max_distance_from_center": 16,
        "use_expansion_hack": False,
        "liquid_settings": "ignore_waterlogging",
    })
    write(os.path.join(DATA, "worldgen/structure_set/waygate.json"), {
        "structures": [{"structure": f"{NS}:waygate", "weight": 1}],
        "placement": {
            "type": "minecraft:random_spread",
            # Vanilla's ruined portal spacing, on purpose: the user asked for
            # "similar frequency to natural nether portal spawns", and this is
            # the number that sentence means.
            "salt": 1783551617,
            "spacing": 40,
            "separation": 15,
        },
    })
    print(f"waygate structure : {size}x{size} ring on a "
          f"{gate.size[0]}x{gate.size[1]}x{gate.size[2]} pad, "
          f"{len(gate.blocks)} block(s), spacing 40/15")


if __name__ == "__main__":
    sys.exit(main())
