# Silent Gear 4.2.1.1 (MC 1.21.1 NeoForge) — Feature Inventory for re-implementation in Elysium

Source jar: `/tmp/ref/silent-gear-1.21.1-neoforge-4.2.1.1/` (library: Silent Lib 10.6.0).
Everything below comes from the jar unless it is marked **[web]** or **[inferred]**. "Bytecode" means `javap -c` output for the named class, under `net/silentchaos512/gear/`. Data paths are relative to `data/silentgear/`.

**Important finding on the in-game guide:** the Patchouli book is **not shipped** in 4.2.1.1. `patchouli_books/gears_guide/en_us/` holds only `templates/compounding_recipe.json`. The `guide_book` item only prints a link to the wiki (`item.silentgear.guide_book.unimplemented1/2`, `check_wiki`). Mechanics were therefore reconstructed from bytecode, which is the primary source here.

**Key version facts (4.x differs from older Silent Gear):**
- **Material tiers are gone.** A material has no `tier` field. Progression comes from the `harvest_tier` property (name plus `level_hint` 0–4, backed by `incorrect_for_*` block tags) and from `rarity`.
- **Stats are now called "properties"** (`GearProperty` registry). Properties use 5 operations: AVERAGE, MAX, ADD, MULTIPLY_BASE, MULTIPLY_TOTAL. The JSON aliases are avg, max, add, mul1 and mul2.
- **Synergy applies only inside compound materials (alloys).** It does **not** apply to a gear part that mixes several materials. Mixing materials in a part is a pure weighted average (SG-3).
- Grades (E→MAX) and Starcharged levels are **material modifiers**: data components on a material item. They change that material's property values.

---

## Contents / feature count per system

| System | IDs | Count |
|---|---|---|
| A. Property (stat) engine | SG-1 … SG-8 | 8 |
| B. Materials | SG-10 … SG-17 | 8 |
| C. Parts | SG-20 … SG-25 | 6 |
| D. Gear types & gear items | SG-30 … SG-44 | 15 |
| E. Blueprints & templates | SG-50 … SG-52 | 3 |
| F. Traits | SG-60 … SG-64 | 5 |
| G. Durability, breaking, repair, part editing | SG-70 … SG-74 | 5 |
| H. Crafting stations & processing | SG-80 … SG-88 | 9 |
| I. World content, resources, loot | SG-90 … SG-97 | 8 |
| J. UX, config, commands, compat | SG-100 … SG-109 | 10 |
| **Total** | | **77** |

Size scale: S ≈ < 1 day, M ≈ 1–3 days, L ≈ 1–2 weeks, XL ≈ > 2 weeks (for one dev who knows NeoForge).

Elysium overlap: I checked `/tmp/Elysium-mono` read-only. What Elysium already has:
- `ElysiumGearMaterial`: one material per item. Fields: tool `Tier`, flat damage bonus, `ArmourProfile` (boots/legs/chest/helm armour, enchantability, toughness, knockback resistance, durability multiplier), Elysium tier and element.
- `ElysiumMaterials`: Voidglass, Aetherium, Neutronium, the vanilla metals, plus a table of about 15 modded metals.
- `ElysiumMaterialGear`: nine fixed shapes (sword, pickaxe, axe, shovel, hoe, hammer 3×3, broadaxe, scythe 3×3 crops, spear 3×3 dig) plus material armour.
- Sockets and runes (`ElysiumSocketable`), reforging (`ElysiumReforgeHandler/Item`), ascension (`ElysiumGearAscension`).
- `ElysiumQuality`: CRUDE 0.8×, PLAIN 1.0×, SOUND 1.15×, FINE 1.3× and MASTERWORK 1.5× durability, by practice count. `ElysiumMakersMark` and `ElysiumCraftLog` go with it.
- Silent Gear compat: `compat/SilentGearBridge.java` and `GearConvergence.java` do a lossless Elysium↔Silent Gear item swap. `silentgear/ElysiumSilentGear.java` is a no-op detector. `data/elysium/silentgear_materials/{aetherium,neutronium}.json` are Silent Gear material JSONs, `main` part only, with no traits.
- There is **no** modular part system, multi-material averaging, trait engine, synergy, alloying, grader, charger, salvager, repair kit or blueprint in Elysium. `ElysiumStations.SALVAGER` in elysium-npcs is only a villager workstation.

---

# A. Property (stat) engine

### SG-1 Property registry (all stats)
- **What:** a registry `silentgear:gear_property` of typed properties. Every material, part and trait contributes modifiers to these.
- **How:** `setup/gear/GearProperties` bytecode. `GearProperty.Builder(default, base, min, max)` supplies `defaultValue, baseValue, minimumValue, maxValue`. Final values are clamped to [min, max]. "Grades" means the value is affected by material grade (SG-14). "Synergy" means it is affected by compound synergy (SG-15). MAXF = 2.1474836E9.

| property id | kind | default op | display | int? | default | base | min | max | group | grades | synergy |
|---|---|---|---|---|---|---|---|---|---|---|---|
| additive | boolean (materials only) | – | – | – | false | false | – | – | SPECIAL | – | – |
| traits | trait list | – | – | – | [] | [] | – | – | SPECIAL | – | – |
| durability | number | AVG | UNIT (shows cur/max) | yes | 0 | 0 | 0 | MAXF | GENERAL | yes | yes |
| armor_durability | number | AVG | MULTIPLIER | yes | 0 | 0 | 0 | 1.3421773E8 | GENERAL | yes | yes |
| repair_efficiency | number | AVG | PERCENT | no | 1 | 0 | 0 | 1000 | GENERAL | no | no |
| repair_value ("Repair Bonus") | number | AVG | PERCENT | no | 1 | 0 | 0 | 1000 | GENERAL | no | no |
| enchantment_value | number | AVG | UNIT | yes | 0 | 0 | 0 | MAXF | GENERAL | yes | yes |
| charging_value | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | GENERAL | no | no |
| rarity | number | AVG | UNIT | yes | 0 | 0 | 0 | MAXF | GENERAL | no | no |
| harvest_tier | HarvestTier {name, level_hint, incorrect_blocks_for_tool} | best-of | – | – | ZERO | – | – | – | HARVEST | no | no |
| harvest_speed | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | HARVEST | yes | yes |
| block_reach | number | AVG | UNIT | no | 0 | 0 | −100 | 100 | HARVEST | no | no |
| attack_damage | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | ATTACK | yes | yes |
| attack_speed | number | AVG | UNIT | no | 0 | 0 | −3.9 | 4.0 | ATTACK | no | no |
| attack_reach | number | AVG | UNIT | no | 0 | 0 | −100 | 100 | ATTACK | no | no |
| magic_damage | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | ATTACK | yes | yes |
| ranged_damage | number | AVG | MULTIPLIER | no | 0 | 0 | 0 | MAXF | PROJECTILE | yes | yes |
| draw_speed | number | AVG | MULTIPLIER | no | 0 | 0 | −10 | 10 | PROJECTILE | no | no |
| projectile_speed | number | AVG | MULTIPLIER | no | 1 | 0 | 0 | MAXF | PROJECTILE | no | yes |
| projectile_accuracy | number | AVG | PERCENT | no | 1 | 0 | 0 | 10000 | PROJECTILE | no | no |
| armor | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | ARMOR | yes | yes |
| armor_toughness | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | ARMOR | yes | yes |
| knockback_resistance | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | ARMOR | yes | yes |
| magic_armor | number | AVG | UNIT | no | 0 | 0 | 0 | MAXF | ARMOR | yes | yes |

- Groups: SPECIAL, GENERAL, HARVEST, ATTACK, PROJECTILE, ARMOR. A gear type lists its *relevant groups* (SG-30), and only properties in those groups are computed for it.
- **Data-driven:** no; the properties are code-registered. Values come from JSON.
- **Player interaction:** tooltip "Properties" section (hold the key, SG-100).
- **Depends on:** none. **Size:** M. **Elysium:** PARTIAL. It has fixed per-material numbers (damage bonus, ArmourProfile, Tier), but no generic property registry.

### SG-2 Modifier operations and the compute formula
- **What:** how a list of modifiers for one property becomes a number.
- **How** (`api/property/NumberProperty.compute`, bytecode verified):
  1. If there are no modifiers, return `base`.
  2. `f = base + WeightedAvg(AVG mods)`.
  3. `f = max(f, every MAX mod value)`.
  4. `r = f + Σ(f × v)` over MULTIPLY_BASE mods. This is additive percentages of `f`.
  5. `r = r × Π(1 + v)` over MULTIPLY_TOTAL mods.
  6. `r = r + Σ v` over ADD mods.
  7. If clamping is on, `r = clamp(r, min, max)`.
- **Weighted average:** `primary` = the value of the *first* AVG modifier (if ≤ 0, use 1). `weight_i = 1 + v_i / (1 + |primary|)`. `avg = Σ(v_i·w_i) / Σ w_i`. When Σw = 0, the result is Σ(v·w).
  - Higher values get more weight. Example: iron 250 plus diamond 1561, primary 250. The weights are 1.996 and 7.22, so the average is **≈1277**, not 905.
- **Compress** (`NumberProperty.compressModifiers`) runs when one part (or compound) is collapsed to a single modifier list. For each operation separately: several MAX mods become the max. Several of any other op (AVG **and** ADD / MUL1 / MUL2) become their *weighted average* by the same formula, keeping the op.
- **JSON:** a bare number means AVG. Otherwise `{"operation": "ADD"|"MULTIPLY_BASE"|"MULTIPLY_TOTAL"|"MAX"|"AVERAGE", "value": x}`, or a list of such objects (e.g. `dagger_blade.attack_damage`). The aliases `avg`, `max`, `add`, `mul1` and `mul2` are also accepted [web: NumberProperty source].
- **Tooltip:** MUL ops show 2 decimals; int properties show 0.
- **Depends on:** SG-1. **Size:** S. **Elysium:** NONE.

### SG-3 Property pipeline: material → part → gear → trait bonus → final
- **What:** the order in which gear properties are computed on every recalculation. That is on craft, on part change, and when the damage "factor level" changes.
- **How** (`util/GearData.tryRecalculateGearData`, [web: 1.21.x GearData source; the call graph matches the 4.2.1.1 bytecode]):
  1. **Material level.** `MaterialInstance.getPropertyModifiers(partType, key)` returns the material JSON values for that part type. The value for the most specific key wins (e.g. `armor/helmet` over `armor`). Each *material modifier* (grade, starcharge, crude) is then applied (SG-14).
  2. **Part level.** `CoreGearPart.getPropertyModifiers` builds the list: all materials' mods, plus the part's own `properties` (e.g. `pickaxe_head: attack_speed ADD 1.2`). It then runs `reduce()` and fires a `GetPropertyModifiersEvent`. `compressModifiers` (SG-2) collapses the list to one modifier per operation. **No synergy here.**
  3. **Gear base.** `PartList.getPropertyModifiersFromParts` gathers every part's compressed mods for each property. `compute` (SG-2) runs with `base = property.baseValue`. So AVGs are weighted-averaged *across parts* and ADDs are summed across parts.
  4. **Bonus.** For each valid trait: `trait.getBonusProperties(level, player, property, baseValue, damageRatio)`. Here `damageRatio = clamp(damage / baseDurability, 0, 1)`. `number_property_modifier` effects produce `ADD` mods (SG-61). A per-property config multiplier (`Config.Common.getPropertyBonusMultiplier`) may also add a mod.
  5. **Final.** `compute(base-value-as-AVG + bonus mods)` with clamping. The result is stored in the `silentgear:gear_properties` data component.
- **Worked check:** iron pickaxe = pickaxe_head (3× iron) + wood rod.
  - Durability 250.
  - Attack damage `2 + 1(ADD) = 3`. With the player's base 1 that is 4, the same as vanilla.
  - Attack speed `0 + 1.2 = 1.2`. The attribute is `1.2 − 4 = −2.8`, the same as vanilla.
  - Harvest speed 6.
- **Depends on:** SG-1, SG-2, SG-10, SG-20, SG-60. **Size:** L. **Elysium:** NONE. Elysium stats are static per item class.

### SG-4 Gear-type-specific property keys
- **What:** a material can give different values per gear type, e.g. `"armor/helmet": 2`, `"attack_speed/axe": -0.1`, `"attack_speed/hoe": -2`.
- **How:** `PropertyKey(property, gearType)`. `GearPropertyMap.getMostSpecificKey` walks up the gear-type parents (SG-30), so `armor/helmet` beats `armor` on a helmet. Armour pieces use `armor/<slot>`. The plain `armor` value is the full-set total that the material book shows.
- **Keys in the shipped data:** `armor/{helmet,chestplate,leggings,boots}`, `attack_speed/axe`, `attack_speed/hoe`.
- **Depends on:** SG-1, SG-30. **Size:** S. **Elysium:** PARTIAL (ArmourProfile gives per-slot armour).

### SG-5 Harvest tiers (replaces material tiers)
- **What:** the mining level. It is the best tier among all modifiers (`HarvestTierProperty.compute` uses `isBetterThan`, comparing by level).
- **Data:** `{"name", "level_hint", "incorrect_blocks_for_tool"}`. 31 `tags/block/incorrect_for_<x>_tools.json` alias the vanilla tags. `needs_copper_tool` = gold block, raw gold block, gold and deepslate gold ore, redstone and deepslate redstone ore, and `#c:ores/silver` (optional).

| level_hint | tiers (name → vanilla tag it aliases) |
|---|---|
| 0 | wood, bamboo, netherwood → incorrect_for_wooden; gold (vanilla gold tag) |
| 1 | stone, flint, bone, basalt, blackstone, end_stone, netherrack, obsidian, sandstone, terracotta, gold(sg) → incorrect_for_stone |
| 1.5 | copper, amethyst, lapis_lazuli → incorrect_for_stone, plus they satisfy `needs_copper_tool` [inferred] |
| 2 | iron, bronze, blaze_gold, quartz, emerald, high_carbon_steel → incorrect_for_iron |
| 3 | diamond, crimson_iron, azure_silver, dimerald → incorrect_for_diamond |
| 4 | netherite, crimson_steel, azure_electrum, tyrian_steel → incorrect_for_netherite |

- **Depends on:** SG-1. **Size:** S. **Elysium:** HAVE. `ElysiumGearMaterial.toolTier` is a vanilla `Tier`, but there is no custom tier ladder.

### SG-6 Property → vanilla behaviour mapping
Bytecode `util/GearHelper`, `item/gear/*`, `api/item/*`:
- **Attack damage attribute** = `attack_damage` (0 if negative; 1 if broken). This is added to the player's base 1.
- **Attack speed attribute** = `attack_speed − 4`. It is `+0.7` more if broken. Only GearTool items get it.
- **Reach:** `block_reach` adds to BLOCK_INTERACTION_RANGE and `attack_reach` adds to ENTITY_INTERACTION_RANGE (both ADD_VALUE, main hand).
- **Mining speed:** `harvest_speed × (1 + Σ trait mining-speed mods)` when the tool is correct for the block, otherwise 1. If broken, 0.25.
- **Tool durability** = `durability`. **Armour durability** = `int(armor_durability) × int(armorDurabilityMultiplier)`. The multipliers are helmet 11, chest 16, legs 15, boots 13, elytra 25, shield 22.466667.
- **Armour** = `armor` (per-slot key, 0 if broken). **Toughness attribute** = `armor_toughness / 4`. **Knockback resistance** = `knockback_resistance / 10` (only if > 0).
- **Magic armour:** worn pieces' `magic_armor` are summed to m. MAGIC damage is scaled by `1 − s`, where `s = 0.03·m` if m ≤ 20, else `0.6 + 0.015·(m − 20)` (`GearEvents.getReducedMagicDamageScale`).
- **Magic damage:** `GearHelper.getMagicDamageModifier` supplies bonus magic damage on hit [inferred usage].
- **Enchantability** = `int(enchantment_value)`, or 0 if config `allow_enchanting = false`.
- **Rarity** (`GearHelper.getRarity`): `r = int(rarity)`. Add 20 if enchanted (80 with vanilla-style tooltips). `<40` COMMON, `<80` UNCOMMON, `<120` RARE, otherwise EPIC.
- **Bow:**
  - Draw delay = `20 / draw_speed` ticks, with draw_speed ≤ 0 treated as 1, clamped to [10, 100].
  - Arrow velocity: `t = useTicks / drawDelay`, `v = (t² + 2t) / 3`, capped at 1.
  - `ranged_damage` multiplies arrow damage.
- **Crossbow:** charge time = `round(clamp(25 / draw_speed, 5, 50))` ticks.
- **Trident:** throw after 10 use ticks; projectile damage uses a base of 4.
- **Dagger:** on hit, the target's invulnerableTime is multiplied by 0.67.
- **Tools:** 1 durability per block. `GearTool.getDamageOnHitEntity` = 2, the vanilla convention [inferred].
- **Depends on:** SG-1, SG-3. **Size:** M. **Elysium:** PARTIAL (vanilla item classes do this for fixed values).

### SG-7 Damage-factor recalculation
- Gear recalculates properties whenever `damage / max(1, maxDamage / levels)` changes, with `damage_factor_levels` = 10 by default (`GearHelper.getDamageFactor`). This is what lets "as damaged" traits (Sharp, Eroded, Chipping…) scale in 10 steps.
- **Depends on:** SG-3. **Size:** S. **Elysium:** NONE.

### SG-8 Stat locking and debugging
- `/sgear lock_stats` freezes recalculation (`misc.silentgear.lockedStats`). `/sgear properties recalculate` and `info` exist. Config `debug.logging.properties` logs every recalculation.
- **Size:** S. **Elysium:** NONE.

---

# B. Material system

### SG-10 Material JSON format and material types
- **Path:** `data/<ns>/silentgear_materials/<id>.json`; subfolders give ids like `wool/red`.
- **Fields:**
  - `type`: `silentgear:simple` | `compound` | `custom_compound` | `processed`.
  - `parent`: a material id. The child inherits every property it does not define; e.g. `wood/oak` only changes ingredient, colour and name.
  - `crafting`: `can_salvage`, `categories[]`, `gear_type_blacklist[]`, `ingredient` (item / tag / `silentgear:custom_compound` ingredient), `part_substitutes {partType: ingredient}`.
  - `display`: `color` ARGB, `main_texture_type` HIGH_CONTRAST | LOW_CONTRAST, `name`, `name_prefix`.
  - `properties`: `{ "<partType>": { "<property>[/<gearType>]": value|modifier, "harvest_tier": {...}, "traits": [ {trait, level, conditions[]} ] } }`.
- **Types:**
  - `simple`: a normal material.
  - `compound`: the value comes from its sub-materials (alloy items). **No properties in its JSON** (SG-15).
  - `custom_compound`: a named alloy with fixed properties, made in an alloy maker. Examples are `high_carbon_steel` and `dimerald`, whose ingredient is a `silentgear:custom_ingot` / `custom_gem` carrying the `material` id.
  - `processed`: wraps another material and applies modifiers. Example: `sheet_metal`, where main armour, armour_durability and toughness are MULTIPLY_TOTAL −0.5/−0.4/−0.5 and durability is MULTIPLY_TOTAL −1.0 (i.e. 0).
- **Counts:** 132 JSON files: 79 simple roots, 45 simple children (16 wool, 16 terracotta, 9 wood, 3 stone, 1 sandstone), 5 compound, 2 custom_compound, 1 processed. `example` is intangible and blacklisted from all gear.
- **Part types used in material properties:** main 72, rod 60, tip 21, setting 9, binding 7, cord 7, lining 6, grip 5, coating 5, fletching 4.
- **Depends on:** SG-1. **Size:** L. **Elysium:** PARTIAL. `ElysiumGearMaterial` is Java-built; Elysium already writes two SG-format JSONs.

### SG-11 Material categories
- **15 categories** with usage counts: metal 42, basic 27, intermediate 25, organic 22, advanced 16, rock 13, endgame 9, gem 8, fiber 5, cloth 4, wood 4, dust 3, sheet 2, intangible 2, slime 1.
- **Used by:**
  - Synergy (SG-15).
  - Recipe material filters, e.g. rings/bracelets/necklaces need `metal`; elytra wings need `cloth`/`sheet`.
  - Alloy-maker acceptance (SG-80).
  - The metal press (`metal` only).
- Compound materials take the union of their sub-materials' categories (`CompoundMaterial.getCategories`).
- **Size:** S. **Elysium:** NONE (Elysium uses "element" instead).

### SG-12 Material list (all 132 files)
See **Appendix M1** for id, name, type, parent, categories, ingredient and rod substitute; **M2** for main-part stats and traits; **M3** for the other part types.
- **Special materials:**
  - Crimson iron / crimson steel: Nether ore and alloy.
  - Azure silver / azure electrum: End ore and alloy.
  - Blaze gold.
  - Tyrian steel: end-game alloy.
  - Dimerald: diamond + emerald gem alloy.
  - High carbon steel: iron + 3 coal.
  - Crude/metal/gem/fabric/super alloys (compound).
  - Sheet metal (processed).
  - Barrier (creative, intangible).
- **Mod-compat materials:** aluminum, aluminum_steel, bismuth, bismuth_brass, bismuth_steel, brass, bronze, compressed_iron, electrum, enderium, invar, lead, lumium, nickel, osmium, platinum, redstone_alloy, refined_glowstone, refined_iron, refined_obsidian, signalum, silver, steel, tin, titanium, uranium, zinc. Each resolves via its `c:` tags and only works if another mod provides the item.
- **Size:** L (data entry). **Elysium:** PARTIAL. Elysium has ~20 metals as ElysiumGearMaterial plus 2 SG JSONs (aetherium, neutronium).

### SG-13 Part substitutes
- `crafting.part_substitutes.silentgear:rod`: an ingredient that counts as a finished rod of this material in gear recipes. Examples: `c:rods/iron`, vanilla stick = wood, bone, bamboo, end_rod, blaze rod, breeze rod; 49 materials have one (M1).
- **Size:** S. **Elysium:** NONE.

