# The Ledger Web: Elysium's own passive tree

Dylan, 28 Sep 2026: "Need to both test the skill tree but also make it more
expansive path of exile style."

## Where it stands

**Expanded, 29 September 2026** (CONVERSION.md §16.14): a third ring, the
frontier, of sixteen clusters outside the starts — 723 nodes where there were
419 — with the points three a level. **Built, 28 September 2026** (CONVERSION.md §16.9): the Ledger Web is in the
library — `tree/ElysiumTreeRules` (the table), `ElysiumTree` (the layout, 419
nodes), `ElysiumTreeState` (what a player holds; allocate and refund with
their reasons; the doctrine tags), `ElysiumTreeNames`, and the Web tab of the
character sheet (`screen/ElysiumWebTab`). The old Extraction Ledger — 45
nodes as a Pufferfish's Skills datapack, for a mod the pack never shipped, so
a tree nothing drew — is retired; its six doctrines are six of the twelve
keystones. The player's guide is `docs/SKILLTREE.md`. What follows is the
design as it was written before the build, kept because the build follows it.

## What "Path of Exile style" means here

* **One web, hundreds of nodes**, not four branches of eleven. Every player
  sees the same web; where you start on it is what your origin and calling
  gave you.
* **Small, notable, keystone.** Small nodes are one number each (a stat, an
  attribute, a share). Notables sit at the heart of a cluster and give the
  cluster its name and its larger bonus. Keystones change a rule: the six
  doctrines become keystones, and each keystone is a commitment you can only
  hold one of per pair.
* **Clusters with a subject.** Each cluster is about one thing the pack has
  a system for — one of the twelve stats, one of the five elements, the
  Seam (extraction), the Line (fighting), the Weather and hazards, the
  Empire (standing), the Works (building), the Bench (crafting), the
  Kitchen, the Road (travel), the Deep (space). Clusters that share a
  subject sit near each other, so a path through the web reads as a career.
* **Starts by origin and calling.** The six origins and nine callings each
  own a start position on the rim; a new player's first node is already
  allocated for them and it says what they are. Paths from different starts
  cross in the middle, so a Druun Medicae and an Imperial Enforcer can both
  reach the Deep by walking different halves of the web.
* **Points from levels.** Three passive points per character level
  (`ElysiumCharacter.LEVEL`; one until 28 September 2026, when Dylan asked for
  three), separate from the ten stat points a level already gives; the web is
  the second thing a level buys. Respec by refund: one point
  back per use of a Ledger Erasure, an item the Empire sells and drops, so a
  build can be walked back but not for free.
* **Travel nodes.** Plain connections that give a small share of a stat, so
  every path between clusters costs something and buys something.

## Size

| | now | target |
|---|---|---|
| nodes | 45 → 419 → 723 (29 Sep) | 400 and more |
| notables | 0 → 26 → 42 | 60 and more (one per cluster) |
| keystones | 6 (tags) | 12 (the six doctrines and six new ones) |
| starts | 1 | 15 (six origins, nine callings) |
| connections | ~60 | derived from the layout |

## Data, and where it lives

Rules first, pure, harness-checked, the way everything in the pack is built:

* `elysium-lib/.../tree/ElysiumTreeRules.java`: the clusters (subject,
  centre angle and radius on the web, size), the node kinds and their
  rewards (small: one stat or attribute and a value; notable: two or three;
  keystone: a doctrine or a new rule), the starts (origin or calling -> a
  cluster and a node), the point rule (three per level, cap by level), and
  `faults()`.
* `ElysiumTree`: the generated web as data — every node with its position,
  its rewards and its neighbours — built from the rules by a deterministic
  layout (clusters on rings, nodes on a spiral inside their cluster, travel
  nodes between neighbouring clusters), so that adding a cluster row adds
  its nodes and its roads without anyone placing a point by hand.
* `ElysiumTreeState`: a player attachment with the allocated node ids and
  the points spent; `allocate(node)` allowed only when a neighbour is held
  or the node is the player's start; `refund(node)` allowed only when the
  node is a leaf of what is held (the held set must stay connected).
* Effects: allocated nodes apply through the existing stat block and
  attribute contributors (`ElysiumStackModifiers`, the stat sheet), and
  keystones set the same doctrine tags `ElysiumDoctrines` already reads, so
  every reader in the pack keeps working unchanged.

## The screen

A new tab on the character sheet (`/elysium sheet`): a pannable, zoomable
canvas drawn by `ElysiumUI`, nodes as icons on frames (small, notable,
keystone), held nodes lit, reachable nodes in the palette's accent, the
rest dim; hover explains the node in one line; click allocates when a point
is free; right-click refunds a leaf when an Erasure is in the inventory. A
search box finds a node by name. The web is drawn from `ElysiumTree`, so the
screen and the server agree by construction.

## Tests

* Harness: every node reachable from every start; no two nodes share a
  position; keystone pairs exclusive; the sum of every stat reachable within
  N points bounded (a build cannot buy more than the stat scaling allows);
  every start's first ten nodes are about its subject.
* Validator: the tree's language file names every node; every reward names
  a stat or attribute that exists; the six old doctrines are all keystones;
  the Pufferfish datapack is retired (moved, with a licensing row) rather
  than left as a second, invisible tree.
* Live suite: `/elysium tree allocate <node>` and `refund`, held set stays
  connected, points cannot go negative, a keystone's tag appears and
  disappears.
* Client stage: the tab opens, the web draws, a click allocates, a
  screenshot of a build of forty points.

## Order of work

1. Rules and harness, with the 45 existing nodes rewritten as the first
   clusters so nothing already designed is lost.
2. The layout generator and `ElysiumTree`, rendered headless to a PNG (the
   way creature plans are previewed) until it reads well.
3. State, effects, commands, the live scenarios.
4. The screen and the client stage.
5. Retire the Pufferfish datapack and `gen_skilltree.py`; rewrite
   `docs/SKILLTREE.md` as the player's guide to the web.
