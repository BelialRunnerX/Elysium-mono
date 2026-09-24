# Apotheosis family (MC 1.21.1 / NeoForge): feature inventory for re-implementation in Elysium

**Versions covered:** Apotheosis 8.8.0 · Apothic Attributes 2.10.1 · Apothic Enchanting 1.6.2 · Apothic Spawners 1.4.0 · Placebo 9.9.2.
**Sources:** jar data (`data/**`), `assets/*/lang/en_us.json`, the Patchouli book *Chronicle of Shadows* (`assets/apotheosis/patchouli_books/apoth_chronicle/en_us`), config defaults read from bytecode (`javap -c`), and class/method names. The web (GitHub `1.21` branch sources) was used only where the jars have no data, and those items are marked **[web]**. Where the jar data and the in-game book disagree, the jar data is used and the conflict is noted.

**Path shorthands used below**
- `AP:` = `/tmp/ref/Apotheosis-1.21.1-8.8.0/data/apotheosis/`
- `AA:` = `/tmp/ref/ApothicAttributes-1.21.1-2.10.1/data/apothic_attributes/`
- `AE:` = `/tmp/ref/ApothicEnchanting-1.21.1-1.6.2/data/apothic_enchanting/`
- `AS:` = `/tmp/ref/ApothicSpawners-1.21.1-1.4.0/data/apothic_spawners/`
- `BOOK:` = Patchouli entries in `…/Apotheosis-1.21.1-8.8.0/assets/apotheosis/patchouli_books/apoth_chronicle/en_us/entries/`

**Feature ID prefixes:** `PL` Placebo (library), `AA` Apothic Attributes, `AP` Apotheosis (Adventure module), `AE` Apothic Enchanting, `AS` Apothic Spawners.
**Size estimates:** S ≈ under a day · M ≈ 1–3 days · L ≈ about a week · XL ≈ several weeks.
**Elysium overlap** was checked read-only in `/tmp/Elysium-mono` (elysium-lib `item/ElysiumSockets, ElysiumRune, ElysiumRuneTiers, ElysiumRarities, ElysiumAscension, ElysiumAreaBreak, ElysiumGear`; `affix/ElysiumAffix(es)`; `stats/ElysiumStats`; `event/ElysiumCombatHandler, ElysiumLootHandler, ElysiumSquadHandler`; `entity/ElysiumSquads`; `craft/ElysiumMakersMark`; elysium-core `compat/ApotheosisBridge`, `item/ElysiumReforgeHandler, ElysiumGearAscension`; elysium-mobs `variant/*, boss/*`; `ElysiumScaling`).

## Summary: feature count per module

| Module | Features | IDs |
|---|---|---|
| Placebo (library pieces the others depend on) | 5 | PL-1 … PL-5 |
| Apothic Attributes | 12 | AA-1 … AA-12 |
| Apotheosis (Adventure + integration) | 34 | AP-1 … AP-34 |
| Apothic Enchanting | 13 | AE-1 … AE-13 |
| Apothic Spawners | 6 | AS-1 … AS-6 |
| **Total** | **70** | |

Content counts (from jar data): **94 affixes** (42 stat, 37 basic_effect, 15 ability) · **5 rarities** · **5 world tiers** · **23 gems** (13 core, 3 overworld, 3 nether, 2 end, 2 twilight) × **6 purities** · **96 affix loot entries** (50 vanilla, 46 Twilight Forest) · **31 invaders** · **4 elites** · **1 mob augment** · **43 gear sets** · **27 tier augments** · **9 rogue spawners** · **4 tiered and 1 endless gateway** · **22 custom attributes** (+1 `max_eterna` from AE) · **7 mob effects** · **37 brewing mixes** · **18 new enchantments** (+1 placeholder) · **26 JSON shelf stat entries** plus 5 code-defined stat blocks · **19 AE infusion recipes** (+4 in Apotheosis) · **16 spawner stats/modifiers** (+16 inverse).

---
# 1. Placebo (library): only the pieces the gameplay depends on

### PL-1: Gear sets
- **What it is:** A weighted equipment loadout (armour + hands) that is put on a mob. Invaders, elites and gateway waves use it.
- **How it works:** JSON type `placebo:gear_set`. It has lists `helmets/chestplates/leggings/boots/mainhands/offhands`, each an array of `{stack, weight}`; a set-level `weight`; and `tags` (for example `haven_melee`). A reference such as `#summit_ranged` picks a random set with that tag, weighted by `weight`. "Enchanted" sets carry enchantment components on their stacks.
- **Content:** 43 sets under `AP:gear_sets/` with tags `{haven,frontier,ascent,summit,pinnacle}_{melee,ranged}`, plus 2 `gateway_only` sets (`bastion_guard`, `nether_herald`, no tags). Materials by tier: haven = leather/chain + stone tools; frontier = chain/iron/diamond (+ Twilight ironwood/steeleaf/knightmetal); ascent = iron/diamond/enchanted gold (+ arctic/fiery/knightmetal); summit = diamond/netherite/enchanted iron/enchanted diamond (+ enchanted arctic/fiery); pinnacle = enchanted netherite/enchanted yeti.
- **Depends on:** none · **Size:** S · **Elysium:** NONE (Elysium mobs use `MobVariant`/families, not loadouts).

### PL-2: Weighted/stack loot entries and tiered weights plumbing
- **What it is:** Loot pool entry `placebo:stack_entry` (a stack with `min`/`max` count), used by every Apotheosis loot table.
- **Depends on:** none · **Size:** S · **Elysium:** HAVE equivalent (vanilla loot tables suffice).

### PL-3: Wandering-trader trade registry
- **What it is:** Datapack trades: `placebo:basic_trade` = `{input_1, input_2?, output, rare, xp}`. Apotheosis adds `apotheosis:tiered_trade` (a map from world tier to trade) and `apotheosis:affix_trade`/`automatic_affix_trade`. See AP-27.
- **Depends on:** none · **Size:** S · **Elysium:** PARTIAL (elysium-npcs has `ElysiumVillagerTrades`, which covers villagers only).

### PL-4: JSON brewing mixes
- **What it is:** `brewing_mixes/*.json` = `{mix_type:"potion", from, ingredient, to}`, loaded as brewing recipes. Used by AA-9 (37 mixes).
- **Depends on:** none · **Size:** S · **Elysium:** NONE.

### PL-5: Dynamic reloadable registries, synced config, and supporter cosmetics
- **What it is:** The base class for every datapack registry above (reload, sync to client, conditions). Also provides server-authoritative/synced `.cfg` configs. The Patreon wings/trails cosmetics (`placebo.toggleWings`) are not relevant to Elysium.
- **Depends on:** none · **Size:** M (only if you mirror the generic infrastructure) · **Elysium:** PARTIAL (Elysium has its own config/registries).

---

# 2. Apothic Attributes (AA)

### AA-1: Custom attribute registry (22 attributes)
- **What it is:** New entity attributes, synced to the client, with GUI descriptions. Many take "percentage" display (`PercentageAttribute`). Source: bytecode of `api/ALObjects$Attributes`; descriptions from `assets/apothic_attributes/lang/en_us.json`. Constructor order is (default, min, max).

