#!/usr/bin/env bash
# What goes on the end of a rod, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-agronomy
T=$M/src/main/java/com/elysium/agronomy/rules/TackleRules.java
G=$M/tools/gen_fish.py
SNAP=/tmp/tackle_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  (cd $M && timeout 400 python3 validate.py 2>&1 | tail -5)
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-agronomy elysium-foundry)
restore() { rm -rf $ROOT/elysium-agronomy $ROOT/elysium-foundry && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
gexpect() { (cd $M && python3 tools/gen_fish.py >/dev/null 2>&1); expect "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the one thing tackle may never do: take a fish away"
edit $T '        List<FishRules.Water> made = new ArrayList<>();
        made.add(where);' '        List<FishRules.Water> made = new ArrayList<>();
        if (bobber.deeper() == 0) { made.add(where); }'
expect "a bobber that trades the water it is cast into for a deeper one" "stops coming up with"

edit $T '        if (bobber.anytime()) {
            for (FishRules.Water one : List.copyOf(made)) {
                made.add(new FishRules.Water(one.kind(), one.band(),
                        FishRules.DAY.equals(one.when())
                                ? FishRules.NIGHT : FishRules.DAY));
            }
        }' '        if (bobber.anytime()) {
            made.clear();
            made.add(new FishRules.Water(where.kind(), where.band(),
                    FishRules.DAY.equals(where.when())
                            ? FishRules.NIGHT : FishRules.DAY));
        }'
expect "a bobber that swaps day for night rather than adding it" "stops coming up with"

echo "the hooks"
edit $T '            new Hook("gilded_hook", 2, 1, false));' '            new Hook("gilded_hook", 0, 1, false));'
expect "a hook that is the bare end of a line with a name on it" "is the bare end of a line with a name on it"

edit $T '            new Hook("iron_hook", 1, 1, false),' '            new Hook("gilded_hook", 2, 1, false),'
expect "two hooks with one name" "names two hooks"

edit $T '            new Hook("barbed_hook", 0, 1, true),' '            new Hook("barbed_hook", 1, 1, false),'
expect "two hooks that do exactly the same thing" "does exactly what another hook does"

edit $T '            new Hook("gilded_hook", 2, 1, false));' '            new Hook("gilded_hook", 9, 1, false));'
expect "a hook worth the whole rod ladder in one item" "so it is the whole ladder in one item"

edit $T '            if (!what.add(one.luck() + "/" + one.catches() + "/" + one.holds())) {' '            if (false) {'
expect "a hook sameness check that cannot report anything" "two hooks that do exactly the same thing are not reported"

echo "the bobbers"
edit $T '            new Bobber("cork_bobber", 0, false, 0.25F),' '            new Bobber("cork_bobber", 0, false, 0.0F),'
expect "a bobber that does nothing at all" "does nothing at all"

edit $T '            new Bobber("lead_bobber", 1, false, 0.0F),' '            new Bobber("lead_bobber", 4, false, 0.0F),'
expect "a bobber that reaches every band there is" "so every water is every other water"

edit $T '            if (!one.opens() && one.sooner() <= 0.0F) {' '            if (false) {'
expect "a bobber usefulness check that cannot report anything" "a bobber that does nothing at all is not reported"

echo "a better hook never making the rare fish harder to get"
edit $T '                if (rareAfter / commonAfter
                        < rareBefore / commonBefore - 1.0E-9D) {' '                if (false) {'
expect "a hook rule that cannot report anything" "cannot say no"

edit $T '        int luck = Math.min(FishRules.RODS - 1, rod + tackle.hook().luck());' '        int luck = Math.max(0, rod - tackle.hook().luck());'
expect "a hook that spends its luck backwards, making the rare fish rarer" "harder to get than"

echo "the bait, which could switch a rod on"
edit $T '    public static final int WORM_TICKS = 1_200;' '    public static final int WORM_TICKS = 12;'
expect "a worm farm that makes bait as fast as anybody could want it" "is a rod that always catches what it is pointed at"

edit $T '        if (perHour > ceiling) {' '        if (false) {'
expect "a bait ceiling that cannot report anything" "cannot say no"

echo "a mount for every fish"
edit $T '        for (FishRules.Fish one : FishRules.FISH) {
            made.add(mountOf(one.id()));
        }' '        for (FishRules.Fish one : FishRules.FISH.subList(0, 30)) {
            made.add(mountOf(one.id()));
        }'
expect "fewer mounts than there are fish" "has nowhere to go on the wall"

echo "the world the tackle is actually in"
rm -f $M/src/main/resources/data/elysiumagronomy/recipe/iron_hook.json
expect "a hook with no recipe" "so a hook is an item only the creative menu has"

rm -f $M/src/main/resources/assets/elysiumagronomy/models/item/cork_bobber.json
expect "a bobber with no model" "has no model"

rm -f $M/src/main/resources/data/elysiumagronomy/recipe/river_dace_mount.json
expect "a fish with no mount recipe" "have no mount to go on a wall"

edit $G '            "key": {"P": {"tag": "minecraft:planks"},
                    "F": {"item": f"{NS}:{one['"'"'id'"'"']}"}},' '            "key": {"P": {"tag": "minecraft:planks"},
                    "F": {"item": "minecraft:cod"}},'
gexpect "a mount anybody can craft without ever catching the fish" "so it is a plaque anybody can craft"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all agronomy checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | tail -3 | head -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "tackle: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
