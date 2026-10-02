# The prompt to paste into a new Cowork session

Everything below the line is meant to be copied whole. It assumes nothing about
the new session except that your machine is linked and
`C:\Users\weaks\curseforge` is a connected folder.

---

I'm continuing work on Elysium, a private Minecraft 1.21.1 / NeoForge modpack
conversion. It is for my own use — not commercial, not public — so licensing is
my problem to handle with mod authors, not a reason to stop.

**Where the source is (2 October 2026).** The working tree is a git
checkout on branch `integration` at

    C:\Users\weaks\AppData\Local\Temp\Elysium-mono      (= /tmp/Elysium-mono in Git Bash)

mirrored by fast-forward to `E:\Elysium-mono` and `D:\Elysium-mono` after
every commit, with a bundle, patch and the built jars in
`E:\testingbuilds\Elysium\handback` and the same under `D:`. Check it:

    ls -d elysium-*            should count 29
    ls tools/negtest/*.sh      should count 212
    git log --oneline -1       84ef6ee2 or later

Read first: `docs/handoff/2026-09-28-questbook-handoff.md` (the dated trail,
newest at the bottom), then `docs/CONVERSION.md` §16.15–16.16 and §17, then
`docs/PLAYTEST.md` for what is proved live and what is still by hand.

**Then read `docs/AGENTS.md`.** It is short, and it is the whole working
agreement: how the repository is laid out, how to take a unit of work without
standing in somebody else's files, what "finished" means, and the dozen
mistakes this project has actually made and now checks for.

**Take a unit before you touch anything:**

    python3 tools/board.py list
    python3 tools/board.py claim <module> --as <yourname>

**Where it stands.** 29 modules, all built and validating; the root
`validate.py` runs 29 module validators plus the cross-cutting checks
(`check_obtainable.py` among them: every item, block and creature the pack
adds is reachable from a new world in survival); 212 negative-test scripts;
a 70-scenario live suite over RCON (`tools/livetest/livetest.py`) on the
dedicated server at `E:\testingbuilds\ElysiumServer`, and client stages
(`tools/livetest/stages/`) that drive the dev client at
`E:\testingbuilds\Elysium`. `docs/MANIFEST.md` is the ledger and
`docs/SPLITS.md` holds the jars that span several modules.

**Before you hand anything back:**

    ./compile.sh
    python3 validate.py
    python3 check_rules.py
    bash tools/negtest/negtest_<yours>.sh
    python3 tools/ownership.py --as <yourname>

Then build with `./compile.sh --jars --bundle` and install `dist/elysium-all-1.0.0.jar`
and `dist/elysiumcolonies-1.0.0.jar` into both E: instances' `mods` (retire the
old jars to `mods/_superseded`, never delete), restart the server, and run the
live suite on a quiet machine. Never compile while a negtest has a module
snapshotted; never run a client stage beside a compile or negtest.

**Standing preferences.** One feature all the way through before the next —
architected, ruled, harnessed, generated, validated, negative-tested, built.
Prioritise converting the remaining mods over polishing what exists. Uncap
everything and scale percentile effects on stat/(stat+b); the player should
scale hard. Don't change the GUI. There is no retexture generator. Don't touch
anything under `Instances/V+ and Tech*`. Retire a jar by moving it to
`mods/_superseded/`, never by deleting it, and only after the three checks in
`docs/CONVERSION.md` §4b.
