# Elysium Dungeons

**NeoForge 21.1.248 · Minecraft 1.21.1 · Java 21 · requires [elysium-lib](https://github.com/BelialRunnerX/elysium-lib)**

A portal to a dungeon that is never the same twice.

Build a frame, strike it with a Rift Key, and step through. Inside is a dungeon
assembled from tiles — filler rooms, one or more loot rooms and a boss room, all
of them sealed boxes joined only by doorways. Walk back out through the return
rift and, once the last player has left, **that dungeon is finished forever**.
Step through the same portal again and a completely new one is built.

---

## Playing it

| | |
|---|---|
| **Rift Frame** | 4 obsidian, 4 deepslate bricks, 1 ender pearl → 4 blocks |
| **Rift Key** | ender eye, gold ingot, 2 iron ingots. 64 uses. |

Build a rectangle of Rift Frames — the same shape as a Nether portal, minimum
2×3 inside — and right-click it with the key.

The key is damaged, not consumed, so a run costs durability rather than a
crafting trip.

---

## How "a new dungeon every time" actually works

Nothing is ever deleted. Each dungeon is built at its own cell of a spiral grid
in a void dimension, 1024 blocks from its neighbours, from a seed that has never
been used before. The old dungeon is left behind in chunks nothing will load
again.

That is the design, and the alternatives are all worse:

- **Deleting region files** at runtime is not safe, and the chunk system will
  hand out stale copies of what you deleted.
- **Clearing and rebuilding in place** makes every entry wait on hundreds of
  thousands of block writes, and destroys anything a player left on the floor.
- **Building somewhere new** is a plain write into empty chunks. It cannot
  corrupt anything, and the old dungeon stays intact for as long as anyone is
  standing in it.

The abandoned dungeons cost disk and nothing else.

### The seed

A pure function of the world seed, a counter that never repeats, and the
portal's position — and deliberately **not** of the clock. Two dungeons are
never the same because the counter differs; one dungeon is always itself
because nothing about it is timing-dependent, so a layout can be reproduced
from its seed and a bug report is worth filing.

### Who shares a dungeon

One live dungeon per portal. A party stepping through together lands in the same
place. When the last player leaves — walking out, dying, logging off, or being
teleported away by anything else — the dungeon retires and the next entry
through that portal builds a new one.

Joining a dungeon that is already standing does **not** rebuild it. That would
refill the chests and respawn the boss under the people already inside.

---

## How a dungeon is shaped

A grid of cells, grown outward from the entrance. Which cell to grow from is
weighted — mostly the newest, sometimes an older one — because always-newest
gives one long snake with no decisions and always-random gives a fat blob where
the boss is four steps from the door. The weighting is what produces a spine
with branches hanging off it.

Then, in this order:

1. **Entrance** at the start.
2. **Boss** at the cell furthest away, measured in doorways walked rather than
   straight-line distance.
3. **Loot** at dead ends, deepest first.
4. **Filler** everywhere else.

The boss is placed before the loot so it always gets the furthest cell —
otherwise the longest walk in the dungeon could end in a chest.

```
seed 106954  rooms 12  walk to boss 7

     [E]-[.]-[.]
     |   |   |
     [.]-[.]-[.]
             |
             [.]
             |
     [$]-[$]-[.]
         |   |
         [B]-[.]
```

---

## Adding your own rooms

A room is a registered object that fills a box. The shell — floor, walls,
ceiling and doorways — is built for you, so a room cannot accidentally leave a
hole into the void or brick up a door.

```java
public static final DungeonRoom OSSUARY = DungeonRoom.builder(id("ossuary"))
        .kind(DungeonLayout.Kind.FILLER)
        .weight(3)
        .build((context, random) -> {
            for (int i = 0; i < 20; i++) {
                BlockPos at = context.randomFloorPos(random);
                context.setClear(at.getX(), 0, at.getZ(),
                        Blocks.BONE_BLOCK.defaultBlockState());
            }
        });
```

Register it from your mod's constructor. Everything is in **room coordinates**,
so (0,0,0) is the inside corner of the floor — a room written against world
coordinates would work in the first dungeon and be wrong in every one after it.

Use `setClear` rather than `set` for anything free-standing: it refuses to place
in a doorway, and in an enclosed dungeon a pillar in front of the only exit is a
run that cannot be finished.

Weights are relative within a kind. Registering a `BOSS` room adds to the pool
the boss is drawn from; there is no need to remove the one shipped here.

---

## What it needs, and what it does not

**elysium-lib is required.** Loot rooms pay out through the library's reward
providers, so what you find in a chest is whatever any installed mod has
registered.

**elysium-core is optional.** With it installed, dungeon chests hold Elysium
gear. Without it, they hold whatever else is registered — and if nothing is,
they are empty, which is a poorer dungeon rather than a broken one.

---

## Building

```bash
cd ../elysium-lib && ./gradlew publishToMavenLocal
cd ../elysium-dungeons && ./gradlew build
python3 validate.py
```

`validate.py` checks the things that would strand a player in a void dimension
— the dimension files exist and their id matches the Java, every block has a
model and a blockstate variant, both items have recipes, the frame has a loot
table and a tool tag, and every layout kind has at least one room registered.

The layout generator itself is executed rather than only compiled. See
`tools/LayoutHarness.java`: it runs 400 dungeons and asserts that every room is
reachable, that no door is one-way, that the boss is at the deepest point, that
different seeds give different dungeons, and that the same seed gives the same
one.
