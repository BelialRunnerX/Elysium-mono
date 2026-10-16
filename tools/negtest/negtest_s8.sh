#!/bin/bash
cd /tmp/Elysium-mono/elysium-space || exit 2
export JAVA_TOOL_OPTIONS=
pass=0; failn=0
S=src/main/java/com/elysium/space
cp $S/machine/MachineBlock.java /tmp/nb1
python3 - <<'PY'
p='src/main/java/com/elysium/space/machine/MachineBlock.java';s=open(p).read()
s=s.replace("buf.writeVarInt(machine.tankList().size());\n                                buf.writeBoolean(machine.slots() != null);","buf.writeBoolean(machine.slots() != null);\n                                buf.writeVarInt(machine.tankList().size());")
open(p,'w').write(s)
PY
out=$(timeout 900 python3 validate.py 2>&1); cp /tmp/nb1 $S/machine/MachineBlock.java
grep -q "written as" <<<"$out" && { pass=$((pass+1)); echo "ok   : packet order"; } || { failn=$((failn+1)); echo "MISS : packet order"; }
T=src/main/resources/assets/elysiumspace/textures/block/frost_stone.png
cp $T /tmp/nb2; cp /tmp/ref/stellaris/assets/stellaris/textures/block/mars_stone.png $T
out=$(timeout 900 python3 validate.py 2>&1); cp /tmp/nb2 $T
grep -q "unchanged; the reskin must redraw it" <<<"$out" && { pass=$((pass+1)); echo "ok   : unreskinned texture"; } || { failn=$((failn+1)); echo "MISS : unreskinned texture"; }
P=tools/port_mobs.py
cp $P /tmp/nb3; sed -i 's/"frost_raptor": "frostfall"/"frost_raptor": "cinderveil"/' $P
out=$(timeout 900 python3 validate.py 2>&1); cp /tmp/nb3 $P
grep -q "another world's palette" <<<"$out" && { pass=$((pass+1)); echo "ok   : mob palette"; } || { failn=$((failn+1)); echo "MISS : mob palette"; }
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 900 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
