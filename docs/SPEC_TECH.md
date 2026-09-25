# The tech spine — one physics, four ages

This is the contract every tech module in the conversion is built against. It
exists because Ages II, III and IV all connect to Age I's rotation, and a
decision made in `elysium-kinetics` that is not written down here is a decision
the next session will make differently.

Read `CONVERSION.md` first for what the four ages *are*. This file is only the
physics.

---

## 0. One quantity, named once

Create calls its quantity Stress Units. We do not, for a reason that has
nothing to do with originality: `elysium-lib` already has a `stress` package,
and it is about **citizens** — how a MineColonies worker is coping. Two things
called stress in one codebase is a bug waiting to be written by whoever reads
the wrong one.

So the rotational quantity is **torque**, and the terms are:

| Term | Meaning |
|---|---|
| **speed** | rpm, signed. Negative is anticlockwise. |
| **torque** | the twisting force a line carries. The unit is τ. |
| **power** | torque × speed. What a source actually makes. |
| **line** | one rigidly coupled set of shafts. Everything on it turns at one speed. |
| **stalled** | a line whose loads want more torque than its sources can make at any speed. It stops. |
| **sheared** | a line driven past the rating of its weakest shaft. It breaks. |

`CONVERSION.md` §2 rule 2 said "stress units, from Age I". This supersedes it:
the *rule* it was stating — one rotation unit across all four ages — stands
exactly as written, under this name.

---

## 1. The engine

### Sources have a curve, not a number

This is the one place we deliberately do not follow Create. In Create a source
supplies a fixed capacity and a fixed speed; adding one machine too many flips
the whole network to OVERSTRESSED and everything stops at once. It is easy to
implement and it reads as a light switch.

Ours is the physical model, which is no harder and behaves far better. Every
source is described by two numbers:

* **free speed** `f` — how fast it spins with nothing attached.
* **stall torque** `s` — how hard it pushes when it is not moving at all.

and its torque at any speed is the line between them:

```
τ(rpm) = s × (1 − rpm / f),  clamped at 0
```

A water wheel is slow and strong (low `f`, high `s`). A steam engine is fast
and comparatively weak. That difference is now a fact about the machine rather
than a tooltip.

### A line finds its own speed

Loads draw a constant torque `d` each — a millstone needs the same force to
crush whether it is turning fast or slow. So a line settles where supply meets
demand. Summing over the sources:

```
Σs − rpm × Σ(s/f) = D          where D = Σ draws
```
```
rpm = (Σs − D) / Σ(s/f)
```

Three consequences, all of them good:

1. **Adding a machine slows the line down** instead of switching it off. The
   feedback is continuous and a player can feel the cost of the last thing they
   attached before it becomes a problem.
2. **Stalling is what happens when D ≥ Σs** — the loads outweigh what the
   sources can push against even from a standstill. Then, and only then, rpm is
   0 and nothing runs.
3. **A machine that needs a minimum speed stops first.** The failure a player
   sees is "the saw is turning too slowly", which points at the fix, rather
   than "OVERSTRESSED", which points at nothing.

### Gearing reflects, and costs

A sub-line geared so that `rpm_sub = r × rpm_main` appears to the main line as:

* a load of `d` becomes `d × r` — power is the same on both sides;
* a source of `(f, s)` becomes `(f / r, s × r)`;
* and each gear stage loses **3%** of the power crossing it.

So gearing up for speed costs torque, gearing down for torque costs speed, and
a long train costs more than a short one. Create charges nothing for gearing;
charging for it is what makes *where you put the machine* a decision.

### Shafts shear

Every shaft material has a speed rating. A line driven past the rating of its
weakest shaft breaks that shaft. This is the counterweight to gearing up.

| Material | Rating | Age |
|---|---|---|
| Timber | 64 rpm | I |
| Brass | 192 rpm | I (late) |
| Steel | 512 rpm | II |
| Ascendant alloy | 1024 rpm | III |

`MAX_RPM = 1024` is the hard ceiling: nothing in any age turns faster.

---

## 2. Where the ages join

| Age | Module | What it adds to the spine |
|---|---|---|
| I | `elysium-kinetics` | The spine itself. Rotation, torque, contraptions. Converts torque to FE **badly** — a dynamo is a load with a poor exchange rate, so electricity in Age I is a curiosity. |
| II | `elysium-industry` | FE properly. Age II machines accept a shaft as an alternative input at a **penalty**, so an Age I line can run an Age II press slowly rather than not at all. |
| III | `elysium-atomics` | FE **kept** rather than spent the tick it was made, plus heat and chemistry. Motors here are sources with very high free speed, which is what makes Age III able to drive Age I machinery at speeds timber cannot survive. |
| IV | `elysium-foundry` | Assumes both. Adds no new physics; adds the full material chain. |

