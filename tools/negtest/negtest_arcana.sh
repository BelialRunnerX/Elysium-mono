#!/usr/bin/env bash
# Turning things into other things, broken one way at a time.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-arcana
R=$M/src/main/java/com/elysium/arcana/rules/ArcanaRules.java
S=$M/src/main/java/com/elysium/arcana
G=$M/tools/gen_arcana.py
RES=$M/src/main/resources
SNAP=/tmp/arcana_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP elysium-arcana)
restore() { rm -rf $ROOT/elysium-arcana && (cd $ROOT && tar xf $SNAP); }
out() { (cd $M && timeout 400 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
expect_regen() { local w="$2" g
  (cd $M && python3 tools/gen_arcana.py >/dev/null 2>&1)
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

echo "the table, which is the only place this can be catastrophically wrong"
edit $R '                    if (priced != null && (cheapest == null || priced < cheapest)) {
                        cheapest = priced;
                    }' '                    if (priced != null && (cheapest == null || priced > cheapest)) {
                        cheapest = priced;
                    }'
expect "a thing priced above what it actually costs" "exactly what a player rides"

edit $R '                if (seeds.containsKey(entry.getKey())) {
                    // A decided value is decided. Deriving over the top of one
                    // is how a seed table stops meaning anything.
                    continue;
                }' '                if (false) {
                    continue;
                }'
expect "a decided value derived over" "stops meaning anything"

edit $R '        return Map.copyOf(closed(recipes, worth, seeds));' '        return Map.copyOf(worth);'
expect "a table shipped without being closed against profit" "need not agree with one another"

edit $R '            if (each == null || each <= 0L) {' '            if (false) {'
expect "an unpriceable ingredient treated as free" "dragon egg at nothing"

edit $R '        long each = total / step.makes();
        if (each <= 0L) {' '        long each = total / step.makes();
        if (false) {'
expect "something worth nothing left in the table" "dissolved for ever"

edit $R '            if (total < 0L) {
                return null;                       // it overflowed; it is not a price
            }' '            if (false) {
                return null;
            }'
expect "a recipe whose ingredients overflow a long" "therefore free"

edit $R '            List<String> rideable = profitable(recipes, table);' '            List<String> rideable = java.util.List.of();'
expect "a closure that asks nothing" "closing against something else"

edit $R '                if (table.remove(step.output()) != null) {
                    removed = true;
                }' '                if (table.containsKey(step.output())) {
                    removed = true;
                }'
expect "a closure that removes nothing" "the loop is decoration"

edit $R '                if (seeds.containsKey(step.output())) {
                    // A decided value stays decided. One bad recipe for
                    // cobblestone must not take cobblestone out of the price
                    // list, because everything else is priced from it.
                    continue;
                }' '                if (false) {
                    continue;
                }'
expect "a bad recipe taking cobblestone out of the price list" "takes cobblestone out of the game"

edit $R '            if (priceable && made * step.makes() > spent) {' '            if (priceable && made > spent) {'
expect "a profit check that is not about what a recipe makes" "checking something else"

edit $R '                if (each == null) {
                    priceable = false;
                    break;
                }' '                if (false) {
                    priceable = false;
                    break;
                }'
expect "an unpriceable ingredient judged as free" "reads as profitable"

edit $R '        worth.put("minecraft:cobblestone", 1L);' '        worth.put("minecraft:cobblestone", 4L);'
expect "a unit nobody can hold" "relative to something nobody can hold"

edit $R '        worth.put("minecraft:diamond", 8_192L);' '        worth.put("minecraft:diamond", 128L);'
expect "iron worth more than diamond" "already has in their head"

echo "the two directions, and the difference between them"
edit $R '    public static final float BURN_SHARE = 0.9F;' '    public static final float BURN_SHARE = 1.0F;'
expect "a lossless round trip" "infinite supply of something"

edit $R '    public static final float BURN_SHARE = 0.9F;' '    public static final float BURN_SHARE = 0.2F;'
expect "a transmuter that is really a bin" "it is a bin"

edit $R '        return (long) Math.floor((double) worth * count * BURN_SHARE);' '        return (long) Math.ceil((double) worth * count * BURN_SHARE);'
expect "rounding that leans the wrong way" "the one direction the arithmetic leans"

edit $R '        return worth * (long) count;' '        return worth * (long) count / 2L;'
expect "condensing that costs less than the rules say" "not the loss the rules state"

edit $R '        if (worth <= 0L || motes <= 0L) {
            return 0;
        }' '        if (false) {
            return 0;
        }'
expect "a balance of nothing affording something" "affords something"

