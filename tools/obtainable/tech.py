"""Code sources for the tech group: elysiumkinetics, elysiumindustry, elysiumlogistics,
elysiumatomics, elysiumfoundry.

Loaded by check_obtainable.py through OBTAINABLE_SOURCES_EXTRA. Each entry is pinned to the
Java that hands the thing out; the checker fails the day those words leave the file.
"""

INDUSTRY = "elysium-industry/src/main/java/com/elysium/industry/rules/FurnaceRules.java"


def declare(declared):
    # The brick machines (FurnaceRules.FIRINGS, run by FurnaceBlock.FurnaceBlockEntity.work).
    # Their jobs are Java, not recipe JSON. A thing goes in by right-click, one a click, and only
    # if the machine has a job for it (FurnaceRules.slotFor). What it made comes out to an empty
    # hand on the formed controller (FurnaceBlock.useWithoutItem -> take), and all three slots
    # drop when the controller is broken; the FURNACE block entity still has no item capability.
    # The blast furnace burns FurnaceRules.COKE_PER_BLAST coke a job (FurnaceRules.FUELS, slot 2)
    # and does not start a job without it, so steel needs coke.
    declared("elysiumindustry:coke",
             ["elysiumindustry:coke_oven", "elysiumindustry:coke_brick", "minecraft:coal"],
             INDUSTRY, '"elysiumindustry:coke", 1, COKING_TICKS',
             "a formed coke oven (3x3x3 of coke brick round one hollow block) turns one coal into "
             "one coke in 900 ticks; an empty hand on the controller takes it")
    declared("elysiumindustry:steel_ingot",
             ["elysiumindustry:blast_furnace", "elysiumindustry:blast_brick", "minecraft:iron_ingot",
              "elysiumindustry:coke"],
             INDUSTRY, '"elysiumindustry:steel_ingot", 1, BLASTING_TICKS',
             "a formed blast furnace (3x3x4 of blast brick) turns one iron ingot and one coke into "
             "one steel ingot in 600 ticks; an empty hand on the controller takes it")
