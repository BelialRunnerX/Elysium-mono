# The prompt to paste into a new Cowork session

Everything below the line is meant to be copied whole. It assumes nothing about
the new session except that your machine is linked and
`C:\Users\weaks\curseforge` is a connected folder.

---

I'm continuing work on Elysium, a private Minecraft 1.21.1 / NeoForge modpack
conversion. It is for my own use — not commercial, not public — so licensing is
my problem to handle with mod authors, not a reason to stop.

**Get the source back first. It is not in this container.**

The whole tree is on my machine as plain files, at

    C:\Users\weaks\curseforge\Elysium-mono

18058 files, every one verified against a SHA-256 manifest
(`MIRROR-MANIFEST.json`, at the top of that folder) after it was written. Copy
it into the container at `/tmp/Elysium-mono` and check it arrived:

    ls -d elysium-*            should count 29
    ls tools/negtest/*.sh      should count 140
    python3 tools/board.py list

**Then read `docs/AGENTS.md`.** It is short, and it is the whole working
agreement: how the repository is laid out, how to take a unit of work without
standing in somebody else's files, what "finished" means, and the dozen
mistakes this project has actually made and now checks for.

**Take a unit before you touch anything:**

    python3 tools/board.py list
    python3 tools/board.py claim <module> --as <yourname>

**Where it stands.** 29 modules, 45 validators (29 module +
16 cross-cutting), 140 negative-test scripts. 33 of the pack's jars
are replaced; 102 are still planned, across 16 units on the board.
`docs/MANIFEST.md` is the ledger and `docs/SPLITS.md` holds the jars that span
several modules.

**Before you hand anything back:**

    ./compile.sh
    python3 validate.py
    python3 check_rules.py
    bash tools/negtest/negtest_<yours>.sh
    python3 tools/ownership.py --as <yourname>

Then build with `./compile.sh --jars --bundle`, send me the jars, and write
them into both `Instances/Claude/mods` and `Instances/Claude-dev-build/mods`.

**Standing preferences.** One feature all the way through before the next —
architected, ruled, harnessed, generated, validated, negative-tested, built.
Prioritise converting the remaining mods over polishing what exists. Uncap
everything and scale percentile effects on stat/(stat+b); the player should
scale hard. Don't change the GUI. There is no retexture generator. Don't touch
anything under `Instances/V+ and Tech*`. Retire a jar by moving it to
`mods/_superseded/`, never by deleting it, and only after the three checks in
`docs/CONVERSION.md` §4b.
