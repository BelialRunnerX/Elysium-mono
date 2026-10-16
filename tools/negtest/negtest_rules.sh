#!/bin/bash
cd /tmp/Elysium-mono/elysium-solarpunk || exit 1
J=src/main/java/com/elysium/solarpunk
pass=0; miss=0
snap() { rm -rf /tmp/_ar; mkdir -p /tmp/_ar; cp -r $J/power /tmp/_ar/; cp -r $J/machine /tmp/_ar/; cp tools/gen_solarpunk.py /tmp/_ar/; cp src/main/resources/assets/elysiumsolarpunk/lang/en_us.json /tmp/_ar/; }
restore() { rm -rf $J/power $J/machine; cp -r /tmp/_ar/power $J/; cp -r /tmp/_ar/machine $J/; cp /tmp/_ar/gen_solarpunk.py tools/; cp /tmp/_ar/en_us.json src/main/resources/assets/elysiumsolarpunk/lang/; }
try() {
  local label="$1" want="$2" break_it="$3"
  snap
  bash -c "$break_it" || { echo "SETUP FAILED: $label"; miss=$((miss+1)); restore; return; }
  if timeout 1800 python3 validate.py 2>&1 | grep -qF "$want"; then
    echo "CAUGHT : $label"; pass=$((pass+1))
  else
    echo "MISSED : $label  (expected: $want)"; miss=$((miss+1))
  fi
  restore
}

try "a Minecraft import in PowerRule" "only stay executable while these files" \
  "sed -i 's|^package com.elysium.solarpunk.power;|package com.elysium.solarpunk.power;\n\nimport net.minecraft.world.level.Level;|' $J/power/PowerRule.java"

try "a condition with no words" "disagree: enum has" \
  "sed -i 's|    CLEAR(\"clear\"),|    CLEAR(\"cloudless\"),|' $J/power/PowerCondition.java"

try "the governor's range hard-coded" "goes stale the moment a condition" \
  "sed -i 's|IntegerProperty.create(\"condition\", 0, PowerCondition.values().length - 1)|IntegerProperty.create(\"condition\", 0, 7)|' $J/power/PowerGovernorBlock.java"

try "the rules stop being applied" "every governor a player has placed" \
  "python3 -c \"
import io,re
p='$J/machine/SolarpunkPower.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('Set<PowerClass> held = PowerRule.held(survey.rules(),','Set<PowerClass> held = java.util.Set.of(); if (false) held = PowerRule2.held(survey.rules(),',1)
s=s.replace('PowerRule2','PowerRuleX')
io.open(p,'w',encoding='utf-8').write(s)
\""

try "the held set is read and ignored" "still asks for power is a class" \
  "sed -i 's|            if (held.contains(classes\[i\])) {|            if (false) {|' $J/machine/SolarpunkPower.java"

try "the grid walked twice a tick" "walks the grid more than once a tick" \
  "sed -i 's|        List<PowerGrid.Receiver> receivers = survey.receivers();|        List<PowerGrid.Receiver> receivers = PowerGrid.receivers(level, from);|' $J/machine/SolarpunkPower.java"

try "governors stop being found" "no longer tests for a governor" \
  "sed -i 's|side_state.getBlock() instanceof PowerGovernorBlock|side_state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock|' $J/power/PowerGrid.java"

try "a governor made to carry power" "cannot annotate a grid without rewiring" \
  "python3 -c \"
import io
p='$J/power/PowerGrid.java'
s=io.open(p,encoding='utf-8').read()
s=s.replace('''                if (side_state.getBlock() instanceof PowerGovernorBlock) {
                    if (rules.size() < MAX_RULES) {
                        rules.add(PowerGovernorBlock.ruleOf(side_state));
                    }
                    continue;''','''                if (side_state.getBlock() instanceof PowerGovernorBlock) {
                    if (rules.size() < MAX_RULES) {
                        rules.add(PowerGovernorBlock.ruleOf(side_state));
                        queue.add(side.immutable());
                    }
                    continue;''',1)
io.open(p,'w',encoding='utf-8').write(s)
\""

try "the survey walk reads through unloaded chunks" "no longer skips unloaded" \
  "python3 -c \"
import io
p='$J/power/PowerGrid.java'
s=io.open(p,encoding='utf-8').read()
i=s.index('public static Survey survey(')
j=s.index('public static int conduitCount(')
s=s[:i]+s[i:j].replace('if (!level.isLoaded(side)) {','if (false) {',1)+s[j:]
io.open(p,'w',encoding='utf-8').write(s)
\""

echo "---- caught $pass, missed $miss"
[ "$miss" -eq 0 ]
