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

echo "the charter grant"
edit $S/rules/ReliefRules.java 'public static final int GRANT_DAYS = 100;' 'public static final int GRANT_DAYS = 12;'
expect "a grant sized for the bootstrap alone" "sized for the bootstrap"

edit $S/rules/ReliefRules.java 'public static final int GRANT_DAYS = 100;' 'public static final int GRANT_DAYS = 4000;'
expect "a grant that never ends in practice" "never ends in practice"

edit $S/rules/ReliefRules.java 'public static final int GRANT_STACKS_PER_DAY = 64;' 'public static final int GRANT_STACKS_PER_DAY = 2;'
expect "a day's grant that cannot finish a building" "cannot finish a building"

edit $S/rules/ReliefRules.java 'public static final int GRANT_PER_DAY = GRANT_STACKS_PER_DAY * 64;' 'public static final int GRANT_PER_DAY = 128;'
expect "two ways of saying how much, disagreeing" "two ways of saying how much disagree"

edit $S/rules/ReliefRules.java 'public static final int REPORT_AFTER = 4;' 'public static final int REPORT_AFTER = 40;'
expect "an owner told only after months of silence" "months of silence"

edit $S/event/ColonyLife.java 'if (relief == ReliefRules.Relief.REPORT
                || ReliefRules.alsoReport(town.day(), ledger.lastProgress())) {' 'if (relief == ReliefRules.Relief.REPORT) {'
expect "a town that is helped and never heard from" "sits silent for months"

restore
echo "grant: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
