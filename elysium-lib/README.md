# Elysium Library

**NeoForge 21.1.248 · Minecraft 1.21.1 · Java 21**

The engine behind Elysium, with no content of its own.

Everything Elysium mods share and nothing anyone can hold: the character and
stat systems, the psionic elements and their counter matrix, the gear and rune
framework, faction standing, and the event handlers that make all of it happen
during play.

It registers **no items, no blocks, no mobs and no recipes.** Installed on its
own it is inert but not broken — characters still level, stats still apply,
standing still accrues. There is simply nothing to mine and nobody to fight
about it.

If you want to play, install [elysium-core](https://github.com/BelialRunnerX/elysium-core)
alongside it. If you want to build, read **[EXTENDING.md](EXTENDING.md)**.

---

## What it provides

| System | What the library owns |
|---|---|
| **Stats** | Twelve canonical stats, the diminishing-returns curve, summing across race/class/level/points/gear, persistence, the character sheet, point spending and respec |
| **Character** | Levels, experience, unspent points, first-join flow, the networking that keeps a client's sheet honest |
| **Elements** | Five psionic elements and a closed advantage ring; matchup resolution in combat; psionic affixes on gear |
| **Gear** | Sockets, alignment, tiering, reforging, ascension, the durability and tooltip framework |
| **Materials** | What gear is made of: element, tier, tool tier, armour numbers, and a tag-based ingredient whose availability is a runtime question rather than a load-time one |
| **Runes** | Definitions carrying an affix, an effect, or both; per-copy counting; alignment bonuses |
| **Standing** | Favor and Suspicion, bands, decay, spawn chance and cap, the dispatch loop, the reward split |
| **Passives** | Seventeen hooks a race or class answers, and the rules for combining their answers |
| **Bestiary** | A registry of creatures by faction and role, so a mod that has mobs and a mod that needs mobs never have to know about each other |
| **Scaling** | One shared rule for what level a spawn is built for, so two mods never disagree about difficulty |
| **Interface** | A drawing toolkit and a fixed palette, the character screen, and the in-world HUD — all shapes, no textures, so nothing is resampled at any GUI scale |

## What it deliberately does not

Any decision that belongs to a game rather than an engine. The library ships no
races, no classes, no runes, no items, no mobs and no integrations with other
mods — each of those is a registration a content mod makes.

There is one sentence to understand before extending it:

> **The library owns the number; you own what it does.**

A stat you register is summed, saved, displayed and spendable for free — and
does nothing until your own event handler reads it and acts. Races, classes and
runes are the exception: their behaviour travels with them as an object, so
those are complete once registered.

---

## Building

```bash
./gradlew build                  # the jar, in build/libs
./gradlew publish                # into ./repo, for a sibling checkout
./gradlew publishToMavenLocal    # into ~/.m2
python3 validate.py              # the library's own invariants
```

`validate.py` runs in seconds and checks the thing that matters most: that
nothing in `com.elysium.lib` refers to a mod built on it. Also that the element
ring is closed, the stats are the twelve they claim to be, every key the engine
emits has an entry in the lang file, and that `ElysiumPalette.java` still
matches the generator that produced it.

CI runs the same checks on every push and uploads the jar.

---

## The interface

Every Elysium screen is drawn from `ElysiumUI` — filled rectangles on integer
pixel boundaries and nothing else. No GUI textures at all, because a texture is
authored at one size and then scaled by the player's GUI scale setting: at
scale 3, which is what anyone on a 4K monitor is using, a one-pixel border has
been tripled by a nearest filter. That softness is most of what makes a modded
interface look unlike the game's own.

The colours and spacing all live in `ElysiumPalette`, which is **generated** —
do not edit it:

```bash
python3 ui/palette.py     # regenerate ElysiumPalette.java
python3 ui/screens.py     # render every screen to ui/preview/, both widths
```

`ui/mock.py` reimplements the toolkit against Pillow and imports the same
`palette.py`, so the previews are drawn from the numbers the game uses rather
than from a copy of them. That matters because a GUI is the one part of this
project a compiler cannot check — it will happily compile an unreadable screen,
and a preview drawn from different numbers than the game is worse than no
preview at all, because it is confidently wrong.

Both scripts fail rather than warn. `palette.py` refuses to generate if any
text colour falls below its contrast floor against the surface it sits on, and
`screens.py` exits non-zero if any panel does not fit inside 320x240 — the
shape a 960x540 window has at GUI scale 3, and the one that caught the reforge
table hanging four pixels off the bottom of a screen it had been measured for.

---

## Versioning

The id is the persistence key for every registered thing, so ids are permanent
once anyone has played them. Adding an extension point is a minor version;
changing the meaning of an existing hook is a major one. A content mod declares
`versionRange="[1.0.0,)"` and keeps working across the former.