| Attribute (`apothic_attributes:`) | Default | Min | Max | Kind | Exactly what it does (lang + code) |
|---|---|---|---|---|---|
| `armor_pierce` | 0 | 0 | 1000 | flat | Flat reduction of the target's armour before the armour formula; can push the target below 0 (AA-2). |
| `armor_shred` | 0 | 0 | 2 | % | Percentage reduction of target armour, applied **before** pierce. |
| `arrow_damage` | 1 | 0 | 10 | % mult | Multiplies arrow base damage on spawn (`baseDamage × value`). "Tridents are arrows." |
| `arrow_velocity` | 1 | 0 | 10 | % mult | Multiplies arrow velocity vector on spawn. Vanilla arrow damage scales with speed. |
| `cold_damage` | 0 | 0 | 1000 | flat | Bonus magic damage on melee hit plus Slowness (AA-4). |
| `crit_chance` | 0.05 | 0 | 10 | % | Chance for any damage to crit; over 100% allows multi-crits (AA-3). |
| `crit_damage` | 1.5 | 1 | 100 | % mult | Crit multiplier; also replaces the vanilla jump-crit 1.5×. |
| `current_hp_damage` | 0 | 0 | 1 | % | Bonus physical damage = target current HP × value, on melee hit. |
| `dodge_chance` | 0 | 0 | 1 | % | Chance to fully avoid a melee attack or projectile (AA-6). |
| `draw_speed` | 1 | 0 | 4 | % mult | Speeds up bow/crossbow/trident use ticks (AA-7). |
| `experience_gained` | 1 | 0 | 1000 | % mult | Multiplies XP from mob kills and block breaks. |
| `fire_damage` | 0 | 0 | 1000 | flat | Bonus magic damage on melee hit plus burning (AA-4). |
| `ghost_health` | 0 | 0 | 1000 | flat | "Not Yet Implemented" (lang). Hidden from the GUI by default. |
| `healing_received` | 1 | 0 | 1000 | % mult | Multiplies all incoming healing (`LivingHealEvent`). |
| `life_steal` | 0 | 0 | 10 | % | Heals attacker for % of post-mitigation damage (AA-5). |
| `mining_speed` | 1 | 0 | 10 | % mult | Multiplies block break speed. |
| `overheal` | 0 | 0 | 10 | % | Converts % of post-mitigation damage into absorption (AA-5). |
| `projectile_damage` | 1 | 0 | 10 | % mult | Multiplies damage of every projectile the entity owns. Mobs use this in place of attack damage. |
| `prot_pierce` | 0 | 0 | 34 | flat | Flat reduction of target Protection points; cannot go below 0. |
| `prot_shred` | 0 | 0 | 1 | % | % reduction of target Protection points, applied **before** prot pierce. |
| `elytra_flight` | false | – | – | boolean | Fly as if wearing an elytra. |
| `cooldown_reduction` | 0 | −10 | 0.95 | % | Reduces "⌛" cooldowns of triggered abilities (affix and gem cooldowns, Berserker's Fury). |

Also referenced: `neoforge:creative_flight` (used by the *Unbound* affix) and `apothic_enchanting:max_eterna` (AE-12).
- **Data-driven?** No (code registry). **Player interaction:** none directly; values come from gear, gems, affixes and effects.
- **Depends on:** none · **Size:** M · **Elysium:** PARTIAL. `ElysiumStats` has 12 character stats (Vitality, Fortitude, Resilience, Strength, Agility, Accuracy (crit), Reflexes (dodge), Retribution (reflect), Intellect, Willpower, Luck, Presence), and lifesteal/crit/dodge are resolved in `ElysiumCombatHandler`. There are no real `Attribute`s for pierce/shred/prot pierce/arrow/draw/mining/xp/healing/overheal/cold/fire/current-HP.

### AA-2: Armour and Protection formula replacement ("combat rules")
- **What it is:** Replaces the vanilla armour and protection math so that high values stay meaningful and penetration works.
- **How it works:** These are config defaults from `ALConfig` bytecode (`[combat_rules]`, EvalEx expressions):
  - A-value: `if(damage < 20, 10, 10 + (damage - 20) / 2)`
  - Armour: damage taken multiplier `a / (a + armor)`. Armour is first reduced by **shred then pierce**: `armor' = armor × (1 − shred × (1 − T)) − pierce × (1 − T)`. Here `T` is the toughness factor (`getBypassResistance`), and shred/pierce are skipped if below 0.001. Confirmed in bytecode `ALCombatRules.getDamageAfterArmor`, which afterwards also applies vanilla `EnchantmentHelper.modifyArmorEffectiveness` (the mace Breach enchantment).
  - Toughness: `min(toughness × 0.02, 0.6)` = the fraction by which the attacker's pierce/shred is reduced. In Apotheosis, toughness **does not** add damage reduction (BOOK `attributes/armor_toughness`).
  - Negative armour: if `armor' < 0`, then `damage × (1 + |armor'| × 0.015)` (Negative Armor Factor).
  - Protection: `1 - min(0.025 * protPoints, 0.85)`, computed after prot shred then prot pierce. So each Protection point is 2.5%, capped at 85% (vanilla is 4% per point, 80% cap). Points: Protection gives 1 per level, specialized protections give 2 per level, Feather Falling gives 3 per level (BOOK `enchanting/changes/protection`).
  - Apotheosis also raises attribute caps: `generic.armor` max = **200** and `generic.armor_toughness` max = **100** (bytecode `Apotheosis.class`).
- **Data-driven?** Config expressions. **Depends on:** AA-1 · **Size:** M · **Elysium:** NONE (Elysium has its own "Resilience" flat % after armour, not an armour-formula replacement).

### AA-3: Critical strikes (multi-crit)
- **How it works [web + bytecode]:** Runs on `LivingIncomingDamageEvent`. `rand < critChance && critDmg > 1` → `damage += base × (critDmg − 1)`. Then `critChance -= 1` and `critDmg *= 0.85` (constant `0.85f` in `apothCriticalStrike`), and the loop repeats. So 250% chance gives 2 guaranteed crits plus a 50% chance of a third, each weaker. Vanilla jump crits use `crit_damage` instead of 1.5 (`vanillaCritDmg`). Damage types tagged `apothic_attributes:cannot_critically_strike` (`current_hp_damage`, `#bypasses_invulnerability`) never crit. A custom crit particle payload is sent.
- **Depends on:** AA-1 · **Size:** S · **Elysium:** PARTIAL (Accuracy gives crit chance, but there is no multi-crit or crit-damage stat).

### AA-4: Auxiliary melee damage (current-HP, fire, cold)
- **How it works (bytecode `AttributeEvents.lambda$meleeDamageAttributes$0`, `AuxDmgTracker`):** On a *physical* melee hit whose direct attacker is a LivingEntity:
  - `current_hp_damage`: extra hit = `target.getHealth() × value`, damage type `apothic_attributes:current_hp_damage` (physical, cannot crit).
  - `fire_damage`: extra hit = `value`, type `fire_damage` (tag `is_magic` + `bypasses_armor`). Afterwards the target's fire ticks += `10 × dealt`.
  - `cold_damage`: extra hit = `value`, type `cold_damage` (magic, bypasses armour). Afterwards Slowness is applied with duration `min(150, 15 × dmg)` ticks and amplifier `max(0, log2(round(dmg / 5)))`.
  - Each aux hit is scaled by the attacker's attack strength. It is skipped below a threshold (constants `0.55f`, `0.001`), and invulnerability frames are bypassed via a tracker. **[threshold semantics inferred]**
- **Depends on:** AA-1, AA-10 · **Size:** M · **Elysium:** NONE (Elysium has an elemental counter matrix, a different model).

### AA-5: Life steal and overheal
- **How it works [web]:** On `LivingDamageEvent.Post`, `dmg = min(newDamage, preHealth)`. Heal attacker by `dmg × life_steal`. Absorption becomes `min(maxHP × 0.5, currentAbsorption + dmg × overheal)`, so overheal is capped at 50% of max HP. Only physical damage counts (BOOK).
- **Depends on:** AA-1 · **Size:** S · **Elysium:** PARTIAL (lifesteal exists in `ElysiumCombatHandler`/passives; no overheal).

### AA-6: Dodge
- **How it works [web + bytecode]:** Melee (attacker within interaction/melee range) or any projectile impact. Roll against `dodge_chance` with a per-tick deterministic seed `base + 0x9E3779B9 + (base<<6) + (base>>2)`, where `base = tickCount + UUID.hashCode()`. On a dodge the damage/impact is cancelled, a sound plays and smoke particles appear (`onDodge`).
- **Depends on:** AA-1 · **Size:** S · **Elysium:** HAVE (Reflexes dodge in `ElysiumCombatHandler`).

### AA-7: Ranged attributes (draw speed, arrow damage/velocity, projectile damage)
- **How it works:** `draw_speed` adds extra use-ticks per tick: whole points add ticks, and the fractional part is applied every ⌊1/t⌋ ticks (constants `0.5`) **[web]**. It applies only to items that benefit (bows, crossbows, tridents). When an arrow joins the world, its base damage is multiplied by `arrow_damage` and its velocity by `arrow_velocity`. `projectile_damage` multiplies incoming projectile damage by the shooter's value (`projDmg`). There is also a trident fix (`ThrownTridentMixin`).
- **Depends on:** AA-1 · **Size:** S · **Elysium:** NONE.

### AA-8: Utility attributes and cooldown system
- `mining_speed` → `BreakSpeed × value`. `experience_gained` → mob XP (`LivingExperienceDropEvent`) and block XP (`BlockDropsEvent`) × value. `healing_received` → heal × value. `elytra_flight` and `creative_flight` grant flight. `cooldown_reduction` feeds `api/CooldownTracker` and `AbilityCooldowns`, which every "⌛" effect uses.
- **Depends on:** AA-1 · **Size:** S · **Elysium:** PARTIAL (Luck exists; no cooldown API).

### AA-9: Mob effects, potions and brewing
| Effect | Mechanic (bytecode/web/BOOK) |
|---|---|
| `sundering` | +20% damage taken per level (additive with Resistance; "Sundering II + Resistance II = normal damage"). |
| `bleeding` | Every 40 ticks deals `1 + amplifier` damage, type `apothic_attributes:bleeding` (bypasses armour). |
| `grievous` (Grievous Wounds) | `healing_received` −0.4 per level (100% blocked at III+). |
| `vitality` (Bursting Vitality) | `healing_received` +0.2 per level. |
| `knowledge` (Ancient Knowledge) | `experience_gained` +`knowledgeMultiplier` per level (config default **4.0**). BOOK says ×4 at I and ×8 at II. |
| `detonation` (Flaming Detonation) | On its last tick: clears fire and deals `(1+amp) × remainingFireTicks / 14`. 100 flame particles, dragon-fireball sound at pitch 1.2 **[web]**. |
| `flying` | Grants flight via attribute modifier. |

Potions: resistance, absorption, haste, mining fatigue, wither, levitation, flying, sundering, knowledge, vitality, grievous (+ long/strong variants). **37 brewing mixes** (`AA:brewing_mixes/`), for example awkward+golden apple→absorption, awkward+mushroom stew→haste, awkward+XP bottle→knowledge, awkward+shulker shell→resistance, awkward+sweet berries→vitality, awkward+wither skeleton skull→wither, haste+FSE→fatigue, resistance+FSE→sundering, vitality+FSE→grievous, slow falling+FSE→levitation, levitation+popped chorus→flying (+ redstone/glowstone upgrades; knowledge strong = XP bottle). The config also adds potion description tooltips.
- **Depends on:** AA-1, PL-4 · **Size:** M · **Elysium:** PARTIAL (mob abilities include "Sunder"-like effects in `MobAbilities`, but there are no registered effects).

### AA-10: Damage types and tags
5 damage types (`AA:damage_type/`): `bleeding, cold_damage, current_hp_damage, detonation, fire_damage`. Tags: `bypasses_armor` += bleeding, detonation, fire, cold. `neoforge:is_magic` += detonation, fire, cold. `neoforge:is_physical` += current_hp_damage. `apothic_attributes:is_non_physical` = magic/poison/wither/fire/explosion/lightning/freezing. Death messages are in lang.
- **Depends on:** none · **Size:** S · **Elysium:** NONE.

### AA-11: Attributes GUI and attribute tooltip rework
- **What it is:** An inventory button (offset −3,3) opens a scrollable list of every attribute with base, min, max and current value. Hover shows each modifier and its source (item or effect), the modifier formula (`Addition / Multiply Base / Multiply Total`), and a "Hide Unchanged" toggle. Hidden by default: `neoforge:nametag_distance, neoforge:creative_flight, apothic_attributes:elytra_flight, apothic_attributes:ghost_health`. Item tooltips are rewritten to show merged modifier values, "(dynamic)" entries and base values.
- **Depends on:** AA-1 · **Size:** M · **Elysium:** PARTIAL (Elysium has its own character/stat screens; check `elysium-lib/client`).

### AA-12: Stack attribute-modifier API, Curios slot groups, `/apoth` bonus modifier command
- `StackAttributeModifiersEvent` lets code add attribute modifiers per stack (this is how affixes and gems contribute). There is also an equipment-slot abstraction including Curios slots, and a command `BonusModifierCommand`.
- **Depends on:** AA-1 · **Size:** M · **Elysium:** PARTIAL (Elysium applies rune/affix modifiers through the item attribute component).

---
# 3. Apotheosis 8.8.0 (AP): Adventure module and integrations

### AP-1: World Tiers (progression)
- **What it is:** Five per-player difficulty/loot tiers: **Haven → Frontier → Ascent → Summit → Pinnacle**. They set loot rarity and gem purity weights, mob and player augments (AP-3), which invaders, gems, elites and affixes can appear, invader spawn chance, gateway access, and max Eterna.
- **How it works:**
  - A tier is unlocked by an advancement (`AP:advancement/progression/*.json`) and then **activated manually** in the World Tier Select Screen (keybind `CTRL+T`, `key.apotheosis.open_world_tier_select`). Tiers can be switched down at any time. The tier is per player; world content "resolves the tier of the closest player" (BOOK `world_tiers/select_screen`).
  - Unlock criteria (jar advancements):
    - Haven = pick up any affixed item (`inventory_changed` with `apotheosis:affixed_item`).
    - Frontier = `apotheosis:equipped_item` with rarity ≥ Common in head/chest/legs/feet/hand.
    - Ascent = ≥ Uncommon in all 5 slots **and** kill an entity matching `apotheosis:is_invader`.
    - Summit = ≥ Rare in all 5 slots **and** kill the Wither.
    - Pinnacle = ≥ Epic in all 5 slots **and** kill the Ender Dragon (BOOK).
  - Config `[world_tiers] Enable Manual World Tier Changes` (default true). Stat `world_tiers_activated`. There is a tutorial screen, a "Detailed Info" screen listing tier augments plus rarity and purity percentages, and a `WorldTierCommand`. An item tooltip shows "Requires World Tier: X". Gateways check the tier (AP-26).
- **Data-driven?** Tier set is hard-coded (`tiers/WorldTier` enum). Unlocks are advancements.
- **Player interaction:** keybind screen, advancements tab "Apothic World Tiers".
- **Depends on:** AP-2 · **Size:** L · **Elysium:** PARTIAL. Elysium scales mobs by average nearby character level plus standing meters (`ElysiumScaling`), and has gear "tiers" (`ElysiumRarities` / ascension). There is no player-chosen world-difficulty tier that drives loot tables.

### AP-2: Tiered weights, luck/quality and generation context (`GenContext`)
- **What it is:** The weighting primitive used by rarities, affixes, gems, purities, loot entries, invaders, elites and gear.
- **How it works:** A weight is either one `{weight, quality}` for all tiers or a per-tier map. **Missing tiers = weight 0.** Effective weight = `weight + round(luck × quality)` **[web: `TieredWeights.getWeight`]**. Luck is the player's `generic.luck` attribute (tier augments raise it, AP-3). `Constraints` = `{dimensions:[…], biomes: tag}`. `GenContext` carries rand, tier, luck, dimension, biome and position.
- **Depends on:** none · **Size:** S · **Elysium:** NONE (Elysium rolls are derived and deterministic in `ApotheosisBridge`).

### AP-3: Tier augments (world-tier buffs)
- **What it is:** Attribute modifiers or damage reductions applied to **players** in a tier or to **monsters spawned** in that tier. Source `AP:tier_augments/<tier>/*.json`; format `{type: apotheosis:attribute|apotheosis:damage_reduction, tier, target: players|monsters, modifier{attribute,operation,value} | amount+damage_type, sort_index}`.

| Tier | Players | Monsters |
|---|---|---|
| Haven | max_eterna −70 (if AE loaded) | — |
| Frontier | experience_gained +35% (mult_total), luck +20% (mult_total), max_eterna −55 | armor +4 |
| Ascent | XP +55%, luck +30%, max_eterna −40 | armor +8, armor_pierce +5 |
| Summit | XP +75%, luck +50%, max_eterna −25 | armor +12, toughness +10, armor_pierce +15, prot_pierce +10, −10% physical, −10% magic damage |
| Pinnacle | XP +125%, luck +100% (no Eterna penalty) | armor +16, toughness +20, armor_pierce +25, prot_pierce +20, −15% physical, −15% magic |

Note: `luck` uses `add_multiplied_total`, so it only helps if the player already has luck.
- **Depends on:** AP-1, AA-1, AE-12 · **Size:** S · **Elysium:** NONE.

### AP-4: Loot categories (what kind of gear an item is)
- **What it is:** Maps every item to a category: `melee_weapon, trident, bow, crossbow, breaker (mining tool), pickaxe, shovel, helmet, chestplate, leggings, boots, armor, shield, shears, none` (lang `loot_category.*`). Categories gate affixes and gems and decide which rarity-rule list applies. Detection is code-based (tool actions, armour slot, etc.). Data map override `AP:data_maps/item/loot_category_overrides.json` sets `iron_sword→melee_weapon` and `shulker_shell→none`.
- **Depends on:** none · **Size:** S · **Elysium:** PARTIAL (`ElysiumGear` decides "is gear" via `TieredItem`, armour slot or attack-damage modifier, but it has no category taxonomy).

### AP-5: Rarities and rarity rules
- **What it is:** Five rarities (`AP:rarities/*.json`, type `apotheosis:rarity`). Each has a colour, a salvage material, an invader spawn sound, render data (beam height, glow and animated shadow textures) and a **rule list** executed in order during generation (AP-8). The lang also has an unused "Ancient" rarity.
- **Rule types [web: `LootRule`]:** `affix {affix_type}` picks one weighted affix of that type that fits the item. If none is available it logs an error and adds nothing. The affix level is `rand.nextFloat()` (0–1). `chanced {chance, rule}` runs its rule if `nextFloat() ≤ chance`. `select {chance, if_true, if_false}` runs one branch or the other. `socket {min,max}` sets sockets to `nextIntBetweenInclusive(min,max)` but only ever increases. `durability {min,max}` sets `durability_bonus = lerp(nextFloat,min,max)`. `component {components}` applies a component patch (used for **Unbreakable** mythics).

| Rarity | Colour | Material | Rules (in order) | Tier weights `weight(quality)` H / F / A / S / P |
|---|---|---|---|---|
| Common | #808080 | Mysterious Scrap Metal | stat; 25% stat | 600 / 290 / 100 / 0 / 0 |
| Uncommon | #33FF33 | Timeworn Fabric | stat; stat; 50% basic_effect; socket 0–1 | 360(q2.5) / 600 / 300 / 120 / 0 |
| Rare | #5555FF | Luminous Crystal Shard | stat; stat; basic_effect; 35% basic_effect; socket 0–2; durability 10–25% | 40(q5) / 100(q5) / 500(q2.5) / 290(q2.5) / 100 |
| Epic | #BB00BB | Arcane Sands | stat×3; basic_effect; 25% basic_effect; ability; socket 1–3; durability 25–55% | 0 / 10 / 100(q5) / 540(q5) / 650(q2.5) |
| Mythic | #ED7014 | Godforged Pearl | stat×4; basic_effect×2; ability; 95%→socket 1–3 else **socket 4**; 99%→durability 45–75% else **Unbreakable** (removes durability_bonus) | 0 / 0 / 0 / 50(q5) / 250(q10) |

- **Rarity override for shears** (`AP:rarity_override/apotheosis/shears.json`): C = socket 0–1, stat, dur 5–10%. U = socket 1, stat, dur 10–15%. R = socket 1, stat, basic, dur 10–25%. E = socket 1, stat, 2×basic, dur 25–55%. M = socket 1–2, stat, 2×basic, 99% dur 45–75% else unbreakable.
- **Book vs data:** BOOK `affix_loot/rarities` says Rare gets 3 stats and Epic may get a 4th stat. **The data says Rare = 2 stats and Epic = exactly 3 stats + 1 ability.** Use the data.
- **Visuals:** a dropped item shows a coloured beam (height 0/0/2.5/3/3.5), glow, shadow sprite sheet, and particles for Epic and Mythic. Client config `Enable Affix Item Effects`.
- **Depends on:** AP-2, AP-4 · **Size:** M · **Elysium:** PARTIAL. `ElysiumRarities` has 6 named gear tiers plus infinite "Ascendant N", each with level requirement, elemental advantage and reforge budget. These are tiers, not rule-based generation.

### AP-6: Affix framework (affix instances, levels, naming, durability affix)
- **What it is:** Affixes are datapack entries (`AP:affixes/**`, codec by `type`) with a `definition{affix_type: stat|basic_effect|ability, exclusive_set:[affix ids], weights}`. They are stored on the item as component `apotheosis:affixes` (affix → level float), plus `apotheosis:rarity`, `apotheosis:durability_bonus`, `apotheosis:sockets`, `apotheosis:touched_by_malice` and the affixed-name component.
- **Level → value:** `values` are per-rarity `{min,max,step}` (or a constant). Value = min + (max−min)×level, quantized by `step` (negative steps allowed, e.g. Enlightened). Level 0–1 is the standard range (`STANDARD_MAX_LEVEL=1.0`). Overcharge goes up to `MAX_LEVEL=2.0` (bytecode `Affix`), which extrapolates beyond max. Level-independent affixes (flags, telepathic, etc.) are skipped by augmenting and malice.
- **Hooks an affix can implement (from `Affix` methods):** attribute modifiers, `doPostAttack`, `doPostHurt`, `onHurt` (damage reduction), `onShieldBlock`, `onBlockBreak`, `onProjectileFired`, `onProjectileImpact`, `onItemUse`, `modifyLoot`, `getEnchantmentLevels`, durability handling.
- **Mob-effect affixes:** `target` ∈ `attack_self, attack_target, hurt_self, hurt_attacker, break_self, arrow_self, arrow_target, block_self, block_attacker, projectile_self, projectile_target`. Each has `cooldown` (ticks, "⌛", reduced by `cooldown_reduction`), optional `stack_on_reapply` + `stacking_limit` (adds amplifier up to the limit), and `duration`/`amplifier` as leveled values.
- **Durability bonus** ("Durable", `affix.apotheosis:durable.desc`): "ignores X% of durability damage", applied after Unbreaking (multiplicative) (BOOK).
- **Naming:** the item name becomes `[prefix affix] [base name] [suffix affix]`, built from 1–2 random affixes (`misc.apotheosis.affix_name.two/three`), coloured by rarity, not italic. `NameHelper` also generates boss/item root names from `apotheosis/names.cfg` (config lists: Names, Name Parts, Prefixes, Suffixes (always "the …"), and per-item root names for helms, chestplates, etc.). Probabilities in `NameHelper`: 0.4 and 0.15 (extra name parts), 0.45 (use a full name vs. parts), 0.3 (prefix), 0.8 (suffix).
- **Sigil of Unnaming / `unnaming` smithing recipe** removes the affix name components.
- **Depends on:** AP-4, AP-5, AA-1, AA-12 · **Size:** XL (hooks into every combat, tool and projectile event) · **Elysium:** PARTIAL. `ElysiumAffix` has a min/max level-interpolated attribute bonus (0–1 roll), psionic affixes per element, and runes carry affixes. There are no typed affix pools, exclusivity, per-rarity value tables, effect hooks, cooldowns or naming.

### AP-7: Affix catalogue (94 affixes)
Data: `AP:affixes/**/*.json`. Abbreviations: C/U/R/E/M = rarity. `a–b/s` = min–max with step s. `t` = ticks. `amp` = amplifier. `cd` = cooldown in ticks. Weight `25(q0.1)` = weight 25, quality 0.1 in every tier. `pin:` = Pinnacle-only. Names and suffixes come from `assets/apotheosis/lang/en_us.json`.

**Semantics of the non-attribute impl types** (lang descriptions + bytecode constants):
- `damage_reduction`: reduces incoming damage of that type by value %. Bypass-invulnerability/enchantment damage is excluded.
- `enchantment`: adds levels to an enchantment. `mode: existing` = only if the item already has it. `global` (gems) = every existing enchantment.
- `radial`: breaks an x×y area (with offsets). The keybind "Change Radial Mining Mode" cycles *Enabled / Disabled / Enabled While Sneaking / Disabled While Sneaking*. Multiple listed shapes = one is picked at roll.
- `omnetic`: harvests every block as if it were the listed tool set (iron/diamond/netherite axe+shovel+pickaxe+sword+hoe), with the best speed.
- `stoneforming` (stone/sand/leaf/gardening forming): breaking a block in the candidate tag drops the selected target block instead. Shift-right-click chooses the target. Tags: `stoneforming_candidates = #base_stone_overworld,#c:cobblestones`; `sandforming = #sand,#terracotta,clay,gravel`; `leafforming = #leaves`; `gardening = #small_flowers,#tall_flowers,#flowers`.
- `enlightened`: right-click places a torch (config `Torch Placement Item`, default `minecraft:torch`) for **value** durability.
- `telepathic`: mob drops (weapons) or block drops (tools) go straight to the player's inventory.
- `cleaving`: at full attack strength (≥0.98), with `chance`, the attack also hits up to `targets` enemies within 6 blocks. Players are not hit unless config `Cleave Players` (default false) is true.
- `thunderstruck`: at attack strength ≥0.98, deals `value` lightning damage (bypasses armour) to enemies within 6 blocks.
- `executing`: at attack strength ≥0.98, kills targets below value % max HP (damage type `apotheosis:execute`, bypasses armour, effects and enchantments).
- `festive`: on kill, with `chance`, the victim becomes a "Loot Piñata" and drops its loot `rolls` times (20) in a burst. Items tagged `apotheosis:cannot_be_duplicated` are excluded.
- `spectral`: `value` chance to fire a second (cloned) arrow.
- `magical`: arrows deal magic damage (tag `is_magic` + `bypasses_armor`).
- `psychic`: blocking a projectile reflects value % of the damage to the shooter (damage type `apotheosis:psychic`, magic, bypasses armour).
- `retreating`: blocking a melee hit leaps you backward (velocity constants 9.0 / 0.25).
- `catalyzing`: blocking an explosion grants Strength for `value` ticks.

| # | ID | Name *(suffix)* | Impl type | Affix type | Categories | Effect | Values per rarity (min–max/step) | Weight (quality) | Exclusive with |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `armor/attribute/adamantine` | Adamantine *of Adamantine* | attribute | stat | chestplate,leggings | generic.armor (add_multiplied_base) | R 0.15–0.3 · E 0.15–0.3 · M 0.25–0.4 | 25(q0.1) |  |
| 2 | `armor/attribute/aquatic` | Aquatic *of the Ocean* | attribute | stat | boots | swim_speed (add_multiplied_total) | C 0.2–0.3 · U 0.2–0.3 · R 0.3–0.5 · E 0.3–0.5 · M 0.4–0.7 | 25(q0.1) |  |
| 3 | `armor/attribute/blessed` | Blessed *of Health* | attribute | stat | helmet,chestplate,leggings,boots | generic.max_health (add_value) | C 2–4/0.25 · U 2–5/0.25 · R 3–6/0.25 · E 3–8/0.25 · M 5–8/0.25 | 25(q0.1) |  |
| 4 | `armor/attribute/elastic` | Elastic *of the Climb* | attribute | stat | boots | generic.step_height (add_value) | C 0.5 · U 0.5 · R 0.5–1.5/0.25 · E 0.5–1.5/0.25 · M 1–2/0.25 | 25(q0.1) |  |
| 5 | `armor/attribute/fireproof` | Fireproof *of Flaming Defense* | attribute | stat | helmet | generic.burning_time (add_multiplied_base) | U -0.15–-0.35/-0.05 · R -0.2–-0.4/-0.05 · E -0.35–-0.75/-0.05 · M -0.6–-1/-0.05 | 25(q0.1) |  |
| 6 | `armor/attribute/fortunate` | Fortunate *of the Four-Leaf Clover* | attribute | stat | helmet,chestplate,leggings,boots | generic.luck (add_value) | C 0.5–1.5/0.25 · U 0.5–1.5/0.25 · R 1.5–3/0.25 · E 1.5–3.5/0.25 · M 3–5/0.25 | 25(q0.1) |  |
| 7 | `armor/attribute/gravitational` | Gravitational *of Gravity* | attribute | stat | chestplate | generic.gravity (add_multiplied_total) | C -0.1–-0.2/-0.01 · U -0.1–-0.25/-0.01 · R -0.15–-0.3/-0.01 · E -0.15–-0.35/-0.01 · M -0.2–-0.5/-0.01 | 25(q0.1) |  |
| 8 | `armor/attribute/ironforged` | Ironforged *of Iron* | attribute | stat | helmet,chestplate,leggings,boots | generic.armor (add_value) | C 1–1.5/0.25 · U 1–1.5/0.25 · R 1.5–3/0.25 · E 2–5/0.25 · M 4–8/0.25 | 25(q0.1) |  |
| 9 | `armor/attribute/oxygenated` | Oxygenated *of the Diver* | attribute | stat | helmet | generic.oxygen_bonus (add_value) | U 0.2–0.3 · R 0.35–0.5 · E 0.55–0.7 · M 0.85–1.25 | 25(q0.1) |  |
| 10 | `armor/attribute/spiritual` | Spiritual *of the Witch Doctor* | attribute | stat | chestplate,leggings | healing_received (add_multiplied_base) | R 0.1–0.25 · E 0.15–0.3 · M 0.2–0.4 | 25(q0.1) |  |
| 11 | `armor/attribute/stalwart` | Stalwart *of Stubbornness* | attribute | stat | helmet,chestplate,leggings,boots | generic.knockback_resistance (add_value) | U 0.05–0.1 · R 0.05–0.1 · E 0.15–0.2 · M 0.25–0.35 | 25(q0.1) |  |
| 12 | `armor/attribute/steel_touched` | Steel Touched *of the Defender* | attribute | stat | helmet,chestplate,leggings,boots | generic.armor_toughness (add_value) | R 1 · E 1.5–3/0.25 · M 2–6/0.25 | 25(q0.1) |  |
| 13 | `armor/attribute/timekeeping` | Timekeeping *of Chronos* | attribute | stat | helmet,chestplate | cooldown_reduction (add_value) | R 0.01–0.05/0.005 · E 0.05–0.075/0.005 · M 0.05–0.1/0.005 | 25(q0.1) |  |
| 14 | `armor/attribute/unbound` | Unbound *of Icarus* | attribute | stat | chestplate | creative_flight (add_value) | M 1 | pin:1(q0.2) | winged |
| 15 | `armor/attribute/windswept` | Windswept *of the Current* | attribute | stat | leggings,boots | generic.movement_speed (add_multiplied_total) | C 0.1–0.2 · U 0.1–0.25 · R 0.15–0.3 · E 0.15–0.4 · M 0.2–0.45 | 25(q0.1) |  |
| 16 | `armor/attribute/winged` | Winged *of the Skies* | attribute | stat | chestplate | elytra_flight (add_value) | E 1 · M 1 | 25(q0.1) |  |
| 17 | `armor/dmg_reduction/blast_forged` | Blast-Forged *of the Forge God* | damage_reduction | basic_effect | chestplate,leggings | -explosion dmg % | C 0.05–0.1 · U 0.1–0.15 · R 0.15–0.2 · E 0.15–0.25 · M 0.15–0.35 | 25(q0.1) |  |
| 18 | `armor/dmg_reduction/blockading` | Blockading *of the Blockade* | damage_reduction | ability | chestplate,leggings | -physical dmg % | C 0.01–0.05 · U 0.01–0.05 · R 0.05–0.1 · E 0.05–0.1 · M 0.05–0.15 | 25(q0.1) |  |
| 19 | `armor/dmg_reduction/deflective` | Deflective *of Deflection* | damage_reduction | basic_effect | helmet,chestplate | -projectile dmg % | C 0.05–0.1 · U 0.1–0.15 · R 0.15–0.2 · E 0.15–0.2 · M 0.15–0.3 | 25(q0.1) |  |
| 20 | `armor/dmg_reduction/feathery` | Feathery *of the Eagle* | damage_reduction | basic_effect | boots | -fall dmg % | C 0.05–0.1 · U 0.1–0.15 · R 0.15–0.25 · E 0.15–0.25 · M 0.15–0.4 | 25(q0.1) |  |
| 21 | `armor/dmg_reduction/grounded` | Grounded *of the Lightning Rod* | damage_reduction | basic_effect | helmet,boots | -lightning dmg % | C 0.05–0.1 · U 0.1–0.15 · R 0.15–0.25 · E 0.15–0.25 · M 0.15–0.4 | 25(q0.1) |  |
| 22 | `armor/dmg_reduction/runed` | Runed *of the Spellwarden* | damage_reduction | ability | helmet,chestplate,leggings,boots | -magic dmg % | C 0.01–0.05 · U 0.01–0.05 · R 0.05–0.1 · E 0.05–0.1 · M 0.05–0.15 | 25(q0.1) | blockading |
| 23 | `armor/mob_effect/blinding` | Blinding *of Darkness* | mob_effect | basic_effect | helmet | blindness → hurt_attacker | U 40–80/20t amp0 cd300 · R 40–80/20t amp0 cd240 · E 40–80/20t amp0 cd240 · M 60–100/20t amp0 cd200 | 25(q0.1) |  |
| 24 | `armor/mob_effect/bolstering` | Bolstering *of Fortitude* | mob_effect | basic_effect | chestplate,leggings | resistance → hurt_self | U 40–100/20t amp0 cd240 · R 80–120/20t amp0 cd240 · E 80–140/20t amp0–1/0.2 cd240 · M 80–160/20t amp0–1/0.5 cd240 | 25(q0.1) |  |
| 25 | `armor/mob_effect/bursting` | Bursting *of Vitality* | mob_effect | basic_effect | chestplate,leggings | vitality → hurt_self | E 200t amp0 cd300 · M 200t amp0–1/0.25 cd300 | 25(q0.1) | revitalizing |
| 26 | `armor/mob_effect/nimble` | Nimble *of the Fox* | mob_effect | basic_effect | leggings,boots | speed → hurt_self | U 100–300/20t amp0 cd800 · R 200–400/20t amp0 cd800 · E 200–400/20t amp0–2/0.25 cd700 · M 200–400/20t amp0–2/0.5 cd600 | 25(q0.1) |  |
| 27 | `armor/mob_effect/revitalizing` | Revitalizing *of the Wellspring* | mob_effect | basic_effect | chestplate,leggings | instant_health → hurt_self | E 1t amp0 cd300 · M 1t amp0–1/0.25 cd240 | 25(q0.1) |  |
| 28 | `breaker/ability/enlightened` | Enlightened *of the Lightbringer* | enlightened | ability | any valid | This tool can place a light source at the cost of X durability. | R 12–8/-1 · E 10–5/-1 · M 5–0/-1 | 25(q0.1) |  |
| 29 | `breaker/ability/sandforming` | Sandforming *of the Sands* | stoneforming | ability | breaker | convert drops within #apotheosis:sandforming_candidates | — | 25(q0.1) | stoneforming |
| 30 | `breaker/ability/stoneforming` | Stoneforming *of the Stoneworker* | stoneforming | ability | breaker | convert drops within #apotheosis:stoneforming_candidates | — | 25(q0.1) | sandforming |
| 31 | `breaker/ability/supermassive` | Super-Massive *of the World-Breaker* | radial | ability | breaker | AoE mining (x×y) | M 7x7 | pin:20(q0.75) | radial |
| 32 | `breaker/attribute/destructive` | Destructive *of Quarrying* | attribute | stat | breaker,shears | mining_speed (add_multiplied_total) | C 0.15–0.3 · U 0.15–0.3 · R 0.25–0.5 · E 0.25–0.7 · M 0.55–0.85 | 25(q0.1) |  |
| 33 | `breaker/attribute/experienced` | Experienced *of the Scholar* | attribute | stat | breaker | experience_gained (add_multiplied_total) | C 0.25–0.4 · U 0.25–0.4 · R 0.35–0.5 · E 0.35–0.6 · M 0.55–0.65 | 25(q0.1) |  |
| 34 | `breaker/attribute/lengthy` | Lengthy *of Grasping* | attribute | stat | breaker | player.block_interaction_range (add_value) | C 0.5–1.5/0.25 · U 0.5–1.5/0.25 · R 1–2.5/0.25 · E 1–2.5/0.25 · M 1.5–4/0.25 | 25(q0.1) |  |
| 35 | `breaker/attribute/submerged` | Submerged *of the Seaborn Miner* | attribute | stat | breaker | player.submerged_mining_speed (add_value) | C 0.1–0.3 · U 0.2–0.3 · R 0.3–0.5 · E 0.4–0.55 · M 0.5–0.8 | 25(q0.1) |  |
| 36 | `breaker/effect/omnetic` | Omnetic *of the Singularity* | omnetic | basic_effect | breaker | harvest as tool set | R iron · E diamond · M netherite | 25(q5) |  |
| 37 | `breaker/effect/radial` | Radial *of the Earthbreaker* | radial | basic_effect | breaker,shears | AoE mining (x×y) | U 1x2/1x3/3x2 · R 1x3/3x2 · E 3x2/3x3/5x3 · M 3x3/5x3/5x5 | 25(q5) |  |
| 38 | `breaker/enchantment/prosperous` | Prosperous *of Great Fortune* | enchantment | basic_effect | breaker | +lvl fortune (existing) | U 1 · R 1–2/0.5 · E 1–3/0.5 · M 2–4/0.5 | 25(q0.1) |  |
| 39 | `breaker/mob_effect/spelunkers` | Spelunker's *of Cave Exploration* | mob_effect | basic_effect | breaker | speed → break_self | U 200–300/20t amp0 cd600 · R 300–400/20t amp0 cd600 · E 300–460/20t amp0–1/0.25 cd600 · M 340–500/20t amp0–2/0.25 cd600 | 25(q0.1) |  |
| 40 | `breaker/mob_effect/swift` | Swift *of Smashing* | mob_effect | basic_effect | breaker | haste → break_self | U 100–200/20t amp0 cd600 · R 200–300/20t amp0 cd600 · E 200–360/20t amp0–1/0.25 cd600 · M 240–400/20t amp0–2/0.25 cd600 | 25(q0.1) |  |
| 41 | `generic/attribute/lucky` | Lucky *of the Serendipitous* | attribute | stat | bow,breaker,helmet,chestplate,leggings,boots,shield,trident,melee_weapon,shears | generic.luck (add_value) | U 1–1.5/0.25 · R 2–3/0.25 · E 2.5–4/0.25 · M 3–6/0.25 | 25(q0.1) |  |
| 42 | `generic/telepathic` | Telepathic *of Space and Time* | telepathic | basic_effect | (rarities R,E,M) | Mob drops will teleport to you. | (level-independent) | 25(q0.1) |  |
| 43 | `melee/attribute/berserking` | Berserking *of the Berserker* | attribute | stat | melee_weapon | overheal (add_value) | C 0.05–0.1 · U 0.05–0.1 · R 0.1–0.2 · E 0.1–0.2 · M 0.15–0.25 | 25(q0.1) | vampiric |
| 44 | `melee/attribute/forceful` | Forceful *of the Bull* | attribute | stat | melee_weapon | generic.attack_knockback (add_value) | C 0.5–1/0.25 · U 0.5–1.5/0.25 · R 0.75–2.5/0.25 · E 1–2.5/0.25 · M 1.5–3/0.25 | 25(q0.1) |  |
| 45 | `melee/attribute/giant_slaying` | Giant Slaying *of Colossal Strikes* | attribute | stat | melee_weapon | current_hp_damage (add_value) | E 0.1–0.2 · M 0.1–0.25 | pin:15(q0.75) |  |
| 46 | `melee/attribute/glacial` | Glacial *of the Frostlands* | attribute | stat | melee_weapon,trident | cold_damage (add_value) | U 2–4/0.25 · R 2–5/0.25 · E 4–6/0.25 · M 4–10/0.25 | 25(q0.1) | infernal |
| 47 | `melee/attribute/graceful` | Graceful *of the Duelist* | attribute | stat | melee_weapon,trident | generic.attack_speed (add_multiplied_total) | C 0.15–0.25 · U 0.2–0.3 · R 0.2–0.35 · E 0.25–0.5 · M 0.4–0.85 | 25(q0.1) |  |
| 48 | `melee/attribute/infernal` | Infernal *of the Sun* | attribute | stat | melee_weapon,trident | fire_damage (add_value) | U 2–4/0.25 · R 2–5/0.25 · E 4–6/0.25 · M 4–10/0.25 | 25(q0.1) | glacial |
| 49 | `melee/attribute/intricate` | Intricate *of Critical Thinking* | attribute | stat | melee_weapon,trident,bow | crit_chance (add_value) | C 0.1–0.2 · U 0.1–0.2 · R 0.1–0.25 · E 0.15–0.35 · M 0.25–0.55 | 25(q0.1) |  |
| 50 | `melee/attribute/lacerating` | Lacerating *of Surgical Precision* | attribute | stat | melee_weapon,trident,bow | crit_damage (add_value) | C 0.1–0.2 · U 0.1–0.2 · R 0.15–0.25 · E 0.15–0.25 · M 0.25–0.4 | 25(q0.1) |  |
| 51 | `melee/attribute/lengthy` | Lengthy *of Extreme Length* | attribute | stat | melee_weapon,trident | player.entity_interaction_range (add_value) | C 0.5–1.5/0.25 · U 0.5–1.5/0.25 · R 0.75–2.5/0.25 · E 1–2.5/0.25 · M 1.5–3/0.25 | 25(q0.1) |  |
| 52 | `melee/attribute/murderous` | Murderous *of the Minotaur* | attribute | stat | melee_weapon,trident | generic.attack_damage (add_multiplied_base) | R 0.15–0.43 · E 0.18–0.48 · M 0.25–0.55 | 25(q0.1) |  |
| 53 | `melee/attribute/piercing` | Piercing *of the Sunderer* | attribute | stat | melee_weapon,trident,bow | armor_pierce (add_value) | C 2–4/0.25 · U 2–4/0.25 · R 4–8/0.25 · E 5–10/0.25 · M 5–12/0.25 | 25(q0.1) |  |
| 54 | `melee/attribute/vampiric` | Vampiric *of Bloodletting* | attribute | stat | melee_weapon,trident | life_steal (add_value) | C 0.05–0.08 · U 0.05–0.08 · R 0.05–0.1 · E 0.08–0.13 · M 0.1–0.15 | 25(q0.1) | berserking |
| 55 | `melee/attribute/violent` | Violent *of Slaying* | attribute | stat | melee_weapon,trident | generic.attack_damage (add_value) | C 2–3/0.25 · U 2–3/0.25 · R 3–5/0.25 · E 4–6/0.25 · M 5–8/0.25 | 25(q0.1) |  |
| 56 | `melee/cleaving` | Cleaving *of the Butcher* | cleaving | ability | any valid | chance / targets | E 0.3–0.5/0.05 ×2–5/1 · M 0.4–0.6/0.05 ×3–6/1 | 25(q0.1) |  |
| 57 | `melee/executing` | Executing *of the Executioner* | executing | ability | melee_weapon,trident | Enemies below X% max health will be executed. | E 0.1–0.2 · M 0.15–0.25 | 25(q0.1) |  |
| 58 | `melee/festive` | Festive *of Partying* | festive | basic_effect | melee_weapon,trident | piñata chance (rolls) | E 0.02–0.05/0.005 ×20 · M 0.03–0.06/0.005 ×20 | 25(q0.1) |  |
| 59 | `melee/mob_effect/bloodletting` | Bloodletting *of the Slayer* | mob_effect | basic_effect | melee_weapon,trident | bleeding → attack_target; stacks≤3 | U 100t amp0 cd80 · R 100–120/20t amp0 cd80 · E 100–120/20t amp0 cd80 · M 100–120/20t amp0–1/0.125 cd80 | 25(q0.1) |  |
| 60 | `melee/mob_effect/caustic` | Caustic *of the Caustic Realm* | mob_effect | basic_effect | melee_weapon,trident | sundering → attack_target; stacks≤3 | R 100–180/20t amp0 cd60 · E 100–200/20t amp0–1/0.125 cd60 · M 200–400/20t amp0–1/0.25 cd60 | 25(q0.1) |  |
| 61 | `melee/mob_effect/elusive` | Elusive *of Evasion* | mob_effect | basic_effect | melee_weapon,trident | speed → attack_self; stacks≤3 | U 200–400/20t amp0 cd300 · R 200–600/20t amp0 cd300 · E 200–600/20t amp0–1/0.25 cd300 · M 200–800/20t amp0–2/0.25 cd300 | 25(q0.1) |  |
| 62 | `melee/mob_effect/omniscient` | Omniscient *of the Grand Sage* | mob_effect | basic_effect | melee_weapon,trident | knowledge → attack_self; stacks≤8 | M 100–160/20t amp0–1/0.125 cd80 | pin:20(q0.75) | sophisticated |
| 63 | `melee/mob_effect/sophisticated` | Sophisticated *of the Ancient Library* | mob_effect | basic_effect | melee_weapon,trident | knowledge → attack_self; stacks≤3 | U 400–600/20t amp0 cd400 · R 400–800/20t amp0 cd400 · E 400–800/20t amp0–1/0.25 cd400 · M 400–1000/20t amp0–2/0.25 cd400 | 25(q0.1) |  |
| 64 | `melee/mob_effect/weakening` | Weakening *of Weakness* | mob_effect | basic_effect | melee_weapon,trident | weakness → attack_target | U 80–140/20t amp0 cd300 · R 80–160/20t amp0 cd300 · E 80–180/20t amp0–1/0.25 cd300 · M 80–200/20t amp0–2/0.125 cd300 | 25(q0.1) |  |
| 65 | `melee/thunderstruck` | Thunderstruck *of the Storm* | thunderstruck | ability | melee_weapon,trident | Your attacks deal X damage to nearby enemies. | E 3–6/1 · M 4–8/1 | 25(q0.1) |  |
| 66 | `ranged/attribute/agile` | Agile *of Dexterity* | attribute | stat | bow | draw_speed (add_multiplied_total) | C 0.2–0.4 · U 0.2–0.4 · R 0.3–0.5 · E 0.5–0.6 · M 0.5–0.65 | 25(q0.1) |  |
| 67 | `ranged/attribute/elven` | Elven *of the First Archer* | attribute | stat | bow,trident | arrow_damage (add_multiplied_total) | C 0.15–0.2 · U 0.15–0.25 · R 0.2–0.3 · E 0.25–0.35 · M 0.25–0.4 | 25(q0.1) |  |
| 68 | `ranged/attribute/streamlined` | Streamlined *of the Sniper* | attribute | stat | bow,trident | arrow_velocity (add_multiplied_total) | C 0.15–0.2 · U 0.15–0.2 · R 0.15–0.25 · E 0.15–0.3 · M 0.15–0.35 | 25(q0.1) |  |
| 69 | `ranged/attribute/windswept` | Windswept *of the Current* | attribute | stat | bow,trident | generic.movement_speed (add_multiplied_total) | C 0.15–0.25 · U 0.15–0.3 · R 0.15–0.3 · E 0.15–0.35 · M 0.2–0.4 | 25(q0.1) |  |
| 70 | `ranged/enchantment/prosperous` | Prosperous *of the Treasure-Seeker* | enchantment | ability | bow | +lvl looting | E 6–8/0.25 · M 8–10/0.25 | 25(q0.1) |  |
| 71 | `ranged/magical` | Magical *of the Arcane* | magical | ability | (rarities E,M) | Your arrows deal magic damage. | (level-independent) | 25(q0.1) |  |
| 72 | `ranged/mob_effect/acidic` | Acidic *of Corrosion* | mob_effect | basic_effect | bow | sundering → arrow_target; stacks≤4 | M 80–160/20t amp0 cd40 | pin:20(q0.75) |  |
| 73 | `ranged/mob_effect/blighted` | Blighted *of the Blighted Lands* | mob_effect | basic_effect | bow,trident | wither → arrow_target | E 160–200/20t amp0–1/0.25 cd300 · M 160–200/20t amp0–3/0.25 cd300 | 25(q0.1) |  |
| 74 | `ranged/mob_effect/deathbound` | Deathbound *of the Witherlord* | mob_effect | basic_effect | bow,trident | wither → arrow_target; stacks≤4 | M 100–200/20t amp1 cd40 | pin:20(q0.75) | blighted |
| 75 | `ranged/mob_effect/ensnaring` | Ensnaring *of the Predator* | mob_effect | basic_effect | bow,trident | slowness → arrow_target | U 40–80/20t amp0 cd160 · R 40–100/20t amp0 cd160 · E 40–120/20t amp0–1/0.25 cd160 · M 80–160/20t amp0–2/0.25 cd160 | 25(q0.1) |  |
| 76 | `ranged/mob_effect/fleeting` | Fleeting *of the Ranger* | mob_effect | basic_effect | bow,trident | speed → arrow_self | U 100–200/20t amp0 · R 100–200/20t amp0 · E 100–200/20t amp0–1/0.25 · M 100–300/20t amp0–2/0.25 | 25(q0.1) |  |
| 77 | `ranged/mob_effect/grievous` | Grievous *of Wounding* | mob_effect | basic_effect | bow,trident | grievous → arrow_target | U 200t amp0 cd500 · R 200–300/20t amp0 cd500 · E 200–300/20t amp0–1/0.25 cd400 · M 200–300/20t amp0–2/0.25 cd400 | 25(q0.1) |  |
| 78 | `ranged/mob_effect/ivy_laced` | Ivy Laced *of Natural Toxins* | mob_effect | basic_effect | bow,trident | poison → arrow_target; stacks≤5 | R 100–160/20t amp0 cd40 · E 100–160/20t amp0–1/0.25 cd40 · M 100–200/20t amp0–2/0.25 cd40 | 25(q0.1) |  |
| 79 | `ranged/mob_effect/shulkers` | Shulk-Touched *of Levitation* | mob_effect | basic_effect | bow | levitation → arrow_target | E 20–80/20t amp0–1/0.25 cd140 · M 20–100/20t amp0–2/0.25 cd140 | 25(q0.1) |  |
| 80 | `ranged/spectral` | Spectral *of the Forlorn* | spectral | ability | any valid | You have a X% chance to shoot a second arrow. | E 0.2–0.5 · M 0.3–0.7 | 25(q0.1) |  |
| 81 | `shears/effect/gardening` | Gardening *of the Gardener* | stoneforming | basic_effect | shears | convert drops within #apotheosis:gardening_candidates | — | 25(q0.1) | leafforming |
| 82 | `shears/effect/leafforming` | Leaf-Forming *of the Arborist* | stoneforming | basic_effect | shears | convert drops within #apotheosis:leafforming_candidates | — | 25(q0.1) | gardening |
| 83 | `shield/attribute/ironforged` | Ironforged *of Iron* | attribute | stat | shield | generic.armor (add_multiplied_base) | C 0.1–0.15 · U 0.1–0.15 · R 0.15–0.2 · E 0.15–0.25 · M 0.2–0.3 | 25(q0.1) |  |
| 84 | `shield/attribute/stalwart` | Stalwart *of Stubbornness* | attribute | stat | shield | generic.knockback_resistance (add_value) | C 0.1–0.2 · U 0.1–0.2 · R 0.2–0.3 · E 0.2–0.3 · M 0.25–0.35 | 25(q0.1) |  |
| 85 | `shield/attribute/steel_touched` | Steel Touched *of the Defender* | attribute | stat | shield | generic.armor_toughness (add_multiplied_base) | R 0.15–0.2 · E 0.15–0.2 · M 0.2–0.3 | 25(q0.1) |  |
| 86 | `shield/catalyzing` | Catalyzing *of the Converted* | catalyzing | ability | any valid | Blocking an explosion will grant you great strength. | E 200–400/20 · M 300–600/20 | 25(q0.1) |  |
| 87 | `shield/mob_effect/devilish` | Devilish *of the Veteran* | mob_effect | basic_effect | shield | bleeding → block_attacker; stacks≤4 | U 100t amp0 cd40 · R 100–120/20t amp0 cd40 · E 100–140/20t amp0–1/0.125 cd40 · M 100–140/20t amp0–1/0.25 cd40 | 25(q0.1) |  |
| 88 | `shield/mob_effect/galvanizing` | Galvanizing *of the Indestructible* | mob_effect | basic_effect | shield | resistance → block_self; stacks≤3 | M 100–160/20t amp0–1/0.125 cd80 | pin:20(q0.75) | reinforcing |
| 89 | `shield/mob_effect/reinforcing` | Reinforcing *of Steel* | mob_effect | basic_effect | shield | resistance → block_self | R 120–180/20t amp0 cd200 · E 120–200/20t amp0–1/0.25 cd200 · M 160–240/20t amp0–1/0.5 cd200 | 25(q0.1) |  |
| 90 | `shield/mob_effect/venomous` | Venomous *of the Snake* | mob_effect | basic_effect | shield | poison → block_attacker; stacks≤4 | U 120t amp0 cd200 · R 120–180/20t amp0 cd200 · E 120–200/20t amp0–1/0.25 cd120 · M 160–240/20t amp0–1/0.5 cd120 | 25(q0.1) |  |
| 91 | `shield/mob_effect/withering` | Withering *of the Dark Skeleton* | mob_effect | basic_effect | shield | wither → block_attacker | E 40–100/20t amp0–1/0.5 · M 60–160/20t amp0–2/0.25 | 25(q0.1) |  |
| 92 | `shield/psychic` | Psychic *of the Vengeful* | psychic | ability | any valid | Blocking a projectile deals X% of the damage to the shooter. | E 0.5–0.9 · M 0.6–1.2 | 25(q0.1) |  |
| 93 | `shield/retreating` | Retreating *of the Jester* | retreating | ability | (rarities E,M) | You will leap back when blocking in melee. | (level-independent) | 25(q0.1) |  |
| 94 | `weapon/attribute/shredding` | Shredding *of the Maelstrom* | attribute | stat | melee_weapon,trident,bow | armor_shred (add_value) | C 0.1–0.2 · U 0.1–0.2 · R 0.15–0.25 · E 0.2–0.28 · M 0.3–0.4 | 25(q0.1) |  |

Notes:
- Pinnacle-only affixes are *Unbound* (creative flight), *Super-Massive* (7×7), *Omniscient*, *Acidic*, *Deathbound* and *Galvanizing*.
- *Unbound* is exclusive with *Winged*.
- Lang contains names for affixes that have **no data in 8.8.0**: Satanic, Annihilating, Decimating, Nullifying, Spellbreaking, Dwarven, Detonating. These are legacy; do not implement unless wanted.
- **Depends on:** AP-6, AA-1…AA-9 · **Size:** XL in total (42 stat affixes are S together; each special affix is S–M) · **Elysium:** PARTIAL (a few overlaps: `ElysiumAreaBreak` = 3×3 + tree fell ≈ radial; runes give flat attributes ≈ stat affixes; dodge/reflect/lifesteal exist as stats).

### AP-8: Affix item generation and loot injection
- **What it is:** How affix items appear in the world.
- **How it works:**
  1. **Affix loot entries** (`AP:affix_loot_entries/*.json`, type `apotheosis:affix_loot_entry {stack, weights}`) = the base item pool. 50 vanilla entries + 46 Twilight Forest entries (conditioned on the mod). Vanilla weights per tier:
     - Leather armour 25 and stone tools 25: Haven only.
     - Chainmail 10: Haven, Frontier(q1).
     - Golden 5 / 10 / 5: H / F / A.
     - Iron 25(q1) / 10 / 10: F / A / S.
     - Diamond 25 / 25 / 5: A / S / P.
     - Netherite 5(q1) / 25(q2): S / P.
     - Bow 15(q1) all tiers; crossbow 10(q1); shield 10(q1); shears 5(q1); turtle helmet 2; trident 5 / 7 / 7(q1) A / S / P.
  2. **Rarity** = weighted pick (AP-2, AP-5) for the tier and luck, then `LootController.createLootItem` runs the rarity's rule list for the item's category **[web]**. Name built as in AP-6.
  3. **Global loot modifiers** (`AP:loot_modifiers/`, registered in `data/neoforge/loot_modifiers/global_loot_modifiers.json`):
     - `affix_loot_injection`: adds 1 random affix item to chest loot. Chance 0.35 for `minecraft:chests/*`, 0.30 for any `chests/*`, 0.30 for `twilightforest:structures/*`. *The first matching pattern wins* **[inferred from ordering]**.
     - `affix_conversion`: each already-generated gear item in any non-block loot table has a **35%** chance to be converted into an affix item (0% for `*blocks*`).
     - `affix_hook`: code hook (loot function `ReforgeItemFunction` / contextual entries, `apotheosis:random_affix_item` pool entry).
  4. **Mob augment** `AP:apothic_augments/random_affix_items.json`: **12%** of naturally spawned monsters (not spawner/trial spawner) get a random affix item equipped (which can drop).
  5. Invaders (AP-20) always carry and drop one, and elites have an `affix_chance` (AP-21).
- **Player interaction:** none (world loot). Commands `/apoth affix|rarity|reforge|category_check|debug_weight`.
- **Depends on:** AP-2, AP-4, AP-5, AP-6 · **Size:** L · **Elysium:** PARTIAL (`ElysiumLootHandler` rewards by Favor/Suspicion; no affix-item injection).

### AP-9: Gems and purities
- **What it is:** Socketable gem items (one item `apotheosis:gem`, with the gem id and purity in components). Each gem has different bonuses depending on the **gem class** of the item it is socketed into. Six purities: **Cracked, Chipped, Flawed, Normal, Flawless, Perfect**. Purity alone sets strength; Perfect cannot be upgraded (BOOK `gems/gems`).
- **JSON (`AP:gems/**`)**: `{type: apotheosis:gem, weights (tiered), constraints{dimensions}, min_purity, unique, bonuses:[{type, gem_class, values{purity:…}, …}]}`.
  - `gem_class` is either a single category or `{key, types[]}`. Keys: `light_weapon` = melee_weapon+trident, `ranged_weapon` = bow+trident, `weapons`, `core_armor` = chest+legs, `lower_armor` = legs+boots, `armor` = all 4, `tools` = breaker+shears, `weapon_or_tool`, `anything` (`neoforge:any`).
  - `unique: true` means only one copy of that gem per item.
- **Bonus types:** `attribute`, `multi_attribute` (with desc `and`/`and_but`/`but_and` for mixed pos/neg), `durability`, `damage_reduction`, `enchantment` (`existing`/`global`), `mob_effect` (same targets, cooldowns and stacking as affixes), and specials: `all_stats`, `drop_transform`, `radial`, `omnetic`, `mageslayer`, `bloody_arrow`, `leech_block`, `frozen_drops`, `twilight_treasure_goblin`, `twilight_ore_magnet`, `twilight_fortification`.
- **Special bonus semantics (lang):**
  - `bloody_arrow`: "When firing, you lose X% Max Health and deal X% damage" (bow).
  - `leech_block`: "Blocking an attack heals you for X% of the damage" (shield).
  - `mageslayer`: "Ignore X% of magic damage and heal for the same amount".
  - `frozen_drops`: "Killing an enemy with [frozen] increases loot drops by X%".
  - `drop_transform`: "Breaking Copper Ore has an X% chance to drop Raw Gold instead".
  - `all_stats`: "+X% to All Stats" (31 listed attributes, mult_total).
  - Twilight bonuses: summon a Treasure Goblin (loot table `AP:loot_table/entity/treasure_goblin.json`), Ore-Magnet use costing X durability, Twilight Shields fortification.
- **Content (23 gems)**. Purity abbreviations: CR/CH/FW/NO/FS/PF. `t/a/cd` = duration ticks / amplifier / cooldown.

| Gem | ID | Unique | Min purity | Dimension | Tier weights | Socket class → bonus | Values by purity |
|---|---|---|---|---|---|---|---|
| **Ballast Gem** | `core/ballast` | no | cracked | any | all:100(q0.1) | light_weapon: generic.attack_damage (add_value) | CR 1 / CH 2 / FW 3.5 / NO 5 / FS 7 / PF 10 |
| | | | | | | tools: durability: ignore % of damage | CR 0.1 / CH 0.15 / FW 0.25 / NO 0.35 / FS 0.45 / PF 0.6 |
| | | | | | | core_armor: generic.knockback_resistance (add_value) | CR 0.1 / CH 0.2 / FW 0.3 / NO 0.4 / FS 0.5 / PF 0.7 |
| **Brawler's Gem** | `core/brawlers` | no | cracked | any | all:100(q0.1) | light_weapon: generic.attack_speed (add_multiplied_base) | CR 0.1 / CH 0.15 / FW 0.2 / NO 0.3 / FS 0.35 / PF 0.5 |
| | | | | | | core_armor: generic.max_health (add_value) | CR 1 / CH 2 / FW 4 / NO 6 / FS 8 / PF 12 |
| | | | | | | shield: generic.max_health (add_multiplied_total) | CR 0.05 / CH 0.1 / FW 0.15 / NO 0.2 / FS 0.25 / PF 0.3 |
| **Breach Gem** | `core/breach` | yes | cracked | any | all:100(q0.1) | light_weapon: armor_pierce (add_value) | CR 2 / CH 3 / FW 5 / NO 7 / FS 9 / PF 12 |
| | | | | | | tools: player.block_interaction_range (add_value) | CR 0.5 / CH 1 / FW 1.5 / NO 2 / FS 2.5 / PF 3 |
| | | | | | | bow: prot_pierce (add_value) | CR 4 / CH 5 / FW 7 / NO 8 / FS 10 / PF 15 |
| **Gem of the Combatant** | `core/combatant` | yes | cracked | any | all:100(q0.1) | ranged_weapon: arrow_damage (add_multiplied_base) | CR 0.05 / CH 0.15 / FW 0.2 / NO 0.3 / FS 0.4 / PF 0.55 |
| | | | | | | core_armor: -physical dmg % | CR 0.05 / CH 0.075 / FW 0.125 / NO 0.175 / FS 0.225 / PF 0.275 |
| | | | | | | melee_weapon: durability: ignore % of damage | CR 0.05 / CH 0.1 / FW 0.15 / NO 0.225 / FS 0.3 / PF 0.4 |
| **Guardian Gem** | `core/guardian` | no | cracked | any | all:100(q0.1) | core_armor: generic.armor (add_value) | CR 0.5 / CH 1 / FW 2.5 / NO 4 / FS 6 / PF 8 |
| | | | | | | light_weapon: life_steal (add_value) | CR 0.05 / CH 0.1 / FW 0.15 / NO 0.2 / FS 0.25 / PF 0.3 |
| | | | | | | shield: generic.armor (add_multiplied_total) | CR 0.05 / CH 0.1 / FW 0.2 / NO 0.25 / FS 0.3 / PF 0.45 |
| **Lightning Gem** | `core/lightning` | no | cracked | any | all:100(q0.1) | bow: arrow_velocity (add_multiplied_base) | CR 0.05 / CH 0.15 / FW 0.25 / NO 0.35 / FS 0.425 / PF 0.55 |
| | | | | | | tools: mining_speed (add_value) | CR 0.05 / CH 0.15 / FW 0.25 / NO 0.35 / FS 0.425 / PF 0.5 |
| | | | | | | lower_armor: generic.movement_speed (add_multiplied_total) | CR 0.1 / CH 0.225 / FW 0.375 / NO 0.45 / FS 0.55 / PF 0.7 |
| **Lunar Gem** | `core/lunar` | no | cracked | any | all:100(q0.1) | light_weapon: cold_damage (add_value) | CR 1 / CH 1.5 / FW 2.5 / NO 4 / FS 6 / PF 8 |
| | | | | | | core_armor: generic.gravity (add_multiplied_total) | CR -0.1 / CH -0.25 / FW -0.45 / NO -0.65 / FS -0.85 / PF -1.04 |
| | | | | | | boots: swim_speed (add_multiplied_base) | CR 0.1 / CH 0.2 / FW 0.3 / NO 0.45 / FS 0.6 / PF 0.8 |
| **Gem of the Samurai** | `core/samurai` | yes | cracked | any | all:100(q0.1) | weapons: crit_chance (add_value) | CR 0.05 / CH 0.15 / FW 0.25 / NO 0.35 / FS 0.4 / PF 0.5 |
| | | | | | | lower_armor: generic.armor_toughness (add_multiplied_total) [CR 0.1 / CH 0.15 / FW 0.175 / NO 0.225 / FS 0.275 / PF 0.35] AND generic.movement_speed (add_multiplied_total) [CR -0.025 / CH -0.05 / FW -0.075 / NO -0.1 / FS -0.125 / PF -0.15] |  |
| | | | | | | helmet: arrow_velocity (add_multiplied_total) | CR 0.075 / CH 0.15 / FW 0.25 / NO 0.375 / FS 0.425 / PF 0.5 |
| | | | | | | shield: dodge_chance (add_value) | CR 0.01 / CH 0.02 / FW 0.04 / NO 0.06 / FS 0.08 / PF 0.1 |
| **Slipstream Gem** | `core/slipstream` | yes | cracked | any | all:100(q0.1) | bow: draw_speed (add_multiplied_base) | CR 0.1 / CH 0.25 / FW 0.35 / NO 0.45 / FS 0.5 / PF 0.6 |
| | | | | | | tools: mining_speed (add_multiplied_base) | CR 0.1 / CH 0.15 / FW 0.225 / NO 0.3 / FS 0.375 / PF 0.45 |
| | | | | | | boots: dodge_chance (add_value) | CR 0.025 / CH 0.05 / FW 0.075 / NO 0.1 / FS 0.125 / PF 0.15 |
| **Solar Gem** | `core/solar` | no | cracked | any | all:100(q0.1) | light_weapon: fire_damage (add_value) | CR 1 / CH 1.5 / FW 2.5 / NO 4 / FS 6 / PF 8 |
| | | | | | | core_armor: generic.gravity (add_multiplied_total) | CR 0.1 / CH 0.25 / FW 0.45 / NO 0.65 / FS 0.85 / PF 1.04 |
| | | | | | | boots: generic.step_height (add_value) | CR 0.25 / CH 0.5 / FW 1 / NO 1.5 / FS 2 / PF 3 |
| **Splendor Gem** | `core/splendor` | no | cracked | any | all:100(q0.1) | armor: generic.luck (add_value) | CR 0.5 / CH 1.25 / FW 2 / NO 2.25 / FS 3.5 / PF 5 |
| | | | | | | weapon_or_tool: experience_gained (add_multiplied_base) | CR 0.075 / CH 0.15 / FW 0.225 / NO 0.3 / FS 0.4 / PF 0.6 |
| **Tyrannical Gem** | `core/tyrannical` | yes | cracked | any | all:100(q0.1) | light_weapon: generic.attack_knockback (add_value) | CR 0.5 / CH 1 / FW 1.5 / NO 2 / FS 3 / PF 3.5 |
| | | | | | | core_armor: generic.armor_toughness (add_value) | CR 0.5 / CH 1 / FW 2 / NO 3 / FS 4 / PF 6 |
| | | | | | | shield: generic.armor_toughness (add_multiplied_base) | CR 0.05 / CH 0.15 / FW 0.225 / NO 0.3 / FS 0.375 / PF 0.5 |
| | | | | | | boots: generic.max_health (add_multiplied_total) [CR 0.05 / CH 0.075 / FW 0.125 / NO 0.175 / FS 0.225 / PF 0.4] AND life_steal (add_value) [CR -0.025 / CH -0.05 / FW -0.075 / NO -0.1 / FS -0.125 / PF -0.2] |  |
| | | | | | | bow: bleeding → arrow_target stacks≤4 | FS 160t/a0/cd40 / PF 160t/a1/cd40 |
| **Gem of the Warlord** | `core/warlord` | no | cracked | any | all:100(q0.1) | weapons: crit_damage (add_multiplied_base) | CR 0.1 / CH 0.2 / FW 0.35 / NO 0.45 / FS 0.55 / PF 0.7 |
| | | | | | | chestplate: generic.max_health (add_multiplied_base) | CR 0.05 / CH 0.1 / FW 0.15 / NO 0.2 / FS 0.25 / PF 0.35 |
| | | | | | | helmet: generic.attack_damage (add_multiplied_total) | CR 0.05 / CH 0.1 / FW 0.125 / NO 0.15 / FS 0.175 / PF 0.225 |
| | | | | | | trident: strength → arrow_self stacks≤5 | FS 200t/a0/cd40 / PF 200t/a1/cd40 |
| **Gem of the Earth** | `overworld/earth` | yes | flawed | overworld | fro:50(q2) asc:50(q2) sum:50(q2) pin:50(q2) | light_weapon: +sharpness levels (existing) | FW 1 / NO 2 / FS 3 / PF 4 |
| | | | | | | core_armor: +protection levels (existing) | FW 1 / NO 2 / FS 3 / PF 4 |
| | | | | | | tools: +fortune levels (existing) | FW 1 / NO 2 / FS 3 / PF 4 |
| **Gem of the Royal Family** | `overworld/royalty` | yes | flawed | overworld | fro:50(q2) asc:50(q2) sum:50(q2) pin:50(q2) | helmet: +% to all 31 listed attributes (add_multiplied_total) | FW 0.05 / NO 0.075 / FS 0.1 / PF 0.15 |
| | | | | | | breaker: copper ore raw copper → raw gold chance | FW 0.15 / NO 0.2 / FS 0.25 / PF 0.4 |
| | | | | | | bow: prot_shred (add_value) [FW 0.25 / NO 0.3 / FS 0.35 / PF 0.4] AND draw_speed (add_multiplied_total) [FW -0.35 / NO -0.45 / FS -0.55 / PF -0.65] |  |
| | | | | | | shield: generic.armor (add_multiplied_base) [FW 0.15 / NO 0.25 / FS 0.35 / PF 0.5] AND generic.armor_toughness (add_multiplied_base) [FW 0.075 / NO 0.125 / FS 0.225 / PF 0.3] AND generic.movement_speed (add_multiplied_total) [FW -0.25 / NO -0.3 / FS -0.35 / PF -0.4] |  |
| **Gem of the Verdant Ruin** | `overworld/verdant_ruin` | yes | flawed | overworld | fro:50(q2) asc:50(q2) sum:50(q2) pin:50(q2) | weapons: armor_shred (add_value) [FW 0.075 / NO 0.15 / FS 0.25 / PF 0.4] AND generic.armor (add_multiplied_total) [FW -0.025 / NO -0.05 / FS -0.075 / PF -0.1] |  |
| | | | | | | chestplate: -magic dmg % | FW 0.125 / NO 0.15 / FS 0.175 / PF 0.2 |
| | | | | | | boots: generic.knockback_resistance (add_multiplied_total) [FW 0.05 / NO 0.1 / FS 0.15 / PF 0.2] AND generic.attack_knockback (add_multiplied_total) [FW -0.075 / NO -0.1 / FS -0.125 / PF -0.15] |  |
| | | | | | | breaker: radial | FW 3x2 / NO 3x3 / FS 5x3 / PF 5x5 |
| **Endersurge Gem** | `the_end/endersurge` | yes | flawless | the_end | sum:50(q2) pin:50(q2) | anything: +sharpness levels (global) | FS 1 / PF 2 |
| **Gem of the Mageslayer** | `the_end/mageslayer` | yes | normal | the_end | sum:50(q2) pin:50(q2) | light_weapon: prot_shred (add_value) [NO 0.3 / FS 0.55 / PF 0.75] AND armor_shred (add_multiplied_base) [NO -0.2 / FS -0.35 / PF -0.55] |  |
| | | | | | | helmet: mageslayer — Ignore X% of magic damage and heal for the same amount. | NO 0.15 / FS 0.225 / PF 0.35 |
| | | | | | | shield: resistance → block_self | NO 200t/a0/cd400 / FS 300t/a0/cd400 / PF 300t/a1/cd400 |
| **Gem of the Ravenous Blood Lord** | `the_nether/blood_lord` | yes | flawed | the_nether | asc:50(q2) sum:50(q2) pin:50(q2) | light_weapon: life_steal (add_value) [FW 0.1 / NO 0.125 / FS 0.15 / PF 0.15] AND life_steal (add_multiplied_total) [FW 0.05 / NO 0.1 / FS 0.2 / PF 0.3] |  |
| | | | | | | helmet: generic.attack_damage (add_multiplied_total) [FW 0.25 / NO 0.35 / FS 0.45 / PF 0.6] AND generic.max_health (add_multiplied_total) [FW -0.3 / NO -0.4 / FS -0.5 / PF -0.65] |  |
| | | | | | | chestplate: healing_received (add_multiplied_base) | FW 0.2 / NO 0.3 / FS 0.4 / PF 0.5 |
| | | | | | | bow (implicit): bloody_arrow — When firing, you lose X% Max Health and deal X% damage. X | FW cooldown=450,damage_mult=1.6,health_cost=0.2 / NO cooldown=450,damage_mult=1.9,health_cost=0.3 / FS cooldown=450,damage_mult=2.2,health_cost=0.4 / PF cooldown=450,damage_mult=2.5,health_cost=0.5 |
| | | | | | | shield (implicit): leech_block — Blocking an attack heals you for X% of the damage. X | FW cooldown=450,heal_factor=0.25 / NO cooldown=450,heal_factor=0.4 / FS cooldown=450,heal_factor=0.55 / PF cooldown=450,heal_factor=0.65 |
| **Gem of the Incandescent Inferno** | `the_nether/inferno` | yes | flawed | the_nether | asc:50(q2) sum:50(q2) pin:50(q2) | light_weapon: detonation → attack_target | FS 80t/a0/cd1200 / PF 80t/a1/cd1200 |
| | | | | | | chestplate: +berserkers_fury levels (single) | FW 1 / NO 1 / FS 2 / PF 2 |
| | | | | | | breaker: +efficiency levels (single) | FW 1 / NO 2 / FS 3 / PF 4 |
| | | | | | | helmet: fire_damage (add_multiplied_total) | FW 0.5 / NO 0.625 / FS 0.75 / PF 0.9 |
| **Gem of the Molten Breach** | `the_nether/molten_breach` | yes | flawed | the_nether | asc:50(q2) sum:50(q2) pin:50(q2) | light_weapon: prot_pierce (add_value) | FW 5.5 / NO 8 / FS 9.5 / PF 12 |
| | | | | | | bow: armor_pierce (add_value) | FW 4 / NO 5.5 / FS 8 / PF 10 |
| | | | | | | shield: player.entity_interaction_range (add_value) | FW 0.5 / NO 0.75 / FS 1.25 / PF 1.75 |
| | | | | | | chestplate: generic.attack_damage (add_multiplied_base) [FW 0.05 / NO 0.1 / FS 0.125 / PF 0.175] AND healing_received (add_multiplied_total) [FW -0.05 / NO -0.15 / FS -0.25 / PF -0.35] |  |
| | | | | | | leggings: generic.scale (add_value) | FW 0.075 / NO 0.125 / FS 0.15 / PF 0.25 |
| | | | | | | tools: omnetic | FW diamond / NO diamond / FS netherite / PF netherite |
| **Gem of the Twilight Forest** | `twilight/forest` | yes | flawed | twilight_forest | sum:50(q2) pin:50(q2) | weapons: twilight_treasure_goblin — Attacking an entity has a X% chance to summon a Treasure Goblin. X | FW chance=0.005,cooldown=4800 / NO chance=0.0075,cooldown=4800 / FS chance=0.01,cooldown=4800 / PF chance=0.015,cooldown=4800 |
| | | | | | | shield: healing_received (add_multiplied_base) [FW 0.1 / NO 0.2 / FS 0.3 / PF 0.45] AND generic.attack_speed (add_multiplied_base) [FW -0.05 / NO -0.1 / FS -0.15 / PF -0.25] |  |
| | | | | | | breaker: twilight_ore_magnet — Can be used as an Ore Magnet, costing an extra X durability. | FW 24 / NO 20 / FS 16 / PF 10 |
| | | | | | | chestplate: armor_shred (add_multiplied_base) [FW 0.35 / NO 0.5 / FS 0.75 / PF 1] AND armor_pierce (add_multiplied_base) [FW -0.15 / NO -0.225 / FS -0.325 / PF -0.4] AND prot_pierce (add_multiplied_base) [FW -0.05 / NO -0.125 / FS -0.225 / PF -0.2] |  |
| **Gem of the Frozen Queen** | `twilight/queen` | yes | flawed | twilight_forest | sum:50(q2) pin:50(q2) | chestplate: twilight_fortification — Taking damage has a X% chance to fortify you with Twilight Shields. X | FW chance=0.05,cooldown=6000 / NO chance=0.1,cooldown=5400 / FS chance=0.125,cooldown=5100 / PF chance=0.15,cooldown=4800 |
| | | | | | | helmet: cold_damage (add_multiplied_total) | FW 0.5 / NO 0.625 / FS 0.75 / PF 0.9 |
| | | | | | | melee_weapon: frozen_drops — Killing an enemy with X increases loot drops by X%. | NO 0.666 / FS 1.35 / PF 2.25 |
| | | | | | | ranged_weapon: frosted → arrow_target | FS 180t/a1/cd500 / PF 240t/a2/cd500 |

- **Depends on:** AP-2, AP-4, AP-6 (hooks), AA-1 · **Size:** L (framework M plus about 12 special bonuses) · **Elysium:** PARTIAL. Elysium **runes** (`ElysiumRune`) are its gems: affix + tick effect, element alignment ×`ALIGNED_MULTIPLIER`, and linear tiers (tier N = N× base; merging 2 same-tier runes makes the next tier, `ElysiumRuneTiers`). What is missing: gem-class-dependent bonuses, uniqueness, purity names, and dimension-gated drops.

### AP-10: Gem drops and purity weights
- **Purity weights by tier** (`AP:purity_weights/weights.json`, `weight(quality)`):

| Tier | Cracked | Chipped | Flawed | Normal | Flawless | Perfect |
|---|---|---|---|---|---|---|
| Haven | 550 | 410 (q2.5) | 40 (q5) | 0 | 0 | 0 |
| Frontier | 230 | 540 | 180 (q2.5) | 50 (q1) | 0 | 0 |
| Ascent | 20 | 250 | 560 (q2.5) | 160 (q5) | 10 (q1) | 0 |
| Summit | 0 | 80 | 310 (q2.5) | 580 (q5) | 20 (q5) | 10 |
| Pinnacle | 0 | 0 | 100 | 330 | 470 | 100 (q5) |

  A gem's purity is clamped to at least its `min_purity`. Gem choice = tiered weight with dimension constraint (core gems 100(q0.1) in all tiers; dimensional gems 50(q2), and only in their tiers and dimension).
- **Loot modifiers (`AP:loot_modifiers/`):**
  - `gem_loot_injection`: chests: 25% `minecraft:chests/*`, 20% other `chests/*`, 20% Twilight structures.
  - `gem_entity_drops`: monster killed by a player: 0.5%.
  - `gem_entity_drops_from_real_players`: monster killed by a *real* (non-fake) player: an additional 4%.
- **Boss drops:** `entity/boss_drops.json` (1 roll): random gem 30 / Sigil of Malice 25 / nothing 45. `entity/rare_boss_drops.json`: gem 45 / Malice 30 / boss music disc 5 / nothing 20. Gateways and chest tables also include `apotheosis:random_gem` entries.
- **Depends on:** AP-9, AP-2, AP-1 · **Size:** M · **Elysium:** NONE (rune drops, if any, are not tier-weighted).

### AP-11: Sockets and socketing (smithing table)
- **What it is:** Items have N sockets (component). Gems go in via the **Smithing Table** (item left, gem right; recipe `AP:recipe/socketing.json` type `apotheosis:socketing`). Right-clicking a gem onto an item in the inventory also works ("Right-click to socket X into Y").
- **Sources of sockets:**
  - Rarity rules (Uncommon 0–1, Rare 0–2, Epic 1–3, Mythic 1–3 or 4 at 5%).
  - **Sigil of Socketing**: smithing recipe `sigil_add_sockets.json` (`apotheosis:add_sockets`, `max_sockets: 2`) adds 1 socket, but at most **2 sockets can be added by sigils** in total ("Max Sockets Applied").
- **Removal:** **Sigil of Withdrawal** (smithing, `apotheosis:withdrawal`) removes all gems and returns item and gems (BOOK). The lang still has *Vial of Arcane Extraction / Searing Expulsion* (destroy item or gems), which is legacy with no recipe in 8.8.0.
- **Events:** `ItemSocketingEvent`, `CanSocketGemEvent`, `GetItemSocketsEvent`. Command `/apoth socket`. Tooltip renderer draws socket icons.
- **Depends on:** AP-9 · **Size:** M · **Elysium:** PARTIAL (`ElysiumSockets`: socket capacity from gear tier, "one slot per two tiers, no ceiling", rune socketing and alignment). Missing: smithing-table socketing UX, sigil-added sockets, withdrawal.

### AP-12: Gem Dust and crushing
- **What it is:** `apotheosis:gem_dust` is the universal crafting currency.
  - **First dust:** drop any gem and let a falling anvil land on it (`info.apotheosis.gem_crushing`; `INBTSensitiveFallingBlock`).
  - Afterwards use the Salvaging Table: gem salvaging `AP:recipe/salvaging/gem/*`: cracked 1–2, chipped 1–3, flawed 2–4, normal 3–5, flawless 4–7, perfect 5–10 dust.
- **Uses:** gem cutting, sigils, Gem-Fused Slate (8 deepslate + 1 dust → 8), tables, gateways, spawner runes.
- **Depends on:** AP-9, AP-15 · **Size:** S · **Elysium:** NONE.

### AP-13: Gem Cutting Table (purity upgrade)
- **Recipe:** `SHS / PGP / P P`, where S = smooth stone, H = shears, P = planks, G = gem dust.
- **How it works:** Recipe type `apotheosis:purity_upgrade` (`AP:recipe/gem_cutting/<input purity>.json`). Inputs: **2 identical gems of the same purity** + `left` (gem dust) + **one of** the `right` material options. Output: 1 gem of the next purity.

| From → to | Gem dust | Material (any one option) |
|---|---|---|
| Cracked → Chipped | 1 | 3 Scrap Metal **or** 1 Timeworn Fabric |
| Chipped → Flawed | 3 | 9 Scrap **or** 3 Fabric **or** 1 Crystal Shard |
| Flawed → Normal | 5 | 27 Scrap **or** 9 Fabric **or** 3 Shard **or** 1 Arcane Sands |
| Normal → Flawless | 7 | 27 Fabric **or** 9 Shard **or** 3 Sands **or** 1 Godforged Pearl |
| Flawless → Perfect | 9 | 27 Shard **or** 9 Sands **or** 3 Pearl |

  The "2 gems" rule comes from the Gem Case button text "Upgrade 2 %s gems to %s" plus BOOK "a second Gem of the same purity". The "any one option" reading of `right` is an interpretation of the list, consistent with the lower rarity costing 3× more.
- **GUI:** `GemCuttingScreen`, JEI category. Advancement trigger `GemCutTrigger`.
- **Depends on:** AP-9, AP-12, AP-19 · **Size:** M · **Elysium:** PARTIAL (rune merge at the Ascension Forge uses the same 2→1 idea, without materials).

### AP-14: Gem Case and Gem Case of House Fabergé
- **Gem Case:** block storage for gems. Capacity is **32,000 per gem type and purity** (BOOK). It has a name filter and an item filter ("fits in this item"), and 6 auxiliary material slots that let you **upgrade gems in place** using the purity recipes (button "Upgrade 2 X to Y", "Will upgrade as many gems as possible!"). Recipe `GGG / BEB / BNB`: glass, basalt, Gem Cutting Table, Ender Chest.
- **Ender Gem Case:** capacity about 2 billion. Made by infusion (`AP:recipe/infusion/ender_gem_case.json`, `keep_nbt_infusion`, Eterna 100, Quanta 100, Arcana 45–50).
- **Depends on:** AP-9, AP-13, AE-5 · **Size:** M · **Elysium:** NONE.

### AP-15: Salvaging Table
- **Recipe:** `III / RSO / GLG`, where I = copper ingot, R = iron pickaxe, S = smithing table, O = iron axe, G = gem dust, L = lava bucket.
- **How it works:** A GUI with an input grid ("Salvage Items" button, or salvage all valid items in the inventory). Recipe type `apotheosis:salvaging {input, outputs:[{stack, min_count, max_count}]}` (`AP:recipe/salvaging/`):
  - Affix items → 1–4 of their rarity material (common→scrap, uncommon→fabric, rare→shard, epic→sands, mythic→pearl).
  - Gems → dust (AP-12).
  - 18 "other" recipes, for example: iron/gold/diamond/leather/chain armour → 1–3 ingots/gems; iron/gold/diamond tools → 0–1; netherite armour/tools → 0–2 netherite scrap; horse armour → 3–8; stone/wooden tools → 0–1 cobblestone/planks; wolf armour → 1–3 armadillo scute; PneumaticCraft compressed iron armour (conditional).
- **Depends on:** AP-5, AP-9 · **Size:** M · **Elysium:** PARTIAL (a villager "Salvager" station exists in elysium-npcs as flavour and trades, with no salvage recipes).

### AP-16: Reforging (Simple Reforging Table and Reforging Table)
- **Recipes:**
  - Simple: ` I / GEG / SSS` (iron ingot, gem dust, enchanting table, smooth stone). Max rarity **Rare**.
  - Full: ` I / ASA / NNN` (netherite ingot, arcane sands, simple table, nether bricks). All rarities.
- **How it works:** Three slots: item (centre), rarity material (top-left), Sigils of Rebirth (bottom-right). Three choices are offered, like an enchanting table.
  - Choice *i* uses seed `playerReforgeSeed ^ itemId.hashCode() + i`, and the output rarity = the material's rarity **[web]**.
  - Choice *i* costs `(i+1) ×` base sigils, materials and levels.
  - After a reforge the player's reforge seed is re-randomised.
  - Any item with a non-`none` loot category can be reforged, even unaffixed ones (BOOK).
- **Base costs** (`AP:recipe/reforging/*.json`, type `apotheosis:reforging {rarity, level_cost, material_cost, sigil_cost, tables}`):

| Rarity | Levels | Materials | Sigils of Rebirth | Tables |
|---|---|---|---|---|
| Common | 2 | 1 | 0 | simple, full |
| Uncommon | 5 | 2 | 1 | simple, full |
| Rare | 15 | 2 | 2 | simple, full |
| Epic | 30 | 2 | 4 | full |
| Mythic | 50 | 3 | 5 | full |

  Sigil of Rebirth: `GGG / EEE / GGG` (Gem-Fused Slate, Gem Dust) → 6. Sounds `reforge_item_placed`, `reforge_item_reforged`.
- **Depends on:** AP-5, AP-8, AP-19 · **Size:** M · **Elysium:** PARTIAL (`ElysiumReforgeHandler` rerolls three bonus stats on Elysium gear with a tier-scaled budget; no rarity-targeted affix regeneration and no 3-choice GUI).

### AP-17: Augmenting Table
- **Recipe:** ` N / GEG / PPP` (Nether Star, Godforged Pearl, Enchanting Table, polished blackstone).
- **How it works:** Slots for the affix item and Sigils of Enhancement, and a drop-down to select one affix.
  - **Upgrade:** level += 0.25, capped at 1.0 (`STANDARD_MAX_LEVEL`). Costs **2 Sigils + 225 levels** (config `Upgrade Sigil Cost` = 2, `Upgrade Level Cost` = 225; charged as the XP of that many levels).
  - **Reroll:** swaps the affix for another of the same affix type that is valid for the item (weighted, current affix removed; the alternatives are listed on the left). Costs **1 Sigil + 175 levels**.
  - Level-independent affixes cannot be upgraded. Creative mode bypasses costs **[web: AugmentingMenu]**.
- **Sigil of Enhancement:** `GEG / EOE / GEG` (dust, slate, Godforged Pearl) → 4.
- **Book vs config:** the book says "upgrade by 25%", which matches.
- **Depends on:** AP-6, AP-7, AP-19 · **Size:** M · **Elysium:** NONE.

### AP-18: Sigils (Malice, Supremacy, and the others)
| Sigil | How obtained | Effect |
|---|---|---|
| Socketing | `GUG / EEE / GAG` (dust, gunpowder, slate, amethyst) → 3 | +1 socket at a smithing table, at most 2 added (AP-11). |
| Withdrawal | `GBG / ELE / GMG` (slate, blaze rod, ender pearl, lava bucket, dust) → 4 | Removes all gems; keeps the item and the gems. |
| Rebirth | → 6 | Reforging fuel (AP-16). |
| Enhancement | → 4 | Augmenting fuel (AP-17). |
| Unnaming | `GGG / FFF / GGG` (slate, flint) → 6 | Removes the affix name. |
| **Malice** | Boss drops (25–30 weight), gateway rewards | Smithing: needs ≥2 level-dependent affixes. Picks 2 distinct affixes with the player's reforge seed: **one gets +2.0 level (overcharged, cap `MAX_LEVEL` 2.0), the other is reset to level 0**. Increments `touched_by_malice` ("Touched by Malice (xN)"). Sound `malice`. **[web: AffixHelper.applyMalice + bytecode `fconst_2`/`fconst_0`]** |
| **Supremacy** | Endless gateway, every 100th wave | Smithing: every affix `+1.5` level (cap 2.0) **[web]**. |
- **Depends on:** AP-6, AP-11 · **Size:** M · **Elysium:** NONE.

### AP-19: Rarity materials, crafting intermediates and upgrade templates
- **Rarity materials:** Mysterious Scrap Metal, Timeworn Fabric, Luminous Crystal Shard, Arcane Sands, Godforged Pearl (tag `apotheosis:rarity_materials`). Obtained only by salvaging (AP-15).
- **Other intermediates:**
  - Gem-Fused Slate: 8 deepslate + 1 gem dust → 8.
  - God-Fused Pearl: infusion of a Godforged Pearl with Eterna 100, Quanta 9.65–11, Arcana 58.75–60.
  - Manifestation of Infinity (`ancient_material`): lang only; legacy.
- **Upgrade smithing templates** (a tiered gear ladder):
  - Iron template: ` M / SGS / SGS` (scrap metal, stone, slate) → 2. Upgrades stone tools and chain armour → iron (+4 iron ingots).
  - Gold template: uses Timeworn Fabric. Iron → gold (+4 gold).
  - Diamond template: uses Crystal Shard. Gold → diamond (+4 diamonds).
  - 27 smithing recipes in `AP:recipe/smithing/`. These keep components, so affixes carry over.
- **Depends on:** AP-15 · **Size:** S · **Elysium:** PARTIAL (Elysium has its own material ladder and convergence smithing recipe `ConvergenceSmithingRecipe`).

### AP-20: Apothic Invaders (natural bosses)
- **What it is:** Rare, heavily buffed mobs with a rarity (Uncommon to Mythic). Each spawns with a beacon beam, a sound (`invader_<rarity>`), and a chat announcement "%s has awoken nearby!" to players within range. Each wears a tier gear set, is enchanted, and **always drops one affix item of its rarity**, with stronger-than-usual affixes, prefixed with the invader's name (BOOK `mobs/invader_items`). Invader drops are also more likely to include gems.
- **Spawning** (`AP:data_maps/dimension_type/invader_spawn_rules.json`, `mobs/InvaderSpawnRules`, `ApothMobEvents` **[web]**):
  - On `FinalizeSpawnEvent` (NATURAL or CHUNK_GENERATION spawn of a `Monster`), roll the dimension's chance for the nearest player's tier. If the roll succeeds, a surface check passes, and the player has no cooldown, the mob is replaced by or turned into an invader.
  - Chances per spawn:

| Dimension | Haven | Frontier | Ascent | Summit | Pinnacle | Surface rule |
|---|---|---|---|---|---|---|
| Overworld | 0 | 0.018 | 0.02 | 0.025 | 0.03 | needs_sky_or_same_vertical_slice |
| The End | 0 | 0.018 | 0.02 | 0.025 | 0.03 | surface_outer_end |
| The Nether | 0 | 0.025 | 0.027 | 0.03 | 0.035 | any |
| Twilight Forest | 0 | 0.05 | 0.053 | 0.06 | 0.063 | needs_surface |

  - Cooldown: config `Boss Spawn Cooldown` default **3600 ticks** (range 0–720000). It is applied to the triggering player and to all same-tier players within `Boss Announcement Range` (default **140** blocks, horizontal).
  - Other config defaults: `Boss Auto-Aggro` false, `Boss Glowing On Spawn` true, `Curse Boss Items` false.
  - Invader rarity = rarity weights for the tier, restricted to the rarities the invader defines stats for.
- **JSON (`AP:apothic_invaders/**`, type `apotheosis:invader`):** `{entity, size{width,height} (spawn-space check), basic_data{weights, constraints, name ("use_name_generation"), bonus_loot:[tables], valid_gear_sets{tier:[#tag]}, nbt?, mount?}, stats{rarity:{attribute_modifiers[{attribute,operation,value|{min,max}}], effects[{effect,amplifier}], enchant_chance, enchantment_levels{primary,secondary}}}}`.
- **Content: 31 invaders.** All have fire resistance (Blaze has Resistance instead) and `scale` −15%…+25% (Blaze and Breeze are fixed ×2).

| File | Entity | Where | Tier weights (w/q) | Rarities | Max HP add (per rarity) | Dmg stat (per rarity) | Armor/Tough (E,M) | Bonus loot | Extras |
|---|---|---|---|---|---|---|---|---|---|
| `overworld/bogged` | minecraft:bogged | overworld | fro 50/0.1 asc 50/0.1 sum 50/0.1 pin 50/0.1 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/bogged_in_wet` | minecraft:bogged | #c:is_wet/overworld | all 120/0.1 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/breeze` | minecraft:breeze | overworld | sum 40/1.5 pin 40/1.5 | M | M 90–140 | M cold_damage +30–40 · M projectile_damage +2.25–4.15 | M 12/20 | boss_drops,rare_boss_drops | gear #{tier}_ranged; M ench 1@100/80 |
| `overworld/enderman` | minecraft:enderman | overworld | asc 50/1.5 sum 50/1.5 pin 50/1.5 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops,rare_boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/husk` | minecraft:husk | overworld | fro 50/0.1 asc 50/0.1 sum 50/0.1 pin 50/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/husk_in_dry` | minecraft:husk | #c:is_dry/overworld | all 120/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/skeleton` | minecraft:skeleton | overworld | all 100/0.1 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/stray` | minecraft:stray | overworld | fro 50/0.1 asc 50/0.1 sum 50/0.1 pin 50/0.1 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/stray_in_cold` | minecraft:stray | #c:is_cold/overworld | all 120/0.1 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/vindicator` | minecraft:vindicator | overworld | asc 100/1.5 sum 100/1.5 pin 100/1.5 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops,rare_boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/wolf` | minecraft:wolf | overworld | fro 100/1.5 asc 100/1.5 sum 100/1.5 pin 100/1.5 | REM | R 50–70 · E 60–100 · M 90–140 | R attack_damage +0.35–0.55 · E attack_damage +0.4–0.75 · M attack_damage +0.7–1.1 | E 6/12 · M 12/20 | boss_drops,rare_boss_drops | nbt {"AngerTime": 99999999}; gear #{tier}_melee; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `overworld/zombie` | minecraft:zombie | overworld | all 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_end/enderman` | minecraft:enderman | the_end | all 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_end/endermite` | minecraft:endermite | the_end | all 5/2 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_end/evoker` | minecraft:evoker | the_end | sum 100/1.5 pin 100/1.5 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops,rare_boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_end/phantom` | minecraft:phantom | the_end | asc 50/0.1 sum 50/0.1 pin 50/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops,rare_boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_end/shulker` | minecraft:shulker | the_end | all 20/2 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_nether/blaze` | minecraft:blaze | the_nether | sum 100/1.5 pin 100/1.5 | M | M 90–140 | M fire_damage +30–40 · M projectile_damage +2.75–5.5 | M 12/20 | boss_drops,rare_boss_drops | gear #{tier}_ranged; M ench 1@100/80 |
| `the_nether/piglin` | minecraft:piglin | the_nether | all 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | nbt {"CannotHunt": 1, "ImmuneToZombification": 1}; gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_nether/piglin_brute` | minecraft:piglin_brute | the_nether | asc 100/1.5 sum 100/1.5 pin 100/1.5 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops,rare_boss_drops | nbt {"ImmuneToZombification": 1}; gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_nether/wither_skeleton` | minecraft:wither_skeleton | the_nether | asc 100/1.5 sum 100/1.5 pin 100/1.5 | EM | E 60–100 · M 90–140 | E fire_damage +7–12 · E arrow_damage +0.45–0.65 · M fire_damage +12–20 · M arrow_damage +0.75–1.2 | E 6/12 · M 12/20 | boss_drops,rare_boss_drops | gear #{tier}_ranged; E ench 0.75@45/30; M ench 0.95@60/40 |
| `the_nether/zoglin` | minecraft:zoglin | the_nether | fro 100/2 asc 100/2 sum 100/2 pin 100/2 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `the_nether/zombified_piglin` | minecraft:zombified_piglin | the_nether | all 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25 |
| `twilight/carminite_golem` | twilightforest:carminite_golem | twilight_forest | sum 100/0.1 pin 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops,rare_boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25; needs twilightforest |
| `twilight/goblin_knight` | twilightforest:lower_goblin_knight | twilight_forest | asc 100/0.1 sum 100/0.1 pin 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops,rare_boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25; needs twilightforest |
| `twilight/helmet_crab` | twilightforest:helmet_crab | twilight_forest | pin 2/0.55 | M | M 100–140 | M attack_damage +0.32–0.85 | M 12/20 | boss_drops,rare_boss_drops | gear #{tier}_melee; M ench 0.85@60/35; needs twilightforest |
| `twilight/kobold` | twilightforest:kobold | twilight_forest | all 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25; needs twilightforest |
| `twilight/redcap` | twilightforest:redcap | twilight_forest | all 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25; needs twilightforest |
| `twilight/redcap_sapper` | twilightforest:redcap_sapper | twilight_forest | all 100/0.1 | UREM | U 20–60 · R 40–90 · E 80–150 · M 110–180 | U attack_damage +0.2–0.4 · R attack_damage +0.25–0.5 · E attack_damage +0.3–0.65 · M attack_damage +0.7–1.05 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_melee; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25; needs twilightforest |
| `twilight/skeleton` | minecraft:skeleton | twilight_forest | all 100/0.1 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25; needs twilightforest |
| `twilight/skeleton_druid` | twilightforest:skeleton_druid | twilight_forest | all 100/0.1 | UREM | U 20–50 · R 45–70 · E 95–120 · M 125–150 | U projectile_damage +0.2–0.4 · R projectile_damage +0.3–0.55 · E projectile_damage +0.45–0.65 · M projectile_damage +0.75–1.2 | E 4/10 · M 10/20 | boss_drops | gear #{tier}_ranged; U ench 0.25@20/12; R ench 0.35@23/15; E ench 0.6@35/25; M ench 0.75@35/25; needs twilightforest |

- Common stat pattern (for example the zombie): movement speed +15–20% (U) up to +30–65% (M) base-mult. Attack damage +20–40% (U) up to +70–105% (M). Knockback resistance 0.3 / 0.4 / 0.5 / 0.8. Epic gets armour 4 and toughness 10; Mythic gets armour 10 and toughness 20. `enchant_chance`: 0.25 / 0.35 / 0.6 / 0.75 at enchant levels 20/12, 23/15, 35/25, 35/25.
- **Items:** *Apothic Invader Summoner* (`boss_summoner`, creative/utility; spawns a random invader). Commands `/apoth boss`. Advancement predicate `apotheosis:is_invader`.
- **Depends on:** AP-1, AP-2, AP-5, AP-8, PL-1, AP-22 · **Size:** L · **Elysium:** PARTIAL (`elysium-mobs` has 2 hand-authored bosses (Choir, Praetor) plus dungeon bosses, and `ElysiumSquads` sends roaming bands with elite leaders on a scheduled gap. There are no random data-driven "invader" upgrades of natural spawns.)

### AP-21: Apothic Elites and Mob Augments
- **Elites** (`AP:apothic_elites/**`, type `apotheosis:elite`): named or empowered variants that replace a normal spawn silently (no announcement).
  - On a matching spawn the elite is chosen by tiered weight, then applied if `nextFloat ≤ success_chance`.
  - JSON: `{entities (one or a list), success_chance, basic_data{name, constraints, spawn_conditions[not spawn_type / surface_type], mount{entity,nbt}, supporting_entities, nbt, valid_gear_sets, weights}, stats{attribute_modifiers, effects, enchant_chance, enchantment_levels}, affix_data{affix_chance}}`.

| Elite | Entities | Chance | Tiers | Key stats |
|---|---|---|---|---|
| Craig the Eternal (rainbow name) | goat (screaming, 1 horn) + support sheep "jeb_" | 0.005 | Pinnacle only | +4096 HP, attack ×(5–11) (mult_total 4–10), KB res 1, scale +3, Fire Res II, Glowing, enchant 100/100. Also the final wave of the Pinnacle gateway. |
| Honeyed Archer | skeleton/stray/bogged riding an angry bee | 0.008 | F–P | +10–20 HP, arrow dmg +25–35%, KB 0.5, affix 25%. Surface only. |
| Undead Knight | skeleton/stray/bogged on a skeleton horse (burns in sun) | 0.01 | F–P | +10–30 HP, arrow +30–50%, affix 35%. |
| Withering Archer (Nether) | wither skeleton/skeleton carrying a wither-potion area cloud (r 2.5) | 0.02 | F–P | +20 HP, speed +10%, arrow +25%, affix 45%. |

- **Augments** (`AP:apothic_augments/`, type `apotheosis:augment {application_chance, conditions, modifiers}`): 1 entry, `random_affix_items` = 12% of monsters not from spawners get a random affix item (`apotheosis:random_affix_item` modifier).
- **Depends on:** AP-2, AP-8, PL-1 · **Size:** M · **Elysium:** PARTIAL (elite role in squads, `MobVariant` abilities; not data-driven spawn replacements).

### AP-22: Gear sets usage
See PL-1. Tags per tier are referenced by invaders, elites and gateway waves. **Size:** S · **Elysium:** NONE.

### AP-23: Invader Dungeons (underground structures) and the Caged Invader block
- **Features:**
  - `boss_dungeon` and `boss_dungeon_2` (`AP:worldgen/configured_feature`, success 1.0), each placed 6×/chunk from y=0 to top and 8×/chunk from bottom+6 to y=−1 (`placed_feature/*_deep`).
  - Each contains a **Caged Invader** (`boss_spawner` block) that releases an invader when a player gets close, plus loot chests (BOOK).
  - An item-frame gem processor (`processor_list/item_frame_gems`) puts gems in item frames.
  - Biome modifiers (`AP:neoforge/biome_modifier/*`, type `apotheosis:blacklist`) exclude oceans. Config `Generation Dimension Whitelist` = `overworld`.
- **Depends on:** AP-20 · **Size:** M · **Elysium:** PARTIAL (elysium-dungeons has instanced dungeons).

### AP-24: Rogue spawners (Brutal and Swarm)
- **What it is:** Underground spawner "rooms" whose covers are cracked stone bricks, mossy cobblestone, crying obsidian or lodestone (tag `rogue_spawner_covers`), with a loot chest.
  - Feature `rogue_spawner` (success 0.01), placed 65×/chunk (y 10–top) and 85×/chunk (bottom+6 to −1).
  - Config `Spawner Value Chance` **0.11** = chance the chest is `chest_valuable` instead of the variant table.
- **JSON (`AP:rogue_spawners/`, type `apotheosis:rogue_spawner {weight, stats{apothic_spawners:*}, spawn_potentials, loot_table}`):**
  - **Brutal** (4; loot `chests/spawner_brutal`, 5–8 rolls): zombie w30, husk w20, pillager w20, rotating w10 (zombie/husk/pillager).
    - Mobs have +50% HP, +50% damage (pillager +30% projectile), +15% speed, and Fire Resistance.
    - Stats: min delay 200 (rotating 180), max delay 400, count 3 (pillager 2), max nearby 6 (pillager 4, rotating 7), player range 12, spawn range 5.
  - **Swarm** (5; loot `chests/spawner_swarm`, 5–6 rolls): spider w30, cave spider w20, silverfish w20, baby zombie w10 (Speed II, youthful), fast cave spider w10 (Speed III).
    - Stats: min 50, max 125, count 2, max nearby 10, range 14, spawn range 7.
- **Depends on:** AS-1 · **Size:** M · **Elysium:** NONE.

### AP-25: Tome Towers (surface structures)
- Jigsaw structures `tower_main/leaf/sand/spruce` (`AP:worldgen/structure*`). Random spread spacing 26, separation 18, salt 12121818; each picked with weight 1; not in oceans.
  - Biomes: main = forest/mountain/plains; leaf = jungle/swamp/lush; sand = badlands/savanna/desert; spruce = taiga/snowy.
- Loot `chests/tome_tower` (5–9 rolls): includes `enchant_randomly` books (weight 400), random gem 80, random affix item 80, and tomes and materials.
- **Depends on:** AP-8, AP-10 · **Size:** M · **Elysium:** PARTIAL (Elysium has its own structures and dungeons).

### AP-26: Gateways to Eternity integration (tiered gateways and endless invader gate)
- **Requires the Gateways mod.** New gateway type `apotheosis:tiered` (settings: tier, size, colour, soundtrack). It can only be opened in the matching world tier ("This Gateway can only be opened in World Tier: %s").
- **New wave entities:** `apotheosis:invader`, `apotheosis:elite`, `apotheosis:true_random_invader`.
- **New modifiers:** `apotheosis:affix {affix_chance}` (gives the wave mob an affix item), `apotheosis:passenger`.
- **New rewards:** `apotheosis:affix_item {rarities}`, `apotheosis:gem {purities}`, `apotheosis:true_random_gem`.
- **Gate pearl recipes** (`AP:recipe/gateways/tiered/`):
  - Frontier: `SIS / BEB / RRR` (spider eye, iron, bone, ender pearl, rotten flesh).
  - Ascent: `ILI / LEL / GGG` (gold, crystal shard, ender pearl, dust).
  - Summit: `BGB / AEA / MMM` (blaze powder, ghast tear, arcane sands, ender eye, dust).
  - Pinnacle: `SWS / IGI / EEE` (Malice, warden tendril, infused breath, Godforged Pearl, dust). There is a fallback recipe without AE.

| Gate | Waves (max time t) | Notable wave content | Per-wave XP | Final rewards |
|---|---|---|---|---|
| Frontier (small, #33FF33) | 3 (2400, 2400, 3200) | affixed zombies (frontier/iron), affixed skeletons (chain); wave 3 + 2 invaders; waves 2–3 buff mobs +10/15% HP, +2.5/3.5 armor, +15/25% dmg | 350, 650 | 3 affix items (Uncommon/Rare), 5 gems (chipped/flawed), 16 dust, 8 Timeworn Fabric, 2 Sigils of Socketing |
| Ascent (medium, #5555FF) | 3 (2400, 3200, 4800) | witch, affixed husk/stray, polar bear, wolves, bogged, vampiric phantoms, 3 invaders + 5 "Nether Heralds" | 450, 650, 850 | 3 affix (Rare/Epic), 5 gems (flawed/normal), 24 dust, 12 Crystal Shard, 2 Socketing, 3 wither skeleton skulls |
| Summit (medium, #BB00BB) | 3 (3000, 3200, 6400) | ghasts, zombified piglins, wither skeletons, ghast riders, piglin brutes, 3 invaders + 5 Bastion Elite Guards | 1050, 1250, 1550 | 3 affix (Epic/Mythic), 5 gems (normal/flawless), 48 dust, 24 Arcane Sands, 6 Socketing, 1 netherite upgrade template |
| Pinnacle (large, #ED7014) | 4 (3000, 3200, 6400, 2000) | 3 "giants" (zombies), illusioners, evokers, killer bunnies, 3 invaders + vexes, final wave Craig the Eternal | 2000, 2000, 2500 | 5 Mythic affix items, 10 Perfect gems, 64 dust, 64 Godforged Pearls, 1 Endless Invader gate pearl |
| Endless Invader (large, rainbow) | endless; base wave 3 true-random invaders (3600t) | every 3 waves (max 100): +1 invader, −25 wave time, −5 setup time, mobs +25% HP, +10% armour, +3% toughness, +25% dmg/proj, +8% shred | per base wave: 1 Mythic affix, 3 Perfect true-random gems, 25000 XP; per 3 waves: 16 pearls, 16 dust, 25000 XP; every 100 waves: +100% HP and **Sigil of Supremacy** | — |

  Rules: 3 lives, a player must be nearby. Summit and Pinnacle add leash 48 and spawn range 24. Endless allows discarding and uses spacing 32 and an inward-spiral spawn. Soundtracks: music discs Shimmer / Flash / Glimmer, plus the `soundtrack_summit` and `soundtrack_pinnacle` sounds.
- **Depends on:** AP-1, AP-8, AP-9, AP-20, AP-21, PL-1, the external Gateways mod · **Size:** XL if you also rebuild the gateway engine (wave system, portal entity, boss bar); L if you only port the content · **Elysium:** NONE (the dungeon instances are the closest analogue).

### AP-27: Wandering Trader improvements
- `AP:wanderer_trades/` (PL-3):
  - 16 basic trades, for example 25 emeralds → Beacon (rare), plus totem, enchanted golden apple, diamond, skulls, horse armour, ingots, saddle, ender eye, blaze powder, prismarine.
  - 10 **tiered rare-gear trades** (`apotheosis:tiered_trade`, Summit and Pinnacle variants). Named heavily-enchanted gear such as *Arachnid's Fear* (diamond sword + 45 emeralds → Bane X, Fire Aspect V, Looting V, Mending, Unbreaking V). The others: Bonesplitter, Captive Dreams, Eternal Vigilance, Greatplate of Eternity, Rune-Forged Greaves, Stonebreaker, Thunder-Forged Legguards, Timeworn Visage, Treecapitator.
  - 10 `automatic_affix_trade` slots (auto-generated affix items).
- Config `Underground Trader` true: traders can spawn underground. Improved spawn rates via `WandererSpawnerMixin` (BOOK).
- **Depends on:** PL-3, AP-8 · **Size:** M · **Elysium:** PARTIAL (`ElysiumVillagerTrades`).

### AP-28: Potion Charms
- **What it is:** Charm of X.
  - Crafting: 3 identical potions + 6 blaze powder (`BBB / PPP / BBB`, `apotheosis:potion_charm_crafting`).
  - Right-click toggles it on or off. While enabled and in the inventory (or a Curios `charm` slot), it re-applies a couple of seconds of the effect and uses durability. "A single charm lasts longer than the three potions combined" (BOOK).
  - Tag `extended_charm_duration` (health_boost, night_vision) gives these a longer applied duration. Blacklist tag `potion_charm_blacklist` (empty).
  - **Unbreakable charm** via infusion (Eterna 100, Quanta 8.5–13.5, Arcana 32.5–37.5).
  - Config `Restrict Charms to Curios` false.
- **Depends on:** AE-5 (unbreakable variant), AA-9 · **Size:** M · **Elysium:** PARTIAL (elysium-trinkets has crafted and unique trinkets).

### AP-29: Spawner rune system (Apotheosis replaces the Apothic Spawners item modifiers)
- Apotheosis **disables every default Apothic Spawners modifier recipe** by overriding `data/apothic_spawners/recipe/spawner_modifiers/*.json` with `neoforge:false`. It adds rune items instead (`AP:recipe/spawner_modifiers/`).
- **Obtaining runes:** Blowing up a spawner drops 48–64 **Spawner Chains** (the `apothic_spawners:gameplay/unstable_spawner` loot table is overridden). Spawner Rune: `GEG / SES / SSS` (dust, slate, chains) → 2. Infused Spawner Rune: infusion Eterna 70, Quanta 30, Arcana 50 (or a crafted fallback without AE).
- **Tier upgrade runes** set the stats outright:

| Rune (recipe core) | Min delay | Max delay | Spawn count | Max nearby | Player range |
|---|---|---|---|---|---|
| Frontiersman's (Timeworn Fabric, clock, flint, rune) | 150 | 600 | 7 | 12 | 24 |
| Wayfarer's (Crystal Shard, **spawn egg**, quartz, Sigil of Socketing) | 100 | 400 | 10 | 18 | 32 |
| Mountaineer's (Arcane Sands, warden tendril, phantom membrane, Malice, infused rune) | 50 | 200 | 13 | 24 | 40 |
| Thundering (Godforged Pearl, **Perfect Endersurge gem**, infused breath, nether star, infused rune) | 20 | 20 | 16 | 32 | 48 |

- **Utility runes** (each has an `_inverse` recipe: the same rune + a quartz off-hand turns the flag off):

| Rune | Recipe | Effect |
|---|---|---|
| Spawning Range | rune + 4 pistons | spawn_range +2 (max 32) |
| Control | rune + 4 comparators | redstone_control = true |
| Perpetual Twilight | rune + 4 soul lanterns | ignore_light = true |
| Debilitation | rune + 4 pointed dripstone | initial_health −0.16 (min 0.2) |
| Silence | rune + 4 wool | silent |
| Youth | rune + 4 turtle eggs | youthful |
| Immolation | rune + 4 campfires | burning |
| Stupor | infused rune + 3 chorus + fatigue potion | no_ai |
| Defiance | infused rune + conduit + 3 ender pearls | ignore_conditions |
| Solitude | infused rune + nether star + 2 phantom membranes + ender eye | ignore_players |
| Echoes | infused rune + 8 echo shards | echoing +1 (max 3) |

- **Depends on:** AS-1, AS-2, AE-5, AP-12 · **Size:** M · **Elysium:** NONE.

### AP-30: Apothic Enchanting integration
- Tier augments reduce `max_eterna` (AP-3). A GUI message: "Limited to %s by your World Tier".
- **Enchanting Table of the Raven** recipe: ` A / GPG / OOO` with a **Perfect Gem of the Royal Family**, God-Fused Pearls, Apothic Enchanting Table, obsidian (AE-11).
- Tag `apothic_enchanting:cannot_be_converted_to_xp` += gems (Knowledge of the Ages).
- Infusions: God-Fused Pearl, Ender Gem Case, Unbreakable Potion Charm, Infused Spawner Rune.
- **Depends on:** AE-1…AE-5 · **Size:** S · **Elysium:** NONE.

### AP-31: Quality-of-life features
- **Item linking:** hotkey to link the hovered item in chat. Config enabled, cooldown 100 ticks.
- **Equipment comparison:** hold the hotkey to compare the hovered item against the equipped one (`EquipmentComparePositioner`).
- **Radial mining mode toggle** (see AP-7).
- **Affix item world effects:** beams, shadows and rarity particles.
- **Jade and JEI plugins:** salvaging, gem cutting, smithing (socket, withdrawal, unnaming, malice, supremacy, add sockets), potion charm, reforge. A Patchouli book (crafted from book + gold ingot).
- **Commands:** `/apoth` affix, boss, category, debug_weight, gem, rarity, reforge, socket, world_tier.
- **Depends on:** various · **Size:** M in total · **Elysium:** PARTIAL (Elysium has a codex item and screens).

### AP-32: Twilight Forest compatibility
- 46 affix loot entries, 8 invaders, twilight gear sets, 2 gems (Forest, Frozen Queen) with special bonuses (Treasure Goblin, Ore Magnet, Fortification, `frosted` effect).
- **Size:** M · **Elysium:** NONE (and not needed unless you support TF).

### AP-33: Vanilla tweaks inside Apotheosis
- Armour max 200 and toughness max 100 (AA-2).
- Damage types `apotheosis:execute` and `apotheosis:psychic` plus tags.
- Wither skull and skeleton mixins. `EndDragonFightMixin` (dragon kill tracking for the Pinnacle unlock). `MobMixin` (boss/elite data).
- **Size:** S · **Elysium:** NONE.

### AP-34: Cosmetic content
- 3 music discs: Flash, Glimmer, Shimmer (tag `boss_music_discs`, dropped by rare invaders at weight 5).
- 5 paintings: Gems, Craig, Tower, Enchanting Table, Window.
- Leftover models and lang for Ender Lead, Lucky Rabbit's Foot, star cube and hammer (not registered in `Apoth$Items`, so they are legacy).
- **Size:** S · **Elysium:** NONE (optional).

---
# 4. Apothic Enchanting 1.6.2 (AE)

### AE-1: Enchanting stats and how a table gathers them
- **Stats:**
  - **Eterna** sets the max enchanting level.
  - **Quanta** (%) sets the variance of power.
  - **Arcana** (%) changes the rarity weights and guarantees extra enchantments.
  - **Quantic Stability** (rectification): no negative Quanta rolls.
  - **Clues:** how many preview lines are shown.
  - **Treasure** allowed.
  - **Blacklist** (filtered enchantments).
- **Gathering** (`EnchantmentTableStats`, bytecode + [web]):
  - Scans the vanilla `BOOKSHELF_OFFSETS`, requiring the midpoint to be `#enchantment_power_transmitter` (air).
  - Per block: registry stats (AE-3 JSON) if present; otherwise the block's `EnchantmentStatBlock` methods. A block without either gives **Eterna = 2 × vanilla enchantPowerBonus** (vanilla bookshelf = 2) with a default max Eterna of **30** (`EnchantmentStatBlock.getMaxEnchantingPower` default).
  - Eterna capping: contributions are grouped by their per-block max. Groups are processed in ascending order of max, and each group can only raise the running total up to its own max. Blocks with max ≤ 0 add freely (can be negative).
  - The builder starts at **Quanta 15, Clues 1**. Final clamps: Eterna 0–100, Quanta 0–100, Arcana 0–100.
  - Item enchantability adds Arcana: **+50% of the item's enchantability** (BOOK `table/enchantability`).
  - **Units:** the stat JSON `eterna` is in table-level units (vanilla shelves give 2 each, so 15 shelves = level 30). The book's "each point of Eterna = 2 levels" is display-scale only. The effective level is also capped by the player's `max_eterna` attribute (AE-12).
- **Depends on:** none · **Size:** M · **Elysium:** NONE.

### AE-2: Enchanting algorithm (replaces the vanilla enchanting table)
- **Slot levels [web: ApothEnchantmentHelper]:** `L = round(eterna)`. Slot 3 = L. Slot 2 = round(L × U(0.6, 0.8)). Slot 1 = round(L × U(0.2, 0.4)), minimum 1. Random draws use the player's enchanting seed (BOOK `table/seed`).
- **Power:** `power = clamp(round(L × (1 + quanta × f / 100)), 1, 200)`. Here `f = clamp(gaussian/3, −1, 1)` normally, or `f = U(0, 1)` with Quantic Stability (BOOK: "normal distribution → uniform on positive side").
- **Candidate enchantments:** those in `#in_enchanting_table`, or (with a treasure shelf) `#treasure` too. Blacklisted enchantments and existing ones are removed. The level of each enchantment is the highest whose `[minPower(level), maxPower(level)]` contains the power (AE-9).
- **Rarity weights by Arcana** (`table/Arcana` enum [web]; the vanilla weight is converted to a legacy rarity):

| Arcana ≥ | Common | Uncommon | Rare | Very Rare |
|---|---|---|---|---|
| 0 | 10 | 5 | 2 | 1 |
| 10 | 8 | 5 | 3 | 1 |
| 20 | 7 | 5 | 4 | 2 |
| 30 | 5 | 5 | 4 | 2 |
| 40 | 5 | 5 | 4 | 3 |
| 50 | 5 | 5 | 5 | 5 |
| 60 | 3 | 4 | 5 | 5 |
| 70 | 2 | 4 | 5 | 5 |
| 80 | 2 | 4 | 5 | 7 |
| 90 | 1 | 3 | 5 | 8 |
| 99 | 1 | 2 | 5 | 10 |

- **Number of enchantments [web]:** guaranteed picks at Arcana thresholds 0/33/66/99, i.e. 1–4 guaranteed. Then `while rand.nextInt(max(50, L × 1.15)) ≤ L: pick another; L /= 2`.
  - **Book vs source:** BOOK `table/stats` says "At 25% Arcana you always get ≥2, at 75% ≥3". The 1.21 source loop is `for i in 0..<100 step 33`. **Unverified in bytecode.**
- **XP cost:** charged as the "optimal" XP: the points you would spend if you had exactly the needed levels (BOOK `changes/experience_costs`, `apoth_chargeOptimalLevels`).
- **Enchanting Info screen:** the "?" button shows per-slot min/max power, a power slider, available enchantments with weight and chance, exclusivity, and the rarity weights.
- **Depends on:** AE-1, AE-9 · **Size:** L · **Elysium:** NONE.

### AE-3: Shelves and stat blocks (content)
JSON `AE:enchanting_stats/*.json` = `{block|blocks|tag, stats{eterna, maxEterna, quanta, arcana, clues}}` (quanta and arcana in %). Code-defined entries are marked *.

| Block | Eterna | Max Eterna | Quanta | Arcana | Clues | Other | Recipe / source |
|---|---|---|---|---|---|---|---|
| Vanilla bookshelf* | 2 | 30 | 0 | 0 | 0 | | vanilla |
| Candles* | 0 | – | 0 | +1.25 × candle count | 0 | | vanilla |
| Basic Bookshelf | 2 | (30) | | | | | planks + tomes |
| Skulls (skeleton/creeper/zombie/piglin, `#basic_skulls`) | 0 | 0 | +5 | 0 | 0 | | |
| Wither skeleton skull | 0 | 0 | +10 | 0 | 0 | | |
| Amethyst cluster | 1 | 40 | −1 | 0 | 0 | | |
| Hellshelf | 3 | 45 | 3 | 0 | | | nether bricks + blaze rod + bookshelf + regen potion |
| Infused Hellshelf | 5 | 60 | 5 | 0 | | | infusion E45 Q30 A0 |
| Blazing Hellshelf | 10 | 65 | 10 | 0 | −1 | | infused + fire charges + blaze powder |
| Glowing Hellshelf | 5 | 60 | 5 | 3.333 | | | infused + glowstone |
| Seashelf | 3 | 45 | 0 | 3 | | | prismarine bricks + water bottle + pufferfish + bookshelf |
| Infused Seashelf | 5 | 60 | 0 | 5 | | | infusion E45 Q15 A10 |
| Crystalline Seashelf | 5 | 60 | 3 | 5 | | | infused + prismarine crystals |
| Heart-Forged Seashelf | 15 | 60 | 0 | 20 | | | infused + heart of the sea + shards |
| Dormant Deepshelf | 2 | (30) | 0 | 0 | | | cracked deepslate tiles + books |
| Deepshelf | 5 | 70 | 5 | 5 | | | infusion E60 Q40 A40 |
| Echoing Deepshelf | 5 | 75 | 0 | 15 | | | deepshelf + echo shard + candles |
| Soul-Touched Deepshelf | 5 | 75 | 15 | 0 | | | deepshelf + soul lantern + sculk |
| Echoing Sculkshelf | 10 | 80 | 10 | 15 | +1 | plays sculk noise (1/200 per tick) | echoing deepshelf + warden tendril + sculk + catalyst |
| Soul-Touched Sculkshelf | 10 | 80 | 15 | 10 | +1 | | soul-touched deepshelf + same |
| Endshelf | 5 | 90 | 5 | 5 | | | end stone bricks + infused breath + ender pearl + bookshelf |
| Pearlescent Endshelf | 10 | 90 | 7.5 | 7.5 | | | endshelf + end rods + pearls |
| Draconic Endshelf | 20 | 100 | 0 | 0 | | | endshelf + dragon head + pearls |
| Beeshelf | −30 | 0 | +100 | 0 | | | beehives + honey |
| Melonshelf | −2 | 0 | −10 | 0 | | | melons + glistering melon |
| Stoneshelf | −3 | 0 | 0 | −7.5 | | | polished andesite + books |
| Hellshelf of Sight | – | – | – | – | +1 | | infused hellshelf + ender eye + spyglass + gold blocks + night vision |
| Hellshelf of Masterful Sight | – | – | – | – | +2 | | sightshelf + emerald blocks + netherite + long night vision |
| Seashelf of Aquatic Filtration* | +1 per inserted book | 30 | 0 | +1 per book | | **Blacklists** up to 6 single-enchantment books (chiseled-bookshelf style insertion) | infused seashelves + prismarine |
| Deepshelf of Arcane Treasures* | 10 | 70 | −15 | −15 | | **Allows treasure enchantments** | deepshelves + gold/emerald blocks + diamonds |
| Geode-Encased Bookshelf of Stability* | 10 | 60 | −15 | 0 | | **Quantic Stability** | calcite + bookshelf + budding amethyst |

  Filtering shelf: `getEnchantPowerBonus = books × 0.5`, ×2 via registry = 1 Eterna per book. Treasure and Geode `5.0 × 2 = 10`.
- **Progression** (BOOK `enchanting/progression`): L30 vanilla → L45 hell/sea → L60 infused → L70 deepshelf → L75 echoing/soul → L80 sculk (Warden) → L90 endshelves (infused breath) → L100 one Draconic Endshelf.
- **Depends on:** AE-1, AE-5 · **Size:** M (blocks and recipes) · **Elysium:** NONE.

### AE-4: Clues (enchantment preview)
- The base is 1 clue. Each clue reveals one more enchantment of the slot's roll. "The Runes Reveal All" when every enchantment is shown. It can drop to 0 ("The Runes Reveal Nothing") via the Blazing Hellshelf (−1). Network payload `CluePayload`.
- **Depends on:** AE-2 · **Size:** S · **Elysium:** NONE.

### AE-5: Infusion Enchanting
- **What it is:** Crafting in the enchanting table. The item is placed in the slot, and **only the 3rd slot** is used. It needs Eterna, Quanta and Arcana ≥ the `requirements`, and ≤ `max_requirements` when given (−1 = no max). It lets you "over-spend" XP (BOOK). Recipe types `apothic_enchanting:infusion` and `keep_nbt_infusion` (keeps components).
- **Recipes (19 in AE + 4 in Apotheosis):**

| Input | Output | Min E / Q / A | Max E / Q / A |
|---|---|---|---|
| Hellshelf | Infused Hellshelf | 45 / 30 / 0 | – |
| Seashelf | Infused Seashelf | 45 / 15 / 10 | – |
| Dormant Deepshelf | Deepshelf | 60 / 40 / 40 | – |
| Amethyst Block | Budding Amethyst | 60 / 30 / 50 | – / 50 / – |
| Carrot | Golden Carrot | 20 / 10 / 0 | 20 / 30 / – |
| Echo Shard | 4 Echo Shards | 70 / 50 / 50 | – |
| Honey Bottle | XP Bottle ×1 / ×8 / ×32 | 20 / 60 / 100 E, 25 Q, 25 A | – |
| Dragon's Breath | 3 Infused Dragon's Breath | 80 / 15 / 60 | – / 30 / – |
| Inert Trident | Trident | 40 / 20 / 35 | – / 50 / – |
| Tome of Scrapping | 4 Tomes of Superior Scrapping | 45 / 25 / 35 | – / 50 / – |
| Superior Scrapping Tome | 4 Tomes of Extraction | 60 / 25 / 45 | – / 75 / – |
| Flimsy Ender Lead | Ender Lead | 45 / 25 / 40 | – |
| Ender Lead holding a **witch** | Occult Ender Lead | 75 / 85 / 60 | – / – / 85 |
| Enchantment Library | Library of Alexandria (keep NBT) | 100 / 45 / 100 | 100 / 50 / 100 |
| Creeper music discs | Disc Eterna / Quanta / Arcana | 40/0/0 · 10/40/0 · 10/0/40 | – |
| *(AP)* Godforged Pearl | God-Fused Pearl | 100 / 9.65 / 58.75 | 100 / 11 / 60 |
| *(AP)* Spawner Rune | Infused Spawner Rune | 70 / 30 / 50 | – |
| *(AP)* Gem Case | Ender Gem Case (keep NBT) | 100 / 100 / 45 | 100 / 100 / 50 |
| *(AP)* Potion Charm | Unbreakable charm | 100 / 8.5 / 32.5 | 100 / 13.5 / 37.5 |

  Placeholder enchantment `apothic_enchanting:infusion` (no items) is used to show the slot.
- **Depends on:** AE-1 · **Size:** M · **Elysium:** NONE.

### AE-6: Enchantment Library and Library of Alexandria
- **What it is:** Block storage of enchantments as points **[web: EnchLibraryTile]**.
  - Points for a level: `2^(level−1)`.
  - Library: max level **16**, max points 32,768. Alexandria: max level **31**, max points 2^30.
  - Depositing a book adds each enchantment's points (excess over max is voided) and records the max level ever inserted.
  - Extraction to a target level costs `pts(target) − pts(current)` and is only allowed up to the recorded max level. Click = +1 level; shift-click = as many as possible. A book placed in the output slot receives enchantments.
  - Hopper insertion is allowed; extraction by automation is not. Name and item filters.
- **Recipes:** Library = `CSC / STS / CSC` (ender chests, infused shelves, enchanting table). Alexandria = infusion (AE-5).
- **Depends on:** AE-5 · **Size:** M · **Elysium:** NONE.

### AE-7: New enchantments (18)
Source `AE:enchantment/*.json` (1.21 data-driven enchantments with custom effect components). Colour marks the category: dark_green = **Masterwork** (min power ≥55), dark_purple = **Twisted** (≥45), dark_red = **Corrupted** (curse, ≥65). `minCost` = base + per_level×(L−1); maxCost 200 for all.

| Enchantment | Max lvl | Items | Weight | Min cost (base+per lvl) | Effect with exact numbers |
|---|---|---|---|---|---|
| Berserker's Fury (Corrupted, curse) | 3 | chest armour | 1 | 50+40 | On taking damage: lose **2.5^L** HP (damage type `corrupted`, bypasses armour and effects), gain Resistance, Strength and Speed at amplifier L−1 for 500 t. Cooldown 900 t (0:45). |
| Boon of the Earth (Masterwork) | 5 | pickaxes | 1 | 55+25 | Mining stone: 1%×L chance to roll `boon_stone_drops`. Deepslate: 1.5%×L (`boon_deepslate_drops`). `#base_stone_nether`: 1%×L (`boon_netherrack_drops`). Entries: stone = raw copper/iron/coal 100, raw gold/lapis 20, diamond 5, ore-block variants, emerald 15. Deepslate = raw iron 80, raw gold 100, redstone/lapis/diamond 60, ore blocks. Nether = quartz 150, raw gold 100, glowstone 60, ancient debris 1–3. |
| Chainsaw (Masterwork) | 1 | axes | 1 | 55 | Fells the whole tree, using durability (`ChainsawTask`). |
| Chromatic Aberration | 1 | shears | 5 | 25 | Sheared wool gets a random colour. |
| Crescendo of Bolts (Masterwork) | 5 | crossbow | 1 | 55+30 | +L extra shots per reload, which use no ammo. |
| Endless Quiver (Masterwork) | 1 | bow | 1 | 60 | `ammo_use` set to 0 (all arrows infinite). Exclusive with Infinity. |
| Growth Serum (Masterwork) | 1 | shears | 1 | 40 | Sheep have a 50% chance to regrow wool instantly. |
| Icy Thorns | 3 | chest armour | 2 | 35+20 | When hit: chance 0.5×L (so certain at L≥2) to apply Slowness to the attacker for 10–20 t, amplifier up to clamp(2L, 1, 4). Exclusive with Thorns. |
| Knowledge of the Ages (Masterwork) | 3 | swords (primary) / sharp weapons | 2 | 55+45 | On kill: every drop becomes XP at **25×L XP per item**. Not for player kills. Gems are excluded (tag). |
| Life-Mending (Corrupted, curse) | 3 | any damageable | 1 | 65+35 | Healing received is spent to repair: **2^(L−1) durability per HP**. Exclusive with Mending. Can appear in the table. |
| Miner's Fervor (Twisted) | 5 | mining tools | 2 | 45+30 | Break speed becomes `12 + 4.5×(L−1)` but never instant-break. Exclusive with Efficiency. |
| Nature's Blessing | 3 | hoes | 2 | 15+10 | Right-click acts as bonemeal for **5−(L−1)** durability (5/4/3). |
| Rebounding | 3 | chest/leg armour | 2 | 22+18 | Melee attackers within 4 blocks are knocked back with horizontal 2L and vertical 3L strength. |
| Reflective Defenses | 5 | shields | 2 | 0+18 | On block: chance 0.15+0.1(L−1) to reflect 0.15×L of the blocked damage as magic damage. |
| Scavenger (Masterwork) | 3 | swords / sharp weapons | 1 | 55+50 | 2.5%×L chance to roll the victim's loot table again. Stacks with Looting and works on bosses. |
| Shield Bash | 4 | shields | 2 | 1+17 | +3.5×L melee damage when attacking with the shield. Costs `clamp(20 − 2(L−1), 1, 1024)` durability per hit. Exclusive set: damage. |
| Stable Footing | 1 | boots | 2 | 40 | Removes the airborne (and, with Aqua Affinity, underwater) break-speed penalty. |
| Temptation | 1 | hoes | 5 | 0 | Holding the hoe makes animals follow as if you held their food (`TemptGoalMixin`). |
| Worker Exploitation (Twisted) | 1 | shears | 2 | 30 | Shearing damages the sheep but doubles wool (after Fortune). With Chromatic, the doubled wool is recoloured. |

- `non_treasure` tag = all 18. Curses = Berserker's Fury and Life-Mending.
- The lang also contains **Mounted Strike, Bane of Illagers, Occult Aversion** (magic protection). These have **no JSON in 1.6.2** (not implemented).
- The warden drops a **Warden Tendril** via loot modifier `apothic_enchanting:warden_tendril`: chance 0.1 + 0.1 per Looting level (bytecode constants 0.1/0.1).
- **Depends on:** AE-9, AA-8 (cooldowns) · **Size:** L (18 small effects) · **Elysium:** PARTIAL (`ElysiumAreaBreak` tree-fell ≈ Chainsaw).

### AE-8: Changes to vanilla enchantments and enchantability
- Protection is no longer exclusive with the specialized protections, and Sharpness is no longer exclusive with other damage enchantments. The exclusive-set tags `armor` and `damage` are emptied. Replacement JSON for `minecraft:protection` (1 + 1/level points; max 4) and `minecraft:sharpness` (1 + 0.5/level; max 5).
- Protection points: 2.5% each, cap 85% (AA-2). Specialized protections give 2 points per level; Feather Falling gives 3.
- Tridents can take Sharpness, Looting and Piercing (Piercing makes thrown tridents pass through enemies). Shears can take Unbreaking, Efficiency and Fortune (BOOK `changes/*`).
- The default item enchantability is **1 instead of 0** (items with an explicit 0 stay unenchantable) (`ItemMixin.getEnchantmentValue`).
- **Depends on:** AA-2 · **Size:** S · **Elysium:** NONE.

### AE-9: Enchantment level caps and power functions (per-enchantment config)
- Config `enchantments.cfg` per enchantment (`EnchantmentInfo`):
  - `Max Level` (1–127, default = vanilla max per the description "originally N").
  - `Max Loot Level`.
  - `Forced Level Cap` (−1 disables; clamps the effective level regardless of NBT, `GetEnchantmentLevelEvent`).
  - `Max Power Function` and `Min Power Function` (EvalEx, variable `x`).
- **Defaults [web: PowerFunction]:** max power = **200** for all levels. Min power = vanilla `getMinCost(L)` for L ≤ vanilla max. Above that: `minCost(L) + diff × (L − max)^1.6`, where `diff = minCost(max) − minCost(max−1)` (15 if 0).
- Levels above the vanilla max get a distinct name colour (`EnchantmentMixin`). IMC `set_ench_hard_cap`. A coremod redirects `Enchantment#getMaxLevel` in loot functions to the configured values.
- **Depends on:** none · **Size:** M · **Elysium:** NONE.

### AE-10: Anvil changes, tomes and utility items
- **Anvil:** the "Too Expensive" level cap is removed (`apoth_removeLevelCap`), and XP is charged as optimal points counted from level 0 (BOOK).
- **Prismatic Cobweb** (cobweb + 4 prismarine shards): combine in an anvil to remove all curses.
- **Typed tomes** (enchant like books but only accept matching enchantments; they turn into an enchanted book once enchanted). Crafted from books + blaze rod: Other ×6, Helmet ×5, Chestplate ×8, Leggings ×7, Boots ×4, Weapon ×2, Pickaxe ×3, Fishing ×2, Bow ×3.
- **Scrapping tomes:**
  - Tome of Scrapping (8 books + anvil → 8): anvil with an item destroys the item and makes a book with **half** its enchantments (rounded up), chosen by the enchanting seed.
  - Superior Scrapping: all enchantments, item destroyed.
  - Tome of Extraction: all enchantments, **item kept**.
- **Other recipes:** cobweb (8 string + honeycomb), pufferfish (fish + 4 bamboo).
- **Depends on:** AE-2 (seed), AE-5 · **Size:** M · **Elysium:** NONE.

### AE-11: Items and blocks
- **Apothic Enchanting Table:** a cosmetic table (tome + 2 diamonds + obsidian).
- **Enchanting Table of the Raven:** lets the player **manually set Eterna, Quanta and Arcana** (each 0–100, `RavenTableStats`, `SetRavenStatsPayload`). Recipe in Apotheosis (AP-30).
- **Ender Leads:**
  - Flimsy Ender Lead (pearl + lead + gold) captures and moves animals.
  - Ender Lead (infusion) captures most entities.
  - Occult Ender Lead (from an Ender Lead holding a witch) can **bind the entity to a Mob Spawner** (interaction with AS).
- **Materials:** Warden Tendril (AE-7), Infused Dragon's Breath, Inert Trident (3 nautilus shells + heart of the sea + iron) → Trident.
- **Music discs:** Eterna, Quanta, Arcana (creeper-drop tag + infusion).
- **Depends on:** AE-5 · **Size:** M · **Elysium:** NONE.

### AE-12: `max_eterna` attribute
- `apothic_enchanting:max_eterna`: default 100, range 0–100. The effective table Eterna is `min(table, attribute)`. Apotheosis tier augments lower it (AP-3).
- **Depends on:** AA-1 · **Size:** S · **Elysium:** NONE.

### AE-13: Client and config odds and ends
- Enchanted book metadata tooltip (treasure/tradeable/discoverable/lootable) is on by default. Inline enchantment descriptions are off by default. Sculkshelf noise chance is 1/200 per tick.
- JEI infusion category, Jade plugin, advancement trigger `EnchantedTrigger` (23 advancements).
- **Size:** S · **Elysium:** NONE.

---

# 5. Apothic Spawners 1.4.0 (AS)

### AS-1: Spawner block replacement, stats and silk-touch pickup
- **What it is:** The vanilla spawner is replaced (`ApothSpawnerBlock`/`Tile`, `BlocksMixin`/`ItemsMixin`) with a spawner that has **12 stats** (lang `stat.apothic_spawners.*`).
  - Numeric: `min_delay`, `max_delay`, `spawn_count`, `max_nearby_entities`, `req_player_range`, `spawn_range`, `initial_health` (%), `echoing` (level).
  - Boolean: `ignore_players`, `ignore_conditions`, `redstone_control`, `ignore_light`, `no_ai`, `silent`, `youthful`, `burning`.
  - Vanilla defaults: min 200, max 800, count 4, nearby 6, player range 16, spawn range 4 (vanilla values).
  - If max delay < min delay, min is used. A spawn attempt succeeds if at least one entity spawns. Only same-type entities count toward max nearby (BOOK `spawner/stats`).
- **Silk touch pickup:** config `Spawner Silk Level` default **1** (−1 disables, 0 = always drops; range −1–127). `Spawner Silk Damage` **100** extra durability. `Spawners Drop Empty` false. The item keeps its entity and stats ("%s Spawner").
- **Depends on:** none · **Size:** L · **Elysium:** NONE.

### AS-2: Spawner modifiers (item-in-hand recipes)
- **What it is:** Hold item(s) and right-click a spawner. Recipe type `apothic_spawners:spawner_modifier {mainhand, offhand?, consumes_offhand (default true), stat_changes:[{type, value, mode: add|set (default add), min?, max?}]}`. The mainhand item is always consumed, and the hands can be swapped (BOOK).
- **Default content** (`AS:recipe/spawner_modifiers/`). *Overridden and disabled by Apotheosis* (AP-29):

| Item | Change | Cap | Inverse (+ quartz off-hand, not consumed) |
|---|---|---|---|
| Sugar | min_delay −10 | ≥20 | +10 (≤1600) |
| Clock | max_delay −20 | ≥20 | +20 (≤1600) |
| Fermented spider eye | spawn_count +2 | ≤16 | −2 (≥1) |
| Ghast tear | max_nearby +2 | ≤32 | −2 (≥1) |
| Prismarine crystals | player range +4 | ≤48 | −4 (≥1) |
| Piston | spawn_range +2 | ≤32 | −2 (≥1) |
| Pointed dripstone | initial_health −0.05 | ≥0.20 | +0.05 (≤1) |
| Echo shard | echoing +1 | ≤3 | −1 (≥0) |
| Nether star | ignore_players | – | off |
| Conduit | ignore_conditions | – | off |
| Comparator | redstone_control | – | off |
| Soul lantern | ignore_light | – | off |
| Chorus fruit | no_ai | – | off |
| Wool (tag) | silent | – | off |
| Turtle egg | youthful | – | off |
| Campfire | burning | – | off |

- Advancements: spawn count 16, max delay 20, min delay 20, nearby 32, range 48, spawn range 32, conditions, players, redstone, light, no AI, and "Ultimate Spawner".
- **Depends on:** AS-1 · **Size:** M · **Elysium:** NONE.

### AS-3: Capturing enchantment
- `apothic_spawners:capturing`: max level 3, sword primary / sharp weapons, weight 2, min cost 15+10/level, anvil cost 1.
- Each level gives a **0.5% chance** (config `Capturing Drop Chance` 0.005, 0.001–1) for a killed mob to drop its spawn egg. The mob must have an egg. Spawn eggs retarget spawners, except for entities in tag `apothic_spawners:blacklisted_from_spawners` (warden, elder guardian, `#c:bosses`).
- **Depends on:** AS-1 · **Size:** S · **Elysium:** NONE.

### AS-4: Spawned-mob behaviours
- **No AI:** mobs are still pushable and cannot teleport (`tickDumbMobs`, `dumbMobsCantTeleport`).
- **Despawn protection:** spawned mobs cannot despawn for `Entity Despawn Delay` **600 t** (0–24000).
- **Echoing:** the mob drops its loot table N extra times and XP is multiplied.
- **Initial health:** mobs spawn at that % of max HP.
- **Burning:** set on fire. **Youthful:** baby. **Silent:** no sound.
- Slimes and similar that split keep the spawner flags (`MobSplitEvent`).
- **Depends on:** AS-1 · **Size:** M · **Elysium:** NONE.

### AS-5: Unstable spawner loot
- An exploded spawner rolls `apothic_spawners:gameplay/unstable_spawner`: 48–64 chains by default, replaced by **Spawner Chains** in Apotheosis.
- **Size:** S · **Elysium:** NONE.

### AS-6: JEI, EMI and Jade integration
- A "Spawner Modification" category, tooltips (hold Shift/Ctrl for stats), and Jade spawner info. **Size:** S · **Elysium:** NONE.

---
# 6. Suggested build order (dependencies first)

Order: foundations → item-data systems → acquisition loops → content → world and mobs → enchanting → spawners → polish. Items marked HAVE/PARTIAL in Elysium should be **extended** rather than rebuilt.

1. **AA-1** Custom attributes. Everything else reads them. Map them onto `ElysiumStats` where they overlap (crit, dodge, lifesteal).
2. **AA-10** Damage types and tags. Needed by aux damage, execute and psychic.
3. **AA-2** Armour/protection formula. It changes all combat balance, so do it before tuning numbers.
4. **AA-3, AA-4, AA-5, AA-6, AA-7, AA-8** Combat and utility attribute hooks. Small, independent, and they make stat affixes meaningful.
5. **AA-9** Effects, potions, brewing (**PL-4**). Required by mob-effect affixes and gems (bleeding, sundering, vitality…).
6. **AA-12, AA-11** Stack modifier API and attribute GUI. The API is how affixes and gems add modifiers; the GUI is for debugging everything after.
7. **AP-2** Tiered weights + GenContext. The single weighting primitive for every later roll.
8. **AP-1 + AP-3** World tiers + tier augments. Gate everything by tier early, even if content arrives later.
9. **AP-4** Loot categories. Needed to know which affixes and gems fit an item. Extend `ElysiumGear`.
10. **AP-5** Rarities + rule engine. Extend `ElysiumRarities` with rule lists.
11. **AP-6** Affix framework (component, levels 0–2, hooks, cooldowns, naming). Extend `ElysiumAffix`.
12. **AP-7** Affix catalogue: the 42 stat affixes first, then the 26 mob-effect affixes, then the special ones (radial, cleaving, execute…).
13. **AP-8** Affix loot generation + loot modifiers + 12% mob augment. The first time players see the system.
14. **AP-19 + AP-15** Rarity materials + Salvaging Table. Closes the loop: loot → materials.
15. **AP-16** Reforging tables. The first material sink. Merge with `ElysiumReforgeHandler`.
16. **AP-9** Gem framework + 23 gems. Map onto `ElysiumRune`; add gem classes, uniqueness and purity.
17. **AP-11** Socketing / withdrawal / sigil sockets. Extend `ElysiumSockets`.
18. **AP-10 + AP-12** Gem drops, purity weights, gem dust.
19. **AP-13 → AP-14** Gem cutting, then gem case.
20. **AP-17 + AP-18** Augmenting table, Malice and Supremacy sigils. Endgame item tuning; needs a boss loot source for Malice.
21. **PL-1 / AP-22** Gear sets. Needed before any buffed mob.
22. **AP-21** Elites + mob augments. Simpler than invaders; reuses stats/gear/affix-item application.
23. **AP-20** Invaders. The spawn hook, announcements, cooldown, and boss affix drop. Unlocks the Ascent requirement.
24. **AP-23, AP-24, AP-25** World structures (invader dungeons, rogue spawners, tome towers). Rogue spawners need AS-1 stats.
25. **AE-12 → AE-1 → AE-3 → AE-2 → AE-4** Enchanting stats, shelves, algorithm, clues. Independent of Adventure except the max_eterna tier link.
26. **AE-9 → AE-8 → AE-7** Level caps and power functions, vanilla changes, then the 18 enchantments.
27. **AE-5** Infusion. Unlocks shelf tiers, the library, the Ender Gem Case, the unbreakable charm and the infused rune.
28. **AE-6, AE-10, AE-11, AE-13** Library, anvil and tomes, extra items, polish.
29. **AS-1 → AS-2 → AS-4 → AS-3 → AS-5** Spawner stats, modifiers, behaviours, capturing, chains.
30. **AP-29** Spawner runes (Apotheosis' replacement modifier set; tier runes need world tiers + AE infusion).
31. **AP-28, AP-27, AP-30** Potion charms, wandering trader, enchanting cross-links.
32. **AP-26** Gateways (only if you adopt or rebuild a wave-portal system; it depends on nearly everything above).
33. **AP-31, AP-32, AP-33, AP-34, AE-13, AS-6, PL-5** QoL, compat and cosmetics.

# 7. Could not be determined from the jar data alone

Items taken from the web (GitHub `Shadows-of-Fire/*` 1.21 sources via summarised fetches) or not confirmable:
- **Tiered weight formula** `weight + round(luck × quality)`, and missing tier = 0 (web `TieredWeights`).
- **Rule execution details:** affix level = `nextFloat()`, socket rule only increases, durability = lerp (web `LootRule`). The name-building shuffle is from web `LootController`.
- **Reforging:** seed `playerSeed ^ itemIdHash + slot`, cost ×(slot+1), seed re-randomised after use (web `ReforgingMenu`). The ×1/×2/×3 multiplier is consistent with the BOOK.
- **Augmenting** +0.25 capped at 1.0, reroll rules (web `AugmentingMenu`). The cost numbers are from bytecode config defaults.
- **Malice** (+2.0 to one affix, reset another to 0) and **Supremacy** (+1.5 to all). Web summary plus bytecode constants `fconst_2`/`fconst_0`. The exact merge/cap semantics of `ItemAffixes.Builder.upgrade` (sum clamped to [0, 2]) is inferred.
- **Invader spawn pipeline:** event, cooldown sharing, surface check (web `ApothMobEvents`). The chance numbers themselves are from jar data.
- **Enchanting algorithm:** slot level multipliers (0.2–0.4 / 0.6–0.8 / 1.0), gaussian/3 quanta distribution, guaranteed-enchant thresholds (0/33/66/99 in source vs 25%/75% in the BOOK, **conflict**), extra-enchant loop `max(50, 1.15L)`, and the Arcana weight table. All web (`ApothEnchantmentHelper`, `Arcana`).
- **Eterna gathering** (group-by-max ascending, base Quanta 15, Clues 1, clamps 0–100) from web `EnchantmentTableStats`. The ×2 for non-registry blocks and the code-defined shelf values are bytecode-confirmed.
- **Library point math** `2^(L−1)` and capacities 32,768 / 2^30 (web `EnchLibraryTile`). Levels 16/31 are confirmed by the BOOK.
- **Default power functions** (max 200; min extrapolation `diff × (L−max)^1.6`) and "default max level = vanilla" (web `PowerFunction`, `EnchantmentInfo`). The shipped `enchantments.cfg` is generated at runtime and is not in the jar, so **per-enchantment default max levels beyond vanilla could not be read**.
- **Attribute event formulas:** crit loop details, life steal/overheal cap 50%, dodge seed, draw-speed fractional-tick algorithm (web `AttributeEvents`). The cold-damage slow formula and the fire tick ×10 are bytecode-confirmed. The aux-damage attack-strength threshold (0.55) is seen in bytecode, but its exact semantics are inferred.
- **Detonation** and **Bleeding** formulas (web). Bleeding's 40-tick interval is bytecode-confirmed.
- **Affix specials:** the thunderstruck/cleave radius (6) and attack-strength gate (0.98), retreating velocities, and the catalyzing Strength amplifier are read from bytecode constants without full control flow. Treat the exact use as approximate.
- **Loot-modifier pattern precedence** (first match vs cumulative) for `affix_loot_injection` / `gem_loot_injection` is not visible in data; "first match" is assumed.
- **Gem cutting interpretation:** "2 gems + dust + ONE of the material options" is inferred from recipe shape, the Gem Case button text and the BOOK.
- **Book vs data conflicts (data used):** Rare affix counts (book 3 stats vs data 2), Epic (book "may receive 4th stat" vs data exactly 3 + ability), Mythic sockets (book "three sockets" vs data 1–3 with a 5% chance of 4).
- **Legacy lang-only content** with no data or registration in these versions: affixes Satanic, Annihilating, Decimating, Nullifying, Spellbreaking, Dwarven, Detonating; Vials of Extraction/Expulsion/Unnaming; Manifestation of Infinity; Apotheosis Ender Lead and Lucky Foot; enchantments Mounted Strike, Bane of Illagers, Occult Aversion; rarity "Ancient".
- **External mod:** the Gateways to Eternity engine (waves, portal entity, rules) is not in these jars. Only Apotheosis' content for it was inventoried.
