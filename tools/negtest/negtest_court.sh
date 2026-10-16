#!/usr/bin/env bash
# The court broken one way at a time: its five wardrobes, the writs of
# audience every envoy sells, and who arrives at every standing.
set -uo pipefail
M=/tmp/Elysium-mono; N=$M/elysium-npcs
G=$N/tools/gen_npcs.py
MD=$N/src/main/java/com/elysium/npcs/client/model/EnvoyModel.java
E=$N/src/main/java/com/elysium/npcs/entity/ImperialEnvoy.java
K=$N/src/main/java/com/elysium/npcs/entity/EnvoyKind.java
S=$N/src/main/java/com/elysium/npcs/EnvoyScheduler.java
R=$N/src/main/java/com/elysium/npcs/rules/CourtRules.java
H=$N/tools/CourtHarness.java
SNAP=/tmp/negtest_court_snapshot.tgz
pass=0; fail=0

# One snapshot of the whole module, put back after every case. The costume
# case regenerates, and gen_npcs.py rewrites Java and textures that a copy of
# one file would not put back byte for byte.
tar czf $SNAP -C /tmp Elysium-mono/elysium-npcs

regen() { (cd $M && python3 elysium-npcs/tools/gen_npcs.py >/dev/null 2>&1; python3 elysium-npcs/tools/gen_village.py >/dev/null 2>&1); }
expect() { local w="$2" g; g="$(cd $N && python3 validate.py 2>&1)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1)); else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
# The closing quote on a line of its own is the shape tools/dryrun.py knows to
# neuter. With it at the end of the last python line instead, a dry run of this
# script made every edit for real and restored none of them (29 Sep).
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }
restore() { rm -rf $N; tar xzf $SNAP -C /tmp; }

echo "the wardrobes"
edit $MD 'show(this.queenParts, kind == EnvoyKind.QUEEN);' 'show(this.queenParts, true);'
expect "one envoy wearing everybody's costume" "wears everybody's costume at once"
restore

edit $MD '    private final ModelPart[] sentinelParts;' '    private final ModelPart[] absentParts;'
edit $MD '        this.sentinelParts = new ModelPart[] {' '        this.absentParts = new ModelPart[] {'
edit $MD '        show(this.sentinelParts, kind == EnvoyKind.SENTINEL);' '        show(this.absentParts, kind == EnvoyKind.SENTINEL);'
expect "an envoy with no wardrobe at all" "renders as a bare body"
restore

edit $MD '    private static void show(ModelPart[] parts, boolean visible) {' '    private static void hide(ModelPart[] parts, boolean visible) {'
expect "nothing to hide the other four" "no show() helper"
restore

echo "the costumes themselves"
edit $G '    rows += costume(kind, p)' '    rows += []'
regen
expect "every envoy stripped to the bare body" "renders as a bare body"
restore

echo "the writs of audience"
edit $E '        ItemStack fee = ElysiumPrices.roll(kind.rewardTier(), ledger);
        if (!fee.isEmpty()) {
            rolled.add(new MerchantOffer(
                    new ItemCost(fee.getItem(), fee.getCount()),
                    new ItemStack(ElysiumNpcs.summonsFor(kind).get()),
                    CourtRules.AUDIENCE_USES, 0, 0.0F));
        }
' ''
expect "the five writs creative-only again" "the five writs are creative-only"
restore

edit $E '                    CourtRules.AUDIENCE_USES, 0, 0.0F));' '                    CourtRules.AUDIENCE_USES, 0, 0.05F));'
expect "a writ row the checks can no longer read" "no longer reads as rolled.add"
restore

edit $E '        ItemStack fee = ElysiumPrices.roll(kind.rewardTier(), ledger);' '        ItemStack fee = ElysiumPrices.roll(0, ledger);'
expect "a writ sold off the bottom shelf" "not priced off the office's top shelf"
restore

edit $E '                    CourtRules.AUDIENCE_USES, 0, 0.0F));' '                    kAmnestyUses, 0, 0.0F));'
expect "two writs a visit" "sells kAmnestyUses a visit"
restore

