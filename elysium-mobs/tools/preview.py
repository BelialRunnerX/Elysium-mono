#!/usr/bin/env python3
"""
Turn captured animation frames into something a person can look at.

PoseCapture.java runs the real Minecraft model pipeline headless and writes out
what it would have drawn: quads in model space, already transformed, with their
texture coordinates. Everything here is projection and rasterising — the only
part of this system that is my arithmetic rather than Minecraft's, and so the
only part that can be wrong on its own. That is why `--calibrate` exists.

Usage:
    python3 preview.py <capture.json> <texture.png> <out-dir>
                       [--scale N] [--views side,front,... | --views all]

Writes a filmstrip and an animated GIF per view. The default pair is a side
elevation, which is where gait errors show, and a three-quarter, which is
closer to how the mob is actually seen; `--views` opens up the other three.
See VIEWS below for what each angle is actually for.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

# Minecraft builds entity models with +Y pointing *down* — a part at y=24 is at
# the feet — and the renderer flips the whole thing at draw time. Reproducing
# that flip here is what stops every mob rendering upside down.
Y_FLIP = -1.0

# name: (yaw in degrees, pitch in degrees)
#
# Each of these answers a question the others cannot. A side elevation is where
# a gait error shows, because the whole stride happens in that plane and
# nothing is foreshortened. A three-quarter is closest to how the mob is
# actually seen. The front view is the only one that shows *lateral* error -
# whether a leg swings straight or paddles outwards, which a side view hides
# completely because it is a movement directly towards the camera. The rear
# quarter catches what the front plating hides. And the low angle is what a
# player standing in front of a two-metre creature is looking at, which is not
# the same picture as the eye-level one the previewer defaulted to.
VIEWS = {
    "side": (90.0, 0.0),
    "three_quarter": (35.0, 12.0),
    "front": (0.0, 6.0),
    "rear_quarter": (145.0, 10.0),
    "low": (55.0, -22.0),
}

# The two that walk.sh renders by default. Everything else is opt-in through
# --views, because eight families times five angles is forty renders and most
# of the time the question is about the gait, which the first two answer.
DEFAULT_VIEWS = ("side", "three_quarter")


def rotation(yaw_deg, pitch_deg):
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)
    ry = np.array([[cy, 0.0, sy], [0.0, 1.0, 0.0], [-sy, 0.0, cy]])
    rx = np.array([[1.0, 0.0, 0.0], [0.0, cp, -sp], [0.0, sp, cp]])
    return rx @ ry


def load(capture_path):
    with open(capture_path, encoding="utf-8") as handle:
        return json.load(handle)


def world_bounds(frames):
    """One bounding box across every frame, so the mob does not swim about."""
    points = np.array([v[:3] for f in frames for q in f["quads"] for v in q])
    points[:, 1] *= Y_FLIP
    return points.min(axis=0), points.max(axis=0)


def raster_quad(target, depth, screen, uvs, texture, tex_w, tex_h):
    """
    Fill one quad, nearest-neighbour sampled, with a depth test.

    Two triangles, barycentric interpolation of the texture coordinates. A
    z-buffer rather than a painter's sort: parts interpenetrate (an arm passes
    through the line of the body) and a per-quad sort draws that wrong in a way
    that looks exactly like a modelling mistake, which is the one thing this
    tool must not invent.
    """
    for a, b, c in ((0, 1, 2), (0, 2, 3)):
        p0, p1, p2 = screen[a], screen[b], screen[c]
        d0, d1, d2 = depth[a], depth[b], depth[c]
        t0, t1, t2 = uvs[a], uvs[b], uvs[c]

        min_x = max(int(math.floor(min(p0[0], p1[0], p2[0]))), 0)
        max_x = min(int(math.ceil(max(p0[0], p1[0], p2[0]))), target.shape[1] - 1)
        min_y = max(int(math.floor(min(p0[1], p1[1], p2[1]))), 0)
        max_y = min(int(math.ceil(max(p0[1], p1[1], p2[1]))), target.shape[0] - 1)
        if min_x > max_x or min_y > max_y:
            continue

        area = ((p1[0] - p0[0]) * (p2[1] - p0[1])
                - (p2[0] - p0[0]) * (p1[1] - p0[1]))
        if abs(area) < 1e-9:
            continue

        ys, xs = np.mgrid[min_y:max_y + 1, min_x:max_x + 1]
        px, py = xs + 0.5, ys + 0.5
        w0 = ((p1[0] - px) * (p2[1] - py) - (p2[0] - px) * (p1[1] - py)) / area
        w1 = ((p2[0] - px) * (p0[1] - py) - (p0[0] - px) * (p2[1] - py)) / area
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not inside.any():
            continue

        z = w0 * d0 + w1 * d1 + w2 * d2
        zbuf = raster_quad.zbuffer[min_y:max_y + 1, min_x:max_x + 1]
        nearer = inside & (z < zbuf)
        if not nearer.any():
            continue

        u = (w0 * t0[0] + w1 * t1[0] + w2 * t2[0]) * tex_w
        v = (w0 * t0[1] + w1 * t1[1] + w2 * t2[1]) * tex_h
        u = np.clip(u.astype(np.int32), 0, tex_w - 1)
        v = np.clip(v.astype(np.int32), 0, tex_h - 1)
        sampled = texture[v, u]

        # A fully transparent texel is a hole in the model, not a black pixel.
        nearer &= sampled[:, :, 3] > 0
        if not nearer.any():
            continue

        lit = sampled.astype(np.float32)
        lit[:, :, :3] *= raster_quad.shade
        region = target[min_y:max_y + 1, min_x:max_x + 1]
        region[nearer] = np.clip(lit, 0, 255).astype(np.uint8)[nearer]
        zbuf[nearer] = z[nearer]


# Entity shading, which is not block shading.
#
# These were vanilla's *block* face multipliers (1.0 / 0.5 / 0.8 / 0.6). Blocks
# get hard per-face shading; entities are lit by two directional lights, which
# is a shallower range. Using the block numbers made every preview darker than
# the game, on top of textures that were themselves too dark - so the previewer
# was overstating the problem it was being used to diagnose.
#
# these are the multipliers a player has been looking at for a decade, so a
# model shaded this way reads the way it will read in game. A flat-lit render
# hides exactly the thing a walk cycle has to be judged on — which face of a
# limb is turned towards you as it swings.
FACE_SHADE = {"up": 1.0, "down": 0.62, "north": 0.86, "south": 0.86,
              "east": 0.74, "west": 0.74}


def face_shade(points):
    """Pick a vanilla face multiplier from the quad's dominant normal axis."""
    normal = np.cross(points[1] - points[0], points[3] - points[0])
    length = np.linalg.norm(normal)
    if length < 1e-9:
        return 1.0
    normal = normal / length
    axis = int(np.argmax(np.abs(normal)))
    if axis == 1:
        return FACE_SHADE["up"] if normal[1] > 0 else FACE_SHADE["down"]
    if axis == 2:
        return FACE_SHADE["north"]
    return FACE_SHADE["east"]


