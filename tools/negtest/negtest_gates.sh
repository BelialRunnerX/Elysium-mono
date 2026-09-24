#!/usr/bin/env bash
# Gates that generate already working, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
BAK=/tmp/gates_bak
pass=0; fail=0
rm -rf $BAK && mkdir -p $BAK
keep() { local rel="${1#$ROOT/}"; mkdir -p "$BAK/$(dirname "$rel")"; [ -f "$BAK/$rel" ] || cp "$1" "$BAK/$rel" 2>/dev/null; }
restore() {
  local missed=0
  while read -r f; do cp -f "$BAK/$f" "$ROOT/$f" || missed=1; done < <(cd $BAK && find . -type f 2>/dev/null | sed 's|^\./||')
  [ "$missed" -eq 0 ] || { echo "  RESTORE ERROR"; fail=$((fail+1)); }
  rm -rf $BAK; mkdir -p $BAK
}
out() { (cd $ROOT/elysium-space && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
D=$ROOT/elysium-space/src/main/resources/data/elysiumspace
T=$ROOT/elysium-space/tools

edit() { keep "$1"; python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo '  STALE ANCHOR — stopping'; exit 2; }; }

regen() { (cd $ROOT/elysium-space && python3 tools/gen_waygate_structure.py >/dev/null); }

echo "the gate it generates"
keep $D/structure/waygate.nbt
edit $T/gen_waygate_structure.py 'block = ANCHOR if (across == 0 and up == 0) else FRAME' 'block = FRAME'
regen
expect "a gate generated with no anchor in it" "not a gate the game would accept"
regen

keep $D/structure/waygate.nbt
edit $T/gen_waygate_structure.py 'for across, up in ring(size):' 'for across, up in ring(size)[:4]:'
regen
expect "a ring with most of it missing" "not a gate the game would accept"
regen

keep $D/structure/waygate.nbt
edit $T/gen_waygate_structure.py '                gate.put(x, y, z, "minecraft:air")' '                gate.put(x, y, z, PAD)'
regen
expect "a gate generated solid, to be dug out" "filled in, so it generates broken"
regen

keep $D/structure/waygate.nbt
edit $T/gen_waygate_structure.py 'for x in range(width):
        for y in range(height):
            for z in range(depth):
                gate.put(x, y, z, "minecraft:air")' 'pass'
regen
expect "a template that leaves the hillside in place" "keeps the hill in its opening"
regen

echo "where and how often"
python3 - <<'PYEOF'
import json, os, shutil
p = "/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/worldgen/structure_set/waygate.json"
os.makedirs("/tmp/gates_bak/elysium-space/src/main/resources/data/elysiumspace/worldgen/structure_set", exist_ok=True)
shutil.copy(p, "/tmp/gates_bak/elysium-space/src/main/resources/data/elysiumspace/worldgen/structure_set/waygate.json")
d = json.load(open(p)); d["placement"]["spacing"] = 400; d["placement"]["separation"] = 200
open(p, "w").write(json.dumps(d, indent=2) + "\n")
PYEOF
expect "gates too rare to trip over" "rather than the ruined portal's 40/15"

python3 - <<'PYEOF'
import json, os, shutil
p = "/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/tags/worldgen/biome/has_structure/waygate_biomes.json"
os.makedirs("/tmp/gates_bak/elysium-space/src/main/resources/data/elysiumspace/tags/worldgen/biome/has_structure", exist_ok=True)
shutil.copy(p, "/tmp/gates_bak/elysium-space/src/main/resources/data/elysiumspace/tags/worldgen/biome/has_structure/waygate_biomes.json")
d = json.load(open(p)); d["values"] = [v for v in d["values"] if "frostfall" not in str(v)]
open(p, "w").write(json.dumps(d, indent=2) + "\n")
PYEOF
expect "a world you can only leave by rocket" "no way back except the rocket"

python3 - <<'PYEOF'
import json, os, shutil
p = "/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/tags/worldgen/biome/has_structure/waygate_biomes.json"
os.makedirs("/tmp/gates_bak/elysium-space/src/main/resources/data/elysiumspace/tags/worldgen/biome/has_structure", exist_ok=True)
shutil.copy(p, "/tmp/gates_bak/elysium-space/src/main/resources/data/elysiumspace/tags/worldgen/biome/has_structure/waygate_biomes.json")
d = json.load(open(p)); d["values"] = [v for v in d["values"] if v != "minecraft:plains"]
open(p, "w").write(json.dumps(d, indent=2) + "\n")
PYEOF
expect "nothing leading off the Overworld" "nothing leads off it"

keep $D/worldgen/structure/waygate.json; rm $D/worldgen/structure/waygate.json
expect "a template nothing places" "has no structure"

echo
echo "restoring and re-checking"
restore
regen
if (cd $ROOT/elysium-space && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "gates: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
