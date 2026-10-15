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

echo "the classpath and the game's data (15 Oct 2026)"
# From late September the validator split cp.txt on ":" itself, read "C:/..."
# as two entries that do not exist, and printed "32 entries, not cross-checked"
# for the kiln and nothing at all for the recipes' vanilla ids and tags - and
# passed. Each way of having nothing to check against must now FAIL and name
# what is missing. Every cp.txt below is written in a scratch folder handed
# over as ELYSIUM_MC_LIBS, with MC_JAR unset; the real ones are never touched.
CPD=$(mktemp -d)
REAL_VANILLA=$(python3 - "$ROOT/tools" <<'PYX'
import os, sys
sys.path.insert(0, sys.argv[1])
import mcpath
for jar in (os.path.join(mcpath.libs(), "vanilla.jar"),
            os.path.join(mcpath.libs(), "libs", "vanilla-assets.jar"),
            os.environ.get("MC_JAR", "")):
    if jar and os.path.isfile(jar):
        print(jar)
        break
PYX
)
cp_out() { (cd $M && env -u MC_JAR ELYSIUM_MC_LIBS="$CPD" timeout 600 python3 validate.py 2>&1); }
cp_expect() { local what="$1" g ok=1 w; shift; g="$(cp_out)"
  [[ "$g" == *"PROBLEM(S)"* ]] || ok=0
  for w in "$@"; do [[ "$g" == *"$w"* ]] || ok=0; done
  if [ "$ok" -eq 1 ]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; fail=$((fail+1)); fi; restore; }
cp_write() { python3 - "$CPD" "$1" "$ROOT/tools" <<'PYX'
import os, re, sys, zipfile
folder, how, tools = sys.argv[1], sys.argv[2], sys.argv[3]
sys.path.insert(0, tools)
import mcpath
for name in os.listdir(folder):
    os.remove(os.path.join(folder, name))
if how == "none":
    sys.exit(0)
real = mcpath.classpath()
if not real and how != "absent":
    sys.exit("the real classpath is empty; this case needs it")


def vanilla(jar):
    try:
        with zipfile.ZipFile(jar) as archive:
            return "version.json" in archive.namelist()
    except Exception:
        return False


if how == "absent":
    text = "/nowhere/neoforge.jar:/nowhere/vanilla.jar"
elif how in ("nodata", "fakedata"):
    # The real classpath with every jar of vanilla's data taken off it, which
    # leaves NeoForge's jar and its 190 recipes of its own as the only data.
    text = os.pathsep.join(p for p in real if not vanilla(p))
    if how == "fakedata":
        with zipfile.ZipFile(os.path.join(folder, "vanilla.jar"), "w") as out:
            out.writestr("version.json", "{}")
elif how == "posix":
    text = ":".join("/" + p[0].lower() + p[2:] if re.match(r"^[A-Za-z]:", p) else p
                    for p in real)
elif how == "semicolon":
    text = ";".join(real)
open(os.path.join(folder, "cp.txt"), "w", encoding="utf-8", newline=chr(10)).write(text + chr(10))
PYX
}
cp_write none
cp_expect "no cp.txt and no vanilla jar, which used to print not cross-checked and pass" \
  "there is no classpath file at" "no jar with the game's own data"
cp_write absent
cp_expect "a cp.txt naming jars that are not here" "none of them exists here"
cp_write nodata
cp_expect "NeoForge's recipes the only data in reach, never mistaken for vanilla's" \
  "no jar with the game's own data"
cp_write fakedata
cp_expect "a vanilla.jar with none of the game's data in it" \
  "carries no assets/minecraft/models/item/" "no smelting recipe in the \`blocks\` category"

# The September bug itself: cp.txt split on ":" by hand. Written ";"-joined so
# the split is wrong on any OS, and mcpath (which the guard uses) still reads it.
cp_write semicolon
edit $V '_classpath = mcpath.classpath(_classpath_file)' \
  '_classpath = open(_classpath_file, encoding="utf-8").read().strip().split(":")'
cp_expect "the classpath split on \":\" by hand again, as from September to 15 October" \
  "no jar with the game's own data"

cp_write posix
g="$(cd $M && MC_JAR="$REAL_VANILLA" ELYSIUM_MC_LIBS="$CPD" timeout 600 python3 validate.py 2>&1)"
if [[ "$g" == *"all solarpunk checks passed"* && "$g" == *"derived from vanilla's \`blocks\` category"* \
      && "$g" == *"vanilla ids and tags against"* && "$g" != *"(0 vanilla ids"* ]]; then
  echo "  allowed: the colon-joined POSIX cp.txt, read through mcpath"; pass=$((pass+1))
else
  echo "  FALSE POSITIVE : the colon-joined POSIX cp.txt, read through mcpath"; fail=$((fail+1))
fi
restore
rm -rf "$CPD"

edit $V '            elif _vanilla_tags is not None:   # None has failed, naming the jar' \
  '            elif False:'
edit $V '            elif _vanilla_ids is not None:    # None has failed, naming the jar' \
  '            elif False:'
expect "the vanilla lookups unreachable and the line still saying every id resolved" \
  "not one vanilla id or tag among them looked up"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all solarpunk checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN (validator)"; fail=$((fail+1)); fi
rm -f $SNAP
echo "solarpunk: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
