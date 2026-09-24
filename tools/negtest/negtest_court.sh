#!/usr/bin/env bash
# The court's five wardrobes, broken one way at a time.
set -uo pipefail
M=/tmp/Elysium-mono; N=$M/elysium-npcs
G=$N/tools/gen_npcs.py
MD=$N/src/main/java/com/elysium/npcs/client/model/EnvoyModel.java
pass=0; fail=0
regen() { (cd $M && python3 elysium-npcs/tools/gen_npcs.py >/dev/null 2>&1; python3 elysium-npcs/tools/gen_village.py >/dev/null 2>&1); }
expect() { local w="$2" g; g="$(cd $N && python3 validate.py 2>&1)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1)); else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read(); assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))" "$@"; }

echo "the wardrobes"
cp $MD /tmp/em.bak
edit $MD 'show(this.queenParts, kind == EnvoyKind.QUEEN);' 'show(this.queenParts, true);'
expect "one envoy wearing everybody's costume" "wears everybody's costume at once"
cp /tmp/em.bak $MD

cp $MD /tmp/em.bak
edit $MD '    private final ModelPart[] sentinelParts;' '    private final ModelPart[] absentParts;'
edit $MD '        this.sentinelParts = new ModelPart[] {' '        this.absentParts = new ModelPart[] {'
edit $MD '        show(this.sentinelParts, kind == EnvoyKind.SENTINEL);' '        show(this.absentParts, kind == EnvoyKind.SENTINEL);'
expect "an envoy with no wardrobe at all" "renders as a bare body"
cp /tmp/em.bak $MD

cp $MD /tmp/em.bak
edit $MD '    private static void show(ModelPart[] parts, boolean visible) {' '    private static void hide(ModelPart[] parts, boolean visible) {'
expect "nothing to hide the other four" "no show() helper"
cp /tmp/em.bak $MD

echo "the costumes themselves"
cp $G /tmp/gn.bak
edit $G '    rows += costume(kind, p)' '    rows += []'
regen
expect "every envoy stripped to the bare body" "renders as a bare body"
cp /tmp/gn.bak $G
regen

echo
if (cd $M && python3 validate.py >/dev/null 2>&1); then echo "  tree restored clean"; else echo "  RESTORE FAILED"; fail=$((fail+1)); fi
echo "court: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
