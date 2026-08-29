# Elysium Mobs

**NeoForge 21.1.248 · Minecraft 1.21.1 · Java 21 · requires [elysium-lib](https://github.com/BelialRunnerX/elysium-lib)**

Six families of custom creature — three Unsworn, three Imperial — in five
sub-variants each. Thirty in all, plus a boss for each side. Every one scales on
spawn to the players near it and to how far up the Favor and Suspicion meters
they are.

No vanilla mobs are reused. These are their own entity types, their own models,
their own AI and their own stats.

---

## The two sides fight differently

**The Unsworn** are people who were not counted. Quick, fragile, numerous.

| Family | What it is |
|---|---|
| **Scavenger** | Fast and frail. One is barely a fight; that is the point. |
| **Reaver** | Slow, heavy, hard to move. Wants you cornered. |
| **Whisper** | Low health, high damage. Dangerous exactly until it is noticed. |

**The Empire** fields equipment. Slower, better armoured, and they work as a unit.

| Family | What it is |
|---|---|
| **Sanctioned Drone** | Light and expendable — a thing the Empire spends. |
| **Lictor** | The most armoured thing here, and the slowest. Makes a room expensive to cross. |
| **Auspex Adept** | Weak alone, and the reason the others are worse. Kill it first. |

You should be able to tell which side a room belongs to from the doorway.

### The two bosses

**The Choir of the Uncounted** (Unsworn) — three phases, each one louder. It
fights by refusing to be a single target: every phase it summons more Unsworn,
so ignoring the room to focus the boss means fighting the room by phase three.

**The Praetor of the Sanctioned Answer** (Imperial) — two phases. First a wall:
armoured, shielded, slow to hurt. Then the shield goes and it returns a share of
everything it takes, which is the Imperial racial passive stated as a fight.

---

## Sub-variants

Five per family. They differ in a texture, three numbers and **one ability** —
and the ability is what makes it a different fight rather than a different
colour.

Unsworn variants are named for what happened to them; Imperial ones for the
office they hold.

### The balance budget

Every variant's three multipliers — health, damage, speed — add up to exactly
**3.00**. One of them is 1/1/1 in each family; the other four spend the same
total differently. Tougher means slower; harder-hitting means frailer.

That is deliberately crude, because a crude rule that holds beats a subtle one
that decays. Five sub-variants only stay a *choice* if none is simply better,
and the way that breaks is one variant getting nudged up during tuning and
nobody noticing. `validate.py` adds up all thirty after every edit.

Abilities are **not** in the budget and cannot be — there is no exchange rate
between "+0.2 health" and "poisons on hit". They are balanced by being
different. The numbers carry the part that can be checked; the abilities carry
the part that cannot.

---

## Scaling

On spawn, a creature is built for the **average character level of players
within 64 blocks**, then adjusted by that group's standing band.

The average rather than the highest is a choice with a real cost: a veteran can
soften a fight by bringing low-level friends. It buys the thing that matters
more — a newcomer joining a high-level world is not immediately facing mobs
scaled entirely past them, which is what "highest nearby" produces and what
makes a mod unplayable to the second person who installs it.

Health grows on a flattening curve (√), damage near-linearly. They are not the
same kind of number: health decides how *long* a fight lasts, and proportionally
longer at every level is tedious rather than harder; damage decides whether you
can afford a mistake, and has to keep pace with the armour a levelling player
accumulates.

The level is fixed at spawn and stored, not recomputed — otherwise a fight would
get harder because a high-level player walked past.

---

## Integration

Killing these moves Favor and Suspicion through the library's faction rules.
Standing dispatches send Lictors and Reavers after you. All of it is registered,
none of it is special-cased.

**Elysium Dungeons fills its rooms with these, and neither mod imports the
other.** Mobs registers into the library's bestiary; dungeons asks the bestiary
for a creature of a role. Install both and dungeons are populated; install
either alone and both still work — a dungeon with no mob mod gets a plain
fallback boss and empty filler rooms rather than a crash.

That is checked, not assumed: `validate.py` fails if anything under
`com.elysium.mobs` names another content mod, and the build harness compiles
this mod with dungeons deliberately off the classpath.

---

## Eight entity types for thirty creatures

The sub-variant is synced entity data, not a separate registration. Thirty
registered types would be thirty models, thirty renderers and thirty spawn eggs
for creatures that differ in a texture and one ability — vanilla makes the same
choice for cats, villagers and axolotls.

Spawn eggs are per family and roll a random sub-variant, the same thing a
dungeon or a dispatch does.

---

## Adding your own

A sub-variant is a registration:

```java
public static final MobVariant RUSTEATER =
        MobVariant.builder(id("rusteater"), ElysiumFamilies.SCAVENGER_ID)
                .stats(1.10F, 1.05F, 0.85F)          // keep the total at 3.00
                .colour(ChatFormatting.GOLD)
                .ability(MobAbilities.knitting(0.025F))
                .register();
```

The texture path is derived from the id, so it cannot mismatch. Register from
your mod's constructor.

For a whole new creature, register an `ElysiumBestiary.Entry` and it becomes
available to dungeons and anything else that asks — see the library's
`EXTENDING.md`.

---

## Building

```bash
cd ../elysium-lib && ./gradlew publishToMavenLocal
cd ../elysium-mobs && ./gradlew build
python3 validate.py
python3 tools/gen_mobs.py     # after changing the model table
```

`tools/gen_mobs.py` generates the models, renderers and every texture sheet from
**one box table**. A model says "this box's faces are at (16, 8) on the sheet";
the texture has to have them there. Hand-written, those two drift the first time
a box moves, and the symptom is a mob wearing the wrong part of its own skin —
which compiles, loads, and looks like nonsense. Generating both from one table
makes that impossible, and a packer assigns the UVs so two boxes can never
overlap.

**Rendering is the one thing the harness cannot check.** Everything else here is
compiled and validated; models and textures are correct by construction but have
never been drawn on a screen, so expect visual fixes on first launch.

---

## What is verified, and what is not

**Verified here:** all five projects compile; the thirty variants exist with
five per family and one baseline each; every one spends the same 3.00 budget;
every entity type has a model, a renderer, an attributes registration and a
spawn placement; every variant has a texture and a lang key; both factions have
families and a boss; and nothing under `com.elysium.mobs` names another content
mod. Each of those invariants was deliberately broken to confirm the check
catches it.

**Not verified:** anything that needs the game running — how the models look,
whether the walk animation reads, whether the fights are actually balanced. The
stub harness type-checks; it does not render and it does not play.
