#!/usr/bin/env bash
# Nine things that hunt you, broken one at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/BeastRules.java
RD=$M/src/main/java/com/elysium/bestiary/client/BeastRenderer.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
EN=$M/src/main/java/com/elysium/bestiary/entity/BeastEntity.java
RE=$M/src/main/java/com/elysium/bestiary/registry/BeastEntities.java
RI=$M/src/main/java/com/elysium/bestiary/registry/BeastItems.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
H=$M/tools/BeastHarness.java
SNAP=/tmp/bestiary_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fi; [[ "$g" == *"$w"* ]] || fail=$((fail+1)); restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
# Some rules can only be defeated by editing the generator and the files it
# wrote together, so the generated tree is rebuilt after the edit.
regen() { (cd $M && python3 tools/gen_bestiary.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }

echo "the reward, which is the whole module"
edit $R '        order.sort((first, second) -> Double.compare(first.threat(), second.threat()));' \
        '        order.sort((first, second) -> first.id().compareTo(second.id()));'
expect "a reward worked out in alphabetical order instead of order of danger" "in order of danger"

edit $R '            long worth = Math.max(Math.max(Math.round(beast.threat() * rate), below + 1L),
                    LEAST_WORTH);' \
        '            long worth = Math.max(Math.round(beast.threat() * rate), LEAST_WORTH);'
expect "two nearly equal beasts rounding onto the same reward" "forced past the reward of the beast below"

edit $R '            int drops = (int) Math.max(LEAST_DROPS, Math.min(affordable, wanted));' \
        '            int drops = (int) Math.max(LEAST_DROPS, wanted);'
expect "a drop count the reward cannot pay for" "capped at what the reward can pay for"

edit $R '            return experience + (double) drops * WORTH_OF_A_DROP;' \
        '            return experience;'
expect "a worth that forgets everything the mob dropped" "orbs plus its items at the stated price"

edit $R '        return reward.worth() / beast.threat();' '        return reward.worth();'
expect "a reward per unit of danger with the danger left out" "not reward over danger"

edit $R 'public static final double BASE_RATE = 0.088D;' 'public static final double BASE_RATE = 0.03D;'
expect "a bestiary a vanilla zombie outfarms" "every mob farm in the pack is a zombie farm"

edit $R 'public static final double BASE_RATE = 0.088D;' 'public static final double BASE_RATE = 0.3D;'
expect "a bestiary that makes the game's own mobs pointless" "stop being worth fighting"

edit $R 'public static final double TILT_PER_TIER = 0.06D;' 'public static final double TILT_PER_TIER = 0.9D;'
expect "a top tier worth so much more to farm that nothing else is" "grinder for one mob"

edit $R 'public static final double TILT_PER_TIER = 0.06D;' 'public static final double TILT_PER_TIER = 0.0D;'
expect "a ladder that pays no better at the top than at the bottom" "getting stronger buys nothing"

edit $R 'new Beast("siegehulk", BRUTE, 3, 90.0D, 12.0D, 0.26D, 10.0D, 10, 2.0F, 3.0F)' \
        'new Beast("siegehulk", BRUTE, 3, 400.0D, 12.0D, 0.26D, 10.0D, 10, 2.0F, 3.0F)'
expect "a mob that is an event rather than a mob" "it is an event"

edit $R 'new Beast("spallet", PROWLER, 1, 18.0D, 3.0D, 0.34D, 0.0D, 80, 0.6F, 1.1F)' \
        'new Beast("spallet", PROWLER, 1, 4.0D, 3.0D, 0.34D, 0.0D, 80, 0.6F, 1.1F)'
expect "a beast less dangerous than a zombie" "less dangerous than a zombie"

edit $R 'new Beast("spallet", PROWLER, 1, 18.0D, 3.0D, 0.34D, 0.0D, 80, 0.6F, 1.1F)' \
        'new Beast("spallet", PROWLER, 1, 18.0D, 3.0D, 0.34D, 0.0D, 900, 0.6F, 1.1F)'
expect "one beast that is most of everything you ever meet" "of everything you meet"

echo "the reward written down a second time"
edit $R '        public double threat() {' '        public int experience() { return 1; }

        public double threat() {'
expect "a reward back in the beast record, wearing an accessor" "back in the beast record"

edit $G 'BASE_RATE = number(RULES_SOURCE, "BASE_RATE")' 'BASE_RATE = 0.05'
expect "a generator whose arithmetic has drifted from the rules" "in the generator, so the loot table it writes"

edit $H '            System.out.println("EARNED " + beast.id() + " " + reward.experience() + " "
                    + reward.drops());' '            if (false) { System.out.println(""); }'
expect "a harness that stops printing the table it derived" "believed rather than checked"