**One energy unit** is Forge Energy, as `elysium-space` already uses. Torque is
not energy and is never expressed in FE; the two meet only at a dynamo or a
motor, each of which is a load on one side and a source on the other.

---

## 3. What the code must look like

Same shape as every other module in this project, and the parts that carry
numbers are the parts that get executed:

* `rules/KineticRules.java` — every number and every formula above, JDK only,
  no Minecraft imports. Executed by `tools/KineticHarness.java`.
* `validate.py` — reads the constants back out of the Java and checks the
  design decisions, so a generator or a block that used a different number
  fails the build rather than shipping.
* Negative tests that break each rule and confirm the validator says so.
* Every listener on the right bus (`check_buses.py` now enforces this; we
  shipped a crash over it).
* Every screen on `ElysiumChooser` or drawn with `ElysiumUI` (`check_ui.py`).

---

## 4. Status

### Age II — `elysium-industry`

| Piece | State |
|---|---|
| The grid (`IndustryRules`: voltage, wires, the share) | **done** |
| The joint with Age I: alternator, motor, shaft-driven machines | **done** (arithmetic) |
| Wires (copper, gold, steel) and the transformer | **done** |
| Generators: steam turbine, diesel engine, alternator | **done** |
| Machines: crusher, rolling mill, arc furnace, separator | **done** |
| The motor: a grid driving an Age I line, and the proof it cannot pay | **done** |
| Steel and plate: the one material chain the age makes for itself | **done** |
| Multiblocks | planned |
| A screen for a machine, if any of them ever earns one | not planned |

**Age II's one sentence**, against Age I's: a line is rigidly coupled, so
everything on it slows together; a grid is divided, so everything on it gets
the same *share*. Neither ever stops dead, which is the same decision made
twice, but they fail differently and a player can feel which one they are
standing next to.

A wire has a capacity and **burns** if you exceed it — the Age II answer to a
timber shaft shearing, and the reason voltage tiers exist rather than being a
number on a tooltip. It loses power per block, so where the generator sits is a
decision.

**How long a job takes.** A grid divides, so there is no floor for a recipe to
state and no `Pace` in an Age II recipe: the file says how long the job takes
at a full share, and half a share is twice as long. Progress is counted in
*work* rather than in ticks (`WORK_FULL` per tick at full share) so that a
share which moves mid-job neither invents nor throws away progress, and the
countdown a player is shown is proved against the accumulator on every build.

**The loop, and why it does not pay.** A motor turns electricity into rotation
and an alternator turns rotation back, so the two of them bolted together are a
loop — and a loop that returned more than it took would be free power, which
would break every recipe cost in four ages at once. It is closed in arithmetic,
not by a rule: with `n` motors and `m` alternators geared at `r`, what comes
back over what went in is `p·f·x·(s − x·d) / (R·s)` where `x = mr/n`, a
parabola whose peak is `p·f·s / (4·d·R)` — **0.6** as the numbers stand.
`IndustryRules.roundTripBest()` is that peak, the harness checks it is under
one, and it sweeps every count and gearing a player could build to confirm
nothing beats it. A later age that raises a motor's torque or an alternator's
yield has to keep it under one or the build stops.

**Age II machinery on an Age I line.** A machine's torque draw is its FE draw
divided by `SHAFT_FE_PER_TORQUE`, so the two halves of the spine cannot drift
apart when one of them is retuned; and a machine lets go of the shaft entirely
once the grid is doing at least as well as a shaft could, so a grid-fed machine
does not drag a line it is not using and a shaft-fed one cannot take the
shaft's speed without paying its torque.

**What the age makes for itself.** Steel, out of the arc furnace, gating the
top tier of wire, the diesel engine and the separator; and plate, out of the
rolling mill, needed by everything above the bootstrap pair. The first turbine
and the first alternator are craftable from ingots, because a first grid you
cannot build without a grid is a wall rather than a ladder. Both sit under `c:`
tags so `elysium-foundry` can supply them by its own routes later without
either module naming the other.

### Age III — `elysium-atomics`

