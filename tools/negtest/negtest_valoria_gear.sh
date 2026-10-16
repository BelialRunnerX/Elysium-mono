#!/bin/bash
# Valoria: gear in elysium-arsenal. Each line breaks one thing the roster-driven
# ladder promises and expects validate.py to say so.
cd /tmp/Elysium-mono/elysium-arsenal || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/arsenal
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_vg
    python3 - "$1" "$2" "$3" <<'PY' || { echo "STALE ANCHOR: $4"; failn=$((failn+1)); return; }
import sys; p,o,n=sys.argv[1:]; s=open(p,encoding="utf-8").read(); assert o in s, "pattern missing: "+o; open(p,'w',encoding="utf-8",newline="\n").write(s.replace(o,n,1))
PY
    out=$(timeout 1800 python3 validate.py 2>&1)
    cp /tmp/nb_vg "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/rules/WeaponRules.java 'public static final float RUNG_STEP = 1.15F;' 'public static final float RUNG_STEP = 1.05F;' "a step a reforge could be worth"
t $S/rules/WeaponRules.java 'new Cordon("cobalt", 3, 1750, 12.0F, 4.0F, 12,' 'new Cordon("cobalt", 3, 1750, 12.0F, 4.5F, 12,' "have stopped agreeing"
t $S/rules/WeaponRules.java 'new Cordon("jade", 5, 4112, 22.0F, 11.0F, 20, "c:gems/jade", "#4fbf6a"),' 'new Cordon("jade", 5, 4112, 22.0F, 11.0F, 20, "c:gems/jade", "#4fbf6b"),' "is not the colour of the ingot"
t $S/rules/WeaponRules.java 'new Cordon("nullsteel", 5, 5248, 30.0F, 17.0F, 20, "c:ingots/nullsteel", "#3a2a5a"));' 'new Cordon("nullsteel", 5, 5248, 30.0F, 17.0F, 20, "c:ingots/nullsteel", "#3a2a5a"),
            new Cordon("pearlium", 2, 425, 7.0F, 2.0F, 6, "c:ingots/pearlium", "#d9cfe0"));' "no Valoria set with tools stands on it"
t $S/rules/WeaponRules.java 'new Cordon("hemalite", 5, 2431, 24.0F, 15.0F, 15, "c:ingots/hemalite", "#a02a3a"),' '' "has no rung for it"
t $S/rules/WeaponRules.java 'made.add(new Rung(c.name(), beside, SIDE_HASTE, false,' 'made.add(new Rung(c.name(), beside, 1.0F, false,' "is not quicker"
t $S/registry/RangedItems.java 'if (rung.cordon()) {
                made.put(rung.name(), rung.uses());
            }' 'if (false) { made.put(rung.name(), rung.uses()); }' "have no durability"
t $S/registry/ArsenalItems.java 'if (made.size() != WeaponRules.RUNGS.size()) {' 'if (made.size() != 6 + WeaponRules.RUNGS.size()) {' "nothing counts the tiers"
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 1800 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
