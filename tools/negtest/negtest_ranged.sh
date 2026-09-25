#!/usr/bin/env bash
# Thirty-six bows, twelve kinds of ammunition and twenty-four named weapons,
# broken one way at a time.
#
# Two harnesses, because the two halves fail differently. The arithmetic is
# javac-and-run in about a second, so most of this drives that. The world —
# sprites, models, recipes, tags, registration, the draw — only the module
# validator can see, and that one runs three harnesses first and takes a
# minute, so it is used only where it has to be.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arsenal
R=$M/src/main/java/com/elysium/arsenal/rules/RangedRules.java
U=$M/src/main/java/com/elysium/arsenal/rules/UniqueRules.java
H=$M/tools/RangedHarness.java
A=$ROOT/elysium-lib/src/main/java/com/elysium/lib/ability/ElysiumAbilityRules.java
W=$M/src/main/java/com/elysium/arsenal/rules/WeaponRules.java
AR=$M/src/main/java/com/elysium/arsenal/rules/ArsenalRules.java
E=$M/src/main/java/com/elysium/arsenal/event/ArrowEffects.java
C=$M/src/main/java/com/elysium/arsenal/client/ArsenalClient.java
RI=$M/src/main/java/com/elysium/arsenal/registry/RangedItems.java
UI=$M/src/main/java/com/elysium/arsenal/registry/UniqueItems.java
G=$M/tools/gen_arsenal.py
V=$M/validate.py
SNAP=/tmp/ranged_snap
OUT=/tmp/ranged_out
pass=0; fail=0
# A plain copy of the four things every test below edits, and a plain copy
# back afterwards. Whatever a test breaks, the next one starts from the tree
# as it was -- otherwise every failure after the first is a consequence of
# the first and none of them mean anything.
rm -rf $SNAP && mkdir -p $SNAP
cp -a $M/src/main/java/com/elysium/arsenal $SNAP/arsenal
cp -a $M/tools $SNAP/tools
cp -a $M/src/main/resources $SNAP/resources
cp -a $M/validate.py $SNAP/validate.py
restore() {
  rm -rf $M/src/main/java/com/elysium/arsenal $M/tools $M/src/main/resources
  cp -a $SNAP/arsenal $M/src/main/java/com/elysium/arsenal
  cp -a $SNAP/tools $M/tools
  cp -a $SNAP/resources $M/src/main/resources
  cp -a $SNAP/validate.py $M/validate.py
}
out() {
  rm -rf $OUT && mkdir -p $OUT
  if ! javac -nowarn -d $OUT "$R" "$U" "$H" "$A" "$W" "$AR" 2>&1 | grep -v JAVA_TOOL; then :; fi
  java -cp $OUT com.elysium.arsenal.rules.RangedHarness 2>&1 | grep -v JAVA_TOOL
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
regen() { (cd $M && python3 tools/gen_arsenal.py >/dev/null 2>&1); }

echo "the six kinds, and whether any of them is a worse version of another"
edit $R 'new Bow("recurve", 1.6F, 6.0F, 1.60F, 1, 0.80F, 0.85F),' 'new Bow("recurve", 0.7F, 3.0F, 0.60F, 1, 0.40F, 0.40F),'
expect "a bow beaten on every axis by another" "beats"

edit $R 'new Bow("shortbow", 2.4F, 4.0F, 1.00F, 1, 0.70F, 1.00F),' 'new Bow("shortbow", 2.3F, 3.9F, 0.99F, 1, 0.69F, 0.94F),'
expect "a bow that is the best in the set at nothing" "the answer to no question"

edit $R '            return draw >= other.draw() && damage >= other.damage()' '            return damage >= other.damage()'
expect "a comparison that has stopped looking at how fast a bow draws" "dominated"

edit $R '    public static final int AXES = 6;' '    public static final int AXES = 5;'
expect "an axis nobody is compared on, so one kind owns two corners" "corners"

edit $R 'new Bow("crossbow", 0.6F, 10.0F, 1.20F, 1, 1.00F, 0.60F));' 'new Bow("crossbow", 0.6F, 10.0F, 1.20F, 1, 0.80F, 0.60F));'
expect "two kinds sharing a corner and another corner empty" "corners"

echo "the ladder"
edit $R '    public static final List<String> LADDER = WeaponRules.LADDER;' '    public static final List<String> LADDER = TIERS;'
expect "a ladder with gold in registry order, which dips in the middle" "gold is on the ladder"

echo "the bow the game already ships"
edit $R '    public static final float VANILLA_BOW_DAMAGE = 9.0F;' '    public static final float VANILLA_BOW_DAMAGE = 1.0F;'
expect "our bows deleting the game's own" "delete the game's own"

edit $R '    public static final float VANILLA_BOW_DAMAGE = 9.0F;' '    public static final float VANILLA_BOW_DAMAGE = 900.0F;'
expect "every bow here worse than the game's own" "worse at shooting"

echo "the twelve things a bow can fire"
edit $R 'new Shot("iron_arrow", 1.0F, "", false),' 'new Shot("iron_arrow", 0.0F, "", false),'
expect "two kinds of ammunition that are one kind under two names" "under a different name"

edit $R '    public static boolean takesBolts(Bow kind) {
        return "crossbow".equals(kind.name());' '    public static boolean takesBolts(Bow kind) {
        return "longbow".equals(kind.name());'
expect "a bow that takes the wrong ammunition for its name" "which is not what its name says"

edit $R 'new Shot("barbed_arrow", 2.0F, "bleed", false),' 'new Shot("barbed_arrow", 4.0F, "bleed", false),
            new Shot("worse_barb", 3.0F, "bleed", false),'
expect "a shot that does what another does and hits harder" "does and hits harder"

echo "the twenty-four that have names"
edit $U 'unique("stormfang", "sabre", "netherite", 3, List.of("static_arc", "swiftfoot")),' 'unique("stormfang", "sabre", "iron", 3, List.of("static_arc", "swiftfoot")),'
expect "two uniques smithed from the same weapon" "cannot be made"

edit $U 'unique("kingsmourn", "longsword", "netherite", 3, List.of("cleave", "keen_edge")),' 'unique("kingsmourn", "longsword", "netherite", 3, List.of("thornmail", "lifeline", "cleave")),'
expect "a netherite unique over the whole item ceiling" "one item may carry is"

edit $U 'unique("whisperfang", "sabre", "iron", 1, List.of("swiftfoot")),' 'unique("whisperfang", "sabre", "iron", 1, List.of("thornmail")),'
expect "an iron unique carrying a netherite unique's ability" "the band there is"

edit $U 'unique("needlepoint", "estoc", "iron", 1, List.of("night_eyes")),' 'unique("needlepoint", "estoc", "iron", 1, List.of("sleepwalking")),'
expect "a unique naming an ability the library does not have" "not an ability the library has"

edit $U 'unique("pebblethrower", "sling", "iron", 1, List.of("gleaner")),' 'unique("pebblethrower", "shortbow", "iron", 1, List.of("gleaner")),'
expect "a shape with nothing unique built on it" "stops being interesting"

edit $U 'unique("hailstorm", "repeater", "diamond", 2, List.of("chill_touch")),' 'unique("hailstorm", "spear", "diamond", 2, List.of("chill_touch")),'
expect "two uniques that are one unique under two names" "under two names"

edit $U '            made.put(TIERS.get(rung), ElysiumAbilityRules.MOST_ON_AN_ITEM_WORTH
                    / (1 << (rungs - 1 - rung)));' '            made.put(TIERS.get(rung), ElysiumAbilityRules.MOST_ON_AN_ITEM_WORTH);'
expect "bands that do not widen as the tiers climb" "climbing buys nothing"

edit $U '            if (one.worth() > allowed) {' '            if (false) {'
expect "a band that refuses nothing" "over the iron band"

echo "the checkers themselves, which is where this module has been wrong before"
edit $R '        public boolean dominates(Bow other) {
            return atLeastAsGoodAs(other) && betterSomewhereThan(other);' '        public boolean dominates(Bow other) {
            return false;'
expect "a domination check that cannot report anything" "cannot report anything"

edit $R '        public boolean dominates(Bow other) {
            return atLeastAsGoodAs(other) && betterSomewhereThan(other);' '        public boolean dominates(Bow other) {
            return atLeastAsGoodAs(other) || betterSomewhereThan(other);'
expect "a side-grade reported as dominated" "the only interesting thing a bow set can do"

echo "the world the bows are actually in"
edit $G '            write(os.path.join(ASSETS, "models/item", id + ".json"), bow_model(id))
            made += 1
            for frame in range(3):' '            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": "item/generated",
                   "textures": {"layer0": f"{NS}:item/{id}"}})
            made += 1
            for frame in range(3):'
regen
wexpect "a bow with no draw frames, which holds one picture through the whole draw" "no draw frames"

edit $G '                write(os.path.join(ASSETS, "models/item", drawn + ".json"),
                      {"parent": f"{NS}:item/{id}",
                       "textures": {"layer0": f"{NS}:item/{drawn}"}})
                sprites += 1
                made += 1
            write(os.path.join(DATA, "recipe", id + ".json"), {' '                write(os.path.join(ASSETS, "models/item", drawn + ".json"),
                      {"parent": "item/generated",
                       "textures": {"layer0": f"{NS}:item/{drawn}"}})
                sprites += 1
                made += 1
            write(os.path.join(DATA, "recipe", id + ".json"), {'
regen
wexpect "a draw frame that snaps flat the instant a draw begins" "does not inherit"

edit $G '    write(os.path.join(OUT, "src/main/resources/data/minecraft/tags/item",
                       "arrows.json"),
          {"replace": False,
           "values": [f"{NS}:{name}" for name, _, bolt in ammunition if not bolt]})' '    write(os.path.join(OUT, "src/main/resources/data/minecraft/tags/item",
                       "arrows.json"),
          {"replace": False, "values": []})'
regen
wexpect "arrows no bow in the game will accept" "the game's arrow tag"

edit $G '        write(os.path.join(DATA, "recipe", id + ".json"), {
            "type": "minecraft:smithing_transform",
            "template": {"item": f"{NS}:legend_sigil"},
            "base": {"item": f"{NS}:{tier}_{shape}"},' '        write(os.path.join(DATA, "recipe", id + ".json"), {
            "type": "minecraft:smithing_transform",
            "template": {"item": f"{NS}:legend_sigil"},
            "base": {"item": "minecraft:stick"},'
regen
wexpect "a unique smithed from something other than the weapon it is a version of" "rather than from a"

edit $G '            "addition": {"item": UNIQUE_CATALYSTS[rarity]},' '            "addition": {"item": "elysiumarcana:relic_core"},'
regen
wexpect "a unique that needs another module installed to make" "which is not the game's own"

edit $G '              {"parent": "item/generated",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        made += 1
        pattern' '              {"parent": "item/handheld",
               "textures": {"layer0": f"{NS}:item/{name}"}})
        made += 1
        pattern'
regen
wexpect "an arrow held like a tool" "held like a tool and is not one"

echo "what an arrow does beyond arriving"
edit $E '        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());' '        ResourceLocation id = null;'
wexpect "an arrow fired by a skeleton or a dispenser doing nothing" "read off the item it carries"

edit $E '            case "sundering" -> hit.addEffect(new MobEffectInstance(
                    ElysiumEffects.SUNDERING, EFFECT_TICKS, 0));' '            case "nothing_at_all" -> hit.addEffect(new MobEffectInstance(
                    ElysiumEffects.SUNDERING, EFFECT_TICKS, 0));'
wexpect "a sundering arrow that is a flint arrow with a different recipe" "does sundering does nothing"

edit $E '        hit.hurt(new DamageSource(type, arrow, shooter), dealt * PIERCE_SHARE);' '        hit.hurt(new DamageSource(type, arrow, shooter), 4.0F);'
wexpect "a piercing half that is a share of a number nothing used" "actually took"

edit $E '                other -> other != hit && other != shooter && other.isAlive()' '                other -> other.isAlive()'
wexpect "a chain that comes back to what it just left" "come back to what it just left"

edit $E '        nearest.hurt(level.damageSources().indirectMagic(arrow, shooter),
                dealt * CHAIN_SHARE);' '        nearest.hurt(level.damageSources().arrow(arrow, shooter),
                dealt * CHAIN_SHARE);'
