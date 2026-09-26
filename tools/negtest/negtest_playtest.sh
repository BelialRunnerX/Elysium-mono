#!/bin/bash
# Play-test round of 25 September: the five checks written for the bugs a
# running game showed (emberwood log squashed, door item purple, sail the
# missing model, elite rank refused at first spawn) and the root check that
# follows every model reference. Each line breaks one thing and expects the
# validator to say so.
cd /tmp/Elysium-mono || exit 2
export JAVA_TOOL_OPTIONS=
pass=0; failn=0
# t <module dir or .> <file> <old> <new> <expected message>
t() {
    local dir="$1" file="$2" old="$3" new="$4" want="$5"
    cp "$dir/$file" /tmp/nb_pt
    python3 - "$dir/$file" "$old" "$new" <<'PY'
import sys; p,o,n=sys.argv[1:]; s=open(p,encoding="utf-8").read(); assert o in s, "pattern missing: "+o; open(p,'w',encoding="utf-8",newline="\n").write(s.replace(o,n,1))
PY
    if [ "$dir" = "." ]; then
        out=$(timeout 1800 python3 check_models.py 2>&1)
    else
        out=$(cd "$dir" && timeout 1800 python3 validate.py 2>&1)
    fi
    cp /tmp/nb_pt "$dir/$file"
    if grep -q "$want" <<<"$out"; then pass=$((pass+1)); echo "ok   : $want"; else failn=$((failn+1)); echo "MISS : $want"; fi
}
# gone <module dir> <file> <expected message>: the file is removed, not edited
gone() {
    local dir="$1" file="$2" want="$3"
    cp "$dir/$file" /tmp/nb_pt_gone; rm "$dir/$file"
    out=$(cd "$dir" && timeout 1800 python3 validate.py 2>&1)
    cp /tmp/nb_pt_gone "$dir/$file"
    if grep -q "$want" <<<"$out"; then pass=$((pass+1)); echo "ok   : $want"; else failn=$((failn+1)); echo "MISS : $want"; fi
}

FA=src/main/resources/assets/elysiumflora
# flora: a stacked texture with no metadata, and metadata with no animation
gone elysium-flora $FA/textures/block/emberwood_log.png.mcmeta "with no .png.mcmeta"
t elysium-flora $FA/textures/block/emberwood_log.png.mcmeta '"animation"' '"animatoin"' "has no animation section"
# flora: an item model over a texture nobody ships
t elysium-flora $FA/models/item/emberwood_door.json 'elysiumflora:item/emberwood_door' 'elysiumflora:item/emberwood_dooor' "no such texture is shipped"
# kinetics: the sail given variants its plain Block does not have
t elysium-kinetics src/main/resources/assets/elysiumkinetics/blockstates/sail.json '"": {' '"axis=y": {' "rather than the empty one"
# root: a blockstate model and a model texture that land nowhere
t . elysium-kinetics/src/main/resources/assets/elysiumkinetics/blockstates/sail.json 'elysiumkinetics:block/sail' 'elysiumkinetics:block/sial' "no such model is shipped"
t . elysium-flora/src/main/resources/assets/elysiumflora/models/block/emberwood_planks.json 'elysiumflora:block/emberwood_planks' 'elysiumflora:block/emberwood_plank' "no such texture is shipped"
# flora: a shipped file edited by hand no longer matches its generator
t elysium-flora $FA/models/block/loomflax_stage0.json '"render_type": "minecraft:cutout",' '"render_type": "minecraft:cutout_mipped",' "differ from what the generators write"
# root: a crop model that forgets its render layer draws its holes black
t . elysium-flora/src/main/resources/assets/elysiumflora/models/block/loomflax_stage0.json '"render_type": "minecraft:cutout",' '' "on the solid layer"
# root: the same check reaches the other agents' modules — a kitchen crop and a compressed block (the key is renamed, not removed: it is the last in its file)
t . elysium-agronomy/src/main/resources/assets/elysiumagronomy/models/block/barley_crop_stage0.json '"render_type": "minecraft:cutout",' '' "on the solid layer"
t . elysium-logistics/src/main/resources/assets/elysiumlogistics/models/block/compressed_ice.json '"render_type": "minecraft:translucent"' '"render_type_": "minecraft:translucent"' "on the solid layer"
# root: glass that says cutout when every pixel is part-transparent is still wrong only if it says nothing; a shaft whose faces read opaque pixels needs none (no failure expected, so the check must stay quiet)
cp elysium-kinetics/src/main/resources/assets/elysiumkinetics/models/block/brass_shaft.json /tmp/nb_pt_shaft
python3 - <<'PY'
import json; p="elysium-kinetics/src/main/resources/assets/elysiumkinetics/models/block/brass_shaft.json"; m=json.load(open(p,encoding="utf-8")); m.pop("render_type",None); open(p,"w",encoding="utf-8",newline="\n").write(json.dumps(m,indent=2)+"\n")
PY
out=$(timeout 1800 python3 check_models.py 2>&1); cp /tmp/nb_pt_shaft elysium-kinetics/src/main/resources/assets/elysiumkinetics/models/block/brass_shaft.json
if grep -q "brass_shaft" <<<"$out"; then failn=$((failn+1)); echo "MISS : a shaft reading only opaque pixels was flagged"; else pass=$((pass+1)); echo "ok   : a shaft reading only opaque pixels is left alone"; fi
# space: a traveller must be accepted from as far as the anchor can be clicked
t elysium-space src/main/java/com/elysium/space/gate/WaygateTravel.java 'public static final int REACH = 5;' 'public static final int REACH = 2;' "less than the block reach"
t elysium-space src/main/java/com/elysium/space/gate/WaygateTravel.java 'at.offset(-REACH, -REACH, -REACH)' 'at.offset(-2, -2, -2)' "hard-coded distance"
# space: the Bastion stays flat, and its mountains stay black
BW=src/main/resources/data/elysiumspace/worldgen
t elysium-space $BW/noise_settings/emerald_bastion.json '"erosion": "elysiumspace:emerald_bastion/erosion"' '"erosion": "minecraft:overworld/erosion"' "biomes follow"
t elysium-space $BW/density_function/emerald_bastion/erosion.json '"value": 0.38' '"value": 0.6' "shatters and sinks"
t elysium-space $BW/density_function/emerald_bastion/erosion.json '"location": -0.42' '"location": 0.2' "meant to be mostly flat"
t elysium-space $BW/density_function/emerald_bastion/offset.json 'elysiumspace:emerald_bastion/erosion' 'minecraft:overworld/erosion' "its hills come back"
t elysium-space $BW/noise_settings/emerald_bastion.json '"elysiumspace:emerald_bastion_highlands"' '"elysiumspace:emerald_bastion_plains"' "not volcanic"
# space: a generated lang key and a generated worldgen file edited by hand no longer match the four generators
SA=src/main/resources/assets/elysiumspace
t elysium-space $SA/lang/en_us.json '"elysiumspace.freight.run": "%s"' '"elysiumspace.freight.run": "Run %s"' "differ from what the generators write"
t elysium-space $BW/placed_feature/emerald_bastion_lake.json '"chance": 6' '"chance": 7' "differ from what the generators write"
# lib: the elite class no longer loaded by the constructor
t elysium-lib src/main/java/com/elysium/lib/ElysiumLib.java '        com.elysium.lib.elite.ElysiumElites.bootstrap();' '' "nothing loads the class before RegisterEvent"
echo "$pass passed, $failn missed"
git status --short | grep -q . && echo "TREE DIRTY" || echo "tree restored clean"
