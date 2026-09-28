"""
A reader for the game's NBT, enough to look inside a level.dat.

Gzip-compressed or not, big-endian, every tag kind the game writes. Byte,
int and long arrays come back as lists. No writing: a validator only ever
reads, and anything that rewrites a world goes through the game's own classes
(tools/livetest/stages/StripPlayer.java) so the file stays one the game wrote.
"""
import gzip
import struct

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE, TAG_BYTES, \
    TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INTS, TAG_LONGS = range(13)


class _Reader:
    def __init__(self, data):
        self.data = data
        self.at = 0

    def take(self, fmt):
        size = struct.calcsize(fmt)
        value = struct.unpack_from(">" + fmt, self.data, self.at)[0]
        self.at += size
        return value

    def string(self):
        n = self.take("H")
        s = self.data[self.at:self.at + n].decode("utf-8", "replace")
        self.at += n
        return s

    def payload(self, kind):
        if kind == TAG_BYTE:
            return self.take("b")
        if kind == TAG_SHORT:
            return self.take("h")
        if kind == TAG_INT:
            return self.take("i")
        if kind == TAG_LONG:
            return self.take("q")
        if kind == TAG_FLOAT:
            return self.take("f")
        if kind == TAG_DOUBLE:
            return self.take("d")
        if kind == TAG_BYTES:
            n = self.take("i")
            out = list(struct.unpack_from(">%db" % n, self.data, self.at))
            self.at += n
            return out
        if kind == TAG_STRING:
            return self.string()
        if kind == TAG_LIST:
            inner = self.take("b")
            n = self.take("i")
            return [self.payload(inner) for _ in range(n)]
        if kind == TAG_COMPOUND:
            out = {}
            while True:
                k = self.take("b")
                if k == TAG_END:
                    return out
                name = self.string()
                out[name] = self.payload(k)
        if kind == TAG_INTS:
            n = self.take("i")
            out = list(struct.unpack_from(">%di" % n, self.data, self.at))
            self.at += 4 * n
            return out
        if kind == TAG_LONGS:
            n = self.take("i")
            out = list(struct.unpack_from(">%dq" % n, self.data, self.at))
            self.at += 8 * n
            return out
        raise ValueError("unknown NBT tag kind %d" % kind)


def read(path):
    """The root compound of a .dat file, as nested dicts and lists."""
    with open(path, "rb") as handle:
        raw = handle.read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    reader = _Reader(raw)
    kind = reader.take("b")
    reader.string()
    return reader.payload(kind)


def overworld_biomes(root):
    """
    The overworld's inline multi-noise biome list out of a level.dat root, as
    (biome id, parameters) pairs the way a world preset writes them, or None
    when the world was made from a named preset and carries no list of its own.
    """
    dims = root.get("Data", {}).get("WorldGenSettings", {}).get("dimensions", {})
    source = dims.get("minecraft:overworld", {}).get("generator", {}).get("biome_source", {})
    if not isinstance(source, dict) or "biomes" not in source:
        return None
    return [(entry.get("biome"), entry.get("parameters", {})) for entry in source["biomes"]]
