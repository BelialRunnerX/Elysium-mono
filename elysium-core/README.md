# Elysium

A modular gear progression mod for **Minecraft 1.21.1 / NeoForge 21.1.248**.

Five psionic elements that answer one another, tiered armour and weapons,
socketable runes, finite reforging, and armour ascension — with optional hooks
for Apotheosis, Silent Gear and Legendary Tooltips.

Systems and naming follow the **Sleeping Empire** equipment archive.

---

## This mod needs Elysium Library

Elysium is split in two, so that it can become a series rather than one mod
that grows forever:

| | |
|---|---|
| **[elysium-lib](https://github.com/BelialRunnerX/elysium-lib)** | The engine. Stats, characters, elements, gear, runes, standing — and no content at all. |
| **elysium-core** (this repo) | The content. Six races, nine classes, nine runes, the ore, the gear, the mobs, the recipes. |

**Players install both.** The library is not bundled into this jar — two
Elysium mods each shipping their own copy is exactly the failure the split
exists to prevent.

**Add-on authors** want the library, not this: see
[EXTENDING.md](https://github.com/BelialRunnerX/elysium-lib/blob/main/EXTENDING.md).
Everything this mod registers, another mod can register the same way; there is
nothing in here that reaches into the library through a private door.

---

## Building

**Full step-by-step instructions, including pushing to GitHub, are in
[BUILDING.md](BUILDING.md).** The short version:

### Locally

Requires JDK 21 and an internet connection (Gradle downloads NeoForge and
Minecraft on the first run — expect several minutes and a few GB of cache).

Build the library first — this mod compiles against it:

```bash
cd ../elysium-lib && ./gradlew publishToMavenLocal
cd ../elysium-core && ./gradlew build
```

The finished mod lands at `build/libs/elysium-1.0.0.jar`. Drop it into your
`mods/` folder **alongside `elysiumlib-1.0.0.jar`** and NeoForge 21.1.248 for
Minecraft 1.21.1. Without the library the mods screen will say so plainly.

### Via GitHub Actions

Pushing to GitHub builds the jar automatically — see
`.github/workflows/build.yml`. Open the run from the **Actions** tab and
download **elysium-jar** from the Artifacts section at the bottom of the run
summary. If the build fails, a **build-reports** artifact is attached instead.

### Running it in a dev environment

```bash
./gradlew runClient     # launches Minecraft with the mod loaded
./gradlew runServer     # dedicated server
./gradlew runData       # regenerates data into src/generated/resources
```

Every asset the mod needs is committed under `src/main/resources`, so `build`
produces a complete jar without needing `runData` first.

---

## Gear materials

Every material gets four tool shapes — hammer, broadaxe, scythe, spear — and,
except for the two ore-only Empire alloys, four armour pieces.

| Family | Materials | Elysium tier |
|---|---|---|
| Vanilla | copper, iron, gold, diamond | 0 |
| Vanilla | netherite | 1 |
| The Empire | Voidglass · Aetherium · Neutronium | 0 · 1 · 2 |
| Modded | tin, zinc, lead, silver, nickel, aluminum, platinum, bronze, brass, steel, invar, constantan, electrum, osmium, uranium, titanium, tungsten, cobalt | 0 or 1 |

All of it participates fully: sockets, rune alignment, reforging, ascension and
a required character level. Vanilla materials are a ramp toward the Empire's,
never a replacement for them.

Each material carries an element, and the five are covered before you ever find
Elysium ore — copper is Plasma, iron Kinetic, gold Neural, diamond Dimensional,
netherite Void. So the counter matrix below is something you learn early.

### Modded ores

Gear for the eighteen modded metals is registered in **every** world, whether
or not any mod supplies them, because an item registry that changes shape with
the mod list orphans saved stacks. What varies is only whether it can be made:
recipes use `c:ingots/<name>`, which resolves exactly when some mod fills that
tag, and the creative tab hides the rest.

For an ore that list misses, add it under `[materials] extra_materials` in
`elysium-common.toml` — `name`, `name:tier`, or `name:tier:element` — and
restart. Items are registered during mod loading, so a config change cannot
take effect until the next start.

**`/elysium materials`** prints which materials this pack actually supplies,
which are registered with nothing to make them from, and which of the pack's own
metals Elysium has no gear for. The last list is the one worth reading; the same
thing is logged on every reload.

Adding a material to the shipped table is a Java edit plus
`python3 tools/gen_material_gear.py`, which writes the sprite, model, recipe and
lang entry for each of its eight items. It never overwrites an existing file, so
hand-drawn art always wins and re-running it is safe.

## The counter matrix

The five elements sit in a cycle, and **each one counters the two that precede
it**:

```
Void → Plasma → Neural → Dimensional → Kinetic → Void
```

| Element | Answers |
|---|---|
| Void | Kinetic, Dimensional |
| Plasma | Void, Kinetic |
| Neural | Plasma, Void |
| Dimensional | Neural, Plasma |
| Kinetic | Dimensional, Neural |

Every element beats two and is beaten by two, so none is dead weight.

**The matrix decides whether you have the advantage; the item's tier decides
how much.** A Rare weapon into a favourable matchup is +15%; a Legendary one is
+30%. Wearing the element that answers what is hitting you cuts the damage by
half your armour's tier value, so offence edges out defence on an even trade.

A mixed armour set has no single affinity — the element on the most pieces
wins, and its highest tier sets the strength. Commit to a set or get nothing.

Elysium armour and weapons carry an element. Neutronium is inert: no advantage
in either direction.

---

## Standing

The Empire keeps two numbers on you, 0-100 each, stored on the player and
carried through death. They are not a good axis and a bad axis — they are two
self-reinforcing loops, and you pick which one to ride.

```
kill Unsworn  →  Favor      →  more Unsworn spawn   →  better loot TIER
kill Empire   →  Suspicion  →  more Empire spawn    →  larger loot AMOUNT
```

Each loop feeds itself: the kills that raise a meter also summon more of the
thing you have to kill to raise it further. Nothing stops that but time — both
meters lose a point every two minutes, so a full one takes around two and a
half hours of play to fall back to the notice threshold if you stop feeding it.

**Decay stops at 25.** Below the notice threshold nothing is happening anyway —
no faction mob is dispatched, no ordinary hostile pays out — and bleeding that
range cost a point every two minutes, faster than ordinary fighting refills it.
The first climb to Recognised is therefore a flat one; the pressure to keep
acting starts where the rewards do.

| | Rises from | Sets |
|---|---|---|
| **Favor** | killing Unsworn — a named Raider is +4, any other hostile is +1 one time in three. Socketing or reforging at a workstation is +2. | **the tier of loot.** Raw material at the bottom, neutronium, then runes, then catalysts and weapons at the top. |
| **Suspicion** | killing an Empire mob (+12). Stripping Neutronium ore (+4), Voidglass or Aetherium (+2). Ascending a piece (+8). | **the amount of loot.** One item at a clean record, five when you are being hunted. |

So the loops pay differently and you can feel which one you are on. Pure Favor
is a trickle of good things. Pure Suspicion is a pile of cheap ones. Both at
once is the jackpot — and also four enforcers and four raiders converging on
you at the same time.

Bands are what matter, not the raw number: notice at 25, then 50, then 75. You
are told when a band changes and never in between, and a workstation posts your
full standing when you open it.

## Factions

Every mob is on one of three sides, and ordinary hostiles are not neutral.

| Faction | Who | Killing them |
|---|---|---|
| **Empire** | Imperial Enforcer | +Suspicion |
| **Unsworn** | Unsworn Raider, and every vanilla hostile | +Favor |
| **Neutral** | passive animals, everything else | nothing |

Vanilla hostiles count as Unsworn on purpose. From the Empire's point of view a
creeper is exactly as unsanctioned as a rebel — and if only the mod's own mobs
counted, standing would sit in a corner of the game rather than running through
all of it.

### Imperial Enforcer

Undead in Elysium plate — the Sleeping Empire does not raise new soldiers, it
wakes the ones it already has. Does not burn in daylight, arrives already
hunting you, and how much of the set it wears tells you your band before it
reaches you.

| Band | Arms | Armour |
|---|---|---|
| 1 | Voidcut Blade | helm and chest |
| 2 | Neural Cascade Rifle | helm, chest, legs, boots |
| 3 | Singularity Lance | full set with the Voidweave Aegis |

### Unsworn Raider

Lighter and faster than an enforcer — the Empire's soldiers are armoured, its
enemies are quick. **Every raider carries one of the five elemental weapons at
random**, so the counter matrix comes up in ordinary fights rather than only
when you go looking for it, and the set you chose has to answer a mob you did
not choose.

Higher Favor draws out better-armed raiders: an Elysium Helm at band 2, a
Plasma Carapace at band 3.

---

## Characters

Every player is a race, a class, a level and twelve numbers. You choose the
first two once, on your first join, on a screen you cannot dismiss until you
answer — Origins' approach, and for the same reason: a character with no
identity is a character every other system has to carry a null check for.

You are issued an **Imperial Codex** at the same moment. Right-click it to see
your sheet and spend points; `/elysium sheet` does the same if you lose it.

### The twelve stats

| Stat | What it does |
|---|---|
| **Vitality** | Passive health regeneration, and a little maximum health |
| **Fortitude** | Armour you have without wearing any |
| **Resilience** | Cuts a share off everything that reaches you |
| **Strength** | Your base damage, before a weapon multiplies it |
| **Agility** | Movement speed |
| **Accuracy** | Chance to land a critical hit |
| **Reflexes** | Chance to avoid a blow outright |
| **Retribution** | Sends a share of every blow back at its owner |
| **Intellect** | Psionic potency: elemental advantage and rune strength |
| **Willpower** | A shield that rebuilds itself |
| **Luck** | Chance of more out of everything you kill |
| **Presence** | How fast the Empire notices, and how well a reforge rolls |

Five of these — Strength, Reflexes, Intellect, Willpower, Presence — are the
Sleeping Empire character sheet's own, kept under its own names.

**Damage works as `base x multiplier`.** Strength is your base; the weapon
decides how much of it lands. An Elysium blade multiplies it by 1.6, a hammer
by 1.6, a broadaxe 1.5, a scythe 1.3, a spear 1.25, an ordinary item 1.0, and a
bare fist halves it. Vanilla weapon damage is added on top, untouched.

### Why no stat has a ceiling

Reforging and ascension are meant to climb forever, which means stats do, which
means a percentage stat has to accept any input without ever reaching 100%.
Every proportional stat reads through

```
value / (value + K)
```

which rises steeply, then slows, and approaches 1.0 without arriving. Doubling
Resilience always helps and never finishes the job. Flat stats — Strength,
Fortitude, Vitality — grow linearly and are allowed to.

### Reflection, and how shares stack

Three things send damage back: the **Retribution** stat (approaching 80%), an
Imperial's **Sanctioned Answer** (approaching 100%), and a Warden's **Bulwark**
below half health. They do not add up — they combine the way overlapping
shields do:

```
total = 1 - (1 - a)(1 - b)
```

Adding and clamping is the obvious approach and the wrong one: two 60% sources
would hit the ceiling and every point past that would buy nothing. Combining
what each one *lets through* means 60% and 60% is 84%, a third 60% makes it
94%, and the total climbs toward 100% without ever arriving.

That boundary matters. Reflecting exactly all of a blow is the point where an
attacker takes precisely what they dealt; past it, touching an Imperial would
kill you outright whatever you hit them with. The combination is bounded below
1.0 by construction, so there is no clamp to forget.

| Imperial level | Racial share |
|---|---|
| 10 | 9% |
| 50 | 33% |
| 100 | 50% |
| 200 | 67% |
| 400 | 80% |
| 900 | 90% |

### Levels

A separate track from vanilla experience, so spending green levels on an anvil
never takes your chestplate off. Experience comes from Elysium activity: 14 for
a named faction combatant, 4 for any other hostile, 6 for a vein of Elysium
ore. Each level grants your race's and class's growth automatically plus **2
free points** to assign. `/elysium respec` hands every assigned point back.

There is no maximum level, because armour requires a level and ascension raises
armour tiers forever.

### Races

Six, five of them from the archive's *Known Species*. Every race starts with
the same number of points spread differently, grows 3 points a level in fixed
stats, and has one passive that no amount of points can buy.

| Race | From | Shape | Passive |
|---|---|---|---|
| **Imperial** | humanoid majority | balanced, no weak stat | **Sanctioned Answer** — reflects a share of every blow, climbing with level toward 100% and never arriving |
| **Druun** | Druun Ascendancy, reptilian | huge Strength and Fortitude, no Intellect | **Cold Blood** — up to +60% damage as health falls |
| **Veylari** | Veylari Concord, avian | Accuracy and Intellect, barely armoured | **Lightfeather** — ignores the first ten blocks of a fall and two thirds of the rest |
| **Korrath** | Korrath Dominion, insectoid | Agility and Reflexes, no Willpower | **Molt** — regeneration triples after five seconds untouched |
| **Lumari** | Lumari Collective, energy | enormous Willpower and Intellect, almost no body | **Photonic** — double shield; a third less fire and blast damage, 15% more of everything else |
| **Unsworn** | outside the Code | huge Luck, zero Presence | **Uncounted** — Suspicion sheds twice as fast, Favor comes half as easily |

### Classes

Nine jobs, drawn from what the archive says the Empire actually employs people
to do. A class gives 2 points a level and one passive, against a race's 3 — what you
were born as should outweigh the job you took. Both race and class are chosen
once and kept; a class change belongs at a workstation with a cost, and until
that exists there is no free way to swap.

| Class | Grows | Passive |
|---|---|---|
| **Medicae** | Vitality, Presence | **Triage Field** — heals every player within 8 blocks |
| **Factor** | Luck, Presence | **Profiteer** — +25% chance of a second drop |
| **Artificer** | Intellect, Presence | **Field Repair** — gear wears a third slower, reforges roll 50% higher |
| **Enforcer** | Strength, Fortitude | **Sanctioned Force** — +25% against the Unsworn, 40% less Suspicion |
| **Psion** | Intellect, Willpower | **Resonance** — psionic potency up 50% |
| **Voidrunner** | Agility, Reflexes | **Slipstream** — halves fall damage |
| **Reclaimer** | Fortitude, Luck | **Prospector** — one in four chance of a second ingot from any vein |
| **Warden** | Resilience, Retribution | **Bulwark** — below half health your reflected share is laid over itself |
| **Marksman** | Accuracy, Agility | **Called Shot** — criticals deal +125% instead of +50% |

### Armour grants stats, and asks for a level

Every piece gives `1 + tier` points in each of the two stats its element
governs, plus Fortitude for being armour, plus whatever its reforge rolled —
armour bonus becomes Fortitude, health becomes Vitality, speed becomes Agility.

It also requires **5 character levels per tier**. A piece you cannot meet still
equips and still takes damage; it simply grants nothing until you can. The
tooltip says the requirement outright rather than leaving you to guess.

### Everything scales forever

| Was capped at | Now |
|---|---|
| Tier 5 (Unique) | unbounded — past Sovereign a piece reads Ascendant 1, 2, 3… |
| 3 rune sockets | one per two tiers, no ceiling |
| 3 reforges, ever | 3 per tier — **ascension refills them** |
| Elemental advantage 40% | curves toward +100% and never arrives |
| Character level | unbounded |

That refill is the whole engine: reforge three times, ascend to bank the
results at a higher tier, reforge three more times against better numbers. The
cost is what limits it — each ascension needs a second piece at the same tier,
so the price doubles every step.

## Content

### Armour

| Item | Element | Tier |
|---|---|---|
| Elysium Helm | Void | Rare |
| Plasma Carapace | Plasma | Epic |
| Voidweave Aegis | Void | Legendary |
| Neural Leggings | Neural | Rare |
| Dimensional Boots | Dimensional | Epic |
| Emperor's Crown | Void | Unique, fire resistant |
| Neutronium Helmet / Chestplate / Leggings / Boots | — | Legendary |

Each elemental piece also carries a **psionic affix** scaled by tier: Void
Warding (knockback resistance), Plasma Surge and Kinetic Force (attack damage),
Neural Overclock (attack speed), Dimensional Drift (movement speed).

### Weapons

| Weapon | Element | Tier | Character |
|---|---|---|---|
| Voidcut Blade | Void | Rare | baseline |
| Plasma Brand | Plasma | Rare | baseline |
| Neural Lash | Neural | Rare | lighter, faster |
| Rift Edge | Dimensional | Rare | baseline |
| Kinetic Maul | Kinetic | Rare | slow, heavy |
| **Singularity Lance** | Dimensional | Legendary | one enormous blow |
| **Neural Cascade Rifle** | Neural | Epic | half the weight, twice the rate |

Crafting a weapon consumes a rune of its element — an affinity is a
commitment, and it competes with socketing that rune into armour.

### Area tools

Four shapes, one variant per material. Each is a genuine weapon as well as a
tool — high damage traded against a slow swing — and each swings into the
counter matrix the way a blade does.

| Tool | Ability | Damage | Speed |
|---|---|---|---|
| Hammer | breaks a 3×3 in the plane of the face you struck | 7 | −3.2 |
| Broadaxe | fells the entire tree | 6 | −3.0 |
| Scythe | harvests a 3×3 of crops | 5 | −2.6 |
| Spear | digs a 3×3 | 4 | −2.2 |

| Material | Element | Tier | Mining | Damage bonus |
|---|---|---|---|---|
| Voidglass | Void | Rare | Diamond | — |
| Aetherium | Dimensional | Epic | Diamond | +1 |
| Neutronium | Kinetic | Legendary | Netherite | +2 |

Recipes are deliberately plain — the material across the top, sticks down the
middle — so the only decision is which metal you can afford:

```
Hammer      Broadaxe    Scythe      Spear
m m m       m m m       m m m       . . m
. s .       m s .       . . s       . s .
. s .       . s .       . . s       . s .
```

Area breaking costs one point of durability per extra block, and a tool will
refuse its last point rather than snap mid-swing. Unbreakable blocks are
skipped.

### Rune alignment

Every piece of Elysium gear — armour, weapon or tool — resonates with one
element. A rune socketed into gear of its own element is **aligned**, and
gives **1.75×** what it would give anywhere else. The tooltip marks it.

Nothing is locked out: a Voidward rune in a Neutronium hammer still gives
exactly what it always gave. Alignment is a reward, never a gate. Aligned
elemental runes also run their worn effect one amplifier higher.

Utility runes have no element, so they are never aligned and never penalised —
they are the flat option you take when you cannot get a match. And because
Plasma and Neural have no material of their own, no single metal lets you align
every rune you own.

### Runes

Nine runes. Socket capacity scales with tier: 1 slot at Common/Uncommon, 2 at
Rare/Epic, 3 at Legendary/Unique. A piece cannot take the same rune twice.
Armour, weapons and tools all take runes at the Rune Socket Table; reforging
and ascension remain armour-only.

**Elemental** — an attribute bonus plus a situational effect while worn:

| Rune | Bonus | Worn effect |
|---|---|---|
| Voidward | +2 armour toughness | Resistance below 40% health |
| Plasmaforge | +1.5 attack damage | Strength above 70% health |
| Neuralspike | +15% attack speed | Haste |
| Dimensionalshift | +8% movement speed | Slow Falling while falling |
| Kineticsurge | +0.1 knockback resistance | Jump Boost |

**Utility** — things a raw attribute cannot express:

| Rune | Effect |
|---|---|
| Stabilizer | Steady health regeneration that costs no hunger |
| Reflex | +5% chance to avoid a blow outright |
| Barrier | A shield that rebuilds while it is not being spent |
| Plasma Core | −12% damage from fire and blasts |

Utility runes stack across pieces and resolve once for the whole set, so four
Stabilizers heal four times as fast — not sixteen.

### Reforging

Every piece has **three reforge charges**, and that is all it will ever have.
Each reforge rerolls bonus armour, health and movement speed; quality scales
with tier and with the catalyst used. The tooltip shows what is left.

### Workstations

Three blocks, one shared menu. Put a piece in the left slot, then:

- **rune in the right slot** → socket it
- **matching second piece in the middle slot** → ascend one tier, fully repaired
- **Reforge Catalyst in the middle slot** → spend a charge and reroll

Socketed runes, reforge rolls and ascension all live in a data component, so
they survive every one of these operations.

### Getting hold of it all

Everything the mod registers is reachable in survival except the two spawn
eggs, which are creative tools by definition. `validate.py` proves it: it
starts from what the world itself gives you and closes over the recipe graph
until nothing new appears, then fails on anything left unreached. That check
exists because five armour pieces had quietly been creative-only — registered,
rendered, socketable and reforgeable, with no way to obtain one.

| Source | What it gives |
|---|---|
| Worldgen | Neutronium, Aetherium and Voidglass ore, in every overworld biome |
| Crafting | every rune, all four workstations, all 12 tools, all armour except the Crown |
| Mob loot tables | Neutronium from Enforcers, Voidglass from Raiders |
| Standing loot | ingots, runes, reforge catalysts, elemental weapons, the Emperor's Crown |

Two paths deliberately cross, so neither meter can dead-end the other:

- **Neutronium ore needs a netherite pickaxe**, but Neutronium ingots also come
  off Imperial Enforcers and out of the Favor loot table from Recognised
  upward. You can mine your way to it or earn your way to it.
- **Suspicion bootstraps from mining** Voidglass or Aetherium, which need only
  an iron pickaxe — so you never need an Empire mob to anger the Empire.

The **Emperor's Crown** has no recipe on purpose. It is Elysomnion's own: it
drops from a named Imperial Enforcer, at 4%, and only once Suspicion reaches
**Hunted**. The only way to get it is to be worth hunting.

Elemental armour and weapons each consume the rune of their element, so an
affinity always costs you the rune you could have socketed instead.

### Ores

| Ore | Y range | Drops |
|---|---|---|
| Aetherium Ore | -32 to 64 | Aetherium Ingot |
| Voidglass Ore | -48 to 32 | Voidglass Ingot |
| Neutronium Ore | -64 to 16 | Neutronium Ingot (needs netherite) |

All three generate in overworld biomes and drop themselves under Silk Touch.

---

## Integrations

All optional. The mod never references these mods' classes at compile time, so
it loads and runs identically whether they are present or not. Each is declared
`optional` with `ordering="AFTER"` in `neoforge.mods.toml`, and every touchpoint
goes through `ModList.get().isLoaded(...)`, which takes a string.

Be clear about what "integration" means here, because the three are not equal:

| Mod | 1.21.1 build | What Elysium actually does |
|---|---|---|
| **Apotheosis** | [8.5.4](https://modrinth.com/mod/apotheosis/version/1.21.1-8.5.4) | **Coexists. No integration code.** Elysium's affixes are its own — the original extended Apotheosis's `Affix` class, which made the mod uncompilable without it, so that was replaced with a standalone implementation. Apotheosis can roll its own affixes onto Elysium gear on its usual rarity rules; the two systems add separate attribute modifiers and are unaware of each other. |
| **Silent Gear** | [check current](https://www.curseforge.com/minecraft/mc-mods/silent-gear) | **Real, but unverified.** Neutronium and Aetherium ship as materials under `data/elysium/silentgear_materials/`, which is the datapack route and needs no code dependency. |
| **Legendary Tooltips** | [1.5.5](https://www.curseforge.com/minecraft/mc-mods/legendary-tooltips/files/6400660) | **Coexists, and works by default.** Elysium items carry ordinary vanilla rarities, so Legendary Tooltips' rarity-based frames apply with no setup. The tier and clearance lines render inside the frame. Per-item borders go in its own config. |

### The Silent Gear caveat

This is the only part of the mod aimed at a moving target I cannot test against.
The material format **changed in 1.21**, and the version this project inherited
was written for 1.16–1.20: a flat `stats` block, `colors`/`tier`/`categories` at
the root, and the whole thing filed under `data/silentgear/materials/`. Silent
Gear 1.21 reads `data/<namespace>/silentgear_materials/` — so those files were
never opened, and the integration had been quietly doing nothing.

They have been rewritten against the wiki's own
[1.21 example](https://github.com/SilentChaos512/Silent-Gear/wiki): `properties`
keyed by part type, `mining_speed` renamed to `harvest_speed`, armour split per
slot, mining tier as an object, `armor_toughness` dropped because it is no
longer in the schema.

That is a careful transcription of documentation, not a tested result. If Silent
Gear rejects a field, it says which one in the log — send me that line. Nothing
else in the mod depends on these two files.

### Adding a deeper integration

To call one of these mods' APIs directly, add it as a `compileOnly` dependency
and keep the calling code in a class that is only loaded from behind a
`ModList.get().isLoaded(...)` check — see `ElysiumAffixes.onApotheosisPresent()`
for where that branch goes.

---

## Art

Every item, block and material ships with purpose-drawn art in the
**Voidforged** direction — dark gothic plate with sci-fi emissive cores. That
is a standing objective, not a one-off: see `TEXTURES.md` for the style rules,
the non-negotiable resolutions, and how to add art for a new item.

```bash
cd tools/textures && python3 build.py && python3 preview.py
```

---

## Layout

```
src/main/java/com/elysium/
  Elysium.java            registries, creative tab, mod entry point
  affix/                  psionic + rune affix table
  block/                  ores, storage block, workstations, block entity
  client/                 screen registration (client only)
  data/                   data generators
  element/                the five elements and the counter matrix
  entity/                 factions, the Enforcer and the Raider
  event/                  combat, standing, loot and tick handlers
  item/                   armour, weapons, runes, catalyst, gear data component
  menu/ screen/           workstation menu and its screen
  silentgear/ tooltip/    soft integration hooks
  standing/               Favor and Suspicion
src/main/resources/
  assets/elysium/         models, blockstates, textures, lang
  data/elysium/           recipes, loot tables, worldgen, biome modifiers
  data/minecraft/tags/    tool tags
  data/silentgear/        material definitions
tools/textures/           the texture generator (not shipped in the jar)
validate.py               cross-checks the registry against the resources
```

---

See `FIXES.md` for what changed in the 1.21.1 port, and `TEXTURES.md` for the
art objective.
