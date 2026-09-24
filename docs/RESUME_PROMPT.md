# The prompt to paste into a new Cowork session

Everything below the line is meant to be copied whole. It assumes nothing
about the new session except that your machine is linked and
`C:\Users\weaks\curseforge` is a connected folder.

---

I'm continuing work on Elysium, a private Minecraft 1.21.1 / NeoForge modpack
conversion. It is for my own use — not commercial, not public — so licensing
is my problem to handle with mod authors, not a reason to stop.

**Get the source back first. It is not in this container.**

In my connected `curseforge` folder there are two files, either of which is
enough on its own:

- `elysium-mono-source.tgz` — the working tree; unpacks to `Elysium-mono/`
- `elysium-mono.bundle` — a complete git repo in one file

Stage one of them, put the tree at `/tmp/Elysium-mono`, and check it arrived:
`git log --oneline -2` should show the conversion commit on top of `72cb0ed`,
`ls -d elysium-*` should count 29, and `docs/TODO.md` should exist.

**Then read `/tmp/Elysium-mono/docs/TODO.md` before doing anything else.** It
is the handover: where this stands, what is honestly still missing, the
protocols, and the order to work in. Read all of it. Don't start from my
summary here — start from that file.

## How I want you to work

One feature at a time, all the way through: architect it, write the pure rules,
write the harness, make the harness pass, write the game-side code, write the
generator, write the validator section, write the negative tests, run the root
validation, build. A feature that is "done except for the validator" is not
done. Keep each piece as modular but as extensible as you can.

Every rule lives in a pure JDK class under `rules/` — no `net.minecraft`, no
`net.neoforged` — so a harness can sweep millions of cases without a game.
Every check must be negative-tested: break it deliberately, one way at a time,
and prove it reports. `docs/TODO.md` §5 has the rest, and every protocol in it
is there because something went wrong without it.

Type-check and build with `./compile.sh` (add `--jars --bundle` for the merged
jar). Validate with `python3 validate.py` at the root — 29 module validators
and 16 cross-cutting checks, about eight minutes. Don't build the launcher's
exe or run Gradle builds; `compile.sh` is the check that matters.

## Standing constraints — these are not negotiable

- **Never write to, replace, or delete anything in the `V+ and Tech` instance.**
  Reading it is fine. It is my working pack and it stays untouched.
  `Claude-dev-build` is yours to develop in; `Claude` is my testing copy.
- **Never delete a mod jar.** Retiring one means *moving* it to
  `mods/_superseded/`, and only after the three pre-checks in
  `docs/CONVERSION.md` §4b. I'll clean up jars myself later.
- **Never mark a manifest row DONE for a frame.** Twelve furniture shapes is
  not `handcrafted`; nine beasts is not sixty. Marking a row DONE retires a jar
  and takes real content out of my pack.
- **Never use a credential pasted in plain text**, including any token that
  appears anywhere in old notes. If something needs auth, tell me and I'll do it.
- GitHub is gated for `curl` here and Maven, npm and PyPI return 403. If a
  fetch is blocked, say so and move on — don't go looking for another route.

## Design directions already decided

Uncap progression rather than gating it, and give percentile stat and stat+
scaling — I want the player to scale really hard. Don't change the GUI
elements; they're right as they are. We are not doing the retexture generator.
When you fix a bug, fix it so the intended feature or mechanic still works —
don't neuter a mechanic to make a check pass.

## What to do first

`docs/TODO.md` §6 has the order I agreed to. Unless something has changed:

1. Push the `elysium-conversion` branch to GitHub — I'll run the push, just
   tell me when the tree is ready.
2. I'll launch the pack once and send you the crash log or `latest.log`;
   nothing in this tree has ever met the running game. Test what you can
   yourself before asking me — I'd rather you caught it than I did.
3. Finish `elysium-comforts`. Five small jars, the only module within reach of
   actually being complete.

Start by restoring the tree and reading `docs/TODO.md`, then tell me what you
found and what you intend to do first. Don't ask me to re-explain any of the
above — it's all in that file in more detail.