echo "who decides"
edit $S/transmute/Transmuting.java '        if (!ArcanaAttachments.knows(player, id)) {' '        if (false) {'
expect "a packet condensing anything at all" "defeated by one message"

edit $S/transmute/Transmuting.java '        long worth = MoteTable.table(player.level()).getOrDefault(id, 0L);' '        long worth = 1L;'
expect "a price that does not come from this server" "somewhere other than this server"

edit $S/transmute/Transmuting.java '        if (!ArcanaAttachments.spend(player, price)) {' '        if (false) {'
expect "motes that are never taken" "everything is free"

edit $S/transmute/Transmuting.java '        if (held.isDamaged() || !held.getComponentsPatch().isEmpty()) {' '        if (false) {'
expect "a worn pick priced as a fresh one" "enchantment launderer"

edit $S/transmute/Transmuting.java '        held.shrink(taken);' '        ;'
expect "one diamond dissolved for ever" "infinite supply of motes"

edit $S/registry/ArcanaAttachments.java '        if (now < 0L) {
            return false;
        }' '        if (false) {
            return false;
        }'
expect "a balance that can go below nothing" "spend twice"

edit $S/registry/ArcanaAttachments.java '        player.setData(LEARNED.get(), List.copyOf(now));' '        was.add(id);'
expect "a catalogue mutated in place" "silently stops growing"

edit $S/registry/ArcanaAttachments.java '                    .copyOnDeath()
                    .build());

    /** Every item this player has dissolved at least one of. */' '                    .build());

    /** Every item this player has dissolved at least one of. */'
expect "a balance lost on death" "punishment for dying"

edit $S/network/ArcanaPayloads.java '    public record ChooseTransmutation(String key, int count)' '    public record ChooseTransmutation(String key, int count, long price)'
expect "a packet that carries the price" "trusted with the one number"

edit $S/block/MoteTable.java '            worth = ArcanaRules.derive(recipes, ArcanaRules.SEEDS);' '            worth = new java.util.HashMap<>(ArcanaRules.SEEDS);'
expect "a table in the world the harness never proved" "a different table from the one that ships"

edit $S/block/MoteTable.java '        TreeMap<String, String> sorted = new TreeMap<>();' '        java.util.HashMap<String, String> sorted = new java.util.HashMap<>();'
expect "a price table that differs on a different day" "different on a different day"

edit $S/block/MoteTable.java '        if (manager != builtAgainst) {' '        if (true) {'
expect "several thousand recipes derived on every click" "does nothing"

echo "arcana in the world"
rm $RES/assets/elysiumarcana/textures/block/transmuter.png
expect "a transmuter with no texture" "purple cube"

edit $G '        "E": ITEM("minecraft:nether_star")}, "transmuter"),' '        "E": ITEM("elysiumfoundry:starsteel_ingot")}, "transmuter"),'
expect_regen "a transmuter only a full install can build" "cannot build the only block it adds"

edit $S/client/TransmuterScreen.java 'public class TransmuterScreen extends ElysiumChooser {' 'public class TransmuterScreen extends net.minecraft.client.gui.screens.Screen {'
expect "a catalogue that looks like a different mod" "different mod from every other list"

edit $S/client/TransmuterScreen.java 'known.keys().get(index), hasShiftDown() ? 64 : 1));' 'known.keys().get(index), 1));'
expect "sixty-four clicks to fill a stack" "sixty-four clicks"

echo "independence, which for a module that prices everything is the whole design"
edit $S/ElysiumArcana.java '    public static final String MODID = "elysiumarcana";' '    public static final String MODID = "elysiumarcana";
    static final Class<?> UP = com.elysium.industry.rules.IndustryRules.class;'
expect "a module that prices everything depending on something" "must not depend on any of the modules"

edit $M/src/main/resources/META-INF/neoforge.mods.toml '    modId="elysiumlib"' '    modId="projecte"'
expect "a dependency on the mod this module replaces" "must not require any of it"

edit $R 'import java.util.ArrayList;' 'import net.minecraft.world.item.Item;
import java.util.ArrayList;'
expect "rules no harness could price without a game" "without a game"

echo "the seam"
edit $S/ElysiumArcana.java 'MODID = "elysiumarcana"' 'MODID = "elysium_arcana"'
expect "a module id nothing else in the tree spells that way" "everything else in the tree spells"

edit $S/ElysiumArcana.java '        com.elysium.arcana.registry.ArcanaAttachments.ATTACHMENTS.register(modEventBus);' '        '
expect "a ledger written and never registered" "none of it exists"

echo
echo "restoring and re-checking"
restore
g="$(out)"
if [[ "$g" == *"all arcana checks passed"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "arcana: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
