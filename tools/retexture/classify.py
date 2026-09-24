"""
What kind of surface a texture is, decided from its path and then its pixels.

<h2>Why the path first</h2>

Because the file name is the only place the author's intent survives. A Create
brass casing and a Create andesite casing are both brown-grey squares with the
same value structure; nothing in the pixels distinguishes them, and everything
in the name does. Reading the name first and the pixels second means the common
case is right for a reason rather than by luck.

<h2>And why the pixels at all</h2>

Because seven thousand textures have names their authors never expected anybody
else to parse, and "I could not tell" has to produce something rather than
nothing. The fallback is a vote over hue and saturation against one prototype
per class — deliberately crude, because a crude guess that is right two times
in three beats leaving a third of a mod untouched, and the overrides file
exists for the rest.
"""
import re

#: Path fragments that decide a class outright, most specific first. Order is
#: the whole of the rule: "copper_casing" is warm metal and not a casing-grey,
#: and "circuit_board" is a circuit and not a board of timber.
BY_NAME = [
    (r"(?:^|[/_])(?:gui|widget|slot|button|panel|tab|bar|icon|overlay)s?(?:[/_]|$)",
     "ui"),
    (r"circuit|chip|processor|logic|board|wafer|silicon|calculation|engineering_proc",
     "circuit"),
    (r"glass|window|pane|tank|tube|vial|lens|crystal|quartz|screen|display",
     "glass"),
    (r"copper", "metal_copper"),
    (r"gold|golden|electrum", "metal_gold"),
    (r"brass|bronze|zinc|nickel|invar|constantan", "metal_warm"),
    (r"plastic|polymer|hdpe|substrate|slime|rubber|elite|ultimate|advanced_",
     "plastic"),
    (r"plank|log|wood|crate|barrel|oak|spruce|birch|jungle|acacia|dark_oak|warped|"
     r"crimson|bamboo|cherry", "wood"),
    (r"andesite|granite|diorite|stone|concrete|brick|cobble|gravel|scoria|slate|"
     r"limestone", "stone"),
    (r"leaf|leaves|plant|crop|seed|sapling|resin|latex|meat|organic|compost|fertil",
     "organic"),
    (r"steel|iron|aluminum|aluminium|titanium|tungsten|osmium|silver|tin|lead|"
     r"platinum|casing|frame|machine|block|plate|sheet|ingot|girder|scaffold",
     "metal_pale"),
]

#: When the name says nothing. One prototype per class, as (hue in degrees,
#: saturation, lightness); the nearest wins, with hue weighted hardest because
#: hue is what a name would have told us.
PROTOTYPES = {
    "metal_pale": (210.0, 0.06, 0.55),
    "metal_dark": (250.0, 0.12, 0.25),
    "metal_warm": (45.0, 0.45, 0.55),
    "metal_copper": (22.0, 0.55, 0.50),
    "metal_gold": (48.0, 0.75, 0.60),
    "stone": (30.0, 0.08, 0.45),
    "wood": (28.0, 0.42, 0.38),
    "glass": (195.0, 0.35, 0.65),
    "circuit": (140.0, 0.45, 0.35),
    "plastic": (280.0, 0.25, 0.45),
    "organic": (95.0, 0.45, 0.40),
}

#: Paths that are never touched. A font is not a surface, a colormap is data
#: painted as a picture, and a map decoration recoloured to voidsteel is an
#: unreadable map.
NEVER = re.compile(r"textures/(?:font|colormap|map)/|_mcmeta$|/misc/|/effect/")

#: What marks a file as the glowing half of a pair rather than a surface.
EMISSIVE = re.compile(r"(?:_e|_emissive|_glow|_lit|_overlay|_light)\.png$|/glow")


def is_texture(path):
    return path.endswith(".png") and "/textures/" in path and not NEVER.search(path)


def emissive(path):
    return bool(EMISSIVE.search(path))


def by_name(path):
    lowered = path.lower()
    for pattern, kind in BY_NAME:
        if re.search(pattern, lowered):
            return kind
    return None


def _hls(pixel):
    import colorsys
    red, green, blue = [c / 255.0 for c in pixel[:3]]
    lightness_hue, lightness, saturation = colorsys.rgb_to_hls(red, green, blue)
    return lightness_hue * 360.0, saturation, lightness


def by_pixels(image):
    """
    The class whose prototype this texture's own average is nearest.

    Averaged over the opaque pixels only, and over hue as a circle rather than
    as a number, because the mean of 350 degrees and 10 degrees is red and not
    cyan. Fully transparent pixels are skipped entirely: a mod that leaves
    bright magenta under its alpha would otherwise classify every cut-out
    sprite as plastic.
    """
    import math
    pixels = image.load()
    x_sum = y_sum = saturation_sum = lightness_sum = 0.0
    seen = 0
    for y in range(image.height):
        for x in range(image.width):
            pixel = pixels[x, y]
            if len(pixel) > 3 and pixel[3] < 32:
                continue
            hue, saturation, lightness = _hls(pixel)
            radians = math.radians(hue)
            weight = saturation
            x_sum += math.cos(radians) * weight
            y_sum += math.sin(radians) * weight
            saturation_sum += saturation
            lightness_sum += lightness
            seen += 1
    if seen == 0:
        return "metal_pale"
    hue = math.degrees(math.atan2(y_sum / seen, x_sum / seen)) % 360.0
    saturation = saturation_sum / seen
    lightness = lightness_sum / seen

    best = None
    for kind, (prototype_hue, prototype_saturation, prototype_lightness) \
            in PROTOTYPES.items():
        gap = abs(hue - prototype_hue)
        gap = min(gap, 360.0 - gap) / 180.0
        distance = (gap * 2.0
                    + abs(saturation - prototype_saturation)
                    + abs(lightness - prototype_lightness) * 0.5)
        if best is None or distance < best[0]:
            best = (distance, kind)
    return best[1]


def classify(path, image, overrides=None):
    """
    The class, and how it was decided, so a coverage report can say.

    @return (class, "override" | "name" | "pixels")
    """
    if overrides:
        for pattern, kind in overrides:
            if re.search(pattern, path.lower()):
                return kind, "override"
    named = by_name(path)
    if named:
        return named, "name"
    return by_pixels(image), "pixels"
