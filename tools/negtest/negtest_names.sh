#!/usr/bin/env bash
# Our own names, broken one way at a time.
#
# The property every case below circles: a conversion keeps the idea and
# writes its own name for it. The project said so in docs/CONVERSION.md from
# the beginning, held the code and the art to it with compile.sh and
# check_retexture.py, and held the names to nothing -- so 134 of
# elysium-bestiary's 176 creature ids were the source jars' own for months and
# every validator passed the whole time. check_names.py is the rule that was
# missing, and these are the proofs it can fail.
#
# The hard half is not catching a borrowed name. It is *not* catching a shared
# one: half the pack ships a swordfish and a copper bee, and a check that
# reports those is a check somebody comments out in a fortnight. So there are
# as many cases below for the things it must stay quiet about as for the
# things it must report.
set -uo pipefail
ROOT=/tmp/Elysium-mono
C=$ROOT/check_names.py
LANG_BES=$ROOT/elysium-bestiary/src/main/resources/assets/elysiumbestiary/lang/en_us.json
WORDS=$ROOT/tools/data/common_words.txt
THEIRS=$ROOT/tools/data/borrowed_entity_names.json
SNAP=/tmp/names_snap.tar
pass=0; fail=0
if ! (cd $ROOT && timeout 200 python3 check_names.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the check does not pass before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP check_names.py tools/data \
     elysium-bestiary/src/main/resources/assets/elysiumbestiary/lang)
restore() { (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $ROOT && timeout 200 python3 check_names.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
name() { python3 -c "
import json,sys
f,k,v = sys.argv[1], sys.argv[2], sys.argv[3]
d = json.load(open(f, encoding='utf-8'))
d['entity.elysiumbestiary.' + k] = v
json.dump(d, open(f,'w',encoding='utf-8'), indent=2, sort_keys=True)
" "$@"; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
drop_word() { python3 -c "
import sys
p, w = sys.argv[1], sys.argv[2]
keep = [l for l in open(p, encoding='utf-8') if l.strip() != w]
open(p,'w',encoding='utf-8').writelines(keep)
" "$@"; }

echo "a name somebody else invented"
name $LANG_BES bonescaller "Bonescaller"
expect "a coinage taken from the jar it was converted from" "is also what born_in_chaos_v1 calls"

name $LANG_BES eldknight "Eldknight"
expect "a coinage from a second jar" "is also what knightquest calls"

name $LANG_BES iceologer "Iceologer"
expect "a coinage from a third" "is also what friendsandfoes calls"

name $LANG_BES dryador "Dryador"
expect "a coinage from the jar this dimension came out of" "is also what valoria calls"

echo "and the half that matters more: what it must not report"
name $LANG_BES swordfish "Swordfish"
expect "a real fish two mods both ship" "not one of them anybody's coinage"

name $LANG_BES zombie "Zombie"
expect "a word the game itself uses" "not one of them anybody's coinage"

name $LANG_BES copper_bee "Copper Bee"
expect "two ordinary words either side of an underscore" "not one of them anybody's coinage"

name $LANG_BES netherite_bee "Netherite Bee"
expect "one of Mojang's words and one ordinary one" "not one of them anybody's coinage"

echo "the rule that tells the two apart"
edit $C '    return any(part not in words for part in name.split("_") if part)' \
        '    return True'
expect "every shared name treated as a coinage" "is also what"

edit $C '    return any(part not in words for part in name.split("_") if part)' \
        '    return False'
name $LANG_BES bonescaller "Bonescaller"
expect "no shared name treated as a coinage" "not one of them anybody's coinage"

edit $C '    return any(part not in words for part in name.split("_") if part)' \
        '    return name not in words'
expect "a name judged whole rather than part by part" "water_bee is registered by"

echo "the two lists it stands on"
drop_word $WORDS "swordfish"
name $LANG_BES swordfish "Swordfish"
expect "a word missing from the list of ordinary words" "swordfish is registered by"

rm -f $WORDS
expect "no list of ordinary words at all" "cannot tell a coinage from an ordinary noun"

rm -f $THEIRS
expect "nothing to compare our names against" "compares our names against nothing"

echo "and the counting that says it looked at anything"
edit $C 'if MINE and len(MINE) < 50:' 'if MINE and len(MINE) < 100000:'
expect "a repository that reads as almost nameless" "so this check is looking at almost"

edit $C 'if NOT_MINE and len(NOT_MINE) < 200:' 'if NOT_MINE and len(NOT_MINE) < 100000:'
expect "a pack that reads as almost nameless" "could match one of"

echo
echo "names: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
