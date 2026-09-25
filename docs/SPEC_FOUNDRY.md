# The fourth age — the material chain, and what it costs

This is the design for the manifest's `(new) gregtech equivalent` row, filed to
`elysium-foundry`. It has no jar behind it. `CONVERSION.md` §2 says what the
four ages are; `SPEC_TECH.md` is the physics of the first three. This file is
the fourth age alone: what the chain is, why it is shaped the way it is, and
the price of every material in it — because a chain nobody has priced is a
chain nobody can check, and "the pack leaks" is the bug GregTech-likes ship.

Read `elysium-foundry/src/main/java/com/elysium/foundry/rules/FoundryRules.java`
next. Everything below is stated there as arithmetic and executed by
`tools/FoundryHarness.java`; this file says why.

---

## 0. Rulings

- **No new physics.** Age I is rotation, Age II a divided grid, Age III a
  buffered one. The fourth age invents no mechanism. What it adds is the thing
  the three were built to pay for: a chain deep enough that a factory is a
  supply problem rather than a crafting problem.
- **The ore ladder ends at Age III.** One ore is one unit of metal in Age I,
  two in Age II, three in Age III (`AtomicRules.ENRICHER_MULTIPLIER`). The
  fourth age never makes it four. What it adds at the rock face is
  *recovery*: the companion metal every earlier age threw away (§3).
- **Two machines, not twelve.** The alloy smelter holds two metals at once,
  which nothing below it can; the refinery gets the companion back, which
  nothing below it does. Every other step — crushing, rolling, arc heat — is
  Age II's and stays Age II's. A GregTech-like with a wall of machines is a
  GregTech-like nobody finishes.
- **One unit, integer arithmetic.** An ingot is nine (`UNIT`), a nugget one, so
  every recipe in the chain is checked in whole numbers. A form that does not
  divide nine is not added.
- **Every alloy is two things.** The smelter has two slots; a chain a player
  can hold in their head is written in pairs. Three-input alloys are where a
  chain becomes a lookup.
- **The chain is one table.** `FoundryRules.MATERIALS`, read by the generator,
  the registries and the harness. The Cordon's metals (§4) join it from
  `elysium-ores`' roster rather than being typed a second time here.
- **Uncapped where it is a number a player grows.** Nothing in this age is a
  percentage; it has nothing to uncap. The one share it uses — a grid's — is
  Age II's and already never reaches one.

---

## 1. What already exists

Thirty-four materials in four tiers, six forms, four casings and one machine.

| Tier | Materials | Made how |
|---|---|---|
| 0 | iron, copper, gold, tin, zinc, nickel, silver, lead, aluminum, osmium, platinum, lithium, crimson iron, azure silver, uranium, coal dust, redstone dust; bort, certus quartz, fluorite, rime shard, uraninite (one shape each) | dug |
| 1 | bronze, brass, steel, cast iron, electrum, invar, constantan | two dug things, alloyed |
| 2 | stainless, signal alloy, hardened bronze | an alloy of an alloy |
| 3 | void steel, starsteel | wants something the third age makes |

Forms: nugget (1), ingot (9), plate (9), rod (9), gear (36), block (81). Rods
and gears exist only for casing materials. Casings: steel (I, 64 FE at LOW),
invar (II, 256 at MEDIUM), stainless (III, 1024 at HIGH), starsteel (IV, 4096 at
EXTREME); a casing may be made of anything at or below its rung and nothing
above it. The alloy smelter is a tier-I machine.

Four properties are already proved by execution and stay proved: no material is
made of itself however far down; no alloy gives back more than went in; every
material traces to something dug; no casing skips a rung. A fifth is checked by
the validator: every ore family `elysium-ores` generates has a supplier in the
tree, so no ore drops nothing once the old jars go.

---

## 2. What the deep chain adds

Three things, each of which the existing chain was missing and each of which
GregTech has in a form we do not copy:

1. **Recovery** (§3). GregTech's ore processing is a five-machine byproduct
   tree per ore. Ours is one machine and one table: each dug metal has one
   companion, and the refinery gets a third of a unit of it back per ore.
2. **The Cordon's metals** (§4). Valoria's seventeen gear sets stand on
   fourteen new materials in `elysium-ores`. The chain prices them and the
   smelter makes the seven alloys among them, so a Cordoned alloy costs what
   the chain says and not what a crafting grid happened to allow.
3. **Depth without a fifth tier.** The top stays starsteel. The Cordon's two
   Void alloys sit at tier 3 beside it; a deeper tier would be a rung nothing
   in the pack needs yet, and a rung nothing needs is decoration.

Deliberately not added: wires, foils, bolts, springs, rings and the rest of
GregTech's twenty forms (a form is added when a recipe needs it, and none
does); circuits as a progression spine (world tier is the gate here, not a
research tree); a fifth voltage; any machine that repeats one below it.

---

## 3. Recovery — the refinery

**The problem.** Ages I to III give one, two and three units of the ore's own
metal and throw away everything else the rock held. "Age IV is the only full
recovery" (`CONVERSION.md` §2 rule 5) is a promise the existing module does
not keep.

**The machine.** The **refinery**, a tier-III machine (stainless casing, 1024
FE at HIGH). Raw ore in, two things out: the same three units of ingot Age
III's enricher gives — never more — and `RECOVERY_NUGGETS` (three) of the
ore's companion. Two hundred ticks at a full share.

**The companions.** One per dug metal, chosen so that every companion is a
metal the chain ships a nugget of, and so that no metal is its own companion:

| Ore | Companion | Ore | Companion |
|---|---|---|---|
| iron | nickel | lead | silver |
| copper | gold | aluminum | lithium |
| gold | silver | lithium | aluminum |
| tin | zinc | osmium | platinum |
| zinc | tin | platinum | osmium |
| nickel | platinum | uranium | lead |
| silver | lead | crimson iron | iron |
| azure silver | silver | cobalt | nickel |
| pearlium | silver | | |

