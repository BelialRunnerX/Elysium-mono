# Stale anchors — every negative-test script, swept 27 September 2026

A negative test breaks a thing and proves the validator says so. It finds the
line to break by matching a piece of text — an **anchor**. When that line is
later rewritten the anchor stops matching, and the case silently tests
nothing: it neither fails nor complains, it just stops being a test. The suite
still prints green.

`python3 tools/dryrun.py` exists to find these. It writes a copy of the script
with every mutation it recognises neutered and, since 2 October, runs that
copy inside a throwaway `git worktree` of the working tree, so whatever the
patterns miss lands in a copy nobody keeps. It reports nothing but stale
anchors, what the script wrote inside the copy, and proof that the real tree
is byte-identical before and after.

## How this list was made, and what it cost

All 182 scripts in `tools/negtest/` were dry-run. **Six holes had to be closed
in `dryrun.py` first**, all now recorded in `tools/negtest/README.md`. The
worst of them was that a dry script which failed to *parse* printed nothing —
which reads exactly like a clean run. `negtest_gates.sh`, `negtest_icons.sh`
and `negtest_uncap.sh` had been in that state for as long as they had
existed.

**This is not fallout from the bestiary rename.** Every finding here is in a
module that rename never touched. The bestiary family — thirteen scripts, 544
cases — was re-run to completion afterwards with none missed, and the one
stale anchor found in `elysium-bestiary` (`negtest_abilities.sh`, where a
comment had been written into the middle of a three-line anchor) is already
fixed.

## What to do

From `/tmp/Elysium-mono`, for each script under your module:

    python3 tools/dryrun.py tools/negtest/negtest_<name>.sh "all <module> checks passed"

That builds the copy, runs the dry script in it, prints its output, removes
the copy and says whether the real tree changed — it must not. Budget a minute
a script. Nothing is written beside the script any more; `--in-place` is the
old behaviour and is only safe for a script whose run in the copy wrote
nothing.

Each `STALE:` line names the file and the text that no longer matches. Find
what that line became and update the anchor — and prefer anchoring a **whole
statement** rather than a run of lines, which is what went wrong in the
bestiary's case. Then run the script for real: a stale anchor sometimes hides
a check that has genuinely stopped working.

## Confirmed — 20 script(s), 48 anchor(s)

### elysium-arcana

**`negtest_abilities.sh`**

    STALE: SummonEntities.java :: ElysiumAbilityEntities.bolt(BOLT::get);

### elysium-armory

**`negtest_affixes.sh`**

    STALE: keen.json :: "weight": 12
    STALE: ElysiumRollAffixesFunction.java :: ElysiumAffixRoll.roll(stack, count.getInt(context), context.getRandom())

### elysium-atelier

