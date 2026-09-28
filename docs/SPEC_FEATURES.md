# Features from the Fourth Edition specification

The Fourth Edition is a build contract for a standalone C++ game. This file is
the *other* use of it: a list of everything in those 158 pages that can become a
feature of the Minecraft mod, what it would mean here, and whether it exists yet.

Three things are deliberately not on this list:

  * **engine work the mod gets for free** — cube-sphere planets, LOD and seam
    contracts, greedy meshing, the GPU pipeline, chunk streaming, save formats.
    Minecraft already is that engine, and reimplementing it inside a mod would
    be building a worse one.
  * **scale the mod cannot hold** — a galaxy of star systems, orbital
    construction, interplanetary travel. A Minecraft world is one world.
  * **fortress-management simulation as its own game mode** — 192 citizens with
    jobs, stress and relationships is a different game from the one the mod is.
    Individual *mechanisms* from that half are still listed where they transfer
    to villagers, which the mod already has.

Status is measured against the mod as it stands, not claimed.

  * `HAVE` — implemented and validated.
  * `PARTIAL` — some of it exists; the gap is named.
  * `GAP` — nothing in the mod does this.

---

## A. Survival and environment  (spec Part 10, 5.3)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| A1 | **Five elemental hazards** — Thermal/Plasma, Cryogenic/Kinetic, Corrosive/Neural, Radiological/Void, Pressure/Dimensional | 10.2 | Biome- and depth-driven damage-over-time keyed to the five elements the mod already has. Gear of the matching element resists it; a complete set seals. | HAVE |
| A2 | **Hazard resistance arithmetic** — `1 − Π(1 − source)` | 10.3 | One stacking rule shared by armour, trinkets, runes, food and shelter, for *partial* protection. | HAVE |
| A3 | **Cheap counter vs engineered counter** | 10.2 | Every hazard has a free answer (travel at night, go shallow, eat a ration) and a built answer (insulation, shielded room, drainage). Both are shown to the player when the hazard first bites, and the cheap one is now craftable — see F3. | HAVE — the engineered counters are A7 |
| A4 | **Sealed shelter as a hazard answer** | 10.2, 8.5 | A filed claim suppresses every hazard inside its radius; a detected room removes a share of it. | HAVE — both the claim and the room (C3) |
| A5 | **Weather states with operational effect** — ash, firestorm, whiteout, acid rain, ion storm, fallout gust | 5.3 | Weather events that change hazard intensity and machine output rather than only visuals. | HAVE — six storms across four frontier worlds: ashfall and firestorm on Cinderveil, whiteout on Frostfall, ion storm and regolith gust on the Pale Moon, solar flare in orbit. A storm is registered as a *hazard source*, not a system of its own, so everything that already answers a hazard answers it — which is why "storm shutters" turned out to be a wall (see A4). It takes half a solar panel's output and never all of it. The schedule is arithmetic on the world's own day rather than a stored roll, the way the market's prices are: a sky has a history before anybody visits it and a shape somebody can learn, and nothing has to be saved. The Overworld and the Emerald Bastion are declared calm on purpose — one is the player's own world and one is the paradise — and the validator fails if either quietly acquires a sky. |
| A6 | **Life-support gauges** — oxygen, shielding, energy, temperature | 10.1 | Probably too far from Minecraft's own hunger/air; the *hazard shielding* gauge is the one worth having (A1–A3). | PARTIAL — deliberately narrowed |
| A7 | **Environmental engineering projects** — drainage, thermal management, corrosion treatment, radiation bunker, storm shutters | 10.4 | Machines and block treatments that reduce a hazard over a bounded volume. The room engine (C3) is what they land on: each is a room kind with its own tag requirements and its own hazard. | PARTIAL — the engine exists and *storm shutters* is now answered by it rather than by a project of its own: weather is a hazard source (A5), a sealed room already stops hazards, so the answer to a storm is a roof. Three of the remaining projects do not exist. |

