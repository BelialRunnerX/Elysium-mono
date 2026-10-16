"""
The live scenarios. Each builds a rig on its own plot, lets ticks pass, and
reads the result back. Coordinates are relative to the plot's origin.

A rig is described in one place, read the way a player would build it, so a
failing scenario names a thing a player would meet.
"""
from livetest import scenario, SERVER_LOG

RUNS = [
    # (run block, device block, own colour, a device face to check)
    ("elysiumspace:cable", "elysiumspace:coal_generator"),
    ("elysiumspace:pipe", "elysiumspace:fluid_tank"),
    ("elysiumindustry:copper_wire", "elysiumindustry:steam_turbine"),
    ("elysiumsolarpunk:power_conduit", "elysiumsolarpunk:solar_condenser"),
    ("elysiumlogistics:bus", "elysiumlogistics:timber_drawer"),
    ("elysiumatomics:coolant_channel", "elysiumatomics:reactor_core"),
]


@scenario("runs: every run ends in a plate against its device and arms between its own")
def runs_plates(t):
    for row, (run, device) in enumerate(RUNS):
        z = 2 + row * 3
        t.setblock(2, 0, z, device)
        t.fill((3, 0, z), (5, 0, z), run)
        t.advance(5)
        t.expect_state(3, 0, z, f"{run}[west=device,east=run]", f"{run} against {device}")
        t.expect_state(4, 0, z, f"{run}[west=run,east=run,north=none,south=none,up=none,down=none]",
                       f"{run} in the middle of its line")
        t.expect_state(5, 0, z, f"{run}[west=run,east=none]", f"{run} at the end of its line")
        t.setblock(6, 0, z, "minecraft:water")          # a bucket poured at the end of the line
        t.advance(40)
        t.expect_state(5, 0, z, run, f"water washed {run} away")
        t.setblock(6, 0, z, "minecraft:air")
        t.fill((3, 0, z), (7, 0, z), "minecraft:air")
        t.fill((3, 0, z), (5, 0, z), run)


@scenario("runs: a dyed run joins its own colour, an undyed run, or a device, and nothing else")
def runs_dye(t):
    cable = "elysiumspace:cable"
    # red, red, blue, undyed, then a generator
    t.fill((2, 0, 2), (5, 0, 2), cable)
    t.setblock(6, 0, 2, "elysiumspace:coal_generator")
    t.cmd(f"data merge block {t.at(2, 0, 2)} {{run_dye:\"red\"}}")
    t.cmd(f"data merge block {t.at(3, 0, 2)} {{run_dye:\"red\"}}")
    t.cmd(f"data merge block {t.at(4, 0, 2)} {{run_dye:\"blue\"}}")
    t.advance(5)
    t.expect_state(2, 0, 2, f"{cable}[east=run]", "red joins red")
    t.expect_state(3, 0, 2, f"{cable}[west=run,east=none]", "red refuses blue")
    t.expect_state(4, 0, 2, f"{cable}[west=none,east=run]", "blue refuses red and joins undyed")
    t.expect_state(5, 0, 2, f"{cable}[west=run,east=device]", "undyed joins blue and plates the generator")
    # dyeing the blue one red joins the line up again
    t.cmd(f"data merge block {t.at(4, 0, 2)} {{run_dye:\"red\"}}")
    t.advance(5)
    t.expect_state(3, 0, 2, f"{cable}[east=run]", "a re-dyed run tells its neighbour")
    t.expect_state(4, 0, 2, f"{cable}[west=run]", "a re-dyed run re-reads itself")


@scenario("space: a coal generator fills, and a cable carries it into a separator")
def space_power(t):
    t.setblock(2, 0, 2, "elysiumspace:coal_generator")
    t.hopper_into(2, 0, 2, "minecraft:coal", 16)
    t.fill((3, 0, 2), (5, 0, 2), "elysiumspace:cable")
    t.setblock(6, 0, 2, "elysiumspace:water_separator")
    t.advance(200)
    # the generator's own store reads 0 while a cable drains it as fast as it makes: ask whether it burns
    t.expect_number(2, 0, 2, "Burn", 1, "the generator never lit its coal")
    t.expect_number(6, 0, 2, "Energy", 1, "the cable carries nothing into the separator")


@scenario("space: a tank pours into the pipe below it, and the pipe carries the water into a separator")
def space_pipe(t):
    # a tank drains downwards, like a hopper; the pipe under it carries along its line
    t.setblock(2, 1, 2, "elysiumspace:fluid_tank")
    t.cmd(f"data merge block {t.at(2, 1, 2)} {{Tank0:{{Fluid:{{id:\"minecraft:water\",amount:8000}}}}}}")
    t.expect_number(2, 1, 2, "Tank0.Fluid.amount", 8000, "the tank did not take the water it was given")
    t.fill((2, 0, 2), (5, 0, 2), "elysiumspace:pipe")
    t.setblock(6, 0, 2, "elysiumspace:water_separator")
    t.advance(200)
    t.expect_number(6, 0, 2, "Tank0.Fluid.amount", 1, "the pipe carries no water into the separator")
    t.expect_number(2, 1, 2, "Tank0.Fluid.amount", 1, "the tank was emptied faster than it pours")


@scenario("space: a red cable does not carry into a blue one, and does into an undyed one")
def space_dyed_gap(t):
    t.setblock(2, 0, 2, "elysiumspace:coal_generator")
    t.fill((3, 0, 2), (4, 0, 2), "elysiumspace:cable")
    t.setblock(5, 0, 2, "elysiumspace:cable")
    t.setblock(6, 0, 2, "elysiumspace:water_separator")
    t.cmd(f"data merge block {t.at(3, 0, 2)} {{run_dye:\"red\"}}")
    t.cmd(f"data merge block {t.at(4, 0, 2)} {{run_dye:\"red\"}}")
    t.cmd(f"data merge block {t.at(5, 0, 2)} {{run_dye:\"blue\"}}")
    # Fuel last: with coal in first, a tick or two of an undyed line carried a
    # trickle into the separator before the dyes landed (one flake in 54, 27 Sep).
    t.hopper_into(2, 0, 2, "minecraft:coal", 16)
    t.advance(100)
    t.expect_number(3, 0, 2, "Energy", 1, "the generator fed nothing into the red cable")
    t.expect(t.number(6, 0, 2, "Energy") in (None, 0.0), "energy crossed from a red cable into a blue one")
    t.cmd(f"data merge block {t.at(5, 0, 2)} {{run_dye:\"none\"}}")
    t.advance(100)
    t.expect_number(6, 0, 2, "Energy", 1, "once the blue cable is undyed the red line still carries nothing")


@scenario("industry: a steam turbine on coal supplies a controller across copper wire")
def industry_grid(t):
    t.setblock(2, 0, 2, "elysiumindustry:steam_turbine")
    t.hopper_into(2, 0, 2, "minecraft:coal_block", 4)
    t.fill((3, 0, 2), (5, 0, 2), "elysiumindustry:copper_wire")
    t.setblock(6, 0, 2, "elysiumlogistics:controller")
    t.setblock(7, 0, 2, "elysiumlogistics:bus")           # a drawer on a bus is what makes the controller ask
    t.setblock(8, 0, 2, "elysiumlogistics:timber_drawer")
    t.advance(200)
    t.expect_number(6, 0, 2, "demand", 1, "the controller asks for nothing with a drawer on its bus")
    t.expect_number(2, 0, 2, "burning", 1, "the turbine never lit its coal")
    t.expect_number(6, 0, 2, "supply", 1, "the wire carries no supply to the controller")


@scenario("solarpunk: a condenser in daylight powers a bioreactor through a conduit")
def solarpunk_conduit(t):
    t.setblock(2, 0, 2, "elysiumsolarpunk:solar_condenser")
    t.setblock(2, 0, 4, "minecraft:daylight_detector")   # the sky as the game itself reads it here
    t.fill((3, 0, 2), (5, 0, 2), "elysiumsolarpunk:power_conduit")
    t.setblock(6, 0, 2, "elysiumsolarpunk:algae_bioreactor")
    t.advance(200)
    t.expect_state(2, 0, 4, "minecraft:daylight_detector[power=15]", "the stage itself has no full daylight")
    # the condenser's own store reads 0 while the conduit drains it as fast as it makes: ask the bioreactor
    t.expect_number(6, 0, 2, "energy", 1, "the condenser under an open sky at noon powers nothing through the conduit")


@scenario("logistics: an importer empties a chest into a drawer over a fed bus")
def logistics_bus(t):
    t.setblock(2, 0, 2, "elysiumindustry:steam_turbine")
    t.hopper_into(2, 0, 2, "minecraft:coal_block", 4)
    t.setblock(3, 0, 2, "elysiumlogistics:controller")
    t.setblock(4, 0, 2, "elysiumlogistics:bus")
    t.setblock(5, 0, 2, "elysiumlogistics:timber_drawer")
    t.setblock(3, 0, 3, "elysiumlogistics:bus")
    t.setblock(3, 0, 4, "elysiumlogistics:importer[facing=south]")
    t.chest(3, 0, 5)
    t.cmd(f"item replace block {t.at(3, 0, 5)} container.0 with minecraft:cobblestone 64")
    t.advance(400)
    t.expect_item(5, 0, 2, "minecraft:cobblestone", 1, "the drawer holds none of the chest's cobblestone",
                  path="store.slots")