Gems, dusts and uraninite have no companion: a gem is dug whole, and uraninite
is uranium by another name.

**Why this is not matter out of nothing.** It is the second and last exception
to the rule, after ore processing itself, and it is bounded two ways. The
amount is fixed: a third of a unit per ore, never more, stated once as
`RECOVERY_NUGGETS` and checked to stay under a unit. And it cannot loop: the
refinery accepts only a **raw** form, and no recipe anywhere in the chain
produces a raw form — raw ore comes out of the ground and out of nothing else.
An ingot cannot be turned back into raw ore, so recovery cannot be fed its own
output. The harness checks both: every companion is a mined metal that is not
the ore itself, and the recovered amount is less than a unit.

**What it costs.** A refinery is two tier-III casings, four stainless plates,
two stainless gears and Age II's separator. Priced through the chain, a
tier-III casing is 4 stainless plates and half a gear, and a stainless ingot is
63 units of dug metal (three steel at 18, one nickel at 9), so a refinery is
about **1,900 units of dug metal** and the whole of Age II. A player who has
reached it has built a factory, which is the point.

---

## 4. The Cordon's metals

`elysium-ores` ships fourteen materials behind Valoria's seventeen gear sets
(`rules/CordonMaterials.java` there; `SPEC_VALORIA.md` for the names). The
chain takes them on as follows. Nothing is registered twice: the ores module
ships the ingot, nugget, gem, raw form and block; this age ships only the
worked forms (dust, plate) and prices the whole.

| Material | Tier | Made of | Deep cost (units) |
|---|---|---|---|
| cobalt, pearlium | 0 | dug | 9 |
| jade, pyratite, amber, ruby, sapphire, null amethyst | 0 | dug, one shape | 9 |
| ember slag, bio culture, pressure shell, blight crystal, null shard, relic shard | 0 | left by a creature or a ruin | 9 |
| nightgold | 1 | gold 9 + cobalt 9 | 18 |
| relicsteel | 1 | relic shard 36 | 36 |
| slagsteel | 2 | cobalt 9 + ember slag 9 | 18 |
| biolith | 2 | cobalt 9 + bio culture 9 | 18 |
| bathysteel | 2 | cobalt 9 + pressure shell 9 | 18 |
| hemalite | 3 | nightgold 9 + blight crystal 9 | 27 |
| nullsteel | 3 | nightgold 9 + null shard 9 | 27 |

The six things a creature leaves are tier-0 roots: the ground does not give
them, but a world does, and the chain's "everything traces to something dug"
reads them as dug because they cannot be made. The two-part alloys get an alloy
smelter recipe here at the chain's price; relicsteel is riveted from four
shards on a crafting grid in `elysium-ores` and has no smelter recipe, being
one thing and not two.

The Cordon's alloys are cheap beside the Empire's — an ingot for an ingot,
where bronze is four for one — because their scarcity is in the drop, not the
recipe: a null shard is something a Null Scorpion leaves, and the chain prices
what it can count.

---

## 5. The whole chain, priced

Deep cost is what one ingot ultimately costs in units of dug metal, the number
`FoundryRules.deepCost` computes and the harness bounds (at least 9, at most 8
ingots' worth per step). The validator reads this table and refuses the build
if it disagrees with the rules.

| Material | Tier | Deep cost |
|---|---|---|
| bronze | 1 | 36 |
| brass | 1 | 36 |
| steel | 1 | 18 |
| cast_iron | 1 | 45 |
| electrum | 1 | 18 |
| invar | 1 | 27 |
| constantan | 1 | 18 |
| nightgold | 1 | 18 |
| relicsteel | 1 | 36 |
| stainless | 2 | 63 |
| signal_alloy | 2 | 36 |
| hardened_bronze | 2 | 99 |
| slagsteel | 2 | 18 |
| biolith | 2 | 18 |
| bathysteel | 2 | 18 |
| void_steel | 3 | 72 |
| starsteel | 3 | 135 |
| hemalite | 3 | 27 |
| nullsteel | 3 | 27 |

Every dug material and every drop is 9.

---

## 6. What the code must look like

Same shape as every other module, and the numbers are the part that runs:

- `rules/FoundryRules.java`: the chain, the companions (`COMPANIONS`,
  `RECOVERY_NUGGETS`, `companionOf`, `recoveredPerOre`), the refinery's draw
  as `casing(3).draw()`. JDK and the three ages' rules only.
- `tools/FoundryHarness.java`: a `theRecovery` section — every companion is a
  mined metal, none is its own, the amount is under a unit, and the refinery
  draws more than the smelter. `theChain` and `theConservation` sweep the
  Cordon's rows with everyone else's.
- `block/RefineryBlock.java` and `recipe/RefineRecipe.java`, on the same
  `GridBlock` the smelter uses: one slot in, two out, a share of the grid.
- `tools/gen_foundry.py`: the mirrored table gains the Cordon's rows and the
  companions; it writes the refining recipes, the refinery's block and recipe,
  and the Cordon's dust and plate forms. The validator regenerates into a
  scratch directory and compares.
- `validate.py`: the companion table in the rules and in the generator agree;
  this file's §5 agrees with the rules' deep costs; the refinery is registered
  and its recipe type is on the bus.
- `tools/negtest/negtest_foundry_recovery.sh`.

---

## 7. Status

| Piece | State |
|---|---|
| This design | written 24 Sep 2026 |
| The Cordon's rows in the chain, priced | building |
| The refinery, the companions, recovery | building |
| Worked forms for the Cordon's metals | building |
| A player reaching the refinery in a real world | untested; nothing here has booted the game |