**`negtest_arch.sh`**

    STALE: AtelierItems.java :: : java.util.List.of(AtelierBlocks.ALL, AtelierBlocks.OPENINGS,

**`negtest_cloth.sh`**

    STALE: AtelierItems.java :: AtelierBlocks.FITTINGS, AtelierBlocks.CLOTH)) {

**`negtest_courses.sh`**

    STALE: AtelierItems.java :: AtelierBlocks.PIECES, AtelierBlocks.COURSES)) {
    STALE: AtelierItems.java :: int blocks = AtelierBlocks.ALL.size() + AtelierBlocks.OPENINGS.size()

### elysium-atomics

**`negtest_atomics.sh`**

    STALE: AtomicRules.java :: } else if (surplus > 0.0F) {
    STALE: AtomicRules.java :: fromCells = Math.min(shortfall, couldGive(cells, stored));
    STALE: AtomicRules.java :: toCells = Math.min(surplus, couldTake(cells, stored));
    STALE: AtomicRules.java :: float carried = Math.max(supply, Math.min(demand, supply + fromCells) + toCells)

### elysium-bestiary

**`negtest_abilities.sh`**

    STALE: SummonEntities.java :: ElysiumAbilityEntities.bolt(BOLT::get);

### elysium-colonies

**`negtest_colonies.sh`**

    STALE: Colonies.java :: public record Settlement(int citizens, @Nullable UUID owner) {
    STALE: ColonyProgress.java :: ColonyRules.worldFloor(town.citizens())
    STALE: ColonyStanding.java :: ElysiumStanding.addFloorSource(player -> {
    STALE: ColonyStanding.java :: ColonyRules.noticeFloor(town.citizens())

**`negtest_colonies3.sh`** — repaired 2 October, see the end of this file

    STALE: ColonyLife.java :: String wanted = Charters.plan().next(town.citizens(),
    STALE: FrontierExchange.java :: int taken = Colonies.take(level, town.id(), offer.getKey(), cost);
    STALE: FrontierExchange.java :: if (ItemStack.isSameItem(offer.getKey(), want.wanted())) {
    STALE: FrontierExchange.java :: if (spent >= ExchangeRules.MOST_SPENT_PER_DAY) {
    STALE: ExchangeRules.java :: public static final int BASE_RATE = 8;
    STALE: ExchangeRules.java :: int budget = Math.max(0, MOST_SPENT_PER_DAY - Math.max(0, spent));
    STALE: ExchangeRules.java :: int rate = Math.round(BASE_RATE / worldFactor);

**`negtest_colonies4.sh`**

    STALE: ColonyLife.java :: int traded = FrontierExchange.settle(level, town, world);

**`negtest_colonies5.sh`**

    STALE: ColonyLife.java :: if (report(level, town, ReliefRules.stuckFor(town.day(),
    STALE: ColonyLife.java :: && ReliefRules.dueToReport(town.day(), ledger.lastReported())
    STALE: ReliefRules.java :: public static final int GRANT_DAYS = 12;

### elysium-core

**`negtest_t.sh`**

    2. the validator's shape list goes stale again

### elysium-kinetics

**`negtest_kinetics.sh`**

    STALE: KineticRecipe.java :: MIXING("mixing");

**`negtest_seam.sh`**

    STALE: KineticItems.java :: made.add(DYNAMO);

### elysium-lib

**`negtest_affixes.sh`**

    STALE: keen.json :: "weight": 12
    STALE: ElysiumRollAffixesFunction.java :: ElysiumAffixRoll.roll(stack, count.getInt(context), context.getRandom())

**`negtest_hazards.sh`**

    STALE: ElysiumResistance.java :: return Math.min(share, MAX_SINGLE_SHARE);
    STALE: ElysiumResistance.java :: remaining *= 1.0F - clampShare(share);
    STALE: ElysiumResistance.java :: if (!(share > 0.0F)) {
    STALE: HazardHarness.java :: final float shelter = 0.35F;
    STALE: ElysiumProtection.java :: public static final float BASE_SHARE = 0.11F;

**`negtest_healthbar.sh`**

    STALE: ElysiumHealthBar.java :: return Math.max(1, Math.min(width - 1, health));
    STALE: ElysiumHealthBar.java :: return Math.min(1.0F, value / capacity);
    STALE: ElysiumHealthBar.java :: if (!(capacity > 0.0F) || !(value > 0.0F)) {
    STALE: ElysiumHealthBar.java :: public static final int BOTTOM_INSET = 23;
    STALE: ElysiumHealthBar.java :: public static final int WIDTH = 81;

**`negtest_scaling.sh`**

    STALE: ElysiumStats.java :: ChatFormatting.AQUA, 1200.0F, 0.85F
    STALE: ElysiumStats.java :: ChatFormatting.DARK_AQUA, 1400.0F, 0.90F
    STALE: ElysiumStat.java :: return value / (value + halfway) * ceiling;
    STALE: ElysiumStat.java :: public static final float SCALING_DIVISOR = 512.0F;
    STALE: ElysiumStats.java :: return Math.min(VANILLA_TOUGHNESS_CEILING, beyond * TOUGHNESS_PER_POINT);

**`negtest_tooltip.sh`** — repaired 2 October, see the end of this file

    STALE: ElysiumQualityOfLife.java :: @SubscribeEvent

### elysium-logistics

**`negtest_logistics.sh`** (found 29 Sep by the drawer work)

    STALE: IndustryRules.java :: return new IndustryRules.Machine(devices * DEVICE_DRAW,   (now + extra * DEVICE_DRAW)


**`negtest_logistics2.sh`**

    STALE: LogisticItems.java :: TIMBER_DRAWER, BRASS_DRAWER, STEEL_DRAWER, BUS, CONTROLLER);

### elysium-ores

**`negtest_ores.sh`**

    STALE: netherrack_lead_ore.json :: "family": "lead"

### elysium-solarpunk

**`negtest_machines2.sh`**

    STALE: SolarpunkMachines.java :: machine.getInventoryX()


## Needs a clean run by its owner — and why

`dryrun.py` leaked on one shape of script when this sweep was made, and these
nine are written in it: a helper that does something *before* its inline
python —

    edit() { keep "$1"; python3 -c "
    ...
    " "$@"; }

Six holes of this family were closed that day and this one was left open, so I
stopped rather than keep patching a shared tool under time pressure. The
practical effect was that a dry run of these scripts executed some of their
real edits and, because `restore()` *is* neutered, did not undo them. It
rewrote 47 files in `elysium-lib` before it was noticed. Everything it touched
was restored and every module validates.

So the findings below are **real anchors that did not match**, but they were
collected from a run that had partially mutated the tree, and later ones may
be consequences of earlier ones rather than independent. Re-run each properly
— as a real negative test, not a dry run — and see which cases report MISSED.

**Since 2 October the nine can be dry-run again.** The dry copy runs inside a
throwaway worktree, so the edits this shape makes for real land in the copy
and the tree is untouched — the tool proves it after every run. The shape
itself is still not neutered, which has one consequence worth knowing: inside
the copy the edits are not undone between cases either, so in a script that
edits the same file twice a second `STALE` may be the first edit's doing. The
nine:

    negtest_armour, negtest_freight, negtest_gates, negtest_jewelry,
    negtest_runes, negtest_shares, negtest_weather, negtest_weather_a,
    negtest_weather_b

The fix that was asked for here — "parsing the script, or running the dry
copy in a throwaway checkout, would end the whole family of them" — is the
second one, done on 2 October; `tools/negtest/README.md` has the details and
the cost.

### What was seen anyway — 4 script(s), 4 anchor(s)

**elysium-lib**

`negtest_weather.sh`

    STALE ANCHOR in /tmp/Elysium-mono/elysium-lib/src/main/java/com/elysium/lib/weather/ElysiumWeatherRules.java — stopping

`negtest_weather_a.sh`

    STALE ANCHOR in /tmp/Elysium-mono/elysium-lib/src/main/java/com/elysium/lib/weather/ElysiumWeatherRules.java — stopping

`negtest_weather_b.sh`

    STALE ANCHOR in /tmp/Elysium-mono/elysium-space/src/main/java/com/elysium/space/world/SpaceWeather.java — stopping

**elysium-space**

`negtest_gates.sh`

    STALE ANCHOR — stopping

`negtest_weather.sh`

    STALE ANCHOR in /tmp/Elysium-mono/elysium-lib/src/main/java/com/elysium/lib/weather/ElysiumWeatherRules.java — stopping

`negtest_weather_a.sh`

    STALE ANCHOR in /tmp/Elysium-mono/elysium-lib/src/main/java/com/elysium/lib/weather/ElysiumWeatherRules.java — stopping

`negtest_weather_b.sh`

    STALE ANCHOR in /tmp/Elysium-mono/elysium-space/src/main/java/com/elysium/space/world/SpaceWeather.java — stopping


### Seen on 2 October, through the net — 3 script(s), 7 anchor(s)

The nine were dry-run again with the dry copy inside a throwaway worktree,
and the real tree was byte-identical before and after every one. Six of them
— `negtest_freight`, `negtest_gates`, `negtest_jewelry`, `negtest_weather`,
`negtest_weather_a`, `negtest_weather_b` — report no stale anchor at all:
their `edit()` is neutered now, so every anchor is checked against an
unmodified file, and the `STALE ANCHOR — stopping` lines above were the 27
September run's own earlier edits getting in the way of its later anchors.
The other three make their edits for real (a `t()` whose python arrives as
`"$3"`), so a later anchor in the same file can be an earlier edit's doing;
`negtest_armour.sh` restores between cases, the other two do not. Repaired on
2 October — see the end of this file.

**elysium-lib**

`negtest_armour.sh` (both in `ElysiumArmourHandler.java`)

    SETUP FAILED rules reach mobs    :: if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
    SETUP FAILED Breach ignored      :: EnchantmentHelper.modifyArmorEffectiveness(level, weapon, player, source, share)

`negtest_runes.sh`

    anchor: total += value.apply(socketed.rune()) * socketed.scale();   (ElysiumRune.java, "dodge ignores tier")
    anchor: RESISTANCE_MAX_LEVEL = 3;                                   (ElysiumRuneTiers.java, "Resistance cap lifted")

`negtest_shares.sh`

    anchor: ChatFormatting.DARK_AQUA, 1400.0F, UNCAPPED)                           (ElysiumStats.java, "Resilience capped at 90%")
    anchor: return com.elysium.lib.stats.ElysiumShares.fromOdds(stat * fraction);  (ElysiumResistance.java, "gear hazard share linear and clamped")
    anchor: com.elysium.lib.stats.ElysiumShares.fromOdds(blunt)                    (ElysiumCombatHandler.java, "elemental blunt clamped at 90%")

The same pass also dry-ran `negtest_court.sh`, `negtest_harvest_audit.sh`,
`negtest_atelier.sh` and `negtest_comforts.sh`: no stale anchors, tree
unchanged. What the net absorbed meanwhile is worth knowing: `negtest_court.sh`
regenerates 24 textures through `elysium-npcs/tools/gen_village.py`, a path
the `tools/gen_` pattern does not match, and `negtest_runes.sh` and
`negtest_shares.sh` edit five and seven files for real. In place, all of that
would have landed in the tree.


### Repaired, 2 October — 5 script(s): 11 anchors repointed, 2 cases rewritten, 3 deleted

Each script was dry-run to no stale anchor and then run for real, alone on
the machine, after the repair. What a case tests is unchanged unless said.

**`negtest_colonies3.sh`** (elysium-colonies) — 8 stale: 5 repointed, 3 deleted

- `found(` is `foundNear` now; the case anchors its first statement,
  `BlockPos hall = town.centre();`, unique in the file and outliving the
  signature.
- `Charters.plan().next(` — the continuation line is indented 16, not 20.
- FrontierExchange: `cost` is `selling`; the same-item test is one of two
  conditions in its `if`, so the whole condition is the anchor; `spent` is
  `sold`, and the cap is tested twice in `settle()` while the validator wants
  the phrase anywhere in the body — the case now breaks both copies.
- ExchangeRules: `BASE_RATE`, the `budget` and the world-factor `rate` are
  gone. Pricing moved to `Market`/`MarketRules`, the cap into
  `FrontierExchange.settle()`, and the validator reads nothing in the file
  but `KEEP`. Three cases deleted: the flat-rate fault is
  `negtest_colonies5.sh`'s "an exchange trading at a flat rate", the cap is
  the exchange case above, and "nothing is ever worth producing" has no
  message any more.
- A validator gap the sweep found: "a fake player conjured into somebody
  else's colony" MISSED with a sound anchor. The check counted
  `FakePlayerFactory.get(` and the case conjures `getMinecraft(`. The count
  now matches every fake-player-returning overload
  (`FakePlayerFactory\.get(?:Minecraft)?\(`), same message; the colonies
  validator passes, and the case proves it below.
- Real run: 30 caught, 0 missed, tree restored clean (04:13, after the validator fix; the fake-player case among the caught)

**`negtest_tooltip.sh`** (elysium-lib) — 1 stale, repointed

- The `@SubscribeEvent` anchor depended on the previous edit's rename, which
  a dry run (edits neutered) reports as stale. Folded into one edit anchored
  on `@SubscribeEvent` + `onDeath(`.
- Not a stale anchor, but found by the first real run: the two lang-file
  cases read and wrote the JSON with the platform default encoding, which on
  Windows is cp1252 and cannot decode the file's UTF-8 — a Traceback, no
  pop, and a MISSED fault that was never injected. Both now say UTF-8.
- Real run: 19 caught, 0 missed, tree restored clean (06:26, after the UTF-8 fix; the first run had been 14 caught, 1 missed, stopped)

**`negtest_armour.sh`** (elysium-lib) — 2 stale, repointed

- The handler takes any `LivingEntity` as `defender`, and the validator wants
  `instanceof Player` gone from the whole file; it occurs three times, so
  the case replaces every copy (no count). `player` is `defender` in the
  `modifyArmorEffectiveness(` call.
- Real run: caught 5, missed 0 (04:57); the script restores per case and git shows elysium-lib clean after it

**`negtest_runes.sh`** (elysium-lib) — 2 stale, both real (not cascades): 1 repointed, 1 rewritten

- `sumOn()` sums odds, paced: the two-line statement is the anchor and the
  case drops `* socketed.scale()` from it.
- `RESISTANCE_MAX_LEVEL` is gone by design (no ceilings; the level is what
  the paced share buys and never reaches V). "Resistance cap lifted" is now
  "Resistance reaches level V": the level climbs linearly with tier, which
  `RuneTierHarness` forbids ("level V is all damage").
- Real run: baseline clean, caught 12, missed 0 (05:28); git shows elysium-lib clean after it

**`negtest_shares.sh`** (elysium-lib) — 3 stale, all real (not cascades), repointed

- The curve halfways are divided by `ElysiumShares.PACE`; `pieceShare()`'s
  return and the blunt call are two lines each and paced. All three anchor
  the whole statement; the injected faults are unchanged and the messages
  still exist ("has a ceiling of" in the validator, "removes more than 99%"
  in `HazardHarness`).
- Two more found by the first real run (8 caught, 2 missed), neither a
  repointed anchor: "hazard sources clamped at 95%" expected "something
  clamps a single source", a message the validator no longer prints — it
  now says "hazard protection clamps each source again", and the case
  expects that; "dodge summed as percentages" anchored the first
  `add(dodge,` in `onIncomingDamage()`, and a second one (the dodge_chance
  attribute) now precedes the player's statement the validator reads, so the
  case anchors that whole statement.
- Real run: baseline clean, caught 10, missed 0 (06:51, after the two repairs above; the first run was 8 caught, 2 missed); git shows elysium-lib clean after it


### Seen on 2 October, by the ratchet sweep — 3 script(s), 6 anchor(s): repaired the same day, see below the list

The dry runs that proved the last 27 ratchet conversions (space, ores, flora,
logistics, industry, core, atomics, agronomy, kinetics, almanac) showed these,
all pre-existing Java and data drift unrelated to the conversions (as first
read; the space one turned out to be the dry run's own doing):

* `negtest_atomics.sh` — 4 anchors in `AtomicRules.java` (`} else if (surplus > 0.0F) {`,
  `fromCells = Math.min(shortfall, ...`, `toCells = Math.min(surplus, ...`,
  `float carried = ...`): `settle()` now delegates to `IndustryRules.settle` and
  has none of those locals, so four cases inject nothing.
* `negtest_logistics.sh` — 1 anchor in `LogisticRules.java`:
  `return new IndustryRules.Machine(devices * DEVICE_DRAW,`; the line now reads
  `devices * DEVICE_DRAW + extra * DEVICE_DRAW,` (the pack extras of 29 Sep).
* `negtest_space.sh` — the case "silk touch gives cobblestone" sets up by editing
  `data/elysiumspace/loot_table/blocks/pale_stone.json`, which no longer exists
  under that name (the stones' drops were reworked on 29 Sep): `FileNotFoundError`
  before the fault is injected.

Repaired as the five above were — dry run to 0 stale, then one real run each,
alone on the machine (no other `validate.py` running). What a case tests is
unchanged unless said.

**`negtest_atomics.sh`** (elysium-atomics) — 4 stale: 3 repointed, 1 deleted

- The `Math.min`s and the `else if` the cases removed are Age II's now:
  `settle()` hands `reserveOf(cells, stored)` to `IndustryRules.settle` and
  reads `fromReserve`/`toReserve`/`burned` back, and the atomics validator's
  source check wants exactly that call. What AtomicRules still owns is the
  reserve it reports and the burn it reads, so the faults moved there: "a bank
  that hands over more than it holds" has `couldGive()` return
  `Float.MAX_VALUE` and "a bank that swallows a surplus it has no room for" has
  `couldTake()` do the same — the same effect as the old `fromCells = shortfall`
  / `toCells = surplus`, since Age II takes the min against what the reserve
  claims; "a wire made safe by putting a battery on it" anchors the whole
  two-line `return new Buffered(supply, demand, ...)` (the one-line
  `flow.grid().burned());` also occurs in `after()`) and masks the burn while
  charging (`burned() && !flow.charging()`). Same messages, all three.
- "charging and draining written so they could both happen" deleted: the
  exclusive branch is `IndustryRules.settle`'s, the atomics validator has never
  printed "not exclusive" (the phrase is `elysium-industry/validate.py`'s), and
  `negtest_industry.sh` ("lending and borrowing written so they could both
  happen") injects that fault where it lives.
- Real run: 31 caught, 0 missed, tree restored clean (07:47; the three repointed cases among the caught)

**`negtest_logistics.sh`** (elysium-logistics) — 1 stale, repointed

- The anchor now reads `devices * DEVICE_DRAW + extra * DEVICE_DRAW,`; the
  fault (a constant draw) and the message are unchanged.
- Real run: 23 caught, 0 missed, tree restored clean (08:03)

**`negtest_space.sh`** (elysium-space) — 0 stale: the finding was the dry run's

- `pale_stone.json` exists under that name and is the stone's loot table; no
  anchor had drifted. The `FileNotFoundError` was the preceding case, "loot
  table missing", which `os.rename`s that file: in a dry run `try()`'s python
  runs for real inside the copy while `restore()` and the trailing
  `; mv $LOOT.gone $LOOT` are neutered (`dryrun.py` rewrites `; mv …` to
  `; true`), so the file stayed gone for every later reader. The case now comes
  after "silk touch gives cobblestone", the last reader, with a comment saying
  why. A real run was never affected (restore puts the file back between cases).
- Dry run: no stale anchor; the copy's write list ends with `D pale_stone.json`
  + `?? pale_stone.json.gone`, as it now should; real tree unchanged.
- Real run: caught 38, missed 0 (08:23; both cases among the caught); git shows elysium-space and elysium-lib clean after it

## One more thing, while you are in there

Several scripts name their runner something other than `out()` or
`worldout()`, so the patterns in `dryrun.py` cannot neuter it. Since 2 October
that no longer costs a real validator run: inside the copy every validator
answers with the ok phrase at once, whatever the runner is called. A sweep of
the corpus now costs the copies — roughly a minute a script on this machine —
rather than the validators. Renaming the runner `out()` is still the tidier
thing to do.
