#!/usr/bin/env bash
# The Ledger Web, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-lib
T=$M/src/main/java/com/elysium/lib/tree
RULES=$T/ElysiumTreeRules.java
STATE=$T/ElysiumTreeState.java
STATS=$M/src/main/java/com/elysium/lib/stats/ElysiumStats.java
DOCTRINE=$M/src/main/java/com/elysium/lib/doctrine/ElysiumDoctrine.java
LANG_FILE=$M/src/main/resources/assets/elysiumlib/lang/en_us.json
SNAP=/tmp/web_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-lib/src/main/java/com/elysium/lib/tree elysium-lib/src/main/java/com/elysium/lib/stats elysium-lib/src/main/java/com/elysium/lib/doctrine elysium-lib/src/main/resources/assets/elysiumlib/lang)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 1800 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | grep -i 'web\|FAIL\|problem' | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the rules, through the harness"
edit $RULES '            new Start("marksman", "calling", "dimensional", "accuracy"));' '            new Start("marksman", "calling", "dimensional", "accuracy"), new Start("marksman_2", "calling", "dimensional", "accuracy"));'
expect "a tenth calling start, for a calling the pack does not have" "calling starts; the pack has nine callings"

edit $RULES '        s.add(new Subject("bench", RING_SYSTEMS, List.of("intellect", "accuracy", "luck"), "Master Artificer", "freehold"));' '        s.add(new Subject("bench", RING_SYSTEMS, List.of("intellect", "accuracy", "luck"), "Master Artificer", "bonded"));'
expect "a keystone hung off no cluster, and its pair on one cluster" "hangs off no cluster"

edit $T/ElysiumTree.java '    public static final double R_RING1 = 150;' '    public static final double R_RING1 = 100;'
expect "ring one drawn too tight to read" "which is one frame over another"

edit $RULES '    public static final int POINTS_PER_LEVEL = 3;' '    public static final int POINTS_PER_LEVEL = 9;'
expect "points a level that buy the whole web by mid-game" "fifty levels buy"

edit $RULES '        s.add(new Subject("cages", RING_FRONTIER, List.of("retribution", "fortitude", "strength"), "Cagewarden", null));' '        s.add(new Subject("cages", RING_FRONTIER, List.of("retribution", "fortitude", "strength"), "Cagewarden", "sanctioned"));'
expect "a frontier cluster hung with a keystone nothing reads" "the frontier cluster"

echo "the state's promises"
edit $STATE '        if (ElysiumTree.rootedWithin(after, starts(player)).size() != after.size()) {' '        if (false) {'
expect "a refund that no longer keeps every held node hanging off a start" "a given-back node no longer has to leave the rest hanging off a start"

edit $STATE '            player.addTag(ElysiumDoctrine.byId(doctrine).tag());' '            ;'
expect "a keystone that sets no doctrine tag" "a keystone taken sets no doctrine tag"

echo "the seams"
edit $STATS '                .plus(com.elysium.lib.tree.ElysiumTreeState.block(player));' ';'
expect "a stat sheet that no longer counts the web" "no longer adds the web's block"

edit $DOCTRINE '            {"bonded", "freehold"},' ''
expect "a keystone pair the doctrines no longer exclude" "differ, so a pair one side excludes the other allows"

edit $LANG_FILE '  "elysium.tree.notable.seam": "Master of the Seam",' '  "elysium.tree.notable.seam_gone": "Master of the Seam",'
expect "a notable with no words" 'the cluster "seam" has no words'

echo "restoring and re-checking"
restore
if out | grep -q "problem"; then echo "  RESTORE FAILED"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "web: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