# --- the network's other faces, and what a player fits to a drawer (29 September 2026)
# Each device on a fed bus: a steam turbine on coal beside the controller, as
# the importer above. There is no player here, so a device is told what to
# care about the way it saves itself (/data merge runs loadAdditional): its
# `filter` is the item it was shown, its `threshold` a rung of
# NetRules.THRESHOLDS, an ender link's `channel` the id it was shown. A
# drawer's store is written the same way: store.slots[N].kind / .count,
# store.voiding (the seal), store.locked (the key). The traps: a drawer's
# slots list carries the kind's own count of 1 beside the real count, so read
# store.slots[0].count rather than expect_item; and a channel is only joined
# on load or by hand (MECHANICS.md), so the ender link's plot is unloaded and
# loaded again before it is asked to carry anything.
import re as _re
import time as _time


def _text(t, dx, dy, dz, path):
    """A string NBT value, without its quotes, or ''."""
    m = _re.search(r'has the following block data: "([^"]*)"', t.data(dx, dy, dz, path))
    return m.group(1) if m else ""


def _count_is(t, dx, dy, dz, path, wanted, why):
    """Exactly this number at that path: the rules give numbers, not floors."""
    value = t.number(dx, dy, dz, path)
    return t.expect(value == wanted, f"{why}: {path} at {t.at(dx, dy, dz)} is {value}, wanted {wanted}")


def _drawer(t, dx, dy, dz, item, count, **flags):
    """A timber drawer holding this many of the item, sealed (voiding) or locked as asked."""
    t.setblock(dx, dy, dz, "elysiumlogistics:timber_drawer")
    slot = f'{{kind:{{id:"{item}",count:1}},count:{count}}}' if item else "{count:0}"
    more = "".join(f",{name}:{'1b' if on else '0b'}" for name, on in flags.items())
    out = t.cmd(f"data merge block {t.at(dx, dy, dz)} {{store:{{slots:[{slot}]{more}}}}}")
    if "Modified" not in out:
        t.problems.append(f"could not write the drawer at {t.at(dx, dy, dz)}: {out}")


def _fed_controller(t, dx, dy, dz):
    """A steam turbine on coal and, east of it, the controller it lights."""
    t.setblock(dx, dy, dz, "elysiumindustry:steam_turbine")
    t.hopper_into(dx, dy, dz, "minecraft:coal_block", 4)
    t.setblock(dx + 1, dy, dz, "elysiumlogistics:controller")


def _shown(t, dx, dy, dz, item, more=""):
    """Tell a device what to care about, as a right-click with the item would."""
    out = t.cmd(f'data merge block {t.at(dx, dy, dz)} {{filter:{{id:"{item}",count:1}}{more}}}')
    if "Modified" not in out:
        t.problems.append(f"could not set the filter of the device at {t.at(dx, dy, dz)}: {out}")


def _reload(t, *spots):
    """Unload the plot and load it again: what reopening a world does to every block entity on it."""
    x0, z0 = t.ox - 1, t.oz - 1
    x1, z1 = t.ox + t.PLOT - 2, t.oz + t.PLOT - 2

    def loaded():
        return [("Test passed" in t.cmd(f"execute if loaded {t.at(*spot)}")) for spot in spots]

    t.cmd(f"forceload remove {x0} {z0} {x1} {z1}")
    gone = False
    for _ in range(60):
        _time.sleep(0.5)
        if not any(loaded()):
            gone = True
            break
    t.cmd(f"forceload add {x0} {z0} {x1} {z1}")
    for _ in range(60):
        _time.sleep(0.5)
        if all(loaded()):
            break
    t.expect(gone, "the plot did not unload when its forceload was taken away, so nothing on it was loaded again")
    t.expect(all(loaded()), "the plot did not come back when it was force-loaded again")


@scenario("logistics: an exporter moves only the thing it was shown out of the network, and one shown nothing is idle")
def logistics_exporter(t):
    _fed_controller(t, 2, 0, 2)
    t.setblock(4, 0, 2, "elysiumlogistics:bus")
    _drawer(t, 5, 0, 2, "minecraft:cobblestone", 40)
    t.setblock(4, 0, 3, "elysiumlogistics:bus")
    _drawer(t, 5, 0, 3, "minecraft:dirt", 40)
    t.setblock(3, 0, 3, "elysiumlogistics:bus")
    t.setblock(3, 0, 4, "elysiumlogistics:exporter[facing=south]")
    _shown(t, 3, 0, 4, "minecraft:cobblestone")
    t.chest(3, 0, 5)
    t.setblock(2, 0, 3, "elysiumlogistics:exporter[facing=west]")     # shown nothing
    t.chest(1, 0, 3)
    t.advance(200)          # ten rounds of NetRules.TICK_EVERY; a round is a stack, the controller passes sixteen a tick
    t.expect_item(3, 0, 5, "minecraft:cobblestone", 40,
                  "the exporter shown cobblestone did not move the drawer's forty into its chest")
    t.expect(not any(i == "minecraft:dirt" for i, _ in t.items(3, 0, 5)),
             f"the exporter shown cobblestone moved dirt as well, into the chest at {t.at(3, 0, 5)}")
    _count_is(t, 5, 0, 2, "store.slots[0].count", 0, "the cobblestone drawer still holds what the exporter moved out")
    _count_is(t, 5, 0, 3, "store.slots[0].count", 40, "the dirt drawer lost dirt to an exporter shown cobblestone")
    t.expect(not t.items(1, 0, 3), f"an exporter shown nothing filled its chest at {t.at(1, 0, 3)}")


@scenario("logistics: a storage bus makes the chest it faces part of the network, so a hopper into the controller fills that chest")
def logistics_storage_bus(t):
    _fed_controller(t, 2, 0, 2)
    t.setblock(4, 0, 2, "elysiumlogistics:bus")
    t.setblock(5, 0, 2, "elysiumlogistics:storage_bus[facing=east]")
    t.chest(6, 0, 2)
    # Into the controller: the network owns no drawer, so the only room it has is the chest it borrows.
    t.hopper_into(3, 0, 2, "minecraft:cobblestone", 8)
    t.advance(200)
    t.expect_item(6, 0, 2, "minecraft:cobblestone", 8,
                  "the chest behind the storage bus did not take the hopper's eight cobblestone")
    t.expect(not t.items(3, 1, 2), f"the hopper into the controller still holds cobblestone at {t.at(3, 1, 2)}")


@scenario("logistics: every device costs its controller what the rules bill: one draw for a drawer or a monitor, two for a storage bus or an interface, four for an ender link")
def logistics_device_draws(t):
    _fed_controller(t, 2, 0, 2)
    t.fill((4, 0, 2), (8, 0, 2), "elysiumlogistics:bus")
    # LogisticRules.DEVICE_DRAW is 1.5 a slot; NetRules.DEVICES says how many slots' worth each device pays.
    steps = [
        ("elysiumlogistics:timber_drawer", 1.5),
        ("elysiumlogistics:monitor", 3.0),
        ("elysiumlogistics:storage_bus[facing=north]", 6.0),
        ("elysiumlogistics:interface", 9.0),
        ("elysiumlogistics:ender_link", 15.0),
    ]
    for at, (block, demand) in enumerate(steps):
        t.setblock(4 + at, 0, 1, block)
        t.advance(60)       # the controller looks again every forty ticks, then the grid is solved
        name = block.split(":")[1].split("[")[0]
        _count_is(t, 3, 0, 2, "demand", demand, f"with the {name} added, the controller asks the grid for the wrong draw")
    # A monitor keeps what it was shown, since that is all it has to say a count of.
    _shown(t, 5, 0, 1, "minecraft:cobblestone")
    t.expect(_text(t, 5, 0, 1, "filter.id") == "minecraft:cobblestone", "the monitor forgot what it was shown")


@scenario("logistics: a detector lights a lamp at its threshold: sixty-three cobblestone is dark, sixty-four is lit, sixty-three again is dark")
def logistics_detector(t):
    _fed_controller(t, 2, 0, 2)
    t.setblock(4, 0, 2, "elysiumlogistics:bus")
    t.setblock(5, 0, 2, "elysiumlogistics:timber_drawer")
    t.setblock(4, 0, 3, "elysiumlogistics:bus")
    t.setblock(4, 0, 4, "elysiumlogistics:detector[facing=south]")
    _shown(t, 4, 0, 4, "minecraft:cobblestone", ",threshold:64")       # the third rung of NetRules.THRESHOLDS
    t.setblock(5, 0, 4, "minecraft:redstone_lamp")
    for count, lit in ((63, False), (64, True), (63, False)):
        t.cmd(f'data merge block {t.at(5, 0, 2)} {{store:{{slots:[{{kind:{{id:"minecraft:cobblestone",count:1}},count:{count}}}]}}}}')
        t.advance(60)       # three rounds of the detector, and the lamp's four-tick delay
        t.expect_state(4, 0, 4, f"elysiumlogistics:detector[powered={str(lit).lower()}]",
                       f"holding {count} cobblestone against a threshold of 64, the detector's redstone is wrong")
        t.expect_state(5, 0, 4, f"minecraft:redstone_lamp[lit={str(lit).lower()}]",
                       f"holding {count} cobblestone against a threshold of 64, the lamp beside the detector is wrong")


