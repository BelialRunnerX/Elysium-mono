#!/usr/bin/env bash
# The datapack check, against the four files that actually shipped broken.
set -uo pipefail
ROOT=/tmp/Elysium-mono
SNAP=/tmp/datapack_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_datapack.py \
    elysium-flora/src/main/resources/data/elysiumflora \
    elysium-enchanting/src/main/resources/data/elysiumenchanting/enchantment \
    elysium-space/src/main/resources/data/elysiumspace/worldgen)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 600 python3 check_datapack.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
reject() { local g; g="$(out)"
  if [[ "$g" == *"no tag written inside a list"* ]]; then echo "  allowed: $1"; pass=$((pass+1));
  else echo "  FALSE POSITIVE : $1"; fail=$((fail+1)); fi; restore; }

F=$ROOT/elysium-flora/src/main/resources/data/elysiumflora
E=$ROOT/elysium-enchanting/src/main/resources/data/elysiumenchanting/enchantment
S=$ROOT/elysium-space/src/main/resources/data/elysiumspace/worldgen

echo "the four that shipped"
python3 - <<'PY'
import json, pathlib, collections
p = pathlib.Path("/tmp/Elysium-mono/elysium-flora/src/main/resources/data/elysiumflora/neoforge/biome_modifier/emberwood.json")
d = json.loads(p.read_text(), object_pairs_hook=collections.OrderedDict)
d["biomes"] = ["#minecraft:is_nether", "elysiumspace:cinderveil_desert"]
p.write_text(json.dumps(d, indent=2) + "\n")
PY
expect "a tag written inside a list of biomes" "inside a list"

python3 - <<'PY'
import json, pathlib, collections
p = pathlib.Path("/tmp/Elysium-mono/elysium-enchanting/src/main/resources/data/elysiumenchanting/enchantment/maelstrom.json")
d = json.loads(p.read_text(), object_pairs_hook=collections.OrderedDict)
d["effects"]["minecraft:trident_spin_attack_strength"] = [
    {"effect": d["effects"]["minecraft:trident_spin_attack_strength"]}]
p.write_text(json.dumps(d, indent=2) + "\n")
PY
expect "a single-value effect written as a list" "is a single effect in the jar"

python3 - <<'PY'
import json, pathlib, collections
p = pathlib.Path("/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/worldgen/configured_feature/pale_moon_dripstone_cluster.json")
d = json.loads(p.read_text(), object_pairs_hook=collections.OrderedDict)
d["type"] = "elysiumspace:pale_moon_dripstone_cluster"
p.write_text(json.dumps(d, indent=2) + "\n")
PY
expect "a configured feature whose type names itself" "which is not a feature"

rm $S/placed_feature/emgrove.json $S/placed_feature/tall_emgrove.json
expect "a random_selector pointing at features with no placed form" "there is no worldgen/placed_feature by that name"

echo "the rest of the surface"
python3 - <<'PY'
import json, pathlib, collections
p = pathlib.Path("/tmp/Elysium-mono/elysium-enchanting/src/main/resources/data/elysiumenchanting/enchantment/maelstrom.json")
d = json.loads(p.read_text(), object_pairs_hook=collections.OrderedDict)
d["effects"]["minecraft:trident_spin_strength"] = d["effects"].pop("minecraft:trident_spin_attack_strength")
p.write_text(json.dumps(d, indent=2) + "\n")
PY
expect "an effect component that does not exist, silently ignored" "not an enchantment effect component"

python3 - <<'PY'
import json, pathlib, collections, glob
for f in glob.glob("/tmp/Elysium-mono/elysium-enchanting/src/main/resources/data/elysiumenchanting/enchantment/*.json"):
    d = json.loads(pathlib.Path(f).read_text(), object_pairs_hook=collections.OrderedDict)
    e = d.get("effects") or {}
    if "minecraft:damage" in e and isinstance(e["minecraft:damage"], list) and e["minecraft:damage"]:
        e["minecraft:damage"][0] = e["minecraft:damage"][0].get("effect", {})
        pathlib.Path(f).write_text(json.dumps(d, indent=2) + "\n")
        break
PY
expect "a conditional effect with its 'effect' unwrapped" "has no 'effect'"

python3 - <<'PY'
import json, pathlib, collections
p = pathlib.Path("/tmp/Elysium-mono/elysium-space/src/main/resources/data/elysiumspace/worldgen/placed_feature/emgrove.json")
d = json.loads(p.read_text(), object_pairs_hook=collections.OrderedDict)
d["feature"] = "elysiumspace:emgrove_that_is_not_there"
p.write_text(json.dumps(d, indent=2) + "\n")
PY
expect "a placed feature naming a configured feature we do not ship" "there is no worldgen/configured_feature by that name"

echo "things that must not be flagged"
reject "crafting patterns, which are rows of hashes"
python3 - <<'PY'
import json, pathlib, collections
p = pathlib.Path("/tmp/Elysium-mono/elysium-flora/src/main/resources/data/elysiumflora/neoforge/biome_modifier/emberwood.json")
d = json.loads(p.read_text(), object_pairs_hook=collections.OrderedDict)
d["biomes"] = "#minecraft:is_nether"
p.write_text(json.dumps(d, indent=2) + "\n")
PY
reject "a vanilla biome tag, which we do not ship and should not have to"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"no tag written inside a list"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "datapack: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
