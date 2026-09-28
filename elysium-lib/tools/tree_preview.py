#!/usr/bin/env python3
"""
Draw the Ledger Web from the harness's dump, so the layout can be read before
anything in the game draws it.

    java ... TreeHarness /tmp/web.txt
    python3 tools/tree_preview.py /tmp/web.txt /tmp/web.png [--scale 2]

One line per node in the dump: "id kind cluster x y rewards | links". Roads
are thin, smalls are dots, notables are rings, keystones are diamonds, starts
are squares on the rim; each cluster takes a hue from its position so a
cluster reads as one thing. This is the preview the spec asks for ("rendered
headless to a PNG until it reads well"); it is not what the screen draws,
which is ElysiumUI's own frames over the same positions.
"""
import colorsys
import math
import sys

from PIL import Image, ImageDraw


def read(path):
    nodes = {}
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        head, links = line.split(" | ")
        parts = head.split(" ")
        nid, kind, cluster, x, y = parts[0], parts[1], parts[2], float(parts[3]), float(parts[4])
        rewards = parts[5] if len(parts) > 5 else ""
        nodes[nid] = (kind, cluster, x, y, rewards, [l for l in links.split(",") if l])
    return nodes


def main():
    src, out = sys.argv[1], sys.argv[2]
    scale = float(sys.argv[sys.argv.index("--scale") + 1]) if "--scale" in sys.argv else 2.0
    nodes = read(src)
    extent = max(max(abs(x), abs(y)) for _, _, x, y, _, _ in nodes.values()) + 30
    size = int(extent * 2 * scale)
    image = Image.new("RGB", (size, size), (16, 14, 24))
    draw = ImageDraw.Draw(image)

    def at(x, y):
        return (x + extent) * scale, (y + extent) * scale

    clusters = sorted({c for _, c, _, _, _, _ in nodes.values()})
    hue = {c: i / max(1, len(clusters)) for i, c in enumerate(clusters)}

    def colour(cluster, light=0.55):
        r, g, b = colorsys.hls_to_rgb(hue.get(cluster, 0.0), light, 0.55)
        return int(r * 255), int(g * 255), int(b * 255)

    for nid, (kind, cluster, x, y, _, links) in nodes.items():
        for other in links:
            if other > nid:
                ox, oy = nodes[other][2], nodes[other][3]
                draw.line([at(x, y), at(ox, oy)], fill=(70, 62, 90), width=max(1, int(scale)))
    for nid, (kind, cluster, x, y, _, links) in nodes.items():
        px, py = at(x, y)
        c = colour(cluster)
        if kind == "small":
            r = 3 * scale
            draw.ellipse([px - r, py - r, px + r, py + r], fill=c)
        elif kind == "road":
            r = 2 * scale
            draw.ellipse([px - r, py - r, px + r, py + r], fill=(150, 140, 170))
        elif kind == "notable":
            r = 6 * scale
            draw.ellipse([px - r, py - r, px + r, py + r], outline=colour(cluster, 0.75), width=int(2 * scale), fill=c)
        elif kind == "keystone":
            r = 8 * scale
            draw.polygon([(px, py - r), (px + r, py), (px, py + r), (px - r, py)], fill=(230, 190, 90), outline=(255, 240, 200))
        elif kind == "start":
            r = 6 * scale
            draw.rectangle([px - r, py - r, px + r, py + r], fill=(90, 200, 230), outline=(230, 250, 255))
            draw.text((px + r + 2, py - 5 * scale), nid[len("start_"):], fill=(200, 220, 240))
        elif kind == "hub":
            r = 9 * scale
            draw.ellipse([px - r, py - r, px + r, py + r], fill=(240, 240, 250))
    for nid, (kind, cluster, x, y, _, links) in nodes.items():
        if kind == "notable":
            px, py = at(x, y)
            draw.text((px + 8 * scale, py - 4 * scale), cluster, fill=(220, 220, 230))
        if kind == "keystone":
            px, py = at(x, y)
            draw.text((px + 9 * scale, py - 4 * scale), nid[len("key_"):], fill=(250, 220, 140))
    image.save(out)
    kinds = {}
    for kind, *_ in nodes.values():
        kinds[kind] = kinds.get(kind, 0) + 1
    print("ledger web preview: %d node(s) %s -> %s" % (len(nodes), kinds, out))


if __name__ == "__main__":
    main()
