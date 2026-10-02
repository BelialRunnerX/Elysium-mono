# Handoff, 28 September 2026 (evening): the questbook, the web's points, XP for every action

Written by Claude Fable 5.1 for whoever continues (Dylan is switching the session to
Opus 5.5 for a while). Everything below was decided and researched; almost none of
the quest-system code is written yet. Read this before touching elysium-almanac.

## 1. Dylan's current priorities, in order (they override the finish list in ELYSIUM finish mandate)

1. **A quest system**: "a questbook that introduces the player to all the blocks and
   mechanics and gives rewards for doing it". The book already exists: `elysium-almanac`
   (80 quests, 11 chapters, no rewards, five task kinds). The job is to expand it to
   cover every module's blocks and mechanics and to add rewards. See §3.
2. **Expand the Ledger Web** (the skill tree in the character sheet's Web tab, lib
   `tree/`) further, "path of exile style" (419 nodes today).
3. **Every game action grants character XP** (mining, digging, combat, crafting…).
   Today only kills (4/14 XP in `ElysiumStandingHandler.onDeath`) and ore breaks
   (6 XP, same handler) grant XP.
4. **Web points: 3 per character level** (Dylan, this evening: "tie skill points to the
   character elysium level, 3 per level"). Today `ElysiumTreeRules.POINTS_PER_LEVEL = 1`
   from level 2. The stat points (`ElysiumCharacter.POINTS_PER_LEVEL = 10`, Stats tab)
   are a different thing and stay.
5. Then back to the finish list: item 3 (foundry GregTech-equivalent, space HarvestCraft),
   item 4 hygiene, item 5 play-through.

Dylan also asked for screenshots of whatever is tested (client stages under
`tools/livetest/stages`, driver `mc.ps1`; see memory `mc-playtest-driver`).

## 2. State of the tree at handoff

* Branch `integration` at C:\Users\weaks\AppData\Local\Temp\Elysium-mono (= /tmp/Elysium-mono).
* **Abandoned ruins (colonies, roadmap §3 item 1 "abandoned half")** — built, validated,
  compiled (dist has the jars) and committed as a47010e7 "Colonies: abandoned ruins, and
  a handoff for the questbook" (negtest_colonies3: the four new cases caught; its nine
  misses are the pre-existing stale anchors in STALE-ANCHORS.md). What is NOT yet done for ruins: the live scenario
  `colony_ruin` (tools/livetest/scenarios.py) has not been run against a server on
  the new jar, and the jars in E:\testingbuilds\ElysiumServer and E:\testingbuilds\Elysium
  are still the cf24eb16 build. Install (move old jars to mods/_superseded, copy
  dist/elysium-all-1.0.0.jar + elysiumcolonies-1.0.0.jar), restart the server
  (rcon "stop", then run.bat nogui), then
  `python3 tools/livetest/livetest.py --password elysiumtest --only colony_ruin`.
  The colonies validator's ruins rule had a bug (string literals were stripped before
  the `equals("minecolonies")` check) — fixed with `keep_strings=True`.
* Mirrors /e/Elysium-mono and /d/Elysium-mono and the handbacks are at cf24eb16 until
  fast-forwarded (`git -c safe.directory='*' -C /e/Elysium-mono merge --ff-only integration`
  pattern; see memory `elysium-finish-mandate`).
* Client game on E: is PAUSED by Dylan (ESC menu). Press ESC before any client stage.

## 3. The quest system: design decided, code not started

### What exists (read these first)
* `elysium-almanac/src/main/java/com/elysium/almanac/rules/AlmanacRules.java` — the graph:
  `Kind {HAVE, PLACE, VISIT, SLAY, READ}`, `Task`, `Quest(id, chapter, needs, tasks)`,
  `Chapter(id, after)`, 11 chapters, 80 quests, `cycles()/unreachable()/depthOf()/openTo()`.
* `event/AlmanacProgress.java` — inventory walk every 20 ticks for HAVE/READ, events for
  PLACE/SLAY/VISIT, book handed on first join (`GIVEN = "#given"`), `finish()` shows an
  action-bar line.
* `registry/AlmanacAttachments.java` — `DONE` and `SLAIN` (flat id,count list), both
  serialized + synced + copyOnDeath (the negtest checks all three on both).
