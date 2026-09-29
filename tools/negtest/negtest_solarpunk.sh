#!/usr/bin/env bash
# What mines a solarpunk block, broken one way at a time (the harvest audit,
# 29 September 2026).
#
# A block breaks at the speed of the tool the game lists it under, and one in
# no tool's list breaks at a fist's pace whatever a player holds. Which tool is
# for which block is rules/HarvestRules.java, read by the generator; the glass
# is the one block broken by hand, with its reason in the validator. Each rule
# that holds that is defeated here once.
#
# A new text that comments a line out starts with a space: " // x", never
# "// x". Git Bash hands an argument that begins with two slashes to a native
# program with one of them taken off, so the line stays live and the case
# reads as missed.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-solarpunk
S=$M/src/main/java/com/elysium/solarpunk
HR=$S/rules/HarvestRules.java
CT=$S/SolarpunkContent.java
G=$M/tools/gen_solarpunk.py
V=$M/validate.py
T=$M/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
SNAP=/tmp/solarpunk_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-solarpunk)
restore() { rm -rf $ROOT/elysium-solarpunk && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; restore; exit 2; }; }
regen() { (cd $M && python3 tools/gen_solarpunk.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }

echo "what mines a block (the harvest audit, 29 Sep 2026)"
python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].remove("elysiumsolarpunk:rammed_earth")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "rammed earth in no tool's tag" "are in no mineable tag and not exempt"

edit $HR '            if (said == 0) {' '            if (false) {'
expect "the rule that finds a block no tool is decided for, disabled" "a block no tool is decided for is not reported"

edit $CT '            BLOCKS.register("bloom_glass", () ->
                    new Block(BlockBehaviour.Properties.of()' \
        '            BLOCKS.register("bloom_glass", () ->
                    new Block(BlockBehaviour.Properties.of().requiresCorrectToolForDrops()'
expect "the exempt glass asking for the right tool" "but it asks for the right tool: it says so where it is registered"

edit $CT '            BLOCKS.register("bloom_glass", () ->
                    new Block(BlockBehaviour.Properties.of()' \
        '            BLOCKS.register("bloom_glass", () ->
                    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)'
expect "the glass built on a game block nobody has looked up" "nothing here knows whether that asks for the right tool"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumsolarpunk:rammed_eart")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a pickaxe tag naming a block nobody registers" "so the game refuses the whole tag"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumsolarpunk:bloom_glass")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "the glass exempt from the tags and in one all the same" "is exempt from the mineable tags and in one all the same"

edit $HR '    public static final List<String> BY_HAND = List.of("bloom_glass");' \
        '    public static final List<String> BY_HAND = List.of("bloom_glass", "bloom_lantern");'
expect "the lantern broken by hand for no stated reason" "breaks bloom_lantern by hand and this gives no reason"

edit $HR '    public static final List<String> BY_HAND = List.of("bloom_glass");' \
        '    public static final List<String> BY_HAND = List.of();'
expect "a reason kept for an exemption the rules dropped" "HarvestRules no longer does"

edit $HR '            Map.entry("earth", "pickaxe"),' '            Map.entry("earth", "shovel"),'
expect "the rules moved rammed earth and the tag left where it was" "the tags are written from the rules by tools/gen_solarpunk.py"

edit $HR '            Map.entry("earth", "pickaxe"),' '            Map.entry("earth", "trowel"),'
expect "a material mined by a tool the game keeps no list for" "which is no tool the game keeps a list for"

edit $HR 'import java.util.Map;' 'import java.util.Map;
import net.minecraft.world.level.block.Block;'
expect "the rules reaching for the game" "the rules are the JDK and nothing else"

edit $HR '            Map.entry("earth", "pickaxe"),
' ''
expect "a block made of a material the rules do not decide a tool for" "HarvestRules does not say what mines that"

edit $V '    "bloom_glass": "glass breaks at a touch and asks for no tool, the game'"'"'s own glass is "' \
        '    "bloom_glass_": "glass breaks at a touch and asks for no tool, the game'"'"'s own glass is "'
expect "the glass exempt on nobody's say-so" "breaks bloom_glass by hand and this gives no reason"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all solarpunk checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN (validator)"; fail=$((fail+1)); fi
rm -f $SNAP
echo "solarpunk: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
