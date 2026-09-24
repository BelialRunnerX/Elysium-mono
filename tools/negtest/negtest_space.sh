#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_space.lock
flock -n 9 || { echo "another negtest_space is running; refusing"; exit 2; }
S=elysium-space; J=$S/src/main/java/com/elysium/space; R=$S/src/main/resources; D=$R/data/elysiumspace
W=$J/world/SpaceWorld.java; MAIN=$J/ElysiumSpace.java; TOML=$R/META-INF/neoforge.mods.toml
CRED=$S/CREDITS.md; SHIP=$R/CREDITS-elysiumspace.md
BIOME=$D/worldgen/biome/pale_moon_desert.json; BPL=$D/worldgen/biome/emerald_bastion_plains.json
NS=$D/worldgen/noise_settings/emerald_bastion.json; CONT=$D/worldgen/density_function/emerald_bastion/continents.json
EM=$D/worldgen/placed_feature/emerald_bastion_ore_emerald.json; LOOT=$D/loot_table/blocks/pale_stone.json
ROOTS=$R/assets/elysiumspace/models/block/emgrove_roots.json; LANG=$R/assets/elysiumspace/lang/en_us.json
HF=elysium-lib/src/main/java/com/elysium/lib/hazard/ElysiumHazardField.java
FILES="$W $MAIN $TOML $CRED $SHIP $BIOME $BPL $NS $CONT $EM $LOOT $ROOTS $LANG $HF"
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
try "loot table missing" space "has no loot table" "
import os; os.rename('$LOOT', '$LOOT.gone')
" ; mv $LOOT.gone $LOOT 2>/dev/null
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
echo "caught $pass, missed $miss"
