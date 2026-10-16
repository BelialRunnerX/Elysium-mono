#!/usr/bin/env bash
# Claim ascension and claim shelter, broken one way at a time.
set -uo pipefail
cd /tmp/Elysium-mono/elysium-lib
C=src/main/java/com/elysium/lib/claim
H=src/main/java/com/elysium/lib/hazard
E=src/main/java/com/elysium/lib/event
pass=0; fail=0
out() { python3 validate.py 2>&1; }
# Substring test rather than `printf ... | grep -qF`.
#
# grep -q exits the moment it matches, which closes the pipe under printf and
# kills it with SIGPIPE — and with `set -o pipefail` the pipeline then reports
# failure even though the string was found. Every catch whose match happened
# to land early in a large output was silently reported as MISSED. The same
# flag hid a real catch once before in this repository, by a different route.
expect() { local n="$1" w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $n"; pass=$((pass+1));
  else echo "  MISSED : $n"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

echo "the radius ladder"
cp $C/ElysiumClaim.java /tmp/c.bak
edit $C/ElysiumClaim.java 'public static final int RADIUS_PER_ASCENSION = 64;' \
                          'public static final int RADIUS_PER_ASCENSION = 32;'
expect "a tier that is not 64 blocks" "not the"
cp /tmp/c.bak $C/ElysiumClaim.java

cp $C/ElysiumClaim.java /tmp/c.bak
edit $C/ElysiumClaim.java 'long radius = (long) DEFAULT_RADIUS + (long) RADIUS_PER_ASCENSION * tiers;' \
                          'int radius = DEFAULT_RADIUS + RADIUS_PER_ASCENSION * tiers;'
expect "radius overflows on a hand-edited save" "contains nothing"
cp /tmp/c.bak $C/ElysiumClaim.java

cp $C/ElysiumClaim.java /tmp/c.bak
edit $C/ElysiumClaim.java 'int tiers = Math.max(0, ascension);' 'int tiers = ascension;'
expect "a negative ascension shrinks the claim" "shrinks the claim below its default"
cp /tmp/c.bak $C/ElysiumClaim.java

echo "the gap below Hunted"
cp $C/ElysiumClaim.java /tmp/c.bak
edit $C/ElysiumClaim.java 'return (int) Math.min(MAX_FLOOR, withAscension);' \
                          'return (int) withAscension;'
expect "ascension pushes a player into Hunted" "inside the Hunted band"
cp /tmp/c.bak $C/ElysiumClaim.java

cp $C/ElysiumClaim.java /tmp/c.bak
edit $C/ElysiumClaim.java 'public static final int FLOOR_PER_ASCENSION = 4;' \
                          'public static final int FLOOR_PER_ASCENSION = 0;'
expect "ascension costs no standing at all" "does not raise the Suspicion floor"
cp /tmp/c.bak $C/ElysiumClaim.java

echo "identity through growth"
cp $C/ElysiumClaim.java /tmp/c.bak
edit $C/ElysiumClaim.java '        return new ElysiumClaim(owner, ownerName, anchor, radius,
                (int) Math.min(Integer.MAX_VALUE, total), ascension);' \
'        return new ElysiumClaim(owner, ownerName, anchor, radius,
                (int) Math.min(Integer.MAX_VALUE, total), 0);'
expect "building resets the claim's ascension" "territory shrinks every time they build"
cp /tmp/c.bak $C/ElysiumClaim.java

echo "saves written before ascension existed"
cp $C/ElysiumClaim.java /tmp/c.bak
edit $C/ElysiumClaim.java 'Codec.INT.optionalFieldOf("ascension", 0)' 'Codec.INT.fieldOf("ascension")'
expect "a required field drops every existing claim" "the world loses every claim in it"
cp /tmp/c.bak $C/ElysiumClaim.java

echo "shelter"
cp $E/ElysiumHazardHandler.java /tmp/e.bak
edit $E/ElysiumHazardHandler.java 'boolean sheltered = ElysiumProtection.inShelteredClaim(level, pos);' \
                                  'boolean sheltered = false;'
expect "a filed claim does not stop the weather" "a filed base does not shelter anybody"
cp /tmp/e.bak $E/ElysiumHazardHandler.java

echo
echo "negative tests: $pass caught, $fail missed"
python3 validate.py >/dev/null 2>&1 && echo "restored clean" || echo "RESTORE FAILED"
[ "$fail" -eq 0 ]
