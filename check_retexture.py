#!/usr/bin/env python3
"""
Does the tech reskin keep its three promises?

    python3 check_retexture.py

The promises are: recolour and never reshape, every colour from the Elysium
palette, and nobody else's art copied anywhere. The first two are properties of
a function and are checked by running it — on textures generated here, never on
a mod's, because the mods this tool is aimed at ship All Rights Reserved art and
this repository holds none of it.

The third is checked by reading the tool: it opens jars where they are
installed and writes a pack beside them, and nothing in this repository is
allowed to contain their assets.
"""
import json
import os
import pathlib
import random
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent
TOOL = ROOT / "tools" / "retexture"
sys.path.insert(0, str(TOOL))

problems = []
checks = 0


def fail(message):
    problems.append(message)


def check(condition, message):
    global checks
    checks += 1
    if not condition:
        fail(message)


def main():
    try:
        from PIL import Image
    except ImportError:
        print("retexture         : Pillow is not installed, so nothing was checked")
        return 1

    import build
    import classify
    import palette
    import recolour

    # ---------------------------------------------------------------- palette
    live = palette.live()
    if live is None:
        # Run from a copy on somebody else's machine, where there is no art
        # system to compare against. That is the normal case out there and a
        # fact rather than a fault; inside the repository, art/ is present and
        # the comparison is made.
        if (ROOT / "art").is_dir():
            fail("art/ is here and could not be read, so the frozen palette cannot "
                 "be compared with the thing it was frozen from and may have drifted")
        else:
            print("retexture         : outside the repository, so the frozen palette "
                  "is taken as given")
    else:
        frozen = json.load(open(palette.FROZEN, encoding="utf-8"))
        check(frozen == live,
              "tools/retexture/ramps.json no longer matches what art/style.py and "
              "art/materials.py produce, so the reskin paints in an older palette "
              "than the mod does. Run tools/retexture/freeze.py")

    ramps = palette.load()
    for family, expected in (("surfaces", 6), ("glows", 5)):
        for name, ramp in ramps[family].items():
            check(len(ramp) == expected,
                  f"the {name} ramp has {len(ramp)} steps and a {family[:-1]} ramp "
                  f"has {expected}; a ramp of a different length maps brightness "
                  f"onto different colours than every other one")
            levels = [recolour.luminance(palette.rgb(step)) for step in ramp]
            check(levels == sorted(levels),
                  f"the {name} ramp's steps are not in order of brightness, so a "
                  f"texture mapped onto it comes out with its shading inverted "
                  f"somewhere: {[round(v, 3) for v in levels]}")
            check(levels[-1] - levels[0] > 0.35,
                  f"the {name} ramp is too flat to hold a drawing: every brightness "
                  f"in the source lands within {round(levels[-1] - levels[0], 3)} of "
                  f"every other")

    # Every class the classifier can return has somewhere to be painted.
    for _, kind in classify.BY_NAME:
        check(kind in ramps["surfaces"],
              f"the classifier can answer '{kind}' and the palette has no ramp for "
              f"it, so those textures fall back to metal and the rule that named "
              f"them does nothing")
    for kind in classify.PROTOTYPES:
        check(kind in ramps["surfaces"],
              f"the colour fallback can answer '{kind}' and the palette has no ramp "
              f"for it")
    for mod, energy in palette.ENERGY.items():
        check(energy in ramps["glows"],
              f"{mod} glows in '{energy}' and there is no such energy ramp, so its "
              f"machines would crash the build rather than light up")
    check(palette.DEFAULT_ENERGY in ramps["glows"],
          "the default energy colour is not a ramp, so any mod nobody listed fails")

    # ------------------------------------------------------- what it must not touch
    ours = set()
    for properties in ROOT.glob("elysium-*/gradle.properties"):
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("mod_id="):
                ours.add(line.split("=", 1)[1].strip())
    for mod in sorted(ours):
        check(mod in build.PROTECTED,
              f"{mod} is one of ours and the reskin does not protect it, so running "
              f"the tool repaints Elysium's own art with a generated approximation "
              f"of Elysium's own art")
    check("minecraft" in build.PROTECTED,
          "the reskin would repaint vanilla, which is not what it is for")
    check(build.PACK_FORMAT == 34,
          "the pack format is not the one 1.21.1 reads, so the game shows the pack "
          "as incompatible and a player has to force it")

    # ----------------------------------------------------------- the mapping
    surfaces = {name: [palette.rgb(step) for step in ramp]
                for name, ramp in ramps["surfaces"].items()}
    glows = {name: [palette.rgb(step) for step in ramp]
             for name, ramp in ramps["glows"].items()}
    metal = surfaces["metal_pale"]
    kinetic = glows["kinetic"]

    # A texture of our own: every brightness, every alpha, some saturated.
    random.seed(7)
    source = Image.new("RGBA", (16, 16))
    pixels = []
    for index in range(256):
        if index % 4 == 3:
            pixels.append((random.randrange(180, 256), random.randrange(0, 60),
                           random.randrange(0, 60), index))
        else:
            grey = index
            pixels.append((grey, grey, grey, 255 - (index % 256)))
    source.putdata(pixels)

    made = recolour.recolour(source, metal, kinetic)
    check(made.size == source.size,
          "the mapping changed the size of a texture, which moves every UV in every "
          "model that points at it")
    before = recolour.pixels_of(source)
    after = recolour.pixels_of(made)
    check([p[3] for p in before] == [p[3] for p in after],
          "the mapping changed a texture's alpha, so a cut-out sprite gains or "
          "loses pixels and the shape is no longer the author's")
    check(after == recolour.pixels_of(recolour.recolour(source, metal, kinetic)),
          "the mapping is not deterministic, so two builds of the same pack differ")

    # Order of brightness is the drawing. It survives.
    greys = [(index, index, index, 255) for index in range(256)]
    line = Image.new("RGBA", (16, 16))
    line.putdata(greys)
    mapped = recolour.pixels_of(recolour.recolour(line, metal, kinetic))
    levels = [recolour.luminance(pixel) for pixel in mapped]
    check(all(levels[i] <= levels[i + 1] + 1e-9 for i in range(len(levels) - 1)),
          "a pixel that was lighter than its neighbour came out darker: the mapping "
          "inverts shading somewhere, which is the one thing 'recolour, never "
          "reshape' forbids")
    check(levels[0] < levels[-1],
          "black and white came out the same brightness, so the drawing is gone")

    # Every colour is on the palette, and nowhere else.
    def on_ramp(colour, ramp):
        for index in range(len(ramp) - 1):
            first, second = ramp[index], ramp[index + 1]
            for share in range(0, 101):
                candidate = tuple(int(round(first[channel]
                                            + (second[channel] - first[channel])
                                            * share / 100.0))
                                  for channel in range(3))
                if all(abs(candidate[c] - colour[c]) <= 1 for c in range(3)):
                    return True
        return False

    stray = [pixel for pixel in after
             if not on_ramp(pixel[:3], metal) and not on_ramp(pixel[:3], kinetic)]
    check(not stray,
          f"{len(stray)} colour(s) in a mapped texture are on neither the surface "
          f"ramp nor the energy ramp, so the pack is not in the Elysium palette: "
          f"{stray[:3]}")

    # Energy is energy, paint is paint, and a glow is sparse.
    #
    # A machine with a lit core: grey everywhere, four bright pixels. Those
    # four are the light and the rest is the casing.
    machine = Image.new("RGBA", (8, 8), (120, 122, 126, 255))
    lamp = recolour.pixels_of(machine)
    for index in (27, 28, 35, 36):
        lamp[index] = (255, 40, 40, 255)
    machine.putdata(lamp)
    machine_out = recolour.pixels_of(recolour.recolour(machine, metal, kinetic))
    check(on_ramp(machine_out[27][:3], kinetic),
          "a bright saturated pixel in an otherwise grey texture was painted as "
          "surface rather than as energy, so a running machine looks switched off")
    check(on_ramp(machine_out[0][:3], metal),
          "a desaturated pixel was painted as energy, so every shaded corner of "
          "every machine glows")
    # And a plank, which is warm and saturated everywhere and is not a light.
    # This is the case the first run of the tool got wrong: it painted amber
    # lightning across every wooden panel in Create.
    plank = Image.new("RGBA", (8, 8))
    grain = []
    for index in range(64):
        grain.append((150, 100, 55, 255) if index % 5 else (188, 130, 70, 255))
    plank.putdata(grain)
    plank_out = recolour.pixels_of(recolour.recolour(plank, surfaces["wood"],
                                                     kinetic))
    check(all(on_ramp(pixel[:3], surfaces["wood"]) for pixel in plank_out),
          "a texture that is warm and saturated all over was read as a glow, so "
          "wood grain comes out as lightning; a glow is sparse and that is the half "
          "of the test a per-pixel threshold cannot make")
    emissive_out = recolour.pixels_of(
        recolour.recolour(machine, metal, kinetic, always_energy=True))
    check(on_ramp(emissive_out[0][:3], kinetic),
          "the glowing half of a texture pair was painted as surface, so an emissive "
          "overlay stops being emissive")

    # The table walks the ramp by brightness rather than by position, and that
    # is the property worth checking: a pixel a third of the way up the
    # source's range comes out a third of the way up the ramp's range. Without
    # it the bottom fifth of every texture is spent between two near-blacks,
    # which is how the first run of this tool turned a press head into a
    # silhouette.
    for name, ramp in sorted(surfaces.items()):
        table = recolour.table(ramp)
        low = recolour.luminance(table[0])
        high = recolour.luminance(table[-1])
        check(table[0] == ramp[0] and table[-1] == ramp[-1],
              f"the ends of the {name} ramp are not the ends of the mapping, so "
              f"pure black and pure white land somewhere in the middle of it")
        drift = max(abs(recolour.luminance(table[level])
                        - (low + (high - low) * level / 255.0))
                    for level in range(256))
        check(drift < 0.045,
              f"the {name} mapping is {round(drift, 3)} off a straight line through "
              f"the ramp's own brightness range, so the drawing's midtones are "
              f"pushed somewhere the author did not put them")
        levels = [recolour.luminance(colour) for colour in table]
        check(all(levels[i] <= levels[i + 1] + 1e-9 for i in range(255)),
              f"the {name} mapping is not monotone, so shading inverts somewhere")
        check(all(on_ramp(colour, ramp) for colour in table),
              f"the {name} mapping produces a colour that is not on its own ramp")

    # --------------------------------------------------------- classification
    for path, expected in (
            ("assets/create/textures/block/brass_casing.png", "metal_warm"),
            ("assets/create/textures/block/andesite_casing.png", "stone"),
            ("assets/create/textures/block/copper_casing.png", "metal_copper"),
            ("assets/mekanism/textures/block/basic_control_circuit.png", "circuit"),
            ("assets/mekanism/textures/block/steel_casing.png", "metal_pale"),
            ("assets/ae2/textures/block/quartz_glass.png", "glass"),
            ("assets/storagedrawers/textures/block/oak_front_1.png", "wood"),
            ("assets/create/textures/gui/widget_button.png", "ui")):
        got = classify.by_name(path)
        check(got == expected,
              f"{os.path.basename(path)} is classified as {got} and should be "
              f"{expected}; the name is the only place the author's intent survives")
    check(classify.by_name("assets/create/textures/block/copper_casing.png")
          != classify.by_name("assets/create/textures/block/andesite_casing.png"),
          "two casings that differ only in their material come out the same, which "
          "is the distinction a player reads a machine by")
    check(not classify.is_texture("assets/create/textures/font/thing.png"),
          "a font would be recoloured, so the interface becomes unreadable")
    check(not classify.is_texture("assets/create/textures/colormap/grass.png"),
          "a colormap would be recoloured; it is data painted as a picture and "
          "repainting it changes the colour of every leaf in the world")
    check(not classify.is_texture("assets/create/models/block/cog.json"),
          "something that is not a texture would be opened as one")
    check(classify.emissive("assets/create/textures/block/thing_glow.png"),
          "the glowing half of a pair is not recognised, so it is painted as paint")
    check(not classify.emissive("assets/create/textures/block/thing.png"),
          "an ordinary texture is treated as emissive, so everything glows")

    # ------------------------------------------------- nothing of theirs is here
    for pattern in ("create", "mekanism", "immersiveengineering", "ae2"):
        stolen = [p for p in ROOT.rglob(f"**/assets/{pattern}/**/*.png")]
        check(not stolen,
              f"this repository contains {len(stolen)} file(s) under assets/"
              f"{pattern}/; their art is All Rights Reserved and the thing we ship "
              f"is the generator")
    source_text = (TOOL / "build.py").read_text(encoding="utf-8")
    check("jar.open(" in source_text and "os.makedirs(out_dir" in source_text,
          "the builder no longer reads from the installed jars and writes beside "
          "them, which is the arrangement that keeps anybody's art on their own disk")

    print(f"retexture         : {checks} checks: recoloured and never reshaped, "
          f"every colour from the palette, nobody's art in this repository")
    if problems:
        print(f"{len(problems)} PROBLEM(S):")
        for problem in problems:
            print("  - " + problem)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
