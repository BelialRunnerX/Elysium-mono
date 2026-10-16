#!/bin/bash
cd /tmp/Elysium-mono || exit 1
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
exec 9>/tmp/negtest_icons.lock; negtest_lock 9 /tmp/negtest_icons.lockdir || exit 2
D=elysium-lib/src/main/resources/assets/elysiumlib/textures/mob_effect
rm -rf /tmp/_ic; cp -r $D /tmp/_ic
pass=0; miss=0
t() { python3 -c "$3" || { echo "SETUP FAILED $1"; miss=$((miss+1)); return; }
  if timeout 1700 python3 elysium-lib/validate.py 2>&1 | grep -qF "$2"; then echo "CAUGHT : $1"; pass=$((pass+1)); else echo "MISSED : $1"; miss=$((miss+1)); fi
  rm -rf $D; cp -r /tmp/_ic $D; }
t "pressure icon missing" "elysiumlib:pressure_ward has no icon" "import os; os.remove('$D/pressure_ward.png')"
t "icon at 16x16" "vanilla draws effects at 18x18" "from PIL import Image; Image.open('$D/thermal_ward.png').resize((16,16)).save('$D/thermal_ward.png')"
t "invisible icon" "fully transparent" "from PIL import Image; Image.new('RGBA',(18,18),(0,0,0,0)).save('$D/burn.png' if False else '$D/corrosive_ward.png')"
echo "---- icons: caught $pass, missed $miss"
[ "$miss" -eq 0 ]
