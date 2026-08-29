# Making it a roguelike

Design notes, not a commitment. Nothing here is built. Every idea is written
against systems that already exist in this repo, with the class that would carry
it named, so this can be picked up cold.

---

## The framing

What is built today is an **ARPG**. Stats, levels, affixes, sockets, reforging,
elemental counters, gear that persists — that is Diablo's shape, and it is a
good one.

The only roguelike element is the regenerating dungeon.

The distinction that matters is not permadeath. It is this:

> In an ARPG, all power is permanent and the dungeon delivers content.
> In a roguelike, **the run is the unit of play.**

Today a player enters the portal, clears, leaves, and re-enters — and the second
run is mechanically identical to the first. Different room arrangement, same
decisions, same power, same stakes. Three things are missing, and they are
listed in dependency order.

Full roguelike purism would be wrong here. Minecraft's whole appeal is
persistent investment, and throwing that away to chase a genre label would trade
a strength for a convention. What follows is roguelite: permanent progression
*outside* the portal, run-scoped everything *inside* it.

---

## 1. A run needs a shape, and the exit needs to be a decision

**The problem.** Leaving and re-entering is free and lossless, so there is no
moment where a player weighs anything. A dungeon is a vending machine.

**The change.** The portal remembers a **depth counter**. Each boss room
contains a descent rather than only loot. Walking back out through the portal
banks what you are carrying and resets depth to 1.

That single change converts "go again" into *"one more floor, or cash out?"* —
which is the entire engine of Diablo's Rifts and Deep Rock's extractions, and it
is the cheapest of everything in this document.

**Where it lives.** `DungeonLayout` already computes BFS depth within one
dungeon and places the boss at the deepest cell; the instance allocator already
holds a per-portal seed and occupancy. Depth becomes one more integer on the
portal's saved state, feeding three things that already take a parameter:

| Depth feeds | Existing hook |
|---|---|
| Room count | `DungeonLayout`'s requested room count |
| Mob level band | `ElysiumScaling.levelFor` — see the tension in §6 |
| Loot tier | `ElysiumStanding.lootTier`, which already takes a tier |

**Open question.** Does depth survive logging out? Probably yes, or a
disconnect becomes a free bank. Does it survive a server restart? It is
`SavedData` either way, so this is a decision rather than a difficulty.

---

## 2. Run-scoped power — the mechanism already exists

**This is the highest-leverage item in the document**, because it is what makes
run two different from run one, and because most of the engine is already
written and being used for something else.

`ElysiumPassives` exposes seventeen hooks that races and classes register
answers to. A boon is *the same thing with a shorter lifetime*: a run-scoped
list of registrants held on the player, consulted by the identical hooks,
cleared when the player leaves the dungeon.

Offer three after each boss or elite. The player picks one.

**Two rules that decide whether this is good or filler:**

- **Change rules, not numbers.** "Your reflect now applies to ranged attacks"
  is a boon. "+12% reflect" is a stat stick, and the game already has stat
  sticks — twelve of them, with a whole screen for spending points.
- **Flavour them by element.** Boons come tagged void / plasma / neural /
  dimensional / kinetic. Stacking one element deep gives the run an identity,
  which the existing counter-matrix then makes *matter* against a dungeon's
  own elemental theme (see §4). The five-element ring stops being a damage-type
  lookup and starts being a build decision.

**Where it lives.** A new run-scoped attachment holding boon ids, plus a
registry in the library mirroring how `ElysiumRace` and `ElysiumClass` register
their passives. The library owns the concept; a content mod ships the boons —
same split as everything else here.

**Risk worth naming.** Seventeen hooks is a small surface for boon design. Some
boons will want to hook things nothing currently hooks (on-room-enter,
on-chest-open, on-boss-kill). Expect to add hooks, and expect that to be the
real work rather than the boons themselves.

---

## 3. Stakes

**The problem.** Minecraft's death — drop everything, jog back, pick it up —
defuses run tension completely. No amount of design elsewhere survives it.

**The gentle version that is still real:** dying in a dungeon ejects you to the
overworld, the instance collapses, run loot is lost, and your **equipped gear
survives**.

The reason to prefer this over anything harsher is that it is *mechanically
honest*. In the current implementation the dungeon genuinely is not a place —
it is an instance, allocated per portal, discarded on exit. There is nothing to
walk back to. The rule is not a punishment bolted on; it is what the
architecture already says.