echo "as big as it looks"
edit $RD '        float across = row.width() * PIXELS / Math.max(shape.wide(), shape.deep());' \
        '        float across = 1.0F;'
expect "a beast drawn as wide as whatever shape it shares" "no longer worked out from how wide it is"

edit $RD '        float up = row.height() * PIXELS / shape.tall();' '        float up = 1.0F;'
expect "a head above its own hitbox" "no longer worked out from how tall it is"

edit $RD '        pose.scale(across, up, across);' '        pose.scale(1.0F, 1.0F, 1.0F);'
expect "a shape never squashed onto the hitbox at all" "not squashed onto the hitbox"

echo "the shapes"
edit $R 'public static final String WIGHT = "wight";' 'public static final String WIGHT = "ghoul";'
expect "a family of beasts with no shape at all" "no body plan"

edit $M/src/main/java/com/elysium/bestiary/client/shape/ProwlerShape.java 'public static final float WIDE = ' 'public static final float WIDTH = '
expect "a shape that stopped publishing how wide it is" "does not publish its WIDE"

python3 - "$M/src/main/java/com/elysium/bestiary/client/shape/WightShape.java" <<'PYX'
import re, sys
p = sys.argv[1]; s = open(p, encoding="utf-8").read()
# A size typed in by hand where the generator measured one.
s = re.sub(r"public static final float TALL = [\d.]+F;", "public static final float TALL = 32.0F;", s, count=1)
open(p, "w", encoding="utf-8").write(s)
PYX
expect "a shape whose size is typed rather than measured" "is not what the generator produces"

python3 - "$M/src/main/java/com/elysium/bestiary/client/shape/ProwlerShape.java" <<'PY'
import re, sys
p = sys.argv[1]; s = open(p, encoding="utf-8").read()
# Drop every toe, joints and tips alike: two dozen boxes, enough to fall under a hundred.
s = re.sub(r'\n        (?:PartDefinition \w+ = )?[a-zA-Z0-9]+\.addOrReplaceChild\("leg_[a-z_]+_toe_\d+_\d+",\n[^;]*;', "", s)
open(p, "w", encoding="utf-8").write(s)
PY
expect "a shape thinned below the eight's density" "recoloured six-box mob"

edit $M/src/main/java/com/elysium/bestiary/client/shape/Shapes.java '            case "wight" -> { return WightShape.createBodyLayer(); }' '            case "ghoul" -> { return WightShape.createBodyLayer(); }'
expect "a family the registry stopped listing" "does not list the wight"

edit $M/src/main/java/com/elysium/bestiary/client/BeastModel.java '        this.inner.setupAnim(creature, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);' '        // stood still'
expect "a beast that stopped walking its shape's walk" "no longer delegates to its shape's walk"

edit $M/src/main/java/com/elysium/bestiary/client/shape/Shapes.java '            case "fowl" -> { return FowlShape.createBodyLayer(); }' '            case "hen" -> { return FowlShape.createBodyLayer(); }'
expect "an animal kind the registry stopped listing" "does not list the fowl"

edit $M/src/main/java/com/elysium/bestiary/client/CourtModel.java '        this.inner.setupAnim(creature, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);' '        // stood still'
expect "a court that stopped walking its form's walk" "CourtModel no longer delegates"

edit $M/src/main/java/com/elysium/bestiary/client/ShadeModel.java 'boolean hunting = entity != null && entity.getTarget() != null;' 'boolean hunting = false;'
expect "a shade that stopped reaching for what it hunts" "no longer reaches when it hunts"

echo "what it leaves"
edit $G '    return [staple, essence, rare]' '    return [staple + 2, essence, rare]'
expect_regen "a loot table that hands out more than the bestiary is balanced on" "is not the reward a player gets"

edit $G '    twice = drops * 2' '    twice = drops'
expect_regen "a loot table that hands out half what the bestiary says" "is not the reward a player gets"

edit $G '        "count": {"type": "minecraft:uniform", "min": 0.0, "max": float(most)},' \
        '        "count": {"type": "minecraft:constant", "value": float(most)},'
expect_regen "a count nothing can work an expectation out of" "has stopped being checked"

edit $R '            PROWLER, new Leavings("minecraft:string", "minecraft:spider_eye",' \
        '            PROWLER, new Leavings("minecraft:stringg", "minecraft:spider_eye",'
expect_regen "a staple drop with a letter too many in it" "neither the game nor this pack makes them"

edit $R '                    List.of("minecraft:coal", "minecraft:amethyst_shard",
                            "minecraft:echo_shard")));' \
        '                    List.of("minecraft:coal", "minecraft:amethyst_shard",
                            "minecraft:echoshard")));'
expect_regen "a rare drop only the hardest wight ever leaves, misspelt" "neither the game nor this pack makes them"

