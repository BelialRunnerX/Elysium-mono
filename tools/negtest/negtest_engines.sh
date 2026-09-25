#!/usr/bin/env bash
# Engines that burn something, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-kinetics
E=$M/src/main/java/com/elysium/kinetics/rules/EngineRules.java
L=$M/src/main/java/com/elysium/kinetics/rules/LineRules.java
B=$M/src/main/java/com/elysium/kinetics/block/EngineBlock.java
G=$M/tools/gen_kinetics.py
SNAP=/tmp/engines_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-kinetics)
restore() { rm -rf $ROOT/elysium-kinetics && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_kinetics.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one sum an engine may not get wrong"
edit $E '            new Engine("oil_engine", new KineticRules.Source(160.0F, 48.0F), 5, 112),' '            new Engine("oil_engine", new KineticRules.Source(160.0F, 48.0F), 5, 8),'
expect "an engine getting more out of a fuel than the fuel contains" "a machine that makes energy is the same bug as a loop that gains torque"

edit $E '                if (got > one.allowed() + 1.0E-3F) {' '                if (false) {'
expect "an energy check that cannot report anything" "cannot say no"

edit $E '    public static final float EFFICIENCY = 0.60F;' '    public static final float EFFICIENCY = 1.40F;'
expect "an engine allowed more energy out than there is in the fuel" "a perfect engine is the shape of a bug rather than of a reward"

echo "torque round a loop"
edit $L '        KineticRules.Source back = there.through(1.0F / ratio, stages);' '        KineticRules.Source back = there.through(1.0F / ratio, 0);'
expect "a round trip that pays the gear loss only once" "the round trip pays the teeth only once"

edit $L '        float drawn = 0.0F;
        for (Drawn one : loads) {' '        float drawn = 1.0F;
        for (Drawn one : loads) {'
expect "books that do not add up" "which does not add up"

edit $L '        return new Balance(pushed, drawn, Math.max(0.0F, pushed - drawn));' '        return new Balance(pushed, drawn, 0.0F);'
expect "a line whose idle torque is thrown away rather than counted" "which does not add up"

echo "the ceiling an engine is measured against"
edit $E '        if (ceiling >= 1.0F) {' '        if (false) {'
expect "a ceiling check that cannot say a ceiling is not a ceiling" "an engine allowed more energy out than there is in the fuel is not reported"

edit $E '        if (ceiling <= 0.0F) {' '        if (false) {'
expect "a ceiling check that cannot say an engine is allowed nothing" "an engine allowed nothing at all is not reported"

edit $E '    public static List<String> faultsInCeiling(float ceiling) {' '    public static java.util.List<String> faultsInCeiling(float ceiling) {'
expect "the ceiling rule taken out from under the validator" "the whole section becomes a comment"

echo "the ladder of fuels"
edit $E '            new Fuel("elysiumkinetics:distillate", 48_000, "distilling creosote"));' '            new Fuel("elysiumkinetics:distillate", 800, "distilling creosote"));'
expect "a fuel worth less than the one below it" "so the ladder runs backwards and nobody refines anything"

edit $E '            if (one.burn() < under.burn()) {' '            if (false) {'
expect "a fuel ladder check that cannot report anything" "is not reported, so the ladder could run backwards"

echo "the ladder of engines"
edit $E '            new Engine("distillate_engine",
                    new KineticRules.Source(256.0F, 72.0F), 6, 220));' '            new Engine("distillate_engine",
                    new KineticRules.Source(160.0F, 48.0F), 6, 220));'
expect "an engine no stronger than the one below it" "so nobody would ever build it"

edit $E '            new Engine("furnace_engine", new KineticRules.Source(64.0F, 24.0F), 4, 32),' '            new Engine("furnace_engine", new KineticRules.Source(64.0F, 24.0F), 6, 32),'
expect "the first engine taking every fuel there is" "the engines above it are blocks nobody would ever make"

edit $E '            if (efficiencyOf(one, cheapest) <= efficiencyOf(under, cheapest)) {' '            if (false) {'
expect "a refining-reward check that cannot report anything" "are not reported, so the top of the ladder could be merely thirstier"

echo "the block a player actually fills"
edit $B '            return left > 0 ? rules().source() : null;' '            return rules().source();'
expect "an engine that pushes on an empty tank" "so it is free"

edit $B '                left -= Math.max(1, rules.burning() / TICKS_PER_SECOND);' '                left -= 1;'
expect "an engine that spends fuel at a rate of its own" "so the sum the harness proves is about an engine nobody runs"

echo "the world the engines are actually in"
rm -f $M/src/main/resources/data/elysiumkinetics/recipe/oil_engine.json
expect "an engine with no recipe" "so a rung of the engine ladder is a block only the creative menu has"

rm -f $M/src/main/resources/data/elysiumkinetics/recipe/refining/distillate.json
expect "a fuel of ours with no way up to it" "so it is an item nobody can ever hold"

rm -f $M/src/main/resources/assets/elysiumkinetics/models/item/creosote.json
expect "a fuel of ours with no model" "so a fuel of ours is a purple cube"

edit $G '        write(os.path.join(DATA, "recipe", "refining", name + ".json"), {
            "type": f"{NS}:refining",' '        write(os.path.join(DATA, "recipe", "refining", name + ".json"), {
            "type": f"{NS}:milling",'
gexpect "a refining recipe that is not a refining recipe" "is not a refining recipe"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all kinetics checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "engines: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
