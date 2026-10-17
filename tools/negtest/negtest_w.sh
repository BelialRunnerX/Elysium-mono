set -u
cd /tmp/Elysium-mono
BK=/tmp/w-backup; rm -rf $BK; mkdir -p $BK
pass=0; fail=0
check () {
  out=$(cd elysium-solarpunk && python3 -W ignore validate.py 2>&1)
  if echo "$out" | grep -q "$2"; then echo "  CAUGHT  $1"; pass=$((pass+1));
  else echo "  MISSED  $1 (wanted: $2)"; echo "$out" | tail -3 | sed 's/^/          /'; fail=$((fail+1)); fi
}
save(){ mkdir -p "$BK/$(dirname "$1")"; cp "$1" "$BK/$1"; }
restore(){ cp "$BK/$1" "$1"; rm -rf elysium-solarpunk/tools/__pycache__; }
R=elysium-solarpunk/src/main/resources

# Everything tools/gen_solarpunk.py owns (main() deletes these and writes them
# again), kept before any case runs it and put back byte for byte after each
# case that did (17 Oct 2026). Cases 4 and 5 used to put it back by running the
# generator again on the restored script, and that left all 82 textures
# modified: the Pillow on this machine writes the same pixels as other bytes
# than the PNGs in the repository were written with.
J=elysium-solarpunk/src/main/java/com/elysium/solarpunk
tar cf "$BK/gen.tar" $R/assets/elysiumsolarpunk $R/data $J/SolarpunkContent.java $J/block/pending elysium-solarpunk/MACHINES.md
putgen(){ rm -rf $R/assets/elysiumsolarpunk $R/data $J/block/pending; tar xf "$BK/gen.tar"; }

echo "1. a biome modifier points at a placed feature that is not there"
save $R/data/elysiumsolarpunk/neoforge/biome_modifier/add_alabaster.json
python3 - <<'PY'
import json,pathlib
p=pathlib.Path("elysium-solarpunk/src/main/resources/data/elysiumsolarpunk/neoforge/biome_modifier/add_alabaster.json")
d=json.loads(p.read_text()); d["features"]="elysiumsolarpunk:alabaster_seam"; p.write_text(json.dumps(d,indent=2))
PY
check "dangling placed feature" "not a placed feature this mod ships"
restore $R/data/elysiumsolarpunk/neoforge/biome_modifier/add_alabaster.json

echo "2. a configured feature places a block nobody registered"
save $R/data/elysiumsolarpunk/worldgen/configured_feature/white_marble.json
python3 - <<'PY'
import json,pathlib
p=pathlib.Path("elysium-solarpunk/src/main/resources/data/elysiumsolarpunk/worldgen/configured_feature/white_marble.json")
d=json.loads(p.read_text())
for tgt in d["config"]["targets"]: tgt["state"]["Name"]="elysiumsolarpunk:white_granite"
p.write_text(json.dumps(d,indent=2))
PY
check "feature places an unregistered block" "which this mod does not register"
restore $R/data/elysiumsolarpunk/worldgen/configured_feature/white_marble.json

echo "3. a mined stone loses its worldgen entirely"
save $R/data/elysiumsolarpunk/neoforge/biome_modifier/add_white_limestone.json
rm $R/data/elysiumsolarpunk/neoforge/biome_modifier/add_white_limestone.json
check "stone with no way in" "does not generate in any world"
restore $R/data/elysiumsolarpunk/neoforge/biome_modifier/add_white_limestone.json

echo "4. the trim overlay stops drawing"
save elysium-solarpunk/tools/gen_solarpunk.py
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-solarpunk/tools/gen_solarpunk.py"); t=p.read_text()
t=t.replace("        if \"overlay\" in entry:\n            pattern, metal = entry[\"overlay\"]",
            "        if False and \"overlay\" in entry:\n            pattern, metal = entry[\"overlay\"]",1)
p.write_text(t)
PY
python3 elysium-solarpunk/tools/gen_solarpunk.py >/dev/null 2>&1
check "trim with no trim on it" "pixel-identical to"
restore elysium-solarpunk/tools/gen_solarpunk.py
putgen

echo "5. the overlay draws in the stone instead of the metal"
save elysium-solarpunk/tools/gen_solarpunk.py
python3 - <<'PY'
import pathlib
p=pathlib.Path("elysium-solarpunk/tools/gen_solarpunk.py"); t=p.read_text()
t=t.replace("            tile = OVERLAYS[pattern](tile, PALETTE[metal], seed_of(entry[\"id\"]))",
            "            tile = OVERLAYS[pattern](tile, PALETTE[\"soil\"], seed_of(entry[\"id\"]))",1)
p.write_text(t)
PY
python3 elysium-solarpunk/tools/gen_solarpunk.py >/dev/null 2>&1
check "trim drawn in the wrong palette" "it was not the metal in its name"
restore elysium-solarpunk/tools/gen_solarpunk.py
putgen

echo "6. a generated stone drops nothing"
save $R/data/elysiumsolarpunk/loot_table/blocks/alabaster.json
rm $R/data/elysiumsolarpunk/loot_table/blocks/alabaster.json
check "generated stone with no loot" "mined into nothing"
restore $R/data/elysiumsolarpunk/loot_table/blocks/alabaster.json

echo
(cd elysium-solarpunk && python3 -W ignore validate.py >/dev/null 2>&1) && echo "  restored tree passes" || { echo "  RESTORE FAILED"; fail=$((fail+1)); }
echo; echo "$pass caught, $fail missed"
