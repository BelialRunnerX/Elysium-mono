#!/usr/bin/env python3
"""
Live tests: the pack running, asked questions over RCON.

    python3 tools/livetest/livetest.py [--host 127.0.0.1] [--port 25575] [--password ...] [--only name]

Dylan, 27 September 2026: *validation should include testing pipes and cables,
testing power generation, making sure crop farm machines actually farm crops
and generate items, testing I/O of all block inventories.* The validators read
the tree; they cannot see a machine run. This does: against a dedicated server
of the pack, each scenario builds a rig with commands on a force-loaded stage
far from spawn, lets ticks pass, and reads the result back with `/data get`
and `/execute if block`. A scenario passes or it says what it saw.

Every rig is built on its own plot of the stage so scenarios never share a
block, and the plot is cleared first so a rerun starts clean. Blocks that need
random ticks (crops growing, pots) are not testable here - a force-loaded chunk
gets no random ticks without a player - so those stay in the client stages;
everything with a block-entity ticker is testable.

Scenarios live in scenarios.py, one function each, decorated with @scenario.
"""
import argparse
import importlib
import os
import re
import socket
import struct
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))


class Rcon:
    def __init__(self, host, port, password):
        self.sock = socket.create_connection((host, port), timeout=60)
        self.rid = 0
        self._send(3, password)
        if self._recv()[0] == -1:
            raise SystemExit("RCON refused the password")

    def _send(self, kind, body):
        self.rid += 1
        payload = struct.pack("<ii", self.rid, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv(self):
        raw = b""
        while len(raw) < 4:
            raw += self.sock.recv(4 - len(raw))
        (length,) = struct.unpack("<i", raw)
        data = b""
        while len(data) < length:
            data += self.sock.recv(length - len(data))
        rid, kind = struct.unpack("<ii", data[:8])
        return rid, data[8:-2].decode("utf-8", "replace")

    def cmd(self, command):
        self._send(2, command)
        return self._recv()[1]


class Stage:
    """One scenario's plot: an origin, a few helpers, and the assertions."""

    STAGE_X, STAGE_Y, STAGE_Z = 9000, 200, 9000
    PLOT = 24

    def __init__(self, rcon, index, name):
        self.rcon = rcon
        self.name = name
        self.ox = self.STAGE_X + (index % 8) * self.PLOT
        self.oz = self.STAGE_Z + (index // 8) * self.PLOT
        self.oy = self.STAGE_Y
        self.problems = []
        self.log = []

    # --- world ---------------------------------------------------------
    def cmd(self, command):
        out = self.rcon.cmd(command)
        self.log.append((command, out))
        return out

    def at(self, dx, dy, dz):
        return f"{self.ox + dx} {self.oy + dy} {self.oz + dz}"

    def clear(self):
        x0, z0 = self.ox - 1, self.oz - 1
        x1, z1 = self.ox + self.PLOT - 2, self.oz + self.PLOT - 2
        self.cmd(f"forceload add {x0} {z0} {x1} {z1}")
        self.cmd(f"fill {x0} {self.oy - 1} {z0} {x1} {self.oy + 12} {z1} minecraft:air")
        self.cmd(f"fill {x0} {self.oy - 1} {z0} {x1} {self.oy - 1} {z1} minecraft:smooth_stone")

    def setblock(self, dx, dy, dz, block, nbt=""):
        out = self.cmd(f"setblock {self.at(dx, dy, dz)} {block}{nbt}")
        if "Changed the block" not in out and "is already" not in out:
            self.problems.append(f"setblock {block} at {self.at(dx, dy, dz)}: {out}")
        return out

    def fill(self, a, b, block):
        out = self.cmd(f"fill {self.at(*a)} {self.at(*b)} {block}")
        if "Successfully filled" not in out:
            self.problems.append(f"fill {block}: {out}")

    def hopper_into(self, dx, dy, dz, item, count):
        """A hopper above the machine, holding the item: the machine's top face takes it."""
        self.setblock(dx, dy + 1, dz, "minecraft:hopper[facing=down]")
        out = self.cmd(f"item replace block {self.at(dx, dy + 1, dz)} container.0 with {item} {count}")
        if "Replaced" not in out:
            self.problems.append(f"could not fill the hopper with {item}: {out}")

    def chest(self, dx, dy, dz):
        self.setblock(dx, dy, dz, "minecraft:chest")

    def advance(self, ticks):
        """Runs ticks as fast as the server can, and waits for them to be done."""
        self.cmd(f"tick sprint {ticks}")
        for _ in range(240):
            time.sleep(0.5)
            if "sprint" not in self.cmd("tick query").lower():
                return
        self.problems.append(f"the server did not finish sprinting {ticks} ticks in two minutes")

    # --- reads ---------------------------------------------------------
    def data(self, dx, dy, dz, path=""):
        return self.cmd(f"data get block {self.at(dx, dy, dz)} {path}".rstrip())

    def number(self, dx, dy, dz, path):
        """A numeric NBT value, or None."""
        out = self.data(dx, dy, dz, path)
        m = re.search(r"has the following block data: (-?\d+(?:\.\d+)?)[bsfdL]?\b", out)
        return float(m.group(1)) if m else None

    def items(self, dx, dy, dz, path="Items"):
        """[(id, count)] out of an item list."""
        out = self.data(dx, dy, dz, path)
        return [(i, int(c)) for i, c in re.findall(r'id: "([^"]+)"[^}]*?count: (\d+)', out)] \
            + [(i, int(c)) for c, i in re.findall(r'count: (\d+)[^}]*?id: "([^"]+)"', out)]

    def state_is(self, dx, dy, dz, block):
        out = self.cmd(f"execute if block {self.at(dx, dy, dz)} {block}")
        return "Test passed" in out

    # --- assertions ----------------------------------------------------
    def expect(self, ok, message):
        if not ok:
            self.problems.append(message)
        return ok

    def expect_state(self, dx, dy, dz, block, why):
        return self.expect(self.state_is(dx, dy, dz, block), f"{why}: {self.at(dx, dy, dz)} is not {block}")

    def expect_number(self, dx, dy, dz, path, least, why):
        value = self.number(dx, dy, dz, path)
        return self.expect(value is not None and value >= least,
                           f"{why}: {path} at {self.at(dx, dy, dz)} is {value}, wanted at least {least}")

    def expect_item(self, dx, dy, dz, item, least, why, path="Items"):
        found = sum(c for i, c in self.items(dx, dy, dz, path) if i == item)
        return self.expect(found >= least, f"{why}: {found} x {item} at {self.at(dx, dy, dz)}, wanted at least {least}")


SCENARIOS = []


def scenario(name):
    def wrap(fn):
        SCENARIOS.append((name, fn))
        return fn
    return wrap


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", default=os.environ.get("ELYSIUM_RCON", "elysiumtest"))
    parser.add_argument("--only", default="", help="run scenarios whose name contains this")
    parser.add_argument("--verbose", action="store_true", help="print every command and answer")
    args = parser.parse_args()

    sys.path.insert(0, HERE)
    # Run as a script this module is __main__; the scenarios import it by
    # name, so point that name at this very module or they register nowhere.
    sys.modules["livetest"] = sys.modules[__name__]
    importlib.import_module("scenarios")
    rcon = Rcon(args.host, args.port, args.password)
    rcon.cmd("gamerule sendCommandFeedback true")
    rcon.cmd("time set 6000")
    rcon.cmd("weather clear")
    rcon.cmd("gamerule doDaylightCycle false")

    passed = failed = 0
    for index, (name, fn) in enumerate(SCENARIOS):
        if args.only and args.only not in name:
            continue
        stage = Stage(rcon, index, name)
        stage.clear()
        try:
            fn(stage)
        except Exception as boom:            # a scenario that throws is a failure with a reason
            stage.problems.append(f"the scenario itself failed: {boom!r}")
        if stage.problems:
            failed += 1
            print(f"  FAIL   {name}")
            for problem in stage.problems:
                print(f"           {problem}")
        else:
            passed += 1
            print(f"  pass   {name}")
        if args.verbose:
            for command, out in stage.log:
                print(f"           > {command}\n             {out[:160]}")
    rcon.cmd("gamerule doDaylightCycle true")
    rcon.cmd("forceload remove all")
    print(f"live tests: {passed} passed, {failed} failed")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
