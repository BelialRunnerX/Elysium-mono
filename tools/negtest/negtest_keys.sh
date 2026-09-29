#!/usr/bin/env bash
# Two keys on one default, one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
KEYS=$ROOT/elysium-space/src/main/java/com/elysium/space/client/SpaceKeys.java
LIBKEYS=$ROOT/elysium-lib/src/main/java/com/elysium/lib/client/ElysiumKeys.java
SNAP=/tmp/keys_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-space/src/main/java/com/elysium/space/client/SpaceKeys.java elysium-lib/src/main/java/com/elysium/lib/client/ElysiumKeys.java)
restore() { (cd $ROOT && tar xf $SNAP); }
out() { (cd $ROOT && timeout 300 python3 check_keys.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the launch key, the way it was found"
edit $KEYS '    static final int KEY_H = 72;' '    static final int KEY_H = 71;'
expect "a launch key on Curios' G" "which is Curios: open inventory (G)"

echo "two of ours on one key"
edit $KEYS '    static final int KEY_H = 72;' '    static final int KEY_H = 74;'
expect "the launch and the jet on one key" "the same key as key.elysiumspace.jet_mode"

echo "a default the check cannot read"
edit $KEYS '            InputConstants.Type.KEYSYM, KEY_H, "key.categories.elysiumspace");' '            InputConstants.Type.KEYSYM, someKey(), "key.categories.elysiumspace");'
expect "a launch key computed at runtime" "which this check cannot read"

echo "restoring and re-checking"
restore
if out | grep -q "PROBLEM"; then echo "  RESTORE FAILED"; fail=$((fail+1)); else echo "  tree restored clean"; fi
rm -f $SNAP
echo "keys: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
