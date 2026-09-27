# What changed, and what it means for your module

## The finding

134 of `elysium-bestiary`'s 176 creature ids were the names of the mods they
had been converted from — 58 from born_in_chaos, 28 from Valoria, 27 from
fangs_n_claws, 13 from knightquest, 10 from friendsandfoes, and a handful
more.

`docs/CONVERSION.md` §1 has said since the beginning what conversion means
here: mechanics reimplemented, every number in a pure rules class, art
generated or drawn by us, **names and lore re-themed into Elysium's own
world**. Three of those four are enforced by something that fails a build —
`compile.sh` and the harnesses hold the mechanics, `check_rules.py` holds the
numbers, `check_retexture.py` will fail over a recoloured sprite. The fourth
was prose.

So every validator passed, every negative test caught what it was pointed at,
and the claim at the top of the conversion document was the one being broken,
in the open, for months.

## What was done about it

All 167 creature ids are re-themed. `check_names.py` is the rule now, and it
runs from the root `validate.py` beside `check_retexture.py`, because the two
are halves of one promise.

> No creature of ours is called what somebody else's creature is called,
> unless the name is a word the language already had.

The second half is what keeps it usable. Half the pack ships a `swordfish` and
a `copper_bee`, and there is no other word for a swordfish — convergence on an
ordinary noun is arithmetic, not influence. The line is drawn with a dictionary
rather than an exemption list: a shared name is reported only when part of it
is a **coinage**, a word that did not exist until a mod author made it up. The
first version had no such rule, reported forty-five bees and fish, and would
have been commented out inside a fortnight.

`docs/NAMING.md` has the scheme. The short version: names are *derived* from
the columns each roster already carries, not typed into a list, because a list
fixes the problems it knows about and nothing else. A kindred family shares a
form and varies by matter (`ferricbarb`, `dunebarb`, `rimebarb`,
`cinderbarb`); a court's members say which court and which job
(`forgeprime`, `forgecaller`, `forgeguard`, `forgemortar`).

## Three things to check in your own module

These are not bestiary problems. They are shapes, and the other modules are
built the same way.

**1. Your labels may be spelled like your ids.** A court's label and a kindred
family's label are strings living in the same table as the member ids, and
thirteen of them were spelled exactly like one of their own members —
`bonescaller` was both the bonescaller court *and* its herald. Any rewrite
that treats a quoted token as an id turns the label into a member's name. If
your module has a roster with a grouping column, check no group is named after
one of its members.

**2. Some of your ids are not yours.** `BeastRules.ZOMBIE` is the game's own
zombie, kept as the yardstick every beast in the module is priced against. The
rewrite renamed the ruler. Nothing failed to compile, no validator complained,
and the only reason it was found is that fifteen cases of
`negtest_behaviours.sh` reported MISSED. If your module holds a reference to
something vanilla or to another module's thing, and it is spelled as a bare
id, it looks exactly like one of yours.

**3. An id also lives inside a filename.** A negative test that deletes a
creature's texture writes
`remove $M/.../textures/entity/crab.png` — not a quoted token, not prose. After
the rename those lines deleted files that no longer existed, `rm -f` said
nothing, the validator had nothing to report, and eleven cases across seven
scripts silently stopped testing. See `STALE-ANCHORS.md`: the same thing may
already be true in your scripts for reasons that have nothing to do with this
rename.

## And one about the tooling everybody shares

`dryrun.py` had three more holes, closed this week, on top of the two already
recorded:

- A helper whose body is an inline `python3 -c` that writes was not neutered,
  so a *dry* run wrote nine creature names into a language file and deleted a
  word from a data file. Since `restore()` is neutered for a dry run, nothing
  put them back.
- A command split over several lines with a trailing backslash was
  half-neutered, leaving the rest dangling.
- A multi-line `restore()` with a brace group inside it was matched to the
  wrong closing brace.

The last two produced a dry script that **would not parse**, so running it
printed nothing — which is exactly what a clean dry run prints.
`negtest_gates.sh`, `negtest_icons.sh` and `negtest_uncap.sh` had been in that
state for as long as they had existed. `dryrun.py` now runs `bash -n` over
what it wrote and refuses to hand over a script that is not a script.

The rule worth keeping: **a tool whose output is silence has to prove it ran.**

## Numbers at the hand-off

- 29 modules compile against NeoForge 21.1.248
- 29 module validators and 17 cross-cutting checks, 46 in all, passing
- `check_rules.py`: 583 rules across 20 validators, 94 on the ratchet, none added
- `tools/negtest/`: 181 scripts; the bestiary family is 544 cases, none missed
- `docs/MANIFEST.md`: 203 rows, 86 done, 2 to go, every jar decided
- Every entity id in `elysium-bestiary` is new, so a world saved against the
  previous jar will not find its mobs
