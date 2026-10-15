#!/usr/bin/env bash
# The armory in survival, broken one rule at a time (29 September 2026).
#
# The reachability check found affix essence and the template out of every
# survival player's reach, and the reading around them found the conversion
# recipe choosing a shape by the order of a map that changes every launch, and
# three parts naming extras the grid never charged, one of them an item that
# does not exist. Each fix is defeated here once. Where a rule could be
# satisfied by a token that no longer does anything, the break leaves the token
# behind on purpose, commented out, because that is how rules get fooled.
#
# A new text that comments a line out starts with a space: " // x", never
# "// x". Git Bash hands an argument that begins with two slashes to a native
# program with one of them taken off, so "// x" reaches python as "/ x", the
# line stays live, and the case reads as missed (found on the first run here,
# 29 September 2026).
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-armory
S=$M/src/main/java/com/elysium/armory
R=$M/src/main/resources
SNAP=/tmp/negtest_armory_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-armory)
restore() { rm -rf $ROOT/elysium-armory && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8', newline='').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:60] + '\n')
    sys.exit(2)
open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; restore; exit 2; }; }

SALVAGE=$S/craft/ArmorySalvage.java
SRULES=$S/property/ArmorySalvageRules.java
REFORGE=$S/craft/ArmoryReforge.java
PLANS=$S/property/ArmoryPlanRules.java
PRINT=$S/item/ArmoryBlueprintItem.java
TABLE=$S/block/ArmorySmithingTableBlock.java
CONV=$S/property/ArmoryConversionRules.java
CRECIPE=$S/recipe/ArmoryConversionRecipe.java
PART=$S/recipe/ArmoryCompoundPartRecipe.java

echo "where essence comes from"
edit $SALVAGE 'return !stack.isEmpty() && ArmorySalvageRules.salvageable(modular, affixLevels(stack));' \
              'return !stack.isEmpty() && modular;'
expect "the salvager taking modular gear alone again" "so essence can never be had"

edit $SRULES 'return modular || totalAffixLevels > 0;' 'return modular;'
expect "the rule letting only modular gear on the bench" "so essence has no way in"

edit $SRULES 'return paid > 0 ? Math.min(levels, paid) : levels;' 'return levels;'
expect "a reforged piece paying back one essence a level, however little it cost" \
       "a reroll and a salvage print essence"

edit $SALVAGE 'int essence = ArmorySalvageRules.essence(affixLevels(gear), paidFor(gear));' \
              'int essence = ArmorySalvageRules.essence(affixLevels(gear), 0); // paidFor(gear)'
expect "salvage not asking what the reforge cost, the call left in a comment" \
       "without asking what a reforge cost"

edit $SALVAGE 'return stack.getOrDefault(ArmoryComponents.REFORGED.get(), 0);' \
              'return 0; // stack.getOrDefault(ArmoryComponents.REFORGED.get(), 0);'
expect "the price read from nowhere" "other than the mark the reforge leaves"

edit $REFORGE 'copy.set(ArmoryComponents.REFORGED.get(), cost(targetRarity));' \
              ' // copy.set(ArmoryComponents.REFORGED.get(), cost(targetRarity));'
expect "a reforge that leaves no price on the piece" "does not record what it cost"

edit $S/registry/ArmoryComponents.java 'TYPES.register("reforged",' 'TYPES.register("reforge_price",'
expect "the mark registered under another name" "is never registered, so nothing can carry it"

echo "where a template is found"
edit $PLANS 'new Found("minecraft:chests/simple_dungeon", 0.10D)' 'new Found("minecraft:chests/simple_dungeon", 0.50D)'
expect "a dungeon holding a template every other chest" "not one in ten"

edit $R/data/elysiumarmory/loot_modifiers/template_from_simple_dungeon.json '"chance": 0.1' '"chance": 0.5'
expect "a modifier that says other than the rules" "one chest in 10, and no loot modifier says so"

edit $M/tools/gen_loot.py '"pools": [{"rolls": 1,' '"pools": [{"rolls": 2,'
expect "a generator that no longer writes what ships" "is not what tools/gen_loot.py writes"

edit $R/data/neoforge/loot_modifiers/global_loot_modifiers.json '"elysiumarmory:template_from_village_armorer",' ''
expect "a modifier written and never listed" "template_from_village_armorer is written and never listed"

edit $R/data/elysiumarmory/loot_table/found/template.json '"name": "elysiumarmory:template"' \
                                                          '"name": "elysiumarmory:blueprint"'
expect "a found table that hands out a blueprint" "not one template"

edit $PRINT 'plan(stack, plans.get(drawn));' ' // plan(stack, plans.get(drawn));'
expect "a found template never read, the call left in a comment" "is never read into a plan"

edit $PRINT 'if (reusable || level.isClientSide() || partOf(stack) != null) {' \
            'if (level.isClientSide() || partOf(stack) != null) {'
expect "a blank blueprint read as if it were a found template" \
       "has lost \"if (reusable || level.isClientSide() || partOf(stack) != null) { return; }\""

