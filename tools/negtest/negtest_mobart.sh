#!/usr/bin/env bash
# The bestiary rendering as silhouettes, reintroduced one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono/elysium-mobs
G=$ROOT/tools/gen_mobs.py
pass=0; fail=0
regen() { (cd /tmp/Elysium-mono && python3 elysium-mobs/tools/gen_mobs.py >/dev/null 2>&1); }
out() { (cd $ROOT && python3 validate.py 2>&1); }
expect() { local w="$2" g; regen; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@"; }

echo "the palette going dark again"
cp $G /tmp/gm.bak
edit $G '"scavenger": ("#888175", "#c5bbab"),' '"scavenger": ("#3a352c", "#5c5344"),'
expect "a family back at a quarter of vanilla's value" "renders as a silhouette"
cp /tmp/gm.bak $G

cp $G /tmp/gm.bak
edit $G '"scavenger": ("#888175", "#c5bbab"),' '"scavenger": ("#f0eee8", "#c5bbab"),'
expect "a family brighter than anything vanilla ships" "glow against every world"
cp /tmp/gm.bak $G

echo "the range collapsing"
cp $G /tmp/gm.bak
edit $G '    "plate": (1.52, 0.55),   # helm, cuirass, pauldrons, greaves — the shell' \
        '    "plate": (1.00, 1.00),'
expect "the armour plate no lighter than the body" "no value structure"
cp /tmp/gm.bak $G

cp $G /tmp/gm.bak
python3 - <<'EOF'
p = "/tmp/Elysium-mono/elysium-mobs/tools/gen_mobs.py"
s = open(p).read()
old = 'def material_for(boxes):\n    """part name -> material key."""\n    out = {}'
assert old in s
open(p, "w").write(s.replace(old, old + "\n    return out", 1))
EOF
expect "every part made of the same thing" "nothing inside it"
cp /tmp/gm.bak $G

echo
regen
if (cd $ROOT && python3 validate.py >/dev/null 2>&1); then
  echo "  tree restored clean"
else
  echo "  RESTORE FAILED"; fail=$((fail+1))
fi
echo "mob art: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
