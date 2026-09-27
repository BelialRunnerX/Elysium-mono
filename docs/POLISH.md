# Making the pack ready: the polish list

Dylan, 26 September 2026: *"do everything you can come up with to make this a
ready and polished modpack"*, then two specific things. This file is the list,
kept in the order the work is done, with the state of each item. Items marked
`done` name the commit; everything else is open.

## Ground state

Seven jars per instance: `elysium-all`, `elysiumcolonies`, MineColonies,
Structurize, BlockUI, Domum Ornamentum, Curios. Everything else is retired to
`mods/_superseded` with a licensing row (`docs/LICENSING.md`). Both instances
boot with no registry errors and no missing Elysium models.

## 1. What a player meets first

- [x] **P1 Instance hygiene.** (done 26 Sep: 226 files and the retired mods' folders moved to `config/_superseded` on both instances; server named, survival) `config/` still holds 250-odd files from retired
      mods, plus their folders (Mekanism, ProjectE, xaero, patchouli, ...).
      Move what no kept mod reads into `config/_superseded`; same on the
      server. Reset `server.properties` (motd, difficulty), clear test ops.
- [ ] **P2 Mod metadata.** The merged jar's `neoforge.mods.toml` has 28
      display names and no description, logo or credits. Add a one-paragraph
      description per module (from the manifest's sentence), a generated pack
      emblem as `logoFile`, and credits for the Stellaris derivation.
- [ ] **P3 First join.** Confirm the new player gets the almanac and the codex
      and a toast that says where to start; confirm the character pick cannot
      be skipped and cannot be re-entered by accident.
- [ ] **P4 Fresh reference world.** The launcher's reference `level.dat` still
      carries `valoria:the_valoria`; re-save it from the seven-jar pack.

## 2. What is missing that a finished pack has

- [ ] **P5 Sound.** The pack ships no `sounds.json` and no `.ogg` at all:
      every creature, machine and fitting is silent. Synthesise a sci-fi sound
      set from one generator (ambient, hurt, death per creature kind; a hum
      per engine class; clicks for stations) and wire the entities and blocks
      to it. Generated, like the art.
- [ ] **P6 Recipe browsing.** JEI left with the retirement. A player has no way
      to look a recipe up in game. Decision for Dylan: keep JEI as a tool
      (one row on the ledger) or grow the almanac into a recipe book.
- [ ] **P7 Unreachable items.** A static pass over the jar lists items no
      recipe or loot table produces. Most are made in code (fish, buds, gear
      parts, summons); the ones to check by hand: `elysium:emperor_crown`,
      `elysiumatomics:raw_uranium`, `elysiumindustry:coke`,
      `elysiumsolarpunk:biomass_briquette`, `elysiumspawners:spawner`,
      `elysiumspace:*_bucket`, `*_canister`, `elysiumflora:*_seeds`,
      `elysiumfoundry:bort|certus_quartz|fluorite|rime_shard`.
- [x] **P8 MineColonies and the pigeons.** (done: the pigeon lacked `FOLLOW_RANGE`, which MineColonies' RecipeAnalyzer reads for every animal) MineColonies logs "Couldnt analyze
      animal: elysiumcomforts:pigeon" while sizing animals for its herders.
      Find what it reads and give the pigeon it.

## 3. Dylan's two specific asks

- [x] **P9 Connection plates and dyes.** (built 26 Sep, network and live-tested 27 Sep, see CONVERSION.md §16, §16.1) When a pipe, cable, wire, conduit, bus or
      channel touches a block that is not another of its kind, its face
      against that block shows a large flat square in the run's own colour:
      the visual cue that the connection is live. Blocks: `elysiumspace:pipe`,
      `elysiumspace:cable`, `elysiumsolarpunk:power_conduit`,
      `elysiumindustry:*_wire`, `elysiumlogistics:bus`,
      `elysiumatomics:coolant_channel`. Plus Dylan's dye rule: dyed runs join
      only their own colour, undyed runs, or a device.
- [x] **P11 Live tests.** (27 Sep, CONVERSION.md §17) `tools/livetest/livetest.py`
      asks the running dedicated server: twenty-one scenarios over RCON covering
      every run's plates and dye rule, energy and fluid carried along cables
      and pipes, the industry and solarpunk grids, the harvesters, and every
      producing machine and hopper-fed inventory in the pack. Its first runs
      found the cable buffer sloshing between two cables and never arriving,
      a fluid tank saved twice and emptied on load, the whole solarpunk
      power economy bounded to zero by its own outward limits, and twenty
      machines that asked their own refusing output slot for room and never
      produced (§16.2); all fixed, each with a rule and negative cases.
      Rerun it after any change to a machine, a run or a capability.
- [~] **P12 Feature catalogue and one-by-one validation.** (Dylan, 27 Sep)
      `tools/catalogue.py` reads every module and writes `docs/FEATURES.md`
      and `docs/features.json`: every block, item, creature, biome,
      enchantment, effect, structure, dimension, world feature, advancement,
      loot modifier and recipe kind by id and name, and the mechanics each
      validator executes (29 modules; ~5 000 blocks, ~2 400 items, 396
      creatures, 36 structures, 7 dimensions). The `catalogue` live scenario
      then sets every block once, gives every item once and summons every
      creature once on the test server; its first pass found the seven vein
      generators the game refused to place. `docs/MECHANICS.md` then listed
      every runtime mechanic (four surveys, 29 modules) with how a server
      exercises it; `scenarios_mechanics.py` runs the reachable ones and its
      first passes found the absorption clamp, dead relic passives, animals
      that never bred, the herald chain and a dozen more (CONVERSION.md
      §16.3). Player-only mechanics are marked `client-only` for the stages.
      The client stage `stage_mechanics_client.ps1` then took the player-only
      ones on the test client and found every grade doing nothing (§16.4);
      the world scan found the hoar hill never generating (every wilds box
      now shadows the vanilla claims inside it). Left: the remaining
      `client-only` rows (menus, screens, natural spawns, colony autonomy).
- [x] **P13 Two mod folders on the stick.** (27 Sep) `tools/modfolders.py`
      rebuilds `E:\Elysium-client-mods` and `E:\Elysium-server-mods` from the
      test server's mods folder, sorting each jar by the sides its mods file
      declares, with a README naming every jar and why it is there. Today
      both hold the same seven jars, because none of ours or of the
      MineColonies stack is client only; the server folder is the set the
      dedicated test server boots with, the client folder the set the test
      client boots with.
- [ ] **P10 Creature models.** Every creature added since the original eight
      Elysium mobs gets a model in the same class as those: a hundred-plus
      cubes, a real part hierarchy, a generated sheet in the sci-fi palette;
      simpler only where the animal is simple (birds, small fauna). Built from
      body plans in the mobs generator's `art` package, per roster kind.
      Dylan, 27 Sep: **the eight Elysium mobs keep their models exactly as
      they are** (`elysium-mobs`; reference renders in
      `docs/reference/mobs/`), and every other creature is patterned after
      their complexity but with a **different shape**: no hunched clawed
      humanoid recoloured, but a body plan of its own (quadruped, insectile,
      serpentine, winged, floating) at the same density of cubes and joints.
      Last, by Dylan's order.
