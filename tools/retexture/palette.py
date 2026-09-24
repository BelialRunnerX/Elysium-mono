"""
The palette a retexture maps into, and where it comes from.

<h2>Why it is not a new palette</h2>

Because a reskin that invents its own colours is a second art direction sitting
beside the first, and the player sees both at once: an Elysium hammer in the
hand and an Elysium-ish machine on the wall, not quite agreeing. So every ramp
here is either one of `art/style.py`'s own or built by `art/materials.py`'s own
generator from one base colour, which is the same thing every Elysium sprite is
built from.

<h2>Why it is frozen to a file</h2>

Because this tool runs on somebody else's machine, against mods this repository
does not have and may not redistribute. It travels as a folder, without the art
system beside it, so the ramps travel with it as `ramps.json`.

That is a copy, and a copy drifts. `freeze.py` writes it and
`check_retexture.py` fails if it no longer matches what `art/` would produce —
so the copy can be stale for exactly as long as it takes to run the validator.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
FROZEN = os.path.join(HERE, "ramps.json")

#: Surfaces, and the ramp each is drawn on. A base colour here is fed through
#: art/materials.py's generator, so it gets the same five lightness steps and
#: the same derived outline as every material in the mod.
#:
#: Read this as the whole art direction of the reskin: three greys for the
#: three kinds of metal a tech mod actually has, one for stone, one for timber,
#: one cold one for glass, and the rest are the exceptions worth naming.
BASES = {
    # Machine casings and frames: the alloy everything else is bolted to.
    "metal_dark": "#4a4470",
    # Steel, iron, aluminium: the pale structural metal.
    "metal_pale": "#9aa3b0",
    # Brass, bronze, anything a Create machine is trimmed with.
    "metal_warm": "#c4a748",
    # Copper, specifically: enough mods use it as a material that folding it
    # into "warm" loses a distinction a player reads at a glance.
    "metal_copper": "#c87a45",
    "metal_gold": "#e6c34d",
    # Andesite, concrete, the cast blocks.
    "stone": "#70707a",
    # Timber, crates, the parts of a tech mod that are not machinery.
    "wood": "#6a4a30",
    # Glass, windows, tanks: cold and pale, so a tank reads as a tank.
    "glass": "#7fc4d4",
    # Circuit boards, logic, anything that is thinking rather than turning.
    "circuit": "#2f8f6a",
    # Mekanism's plastics and the softer polymer parts.
    "plastic": "#6f6480",
    # Interfaces: panels, slots, borders.
    "ui": "#3a3550",
    # Crops, resin, the organic half of Industrial Foregoing.
    "organic": "#6f8f46",
}

#: How saturated a derived surface ramp is, against the 0.46 the gear
#: materials use.
#:
#: Lower, and for a reason about scale rather than taste: a material ramp is
#: seen on a sixteen-pixel sprite in an inventory row, where it has to be
#: nameable at a glance, and a surface ramp is seen on a wall of a hundred
#: blocks. The saturation that reads as "warm steel" on a hammer reads as
#: "yellow" on a factory, and the first run of this tool proved it.
SURFACE_SATURATION = 0.30

#: Surfaces taken whole from the art system rather than derived, because they
#: already exist there and a second voidsteel would be a second voidsteel.
BORROWED = {
    "metal_dark": "voidsteel",
    "metal_pale": "neutronium",
    "stone": "stone",
    "glass": "aetherium",
}

#: Which energy colour a mod's glowing parts take. The rule from style.py is
#: that colour only ever carries energy, so this is the one place a mod is
#: allowed to keep an identity: Create stays warm and kinetic, Mekanism stays
#: cold and dimensional, and a player can still tell whose machine is humming.
ENERGY = {
    "create": "kinetic",
    "createaddition": "kinetic",
    "createbigcannons": "kinetic",
    "createdeco": "kinetic",
    "createdieselgenerators": "plasma",
    "create_new_age": "dimensional",
    "createutilities": "kinetic",
    "immersiveengineering": "kinetic",
    "mekanism": "dimensional",
    "mekanismgenerators": "dimensional",
    "mekanismtools": "dimensional",
    "ae2": "void",
    "appliedenergistics2": "void",
    "refinedstorage": "aetherium",
    "industrialforegoing": "neural",
    "sophisticatedstorage": "aetherium",
    "sophisticatedbackpacks": "aetherium",
    "storagedrawers": "kinetic",
    "functionalstorage": "kinetic",
    "dankstorage": "void",
}

#: What a mod nobody listed glows with.
DEFAULT_ENERGY = "dimensional"


def _from_art():
    """The ramps as the art system itself would build them, or None."""
    root = os.path.dirname(os.path.dirname(HERE))
    art = os.path.join(root, "art")
    if not os.path.isdir(art):
        return None
    import sys
    if art not in sys.path:
        sys.path.insert(0, art)
    try:
        import materials
        import style
    except ImportError:
        return None
    surfaces = {}
    for name, base in BASES.items():
        borrowed = BORROWED.get(name)
        surfaces[name] = (list(style.METAL[borrowed]) if borrowed
                          else materials.ramp(base, saturation=SURFACE_SATURATION))
    return {"surfaces": surfaces, "glows": {k: list(v) for k, v in style.GLOW.items()}}


def live():
    """The ramps from art/, for the freezer and the validator. None elsewhere."""
    return _from_art()


def load():
    """
    The ramps this run will use.

    The art system when it is there, because then a change to it is picked up
    without anybody remembering to freeze; the frozen copy otherwise, because
    on somebody else's machine there is no art system to ask.
    """
    built = _from_art()
    if built is not None:
        return built
    if not os.path.isfile(FROZEN):
        raise SystemExit(f"no palette: neither art/ nor {FROZEN} is here")
    return json.load(open(FROZEN, encoding="utf-8"))


def energy_for(mod_id):
    return ENERGY.get(mod_id, DEFAULT_ENERGY)


def rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))
