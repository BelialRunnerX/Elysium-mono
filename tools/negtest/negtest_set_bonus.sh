#!/usr/bin/env bash
# A gear set's wielder effect, read the way the library's engine reads it (15 Oct 2026):
# elysium_regalia's four-piece Speed said level 0, which the engine gives as amplifier
# -1 and skips, so the bonus never applied. Broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-core
SETS=$M/src/main/resources/data/elysium/elysium_gear_sets
R=$SETS/elysium_regalia.json
SNAP=/tmp/negtest_set_bonus_snapshot.tgz
pass=0; fail=0

tar czf $SNAP -C /tmp Elysium-mono/elysium-core

out() { (cd $ROOT && python3 elysium-core/validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
# Sets one field of the regalia's four-piece Speed target to a JSON value.
target() { python3 -c "
import json, sys
p, key, value = sys.argv[1], sys.argv[2], json.loads(sys.argv[3])
s = json.load(open(p, encoding='utf-8'))
hits = [t for b in s['bonuses'] for e in b['effects'] if e['type'] == 'wielder_effect'
        for t in e['targets'] if t['effect'] == 'minecraft:speed']
assert len(hits) == 1, 'anchor not found in ' + p
hits[0][key] = value
open(p, 'w', encoding='utf-8', newline='\n').write(json.dumps(s, indent=2) + '\n')
" "$R" "$@" || { echo "STALE ANCHOR: $R"; fail=$((fail+1)); }; }
restore() { rm -rf $M; tar xzf $SNAP -C /tmp; }

echo "a set bonus's effect is given at a level the engine gives"
target levels '[0]'
expect "the four-piece Speed at level 0, as it shipped" "the bonus never applies"

target levels '[]'
expect "a Speed with no level at all" "the bonus never applies"

target levels '[1.5]'
expect "a level that is not a whole number" "which are not all whole numbers"

target mode '"full_set_only"'; target levels '[0]'
expect "a full-set Speed at level 0" "the bonus never applies"

target mode '"piece_count"'; target levels '[1, 1, 1, 0]'
expect "by pieces worn, nothing at the four pieces the bonus is for" "at the 4 pieces the bonus is for it gives nothing"

target mode '"piece_count"'; target levels '[1, 1]'
expect "by pieces worn, a list too short to reach four pieces" "at the 4 pieces the bonus is for it gives nothing"

target mode '"by_moonlight"'
expect "a mode the engine has no reading for" "which the engine does not read"

target duration '0'
expect "a Speed that lasts no ticks" "so it never shows"

rm -rf "$SETS"
expect "no gear sets left to read" "so the set bonuses were not read"

echo
echo "restoring and re-checking"
restore
if (cd $ROOT && python3 elysium-core/validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1))
fi
rm -f $SNAP
echo "set bonus: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
