#!/usr/bin/env bash
# The spaceport, broken one way at a time.
#
# Every check the port added is deliberately defeated here and the suite fails
# if the validator or the harness does not notice. A check nobody has seen fail
# is a check nobody has seen.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-colonies
S=$M/src/main/java/com/elysium/colonies
R=$M/src/main/resources
SNAP=/tmp/port_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-colonies)
restore() { rm -rf $ROOT/elysium-colonies && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }

edit $S/rules/PortRules.java 'public static int outbound(int tether, int legs) {
        return throughput(tether, legs) / 2;' 'public static int outbound(int tether, int legs) {
        return throughput(tether, legs);'
expect "a port that only sends" "only sends or only receives"
restore
echo "subset: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
