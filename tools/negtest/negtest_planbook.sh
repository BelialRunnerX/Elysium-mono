#!/usr/bin/env bash
# The plan book, broken one way at a time.
#
# Every rule the plan book added is deliberately defeated here. Where a rule
# could plausibly be satisfied by a token that is no longer doing anything,
# the break leaves the token behind on purpose — commented out, renamed, or
# moved out of the method that has to run it — because that is the failure
# mode that has caught us four times now.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-armory
S=$M/src/main/java/com/elysium/armory
R=$M/src/main/resources
SNAP=/tmp/planbook_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-armory)
restore() { rm -rf $ROOT/elysium-armory && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 300 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:60] + '\n')
    sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

PART=$S/recipe/ArmoryCompoundPartRecipe.java
BOOK=$S/recipe/ArmoryPlanBookRecipe.java
DATA=$S/item/ArmoryPlanBook.java

echo "a book stands in for one plan"
edit $PART 'part = onlyKnown(book, materials);' '// part = onlyKnown(book, materials);'
expect "the call to onlyKnown left as a comment" "resolved against the materials"

edit $PART 'return null;               // two plans fit' 'break;                     // two plans fit'
expect "a book that knows two fitting plans picking one" "instead of refusing"

edit $PART 'return found != null && fits(found.part(), found.materials());' \
           'return found != null && found.materials().size() > 0;'
expect "matches() judging by a rule onlyKnown does not share" "by different"

echo "what a book costs to use"
edit $PART '                reusable = true;
                book = stack;' '                reusable = false;
                book = stack;'
expect "the book eaten by the craft that reads it" "consumed when it is used"

echo "a template is still a template"
edit $BOOK 'if (!plan.reusable()) {' 'if (plan == null) {'
expect "a single-use template learnable into permanent knowledge" "template can be learned"

edit $BOOK 'return NonNullList.withSize(input.size(), ItemStack.EMPTY);' \
           'return super.getRemainingItems(input); // NonNullList.withSize(input.size(), ItemStack.EMPTY)'
expect "the book handed back as a remainder, with the old call kept as a comment" \
       "returned as a remainder"

echo "what the book remembers"
edit $DATA 'if (part == null || part.isBlank() || knows(part)) {' \
           'if (part == null || part.isBlank()) { // knows(part)'
expect "a plan learned twice stored twice" "learned twice"

edit $DATA '        more.sort(String::compareTo);
' ''
expect "plans kept in the order they were read" "order they were learned"

echo "the book as an item"
python3 - <<'PY'
import re
p = "/tmp/Elysium-mono/elysium-armory/src/main/resources/assets/elysiumarmory/lang/en_us.json"
s = open(p, encoding="utf-8").read()
s = re.sub(r'\n\s*"item\.elysiumarmory\.plan_book":[^\n]*\n', '\n', s, count=1)
open(p, "w", encoding="utf-8").write(s)
PY
expect "an item with no name" "item.elysiumarmory.plan_book has no translation"

rm $R/assets/elysiumarmory/textures/item/plan_book.png
expect "a book nobody can see" "has no sprite"

rm $R/data/elysiumarmory/recipe/plan_book_item.json
expect "a book nobody can make" "cannot be made"

rm $R/data/elysiumarmory/recipe/plan_book.json
expect "a book nothing can teach" "can never learn a plan"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all armory checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "plan book: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
