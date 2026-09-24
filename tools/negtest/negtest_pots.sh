#!/usr/bin/env bash
# A hundred and sixty-five pots, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
R=$M/src/main/java/com/elysium/agronomy/rules/PotRules.java
SNAP=/tmp/pots_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-agronomy)
restore() { rm -rf $ROOT/elysium-agronomy && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the catalogue, which is the half that is easy to leave holes in"
edit $R '    public static final List<String> FITTINGS = List.of(PLAIN, HOPPER, WAXED);' '    public static final List<String> FITTINGS = List.of(PLAIN, HOPPER);'
expect "a material that has lost a fitting" "three fittings"

edit $R '            "brown", "green", "red", "black");' '            "brown", "green", "red");'
expect "the catalogue losing a dye" "lost a colour"

edit $M/src/main/java/com/elysium/agronomy/registry/PotBlocks.java \
  'for (PotRules.Pot pot : PotRules.catalogue()) {' 'for (PotRules.Pot pot : PotRules.catalogue().subList(0, 9)) {'
expect "a registry that stops walking the catalogue" "no longer walks the catalogue"

edit $M/src/main/java/com/elysium/agronomy/registry/PotBlocks.java \
  'if (made.size() != PotRules.catalogue().size()) {' 'if (false) {'
expect "a pot registry that does not count what it built" "do not count what they built"

echo "the soil"
edit $R 'new Soil("rich_loam", 3, 1.70F, List.of("common", "crop", "hardy", "desert")),' 'new Soil("rich_loam", 3, 1.70F, List.of("common", "crop", "hardy", "desert", "fungal", "nether", "end", "aquatic")),'
expect "a soil that grows everything" "grows everything"

edit $R 'new Soil("void_silt", 4, 1.90F, List.of("end", "fungal", "aquatic")));' 'new Soil("void_silt", 4, 0.90F, List.of("end", "fungal", "aquatic")));'
expect "a rung of the soil ladder that buys nothing" "buys nothing"

edit $R 'new Sprout("chorus_flower", "end", 4200, "chorus_fruit", 2),' 'new Sprout("chorus_flower", "sky", 4200, "chorus_fruit", 2),'
expect "a sprout nothing will grow" "nothing will grow"

edit $R 'new Sprout("kelp", "aquatic", 1500, "dried_kelp", 2),
            new Sprout("sea_pickle", "aquatic", 2600, "sea_pickle", 2),
            new Sprout("lily_pad", "aquatic", 2000, "lily_pad", 2),' 'new Sprout("kelp", "common", 1500, "dried_kelp", 2),
            new Sprout("sea_pickle", "common", 2600, "sea_pickle", 2),
            new Sprout("lily_pad", "common", 2000, "lily_pad", 2),'
expect "a kind of soil nothing ever asks for" "nothing ever asks for"

edit $R 'new Sprout("bamboo", "common", 1400, "bamboo", 3),' 'new Sprout("bamboo", "common", 1400, "bamboo", 1),'
expect "a sprout that gives back exactly what you planted" "exactly what you planted"

echo "the fitting, which must not be a hidden upgrade"
edit $R '    public static float speedOf(Pot pot) {
        return 1.0F;
    }' '    public static float speedOf(Pot pot) {
        return WAXED.equals(pot.fitting()) ? 1.4F : 1.0F;
    }'
expect "waxing a pot quietly making it faster" "changes how fast it grows"

edit $R '        public boolean feeds() {
            return HOPPER.equals(fitting);
        }' '        public boolean feeds() {
            return true;
        }'
expect "a pot that disagrees with itself about emptying into what is below" "disagrees with itself"

echo "the world the pots are actually in"
rm -f $M/src/main/resources/assets/elysiumagronomy/blockstates/red_terracotta_hopper_botany_pot.json
expect "a pot with no blockstate" "a pot needs are not there"

rm -f $M/src/main/resources/data/elysiumagronomy/recipe/void_silt.json
expect "a soil that cannot be made" "a pot needs are not there"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/assets/elysiumagronomy/lang/en_us.json")
d = json.loads(p.read_text(encoding="utf-8"))
d.pop("block.elysiumagronomy.rich_loam", None)
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "a soil with no name" "show their id"

python3 - <<'PYX'
import pathlib
pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/data/elysiumagronomy/botany_soil/ashen_bed.json").unlink()
PYX
expect "the soil table on disk disagreeing with the rules" "disagree"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-agronomy/src/main/resources/data/elysiumagronomy/botany_soil/rich_loam.json")
d = json.loads(p.read_text(encoding="utf-8"))
d["block"] = "elysiumagronomy:not_a_soil_we_make"
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "a soil the table says is ours and nothing registers" "nothing registers"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "pots: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