## B. Discovery, scanning and research  (Part 17)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| B1 | **Scanning hierarchy** — flora, fauna, mineral vein, structure, anomaly | 17.1 | The Field Scanner. Kind is read off the thing itself, so a modded creature is catalogued the day it is installed. Rewards rise with difficulty; each new find costs durability. | HAVE |
| B2 | **Research fields** — Geology, Xenobiology, Environmental, Imperial systems, Rift science, Engineering | 17.2 | Six tracks, six named bands each, advanced by twelve deeds. Most pay once per subject, so nothing can be farmed; the repeatable few are each worth less than the cheapest discovery. The harness measures all six at 60–300 deeds to finish. | HAVE |
| B3 | **Research unlocks blueprints, not damage** | 17.2 | Seven unlocks, all information or access: scanner detail, hazard telemetry and a protection audit, the Empire's band edges, a rift mapped on arrival, a bench teardown. The validator refuses any unlock described as granting power, and refuses any unlock nothing reads. | HAVE |
| B4 | **Naming and filing** — file with the Empire for more pay and Suspicion, with the Unsworn for less pay and Favor, or keep it private for nothing | 17.3 | The scanner carries which office it reports to; sneak-use cycles it. 25 filings to reach the Empire's notice, so cataloguing is never the fastest route to being Marked. | HAVE |
| B5 | **Catalogue completion** | 17.1, 17.2 | The catalogue survives death, rides in the synced sheet, and is shown on the character screen's Research page beside the six tracks. | HAVE |

## C. Base, power and automation  (Part 8, 9)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| C1 | **Power priority and brownouts** — Critical, Defense, Logistics, Industry, Comfort | 9.3 | Five classes; a generator's buffer is divided by class, whole bands first, shared proportionally within a band. Reach comes from the Power Conduit, which also reports the grid. A machine unpaid for two seconds is browned out. Defense is declared and unoccupied until D3. | HAVE |
| C2 | **Automation language** — trigger / condition / action, finite vocabulary | 9.5 | The Power Governor: one rule per block, cycled by clicking. Eight conditions (time, weather, three charge thresholds, always) and one action — hold a power class and everything below it. Rules combine by union, so the order they are found in cannot matter; the harness proves that over every ordering at every grid state. | HAVE |
| C3 | **Room types with function** — Shelter, Workshop, Smeltery, Greenhouse, Med bay, Armory, Control room, Reactor room, Bunker | 8.5 | A room is a volume of air you cannot walk out of. Its kind is read off what the walls are made of, as block tags, so a content mod can add to one or register a kind of its own. Shelter, Med bay, Greenhouse and Bunker ship; the other five are A7's engineered counters and land on the same engine. | PARTIAL — 4 of 9, and the engine |
| C4 | **Machine catalogue** | 9.2 | Nine solarpunk machines exist, including the Harvester; the spec lists more (arc smelter, fabricator, chemical vat, crusher, extractor). | PARTIAL |
| C5 | **Network storage vs physical logistics** | 9.4 | Both offered; ordinary crafting never forced through a belt maze. | HAVE — power is networked by conduit; items by the logistics controller, which makes every drawer on its bus one inventory a hopper can fill or empty, and draws grid power per drawer to do it; in Age III an assembly core plans a crafting order against those drawers and hands it out in batches to assemblers on the bus. No terminal or screen, and no belt maze: a bench still crafts from what a player carries |
| C6 | **Base lifecycle and Suspicion from building** | 8.3, 8.4 | Registered building is *filed*: each placement inside a claim raises a permanent floor, never above 70. Unregistered building is *found*: industry (machines, and a data-driven tag of furnaces, metal and redstone — never shelter) builds up a works per 128-block site, which decays, warns at three quarters, and at the line is noticed — Suspicion that escalates per notice with no meter cap, so an unregistered factory can carry its builder into Hunted where a claim never could. Planting or ascending a beacon over a works absorbs it: the lifecycle is build wild, get noticed, register. Doctrine scales the weight. | HAVE |

## D. Empire, standing and consequence  (Part 14)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| D1 | Standing model, Favor and Suspicion | 14.1 | — | HAVE |
| D2 | The Court | 14.4 | — | HAVE (five envoys) |
| D3 | **Register Actions and base sieges** | 14.3 | Declaring a claim invites a response. | PARTIAL — beacon exists, siege does not |
| D4 | **What raises attention** | 14.2 | A single audited list of everything that moves Suspicion. | PARTIAL |
| D5 | **Expanded Imperial infrastructure** | 14.5 | Stations, drones, archives as world content. | GAP |

