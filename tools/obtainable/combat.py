"""Declared sources for the combat group: arsenal, bestiary, mobs, spawners, trinkets.

Each is pinned to the code that hands the thing out. Loaded by check_obtainable.py
through OBTAINABLE_SOURCES_EXTRA.
"""

ARSENAL = "elysium-arsenal/src/main/java/com/elysium/arsenal/"
DIG_RULES = ARSENAL + "rules/DigRules.java"
SHAPES = "elysium-lib/src/main/java/com/elysium/lib/ability/ElysiumAbilityShapes.java"


def declare(declared):
    # ------------------------------------------------------------------
    # elysium-arsenal: brushing and identifying
    # ------------------------------------------------------------------
    # What a site holds is DigRules.POOLS, drawn by SiteBlockEntity.whatIsBuried
    # and dropped by BrushItem.onUseTick when the last stage is brushed. A site
    # block has to be in the world (its loot table gives the block underneath,
    # never the site) and any brush works; iron is the first rung.
    #
    # The sites are laid by the arsenal's own features (29 Sep: DigRules.PATCHES
    # and SEAMS, written by gen_arsenal.py as worldgen/ and neoforge/biome_modifier/),
    # which the walk reads itself, so the placed: needs below are met by data.
    for site, pool_line in (
            ("suspicious_dirt", 'new Find("elysiumarsenal:artifact_shard", 12),'),
            ("suspicious_red_sand", 'new Find("elysiumarsenal:artifact_shard", 14),'),
            ("fossiliferous_dirt", 'new Find("elysiumarsenal:artifact_shard", 16),')):
        declared("elysiumarsenal:artifact_shard",
                 [f"placed:elysiumarsenal:{site}", "elysiumarsenal:iron_brush"],
                 DIG_RULES, pool_line,
                 f"brushing {site} to the end drops what its pool holds at that spot; every pool holds shards")
    declared("elysiumarsenal:chiseled_bone_block",
             ["placed:elysiumarsenal:fossiliferous_dirt", "elysiumarsenal:iron_brush"],
             DIG_RULES, 'new Find("elysiumarsenal:chiseled_bone_block", 16),',
             "the fossil pool, which only fossiliferous dirt draws from, holds chiselled bone")

    # Every half of every fossil, walked from FossilRules.FOSSILS into the fossil
    # pool (DigRules.pools). The whole fossils are recipes (skull + body), which
    # the walk reads itself.
    for part, half in (("SKULL", "head"), ("BODY", "body")):
        declared(f"/^elysiumarsenal:[a-z]+_fossil_{half}$/",
                 ["placed:elysiumarsenal:fossiliferous_dirt", "elysiumarsenal:iron_brush"],
                 DIG_RULES, f"fossil.add(new Find(OURS + one.id(FossilRules.{part}), HALF_A_FOSSIL));",
                 f"the fossil pool holds every fossil's {half}, walked from the fossil roster")

    # The rest of what brushing alone hands out: the rotten log (whose planks and
    # set are recipes), both mud bricks (cracked ones are smelted from mud bricks
    # as well) and the four totems.
    for item, site, pool, weight in (
            ("rotten_log", "suspicious_dirt", "dirt", 10),
            ("growth_totem", "suspicious_dirt", "dirt", 2),
            ("cracked_mud_bricks", "suspicious_red_sand", "red sand", 10),
            ("infested_mud_bricks", "suspicious_red_sand", "red sand", 3),
            ("torrent_totem", "suspicious_red_sand", "red sand", 3),
            ("soul_totem", "fossiliferous_dirt", "fossil", 3),
            ("radiance_totem", "fossiliferous_dirt", "fossil", 2)):
        declared(f"elysiumarsenal:{item}",
                 [f"placed:elysiumarsenal:{site}", "elysiumarsenal:iron_brush"],
                 DIG_RULES, f'new Find("elysiumarsenal:{item}", {weight}),',
                 f"the {pool} pool, which {site} draws from, holds {item}")

    # An unidentified artifact, used, becomes one of the three, equally likely.
    declared("/^elysiumarsenal:identified_/", ["elysiumarsenal:unidentified_artifact"],
             ARSENAL + "item/ArtifactItem.java",
             "ArtifactRules.identify(level.getRandom().nextInt());",
             "using an unidentified artifact rolls one of the artifacts and gives its identified item")

    # ------------------------------------------------------------------
    # elysium-bestiary: the two ability entities
    # ------------------------------------------------------------------
    # SummonEntities.fillIn hands the library the bolt and the shade;
    # ElysiumAbilityUse.thrown / calledUp make them when a relic's active is
    # pressed (RelicItems.RelicItem.use). Which ability throws or summons is
    # the shape table; which relic carries it is arcana's RelicRules.
    declared("creature:elysiumbestiary:bolt", ["elysiumarcana:hunters_mark_ring"],
             SHAPES, 'made.put("hunters_mark", BOLT);',
             "the hunter's mark ring's on-use ability is thrown: it makes bolts")
    declared("creature:elysiumbestiary:bolt", ["elysiumarcana:stormcallers_rod"],
             SHAPES, 'made.put("stormcall", BOLT);',
             "the stormcaller's rod's on-use ability is thrown: it makes bolts")
    declared("creature:elysiumbestiary:shade", ["elysiumarcana:gravewake_censer"],
             SHAPES, 'made.put("gravewake", SUMMON);',
             "the gravewake censer's sneak-use ability summons shades beside the user")

    # ------------------------------------------------------------------
    # elysium-mobs: the two bosses
    # ------------------------------------------------------------------
    # A dungeon's throne room asks the library bestiary for a BOSS of any
    # faction (DungeonRooms.spawnBoss); the only BOSS entries are these two.
    declared("creature:elysiummobs:choir", ["dimension:elysiumdungeons:dungeon"],
             "elysium-mobs/src/main/java/com/elysium/mobs/ElysiumMobs.java",
             'bossEntry("choir", ElysiumFaction.UNSWORN, CHOIR);',
             "the choir is one of the two BOSS entries a dungeon's throne room draws from")
    declared("creature:elysiummobs:praetor", ["dimension:elysiumdungeons:dungeon"],
             "elysium-mobs/src/main/java/com/elysium/mobs/ElysiumMobs.java",
             'bossEntry("praetor", ElysiumFaction.EMPIRE, PRAETOR);',
             "the praetor is one of the two BOSS entries a dungeon's throne room draws from")
    # And in the overworld: the Empire's register siege at the Hunted band.
    declared("creature:elysiummobs:praetor", [],
             "elysium-mobs/src/main/java/com/elysium/mobs/register/RegisterScheduler.java",
             "spawnPraetor(level, player, centre, random);",
             "a player at Hunted Suspicion (75: 12 a kill of an Empire mob, or ore mining) gets a "
             "five-wave register siege whose last wave is a Praetor")

    # ------------------------------------------------------------------
    # elysium-spawners: the carried spawner
    # ------------------------------------------------------------------
    # SpawnerPickup.onDrops: a vanilla spawner broken with Silk Touch drops
    # this module's spawner item holding the vanilla one's contents. Its inputs
    # are the base game's (a spawner the world placed, a Silk Touch pickaxe);
    # the walk has no base-game placements, so placed:minecraft:spawner would
    # never be met, and minecraft:spawner is never an item.
    declared("elysiumspawners:spawner", [],
             "elysium-spawners/src/main/java/com/elysium/spawners/event/SpawnerPickup.java",
             "boolean vanilla = isVanillaSpawner(entity);",
             "a vanilla spawner (monster room, mineshaft, stronghold) mined with a Silk Touch "
             "pickaxe drops an Elysium spawner holding what it held")

    # And broken without Silk Touch, any spawner (vanilla's too) rolls the
    # unstable_spawner table instead: SpawnerPickup.smashed.
    declared("table:elysiumspawners:unstable_spawner", [],
             "elysium-spawners/src/main/java/com/elysium/spawners/event/SpawnerPickup.java",
             ".getLootTable(UNSTABLE);",
             "a spawner smashed without Silk Touch gives up the parts its cage was made of")

    # Not declared, because the walk already reaches them by their own biome
    # modifiers: the bestiary's CALL summons (BeastActs.callsKin), SPLIT pieces
    # (BeastActs.comesApart), kin SUMMONS copies, court HERALD copies and the
    # companions. Declaring them would only duplicate the spawn.
