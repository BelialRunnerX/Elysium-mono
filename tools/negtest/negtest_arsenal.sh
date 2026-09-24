#!/usr/bin/env bash
# The tools that take a wall at a time, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arsenal
R=$M/src/main/java/com/elysium/arsenal/rules/ArsenalRules.java
S=$M/src/main/java/com/elysium/arsenal
G=$M/tools/gen_arsenal.py
W=$M/src/main/java/com/elysium/arsenal/rules/WeaponRules.java
I=$M/src/main/java/com/elysium/arsenal/item/WeaponItem.java
RI=$M/src/main/java/com/elysium/arsenal/registry/ArsenalItems.java
RES=$M/src/main/resources
SNAP=/tmp/arsenal_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-arsenal)
restore() { rm -rf $ROOT/elysium-arsenal && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
expect_regen() { local w="$2" g
  (cd $M && python3 tools/gen_arsenal.py >/dev/null 2>&1)
  g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

echo "the band, which is what keeps the pickaxe a tool"
edit $R '    public static final float BROAD_SHARE = 0.55F;' '    public static final float BROAD_SHARE = 1.2F;'
expect "a hammer faster than the pickaxe it replaces" "crafting ingredient"

edit $R '    public static final float BROAD_SHARE = 0.55F;' '    public static final float BROAD_SHARE = 0.1F;'
expect "a hammer nobody would ever make" "nobody would ever make one"

edit $R '    public static final float PAXEL_SHARE = 0.75F;' '    public static final float PAXEL_SHARE = 0.4F;'
expect "two penalties for one convenience" "two penalties for one convenience"

edit $R '    public static final int REACH = 1;' '    public static final int REACH = 2;'
expect "a ceiling a player drops on themselves" "drops on themselves"

edit $R '        int across = 2 * Math.max(0, reach) + 1;' '        int across = 3;'
expect "durability charged for a different number of blocks than were taken" "can disagree"

edit $R '            if (share <= 0.0F || share >= 1.0F) {' '            if (false) {'
expect "a tool as fast as the one it replaces" "rather than a tool"

edit $R '        return swing() > 1.0F;' '        return true;'
expect "a survival check that always says yes" "checking something else"

edit $R '        return clearing() < specialistClearing();' '        return true;'
expect "a worth-carrying check that always says yes" "nobody would ever make"

echo "what a swing costs"
edit $R '    public static final int COST_PER_BLOCK = 1;' '    public static final int COST_PER_BLOCK = 0;'
expect "nine blocks for the price of one" "every other tool in the game a mistake"

edit $R '        return Math.max(0, blocks) * COST_PER_BLOCK;' '        return 1;'
expect "a swing that costs one however many it took" "nine blocks can be cheaper than nine"

edit $S/item/BroadToolItem.java '                stack.hurtAndBreak(ArsenalRules.costOf(taken), player,
                        EquipmentSlot.MAINHAND);' '                ;'
expect "extra blocks taken for free" "nine times the tool for one price"

edit $S/item/BroadToolItem.java '        if (state.getDestroySpeed(level, pos) <= 0.0F) {
            // Instant. A tool that cleared nine blocks every time somebody
            // brushed a leaf would be unusable rather than powerful.
            return broke;
        }' '        if (false) {
            return broke;
        }'
expect "nine blocks cleared every time somebody brushes a leaf" "unusable rather than powerful"

edit $S/item/BroadToolItem.java '                if (other.isAir() || !stack.isCorrectToolForDrops(other)) {
                    continue;
                }' '                if (other.isAir()) {
                    continue;
                }'
expect "a hammer that takes what a pickaxe could not" "every tool at once"

edit $S/item/BroadToolItem.java '                        || hardness > state.getDestroySpeed(level, pos) + HARDER_BY) {' '                        || false) {'
expect "hitting stone beside a vein taking the vein" "takes the vein"

edit $S/item/BroadToolItem.java '        SWINGING.set(true);' '        ;'
expect "a swing inside a swing inside a swing" "nine blocks around each of nine"

edit $S/item/BroadToolItem.java '                found.add(switch (face.getAxis()) {' '                found.add(switch (net.minecraft.core.Direction.Axis.Y) {'
expect "a swing at a floor taking the block under the player" "a hole they did not dig"

echo "the scythe, which is the one that could make food"
edit $R '        return Math.max(0, harvested);' '        return Math.max(0, harvested) + 1;'
expect "a seed farm that needs no field" "needs no field"

edit $S/item/ScytheItem.java '        int held = ArsenalRules.replanted(1);' '        int held = 0;'
expect "a replant the harness never proved" "a different scythe"

edit $S/item/ScytheItem.java '                stack.shrink(taken);' '                ;'
expect "a field that grows every time it is harvested" "where the wheat is coming from"

edit $S/item/ScytheItem.java '        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {' '        if (!(state.getBlock() instanceof CropBlock crop)) {'
expect "an ungrown crop harvested" "turning a seed into a seed"

echo "the catalogue"
edit $S/registry/ArsenalItems.java '        for (String name : ArsenalRules.TIERS) {' '        for (String name : ArsenalRules.TIERS.subList(0, 1)) {'
expect "tools registered from something other than the catalogue" "six more declarations"

