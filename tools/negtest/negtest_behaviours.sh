#!/usr/bin/env bash
# A mechanic apiece, broken one way at a time.
#
# Nine beasts on three sets of family goals is nine rows of numbers and three
# fights. Every rule below is a way that could quietly go back to being true.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
B=$M/src/main/java/com/elysium/bestiary/rules/BehaviourRules.java
A=$M/src/main/java/com/elysium/bestiary/entity/act/BeastActs.java
EN=$M/src/main/java/com/elysium/bestiary/entity/BeastEntity.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/behaviours_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
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
regen() { (cd $M && python3 tools/gen_bestiary.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }

echo "one verb each, and none spare"
edit $B '            if (verb == null || verb.isEmpty()) {
                bad.add(beast.id() + " does nothing in particular' '            if (false) {
                bad.add(beast.id() + " does nothing in particular'
expect "a beast that does nothing in particular, unreported" "anything in particular is not"

edit $B '            if (!VERBS.contains(verb)) {' '            if (false) {'
expect "a beast that does something nothing can perform, unreported" "nothing can perform is not reported"

edit $B '            if (!taken.add(verb)) {' '            if (false) {'
expect "two beasts that fight the same way, unreported" "do the same thing are not reported"

edit $B '            if (!anybody) {' '            if (false) {'
expect "a verb written, branched and given to nobody, unreported" "a verb nothing does is not reported"

edit $B '            if (!real) {' '            if (false) {'
expect "something that is not a beast given something to do, unreported" "given something to do is"

echo "a wait and a strength somebody would notice"
edit $B '            if (waits < soonest) {' '            if (false) {'
expect "a mechanic that never stops happening, unreported" "react to is not reported"

edit $B '            if (waits > latest) {' '            if (false) {'
expect "a mechanic most fights end before, unreported" "most fights end before is not reported"

edit $B '            if (hard < least || hard > most) {' '            if (false) {'
expect "a strength outside its own band, unreported" "under its own floor is not reported"

edit $B '            if (reach < least || reach > most) {' '            if (false) {'
expect "a reach outside its own band, unreported" "a reach under its own floor is not reported"

edit $B '            if (reach < beast.width()) {' '            if (false) {'
expect "a beast that cannot touch what is standing against it, unreported" "wider than anything its mechanic can touch is not"

echo "the ladder the mechanics climb"
edit $B '                if (strengthOf(above, beasts) <= strengthOf(below, beasts)) {' \
        '                if (false) {'
expect "a tier that buys a bigger health bar and nothing else, unreported" "is not reported, so climbing a ladder"

edit $B '                if (waitsOf(above, beasts) >= waitsOf(below, beasts)) {' \
        '                if (false) {'
expect "a tier whose mechanic happens no oftener, unreported" "happens less often than its"


edit $B '        return (float) (LEAST_STRENGTH
                + (MOST_STRENGTH - LEAST_STRENGTH) * fraction(beast, all));' \
        '        return (float) (MOST_STRENGTH
                - (MOST_STRENGTH - LEAST_STRENGTH) * fraction(beast, all));'
expect "a mechanic that lands softer the more dangerous the beast" "lands no harder than"

edit $B '        return (int) Math.round(LATEST - (LATEST - SOONEST) * fraction(beast, all));' \
        '        return (int) Math.round(SOONEST + (LATEST - SOONEST) * fraction(beast, all));'
expect "a mechanic that happens less often the more dangerous the beast" "does what it does no more often than"

edit $B '        return Math.max(0.0D, Math.min(1.0D, (beast.threat() - least) / (most - least)));' \
        '        return 0.0D;'
expect "every beast sitting at the bottom of the band whatever it is" "lands no harder than"

edit $B '        bad.addAll(faultsInSpares(VERBS, DOES));' '        '
expect "a behaviour rule that answers its controls and is never asked about the roster" "never added up by the"

echo "the one place it happens"
edit $A '            case BehaviourRules.CHILL -> cools(beast, at, reach, strength);' '            '
expect "a verb with no branch to happen in" "nothing performs chill"

edit $A '        BeastRules.Beast row = beast.beast();
        String verb = BehaviourRules.verbOf(row.id());' \
        '        BeastRules.Beast row = beast.beast();
        String verb = row.id().equals("shardling")
                ? BehaviourRules.SPLIT
                : BehaviourRules.verbOf(row.id());'
expect "the one place a mechanic happens starting to name a beast" "has started growing a branch per beast"

edit $A '        float strength = BehaviourRules.strengthOf(row, all);' '        float strength = 1.0F;'
expect "how hard a mechanic lands, typed beside the rules" "typed here rather than worked out from the beast"

edit $A '        float reach = BehaviourRules.reachOf(row);' '        float reach = 4.0F;'
expect "how far a mechanic reaches, typed beside the rules" "worked out from how big the beast is"

edit $A '        if (beast.isAlive() || beast.piece()) {' '        if (beast.isAlive()) {'
expect "a piece that comes apart into more pieces" "nothing stops a piece coming apart"

edit $A '        if (family.isEmpty() || beast.called() >= MOST_CALLED) {' '        if (family.isEmpty()) {'
expect "a beast that calls up its family without limit" "nothing caps how many of its family"

echo "the clock, and the death that cannot be on one"
edit $EN '                waits = com.elysium.bestiary.rules.BehaviourRules.waitsOf(beast(),
                        BeastRules.BEASTS);' '                waits = 100;'
expect "a wait typed into the entity rather than derived" "not the number the rules worked out"

edit $EN '            } else if (com.elysium.bestiary.entity.act.BeastActs.perform(this,
                    getTarget())) {' '            } else if (false) {'
expect "nothing asking a beast to do what it does while it is alive" "while it is alive"

edit $EN '            com.elysium.bestiary.entity.act.BeastActs.perform(this, getTarget());
        }
    }

    @Override
    public void addAdditionalSaveData' '        }
    }

    @Override
    public void addAdditionalSaveData'