edit $TABLE 'List<String> parts = ArmoryBlueprintItem.plannable();' \
            'List<String> parts = new java.util.ArrayList<>(com.elysium.armory.material.ArmoryParts.get().all().keySet());'
expect "the smithing table keeping its own list of plans" "keeps a list of plans of its own"

edit $PRINT 'if (ArmoryPlanRules.plannable(part.isUpgrade(), part.crafting().materials())) {' \
            'if (part.crafting().materials() > 0) {'
expect "the plans listed without the rule" "the plans are not listed by the rule"

edit $PLANS 'return !upgrade && materials > 0;' 'return materials > 0;'
expect "an upgrade offered as a plan" "an upgrade can be planned"

edit $PLANS 'return Math.min(plans - 1, (int) Math.floor(clamped * plans));' \
            'return Math.min(plans - 2, (int) Math.floor(clamped * plans));'
expect "a plan no template can ever be" "is never found on a template"

echo "what a vanilla piece becomes"
edit $CONV 'boolean whole = path.equals(shape.word()) || path.endsWith("_" + shape.word());' \
           'boolean whole = path.endsWith(shape.word());'
edit $CONV 'if (whole && (best == null || shape.word().length() > best.word().length())) {' \
           'if (whole && best == null) {'
expect "the old match: any ending, the first shape found" "in another order"

edit $CONV 'if (whole && (best == null || shape.word().length() > best.word().length())) {' \
           'if (whole && best == null) {'
expect "the first shape found winning over the longest" "the longest has to win"

edit $CONV 'boolean whole = path.equals(shape.word()) || path.endsWith("_" + shape.word());' \
           'boolean whole = path.endsWith(shape.word());'
expect "a shape read in the middle of a word" "a shape was read in the middle of a word"

edit $CONV 'new Shape("shears", "shears", "iron"),' 'new Shape("shears", "shears", null),'
expect "shears with no material again" "minecraft:shears converts to null"

edit $CRECIPE 'return ArmoryConversionRules.of(key.getPath());' \
              'return null; // ArmoryConversionRules.of(key.getPath());'
expect "the recipe not asking the rule, the call left in a comment" "does not ask ArmoryConversionRules"

edit $CRECIPE 'public class ArmoryConversionRecipe extends CustomRecipe {' \
              'public class ArmoryConversionRecipe extends CustomRecipe {

    private static boolean pickaxe(String path) {
        return path.endsWith("pickaxe");
    }'
expect "the recipe matching names itself again" "reads names with a table of its own again"

edit $CONV '"chainmail", "iron");' '"chainmail", "chainmail");'
expect "a conversion into a material that does not exist" "which is no material here"

echo "what a part charges"
edit $PART 'boolean[] extras = ArmoryPlanRules.extras(part.crafting().extra(), bench.size(),' \
           'boolean[] extras = ArmoryPlanRules.extras(List.of(), bench.size(),'
expect "a recipe that asks for no extras" "never charged"

edit $PART '            if (extras[i]) {
                continue;
            }
' ''
expect "the extras read as materials" 'has lost "if (extras[i]) { continue; }"'

edit $PART 'List<String> materials = part == null ? null : materialsOf(part, bench);' \
           'List<String> materials = part == null ? null : plain(bench); // materialsOf(part, bench);'
expect "the grid read without taking the extras out" "without taking the part's extras out"

edit $PART 'List<String> materials = known == null ? null : materialsOf(known, bench);' \
           'List<String> materials = known == null ? null : plain(bench); // materialsOf(known, bench);'
expect "a plan book choosing without the extras" "a plan book picks its plan without the extras"

edit $PART 'return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));' \
           'return false; // stack.is(TagKey.create(Registries.ITEM, tag))'
expect "an extra named by tag that nothing answers to" "an extra named by #tag matches nothing"

edit $PLANS '            taken[found] = true;
' ''
expect "one thing on the bench paying for two extras" "one thing on the bench paid for two extras"

edit $PRINT 'for (String extra : part.crafting().extra()) {' 'for (String extra : List.<String>of()) {'
expect "a blueprint that keeps quiet about its extras" "does not say what else its part takes"

python3 - <<'PY'
import re
p = "/tmp/Elysium-mono/elysium-armory/src/main/resources/assets/elysiumarmory/lang/en_us.json"
s = open(p, encoding="utf-8", newline="").read()
s = re.sub(r'\n\s*"elysiumarmory\.blueprint\.also":[^\n]*\n', '\n', s, count=1)
open(p, "w", encoding="utf-8", newline="").write(s)
PY
expect "the extras line with no words" "elysiumarmory.blueprint.also has no translation"

echo "what the data names"
edit $R/data/elysiumarmory/elysium_parts/setting.json '"elysiumfoundry:bort"' '"minecraft:bort"'
expect "the setting asking for an item the game does not have" \
       "names minecraft:bort, which the game does not have"

edit $R/data/elysiumarmory/elysium_materials/mireweave.json '"elysiumores:mireweave"' '"elysiumores:mireweeve"'
expect "a material made of an item no module registers" "which no module of the pack registers"

edit $M/tools/gen_parts.py '"bort": "elysiumfoundry:bort",' '"bort": "minecraft:bort",'
expect "the generator writing the bad id again" "the shipped parts are not what tools/gen_parts.py generates"