* `client/ChapterScreen → QuestScreen → StepScreen`, all `ElysiumChooser` subclasses
  (lib `client/ElysiumChooser`: rows = `Choice(label, detail, accent, enabled)`,
  `chose(index)`, `status()`, one `footer()` button, optional typed filter).
* `tools/gen_almanac.py` — the WORDS (`CHAPTERS`, `QUESTS` dicts: title + one "why" line),
  reads quest ids out of the Java, writes lang, sprite, recipe. Validator refuses hand
  edits under the generator and "why" lines under sixty characters.
* `validate.py` (7 sections), `tools/AlmanacHarness.java` (plays the whole book),
  `tools/negtest/negtest_almanac.sh` (~45 cases; its `edit` anchors are exact strings in
  the Java/generator — keep them or update the negtest).
* Catalogue of everything the pack adds: `docs/features.json` (module dir → modid, kinds
  {Blocks, Items, Creatures, Dimensions, …} as [id, name] pairs; written by
  `tools/catalogue.py`). Mechanics per module: `docs/MECHANICS.md`.

### Decisions
* **Keep the almanac as the questbook**; do not build a second system. Chapters per
  module; the book's own rules (one opening quest `welcome`, one first chapter, no cycles,
  nothing unreachable, deepest chain ≤ 30, no quest waits on a later chapter) stay.
* **New task kinds** (every one needs a handler in AlmanacProgress, a label in
  StepScreen, a lang line, a harness check that it is declared, a negtest case):
  `CRAFT` (PlayerEvent.ItemCraftedEvent), `BREAK` (BlockEvent.BreakEvent — mining and
  digging), `USE` (PlayerInteractEvent.RightClickBlock — "open the machine"),
  `EAT` (LivingEntityUseItemEvent.Finish), `SMELT` (PlayerEvent.ItemSmeltedEvent),
  `LEVEL` (character level ≥ count, tick check via `ElysiumCharacter.getLevel`),
  `WEB` (hold ≥ count nodes on the Ledger Web, tick check via
  `ElysiumTreeState.held(player).size()`). `VISIT` should also be satisfied by the tick
  check when the player is already standing in the dimension.
* **Tags**: a task's `what` may start with `#` and name a tag (item tag for HAVE/CRAFT/EAT/
  SMELT, block tag for PLACE/BREAK/USE, entity_type tag for SLAY). The almanac ships its
  own **family tags** `#elysiumalmanac:<family>` generated by gen_almanac.py from an
  explicit id list (`FAMILIES = {family: [ids]}`), every entry `{"id": ..., "required": false}`
  so a missing module never breaks loading. The generator writes item+block tags for
  block ids, item tags for items, entity_type tags for creatures. Do not name the runtime
  kind-tag pack's tags (`c:tools/...` from lib `ElysiumKindPack`): the validator cannot
  see them.
* **Counts for event kinds**: one `TALLY` attachment (flat "kind what", count list, same
  serialize/sync/copyOnDeath as SLAIN). On an event, every task in the whole book of
  that kind whose `what` matches (id equal, or tag contains) has its tally bumped, so a
  player is credited from the first one whether or not the quest was open.
* **Rewards**: `record Reward(int xp, List<Give> gives)`, `Give(String item, int count)`,
  a `reward` field on `Quest`. XP tiers 20/40/80/160/320 (a level costs 100 + 10·level).
  About a third of quests also give items (real ids; vanilla allowed and checked against
  `assets/minecraft/models/item/*` in `C:/tmp/mc/libs/vanilla-assets.jar`). Rewards are
  **claimed in the book**: StepScreen gets a reward row ("Reward: 80 XP and 8 Iron Ingot",
  detail "Click to claim" / "Claimed"); choosing it sends a `AlmanacClaim(String quest)`
  payload (register in a new `network/AlmanacNetwork` via RegisterPayloadHandlersEvent,
  pattern: lib `network/ElysiumNetwork` + `ElysiumPayloads.TreeAllocate`). Server checks
  done && not claimed, gives items (inventory add, else drop), `ElysiumCharacter.addXp`,
  records in a synced `CLAIMED` attachment. `finish()` should say a reward is waiting.
* **Commands** for tests: a `/almanac` command (RegisterCommandsEvent, pattern
  `elysium-armory/.../command/ArmoryCommand.java`): `finish <id>`, `claim <id>`, `reset`,
  `status` (op).