@scenario("logistics: an interface hands the network to the hopper under it, which fills the chest below")
def logistics_interface(t):
    _fed_controller(t, 2, 2, 2)
    t.setblock(4, 2, 2, "elysiumlogistics:bus")
    _drawer(t, 5, 2, 2, "minecraft:cobblestone", 40)
    t.setblock(4, 2, 3, "elysiumlogistics:bus")
    t.setblock(4, 2, 4, "elysiumlogistics:interface[facing=south]")
    t.setblock(4, 1, 4, "minecraft:hopper[facing=down]")     # a hopper takes from whatever inventory is above it
    t.chest(4, 0, 4)
    t.advance(500)          # one item every eight ticks: forty in 320
    t.expect_item(4, 0, 4, "minecraft:cobblestone", 40,
                  "the hopper under the interface did not draw the drawer's forty cobblestone into the chest")
    _count_is(t, 5, 2, 2, "store.slots[0].count", 0, "the drawer still holds cobblestone the hopper under the interface took")


@scenario("logistics: an ender link carries a hopper's cobblestone from one network into the other's drawer over one channel, once the plot has loaded again")
def logistics_ender_link(t):
    # Network A: a controller and a link and no drawer of its own, so what a hopper gives it can only go through the link.
    _fed_controller(t, 2, 0, 2)
    t.setblock(4, 0, 2, "elysiumlogistics:bus")
    t.setblock(5, 0, 2, "elysiumlogistics:ender_link[facing=east]", '{channel:"minecraft:diamond"}')
    t.hopper_into(3, 0, 2, "minecraft:cobblestone", 8)
    # Network B, nine blocks of air away: its own turbine and controller, a link on the same channel, and a drawer.
    _fed_controller(t, 14, 0, 2)
    t.setblock(16, 0, 2, "elysiumlogistics:bus")
    t.setblock(17, 0, 2, "elysiumlogistics:ender_link[facing=east]", '{channel:"minecraft:diamond"}')
    t.setblock(16, 0, 3, "elysiumlogistics:timber_drawer")
    # /data names the channel; only a load or a right-click puts the link on its roster (MECHANICS.md).
    _reload(t, (5, 0, 2), (17, 0, 2))
    t.advance(300)
    _count_is(t, 16, 0, 3, "store.slots[0].count", 8,
              "the other network's drawer did not receive the eight cobblestone the hopper gave the first")
    t.expect(_text(t, 16, 0, 3, "store.slots[0].kind.id") == "minecraft:cobblestone",
             f"the other network's drawer holds something other than cobblestone at {t.at(16, 0, 3)}")
    t.expect(not t.items(3, 1, 2), f"the hopper into the first network's controller still holds cobblestone at {t.at(3, 1, 2)}")


@scenario("logistics: a void seal destroys what a full drawer cannot take, a plain drawer refuses it, and a key keeps an emptied drawer's kind")
def logistics_seal_and_key(t):
    room = 32 * 64                                              # LogisticRules.TIMBER_DRAWER: 32 stacks of one kind
    _drawer(t, 2, 0, 2, "minecraft:cobblestone", room - 8, voiding=True)      # sealed, eight short of full
    t.hopper_into(2, 0, 2, "minecraft:cobblestone", 64)
    _drawer(t, 2, 0, 5, "minecraft:cobblestone", room - 8)                    # plain, eight short of full
    t.hopper_into(2, 0, 5, "minecraft:cobblestone", 64)
    _drawer(t, 2, 0, 8, "minecraft:cobblestone", 0, locked=True)              # locked to cobblestone, and run dry
    t.hopper_into(2, 0, 8, "minecraft:dirt", 8)
    t.setblock(2, 0, 11, "elysiumlogistics:timber_drawer")                    # bare and unlocked
    t.hopper_into(2, 0, 11, "minecraft:dirt", 8)
    t.advance(600)          # a hopper moves one item every eight ticks: sixty-four in 512
    _count_is(t, 2, 0, 2, "store.slots[0].count", room, "the sealed drawer did not fill to its 2,048")
    t.expect(not t.items(2, 1, 2),
             f"the hopper on the sealed drawer still holds cobblestone at {t.at(2, 1, 2)}, so the seal voided nothing")
    _count_is(t, 2, 0, 5, "store.slots[0].count", room, "the plain drawer did not fill to its 2,048")
    t.expect_item(2, 1, 5, "minecraft:cobblestone", 56, "the plain drawer took more than its 2,048: the hopper on it should keep 56")
    _count_is(t, 2, 0, 8, "store.slots[0].count", 0, "the locked drawer took dirt")
    t.expect(_text(t, 2, 0, 8, "store.slots[0].kind.id") == "minecraft:cobblestone",
             "the locked drawer, run dry, forgot the cobblestone it was locked to")
    t.expect_item(2, 1, 8, "minecraft:dirt", 8, "the hopper on the locked drawer lost dirt")
    _count_is(t, 2, 0, 11, "store.slots[0].count", 8, "the bare drawer did not take the hopper's dirt")
    # The other half of the lock: the kind it keeps is the kind it still takes.
    t.cmd(f"item replace block {t.at(2, 1, 8)} container.0 with minecraft:cobblestone 8")
    t.advance(100)
    _count_is(t, 2, 0, 8, "store.slots[0].count", 8, "the locked drawer refused the cobblestone it is locked to")


@scenario("inventories: every machine with a top face takes what a hopper offers")
def inventories_in(t):
    rigs = [
        ("elysiumspace:coal_generator", "minecraft:coal", "Fuel.Items"),
        ("elysiumindustry:steam_turbine", "minecraft:coal_block", "items"),
        ("elysiumlogistics:timber_drawer", "minecraft:cobblestone", "store.slots"),
    ]
    for row, (block, item, path) in enumerate(rigs):
        z = 2 + row * 3
        t.setblock(2, 0, z, block)
        t.hopper_into(2, 0, z, item, 8)
        t.advance(60)
        t.expect_item(2, 0, z, item, 1, f"{block} took nothing from the hopper above it", path=path)


@scenario("solarpunk: a powered harvester cuts ripe wheat and drops it into the chest below")
def solarpunk_harvester(t):
    t.setblock(2, 0, 2, "elysiumsolarpunk:solar_condenser")
    t.fill((3, 0, 2), (5, 0, 2), "elysiumsolarpunk:power_conduit")
    t.setblock(5, 1, 2, "elysiumsolarpunk:power_conduit")   # up one, to the harvester's level
    t.setblock(6, 1, 2, "elysiumsolarpunk:harvester")
    t.setblock(6, 0, 2, "minecraft:chest")          # the tray is pushed into whatever sits below
    t.fill((6, 0, 3), (6, 0, 5), "minecraft:farmland")
    t.fill((6, 1, 3), (6, 1, 5), "minecraft:wheat[age=7]")
    t.advance(600)
    t.expect_item(6, 0, 2, "minecraft:wheat", 1, "the harvester cut no wheat into the chest below it")


# --- the machines that make things -----------------------------------------
# Each rig: a source of what the machine runs on, the machine, a hopper above
# it holding the input, and a read of the product slot. These are the twenty
# machines that never produced anything before 27 September 2026 (they asked
# their own refusing output slot for room), plus the ones that always did.

def _slot(t, dx, dy, dz, path, slot, item, least, why):
    """The count of an item in one numbered slot of an ItemStackHandler."""
    found = sum(c for i, c in t.items(dx, dy, dz, f"{path}.Items[{{Slot:{slot}}}]") if i == item)
    return t.expect(found >= least, f"{why}: {found} x {item} in slot {slot} at {t.at(dx, dy, dz)}, wanted at least {least}")


def _grid(t, z, medium=False):
    """A lit engine and three wires along z, ending at x=6: a steam turbine on copper (low
    voltage) or a diesel engine on gold (medium), which the arc furnace and the separator want."""
    t.setblock(2, 0, z, "elysiumindustry:diesel_engine" if medium else "elysiumindustry:steam_turbine")
    t.hopper_into(2, 0, z, "minecraft:coal_block", 8)
    t.fill((3, 0, z), (5, 0, z), "elysiumindustry:gold_wire" if medium else "elysiumindustry:copper_wire")


@scenario("industry: a crusher on a lit grid turns bone blocks into bone meal")
def industry_crusher(t):
    _grid(t, 2)
    t.setblock(6, 0, 2, "elysiumindustry:crusher")
    t.hopper_into(6, 0, 2, "minecraft:bone_block", 4)
    t.advance(600)
    _slot(t, 6, 0, 2, "items", 1, "minecraft:bone_meal", 1, "the crusher crushed nothing")


@scenario("industry: a rolling mill, an arc furnace and a separator each make their product on a grid")
def industry_plants(t):
    rigs = [("elysiumindustry:rolling_mill", "minecraft:copper_ingot", "elysiumindustry:copper_plate", False),
            ("elysiumindustry:arc_furnace", "minecraft:clay_ball", "minecraft:brick", True),
            ("elysiumindustry:separator", "minecraft:raw_copper", "minecraft:copper_ingot", True)]
    for row, (block, put, made, medium) in enumerate(rigs):
        z = 2 + row * 4
        _grid(t, z, medium)
        t.setblock(6, 0, z, block)
        t.hopper_into(6, 0, z, put, 4)
    t.advance(900)
    for row, (block, put, made, medium) in enumerate(rigs):
        _slot(t, 6, 0, 2 + row * 4, "items", 1, made, 1, f"{block} made nothing of its {put}")


