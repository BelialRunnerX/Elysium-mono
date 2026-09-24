set -u
cd /tmp/Elysium-mono
BK=/tmp/v-backup; rm -rf $BK; mkdir -p $BK
pass=0; fail=0
check () {
  out=$(cd elysium-npcs && python3 validate.py 2>&1)
  if echo "$out" | grep -q "$2"; then echo "  CAUGHT  $1"; pass=$((pass+1));
  else echo "  MISSED  $1 (wanted: $2)"; echo "$out" | tail -3 | sed 's/^/          /'; fail=$((fail+1)); fi
}
save(){ mkdir -p "$BK/$(dirname "$1")"; cp "$1" "$BK/$1"; }
restore(){ cp "$BK/$1" "$1"; rm -rf elysium-npcs/tools/__pycache__; }
S=elysium-npcs/src/main/java/com/elysium/npcs/village/ElysiumStations.java
T=elysium-npcs/src/main/java/com/elysium/npcs/village/ElysiumVillagerTrades.java
R=elysium-npcs/src/main/resources

echo "1. the POI stops matching every block state"
save $S; sed -i 's/block.get().getStateDefinition().getPossibleStates()/java.util.List.of(block.get().defaultBlockState())/' $S
check "POI on the default state only" "not registered against every block state"
restore $S

echo "2. a profession loses its clothing"
save $R/assets/elysiumnpcs/textures/entity/villager/profession/splicer.png
rm $R/assets/elysiumnpcs/textures/entity/villager/profession/splicer.png
check "untextured villager" "renders untextured"
restore $R/assets/elysiumnpcs/textures/entity/villager/profession/splicer.png

echo "3. a profession loses its trades"
save $T; python3 - <<'PY'
import pathlib,re
p=pathlib.Path("elysium-npcs/src/main/java/com/elysium/npcs/village/ElysiumVillagerTrades.java")
t=p.read_text()
t=t.replace("if (profession == ElysiumStations.SALVAGER.profession().get()) {",
            "if (false) {",1)
p.write_text(t)
PY
check "profession with no trades" "offers nothing"
restore $T

echo "4. a bench loses its recipe"
save $R/data/elysiumnpcs/recipe/void_crucible.json
rm $R/data/elysiumnpcs/recipe/void_crucible.json
check "unobtainable bench" "unobtainable"
restore $R/data/elysiumnpcs/recipe/void_crucible.json

echo "5. a trade names an Elysium item"
save $T; sed -i 's/sell(Items.ANVIL, 1, 28, 2, 30)/sell(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("elysium:neutronium_ingot")), 1, 28, 2, 30)/' $T
check "trade names a modded item" "has to be a vanilla item"
restore $T

echo "6. the village lang keys are added by hand instead of a generator"
save $R/assets/elysiumnpcs/lang/en_us.json
python3 - <<'PY'
import json,pathlib
p=pathlib.Path("elysium-npcs/src/main/resources/assets/elysiumnpcs/lang/en_us.json")
d=json.loads(p.read_text()); d["entity.minecraft.villager.tinkerer"]="Tinkerer"
p.write_text(json.dumps(d,indent=2,sort_keys=True)+"\n")
PY
check "hand-edited lang" "does not match its generators"
restore $R/assets/elysiumnpcs/lang/en_us.json

echo
(cd elysium-npcs && python3 validate.py >/dev/null 2>&1) && echo "  restored tree passes" || { echo "  RESTORE FAILED"; fail=$((fail+1)); }
echo; echo "$pass caught, $fail missed"
