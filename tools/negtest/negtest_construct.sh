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
(cd $ROOT && tar cf $SNAP elysium-atelier elysium-arsenal elysium-bestiary tools/construct)
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

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"the way FML will build them"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "construction: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
