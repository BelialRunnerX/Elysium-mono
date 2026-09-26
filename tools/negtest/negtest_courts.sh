#!/usr/bin/env bash
# Eight mini-bosses and their courts, broken one way at a time.
#
# The property every case below circles: a court is a fight rather than a wall.
# The bestiary's nine beasts are a ladder with a proved reward curve and no boss
# tier at all, and everything added since has been held *under* it. That is the
# wrong default for a boss, so this is the roster where a head is allowed to be
# harder -- by a stated amount, and no more.
#
# The failure this guards against arrives by addition. Nobody ever decides that
# the minions should be the fight; somebody adds a ninth kind of skeleton, the
# budget was never written down, and nothing says when it happened. So the
# budget is written down, it is divided among the court's members, and adding to
# a court makes every member of it weaker.
set -uo pipefail
ROOT=/tmp/Elysium-mono
M=$ROOT/elysium-bestiary
R=$M/src/main/java/com/elysium/bestiary/rules/CourtRules.java
EN=$M/src/main/java/com/elysium/bestiary/entity/CourtEntity.java
AC=$M/src/main/java/com/elysium/bestiary/entity/act/CourtRoles.java
RG=$M/src/main/java/com/elysium/bestiary/registry/CourtEntities.java
MD=$M/src/main/java/com/elysium/bestiary/ElysiumBestiary.java
CL=$M/src/main/java/com/elysium/bestiary/client/BestiaryClient.java
G=$M/tools/gen_bestiary.py
V=$M/validate.py
SNAP=/tmp/courts_snap.tar
pass=0; fail=0
if ! (cd $M && timeout 600 python3 validate.py >/dev/null 2>&1); then
  echo "REFUSING TO START: the module does not validate before any edit."
  exit 3
fi
rm -f $SNAP
(cd $ROOT && tar cf $SNAP elysium-bestiary)
restore() { rm -rf $ROOT/elysium-bestiary && (cd $ROOT && tar xf $SNAP); }
trap 'restore' EXIT INT TERM
out() { (cd $M && timeout 600 python3 validate.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; echo "         got: $(echo "$g" | tail -2 | head -1 | cut -c1-150)"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:70] + '\n'); sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }
regen() { (cd $M && python3 tools/gen_bestiary.py >/dev/null 2>&1); }
expect_regen() { regen; expect "$1" "$2"; }
remove() { rm -f "$1"; }

echo "a court worth more than the head it stands behind"
edit $R '            new Member("forgelet", "forge", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),' \
        '            new Member("forgelet", "forge", SWARM, 2, 95.0D, 3.0D, 0.34D, 3, 40, "", ""),'
expect "a swarm big enough to be the fight" "so the head is the thing you ignore"

edit $R '            if (standing > head.threat() * share) {' '            if (false) {'
expect "the rule that holds a court under its head" "worth more than the head it stands behind is not reported"

edit $R '    public static final double COURT_SHARE = 1.0D;' \
        '    public static final double COURT_SHARE = 100.0D;'
expect "a share wide enough that no court is ever too big" "worth more than the head it stands behind is not reported"

edit $R '            total += one.standing();' '            total += 0.0D;'
expect "a court whose members are counted as worth nothing" "is reported wrongly"

echo "a boss, or a wall"
edit $R '            new Member("graftprime", "graft", HEAD, 3, 194.0D, 14.0D, 0.26D, 1, 4, "minecraft:water_bucket", "minecraft:shears"),' \
        '            new Member("graftprime", "graft", HEAD, 3, 1940.0D, 14.0D, 0.26D, 1, 4, "minecraft:water_bucket", "minecraft:shears"),'
expect "a head past what this pack calls a boss" "into what it calls a wall"

edit $R '            if (one.head() && beside != null' '            if (false && beside != null'
expect "the rule that says how much harder a boss may be" "past what this pack calls a boss is not reported"

edit $R '            if (one.head() && beside(one, beasts) == null) {' '            if (false) {'
expect "a head on a rung with no beast on it" "on a rung with no beast on it is not reported"

edit $R '    public static final double BOSS_OVER_BEAST = 3.0D;' \
        '    public static final double BOSS_OVER_BEAST = 300.0D;'
edit $R '            new Member("rotprime", "rot", HEAD, 1, 39.0D, 7.0D, 0.26D, 1, 4, "minecraft:iron_sword", ""),' \
        '            new Member("rotprime", "rot", HEAD, 1, 139.0D, 7.0D, 0.26D, 1, 4, "minecraft:iron_sword", ""),'
expect "a ceiling wide enough to let a boss out-fight the rung above it" "a whole rung higher"

edit $R '            if (above != null && one.threat() > above.threat()) {' '            if (false) {'
expect "the rule that keeps a boss under the rung above it" "out-fighting a tier-two beast is not reported"

echo "the head of a court"
edit $R '            new Member("forgeprime", "forge", HEAD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "minecraft:shield", "minecraft:bow"),' \
        '            new Member("forgeprime", "forge", SHIELD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "minecraft:shield", "minecraft:bow"),'
expect "a court with nobody at the head of it" "court has no head"