### SG-14 Material modifiers: Grade, Starcharged, Crude
Each is stored as a data component on the material item and applied in `MaterialInstance.getPropertyModifiers`.

**Grade** (`MaterialGrade`, `GradeMaterialModifier`):

| grade | NONE | E | D | C | B | A | S | SS | SSS | MAX |
|---|---|---|---|---|---|---|---|---|---|---|
| bonus % | 0 | 1 | 2 | 3 | 4 | 5 | 10 | 15 | 25 | 30 |

- Applies only to properties with `affectedByGrades` (SG-1): durability, armor_durability, enchantment_value, harvest_speed, attack_damage, magic_damage, ranged_damage, armor, armor_toughness, knockback_resistance, magic_armor.
- Formula: `v' = v + |v| × bonus/100`, for every modifier op (`IMaterialModifier.Helper.modifyNumberValuesWithBonusOrPenalty`).

**Starcharged** (`StarchargedMaterialModifier`, level L):
- Charge `C = L × material.charging_value`.
- It only modifies AVG, MAX and ADD mods, and never charging_value itself:
  - durability `×1.25^C`
  - armor_durability and enchantment_value `×1.1^C`
  - harvest_speed `+1.5·L·C`
  - attack_damage and magic_damage `+C`
  - ranged_damage `+C/2`
  - armor, armor_toughness and magic_armor `+2C`
- Name gets the "Starcharged N" suffix.

**Crude** (`CrudeMaterialModifier`): added to compounds made in the Crude Mixer. For synergy-affected properties, `v' = v + |v|·(m − 1)` with `m = crude_mixer.property_multiplier` (default 0.8, i.e. −20%).

**Unit** (`UnitMaterialModifier`): name-only.

- **Depends on:** SG-3, SG-81, SG-82, SG-80. **Size:** M. **Elysium:** PARTIAL. `ElysiumQuality` is a durability-only grade by practice; there is no charge system.

### SG-15 Compound materials and the synergy formula
- **What:** an alloy item (`alloy_ingot`, `hybrid_gem`, `mixed_fabric`, `crude_alloy`, `super_alloy`) stores a list of sub-materials. Its properties come from them.
- **How** (`gear/material/CompoundMaterial.getPropertyModifiers`, bytecode):
  1. Collect the sub-materials' mods for the part type, then `reduce`, then fire `GetMaterialPropertiesEvent`, then `compressModifiers` (weighted average, SG-2).
  2. If the property is synergy-affected, apply `synergy = SynergyUtils.getSynergy(partType, subMaterials, traits)` as `v' = v + |v|·(synergy − 1)` (`NumberProperty.applySynergy`).