* **Coverage rule (the "all the blocks" claim, in validate.py)**: every Block, Item and
  Creature id in `docs/features.json` for every module except almanac, lib and
  ascendancy is *introduced* — named directly by a task, or a member of a family tag a
  task names — or matched by an `EXCUSED = [(regex, why)]` list in gen_almanac.py
  (spawn eggs, `_pulling_N` sprites, fish buckets, …). Print
  "introduces N of M blocks, N of M items, N of M creatures; K excused". Require 100 %.
* **Harness additions**: every quest has a reward with xp > 0; the book's total XP puts a
  player who does everything somewhere between level 20 and 60 on the curve; every Kind
  is handled; family tags name only catalogued ids.
* **Client stage** `stage_almanac_client.ps1`: give a hand crank, see the quest finish,
  open the book (right-click the almanac), screenshot the contents, a chapter, a step,
  claim the reward, screenshot; read the level from `/elysium sheet`. Live scenario over
  RCON is limited because quests live on a player.
* **Docs**: docs/CONVERSION.md §16.12 (the questbook), docs/ROADMAP.md, MECHANICS.md almanac
  row, PLAYTEST.md scenario count.

### Chapter plan (order matters: a quest may not wait on a later chapter)
Existing 1–11 stay and are expanded to cover their modules fully:
1 first_light (kinetics) · 2 reconstruction (industry, transit) · 3 warehouse (logistics) ·
4 garden (agronomy: pots, hives, crops, kitchen, fishing, worm farm, rock generators, bees) ·
5 ascent (atomics) · 6 automation (logistics) · 7 foundry · 8 frontier (space: rockets,
motors, oil, five worlds, suits, stations, waygates, freight) · 9 the_dark (arsenal weapons +
bestiary) · 10 the_homestead (atelier, comforts) · 11 the_wilds (wilds, transit compass).
New: 12 the_ledger (core: codex, sheet, stats, levels, the Ledger Web, Ledger Erasure; after
first_light) · 13 the_seams (ores; after first_light) · 14 the_grove (flora) · 15 the_commons
(solarpunk; after reconstruction) · 16 the_forge (armory) · 17 the_geodes (arcana) ·
18 the_tomes (enchanting) · 19 the_dig (arsenal fossils, brushing, totems, artifacts, bows;
after the_dark) · 20 the_charms (trinkets) · 21 the_bands (mobs) · 22 the_cages (spawners) ·
23 the_empire (lib/core: standing, factions, squads, dispatch, hazards, weather, medical,
works, research, runes, gems, material gear; after the_ledger) · 24 the_court (npcs) ·
25 the_town (colonies: stake, town, ruin, charter, spaceport) · 26 the_rift (dungeons).

### Suggested way to produce ~350 quests' content
Fan out drafting to subagents by module group (A agronomy; B space+wilds+transit;
C core+lib+npcs+colonies; D arsenal+bestiary+mobs+spawners+trinkets; E atelier+comforts+
flora+solarpunk; F armory+arcana+enchanting+dungeons+ores; G the five tech modules).
Each returns a Python draft in the scratchpad: `CHAPTERS` [(id, after, title, why)],
`QUESTS` [dicts: id, chapter, needs, tasks [(kind, what, count)], xp, gives, title, why],
`FAMILIES` {family: [ids]}, `EXCUSED` [(regex, why)], with every id checked against
docs/features.json. Then one merge script emits the Java `QUESTS`/`CHAPTERS` blocks and
the generator dicts, and the harness + validator catch mistakes. Build the infrastructure
(kinds, tags, rewards, claim, command, coverage rule) against the existing 80 quests
first so it compiles and validates before content lands. House voice for words: see the
existing `QUESTS` dict in gen_almanac.py (a title a player can scan; one line that says
why it is worth doing; never a restatement of the title).

## 4. The web's points and XP for every action (not started)

* `ElysiumTreeRules.POINTS_PER_LEVEL` 1 → 3. Update the words that say "one point per
  level": docs/SKILLTREE.md line ~36, docs/SPEC_SKILLTREE.md lines ~39–65, the Javadoc in
  `ElysiumTreeState`, and check `TreeHarness`/lib validate "The Ledger Web" section and
  `negtest_web.sh` for anything pinned to 1. `ElysiumTreeRules.faults()` only requires ≥ 1.
  With 3 per level a level-50 character holds ~147 of 419 nodes, which is also the
  argument for expanding the web (priority 2).
