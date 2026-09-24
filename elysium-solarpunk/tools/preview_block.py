#!/usr/bin/env python3
"""Render a block model's boxes, so a shape can be looked at rather than imagined.

Why this exists
---------------
The conduit is the first block in this mod whose shape is written as raw boxes
rather than parented to a vanilla model, and raw boxes are exactly the kind of
thing that is right in the JSON and wrong on the screen: an arm a pixel proud of
the core, a UV that wraps the whole tile onto a four-pixel strip, an up/down
rotation the wrong way round. All three read as perfectly sensible numbers.

So the numbers get drawn. Orthographic isometric, nearest-neighbour sampling
from the block's own texture, one image per connection arrangement. It is not a
substitute for the game — no lighting model, no ambient occlusion — but it
answers the question the numbers cannot: does this look like a pipe.

Usage:
    python3 tools/preview_block.py power_conduit
"""

import json
import pathlib
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
RES = HERE.parent / "src/main/resources/assets/elysiumsolarpunk"

SCALE = 12            # pixels per block-unit of the 16-unit cube
SHADE = {"up": 1.0, "down": 0.55, "north": 0.8, "south": 0.8,
         "east": 0.65, "west": 0.65}

# The three visible faces in this view, and which corners of the box they use.
# Drawn back to front, so nothing needs a depth buffer.
VIEW = ("up", "south", "east")


def load_texture(name):
    path = RES / f"textures/block/{name.split(':')[-1].split('/')[-1]}.png"
    return Image.open(path).convert("RGBA")


def project(x, y, z):
    """Block units to screen pixels, isometric."""
    return ((x - z) * SCALE * 0.866, (x + z) * SCALE * 0.5 - y * SCALE)


def face_quad(box, face):
    x1, y1, z1, x2, y2, z2 = box
    if face == "up":
        return [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)]
    if face == "south":
        return [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)]
    if face == "east":
        return [(x2, y2, z1), (x2, y2, z2), (x2, y1, z2), (x2, y1, z1)]
    raise ValueError(face)


def sample(texture, face, box, u, v):
    """Colour at (u, v) across the face, 0..1, from the model's own UV."""
    x1, y1, z1, x2, y2, z2 = box
    if face == "up":
        tu, tv = x1 + (x2 - x1) * u, z1 + (z2 - z1) * v
    elif face == "south":
        tu, tv = x1 + (x2 - x1) * u, 16 - (y2 - (y2 - y1) * v)
    else:
        tu, tv = z1 + (z2 - z1) * u, 16 - (y2 - (y2 - y1) * v)
    px = min(texture.width - 1, max(0, int(tu / 16 * texture.width)))
    py = min(texture.height - 1, max(0, int(tv / 16 * texture.height)))
    return texture.getpixel((px, py))


def draw_face(canvas, texture, box, face, origin):
    quad = face_quad(box, face)
    screen = [project(*point) for point in quad]
    xs = [p[0] for p in screen]
    ys = [p[1] for p in screen]
    steps = int(max(max(xs) - min(xs), max(ys) - min(ys)) * 2) + 2
    shade = SHADE[face]
    for a in range(steps + 1):
        for b in range(steps + 1):
            u, v = a / steps, b / steps
            # Bilinear across the quad, which is a parallelogram in this view.
            sx = (screen[0][0] * (1 - u) * (1 - v) + screen[1][0] * u * (1 - v)
                  + screen[2][0] * u * v + screen[3][0] * (1 - u) * v)
            sy = (screen[0][1] * (1 - u) * (1 - v) + screen[1][1] * u * (1 - v)
                  + screen[2][1] * u * v + screen[3][1] * (1 - u) * v)
            colour = sample(texture, face, box, u, v)
            if colour[3] == 0:
                continue
            lit = tuple(min(255, int(channel * shade)) for channel in colour[:3])
            px, py = int(sx + origin[0]), int(sy + origin[1])
            if 0 <= px < canvas.width and 0 <= py < canvas.height:
                canvas.putpixel((px, py), lit + (255,))


def boxes_of(model_path):
    model = json.load(open(model_path, encoding="utf-8"))
    return [tuple(element["from"]) + tuple(element["to"])
            for element in model["elements"]]


def rotate(box, turn):
    x1, y1, z1, x2, y2, z2 = box
    if turn == "y90":
        pts = [(16 - z1, y1, x1), (16 - z2, y2, x2)]
    elif turn == "y180":
        pts = [(16 - x1, y1, 16 - z1), (16 - x2, y2, 16 - z2)]
    elif turn == "y270":
        pts = [(z1, y1, 16 - x1), (z2, y2, 16 - x2)]
    elif turn == "x270":
        pts = [(x1, 16 - z1, y1), (x2, 16 - z2, y2)]
    elif turn == "x90":
        pts = [(x1, z1, 16 - y1), (x2, z2, 16 - y2)]
    else:
        return box
    low = [min(a, b) for a, b in zip(*pts)]
    high = [max(a, b) for a, b in zip(*pts)]
    return tuple(low + high)


ARM_TURN = {"north": None, "east": "y90", "south": "y180", "west": "y270",
            "up": "x270", "down": "x90"}


def render(block, arms, out):
    texture = load_texture(block)
    boxes = boxes_of(RES / f"models/block/{block}_core.json")
    arm = boxes_of(RES / f"models/block/{block}_arm.json")[0]
    for face in arms:
        boxes.append(rotate(arm, ARM_TURN[face]))

    canvas = Image.new("RGBA", (int(24 * SCALE), int(24 * SCALE)), (24, 22, 30, 255))
    origin = (12 * SCALE, 14 * SCALE)
    # Back to front: the further a box's centre is along (x + z), the nearer it
    # is to the viewer in this projection.
    for box in sorted(boxes, key=lambda b: (b[0] + b[3]) + (b[2] + b[5])):
        for face in VIEW:
            draw_face(canvas, texture, box, face, origin)
    canvas.save(out)
    return out


if __name__ == "__main__":
    block = sys.argv[1] if len(sys.argv) > 1 else "power_conduit"
    target = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else pathlib.Path("/tmp")
    target.mkdir(parents=True, exist_ok=True)
    cases = {
        "bare": [],
        "straight": ["east", "west"],
        "elbow": ["east", "down"],
        "tee": ["east", "west", "down"],
        "all": ["north", "east", "south", "west", "up", "down"],
    }
    for name, arms in cases.items():
        print(render(block, arms, target / f"{block}_{name}.png"))
