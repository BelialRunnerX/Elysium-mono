#!/bin/bash
# The fourth age's recovery and the Cordon's rows in the chain. Each line
# breaks one thing the section promises and expects validate.py to say so.
cd /tmp/Elysium-mono/elysium-foundry || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/foundry
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_fr
    python3 - "$1" "$2" "$3" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p,encoding="utf-8").read(); assert o in s, "pattern missing: "+o; open(p,'w',encoding="utf-8",newline="\n").write(s.replace(o,n,1))
PY
    out=$(timeout 900 python3 validate.py 2>&1)
    cp /tmp/nb_fr "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/rules/FoundryRules.java 'made.put("cobalt", "nickel");' 'made.put("cobalt", "cobalt");' "is its own companion"
t $S/rules/FoundryRules.java 'RECOVERY_NUGGETS = 3;' 'RECOVERY_NUGGETS = 9;' "a whole unit or more"
t $S/rules/FoundryRules.java 'made.put("pearlium", "silver");' 'made.put("pearlium", "silver");
        made.put("steel", "nickel");' "a recipe can make and loop"
t tools/gen_foundry.py '"cobalt": "nickel", "pearlium": "silver",' '"cobalt": "silver", "pearlium": "silver",' "companions differ between the rules and the generator"
t $S/rules/FoundryRules.java 'alloy("nightgold", 1, "gold", UNIT, "cobalt", UNIT),' 'alloy("nightgold", 1, "gold", 2 * UNIT, "cobalt", UNIT),' "price depends on which file you read"
t ../docs/SPEC_FOUNDRY.md '| nightgold | 1 | 18 |' '| nightgold | 1 | 27 |' "SPEC_FOUNDRY.md says nightgold"
t $S/block/RefineryBlock.java 'items.extractItem(IN, 1, false);' 'items.extractItem(IN, 0, false);' "never consumes the ore"
t $S/block/RefineryBlock.java 'return slot == IN;' 'return true;' "can push into the refinery"
t $S/registry/FoundryRecipes.java 'TYPES.register("refining",' 'TYPES.register("refining2",' "refining recipe type"
t $S/registry/FoundryItems.java '"bathysteel", "hemalite", "nullsteel");' '"bathysteel", "hemalite");' "Cordon's metals differ"
echo "$pass passed, $failn missed"
