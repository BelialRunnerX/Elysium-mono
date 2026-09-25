# Frontier crops: growing food where food does not grow

**Status:** design. Nothing is built. This is the document `docs/prompts/agent-5-agronomy.md`
asks for before a line of it is written, and it exists because "a space-themed
Pam's" is a sentence with no design in it at all.

**Owner:** `elysium-agronomy`. It reads `elysium-space` and does not edit it.

---

## 1. What this is a replacement for

Nothing. Every other row in `docs/MANIFEST.md` names a jar; this one names a
hole. `elysium-space` has five worlds — the Pale Moon, Cinderveil, Frostfall,
the Emerald Bastion and High Orbit — and a player who reaches any of them eats
out of a backpack until they come home. A frontier you cannot live on is a
frontier you visit.

So the question is not "what crops would be fun on a moon". It is: **what has
to be true for a base off Earth to feed itself, and what is the interesting
thing a player does to make that true.**

## 2. The rule every other part of this module is held to

`AgronomyRules.FARM_AT_MOST` says a farm may be worth two thirds of a mine and
no more, and `BeeRules.HIVE_AT_MOST` a quarter. Those are about *resources* —
metal a player would otherwise dig for — and they exist because the moment a
farm beats a mine, three ages of ore ladder stop being a ladder.

**Food is not held to that rule and must not be.** A frontier crop competes
with a backpack full of bread, not with a pickaxe, and pricing it against the
ore ladder would be the wrong rule applied precisely — the same mistake as
asking whether a cabbage beats a mine.

What food *is* held to is the diet: thirty different foods for ten hearts,
counted as a set. Which means a frontier crop is worth growing if it is a
**different** food, and worth nothing at all if it is a reskinned potato. That
is the design constraint, and it is a sharper one than a number.

## 3. The decision: a crop is a climate, not a plant

The temptation is a roster — twenty alien plants, each with a sprite and a
nutrition, one per world with a few spare. That is a catalogue pass, it is
four hundred lines of table, and it produces twenty things that differ by
their colour.

Instead: **a frontier crop is an Earth crop that has been made to survive a
named hardship**, and the hardship is the world's, not the plant's.

Each of the five worlds states what is wrong with it:

| world | what is wrong with it | what a crop needs |
|---|---|---|
| Pale Moon | no air, no water, no light but the sun | a sealed pot and a water loop |
| Cinderveil | too hot, ash instead of soil | heat tolerance and a filtered bed |
| Frostfall | too cold, frozen ground | a heated bed |
| Emerald Bastion | air and water, hostile life | fencing, which is not this module's |
| High Orbit | no gravity, no ground at all | a rooted frame and full life support |

A crop is then a pair: *which Earth crop* and *which hardships it has been
made to bear*. Eight Earth crops (this module's own, from `CropRules`) times
five worlds is not forty new plants; it is eight plants and a **greenhouse
ladder**, which is the thing actually worth building.

## 4. What gets built

### 4a. The bed, which is the real content

Four rungs, each answering one more hardship, each a block:

    cold frame       keeps frost off        Frostfall, and a cold night anywhere
    sealed bed       holds air and water    Pale Moon
    filtered bed     keeps ash out          Cinderveil
    rooted frame     needs no ground        High Orbit

They are not a ladder of speed. Each answers a *different* hardship, and a
world needs the ones its column names — so Frostfall needs one block, the Pale
Moon two, High Orbit all four. That is the progression, and it is made of
places rather than of numbers.

### 4b. What a crop becomes

An Earth seed put through an awakening altar with the sixth rung and a sample
of the world's own ground comes out as that world's strain: `frostfall_cabbage`,
`pale_moon_barley`. The strain is a different food for the diet, grows only in
the beds its world needs, and yields what the Earth crop yields.

Which reuses `AltarRules` whole rather than inventing a second upgrade system,
and means the frontier is gated behind the sixth rung rather than behind a new
progression nobody asked for.

### 4c. The counts

Eight Earth crops × five worlds is forty strains, and forty is too many for
what they do. **Three Earth crops per world**, chosen so no two worlds grow
the same three: fifteen strains, four beds, and every world's set is different
enough that a player who has seen one has not seen them all.

## 5. What has to be proved

The properties, which is the part that decides whether this is worth building
at all:

- **Every world can feed a base.** For each of the five, the crops that will
  grow there, in the beds that world allows, feed more than a player eats.
  That is arithmetic over `AgronomyRules`' own hunger numbers and it is the
  one thing this whole feature is for.
- **No world can be fed without its beds.** For each world and each bed it
  needs, removing that bed drops it below feeding itself. A bed that is not
  load-bearing is a recipe nobody makes.
- **Every strain is a different food.** Fifteen strains, fifteen diet entries,
  none of them the same entry as an Earth crop — or the diet mechanic counts
  them once and the frontier feeds nobody any better than a cellar does.
- **No strain is as good as its Earth crop on every axis.** A strain that
  grows faster, yields more and feeds better than the plant it came from is a
  reason to farm on a moon rather than at home, and home is where the rest of
  the game is.
- **No two worlds grow the same three.** Which is a set comparison and the
  kind of thing that goes wrong the day somebody adds a sixth world.

## 6. What this does not do

No new progression, no new energy system, no atmosphere simulation. The beds
are blocks with a condition; the condition is read from the world the pot is
standing in; the world states its own hardships in `elysium-space` and this
module reads them. If `elysium-space` adds a world, the table above grows by a
row and the count checks fail until somebody fills it in — which is the point.

## 7. Why it is not built yet

Because it reads five dimension files that belong to another agent's module,
and because the honest order is: `elysium-space` states its hardships in a
form this module can read, and then this module grows things in them. Doing it
the other way round means agronomy inventing what a moon is like, and the moon
already exists.

**The next step is not code.** It is one table in `elysium-space` — five
worlds, and for each the hardships it imposes — agreed with whoever holds that
module. Everything above follows from it.
