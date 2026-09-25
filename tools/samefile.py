#!/usr/bin/env python3
"""
Whether a regenerated file is the shipped file.

    import samefile
    if not samefile.same(made, kept): ...

Byte for byte, except for a PNG, which is compared as an image: the same
size, mode and pixels. Two versions of Pillow write the same pixels with
different zlib streams, so a drift check that compared the bytes reported
every sprite in the repository as edited by hand the first time the
generators ran under a different Pillow — and a check that fails on every
machine but one is a check nobody runs.
"""
import os


def same(made, kept):
    if not os.path.isfile(made) or not os.path.isfile(kept):
        return False
    a = open(made, "rb").read()
    b = open(kept, "rb").read()
    if a == b:
        return True
    if not (made.lower().endswith(".png") and kept.lower().endswith(".png")):
        return False
    try:
        from PIL import Image
    except ImportError:
        return False
    try:
        with Image.open(made) as x, Image.open(kept) as y:
            x.load()
            y.load()
            if x.size != y.size:
                return False
            return x.convert("RGBA").tobytes() == y.convert("RGBA").tobytes()
    except Exception:
        return False
