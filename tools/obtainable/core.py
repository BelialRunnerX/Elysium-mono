"""
Declared sources for elysium-core (elysium), elysium-npcs (elysiumnpcs),
elysium-colonies (elysiumcolonies) and elysium-lib (elysiumlib): what only Java
hands out. Each is pinned to the file and the words that do the handing out.

Loaded by check_obtainable.py through OBTAINABLE_SOURCES_EXTRA.
"""

CORE = "elysium-core/src/main/java/com/elysium/core/"
NPCS = "elysium-npcs/src/main/java/com/elysium/npcs/"
COLONIES = "elysium-colonies/src/main/java/com/elysium/colonies/"
LIB = "elysium-lib/src/main/java/com/elysium/lib/"


def declare(declared):
    # --- First login --------------------------------------------------------
    # ElysiumStartingKit.onJoin rolls elysium:gameplay/starting_kit (a gift
    # table: 8 bread, 16 torches) once per player. The table held the Imperial
    # Codex too until 29 Sep, a second copy: the library hands one out below.
    declared("table:elysium:gameplay/starting_kit", [],
             CORE + "event/ElysiumStartingKit.java",
             'fromNamespaceAndPath(Elysium.MODID, "gameplay/starting_kit")',
             "every player's first login opens the starting-kit gift table (bread, torches)")
    # elysium-lib's ElysiumJoinHandler.onLoggedIn hands over the codex a content
    # mod supplies through ElysiumHooks.setCodex (ElysiumContent: the Imperial
    # Codex), once per player (elysiumlib:codex_given). The codex's only source.
    declared("elysium:imperial_codex", [],
             LIB + "event/ElysiumJoinHandler.java",
             "ItemStack codex = ElysiumHooks.codex();",
             "every player's first login: elysium-lib's join handler hands over the codex elysium-core "
             "supplies through ElysiumHooks.setCodex, once (elysiumlib:codex_given)")

    # --- Standing dispatch (elysium-lib ElysiumStandingHandler.considerDispatch)
    # Every 200 ticks each faction's dispatcher may send one mob at the player;
    # ElysiumStanding.spawnChance is zero at or below Notice (25), so the meter
    # must be 26+. Core registers the only two dispatchers.
    declared("creature:elysium:imperial_enforcer", ["placed:elysium:aetherium_ore"],
             CORE + "ElysiumContent.java",
             "Elysium.IMPERIAL_ENFORCER.get().create(level)",
             "the Empire dispatcher sends enforcers at a player whose Suspicion is above 25 (Noted); "
             "breaking the Empire's registered ores raises it (+2 aetherium or voidglass, +4 neutronium), "
             "as do unregistered works (furnaces, metal blocks outside a claim)")
    declared("creature:elysium:unsworn_raider", ["creature:minecraft:zombie"],
             CORE + "ElysiumContent.java",
             "Elysium.UNSWORN_RAIDER.get().create(level)",
             "the Unsworn dispatcher sends raiders at a player whose Favor is above 25; any vanilla hostile "
             "killed gives +1 Favor one time in three (INCIDENTAL_FAVOR_CHANCE), about 75 kills from a new world")

    # --- The Emperor's Crown --------------------------------------------------
    declared("elysium:emperor_crown", ["creature:elysium:imperial_enforcer"],
             CORE + "event/ElysiumCrownDrops.java",
             "new ItemStack(Elysium.EMPEROR_CROWN.get())",
             "a named Empire combatant (an enforcer, or an Empire elysiummobs creature) killed by a player at "
             "Hunted Suspicion (75+) drops the Crown 4% of the time (CROWN_CHANCE)")

    # --- Inspection beacon (planted by the Empire, no item, empty loot) -------
    declared("placed:elysium:inspection_beacon", ["placed:elysium:aetherium_ore"],
             CORE + "block/InspectionBeaconBlock.java",
             "com.elysium.core.Elysium.INSPECTION_BEACON.get()",
             "at Suspicion 25+, breaking 64 registered ores inside five minutes (ElysiumStandingHandler.extracted) "
             "or having unregistered works noticed (ElysiumWorksHandler) plants a post on the surface nearby")

    # --- Gem dust --------------------------------------------------------------
    # elysium:gem_grinding is a special crafting recipe (data/elysium/recipe/
    # gem_grinding.json turns it on): any ElysiumRuneItem alone in the grid.
    declared("elysium:gem_dust", ["elysium:voidward_rune"],
             CORE + "recipe/GemGrindingRecipe.java",
             "new ItemStack(Elysium.GEM_DUST.get(), Math.min(dust, 64))",
             "any one of the nine Elysium runes alone in a crafting grid grinds to gem dust (at least one)")

    # --- Colonies: the ruin's chest --------------------------------------------
    # The elysiumcolonies:ruin structure places only the ruin_seed marker; on its
    # first random tick Ruins.raise builds a decayed MineColonies building and
    # sets one chest's loot table to RuinRules.LOOT = elysiumcolonies:chests/ruin.
    declared("table:elysiumcolonies:chests/ruin", ["placed:elysiumcolonies:ruin_seed"],
             COLONIES + "settle/Ruins.java",
             "ResourceLocation.parse(RuinRules.LOOT)",
             "a ruin marker (12+ chunks from spawn, in a world whose charter settles) raises a ruined "
             "building with one chest of this table on its first random tick")

    # --- The court ---------------------------------------------------------------
    # EnvoyScheduler: every 5 minutes, 35%, one envoy chosen by CourtRules.arrivals:
    # of the half of the court the higher meter picks (ties go to Suspicion),
    # those who will deal; when nobody there will, anybody who will. The
    # Sentinel deals at band 0, so a player at any standing is visited.
    declared("creature:elysiumnpcs:envoy", [],
             NPCS + "EnvoyScheduler.java",
             "ImperialEnvoy envoy = ElysiumNpcs.ENVOY.get().create(level);",
             "every five minutes a player has a 35% chance of a visiting envoy; at any standing somebody "
             "comes, since the Sentinel deals at band 0 and CourtRules.arrivals falls back to whoever will deal")
    declared("elysiumnpcs:writ_of_amnesty",
             ["creature:elysiumnpcs:envoy", "elysium:aetherium_ingot", "elysium:voidglass_ingot"],
             NPCS + "entity/ImperialEnvoy.java",
             "new ItemStack(ElysiumNpcs.WRIT_OF_AMNESTY.get())",
             "every Suspicion-reading envoy carries a guaranteed amnesty row (2 uses) priced at its top tier; "
             "the Sentinel's is 4-6 aetherium or voidglass ingots (ElysiumContent.registerPrices tier 1)")
    # Every envoy sells its own writ of audience as a fixed row after the
    # amnesty writ, one a visit (CourtRules.AUDIENCE_USES), priced at the
    # office's top tier. The screen refuses below the office's band, so each
    # writ is sold where its envoy deals. The needs are the price metals of all
    # five, which is stricter than any one writ: the Sentinel's, the
    # Architect's and the Commander's need only aetherium or voidglass.
    declared("/^elysiumnpcs:.*_summons$/",
             ["creature:elysiumnpcs:envoy", "elysium:aetherium_ingot", "elysium:voidglass_ingot",
              "elysium:neutronium_ingot"],
             NPCS + "entity/ImperialEnvoy.java",
             "new ItemStack(ElysiumNpcs.summonsFor(kind).get())",
             "every envoy sells its own writ of audience, one a visit, at its office's top tier "
             "(ElysiumContent.registerPrices): the Sentinel's to anybody for 4-6 aetherium or voidglass "
             "ingots, the Architect's at Suspicion 25+ and the Commander's at Favor 25+ for 12-16, the "
             "Emperor's at Hunted (75+) and the Queen's at Exalted (75+) for 24-32 neutronium ingots")