def _vertical(t, x, z, machine, put):
    """A machine driven from above: engine, shaft, machine down a column; the input comes in from the side."""
    t.setblock(x, 2, z, "elysiumkinetics:furnace_engine[axis=y]")
    t.hopper_into(x, 2, z, "minecraft:coal", 8)
    t.setblock(x, 1, z, "elysiumkinetics:timber_shaft[axis=y]")
    t.setblock(x, 0, z, machine)
    t.setblock(x + 1, 0, z, "minecraft:hopper[facing=west]")
    out = t.cmd(f"item replace block {t.at(x + 1, 0, z)} container.0 with {put} 8")
    if "Replaced" not in out:
        t.problems.append(f"could not fill the side hopper with {put}: {out}")


@scenario("kinetics: a furnace engine turns a shaft line, and the millstone on it grinds bone")
def kinetics_millstone(t):
    _vertical(t, 5, 2, "elysiumkinetics:millstone", "minecraft:bone")   # a millstone turns on a vertical shaft
    t.advance(900)
    t.expect_number(5, 2, 2, "left", 1, "the engine never lit its coal")
    _slot(t, 5, 0, 2, "items", 1, "minecraft:bone_meal", 1, "the millstone ground nothing")


@scenario("kinetics: the press, the saw, the mixer and the refinery each make their product on a shaft")
def kinetics_machines(t):
    rigs = [("elysiumkinetics:press", "minecraft:cactus", "minecraft:green_dye"),
            ("elysiumkinetics:saw", "minecraft:bamboo_block", "minecraft:bamboo"),
            ("elysiumkinetics:mixer", "minecraft:charcoal", "minecraft:coal"),
            ("elysiumkinetics:refinery", "minecraft:coal_block", "elysiumkinetics:creosote")]
    for row, (block, put, made) in enumerate(rigs):
        z = 2 + row * 4
        if block.endswith(":press") or block.endswith(":mixer"):
            _vertical(t, 5, z, block, put)          # the press and the mixer stand on a vertical shaft
        else:
            t.setblock(2, 0, z, "elysiumkinetics:furnace_engine[axis=x]")
            t.hopper_into(2, 0, z, "minecraft:coal", 8)
            t.fill((3, 0, z), (4, 0, z), "elysiumkinetics:timber_shaft[axis=x]")
            t.setblock(5, 0, z, f"{block}[axis=x]")
            t.hopper_into(5, 0, z, put, 4)
    t.advance(1200)
    for row, (block, put, made) in enumerate(rigs):
        _slot(t, 5, 0, 2 + row * 4, "items", 1, made, 1, f"{block} made nothing of its {put}")


@scenario("foundry: the alloy smelter makes bronze and the refinery splits raw iron, on a grid")
def foundry_machines(t):
    _grid(t, 2)
    t.setblock(6, 0, 2, "elysiumfoundry:alloy_smelter")
    t.setblock(6, 1, 2, "minecraft:hopper[facing=down]")
    t.cmd(f"item replace block {t.at(6, 1, 2)} container.0 with minecraft:copper_ingot 12")
    t.cmd(f"item replace block {t.at(6, 1, 2)} container.1 with elysiumfoundry:tin_ingot 4")
    # the refinery is a high-voltage machine: a diesel engine's medium grid stepped up through a transformer
    t.setblock(2, 0, 6, "elysiumindustry:diesel_engine")
    t.hopper_into(2, 0, 6, "minecraft:coal_block", 8)
    t.setblock(3, 0, 6, "elysiumindustry:gold_wire")
    t.setblock(4, 0, 6, "elysiumindustry:transformer")
    t.setblock(5, 0, 6, "elysiumindustry:steel_wire")
    t.setblock(6, 0, 6, "elysiumfoundry:refinery")
    t.hopper_into(6, 0, 6, "elysiumfoundry:raw_aluminum", 4)
    t.advance(900)
    _slot(t, 6, 0, 2, "items", 2, "elysiumfoundry:bronze_ingot", 1, "the alloy smelter made no bronze")
    _slot(t, 6, 0, 6, "items", 1, "elysiumfoundry:aluminum_ingot", 1, "the refinery refined no aluminium")
    _slot(t, 6, 0, 6, "items", 2, "elysiumfoundry:lithium_nugget", 1, "the refinery recovered no lithium")


@scenario("agronomy: a rock generator makes cobblestone by itself, and a centrifuge spins a comb on a grid")
def agronomy_machines(t):
    t.setblock(2, 0, 2, "elysiumagronomy:rock_generator")
    _grid(t, 6)
    t.setblock(6, 0, 6, "elysiumagronomy:centrifuge")
    t.hopper_into(6, 0, 6, "elysiumagronomy:iron_comb", 4)
    t.advance(2400)
    _slot(t, 2, 0, 2, "items", 0, "minecraft:cobblestone", 1, "the rock generator made no cobblestone in two hours")
    made = t.items(6, 0, 6, "items.Items")
    t.expect(any(i != "elysiumagronomy:iron_comb" for i, c in made),
             f"the centrifuge spun nothing out of the comb: {made}; spun {t.number(6, 0, 6, 'spun')}, "
             f"share {t.number(6, 0, 6, 'share')}, supply {t.number(6, 0, 6, 'supply')}, demand {t.number(6, 0, 6, 'demand')}")


@scenario("agronomy: a hive with flowers around it fills with comb")
def agronomy_hive(t):
    t.setblock(4, 0, 4, "elysiumagronomy:hive")
    t.hopper_into(4, 0, 4, "elysiumagronomy:bee", 1)
    for dx, dz in ((2, 2), (6, 2), (2, 6), (6, 6), (4, 2), (4, 6), (2, 4), (6, 4)):
        t.setblock(dx, 0, dz, "minecraft:grass_block")
        t.setblock(dx, 1, dz, "minecraft:dandelion")
    t.advance(75_000)      # a hive fills one comb an hour, and an hour is 72 000 ticks at eight flowers' pace
    t.expect_number(4, 0, 4, "flowers", 1, "the hive counted no flowers")
    made = [i for i, c in t.items(4, 0, 4, "items.Items") if i.endswith("comb")]
    t.expect(bool(made), f"the hive made no comb in forty thousand ticks among eight flowers "
                         f"(worked {t.number(4, 0, 4, 'worked')})")


# --- the stations around the water and in the kitchen ------------------------
# The worm farm, the tackle box, the bobbers, the skillet and the cutting board
# were the agronomy blocks PLAYTEST.md called unverified until 29 September
# 2026. Each is asked what its rules say without a player: a block entity's
# ticker sprints, a box's slots answer a hopper, and `data merge` runs
# loadAdditional, so a farm or a station can be handed the state a player's
# hands would have put in it. What only a hand can do - feed a farm a fish,
# put a knife to a board, cast a bobber - is said so, and the client stage
# has it.

import glob as _glob
import json as _json
import os as _os
import re as _re

_AGRONOMY = "elysiumagronomy"
_AGRONOMY_DATA = _os.path.join(_os.path.dirname(_os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))),
                               "elysium-agronomy", "src", "main", "resources", "data", _AGRONOMY)


def _station(t, dx, dy, dz, block, contents, making):
    """A kitchen station handed what a player put in it and told to start, in StationBlockEntity's own tags."""
    t.setblock(dx, dy, dz, block)
    listed = ",".join(f'{{id:"{item}",count:1}}' for item in contents)
    t.cmd(f'data merge block {t.at(dx, dy, dz)} {{contents:[{listed}],cooking:1b,making:"{making}",progress:0}}')


@scenario("agronomy: a worm farm fed a river fish makes river bait one every 1 200 ticks, stops at sixteen, and drops them when broken")
def agronomy_worm_farm(t):
    farm = f"{_AGRONOMY}:worm_farm"
    # Feeding is a fish in a hand (WormFarmBlock.useItemOn); what it leaves in
    # the farm is the fish's family (TackleRules.baitFor), so the farm is handed that.
    t.setblock(2, 0, 2, farm)
    t.cmd(f'data merge block {t.at(2, 0, 2)} {{family:"river"}}')
    t.expect('"river"' in t.data(2, 0, 2, "family"), "the farm did not keep the family it was fed")
    t.setblock(5, 0, 2, farm)                                   # never fed
    t.setblock(8, -1, 2, "minecraft:hopper")                    # catches what the full farm drops
    t.setblock(8, 0, 2, farm)                                   # full, and a tick from its next bait
    t.cmd(f'data merge block {t.at(8, 0, 2)} {{family:"lake",held:16,worked:1199}}')
    t.advance(2500)                        # TackleRules.WORM_TICKS is 1 200: two baits and change
    held = t.number(2, 0, 2, "held")
    t.expect(held == 2, f"a fed farm made {held} bait in 2 500 ticks; one every 1 200 ticks is two")
    t.expect(t.number(5, 0, 2, "held") in (None, 0.0), f"a farm nobody fed made {t.number(5, 0, 2, 'held')} bait")
    t.expect(t.number(8, 0, 2, "held") == 16,
             f"a full farm went past sixteen: {t.number(8, 0, 2, 'held')} (TackleRules.WORM_FARM_HOLDS)")
    # Emptying is an empty hand (useWithoutItem); breaking it drops the same
    # stack, as its family's bait item, and the hopper under it says how many.
    t.setblock(8, 0, 2, "minecraft:air")
    t.advance(40)
    t.expect_item(8, -1, 2, f"{_AGRONOMY}:lake_bait", 16,
                  "a broken farm holding sixteen did not drop sixteen lake bait into the hopper under it")


