"""
Voidforged — the Elysium art system.

Direction: dark gothic plate, sci-fi emissive cores.

Rules every texture follows:
  * One near-black outline traced around every silhouette. Heavy, unbroken.
  * Metals are desaturated and cool. Five shade steps, lit from the top-left,
    on the same lightness curve materials.py generates - top step around
    luminance 200. The ramps here used to stop at 126, which is darker than
    vanilla iron's *midtone*, and a sprite whose brightest pixel is a midtone
    has no value structure and reads as a silhouette however well it is drawn.
  * Colour never carries a material; it only ever carries *energy*. Anything
    *saturated* on a sprite is glowing - which is the version of this rule that
    survives. Brightness is no longer the glow's alone, because it was costing
    every sprite its form.
  * Emissive accents are drawn core-out: a near-white core, a saturated ring,
    then a dim halo bleeding into the metal. Small and sparse — one focal glow
    per sprite, at most two.
  * Silhouettes are notched and angular. Gothic means asymmetry at the edges:
    spikes, crenellations, a broken line rather than a smooth arc.
"""

# --------------------------------------------------------------------------
# Metal ramps: index 0 is the outline, 1 darkest .. 5 brightest highlight
# --------------------------------------------------------------------------
METAL = {
    # Elysium's base alloy — black iron with a violet bloom in the highlights
    "voidsteel": ["#06050b", "#16122b", "#2d2654", "#483e83", "#7267b2", "#b8b0de"],
    # Neutronium — denser, colder, almost no hue
    "neutronium": ["#040507", "#181c25", "#333a47", "#525c70", "#7d889c", "#c1c6cd"],
    # Carved obsidian, used for rune tablets and workstation stone
    "obsidian": ["#040309", "#191429", "#332952", "#524280", "#7b69af", "#bdb5d9"],
    # Aetherium — pale planar alloy, a cold teal cast in the highlights
    "aetherium": ["#03090c", "#10252e", "#20495a", "#33788f", "#58aac1", "#b2d4dc"],
    # Ordinary stone, for the ore blocks' matrix
    "stone": ["#2c2c30", "#4a4a50", "#5e5e66", "#70707a", "#82828d", "#95959f"],
}

# --------------------------------------------------------------------------
# Glow ramps: index 0 dimmest halo .. 4 white-hot core
# --------------------------------------------------------------------------
GLOW = {
    "void":        ["#25104a", "#4b1f92", "#7a37cc", "#a86ef0", "#e3d2ff"],
    "plasma":      ["#3a0d09", "#8a2712", "#d4521a", "#f28f3e", "#ffdaa6"],
    "neural":      ["#04281d", "#0a6242", "#159c68", "#33d296", "#c6ffe4"],
    "dimensional": ["#06203a", "#0c4a80", "#187dc2", "#3aa6e8", "#cdefff"],
    "kinetic":     ["#372504", "#83570b", "#c28815", "#e6b23c", "#fff0ba"],
    "aetherium":   ["#052a36", "#0b5f75", "#149bb8", "#3ecde6", "#d2f6ff"],
    "voidglass":   ["#180630", "#3a1370", "#6425ae", "#9459e6", "#dfc2ff"],
    # Neutronium is inert. It gets a cold pale rim, never a colour.
    "inert":       ["#1b1e25", "#3a4250", "#5a6472", "#8993a4", "#c6d0dd"],
}

# Which glow belongs to which element id used in Elysium.java
ELEMENT_GLOW = ["void", "plasma", "neural", "dimensional", "kinetic"]
