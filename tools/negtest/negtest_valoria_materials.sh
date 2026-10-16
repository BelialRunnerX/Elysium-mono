#!/bin/bash
# Valoria: materials in elysium-ores. Each line breaks one thing the section
# promises and expects validate.py to say so.
cd /tmp/Elysium-mono/elysium-ores || exit 2
export JAVA_TOOL_OPTIONS=
S=src/main/java/com/elysium/ores
pass=0; failn=0
t() {
    cp "$1" /tmp/nb_vm
    python3 - "$1" "$2" "$3" <<'PY' || { echo "STALE ANCHOR: $4"; failn=$((failn+1)); return; }
import sys; p,o,n=sys.argv[1:]; s=open(p,encoding="utf-8").read(); assert o in s, "pattern missing: "+o; open(p,'w',encoding="utf-8",newline="\n").write(s.replace(o,n,1))
PY
    out=$(timeout 900 python3 validate.py 2>&1)
    cp /tmp/nb_vm "$1"
    if grep -q "$4" <<<"$out"; then pass=$((pass+1)); echo "ok   : $4"; else failn=$((failn+1)); echo "MISS : $4"; fi
}
t $S/rules/CordonMaterials.java 'new Set("marsh", "mireweave", false, true),' 'new Set("marsh", "mireweave", false, true),
            new Set("holiday", "mireweave", true, false),' "gear sets and the roster carries"
t $S/rules/CordonMaterials.java 'new Set("jade", "jade", true, false),' 'new Set("jade", "jadeite", true, false),' "is not a material"
t $S/rules/CordonMaterials.java 'new Set("black_gold", "nightgold", false, true),' 'new Set("black_gold", "nightgold", true, true),' "has no tool tier"
t $S/rules/CordonMaterials.java '"cobalt_ingot", 1, "ember_slag", 1)' '"cobalt_ingot", 1, "ember_slag", 1, "lattice", 1)' "which is not below it"
t $S/rules/CordonMaterials.java 'new Drop("relic_shard", Element.KINETIC, "#c0b080", "Project Remains and the Containment Vaults"));' 'new Drop("relic_shard", Element.KINETIC, "#c0b080", "Project Remains and the Containment Vaults"),
            new Drop("loom_fang", Element.NEURAL, "#e0e0d0", "the Loom Spider"));' "nothing here is made of it"
t $S/rules/CordonMaterials.java 'import java.util.ArrayList;' 'import java.util.ArrayList;
import net.minecraft.world.item.Item;' "the JDK and"
t $S/registry/MaterialItems.java 'for (String name : CordonMaterials.items()) {' 'for (String name : java.util.List.of("cobalt_ingot")) {' "second list"
t $S/ElysiumOres.java 'com.elysium.ores.registry.MaterialItems.register(modEventBus);' '// no items' "never calls"
F=src/main/resources/data/elysiumores/recipe/nullsteel_ingot.json
cp $F /tmp/nb_vm_b; rm $F
out=$(timeout 900 python3 validate.py 2>&1); cp /tmp/nb_vm_b $F
grep -q "alloy with no recipe" <<<"$out" && { pass=$((pass+1)); echo "ok   : missing alloy recipe"; } || { failn=$((failn+1)); echo "MISS : missing alloy recipe"; }
F=src/main/resources/assets/elysiumores/textures/item/jade.png
cp $F /tmp/nb_vm_c; cp src/main/resources/assets/elysiumores/textures/item/ruby.png $F
out=$(timeout 900 python3 validate.py 2>&1); cp /tmp/nb_vm_c $F
grep -q "not what tools/gen_material_art.py draws" <<<"$out" && { pass=$((pass+1)); echo "ok   : hand-swapped sprite"; } || { failn=$((failn+1)); echo "MISS : hand-swapped sprite"; }
# The tree the cases leave must pass its validator: a case is "caught" just as
# well off a break the tree had before the first one (16 Oct: run on a snapshot
# taken half way through negtest_p2.sh, whose iron.json was still broken, the
# first case passed that way and nothing looked again).
if timeout 900 python3 validate.py >/dev/null 2>&1; then echo "tree restored clean"
else echo "RESTORE FAILED: validate.py does not pass on the tree the cases left"; failn=$((failn+1)); fi
echo "$pass passed, $failn missed"
[ "$failn" -eq 0 ]