edit $V '                if entry.get("type") == "minecraft:item" and entry.get("name"):' \
        '                if entry.get("type") == "minecraft:itemm" and entry.get("name"):'
expect "a sweep that reads no drops at all out of nine loot tables" "balancing zero against zero"

edit $V '    return {"minecraft:" + key.split(".", 2)[2] for key in named
            if key.startswith(("item.minecraft.", "block.minecraft."))}' \
        '    return {"minecraft:" + key.split(".", 2)[2] for key in named
            if key.startswith(("item.mojang.", "block.mojang."))}'
expect "a game list that reads as empty, so both drop checks quietly stop" "is checking nothing at all"

edit $V '    for where, _, names in os.walk(ROOT):
        if not where.replace(os.sep, "/").endswith("/lang"):' \
        '    for where, _, names in os.walk(ROOT):
        if not where.replace(os.sep, "/").endswith("/langue"):'
expect "a pack list that reads as empty, so our own drops read as invented" "reads as invented"

echo "what you meet"
edit $G '                      "weight": BY_ID[haunt["beast"]]["weight"],' \
        '                      "weight": 1,'
expect_regen "a spawn weight that is not the weight the harness proved the shares from" "is not the share a player meets"

edit $G '                  "biomes": haunt["where"],' '                  "biomes": "#minecraft:is_mountain",'
expect_regen "a bestiary most players never meet" "a third of the bestiary is a rumour"

