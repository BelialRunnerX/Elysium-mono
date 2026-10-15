#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_space.lock
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
if command -v flock >/dev/null 2>&1; then negtest_lock 9 /tmp/negtest_space.lockdir || { echo "another negtest_space is running; refusing"; exit 2; }
else mkdir /tmp/negtest_space.lockdir 2>/dev/null || { echo "another negtest_space is running; refusing (rmdir /tmp/negtest_space.lockdir if not)"; exit 2; }; trap 'rmdir /tmp/negtest_space.lockdir 2>/dev/null' EXIT; fi
S=elysium-space; J=$S/src/main/java/com/elysium/space; R=$S/src/main/resources; D=$R/data/elysiumspace
W=$J/world/SpaceWorld.java; MAIN=$J/ElysiumSpace.java; TOML=$R/META-INF/neoforge.mods.toml
CRED=$S/CREDITS.md; SHIP=$R/CREDITS-elysiumspace.md
BIOME=$D/worldgen/biome/pale_moon_desert.json; BPL=$D/worldgen/biome/emerald_bastion_plains.json
NS=$D/worldgen/noise_settings/emerald_bastion.json; CONT=$D/worldgen/density_function/emerald_bastion/continents.json
EM=$D/worldgen/placed_feature/emerald_bastion_ore_emerald.json; LOOT=$D/loot_table/blocks/pale_stone.json
FLOOT=$D/loot_table/blocks/frost_stone.json; SMELT=$D/recipe/cinder_stone_from_cinder_cobblestone.json
PICK=$R/data/minecraft/tags/block/mineable/pickaxe.json; SB=$J/registry/SpaceBlocks.java
AXE=$R/data/minecraft/tags/block/mineable/axe.json; SF=$J/registry/SpaceFluids.java
ROOTS=$R/assets/elysiumspace/models/block/emgrove_roots.json; LANG=$R/assets/elysiumspace/lang/en_us.json
HF=elysium-lib/src/main/java/com/elysium/lib/hazard/ElysiumHazardField.java
FILES="$W $MAIN $TOML $CRED $SHIP $BIOME $BPL $NS $CONT $EM $LOOT $ROOTS $LANG $HF $FLOOT $SMELT $PICK $SB $AXE $SF"
pass=0; miss=0
snap()    { rm -rf /tmp/_ns; mkdir -p /tmp/_ns; i=0; for f in $FILES; do cp "$f" "/tmp/_ns/$i"; i=$((i+1)); done; }
restore() { i=0; for f in $FILES; do cp "/tmp/_ns/$i" "$f"; i=$((i+1)); done; }
for v in space lib; do timeout 1700 python3 elysium-$v/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED: $v not clean"; exit 2; }; done
echo "baseline clean"
try() { local label="$1" which="$2" want="$3" edit="$4"; snap
  if ! python3 -c "$edit"; then echo "SETUP FAILED: $label"; restore; return; fi
  if timeout 1700 python3 elysium-$which/validate.py 2>&1 | grep -qF "$want"; then echo "CAUGHT : $label"; pass=$((pass+1))
  else echo "MISSED : $label  (expected: $want)"; miss=$((miss+1)); fi
  restore; }
R='
import sys, json
def rep(path, old, new):
    s = open(path, encoding="utf-8").read()
    if old not in s: sys.exit("anchor not found in " + path + ": " + old[:60])
    open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
def jedit(path, fn):
    d = json.load(open(path)); fn(d); json.dump(d, open(path, "w"), indent=2)
