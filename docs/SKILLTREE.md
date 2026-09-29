# The Ledger Web

The passive tree. One web, shared by everyone; where you start on it is what
your origin and calling gave you. Open the character sheet (`/elysium sheet`,
or the sheet key) and take the **Web** tab.

## What you are looking at

* **The hub**, Enrolled, in the middle. Your name is in the ledger.
* **Ring one**: twelve clusters, one per stat. Every small node in a cluster is
  a little more of that stat; the notable at its heart (Ironblood, Quickstep,
  Iron Will…) is a lot more of it and some of the stat beside it.
* **Ring two**: fourteen larger clusters, one per thing the pack has a system
  for — the Seam (extraction), the Line (fighting), the Weather, the Empire,
  the Works, the Bench, the Kitchen, the Road, the Deep, and the five elements.
  Their small nodes rotate through the three stats that subject leans on; the
  notable (Master of the Seam, Stormcoat, Quartermaster…) gives two of them.
* **Keystones**, the diamonds hanging off the outside of ring two: a doctrine
  committed to, plus a trade — twelve of one stat gained, six of another lost.
  They come in pairs, one of each pair at a time: Sanctioned or Clandestine
  extraction, Warranted or Unsworn Writ, Acclimatised or Deep-Lunged, Ordered
  or Irregular, Bonded or Freehold, Stormborn or Surfacer. A keystone's tag is
  the same one the pack has always read for a doctrine, so what a doctrine did
  before, it does now.
* **Starts**, the squares on the rim: one per origin and one per calling. You
  hold your two for free from the day you declare. They cannot be given back.
* **The frontier**, the third ring outside the starts (29 September 2026):
  sixteen large clusters, one for each part of the pack the first two rings do
  not name — the Dig, the Hunt, the Grove, the Commons, the Town, the Foundry,
  the Reactor, the Rift, the Garden, the Homestead, the Warehouse, the Geodes,
  the Charms, the Forge, the Tomes and the Cages. Each sits beside the system it
  grows out of (the Forge by the Bench, the Reactor by Plasma, the Town by the
  Empire) and is reached by a road out from the nearest road on the rim, so a
  career reads outward from its start: a Marksman's road leads straight out to
  the Rift. Its notables are Patient Hand, Beastmaster, Heartwood, Sunwright,
  Founder, Alloyed, Critical Mass, Riftdelver, Green Thumb, Hearthkeeper,
  Ledgerkeeper, Geodesist, Trinketwise, Master Smith, Illuminated and
  Cagewarden. The frontier has no keystones yet: a keystone sets a doctrine
  something in the pack must read, and none is written for these.
* **Passages** (roads) between all of the above: a point each, and one of the
  stat on the way.

A path through the web reads as a career. An Imperial Factor starts beside
the Empire; a Korrath Warden beside Plasma and the Works; both can reach the
Deep by walking different halves of the web.

## Points

Three passive points per character level from level two (Dylan, 28 September
2026: "3 per level"), separate from the ten stat points a level already gives.
The web is 723 nodes; a level-fifty character holds about a fifth of it. Take a node by clicking it when it is lit — lit means it touches
something you hold and you have a point free. Hover any node to read what it
gives.

## Giving a node back

Right-click a node you hold to give it back. Two rules: it has to be a leaf
(nothing else you hold may hang from it — give those back first), and it
costs a **Ledger Erasure**, an item crafted from paper, a book and gem dust
(the Empire's clerks strike an entry for a fee). Creative mode pays nothing.

## By command

```
/elysium tree points              free points, spent, erasures in the bag
/elysium tree held                every node you hold
/elysium tree allocate <node>     the same as a click; refused with the reason
/elysium tree refund <node>       the same as a right-click
/elysium tree reset               everything given back, no erasure asked (operators)
/elysium level <n>                levels for a test (operators)
```

Node ids are the cluster and a number (`vitality_3`, `seam_11`), the cluster
and `_notable`, `key_<doctrine>`, `start_<origin or calling>`, `road_…`, and
`enrolled`.

## Where it lives

* `elysium-lib/.../tree/ElysiumTreeRules.java` — the table: clusters, rewards,
  keystones, starts, the point rule. Change a number here and the web follows.
* `ElysiumTree.java` — the layout, arithmetic from the table. Add a cluster row
  and it is placed, wired and reachable without a point set by hand.
* `ElysiumTreeState.java` — what a player holds; allocate and refund with their
  reasons; the doctrine tags a keystone sets.
* `screen/ElysiumWebTab.java` — the canvas on the character sheet.
* `elysium-lib/tools/TreeHarness.java` builds the web on the JDK and holds it
  to itself (size, reach from every start, spacing, one notable a cluster,
  keystone pairs on opposite sides, a forty-point ceiling);
  `tools/tree_preview.py` draws its dump to a PNG.

The Extraction Ledger — forty-five nodes as a Pufferfish's Skills datapack,
which needed a mod the pack never shipped — was retired on 28 September 2026.
Its six doctrines are six of the twelve keystones.