edit $G '                  "spawners": [{' '                  "spawners": [{
                      "type": "%s:spallet" % NS,
                      "weight": 1,
                      "minCount": 1,
                      "maxCount": 1,
                  }, {'
expect_regen "a beast spawned by two rules at once" "appears twice as often as its weight says"

edit $G '                      "minCount": haunt["least"],' '                      "minCount": 0,'
expect_regen "a spawn roll that puts nothing there" "puts nothing there"

echo "the words, and the tree"
edit $G '    "spallet": "It comes apart when you hit it and the pieces keep coming.",' \
        '    "spallet": "A mob.",'
expect_regen "a beast described in four words" "tells a player nothing they could not see"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-bestiary/src/main/resources/assets/elysiumbestiary/lang/en_us.json")
words = json.loads(p.read_text(encoding="utf-8"))
del words["entity.elysiumbestiary.prowlframe"]
p.write_text(json.dumps(words, indent=2) + "\n", encoding="utf-8")
PYX
expect "a beast that reads as its own translation key" "entity.elysiumbestiary.prowlframe has no name"

python3 - <<'PYX'
import pathlib
from PIL import Image
p = pathlib.Path("/tmp/Elysium-mono/elysium-bestiary/src/main/resources/assets/elysiumbestiary/textures/entity/shearjaw.png")
image = Image.open(p).convert("RGBA")
image.putpixel((3, 3), (255, 0, 255, 255))
image.save(p)
PYX
expect "a sheet edited by hand under a generator" "edited by hand"

echo "in the world"
edit $RE '            made.put(beast.id(), TYPES.register(beast.id(),' \
        '            if (beast.tier() > 2) { continue; }
            made.put(beast.id(), TYPES.register(beast.id(),'
expect "a bestiary registered in part" "registered in part"

edit $RE '                            .sized(beast.width(), beast.height())' '                            .sized(1.0F, 1.0F)'
expect "an entity type built to a size that is not its own row's" "not built to the size in its own row"

edit $RE '    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (BeastRules.Beast beast : BeastRules.BEASTS) {' \
        '    public static void onAttributes(EntityAttributeCreationEvent event) {
        for (BeastRules.Beast beast : BeastRules.family(BeastRules.BRUTE)) {'
expect "six beasts with no attributes at all" "spawns with a player's health"

edit $RE '    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (BeastRules.Beast beast : BeastRules.BEASTS) {' \
        '    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        for (BeastRules.Beast beast : BeastRules.family(BeastRules.BRUTE)) {'
expect "a beast that spawns in daylight in somebody's base" "spawns in daylight"

edit $CL '        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            event.registerEntityRenderer(' \
        '        for (BeastRules.Beast beast : BeastRules.family(BeastRules.BRUTE)) {
            event.registerEntityRenderer('
expect "a mob drawn as a black and magenta cube" "one at a time"

edit $CL '        for (String family : BeastRules.FAMILIES) {
            event.registerLayerDefinition(' \
        '        for (String family : java.util.List.of(BeastRules.BRUTE)) {
            event.registerLayerDefinition('
expect "a family with no model layer, which is a crash on sight" "a crash on the first one"

edit $RI '        for (BeastRules.Beast beast : BeastRules.BEASTS) {
            int background' '        for (BeastRules.Beast beast : BeastRules.family(BeastRules.BRUTE)) {
            int background'
expect "a beast with no spawn egg to look at it with" "cannot be put in the world"

edit $EN '        String name = EntityType.getKey(getType()).getPath();' '        String name = "shearjaw";'
expect "a beast that no longer finds its own row" "finds its own row by the name"

edit $EN '        return BeastRules.earned(beast()).experience();
    }

    @Override
    public boolean shouldDropExperience()' '        return 5;
    }

    @Override
    public boolean shouldDropExperience()'
expect "a kill that gives something other than what was derived" "not what the bestiary derived"

edit $EN '            setRemainingFireTicks(8 * 20);' '            setRemainingFireTicks(0);'
expect "wights that do not burn at dawn" "one thing that makes a family more"

edit $EN '        return net.minecraft.world.entity.EntityDimensions.scalable(beast.width(),
                beast.height());' '        return net.minecraft.world.entity.EntityDimensions.scalable(1.0F, 1.0F);'
expect "a beast the size of whichever one the game built the type for" "dimensions are not its row's"

edit $M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java \
  'com.elysium.bestiary.registry.BeastItems.ITEMS.register(modEventBus);' '/* */'
expect "a register declared and never put on the bus" "never put on the bus"

edit $RE '        if (made.size() != BeastRules.BEASTS.size()) {' '        if (false) {'
expect "a registry that never counts what it registered" "does not count what it registered"

echo "independence and the seam"
edit $R 'import java.util.ArrayList;' 'import net.minecraft.world.entity.Mob;
import java.util.ArrayList;'
expect "rules no harness could derive a bestiary from without a game" "the JDK alone"

edit $M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java \
  'public static final String MODID = "elysiumbestiary";' \
  'public static final String MODID = "elysiumbestiary";
    static final Class<?> UP = com.elysium.wilds.Anything.class;'
expect "a bestiary reaching up into a later module" "reaches up into a later module"

edit $M/src/main/java/com/elysium/bestiary/entity/BeastEntity.java \
  'import com.elysium.bestiary.rules.BeastRules;' \
  'import com.elysium.arcana.rules.ArcanaRules;
import com.elysium.bestiary.rules.BeastRules;'
expect "a beast that needs another module installed to exist" "works in a pack with nothing else installed"

python3 - <<'PYX'
import pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-bestiary/src/main/resources/META-INF/neoforge.mods.toml")
s = p.read_text(encoding="utf-8")
s = s.replace('modId="elysiumlib"', 'modId="mowziesmobs"', 1)
p.write_text(s, encoding="utf-8")
PYX
expect "a declared dependency on a mod this module replaces" "beyond the game and the library"

echo "what mines a machine (the harvest audit, 29 Sep 2026)"
HR=$M/src/main/java/com/elysium/bestiary/rules/HarvestRules.java
FB=$M/src/main/java/com/elysium/bestiary/registry/FarmBlocks.java
T=$M/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].remove("elysiumbestiary:grinder_saw")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a machine in no tool's tag" "ask for the right tool and are in no tool's tag"

edit $HR '            if (said == 0) {' '            if (false) {'
expect "the rule that finds a machine no tool is decided for, disabled" "a block no tool is decided for is not reported"

edit $HR '    public static final List<String> BY_HAND = List.of();' \
        '    public static final List<String> BY_HAND = List.of("quiet_casing");'
edit $V '    MINE_EXEMPT = {}' '    MINE_EXEMPT = {"quiet_casing": "wool, and wool breaks by hand"}'
expect_regen "an exempt machine that asks for the right tool" "but it asks for the right tool: it copies Blocks.IRON_BLOCK, which does"

edit $FB 'BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)' 'BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL)'
expect "a machine built on a game block nobody has looked up" "nothing here knows whether that asks for the right tool"

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumbestiary:grinder_sa")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a pickaxe tag naming a block nobody registers" "so the game refuses the whole tag"

edit $HR '    public static final List<String> BY_HAND = List.of();' \
        '    public static final List<String> BY_HAND = List.of("quiet_casing");'
expect "a machine broken by hand for no stated reason" "breaks quiet_casing by hand and this gives no reason"

edit $HR '    public static final String MACHINE_TOOL = "pickaxe";' \
        '    public static final String MACHINE_TOOL = "axe";'
expect "the rules moved the machines and the tag left where it was" "the tags are written from the rules by tools/gen_bestiary.py"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all bestiary checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
edit $M/src/main/java/com/elysium/bestiary/registry/BeastItems.java 'registerRoster("warded", com.elysium.bestiary.rules.WardRules.WARDED.stream()' 'registerRoster("warded", java.util.List.<com.elysium.bestiary.rules.WardRules.Warded>of().stream()'
expect "a roster with an egg's name and no egg" "registers no spawn eggs for WardRules.WARDED"

echo "bestiary: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