* XP for every action: a pure `ElysiumXpRules` in lib (sources MINE, DIG, BUILD, CRAFT,
  SMELT, FIGHT (per damage dealt), SLAY, FARM, FISH, TAME, EXPLORE, TRADE, ENCHANT, EAT;
  base XP each; a per-source per-minute cap against place/break farming) and one
  `ElysiumXpHandler` in lib `event/` that calls `ElysiumCharacter.addXp` — the one place XP
  is scaled by passives. Keep the existing kill/ore awards or move them into the rules
  (the standing handler's 4/14/6 numbers). Harness + lib validate section + negtest +
  a client stage that breaks blocks and reads the level. Flag to Dylan that the level
  curve (`ElysiumLevelling.xpToNext = 100 + 10·level`) was not retuned.

## 5. Environment reminders (full detail in memory files)
* Compile: PATH jdk-21.0.12.101, `ELYSIUM_MC_LIBS` unset, `JAVA_TOOL_OPTIONS=`,
  `ELYSIUM_ANNOTATIONS=/tmp/mc/annotations-24.1.0.jar`, `./compile.sh --jars --bundle`.
* Validators: PATH Python312 + jdk-21.0.4.7, `ELYSIUM_MC_LIBS=C:/tmp/mcwin`,
  `ELYSIUM_ANNOTATIONS=C:/tmp/mc/annotations-24.1.0.jar`, `MC_JAR=C:/tmp/mc/libs/vanilla-assets.jar`,
  `ELYSIUM_COMPAT_LIBS=/tmp/mods`, `STELLARIS=/tmp/ref/stellaris`, `PYTHONIOENCODING=utf-8`.
* Negtests snapshot/restore a module with tar: never compile or edit that module while one runs;
  one at a time. Write long patch scripts with the Write tool, not heredocs.
* Core generators run in order gen_data → art/build.py → gen_material_gear.py, then
  `git checkout` starting_kit.json.

## 6. Progress notes

* **28 Sep, late evening — step 1, the questbook's machinery, built and green.** elysium-almanac
  now has twelve task kinds (HAVE, PLACE, VISIT, SLAY, READ, CRAFT, BREAK, USE, EAT, SMELT, LEVEL,
  WEB), `#tag` subjects with generated family tags, a TALLY attachment counting events from the
  first one, rewards by rule (20 + 10 a step of depth + 10 an extra task, capped 320) or by
  declaration (`AlmanacRules.REWARDS`), claimed from the step screen's last row over the
  `AlmanacClaim` payload into `ElysiumCharacter.addXp`, a `/almanac status|claim|finish|reset`
  command, and a StepScreen that names blocks, entities and worlds. Validator: tags and worlds
  resolved against the tree, rewards paid once and only for done steps, every kind answered
  and labelled, the level curve held against the library's. Harness: kinds and rewards (the
  71-quest book is worth level 32). Found and fixed on the way: the `luna` quest visited
  `elysiumspace:luna`, a dimension that does not exist (the moon is `pale_moon`); the validator
  now refuses a VISIT to a world nothing ships. Compile green. Content drafting by seven
  subagents is in flight (drafts + brief + checker in the session scratchpad `drafts/`).
* **28 Sep, night — step 2, the content and XP for every action.** Seven drafting groups
  (drafts, brief `QUEST_DRAFT_SPEC.md`, checker `draft_check.py` and `merge_drafts.py` in the
  session scratchpad `drafts/`) wrote 448 steps; merged, the book is 519 steps over 26 chapters,
  265 family tags, every catalogued block, item and creature introduced (7,085) or excused with
  a reason (727), worth level 63 (declared experience halved at the merge). The catalogue
  (`docs/features.json`) was regenerated: it lacked 21 ids from the 28 September space work.
  Experience from every action: `ElysiumXpRules` + `ElysiumXpHandler` + `XpHarness` +
  `negtest_xp.sh`, fourteen sources, capped per minute. Web points are 3 a level (committed
  0bbc2e6b). Written up in CONVERSION §16.12 and §16.13.
  **Content gaps the drafting found, for Dylan** (the book names them; a player cannot yet
  finish those steps): no survival source for arcana shards (the_geodes cannot start); arsenal
  dig sites, fossils, totems and rotten wood are never placed in a world; the Vale has no
  entrance (its visit quest was dropped); pigeons never spawn; `neural_leggings`,
  `dimensional_boots` and the armory `template` have no recipe or drop. *(All closed by the survival
  walk of 29 Sep - 2 Oct: geodes grow wild, the dig generates, the Vale has its gate, pigeons spawn,
  the template is found in chests, and the leggings and boots are crafted - check_obtainable.py reaches
  every one; nothing here waits on Dylan any more.)*
