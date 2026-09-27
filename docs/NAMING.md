# Our own names

*Written 26 September 2026, after finding that 134 of elysium-bestiary's 176
creature ids were the names of the mods they were converted from.*

## What went wrong

`docs/CONVERSION.md` §1 has said from the beginning what conversion means
here: *mechanics are reimplemented, every number lives in a pure rules class,
art is generated or drawn by us, names and lore are re-themed into Elysium's
own world.* Three of those four were enforced. `compile.sh` and the harnesses
hold the mechanics. `check_rules.py` holds the numbers. `check_retexture.py`
holds the art — it will fail the build over a recoloured sprite.

Nothing at all held the names. So the bestiary was converted mod by mod with
real care taken over every rule in it, and the rosters kept the names they
arrived with: 58 from born_in_chaos, 28 from Valoria, 27 from fangs_n_claws,
13 from knightquest, 10 from friendsandfoes, and a handful more. Every
validator passed the entire time. Every negative test caught what it was
pointed at. The one claim nobody had written down as a rule was the one being
broken, for months, in the open.

That is the lesson worth more than the fix: **a principle stated in a document
is not a principle. It is a hope.** This project's whole method is to turn
claims into arithmetic something executes, and the claim at the top of the
conversion document had been left as prose.

## The rule

> No creature of ours is called what somebody else's creature is called,
> unless the name is a word the language already had.

Both halves matter. The first half is the point. The second half is what keeps
the check usable: half the pack ships a `swordfish` and a `copper_bee`, and
there is no other word for a swordfish. Convergence on an ordinary noun is
arithmetic, not influence. A check that reports it is a check somebody
comments out inside a fortnight — the first version of `check_names.py` did
exactly that and reported forty-five bees and fish.

The line between the two is drawn with a dictionary rather than with an
exemption list, so it does not need maintaining: a shared name is reported
only when some part of it is a **coinage**, a word that did not exist until a
mod author made it up. `bonescaller`, `iceologer` and `chupacabra` are
coinages. `swordfish`, `netherite_bee` and `atlantic_cod` are not.

`check_names.py` is the enforcement, `tools/negtest/negtest_names.sh` is the
sixteen proofs that it works, and eight of those sixteen are proofs that it
stays *quiet* about the things it should.

## The scheme

The replacement names were derived, not typed. A list of 167 replacements
fixes 167 problems and does nothing about the 168th, because nothing in a list
knows where a name came from. `tools/rename_bestiary.py` builds each one out
of the columns the roster already carries, so the structure a player can
already feel is the structure the names carry too.

Two halves to a name: **what a thing is made of or came out of**, and **what
shape it takes**. Both come from the settled system the pack is set in —
foundries, reactors, regolith, signal — rather than from folklore, which is
where all 134 came from.

| roster | how a name is built | example |
|---|---|---|
| the kindred | a family shares a form; members vary by matter | `ferricbarb`, `dunebarb`, `rimebarb`, `cinderbarb` |
| a family of one | named for what it is, since there is nothing to tell apart | `falsecrate`, `deepbore`, `spinecaster` |
| the courts | the court's stem, then the job in it | `forgeprime`, `forgecaller`, `forgeguard`, `forgemortar` |
| the companions | the one job it does, because a companion is a tool that walks | `lumen`, `holdfast`, `borer`, `igniter` |
| the warded | what it is made of — deliberately *not* the ward it carries | `scurf`, `cinderdam`, `rimeguard` |
| the myths | the one thing a rumour keeps when it is retold | `glasseye`, `siphon`, `skimwing` |
| the Vale | the crossing that made it, and how far out that crossing is | `verdlet`, `pinekin`, `ashulk`, `cinderprime` |

Three rules hold the scheme together:

1. **A family shares a form.** `-barb` is the scorpion kindred whatever it is
   made of, so kinship reads off the name and a player can meet two and know
   they are related.
2. **The qualifier says where or what, never how hard.** Harder is what
   `tier` is for — the rule `KindredRules` already had, now in the names too.
3. **A name nobody could have derived is a name somebody borrowed.** This is
   the one that makes the scheme self-policing: every id in the module can be
   rebuilt from its own row, and `tools/rename_bestiary.py` will say so.

The derivation is checked before it is applied: nothing repeated, nothing
illegal as an id, nothing that did not move, and nothing that is anybody
else's. It caught three things a typed list would have shipped — a collision
between the krampus court's head and the rime barrens' ring-three denizen,
a name Immersive Engineering already uses, and one row where I had typed the
same name on both sides of the arrow.

## What the rename itself got wrong

A mechanical rewrite of 167 ids across 28 files found four defects that are
worth writing down, because every one of them is a *class* rather than a typo
and three were found by the negative tests rather than by reading.

**An id is not the only thing spelled like an id.** A court's label and a
kindred family's label are strings too, and three court labels and ten family
labels were spelled exactly like one of their own members — `bonescaller` was
both the bonescaller court and its herald. A whole-token rewrite turned the
label into the member's new name, so the court of nine became the court of
`forgecaller` and the ogre family became the `ferrichulk` family. Labels are
now the stem their members are built from (`forge`, `hulk`), and both rosters
are checked for a member id equal to a label.

**Some of the ids are not ours at all.** `BeastRules.ZOMBIE` is the game's own
zombie, kept as the yardstick every beast in the module is priced against, and
the rewrite renamed it. Nothing failed to compile; the ruler just stopped being
a ruler. Fifteen cases of `negtest_behaviours.sh` reported MISSED and that is
the only reason it was found. `tools/rename_bestiary.py` now reads the game's
own jar and refuses any id that is a vanilla mob unless it is listed, with a
reason, in `VANILLA_OK`.

**An id also lives inside a filename.** A negative test that deletes a
creature's texture writes `remove $M/.../textures/entity/crab.png`, which is
neither a quoted token nor prose. After the rename those lines deleted a file
that no longer existed, `rm -f` said nothing, the validator had nothing to
report, and eleven cases across seven scripts silently stopped testing. They
reported MISSED, which is the whole point of them.

**A dry run that will not parse is silent, and so is a clean one.** Fixing the
above meant leaning on `dryrun.py`, which turned out to have three more holes —
including one that let a helper write to the tree during what was supposed to
be a dry run, and one that had left three scripts with a dry run that died on a
syntax error for as long as they had existed. `tools/negtest/README.md` has the
detail. The rule that came out of it: a tool whose output is silence has to
prove it ran.

## What is not covered, and why

Items and blocks are not checked. `steel_ingot` is a noun the language already
had and half the modded world calls it that; an item named after another mod's
creature would be a borrowing, but the false-positive rate on a full item
sweep is high enough to make the check unusable, and an unusable check is
worse than none. If it is worth doing later, do it as *our item ids against
their entity names* rather than item against item.

`tools/data/borrowed_entity_names.json` is a snapshot: 1,270 entity names
across 51 namespaces, read out of all 176 jars in the pack on 26 September.
Rebuild it when the pack's jars change, or a new mod's coinage will not be
known to be one.