edit $R '            new Member("forgecaller", "forge", HERALD, 2, 16.0D, 6.0D, 0.28D, 1, 10, "", ""),' \
        '            new Member("forge", "forge", HEAD, 2, 16.0D, 6.0D, 0.28D, 1, 10, "minecraft:bow", ""),'
expect "a court with two heads" "court has 2 heads"

edit $R '            if (entry.getValue() == 0) {' '            if (false) {'
expect "the rule that finds a headless court" "with no head is not reported"

edit $R '            if (entry.getValue() > 1) {' '            if (false) {'
expect "the rule that finds a two-headed court" "with two heads is not reported"

edit $R '                if (!one.head() && one.threat() >= head.threat()) {' '                if (false) {'
expect "the rule that keeps the head the hardest thing in its court" "worth more than its own head is not reported"

echo "a second phase that changes nothing"
edit $R '"minecraft:shield", "minecraft:bow"),' '"minecraft:bow", "minecraft:bow"),'
expect "a second phase answered the same way as the first" "in both of its phases"

edit $R '            if (one.twoPhased() && one.then().equals(one.first())) {' '            if (false) {'
expect "the rule that makes a second phase different" "answered by the same thing as the first is not reported"

edit $R '            new Member("hoarprime", "hoar", HEAD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "minecraft:snowball", ""),' \
        '            new Member("hoarprime", "hoar", HEAD, 2, 97.0D, 10.5D, 0.26D, 1, 4, "", ""),'
expect "a head nothing answers at all" "nothing answers it, which is"

edit $R '            if (one.head() && (one.first() == null || one.first().isEmpty())) {' '            if (false) {'
expect "the rule that says every head has an answer" "a head nothing answers is not reported"

edit $R '            if (one.twoPhased() && !one.head()) {' '            if (false) {'
expect "the rule that keeps phases to heads" "that is not a head having two phases is not reported"

edit $R '            new Member("forgecaller", "forge", HERALD, 2, 16.0D, 6.0D, 0.28D, 1, 10, "", ""),' \
        '            new Member("forgecaller", "forge", HERALD, 2, 16.0D, 6.0D, 0.28D, 1, 10, "minecraft:bow", "minecraft:shield"),'
expect "something that is not a head fighting twice" "does not head a court, so"

echo "the one a court may have only one of"
edit $R '            new Member("graftmender", "graft", HEALER, 3, 36.0D, 4.0D, 0.24D, 1, 8, "", ""),' \
        '            new Member("graftmender", "graft", HEALER, 3, 36.0D, 4.0D, 0.24D, 2, 8, "", ""),'
expect "two healers standing at once" "and one is the most any court may have"

edit $R '                if (found > 1) {' '                if (false) {'
expect "the rule that keeps a court to one healer" "with two healers standing at once is not reported"

echo "one court, one rung"
edit $R '            new Member("forgelet", "forge", SWARM, 2, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),' \
        '            new Member("forgelet", "forge", SWARM, 3, 5.0D, 3.0D, 0.34D, 3, 40, "", ""),'
expect "a court straddling two rungs" "measured against two different ladders"

edit $R '                if (one.tier() != rung) {' '                if (false) {'
expect "the rule that holds a court to one rung" "straddling two rungs is not reported"

echo "nothing spare and nothing missing"
edit $R '            new Member("breachcharger", "breach", MOUNT, 3, 28.0D, 6.0D, 0.40D, 1, 8, "", ""));' \
        '            new Member("breachcharger", "breach", SWARM, 3, 28.0D, 6.0D, 0.40D, 1, 8, "", ""));'
edit $R '            new Member("graftcharger", "graft", MOUNT, 3, 18.0D, 6.0D, 0.40D, 1, 8, "", ""),' \
        '            new Member("graftcharger", "graft", SWARM, 3, 18.0D, 6.0D, 0.40D, 1, 8, "", ""),'
expect_regen "a role no court fills" "leaves a role unfilled"

edit $R '            if (!filled) {' '            if (false) {'
expect "the rule that finds an unfilled role" "a role no court fills is not reported"

edit $R '            if (!roles.contains(one.role())) {' '            if (false) {'
expect "the rule that holds a role to something a court has" "filling a role no court has is not reported"

edit $R '            if (!seen.add(one.id())) {' '            if (false) {'
expect "the rule that finds two creatures under one name" "written under one name are not reported"

echo "one branch per role, and no creature named"
edit $AC '            case CourtRules.HEALER -> {' '            case "unreachable" -> {'
expect "a role nothing makes happen on the clock" "nothing decides what a healer does on the clock"

edit $AC '            case CourtRules.SHIELD -> hurt.addEffect(new MobEffectInstance(' \
        '            case "unreachable" -> hurt.addEffect(new MobEffectInstance('
expect "a role nothing decides on contact" "what a shield does when it reaches you"

