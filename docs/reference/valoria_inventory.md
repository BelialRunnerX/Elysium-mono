# Valoria 1.21.1-1.0.4.2 (NeoForge port): what it contains

Source: Dylan's own port at `C:\ClaudeValoria\Valoria`
(github.com/BelialRunnerX/Valoria-NeoForge, made with the author's permission),
of IriDark's Forge 1.20.1 mod (github.com/IriDark/Valoria). Code GPL-3.0, art
CC BY-NC 4.0. Scanned 24 Sep 2026 from its registries and language file.
`docs/SPEC_VALORIA.md` says what each of these becomes.

## Size

| | count |
|---|---|
| Java files | 751 (registries 19, item types 108, block types 79, entities 76, client 100+) |
| items / blocks | ~932 item entries (520 items + 406 block items + 6 boats) / 458 blocks |
| entity types | 51 (4 bosses, 17 hostiles, 5 minions, 4 neutral, 22 projectiles) |
| block entities | 17 |
| mob effects / enchantments / attributes / particle types | 9 / 3 / 22 / 16 |
| armour materials / tool tiers / rarities | 14 / 19 / 14 |
| recipe types / recipe JSONs | 12 / 989 |
| loot tables / global loot modifiers | 517 / 17 |
| sound events / ogg files | 134 / 277 |
| textures | 1,550 PNG (item 758, block 396, entity 86, gui 75, curio 71, particle 56) |
| models / blockstates | 2,079 / 466 |
| lang keys | 1,665 in 8 locales |
| biomes / structures / features | 15 / 16 / 170 |

## Dependencies, and what replaces each

| Valoria needs | Used for | elysium-valoria uses instead |
|---|---|---|
| Tridot (required, 70 import roots) | screen shake, particles, post-processing, DotStyle text, boss bars, animation states, item skins, music modifiers, percentage armour, builders, codex book, attack system, spawners, trades | the library (hazards, gear, sets, UI kit, telegraph, bestiary), vanilla boss bars and particles, box models, our own registration loops; no skins, no post-processing |
| Curios (required, 7 slots) | every accessory | Curios still, through the library's trinket hooks |
| GeckoLib (required) | the fire boss, 7 geo models | box models |
| JEI, Jade, KubeJS, JER | integrations | the pack's own, later |

## Items

**Melee.** Greatswords and swords (17: flame, club, quantum reaper, silken blade,
spectral blade thrown and returning, corpsecleaver self-heal, coral reef
knockback, infernal sword eruption on magma charge, void edge, ethereal sword
soul rage, phantom launch-and-stun); katanas (12, all dash: iron to netherite,
samurai, jade, silken wakizashi, meatcutter bleed, wraith, holiday, lunar,
murasama charge); scythes (13, all radial strike with a use count: iron to
netherite, crimtane, jade, nature, aquarius, infernal, void, reaper, lunar,
beast summoning devourers); spears (16, thrown and returning: wooden to
netherite, pyratite explosive, jade, ethereal, nature, aquarius, infernal, void
pillar-shard, lunar, glaive radius 2); rapiers (6); kunai, shuriken, bombs,
crystal shard, acorns, gunpowder charges, clawhook, bloodhound mark, blaze reap
conjured blast, prospector's pick; 7 multi-tools.

**Ranged.** 12 bows and crossbows (samurai long bow, jade, nature, aquarius,
infernal, darkness, void, phantasm bow with overcharge and phantom rain,
phantasm crossbow, lunar); 7 arrows (nature, aquarius, infernal, wicked,
pyratite, soul, phantom).

**Shields (13)**: bronze 25 % to phantasm 80 %, with parry, reflect and speed
effects.

**Armour (14 sets)** with set effects: fallen collector and marsh (summon
damage, necromancy count), black gold (percentage armour), cobalt, samurai,
ethereal (night vision), nature (regen), depth (water breathing), infernal
(strength, fire resistance), spider (25 nihility resistance, per-piece debuffs
on hit), pyratite (explosion immunity), crimtane (exhaustion on hit), awakened
void (75), phantasm (100). 13 trim materials, a crown.

**Tools.** Full suites for jade, cobalt, ethereal, crimtane, nature, aquarius,
infernal, void; partial for pearlium, holiday, lunar.

**Accessories (~110)** in seven Curios slots: 30 tiered necklaces (10 kinds x
iron/gold/netherite), 12 reactive necklaces (eye, sun, moon, celestial), 18
tiered rings, 2 belts, 7 gloves and vambraces, 12 talismans (4 gems x 3
shapes), 27 runes (lithic, ancient, void slate x effects), void crystal (stores
souls, +25 nihility resistance), nihility monitor, respirator, gas mask,
bandage, devil and harmony hearts, elemental charm, bloodsight monocle, jewelry
bag, pixie pet.

**Consumables.** Healing vial/flask/elixir, cleansing vial/flask/elixir,
clarity vial, three bandages, toxins bottle (poisons a weapon for ten hits), 17
foods with a nihility cost, 19 placeable drinks (alcohol gives tipsy), rot in
three stages while in the dimension.

**Materials.** Gems amber/amethyst/ruby/sapphire, wicked amethyst, jade,
pyratite, crystal shard; ingots bronze, pearlium, cobalt, black gold, ancient,
crimtane, nature, aquarius, infernal, void, ethereal shard, relic gold;
elemental materials (nature gift, oceanic shell, infernal stone, pain crystal,
nihility shard, illusion stone, elemental crystal, rotten bone); five cores and
an unstable core; five smithing templates; six brick kinds; souls (collector
50/50); 11 skin fragments; two structure locators; four boss summoners; nine
loot bags; portal shard and void key; 28 spawn eggs; codex, page, summon book,
music disc, 19 paintings, 6 boats, 27 banner patterns.

## Blocks

**Stations** (all with block entities): kiln (menu), stone crusher (in-world),
jeweler table (menu, POI), keg (menu), soul infuser (menu, renderer),
elemental manipulator (menu, loop sound), alchemy station tiers 1-4 (menu),
heavy workbench (menu), cryptic altar and wicked altar (boss summons), elegant
pedestal.

**Sets.** Three woods (shadewood, eldritch, dreadwood: 20 blocks each plus
leaves, branches, chests); 16 stone families (limestone, crystal stone, ancient
stone, ephemarite x2, ambane, picrite, cobbled shale, deep marble, dunestone,
ash, tombstone, void stone, umbral, pearlium, bronze) with stairs, slabs,
walls, bricks, cracked, polished, cut, chiseled, pillars; void sand family;
metal and gem blocks (17); flesh set (9, with a spreader); ~60 plants and 13
pots; 18 archaeology pots; 21 drinkware blocks.

**Functional.** Portal frame (5x5 directional pattern) and portal (shader);
umbral keypad, activator and unbreakable blocks; void pillars and charged
pillars; four lanterns; seven traps, spikes and quicksand; six graves and a
sarcophagus; 16 ores (amber, sapphire, ruby, cobalt with deepslate; pyratite,
jade, picrite jade, wicked amethyst, dormant crystals, pearlium, raw cobalt);
five boss trophies.

## Entities

| kind | who |
|---|---|
| bosses | Necromancer (500 hp, 8 spell goals, summons zombies/skeletons/sorcerers/undead, music), Dryador (750, acorns, stomp, radial acorns, pixies, phase at half), Firron (1250, rush, sweep, piercing dash, triple sweep, phase at half, fire immune), Wicked Crystal (2000, pull, shield ring, crystal spawn, radial, crystal storm in phase two) |
| hostiles | scourge, swamp wanderer, devil (Nether), troll, corrupted troll, draugr, shadewood spider, sorcerer (caster, heals), ent, maggot, corrupted, king crab, wicked scorpion (acid spit), goblin (collects, hides, flees), scavenger (creature) |
| minions | undead (flying), flesh sentinel (flying), wicked shield, crystal, pixie |
| neutral | haunted merchant (brain AI, trades, voice lines), mannequin, nature golem and river golem (creature, punch, stomp, slap) |
| projectiles | 7 arrows, spear, kunai, shuriken, spectral blade, bomb, acorn, crystal shard, pyratite shard, meat block, spell, acid spit, claw, laser, devourer, crystal spikes, water bubble, locator |

Spawns: ten Overworld/Nether biome modifiers (devil, ent, goblin, king crab,
golems, scourge, sorcerer, swamp wanderer, troll); in the dimension by biome
(spiders and scavengers in the forest; scavengers, corrupted trolls and
scorpions across the barrens and voids; corrupted and flesh sentinels in the
meaty lands); bosses by altar and summoner; undead and draugr in crypts.

## World

**The Valoria**: noise dimension, 15 hand-placed biomes (shade forest, two
shaded and two plain ecotones, coast, ocean wastes, river, void barren, void
desert, void hills, void peaks, void shore, meaty lands, meaty caves), fixed
time 22000, ambient light 0, custom sky, monster light 0-6, beds work, no
raids. 89 configured and 99 placed features, 17 feature classes, 3 tree
growers, a biome-tag placement modifier and a filtering biome modifier.

**Portal**: a 5x5 directional frame of frame shards, filled by pattern match,
custom teleporter and POI, shader render.

**Structures (16 in 6 sets)**: crypts (crypt, medium crypt), necromancer crypt
(concentric), fortress (concentric), magmatic halls (caves), five monstrosity
bones, six small ruins (ruin, tower, taint spike, deep ruins, sand ruin,
corrupted well); 13 template pools, 7 processor lists, 11 chest tables, 42 NBT
templates.

**Ores**: Overworld amber, sapphire, ruby, cobalt (with deepslate), limestone,
pearlium, ephemarite, ancient stone, four geodes, dunestone and quicksand
disks, crystal disks; dimension jade, picrite jade, wicked amethyst, pyratite,
dormant crystals, deep marble, void sand, pyratite blobs; 39 biome modifiers.

## Systems

**Nihility.** Attributes max_nihility (100), nihility_resilience (5 s),
nihility_resistance, nihility_damage. Inside the dimension, every resilience
interval the meter gains `max(0.05, 1 - resistance x 0.05) x (difficulty x
0.5)`; outside, it loses 1 per decay interval (6 s, x2 on easy); paused on
peaceful or under nihility protection. At 50 % periodic void damage `1 +
segments x multiplier` (a segment per 10 % past half); at 75 % a heartbeat and
optional blindness; at 95 % KILL (default), DAMAGE or TELEPORT to the respawn
point with the meter cleared. Damage ticks every 40 ticks, 20 past critical.
Counterplay: armour void defence (spider 25 to phantasm 100), void crystal,
nihility protection effect, respirator, gas mask, a rune, cleansing drinks;
raw food adds. HUD overlay, meter, shield overlay, item badge, render layer,
monitor curio, sync packet.

**Souls.** A data component on soul items; collector 50/50, void crystal,
ethereal sword (rage mode at full); the soul infuser spends them; a soul
event, particle packet, soul shard and arrow, soulroot plants.

**Codex.** Fifty unlockables of seven kinds (item, entity, entity tag, tag,
dungeon, dungeon tag, dimension) on kill, visit and dimension listeners;
book UI, packets, loot condition, command argument; progression config.

**Abilities.** Registry of ability types, dash and scythe components with
descriptions, item pagination component, player ability capability, cast and
sync packets, overlay, 34 tooltip textures; attributes dash_distance,
attack_radius, summon_damage, necromancy_count.

**Magma charge.** Refills 1 per 10 ticks, drains in lava or on fire, spent by
the infernal sword's eruption; overlay; immunity charms of 10 s.

**Enchantments.** Bleeding (entity effect), accuracy (phantasm spread),
explosive flame (blaze reap). **Effects (9)**: aloe regen, stun, tipsy,
bleeding, sinister prediction, exhaustion, renewal, soul burst, nihility
protection. **Attributes (22)**: elemental damage and resistance pairs for
nature, depth, infernal, nihility; crit and ranged crit chance and damage;
dodge, miss; excavation speed; summon damage, necromancy count. **Damage
types**: bleeding, void, voidthorn, quicksand.

**Recipes (989)**: 355 shaped, 182 stonecutting, 84 manipulator, 74 heavy
workbench, 67 shapeless, 46 smithing, 42 jewelry, 33 smelting, 24 alchemy, 17
keg, 17 blasting, 15 kiln, 9 crusher, 6 campfire, 6 smoking, 3 alchemy
upgrade, 1 each purifying food, soul infuser, weapon poisoning. **Loot**: 17
item tables (bags), 11 chest, 21 entity, 3 archaeology, 3 crusher, 17 global
modifiers (seasonal and dungeon drops). **Audio**: 11 music tracks, 5
ambients, 109 block and 109 entity sounds. **Rendering**: 16 particle types
with 56 textures, a portal shader, 7 geo models. **Integrations**: 9 JEI
categories, 7 Jade providers, 6 KubeJS schemas.