| Piece | State |
|---|---|
| The engine (`AtomicRules`: storage, heat, the servo, the ladder) | **done** |
| The grid learns to lend and borrow (`IndustryRules.Reserve`/`Flow`) | **done** |
| Cells: basic, advanced, quantum | **done** |
| The reactor, its coolant channels and its meltdown | **done** |
| The solar panel, which exists to make cells worth building | **done** |
| The servo: a grid driving an Age I line faster than Age I can turn | **done** |
| Machines: enricher, reaction chamber, centrifuge | **done** |
| The uranium chain the pack was missing | **done** |
| Teleportation, and anything that moves matter | planned |

**Age III's one sentence**, against the two below it: a line is rigidly
coupled, so everything on it slows together; a grid is divided, so everything
on it gets the same share; a **buffered** grid keeps what it does not spend and
spends what it kept. That is the first time in the spine that time is a
resource — every grid until now has been instantaneous, so building more
generation than you need was waste.

**How it joins the grid, and what Age II had to learn.** Not by running one of
its own. `IndustryRules.Reserve` is two floats — the most everything on the
grid could lend this tick and the most it could take — and `IndustryRules.Flow`
is what came of it. Age II ships nothing that answers those, and knows nothing
about what a cell is, how much it holds or what it is made of. There is one
model of electricity in the pack, and a machine never has to know whether there
is a battery behind it.

**Energy is kept in whole units and the rounding goes against the player.**
Charging rounds down and discharging rounds up, so a bank takes at most what it
was offered and gives up at least what was drawn. A bank kept in floating point
gains and loses a unit or two per tick at the magnitudes the top cell holds,
and over a night that is thousands of units appearing out of nothing; there is
no tolerance that makes "energy is conserved" true of a float, and there is an
exact answer available.

**Heat settles; it does not run away.** Heat in is what the throttle makes;
heat out is a fraction of how far above the room the core already is; the
fraction is how much plumbing touches it. So a given throttle with a given
number of channels reaches a temperature and stays there, and prodding the core
says both where it is and where this setting will land. A meltdown is always
something a player did and always something they could have worked out first.
Bare and wide open a core settles at several times `MELTDOWN`; fully plumbed
and wide open it settles just under `SAFE`, and one channel short of full it
does not — so every channel is a decision.

**The loop, again, and it is not the same loop.** A servo turning an alternator
is a new one, and Age II's proof covers Age II's motor and says nothing about
this. The servo's stall torque is pinned at the value that returns exactly what
Age II's motor does — **0.6** — and the harness proves it in closed form,
sweeps every gearing and count, and then checks again with *both* ages' motors
mixed on one line, settled by Age I's own solver rather than by any formula.

**What the age owes the pack.** `elysium-ores` generates uranium, and every
item that could have filled `c:raw_materials/uranium` belongs to a mod this
conversion replaces — so the ore drops nothing until somebody supplies one. The
age that wants the metal is the age that owes it, which is the rule §4b of
CONVERSION.md states: a module is not finished until the integrations it
inherited are written.

### Beside the ages — `elysium-logistics`

Storage is not an age and does not want to be one: a drawer works the day it is
crafted and costs nothing to run. What hangs off the ages is everything above
that — a controller is a machine on Age II's grid, and the three blocks that
make a warehouse build things for you are machines on Age III's.

| Piece | State |
|---|---|
| Three drawers, a bus, a controller that is one inventory | **done** |
| The planner: refuses cycles by name, caps depth, steps and walks | **done** |
| The order: every job after the jobs that make its ingredients | **done** |
| Assembly core, assemblers, terminal | **done** |
| A storage core (types rather than blocks) | planned |
| A screen on the terminal | planned — a strict addition, nothing behind it changes |

**What a plan is, and what it was missing.** `plan()` answers *what has to be
made and how many times*. It says nothing about *when*, and a map has no order
in it. An autocrafter that read the runs in whatever order a hash table
produced would try to assemble the gear before the plates existed, find the
ingredients missing, and stall holding somebody's iron — which is the single
most complained-about behaviour in every storage mod this module replaces. So
`plan()` now returns the jobs in a settled order as well, and the harness
**follows four thousand random plans to the end** with nothing but what the
plan said would have to be found, and fails if any ingredient ever goes short.

**The walk needed a budget, not just a limit.** `MOST_DEPTH` bounds how deep a
plan goes and `MOST_STEPS` how many different things are in it. Neither bounds
how *long* it takes, because the walk is over paths rather than over recipes: a
recipe set where a handful of things each need the same four ingredients, twenty
deep, has forty-eight recipes in it and 2.7 × 10¹¹ paths through them. Every
real Minecraft recipe tree is that shape. There is now a walk budget, and
running out of it is a refusal with a sentence in it — refused in 72ms in the
harness, where before it did not come back.