def render_frame(quads, texture, matrix, low, high, size, margin=6):
    tex_h, tex_w = texture.shape[0], texture.shape[1]
    target = np.zeros((size, size, 4), dtype=np.uint8)
    raster_quad.zbuffer = np.full((size, size), np.inf)

    centre = (low + high) / 2.0
    extent = float(np.max(high - low))
    scale = (size - 2 * margin) / extent if extent > 0 else 1.0

    for quad in quads:
        points = np.array([v[:3] for v in quad], dtype=np.float64)
        points[:, 1] *= Y_FLIP
        # Shaded from the normal in model space, before the camera turns it, so
        # a face keeps the same brightness from every viewpoint — which is how
        # Minecraft does it and why its blocks look solid rather than shiny.
        raster_quad.shade = face_shade(points)
        view = (points - centre) @ matrix.T
        screen = np.empty((4, 2))
        screen[:, 0] = view[:, 0] * scale + size / 2.0
        screen[:, 1] = -view[:, 1] * scale + size / 2.0
        raster_quad(target, view[:, 2], screen,
                    [v[3:5] for v in quad], texture, tex_w, tex_h)

    return Image.fromarray(target, "RGBA")


BACKDROP = (34, 34, 40, 255)


def write_gif(path, images, fps=20):
    """
    One animated GIF, every frame quantised against **one** palette.

    Converting each frame to "P" on its own gives each frame its own adaptive
    palette, and a mob whose colours are eighty shades of one hue then gets a
    different eighty per frame. Nothing moves on the model and the whole thing
    shimmers - which is indistinguishable from a texture that flickers, and
    this tool exists to tell those apart.

    So the strip is quantised once and every frame is mapped onto that result.
    The palette comes from the filmstrip rather than from frame zero, because
    a colour that only appears mid-stride still has to be in it.
    """
    flat = [Image.alpha_composite(Image.new("RGBA", im.size, BACKDROP), im)
            for im in images]
    contact = Image.new("RGB", (flat[0].width * len(flat), flat[0].height))
    for i, frame in enumerate(flat):
        contact.paste(frame.convert("RGB"), (i * frame.width, 0))
    palette = contact.quantize(colors=255, method=Image.MEDIANCUT)
    frames = [frame.convert("RGB").quantize(palette=palette, dither=Image.NONE)
              for frame in flat]
    frames[0].save(path, save_all=True, append_images=frames[1:],
                   duration=max(1, 1000 // fps), loop=0, optimize=True)
    return path


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(2)
    capture_path, texture_path, out_dir = sys.argv[1:4]
    size = 192
    wanted = list(DEFAULT_VIEWS)
    for i, arg in enumerate(sys.argv):
        if arg == "--scale" and i + 1 < len(sys.argv):
            size = int(sys.argv[i + 1])
        elif arg == "--views" and i + 1 < len(sys.argv):
            wanted = ["all"] if sys.argv[i + 1] == "all" \
                else sys.argv[i + 1].split(",")
    if wanted == ["all"]:
        wanted = list(VIEWS)
    unknown = [name for name in wanted if name not in VIEWS]
    if unknown:
        sys.exit("no such view: %s (have %s)"
                 % (", ".join(unknown), ", ".join(VIEWS)))

    data = load(capture_path)
    frames = data["frames"]
    if texture_path == "flat":
        # A judging aid, not an output. The Elysium palettes are very dark, and
        # at 160px a near-black mob is a silhouette — fine for checking a gait,
        # useless for checking whether a ribcage reads as a ribcage. Flat mid
        # grey plus the vanilla face shading shows form and nothing else.
        texture = np.full((64, 64, 4), 190, dtype=np.uint8)
    else:
        texture = np.array(Image.open(texture_path).convert("RGBA"))
    low, high = world_bounds(frames)

    os.makedirs(out_dir, exist_ok=True)
    written = []
    for view_name in wanted:
        yaw, pitch = VIEWS[view_name]
        matrix = rotation(yaw, pitch)
        images = [render_frame(f["quads"], texture, matrix, low, high, size)
                  for f in frames]

        strip = Image.new("RGBA", (size * len(images), size), (0, 0, 0, 0))
        for i, image in enumerate(images):
            strip.alpha_composite(image, (i * size, 0))
        strip_path = os.path.join(out_dir, "%s_%s_strip.png"
                                  % (data["model"], view_name))
        strip.save(strip_path)

        gif_path = os.path.join(out_dir, "%s_%s.gif" % (data["model"], view_name))
        write_gif(gif_path, images)
        written += [strip_path, gif_path]

    for path in written:
        print("  ", path)


if __name__ == "__main__":
    main()