## E. Character, gear and combat  (Part 11, 12, 13)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| E1 | 12 stats, 6 races, 9 classes, level model | 11.1–11.4 | — | HAVE |
| E2 | Five-element counter ring | 12.1 | — | HAVE |
| E3 | Tier and ascension | 12.2 | — | HAVE |
| E4 | Sockets and runes | 12.3 | — | HAVE |
| E5 | Trinkets | 12.4 | — | HAVE (40) |
| E6 | Damage pipeline | 13.1 | — | HAVE |
| E7 | **Encounter readability** | 13.2 | Nothing hits a player for more than 6 without announcing itself first — enforced in the damage pipeline for *every* attacker, not only this mod's. The wind-up grows with the blow, 10 ticks to 25. Unannounced damage is capped rather than cancelled, so the attack stays real. | HAVE |
| E8 | Authored bestiary families | 13.3 | — | HAVE |
| E9 | **Destructible combat** | 13.5 | Only a blow heavy enough to be telegraphed can scar — the same line, read off the same constant — and then a quarter of the time, one block per blow, within one block of the impact. What can break is opt-in: a 30-entry `fragile_cover` tag of glass, leaves, crops and the like, at or under 0.6 hardness. Never a block entity, a bed, a portal, an unloaded chunk, or anything in a sheltered claim; blocks drop rather than vanish. | HAVE |

## F. Fauna, agriculture and food  (Part 15)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| F1 | **Domestication** | 15.3 | Taming and breeding the mod's own creatures. | GAP |
| F2 | **Expanded agriculture** | 15.4 | The aquaponics tank grows crops and the Harvester works a field; soil quality, pollinators and crop variants are still missing. | PARTIAL |
| F3 | **Food design** — buffs that answer hazards | 15.5 | Five field rations, one per hazard, each granting that hazard's *ward*: three minutes of immunity to it, the third named immunity state beside the armour set and the claim. The ward is a library effect, so anything may grant one. | HAVE |
| F4 | Procedural species / population contract | 15.1, 15.2 | Ecology near the player, statistics far away. | GAP |

## G. Economy and contracts  (Part 19)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| G1 | **Contracts** | 19.2 | Three kinds — deliver, cull, survey — each measured from a count the mod already keeps. Taken and settled at an Imperial Archive by sneaking. Expiry is applied on read rather than ticked, so a deadline passes correctly in an unloaded world. Failing costs Favor, never Suspicion: being unreliable is not sedition. | HAVE |
| G2 | System economies / price registry | 19.1 | Prices exist for the Court; a trade layer does not. | PARTIAL |
| G3 | **Crew and specialists** | 19.3 | Hired villagers who follow and work. | GAP |

## H. Mechanisms transferable from the fortress half  (Part 2 of the spec)

| # | Feature | Spec | In the mod this means | Status |
|---|---------|------|----------------------|--------|
| H1 | Villager memory and relationships | P2 §9 | — | HAVE |
| H2 | **Stress bands** — Stable, Strained, Distressed, Crisis, Recovery | P2 §8.2 | Twelve named reasons — nine burdens, three comforts — derived from the world every time they are asked for rather than accumulated. Four bands plus Recovery, which is a direction and not a level. A villager in crisis will not trade, and says which reason. | HAVE |
| H3 | **Explainability requirement** — a player must be able to ask *why* | P2 §8.3 | Sneak-use empty-handed on a villager gives the memory readout and the stress readout: every reason with its own weight, adding to the total shown. The explanation is the computation, not a description of it — the validator refuses a readout that drops either. | HAVE |
| H4 | **Skill families and learning by practice** | P2 §11 | Practice is kept per *trade*, not per profession — a baker who has sold you forty loaves is better at bread and no better at cake, which is what distinguishes it from vanilla's Novice-to-Master. Four bands, up to 25% off that one offer, with a floor so nothing ever becomes free. Shown in the same readout as memory and stress. | HAVE |
| H5 | **Work orders and job assignment** | P2 §10 | — | GAP |
| H6 | **Quality and craft authorship** — who made a thing, and how well | P2 §15.4 | A maker's mark on every piece of Elysium gear: who forged it, what grade, and when. Five grades earned by practice at *that item* — not by research, which would make the tree compulsory and break B3. Quality is durability and nothing else. | HAVE |
| H7 | **Justice: cases, witnesses, verdicts** | P2 §22 | Crimes witnessed by villagers become cases. | PARTIAL — memory records witnessing |
| H8 | **Medical: injury states, treatment, recovery** | P2 §20 | A blow that *lands* — after armour, the mirror of scarring — can leave a wound, chosen by what did it: laceration (weapons: a heart of max health), fracture (falls: pace), burn (fire: mining speed), concussion (explosions: attack speed). Capped so the worst load is weakened, never crippled (14 of 20 health, no attribute under 60%); every wound heals on its own in 20–30 minutes. The Med bay — a shelter plus a brewing stand or cauldron — heals 4x, 8x asleep. Death clears wounds: no death spiral. | HAVE |