- **Synergy formula** (`util/SynergyUtils`, bytecode constants). Let n = number of sub-materials and u = number of unique materials:
  1. If n < 2, synergy = 1.
  2. `base = 1.1·u/(u + 1.1) + 0.476190476` (= 1/2.1). u=1 → 1.000, u=2 → 1.1857, u=3 → 1.2811, u=4 → 1.3389.
  3. If no category is shared by **all** materials: `−0.2`.
  4. For each category with count c > 1: `+0.015 · c/(n − u + 1)`.
  5. For each unique material (when the max rarity > 0): `−0.001·|rarity(first) − rarity(this)|`.
  6. For each trait (the compound's own traits): `trait.onCalculateSynergy`. `synergy_multiplier` effects add `multiplier × level` when `range_min < syn < range_max`:
     - Synergistic: +0.04/level if syn > 1.0.
     - Rustic: +0.05/level if 0.749 < syn < 1.001.
     - Crude: −0.04/level.
  7. Clamp to [0.1, 2.0].
- **Traits on compounds** combine the same way as on gear (SG-60), with trait conditions evaluated against the sub-material list.
- **Tooltip:** "Synergy: N%" (red < 100, green > 100).
- **Depends on:** SG-2, SG-11, SG-60, SG-80. **Size:** M. **Elysium:** NONE.

### SG-16 Processed material: Sheet Metal
- The Metal Press (SG-84) turns any `metal` material into `silentgear:sheet_metal` ×2 (`recipe/sheet_metal.json`, type `pressing/material`).
- Sheet metal inherits the metal's properties with multipliers (SG-10), so durability becomes 0. Main use: the `sheet` category allowed in elytra wings.
- **Size:** S. **Elysium:** NONE.

### SG-17 Custom compound materials
- `custom_ingot` / `custom_gem` items carry a material id. They come from explicit alloy recipes:
  - `alloying/metal/custom_ingot.high_carbon_steel`: iron + 3 coals.
  - `alloying/gem/custom_gem.dimerald`: diamond + emerald.
- The JSON is `type: custom_compound`, and its properties are fixed in the JSON (they are not averaged).
- **Size:** S. **Elysium:** NONE.

---

# C. Parts

### SG-20 Part types (12)
From `setup/gear/PartTypes` bytecode. All part types except `main` have a compound part item. Upgrade parts are separate items.

| part type | removable (mod kit) | paintable | upgrade | max per item | item |
|---|---|---|---|---|---|
| none | – | no | – | – | – |
| main | no | yes | no | 1 | the gear-specific head/blade/plates (MainPartItem) |
| rod | no | yes | no | 1 | `silentgear:rod` (Tool Rod) |
| tip | **yes** | yes | no | 1 | `tip` (Tip Upgrade) |
| cord | no | yes | no | 1 | `cord` |
| fletching | no | yes | no | 1 | `fletching` |
| binding | **yes** | yes | no | 1 | `binding` |
| coating | **yes** | yes | no | 1 | `coating` |
| grip | **yes** | yes | no | 1 | `grip` |
| lining | **yes** | yes | no | 1 | `lining` |
| setting ("Jewelry Setting") | **yes** | yes | no | 1 | `setting` |
| misc_upgrade | **yes** | **no** | **yes** | **256** | the upgrade items (SG-23) |

The max-per-item default of 1 and paintable-by-default are [inferred] from the builder calls; only non-default values are explicit.
- **Size:** M. **Elysium:** NONE. Sockets/runes are the closest analogue (a slot system on gear).

### SG-21 Part definitions (48 JSON)
- **Path:** `silentgear_parts/*.json`.
- **Fields:** `type` core|upgrade, `gear_type`, `part_type`, `crafting.ingredient` (the part item), `properties` (part-level property modifiers and traits), and `upgrade_gear_types {types[], match_parents}` for upgrades.
- The full table is in **Appendix P1**. Every head part carries its gear's base stat offsets, e.g. sword_blade: attack_damage +3, attack_speed +1.6.
- `repair_efficiency` is a per-part value (1.0–2.0), the "materials needed to repair" factor.
- **Size:** M. **Elysium:** NONE. Shape stats live in Java classes.

### SG-22 Compound part crafting (blueprint + N materials)
- **What:** a shapeless recipe `silentgear:compound_part`: 1 blueprint or template, then N `silentgear:material` ingredients filtered by gear type, part type and optional categories, then any extra ingredients. The output part item stores the ordered `silentgear:material_list`.
- Mixed materials are allowed. The first material is "primary" for colour, name and `primary_material` conditions [inferred].
- **Material counts per part** (recipe files `recipe/gear/*_head.json`, `recipe/part/*.json`): see P1. Highlights:
  - 8 chestplate, 7 leggings, 6 hammer, 6 elytra (cloth/sheet only), 5 helmet, 5 excavator, 5 paxel, 5 saw, 4 boots, 4 mattock.
  - 3 each: pickaxe, axe, sickle, katana, machete, mace (+ heavy_core), bow, crossbow, trident, bracelet (metal), necklace (metal), cord.
  - 2 each: sword, hoe, shears, shield, slingshot, prospector hammer, fishing rod, ring (metal), rod (→4 rods).
  - 1 each: shovel, spear, dagger, knife, arrow heads, tip, binding, grip, lining, fletching, coating (+ glass bottle), setting (+ bort).
- A `silentgear:material` ingredient only matches if that material has properties for the part type and isn't blacklisted. For compounds it also needs durability > 0 (`CompoundMaterial.isCraftingAllowed`).
- An `additive` material can only be combined with others (`property.silentgear.additive.warning`).
- **Depends on:** SG-10, SG-20, SG-50. **Size:** M. **Elysium:** NONE.

### SG-23 Upgrade parts (misc_upgrade)

| item | recipe | effect | allowed on |
|---|---|---|---|
| magnetic_upgrade | shaped: advanced_upgrade_base + 2 breeze rods + 3 crimson steel + 2 iron | Magnetic 5 | all |
| red_card_upgrade ×4 | upgrade_base + red dye | Red Card 1, rarity −5; gear breaks permanently like vanilla | all |
| road_maker_upgrade | advanced_upgrade_base + iron_shovel + orange dye | Road Maker 1, durability ×base +0.1, rarity +10 | excavator |
| spoon_upgrade | advanced_upgrade_base + diamond_shovel | Spoon 1, durability ×base +0.2, rarity +10 | pickaxe |
| wide_plate_upgrade | advanced_upgrade_base + crimson iron block + crimson steel ingot | Widen 1, durability ×base +0.1, rarity +10 | hammer, excavator |

- `upgrade_base` ×4 = 2 paper + planks + stone. `advanced_upgrade_base` = upgrade_base + 3 diamond nuggets + 2 blue dye + 3 gold nuggets.
- Unused lang entries exist for `glowstone_coated_upgrade`, `lapis_coated_upgrade` and `redstone_coated_upgrade`: legacy, no recipes.
- **Size:** M. **Elysium:** PARTIAL (runes are socketed upgrades).

### SG-24 Applying optional parts (tip, coating, grip, binding, lining, setting, upgrades)
- **Gear crafting grid (`gear_crafting`):** the head plus a `part_type` ingredient. Optional variants exist, e.g. `boots_with_lining`, `ring_with_gem`.
- **Swap in crafting grid** (`swap_gear_part`): gear + a part replaces the same-type part. The old part is returned unless `upgrades.destroy_swapped_parts = true`.
- **Smithing:** `smithing/upgrade/<gear>` uses template = stick, base = gear, addition = any misc_upgrade part. `smithing/coating/<gear>` uses template = `coating_smithing_template`, base = gear, addition = a coating-capable material (the material itself, no part item needed); 33 of each.
- **Anvil:** upgrade parts can be applied in an anvil (`RepairHandler.handleUpgradeApplication`, XP cost 3). Config `upgrades.apply_in_anvil_only` (default false).
- **Coating smithing template:** netherite_upgrade_smithing_template + blueprint paper + bort; duplication = 7 diamonds + netherrack + template → ×2.
- **Depends on:** SG-20, SG-31. **Size:** M. **Elysium:** PARTIAL (socketing runes).

### SG-25 Part-type semantics (what each non-main part contributes)
- **rod:** tools. Mostly traits; e.g. iron rod → Flexible 2.
- **tip:** ADD bonuses to all tools, e.g. iron tip: durability +128, attack +1, harvest speed +1, draw +0.2, armor_durability +4, rarity +8, plus harvest tier (can raise the tier!).
- **binding:** tools/elytra; traits and multipliers.
- **grip:** tools; e.g. wool grip: attack_speed +0.2, harvest ×base +0.1, repair_efficiency ×base +0.2, Flexible 2.
- **cord:** bows, crossbows, slingshots, fishing rods; ranged / draw modifiers.
- **fletching:** arrows.
- **lining:** armour; e.g. wool lining: knockback_resistance +0.1.
- **coating:** all but elytra; applies a material's coating properties.
- **setting:** curios gem.
- Values are in Appendix M3.
- **Size:** (included in SG-21). **Elysium:** NONE.

---
# D. Gear types and gear items

### SG-30 Gear type hierarchy (45 registered types)
From `setup/gear/GearTypes` bytecode. The parent is used for trait conditions, property keys and blueprint matching. "groups" are the property groups computed for the type (inherited when not listed).

| type | parent | notes (tool actions / groups / durability stat) |
|---|---|---|
| none | – | – |
| all | – | groups SPECIAL, GENERAL |
| tool | all | groups SPECIAL, GENERAL, HARVEST, ATTACK |
| weapon | tool | – |
| armor | all | durability stat = armor_durability; groups SPECIAL, GENERAL, ARMOR |
| harvest_tool | tool | – |
| melee_weapon | weapon | – |
| ranged_weapon | weapon | groups SPECIAL, GENERAL, PROJECTILE |
| hybrid_weapon | weapon | groups SPECIAL, GENERAL, ATTACK, PROJECTILE |
| curio | all | groups SPECIAL only |
| projectile | all | groups SPECIAL, GENERAL, PROJECTILE |
| pickaxe | harvest_tool | DEFAULT_PICKAXE_ACTIONS |
| shovel | harvest_tool | DEFAULT_SHOVEL_ACTIONS |
| axe | harvest_tool | DEFAULT_AXE_ACTIONS |
| hoe | harvest_tool | DEFAULT_HOE_ACTIONS |
| shears | harvest_tool | DEFAULT_SHEARS_ACTIONS |
| hammer | pickaxe | PICKAXE_DIG (3×3 AOE) |
| excavator | shovel | SHOVEL_DIG (3×3 AOE) |
| saw | axe | AXE_DIG (tree feller) |
| sickle | harvest_tool | HOE_DIG |
| mattock | harvest_tool | SHOVEL_DIG, AXE_DIG, HOE_DIG, HOE_TILL |
| paxel | harvest_tool | AXE_DIG/SCRAPE/STRIP/WAX_OFF, PICKAXE_DIG, SHOVEL_DIG |
| prospector_hammer | pickaxe | DEFAULT_PICKAXE_ACTIONS |
| sword | melee_weapon | DEFAULT_SWORD_ACTIONS |
| katana | melee_weapon | DEFAULT_SWORD_ACTIONS |
| machete | melee_weapon | SWORD_DIG, SWORD_SWEEP, AXE_DIG |
| spear | melee_weapon | SWORD_DIG |
| mace | melee_weapon | – |
| dagger | melee_weapon | SWORD_DIG |
| knife | melee_weapon | SWORD_DIG |
| bow | ranged_weapon | 4 animation frames |
| crossbow | ranged_weapon | 4 animation frames |
| slingshot | ranged_weapon | 4 animation frames |
| trident | hybrid_weapon | DEFAULT_TRIDENT_ACTIONS |
| fishing_rod | tool | DEFAULT_FISHING_ROD_ACTIONS; groups SPECIAL, GENERAL |
| shield | tool | DEFAULT_SHIELD_ACTIONS; durability = armor_durability ×22.466667; groups SPECIAL, GENERAL |
| helmet | armor | armour durability ×11 |
| chestplate | armor | ×16 |
| leggings | armor | ×15 |
| boots | armor | ×13 |
| elytra | armor | ×25 |
| arrow | projectile | – |
| bracelet | curio | – |
| necklace | curio | – |
| ring | curio | – |

The parent column comes from `GearType.Builder.of(parent)` in each lambda, matched to the names by registration order. The per-type parent for hammer, excavator, saw and prospector_hammer is [inferred] from the builder argument (PICKAXE, SHOVEL, AXE, PICKAXE).
- **Size:** M. **Elysium:** PARTIAL (9 fixed shapes plus armour, no hierarchy).

### SG-31 Gear items and their construction
Recipes: `recipe/gear/<type>.json`, type `silentgear:gear_crafting`, shapeless. Each type also has a `<type>_quick` variant: blueprint + raw materials + rod/cord in one grid, with no separate head step. Required parts are in code: `GearItem.REQUIRED_PARTS` = MAIN; `GearTool` = MAIN + ROD; `GearRangedWeapon` = MAIN + ROD + CORD; arrow requires MAIN + ROD + FLETCHING and supports TIP, COATING too; elytra = MAIN + BINDING; curio = MAIN (+ SETTING); fishing rod = MAIN + ROD + CORD.

| gear item | recipe (parts) | main part (materials) | notes |
|---|---|---|---|
| pickaxe | pickaxe_head + rod | 3 | |
| shovel | shovel_head + rod | 1 | |
| axe | axe_head + rod | 3 | |
| hoe | hoe_head + rod | 2 | head attack ×base −1 (0 dmg) |
| shears | shear_blades + rod | 2 | Item durability ×base −0.048 |
| paxel | paxel_head + rod | 5 | durability ×base +0.35, harvest ×base −0.2, ench −0.3 |
| mattock | mattock_head + rod | 4 | |
| sickle | sickle_blade + rod | 3 | area plant break / crop harvest |
| machete | machete_blade + rod | 3 | |
| saw | saw_blade + rod | 5 | tree feller |
| hammer | hammer_head + rod | 6 | 3×3 |
| excavator | excavator_head + rod | 5 | 3×3 |
| prospector_hammer | prospector_hammer_head + rod | 2 | ore scanner |
| sword | sword_blade + rod | 2 | guard shows the 2nd material |
| katana | katana_blade + rod | 3 | |
| spear | spear_tip + rod | 1 | attack_reach +1 |
| mace | mace_core + rod | 3 + heavy_core | |
| dagger | dagger_blade + rod | 1 | |
| knife | knife_blade + rod | 1 | tag `c:tools/knife` [inferred] |
| bow | bow_limbs + rod + cord | 3 | "Unstrung" name when missing cord |
| crossbow | crossbow_limbs + rod + cord | 3 | |
| slingshot | slingshot_limbs + rod + cord | 2 | fires pebbles (`SlingshotAmmoItem`) |
| trident | trident_prongs + rod | 3 | |
| fishing_rod | fishing_reel_and_hook + rod + cord | 2 | |
| shield | shield_plate + rod | 2 | |
| helmet / chestplate / leggings / boots | *_plates (+ lining) | 5 / 8 / 7 / 4 | lining optional |
| elytra | elytra_wings + binding | 6 (cloth/sheet) | |
| arrow | arrow_heads + rod + fletching | 1 | GearArrowItem: stack 64, ammo count in data [inferred] |
| ring | [setting blueprint = jeweler_tools] + ring_shank (+ setting) | 2 metal | |
| bracelet | jeweler_tools + bracelet_band (+ setting) | 3 metal | |
| necklace | jeweler_tools + necklace_chain (+ setting) | 3 metal | |

- **Example:** a stone or crude tool can also be made from `rough_rod` (wood/rough material, name prefix "Crude"). The rough-rod description: "Can be used to craft tools like in vanilla, but has some negative effects".
- **Depends on:** SG-20…SG-22, SG-30, SG-50. **Size:** XL (all item classes). **Elysium:** PARTIAL. Elysium has sword, pickaxe, axe, shovel, hoe, hammer (≈SG hammer), broadaxe (≈saw/big axe), scythe (≈sickle), spear (digs 3×3, **not** the same as SG spear), plus armour.

### SG-32 Harvest-tool behaviours
- **Pickaxe:** vanilla actions. With Spoon it also mines `mineable/shovel` (tag `mineable/pickaxe_with_spoon`).
- **Paxel:** pick + axe + shovel with axe strip/scrape/wax-off (`mineable/paxel`).
- **Mattock:** shovel + axe + hoe dig + till (`mineable/mattock`). "Should work in anything that requires a hoe."
- **Sickle** (`GearSickleItem`):
  - Breaking a plant also breaks nearby plants (radius constant 4 in `onBlockBreakEvent`, via `breakExtraBlock` with sound event 2001).
  - Right-click harvests crops in a radius-2 square (`useOn`) and replants them, destroying the seeds. Durability cost is 3 per `getDamageOnBlockBreak`, with a 0.02 chance constant in useOn.
  - [inferred detail: radius values from the constants 2 and 4]
- **Machete:** sword + axe + sickle. Destroy speed ×0.4 constant [inferred on plants]. Clears plants in radius 2. The tool component has speed 15 on some blocks and 1.5 on others [inferred].
- **Shears:** default 100 durability constructor. Tool rules give speed 15 on cobweb/leaves, 5 on wool, 2 on vines [inferred from constants 6/15/15/5/2].
- **Depends on:** SG-31. **Size:** M. **Elysium:** PARTIAL (Scythe harvests 3×3 crops).

### SG-33 AOE tools (hammer, excavator) and Widen
- `IAoeTool.getAoeRadius = 1 + level(Widen)`, so 3×3, 5×5 (Widen 1), 7×7, 9×9 (Widen max 3). The plane is the struck face (ray-trace).
- **Match modes** (config `aoe_tool.match_mode.standard` = MODERATE, `.ores` = STRICT):
  - LOOSE breaks anything.
  - MODERATE breaks only blocks with the same harvest level.
  - STRICT breaks only the exact block.
- Ores are only AOE-broken when you aim at an ore (JEI text).
- **Size:** M. **Elysium:** PARTIAL (Hammer / "Spear" 3×3 with no widen or match modes).

### SG-34 Saw tree-felling
- Breaking a log fells the whole tree (`GearSawItem.detectTree`, `breakTree`).
- Tree detection needs leaves nearby. Constants: 50-block height search, 3 radius, 9×9 horizontal limit, 1000-block cap.
- Recursion depth is configured by `saw.recursion_depth` (default 200). Logs and leaves must be correctly tagged.
- **Size:** M. **Elysium:** NONE (Broadaxe exists but no felling found).

### SG-35 Prospector hammer
- Right-click a block to scan in the facing direction up to `prospector_hammer.range` blocks (default 16, max 64) for `#silentgear:prospector_hammer_targets` (ancient_debris, `#c:ores`).
- Chat reports "Finds: …" or "Nothing found". Costs 2 durability, with a 20-tick cooldown [inferred from constants].
- It is also a pickaxe and counts as `c:tools/hammer` for tool-action recipes [inferred].
- **Size:** S. **Elysium:** PARTIAL (Elysium has a `ElysiumScannerItem`, purpose unknown).

### SG-36 Melee weapons
- Sword, katana, machete (sweep), spear (+1 attack reach from the part; `GearHelper.tryAttackWithExtraReach` uses extra reach on swing, handled via the `SwingGearPayload` packet), mace (vanilla mace behaviours [inferred]).
- Dagger: i-frames ×0.67. Knife: "higher durability, less damage".
- The per-weapon offsets are the head-part property modifiers in P1.
- **Size:** M. **Elysium:** PARTIAL (sword, spear).

### SG-37 Ranged weapons and projectiles
- Bow, crossbow, slingshot and trident with the formulas in SG-6. Trident has a GearTridentProjectile entity and its own renderer.
- Slingshot fires `pebble` (from the stone anvil + hammer: cobblestone → 9 pebbles).
- Arrows are gear items: GearArrowEntity; damage from ranged_damage; heads, fletching and coating are parts.
- Fishing rod: GearFishingHook.
- `projectile_speed` and `projectile_accuracy` feed the projectile shooting code [inferred].
- **Size:** L. **Elysium:** NONE.

### SG-38 Armour (helmet, chestplate, leggings, boots) and lining
- `GearArmorItem`: protection, toughness, knockback and magic armour per SG-6; armour durability per SG-30.
- Lining part is optional.
- Full-set and piece-count potion effects come from traits (`wielder_effect` type `full_set_only` / `piece_count`).
- Broken armour gives 0 armour.
- **Size:** L. **Elysium:** HAVE for fixed-material armour; PARTIAL for modular.

### SG-39 Elytra (gear)
- Wings (6 cloth/sheet materials) + binding. Armour value ×base −0.65 and −3.5 flat.
- Can go in the Curios "back" slot. Needs the Caelus API for that ("Requires Caelus API to use in back slot").
- **Size:** M. **Elysium:** NONE.

### SG-40 Shield (gear)
- Plate (2 materials) + rod. Durability from armour_durability ×22.47.
- **Size:** S. **Elysium:** NONE.

### SG-41 Curios: ring, bracelet, necklace (+ jewelry settings)
- Main part: ring_shank (2 metals), bracelet_band (3 metals), necklace_chain (3 metals). Blueprints need a metal material or gold.
- **Setting** = the `jeweler_tools` blueprint + bort + a material with `setting` properties (gems). Gear recipe: jeweler_tools + main (+ setting).
- Curios slots are registered by data (`curios/slots/*.json`): ring, bracelet, necklace, back, size 1 each, cosmetic. Entity file: player gets back, bracelet, necklace, ring.
- Curio gear gets **only SPECIAL-group properties**, i.e. traits. Traits with `curio` conditions (Bastion, Kitty Vision, Mighty, Moonwalker, Turtle) work from curios. "Requires Curios API to equip".
- **Size:** M. **Elysium:** PARTIAL (`elysium-trinkets` has CuriosSlots and ElysiumTrinketItem).

### SG-42 Conversion recipes (vanilla → Silent Gear)
- 50 `silentgear:conversion` recipes (`recipe/gear/convert/*`): the wooden, stone, iron, golden, diamond and netherite tools (sword, pickaxe, shovel, axe, hoe), plus leather, iron, golden, diamond and netherite armour.
- The result is an SG item with preset parts. Example: iron_pickaxe → pickaxe [pickaxe_head(iron×3) + rod(wood)].
- Config `allow_conversion_recipes` (default true).
- **Size:** S. **Elysium:** PARTIAL (`GearConvergence` swaps Elysium↔SG items).

### SG-43 Gear naming, colours, rendering
- **Name:** `%s <Type>` using the primary main material name, plus prefixes. Examples: material `name_prefix` (prismarine, netherite, crude); `GearNamePrefixesEvent`; "Starcharged".
- **Rendering:** layered models by part; `main_texture_type` HIGH/LOW_CONTRAST; colour per material (`display.color`), tinted in `ColorHandlers`.
- **Custom code:** compound model loaders (`compound_part_model`, `gear_model`), a "broken" model property, and custom renderers for ranged weapons and trident.
- **Size:** L (client). **Elysium:** PARTIAL (textured fixed items).

### SG-44 Third-party compat data
- `weapon_attributes/*.json` (15): Better Combat parents for axe, crossbow, katana, dagger, sword, paxel, bow, sickle, machete, knife, mace, spear, pickaxe, hammer, trident.
- `data/mmorpg/compatible_items`: Mine and Slash.
- JEI plugin (categories: alloy making, gear crafting, grading, salvaging, tool action).
- `bonsaitrees2` sapling recipe and a `woodcutter` recipe.
- Silent's Mechanisms tags.
- **Size:** S. **Elysium:** NONE.

---

# E. Blueprints and templates

### SG-50 Blueprints (reusable) vs templates (single-use)
- Every part and gear type has `<x>_blueprint` (blueprint paper) and `<x>_template` (template board). The two share a shaped pattern; see the recipe list in Appendix R.
- There are 43 blueprint tags (`tags/item/blueprints/*`).
- Blueprints can be used infinitely; templates are consumed ("Can be used only once").
- Config `item.blueprint.types_allowed`: BOTH (default), BLUEPRINT or TEMPLATE. Disabled types show "Disabled in config!".
- The tooltip shows material amount ("Material Required: %d"); hold Alt for the recipe.
- `jeweler_tools` is the setting blueprint (curios).
- The `trident_blueprint` needs a trident + heart of the sea. `elytra` needs an elytra + phantom membranes. `mace` needs breeze rods.
- **Depends on:** SG-22. **Size:** M. **Elysium:** NONE.

### SG-51 Blueprint materials and containers
- **Blueprint paper** ×4 = 4 paper + blue dye.
- **Template board** ×6 = log on the stone anvil, cut with a knife (tool action).
- **Blueprint Book** = book + wool + gold + 3 template boards. It stores blueprints, selected with the cycle keys or Ctrl+click; the container `blueprint_book` works through the `SelectBlueprintInBookPayload` packet. A "Fully-Loaded" variant exists in the creative tab.
- **Blueprint Package** gives the loot table `silentgear:starter_blueprints`: rod, pickaxe, shovel, axe, hoe and sword blueprints. It is handed out on first join if `spawn_with_starter_blueprints` = true.
- **Size:** M. **Elysium:** NONE.

### SG-52 Mod Kit
- `mod_kit` = 3 template boards + stick + iron. Cycle the selected part type with keys.
- Recipes:
  - `mod_kit_remove_part`: kit + gear removes the removable part of the selected type and returns it.
  - `mod_kit_paint_part`: kit + gear + paint paints parts.
- The kit is returned (it is a crafting remainder).
- **Size:** S. **Elysium:** NONE (reforging is its own system).

---
# F. Traits

### SG-60 Trait system: data format, conditions, level combination
- **Trait JSON** (`silentgear_traits/<id>.json`, 75 files): `{ name, description, max_level, conditions[], effects[], extra_wiki_lines[] }`.
  - `conditions` are gear-level conditions, e.g. only on `tool`, `armor`, `harvest_tool`.
  - `effects` are typed effects (SG-61).
  - Some traits have **no data effects**; their logic is hardcoded (SG-63).
- **Trait instances** live in material or part properties: `"traits": [{"trait": id, "level": n, "conditions": [...]}]`.
- **Condition types** (`setup/gear/TraitConditions`): `gear_type`, `material_ratio {ratio}`, `material_count {count}`, `and`, `or`, `not`.
  - `material_ratio`: share of the part's material entries that are this material. Used ratios: 0.33, 0.35, 0.5, 0.66, 0.67, 0.7, 0.75.
  - `material_count`: number of entries of this material.
  - `primary_material` has lang text only; it is not registered in 4.2.1.1.
  - Conditions are checked in `TraitListProperty.reduce` (`TraitInstance.reduceConditions`) against the part's material list, then again at gear level.
- **Level combination** (`TraitListProperty.computeTraits`, bytecode):
  1. Take all trait instances from all parts (N = total number of instances).
  2. For each trait: `sum` = sum of its levels, `count` = number of instances.
  3. `level = clamp(round(sum / min(N/2, count)), 1, maxLevel)`.
  4. Drop instances whose gear-type condition fails.
  - The `min(N/2, count)` divisor means a trait carried by many parts is averaged. A rare trait is divided by at most N/2, so it keeps a bit more.
- **Tooltip:** name + level (roman numerals [inferred]). Hold the key for descriptions.
- **Depends on:** SG-3, SG-10. **Size:** L. **Elysium:** NONE.

### SG-61 Trait effect types (18, `setup/gear/TraitEffectTypes`)
In the formulas below, *lvl* = trait level and *ratio* = damage/maxDamage.

| effect type | JSON fields | behaviour (bytecode) |
|---|---|---|
| number_property_modifier | `property_modifiers {prop: {base_multiplier, multiply_damage_ratio, multiply_original_value}}` | Adds an ADD bonus = `base_multiplier × lvl × (ratio if flag) × (value if flag)` at the bonus stage (SG-3). |
| durability | `activation_chance, effect_scale` | On each durability-damage event: with chance `activation_chance × lvl`, damage += effect_scale (−1 = saves one point, +1 = extra point). Brittle/Bending firing grants a `brittle_damage` advancement trigger. |
| self_repair | `activation_chance, repair_amount` | Every 20 ticks while held/worn by a player: chance `activation_chance × lvl` to repair `repair_amount × lvl`. |
| synergy_multiplier | `multiplier, range_min, range_max` | Synergy += multiplier × lvl if range_min < syn < range_max (compounds only, SG-15). |
| attribute | `attribute_modifiers {slotGroup: [{attribute, operation, values[per level]}]}` | Adds attribute modifiers. Slot keys: `all/any`, `armor/head`, `armor/chest`, `armor/legs`, `armor/feet`. |
| wielder_effect | `potion_effects {gearType: [{type: trait_level/piece_count/full_set_only, effect, levels[], duration}]}` | Applies a potion effect to the wearer/holder. `piece_count` indexes `levels` by the number of armour pieces with the trait. Duration 38 or 318 ticks, refreshed. |
| extra_damage | `affected_type (high_health / aquatic / fire_immune / tagged), affected_entities_tag, bonus_damage_per_level` | On attack: +bonus × lvl if the target matches. high_health = max health ≥ 21; aquatic = water-type; fire_immune = `fireImmune()`; tagged = entity tag. |
| enchantment | `enchantments {ench: [level per trait level]}` | Grants enchantments while the trait is present (`TraitAddedEnchantments` component, capped at 256). They are not removed by `force_remove_enchantments`. |
| block_placer | `block, cooldown, damage_on_use, sound` | Right-click places the block for `damage_on_use` durability. |
| block_filler | `target, fill_properties{block, range_x/y/z, facing_plane_only, replace_block_entities}, use_properties{damage_per_block, sneak_mode}, sound` | Right-click converts a 3×3 area (range 1) of the target block. |
| bonus_drops | `base_chance, bonus_multiplier, ingredient` | Global loot modifier: for each matching drop, with chance `base_chance × lvl`, add `round(count × bonus_multiplier)` extra. |
| item_magnet | `effect_range, pull_strength, affected_items?` | Pulls item entities within `effect_range` (plus level scaling) toward the player. Config `disable_on_crouch` = true. |
| block_mining_speed | `block_tag, speed_modifier` | Adds `speed_modifier × lvl` to the mining-speed multiplier on tagged blocks (SG-6). |
| negate_damage | `damage_type_tag, negated_damage_scale` | Incoming damage of that type × (1 − clamp(scale × lvl, 0, 1)). |
| cancel_effects | `cleared_effects[]` | Removes those effects each tick while equipped. |
| target_effect | `effects_by_level {gearType: {lvl: [MobEffectInstance]}}` | Applies effects to the hit target. |
| fireproof | – | The item entity is immune to fire/lava (mixin `MixinItemEntity`). |
| wind_blast | – | On attack: wind-charge-style explosion, radius 1.2, knockback 1.22, `ExplosionInteraction.TRIGGER` [inferred details]. |
| attach_data_components | `components` | Attaches item components, e.g. `minecraft:food`. |

- **Size:** XL in total, but it splits per effect: S–M each. **Elysium:** NONE (runes give stat bonuses; no effect engine).

### SG-62 Trait catalogue (all 75)
See **Appendix T1**: id, name, max level, gear condition, data effects, description, and every material/part that provides it with level and condition.

### SG-63 Hardcoded trait behaviours (traits with no or partial data effects)
Bytecode: `event/GearEvents`, `util/GearHelper`, `IAoeTool`, `GearPickaxeItem`, mixins, loot modifiers.

| trait | behaviour |
|---|---|
| ancient | Mob XP and block XP += `floor(xp × 0.25 × lvl)` (tools; `onXpDrop`, `onBlockDrops`). |
| lustrous | Break speed += `4 × lvl × light / 15`, with `light = max(skyLight, blockLight × 3/4)` at the player (`getLustrousSpeedBonus`), only when the tool is correct for the block. |
| magmatic | Global loot modifier `magmatic_smelting`: smelts block drops. Fortune is not applied to smelted drops. |
| spoon | Pickaxe also mines `mineable/shovel` blocks (`GearPickaxeItem`, tag `pickaxe_with_spoon`). |
| widen | AOE radius +lvl (SG-33). |
| turtle | Helmet: when the eyes are in water, Water Breathing for 210 ticks (like the vanilla turtle shell). |
| void_ward | Any armour piece: when y < minBuildHeight − 32, push up 20, Slow Falling (400t, amp 3) and Levitation (200t, amp 9). |
| jabberwocky | Mining `#c:ores/diamond` without Silk Touch spawns a random cat, chicken, cod, pufferfish, rabbit, salmon or wolf at the block. |
| confetti | Killing a mob with a Confetti weapon in either hand spawns 3 random firework rockets at the victim's eyes. |
| flammable | Wearer takes damage while on fire → each flammable gear item loses 2 durability; if broken it is destroyed ("Your %s burnt up"). Usable as furnace fuel: burn time = maxDamage × 4. |
| brilliant | Piglins treat the armour/elytra/curio as gold (`isPiglinCurrency`/`makesPiglinsNeutral`). |
| snow_walker | Mixin `MixinPowderSnowBlock`: walk on powder snow when on any armour or curio. |
| indestructible | `isUnbreakable` → damage is never applied; stays at the current damage and can still be repaired. |
| red_card | Gear breaks permanently like vanilla (SG-70). |
| multi_break | Declared but "never coded" (extra_wiki_lines). |
| bounce | Boots cancel fall damage and bounce (`LivingFallEvent`, velocity ×−0.75). Armour knocks back attackers (`onPlayerHurt`). Data part: negates `fly_into_wall` ×0.2/lvl. |
| moonwalker | Gravity attribute ×(1 − 0.15·lvl); `onLivingFall` also reduces fall damage [inferred]. |
| kitty_vision / mighty / stellar / adamant / aquatic / flame_ward | Data-driven potion effects (T1). |

- **Size:** M. **Elysium:** NONE.

### SG-64 Trait-related loot modifiers
- `loot_modifiers/bonus_drops_trait.json`: type `silentgear:bonus_drops_trait`, no conditions. Runs `addLootDrops` of every trait on the tool (Gold Digger → `#c:nuggets`; Imperial → `#c:gems`).
- `magmatic_smelting.json`: condition `silentgear:has_trait` magmatic.
- Also: loot condition `silentgear:gear` and loot function `set_parts` (build gear in loot tables).
- **Size:** S. **Elysium:** NONE.

---

# G. Durability, breaking, repair, part editing

### SG-70 Durability, broken state, Red Card
- Gear **does not disappear** at 0 durability. Damage is clamped to `maxDamage` (`calcDamageClamped`), and the item is **broken** when damage ≥ maxDamage − 1.
- **Broken gear:**
  - mining speed 0.25
  - no attack damage (1) and attack speed +0.7
  - armour 0 and toughness 0
  - cannot hurt enemies
  - broken model, "You broke it!" message
  - a chat message if `send_broken_message` (default true): "Your %s has broken… Consider crafting a repair kit"
- **Permanent breaking** happens only when the Red Card trait is present or config `breaks_permanently` = true.
- Durability trait effects adjust each damage event (SG-61).
- Parts can also take damage (`damageParts`) [inferred: used for parts with own durability].
- The `repair_from_broken` advancement exists.
- **Size:** M. **Elysium:** NONE (vanilla breaking).

### SG-71 Repair
**Repair value of one material item** (`MaterialInstance.getRepairValue`, [web: 1.21.x source; structure matches bytecode]):

`round(durability(main) × (1 + repair_value(main)) × itemRepairModifier × (1 + typeBonus)) + 1`

- `itemRepairModifier` = 1 for tools and the gear type's `armorDurabilityMultiplier` for armour, elytra, shield and arrow items (they override `getRepairModifier`).
- `typeBonus`: QUICK 0, ANVIL +0.1 (`RepairContext$Type`).
- Only materials that are in the gear's main parts can repair it (`MaterialInstance.canRepair`, `GearHelper.getIsRepairable`) [inferred detail].

**Quick repair in the crafting grid** (`QuickRepairRecipe`): gear + a repair kit (and/or loose materials).
- Loose materials: damage −= `round(Σ repairValue × repair_efficiency(gear) × missingKitEfficiency)`. `repairs.missing` = 0 by default, so loose materials alone do nothing unless the config is raised.
- **Repair kits:** fill them by crafting kit + material items (`fill_repair_kit`). Each item is 1 unit (`getMaterialValue = 1`). Empty them with kit + stick. The kit repairs using its stored materials with its efficiency (+ type bonus):

| kit | capacity | efficiency | recipe |
|---|---|---|---|
| very_crude | 8 | 0.30 | template boards + sticks + stone |
| crude | 16 | 0.35 | template boards + sticks + iron |
| sturdy | 32 | 0.40 | iron ingots + iron rods + diamond |
| crimson | 48 | 0.45 | crimson steel + blaze rods + blaze gold |
| azure | 64 | 0.50 | azure electrum + end rods + emerald |

**Anvil** (`RepairHandler.handleGearRepair`): damage −= `round(repairValue × repair_efficiency × anvil_effectiveness(0.5) × count)` [inferred structure]. Setting 0 disables it.

- `repair_efficiency` is a gear property: the part JSON value is 1.0–2.0 per head, and grips can add to it.
- Advancements: `repair_kit`, `repair_from_broken`, trigger `gear_repaired`.
- **Depends on:** SG-3, SG-10. **Size:** M. **Elysium:** PARTIAL (vanilla anvil repair).

### SG-72 Swapping and removing parts
- `swap_gear_part` recipe (SG-24).
- Mod kit remove/paint (SG-52).
- `destroy_swapped_parts` config.
- Stats are recalculated after any change.
- **Size:** S. **Elysium:** NONE.

### SG-73 Enchanting rules
- Gear is enchantable by `enchantment_value` if `allow_enchanting` (default true).
- `force_remove_enchantments` (default false) strips non-trait enchantments on recalculation.
- Trait-granted enchantments are tracked separately. `allow_enchanted_effect` is a client config (glint).
- A `star_charged` enchantment exists only as a lang key (legacy).
- **Size:** S. **Elysium:** PARTIAL (vanilla enchanting on Elysium items).

### SG-74 Paint and colour customisation
- `paint` item (Color: %s) + `quick_paint` recipe (gear + paint).
- **Paint Mixer** block (recipe: smooth stone, dyes, copper, iron rods, bucket) mixes dyes into paint (`PaintUtils`, `paint_filler` tag).
- `mod_kit_paint_part` paints the selected part type.
- The exact colour-mixing maths is not extracted [cannot determine precisely].
- **Size:** M. **Elysium:** NONE.

---

# H. Crafting stations and processing

### SG-80 Alloy makers (compound materials)
All five share `block/alloymaker/AlloyMakerBlockEntity`:
- Work time **10 s = 200 ticks** (2 s in dev builds).
- A work-enable toggle (`ToggleWorkModePayload`) and an output-hint slot.
- They accept only materials allowed by their category list.
- They need **≥2 different materials** unless an explicit recipe matches.
- The inputs must share at least one part type they can all be used for (`canCompoundMaterials`).
- The output is a compound item listing the inputs (SG-15).

| block | categories accepted | input slots | output item | recipe type | block recipe |
|---|---|---|---|---|---|
| Alloy Forge | metal, dust | 6 | `alloy_ingot` ("%s Alloy") | alloy_making/metal | blackstone + crimson steel + iron block |
| Recrystallizer | gem, dust | 6 | `hybrid_gem` | alloy_making/gem | purpur + azure electrum + diamond + emerald + gold block |
| Refabricator | cloth, fiber, slime | 6 | `mixed_fabric` | alloy_making/fabric | planks + iron + bort + diamond + iron block |
| Crude Mixer | any | 4 | `crude_alloy` ("Crude %s Alloy"), plus the **Crude modifier ×0.8** | alloy_making_crude | stone + planks + flax string |
| Super Mixer | any | 8 | `super_alloy` | alloy_making/super | tyrian steel blocks + beacon + custom gem |

**Explicit alloy recipes** (`recipe/alloying/`):
- azure_electrum_ingot ×3 ← azure silver **block** + 2 gold + ender pearl.
- blaze_gold_ingot ← gold + 2 blaze powder.
- crimson_steel_ingot ×3 ← crimson iron **block** + 2 blaze rods + magma cream.
- custom_ingot (high_carbon_steel) ← iron + 3 coals.
- tyrian_steel_ingot ×4 ← crimson steel + azure electrum + crushed shulker shell + netherite scrap.
- Gem: custom_gem (dimerald) ← diamond + emerald.

- Crafting-table alternatives exist for azure electrum, crimson steel and blaze gold.
- **Depends on:** SG-15, SG-11. **Size:** L. **Elysium:** NONE.

### SG-81 Material Grader
- **Recipe:** quartz, iron, advanced upgrade base, blaze gold.
- **Slots:** 1 input, 1 catalyst, 4 outputs [inferred from slot arrays]. Analyse time 5 s (100 ticks).
- **Catalysts** (`tags/item/grader_catalysts/tierN`): T1 glowing_dust, T2 blazing_dust, T3 glittery_dust (T4/T5 empty tags).
  - glowing_dust ×4 = stick + 2 glowstone dust + quartz.
  - blazing_dust ×4 = blaze gold dust + 2 glowstone dust.
  - glittery_dust ×8 = 4 popped chorus + 2 emerald nuggets + 2 glowstone + nether banana.
- **Grade roll** (`MaterialGrade.selectWithCatalyst`):
  1. `median = ordinal(config median_grade = C) + tier − 1`, capped at MAX.
  2. `g = round(median.ordinal + gaussian × standard_deviation(1.5))`, clamped to [1 (E), MAX].
  3. The material is only upgraded if g > its current grade. Each attempt consumes a catalyst [inferred] and one material item is moved to output.
- `can_grade_parts` (default false) lets it grade the materials inside a part.
- **Size:** M. **Elysium:** PARTIAL (`ElysiumQuality` is a different mechanism: crafter practice).

### SG-82 Starlight Charger (Starcharged)
- **Recipe:** quartz blocks, glass, polished blackstone, blaze gold block.
- **Slots:** input, catalyst, output.
- **Energy:** gains `charge_rate` (100) per tick while it has room (max 1,000,000), `work_time` = NIGHTTIME, and — if `requires_view_of_sky` — a clear sky above.
- **Structure level:** 4 pillars at the diagonal offsets (±3, ±3). The block **2 above** each corner position sets its level: crimson steel block = 1, azure electrum block = 2, tyrian steel block = 3. Structure level = min of the four; re-checked every 100 ticks.
- **Catalysts** (`starlight_charger_catalysts/tierN`): T1 `#c:dusts/blaze_gold`, T2 `#c:dusts/azure_silver`, T3 `#c:dusts/starmetal`. Starmetal dust ×3 = azure electrum dust + 2 azure silver dust + blaze gold dust + nether star fragment.
- **Charging** needs catalyst tier T ≤ structureLevel and T > the item's current charge level. It drains `150 + 50·T²` energy per tick, over `workTime = 5 × clamp(rarity, 5, 500)` ticks. The result is Starcharged level T (effects SG-14).
- `can_charge_parts` (default false).
- **Size:** L. **Elysium:** NONE.

### SG-83 Salvager
- **Recipe:** piston, crimson iron, iron block, obsidian.
- **Slots:** 1 input, 18 outputs. Work time 10 s.
- **Recipes:**
  - `salvaging` (54 vanilla items → fixed results, e.g. iron_pickaxe → 3 iron + 2 sticks; also horse armour, clock, compass, shears, bows).
  - `salvaging/gear` (33 SG gear types → their parts; or straight to materials if `break_down_parts_with_gear`).
  - `salvaging/compound_part` (part → one material item per material entry; non-stackable parts only).
- **Loss:** each output item is independently lost with `lossRate = min + (damage/maxDamage) × (max − min)`, with min 0 and max 0.5. Undamageable input uses min.
- Materials must have `can_salvage` = true.
- **Size:** M. **Elysium:** NONE.

### SG-84 Metal Press
- **Recipe:** obsidian, iron rods, tyrian steel.
- 2 slots, 10 s. Recipe types `pressing` and `pressing/material`. The only shipped recipe is metal → 2 sheet metal.
- **Size:** S. **Elysium:** NONE.

### SG-85 Stone Anvil and tool-action recipes (early game)
- **Stone Anvil** (cobblestone + dirt): place an item on it, then use a tool matching `tool` (`c:tools/hammer` or `c:tools/knife`) to craft. Each recipe has `damage_to_tool` and a sound.
- **Recipes (9):**
  - cobblestone → 9 pebbles (hammer)
  - log → 6 template boards (knife)
  - shulker shell → crushed shulker shell (hammer, 10 damage)
  - ingots → dusts (hammer) for azure electrum, azure silver, blaze gold, crimson iron, crimson steel, tyrian steel
- **Crude tools:** `crude_tool_parts` ×4 = cobblestone + dirt + stick. `crude_hammer` = 2 cobble + 2 parts; `crude_knife` = cobble + parts.
- **Size:** M. **Elysium:** NONE.

### SG-86 Gear Smithing Table
- `gear_smithing_table` block. The smithing recipes `smithing/coating` and `smithing/upgrade` (SG-24) run in it (and/or the vanilla smithing table) [inferred]. It is also probably the Gear Smith villager workstation [inferred].
- **Size:** S. **Elysium:** NONE.

### SG-87 Rods and basic components
- `rod` from the rod blueprint + 2 materials → 4 rods.
- Direct rod items also exist:
  - `stone_rod` ×4 = 2 cobble.
  - `iron_rod` ×4 = 3 iron.
  - `rough_rod` (crude, from `rods/rough` tag).
  - netherwood_stick.
- **Size:** S. **Elysium:** NONE.

### SG-88 Upgrade bases
- `upgrade_base` and `advanced_upgrade_base` (SG-23).
- **Size:** S. **Elysium:** NONE.

---
# I. World content, resources, loot

### SG-90 Ores and world generation
Data: `worldgen/configured_feature`, `worldgen/placed_feature`, `neoforge/biome_modifier`.

| ore | dimension (biome tag) | vein size | count/chunk | height | replaces |
|---|---|---|---|---|---|
| crimson_iron_ore / blackstone_crimson_iron_ore | Nether (`#is_nether`) | 8 | 14 | uniform 10 above bottom → 10 below top | `#c:netherracks`, blackstone |
| azure_silver_ore | End (`#is_end`) | 6 | 8 | uniform y16–92 | `#c:end_stones` |
| bort_ore / deepslate_bort_ore | Overworld | 3 | 40 | trapezoid y−60…10 | stone/deepslate ore replaceables |

- Raw ores: raw_crimson_iron, raw_azure_silver (+ blocks).
- Smelting: 200 ticks. Blasting: 100 ticks. XP: crimson 1.0, azure 1.5, dust-smelting 0.5–0.75.
- Storage blocks: crimson iron/steel, azure silver/electrum, blaze gold, tyrian steel, bort.
- **Size:** M. **Elysium:** PARTIAL (Elysium has its own ores/ingots: voidglass, aetherium, neutronium).

### SG-91 Plants and trees
- **Flax** (crop + wild): flax seeds, fibre, flowers (4 → blue dye), flax_string = 2 fibre.
- **Fluffy** (crop + wild): puff, fabric, 16 coloured fluffy blocks, fluffy string/feather/wool.
- Wild patches: tries 32, xz spread 7, y spread 3, rarity 1/64 chunks, Overworld.
- **Netherwood** tree: forking trunk 5+2+2, blob leaves r2. It grows on `netherwood_soil` [inferred]. Full wood set: log, wood, stripped variants, planks, slab, stairs, fence, gate, door, trapdoor, sapling, charcoal + block.
- **Nether banana / golden nether banana:** food, from bastion/nether chests.
- Compostables and furnace fuels are declared in `data/neoforge/data_maps/item`.
- **Size:** M. **Elysium:** NONE.

### SG-92 Special-metal crafting chain
- Crimson iron (Nether ore) → crimson steel (block + 2 blaze rods + magma cream, alloy forge ×3; or crafting-table shaped).
- Azure silver (End ore) → azure electrum (block + 2 gold + ender pearl, ×3).
- Blaze gold = gold + blaze powder (2 in the forge, 4 in the grid).
- Tyrian steel (×4) = crimson steel + azure electrum + crushed shulker shell + netherite scrap.
- Dusts come from the stone anvil + hammer, then smelt back to ingots.
- Starmetal dust (catalyst T3). Bort (gem; used in settings and the coating template).
- Diamond/emerald shards: 9 per gem, reversible.
- Nether star fragments: 9 per star.
- **Size:** M. **Elysium:** PARTIAL (own metals).

### SG-93 Mob-drop loot modifiers
From `loot_modifiers/add_mob_drops`. All require a player kill.

| drop | mobs | chance (looting: base / per level) | count |
|---|---|---|---|
| sinew | cow, pig, all 16 sheep colours, (more in the file) | 0.20 (0.25 / +0.05) | 1 |
| fine_silk | spider (low) | 0.02 (0.025 / +0.005) | 1 |
| fine_silk | cave_spider (high) | 0.04 (0.05 / +0.01) | 1 |
| leather_scrap | zombie, husk (low) | 0.05 (0.06 / +0.01) | 1–3 |
| leather_scrap | zombie_villager (high) | always | 2–5 |

- Processing: sinew → smelt → dried sinew → 3 sinew fibre. 4 fine silk ↔ fine silk cloth. 9 leather scraps = leather.
- **Size:** S. **Elysium:** NONE.

### SG-94 Chest loot injections
From `loot_table/inject/minecraft/chests/*`, used for bastion_bridge, bastion_other, bastion_treasure, nether_bridge and ruined_portal.

**Pool A** (1–2 rolls):

| entry | weight | count |
|---|---|---|
| empty | 20 | – |
| crimson_iron_ingot | 35 | 1–4 |
| blaze_gold_nugget | 35 | 5–10 |
| blaze_gold_ingot | 15 | 1 |
| crimson_steel_ingot | 1 | 1 |

**Pool B** (1–2 rolls):

| entry | weight | count |
|---|---|---|
| empty | 10 | – |
| netherwood_sapling | 20 | 1–2 |
| nether_banana | 10 | 2–4 |
| golden_nether_banana | 1 | 1 |

Bastion_treasure adds a pool: blaze_gold_ingot (weight 6, 2–5) and crimson_steel_dust (weight 1, 1–2). Bastion_other has only pool B.
- **Size:** S. **Elysium:** NONE.

### SG-95 Misc blocks and items
- **Blocks:** phantom_light (placed by Refractive), stone_torch, fluffy blocks.
- **Items:** leather scraps, pebble, template board, blueprint paper, crude tool parts, upgrade bases.
- **Tags:** `c:tools/hammer` and `c:tools/knife` [inferred].
- **Size:** S. **Elysium:** NONE.

### SG-96 Villager profession "Gear Smith"
- `SgVillages` registers a POI and a `gear_smith` profession with a hero-of-the-village gift table. No trade listings were found in the bytecode.
- **Size:** S. **Elysium:** PARTIAL (elysium-npcs has workstations).

### SG-97 Advancements
- 273 advancement files (most are recipe unlocks). There is a gameplay tree: root, blueprint_paper, template_board, stone_anvil, crude_knife, big_job_tools, standard_tools, armor, bow, crossbow, sickle, mattock, tip_upgrade, repair_kit, repair_from_broken, high_durability, grader catalysts 2/3, material_grader, salvager, crimson_iron/steel, blaze_gold, azure_silver/electrum, moonwalker, nether, the_end, kachink1/2, …
- Custom criteria: `gear_property`, `gear_repaired`, `has_part`, `brittle_damage`.
- **Size:** S. **Elysium:** NONE.

---

# J. UX, config, commands, compat

### SG-100 Tooltips and keybinds
- **Keys:** Display Properties & Traits, Display Construction & Materials, Display Trait Descriptions, Cycle Material Info, Cycle Next/Back (blueprint book, mod kit), Open Item.
- **Tooltip content:** properties with coloured values, traits, construction (parts + materials + grade), synergy, material categories, "poorly made" hints.
- `GearHudOverlay`, `DebugOverlay`.
- **Size:** M. **Elysium:** PARTIAL (Elysium has its own tooltips).

### SG-101 Material Book
- `material_book` = book + blueprint paper. A GUI that lists all materials by ID, name or any property, with per-part-type detail pages.
- Given on first join if `spawn_with_material_book` = true.
- **Size:** M. **Elysium:** PARTIAL (`ElysiumCodexItem`, `ElysiumMaterialReport` exist).

### SG-102 Commands (`/sgear`)
| command | what it does |
|---|---|
| `mats list/describe/dump` | TSV export |
| `parts list/describe/dump` | – |
| `traits list/describe/dump_md` | – |
| `properties info/recalculate` | – |
| `grade list/set/remove` | – |
| `starcharged list/set/remove` | – |
| `damage` (set/max) | – |
| `random` | give random gear |
| `lock_stats` | – |

- **Size:** S. **Elysium:** NONE.

### SG-103 Config (common; defaults from `Config$Common` bytecode)

| key | default | range / note |
|---|---|---|
| item.blueprint.types_allowed | BOTH | BLUEPRINT / TEMPLATE / BOTH |
| item.blueprint.spawn_with_starter_blueprints | true | |
| item.material_book.spawn_with_material_book | true | |
| item.repair_kits.capacity.{very_crude,crude,sturdy,crimson,azure} | 8,16,32,48,64 | 0–MAXINT |
| item.repair_kits.efficiency.{…} | 0.30,0.35,0.40,0.45,0.50 | 0–10 |
| item.repair_kits.efficiency.missing | 0.0 | 0–10 (loose-material repair) |
| nerfed_items.enabled | false | |
| nerfed_items.durability_multiplier | 0.05 | 0–1 |
| nerfed_items.harvest_speed_multiplier | 0.5 | 0–1 |
| nerfed_items.items | NerfedGear.DEFAULT_ITEMS (vanilla tools) | list |
| compounds.crude_mixer.property_multiplier | 0.8 | 0–2 |
| gear.allow_conversion_recipes | true | |
| gear.send_broken_message | true | |
| gear.damage_factor_levels | 10 | 1–MAXINT |
| gear.breaks_permanently | false | |
| gear.enchanting.allow_enchanting | true | |
| gear.enchanting.force_remove_enchantments | false | |
| gear.prospector_hammer.range | 16 | 0–64 |
| gear.saw.recursion_depth | 200 | 0–MAXINT |
| gear.aoe_tool.match_mode.standard / .ores | MODERATE / STRICT | LOOSE/MODERATE/STRICT |
| gear.repairs.anvil_effectiveness | 0.5 | 0–1 |
| gear.upgrades.apply_in_anvil_only | false | |
| gear.upgrades.destroy_swapped_parts | false | |
| trait.item_magnet.disable_on_crouch | true | |
| material_grader.median_grade | C | |
| material_grader.standard_deviation | 1.5 | 0–100 |
| material_grader.can_grade_parts | false | |
| salvager.part_loss_rate.min / .max | 0.0 / 0.5 | 0–1 |
| salvager.break_down_parts_with_gear | false | |
| starlight_charger.charge_rate | 100 | per tick |
| starlight_charger.max_charge | 1,000,000 | |
| starlight_charger.work_time | NIGHTTIME | |
| starlight_charger.requires_view_of_sky | true | |
| starlight_charger.can_charge_parts | false | |
| debug.logging.properties | false | |
| other.show_wip_text | true | |

- There is also a per-property bonus multiplier map (`Config.Common.getPropertyBonusMultiplier`, lang "Property Multiplier"). Its default is not extracted [cannot determine].
- **Client config:** vanilla_style_tooltips, show_material_tooltips, show_part_tooltips, allow_enchanted_effect (defaults not extracted).
- **Size:** S. **Elysium:** PARTIAL (`ElysiumMaterialConfig`).

### SG-104 Nerfed vanilla gear
- Optional (off by default): multiplies vanilla tools' durability (×0.05, min 1) and harvest speed (×0.5) to push players toward SG gear.
- **Size:** S. **Elysium:** NONE.

### SG-105 Starting kit
- First join gives a blueprint package (starter blueprints loot table) and a material book, per config.
- **Size:** S. **Elysium:** NONE.

### SG-106 Guide book
- The `guide_book` item (book + template board) only opens a wiki link (`OpenGuideBookPayload`).
- **Size:** S. **Elysium:** PARTIAL (Codex).

### SG-107 API events (for add-ons)
- `GearItemEvent`, `GearNamePrefixesEvent`, `GearRecalculateEvent`, `GetMaterialPropertiesEvent`, `GetPropertyModifiersEvent`, `GetTraitsEvent`, plus data-generator builders (MaterialBuilder, PartBuilder, TraitBuilder).
- **Size:** S. **Elysium:** NONE.

### SG-108 Networking and sync
- Materials, parts and traits are datapack registries synced to clients (`SyncMaterialsPayload`, `SyncPartsPayload`, `SyncTraitsPayload`, `DataResourcesPayload`). There is also a key-press-on-item payload (cycle items) and a recalculate-stats payload.
- **Size:** M. **Elysium:** PARTIAL (Elysium uses its own registries).

### SG-109 Creative tabs and sample gear
- Two tabs: Main and Gear. `GearHelper.fillItemGroup` builds sample gear from random materials. `GearGenerator` makes random gear, used by the `random` command and loot.
- **Size:** S. **Elysium:** PARTIAL.

---

# Suggested build order (dependency-respecting)

1. **SG-1 Property registry.** Everything reads and writes properties.
2. **SG-2 Compute formula.** Pure maths; unit-testable straight away (weighted average, op order).
3. **SG-4 Gear-type property keys** and **SG-30 gear-type hierarchy.** Needed for key lookup and conditions.
4. **SG-5 Harvest tiers.** Map onto `ElysiumGearMaterial.toolTier` and tags early.
5. **SG-10 Material JSON loader + SG-11 categories + SG-13 substitutes.** Data layer. Elysium's 2 existing SG-format JSONs can be the first test files.
6. **SG-20 Part types + SG-21 part definitions.**
7. **SG-22 Compound part crafting** (blueprint + N materials → part with `material_list`).
8. **SG-3 Property pipeline** (material → part → gear). This is the heart; build it now that materials and parts exist.
9. **SG-6 Property → vanilla attributes** and **SG-7 damage factor.** Makes items actually behave.
10. **SG-31 Core gear items:** start with pickaxe, shovel, axe, hoe and sword (rod + head). This reuses Elysium's shapes as the first converted items.
11. **SG-50/51 Blueprints, templates, blueprint paper, template board.**
12. **SG-60 Trait engine** (instances, conditions, level combination). Then **SG-61 effects** in order: number_property_modifier, durability, attribute, enchantment, wielder_effect, extra_damage, self_repair, negate_damage, cancel_effects, target_effect, fireproof, item_magnet, block_mining_speed, bonus_drops, block_placer, block_filler, attach_data_components, wind_blast.
13. **SG-62 trait data** (75 files) and **SG-63 hardcoded traits.**
14. **SG-70 Broken state + SG-71 repair** (quick repair, kits, anvil).
15. **SG-24 Optional parts** (tip, grip, binding, coating, lining) and **SG-72 swap/remove**, then **SG-23 upgrades.**
16. **SG-38 Armour** (+ lining, magic armour) and **SG-40 shield.**
17. **SG-33 AOE, SG-34 saw, SG-32 sickle/machete/mattock/paxel, SG-35 prospector hammer, SG-36 melee variants.** Extend Elysium's Hammer, Broadaxe and Scythe here.
18. **SG-37 Ranged** (bow, crossbow, slingshot, trident, fishing rod, arrows) and **SG-39 elytra.**
19. **SG-41 Curios gear + settings.** Plug into elysium-trinkets' CuriosSlots.
20. **SG-15 Compound materials + synergy**, then **SG-80 alloy makers** and **SG-17 custom compounds.**
21. **SG-14 Grades + SG-81 grader.** Decide the relation to ElysiumQuality: keep both or merge.
22. **SG-14 Starcharge + SG-82 starlight charger.** Needs the special-metal blocks from SG-92.
23. **SG-83 Salvager, SG-84 metal press + SG-16 sheet metal, SG-85 stone anvil + tool actions.**
24. **SG-90–95 World content** (ores, plants, loot, drops). Can be done in parallel from step 5 onward, since it has no code dependencies.
25. **SG-42 Conversions, SG-43 naming/rendering, SG-100 tooltips, SG-101 material book, SG-102 commands, SG-103 config, SG-104 nerfed gear, SG-44 compat, SG-97 advancements.** Polish.

---

# Cannot determine from jar data (took from web, inferred, or unconfirmed)

1. **Patchouli guide content:** not shipped in 4.2.1.1, so there was no "how it works" text. All mechanics come from bytecode.
2. **[web]** The exact Java of `GearData.calculate*Properties`, `CoreGearPart.getPropertyModifiers`, `MaterialInstance.getRepairValue` and `RepairKitItem` was read from GitHub branch `1.21.x`. That branch appears newer: it uses `Identifier`, a post-1.21.1 mapping. The structure matched the 4.2.1.1 bytecode call graph. The QUICK/ANVIL bonus values (0 / +0.1) were confirmed in bytecode.
3. **Default-true "paintable" and default max-per-item = 1** for part types are inferred; only non-defaults are explicit in the builder calls.
4. **Gear-type parent assignment** is inferred from registration order plus `Builder.of(parent)` constants; the "hammer→pickaxe" style mapping is plausible, not proven.
5. **Sickle/machete radii, saw limits (50/3/9/1000), prospector cost/cooldown, shears speeds, wind-blast parameters and trident damage base** are bytecode constants whose exact roles were not traced line by line.
6. **Magnet range scaling with level, moonwalker/bounce fall maths:** partially traced.
7. **Anvil repair exact formula** (whether it scales by the number of materials in the right slot).
8. **The `Config.Common` per-property bonus-multiplier map default and the client config defaults.**
9. **Paint mixer colour-mixing maths.**
10. **Food values** of nether banana / golden nether banana and yummy (yummy is in data: nutrition 10, saturation 1.0, 3.2 s, returns a stick).
11. **Gear Smith villager trades:** none found. They may come from a data file or not exist.
12. **Whether `primary_material` trait conditions work:** there is a lang key, but the condition is not registered in 4.2.1.1.
13. **The legacy/unused items** `fragment`, `glowstone/lapis/redstone_coated_upgrade` and the `star_charged` enchantment exist only as lang keys.

---
# Appendices

## Appendix M1: all 132 material files

Child materials (parent ≠ empty) inherit every property from the parent; only ingredient, colour and name differ. Types: simple / compound / custom_compound / processed. Source: `silentgear_materials/**/*.json`; names from `assets/silentgear/lang/en_us.json`.

| # | id | name | type | parent | categories | crafting ingredient | rod substitute | salvage |
|---|---|---|---|---|---|---|---|---|
| 1 | aluminum | Aluminum | simple | empty | metal, intermediate | #c:ingots/aluminum | #c:rods/aluminum | Y |
| 2 | aluminum_steel | Aluminum Steel | simple | empty | metal, advanced | #c:ingots/aluminum_steel | #c:rods/aluminum_steel | Y |
| 3 | amethyst | Amethyst | simple | empty | gem, intermediate | #c:gems/amethyst |  | Y |
| 4 | azure_electrum | Azure Electrum | simple | empty | metal, endgame | #c:ingots/azure_electrum | #c:rods/azure_electrum | Y |
| 5 | azure_silver | Azure Silver | simple | empty | metal, advanced | #c:ingots/azure_silver | #c:rods/azure_silver | Y |
| 6 | bamboo | Bamboo | simple | empty | organic, wood, basic | bamboo_planks | bamboo | Y |
| 7 | barrier | block.minecraft.barrier | simple | empty | intangible | barrier |  | N |
| 8 | basalt | Basalt | simple | empty | rock, basic | basalt |  | Y |
| 9 | bismuth | Bismuth | simple | empty | metal, intermediate | #c:ingots/bismuth | #c:rods/bismuth | Y |
| 10 | bismuth_brass | Bismuth Brass | simple | empty | metal, intermediate | #c:ingots/bismuth_brass | #c:rods/bismuth_brass | Y |
| 11 | bismuth_steel | Bismuth Steel | simple | empty | metal, advanced | #c:ingots/bismuth_steel | #c:rods/bismuth_steel | Y |
| 12 | blackstone | Blackstone | simple | empty | rock, basic | blackstone |  | Y |
| 13 | blaze_gold | Blaze Gold | simple | empty | metal, advanced | #c:ingots/blaze_gold | #c:rods/blaze_gold | Y |
| 14 | blaze_rod | Blaze Rod | simple | empty | metal | — | #c:rods/blaze | Y |
| 15 | bone | Bone | simple | empty | organic, basic | bone_block | bone | Y |
| 16 | brass | Brass | simple | empty | metal, intermediate | #c:ingots/brass | #c:rods/brass | Y |
| 17 | breeze_rod | Breeze Rod | simple | empty | metal | — | #c:rods/breeze | Y |
| 18 | bronze | Bronze | simple | empty | metal, intermediate | #c:ingots/bronze | #c:rods/bronze | Y |
| 19 | compressed_iron | Compressed Iron | simple | empty | metal, advanced | #c:ingots/compressed_iron | #c:rods/compressed_iron | Y |
| 20 | copper | Copper | simple | empty | metal, basic | #c:ingots/copper | #c:rods/copper | Y |
| 21 | crimson_iron | Crimson Iron | simple | empty | metal, advanced | #c:ingots/crimson_iron | #c:rods/crimson_iron | Y |
| 22 | crimson_steel | Crimson Steel | simple | empty | metal, endgame | #c:ingots/crimson_steel | #c:rods/crimson_steel | Y |
| 23 | crude_alloy | Crude Alloy | compound | empty |  | sg:crude_alloy |  | Y |
| 24 | crushed_shulker_shell | Shulker Shell | simple | empty | rock, dust, endgame | sg:crushed_shulker_shell |  | Y |
| 25 | diamond | Diamond | simple | empty | gem, advanced | #c:gems/diamond | #c:rods/diamond | Y |
| 26 | dimerald | Dimerald | custom_compound | empty | gem, advanced | sg:custom_gem (dimerald) |  | Y |
| 27 | electrum | Electrum | simple | empty | metal, intermediate | #c:ingots/electrum | #c:rods/electrum | Y |
| 28 | emerald | Emerald | simple | empty | gem, advanced | #c:gems/emerald | #c:rods/emerald | Y |
| 29 | end_rod | End Rod | simple | empty | metal | — | end_rod | Y |
| 30 | end_stone | End Stone | simple | empty | rock, basic | #c:end_stones |  | Y |
| 31 | enderium | Enderium | simple | empty | metal, endgame | #c:ingots/enderium | #c:rods/enderium | Y |
| 32 | example | Example | simple | empty | intangible | — |  | N |
| 33 | feather | Feather | simple | empty | organic, basic | #c:feathers |  | Y |
| 34 | fine_silk | Fine Silk | simple | empty | organic, fiber, basic | sg:fine_silk |  | Y |
| 35 | fine_silk_cloth | Fine Silk Cloth | simple | empty | organic, cloth, intermediate | sg:fine_silk_cloth |  | Y |
| 36 | flax | Flax | simple | empty | organic, fiber, basic | sg:flax_string |  | Y |
| 37 | flint | Flint | simple | empty | rock, basic | flint |  | Y |
| 38 | fluffy_string | Fluffy String | simple | empty | organic, fiber, basic | sg:fluffy_string |  | Y |
| 39 | glowstone | Glowstone | simple | empty | gem, dust, intermediate | #c:dusts/glowstone |  | Y |
| 40 | gold | Golden | simple | empty | metal, intermediate | #c:ingots/gold | #c:rods/gold | Y |
| 41 | high_carbon_steel | High-Carbon Steel | custom_compound | empty | metal, advanced | sg:custom_ingot (high_carbon_steel) |  | Y |
| 42 | hybrid_gem | Hybrid Gem | compound | empty |  | sg:hybrid_gem |  | Y |
| 43 | invar | Invar | simple | empty | metal, intermediate | #c:ingots/invar | #c:rods/invar | Y |
| 44 | iron | Iron | simple | empty | metal, intermediate | #c:ingots/iron | #c:rods/iron | Y |
| 45 | lapis_lazuli | Lapis Lazuli | simple | empty | gem, intermediate | #c:gems/lapis |  | Y |
| 46 | lead | Lead | simple | empty | metal, intermediate | #c:ingots/lead | #c:rods/lead | Y |
| 47 | leather | Leather | simple | empty | organic, cloth, basic | #c:leathers |  | Y |
| 48 | leaves | Leaf | simple | empty | organic, basic | #minecraft:leaves |  | Y |
| 49 | lumium | Lumium | simple | empty | metal, advanced | #c:ingots/lumium | #c:rods/lumium | Y |
| 50 | metal_alloy | Metal Alloy | compound | empty |  | sg:alloy_ingot |  | Y |
| 51 | mixed_fabric | Mixed Fabric | compound | empty |  | sg:mixed_fabric |  | Y |
| 52 | netherite | Netherite | simple | empty | metal, endgame | #c:ingots/netherite |  | Y |
| 53 | netherrack | Netherrack | simple | empty | rock, organic, basic | #c:netherracks |  | Y |
| 54 | netherwood | Netherwood | simple | wood | organic, wood | sg:netherwood_planks | #silentgear:rods/netherwood | Y |
| 55 | nickel | Nickel | simple | empty | metal, intermediate | #c:ingots/nickel | #c:rods/nickel | Y |
| 56 | obsidian | Obsidian | simple | empty | rock, advanced | #c:obsidians |  | Y |
| 57 | osmium | Osmium | simple | empty | metal, intermediate | #c:ingots/osmium | #c:rods/osmium | Y |
| 58 | paper | Paper | simple | empty | organic, sheet, basic | #c:paper |  | Y |
| 59 | phantom_membrane | Phantom Membrane | simple | empty | organic, cloth, intermediate | phantom_membrane |  | Y |
| 60 | platinum | Platinum | simple | empty | metal, advanced | #c:ingots/platinum | #c:rods/platinum | Y |
| 61 | prismarine | Prismarine | simple | empty | gem, organic, advanced | #c:gems/prismarine |  | Y |
| 62 | quartz | Quartz | simple | empty | gem, intermediate | #c:gems/quartz | #c:rods/quartz | Y |
| 63 | redstone | Redstone | simple | empty | metal, dust, intermediate | #c:dusts/redstone |  | Y |
| 64 | redstone_alloy | Redstone Alloy | simple | empty | metal, intermediate | #c:ingots/redstone_alloy | #c:rods/redstone_alloy | Y |
| 65 | refined_glowstone | Refined Glowstone | simple | empty | metal, advanced | #c:ingots/refined_glowstone | #c:rods/refined_glowstone | Y |
| 66 | refined_iron | Refined Iron | simple | empty | metal, intermediate | #c:ingots/refined_iron | #c:rods/refined_iron | Y |
| 67 | refined_obsidian | Refined Obsidian | simple | empty | metal, endgame | #c:ingots/refined_obsidian | #c:rods/refined_obsidian | Y |
| 68 | sandstone | Sandstone | simple | empty | rock, basic | #c:sandstone/uncolored_blocks |  | Y |
| 69 | sandstone/red | Red Sandstone | simple | sandstone |  | #c:sandstone/red_blocks |  | Y |
| 70 | sheet_metal | Sheet Metal | processed |  | sheet | sg:sheet_metal |  | Y |
| 71 | signalum | Signalum | simple | empty | metal, endgame | #c:ingots/signalum | #c:rods/signalum | Y |
| 72 | silver | Silver | simple | empty | metal, intermediate | #c:ingots/silver | #c:rods/silver | Y |
| 73 | sinew | Sinew | simple | empty | organic, fiber, basic | sg:sinew_fiber |  | Y |
| 74 | slime | Slime | simple | empty | slime, organic, intermediate | slime_block |  | Y |
| 75 | steel | Steel | simple | empty | metal, intermediate | #c:ingots/steel | #c:rods/steel | Y |
| 76 | stone | Stone | simple | empty | rock, basic | #c:cobblestones | #c:rods/stone | Y |
| 77 | stone/andesite | Andesite | simple | stone | rock, basic | andesite |  | Y |
| 78 | stone/diorite | Diorite | simple | stone | rock, basic | diorite |  | Y |
| 79 | stone/granite | Granite | simple | stone | rock, basic | granite |  | Y |
| 80 | string | String | simple | empty | organic, fiber, basic | string |  | Y |
| 81 | super_alloy | Super Alloy | compound | empty |  | sg:super_alloy |  | Y |
| 82 | terracotta | Terracotta | simple | empty | rock, basic | terracotta |  | Y |
| 83 | terracotta/black | Black Terracotta | simple | terracotta |  | black_terracotta |  | Y |
| 84 | terracotta/blue | Blue Terracotta | simple | terracotta |  | blue_terracotta |  | Y |
| 85 | terracotta/brown | Brown Terracotta | simple | terracotta |  | brown_terracotta |  | Y |
| 86 | terracotta/cyan | Cyan Terracotta | simple | terracotta |  | cyan_terracotta |  | Y |
| 87 | terracotta/gray | Gray Terracotta | simple | terracotta |  | gray_terracotta |  | Y |
| 88 | terracotta/green | Green Terracotta | simple | terracotta |  | green_terracotta |  | Y |
| 89 | terracotta/light_blue | Light Blue Terracotta | simple | terracotta |  | light_blue_terracotta |  | Y |
| 90 | terracotta/light_gray | Light Gray Terracotta | simple | terracotta |  | light_gray_terracotta |  | Y |
| 91 | terracotta/lime | Lime Terracotta | simple | terracotta |  | lime_terracotta |  | Y |
| 92 | terracotta/magenta | Magenta Terracotta | simple | terracotta |  | magenta_terracotta |  | Y |
| 93 | terracotta/orange | Orange Terracotta | simple | terracotta |  | orange_terracotta |  | Y |
| 94 | terracotta/pink | Pink Terracotta | simple | terracotta |  | pink_terracotta |  | Y |
| 95 | terracotta/purple | Purple Terracotta | simple | terracotta |  | purple_terracotta |  | Y |
| 96 | terracotta/red | Red Terracotta | simple | terracotta |  | red_terracotta |  | Y |
| 97 | terracotta/white | White Terracotta | simple | terracotta |  | white_terracotta |  | Y |
| 98 | terracotta/yellow | Yellow Terracotta | simple | terracotta |  | yellow_terracotta |  | Y |
| 99 | tin | Tin | simple | empty | metal, basic | #c:ingots/tin | #c:rods/tin | Y |
| 100 | titanium | Titanium | simple | empty | metal, endgame | #c:ingots/titanium | #c:rods/titanium | Y |
| 101 | turtle | Turtle | simple | empty | organic, intermediate | turtle_scute |  | Y |
| 102 | tyrian_steel | Tyrian Steel | simple | empty | metal, endgame | #c:ingots/tyrian_steel | #c:rods/tyrian_steel | Y |
| 103 | uranium | Uranium | simple | empty | metal, advanced | #c:ingots/uranium | #c:rods/uranium | Y |
| 104 | vine | Vine | simple | empty | organic, basic | vine |  | Y |
| 105 | wood | Wooden | simple | empty | organic, wood | #minecraft:planks | stick | Y |
| 106 | wood/acacia | Acacia Wood | simple | wood |  | acacia_planks |  | Y |
| 107 | wood/birch | Birch Wood | simple | wood |  | birch_planks |  | Y |
| 108 | wood/crimson | Crimson Wood | simple | wood |  | crimson_planks |  | Y |
| 109 | wood/dark_oak | Dark Oak Wood | simple | wood |  | dark_oak_planks |  | Y |
| 110 | wood/jungle | Jungle Wood | simple | wood |  | jungle_planks |  | Y |
| 111 | wood/oak | Oak Wood | simple | wood |  | oak_planks |  | Y |
| 112 | wood/rough | Rough Wood | simple | empty | organic, wood | — | #silentgear:rods/rough | N |
| 113 | wood/spruce | Spruce Wood | simple | wood |  | spruce_planks |  | Y |
| 114 | wood/warped | Warped Wood | simple | wood |  | warped_planks |  | Y |
| 115 | wool | Wool | simple | empty | organic, cloth, basic | #minecraft:wool |  | Y |
| 116 | wool/black | Black Wool | simple | wool |  | black_wool |  | Y |
| 117 | wool/blue | Blue Wool | simple | wool |  | blue_wool |  | Y |
| 118 | wool/brown | Brown Wool | simple | wool |  | brown_wool |  | Y |
| 119 | wool/cyan | Cyan Wool | simple | wool |  | cyan_wool |  | Y |
| 120 | wool/gray | Gray Wool | simple | wool |  | gray_wool |  | Y |
| 121 | wool/green | Green Wool | simple | wool |  | green_wool |  | Y |
| 122 | wool/light_blue | Light Blue Wool | simple | wool |  | light_blue_wool |  | Y |
| 123 | wool/light_gray | Light Gray Wool | simple | wool |  | light_gray_wool |  | Y |
| 124 | wool/lime | Lime Wool | simple | wool |  | lime_wool |  | Y |
| 125 | wool/magenta | Magenta Wool | simple | wool |  | magenta_wool |  | Y |
| 126 | wool/orange | Orange Wool | simple | wool |  | orange_wool |  | Y |
| 127 | wool/pink | Pink Wool | simple | wool |  | pink_wool |  | Y |
| 128 | wool/purple | Purple Wool | simple | wool |  | purple_wool |  | Y |
| 129 | wool/red | Red Wool | simple | wool |  | red_wool |  | Y |
| 130 | wool/white | White Wool | simple | wool |  | white_wool |  | Y |
| 131 | wool/yellow | Yellow Wool | simple | wool |  | yellow_wool |  | Y |
| 132 | zinc | Zinc | simple | empty | metal, basic | #c:ingots/zinc | #c:rods/zinc | Y |

## Appendix M2: main-part properties per material (72 materials with a `silentgear:main` block)

Columns: dur = durability, aDur = armor_durability (× slot multiplier), tier = harvest_tier(level), hSpd = harvest_speed, atk = attack_damage, aSpd = attack_speed, mDmg = magic_damage, rDmg = ranged_damage, draw = draw_speed, pSpd = projectile_speed, pAcc = projectile_accuracy, armor = set total (H/C/L/B per piece), tough = armor_toughness, kbRes = knockback_resistance, mArm = magic_armor, ench = enchantment_value, charge = charging_value, rar = rarity, repV = repair_value. Plain numbers are AVERAGE modifiers; `+x` = ADD, `×b` = MULTIPLY_BASE, `×t` = MULTIPLY_TOTAL. Trait conditions: `ratio≥x` = material_ratio, `count≥n` = material_count, `on X` = gear_type. Compound materials (crude_alloy, metal_alloy, hybrid_gem, mixed_fabric, super_alloy) have no entries: they are computed from their sub-materials (SG-15). Child materials (wool/*, terracotta/*, wood/*, stone/*, sandstone/red) inherit the parent row.

| id | dur | aDur | tier(lvl) | hSpd | atk | aSpd | mDmg | rDmg | draw | pSpd | pAcc | armor(set) | H | C | L | B | tough | kbRes | mArm | ench | charge | rar | repV | other keys | main traits |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| aluminum | 365 | 15 | iron(2) | 8 | 2 | 0.2 | 2 | 2 | 0.2 |  |  | 14 | 2 | 6 | 4 | 2 | 0.5 |  | 3 | 14 | 0.9 | 25 |  |  | malleable 3, soft 2 [ratio≥0.5], synergistic 1 |
| aluminum_steel | 660 | 18 | diamond(3) | 8 | 4 | 0.1 | 1.5 | 2 | 0 |  |  | 18 | 3 | 7 | 5 | 3 | 2 |  | 6 | 11 | 1 | 45 | -0.15 |  | malleable 4, synergistic 2 [ratio≥0.5] |
| amethyst | 210 | 10 | amethyst(1.5) | 6 | 1 | 0 | 3 | 1 | 0 | 1 | 1 | 15 | 3 | 5 | 4 | 3 |  |  | 10 | 16 | 1.3 | 35 |  |  | renew 1 [ratio≥0.7] |
| azure_electrum | 1259 | 61 | azure_electrum(4) | 29 | 7 | 0 | 11 | 3 | 0 | 2 | 1.5 | 19 | 3 | 7 | 6 | 3 | 8 |  | 19 | 37 | 1.5 | 109 | 0.5 |  | malleable 3, accelerate 3 [ratio≥0.35], light 4 [ratio≥0.5] |
| azure_silver | 197 | 17 | azure_silver(3) | 19 | 5 | 0 | 7 | 2 | 0 | 1.2 | 1.1 | 13 | 2 | 5 | 4 | 2 |  |  | 13 | 29 | 1.4 | 83 | 0.5 |  | malleable 3, soft 2, moonwalker 4 [ratio≥0.5] |
| bamboo | 65 | 9 | bamboo(0) | 2 | 0 | 0 |  | 0 | 0 | 1 | 0.9 | 7 | 1 | 3 | 2 | 1 |  |  | 2 | 15 | 0.7 | 4 | 0.25 | attack_speed/axe=-0.2, attack_speed/hoe=-2 | flammable 1, flexible 2 |
| barrier | 1337 | 84 | wood(0) | 5 | 1 | 0 | 1 | 1 | 0 |  |  | 20 | 3 | 8 | 6 | 3 | 10 |  | 10 | 5 | 0.5 | 111 | -1 |  | adamant 5, holy 5 |
| basalt | 137 | 4 | basalt(1) | 4 | 1 | 0 |  | 0 | -0.1 | 1 | 0.8 | 6 | 1 | 3 | 1 | 1 |  |  |  | 6 | 0.7 | 7 |  | attack_speed/axe=-0.2, attack_speed/hoe=-1 | brittle 2, chipping 3 |
| bismuth | 330 | 10 | iron(2) | 5 | 2.5 | 0.1 | 3.5 | 1 | 0 |  |  | 16 | 3 | 6 | 5 | 2 | 2 |  | 6 | 12 | 0.9 | 35 |  |  | malleable 2, lustrous 2 [ratio≥0.5] |
| bismuth_brass | 580 | 15 | iron(2) | 9 | 3 | 0.1 | 2 | 2 | 0.2 |  |  | 18 | 3 | 7 | 5 | 3 | 2 |  | 8 | 14 | 1.1 | 50 | 0.15 |  | malleable 2, lustrous 2 [ratio≥0.5] |
| bismuth_steel | 1050 | 25 | diamond(3) | 10 | 4 | -0.1 | 5 | 3 | 0.1 |  |  | 20 | 3 | 8 | 6 | 3 | 4 |  | 8 | 14 | 1 | 60 | -0.15 |  | malleable 3, lustrous 2 [ratio≥0.5] |
| blackstone | 151 | 5 | blackstone(1) | 4 | 1 | 0 |  | 0 | -0.2 | 1 | 0.8 | 5 | 1 | 2 | 1 | 1 |  |  |  | 4 | 0.6 | 9 |  | attack_speed/axe=-0.2, attack_speed/hoe=-1 | brittle 1, jagged 2, hard 2 |
| blaze_gold | 69 | 9 | blaze_gold(2) | 15 | 2 | 0.1 | 5 | 1 | 0.2 | 1.2 | 0.9 | 13 | 2 | 5 | 4 | 2 | 1 |  | 10 | 24 | 1.2 | 45 |  |  | brilliant 1 [ratio≥0.7], greedy 3, malleable 3 |
| bone | 108 | 4 | bone(1) | 4 | 2 | 0.1 | 1 | 1 | 0 | 0.9 | 1 | 5 | 1 | 2 | 1 | 1 |  |  | 1 | 5 | 0.9 | 8 |  |  | chipping 2 |
| brass | 240 | 8 | iron(2) | 7 | 1.5 | 0.1 | 1.5 | 2 | 0.2 |  |  | 14 | 2 | 6 | 4 | 2 | 1 |  | 6 | 13 | 1.2 | 25 | 0.15 |  | malleable 2, silky 1 [ratio≥0.66] |
| bronze | 300 | 13 | bronze(2) | 6 | 2.5 | 0.2 | 1 | 2 | -0.2 |  |  | 15 | 3 | 6 | 4 | 2 | 1 |  | 6 | 12 | 1.1 | 15 | 0.15 |  | sharp 1 |
| compressed_iron | 1024 | 24 | diamond(3) | 9 | 4 | -0.2 | 1 | 3 | 0 |  |  | 20 | 3 | 8 | 6 | 3 | 2 |  | 4 | 12 | 0.8 | 40 | -0.15 |  | malleable 2, hard 3 |
| copper | 151 | 12 | copper(1.5) | 5 | 1.5 | 0.1 | 1 | 0.1 | 0 |  |  | 10 | 2 | 4 | 3 | 1 |  |  | 8 | 15 | 1.3 | 12 |  | attack_speed/axe=-0.1, attack_speed/hoe=0 | soft 1 [ratio≥0.5] |
| crimson_iron | 420 | 27 | crimson_iron(3) | 10 | 3 | -0.1 | 3 | 2 | -0.1 | 1 | 1.1 | 18 | 3 | 7 | 5 | 3 | 2 |  | 6 | 14 | 0.7 | 31 | 0.5 |  | malleable 3, hard 2, heat_resistant 4 |
| crimson_steel | 2400 | 42 | crimson_steel(4) | 15 | 6 | -0.1 | 6 | 3 | -0.1 | 1 | 1.3 | 22 | 4 | 8 | 6 | 4 | 10 |  | 10 | 19 | 0.9 | 83 | 0.5 |  | malleable 5, hard 3, flame_ward 1 [(count≥3 OR ratio≥0.33)] |
| crushed_shulker_shell | 2500 | 50 |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | 10 | -0.15 | 91 |  | additive=1 | eroded 4 |
| diamond | 1561 | 33 | diamond(3) | 8 | 3 | 0 | 1 | 2 | -0.2 | 0.9 | 1.1 | 20 | 3 | 8 | 6 | 3 | 8 |  | 4 | 10 | 0.8 | 70 |  | attack_speed/hoe=1 | brittle 2, lustrous 1 [(count≥3 OR ratio≥0.5)] |
| dimerald | 1776 | 36 | dimerald(3) | 9 | 3 | 0.1 | 3 | 3 | 0.1 | 1 | 1.2 | 22 | 4 | 9 | 6 | 3 | 10 |  | 10 | 12 | 0.7 | 80 |  |  | brittle 1, gold_digger 2 [(count≥3 OR ratio≥0.5)] |
| electrum | 96 | 10 | iron(2) | 14 | 2 | 0.3 | 5 | 1 | 0.4 |  |  | 15 | 2 | 6 | 5 | 2 |  |  | 11 | 25 | 1.5 | 40 |  |  | malleable 2, soft 2 [ratio≥0.5] |
| emerald | 1080 | 24 | emerald(2) | 10 | 2 | 0 | 2 | 1 | -0.1 | 1.1 | 0.9 | 16 | 3 | 6 | 4 | 3 | 4 |  | 6 | 16 | 1 | 40 | 0.25 | attack_speed/hoe=1 | brittle 1, synergistic 2 |
| end_stone | 1164 | 15 | end_stone(1) | 7 | 2 | 0.1 | 3 | 0 | 0 | 1 | 0.8 | 15 | 3 | 5 | 4 | 3 | 1 |  | 6 | 10 | 0.9 | 32 | -0.2 |  | jagged 3, ancient 2 |
| enderium | 1200 | 34 | netherite(4) | 18 | 6 | 0 | 4 | 3 | 0 |  |  | 22 | 3 | 9 | 7 | 3 | 8 |  | 10 | 13 | 1.2 | 80 | 0.15 |  | malleable 3 |
| example | 100 | 6 | wood(0) | 1 | 1 | 0 | 1 | 0 | 0 |  |  | 4 | 1 | 1 | 1 | 1 | 1 |  | 1 | 1 | 1 | 0 |  |  |  |
| feather | 0 | 2 |  |  |  |  |  |  |  |  |  | 3 | 0.5 | 1 | 1 | 0.5 |  |  |  | 9 | 1.1 | 1 |  |  | flutter 1 |
| fine_silk_cloth | 0 | 14 |  |  |  |  |  |  |  |  |  | 6 | 1 | 2 | 2 | 1 |  |  | 14 | 14 | 0.9 | 40 |  | armor_durability/elytra=18 | snow_walker 1 [on boots] |
| flint | 124 | 4 | flint(1) | 5 | 2 | -0.1 |  | 1 | -0.3 | 1 | 1 | 4 | 0.5 | 2 | 1 | 0.5 |  |  |  | 3 | 0.8 | 6 |  |  | jagged 3 |
| glowstone |  |  |  | 15 | 3 | 0.4 |  | 3 |  |  |  |  |  |  |  |  |  |  |  |  | ×b0.15 | 40 |  | additive=1 | renew 3 |
| gold | 32 | 7 | gold(1) | 12 | 0 | 0 | 4 | 0 | 0.3 | 1.1 | 1 | 11 | 2 | 5 | 3 | 1 |  |  | 8 | 22 | 1.2 | 50 |  | attack_speed/hoe=-2 | brilliant 1 [ratio≥0.7], malleable 1, soft 3 |
| high_carbon_steel | 420 | 24 | high_carbon_steel(2) | 6 | 4 | -0.2 | 1 | 2 | -0.2 |  |  | 20 | 3 | 8 | 6 | 3 | 2 |  | 6 | 11 | 0.8 | 40 | 0.05 |  | malleable 3 |
| invar | 640 | 20 | iron(2) | 7 | 3.5 | 0 | 3 | 2 | 0 |  |  | 18 | 2 | 8 | 6 | 2 | 2 |  | 6 | 13 | 1.2 | 50 | 0.15 |  | malleable 3, adamant 2 [(count≥3 OR ratio≥0.35)] |
| iron | 250 | 15 | iron(2) | 6 | 2 | 0 | 1 | 1 | 0.1 | 1 | 1.1 | 15 | 2 | 6 | 5 | 2 |  |  | 6 | 14 | 0.7 | 20 |  | attack_speed/axe=-0.1, attack_speed/hoe=0 | malleable 3 |
| lapis_lazuli | 200 | 13 | lapis_lazuli(1.5) | 5 | 2 | 0 | 3 | 0 | -0.1 | 1 | 0.8 | 15 | 2 | 6 | 5 | 2 |  |  | 10 | 17 | 1.3 | 30 |  |  |  |
| lead | 260 | 14 | iron(2) | 4 | 2 | -0.4 | 2 | 1 | -0.3 |  |  | 13 | 2 | 5 | 4 | 2 |  |  | 4 | 15 | 0.8 | 40 |  |  | malleable 2, aquatic 2 [(count≥3 OR ratio≥0.35)] |
| leather | 0 | 5 |  |  |  |  |  |  |  |  |  | 7 | 1 | 3 | 2 | 1 |  |  | 8 | 15 | 0.8 | 11 |  |  | snow_walker 1 [on boots] |
| leaves | 0 | 2 |  |  |  |  |  |  |  |  |  | 3 | 0.5 | 1 | 1 | 0.5 |  |  |  | 12 | 1.1 | 1 |  |  | flammable 1, light 1 |
| lumium | 920 | 20 | diamond(3) | 15 | 4 | 0.2 | 3 | 2 | 0 |  |  | 18 | 2 | 8 | 6 | 2 | 4 |  | 10 | 14 | 1.3 | 75 | 0.15 |  | malleable 3, refractive 1 [ratio≥0.5] |
| netherrack | 142 | 5 | netherrack(1) | 5 | 0.5 | 0 | 0.5 | 0.5 | 0.1 | 0.8 | 1 | 8 | 1 | 4 | 2 | 1 |  |  | 4 | 8 | 0.8 | 11 | -0.2 |  | eroded 3, flexible 2 |
| netherwood | 72 | 12 | netherwood(0) | 2 | 0 | 0.2 |  | 0 | 0 | 1 | 0.8 | 8 | 1 | 4 | 2 | 1 |  |  | 6 | 13 | 0.7 | 4 | 0.1 | repair_efficiency=0.5 | flexible 3, jagged 2 |
| nickel | 380 | 17 | iron(2) | 7 | 2.5 | 0.1 | 1 | 0.5 | 0.1 |  |  | 13 | 2 | 5 | 4 | 2 |  |  | 6 | 12 | 1 | 40 |  |  | malleable 3, adamant 1 [(count≥3 OR ratio≥0.35)] |
| obsidian | 1024 | 13 | obsidian(1) | 6 | 3 | -0.4 | 2 | 0 | -0.4 | 0.7 | 0.7 | 20 | 3 | 8 | 6 | 3 | 4 |  | 8 | 7 | 0.6 | 10 | -0.25 |  | jagged 3, crushing 2 |
| osmium | 500 | 30 | iron(2) | 10 | 4 | 0.1 | 2 | 1 | 0 |  |  | 17 | 3 | 6 | 5 | 3 |  |  | 4 | 12 | 1.1 | 35 |  |  | malleable 2 |
| paper | 0 | 2 |  |  |  |  |  |  |  |  |  | 4 | 1 | 1 | 1 | 1 |  |  |  | 11 | 1 | 3 |  |  | flammable 1, rustic 3 |
| phantom_membrane | 0 | 12 |  |  |  |  |  |  |  |  |  | 6 | 1 | 2 | 2 | 1 |  |  | 8 | 10 | 0.7 | 35 |  | armor_durability/elytra=17 | renew 2 [ratio≥0.5] |
| platinum | 900 | 21 | diamond(3) | 12 | 4 | 0 | 4 | 1 | 0 |  |  | 18 | 2 | 8 | 6 | 2 | 2 |  | 12 | 14 | 1.2 | 80 |  |  | malleable 3, soft 2 |
| quartz | 330 | 13 | quartz(2) | 7 | 2 | 0.1 |  | 0 | 0.1 | 1 | 1 | 14 | 3 | 5 | 4 | 2 |  |  | 4 | 10 | 1.2 | 40 |  |  | crushing 3, jagged 2 |
| redstone |  |  |  | 12 | 4 |  |  | 2 |  |  |  |  |  |  |  |  |  |  |  | ×b-0.1 | ×b0.1 | 30 |  | additive=1 | imperial 3 |
| redstone_alloy | 840 | 20 | iron(2) | 11 | 2 | 0.2 | 4 | 1 | 0.2 |  |  | 17 | 3 | 7 | 5 | 2 | 1 |  | 8 | 18 | 0.9 | 45 | -0.15 |  | malleable 3, eroded 3 |
| refined_glowstone | 300 | 18 | iron(2) | 14 | 5 | 0 | 3 | 3 | 0 |  |  | 19 | 3 | 7 | 6 | 3 |  |  | 8 | 18 | 0.8 | 45 | 0.15 |  | malleable 2, lustrous 4 |
| refined_iron | 512 | 20 | iron(2) | 7 | 3 | 0 | 2 | 2 | 0 |  |  | 18 | 3 | 7 | 5 | 3 | 2 |  | 6 | 15 | 0.8 | 25 | -0.15 |  | malleable 4, stellar 1 |
| refined_obsidian | 2500 | 50 | diamond(3) | 20 | 10 | 0.3 | 4 | 4 | 0.2 |  |  | 30 | 5 | 12 | 8 | 5 | 16 |  | 6 | 40 | 0.8 | 70 | -0.3 |  | malleable 3, hard 4 |
| sandstone | 117 | 6 | sandstone(1) | 4 | 1 | 0.1 |  | 0 | -0.1 | 1 | 0.8 | 5 | 1 | 2 | 1 | 1 |  |  |  | 7 | 0.7 | 7 | -0.1 | attack_speed/axe=-0.1, attack_speed/hoe=-1 |  |
| sheet_metal | ×t-1 | ×t-0.4 |  |  |  |  |  |  |  |  |  | ×t-0.5 |  |  |  |  | ×t-0.5 |  |  |  |  |  |  |  |  |
| signalum | 800 | 25 | diamond(3) | 13 | 3 | 0 | 4 | 2 | 0.2 |  |  | 16 | 2 | 7 | 5 | 2 | 2 |  | 4 | 16 | 1.2 | 50 | 0.15 |  | flexible 2, lustrous 4 |
| silver | 64 | 9 | gold(0) | 11 | 0 | 0.2 | 4 | 0 | 0.3 |  |  | 11 | 2 | 5 | 3 | 1 |  |  | 10 | 20 | 1.1 | 40 |  |  | malleable 2, soft 1 |
| steel | 500 | 20 | iron(2) | 6 | 4 | -0.2 | 1 | 2 | -0.2 |  |  | 20 | 3 | 8 | 6 | 3 | 2 |  | 6 | 11 | 0.8 | 40 | 0.15 |  | malleable 5 |
| stone | 131 | 5 | stone(1) | 4 | 1 | 0 |  | 0 | -0.2 | 1 | 0.8 | 5 | 1 | 2 | 1 | 1 |  |  |  | 5 | 0.5 | 4 |  | attack_speed/axe=-0.2, attack_speed/hoe=-1 | ancient 1 |
| terracotta | 165 | 11 | terracotta(1) | 4 | 1.5 | 0.2 |  | 0 | -0.2 | 1 | 0.9 | 9 | 2 | 3 | 3 | 1 |  |  | 3 | 9 | 0.8 | 7 | -0.1 |  | brittle 1, chipping 2, rustic 1 |
| tin | 192 | 13 | stone(1) | 5 | 1.5 | 0 | 1 | 0.5 | 0 |  |  | 12 | 2 | 5 | 3 | 2 |  |  | 2 | 12 | 1.1 | 15 |  |  | malleable 1, soft 2 |
| titanium | 1600 | 37 | netherite(4) | 8 | 6 | 0 | 1 | 1 | -0.2 |  |  | 24 | 4 | 9 | 7 | 4 | 8 |  | 4 | 12 | 1 | 80 |  |  | malleable 2, hard 4 |
| turtle | 0 |  |  |  |  |  |  |  |  |  |  |  | 2 |  |  |  |  |  | 4 | 9 | 0.5 | 20 |  | armor_durability/helmet=25 | turtle 1 [count≥2] |
| tyrian_steel | 3652 | 81 | tyrian_steel(4) | 18 | 8 | 0 | 6 | 4 | 0 | 1.1 | 1.1 | 25 | 5 | 9 | 7 | 4 | 12 |  | 12 | 16 | 1.1 | 100 |  |  | sturdy 3 [ratio≥0.5], void_ward 1 [(count≥3 OR ratio≥0.5)] |
| uranium | 800 | 20 | diamond(3) | 6 | 2 | 0.1 | 2 | 0.5 | 0.1 |  |  | 13 | 2 | 5 | 4 | 2 | 1 |  | 3 | 17 | 1.5 | 50 | -0.15 |  | malleable 2 |
| wood | 59 | 8 | wood(0) | 2 | 0 | 0 |  | 0 | 0 | 1 | 0.9 | 7 | 1 | 3 | 2 | 1 |  |  | 2 | 15 | 0.6 | 1 | 0.25 | attack_speed/axe=-0.2, attack_speed/hoe=-2 | flammable 1, jagged 1 |
| wood/crimson |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | jagged 1 |
| wood/warped |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | jagged 1 |
| wool | 0 | 4 |  |  |  |  |  |  |  |  |  | 4 | 0.5 | 2 | 1 | 0.5 |  |  | 4 | 7 | 0.7 | 7 |  |  | snow_walker 1 [on boots] |
| zinc | 192 | 10 | stone(1) | 3 | 1.5 | 0 | 1 | 0 | 0 |  |  | 10 | 1 | 5 | 3 | 1 |  |  | 2 | 15 | 1.1 | 10 |  |  | malleable 1, soft 2 |

## Appendix M3: non-main part-type properties per material (rod, tip, binding, grip, cord, fletching, lining, coating, setting)

| material | part type | property modifiers | traits |
|---|---|---|---|
| example | binding |  |  |
| fine_silk | binding | magic_armor +2 | lucky 1, flexible 4 |
| flax | binding | armor_durability ×b0.05, harvest_speed ×b0.05 | flexible 3 |
| fluffy_string | binding | armor_durability ×b0.05, harvest_speed ×b-0.05 | flexible 3 |
| sinew | binding | repair_efficiency ×b-0.05 | flexible 4 |
| string | binding | repair_efficiency ×b0.05 | flexible 1 |
| vine | binding | repair_efficiency ×b0.03 |  |
| blaze_gold | coating | armor_durability ×t-0.05, durability ×t-0.05, rarity +20 | brilliant 1, soft 2 |
| example | coating |  |  |
| gold | coating | armor_durability ×t-0.1, durability ×t-0.1, rarity +10 | brilliant 1, soft 3 |
| netherite | coating | armor_durability ×t0.121212, armor_toughness +4, attack_damage ×t0.333333, durability ×t0.3 & +2, enchantment_value +5, harvest_speed ×t0.125, harvest_tier netherite(4), knockback_resistance +1, magic_damage ×t0.333333, ranged_damage ×t0.333333 | fireproof 1 |
| prismarine | coating | armor_durability ×t0.125, armor_toughness +1, durability ×t0.075, knockback_resistance +0.25 | aquatic 5 [ratio≥0.67], aquatic 3 [NOT ratio≥0.67] |
| example | cord |  |  |
| fine_silk | cord | ranged_damage ×b0.07 | flexible 2 |
| flax | cord | draw_speed ×b0.2, ranged_damage ×b-0.1 |  |
| fluffy_string | cord | draw_speed ×b-0.05, ranged_damage ×b0.05 |  |
| sinew | cord | ranged_damage ×b0.2 | flexible 2 |
| string | cord | draw_speed ×b0.1 |  |
| vine | cord | ranged_damage ×b-0.1 |  |
| example | fletching |  |  |
| feather | fletching | projectile_accuracy 1.1, projectile_speed 0.9 |  |
| leaves | fletching | projectile_accuracy 0.9, projectile_speed 1.1 |  |
| paper | fletching | projectile_accuracy 0.9, projectile_speed 1.1 |  |
| example | grip |  |  |
| fine_silk_cloth | grip | harvest_speed ×b0.15, repair_efficiency ×b0.1 | accelerate 1 |
| leather | grip | attack_speed +0.15, harvest_speed ×b0.15, repair_efficiency ×b0.1 | flexible 3 |
| phantom_membrane | grip | attack_speed +0.2, harvest_speed ×b0.1, repair_efficiency ×b0.15 | ancient 2 |
| wool | grip | attack_speed +0.2, harvest_speed ×b0.1, repair_efficiency ×b0.2 | flexible 2 |
| example | lining |  |  |
| fine_silk_cloth | lining | magic_armor +2 | flexible 4 |
| leather | lining |  | flexible 4 |
| phantom_membrane | lining |  | light 2, flexible 3 |
| slime | lining | armor_toughness +0.5 | bounce 1 |
| wool | lining | knockback_resistance +0.1 | flexible 2 |
| aluminum | rod | durability ×t0.2, ranged_damage ×t0.1, rarity 30 | malleable 3, synergistic 2 |
| aluminum_steel | rod | durability ×t0.3, harvest_speed ×t0.1, rarity 45 | malleable 3, synergistic 3 |
| azure_electrum | rod | harvest_speed +3, projectile_speed ×t1.5 | flexible 2, accelerate 5 [ratio≥0.66] |
| azure_silver | rod | durability ×t-0.2, harvest_speed +3 | bending 2 |
| bamboo | rod |  | flexible 3 |
| basalt | rod | attack_damage ×t-0.1, harvest_speed ×t0.1 | brittle 3, chipping 2 |
| bismuth | rod | durability ×t0.1, harvest_speed ×t0.1, rarity 35 | malleable 2 |
| bismuth_brass | rod | durability ×t-0.1, harvest_speed ×t0.2, rarity 50 | malleable 2 |
| bismuth_steel | rod | durability ×t0.25, harvest_speed ×t0.15, rarity 60 | malleable 3 |
| blackstone | rod | harvest_speed ×t-0.1 | brittle 1, jagged 2, hard 1 |
| blaze_gold | rod | harvest_speed ×t0.1 | flexible 3 |
| blaze_rod | rod | attack_damage ×t0.2, harvest_speed ×t0.1, ranged_damage ×t0.1 | flexible 4, reach 1 |
| bone | rod | attack_damage ×t0.2 | flexible 2 |
| brass | rod | durability ×t0.05, harvest_speed ×t0.05, rarity 25 | malleable 2 |
| breeze_rod | rod | attack_damage ×t0.1, harvest_speed ×t0.2, ranged_damage ×t0.2 | flexible 4, wind_blast 1 |
| bronze | rod | attack_damage ×t0.05 | flexible 1 |
| compressed_iron | rod | durability ×t0.25, rarity 40 | malleable 2 |
| copper | rod | harvest_speed ×t0.2 | bending 3, soft 3 |
| crimson_iron | rod | attack_damage ×t0.1 | flexible 3 |
| crimson_steel | rod | harvest_speed ×t0.2, ranged_damage +1 | flexible 4, sturdy 1 [ratio≥0.5] |
| diamond | rod | harvest_speed ×t0.2 | brittle 5 [ratio≥0.5], lustrous 4 [ratio≥0.5] |
| dimerald | rod | harvest_speed ×t0.15 | brittle 1 [ratio≥0.5], ancient 3 [ratio≥0.5] |
| electrum | rod | attack_speed +0.1, draw_speed ×t0.1, rarity 40 | lustrous 3 [ratio≥0.5] |
| emerald | rod | harvest_speed ×t0.3 | brittle 4 [ratio≥0.5] |
| end_rod | rod | attack_damage ×t0.1, harvest_speed ×t0.2, ranged_damage ×t0.1 | flexible 4, sturdy 2, refractive 1 |
| end_stone | rod | attack_damage +1 | malleable 1, ancient 4 |
| enderium | rod | durability ×t0.2, harvest_speed ×t0.2, rarity 60 | malleable 2 |
| example | rod |  |  |
| flint | rod |  | brittle 3, jagged 2 |
| gold | rod | harvest_speed +3 | bending 4 |
| invar | rod | attack_damage ×t0.1, durability ×t0.3, rarity 50 | malleable 3 |
| iron | rod |  | flexible 2 |
| lead | rod | durability ×t0.1, harvest_speed ×t-0.1, rarity 40 | soft 4 |
| lumium | rod | harvest_speed ×t0.2, ranged_damage ×t0.1, rarity 75 | malleable 3, refractive 1 [ratio≥0.5] |
| netherrack | rod |  | eroded 2, bending 2 |
| netherwood | rod | attack_damage +0.5, harvest_speed +1 | flexible 1 |
| nickel | rod | attack_damage ×t0.1, durability ×t0.2, rarity 40 | malleable 3 |
| obsidian | rod | harvest_speed ×t-0.1 | brittle 2, chipping 3 |
| osmium | rod | durability ×t0.1, rarity 35, repair_efficiency ×t0.1 | malleable 2 |
| platinum | rod | durability ×t-0.1, rarity 70, repair_efficiency ×t0.1 | malleable 2, soft 4 |
| quartz | rod | attack_damage ×t0.1, harvest_speed ×t0.2 | brittle 3 |
| redstone_alloy | rod | harvest_speed ×t0.2, rarity 45 | malleable 3 |
| refined_glowstone | rod | durability ×t-0.1, harvest_speed ×t0.2, rarity 45 | malleable 4, lustrous 1 [ratio≥0.75] |
| refined_iron | rod | durability ×t0.15, ranged_damage ×t0.1, rarity 25 | malleable 4 |
| refined_obsidian | rod | durability ×t0.25, rarity 70 | malleable 4, hard 3 |
| sandstone | rod |  | brittle 1 |
| signalum | rod | harvest_speed ×t0.3, rarity 50, repair_efficiency ×t-0.1 | flexible 4, lustrous 2 |
| silver | rod | harvest_speed ×t0.1, magic_damage ×t0.1, rarity 40 | malleable 1, soft 2 |
| steel | rod | durability ×t0.25, rarity 40 | malleable 5 |
| stone | rod | harvest_speed ×t0.1 | brittle 2, crushing 2 |
| terracotta | rod | attack_damage ×t0.1 | brittle 2, crushing 1 |
| tin | rod | attack_speed +0.2, rarity 15 | malleable 2, soft 1 |
| titanium | rod | attack_damage ×t0.2 & +2, durability ×t0.1, rarity 80 | flexible 2, hard 4 |
| tyrian_steel | rod | harvest_speed +-2 | sturdy 4 [ratio≥0.5] |
| uranium | rod | draw_speed ×t0.1, harvest_speed ×t0.1, rarity 40 | malleable 2 |
| wood | rod |  | flexible 2 |
| wood/crimson | rod |  | flexible 2 |
| wood/rough | rod |  | crude 3, bending 5 |
| wood/warped | rod |  | flexible 2 |
| zinc | rod | attack_damage ×t0.05, durability ×t-0.05, rarity 15 | malleable 2, soft 3 |
| amethyst | setting |  | cursed 4 |
| diamond | setting |  | bastion 1 |
| dimerald | setting |  | kitty_vision 1 |
| emerald | setting |  | reach 2 |
| example | setting |  |  |
| flint | setting |  |  |
| lapis_lazuli | setting |  | lucky 3 [ratio≥0.75] |
| prismarine | setting |  | swift_swim 3 [ratio≥0.67] |
| quartz | setting |  | mighty 2 [ratio≥0.5] |
| amethyst | tip | durability ×t-0.25, harvest_tier amethyst(1.5) | silky 1 [ratio≥0.66] |
| azure_electrum | tip | armor_durability +11, durability +401, harvest_speed +5, harvest_tier azure_electrum(4) | malleable 3 |
| azure_silver | tip | armor_durability +3, attack_speed +0.2, durability +83, harvest_speed +3, harvest_tier azure_silver(3), magic_damage +2, rarity +31 | malleable 2, fortunate 3 |
| blaze_gold | tip | armor_durability +3, attack_damage +1, durability +32, harvest_speed +4, harvest_tier blaze_gold(2), magic_damage +1, rarity +14 | soft 2, fiery 2 |
| bronze | tip | armor_durability +2, attack_damage +0.75, attack_speed +0.1, durability +96, harvest_speed +0.75, harvest_tier bronze(2), rarity +6 | malleable 2 |
| copper | tip | armor_durability +1, durability +32, harvest_speed +1, harvest_tier copper(1.5), rarity +4 | malleable 1, dulling 1 |
| crimson_iron | tip | armor_durability +8, attack_damage +2, durability +224, harvest_speed +2, harvest_tier crimson_iron(3), rarity +10 | fiery 1 |
| crimson_steel | tip | armor_durability +16, durability +448, harvest_tier crimson_steel(4), rarity +20 | magmatic 1 |
| diamond | tip | armor_durability +9, attack_damage +2, durability +256, harvest_speed +2, harvest_tier diamond(3), magic_damage +1, ranged_damage +0.5, rarity +20 | brittle 2, lustrous 2 |
| dimerald | tip | attack_damage +2, durability +360, harvest_speed +2, magic_damage +1, ranged_damage +0.5, rarity +25 | imperial 2 |
| emerald | tip | armor_durability +12, attack_damage +1, durability +512, harvest_speed +2, harvest_tier emerald(2), magic_damage +2, ranged_damage +1, rarity +20 | brittle 1, synergistic 2 |
| example | tip |  |  |
| glowstone | tip | attack_damage +2, draw_speed ×t0.3, harvest_speed ×t0.4, magic_damage +2, rarity +15 | refractive 1, lustrous 4 |
| gold | tip | armor_durability +1, draw_speed +0.2, durability +16, harvest_speed +6, magic_damage +2, rarity +30 | malleable 1, soft 3 |
| iron | tip | armor_durability +4, attack_damage +1, draw_speed +0.2, durability +128, harvest_speed +1, harvest_tier iron(2), rarity +8 | malleable 2 |
| lapis_lazuli | tip | attack_speed +0.3, harvest_speed ×t-0.1, harvest_tier lapis_lazuli(1.5), magic_damage +2 | holy 1 [ratio≥0.75], lucky 4 [ratio≥0.75] |
| quartz | tip | armor_durability +4, attack_damage +4, durability +64, harvest_speed +2, harvest_tier quartz(2), ranged_damage +1.5, rarity +20 | chipping 1, jagged 3 |
| redstone | tip | attack_damage +2, attack_speed +0.3, harvest_speed ×t0.2, ranged_damage +2, rarity +10 |  |
| refined_glowstone | tip | harvest_speed +5 | refractive 1 |
| refined_obsidian | tip | durability +600 | vulcan 1 |
| tyrian_steel | tip | durability +251, harvest_tier tyrian_steel(4), rarity +30 | imperial 3, gold_digger 3 |

## Appendix P1: all 48 part definitions

Source: `silentgear_parts/*.json` and material counts from `recipe/gear/*`, `recipe/part/*` (compound_part).

| part id | class | gear type | part type | materials in recipe (→count) | extra ingredients | part property modifiers | traits | upgrade applies to |
|---|---|---|---|---|---|---|---|---|
| arrow_heads | core | arrow | main | 1 |  | enchantment_value ×total 0 |  |  |
| axe_head | core | axe | main | 3 |  | attack_damage +5; attack_speed +1; repair_efficiency 1 |  |  |
| binding | core | all | binding | 1 |  |  |  |  |
| boot_plates | core | boots | main | 4 |  | repair_efficiency 1 |  |  |
| bow_limbs | core | bow | main | 3 |  | draw_speed +1; enchantment_value ×base -0.45; ranged_damage +1; repair_efficiency 1 |  |  |
| bracelet_band | core | bracelet | main | 3 (cat metal) |  |  |  |  |
| chestplate_plates | core | chestplate | main | 8 |  | repair_efficiency 1 |  |  |
| coating | core | all (blacklist elytra) | coating | 1 | glass_bottle |  |  |  |
| cord | core | ranged_weapon | cord | 3 |  |  |  |  |
| crossbow_limbs | core | crossbow | main | 3 |  | draw_speed +1; enchantment_value ×base -0.45; ranged_damage +2; repair_efficiency 1 |  |  |
| dagger_blade | core | dagger | main | 1 |  | attack_damage +2 & ×base -0.5; attack_speed +2.8; repair_efficiency 2 |  |  |
| elytra_wings | core | elytra | main | 6 (cat cloth,sheet) |  | armor ×base -0.65 & +-3.5; repair_efficiency 1 |  |  |
| excavator_head | core | excavator | main | 5 |  | attack_damage +2; attack_speed +1; durability ×base 1; enchantment_value ×base -0.5; harvest_speed ×base -0.5; repair_efficiency 2 |  |  |
| fishing_reel_and_hook | core | fishing_rod | main | 2 |  | attack_speed +4; durability ×base -0.5; enchantment_value ×base -0.75; repair_efficiency 1.25 |  |  |
| fletching | core | projectile | fletching | 1 |  |  |  |  |
| grip | core | tool | grip | 1 |  |  |  |  |
| hammer_head | core | hammer | main | 6 |  | attack_damage +4; attack_speed +0.8; durability ×base 1; enchantment_value ×base -0.5; harvest_speed ×base -0.5; repair_efficiency 1.5 |  |  |
| helmet_plates | core | helmet | main | 5 |  | repair_efficiency 1 |  |  |
| hoe_head | core | hoe | main | 2 |  | attack_damage ×base -1; attack_speed +3; repair_efficiency 1 |  |  |
| katana_blade | core | katana | main | 3 |  | attack_damage +4; attack_speed +1.4; durability ×base 0.125; enchantment_value ×base -0.1; repair_efficiency 1 |  |  |
| knife_blade | core | knife | main | 1 |  | attack_damage +1 & ×base -0.5; attack_speed +2.4; durability ×base 0.25; repair_efficiency 2 |  |  |
| legging_plates | core | leggings | main | 7 |  | repair_efficiency 1 |  |  |
| lining | core | armor | lining | 1 |  |  |  |  |
| mace_core | core | mace | main | 3 | heavy_core | attack_damage +3; attack_speed +0.6; durability ×base 1; repair_efficiency 1 |  |  |
| machete_blade | core | machete | main | 3 |  | attack_damage +2; attack_speed +1.8; harvest_speed ×base 0.4; repair_efficiency 1 |  |  |
| magnetic_upgrade | upgrade | all | misc_upgrade | — (crafted item) |  |  | magnetic 5 | all (+children) |
| mattock_head | core | mattock | main | 4 |  | attack_damage +1; attack_speed +1.4; durability ×base 0.25; enchantment_value ×base -0.25; harvest_speed ×base -0.25; repair_efficiency 1.25 |  |  |
| necklace_chain | core | necklace | main | 3 (cat metal) |  |  |  |  |
| paxel_head | core | paxel | main | 5 |  | attack_damage +3; attack_speed +1; durability ×base 0.35; enchantment_value ×base -0.3; harvest_speed ×base -0.2; repair_efficiency 1.2 |  |  |
| pickaxe_head | core | pickaxe | main | 3 |  | attack_damage +1; attack_speed +1.2; repair_efficiency 1 |  |  |
| prospector_hammer_head | core | prospector_hammer | main | 2 |  | attack_damage +2; attack_speed +1.4; durability ×base -0.25; enchantment_value ×base -0.25; harvest_speed ×base -0.25; repair_efficiency 1.5 |  |  |
| red_card_upgrade | upgrade | all | misc_upgrade | — (crafted item) |  | rarity +-5 | red_card 1 | all (+children) |
| ring_shank | core | ring | main | 2 (cat metal) |  |  |  |  |
| road_maker_upgrade | upgrade | all | misc_upgrade | — (crafted item) |  | durability ×base 0.1; rarity +10 | road_maker 1 | excavator |
| rod | core | tool | rod | 2 → x4 |  |  |  |  |
| saw_blade | core | saw | main | 5 |  | attack_damage +2; attack_speed +1.6; durability ×base 1; enchantment_value ×base -0.5; harvest_speed ×base -0.75; repair_efficiency 1.5 |  |  |
| setting | core | curio | setting | 1 | bort |  |  |  |
| shear_blades | core | shears | main | 2 |  | attack_speed +4; durability ×base -0.048; repair_efficiency 1.25 |  |  |
| shield_plate | core | shield | main | 2 |  | repair_efficiency 1 |  |  |
| shovel_head | core | shovel | main | 1 |  | attack_damage +1.5; attack_speed +1; repair_efficiency 2 |  |  |
| sickle_blade | core | sickle | main | 3 |  | attack_damage +1; attack_speed +2.2; repair_efficiency 1 |  |  |
| slingshot_limbs | core | slingshot | main | 2 |  | draw_speed +1.5; enchantment_value ×base -0.65; ranged_damage +0.5 & ×base -0.75; repair_efficiency 2 |  |  |
| spear_tip | core | spear | main | 1 |  | attack_damage +3; attack_reach +1; attack_speed +1.3; durability ×base -0.2; repair_efficiency 1.25 |  |  |
| spoon_upgrade | upgrade | all | misc_upgrade | — (crafted item) |  | durability ×base 0.2; rarity +10 | spoon 1 | pickaxe |
| sword_blade | core | sword | main | 2 |  | attack_damage +3; attack_speed +1.6; repair_efficiency 1 |  |  |
| tip | core | all | tip | 1 |  |  |  |  |
| trident_prongs | core | trident | main | 3 |  | attack_damage +4; attack_speed +1.1; draw_speed +1; ranged_damage +1; repair_efficiency 1 |  |  |
| wide_plate_upgrade | upgrade | all | misc_upgrade | — (crafted item) |  | durability ×base 0.1; rarity +10 | widen 1 | hammer,excavator |

## Appendix T1: all 75 traits

Source: `silentgear_traits/*.json`, descriptions from en_us.json, sources computed from every material/part JSON. "(code-only)" = behaviour in Java (see SG-63). Effect notation: `×lvl` = multiplied by trait level, `×dmgRatio` = by damage/maxDamage, `×value` = by the current property value.

| # | id | name | max lvl | gear condition | effects (data) | description (lang) | sources (material/part Lvl [cond]) |
|---|---|---|---|---|---|---|---|
| 1 | accelerate | Accelerate | 5 | on tool | propmod attack_speed +0.01×lvl×dmgRatio; draw_speed +0.01×lvl×dmgRatio; harvest_speed +2.0×lvl×dmgRatio | Gains harvest speed, attack speed, and ranged speed as damaged | azure_electrum/main L3 [ratio≥0.35]; azure_electrum/rod L5 [ratio≥0.66]; fine_silk_cloth/grip L1 |
| 2 | adamant | Adamant | 5 |  | wielder potion [armor] resistance piece_count lvls[1, 1, 1, 2] dur38t<br>extra dmg vs high_health +2.0×lvl | Armor increases damage resistance, tools deal more damage to mobs with a base health of more than 10 hearts | barrier/main L5; invar/main L2 [(count≥3 OR ratio≥0.35)]; nickel/main L1 [(count≥3 OR ratio≥0.35)] |
| 3 | ancient | Ancient | 5 | on tool | (code-only) | Increases XP dropped by blocks and mobs | dimerald/rod L3 [ratio≥0.5]; end_stone/main L2; end_stone/rod L4; phantom_membrane/grip L2; stone/main L1 |
| 4 | aquatic | Aquatic | 5 |  | wielder potion [armor] water_breathing full_set_only lvls[1] dur38t<br>extra dmg vs aquatic +2.0×lvl | Full set of armor gives water breathing, deals more damage to aquatic mobs | lead/main L2 [(count≥3 OR ratio≥0.35)]; prismarine/coating L5 [ratio≥0.67]; prismarine/coating L3 [NOT ratio≥0.67] |
| 5 | bastion | Bastion | 5 | (on armor OR on curio) | attribute [all/any] generic.armor add_value [1.0, 2.0, 3.0, 4.0, 5.0] | Provides bonus armor | diamond/setting L1 |
| 6 | bending | Bending | 5 |  | durability: chance 0.075×lvl per damage event, damage +1 | Gear sometimes takes extra damage | azure_silver/rod L2; copper/rod L3; gold/rod L4; netherrack/rod L2; wood/rough/rod L5 |
| 7 | bounce | Bounce | 1 | on armor | negate silentgear:bounce_protects dmg 0.2×lvl | Boots negate fall damage, armor knocks back attackers | slime/lining L1 |
| 8 | brilliant | Brilliant | 1 |  | (code-only) | Shiny! Piglin like. | blaze_gold/coating L1; blaze_gold/main L1 [ratio≥0.7]; gold/coating L1; gold/main L1 [ratio≥0.7] |
| 9 | brittle | Brittle | 5 |  | durability: chance 0.1×lvl per damage event, damage +1 | Gear sometimes takes extra damage | basalt/main L2; basalt/rod L3; blackstone/main L1; blackstone/rod L1; diamond/main L2; diamond/rod L5 [ratio≥0.5]; diamond/tip L2; dimerald/main L1; dimerald/rod L1 [ratio≥0.5]; emerald/main L1; emerald/rod L4 [ratio≥0.5]; emerald/tip L1; flint/rod L3; obsidian/rod L2; quartz/rod L3; sandstone/rod L1; stone/rod L2; terracotta/main L1; terracotta/rod L2 |
| 10 | bulky | Bulky | 5 | on tool | propmod attack_speed +-0.075×lvl×dmgRatio | TODO, not tested | — |
| 11 | chilled | Chilled | 5 | on weapon | extra dmg vs fire_immune +2.0×lvl | Deals more damage to Nether and fire-immune mobs | — |
| 12 | chipping | Chipping | 5 |  | propmod armor +-0.075×lvl×dmgRatio×value; harvest_speed +0.25×lvl×dmgRatio×value | Reduces armor or increases harvest speed as gear is damaged | basalt/main L3; basalt/rod L2; bone/main L2; obsidian/rod L3; quartz/tip L1; terracotta/main L2 |
| 13 | confetti | Confetti! | 5 | on weapon | (code-only) | I like big boom boom | — |
| 14 | crackler | Crackler | 1 | on tool | place basalt on use, cost 3 dur, cooldown 0t | Creates and places basalt at the cost of durability | — |
| 15 | crude | Crude | 5 |  | synergy +-0.04×lvl | Reduces synergy | wood/rough/rod L3 |
| 16 | crushing | Crushing | 5 |  | propmod armor +0.05×lvl×dmgRatio×value; attack_damage +-0.1667×lvl×dmgRatio×value | Increases armor or decreases attack damage as gear is damaged | obsidian/main L2; quartz/main L3; stone/rod L2; terracotta/rod L1 |
| 17 | cure_poison | Cure Poison | 1 |  | clears poison | Removes poison effect when equipped | — |
| 18 | cure_wither | Cure Wither | 1 |  | clears wither | Removes wither effect when equipped | — |
| 19 | cursed | Cursed | 7 |  | attribute [all/any] generic.luck add_value [-0.5, -1.0, -1.5, -2.0, -3.0, -4.0, -5.0] | Reduces luck, how unfortunate | amethyst/setting L4 |
| 20 | dulling | dulling | 5 |  | propmod attack_damage +-1.0×lvl×dmgRatio; harvest_speed +-1.0×lvl×dmgRatio |  | copper/tip L1 |
| 21 | eroded | Eroded | 5 | on tool | propmod attack_damage +-0.15×lvl×dmgRatio×value; harvest_speed +0.15×lvl×dmgRatio×value | Increases harvest speed and reduces attack damage as gear is damaged | crushed_shulker_shell/main L4; netherrack/main L3; netherrack/rod L2; redstone_alloy/main L3 |
| 22 | fiery | Fiery | 2 | on weapon | enchant fire_aspect by lvl [1, 2]; flame by lvl [1] | Adds Fire Aspect or Flame to weapons | blaze_gold/tip L2; crimson_iron/tip L1 |
| 23 | fireproof | Fireproof | 1 |  | fireproof | Cannot be destroyed when dropped in fire or lava | netherite/coating L1 |
| 24 | flame_ward | Flame Ward | 1 |  | fireproof<br>wielder potion [armor] fire_resistance full_set_only lvls[1] dur38t | Gives fire resistance on armor (full set only) | crimson_steel/main L1 [(count≥3 OR ratio≥0.33)] |
| 25 | flammable | Flammable | 1 |  | (code-only) | Takes damage when on fire and can be used as fuel | bamboo/main L1; leaves/main L1; paper/main L1; wood/main L1 |
| 26 | flexible | Flexible | 5 |  | durability: chance 0.075×lvl per damage event, damage -1 | Gear occasionally takes less damage | azure_electrum/rod L2; bamboo/main L2; bamboo/rod L3; blaze_gold/rod L3; blaze_rod/rod L4; bone/rod L2; breeze_rod/rod L4; bronze/rod L1; crimson_iron/rod L3; crimson_steel/rod L4; end_rod/rod L4; fine_silk/binding L4; fine_silk/cord L2; fine_silk_cloth/lining L4; flax/binding L3; fluffy_string/binding L3; iron/rod L2; leather/grip L3; leather/lining L4; netherrack/main L2; netherwood/main L3; netherwood/rod L1; phantom_membrane/lining L3; signalum/main L2; signalum/rod L4; sinew/binding L4; sinew/cord L2; string/binding L1; titanium/rod L2; wood/rod L2; wood/crimson/rod L2; wood/warped/rod L2; wool/grip L2; wool/lining L2 |
| 27 | floatstoner | Floatstoner | 1 | on tool | place end_stone on use, cost 3 dur, cooldown 0t | Creates and places end stone at the cost of durability | — |
| 28 | flutter | flutter | 5 |  | attribute [armor/chest] generic.safe_fall_distance add_value [0.25, 0.5, 0.75, 1.0, 1.25]; [armor/feet] generic.safe_fall_distance add_value [0.25, 0.5, 0.75, 1.0, 1.25]; [armor/head] generic.safe_fall_distance add_value [0.25, 0.5, 0.75, 1.0, 1.25]; [armor/legs] generic.safe_fall_distance add_value [0.25, 0.5, 0.75, 1.0, 1.25] |  | feather/main L1 |
| 29 | fortunate | Fortunate | 3 | on harvest_tool | enchant fortune by lvl [1, 2, 3] | Adds Fortune to tools | azure_silver/tip L3 |
| 30 | gold_digger | Gold Digger | 5 | on harvest_tool | bonus drops silentgear:gold_digger_drops: chance 0.15×lvl, mult 0.5 | Sometimes increases nugget drops when mining | dimerald/main L2 [(count≥3 OR ratio≥0.5)]; tyrian_steel/tip L3 |
| 31 | greedy | Greedy | 5 |  | mining speed ×(1+0.2×lvl) on c:ores<br>magnet range 2.0(+lvl) pull 0.06 items silentgear:greedy_magnet_attracted | Increases the mining speed for ores | blaze_gold/main L3 |
| 32 | hard | Hard | 5 | on tool | propmod harvest_speed +0.05×lvl×dmgRatio×value; ranged_damage +-0.1×lvl×dmgRatio×value | Increases harvest speed or decreases ranged damage as item is damaged | blackstone/main L2; blackstone/rod L1; compressed_iron/main L3; crimson_iron/main L2; crimson_steel/main L3; refined_obsidian/main L4; refined_obsidian/rod L3; titanium/main L4; titanium/rod L4 |
| 33 | heat_resistant | Heat-Resistant | 5 | on armor | fireproof<br>negate minecraft:is_fire dmg 0.04×lvl | Reduces fire damage | crimson_iron/main L4 |
| 34 | heavy | Heavy | 5 | on armor | attribute [armor/chest] generic.movement_speed add_multiplied_base [-0.01, -0.02, -0.03, -0.04, -0.05]; [armor/feet] generic.movement_speed add_multiplied_base [-0.01, -0.02, -0.03, -0.04, -0.05]; [armor/head] generic.movement_speed add_multiplied_base [-0.01, -0.02, -0.03, -0.04, -0.05]; [armor/legs] generic.movement_speed add_multiplied_base [-0.01, -0.02, -0.03, -0.04, -0.05] | Armor decreases movement speed | — |
| 35 | holy | Holy | 5 | on weapon | extra dmg vs tagged(minecraft:undead) +2.0×lvl | Deals extra damage to the undead | barrier/main L5; lapis_lazuli/tip L1 [ratio≥0.75] |
| 36 | ignite | Ignite | 1 | on tool | place fire on use, cost 1 dur, cooldown 0t | Lights blocks on fire at a small durability cost | — |
| 37 | imperial | Imperial | 5 | on harvest_tool | bonus drops silentgear:imperial_drops: chance 0.15×lvl, mult 0.5 | Sometimes increases gem drops when mining | dimerald/tip L2; redstone/main L3; tyrian_steel/tip L3 |
| 38 | indestructible | Indestructible | 1 |  | (code-only) | Prevents durability loss | — |
| 39 | jabberwocky | Jabberwocky | 1 | on harvest_tool | (code-only) | ’Twas brillig, and the slithy toves / Did gyre and gimble in the wabe | — |
| 40 | jagged | Jagged | 5 | on tool | propmod attack_damage +0.1667×lvl×dmgRatio×value; ranged_damage +-0.1667×lvl×dmgRatio×value | Increases attack damage or decreased ranged damage as item is damaged | blackstone/main L2; blackstone/rod L2; end_stone/main L3; flint/main L3; flint/rod L2; netherwood/main L2; obsidian/main L3; quartz/main L2; quartz/tip L3; wood/main L1; wood/crimson/main L1; wood/warped/main L1 |
| 41 | kitty_vision | Kitty Vision | 1 | (on helmet OR on curio) | wielder potion [curio] night_vision trait_level lvls[1] dur318t; [helmet] night_vision trait_level lvls[1] dur318t | Gives night vision on helmets and curios | dimerald/setting L1 |
| 42 | light | Light | 5 | on armor | attribute [armor/chest] generic.movement_speed add_multiplied_base [0.01, 0.02, 0.03, 0.04, 0.05]; [armor/feet] generic.movement_speed add_multiplied_base [0.01, 0.02, 0.03, 0.04, 0.05]; [armor/head] generic.movement_speed add_multiplied_base [0.01, 0.02, 0.03, 0.04, 0.05]; [armor/legs] generic.movement_speed add_multiplied_base [0.01, 0.02, 0.03, 0.04, 0.05] | Armor increases movement speed | azure_electrum/main L4 [ratio≥0.5]; leaves/main L1; phantom_membrane/lining L2 |
| 43 | lucky | Lucky | 7 |  | attribute [all/any] generic.luck add_value [0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0] | Adds luck when held (this is not Fortune) | fine_silk/binding L1; lapis_lazuli/setting L3 [ratio≥0.75]; lapis_lazuli/tip L4 [ratio≥0.75] |
| 44 | lustrous | Lustrous | 5 | on harvest_tool | (code-only) | Tools gain a large harvest speed boost when in light | bismuth/main L2 [ratio≥0.5]; bismuth_brass/main L2 [ratio≥0.5]; bismuth_steel/main L2 [ratio≥0.5]; diamond/main L1 [(count≥3 OR ratio≥0.5)]; diamond/rod L4 [ratio≥0.5]; diamond/tip L2; electrum/rod L3 [ratio≥0.5]; glowstone/tip L4; refined_glowstone/main L4; refined_glowstone/rod L1 [ratio≥0.75]; signalum/main L4; signalum/rod L2 |
| 45 | magmatic | Magmatic | 1 | on harvest_tool | (code-only) | Auto-smelting | crimson_steel/tip L1 |
| 46 | magnetic | Magnetic | 5 |  | magnet range 3.0(+lvl) pull 0.06 | Pulls in nearby items | PART magnetic_upgrade L5 |
| 47 | malleable | Malleable | 5 |  | durability: chance 0.1×lvl per damage event, damage -1 | Gear sometimes takes less damage | aluminum/main L3; aluminum/rod L3; aluminum_steel/main L4; aluminum_steel/rod L3; azure_electrum/main L3; azure_electrum/tip L3; azure_silver/main L3; azure_silver/tip L2; bismuth/main L2; bismuth/rod L2; bismuth_brass/main L2; bismuth_brass/rod L2; bismuth_steel/main L3; bismuth_steel/rod L3; blaze_gold/main L3; brass/main L2; brass/rod L2; bronze/tip L2; compressed_iron/main L2; compressed_iron/rod L2; copper/tip L1; crimson_iron/main L3; crimson_steel/main L5; electrum/main L2; end_stone/rod L1; enderium/main L3; enderium/rod L2; gold/main L1; gold/tip L1; high_carbon_steel/main L3; invar/main L3; invar/rod L3; iron/main L3; iron/tip L2; lead/main L2; lumium/main L3; lumium/rod L3; nickel/main L3; nickel/rod L3; osmium/main L2; osmium/rod L2; platinum/main L3; platinum/rod L2; redstone_alloy/main L3; redstone_alloy/rod L3; refined_glowstone/main L2; refined_glowstone/rod L4; refined_iron/main L4; refined_iron/rod L4; refined_obsidian/main L3; refined_obsidian/rod L4; silver/main L2; silver/rod L1; steel/main L5; steel/rod L5; tin/main L1; tin/rod L2; titanium/main L2; uranium/main L2; uranium/rod L2; zinc/main L1; zinc/rod L2 |
| 48 | mighty | Mighty | 5 | (on tool OR on curio) | wielder potion [curio] haste trait_level lvls[1, 1, 2, 2, 3] dur38t; [tool] strength trait_level lvls[0, 0, 1, 1, 2] dur38t; [tool] haste trait_level lvls[1, 1, 1, 2, 3] dur38t | Gives strength and/or haste on tools based on trait level | quartz/setting L2 [ratio≥0.5] |
| 49 | moonwalker | Moonwalker | 5 | (on boots OR on curio) | attribute [all/any] generic.gravity add_multiplied_base [-0.15, -0.3, -0.45000002, -0.6, -0.75] | I don't believe in gravity! | azure_silver/main L4 [ratio≥0.5] |
| 50 | multi_break | Multi-break | 5 |  | (code-only) | TODO, not coded | — |
| 51 | organic | Organic | 5 |  | propmod enchantment_value +0.1×lvl×dmgRatio×value; magic_damage +-0.15×lvl×dmgRatio×value | Gains enchantability, but loses magic damage as the item is damaged | — |
| 52 | racker | Racker | 1 | on tool | place netherrack on use, cost 3 dur, cooldown 0t | Places netherrack at the cost of durability | — |
| 53 | reach | Reach | 5 |  | attribute [all/any] player.block_interaction_range add_value [0.5, 1.0, 1.5, 2.0, 3.0] | Increases block reach distance | blaze_rod/rod L1; emerald/setting L2 |
| 54 | red_card | Red Card | 1 |  | (code-only) |  | PART red_card_upgrade L1 |
| 55 | refractive | Refractive | 1 | on tool | place phantom_light on use, cost 5 dur, cooldown 0t | Place phantom lights when used | end_rod/rod L1; glowstone/tip L1; lumium/main L1 [ratio≥0.5]; lumium/rod L1 [ratio≥0.5]; refined_glowstone/tip L1 |
| 56 | renew | Renew | 5 |  | self-repair: every 20 ticks chance 0.018×lvl, repair 1×lvl | Slowly repairs the item over time | amethyst/main L1 [ratio≥0.7]; glowstone/main L3; phantom_membrane/main L2 [ratio≥0.5] |
| 57 | road_maker | Road Maker | 1 | on tool | fill minecraft:grass_block→minecraft:dirt_path 3x3, 0.5 dur/block | Turns dirt-like blocks into grass paths | PART road_maker_upgrade L1 |
| 58 | rustic | Rustic | 5 |  | synergy +0.05×lvl if 0.749<syn<1.001 | Increases synergy if it's 100% or less | paper/main L3; terracotta/main L1 |
| 59 | sharp | Sharp | 5 | on tool | propmod attack_damage +0.125×lvl×dmgRatio×value; harvest_speed +0.125×lvl×dmgRatio×value | Gain harvest speed and attack damage as durability is lost | bronze/main L1 |
| 60 | silky | Silky | 1 | on harvest_tool | enchant silk_touch by lvl [1] | Adds Silk Touch to tools | amethyst/tip L1 [ratio≥0.66]; brass/main L1 [ratio≥0.66] |
| 61 | snow_walker | Snow Walker | 1 |  | (code-only) | Walk on powder snow without sinking | fine_silk_cloth/main L1 [on boots]; leather/main L1 [on boots]; wool/main L1 [on boots] |
| 62 | soft | Soft | 5 | on tool | propmod harvest_speed +-0.15×lvl×dmgRatio×value | Reduces harvest speed as tool is damaged | aluminum/main L2 [ratio≥0.5]; azure_silver/main L2; blaze_gold/coating L2; blaze_gold/tip L2; copper/main L1 [ratio≥0.5]; copper/rod L3; electrum/main L2 [ratio≥0.5]; gold/coating L3; gold/main L3; gold/tip L3; lead/rod L4; platinum/main L2; platinum/rod L4; silver/main L1; silver/rod L2; tin/main L2; tin/rod L1; zinc/main L2; zinc/rod L3 |
| 63 | spoon | Spoon | 1 | on pickaxe | (code-only) | Pickaxes can mine soil | PART spoon_upgrade L1 |
| 64 | stellar | Stellar | 5 |  | self-repair: every 20 ticks chance 0.02×lvl, repair 1×lvl<br>wielder potion [armor] speed piece_count lvls[0, 1, 2, 3] dur38t; [armor] jump_boost piece_count lvls[1, 2, 3, 4] dur38t | Armor gives speed and jump boost, items slowly repair themselves | refined_iron/main L1 |
| 65 | sturdy | Sturdy | 5 |  | durability: chance 0.175×lvl per damage event, damage -1 | Gear frequently takes less damage | crimson_steel/rod L1 [ratio≥0.5]; end_rod/rod L2; tyrian_steel/main L3 [ratio≥0.5]; tyrian_steel/rod L4 [ratio≥0.5] |
| 66 | swift_swim | Swift Swim | 5 |  | attribute [all/any] swim_speed add_value [0.2, 0.4, 0.6, 0.8, 1.0] | Increases swim speed | prismarine/setting L3 [ratio≥0.67] |
| 67 | synergistic | Synergistic | 5 |  | synergy +0.04×lvl if 1.0<syn<inf | Gear gets a synergy bonus (if base value is more than 100%) | aluminum/main L1; aluminum/rod L2; aluminum_steel/main L2 [ratio≥0.5]; aluminum_steel/rod L3; emerald/main L2; emerald/tip L2 |
| 68 | terminus | Terminus | 1 | on tool | place stone on use, cost 3 dur, cooldown 0t | Creates and places stone blocks when used | — |
| 69 | turtle | Turtle | 1 | (on helmet OR on curio) | (code-only) | Hold your breath for longer | turtle/main L1 [count≥2] |
| 70 | venom | Venom | 5 | on tool | target poison L1:80t, L2:160t, L3:240t, L4:320t, L5:400t | Poisons the target when attacked | — |
| 71 | void_ward | Void Ward | 1 | on armor | (code-only) | Saves the wearer from falling out of the world | tyrian_steel/main L1 [(count≥3 OR ratio≥0.5)] |
| 72 | vulcan | Vulcan | 1 | on tool | place obsidian on use, cost 20 dur, cooldown 100t | Places obsidian at a high durability cost | refined_obsidian/tip L1 |
| 73 | widen | Widen | 3 | on harvest_tool | (code-only) | Increases the effect radius of hammers and excavators | PART wide_plate_upgrade L1 |
| 74 | wind_blast | Wind Blast | 1 | on tool | wind_blast | Creates a blast of wind when attacking | breeze_rod/rod L1 |
| 75 | yummy | Yummy | 1 |  | food {"can_always_eat": true, "eat_seconds": 3.2, "nutrition": 10, "saturation": 1.0, "using_converts_to": {"id": "minecraft:stick"}} | Makes the item edible, but... | — |

## Appendix R: blueprint and template recipes

| blueprint/template | recipe |
|---|---|
| arrow_blueprint | # / / / @ ; #=#sg:blueprint_paper, /=#c:rods/wooden, @=#c:feathers |
| arrow_template | # / / / @ ; #=#sg:template_boards, /=#c:rods/wooden, @=#c:feathers |
| axe_blueprint | ## / #/ / ·/ ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| axe_template | ## / #/ / ·/ ; #=#sg:template_boards, /=#c:rods/wooden |
| binding_blueprint | #sg:blueprint_paper + material(binding) + material(binding) |
| binding_blueprint_alt | #sg:blueprint_paper + #c:strings + #c:strings |
| binding_template | #sg:template_boards + material(binding) + material(binding) |
| boots_blueprint | #·# / #·# ; #=#sg:blueprint_paper |
| boots_template | #·# / #·# ; #=#sg:template_boards |
| bow_blueprint | ·#/ / #·/ / ·#/ ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| bow_template | ·#/ / #·/ / ·#/ ; #=#sg:template_boards, /=#c:rods/wooden |
| bracelet_blueprint | ### / /#/ ; #=#sg:blueprint_paper, /=material(metal main) |
| bracelet_blueprint_alt | ### / /#/ ; #=#sg:blueprint_paper, /=#c:ingots/gold |
| bracelet_template | ### / /#/ ; #=#sg:template_boards, /=material(metal main) |
| chestplate_blueprint | #·# / ### / ### ; #=#sg:blueprint_paper |
| chestplate_template | #·# / ### / ### ; #=#sg:template_boards |
| coating_blueprint | #sg:blueprint_paper + #sg:blueprint_paper + #sg:blueprint_paper + #sg:blueprint_paper + #c:gems/diamond + #c:gems/emerald |
| coating_smithing_template | netherite_upgrade_smithing_template + #sg:blueprint_paper + #c:gems/bort |
| coating_template | #sg:template_boards + #sg:template_boards + #sg:template_boards + #sg:template_boards + #c:gems/diamond + #c:gems/emerald |
| cord_blueprint | #/ / #/ / #/ ; #=#sg:blueprint_paper, /=material(cord) |
| cord_blueprint_alt | #/ / #/ / #/ ; #=#sg:blueprint_paper, /=#c:strings |
| cord_template | #/ / #/ / #/ ; #=#sg:template_boards, /=material(cord) |
| crossbow_blueprint | /#/ / ### / ·/· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| crossbow_template | /#/ / ### / ·/· ; #=#sg:template_boards, /=#c:rods/wooden |
| dagger_blueprint | # / / ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| dagger_template | # / / ; #=#sg:template_boards, /=#c:rods/wooden |
| elytra_blueprint | /e/ / p#p / p·p ; #=#sg:blueprint_paper, /=#c:ingots/gold, e=elytra, p=phantom_membrane |
| elytra_template | /e/ / p#p / p·p ; #=#sg:template_boards, /=#c:ingots/gold, e=elytra, p=phantom_membrane |
| excavator_blueprint | #·# / ### / ·/· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| excavator_template | #·# / ### / ·/· ; #=#sg:template_boards, /=#c:rods/wooden |
| fishing_rod_blueprint | ··/ / ·/# / /·# ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| fishing_rod_template | ··/ / ·/# / /·# ; #=#sg:template_boards, /=#c:rods/wooden |
| fletching_blueprint | #sg:blueprint_paper + #sg:blueprint_paper + #c:feathers |
| fletching_template | #sg:template_boards + #sg:template_boards + #c:feathers |
| grip_blueprint | #sg:blueprint_paper + #sg:blueprint_paper + material(grip) |
| grip_blueprint_alt | #sg:blueprint_paper + #sg:blueprint_paper + #minecraft:wool |
| grip_template | #sg:template_boards + #sg:template_boards + material(grip) |
| hammer_blueprint | ### / ### / ·/· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| hammer_template | ### / ### / ·/· ; #=#sg:template_boards, /=#c:rods/wooden |
| helmet_blueprint | ### / #·# ; #=#sg:blueprint_paper |
| helmet_template | ### / #·# ; #=#sg:template_boards |
| hoe_blueprint | ## / ·/ / ·/ ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| hoe_template | ## / ·/ / ·/ ; #=#sg:template_boards, /=#c:rods/wooden |
| jeweler_tools | ··p / d#s / ips ; #=#sg:blueprint_paper, d=#c:gems/diamond, i=#c:ingots/iron, p=#minecraft:planks, s=#c:rods/wooden |
| katana_blueprint | ## / #· / /· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| katana_template | ## / #· / /· ; #=#sg:template_boards, /=#c:rods/wooden |
| knife_blueprint | ·# / /· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| knife_template | ·# / /· ; #=#sg:template_boards, /=#c:rods/wooden |
| leggings_blueprint | ### / #·# / #·# ; #=#sg:blueprint_paper |
| leggings_template | ### / #·# / #·# ; #=#sg:template_boards |
| lining_blueprint | #sg:blueprint_paper + #sg:blueprint_paper + #sg:blueprint_paper + #minecraft:wool + #minecraft:wool + #c:strings + #c:strings |
| lining_template | #sg:template_boards + #sg:template_boards + #sg:template_boards + #minecraft:wool + #minecraft:wool + #c:strings + #c:strings |
| mace_blueprint | ·#· / ### / ·/· ; #=#sg:blueprint_paper, /=#c:rods/breeze |
| mace_template | ·#· / ### / ·/· ; #=#sg:template_boards, /=#c:rods/breeze |
| machete_blueprint | ··# / ·## / /·· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| machete_template | ··# / ·## / /·· ; #=#sg:template_boards, /=#c:rods/wooden |
| mattock_blueprint | ##· / #/# / ·/· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| mattock_template | ##· / #/# / ·/· ; #=#sg:template_boards, /=#c:rods/wooden |
| necklace_blueprint | /#/ / #·# / ### ; #=#sg:blueprint_paper, /=material(metal main) |
| necklace_blueprint_alt | /#/ / #·# / ### ; #=#sg:blueprint_paper, /=#c:ingots/gold |
| necklace_template | /#/ / #·# / ### ; #=#sg:template_boards, /=material(metal main) |
| paxel_blueprint | ### / #/# / ·/# ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| paxel_template | ### / #/# / ·/# ; #=#sg:template_boards, /=#c:rods/wooden |
| pickaxe_blueprint | ### / ·/· / ·/· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| pickaxe_template | ### / ·/· / ·/· ; #=#sg:template_boards, /=#c:rods/wooden |
| prospector_hammer_blueprint | ## / ·/ / ·@ ; #=#sg:blueprint_paper, /=#c:rods/wooden, @=#c:ingots/iron |
| prospector_hammer_template | ## / ·/ / ·@ ; #=#sg:template_boards, /=#c:rods/wooden, @=#c:ingots/iron |
| ring_blueprint | ·#/ / #·# / /#· ; #=#sg:blueprint_paper, /=material(metal main) |
| ring_blueprint_alt | ·#/ / #·# / /#· ; #=#sg:blueprint_paper, /=#c:ingots/gold |
| ring_template | ·#/ / #·# / /#· ; #=#sg:template_boards, /=material(metal main) |
| rod_blueprint | #/ / #/ ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| rod_template | #/ / #/ ; #=#sg:template_boards, /=#c:rods/wooden |
| saw_blueprint | ### / ##/ / ··/ ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| saw_template | ### / ##/ / ··/ ; #=#sg:template_boards, /=#c:rods/wooden |
| shears_blueprint | ·# / #/ ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| shears_template | ·# / #/ ; #=#sg:template_boards, /=#c:rods/wooden |
| shield_blueprint | #@# / /// / ·#· ; #=#sg:blueprint_paper, /=#c:rods/wooden, @=#c:ingots/iron |
| shield_template | #@# / /// / ·#· ; #=#sg:template_boards, /=#c:rods/wooden, @=#c:ingots/iron |
| shovel_blueprint | # / / / / ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| shovel_template | # / / / / ; #=#sg:template_boards, /=#c:rods/wooden |
| sickle_blueprint | ·# / ## / /· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| sickle_template | ·# / ## / /· ; #=#sg:template_boards, /=#c:rods/wooden |
| slingshot_blueprint | #·# / ·/· / ·/· ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| slingshot_template | #·# / ·/· / ·/· ; #=#sg:template_boards, /=#c:rods/wooden |
| spear_blueprint | #·· / ·/· / ··/ ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| spear_template | #·· / ·/· / ··/ ; #=#sg:template_boards, /=#c:rods/wooden |
| sword_blueprint | # / # / / ; #=#sg:blueprint_paper, /=#c:rods/wooden |
| sword_template | # / # / / ; #=#sg:template_boards, /=#c:rods/wooden |
| tip_blueprint | #sg:blueprint_paper + #sg:blueprint_paper + #c:paper + #c:stones |
| tip_template | #sg:template_boards + #sg:template_boards + #c:paper + #c:stones |
| trident_blueprint | #H# / #T# / ·#· ; #=#sg:blueprint_paper, H=heart_of_the_sea, T=trident |
