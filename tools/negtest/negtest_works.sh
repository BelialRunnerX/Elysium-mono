#!/bin/bash
# C6 negative tests. Each case breaks one rule, asserts the validator objects
# with the expected words, and restores from a snapshot taken per case.
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_works.lock
flock -n 9 || { echo "another negtest_works is running; refusing"; exit 2; }
L=elysium-lib/src/main/java/com/elysium/lib
H=$L/event/ElysiumWorksHandler.java
C=$L/works/ElysiumConspicuity.java
W=$L/works/ElysiumWorks.java
G=elysium-lib/src/main/resources/data/elysiumlib/tags/block/works/conspicuous.json
B=elysium-core/src/main/java/com/elysium/core/block/RegistryBeaconBlock.java
FILES="$H $C $W $G $B"
pass=0; miss=0
snap()    { rm -rf /tmp/_wk; mkdir -p /tmp/_wk; for f in $FILES; do cp "$f" "/tmp/_wk/$(basename $f)"; done; }
restore() { for f in $FILES; do cp "/tmp/_wk/$(basename $f)" "$f"; done; }

for v in "python3 elysium-lib/validate.py" "python3 elysium-core/validate.py"; do
  if ! timeout 1700 $v >/dev/null 2>&1; then echo "SETUP FAILED: tree not clean ($v)"; exit 2; fi
done
echo "baseline clean"

try() { # label, validator(lib|core), expected, python edit
  local label="$1" which="$2" want="$3" edit="$4"
  snap
  if ! python3 -c "$edit"; then echo "SETUP FAILED: $label"; restore; return; fi
  if timeout 1700 python3 elysium-$which/validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

# edit helper: replace exactly one occurrence, fail loudly if absent
R='
import sys
def rep(path, old, new):
    s = open(path, encoding="utf-8").read()
    if old not in s:
        sys.exit("anchor not found in " + path + ": " + old[:60])
    open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
'

try "claimed placements charged too" lib "charged twice" "$R
rep('$H', 'if (ElysiumClaims.of(level).at(event.getPos()) != null) {', 'if (false) {')"

try "doctrine ignored" lib "does not scale by doctrine" "$R
rep('$H', '''        weight = ElysiumConspicuity.scaled(weight,
                ElysiumDoctrines.suspicionMultiplier(player));''', '')"

try "doctrine applied after recording" lib "after the site has already recorded" "$R
s = open('$H').read()
cut = '''        weight = ElysiumConspicuity.scaled(weight,
                ElysiumDoctrines.suspicionMultiplier(player));
'''
assert cut in s
s = s.replace(cut, '', 1)
anchor = '        ElysiumSite.Placed outcome = works.built(event.getPos(), weight, now);\n'
assert anchor in s
s = s.replace(anchor, anchor + cut, 1)
open('$H','w').write(s)"

try "post planted below notice" lib "notice-threshold gate" "$R
rep('$H', '&& ElysiumStanding.getSuspicion(player) >= ElysiumStanding.NOTICE', '')"

try "creative builders charged" lib "charges creative builders" "$R
rep('$H', 'player.isCreative() || player.isSpectator()', 'player.isSpectator()')"

try "filed under the claim cause" lib "some other cause" "$R
rep('$H', 'ElysiumStanding.CAUSE_WORKS', 'ElysiumStanding.CAUSE_CLAIM')"

try "store stops delegating to the rule" lib "no longer delegates" "$R
rep('$W', 'siteAt(pos, now).placed(weight, now)', 'new ElysiumSite.Placed(siteAt(pos, now), 0)')"

try "planks in the conspicuous tag" lib "building material" "$R
rep('$G', '\"minecraft:furnace\",', '\"minecraft:furnace\", \"minecraft:oak_planks\",')"

try "stone bricks in the conspicuous tag" lib "building material" "$R
rep('$G', '\"minecraft:furnace\",', '\"minecraft:furnace\", \"minecraft:stone_bricks\",')"

try "notice line dropped to a workshop" lib "this is a shed" "$R
rep('$C', 'public static final int NOTICE_AT = 120;', 'public static final int NOTICE_AT = 30;')"

try "notice line raised past factories" lib "the survey never came" "$R
rep('$C', 'public static final int NOTICE_AT = 120;', 'public static final int NOTICE_AT = 400;')"

try "notice resets fully" lib "must be found sooner" "$R
rep('$C', 'public static final int RESIDUAL = NOTICE_AT / 2;', 'public static final int RESIDUAL = 0;')"

try "unregistered capped under the claim" lib "the choice is not one" "$R
rep('$C', 'public static final int MOST_PER_NOTICE = 32;', 'public static final int MOST_PER_NOTICE = 5;')"

try "the decay overflow put back" lib "decay was not monotone" "$R
rep('$C', '''        if (elapsedTicks > Long.MAX_VALUE / DECAY_PER_MINUTE) {
            return 0;
        }''', '')"

try "a registry dragged into the rule" lib "takes the whole system out of the tests" "$R
rep('$C', 'package com.elysium.lib.works;', 'package com.elysium.lib.works;\n\nimport net.neoforged.neoforge.attachment.AttachmentType;')"

try "planting a beacon keeps watching" core "planting a Registry Beacon" "$R
s = open('$B').read()
i = s.index('private void stake(')
j = s.index('absorbWorks(level, player, claim);', i)
s = s[:j] + s[j+len('absorbWorks(level, player, claim);'):]
open('$B','w').write(s)"

try "ascending a beacon keeps watching" core "ascending a Registry Beacon" "$R
rep('$B', '        absorbWorks(level, player, grown);\n', '')"

try "absorb registers nothing" core "no longer asks ElysiumWorks" "$R
rep('$B', 'int cleared = com.elysium.lib.works.ElysiumWorks.of(level).registered(claim);', 'int cleared = 0;')"

echo "---- caught $pass, missed $miss"
