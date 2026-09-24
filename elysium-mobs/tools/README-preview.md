# Watching the animation

Eight mob families share one `setupAnim`. Giving each its own walk was deferred
with the note that a distinctive walk "is not worth guessing at without being
able to watch it". This is the machinery for watching it.

## The design, and why it is shaped this way

The obvious build is: read the box tables out of `gen_mobs.py`, transcribe
`setupAnim` into Python, draw the result. That produces a **second copy of the
animation**, and a second copy can silently disagree with the one in the game.
This project has now been bitten three times by exactly that shape — a stub
that flattened `TieredItem`, a stub that invented `Component.withStyle`, and a
classpath that read stub sources while reporting that it read jars. A previewer
that can disagree with the shipped code is worth less than nothing, because it
is believed.

So nothing is reimplemented. Minecraft's own classes do all of it:

| step | class | what it gives |
| --- | --- | --- |
| `createBodyLayer()` | the generated model | the real mesh |
| `LayerDefinition.bakeRoot()` | Minecraft | the real baked parts, cubes and UVs |
| `setupAnim(...)` | the generated model | the real animation |
| `renderToBuffer(...)` | Minecraft | the real traversal and per-vertex transform |

None of it touches OpenGL — `PoseStack` is JOML arithmetic and `VertexConsumer`
is an interface — so the whole pipeline runs headless. The only thing written
here is `PoseCapture.java`, a `VertexConsumer` that writes vertices down
instead of drawing them, and the projector that turns them into pixels.

That is also why this survives the move to more complex models: it sits
*downstream* of Minecraft's geometry pipeline rather than modelling it.

## Status: working

`walk.sh` is the entry point. From the repo root:

```
mobs/tools/walk.sh [limbSwingAmount]
```

It captures all eight models through the real pipeline, renders each one, and
writes strips, GIFs and two contact sheets into `mobs/tools/preview/`.

`limbSwingAmount` defaults to **0.65**, which is about a walk. It is worth
knowing that this number was wrong in the first version of the harness, which
hardcoded 1.0 — a full sprint. Every gait looked like a caricature of itself,
and "the arms swing too wide" was a fact about the harness rather than about
any model. Judge at 0.65, sanity-check at 0.3 and 1.0.

## Calibration

The capture side cannot really be wrong: it is Minecraft's own code. The
renderer is mine, so it is checked against models whose correct appearance is
already known —

```
java -cp ... PoseCapture net.minecraft.client.model.CowModel 1 cow.json --bind
python3 preview.py cow.json <vanilla cow.png> out/
```

`--bind` skips `setupAnim` and captures the rest pose, which is what a vanilla
model can supply without a live entity to animate from. A cow that comes out
looking like a cow — right proportions, right face, right texture placement —
is the evidence that the projection, the UV mapping and the Y-flip are correct.
This was run against the cow, the chicken and the iron golem before any
judgement was made about our own mobs, and it is worth re-running after any
change to `preview.py`.

Face shading copies vanilla's own multipliers (top 1.0, bottom 0.5, sides 0.8
and 0.6). Flat-lit renders hide the one thing a walk has to be judged on:
which face of a limb is turned towards you as it swings.

## Requirements

The real jars, assembled by `./real_jars.sh`. See that file for the staging
list, including the six that have to come from a flat `curseforge/jars/` folder
because the Maven tree is deeper than the file bridge reaches.
