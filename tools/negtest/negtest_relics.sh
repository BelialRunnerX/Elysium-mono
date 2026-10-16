#!/usr/bin/env bash
# Thirty relics, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arcana
R=$M/src/main/java/com/elysium/arcana/rules/RelicRules.java
SNAP=/tmp/relics_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-arcana elysium-lib/src/main/java/com/elysium/lib/ability)
restore() { rm -rf $ROOT/elysium-arcana $ROOT/elysium-lib/src/main/java/com/elysium/lib/ability && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 500 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-140)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the join with the library"
edit $R 'relic("wanderers_charm", "charm", 1, List.of("swiftfoot")),' 'relic("wanderers_charm", "charm", 1, List.of("fleetfoot")),'
expect "a relic naming an ability the library does not have" "the library does not have"

edit $R 'relic("stormcallers_rod", "relic", 4, List.of("stormcall")),' 'relic("stormcallers_rod", "relic", 4, List.of("gravewake")),'
expect "an ability nothing in the world carries" "exist only in a table"

edit $R 'relic("blinkstep_boots", "boots", 3, List.of("blink", "swiftfoot")),' 'relic("blinkstep_boots", "boots", 3, List.of("blink", "blink", "swiftfoot")),'
expect "a relic carrying the same ability twice" "no item is allowed to carry"

edit $R 'relic("wardens_regalia", "relic", 4, List.of("ward_pulse", "ironhide", "mender")));' 'relic("wardens_regalia", "relic", 4, List.of("ward_pulse", "ironhide", "mender", "cleave")));'
expect "a relic carrying more abilities than an item may" "no item is allowed to carry"

echo "what rarity means"
python3 - <<'PYR' || { echo "  STALE ANCHOR, counted as a miss"; fail=$((fail+1)); }
import pathlib, sys
p = pathlib.Path("/tmp/Elysium-mono/elysium-arcana/src/main/java/com/elysium/arcana/rules/RelicRules.java")
s = p.read_text(encoding="utf-8")
pairs = [('relic("gravewake_censer", "relic", 4, List.of("gravewake")),',
          'relic("gravewake_censer", "relic", 4, List.of("swiftfoot")),'),
         ('relic("stormcallers_rod", "relic", 4, List.of("stormcall")),',
          'relic("stormcallers_rod", "relic", 4, List.of("keen_edge")),')]
for old, new in pairs:
    if old not in s:
        sys.stderr.write("STALE ANCHOR in RelicRules :: " + old[:60] + "\n"); sys.exit(2)
    s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")
PYR
expect "a rarity band that does less than the one below it" "rarer relic is the weaker one"

edit $R 'relic("sure_hands", "ring", 1, List.of("sure_grip")),' 'relic("sure_hands", "charm", 1, List.of("night_eyes")),'
expect "two relics that are the same relic under two names" "same relic under two names"

echo "the registry, which a hand-written relic would go through and the harness would not"
edit $M/src/main/java/com/elysium/arcana/registry/RelicItems.java \
  'ElysiumAbilityRules.cannotShareAnItem(relic.abilities());' 'java.util.List.of();'
expect "a registry that no longer checks what it is about to register" "can carry anything"

edit $M/src/main/java/com/elysium/arcana/registry/RelicItems.java \
  'for (RelicRules.Relic relic : RelicRules.RELICS) {' 'for (RelicRules.Relic relic : RelicRules.RELICS.subList(0, 5)) {'
expect "a registry that stops walking the roster" "no longer walks the roster"

edit $M/src/main/java/com/elysium/arcana/registry/RelicItems.java \
  'String.format("%.1f", ability.cooldown() / 20.0F))' 'String.format("%.1f", 3.0F))'
expect "a tooltip that stops reading the cooldown off the ability" "stops being true"

echo "the passives, fired from the events that mean those words"
P=$M/src/main/java/com/elysium/arcana/event/RelicPassives.java
edit $P '                    if (ability.cooldown() > 0) {
                        player.getCooldowns().addCooldown(item, ability.cooldown());
                    }
' ''
edit $P '                        player.getCooldowns().removeCooldown(item);
                    }' '                        player.getCooldowns().removeCooldown(item);
                    }
                    if (ability.cooldown() > 0) {
                        player.getCooldowns().addCooldown(item, ability.cooldown());
                    }'
expect "an on-hit whose cooldown goes on after it fires" "goes on after the ability fires"

edit $P '        if (!FIRING.add(firing)) {
            return;
        }' '        FIRING.add(firing);'
expect "an on-hit let back in from inside its own blow" "refuses a trigger fired from inside itself"

edit $P '        } finally {
            FIRING.remove(firing);
        }' '        } finally {
        }'
expect "a guard that is never cleared" "clear its guard in a finally"

edit $P '        if (!FIRING.add(firing)) {
            return;
        }' '        if (!FIRING.add(firing)) {
            return;
        }
        FIRING.remove(firing);'
expect "a guard let go before the ability fires" "lets go of its guard before the ability has fired"

echo "the world the relics are actually in"
rm -f $M/src/main/resources/data/elysiumarcana/recipe/phoenix_down.json
expect "a relic that cannot be made" "a relic needs are not there"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-arcana/src/main/resources/assets/elysiumarcana/lang/en_us.json")
d = json.loads(p.read_text(encoding="utf-8"))
d.pop("elysiumarcana.ability.veilstep", None)
p.write_text(json.dumps(d, indent=2) + "\n", encoding="utf-8")
PYX
expect "an ability with no line for the tooltip to show" "a relic needs are not there"

echo "and the rules staying pure"
edit $R 'import com.elysium.lib.ability.ElysiumAbilityRules;' 'import com.elysium.lib.ability.ElysiumAbilityRules;
import com.elysium.arsenal.rules.WeaponRules;'
expect "a table reaching sideways into another module" "the JDK and the library"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all arcana checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN: $(echo "$g" | grep -A1 PROBLEM | tail -1 | cut -c1-160)"; fail=$((fail+1)); fi
echo "relics: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