* **29 Sep, just after midnight — verified and committed.** Root validate green (29 module
  validators and the cross-checks); negtests almanac 73/0, web 9/0, experience 8/0, colonies3
  24 caught (its 9 misses are the old stale anchors). In the game on E: (screenshots in
  `E:	estingbuilds\Elysium\handback\screenshotslmanac` and `\web`): a step finishes on
  holding its item, the book's three pages draw, a reward is taken from the step's row (40 XP
  and 8 oak logs, then "Taken."), `/almanac` answers every refusal, mining in survival raised
  the experience 28 in five seconds, and level 41 holds 120 web points. The live ruin scenario
  found two ruin bugs, now fixed: the blueprint's placeholder fill (`structurize:` substitution
  blocks, over a thousand under a tavern) was placed as building, and the decay roll was given
  the world height instead of the row, so every block read as roof (a tavern kept 100 of 1,729
  blocks; now 250 over its ten real rows). `/elysium colony ruin <pos>` (operator) raises one,
  because a force-loaded chunk with no player gets no random ticks.
  **Next:** expand the Ledger Web (priority 2); decide the content gaps with Dylan.
* **29 Sep, small hours — everything in survival.** Dylan: "All content should be available and
  accessible via survival." `check_obtainable.py` (root validate) walks the pack from a new world;
  seven investigation agents then ten fix agents; CONVERSION §16.15 has the whole list (pigeons, lost
  mail, foundry tags, ore priority and drop order, overworld ores, titanium/tungsten, writs and the
  envoy dead zone, one codex, furnaces, drawer seal and key, the dig, emberwood stairs, thirty geodes,
  arcana tool tags, salvager/template/conversion, agronomy's six, space stones and tool tags, the Vale
  portal, wilds biome tags). 7,173 of 7,173 catalogued things reached; almanac 523 steps, none left out.
  Declared sources live in tools/obtainable_sources.py + tools/obtainable/. Three fix agents (agronomy,
  Vale, arcana harvest) were cut off by a usage limit mid-negtest; their modules validate; their negtests
  were rerun by the lead.
* **29 Sep, midday — every block on a tool list.** The live harvest audit on that build found 676 blocks that
  asked for a tool and had none tagged (drop nothing in survival). Three agents, one brief (HARVEST_BRIEF in
  the scratchpad; CONVERSION 16.16): atelier 2,990 blocks, agronomy 465, bestiary/colonies/armory/npcs/
  solarpunk/ores. The audit now writes logs/harvest-audit.txt. Two Windows tooling traps closed: flock-guarded
  negtests (16) and the atelier validator's container jar path (its blockstate/cut comparisons had never run).
  Live on the 13:31 build: 0 untagged, 0 unbreakable; harvest: passes; the whole live suite 58/58 (evening).
* **30 Sep — twelve more live scenarios** for what PLAYTEST called unverified: logistics exporter, storage bus,
  monitor, detector, interface, ender link, drawer seal + key (7); agronomy worm farm, tackle box, bobbers, skillet,
  cutting board (5). All pass on the shipped jar but the seal half of the drawer one: a void seal never fired through
  a hopper (NeoForge's hopper hook calls the slot full at getSlotLimit) — fixed (LogisticRules.ceiling,
  DrawerStore.getSlotLimit; negtest_logistics_drawer 36/0), rebuilt 1 Oct. Three stale PLAYTEST claims corrected
  (trinkets are found in five structure chests and crafted; the Cordon metals are dug and alloyed; the four packs
  are implemented). Suite now 70 scenarios.
* **1 Oct — the Vale gate by hand.** stage_vale_client.ps1 run to the end on the 21:08 build: compass lights the ring, a
  survival player standing in the gate arrives in elysiumwilds:vale, a step back through the return ring comes home;
  screenshots in handback/screenshots/vale. Stage traps: face north (yaw 180), aim the crosshair at the sill (a compass
  used on air opens its chooser; ESC clears its search, a second ESC closes), stand in the gate rather than walk through
  (autoJump is off on the dev client; the first trip's search freezes the integrated server ~10 s and a walking client
  overshoots into the return ring), and never run a stage while a compile competes for the CPU.