'
try "architectury call" space "refers to" "$R
rep('$MAIN', 'LOGGER.info(\"Elysium Space ready\");', 'LOGGER.info(\"dev.architectury.platform.Platform\");')"
try "stellaris dependency" space "declares a dependency on" "$R
s=open('$TOML').read(); open('$TOML','w').write(s+'\n[[dependencies.\${mod_id}]]\n    modId=\"stellaris\"\n    type=\"optional\"\n')"
try "credit dropped" space "does not say 'Exodus Studio'" "$R
rep('$CRED', 'Exodus Studio', 'the authors')"
try "credit not shipped" space "CREDITS-elysiumspace.md is missing or differs" "$R
rep('$SHIP', 'Exodus Studio', 'Exodus')"
try "airless world breathable-ish" space "WorldHarness failed" "$R
rep('$W', 'of(VACUUM, 1.0F, CRYOGENIC, 0.6F)', 'of(VACUUM, 0.9F, CRYOGENIC, 0.6F)')"
try "paradise spawns monsters" space "WorldHarness failed" "$R
rep('$W', 'Air.BREATHABLE, of(), false)', 'Air.BREATHABLE, of(), true)')"
try "air clears heat" space "WorldHarness failed" "$R
rep('$W', 'return VACUUM.equals(hazardId) || (air == Air.TOXIC && CORROSIVE.equals(hazardId));', 'return VACUUM.equals(hazardId) || THERMAL.equals(hazardId) || (air == Air.TOXIC && CORROSIVE.equals(hazardId));')"
try "gravity modifier wrong" space "WorldHarness failed" "$R
rep('$W', 'return gravity - 1.0D;', 'return gravity;')"
try "world without dimension" space "the world table has" "$R
rep('$W', 'new SpaceWorld(\"emerald_bastion\"', 'new SpaceWorld(\"jupiter\", 2.5D, Air.NONE, of(VACUUM, 1.0F), true),\n            new SpaceWorld(\"emerald_bastion\"')"
try "stellaris id survives" space "still names" "$R
jedit('$BIOME', lambda d: d['spawners'].__setitem__('monster', [{'type':'stellaris:alien_zombie','weight':5,'minCount':1,'maxCount':1}]))"
try "dangling biome" space "which is neither a block nor a data file" "$R
rep('$BPL', 'elysiumspace:emgrove_grove', 'elysiumspace:emgrove_grovee')"
# A world's stone breaks as vanilla stone does (29 Sep): its cobblestone to a
# pickaxe, the stone to Silk Touch, a furnace back, and the pickaxe tag naming
# the stones and the ores. The first case is the table the stones had before.
try "stone drops itself again" space "frost_stone gives elysiumspace:frost_stone to a pickaxe without Silk Touch" "$R
json.dump({'type': 'minecraft:block', 'pools': [{'rolls': 1, 'bonus_rolls': 0, 'entries': [{'type': 'minecraft:item', 'name': 'elysiumspace:frost_stone'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]}, open('$FLOOT', 'w'), indent=2)"
try "silk touch gives cobblestone" space "pale_stone gives elysiumspace:pale_cobblestone to Silk Touch" "$R
jedit('$LOOT', lambda d: d['pools'][0]['entries'][0]['children'].reverse())"
# Last of the cases that read pale_stone's table, on purpose: in a dry run
# (tools/dryrun.py) try()'s python runs for real inside the copy while restore()
# and the mv below are neutered, so the rename would leave every later reader
# of the file with FileNotFoundError -- which is what the 2 October sweep saw.
try "loot table missing" space "has no loot table" "
import os; os.rename('$LOOT', '$LOOT.gone')
" ; mv $LOOT.gone $LOOT 2>/dev/null
try "cobblestone does not smelt back" space "no furnace recipe turns cinder_cobblestone back into cinder_stone" "
import os; os.remove('$SMELT')"
try "stone not pickaxe-mineable" space "frost_stone is in no mineable tag" "$R
jedit('$PICK', lambda d: d['values'].remove('elysiumspace:frost_stone'))"
try "ore not pickaxe-mineable" space "ore lunite_ore is in no mineable tag" "$R
jedit('$PICK', lambda d: d['values'].remove('elysiumspace:lunite_ore'))"
try "cobblestone without its stone" space "frost_cobblestone is registered but frost_stone is not" "$R
rep('$SB', 'BLOCKS.register(\"frost_stone\",', 'BLOCKS.register(\"frost_rock\",')"
# Every copy: each of the three cobblestones is registered once, and all three go.
try "no stone has a cobblestone" space "no world's stone has a cobblestone" "$R
s = open('$SB', encoding='utf-8').read(); open('$SB', 'w', encoding='utf-8').write(s.replace('_cobblestone\", Blocks.', '_cobbles\", Blocks.'))"
# Every block is in a mineable tag or exempt for a reason its registration
# bears out (29 Sep). The terminal and an ore leave the tag; then each way an
# exemption can be wrong: it needs a tool, it copies a block that does, it is
# in a tag after all, it names no block, and a liquid nobody can read.
try "terminal not mineable" space "freight_terminal is in no mineable tag, so it takes a fist" "$R
jedit('$PICK', lambda d: d['values'].remove('elysiumspace:freight_terminal'))"
try "ore in no mineable tag" space "cinder_gold_ore is in no mineable tag, so it takes a fist" "$R
jedit('$PICK', lambda d: d['values'].remove('elysiumspace:cinder_gold_ore'))"
try "exempt vine needs a tool" space "pale_vines is exempt from the mineable tags (it breaks at a touch, as cave vines do), but it asks for the right tool" "$R
rep('$SB', 'copy(Blocks.CAVE_VINES).lightLevel', 'copy(Blocks.CAVE_VINES).requiresCorrectToolForDrops().lightLevel')"
try "exempt propagule copies stone" space "emgrove_propagule is exempt from the mineable tags (it breaks at a touch, as a mangrove propagule does), but it copies Blocks.STONE" "$R
rep('$SB', 'copy(Blocks.MANGROVE_PROPAGULE)', 'copy(Blocks.STONE)')"
try "exempt vine in a tag" space "pale_vines is exempt from the mineable tags but is in axe" "$R
jedit('$AXE', lambda d: d['values'].append('elysiumspace:pale_vines'))"
try "exempt block not registered" space "emgrove_propagule is exempt from the mineable tags but is not registered" "$R
rep('$SB', 'BLOCKS.register(\"emgrove_propagule\",', 'BLOCKS.register(\"emgrove_sprout\",')"
try "liquid registration unread" space "oil is exempt from the mineable tags, but nothing here finds where it is registered" "$R
rep('$SF', 'BLOCKS.register(name,', 'BLOCKS.register(\"\" + name,')"
try "red emgrove" space "still points at" "$R
rep('$ROOTS', 'elysiumspace:block/emgrove_roots_side', 'block/mangrove_roots_side')"
try "block lang missing" space "has no lang entry" "$R
jedit('$LANG', lambda d: d.pop('block.elysiumspace.cinder_stone'))"
try "bastion monsters" space "spawns monsters" "$R
jedit('$BPL', lambda d: d['spawners'].__setitem__('monster', [{'type':'minecraft:zombie','weight':5,'minCount':1,'maxCount':1}]))"
try "bastion stone" space "ground is not deepslate" "$R
jedit('$NS', lambda d: d.__setitem__('default_block', {'Name':'minecraft:stone'}))"
try "bastion grass" space "the Bastion has deepslate, moss and mud" "$R
rep('$NS', 'minecraft:moss_block', 'minecraft:grass_block')"
try "emerald scarce" space "emerald is not the Bastion's most common ore" "$R
jedit('$EM', lambda d: [s.__setitem__('count', 2) for s in d['placement'] if s.get('type')=='minecraft:count'])"
try "bastion oceans" space "below -0.19 is ocean" "$R
jedit('$CONT', lambda d: d['argument']['argument2'].__setitem__('argument1', 0.6))"
try "bastion router overworld" space "still reads the overworld's continents" "$R
jedit('$NS', lambda d: d['noise_router'].__setitem__('continents', 'minecraft:overworld/continents'))"
try "sources ignored" lib "does not take the worst of the built-in rules" "$R
rep('$HF', 'return Math.max(builtIn(level, pos, hazard), fromSources(level, pos, hazard));', 'return builtIn(level, pos, hazard);')"
try "sources summed" lib "adds sources together" "$R
rep('$HF', 'worst = Math.max(worst, unit(entry.getValue().intensity(level, pos, hazard)));', 'worst += unit(entry.getValue().intensity(level, pos, hazard));')"
try "source crash spreads" lib "does not isolate a failing source" "$R
rep('$HF', '} catch (RuntimeException | LinkageError e) {\n                if (BROKEN', '} catch (StackOverflowError e) {\n                if (BROKEN')"
try "unit lets NaN through" lib "hazards harness failed" "$R
rep('$HF', 'if (!(value > 0.0F)) {', 'if (value < 0.0F) {')"
# Nothing to bake the mobs with, nothing to rebuild the generated files from
# (15 Oct 2026). Until 15 October each of these four printed "skipped" and the
# module passed with not one mob baked or not one file rebuilt. Each must now
# FAIL and name what is missing. Two runs, two cases each: the inputs are
# pointed at an empty folder, or at a file that is not there, for one run; the
# real classpath, build, Stellaris and jar are never touched.
env_cases() { local g; g="$(env $1 timeout 1700 python3 elysium-space/validate.py 2>&1)"; shift
  while [ $# -ge 2 ]; do
    if [[ "$g" == *"PROBLEM(S)"* && "$g" == *"$2"* ]]; then echo "CAUGHT : $1"; pass=$((pass+1))
    else echo "MISSED : $1  (expected: $2)"; miss=$((miss+1)); fi
    shift 2
  done; }
NOWHERE=$(mktemp -d)
env_cases "ELYSIUM_MC_LIBS=$NOWHERE STELLARIS=$NOWHERE" \
  "no cp.txt, which used to print geometry skipped and pass" \
  "mob geometry cannot be baked: there is no classpath file at" \
  "no Stellaris data, which used to print generated skipped and pass" \
  "the generated files cannot be rebuilt to compare with the shipped ones: there is no Stellaris data at"
env_cases "ELYSIUM_OUT=$NOWHERE MC_JAR=$NOWHERE/vanilla-assets.jar" \
  "no compiled space classes, which used to print geometry skipped and pass" \
  "mob geometry cannot be baked: the space module's compiled classes are not at" \
  "no game jar, which used to print generated skipped and pass" \
  "the generated files cannot be rebuilt to compare with the shipped ones: no game jar here"
rmdir "$NOWHERE"
echo "caught $pass, missed $miss"
