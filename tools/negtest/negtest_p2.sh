#!/bin/bash
cd /tmp/Elysium-mono/elysium-armory || exit 2
export JAVA_TOOL_OPTIONS=
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_p2
    python3 - "$1" "$2" "$3" <<'PY' || { echo "STALE ANCHOR: $4"; failn=$((failn+1)); return; }
import sys; p,o,n=sys.argv[1:]; s=open(p).read(); assert o in s, "pattern missing: "+o; open(p,'w').write(s.replace(o,n,1))
PY
    out=$(timeout 600 python3 validate.py 2>&1)
    cp /tmp/nb_p2 "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
M=src/main/resources/data/elysiumarmory/elysium_materials
P=src/main/resources/data/elysiumarmory/elysium_parts
t $M/iron.json '"harvest_speed"' '"harvest_speeed"' "no such property"
t $M/iron.json '"durability": 250.0' '"durability": 25.0' "generates from the inventory"
t $M/wool/red.json '"parent": "elysiumarmory:wool"' '"parent": "elysiumarmory:silk_but_not"' "which no pack defines"
t $P/sword_blade.json '"part_type": "main"' '"part_type": "handle"' "no such part type"
t src/main/java/com/elysium/armory/property/ArmoryTier.java '{"iron", 2.0D, IRON}' '{"iron", 0.5D, IRON}' "copper does not sit between stone and iron"
t src/main/java/com/elysium/armory/property/ArmoryVanilla.java 'case "chestplate" -> 16.0D;' 'case "chestplate" -> 6.0D;' "armour durability is not the slot multiplier"
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 600 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