* **1 Oct — two scenario repairs.** The ruin scenario counted standing blocks by mineable tag, and the style pack's own
  hut blocks are in none (a sound builder's-hut ruin, 82 of 1,243 left, counted as 23 and failed); it now reads the
  server's own account from the log, floor a fiftieth of the building and twenty blocks. The cutting-board scenario
  says why it skipped the server's cut count when the log has rolled over. The Vale landing is being made dry
  (settle() took the heightmap that counts water; the live arrival stood in a bay). The dry spiral at 24 then 64
  blocks still landed in a sea measured 16,629 water columns of 16,641 at sea level within 64 blocks (the Vale has no
  ocean biome; its seas are the land biomes' low ground), so a far stage walks the eight compass lines out to
  256 blocks for a shore. Suite 70/70 on the 23:12 build (transit alone after a lag-induced miss).
* **2 Oct, 00:55 — the far shore, live.** On the 00:49 build a fresh ring's first trip landed at (1082, 64, -109) in the
  Vale: land a step east, water three blocks west and north, the ring across the waterline with its pad over the edge.
  Refinement left: settle the ring a stride inland so both landings are dry. Earlier rings reuse their Vale-side ring
  by design, so a landing test needs a fresh anchor AND the old Vale-side ring (frames, gate, mossy pads) removed.
* **2 Oct, headless (Dylan saving the machine for something else; no Minecraft run).** The live runner asks
  `tick query` before each scenario, waits for the server to keep up and marks what ran behind (livetest.py;
  CONVERSION §17, PLAYTEST). PLAYTEST's 69 unticked boxes cross-referenced against the scenarios and stages:
  3 proved, 41 partly, 25 open (colony day-cycle simulation is the big untested group; four boxes contradicted
  the code and say so now: the launch key is H, the skill tree is the Ledger Web, marble/limestone/alabaster are
  solarpunk stones, JEI is not shipped), and a "What the by-hand run still has to do" section groups the open
  boxes into six client sessions. The ratchet: colonies' 47 and lib's 20 half-statement anchors rewritten to
  whole statements (94 -> 27; --bless now writes LF). Dry runs found pre-existing negtest drift to fix some day:
  negtest_colonies3 (8 anchors: ColonyLife found(), Charters.plan().next(), three in FrontierExchange, three in
  ExchangeRules) and negtest_tooltip (1: @SubscribeEvent in ElysiumQualityOfLife). Docs brought to today: TODO §0-1,
  AGENTS (212 scripts, the live suite in "finished"), RESUME_PROMPT, the stages README (all eleven stages;
  stage_seven_client.ps1 copied in from the scratchpad since §16.7 cites it). Also under way: the Vale ring settled a
  stride inland so both landings are dry (wilds), and dryrun.py running every dry run in a throwaway git worktree.
  Both landed in 39166e62. Then the sixteen stale negtest anchors repaired (STALE-ANCHORS.md "Repaired, 2 October"):
  colonies3 30/0, tooltip 19/0, armour 5/0, runes 12/0, shares 10/0 on real runs; the sweep found a validator gap —
  the colonies fake-player rule counted only FakePlayerFactory.get( and let getMinecraft( past, closed — and two
  tooltip cases that read lang JSON in cp1252 and never injected their fault (UTF-8 now).
  The ratchet finished: the last 27 half-statement anchors (space, ores, flora, logistics, industry, core, atomics,
  agronomy, kinetics, almanac) name whole statements; RULES_BASELINE.txt is empty (677 rules, none tolerated).
  Its dry runs showed six more pre-existing stale anchors (atomics 4, logistics 1, space 1), recorded in
  STALE-ANCHORS.md for the next sweep.
* **29 Sep, ~01:15 — step 3, the Ledger Web wider.** A third ring, the frontier: sixteen clusters
  outside the starts, 723 nodes (was 419), 42 notables; each reached by a road out from the rim's
  nearest road. CONVERSION §16.14, SKILLTREE.md, SPEC_SKILLTREE.md. negtest_web 10/0; in the game
  the marksman's road led out to the rift. No frontier keystones (they need doctrine readers):
  a choice for Dylan. The test stage's spawn is camped by hostile mobs at night (the tester died
  four times): stages now set noon and resistance before any survival step.

