#!/usr/bin/env bash
# Packs, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-logistics
P=$M/src/main/java/com/elysium/logistics/rules/PackRules.java
H=$M/tools/PackHarness.java
RI=$M/src/main/java/com/elysium/logistics/registry/LogisticItems.java
G=$M/tools/gen_logistics.py
V=$M/validate.py
SNAP=/tmp/pack_snap
OUT=/tmp/pack_neg
KIN=$ROOT/elysium-kinetics/src/main/java/com/elysium/kinetics/rules
IND=$ROOT/elysium-industry/src/main/java/com/elysium/industry/rules
pass=0; fail=0
if ! python3 "$V" >/dev/null 2>&1; then
  echo "REFUSING TO START: the module does not validate before any edit is made."
  python3 "$V" 2>&1 | tail -5
  exit 3
fi
rm -rf $SNAP && mkdir -p $SNAP
cp -a $M/src/main/java/com/elysium/logistics $SNAP/logistics
cp -a $M/tools $SNAP/tools
cp -a $M/src/main/resources $SNAP/resources
cp -a $M/validate.py $SNAP/validate.py
restore() {
  rm -rf $M/src/main/java/com/elysium/logistics $M/tools $M/src/main/resources
  cp -a $SNAP/logistics $M/src/main/java/com/elysium/logistics
  cp -a $SNAP/tools $M/tools
  cp -a $SNAP/resources $M/src/main/resources
  cp -a $SNAP/validate.py $M/validate.py
}
trap 'restore' EXIT INT TERM
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT $M/src/main/java/com/elysium/logistics/rules/*.java $KIN/*.java $IND/*.java "$H" 2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.logistics.rules.PackHarness 2>&1 | grep -v JAVA_TOOL
}
worldout() { python3 "$V" 2>&1 | grep -v JAVA_TOOL; }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
wexpect() { local w="$2" g; g="$(worldout)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_logistics.py >/dev/null 2>&1); }

echo "the one thing every mod of this kind gets wrong"
edit $P '        return stored <= capacity(body, after);' '        return true;'
expect "a full bag giving up its deep pocket, which is where the items go missing" "the check that stops a bag losing what is in it cannot say no"

edit $P '        if (!mayRemove(body, fitted, going, stored)) {
            return new int[] {stored, capacity(body, fitted)};
        }' '        if (false) {
            return new int[] {stored, capacity(body, fitted)};
        }'
expect "a removal that goes through whether or not what is inside still fits" "so the difference stops existing and nobody can tell what was in it"

echo "capacity is the sum and never more"
edit $P '        int room = body.slots();
        for (Part one : fitted) {
            room += one.slots();
        }
        return room;' '        int room = body.slots() + 1;
        for (Part one : fitted) {
            room += one.slots();
        }
        return room;'
expect "a bag that reports more room than its parts add up to" "and its own size is"

edit $P '            if (slots > 0 != SPACE.equals(kind)) {' '            if (false) {'
expect "a guard that would let a part add room without being a space part" "so capacity is no longer the sum of what is fitted"

echo "a part fitted twice"
edit $P '            if (!seen.add(one.name())) {
                why.add(one.name() + " is fitted twice, which either does nothing "' '            if (false) {
                why.add(one.name() + " is fitted twice, which either does nothing "'
expect "the same part fitted twice" "the same part twice was allowed"

echo "a bag inside a bag"
edit $P '            if (body(one.name()) != null) {' '            if (false) {'
expect "a bag fitted inside a bag, which makes capacity a recursion" "capacity is a recursion with no bottom to it"

echo "the one place anything may be destroyed"
edit $P '        return voids(fitted) ? 0 : over;' '        return 0;'
expect "a bag with no seal quietly eating what would not fit" "where anything may be destroyed"

edit $P '            if (VOID.equals(one.kind())) {
                return true;
            }' '            if (VOID.equals(one.kind())) {
                return false;
            }'
expect "a void seal that does not void" "did not void what would not fit"

edit $P '        int room = capacity(body, fitted) - stored;
        return Math.max(0, Math.min(room, coming));' '        int room = capacity(body, fitted) - stored;
        return Math.max(0, Math.min(room + 1, coming));'
expect "a bag taking in one more than it has room for" "of room"

echo "how many parts a body takes"
edit $P '        if (fitted.size() > body.fittings()) {' '        if (false) {'
expect "a body taking more parts than it has slots for" "part(s) and took"

edit $P '            new Body("knapsack", 18, 2, true),' '            new Body("knapsack", 8, 2, true),'
expect "a rung of the ladder that holds less than the rung below it" "so one of the two is a rung nobody climbs"

echo "the world the packs are actually in"
edit $RI '        for (String name : PackRules.catalogue()) {
            PackRules.Body body = PackRules.body(name);' '        for (String name : PackRules.catalogue().subList(0, 3)) {
            PackRules.Body body = PackRules.body(name);'
wexpect "packs listed by hand rather than walked from the roster" "not registered by walking the roster"

edit $RI '        if (made.size() != PackRules.howMany()) {' '        if (false) {'
wexpect "a registry that does not count what it registered" "counts the packs registered"

# A bag, a crate's item and a part are registered by three statements now; the
# bag's is the first and the one that matters most.
edit $RI '                        properties -> new PackItem(properties, body),
                        new Item.Properties().stacksTo(1)));' '                        properties -> new PackItem(properties, body),
                        new Item.Properties()));'
wexpect "a bag that stacks to sixty-four" "every one of them holds what the one on top holds"

echo "the ladder in the recipes"
edit $G '    "rucksack": (["ILI", "LBL", "LLL"],
                 {"I": {"item": "minecraft:iron_ingot"},
                  "L": {"item": "minecraft:leather"},
                  "B": {"item": f"{NS}:haversack"}}),' '    "rucksack": (["ILI", "LBL", "LLL"],
                 {"I": {"item": "minecraft:iron_ingot"},
                  "L": {"item": "minecraft:leather"},
                  "B": {"item": "minecraft:leather"}}),'
regen
wexpect "a bag not built out of the bag below it, so the one below is a dead end" "is a dead end and a player who made one has wasted it"

edit $G '    "large_pocket": (["LLL", "LPL", "LLL"],
                     {"L": {"item": "minecraft:leather"},
                      "P": {"item": f"{NS}:small_pocket"}}),' '    "large_pocket": (["LLL", "LPL", "LLL"],
                     {"L": {"item": "minecraft:leather"},
                      "P": {"item": "minecraft:string"}}),'
regen
wexpect "a pocket not built out of the pocket below it" "so the smaller pocket is a dead end"

edit $G '    "satchel": (["SLS", "L L", "LLL"],
                {"S": {"item": "minecraft:string"},
                 "L": {"item": "minecraft:leather"}}),' '    "satchel": (["SLS", "L L", "LLL"],
                {"S": {"item": f"{NS}:vault"},
                 "L": {"item": "minecraft:leather"}}),'
regen
wexpect "a first rung built out of a rung further up the ladder" "cannot be built out of a rung"

echo "every part drawn as its own thing"
edit $G '        colour = PART_COLOURS.get(kind)' '        colour = PART_COLOURS.get("space")'
regen
wexpect "every part drawn in one colour, so a row of them says nothing" "says nothing about what is fitted"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "packs: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
