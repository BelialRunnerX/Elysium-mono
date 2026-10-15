#!/usr/bin/env bash
# The shapes a house is made of, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-atelier
R=$M/src/main/java/com/elysium/atelier/rules/AtelierRules.java
S=$M/src/main/java/com/elysium/atelier
G=$M/tools/gen_atelier.py
J=$M/src/main/java/com/elysium/atelier/rules/JoinRules.java
B=$M/src/main/java/com/elysium/atelier/registry/AtelierBlocks.java
H=$M/tools/JoinHarness.java
HR=$M/src/main/java/com/elysium/atelier/rules/HarvestRules.java
HH=$M/tools/HarvestHarness.java
V=$M/validate.py
RES=$M/src/main/resources
SNAP=/tmp/atelier_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-atelier)
restore() { rm -rf $ROOT/elysium-atelier && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
expect_regen() { local w="$2" g
  (cd $M && python3 tools/gen_atelier.py >/dev/null 2>&1)
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

echo "the prices, which is the one thing a decoration mod gets wrong"
edit $R '            new Shape("lattice", 4, 8, 4),' '            new Shape("lattice", 4, 12, 4),'
expect "a shape that makes more units than it takes" "a loop a player can ride"

edit $R '            new Shape("bench", 8, 2, 2),' '            new Shape("bench", 8, 3, 2),'
expect "a batch you could craft and break for a profit" "the pair pays"

edit $R '            new Shape("crate", 8, 4, 4),' '            new Shape("crate", 8, 1, 4),'
expect "a shape nobody will ever make" "more than a whole block"

edit $R '    public static final int WHOLE = 8;' '    public static final int WHOLE = 7;'
expect "a unit nobody can halve in their head" "in a unit nobody can halve"

edit $R '            if (units < 1 || units > WHOLE * 2) {' '            if (false) {'
expect "a shape that is free, or bigger than two blocks" "free furniture"

edit $R '        return yields() > spends();' '        return false;'
expect "a profit check that is not about what a recipe makes" "checking something else"

edit $R '        return units / WHOLE;' '        return (units + WHOLE - 1) / WHOLE;'
expect "six eighths of a plank handed back as a plank" "the pair is free wood"

edit $R '            if (shape.recovers() * WHOLE * shape.made() > shape.spends()) {' '            if (false) {'
expect "nobody checking whether crafting and breaking a batch pays" "the way this actually goes wrong"

echo "the catalogue"
edit $S/registry/AtelierBlocks.java '        for (AtelierRules.Material material : AtelierRules.MATERIALS) {
            for (AtelierRules.Shape shape : AtelierRules.SHAPES) {' '        for (AtelierRules.Material material : AtelierRules.MATERIALS.subList(0, 1)) {
            for (AtelierRules.Shape shape : AtelierRules.SHAPES) {'
expect "blocks registered from something other than the catalogue" "sixteen more declarations"

edit $S/registry/AtelierBlocks.java '                        : BLOCKS.register(id, () -> new ShapedBlock(properties,
                                shape.name())));' '                        : BLOCKS.register(id, () -> new ShapedBlock(properties, "crate")));'
expect "a chair with a shelf's collision box" "a shelf"

edit $S/registry/AtelierItems.java '            made.put(entry.getKey(), ITEMS.registerSimpleBlockItem(entry.getValue()));' '            made.put(entry.getKey(), null);'
expect "blocks with no items" "only a command can place it"

rm $RES/assets/elysiumatelier/models/block/oak_chair.json
expect "a block with no model" "things a block needs are missing"

edit $G '            write(where, {"replace": False, "values": sorted(lists[tool])})' '            write(where, {"replace": False, "values": []})'
expect_regen "blocks in no mineable tag" "take a fist and drop nothing"

echo "the geometry, which is two descriptions of one block"
edit $S/block/ShapedBlock.java '            case "shelf" -> Block.box(0.0D, 4.0D, 10.0D, 16.0D, 13.0D, 16.0D);' '            case "shelf" -> Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);'
expect "a shelf you can see through and cannot walk past" "the other way about"

edit $G '    "lattice": [(0, 0, 7, 16, 16, 9)],' '    "lattice": [(0, 0, 6, 16, 16, 10)],'
expect_regen "a model wider than the box a player walks into" "cannot walk past"

edit $G '    "crate": [(0, 0, 0, 16, 16, 16)],' '    "crate": [(2, 0, 2, 14, 16, 14)],'
expect_regen "a whole block drawn smaller than it is" "cannot walk past"

edit $G '''UPRIGHT = ["pillar"]''' '''UPRIGHT = ["pillar", "beam"]'''
expect_regen "a blockstate for properties the block does not have" "will not load"

echo "generated, and still what the generator writes"
edit $RES/assets/elysiumatelier/lang/en_us.json '"Oak Chair"' '"Oak Chiar"'
expect "a language file edited by hand" "differ from what the generator writes"

edit $G '    "shelf": ["MMM"],' '    "shelf": ["MM"],'
expect_regen "a grid that uses a different number of blocks from its price" "a recipe nobody ships"

edit $G '    "bench": ["MM"],' '    "bench": ["MMM"],'
expect_regen "two shapes laid out the same way" "listed first"

echo "independence and the seam"
edit $R 'import java.util.ArrayList;' 'import net.minecraft.world.level.block.Block;
import java.util.ArrayList;'
expect "rules no harness could price without a game" "without a game"

edit $R '        return new Material(name, "minecraft:" + name + "_planks",' '        return new Material(name, "handcrafted:" + name + "_planks",'
expect "a decoration module that needs another mod installed" "another mod installed"

edit $S/ElysiumAtelier.java '    public static final String MODID = "elysiumatelier";' '    public static final String MODID = "elysiumatelier";
    static final Class<?> UP = com.elysium.arcana.rules.ArcanaRules.class;'
expect "two hundred blocks that need another Elysium module" "not one dependency"

edit $M/src/main/resources/META-INF/neoforge.mods.toml '    modId="elysiumlib"' '    modId="handcrafted"'
expect "a dependency on the mod this module replaces" "must not need anything else"

edit $S/ElysiumAtelier.java 'MODID = "elysiumatelier"' 'MODID = "elysium_atelier"'
expect "a module id nothing else in the tree spells that way" "everything else in the tree spells"

edit $S/ElysiumAtelier.java '        com.elysium.atelier.registry.AtelierItems.TABS.register(modEventBus);' '        '
expect "a tab written and never registered" "none of it exists"

echo "the joins, which are the blocks that have to meet their neighbours"
edit $J '                if (alreadyInTheGame(kind, material)) {
                    continue;
                }' '                if (false) {
                    continue;
                }'
