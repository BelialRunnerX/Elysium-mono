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
- [x] **P2 Mod metadata.** (27 Sep, night) Every module's manifest already
      carried its description; it now carries `credits` (from `mod_credits`
      in its properties; Elysium Space names the Stellaris derivation) and the
      one generated pack emblem (`tools/gen_emblem.py` →
      `elysium_logo.png` in the library jar, drawn only in the interface
      palette). `check_merge.py` requires all three and the emblem's size
      and colours; `negtest_merge.sh` takes each away.
- [~] **P3 First join.** (27 Sep, evening: `stage_firstjoin_client.ps1` on a
      fresh world) The new player gets the codex, the almanac, bread and
      torches, and the "Declare yourself" screen opens over the world; ESC
      does not close it before Confirm, a choice and Confirm record the
      character ("Recorded: Imperial, Marksman"), `/elysium sheet` afterwards
      opens the tabbed sheet and closes on ESC, and `/elysium stats|standing|
      tier` answer. Found on the way: **a grave made in deep water was washed
      away within seconds** and spilled the kit to the sea floor (fixed:
      the headstone is declared solid and a death in deep water now leaves a
      grave holding everything; CONVERSION.md §16.4). The test rig's fresh
      world now spawns on land for its seed. 28 Sep: a beast killed the new
      player under the modal declaration screen at a night spawn, so
      `ElysiumUndeclaredShield` cancels incoming damage until a race and a
      calling are recorded (rule in the library validator,
      `negtest_undeclared.sh`); the stage reruns clean on the P4 reference.
      The codex in the hotbar is the pointer to where to start (decided, 28
      Sep; a toast over the declaration screen would be covered by it).
- [x] **P4 Fresh reference world.** (28 Sep) The reference is now the
      dedicated test server's own `level.dat` and `serverconfig`, copied into
      `tools/livetest/stages/reference/` from a world the seven-jar pack
      generated under the current wilds preset: no Valoria dimension, no
      retired mod's config or datapack, and the game's own spawn for its seed
      (`-80 71 128`, on land). `StripPlayer` turns cheats on for the stages
      and the launcher no longer overrides the spawn. Because a world made
      from a preset carries the overworld's biome list inline, the wilds
      validator now holds the reference's list to `normal.json` entry for
      entry (the hoar-hill class of bug), with a negtest; re-save the
      reference from the server whenever the preset changes.

## 2. What is missing that a finished pack has

- [x] **P5 Sound.** (28 Sep) The pack shipped no `sounds.json` and no
      `.ogg` at all. Now every clip is synthesised: `art/sound.py` (numpy
      oscillators, noise, envelopes, one-pole filters, a comb, a small room)
      renders a voice's three calls (idle, hurt, death), a machine's looped
      hum and a station's click from numbers in
      `elysium-lib/.../sound/ElysiumSoundRules.java` (33 voices, 8 hums, 3
      clicks; pure, `SoundHarness`); `elysium-lib/tools/gen_sounds.py` writes
      110 OGG Vorbis clips (mono, 22 050 Hz, 1.2 MB in all), `sounds.json` and
      a subtitle per event; `ElysiumSounds` registers one event per clip from
      the same table. Every creature class (22, the eight included) answers
      for a voice through `ElysiumVoices` — the bestiary by family, kind or
      form, the fish by water, the eight by id — 28 machines hum while they
      run (kinetics and industry through their bases; space, solarpunk,
      atomics, agronomy, foundry, logistics at their work sites) and 13
      stations click as they open. The library validator holds table,
      registry, files and subtitles to one another and rebuilds the clips
      (`samefile` compares an `.ogg` by its decoded samples, since the Ogg
      serial differs per encode); `check_sound.py` holds every name a module
      asks for to the table and every voice to something that asks for it;
      `negtest_sound.sh`. Vorbis through `soundfile`/libsndfile 1.2.2, already
      on the machine. Left: hear them in the game and tune the descriptions
      by ear (the numbers are a first draft); block-place and fitting sounds
      (plates snapping on) stay the game's own.
- [ ] **P6 Recipe browsing.** JEI left with the retirement. A player has no way
      to look a recipe up in game. Dylan's decision (28 Sep): "support JEI
      though but leave it as a non dependency". So: a JEI plugin in the
      library for every Elysium recipe kind and machine, compiled against the
      JEI API, declared optional in the manifest, and loaded only when JEI is
      installed; the pack boots and plays without it. JEI itself stays a
      player's choice, not a jar we ship.
