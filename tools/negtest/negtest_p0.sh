#!/bin/bash
cd /tmp/Elysium-mono || exit 1
exec 9>/tmp/negtest_p0.lock
negtest_lock() { if command -v flock >/dev/null 2>&1; then flock -n "$1"; else mkdir "$2" 2>/dev/null && trap "rmdir '$2' 2>/dev/null" EXIT; fi; }  # Git Bash on Windows has no flock
negtest_lock 9 /tmp/negtest_p0.lockdir || { echo "another negtest_p0 is running; refusing"; exit 2; }
AT=elysium-lib/src/main/java/com/elysium/lib/attribute
SPEC=$AT/ElysiumAttributeSpec.java; REG=$AT/ElysiumAttributes.java; CH=$AT/ElysiumChanceAttribute.java
SM=$AT/ElysiumStackModifiers.java; CT=$AT/ElysiumContributorTable.java; DT=$AT/ElysiumDamageTypes.java
LIB=elysium-lib/src/main/java/com/elysium/lib/ElysiumLib.java
LANG=elysium-lib/src/main/resources/assets/elysiumlib/lang/en_us.json
BA=elysium-lib/src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json
PK=elysium-armory/src/main/java/com/elysium/armory/property/ArmoryPropertyKey.java
GT=elysium-armory/src/main/java/com/elysium/armory/property/ArmoryGearType.java
GC=elysium-ascendancy/src/main/java/com/elysium/ascendancy/weight/GenContext.java
FILES="$SPEC $REG $CH $SM $CT $DT $LIB $LANG $BA $PK $GT $GC"
pass=0; miss=0
snap()    { rm -rf /tmp/_p0; mkdir -p /tmp/_p0; for f in $FILES; do cp "$f" "/tmp/_p0/$(basename $f)"; done; }
restore() { for f in $FILES; do cp "/tmp/_p0/$(basename $f)" "$f"; done; rm -f elysium-lib/src/main/resources/data/elysiumlib/damage_type/extra.json; }
for v in lib armory ascendancy; do
  timeout 1700 python3 elysium-$v/validate.py >/dev/null 2>&1 || { echo "SETUP FAILED: $v not clean"; exit 2; }
done
echo "baseline clean"
try() { local label="$1" which="$2" want="$3" edit="$4"; snap
  if ! python3 -c "$edit"; then echo "SETUP FAILED: $label"; miss=$((miss+1)); restore; return; fi
  if timeout 1700 python3 elysium-$which/validate.py 2>&1 | grep -qF "$want"; then echo "CAUGHT : $label"; pass=$((pass+1))
  else echo "MISSED : $label  (expected: $want)"; miss=$((miss+1)); fi
  restore; }
R='
import sys, json
def rep(path, old, new):
    s = open(path, encoding="utf-8").read()
    if old not in s: sys.exit("anchor not found in " + path + ": " + old[:60])
    open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
'
try "attribute capped" lib "does not take its ceiling" "$R
rep('$REG', '''new RangedAttribute(key, spec.storedDefault(), spec.storedMin(),
                        ElysiumAttributeSpec.MAX)''', '''new RangedAttribute(key, spec.storedDefault(), spec.storedMin(),
                        1024.0D)''')"
try "attribute unsynced" lib "does not sync" "$R
rep('$REG', 'attribute.setSyncable(true);', '')"
try "registry off the bus" lib "never registers ElysiumAttributes" "$R
rep('$LIB', 'com.elysium.lib.attribute.ElysiumAttributes.register(modEventBus);', '')"
try "attribute lang missing" lib "has no lang entry" "$R
d=json.load(open('$LANG')); del d['attribute.elysiumlib.overheal']; json.dump(d,open('$LANG','w'),indent=2)"
try "chance display duplicated" lib "computes its own percentage" "$R
rep('$CH', 'return Component.literal(ElysiumAttributeSpec.percentText(value));', 'return Component.literal((ElysiumAttributeSpec.chance(value) * 100.0D) + \"%\");')"
try "chance stored linearly" lib "attributes harness failed" "$R
rep('$SPEC', 'return kind == Kind.CHANCE ? odds(nominalDefault) : nominalDefault;', 'return nominalDefault;')"
try "apotheosis attribute dropped" lib "attributes harness failed" "$R
rep('$SPEC', '            chance(\"overheal\", 0.0D),\n', '')"
try "table keeps registration order" lib "attributes harness failed" "$R
rep('$CT', 'new TreeMap<>()', 'new java.util.LinkedHashMap<>()')"
try "table replaces silently" lib "attributes harness failed" "$R
rep('$CT', 'if (byName.putIfAbsent(name, contributor) != null) {', 'if (byName.put(name, contributor) == \"never\") {')"
try "namespace check skipped" lib "without checking the contributor owns" "$R
rep('$SM', 'if (!ElysiumContributorTable.owns(name, modifier.id().toString())) {', 'if (false) {')"
try "contributor crash propagates" lib "does not catch a failing contributor" "$R
rep('$SM', '} catch (RuntimeException | LinkageError e) {', '} catch (StackOverflowError e) {')"
try "damage type without json" lib "damage_type/ holds" "$R
rep('$DT', 'public static final ResourceKey<DamageType> FIRE = key(\"fire_damage\");', 'public static final ResourceKey<DamageType> FIRE = key(\"fire_damage\");\n    public static final ResourceKey<DamageType> ACID = key(\"acid\");')"
try "death message missing" lib "dies with the raw key" "$R
d=json.load(open('$LANG')); del d['death.attack.elysiumlib.bleeding.player']; json.dump(d,open('$LANG','w'),indent=2)"
try "tag replaces vanilla" lib "replaces the tag" "$R
d=json.load(open('$BA')); d['replace']=True; json.dump(d,open('$BA','w'))"
try "tag names unknown type" lib "which is not a damage type here" "$R
d=json.load(open('$BA')); d['values'].append('elysiumlib:acid'); json.dump(d,open('$BA','w'))"
try "plain key beats specific" armory "PropertyHarness failed" "$R
rep('$PK', '''        if (type != null) {
            for''', '''        if (values.containsKey(new ArmoryPropertyKey(property, null))) {
            return values.get(new ArmoryPropertyKey(property, null));
        }
        if (type != null) {
            for''')"
try "gear type parent wrong" armory "PropertyHarness failed" "$R
rep('$GT', '{\"hammer\", \"pickaxe\"}', '{\"hammer\", \"harvest_tool\"}')"
try "armour split wrong" armory "PropertyHarness failed" "$R
rep('$PK', 'case \"chestplate\" -> 8.0D / 20.0D;', 'case \"chestplate\" -> 8.0D / 16.0D;')"
try "constraints inverted" ascendancy "WeightHarness failed" "$R
rep('$GC', 'dimensions == null || dimensions.isEmpty() || dimensions.contains(dimension)', 'dimensions == null || dimensions.isEmpty() || !dimensions.contains(dimension)')"
try "GenContext ignores tier" ascendancy "WeightHarness failed" "$R
rep('$GC', 'w.weightAt(tier, luck)', 'w.weightAt(4, luck)')"
echo "caught $pass, missed $miss"
[ "$miss" -eq 0 ]