echo "what mines a bench (the harvest audit, 29 Sep 2026)"
HR=$S/property/ArmoryHarvestRules.java
BL=$S/registry/ArmoryBlocks.java
T=$R/data/minecraft/tags/block/mineable/pickaxe.json
regen_harvest() { (cd $M && python3 tools/gen_harvest.py >/dev/null 2>&1); }

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].remove("elysiumarmory:salvager")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a bench in no tool's tag" "are in no mineable tag and not exempt"

edit $HR '            if (said == 0) {' '            if (false) {'
expect "the rule that finds a bench no tool is decided for, disabled" "a block no tool is decided for is not reported"

edit $HR '            "stone_anvil", "alloy_forge", "material_grader", "starlight_charger");' \
        '            "alloy_forge", "material_grader", "starlight_charger");'
edit $HR '    public static final List<String> BY_HAND = List.of();' \
        '    public static final List<String> BY_HAND = List.of("stone_anvil");'
edit $M/validate.py 'MINE_EXEMPT = {}' 'MINE_EXEMPT = {"stone_anvil": "a rock, and a rock breaks by hand"}'
edit $BL '                    .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE)));' \
        '                    .mapColor(MapColor.STONE).strength(2.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));'
regen_harvest
expect "an exempt bench that asks for the right tool" "but it asks for the right tool: it says so where it is registered"

edit $BL '            () -> new ArmorySalvagerBlock(BlockBehaviour.Properties.of()' \
        '            () -> new ArmorySalvagerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL)'
expect "a bench built on a game block nobody has looked up" "nothing here knows whether that asks for the right tool"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumarmory:stone_anvi")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a pickaxe tag naming a block nobody registers" "so the game refuses the whole tag"

edit $HR '    public static final List<String> BY_HAND = List.of();' \
        '    public static final List<String> BY_HAND = List.of("stone_anvil");'
expect "a bench broken by hand for no stated reason" "breaks stone_anvil by hand and this gives no reason"

edit $HR '    public static final List<String> AXE = List.of();' \
        '    public static final List<String> AXE = List.of("stone_anvil");'
edit $HR '            "stone_anvil", "alloy_forge", "material_grader", "starlight_charger");' \
        '            "alloy_forge", "material_grader", "starlight_charger");'
expect "the rules moved a bench and the tag left where it was" "the tags are written from the rules by tools/gen_harvest.py"

echo "nothing to run the material harness with (15 Oct 2026)"
# Until 15 October either input missing printed "material harness : not run"
# and the module passed with not one material file loaded. Each must now FAIL
# and name what is missing. The real classpath and build are never touched:
# ELYSIUM_MC_LIBS or ELYSIUM_OUT is pointed at an empty folder for one run.
env_expect() { local what="$1" w="$2" g; shift 2
  g="$(cd $M && env "$@" timeout 600 python3 validate.py 2>&1)"
  if [[ "$g" == *"PROBLEM(S)"* && "$g" == *"$w"* ]]; then echo "  caught : $what"; pass=$((pass+1));
  else echo "  MISSED : $what"; fail=$((fail+1)); fi; restore; }
NOWHERE=$(mktemp -d)
env_expect "no cp.txt where ELYSIUM_MC_LIBS points, which used to print not run and pass" \
  "the material harness cannot run: there is no classpath file at" ELYSIUM_MC_LIBS="$NOWHERE"
env_expect "no armory classes where ELYSIUM_OUT points, which used to print not run and pass" \
  "the material harness cannot run: the armory's compiled classes are not at" ELYSIUM_OUT="$NOWHERE"
rmdir "$NOWHERE"

# The harness's result line (15 Oct 2026). Until 15 October the summary printed
# whatever line came first, which was log4j's "WARN StatusConsoleListener ...",
# and a clean exit with no result line at all passed as "ran". Here the result
# line is taken out and a WARN put where it was: that must FAIL.
edit $M/tools/MaterialHarness.java '        System.out.println("materials: " + materials.size()' \
  '        System.out.println("WARN StatusLogger Log4j2 could not find a logging implementation."); String unusedResult = ("materials: " + materials.size()'
expect "a harness that prints a WARN and no result line, which used to pass as its result" \
  "the MaterialHarness exited cleanly and printed no result line"

echo "the base game's items with no game jar (15 Oct 2026)"
# Until 15 October no game jar appended "(the base game's not checked ...)" to
# a passing line and checked no minecraft: id a material or part names. It must
# now FAIL and name MC_JAR. MC_JAR is pointed at a file that is not there, and
# the validator's fallback places are taken out for this one run (no env var
# can empty them; the real jar is never touched), so this is a machine without
# the jar.
edit $M/validate.py 'if p and os.path.isfile(p)), None)' \
  'if p and os.path.isfile(p) and p == os.environ.get("MC_JAR")), None)'
env_expect "no game jar anywhere, which used to say so on a passing line" \
  "the base game's items a material or part names cannot be checked: no game jar here (MC_JAR is" \
  MC_JAR="$(mktemp -u)/vanilla-assets.jar"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all armory checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "armory in survival: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