expect "a module adding eleven materials of doors the game has had since 2011" "its own shape list says in so many words"

edit $J '                tags.add("minecraft:fences");' '                tags.add("minecraft:walls");'
expect "a fence that no fence in the game reaches for" "is not in #fences"

edit $J '            case GATE -> tags.add("minecraft:fence_gates");' '            case GATE -> tags.add("minecraft:fences");'
expect "a gate a run of fence stops dead on either side of" "is not in #fence_gates"

edit $J '                if (opening.soft()) {
                    tags.add("minecraft:wooden_doors");
                }' '                tags.add("minecraft:wooden_doors");'
expect "a stone door tagged as one a hand opens" "tags and behaviour disagree"

edit $J '        tags.add(opening.soft() ? "minecraft:mineable/axe" : "minecraft:mineable/pickaxe");' \
        '        tags.add("minecraft:mineable/axe");
        tags.add("minecraft:mineable/pickaxe");'
expect "a block mined by both tools at once" "mined by both an axe and a pickaxe"

edit $J '        return tagsFor(one).contains("minecraft:wooden_fences")
                == tagsFor(other).contains("minecraft:wooden_fences");' \
        '        return tagsFor(one).contains("minecraft:wooden_fences")
                != tagsFor(other).contains("minecraft:wooden_fences");'
expect "fences that join everything except their own kind" "join when they are"

edit $B '            if (set.canOpenByHand() != JoinRules.openedByHand(opening)' '            if (false' 
expect "the game's block set and our rules never made to agree about a door" "with neither checking the other"

edit $H '            System.out.println("TAG " + entry.getKey() + " " + block);' '            if (false) { System.out.println(""); }'
expect "a harness that stops printing the tags it derived" "believed rather than checked"

python3 - <<'PYX'
import json, pathlib
# The bug in its natural habitat: one block left out of one tag.
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/fences.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = [v for v in b["values"] if not v.endswith("deepslate_fence")]
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "one block missing from one tag, which is every missing-tag bug there is" "needs to be in minecraft:fences and is not"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/fences.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = sorted(set(b["values"]) | {"elysiumatelier:stone_pane"})
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a block in a tag the rules never put it in" "and the rules do not put it there"

python3 - <<'PYX'
import json, pathlib
# One rotation out of thirty-two: a door that opens through its own frame.
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/assets/elysiumatelier/blockstates/stone_door.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["variants"]["facing=east,half=lower,hinge=left,open=true"]["y"] = 270
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "one rotation of thirty-two turned the wrong way" "is not drawn the way the game draws"

edit $G '                                   ("noside", "template_glass_pane_noside"),' \
        '                                   ("noside", "template_glass_pane_noside_alt"),'
expect_regen "a pane whose blank sides are built on the wrong piece" "and the game builds a"

edit $G '                    "parent": f"minecraft:block/template_trapdoor_{part}",' \
        '                    "parent": f"minecraft:block/template_orientable_trapdoor_{part}",'
expect_regen "a trapdoor with its grain running the wrong way on two facings" "the piece is the wrong shape"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/assets/elysiumatelier/models/block/stone_fence_post.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["parent"] = "minecraft:block/fence_side"
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a fence post built on the side piece" "the piece is the wrong shape"

