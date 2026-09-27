#!/usr/bin/env bash
# An ability that happens: six shapes, one branch each, nothing named twice.
set -uo pipefail
ROOT=/tmp/Elysium-mono
L=$ROOT/elysium-lib
B=$ROOT/elysium-bestiary
A=$ROOT/elysium-arcana
S=$L/src/main/java/com/elysium/lib/ability/ElysiumAbilityShapes.java
U=$L/src/main/java/com/elysium/lib/ability/act/ElysiumAbilityUse.java
H=$L/src/main/java/com/elysium/lib/ability/act/ElysiumAbilityEntities.java
E=$B/src/main/java/com/elysium/bestiary/registry/SummonEntities.java
R=$A/src/main/java/com/elysium/arcana/registry/RelicItems.java
SNAP=/tmp/abilities_snap.tar
pass=0; fail=0
lib()  { (cd $L && timeout 900 python3 validate.py 2>&1); }
best() { (cd $B && timeout 400 python3 validate.py 2>&1); }
arc()  { (cd $A && timeout 400 python3 validate.py 2>&1); }
if ! (cd $L && timeout 900 python3 validate.py >/dev/null 2>&1) \
   || ! (cd $B && timeout 400 python3 validate.py >/dev/null 2>&1) \
   || ! (cd $A && timeout 400 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: one of the three modules does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-lib elysium-bestiary elysium-arcana)
restore() { rm -rf $ROOT/elysium-lib $ROOT/elysium-bestiary $ROOT/elysium-arcana
            (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
expect() { local w="$2" g; g="$($3)"
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

echo "every ability says what it does"
edit $S '        made.put("blink", STEP);' '        made.put("blinking", STEP);'
expect "an ability nothing says anything about" "does nothing at all" lib

edit $S '        made.put("gravewake", SUMMON);' '        made.put("gravewake", "wibble");'
expect "an ability that is a shape nothing can perform" "which is not a shape" lib

edit $S '        made.put("hunters_mark", BOLT);
        made.put("stormcall", BOLT);' '        made.put("hunters_mark", SELF);
        made.put("stormcall", SELF);'
expect "a shape with nothing of that shape left" "so one of the six" lib

edit $S '            if (shape.isEmpty()) {' '            if (false) {'
expect "a shape check that cannot report an ability that does nothing" "is not reported, so the whole roster could go back to being priced and inert" lib

edit $S '            if (!shape.isEmpty() && !SHAPES.contains(shape)) {' '            if (false) {'
expect "a shape check that cannot report a shape nothing can perform" "that is a shape nothing can perform is not reported" lib

edit $S '            if (!named.contains(id)) {' '            if (false) {'
expect "a shape check that cannot report a shape given to nothing" "given to something that is not an ability is not reported" lib

echo "how much, derived rather than typed"
edit $S '        return LEAST_REACH + (MOST_REACH - LEAST_REACH)
                * (float) Math.sqrt(fraction(ability));' '        return LEAST_REACH + (MOST_REACH - LEAST_REACH)
                * (1.0F - (float) Math.sqrt(fraction(ability)));'
expect "a reach that shrinks as an ability gets dearer" "and reaches less far" lib

edit $S '    public static final float MOST_REACH = 8.0F;' '    public static final float MOST_REACH = 1.0F;'
expect "a ceiling on reach under the floor" "blocks and nothing reaches less than" lib

edit $S '        return Math.round(LEAST_TICKS + (MOST_TICKS - LEAST_TICKS)
                * fraction(ability));' '        return Math.round(LEAST_TICKS + (MOST_TICKS - LEAST_TICKS)
                * (1.0F - fraction(ability)));'
expect "a duration that shrinks as an ability gets dearer" "and lasts less long" lib

edit $S '        return Math.max(1, Math.min(MOST_AT_ONCE,
                Math.round(ability.worth())));' '        return Math.round(ability.worth()) * 40;'
expect "an ability that puts forty things in the world" "which is the line between an ability and a performance problem" lib

edit $S '        if (!SUMMON.equals(shape) && !BOLT.equals(shape)) {
            return 0;
        }' '        if (false) {
            return 0;
        }'
expect "an always-on ability counted as putting things in the world" "is counted as putting" lib

edit $S '            if (throwsSomething && count < 1) {' '            if (false) {'
expect "a count check that cannot report an ability that throws nothing" "a summoning ability that summons nothing is not reported" lib

echo "the one place an ability happens"
edit $U '            case ElysiumAbilityShapes.SUMMON -> calledUp(ability, user);' '            case ElysiumAbilityShapes.SELF -> calledUp(ability, user);'
expect "a shape with no branch to perform it" "so every ability of that shape is priced, carried and silent" lib

edit $U '        String shape = ElysiumAbilityShapes.shapeOf(ability.id());' '        String shape = ability.id().equals("blink")
                ? ElysiumAbilityShapes.STEP
                : ElysiumAbilityShapes.shapeOf(ability.id());'
expect "the one place that performs an ability starting to name one" "it has started growing a branch per ability" lib

edit $S '    public static float reachOf(ElysiumAbilityRules.Ability ability) {' '    public static strictfp float reachOf(ElysiumAbilityRules.Ability ability) {'
expect "the reach taken out from under the validator" "so a reach would be typed at each of the places that perform one" lib

echo "the two entities the abilities cannot happen without"
edit $E '            ElysiumAbilityEntities.summon(SHADE::get);' '            ElysiumAbilityEntities.bolt(BOLT::get);'
expect "a summoning ability with nothing to summon" "so every ability of that shape does nothing and says nothing" best

edit $E '            if (!ElysiumAbilityEntities.missing().isEmpty()) {' \
        '            if (false) {'
expect "nothing checking that every shape was given something" "ship as a relic that does nothing" best

edit $E '        event.enqueueWork(() -> {' '        if (true) {'
expect "entity types handed over before their registry is frozen" "asked for before their registry is frozen" best

edit $B/src/main/java/com/elysium/bestiary/entity/ShadeEntity.java '            discard();' '            setHealth(getHealth());'
expect "a summon that never goes out" "so an ability with a cooldown is a way of filling a world with entities" best

rm -f $B/src/main/java/com/elysium/bestiary/entity/RelicBoltEntity.java
expect "nothing to throw" "there is nothing to throw" best

echo "a relic you press"
edit $R '            if (!ElysiumAbilityUse.perform(active, player)) {' '            if (false) {'
expect "a relic that does its own thing on use" "rather than asking the library that knows what an ability is" arc

edit $R '            player.getCooldowns().addCooldown(this, active.cooldown());' '            player.getCooldowns().addCooldown(this, 20);'
expect "a relic whose cooldown is not the ability's own" "the tooltip says one number and the item obeys another" arc

edit $R '            if (player.getCooldowns().isOnCooldown(this)) {' '            if (false) {'
expect "a relic that can be pressed through its own cooldown" "the cooldown is a line in a tooltip" arc

edit $R '                if (ability != null
                        && ElysiumAbilityRules.ACTIVE.equals(ability.kind())
                        && wanted.equals(ability.trigger())) {' '                if (ability != null
                        && wanted.equals(ability.trigger())) {'
expect "a relic that fires whichever ability it finds first" "could set off something that is always on" arc

echo
echo "restoring and re-checking"
restore
g="$(lib)"; h="$(best)"; i="$(arc)"
if [[ "$g" == *"all library checks passed"* && "$h" == *"all bestiary checks passed"* \
      && "$i" == *"all arcana checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "abilities: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