**Two mechanisms that must agree.** The walk refuses a cycle by the path it is
standing on; the ordering refuses one by never settling. If they ever disagree,
the plan is refused rather than a winner being picked.

**No pattern items and no screen**, which are the two things every mod here
replaces has and this one does not. Patterns are a tax dressed as a mechanic
and the only thing they protect against is a planner that cannot take a large
recipe set; ours can, so the core reads the game's own crafting recipes and the
gate is machinery and power instead. The terminal is asked by holding up what
you want — a stack of it, or one if you crouch — and answers in a sentence: how
many steps, how many machine-runs, or exactly why not.

**Which recipe, and which item.** A dozen recipes make a torch and an
ingredient that takes a tag takes many items, so something has to choose. Fewest
different ingredients, then by name, and the lowest-named item a tag will
accept — never whatever the recipe manager listed first. A factory that made a
torch out of coal on Monday and charcoal on Tuesday with nothing changed is one
a player cannot plan around.

### Age I — `elysium-kinetics`

| Piece | State |
|---|---|
| The engine (`KineticRules`) | **done** |
| The tree of speeds (`LineRules`: ratios, jamming, reflection) | **done** |
| The line in the world (`KineticLine`: traversal, one walk per change) | **done** |
| Sources: hand crank, water wheel, windmill | **done** |
| Shafts (timber, brass), cogwheels (8 and 24 teeth), gearbox | **done** |
| The four machines — millstone, press, saw, mixer | **done** |
| Four recipe types, one shared class, one shared block | **done** |
| The readout: prod any block, hear what the line is doing | **done** |
| Clutches and a way to stop a line without breaking it | planned |
| Contraptions (moving structures) | planned |
| Fuel engines and the refinery (a ladder of six fuels, three engines) | **done** |
| The dynamo (torque → FE, badly) | **done** |

**How two blocks connect**, since every later age inherits it. Three rules and
no more:

1. **Along the axis** — same axis, one in front of the other: the same shaft,
   ratio 1.
2. **In mesh** — two cogs side by side, same axis: ratio minus the first's
   teeth over the second's, and one gear stage.
3. **Round a gearbox** — a shaft that runs *into* a gearbox (not one lying
   across its face) is coupled at ratio −1, and one gear stage. So two shafts
   on opposite faces turn together and a corner reverses.

Adding a machine means implementing `KineticNode`; adding a *kind of
connection* means changing `KineticLine` and nowhere else.

**The machines** all share one class. A millstone, press, saw and mixer differ
in exactly three ways — how much torque they draw, how fast they must turn, and
which recipes they read — so `MachineBlock` holds everything else and a fifth
machine is a subclass with three answers in it. Likewise `KineticRecipe` is one
class carrying a `Kind`, with four registered types, so a saw recipe still will
not run in a press but there is one codec rather than four that drift.

**The seam**, which is the one place this age touches the next one's subject.
A dynamo is a load like any other and turns rotation into FE at a rate that is
deliberately poor. The number that makes both directions checkable lives in
`SeamRules.WORTH_OF_A_TORQUE_RPM` — what one torque-rpm of mechanical power is
worth in FE — and **every later age reads it rather than choosing its own**.
Age II's motor charges exactly that to go back the other way, so the round trip
out through a dynamo and home through a motor returns

    (DYNAMO_FE_PER_RPM / DYNAMO.draw) / WORTH_OF_A_TORQUE_RPM  =  0.5

of the mechanical power that left. Under one by arithmetic rather than by
anybody remembering, which is what stops the pack having a generator made of
two blocks and a shaft. A later age that invents its own exchange rate breaks
this, and `elysium-kinetics/validate.py` fails if this one drifts above a half.

**The gearing a dynamo wants is solved, not guessed.** A dynamo geared up by
`r` through `n` stages reflects onto the line as a load of `D·r/k`, so the line
settles slower as the dynamo turns faster and the product has a peak:

    r*   = k·s / (2·D)
    rpm* = k · s·f/4 / D  =  k · powerAt(f/2) / D

which is the sentence worth keeping: **the best a dynamo can ever do is to hold
its source at half its free speed**, where a source makes the most power it
will ever make. Gear past that and the line slows more than the dynamo gains.
The harness sweeps five hundred ratios per source per stage count against that
closed form.

**The windmill** is a thing you build rather than a block you place: a bearing,
and sailcloth flood-filled out from it in the plane it turns in. Sails add
stall torque and not free speed, so a bigger windmill pulls harder and never
goes faster — which is what stops it becoming a steam engine made of wool.
