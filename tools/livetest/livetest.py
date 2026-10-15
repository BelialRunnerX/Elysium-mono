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

The server is the one under ELYSIUM_TEST_ROOT (default C:/testingbuilds, so
C:/testingbuilds/ElysiumServer); tools/livetest/install.sh puts a new build on
it and tools/livetest/rcon.py sends it one command at a time.
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

# Where the test rigs live. ELYSIUM_TEST_ROOT is the one override (default
# C:/testingbuilds); the dedicated server and the dev client are folders under
# it. The E: drive that held them was lost, and on 14 October 2026 the rigs
# were rebuilt on C:.
TEST_ROOT = os.environ.get("ELYSIUM_TEST_ROOT", "C:/testingbuilds").replace("\\", "/").rstrip("/")
SERVER_DIR = TEST_ROOT + "/ElysiumServer"
CLIENT_DIR = TEST_ROOT + "/Elysium"
SERVER_LOG = SERVER_DIR + "/logs/latest.log"


class Rcon:
    def __init__(self, host, port, password):
        self.where = (host, port, password)
        self.rid = 0
        self._connect()

    def _connect(self):
        host, port, password = self.where
        self.sock = socket.create_connection((host, port), timeout=60)
        self._send(3, password)
        if self._recv()[0] == -1:
            raise SystemExit("RCON refused the password")

    def _send(self, kind, body):
        self.rid += 1
        payload = struct.pack("<ii", self.rid, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _exactly(self, n):
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                # The server closed the connection. Before this the loop spun on
                # empty reads for ever (a full run hung 50 minutes, 27 September).
                raise ConnectionError("RCON connection closed by the server")
            data += chunk
        return data

    def _recv(self):
        (length,) = struct.unpack("<i", self._exactly(4))
        data = self._exactly(length)
        rid, kind = struct.unpack("<ii", data[:8])
        return rid, data[8:-2].decode("utf-8", "replace")

    def cmd(self, command):
        try:
            self._send(2, command)
            return self._recv()[1]
        except (ConnectionError, socket.timeout, OSError):
            # One reconnect and one retry: the server drops an RCON client now
            # and then under load, and a scenario should not die of it.
            try:
                self.sock.close()
            except OSError:
                pass
            time.sleep(2)
            self._connect()
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
        if "Changed the block" not in out and "is already" not in out and "Could not set the block" not in out:
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


# --- the server's pace ------------------------------------------------------
#
# A scenario that sprints ticks and reads an entity back assumes the server
# keeps up. It does not when a dev client, a compile or a negtest's javac shares
# the machine: it falls hundreds of ticks behind ("Can't keep up!"), and the
# entity-tick scenarios - pigeons breeding, a zombie burning, the Choir's
# phases, an envoy leaving, a cart falling - fail and pass again on a quiet
# rerun (1 October 2026: six such of seventy). `tick query` tells the average
# time a tick took; a tick's budget is TICK_MS, so anything over it is a server
# behind. The runner waits for it to settle before each scenario and, when it
# will not, says so beside the result rather than letting the miss read as a
# fault in the pack.

TICK_MS = 50.0
#: What counts as behind: the budget with a little room for the sprint's own tail.
BEHIND_MS = 60.0
#: How long the runner waits for the server to settle before a scenario.
SETTLE_TRIES, SETTLE_SECONDS = 12, 5


def tick_ms(answer):
    """The average milliseconds a tick took, out of `tick query`'s answer, or None."""
    found = re.search(r"Average time per tick:\s*([0-9]+(?:\.[0-9]+)?)\s*ms", answer)
    return float(found.group(1)) if found else None


def settled(rcon, verbose=False):
    """Waits until the server keeps up, or gives up; returns the last reading (None when unknown)."""
    last = None
    for attempt in range(SETTLE_TRIES):
        last = tick_ms(rcon.cmd("tick query"))
        if last is None or last <= BEHIND_MS:
            return last
        if verbose or attempt == 0:
            print(f"           (the server is behind: {last:.0f} ms a tick; waiting for it to settle)")
        time.sleep(SETTLE_SECONDS)
    return last


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

    passed = failed = under_load = 0
    for index, (name, fn) in enumerate(SCENARIOS):
        if args.only and args.only not in name:
            continue
        before = settled(rcon, args.verbose)
        stage = Stage(rcon, index, name)
        stage.clear()
        try:
            fn(stage)
        except Exception as boom:            # a scenario that throws is a failure with a reason
            stage.problems.append(f"the scenario itself failed: {boom!r}")
        after = tick_ms(rcon.cmd("tick query"))
        behind = max(ms for ms in (before, after, 0.0) if ms is not None)
        loaded = behind > BEHIND_MS
        under_load += loaded
        if stage.problems:
            failed += 1
            print(f"  FAIL   {name}" + (f"   (the server was behind: {behind:.0f} ms a tick; rerun it alone before believing this)" if loaded else ""))
            for problem in stage.problems:
                print(f"           {problem}")
        else:
            passed += 1
            print(f"  pass   {name}" + (f"   (under load: {behind:.0f} ms a tick)" if loaded else ""))
        if args.verbose:
            for command, out in stage.log:
                print(f"           > {command}\n             {out[:160]}")
    rcon.cmd("gamerule doDaylightCycle true")
    rcon.cmd("forceload remove all")
    print(f"live tests: {passed} passed, {failed} failed"
          + (f"; {under_load} ran with the server behind, so a FAIL among them is not yet a fault" if under_load else ""))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
