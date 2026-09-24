"""Minimal NBT (Java edition, big-endian) reader/writer: enough for structure files."""
import gzip, io, struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    __slots__ = ("type", "value", "elem")

    def __init__(self, type_, value, elem=None):
        self.type, self.value, self.elem = type_, value, elem


def _rs(f):
    n = struct.unpack(">H", f.read(2))[0]
    raw = f.read(n)
    try:
        return raw.decode("utf-8", "surrogatepass")
    except UnicodeDecodeError:
        return raw          # Java's modified UTF-8 we cannot decode: kept as bytes, written back as-is


def _ws(f, s):
    b = s if isinstance(s, bytes) else s.encode("utf-8", "surrogatepass")
    f.write(struct.pack(">H", len(b)) + b)


def _read(f, t):
    if t == END: return Tag(t, None)
    if t == BYTE: return Tag(t, struct.unpack(">b", f.read(1))[0])
    if t == SHORT: return Tag(t, struct.unpack(">h", f.read(2))[0])
    if t == INT: return Tag(t, struct.unpack(">i", f.read(4))[0])
    if t == LONG: return Tag(t, struct.unpack(">q", f.read(8))[0])
    if t == FLOAT: return Tag(t, struct.unpack(">f", f.read(4))[0])
    if t == DOUBLE: return Tag(t, struct.unpack(">d", f.read(8))[0])
    if t == BYTE_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]; return Tag(t, f.read(n))
    if t == STRING: return Tag(t, _rs(f))
    if t == LIST:
        et = f.read(1)[0]; n = struct.unpack(">i", f.read(4))[0]
        return Tag(t, [_read(f, et) for _ in range(n)], et)
    if t == COMPOUND:
        d = {}
        while True:
            ct = f.read(1)[0]
            if ct == END: return Tag(t, d)
            key = _rs(f)
            d[key] = _read(f, ct)
    if t == INT_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]; return Tag(t, list(struct.unpack(f">{n}i", f.read(4 * n))))
    if t == LONG_ARRAY:
        n = struct.unpack(">i", f.read(4))[0]; return Tag(t, list(struct.unpack(f">{n}q", f.read(8 * n))))
    raise ValueError(f"bad tag {t}")


def _write(f, tag):
    t, v = tag.type, tag.value
    if t == BYTE: f.write(struct.pack(">b", v))
    elif t == SHORT: f.write(struct.pack(">h", v))
    elif t == INT: f.write(struct.pack(">i", v))
    elif t == LONG: f.write(struct.pack(">q", v))
    elif t == FLOAT: f.write(struct.pack(">f", v))
    elif t == DOUBLE: f.write(struct.pack(">d", v))
    elif t == BYTE_ARRAY: f.write(struct.pack(">i", len(v)) + v)
    elif t == STRING: _ws(f, v)
    elif t == LIST:
        f.write(bytes([tag.elem if v or tag.elem is not None else END]) + struct.pack(">i", len(v)))
        for e in v: _write(f, e)
    elif t == COMPOUND:
        for k, e in v.items():
            f.write(bytes([e.type])); _ws(f, k); _write(f, e)
        f.write(bytes([END]))
    elif t == INT_ARRAY: f.write(struct.pack(">i", len(v)) + struct.pack(f">{len(v)}i", *v))
    elif t == LONG_ARRAY: f.write(struct.pack(">i", len(v)) + struct.pack(f">{len(v)}q", *v))


def load(path):
    raw = open(path, "rb").read()
    data = gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw
    f = io.BytesIO(data)
    t = f.read(1)[0]
    name = _rs(f)
    return name, _read(f, t)


def save(path, name, root):
    f = io.BytesIO()
    f.write(bytes([root.type])); _ws(f, name); _write(f, root)
    open(path, "wb").write(gzip.compress(f.getvalue(), mtime=0))


def strings(tag):
    if tag.type == STRING and isinstance(tag.value, str):
        yield tag
    elif tag.type == LIST or tag.type == COMPOUND:
        for e in (tag.value if tag.type == LIST else tag.value.values()):
            yield from strings(e)