expect "nothing asking a beast to do what it does when it dies" "the one verb that can only happen then"

edit $EN '                Math.max(1.0D, beast().health() / 2.0D));' '                Math.max(1.0D, beast().health()));'
expect "a piece with as much health as the thing it came off" "one mob is worth two"

edit $EN '        this.xpReward = 0;' '        this.xpReward = 1;'
expect "a piece worth experience, which is the best thing in the pack to farm" "is worth experience"

echo "the words a player is given"
edit $G '    for verb, words in sorted(VERB_WORDS.items()):
        lang["bestiary.%s.does.%s" % (NS, verb)] = words' '    for verb, words in []:
        lang["bestiary.%s.does.%s" % (NS, verb)] = words'
expect_regen "a mechanic a player meets and is told nothing about" "is told nothing about what happened"

edit $G '    for beast_id, verb in sorted(DOES.items()):
        lang["bestiary.%s.mechanic.%s" % (NS, beast_id)] = titled(verb)' \
        '    for beast_id, verb in []:
        lang["bestiary.%s.mechanic.%s" % (NS, beast_id)] = titled(verb)'
expect_regen "a mechanic that reads as its own translation key" "prints a translation key at a player"

edit $B '    public static final String CHILL = "chill";' '    public static final String CHILL = "chilled";'
expect "a verb renamed out from under the words written for it" "the generator cannot read the Java any more"

echo "the checks themselves"
edit $V '    _verbs = re.search(r"List<String> VERBS = List\.of\((.*?)\); ", _one_line)' \
        '    _verbs = re.search(r"List<String> VERBZ = List\.of\((.*?)\); ", _one_line)'
expect "a verb sweep that reads no verbs at all" "could be read out of the rules"

edit $V '    _rules = set(re.findall(r"public static List<String> (faultsIn\w+)\(",
                            BEHAVIOURS))' '    _rules = set(re.findall(r"public static List<String> (faultsInZ\w+)\(",
                            BEHAVIOURS))'
expect "a rule sweep that finds no behaviour rules at all" "behaviour rule(s) are declared at all"

rm -f $A
expect "nothing performing any of it at all" "no beast does anything at all"

rm -f $B
expect "nothing saying what any beast does" "nothing says what any beast does"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all bestiary checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "behaviours: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