edit $S/registry/ArsenalItems.java '        return attacks * share - 4.0F;' '        return pickaxe * share;'
expect "a swing speed that is wrong by a reciprocal" "wrong by a reciprocal"

rm $RES/assets/elysiumarsenal/textures/item/iron_hammer.png
expect "a tool with no sprite" "things a tool needs are missing"

edit $G '        key["T"] = {"item": f"minecraft:{tier}_{BUILT_FROM[shape][0]}"}' '        key["T"] = {"item": "minecraft:stick"}'
expect_regen "a hammer you can make without ever making a pickaxe" "without ever making the one it replaces"

edit $G '''            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": "item/handheld",''' '''            write(os.path.join(ASSETS, "models/item", id + ".json"),
                  {"parent": "item/generated",'''
expect_regen "tools that lie flat in a player's hand" "lie flat"

edit $G '''                  {"parent": "item/handheld",
                   "textures": {"layer0": f"{NS}:item/{tier}_{row[0]}"}})''' '''                  {"parent": "item/generated",
                   "textures": {"layer0": f"{NS}:item/{tier}_{row[0]}"}})'''
expect_regen "weapons that lie flat in a player's hand" "lie flat"

echo "independence and the seam"
edit $R 'import java.util.ArrayList;' 'import net.minecraft.world.item.Item;
import java.util.ArrayList;'
expect "rules no harness could sweep without a game" "without a game"

edit $S/ElysiumArsenal.java '    public static final String MODID = "elysiumarsenal";' '    public static final String MODID = "elysiumarsenal";
    static final Class<?> UP = com.elysium.atelier.rules.AtelierRules.class;'
expect "tools that need another Elysium module" "nothing else installed"

edit $M/src/main/resources/META-INF/neoforge.mods.toml '    modId="elysiumlib"' '    modId="simplyswords"'
expect "a dependency on the mod this module replaces" "must not need anything else"

edit $S/ElysiumArsenal.java 'MODID = "elysiumarsenal"' 'MODID = "elysium_arsenal"'
expect "a module id nothing else in the tree spells that way" "everything else in the tree spells"

edit $S/ElysiumArsenal.java '        com.elysium.arsenal.registry.ArsenalItems.TABS.register(modEventBus);' '        '
expect "a tab written and never registered" "none of it exists"

echo "the weapons, and the ninety-seven nobody ever uses"
edit $W '            return atLeastAsGoodAs(other) && betterSomewhereThan(other);' \
        '            return false;'
expect "a comparison that reports nothing as dead content" "a weapon that is worse at nothing passes"

edit $W '                    && pierce >= other.pierce();' '                    ;'
expect "a comparison blind to one of its five axes" "no longer looks at"

edit $W '            if (!(fastest || hardest || longest || widest || crowds || sharpest
                    || deadliest)) {' '            if (false) {'
expect "a weapon allowed to be middling at everything" "chosen by nobody either"

edit $W '            for (int rung = 1; rung < LADDER.size(); rung++) {' \
        '            for (int rung = 1; rung < TIERS.size(); rung++) {'
expect "a ladder walked in the order the registry lists it" "reports every weapon in the module as broken"

edit $W '    public static final List<String> LADDER =
            List.of("wooden", "stone", "iron", "diamond", "netherite");' \
        '    public static final List<String> LADDER =
            List.of("wooden", "stone", "golden", "iron", "diamond", "netherite");'
expect "gold put on the ladder it is a side-grade to" "wrong about the game"

edit $W '            if (ours.dominates(sword)) {' '            if (false) {'
expect "a weapon set never measured against the game's own sword" "it has deleted one"

edit $W 'new Weapon("longsword", 6.5F, 1.9F, 0.25F, 1, 0.0F)' \
        'new Weapon("longsword", 7.5F, 1.9F, 0.5F, 1, 0.0F)'
expect "a longsword that makes the diamond sword a crafting step" "beats the game's own diamond sword"

edit $W 'new Weapon("sabre", 5.0F, 2.4F, 0.0F, 1, 0.0F)' \
        'new Weapon("sabre", 5.0F, 1.0F, 0.0F, 1, 0.0F)'
expect "a weapon beaten on every axis by another of its tier" "on every axis"

edit $I '        float stoppable = made.damage() * (1.0F - made.pierce());' \
        '        float stoppable = made.damage();'
expect "a piercing weapon whose bypassing half is added on top" "the one thing the domination check cannot see"

edit $I '        if (weapon.pierce() <= 0.0F) {
            return;
        }' '        if (false) {
            return;
        }'
expect "every weapon bypassing armour, so the estoc is pointless" "has no reason to exist"

edit $I '            if (taken >= weapon.sweep() - 1) {
                break;
            }' '            if (false) {
                break;
            }'
expect "a sweep that hits whatever is nearby" "three is a suggestion"

edit $RI '        if (made.size() != WeaponRules.catalogue().size()) {' '        if (false) {'
expect "a game that starts with a weapon missing and says nothing" "can start with one missing"

edit $G '    "estoc":      [" M ", " M ", " S "],' '    "estoc":      ["  M", " M ", "S  "],'
expect_regen "two weapons on one grid, so one cannot be crafted" "one of them cannot be made at all"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all arsenal checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "arsenal: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
