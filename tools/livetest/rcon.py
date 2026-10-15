#!/usr/bin/env python3
"""
A minimal Source-RCON client for the dedicated test server: one command per
argument, each answer printed under it.

    python3 tools/livetest/rcon.py "<command>" [more commands...]
    python3 tools/livetest/rcon.py stop

It talks to 127.0.0.1:25575 with the password in ELYSIUM_RCON (or the test
default), the same server `livetest.py` drives; `install.sh` stops the server
with it before swapping jars. Timeouts are five minutes, because `locate biome`
and a big `fill` can take that long.

It lived in the session scratchpad until the scratchpad was emptied on
11 October 2026; it came back from the session transcript on 14 October 2026
(the heredoc of 26 September 03:05 UTC, with the timeout raised to 300 s at
03:33 the same morning).
"""
import os
import socket
import struct
import sys

HOST, PORT = "127.0.0.1", 25575
PW = os.environ.get("ELYSIUM_RCON", "elysiumtest")


def packet(rid, kind, body):
    data = struct.pack("<ii", rid, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def read(sock):
    raw = sock.recv(4)
    if len(raw) < 4:
        return None, None, ""
    (n,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            break
        data += chunk
    rid, kind = struct.unpack("<ii", data[:8])
    return rid, kind, data[8:-2].decode("utf-8", "replace")


def main():
    s = socket.create_connection((HOST, PORT), timeout=300)
    s.sendall(packet(1, 3, PW))
    rid, kind, _ = read(s)
    if rid == -1:
        sys.exit("rcon auth failed")
    for i, cmd in enumerate(sys.argv[1:], start=2):
        s.sendall(packet(i, 2, cmd))
        s.settimeout(300)
        out = ""
        rid, kind, body = read(s)
        out += body
        s.settimeout(0.6)
        try:
            while True:
                rid, kind, body = read(s)
                if rid is None:
                    break
                out += body
        except socket.timeout:
            pass
        print(f"> {cmd}\n{out.strip()}")
    s.close()


if __name__ == "__main__":
    main()
