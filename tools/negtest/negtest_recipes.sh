#!/usr/bin/env bash
# The processing recipe layer, broken one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-solarpunk
G=tools/gen_solarpunk.py
R=src/main/resources/data/elysiumsolarpunk/recipe
J=src/main/java/com/elysium/solarpunk
pass=0; fail=0

out() { python3 validate.py 2>&1; }
expect() { local n="$1" w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }
regen() { python3 tools/gen_solarpunk.py >/dev/null 2>&1; }
snap() { cp -r src/main/resources/data/elysiumsolarpunk /tmp/dsnap; }
restore() { rm -rf src/main/resources/data/elysiumsolarpunk; cp -r /tmp/dsnap src/main/resources/data/elysiumsolarpunk; }

snap

echo "ids that do not resolve"
# The bug I actually made: a block tag used as an item ingredient.
cp $G /tmp/g.bak
edit $G '"input": f"#{NS}:pressable_fungus", "count": 4,' \
        '"input": "#minecraft:mushroom_grow_block", "count": 4,'
regen
expect "a block tag used as an item ingredient" "not a vanilla *item* tag"
cp /tmp/g.bak $G; regen

cp $G /tmp/g.bak
edit $G '"result": "minecraft:string", "yield": 3,' \
        '"result": "minecraft:twine", "yield": 3,'
regen
expect "a vanilla item that does not exist" "which is not a vanilla item"
cp /tmp/g.bak $G; regen

cp $G /tmp/g.bak
edit $G '"input": f"{NS}:plant_fibre", "count": 2,' \
        '"input": f"{NS}:plant_fibres", "count": 2,'
regen
expect "one of our own items misspelt" "which this mod does not register"
cp /tmp/g.bak $G; regen

echo "the numbers"
cp $G /tmp/g.bak
edit $G '"time": 160, "energy": 320,' '"time": 160, "energy": 0,'
regen
expect "a run that costs nothing" "turns one item into another for free"
cp /tmp/g.bak $G; regen

echo "the machine has to beat the hand recipe"
cp $G /tmp/g.bak
edit $G '"result": f"{NS}:woven_cane", "yield": 2,' \
        '"result": f"{NS}:woven_cane", "yield": 1,'
regen
expect "loom matches the hand recipe" "no reason to exist"
cp /tmp/g.bak $G; regen

echo "the chain has to close"
cp $G /tmp/g.bak
python3 -c "
p='tools/gen_solarpunk.py'
s=open(p).read()
i=s.index('PROCESSING = [')
j=s.index('def processing_recipes', i)
head,body,tail=s[:i],s[i:j],s[j:]
body=body.replace('f\"{NS}:plant_fibre\", \"yield\"', 'f\"{NS}:woven_cane\", \"yield\"')
open(p,'w').write(head+body+tail)
"
regen
expect "a machine-only item nothing makes" "has no source at all"
cp /tmp/g.bak $G; regen

echo "the type has to be registered"
cp $J/recipe/SolarpunkRecipes.java /tmp/sr.bak
edit $J/recipe/SolarpunkRecipes.java 'process("weaving")' 'process("weavingg")'
expect "a recipe type nothing registers" "the recipe will not load"
cp /tmp/sr.bak $J/recipe/SolarpunkRecipes.java

echo "a machine with an empty recipe book"
cp $G /tmp/g.bak
python3 - <<'PYEOF'
import re
p = "tools/gen_solarpunk.py"
s = open(p).read()
i = s.index("PROCESSING = [")
# The closing bracket is kept out of the split. The first version folded it
# into the last entry, so dropping a machine whose row happened to be last took
# the "]" with it and the generator stopped parsing — which looks like the
# check failing to fire rather than like a broken sabotage.
j = s.index("\n]\n", i)
head, body, tail = s[:i], s[i:j], s[j:]
entries = re.split(r"(?=\n    \{)", body)
kept = [e for e in entries if '"machine": "fibre_loom"' not in e]
open(p, "w").write(head + "".join(kept) + tail)
PYEOF
regen
expect "a working machine with no recipes" "working machine that does nothing"
cp /tmp/g.bak $G; regen

echo "an entity that does not read recipes"
cp $J/machine/FibreLoomEntity.java /tmp/fl.bak
edit $J/machine/FibreLoomEntity.java 'extends ProcessingMachineEntity' 'extends SolarpunkMachineEntity'
expect "recipes for a machine that cannot read them" "is not a ProcessingMachineEntity"
cp /tmp/fl.bak $J/machine/FibreLoomEntity.java

echo "the economics"
cp $G /tmp/g.bak
edit $G '"time": 160, "energy": 320,' '"time": 1, "energy": 320,'
regen
expect "a recipe no single generator can run" "can never run on one generator"
cp /tmp/g.bak $G; regen

# The smell that started this: every row at the same rate.
cp $G /tmp/g.bak
# Every machine at the same rate, not just one of them — with three machines a
# single flattened pair still leaves two distinct rates and the check is right
# not to fire.
python3 - <<'PYEOF'
import re
p = "tools/gen_solarpunk.py"
s = open(p).read()
i = s.index("PROCESSING = [")
j = s.index("\n]\n", i) + 3
body = s[i:j]
def flatten(match):
    time = int(match.group(1))
    return '"time": %d, "energy": %d,' % (time, time * 3)
body = re.sub(r'"time": (\d+), "energy": \d+,', flatten, body)
open(p, "w").write(s[:i] + body + s[j:])
PYEOF
expect "energy is a restatement of time" "restatement of \`time\`"
cp /tmp/g.bak $G; regen

restore
echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
