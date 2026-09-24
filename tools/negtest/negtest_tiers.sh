#!/usr/bin/env bash
# P5 — loot identity and world tiers, broken one rule at a time.
#
# A world tier decides what a player is allowed to find, so every check here is
# a check about what the game will and will not hand someone. Each is defeated
# deliberately, and the suite fails if the validators or the harnesses let it
# through.
set -uo pipefail
ROOT=/tmp/Elysium-mono
LIB=$ROOT/elysium-lib
SPACE=$ROOT/elysium-space
LSRC=$LIB/src/main/java/com/elysium/lib
SSRC=$SPACE/src/main/java/com/elysium/space
AFFIX=$LIB/src/main/resources/data/elysiumlib/elysium_affixes
LLANG=$LIB/src/main/resources/assets/elysiumlib/lang/en_us.json
GLOBAL=$LIB/src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json
MODIFIER=$LIB/src/main/resources/data/elysiumlib/loot_modifiers/affixes.json
pass=0; fail=0

SNAP=/tmp/negtest_tiers_snapshot.tgz
tar czf $SNAP -C $ROOT elysium-lib/src elysium-lib/tools elysium-space/src

outlib()   { (cd $LIB && python3 validate.py 2>&1); }
outspace() { (cd $SPACE && python3 validate.py 2>&1); }

