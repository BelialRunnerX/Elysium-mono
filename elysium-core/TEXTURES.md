# Objective: appropriate textures for everything Elysium adds

**Every item, block and material Elysium registers ships with purpose-drawn
art.** No flat colour fills, no missing models, no reused vanilla sprites, and
no texture at the wrong resolution for what it maps onto.

This is a standing requirement, not a one-off pass: anything added to the
registry later is not finished until it has art that follows the direction
below.

---

## Direction — "Voidforged"

Dark gothic plate, sci-fi emissive cores.

The material is black iron and cut obsidian. The interest comes from what is
*trapped inside* it: psionic energy, bled through carved channels and set
gemstones. Elysium gear should look forged for something that was never meant
to be worn safely.

Five rules hold the set together.

**1 · Colour is energy, never material.**
Metals are desaturated and cool. Anything saturated on a sprite is glowing —
that is the only thing hue is allowed to mean. This is why Neutronium has no
element colour at all: it is inert, so it gets a cold pale rim instead of a
glow ramp.

**2 · One outline, near-black, unbroken.**
Every silhouette is traced at index 0 of its metal ramp. It is what makes the
sprites read against both a bright inventory background and a dark cave wall.

**3 · Bloom stays tight.**
One orthogonal ring at the dimmest glow step, and that is all. A wide halo at
16×16 swallows the sprite it is meant to sit on — at this resolution the
surrounding dark metal is what sells the light, not the light itself.

**4 · Sigils are carved, not painted.**
A glowing shape drawn straight onto a plate looks like a sticker. Cut a dark
recess into the metal first, then run the lit line along the bottom of the cut.
`Canvas.carve()` does this.

**5 · Silhouettes are notched.**
Gothic means a broken outline: spikes on the pauldrons, crenellations on the
crown, cheek guards on the helm, a crest that breaks the top of the sprite.
Leave at least a two-pixel gap between paired shapes — boots, legs, spires —
or the outline pass fills the gap and they merge into one blob.

**6 · Diagonals are their own problem.**
Weapons read on the bottom-left → top-right diagonal, and two things go wrong
there. A band whose thickness is offset *diagonally* leaves each step touching
only at its corners, so it draws as a dotted line and the outline pass floods
the gaps — thickness runs along x instead (`_axis`). And `plate()` shades from
which neighbours are empty, which flips on every pixel of a 45-degree
staircase; use `band()` for anything diagonal, which shades by position within
the row instead.

### Palettes

| Ramp | Used for |
|---|---|
| `voidsteel` | Elysium alloy — black iron, violet bloom in the highlights |
| `neutronium` | denser, colder, almost hueless |
| `obsidian` | rune tablets, the Rune Socket Table, Voidglass tools |
| `aetherium` | pale planar alloy, cold teal highlights — the Aetherium tools |
| `stone` | the matrix of the ore blocks |

| Glow | Element |
|---|---|
| `void` `plasma` `neural` `dimensional` `kinetic` | the five gear elements |
| `aetherium` `voidglass` | the two non-neutronium materials |
| `inert` | Neutronium's cold rim — a value ramp, not a colour |

Metal ramps run index 0 (outline) → 5 (highlight). Glow ramps run 0 (dim halo)
→ 4 (white-hot core). Everything is lit from the top-left.

---

## Resolutions that are not negotiable

| Asset | Size | Why |
|---|---|---|
| Items | 16×16 RGBA | with real transparency — an opaque sprite renders as a solid square |
| Block faces | 16×16 RGBA | |
| **Armour layers** | **64×32 RGBA** | these are six unwrapped box faces at fixed coordinates, not a free canvas |
| GUI | 256×256, panel drawn in the top-left 176×166 | |

The armour layers are the easy thing to get wrong. A 64×32 sheet maps like
this:

```
layer_1   helmet (head box) · chestplate (body + arm boxes) · boots (lower leg box)
layer_2   leggings (leg box + waist of the body box)
```

Filling the sheet evenly produces armour that reads as a patterned blanket,
because nothing lines up with the shape underneath. Face coordinates are in
`tools/textures/layers.py`; paint each face separately and keep the light
direction consistent across them.

---

## The generator

```bash
cd tools/textures
python3 build.py      # writes the whole set into src/main/resources
python3 preview.py    # contact sheets + a worn-armour mock into tools/textures/preview
```

Requires Pillow (`pip install pillow`). Nothing in `tools/` ships in the jar.

| File | Contains |
|---|---|
| `style.py` | the palettes and ramps — change a colour here and the whole set follows |
| `canvas.py` | primitives: `plate`, `outline`, `glow`, `carve`, `engrave`, `highlight` |
| `sprites.py` | item and block silhouettes |
| `layers.py` | armour sheets, painted against the humanoid UV map |
| `build.py` | maps sprites to output paths |
| `preview.py` | contact sheets, and a front-view mock that composites the layer faces onto a player figure |

Run `preview.py` after any change. The worn mock is the only cheap way to catch
armour art landing on the wrong body part.

### Adding art for a new item

1. Add a silhouette function to `sprites.py`, built from `rect()` unions.
2. `plate()` it with a metal ramp, `engrave()` the panel lines, add **one**
   focal `glow()` — two at most.
3. `outline()` last. Always last: it traces whatever is already drawn.
4. Register the output path in `build.py`.
5. Run `build.py`, then `preview.py`, and look at it.

`validate.py` at the repo root fails the build if a registered item has no
model, or a model points at a texture that does not exist — so a new item
without art will not pass quietly.

---

## Current coverage

| Group | Count | Status |
|---|---|---|
| Materials (ingots, shard) | 3 | done |
| Elemental runes | 5 | done — carved sigils, one per element |
| Utility runes | 4 | done — aetherium sigils, so the two families read apart |
| Reforge catalyst | 1 | done |
| Elysium armour icons | 5 | done — one element each, plus the Voidweave Aegis |
| Emperor's Crown | 1 | done |
| Neutronium armour icons | 4 | done — inert, no element colour |
| Weapons | 7 | done — five blades, a maul, a lance and a rifle |
| Area tools | 12 | done — four shapes x three materials |
| Ore blocks | 3 | done — crystal veins bedded into stone |
| Storage block | 1 | done |
| Workstation blocks | 3 | done — carved inlay per function |
| Armour layers | 4 sheets | done — painted per body region |
| GUI | 1 | done |

**54 textures, no placeholders.**

### Tool silhouettes

The four tool shapes share one haft and are told apart entirely by the head,
which is the only part legible at inventory size:

- **Hammer** — a blocky head with a bright striking face at each end.
- **Broadaxe** — a wedge on top of the haft: flat back, edge left, underside
  sloping into the handle. The asymmetry is what stops it reading as a leaf.
- **Scythe** — a one-pixel arc, thickened to three by the outline pass, hooked
  at the point.
- **Spear** — a broad leaf head on a *one*-pixel shaft, so the head reads as a
  bulge rather than more shaft.

Each is drawn a pixel lean, because `outline()` grows every silhouette by one
pixel in each direction. That growth is also why two shapes that look distinct
on the grid can merge into the same blob once rendered — check the alpha mask,
not the source.

### Known gaps

- Blocks use a single texture on all six faces. The workstations would read
  better with a distinct top, side and bottom — that means moving them off
  `cube_all` in the block models.
- No animated textures. The workstation inlay and the ore cores are the
  obvious candidates for a `.mcmeta` pulse.
- No item overlay for socketed gear: a piece with three runes looks identical
  in the inventory to one with none.
