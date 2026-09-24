#!/usr/bin/env bash
# Thirty seeds, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
R=$M/src/main/java/com/elysium/agronomy/rules/SeedRules.java
SNAP=/tmp/seeds_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy elysium-foundry elysium-ores)
restore() { rm -rf $ROOT/elysium-agronomy $ROOT/elysium-foundry $ROOT/elysium-ores && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 700 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the rule that keeps the foundry standing"
edit $R 'mined("netherite", 5, "minecraft:netherite_scrap"));' 'mined("netherite", 5, "minecraft:netherite_scrap"),
            mined("steel", 5, "elysiumagronomy:steel_chunk"));'
expect "a seed that grows an alloy" "rather than something the ground gives you"

edit $R 'mined("tin", 1, "elysiumagronomy:tin_chunk"),' 'mined("unobtainium", 1, "elysiumagronomy:unobtainium_chunk"),'
expect "a seed for something nothing in the pack supplies" "rather than something the ground gives you"

edit $R '    public static final Set<String> THE_GAMES_OWN = Set.of(
            "coal", "redstone", "lapis", "quartz", "amethyst", "glowstone",
            "obsidian", "diamond", "emerald", "netherite");' '    public static final Set<String> THE_GAMES_OWN = Set.of();'
expect "the list of the game's own materials emptied" "declares nothing as the game"

echo "the ladder"
edit $R 'mined("netherite", 5, "minecraft:netherite_scrap"));' 'mined("netherite", 4, "minecraft:netherite_scrap"));'
expect "a rung of the ladder nothing grows at" "nobody ever waits"

edit $R 'mined("copper", 1, "minecraft:copper_ingot"),' 'mined("copper", 1, "c:ingots/copper"),'
expect "an essence that turns into a tag rather than an item" "does not load at all"

echo "the one table"
edit $M/src/main/java/com/elysium/agronomy/registry/AgronomyItems.java \
  'for (com.elysium.agronomy.rules.SeedRules.Seed seed
                : com.elysium.agronomy.rules.SeedRules.SEEDS) {
            SEEDS.put(seed.material(), seed.tier());
        }' \
  'SEEDS.put("coal", 1);
        SEEDS.put("iron", 2);'
expect "the registry keeping its own copy of the seed table" "no longer walks the seed table"

edit $M/tools/gen_agronomy.py 'SEED_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/SeedRules.java")' 'SEED_RULES = os.path.join(
    MOD, "src/main/java/com/elysium/agronomy/rules/NotTheSeedRules.java")'
expect "the generator reading the seed table from somewhere else" "will not run"

echo "the seam with the ores below"
python3 - <<'PYX'
import pathlib, shutil
p = pathlib.Path("/tmp/Elysium-mono/elysium-ores/src/main/resources/data/c/tags/block/ores")
shutil.move(str(p), str(p.parent / "ores_moved"))
PYX
expect "elysium-ores declaring no ore tags at all" "ask for ores nothing is known to fill"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "seeds: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
