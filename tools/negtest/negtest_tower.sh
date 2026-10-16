#!/usr/bin/env bash
# The Tome Tower, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-enchanting
D=$M/src/main/resources/data/elysiumenchanting
SNAP=/tmp/tower_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-enchanting)
restore() { rm -rf $ROOT/elysium-enchanting && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
gen() { (cd $M && python3 tools/gen_tower.py >/dev/null); }
tedit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found'
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the building"
rm -f $D/structure/tome_tower_great.nbt
expect "a height that is named and not built" "is missing"

tedit $M/tools/gen_tower.py 'for y in range(1, floors * FLOOR_HEIGHT + 1):
        tower.put(1, y, 1, "minecraft:ladder"' 'for y in range(1, 3):
        tower.put(1, y, 1, "minecraft:ladder"'; gen
expect "a tower nobody can climb" "is a wall with books in it"

tedit $M/tools/gen_tower.py '                if floor > 0 and (x, z) == (1, 1):
                    continue                     # the ladder comes up here
                tower.put(x, y, z, "minecraft:spruce_planks")' \
'                if floor > 1 or ((x, z) == (1, 1) and floor > 0):
                    continue
                tower.put(x, y, z, "minecraft:spruce_planks")'; gen
expect "a tower with no floors above the first" "arrives in a shaft"

tedit $M/tools/gen_tower.py '    tower.put(0, 1, door_z, "minecraft:oak_door",' \
                            '    if False: tower.put(0, 1, door_z, "minecraft:oak_door",'; gen
expect "a tower with half a door" "where a door is two"

tedit $M/tools/gen_tower.py 'tower.put(WIDTH - 2, top + 1, WIDTH - 2, f"{NS}:arcane_table")' ''; gen
expect "a shelf room that cannot be used in" "cannot be used in"

tedit $M/tools/gen_tower.py 'tower.put(WIDTH - 2, 1, WIDTH - 2, "minecraft:chest",' \
                            'tower.put(WIDTH - 2, 1, WIDTH - 2, "minecraft:barrel",'; gen
expect "a tower with nothing in it to find" "nothing in it to find"

tedit $M/tools/gen_tower.py '("LootTable", string(f"{NS}:chests/tome_tower"))' \
                            '("CustomName", string("x"))'; gen
expect "a chest that generates empty" "generates empty"

tedit $M/tools/gen_tower.py 'height = floors * FLOOR_HEIGHT + BATTLEMENT + 1' \
                            'height = floors * FLOOR_HEIGHT + BATTLEMENT + 4'; gen
expect "a height changed without the floors" "one of the two was changed"

tedit $M/tools/gen_tower.py 'SHELVES = [f"{NS}:deepshelf", f"{NS}:sightshelf", f"{NS}:hellshelf",
           f"{NS}:endshelf", f"{NS}:keepers_shelf", f"{NS}:archive_column"]' \
'SHELVES = ["minecraft:bookshelf"]'; gen
expect "a tower that teaches a player nothing" "teaches a player nothing"

echo "the worldgen"
python3 -c "
import json
p='$D/worldgen/structure/tome_tower.json'
d=json.load(open(p)); d['step']='underground_structures'
json.dump(d,open(p,'w'),indent=2)"
expect "a surface structure buried in its own hill" "buried in the hill"

python3 -c "
import json
p='$D/worldgen/structure/tome_tower.json'
d=json.load(open(p)); d['start_pool']='minecraft:empty'
json.dump(d,open(p,'w'),indent=2)"
expect "a structure starting from somebody else's pool" "other than its own"

python3 -c "
import json
p='$D/worldgen/template_pool/tome_tower_start.json'
d=json.load(open(p)); d['elements']=d['elements'][:2]
json.dump(d,open(p,'w'),indent=2)"
expect "a template built and never placed" "built and never placed"

python3 -c "
import json
p='$D/worldgen/template_pool/tome_tower_start.json'
d=json.load(open(p)); d['elements'][0]['weight']=0
json.dump(d,open(p,'w'),indent=2)"
expect "a height weighted at nothing" "never appears"

python3 -c "
import json
p='$D/worldgen/structure_set/tome_tower.json'
d=json.load(open(p)); d['placement']['separation']=d['placement']['spacing']+1
json.dump(d,open(p,'w'),indent=2)"
expect "a spread the game refuses to load" "refuses to load"

python3 -c "
import json
p='$D/tags/worldgen/biome/has_structure/tome_tower.json'
d=json.load(open(p)); d['values']=[]
json.dump(d,open(p,'w'),indent=2)"
expect "a tower that can generate nowhere" "generate nowhere"

echo "what is in it"
python3 -c "
import json
p='$D/loot_table/chests/tome_tower.json'
s=json.dumps(json.load(open(p))).replace('elysiumenchanting:scrapping_tome','minecraft:book')
open(p,'w').write(s)"
expect "a Tome Tower missing one of the tomes" "without one of the tomes"

python3 -c "
import json
p='$D/loot_table/chests/tome_tower.json'
d=json.load(open(p)); d['pools'][0]['rolls']={'type':'minecraft:uniform','min':0,'max':1}
json.dump(d,open(p,'w'),indent=2)"
expect "a tower that might have no tome at the top" "know what is at the top"

echo
echo "restoring and re-checking"
restore
if (cd $M && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
echo "tome tower: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