expect()  { local w="$2" g; g="$(outlib)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }
expects() { local w="$2" g; g="$(outspace)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; }

edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p).read()
assert old in s, 'anchor not found in ' + p
open(p,'w').write(s.replace(old, new, 1))
" "$@"; }

echo "where a tier begins"
cp $LSRC/tier/ElysiumWorldTier.java /tmp/wt.bak
edit $LSRC/tier/ElysiumWorldTier.java 'public static final int LEVELS_PER_TIER = 10;' \
                                      'public static final int LEVELS_PER_TIER = 9;'
expect "a tier that begins at the wrong level" "began one level early"
cp /tmp/wt.bak $LSRC/tier/ElysiumWorldTier.java

cp $LSRC/tier/ElysiumWorldTier.java /tmp/wt.bak
edit $LSRC/tier/ElysiumWorldTier.java 'return Math.max(0, Math.max(byLevel, worldFloor));' \
                                      'return Math.max(0, byLevel + worldFloor);'
expect "a world floor added to the level instead of compared with it" "not a term"
cp /tmp/wt.bak $LSRC/tier/ElysiumWorldTier.java

cp $LSRC/tier/ElysiumWorldTier.java /tmp/wt.bak
edit $LSRC/tier/ElysiumWorldTier.java '        return at / (at + SHARE_PACE);
    }

    /**
     * That share' '        return Math.min(1.0D, at / SHARE_PACE);
    }

    /**
     * That share'
expect "a tier share that reaches certainty" "reached certainty"
cp /tmp/wt.bak $LSRC/tier/ElysiumWorldTier.java

cp $LSRC/tier/ElysiumWorldTier.java /tmp/wt.bak
edit $LSRC/tier/ElysiumWorldTier.java 'return "Apotheosis " + (tier - MAX_NAMED);' \
                                      'return "Apotheosis " + tier;'
expect "the tiers past the names counted from the wrong place" "counted from the top of the names"
cp /tmp/wt.bak $LSRC/tier/ElysiumWorldTier.java

echo "what a player has earned"
cp $LSRC/tier/ElysiumTiers.java /tmp/ti.bak
edit $LSRC/tier/ElysiumTiers.java 'private static final Map<ResourceLocation, Integer> WORLDS = new LinkedHashMap<>();' \
  'private static final Map<ResourceLocation, Integer> WORLDS = new LinkedHashMap<>();
    static { WORLDS.put(ResourceLocation.parse("minecraft:the_nether"), 2); }'
expect "the library keeping its own list of dimensions" "names a dimension of its own"
cp /tmp/ti.bak $LSRC/tier/ElysiumTiers.java

cp $LSRC/tier/ElysiumTiers.java /tmp/ti.bak
edit $LSRC/tier/ElysiumTiers.java '.serialize(com.mojang.serialization.Codec.INT).copyOnDeath().build());' \
                                  '.serialize(com.mojang.serialization.Codec.INT).build());'
expect "standing lost by dying for it" "does not survive death"
cp /tmp/ti.bak $LSRC/tier/ElysiumTiers.java

cp $LSRC/tier/ElysiumTierHandler.java /tmp/th.bak
edit $LSRC/tier/ElysiumTierHandler.java 'PlayerEvent.PlayerLoggedInEvent event' 'PlayerEvent.PlayerRespawnEvent event'
expect "a world declared after a player was already standing in it" "nothing listens for PlayerLoggedInEvent"
cp /tmp/th.bak $LSRC/tier/ElysiumTierHandler.java

cp $LLANG /tmp/ll.bak
edit $LLANG '"elysium.world_tier.raised"' '"elysium.world_tier.moved"'
expect "a tier that announces itself as a raw key" "announce itself as a raw key"
cp /tmp/ll.bak $LLANG

echo "what a tier is worth"
cp $LSRC/tier/ElysiumTierAugmentHandler.java /tmp/ta.bak
edit $LSRC/tier/ElysiumTierAugmentHandler.java '        instance.removeModifier(id);
        instance.addTransientModifier(' '        instance.addTransientModifier('
expect "an augment stacked instead of replaced" "stack another copy"
cp /tmp/ta.bak $LSRC/tier/ElysiumTierAugmentHandler.java

cp $LSRC/tier/ElysiumTierAugmentHandler.java /tmp/ta.bak
edit $LSRC/tier/ElysiumTierAugmentHandler.java 'instance.addTransientModifier(' 'instance.addPermanentModifier('
expect "an augment saved into the player" "applied permanently"
cp /tmp/ta.bak $LSRC/tier/ElysiumTierAugmentHandler.java

cp $LSRC/tier/ElysiumTierMobs.java /tmp/tm.bak
edit $LSRC/tier/ElysiumTierMobs.java 'if (mob.getData(HARDENED.get())) {
            return;
        }' ''
expect "a mob hardened more than once" "hardened twice"
cp /tmp/tm.bak $LSRC/tier/ElysiumTierMobs.java

cp $LSRC/tier/ElysiumTierMobs.java /tmp/tm.bak
edit $LSRC/tier/ElysiumTierMobs.java 'mob.setHealth(mob.getMaxHealth());' ''
expect "a hardened mob arriving at a fraction of its own bar" "fraction of its own bar"
cp /tmp/tm.bak $LSRC/tier/ElysiumTierMobs.java

cp $LSRC/tier/ElysiumTierAugments.java /tmp/tg.bak
edit $LSRC/tier/ElysiumTierAugments.java 'return 1.0D + MOB_HEALTH_LIMIT * ElysiumWorldTier.share(tier);' \
                                         'return 1.0D + MOB_HEALTH_LIMIT * tier;'
expect "mobs that harden without limit" "passed its limit"
cp /tmp/tg.bak $LSRC/tier/ElysiumTierAugments.java

cp $LSRC/tier/ElysiumTierAugments.java /tmp/tg.bak
edit $LSRC/tier/ElysiumTierAugments.java 'return HEALTH_PER_TIER * Math.max(0, tier);' \
                                         'return HEALTH_PER_TIER * ElysiumWorldTier.share(tier);'
expect "a player augment that curves instead of climbing" "curved instead of climbing"
cp /tmp/tg.bak $LSRC/tier/ElysiumTierAugments.java

cp $LSRC/tier/ElysiumTierAugments.java /tmp/tg.bak
edit $LSRC/tier/ElysiumTierAugments.java 'return tier > ElysiumWorldTier.FRONTIER;' 'return true;'
expect "a bonus for existing" "bonus for existing"
cp /tmp/tg.bak $LSRC/tier/ElysiumTierAugments.java

echo "how good a drop is"
cp $LSRC/tier/ElysiumLootRarity.java /tmp/lr.bak
edit $LSRC/tier/ElysiumLootRarity.java 'return 1.0D - (1.0D - byTier) * (1.0D - byLuck);' \
                                       'return Math.min(1.0D, byTier + byLuck);'
expect "a rarity roll that becomes certain" "reached certainty"
cp /tmp/lr.bak $LSRC/tier/ElysiumLootRarity.java

cp $LSRC/tier/ElysiumLootRarity.java /tmp/lr.bak
edit $LSRC/tier/ElysiumLootRarity.java 'while (rarity < MAX_STEPS && rolls.getAsDouble()' \
                                       'while (rarity < MAX_STEPS * 2 && rolls.getAsDouble()'
expect "a roll that climbs past the cap that makes it terminate" "did not stop at the cap"
cp /tmp/lr.bak $LSRC/tier/ElysiumLootRarity.java

cp $LSRC/tier/ElysiumLootRarity.java /tmp/lr.bak
edit $LSRC/tier/ElysiumLootRarity.java 'double divisor = Math.max(0, step) + STEP_DIVISOR_OFFSET;' \
                                       'double divisor = STEP_DIVISOR_OFFSET;'
expect "every step up as likely as the first" "was not harder than step"
cp /tmp/lr.bak $LSRC/tier/ElysiumLootRarity.java

cp $LSRC/tier/ElysiumLootRarity.java /tmp/lr.bak
edit $LSRC/tier/ElysiumLootRarity.java 'return Math.max(0, rarity);
    }

    /**
     * The highest rarity' 'return Math.max(1, rarity + 1);
    }

    /**
     * The highest rarity'
expect "a Common piece that carries an affix" "Common piece carried an affix"
cp /tmp/lr.bak $LSRC/tier/ElysiumLootRarity.java

echo "the catalogue and its gates"
cp $AFFIX/of_the_ascendant.json /tmp/aa.bak
cp $AFFIX/of_the_mountain.json /tmp/am.bak
edit $AFFIX/of_the_ascendant.json '"min_tier": 4' '"min_tier": 3'
edit $AFFIX/of_the_mountain.json '"min_tier": 4' '"min_tier": 3'
expect "a world tier with nothing new in it" "no affix appears at world tier 4"
cp /tmp/aa.bak $AFFIX/of_the_ascendant.json
cp /tmp/am.bak $AFFIX/of_the_mountain.json

cp $AFFIX/keen.json /tmp/ak.bak
edit $AFFIX/keen.json '"min_tier": 0' '"min_tier": -1'
expect "an affix below the bottom of the scale" "negative world tier"
cp /tmp/ak.bak $AFFIX/keen.json

cp $LSRC/affix/ElysiumAffixCatalogue.java /tmp/ac.bak
edit $LSRC/affix/ElysiumAffixCatalogue.java 'affix.allowedAt(worldTier)' 'true'
expect "a pool that ignores every gate in the catalogue" "let everything through"
cp /tmp/ac.bak $LSRC/affix/ElysiumAffixCatalogue.java

cp $LSRC/affix/ElysiumAffixRoll.java /tmp/ar.bak
edit $LSRC/affix/ElysiumAffixRoll.java 'pool(kind, type, worldTier)' 'pool(kind, type, 0)'
expect "a roller that draws from the Frontier's pool whatever the tier" "read against something else"
cp /tmp/ar.bak $LSRC/affix/ElysiumAffixRoll.java

cp $AFFIX/keen.json /tmp/ak.bak
edit $AFFIX/keen.json '"weight": 14' '"weight": 15'
expect "an affix edited away from its table" "not what tools/gen_affixes.py generates"
cp /tmp/ak.bak $AFFIX/keen.json

cp $AFFIX/keen.json /tmp/ak.bak
edit $AFFIX/keen.json '"type": "attribute"' '"type": "fireproof"'
expect "an affix stating a kind only the armory reads" "nothing in the library reads one"
cp /tmp/ak.bak $AFFIX/keen.json

echo "what reaches a chest"
cp $LSRC/loot/ElysiumAffixInjector.java /tmp/ai.bak
edit $LSRC/loot/ElysiumAffixInjector.java 'return generated;
    }

    @Override
    public MapCodec' 'generated.add(new ItemStack(net.minecraft.world.item.Items.DIAMOND));
        return generated;
    }

    @Override
    public MapCodec'
expect "an injector that adds loot of its own" "adds items to a loot table"
cp /tmp/ai.bak $LSRC/loot/ElysiumAffixInjector.java

cp $LSRC/loot/ElysiumAffixInjector.java /tmp/ai.bak
edit $LSRC/loot/ElysiumAffixInjector.java 'ElysiumLootCategory.of(stack) == null
                    || ' ''
expect "affixes rolled onto bread" "roll affixes onto bread"
cp /tmp/ai.bak $LSRC/loot/ElysiumAffixInjector.java

cp $LSRC/loot/ElysiumAffixInjector.java /tmp/ai.bak
edit $LSRC/loot/ElysiumAffixInjector.java '
                    || !ElysiumAffixEffects.of(stack).isEmpty()' ''
expect "a table's own affixes overwritten" "would be overruled"
cp /tmp/ai.bak $LSRC/loot/ElysiumAffixInjector.java

cp $GLOBAL /tmp/gl.bak
edit $GLOBAL '"replace": false' '"replace": true'
expect "every other mod's loot modifiers replaced" "instead of adding to them"
cp /tmp/gl.bak $GLOBAL

cp $MODIFIER /tmp/mo.bak
edit $MODIFIER '"elysiumlib:affixes"' '"elysiumlib:affix_injector"'
expect "a loot modifier naming a serializer nobody registered" "not the one registered"
cp /tmp/mo.bak $MODIFIER

echo "who the handlers answer for"
cp $LSRC/effect/ElysiumEffectEvents.java /tmp/ee.bak
edit $LSRC/effect/ElysiumEffectEvents.java 'return !stack.isEmpty() && ElysiumLootCategory.of(stack) != null;' \
                                           'return !stack.isEmpty();'
expect "the gate that asks nothing" "silently outside the engine again"
cp /tmp/ee.bak $LSRC/effect/ElysiumEffectEvents.java

cp $LSRC/effect/ElysiumEffectEvents.java /tmp/ee.bak
edit $LSRC/effect/ElysiumEffectEvents.java 'ElysiumEffectHandlers.onHit(weapon, target, attacker);' ''
expect "a kind of effect read from the data and never fired" "never happens"
cp /tmp/ee.bak $LSRC/effect/ElysiumEffectEvents.java

echo "the worlds themselves"
cp $SSRC/world/SpaceTiers.java /tmp/st.bak
edit $SSRC/world/SpaceTiers.java 'm.put("frostfall", ElysiumWorldTier.PINNACLE);' \
                                 'm.put("frostfall", ElysiumWorldTier.ASCENT);'
expects "a longer journey worth less than a shorter one" "lower tier"
cp /tmp/st.bak $SSRC/world/SpaceTiers.java

cp $SSRC/world/SpaceTiers.java /tmp/st.bak
edit $SSRC/world/SpaceTiers.java 'm.put("cinderveil", ElysiumWorldTier.SUMMIT);' ''
expects "a world with no standing at all" "disagree"
cp /tmp/st.bak $SSRC/world/SpaceTiers.java

cp $SSRC/ElysiumSpace.java /tmp/sm.bak
edit $SSRC/ElysiumSpace.java 'com.elysium.space.world.SpaceTiers.declare();' ''
expects "worlds the library never hears about" "never called"
cp /tmp/sm.bak $SSRC/ElysiumSpace.java

echo
echo "restoring and re-checking"
tar xzf $SNAP -C $ROOT
ok=1
(cd $LIB && python3 validate.py >/dev/null 2>&1) || ok=0
(cd $SPACE && python3 validate.py >/dev/null 2>&1) || ok=0
if [ "$ok" = 1 ]; then echo "  tree restored clean";
else echo "  RESTORE FAILED — the tree is still broken"; fail=$((fail+1)); fi
echo "tiers: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