@scenario("agronomy: a tackle box takes every hook, bobber, bait and rod a hopper offers, refuses cobblestone, and empties into a hopper below")
def agronomy_tackle_box(t):
    box = f"{_AGRONOMY}:tackle_box"
    # TackleRules.isTackle: the four hooks and four bobbers, a family's bait, anything called a rod
    tackle = ["iron_hook", "barbed_hook", "double_hook", "gilded_hook",
              "cork_bobber", "glow_bobber", "lead_bobber", "lantern_bobber", "river_bait", "reed_rod"]
    for column, item in enumerate(tackle):
        t.setblock(2 + column * 2, 0, 2, box)
        t.hopper_into(2 + column * 2, 0, 2, f"{_AGRONOMY}:{item}", 1)
    t.setblock(2, 0, 6, box)                                    # what is not tackle stays in the hopper
    t.hopper_into(2, 0, 6, "minecraft:cobblestone", 8)
    t.setblock(6, 1, 6, box)                                    # a hopper under a box draws from it, as from a chest
    t.cmd(f"item replace block {t.at(6, 1, 6)} container.0 with {_AGRONOMY}:barbed_hook")
    t.setblock(6, 0, 6, "minecraft:hopper[facing=down]")
    t.chest(6, -1, 6)
    t.advance(100)
    for column, item in enumerate(tackle):
        t.expect_item(2 + column * 2, 0, 2, f"{_AGRONOMY}:{item}", 1,
                      f"the tackle box took no {item} from the hopper above it", path="Items")
    t.expect(not any(i == "minecraft:cobblestone" for i, c in t.items(2, 0, 6, "Items")),
             "a tackle box took cobblestone, so it is a chest with a fish on it")
    t.expect_item(2, 1, 6, "minecraft:cobblestone", 8, "the cobblestone the box refused is gone from the hopper")
    t.expect_item(6, -1, 6, f"{_AGRONOMY}:barbed_hook", 1, "a hopper under a tackle box drew nothing out of it")


@scenario("agronomy: the four bobbers are items that stack to one and sit on a rod as the component its cast reads")
def agronomy_bobbers(t):
    # A cast is a hand's: ElysiumRodItem.use throws the float that carries the
    # bobber and the game's own float does the waiting, so the quarter a cork
    # takes off the wait (WaterRules.waitFor) and the band a lead one opens
    # (TackleRules.reaches) are checked from the client (PLAYTEST.md). What the
    # server answers: each of TackleRules.BOBBERS is an item, a rod carries it
    # in the elysiumagronomy:bobber component and reads it back
    # (AgronomyComponents.bobberOn), and two of one do not stack.
    t.chest(2, 0, 2)
    for slot, name in enumerate(("cork_bobber", "glow_bobber", "lead_bobber", "lantern_bobber")):
        given = t.cmd(f"item replace block {t.at(2, 0, 2)} container.{slot} with {_AGRONOMY}:{name}")
        t.expect("Replaced" in given, f"{name} is not an item the game can give: {given[:80]}")
        rod = t.cmd(f'item replace block {t.at(2, 0, 2)} container.{slot + 9} with '
                    f'{_AGRONOMY}:reed_rod[{_AGRONOMY}:bobber="{name}"]')
        t.expect("Replaced" in rod, f"a reed rod cannot carry {name} as its bobber: {rod[:80]}")
        read = t.cmd(f'execute if items block {t.at(2, 0, 2)} container.{slot + 9} '
                     f'{_AGRONOMY}:reed_rod[{_AGRONOMY}:bobber="{name}"]')
        t.expect("passed" in read, f"a rod fitted with {name} does not read it back: {read[:80]}")
    other = t.cmd(f'execute if items block {t.at(2, 0, 2)} container.9 {_AGRONOMY}:reed_rod[{_AGRONOMY}:bobber="glow_bobber"]')
    t.expect("passed" not in other, "a rod fitted with a cork reads as fitted with a glow bobber")
    # two corks through a hopper land in two slots of one each: a bobber stacks to one
    t.setblock(5, 1, 2, "minecraft:hopper[facing=down]")
    t.cmd(f"item replace block {t.at(5, 1, 2)} container.0 with {_AGRONOMY}:cork_bobber")
    t.cmd(f"item replace block {t.at(5, 1, 2)} container.1 with {_AGRONOMY}:cork_bobber")
    t.chest(5, 0, 2)
    t.advance(60)
    corks = [c for i, c in t.items(5, 0, 2, "Items") if i == f"{_AGRONOMY}:cork_bobber"]
    t.expect(corks == [1, 1], f"two cork bobbers through a hopper became {corks}; a bobber stacks to one")


@scenario("agronomy: a skillet over a fire fries bacon and eggs in 240 ticks, waits while it is cold, and stops on what is not a recipe")
def agronomy_skillet(t):
    pan = f"{_AGRONOMY}:skillet"
    meal = f"{_AGRONOMY}:bacon_and_eggs"
    of = [f"{_AGRONOMY}:bacon", "minecraft:egg"]                # cooking/bacon_and_eggs.json: skillet, 240 ticks, one serving
    t.setblock(2, -1, 2, "minecraft:magma_block")               # one of KitchenRules.HEAT
    _station(t, 2, 0, 2, pan, of, meal)
    _station(t, 5, 0, 2, pan, of, meal)                         # on bare stone: cold, and a skillet needsHeat
    t.setblock(8, -1, 2, "minecraft:campfire")
    _station(t, 8, 0, 2, pan, of[:1], meal)                     # bacon alone is not what it was told to make
    t.advance(120)
    t.expect_number(2, 0, 2, "progress", 100, "the skillet over magma is not frying")
    t.expect(not t.items(2, 0, 2, "made"), "the skillet was done inside 160 ticks; bacon and eggs take 240")
    t.advance(200)
    t.expect_item(2, 0, 2, meal, 1, "the skillet over magma fried no bacon and eggs in 320 ticks", path="made")
    t.expect(not t.items(2, 0, 2, "contents"), "the skillet kept its bacon and egg after frying them")
    t.expect(t.number(5, 0, 2, "cooking") == 1 and t.number(5, 0, 2, "progress") == 0,
             f"a cold skillet did not wait for a fire: cooking {t.number(5, 0, 2, 'cooking')}, "
             f"progress {t.number(5, 0, 2, 'progress')}")
    t.expect(t.number(8, 0, 2, "cooking") == 0 and not t.items(8, 0, 2, "made"),
             "a skillet holding bacon alone went on making bacon and eggs; a recipe is exactly its ingredients")
    t.setblock(5, -1, 2, "minecraft:campfire")                  # a fire lit under the cold one, and it goes
    t.advance(300)
    t.expect_item(5, 0, 2, meal, 1, "a skillet lit after waiting cold fried nothing", path="made")


@scenario("agronomy: a cutting board makes a sandwich, pasta and a crust in 200 ticks with no fire, refuses two dough, and the server read its cuts")
def agronomy_cutting_board(t):
    a = _AGRONOMY
    board = f"{a}:cutting_board"
    _station(t, 2, 0, 2, board, ["minecraft:bread", f"{a}:ham", f"{a}:cabbage_leaf"], f"{a}:ham_sandwich")   # cooking/, 200 ticks
    _station(t, 5, 0, 2, board, [f"{a}:dough"], f"{a}:pasta")                                          # larder/, PREP_TICKS
    _station(t, 8, 0, 2, board, [f"{a}:dough", "minecraft:wheat"], f"{a}:pie_crust")                 # larder/
    _station(t, 11, 0, 2, board, [f"{a}:dough", f"{a}:dough"], f"{a}:pasta")                          # two dough are not one
    _station(t, 14, 0, 2, board, [f"{a}:bacon", "minecraft:egg"], f"{a}:bacon_and_eggs")              # the skillet's meal, on a board
    t.advance(100)
    t.expect_number(2, 0, 2, "progress", 80, "a board on bare stone is not working; a board wants no fire")
    t.expect(not t.items(2, 0, 2, "made"), "the board was done inside 140 ticks; a sandwich takes 200")
    t.advance(200)
    for dx, made in ((2, f"{a}:ham_sandwich"), (5, f"{a}:pasta"), (8, f"{a}:pie_crust")):
        t.expect_item(dx, 0, 2, made, 1, f"the board made no {made} in 300 ticks", path="made")
    t.expect(t.number(11, 0, 2, "cooking") == 0 and not t.items(11, 0, 2, "made"),
             "two dough on a board became pasta; a recipe is exactly its ingredients")
    t.expect(t.number(14, 0, 2, "cooking") == 0 and not t.items(14, 0, 2, "made"),
             "a board fried bacon and eggs, which is the skillet's")
    # The knife is a hand's: StationBlockEntity.slice wears the knife the player
    # holds, so the cut itself is a client check. What the server answers: every
    # knife the cutting folder names and everything it yields is an item, and
    # the folder as the server read it (AgronomyData's line in its log) is the
    # folder in the tree.
    tools, yields = set(), set()
    for path in _glob.glob(_os.path.join(_AGRONOMY_DATA, "cutting", "*.json")):
        with open(path, encoding="utf-8") as f:
            cut = _json.load(f)
        tools.add(cut["tool"])
        yields.update(one["item"] for one in cut["yields"])
    knives = {"knife": [f"{a}:{kind}_knife" for kind in ("flint", "iron", "golden", "diamond", "netherite")],
              "fillet_knife": [f"{a}:fillet_knife"]}
    t.expect(tools <= set(knives), f"the cutting folder asks for a tool no knife is called: {sorted(tools - set(knives))}")
    t.chest(2, 0, 6)
    for slot, item in enumerate(sorted(yields) + [k for tool in sorted(tools) for k in knives.get(tool, [])]):
        given = t.cmd(f"item replace block {t.at(2, 0, 6)} container.{slot % 27} with {item}")
        t.expect("Replaced" in given, f"{item}, which a knife cuts with or cuts to, is not an item the game can give: {given[:80]}")
    on_disk = (len(_glob.glob(_os.path.join(_AGRONOMY_DATA, "cooking", "*.json")))
               + len(_glob.glob(_os.path.join(_AGRONOMY_DATA, "larder", "*.json"))),
               len(_glob.glob(_os.path.join(_AGRONOMY_DATA, "cutting", "*.json"))))
    log = _os.environ.get("ELYSIUM_SERVER_LOG", SERVER_LOG)
    known = None
    if _os.path.isfile(log):
        with open(log, encoding="utf-8", errors="replace") as f:
            for line in f:
                if "the kitchen and the pots know" in line:
                    known = line.strip()
    if known is None:
        # The line is printed at data-pack load, so a log that has rolled over since the
        # server started (log4j rolls it daily) no longer holds it.
        why = "no server log at" if not _os.path.isfile(log) else "no data-pack load line left in"
        print(f"           ({why} {log}, so how many cuts the server read is not checked; ELYSIUM_SERVER_LOG names it)")
    else:
        read = _re.search(r"(\d+) order\(s\), (\d+) cut\(s\)", known)
        t.expect(read is not None and (int(read.group(1)), int(read.group(2))) == on_disk,
                 f"the server read {known.split('know', 1)[-1].strip()}; the tree has {on_disk[0]} orders and {on_disk[1]} cuts")


