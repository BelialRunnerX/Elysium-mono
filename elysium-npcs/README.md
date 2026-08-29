# Elysium Court

Five named figures of the Black and Emerald Empire, met rather than fought.

Requires **Elysium Library**. Everything else is optional.

---

## What it is

Elysium Mobs is what the Empire sends *after* you. This is who it sends to
*deal* with you.

One of five arrives near a player who has climbed far enough up one of the
standing meters, stays about twenty minutes, accepts a tribute in hand and
answers with something from their own office, and leaves.

| | Reads | Will not deal until | Pays at |
|---|---|---|---|
| **Elysomnion**, Emperor | Suspicion | Hunted | tier 3 |
| **Sylphara Voss**, Chief Imperial Architect | Suspicion | noticed | tier 2 |
| **Sentinel**, Stealth Envoy | Suspicion | always | tier 1 |
| **Lillith**, Fleet Commander | Favor | noticed | tier 2 |
| **Aurelia**, Queen | Favor | Hunted | tier 3 |

The Empire's officers read Suspicion — they are interested in people the Code
has noticed. The two who stand outside the chain of command read Favor.

An envoy never arrives to refuse you: the scheduler only considers members of
the court who would actually deal with the player it is visiting, because an
arrival that refuses is indistinguishable from a broken mod.

## Trading

A tribute in the hand, and an answer in return. Not a merchant screen.

Vanilla's trading buys a browsable list, a price that rises with use, and a UI a
player already knows — at the cost of a large API surface for a mod whose trades
have exactly one axis, which is how far up the meter you are. A right-click says
the same thing in one method, reads as a court rather than a shop, and has the
property that matters: **what you get is decided at the moment you offer**, by
`ElysiumRewards`, so every mod that has registered rewards is in the pool.

The trade-off is real: you cannot see what an envoy will give before you give
them something. That is deliberate for a court — you are being received, not
shopping — but a browsable list is the obvious later addition and would sit on
top of this rather than replace it.

## What it does not contain

No item table. No combat code — an envoy has no attack goal and no attack
damage, because the whole point of the court is that it is the half of the
Empire you can talk to. They are killable, and killing one costs you standing,
which is the correct consequence and needs no special rule.

## Building

```
./gradlew build
```

`python3 validate.py` checks the five against the shipped resources: a skin, a
writ with a model and a texture, three lang keys each, both standing meters
covered, every value of `Regalia` wired to a part the model can actually show or
hide, and no import of elysium-core.

`python3 tools/gen_npcs.py` regenerates the model, the renderer, the five skins,
the writ sprites and the lang from the box table and the palettes.