**Harsher options, if the gentle one proves toothless:**

- Banked loot only materialises when you walk out under your own power.
- Depth resets to 1 on death (it already does, since leaving resets it — this
  is just not making death an exception).
- Gear damage or a Suspicion penalty on death, rather than item loss.

**Do not** make players lose levels or spent points. Losing an hour of the
overworld loop to a mistake in the dungeon loop teaches people to stop playing
the dungeon loop.

---

## 4. The portal frame sets the run

The reason to build this in Minecraft rather than as a standalone roguelike is
that Minecraft has an overworld full of persistent investment. The two loops
should feed each other, and this is the cleanest way to make that literal:

**You do not select a difficulty. You build one.**

- The frame's material decides the dungeon's element. A voidsteel frame gives a
  void-themed run — void-aligned rooms, void mobs, void boons weighted up.
- A catalyst block set into the frame raises elite density and loot tier.
- Rarer frame materials unlock deeper starting depth.

This makes crafting, materials and the element system feed run design, which is
the thing no standalone roguelike can do. It also gives the 196 material gear
items and the 26 materials a second job.

**Where it lives.** The portal already validates a frame. It would need to
*read* the frame rather than only check it, and pass the result into the seed
and the room/mob weighting.

---

## 5. Suspicion as in-run heat

`ElysiumStanding` already has two opposed meters, bands, decay, and a dispatch
system that spawns faction members. None of it currently does anything inside a
dungeon.

**Looting inside a run raises Suspicion within that run.** Rising Suspicion
shifts the dungeon's spawn table toward Empire enforcers, at higher level
bands, and eventually starts dispatching them into rooms already cleared.

A greed dial that is already in the fiction: the Empire notices you taking
things. It is the same shape as Spelunky's ghost or Hades' Heat, but it arrives
from the existing story rather than from a genre checklist.

**Design note.** In-run Suspicion should probably be *separate* from the
persistent meter, and roll into it partially on exit. Otherwise a greedy run
leaves the player hunted in the overworld for an hour, which couples the loops
in the punishing direction rather than the interesting one.

---

## 6. Two tensions to decide deliberately

Both of these are live decisions in code that already exists, and both currently
resolve *against* the roguelike loop by default rather than by choice.

### "A class is for life" fights replay value

`ElysiumNetwork.onChoose` refuses to change a chosen race or class, for good
reasons that were about packet security. But roguelikes get their legs from
trying different builds, and if a player is Imperial Enforcer forever, every run
opens identically.

Boons cover part of this — the run's identity can come from what you pick up
rather than what you are. The Ascension Forge respec, already planned as a
paid workstation interaction, covers the rest.

Worth deciding on purpose rather than inheriting.

### Player-level scaling flattens progression

`ElysiumScaling.levelFor` keys off the average level of nearby players. Inside a
dungeon that is probably wrong: if everything scales to you, there is never an
"I am strong now" moment, and depth stops meaning anything, because depth 1 and
depth 10 both feel like your own level.

**Suggested:** inside a dungeon, depth dominates and player level is a minor
term. Outside, keep what exists. This is a small change to one method and it is
the difference between depth being a number and depth being a threat.

---

## Suggested order

Each of these makes the next one better; the reverse is not true.

1. **Depth and banking** (§1). Smallest change. Nothing else means much until a
   run has a shape and an exit worth thinking about.
2. **Depth-dominant scaling** (§6). One method. Makes depth a threat rather
   than a counter. Do it with §1 or immediately after.
3. **Stakes** (§3). Cheap once §1 exists, because "the instance collapses" is
   already what the code does.
4. **Boons** (§2). The big one. Needs §1 to have a moment to offer them at.
5. **Suspicion as heat** (§5). Needs §1 so that "within the run" is defined.
6. **Portal frame sets the run** (§4). Last, because it is the tuning layer over
   everything above, and it is the least useful when there is only one kind of
   run to tune.

---

## What this does not answer

- **Multiplayer.** Depth, banking and death all get harder with two players in
  one instance. Does one player's death end everyone's run? Whose depth is it?
  The instance is per-portal, which helps, but this needs its own pass.
- **How long a floor should take.** Everything above assumes a floor is minutes,
  not an hour. If a floor takes an hour, "one more?" is not a real question and
  the whole structure changes.
- **What Favor is for.** It is the natural between-run currency, but nothing
  here spends it. If boons land, a Favor-bought boon reroll or an extra opening
  pick is the obvious use.
