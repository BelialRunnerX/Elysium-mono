#!/usr/bin/env bash
# Building every block and item, broken one way at a time.
#
# The one check here that runs our own Java. Everything it reports is a crash
# the player would have had four seconds into loading, with no screen up and
# nothing but a log to go on.
set -uo pipefail
ROOT=/tmp/Elysium-mono
AB=$ROOT/elysium-atelier/src/main/java/com/elysium/atelier/registry/AtelierBlocks.java
AI=$ROOT/elysium-arsenal/src/main/java/com/elysium/arsenal/registry/ArsenalItems.java
BE=$ROOT/elysium-bestiary/src/main/java/com/elysium/bestiary/registry/BeastEntities.java
BI=$ROOT/elysium-bestiary/src/main/java/com/elysium/bestiary/registry/BeastItems.java
C=$ROOT/tools/construct/Construct.java
SNAP=/tmp/construct_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_construct.py elysium-atelier elysium-arsenal elysium-bestiary tools/construct)
restore() { rm -rf $ROOT/elysium-atelier $ROOT/elysium-arsenal $ROOT/elysium-bestiary $ROOT/tools/construct
  (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 900 python3 check_construct.py 2>&1); }
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
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the crash this check was written after"
edit $AB '            BlockSetType set = opening.soft() ? BlockSetType.OAK : HARD;' \
        '            BlockSetType set = opening.soft() ? BlockSetType.OAK : BlockSetType.STONE;'
expect "a stone door on a block set that opens by hand" "will not even load"

echo "things that throw where nobody is watching"
edit $AI '                made.put(id, ITEMS.register(id, () -> new WeaponItem(tier, tierName,
                        kind, new Item.Properties())));' \
        '                made.put(id, ITEMS.register(id, () -> new WeaponItem(tier, "mithril",
                        kind, new Item.Properties())));'
expect "a weapon built at a tier nobody has numbers for" "will not build"

edit $AI '        if (made.size() != WeaponRules.catalogue().size()) {' \
        '        if (made.size() == WeaponRules.catalogue().size()) {'
expect "a registry whose own count check is inverted" "will not even load"

# Not the spawn egg's entity lookup: that is a lazy supplier, and a lazy
# supplier is a thing this check genuinely cannot see. The count guard beside
# it runs at class initialisation, which it can.
edit $BI '        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            int background' '        for (BeastRules.Beast beast : BeastRules.family(BeastRules.BRUTE)) {
            int background'
expect "a bestiary with six of its nine spawn eggs" "will not even load"

edit $AB '            BlockBehaviour.Properties properties = like(opening.material()).noOcclusion();' \
        '            BlockBehaviour.Properties properties = null;'
expect "a block handed no properties at all" "will not build"

echo "the harness itself"
edit $C 'INTRUSIVE = Set.of(' 'INTRUSIVE_UNUSED = Set.of('
expect "a harness that will not compile" "the modules do not compile"

echo "nothing to build with, or nothing to build (15 Oct 2026)"
# Until 15 October each of these printed "construction: skipped" and exited 0,
# so a machine without the build environment passed every block and item
# without constructing one. Each must now FAIL and name what is missing. Every
# cp.txt below is written in a scratch folder handed over as ELYSIUM_MC_LIBS;
# the real one is never touched. All four fail before javac, so they cost
# seconds.
CPD=$(mktemp -d)
cp_out() { (cd $ROOT && ELYSIUM_MC_LIBS="$CPD" timeout 900 python3 check_construct.py 2>&1); }
failed() { local w="$2" g="$3"
  if [[ "$g" == *"FAILED"* && "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
cp_write() { python3 - "$CPD" "$1" "$ROOT/tools" <<'PYX'
import os, sys
folder, how, tools = sys.argv[1], sys.argv[2], sys.argv[3]
sys.path.insert(0, tools)
import mcpath
for name in os.listdir(folder):
    os.remove(os.path.join(folder, name))
if how == "none":
    sys.exit(0)
if how == "absent":
    text = "/nowhere/neoforge.jar:/nowhere/loader.jar"
elif how == "nolog4j":
    # The real classpath, with no libs/ folder beside it and so no log4j.
    real = mcpath.classpath()
    if not real:
        sys.exit("the real classpath is empty; this case needs it")
    text = os.pathsep.join(real)
open(os.path.join(folder, "cp.txt"), "w", encoding="utf-8", newline=chr(10)).write(text + chr(10))
PYX
}
cp_write none
failed "no cp.txt at all, which used to print skipped and pass" "there is no classpath file at" "$(cp_out)"
cp_write absent
failed "a cp.txt naming jars that are not here" "none of them exists here" "$(cp_out)"
cp_write nolog4j
failed "the game's classpath with no log4j beside it, which used to print skipped and pass" \
  "log4j-api-2.19.0.jar, log4j-core-2.19.0.jar, log4j-slf4j2-impl-2.19.0.jar are not in" "$(cp_out)"
rm -rf "$CPD"
edit $ROOT/check_construct.py 'REGISTRY = re.compile(r"DeferredRegister")' \
  'REGISTRY = re.compile(r"NothingIsRegisteredLikeThis")'
failed "no registry class found anywhere, which used to print skipped and pass" \
  "uses DeferredRegister, which cannot be right" "$(out)"

echo "a harness that exits 0 and counts nothing (15 Oct 2026)"
# Until 15 October a harness that exited 0 without its REGISTERS and BUILT
# lines passed as "? block(s), item(s) and type(s) built from ? register(s)",
# and one that counted 0 of each passed with the zeros printed. Both counts
# are the only evidence a supplier was called, so each must now FAIL. These
# two compile everything, like the cases above the classpath ones.
all_of() { local what="$1" g="$2" ok=1 w; shift 2
  [[ "$g" == *"FAILED"* ]] || ok=0
  for w in "$@"; do [[ "$g" == *"$w"* ]] || ok=0; done
  if [ "$ok" -eq 1 ]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; fail=$((fail+1)); fi; restore; }
edit $C '        System.out.println("REGISTERS " + registers);
        System.out.println("BUILT " + built);' \
  '        System.out.println("PASSES " + passes + ", and no count after it");'
all_of "a harness that prints neither count and exits 0, which used to pass as ?" "$(out)" \
  "the harness exited 0 without a REGISTERS line" "the harness exited 0 without a BUILT line"
edit $C '        System.out.println("REGISTERS " + registers);
        System.out.println("BUILT " + built);' \
  '        System.out.println("REGISTERS " + 0);
        System.out.println("BUILT " + 0);'
all_of "a harness that counts 0 registers and 0 built, which used to pass" "$(out)" \
  "the harness reports REGISTERS 0" "the harness reports BUILT 0"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"the way FML will build them"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "construction: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