edit $E '            rolled.add(new MerchantOffer(
                    new ItemCost(fee.getItem(), fee.getCount()),' '            if (kind.meter() == EnvoyKind.Meter.SUSPICION) rolled.add(new MerchantOffer(
                    new ItemCost(fee.getItem(), fee.getCount()),'
expect "a writ gated by a braceless if" "the tail of some other statement"
restore

edit $E '        if (!fee.isEmpty()) {' '        if (!fee.isEmpty() && kind.meter() == EnvoyKind.Meter.SUSPICION) {'
expect "only the Empire's officers sell their writs" "so only some offices sell"
restore

edit $E '        fixedRows = rolled.size();
' ''
expect "fixed rows nobody counts" "does not count its fixed rows"
restore

edit $E '        for (int i = fixedRows; i < current.size(); i++) {' '        for (int i = 0; i < current.size(); i++) {'
expect "a writ bought out comes back as a reward" "rerolls the fixed rows"
restore

edit $R '    public static final int AUDIENCE_USES = 1;' '    public static final int AUDIENCE_USES = 2;'
expect "two writs a visit, by the rule" "writs of audience, not one"
restore

echo "who arrives"
mv $R /tmp/court_rules.away
expect "no rule to run" "rules/CourtRules.java is missing"
restore
rm -f /tmp/court_rules.away

edit $R 'import java.util.Arrays;' 'import java.util.Arrays;
import net.minecraft.world.entity.player.Player;'
expect "a rule that reaches for the game" "reaches for net.minecraft"
restore

edit $R '    public static int[] arrivals(int favor, int suspicion,' '    public static int[] arrive(int favor, int suspicion,'
expect "rules the harness cannot compile against" "do not compile against the JDK alone"
restore

edit $R '        return willing(readsFavor, willDeal, favorLeads, false);' '        return half;'
expect "the dead zone back" "nobody can arrive at 300 of 10201 standings"
restore

edit $K '    SENTINEL("sentinel", Meter.SUSPICION, 0, 1,' '    SENTINEL("sentinel", Meter.SUSPICION, 1, 1,'
expect "a Sentinel who waits for the Code to notice you" "the first Favor 0, Suspicion 0"
restore

edit $K '    SENTINEL("sentinel", Meter.SUSPICION, 0, 1,' '    SENTINEL("sentinel", Meter.SUSPICION, ElysiumStanding.BAND_NOWHERE, 1,'
expect "a gate the harness cannot read" "could not read every member's gate"
restore

mv $H /tmp/court_harness.away
expect "nothing runs the rules" "tools/CourtHarness.java is missing"
restore
rm -f /tmp/court_harness.away

edit $S '        int[] chosen = CourtRules.arrivals(favor, suspicion, readsFavor, willDeal);' '        int[] chosen = java.util.stream.IntStream.range(0, court.length).filter(i -> willDeal[i] && readsFavor[i] == (favor > suspicion)).toArray();'
expect "the scheduler choosing on its own again" "does not ask CourtRules.arrivals"
restore

edit $S '        int favor = ElysiumStanding.getFavor(player);' '        int favor = ElysiumStanding.getSuspicion(player);'
expect "the rule handed the wrong meter" "own Favor and Suspicion"
restore

edit $S '            readsFavor[i] = court[i].meter() == EnvoyKind.Meter.FAVOR;' '            readsFavor[i] = court[i].meter() == EnvoyKind.Meter.SUSPICION;'
expect "the halves swapped" "which members read Favor"
restore

edit $S '            willDeal[i] = court[i].willDealWith(player);' '            willDeal[i] = true;'
expect "an envoy sent to refuse you" "only to refuse you"
restore

edit $S '        return court[chosen[random.nextInt(chosen.length)]];' '        return court[chosen[0]];'
expect "always the first the rule names" "the same envoy comes every time"
restore

echo "what mines a bench (the harvest audit, 29 Sep 2026)"
HR=$N/src/main/java/com/elysium/npcs/rules/HarvestRules.java
ST=$N/src/main/java/com/elysium/npcs/village/ElysiumStations.java
T=$N/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
# The stations file and the validator carry non-ASCII; edit() above reads them
# in the locale's encoding, which on this machine is not UTF-8.
uedit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
assert old in s, 'anchor not found in ' + p
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR: $1"; fail=$((fail+1)); }; }
regen_village() { (cd $M && python3 elysium-npcs/tools/gen_village.py >/dev/null 2>&1); }

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].remove("elysiumnpcs:gear_bench")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "the gear bench in no tool's tag again" "are in no mineable tag and not exempt"
restore

edit $HR '            if (said == 0) {' '            if (false) {'
expect "the rule that finds a bench no tool is decided for, disabled" "a block no tool is decided for is not reported"
restore

edit $HR '    public static final List<String> BY_HAND = List.of();' '    public static final List<String> BY_HAND = List.of("gear_bench");'
uedit $N/validate.py 'MINE_EXEMPT = {}' 'MINE_EXEMPT = {"gear_bench": "a bench of parts, and parts come apart by hand"}'
uedit $ST '                        .strength(2.5F, 6.0F)
                        .sound(SoundType.COPPER)));' '                        .strength(2.5F, 6.0F)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.COPPER)));'
regen_village
expect "an exempt bench that asks for the right tool" "but it asks for the right tool: it says so where it is registered"
restore

uedit $ST '                () -> new Block(BlockBehaviour.Properties.of()' '                () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL)'
expect "a bench built on a game block nobody has looked up" "nothing here knows whether that asks for the right tool"
restore

python3 - "$T" <<'PYX'
import json, sys
p = sys.argv[1]
d = json.load(open(p, encoding="utf-8"))
d["values"].append("elysiumnpcs:gear_benc")
open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2) + "\n")
PYX
expect "a pickaxe tag naming a block nobody registers" "so the game refuses the whole tag"
restore

edit $HR '    public static final List<String> BY_HAND = List.of();' '    public static final List<String> BY_HAND = List.of("gear_bench");'
expect "a bench broken by hand for no stated reason" "breaks gear_bench by hand and this gives no reason"
restore

edit $HR '    public static final String BENCH_TOOL = "pickaxe";' '    public static final String BENCH_TOOL = "axe";'
expect "the rules moved the benches and the tag left where it was" "the tags are written from the rules by tools/gen_village.py"
restore

echo
restore
if (cd $N && python3 validate.py >/dev/null 2>&1); then echo "  tree restored clean"; else echo "  RESTORE FAILED"; fail=$((fail+1)); fi
rm -f $SNAP
echo "court: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