- [x] **P7 Unreachable items.** (28 Sep) A static pass over the jar lists
      items no recipe or loot table produces. Checked by hand, each is made
      in code: the Emperor's Crown drops (`ElysiumCrownDrops`), coke comes out
      of the coke oven, the briquette out of the bioreactor, the spawner is
      what a vanilla spawner becomes under silk touch, the space buckets fill
      at their fluids and the canisters are bottled at the separator and
      refinery, the flora's seeds come off the wild plants worldgen places,
      and raw uranium and the foundry's four gems come out of the ores
      module's ore blocks, whose tables hand the block to Java and whose
      unifying loot modifier resolves the family's canonical item at
      runtime. The live suite now proves that last one (`ores:` scenario:
      loot the ore into a chest, ask the chest for the family tag), and it
      found the one real gap: what lands for a gem family is the agronomy's
      chunk (in `c:raw_materials/<gem>`, which the ore-drop search finds
      before `c:gems/<gem>`), and nothing turned a chunk into the gem. The
      foundry generator now writes a furnace and a blast-furnace recipe from
      each gem's raw tag to the gem; the foundry validator holds the four,
      with a negtest.
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
      now shadows the vanilla claims inside it). `stage_screens_client.ps1`
      then opened all fifteen menu screens and counted a night's natural
      spawns; `stage_firstjoin_client.ps1` covered first join (P3). Left: the
      `client-only` rows that need hands on the menus (smithing, reforging,
      infusion, the transmuter's items, trinkets worn) and colony autonomy
      (`stage_seven_client.ps1`, run 26 Sep).
- [x] **P13 Two mod folders on the stick.** (27 Sep) `tools/modfolders.py`
      rebuilds `E:\Elysium-client-mods` and `E:\Elysium-server-mods` from the
      test server's mods folder, sorting each jar by the sides its mods file
      declares, with a README naming every jar and why it is there. Today
      both hold the same seven jars, because none of ours or of the
      MineColonies stack is client only; the server folder is the set the
      dedicated test server boots with, the client folder the set the test
      client boots with.
- [~] **P14 Screens as photographed.** (27 Sep, night: `stage_screens_client.ps1`)
      Five screens drew their content over one another: the arcane table's
      stat lines over its slots, the machine screen's energy line under its
      fuel slot, the scriptorium's buy line over the inventory rule, the
      smithing table's part list over its input slot and the inventory
      heading, the salvager's button on the rule. Each crowded screen has
      sixteen more pixels above the inventory (182 tall) and its slots moved
      clear. Five boxes opened the game's grey chest screen (cabinets, the
      tackle box, the freight terminal, packs, the per-player loot chests):
      the library now owns a chest menu type per row count under the kit's
      `ElysiumChestScreen`, every box goes through `ElysiumChests`, and
      `check_ui.py` refuses the game's. Left: status lines truncated to one
      line ("Add essence to ..", "A book, and some..") could wrap.
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
      Last, by Dylan's order. **Begun 27 Sep, night** (Dylan: "now might be a
      good time to start making more advanced textures and models for mobs"):
      `art/bodyplans.py` holds the plans (a quadruped, a knuckle-walking hulk,
      a stilt-legged insectile walker so far) and `art/creature.py` turns a
      plan into a model class with its bounds measured through the part tree
      and a skin; `gen_bestiary.py` now dresses the nine beasts in them
      (113, 124 and 138 boxes; the six-box table stays only for the kinds
      that still borrow it). Rendered headless through the mobs' PoseCapture
      before shipping. The twelve animals followed the same night: the
      grazers and foragers as two quadruped builds, the fowl as a winged
      plan (76 boxes) and the swimmers as a fish (30, simpler where the
      animal is simple). The kin, court, denizens, wards, myths and
      companions then took the plans by form (each rules class already named
      a beast family or animal kind per row), with their rosters' colours as
      the skins' base and their marks as the accent. The pigeons (a small
      winged plan that beats its wings off the ground, 74 boxes), the 96 bee
      species (an insect: six legs, two pairs of wings beating off each bee's
      own age, 75) and the 105 fish (30) followed, each species' sheet in its
      own colour from the same rows. The shade is a floater (86 boxes,
      translucent, reaching when it hunts). The six-box tables and CreatureModel
      came out on 28 Sep: Bounds is a record of its own, the generator and the
      validator read nothing off a model table, the hitbox arithmetic runs on
      every plan's measured WIDE/TALL/DEEP for beasts and animals alike, and the
      generated shape classes are rebuilt and compared like the resources. Left:
      a second pass on proportions and colour per creature once they have been
      seen in the world. The space mobs stay
      as they are: they are Stellaris's own detailed models, ported, not ours
      to replace.
      Seen in the world on 28 Sep (tools/livetest/stages/stage_creatures_client.ps1:
      group shots and a portrait of each of fourteen kinds, in handback/screenshots/creatures):
      the stilt, hulk, grazer, prowler, fowl, swimmer, floater, winged, insect and fish
      plans all render and animate on their entities; the shade is translucent and
      reaches; nothing is a magenta cube.