edit $AC '        Level level = self.level();
        switch (row.role()) {' '        Level level = self.level();
        switch (row.id()) {'
expect "what a creature does on the clock chosen by which creature it is" "in happen is not chosen by which role"

edit $AC '        switch (row.role()) {
            case CourtRules.SWARM -> hurt.addEffect' '        switch (row.id()) {
            case CourtRules.SWARM -> hurt.addEffect'
expect "what a creature does on contact chosen by which creature it is" "in onContact is not chosen by which role"

edit $AC '    public static final int BETWEEN = 60;' \
        '    public static final int BETWEEN = 60;
    private static final String WHICH = "forgeprime";'
expect "a creature named in the branch that makes its role happen" "is named in the roles"

edit $AC '                if (self.roomFor() > 0 && level instanceof ServerLevel server) {' \
        '                if (level instanceof ServerLevel server) {'
expect "a herald that calls without asking whether there is room" "what a court can have standing is unbounded"

edit $AC '    public static void turns(CourtEntity self, CourtRules.Member row) {' \
        '    public static void doesNotTurn(CourtEntity self, CourtRules.Member row) {'
expect "nothing happening when a head turns" "a flag nothing in the world reflects"

echo "the numbers the entity carries"
edit $EN '        CourtRules.Member found = CourtRules.member(name);' \
        '        CourtRules.Member found = CourtRules.MEMBERS.get(0);'
expect "a creature that does not find its own row" "does not find its own row"

edit $EN '        return Math.max(0, member().most() - called);' '        return Math.max(0, 8 - called);'
expect "how many may stand decided somewhere other than the row" "two different numbers"

edit $EN '        return turned() && row.twoPhased() ? row.then() : row.first();' \
        '        return row.first();'
expect "a head still answered by its first phase after turning" "the same fight with more health"

edit $EN '            CourtRoles.happen(this, row);' '            sinceRole = 0;'
expect "nothing handing a creature to the roles" "no court ever does anything"

edit $EN '                && getHealth() <= getMaxHealth() * TURNS_AT) {' '                && false) {'
expect "nothing turning a two-phased head" "a field nothing ever sets"

echo "nothing registered by hand"
edit $RG '        for (CourtRules.Member one : CourtRules.MEMBERS) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),' \
        '        for (CourtRules.Member one : java.util.List.of(CourtRules.MEMBERS.get(0))) {
            made.put(one.id(), BeastEntities.TYPES.register(one.id(),'
expect "a roster of fifty-eight typed out by hand" "pasted line reads correct while pointing at the wrong row"

edit $RG '        if (made.size() != CourtRules.MEMBERS.size()) {' '        if (false) {'
expect "nothing counting the creatures registered" "a roster that half exists"

edit $RG '<CourtEntity>of(CourtEntity::new,
                                    MobCategory.MONSTER)' '<CourtEntity>of(CourtEntity::new,
                                    MobCategory.CREATURE)'
expect "fifty-eight hostile things in the harmless bucket" "against separate caps"

edit $RG '        float bigger = one.head() ? HEADS_ARE : 1.0F;' '        float bigger = 1.0F;'
expect "a head the same size as the things around it" "cannot be told from inside the room"

edit $MD '        com.elysium.bestiary.registry.CourtEntities.ALL.size();' '        '
expect "a register nothing touches while the mod is built" "depends on when FML"

echo "something has to draw them"
edit $CL '                    context -> new CourtRenderer(context,' '                    context -> new MythRenderer(context,'
expect "courts with no renderer registered" "takes the client down with it"

edit $CL '            event.registerLayerDefinition(CourtModel.LAYERS.get(form),' \
        '            event.registerLayerDefinition(MythModel.LAYERS.get(form),'
expect "model layers never registered" "baking one throws"

echo "what the world is allowed to put on the floor"
edit $G '                      "maxCount": one["most"],' '                      "maxCount": 6,'
expect_regen "a spawn rule allowing more of a thing than its row does" "computed against a number the world does not use"

edit $G '                      "weight": one["weight"],
                      "minCount": 1,
                      "maxCount": one["most"],' '                      "weight": 3,
                      "minCount": 1,
                      "maxCount": one["most"],'
expect_regen "a spawn weight that is not the row's" "times on disk"

remove $M/src/main/resources/assets/elysiumbestiary/textures/entity/hoarprime.png
expect "a creature with no texture" "has no texture"

edit $G '        lang["bestiary.%s.role.%s" % (NS, one["id"])] = ROLE_WORDS[one["role"]]' \
        '        pass'
expect_regen "a creature with no sentence saying what it is" "has no sentence saying what it is in its court"

echo "a phase answered by something nothing makes"
edit $R '"minecraft:shield", "minecraft:bow"),' '"minecraft:shield", "minecraft:bowe"),'
expect "a second phase answered by something nothing makes" "nothing makes one of those"

echo "the rules are all asked"
edit $R '        bad.addAll(faultsInCourt(MEMBERS, COURT_SHARE));' '        '
expect "a rule written, compiled and never added up" "faultsInCourt is written, compiled and never added up"

edit $R '        bad.addAll(faultsInCourt(MEMBERS, COURT_SHARE));' '        if (false) bad.addAll(faultsInCourt(MEMBERS, COURT_SHARE));'
expect "a rule added up only sometimes" "faults() has a condition in it"

echo
echo "courts: $pass caught, $fail missed"
[ "$fail" -eq 0 ] || exit 1