echo "what mines it, which is the fault the game named on 29 September 2026"
python3 - <<'PYX'
import json, pathlib
# The fault in its natural habitat: one stone block in no tool's list.
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = [v for v in b["values"] if v != "elysiumatelier:stone_shallow_roof_slope"]
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a stone roof slope in no tool's list, which drops nothing" "ask for the right tool and are in no tool's tag"

python3 - <<'PYX'
import json, pathlib
# A wooden one drops, but at a fist's pace whatever is held.
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/mineable/axe.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = [v for v in b["values"] if v != "elysiumatelier:oak_awning"]
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a wooden awning in no tool's list, which takes a fist" "every tool breaks them at a fist's pace"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = [v for v in b["values"] if v != "elysiumatelier:stone_awning"]
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/mineable/axe.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = sorted(set(b["values"]) | {"elysiumatelier:stone_awning"})
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a stone awning on the axe's list" "are on the wrong tool's list"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/mineable/axe.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = sorted(set(b["values"]) | {"elysiumatelier:stone_awning"})
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a block on two tools' lists" "are on two tools' lists"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["values"] = sorted(set(b["values"]) | {"elysiumatelier:stone_spaceship"})
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a list naming a block nobody registers, which makes the game refuse the whole list" "is not a block this module registers"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-atelier/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
b = json.loads(p.read_text(encoding="utf-8"))
b["replace"] = True
p.write_text(json.dumps(b, indent=2) + "\n", encoding="utf-8")
PYX
expect "a list that replaces the game's own" "replaces the game's own list"

edit $G '            harvested("pieces", id, piece[0], soft)' '            pass'
expect_regen "the architectural pieces written and reported to nobody, which is how it happened" "ask for the right tool and are in no tool's tag"

edit $HR '    public static final List<String> BY_HAND = List.of();' '    public static final List<String> BY_HAND = List.of("rug");'
expect "a kind broken by hand with no reason given" "breaks rug by hand and this gives no reason"

edit $HR '    public static final List<String> BY_HAND = List.of();' '    public static final List<String> BY_HAND = List.of("rug");'
edit $V 'MINE_EXEMPT = {}' 'MINE_EXEMPT = {"rug": "a rug is a doormat"}'
expect_regen "a stone rug excused from every list, which asks for the right tool all the same" "is exempt from the mineable tags (a rug is a doormat), but"

edit $HR '    public static final List<String> BY_HAND = List.of();' '    public static final List<String> BY_HAND = List.of("rug");'
edit $V 'MINE_EXEMPT = {}' 'MINE_EXEMPT = {"rug": "a rug is a doormat"}'
expect "a kind excused from the lists and on one all the same" "exempt from the mineable tags and in one all the same"

edit $V 'MINE_EXEMPT = {}' 'MINE_EXEMPT = {"rug": "a rug is a doormat"}'
expect "a reason for a kind the rules no longer break by hand" "HarvestRules no longer does"

edit $B '                : BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)' '                : BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE)'
expect "a registry copying a game block nobody looked up" "nothing here knows whether that asks for the right tool"

edit $B '                BlockBehaviour.Properties properties = like(material);
                if (piece.burns()) {' '                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of();
                if (piece.burns()) {'
expect "the pieces built on something other than like(material)" "are no longer built on like(material)"

edit $HR '        return material.soft() ? AXE : PICKAXE;' '        return PICKAXE;'
expect "wood handed to the pickaxe" "and the rules hand it to the pickaxe"

edit $HH '            System.out.println("HARVEST " + one.id() + " " + one.roster() + " "
                    + one.kind() + " " + one.material() + " " + one.tool());' '            if (false) { System.out.println(""); }'
expect "a harness that stops printing what mines each block" "compared against nothing"

echo "the joins and the cuts with no game jar (15 Oct 2026)"
# Until 15 October no game jar printed "the game's jar is not here" for both
# and the module passed with neither compared. Each must now FAIL and name
# MC_JAR. MC_JAR is pointed at a file that is not there and made the only place
# looked, for this one run (no env var can empty the fallback list; the real jar
# is never touched), so this is a machine without the jar. One run, two cases.
env_cases() { local setting="$1" g; shift
  g="$(cd $M && env "$setting" timeout 400 python3 validate.py 2>&1)"
  while [ $# -ge 2 ]; do
    if [[ "$g" == *"PROBLEM(S)"* && "$g" == *"$2"* ]]; then echo "  caught : $1"; pass=$((pass+1));
    else echo "  MISSED : $1"; fail=$((fail+1)); fi
    shift 2
  done; restore; }
edit $V 'VANILLA_JAR = next((_j for _j in [' 'VANILLA_JAR = os.environ.get("MC_JAR") or next((_j for _j in ['
env_cases MC_JAR="$(mktemp -u)/vanilla-assets.jar" \
  "the joins with no game jar, which used to print not compared and pass" \
  "the joins' blockstates cannot be compared with the game's: no game jar here" \
  "the cuts with no game jar, which used to print not checked and pass" \
  "what the game already cuts cannot be checked against the game: no game jar here"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all atelier checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "atelier: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
