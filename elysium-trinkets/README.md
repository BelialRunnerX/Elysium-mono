# Elysium Trinkets

Forty accessories worn in Curios slots. Twenty-four are found, sixteen are made,
and the difference between those two numbers is the whole design.

Requires **Elysium Library** and **Curios API**. Everything else is optional.

---

## The split

**The twenty-four found trinkets change a rule.**

Not "+12% damage" — *the opening blow of a fight passes through you*, *falling
cannot kill you and the Code notices people it cannot kill*, *ore pays twice and
you learn a good deal less doing it*. There is nothing in a rule for an
ascension tier to multiply, which is exactly why none of them ascends: a rule
that is 30% more true is not a thing.

They are found rather than made for the same reason. A rule is either interesting
or it is not, and a player who can craft one to order will craft the two that
suit their build and ignore the other twenty-two. Finding one is being handed a
question — *is this worth a slot?* — which is the point of the slot being scarce.

Every one of them either costs something or only applies sometimes. A trinket
that is strictly better than an empty slot is not a decision, and seven slots of
strictly-better is a stat increase with extra steps.

**The sixteen crafted trinkets change an amount**, and an amount is precisely
what a tier can multiply. They ascend on the same curve armour and weapons
ascend on, without a ceiling.

---

## Slots

Seven, and one sprite silhouette each: ring (two of them), necklace, belt,
charm, hands, back, head. Forty distinct silhouettes at 16×16 would be forty
things you cannot tell apart in an inventory; seven that say *where it goes*,
coloured by the element that says *what it does*, is a reading you can do at a
glance.

---

## What this mod does not contain

No combat code, no tick handler, no attribute logic, no effects.

- **What a trinket does** is an `ElysiumPassive` held by the library. The
  library's fifteen hooks were written for races and classes; a trinket answers
  the same ones. Nothing had to be added to the library to support any of the
  forty.
- **Where a trinket is worn** is Curios, through `CuriosSlots` — the only file
  here that imports it, which the validator enforces. Moving to another
  accessory API is that one file and no trinket.
- **Reforging and ascension** are elysium-core's reforge table, which accepts
  anything implementing `ElysiumSocketable`. This mod is not named there and
  does not name it. A trinket is reforgeable because of what it is, not because
  anyone wired the two together.

What is left is registration and forty descriptions of what an accessory ought
to do, which is what a content mod should be.

---

## Building

```
./gradlew build
```

Or from the monorepo root, `./gradlew buildAll`.

`python3 validate.py` checks the forty against the shipped resources: that each
has a model, a texture and its three lang keys; that its slot is registered,
given to the player and tagged to accept it; that a crafted one has a recipe and
a found one has a loot table and neither has both; and that no recipe uses the
pre-1.21 bare-string ingredient form.

`python3 gen_trinkets.py` (from the repo root) regenerates sprites, models,
lang, slot data, recipes and loot from the Java table. It never overwrites an
existing file, so hand-drawn art always wins and re-running after adding a
trinket is safe.