@scenario("solarpunk: the kiln fires on coal, the press and the loom run on the condenser, the tank grows carrots")
def solarpunk_processors(t):
    t.setblock(2, 0, 2, "elysiumsolarpunk:ceramic_kiln")
    t.setblock(2, 1, 2, "minecraft:hopper[facing=down]")
    t.cmd(f"item replace block {t.at(2, 1, 2)} container.0 with minecraft:cobblestone 8")
    t.cmd(f"item replace block {t.at(2, 1, 2)} container.1 with minecraft:coal 8")
    # one condenser, a conduit line, three machines hanging off it
    t.setblock(2, 0, 6, "elysiumsolarpunk:solar_condenser")
    t.fill((3, 0, 6), (9, 0, 6), "elysiumsolarpunk:power_conduit")
    t.setblock(4, 0, 7, "elysiumsolarpunk:mycelium_press")
    t.hopper_into(4, 0, 7, "minecraft:bamboo", 16)
    t.setblock(6, 0, 7, "elysiumsolarpunk:fibre_loom")
    t.hopper_into(6, 0, 7, "minecraft:bamboo", 16)
    t.setblock(8, 0, 7, "elysiumsolarpunk:aquaponics_tank")
    t.hopper_into(8, 0, 7, "minecraft:carrot", 4)
    t.setblock(9, 0, 7, "minecraft:water")
    t.setblock(8, 2, 7, "minecraft:glowstone")        # block light, which the tank asks for
    t.advance(1200)
    _slot(t, 2, 0, 2, "slots", 2, "minecraft:stone", 1, "the kiln fired no stone")
    _slot(t, 4, 0, 7, "slots", 1, "elysiumsolarpunk:plant_fibre", 1, "the press pressed no fibre")
    _slot(t, 6, 0, 7, "slots", 1, "elysiumsolarpunk:woven_cane", 1, "the loom wove nothing")
    _slot(t, 8, 0, 7, "slots", 1, "minecraft:carrot", 1, "the tank grew no carrots")


@scenario("solarpunk: a tree harvester fells the oak beside it into the chest below")
def solarpunk_tree_harvester(t):
    t.setblock(2, 0, 2, "elysiumsolarpunk:solar_condenser")
    t.fill((3, 0, 2), (5, 0, 2), "elysiumsolarpunk:power_conduit")
    t.setblock(6, 0, 2, "elysiumsolarpunk:tree_harvester")   # it scans its own level, where a trunk's base is
    t.setblock(6, -1, 2, "minecraft:chest")                  # the tray is pushed into whatever sits below
    t.setblock(8, -1, 2, "minecraft:dirt")          # a trunk counts only on ground a sapling would take
    t.fill((7, 3, 1), (9, 6, 3), "minecraft:oak_leaves[persistent=true]")
    t.fill((8, 0, 2), (8, 4, 2), "minecraft:oak_log[axis=y]")   # the trunk through the canopy, nine leaves touching it
    t.advance(1200)
    t.expect_item(6, -1, 2, "minecraft:oak_log", 1, "the tree harvester put no log in the chest below it")


