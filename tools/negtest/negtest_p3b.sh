#!/bin/bash
cd /tmp/Elysium-mono/elysium-armory || exit 2
export JAVA_TOOL_OPTIONS=
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_p3b
    python3 - "$1" "$2" "$3" <<'PY' || { echo "STALE ANCHOR: $4"; failn=$((failn+1)); return; }
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 600 python3 validate.py 2>&1)
    cp /tmp/nb_p3b "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
S=src/main/java/com/elysium/armory
t $S/registry/ArmoryItems.java 'case "elytra" -> new com.elysium.armory.item.ArmoryElytraItem(p);' 'case "elytra" -> new ArmoryGearItem(type, p);' "elytra is not built as a ArmoryElytraItem"
t $S/registry/ArmoryItems.java 'case "crossbow" -> new com.elysium.armory.item.ArmoryRangedItems.Crossbow(p);' 'case "crossbow" -> new ArmoryGearItem(type, p);' "crossbow is not built as a ArmoryRangedItems.Crossbow"
t $S/item/ArmoryBuilder.java 'DYED_COLOR' 'DYED_COLOUR_TYPO' "armour is never dyed"
t $S/event/ArmoryToolEvents.java 'case "saw" ->' 'case "sawz" ->' 'wide tools no longer answer for "saw"'
t $S/item/ArmoryGearLogic.java '"draw_speed"' '"draw_speeed"' "ranged gear does not speak through"
R=src/main/resources/data/elysiumarmory/recipe/spoon_upgrade.json
cp $R /tmp/nb_p3c; rm $R
out=$(timeout 600 python3 validate.py 2>&1); cp /tmp/nb_p3c $R
grep -q "upgrade spoon_upgrade has no recipe" <<<"$out" && { pass=$((pass+1)); echo "ok   : missing upgrade recipe"; } || { failn=$((failn+1)); echo "MISS : missing upgrade recipe"; }
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 600 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
