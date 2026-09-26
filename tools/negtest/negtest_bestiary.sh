#!/usr/bin/env bash
# Nine things that hunt you, broken one at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/BeastRules.java
MD=$M/src/main/java/com/elysium/bestiary/client/BeastModel.java
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

edit $R 'new Beast("siege_beast", BRUTE, 3, 90.0D, 12.0D, 0.26D, 10.0D, 10, 2.0F, 3.0F)' \
        'new Beast("siege_beast", BRUTE, 3, 400.0D, 12.0D, 0.26D, 10.0D, 10, 2.0F, 3.0F)'
expect "a mob that is an event rather than a mob" "it is an event"

edit $R 'new Beast("shardling", PROWLER, 1, 18.0D, 3.0D, 0.34D, 0.0D, 80, 0.6F, 1.1F)' \
        'new Beast("shardling", PROWLER, 1, 4.0D, 3.0D, 0.34D, 0.0D, 80, 0.6F, 1.1F)'
expect "a beast less dangerous than a zombie" "less dangerous than a zombie"

edit $R 'new Beast("shardling", PROWLER, 1, 18.0D, 3.0D, 0.34D, 0.0D, 80, 0.6F, 1.1F)' \
        'new Beast("shardling", PROWLER, 1, 18.0D, 3.0D, 0.34D, 0.0D, 900, 0.6F, 1.1F)'
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

echo "the sheet"
edit $MD '            BODY, new int[]{0, 32, 72, 32},' '            BODY, new int[]{0, 16, 72, 32},'
expect "two parts drawn over one another on the sheet" "overlap on the sheet"

edit $MD '            LEG_RIGHT, new int[]{96, 72, 24, 32});' '            LEG_RIGHT, new int[]{120, 72, 24, 32});'
expect "a part drawn off the edge of the sheet" "runs off a 128-pixel sheet"

edit $MD '            HEAD, new int[]{0, 0, 48, 24},' '            HEAD, new int[]{0, 0, 12, 24},'
expect "a box bigger than the corner of the sheet it is drawn in" "another part's corner"

echo "as big as it looks"
edit $RD '        float across = row.width() * PIXELS / Math.max(shape.wide(), shape.deep());' \
        '        float across = 1.0F;'
expect "a beast drawn as wide as whatever shape it shares" "no longer worked out from how wide it is"

edit $RD '        float up = row.height() * PIXELS / shape.tall();' '        float up = 1.0F;'
expect "a head above its own hitbox" "no longer worked out from how tall it is"

edit $RD '        pose.scale(across, up, across);' '        pose.scale(1.0F, 1.0F, 1.0F);'
expect "a shape never squashed onto the hitbox at all" "not squashed onto the hitbox"

edit $MD '        return new Bounds(highX - lowX, highY - lowY, highZ - lowZ);' \
        '        return new Bounds(16.0F, 32.0F, 16.0F);'
expect "a shape whose size is typed rather than measured" "squashes onto a number somebody typed"

edit $MD '                new Part(LEG_LEFT, -2.0F, -12.0F, 0.0F, -2.0F, 0.0F, -2.0F, 4, 12, 4),
                new Part(LEG_RIGHT, 2.0F, -12.0F, 0.0F, -2.0F, 0.0F, -2.0F, 4, 12, 4)));' \
        '                new Part(LEG_LEFT, -2.0F, -12.0F, 0.0F, -2.0F, 0.0F, -2.0F, 4, 12, 4)));'
expect "a family missing a limb the walk cycle reaches for by name" "rather than six"

edit $MD '        made.put(BeastRules.WIGHT, List.of(' '        made.put("ghoul", List.of('
expect "a family of beasts with no shape at all" "nothing is shaped like a wight"

echo "what it leaves"
edit $G '    return [staple, essence, rare]' '    return [staple + 2, essence, rare]'
expect_regen "a loot table that hands out more than the bestiary is balanced on" "is not the reward a player gets"

edit $G '    twice = drops * 2' '    twice = drops'
expect_regen "a loot table that hands out half what the bestiary says" "is not the reward a player gets"

edit $G '        "count": {"type": "minecraft:uniform", "min": 0.0, "max": float(most)},' \
        '        "count": {"type": "minecraft:constant", "value": float(most)},'
expect_regen "a count nothing can work an expectation out of" "has stopped being checked"

edit $G '    "prowler": "minecraft:string",' '    "prowler": "minecraft:stringg",'
expect_regen "a staple drop with a letter too many in it" "neither the game nor this pack makes them"

edit $G '    "wight": ["minecraft:coal", "minecraft:amethyst_shard", "minecraft:echo_shard"],' \
        '    "wight": ["minecraft:coal", "minecraft:amethyst_shard", "minecraft:echoshard"],'
expect_regen "a rare drop only the hardest wight ever leaves, misspelt" "neither the game nor this pack makes them"

edit $V '                if entry.get("type") == "minecraft:item" and entry.get("name"):' \
        '                if entry.get("type") == "minecraft:itemm" and entry.get("name"):'
expect "a sweep that reads no drops at all out of nine loot tables" "balancing zero against zero"

edit $V '        THE_GAME_HAS = {"minecraft:" + key.split(".", 2)[2] for key in _lang
                        if key.startswith(("item.minecraft.", "block.minecraft."))}' \
        '        THE_GAME_HAS = {"minecraft:" + key.split(".", 2)[2] for key in _lang
                        if key.startswith(("item.mojang.", "block.mojang."))}'
expect "a game list that reads as empty, so the check below it checks nothing" "so the check below is checking nothing"

echo "what you meet"
edit $G '                      "weight": BY_ID[haunt["beast"]]["weight"],' \
        '                      "weight": 1,'
expect_regen "a spawn weight that is not the weight the harness proved the shares from" "is not the share a player meets"

edit $G '                  "biomes": haunt["where"],' '                  "biomes": "#minecraft:is_mountain",'
expect_regen "a bestiary most players never meet" "a third of the bestiary is a rumour"

edit $G '                  "spawners": [{' '                  "spawners": [{
                      "type": "%s:shardling" % NS,
                      "weight": 1,
                      "minCount": 1,
                      "maxCount": 1,
                  }, {'
expect_regen "a beast spawned by two rules at once" "appears twice as often as its weight says"

edit $G '                      "minCount": haunt["least"],' '                      "minCount": 0,'
expect_regen "a spawn roll that puts nothing there" "puts nothing there"

echo "the words, and the tree"
edit $G '    "shardling": "It comes apart when you hit it and the pieces keep coming.",' \
        '    "shardling": "A mob.",'
expect_regen "a beast described in four words" "tells a player nothing they could not see"

python3 - <<'PYX'
import json, pathlib
p = pathlib.Path("/tmp/Elysium-mono/elysium-bestiary/src/main/resources/assets/elysiumbestiary/lang/en_us.json")
words = json.loads(p.read_text(encoding="utf-8"))
del words["entity.elysiumbestiary.stalker"]
p.write_text(json.dumps(words, indent=2) + "\n", encoding="utf-8")
PYX
expect "a beast that reads as its own translation key" "entity.elysiumbestiary.stalker has no name"

python3 - <<'PYX'
import pathlib
from PIL import Image
p = pathlib.Path("/tmp/Elysium-mono/elysium-bestiary/src/main/resources/assets/elysiumbestiary/textures/entity/ripper.png")
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

edit $EN '        String name = EntityType.getKey(getType()).getPath();' '        String name = "ripper";'
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

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all bestiary checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "bestiary: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