@scenario("inventories: every machine with a hopper-fed slot takes its input from a hopper above")
def inventories_all(t):
    rigs = [
        ("elysiumindustry:crusher", "minecraft:bone_block", "items.Items"),
        ("elysiumindustry:rolling_mill", "minecraft:copper_ingot", "items.Items"),
        ("elysiumindustry:arc_furnace", "minecraft:clay_ball", "items.Items"),
        ("elysiumindustry:separator", "minecraft:raw_copper", "items.Items"),
        ("elysiumindustry:diesel_engine", "minecraft:coal", "items.Items"),
        ("elysiumkinetics:millstone", "minecraft:bone", "items.Items"),
        ("elysiumkinetics:press", "minecraft:cactus", "items.Items"),
        ("elysiumkinetics:saw", "minecraft:bamboo_block", "items.Items"),
        ("elysiumkinetics:mixer", "minecraft:charcoal", "items.Items"),
        ("elysiumkinetics:refinery", "minecraft:coal_block", "items.Items"),
        ("elysiumkinetics:furnace_engine", "minecraft:coal", "items.Items"),
        ("elysiumkinetics:oil_engine", "minecraft:coal", "items.Items"),
        ("elysiumkinetics:distillate_engine", "minecraft:coal", "items.Items"),
        ("elysiumfoundry:alloy_smelter", "minecraft:copper_ingot", "items.Items"),
        ("elysiumfoundry:refinery", "elysiumfoundry:raw_aluminum", "items.Items"),
        ("elysiumagronomy:centrifuge", "elysiumagronomy:iron_comb", "items.Items"),
        ("elysiumagronomy:hive", "elysiumagronomy:bee", "items.Items"),
        ("elysiumagronomy:pot", "elysiumagronomy:coal_seed", "items.Items"),
        ("elysiumagronomy:tackle_box", "elysiumagronomy:barbed_hook", "Items"),
        ("elysiumatomics:enricher", "minecraft:raw_copper", "items.Items"),
        ("elysiumatomics:reaction_chamber", "minecraft:charcoal", "items.Items"),
        ("elysiumatomics:centrifuge", "minecraft:netherrack", "items.Items"),
        ("elysiumlogistics:crate", "minecraft:cobblestone", "Items"),
        ("elysiumsolarpunk:ceramic_kiln", "minecraft:cobblestone", "slots.Items"),
        ("elysiumsolarpunk:mycelium_press", "minecraft:bamboo", "slots.Items"),
        ("elysiumsolarpunk:fibre_loom", "minecraft:bamboo", "slots.Items"),
        ("elysiumsolarpunk:aquaponics_tank", "minecraft:carrot", "slots.Items"),
        ("elysiumspace:freight_terminal", "minecraft:cobblestone", "Items"),
    ]
    for row, (block, item, path) in enumerate(rigs):
        dx, dz = 2 + (row % 7) * 3, 2 + (row // 7) * 3
        t.setblock(dx, 0, dz, block)
        t.hopper_into(dx, 0, dz, item, 1 if item.endswith("_hook") else 4)
    t.advance(60)
    for row, (block, item, path) in enumerate(rigs):
        dx, dz = 2 + (row % 7) * 3, 2 + (row // 7) * 3
        t.expect_item(dx, 0, dz, item, 1, f"{block} took nothing from the hopper above it", path=path)


# --- the catalogue, one at a time --------------------------------------------
# docs/features.json (tools/catalogue.py) names every block, item and creature
# the modules add. Every block is set on a floor and read back; every item is
# put in a chest; every creature is summoned and looked for. A block that
# will not stand on grass is tried on farmland and in water before it is
# reported, so a crop or a kelp is not a failure - a block that stands nowhere
# is. This is the long one: run it with --only catalogue.

import json as _json
import os as _os

_FEATURES = _os.path.join(_os.path.dirname(_os.path.dirname(_os.path.dirname(_os.path.abspath(__file__)))),
                          "docs", "features.json")


def _features():
    with open(_FEATURES, encoding="utf-8") as f:
        return _json.load(f)


@scenario("catalogue: every block stands somewhere, every item exists, every creature can be summoned")
def catalogue_everything(t):
    modules = _features()
    # a stage of its own, far from the plots: 160 by 160, grass on stone
    ox, oy, oz = 12000, 200, 12000
    t.cmd(f"forceload add {ox} {oz} {ox + 159} {oz + 159}")
    for zz in range(0, 160, 20):                    # strips small enough for fill's 32 768-block limit
        t.cmd(f"fill {ox} {oy - 2} {oz + zz} {ox + 159} {oy - 2} {oz + zz + 19} minecraft:stone")
        t.cmd(f"fill {ox} {oy - 1} {oz + zz} {ox + 159} {oy - 1} {oz + zz + 19} minecraft:grass_block")
        out = t.cmd(f"fill {ox} {oy} {oz + zz} {ox + 159} {oy + 6} {oz + zz + 19} minecraft:air")
        if "filled" not in out and "No blocks" not in out:
            t.problems.append(f"could not clear the catalogue stage: {out}")
    t.cmd(f"kill @e[type=!minecraft:player,x={ox},y={oy - 10},z={oz},dx=160,dy=40,dz=160]")

    placed = gone = 0
    would_not_stand = []
    cell = 0
    for module, entry in sorted(modules.items()):
        modid = entry["modid"]
        for block, _name in entry["kinds"].get("Blocks", []):
            ident = f"{modid}:{block}"
            x, z = ox + (cell % 80) * 2, oz + (cell // 80) * 2
            cell += 1
            stood = False
            for base in ("minecraft:grass_block", "minecraft:farmland", "minecraft:water", "minecraft:sand", "minecraft:stone",
                         "ceiling", "head"):
                if base == "ceiling":                            # a hanging plant wants something above it
                    t.cmd(f"setblock {x} {oy + 1} {z} minecraft:stone")
                elif base == "head":                             # a vine's body wants the vine's own head above it
                    if not ident.endswith("_plant"):
                        continue
                    t.cmd(f"setblock {x} {oy + 1} {z} {ident[:-len('_plant')]}")
                else:
                    t.cmd(f"setblock {x} {oy - 1} {z} {base}")
                t.cmd(f"setblock {x} {oy} {z} minecraft:air")     # a rerun finds the last run's block here
                out = t.cmd(f"setblock {x} {oy} {z} {ident}")
                if "Changed the block" not in out:
                    break                   # not a placeable block at all: reported below
                t.cmd(f"setblock {x} {oy} {z} {ident}")   # once more with the base in, for blocks that pop on the update
                if "Test passed" in t.cmd(f"execute if block {x} {oy} {z} {ident}"):
                    stood = True
                    break
                # a vine's body with nothing below it becomes the vine's head, as the game's own do
                if ident.endswith("_plant") and "Test passed" in t.cmd(f"execute if block {x} {oy} {z} {ident[:-len('_plant')]}"):
                    stood = True
                    break
            t.cmd(f"setblock {x} {oy + 1} {z} minecraft:air")
            if stood:
                placed += 1
            else:
                gone += 1
                would_not_stand.append(ident)
    t.expect(not would_not_stand,
             f"{gone} block(s) of {placed + gone} stand on none of grass, farmland, water, sand, stone, under a ceiling or under their own head: "
             + ", ".join(would_not_stand[:40]) + (" ..." if len(would_not_stand) > 40 else ""))

    t.cmd(f"setblock {ox} {oy + 8} {oz} minecraft:chest")
    missing_items = []
    have = 0
    for module, entry in sorted(modules.items()):
        modid = entry["modid"]
        for item in entry.get("item_models", []):
            out = t.cmd(f"item replace block {ox} {oy + 8} {oz} container.0 with {modid}:{item}")
            if "Replaced" in out:
                have += 1
            else:
                missing_items.append(f"{modid}:{item}")
    t.expect(not missing_items, f"{len(missing_items)} item(s) of {have + len(missing_items)} cannot be given: "
             + ", ".join(missing_items[:40]) + (" ..." if len(missing_items) > 40 else ""))

    unsummonable = []
    summoned = 0
    px, py, pz = ox + 80, oy + 12, oz + 80
    t.cmd(f"fill {px - 6} {py - 1} {pz - 6} {px + 6} {py - 1} {pz + 6} minecraft:barrier")
    for module, entry in sorted(modules.items()):
        modid = entry["modid"]
        for creature, _name in entry["kinds"].get("Creatures", []):
            ident = f"{modid}:{creature}"
            out = t.cmd(f"summon {ident} {px} {py} {pz}")
            there = "Test passed" in t.cmd(f"execute if entity @e[type={ident},x={px},y={py},z={pz},distance=..12]")
            t.cmd(f"kill @e[type={ident},x={px},y={py},z={pz},distance=..64]")
            if there:
                summoned += 1
            else:
                unsummonable.append(f"{ident} ({out[:60]})")
    t.expect(not unsummonable, f"{len(unsummonable)} creature(s) of {summoned + len(unsummonable)} could not be summoned: "
             + "; ".join(unsummonable[:30]) + (" ..." if len(unsummonable) > 30 else ""))
    t.log.append(("catalogue", f"{placed} blocks stood, {have} items given, {summoned} creatures summoned"))
    print(f"           catalogue: {placed} blocks stood, {have} items given, {summoned} creatures summoned")


# --- the rest of the catalogue: what the game's own commands can reach -------

@scenario("atomics: the enricher, the reaction chamber and the centrifuge each make their product on a stepped-up grid")
def atomics_machines(t):
    rigs = [("elysiumatomics:enricher", "minecraft:raw_copper", "minecraft:copper_ingot"),
            ("elysiumatomics:reaction_chamber", "minecraft:charcoal", "minecraft:coal"),
            ("elysiumatomics:centrifuge", "minecraft:netherrack", "minecraft:quartz")]
    for row, (block, put, made) in enumerate(rigs):
        z = 2 + row * 4
        # every atomics machine is high voltage: a diesel engine's medium grid through a transformer
        t.setblock(2, 0, z, "elysiumindustry:diesel_engine")
        t.hopper_into(2, 0, z, "minecraft:coal_block", 8)
        t.setblock(3, 0, z, "elysiumindustry:gold_wire")
        t.setblock(4, 0, z, "elysiumindustry:transformer")
        t.setblock(5, 0, z, "elysiumindustry:steel_wire")
        t.setblock(6, 0, z, block)
        t.hopper_into(6, 0, z, put, 4)
    t.advance(3000)
    for row, (block, put, made) in enumerate(rigs):
        _slot(t, 6, 0, 2 + row * 4, "items", 1, made, 1,
              f"{block} made nothing of its {put} (share {t.number(6, 0, 2 + row * 4, 'share')})")


@scenario("registries: every dimension loads, every effect applies, every enchantment sits on a sword, every damage type strikes")
def registries(t):
    modules = _features()
    bad = []
    for module, entry in sorted(modules.items()):
        modid = entry["modid"]
        for dim, _n in entry["kinds"].get("Dimensions", []):
            out = t.cmd(f"execute in {modid}:{dim} run forceload add 0 0")
            if "force loaded" not in out and "already" not in out:
                bad.append(f"dimension {modid}:{dim}: {out[:80]}")
            t.cmd(f"execute in {modid}:{dim} run forceload remove 0 0")
    t.expect(not bad, "; ".join(bad))

    # a probe that can be hurt: an armour stand shrugs off most damage types
    t.setblock(2, 0, 2, "minecraft:barrier")
    t.cmd("kill @e[tag=elysium_probe]")
    t.cmd(f"summon minecraft:zombie {t.at(2, 1, 2)} {{NoAI:1b,PersistenceRequired:1b,Tags:[\"elysium_probe\"]}}")
    t.advance(2)
    t.expect("Test passed" in t.cmd("execute if entity @e[tag=elysium_probe]"), "the probe zombie was not summoned")
    bad = []
    for module, entry in sorted(modules.items()):
        modid = entry["modid"]
        for effect, _n in entry["kinds"].get("Effects", []):
            # one probe takes every effect in turn, so whatever the last give left on it
            # goes first: a ward or an antidote still running (cryogenic_ward, antitoxin)
            # makes the effect it guards against refuse to apply, a false failure
            t.cmd("effect clear @e[tag=elysium_probe]")
            out = t.cmd(f"effect give @e[tag=elysium_probe,limit=1] {modid}:{effect} 10 0 true")
            if "Applied" not in out:
                bad.append(f"effect {modid}:{effect}: {out[:80]}")
        for kind, _n in entry["kinds"].get("Damage types", []):
            out = t.cmd(f"damage @e[tag=elysium_probe,limit=1] 0.5 {modid}:{kind}")
            # a type our own handlers intercept answers "invulnerable"; only an unknown type is a failure
            if "Applied" not in out and "invulnerable" not in out:
                bad.append(f"damage type {modid}:{kind}: {out[:80]}")
    t.cmd("effect clear @e[tag=elysium_probe]")
    t.expect(not bad, "; ".join(bad))

    t.chest(4, 0, 2)
    bad = []
    for module, entry in sorted(modules.items()):
        modid = entry["modid"]
        for ench, _n in entry["kinds"].get("Enchantments", []):
            out = t.cmd(f"item replace block {t.at(4, 0, 2)} container.0 with "
                        f"minecraft:diamond_sword[minecraft:enchantments={{levels:{{\"{modid}:{ench}\":1}}}}]")
            if "Replaced" not in out:
                bad.append(f"enchantment {modid}:{ench}: {out[:80]}")
    t.expect(not bad, "; ".join(bad))
    t.cmd("kill @e[tag=elysium_probe]")


@scenario("world: every biome can be found in its dimension, and every structure somewhere")
def world_places(t):
    modules = _features()
    dims = ["minecraft:overworld"] + [f"{e['modid']}:{d}" for e in modules.values() for d, _n in e["kinds"].get("Dimensions", [])]
    # locate searches 6 400 blocks around a point; a rare biome needs more than one point
    origins = [(0, 0), (16000, 0), (-16000, 0), (0, 16000), (0, -16000), (16000, 16000), (-16000, -16000)]
    missing = []
    found = 0
    for module, entry in sorted(modules.items()):
        modid = entry["modid"]
        # a module's biomes live in its own dimensions or the overworld: search those, from
        # several points, before every other world from one
        own = [d for d in dims if d.startswith(modid + ":")] + ["minecraft:overworld"]
        rest = [d for d in dims if d not in own]
        for biome, _n in entry["kinds"].get("Biomes", []):
            hit = False
            for dim, points in [(d, origins) for d in own] + [(d, origins[:1]) for d in rest]:
                for ox_, oz_ in points:
                    out = t.cmd(f"execute in {dim} positioned {ox_} 64 {oz_} run locate biome {modid}:{biome}")
                    if "The nearest" in out:
                        hit = True
                        break
                if hit:
                    break
            if not hit:
                # a rare biome (a hot crag, a hoar hill) may need a far point: a grid of
                # twenty-five, fifty thousand blocks apart, in the module's own world
                # twenty-five, fifty thousand blocks apart, in the module's own worlds and the
                # overworld (the wilds' crags stand in the overworld's own list)
                worlds = list(dict.fromkeys(own + ["minecraft:overworld"]))
                for world in worlds:
                    for ox_ in range(-100000, 100001, 50000):
                        for oz_ in range(-100000, 100001, 50000):
                            out = t.cmd(f"execute in {world} positioned {ox_} 64 {oz_} run locate biome {modid}:{biome}")
                            if "The nearest" in out:
                                hit = True
                                break
                        if hit:
                            break
                    if hit:
                        break
            if hit:
                found += 1
            else:
                missing.append(f"biome {modid}:{biome}")
        for structure, _n in entry["kinds"].get("Structures", []):
            hit = False
            for dim in dims:
                out = t.cmd(f"execute in {dim} run locate structure {modid}:{structure}")
                if "The nearest" in out:
                    hit = True
                    break
            if hit:
                found += 1
            else:
                missing.append(f"structure {modid}:{structure}")
    t.expect(not missing, f"{len(missing)} of {found + len(missing)} places cannot be located in any of our dimensions or the overworld: "
             + ", ".join(missing[:30]) + (" ..." if len(missing) > 30 else ""))
    print(f"           places: {found} biomes and structures located")


# the mechanics, one at a time (docs/MECHANICS.md)
import scenarios_mechanics  # noqa: E402,F401

@scenario("colonies: a ruin marker raises a decayed building with a chest and founds no town (ROADMAP §3)")
def colony_ruin(t):
    # The abandoned half of natural colonies: a marker ticks once and leaves what
    # a building became. A force-loaded chunk with no player near gets no random
    # ticks, so the marker's tick is the operator command that runs the same
    # Ruins.raise; on a stone plot far from spawn the plot afterwards holds the
    # building's stone and wood, a chest, and no new settlement.
    # The building's full height (a level-one tavern of the default style is twenty
    # rows), cleared first so a rerun raises onto nothing.
    x0, z0 = t.ox - 1, t.oz - 1
    x1, z1 = t.ox + t.PLOT - 2, t.oz + t.PLOT - 2
    y0, y1 = t.oy + 1, t.oy + 24
    t.cmd(f"fill {x0} {y0} {z0} {x1} {y1} {z1} minecraft:air")
    before = t.cmd("elysium colony natural")
    raised = t.cmd(f"elysium colony ruin {t.at(11, 0, 11)}")
    t.expect("ruin stands" in raised, f"the ruin was not raised: {raised[:120]}")
    chest = t.cmd(f"fill {x0} {y0 - 4} {z0} {x1} {y1} {z1} minecraft:air replace minecraft:chest")
    t.expect("filled" in chest, f"the ruin left no chest: {chest[:80]}")
    # How much stands is read from the server's own account of the ruin ("a ruin of
    # a minecolonies:builder stands at ...: 82 of 1243 blocks left, 10 rows"), when
    # its log is at hand: the style pack's own hut blocks are in no mineable tag, so
    # a count by tool undercounts a builder's hut by three to one and judged a sound
    # ruin a failure (1 Oct). RuinRules keeps nineteen in twenty of the floor row and
    # never all of the roof, so a fiftieth of the building, and never fewer than
    # twenty blocks, is the least that can stand.
    log = _os.environ.get("ELYSIUM_SERVER_LOG", SERVER_LOG)
    account = None
    if _os.path.isfile(log):
        with open(log, encoding="utf-8", errors="replace") as f:
            for line in f:
                if "a ruin of a" in line and "blocks left" in line:
                    account = line
    counted = _re.search(r"(\d+) of (\d+) blocks left", account) if account else None
    if counted:
        left, total = int(counted.group(1)), int(counted.group(2))
        t.expect(left >= max(20, total // 50),
                 f"the ruin left {left} of {total} blocks; RuinRules keeps the floor and some of every row, so a fiftieth at the least")
    else:
        # Without the log, count by the tool that mines it; the pack's own hut
        # blocks are missed, so only a token of a building is asked for.
        standing = 0
        for tool in ("axe", "pickaxe", "shovel", "hoe"):
            out = t.cmd(f"fill {x0} {y0} {z0} {x1} {y1} {z1} minecraft:air replace #minecraft:mineable/{tool}")
            digits = "".join(ch for ch in out.split("filled")[-1] if ch.isdigit()) if "filled" in out else ""
            standing += int(digits) if digits else 0
        t.expect(standing >= 20, f"the ruin left {standing} tagged block(s) standing (no server log to count by); a building is more than that")
    after = t.cmd("elysium colony natural")
    t.expect(before == after, "raising a ruin changed the list of settlements that founded themselves")


@scenario("harvest: every block that needs a tool has a tool tagged to mine it, and none a player places is unbreakable")
def harvest_audit(t):
    # Whether a block needs a tool is Java and whether it is tagged is data, so
    # only the running game knows both: the space module's pickaxe tag was
    # written three times over and forty-seven blocks dropped nothing (29 Sep).
    answer = t.cmd("elysium audit harvest")
    t.expect("every one of" in answer, f"the harvest audit found blocks a survival player cannot keep: {answer[:400]}")


@scenario("gear: every module's gear is on the vanilla list it enchants by (POLISH.md P12)")
def gear_kind_tags(t):
    # The vanilla kind tags of the pack's gear are written by the library as a
    # data pack from the registry, never as files, so no static pass over the
    # jars can see them. This is the proof they are there: one piece of gear
    # per module that makes gear, put in a chest, and the chest asked whether
    # what it holds carries the list vanilla enchants by.
    for slot, (item, tag) in enumerate((("elysium:aetherium_sword", "#minecraft:swords"),
                                        ("elysium:iron_hammer", "#minecraft:pickaxes"),
                                        ("elysium:elysium_helmet", "#minecraft:head_armor"),
                                        ("elysiumarmory:katana", "#minecraft:swords"),
                                        ("elysiumarmory:boots", "#minecraft:foot_armor"),
                                        ("elysiumarmory:elytra", "#minecraft:enchantable/equippable"),
                                        ("elysiumarsenal:iron_sabre", "#minecraft:swords"),
                                        ("elysiumarsenal:iron_hammer", "#minecraft:pickaxes"),
                                        ("elysiumarsenal:iron_shortbow", "#minecraft:enchantable/bow"),
                                        ("elysiumagronomy:oak_rod", "#minecraft:enchantable/fishing"),
                                        ("elysiumspace:space_suit_chestplate", "#minecraft:chest_armor"))):
        t.chest(2, 0, 2 + slot)
        put = t.cmd(f"item replace block {t.at(2, 0, 2 + slot)} container.0 with {item}")
        t.expect("Replaced" in put, f"{item} could not be put in a chest: {put[:80]}")
        found = t.cmd(f"execute if items block {t.at(2, 0, 2 + slot)} container.0 {tag}")
        t.expect("passed" in found, f"{item} is not on {tag}, so it enchants at no table: {found[:80]}")


@scenario("ores: a mined ore yields the family's canonical material through the unifier (POLISH.md P7)")
def ores_unified_drops(t):
    # The ore tables hand the block to Java, and the unifying loot modifier
    # resolves the family's canonical item at runtime, so a static pass over
    # the jars reads raw uranium and the foundry's gems as things nothing makes.
    # This is the proof they are made: loot the ore where it stands into a
    # chest and ask the chest whether what landed carries the family's tag.
    # What lands is the family's raw form (the agronomy's chunk, which the
    # ore-drop search finds under raw_materials before gems); the foundry's
    # furnace recipes take it on to the gem, and its validator holds that.
    for dx, ore, tag in ((2, "elysiumores:pale_uranium_ore", "#c:raw_materials/uranium"),
                         (5, "elysiumores:cinder_bort_ore", "#c:raw_materials/bort"),
                         (8, "elysiumores:stone_certus_quartz_ore", "#c:raw_materials/certus_quartz"),
                         # the space module's radioactive pair (SPEC_SPACE S6c, 28 Sep)
                         (11, "elysiumspace:neptunium_ore", "#c:raw_materials/neptunium"),
                         (14, "elysiumspace:plutonium_ore", "#c:raw_materials/plutonium")):
        t.setblock(dx, 0, 2, ore)
        t.chest(dx, 0, 4)
        t.cmd(f"loot insert {t.at(dx, 0, 4)} mine {t.at(dx, 0, 2)} minecraft:diamond_pickaxe")
        found = t.cmd(f"execute if items block {t.at(dx, 0, 4)} container.* {tag}")
        t.expect("passed" in found, f"{ore} did not put an item tagged {tag} in the chest: {found[:80]}")