wexpect "a chain whose second hit can chain again, so a herd is one arrow" "hit by something other than the chain"

echo "the machinery that keeps the two halves together"
edit $C '        for (DeferredItem<? extends Item> held : RangedItems.BOWS.values()) {' '        for (DeferredItem<? extends Item> held : java.util.List.<DeferredItem<Item>>of()) {'
wexpect "bows that never learn how to draw" "registered for named bows only"

edit $C '        if (described != expected) {' '        if (false) {'
wexpect "nothing counting the bows that know how to draw" "counts the bows that know how to draw"

edit $C '        event.enqueueWork(ArsenalClient::describeEveryBow);' '        describeEveryBow();'
wexpect "registration off the setup thread, where it silently loses entries" "not safe"

edit $RI '        if (made.size() != RangedRules.catalogue().size()) {' '        if (false) {'
wexpect "a bow registry that does not count what it registered" "counts what the ranged registry registered"

edit $UI '        if (made.size() != UniqueRules.UNIQUES.size()) {' '        if (false) {'
wexpect "a unique registry that does not count what it registered" "counts the uniques registered"

edit $UI '            List<String> trouble =
                    ElysiumAbilityRules.cannotShareAnItem(unique.abilities());' '            List<String> trouble = List.<String>of();'
wexpect "a unique added by hand carrying anything it likes" "never be asked whether the abilities"

edit $M/src/main/java/com/elysium/arsenal/registry/ArsenalItems.java '                        for (RangedRules.Shot shot : RangedRules.SHOTS) {
                            output.accept(RangedItems.SHOTS.get(shot.name()).get());
                        }' '                        output.accept(RangedItems.SHOTS.get("flint_arrow").get());'
wexpect "ammunition that cannot be found in the creative tab" "does not walk one of the rosters"

edit $M/src/main/resources/assets/elysiumarsenal/models/item/iron_longbow.json '"parent": "item/generated"' '"parent": "item/handheld"'
wexpect "a generated file edited by hand" "differ from what the generator writes"

echo
echo "restoring and re-checking"
restore
if python3 "$V" >/dev/null 2>&1; then echo "  tree restored clean"; else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "ranged: $pass caught, $fail missed"
[[ $fail -eq 0 ]] || exit 1