---

## Build order

Ordered by *what the mod can already support*, so each feature lands on
something that exists rather than on scaffolding built for it.

1. **A1–A4 Elemental hazards** — the five elements, gear alignment and armour
   are all in place; this is the largest missing system with the fewest new
   dependencies. *(started)*
2. ~~**B4 + B1 Filing and scanning**~~ — done.
3. ~~**F3 Food as a hazard counter**~~ — done.
4. ~~**C3 Rooms with function**~~ — the engine and three kinds; the other six
   are A7 and want a design session, not a parameter.
5. ~~**C1 Power priority**~~ — done. It needed a grid first: with
   buffer-and-push there was nothing for a priority to be about, so the
   Power Conduit landed with it.
6. ~~**B2–B3 Research fields**~~ — done. Four of the six fields are fed by
   the scanner; Environmental is fed by time in the weather and Engineering
   by building and working benches.
7. ~~**C2 Automation rules**~~ — done. It is a scheduler for C1: a rule
   holds a power class, so priority became programmable rather than fixed.
8. ~~**H2 Stress bands**~~ — done, and H3 with it: the reason stack is what
   the number is made of, so the two cannot disagree.
9. ~~**E7 Encounter readability**~~ — done. The cap lives in the damage
   pipeline so the promise covers mobs from any mod, not only this one's.
10. ~~**H6 craft authorship**~~ — done. Quality comes from practice at the
    bench rather than from a research rank, so the research promise holds.
11. ~~**G1 contracts**~~ — done. Every kind is measured from something the
    mod already counts, so a contract's progress and the world cannot
    disagree.

Everything above this line is a decision about *scope*, not a schedule.

---

## Companion content

**The Extraction Ledger** — a 45-skill category for Pufferfish's Skills,
(retired 28 Sep 2026 for the library's Ledger Web, docs/SKILLTREE.md;
this row is history) generated by `elysium-core/tools/gen_skilltree.py` and shipped inside
elysium-core's data, so it arrives with the mod rather than as a second
download. Four exclusive commitments set scoreboard tags that `ElysiumDoctrine`
reads; the validator checks the tree and the enum against each other in both
directions, because a tag one half sets and the other does not read is a
feature that does nothing and looks exactly like one that works.

## Owner decisions that override the specification

The specification's own authority table puts *later explicit project-owner
decisions* at priority 1 and its inherited text at priority 3, and says the
newest explicit decision wins. These are those.

| Date | Spec says | Decision | Consequence |
|------|-----------|----------|-------------|
| 2026-09-14 | §10.3: independent protections stack "without immunity" | **A fully equipped player is immune to hazards.** | Immunity is a named state — a complete set of the hazard's own element in all four armour slots — rather than a percentage threshold, so a player can look at their armour and know. The `1 − Π(1 − share)` rule still governs everything short of that. Committing four slots to one element is the cost: the other four hazards are met with half shares at best. |
| 2026-09-14 | §10.2 lists shelter as a *cheap counter* among several | **Being inside a claimed base area makes you immune to hazards.** | A second named state, answering a different problem: the armour set is what you wear to *go* into a hostile place, the claim is what you build to *live* in one. Any claim shelters anybody standing in it, so two players can share a base and an unprotected friend can be carried into safety. The permanent Suspicion floor a claim already costs is what buys it. |
| 2026-09-15 | §10.2 lists food among the *cheap counters*; §10.3 says protections stack "without immunity" | **A ration grants immunity to environmental damage.** | The third named state. It shipped as a 10% share and was made immunity instead. What keeps a meal from being the cheapest route to the expensive answer is the clock: a set is worn until it breaks and a claim stands until it is struck, but a ward lasts three minutes and then it is gone. The set is what you wear to go in; the claim is what you build to live there; the meal is what you eat to get across. HazardHarness caps the duration at five minutes so the set always has something left to be for. |
| 2026-09-14 | no equivalent — beacons were fixed at one radius | **The Registry Beacon is ascendable; each tier grants 64 blocks of radius.** | Ascended by using a Registry Beacon on a claimed one, the same verb gear already uses. The price rises with the tier (one beacon, then two, then three) rather than doubling as gear does: radius is linear where gear compounds, so a doubling price would be a wall two tiers in. Each tier also adds 4 to the Suspicion floor, still capped below the Hunted band. |

