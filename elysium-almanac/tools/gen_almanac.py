#!/usr/bin/env python3
"""
The words in the book, and the two files the item needs.

    python3 tools/gen_almanac.py

The quest *graph* is in AlmanacRules, where a harness can play it. The *words*
are here, because they are the half a pack author will actually want to change
and nobody should have to recompile to reword a hint. The ids are read back out
of the Java rather than typed again, so a quest cannot have a title without
existing or exist without a title — validate.py checks both.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
NS = "elysiumalmanac"

OUT = os.environ.get("ELYSIUM_ALMANAC_OUT", MOD)
ASSETS = os.path.join(OUT, "src/main/resources/assets", NS)
DATA = os.path.join(OUT, "src/main/resources/data", NS)
RULES = os.path.join(MOD, "src/main/java/com/elysium/almanac/rules/AlmanacRules.java")

try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Pillow is needed to draw the sprite: pip install pillow")
    sys.exit(1)


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(obj, handle, indent=2)
        handle.write("\n")


def ids():
    """Every quest id and chapter id, read out of the Java."""
    source = open(RULES, encoding="utf-8").read()
    quests = re.findall(r'quest\("([a-z_]+)", "([a-z_]+)"', source)
    chapters = re.findall(r'new Chapter\("([a-z_]+)"', source)
    return [q for q, _ in quests], list(dict.fromkeys(chapters))


# ---------------------------------------------------------------------------
# The words
# ---------------------------------------------------------------------------
#
# A title a player can scan, and one line saying why it is worth doing. The
# second is the whole point of a guide: "craft a hand crank" is a checklist,
# "everything in the first age is turned by something, and this is the
# something you can afford on day one" is a direction.

CHAPTERS = {
    "first_light": ("First Light",
                    "Rotation, and nothing that needs power at all."),
    "reconstruction": ("The Reconstruction",
                       "Steel, wire, and the first grid."),
    "warehouse": ("The Warehouse",
                  "Somewhere to put all of it."),
    "garden": ("The Garden",
               "Things that grow, including metal."),
    "ascent": ("The Ascent",
               "Power you can keep, and the first thing that can kill you."),
    "automation": ("Automation",
                   "The warehouse stops being somewhere you look things up "
                   "and starts being somewhere you ask for them."),
    "foundry": ("The Foundry",
                "Alloys, and the top of the material chain."),
    "frontier": ("The Frontier",
                 "Somewhere else entirely."),
    "the_dark": ("The Dark",
                 "Something to hit things with, and the things."),
    "the_homestead": ("The Homestead",
                      "Somewhere worth coming back to, and what happens if "
                      "you do not."),
    "the_wilds": ("The Wilds",
                  "Knowing where anything is, which is most of knowing what "
                  "to do next."),
}

QUESTS = {
    # --- The Dark ------------------------------------------------------
    "first_blade": ("A sabre",
                    "Six shapes of weapon here, and this is the quick one. It "
                    "does less per hit than almost anything else and gets more "
                    "hits in than all of it."),
    "a_reason_to_choose": ("A spear",
                           "Reach, and nothing else at all. You will hit "
                           "things before they hit you, and you will be very "
                           "sorry the first time one gets inside it."),
    "the_long_answer": ("A greatsword",
                        "Slow, enormous, and it takes two more things with it "
                        "on every swing. The answer to being surrounded, and "
                        "a poor answer to anything else."),
    "the_narrow_answer": ("An estoc",
                          "Half of what it does goes through armour rather "
                          "than into it. Against anything in plate it is the "
                          "best thing here; against anything else it is not."),
    "the_heavy_answer": ("A warhammer",
                         "The hardest single hit in the pack and the slowest "
                         "swing behind it. Bring it to something large and "
                         "leave it at home the rest of the time."),
    "spallet": ("Something in the dark",
                  "A spallet is the smallest thing that hunts you and the "
                  "one you will meet first. It comes apart when you hit it "
                  "and the pieces keep coming."),
    "the_three_families": ("Three kinds of trouble",
                           "A drayhulk will not be knocked back and a carrier "
                           "burns at dawn. Everything in the dark is one of "
                           "three families and each wants a different answer."),
    "worth_the_trouble": ("Worth the trouble",
                          "The second rung of all three ladders. Every one of "
                          "them is worth more than the thing below it, and "
                          "that is a promise this pack actually checks."),
    "the_hollow_lord": ("The nullprime",
                        "It waited a long time for something, and it was not "
                        "you. Bring the heaviest thing you have and do it in "
                        "daylight if you can arrange that."),
    "the_siege_beast": ("The siegehulk",
                        "Three metres of it, and it goes through the wall "
                        "rather than round it. The hardest thing in the "
                        "bestiary and the best-paid."),
    "the_stalker": ("The prowlframe",
                    "You will hear it stop following you, and that is the bad "
                    "part. The top of the prowlers, and the fastest thing "
                    "that will ever chase you."),
    "broad_tool": ("A hammer",
                   "Clears a three-by-three, costs a point of durability for "
                   "every block of it, and swings slower than the pickaxe it "
                   "is standing in for. All three of those are on purpose."),

    # --- The Homestead -------------------------------------------------
    "somewhere_to_sit": ("A chair",
                         "Twelve shapes, sixteen materials, and not one of "
                         "them needs a texture the game did not already have. "
                         "Start with somewhere to sit down."),
    "somewhere_to_put_things": ("A shelf",
                                "The other half of what a room needs. Every "
                                "shape here can be broken back down, and none "
                                "of them gives back more than it cost."),
    "a_door_of_stone": ("A door of stone",
                        "The game has eleven wooden doors and one of iron. "
                        "This is a door of stone, and like the iron one it "
                        "wants redstone rather than a hand."),
    "a_wall_that_joins": ("A wall that joins",
                          "Fences of stone, with a gate in them. They reach "
                          "for each other and not for the oak fence next "
                          "door, which is the whole difficulty with fences."),
    "a_window": ("A window",
                 "Panes in anything at all, which the game does not have: it "
                 "has glass and it has iron bars, and nothing between them "
                 "in any material you like."),
    "the_grave": ("If the worst happens",
                  "Everything you were carrying goes into a grave where you "
                  "fell, sized to what you actually had, and nobody else can "
                  "open it until you have plainly given up on it."),
    "the_crate": ("A trader's crate",
                  "Use it on a villager to pick them up and on a block to put "
                  "them down. Their trades, their level and their restock "
                  "clock all come with them."),

    # --- The Wilds -----------------------------------------------------
    "the_compass": ("A surveyor's compass",
                    "Pick somewhere and it points at the nearest one. It looks "
                    "the same distance for everything, however rare, which is "
                    "why it does not make the server stutter."),
    "somewhere_else": ("A waystone",
                       "Bind one, and then bind another, and the distance "
                       "between them costs power rather than time. The price "
                       "rises with distance and stops rising."),
    "a_road": ("A road",
               "Paving is worth most to whatever is slowest, which is the "
               "opposite of what you would guess and is the reason a cart on "
               "a road beats a hauler off one."),
    "welcome": ("Start here",
                "Everything in this pack hangs off four ages of machinery. You "
                "are at the bottom of the first one, and it needs no power."),
    "crank": ("Turn something by hand",
              "Every machine in the first age is turned by something. A crank "
              "is the something you can afford on day one."),
    "shaft": ("Lay a shaft",
              "A shaft carries rotation. Prod any part of a line and it tells "
              "you what the whole line is doing."),
    "millstone": ("Grind it",
                  "Cobblestone to gravel, bone to meal, ore to more ore later. "
                  "The first machine that pays for itself."),
    "water_wheel": ("Let the river do it",
                    "A crank stops when you walk away. A water wheel does not, "
                    "and that is the whole of the first age's lesson."),
    "brass_shaft": ("Timber will not do",
                    "Timber shears above 64 rpm. Brass takes 192, and nothing "
                    "in the second age will run on less."),
    "saw": ("Cut it",
            "Six planks to a log instead of four, and the cheapest planks in "
            "the game from then on."),
    "alternator": ("Make electricity",
                   "Rotation in, power out, six times what a dynamo manages. "
                   "It draws more torque than one water wheel can push."),
    "copper_wire": ("String a wire",
                    "A grid divides what it has: everything on it gets the "
                    "same share. Copper carries 256 before it burns."),
    "turbine": ("Burn something for it",
                "A firebox and a boiler. It only burns when something on the "
                "grid actually wants the power."),
    "crusher": ("Crush it",
                "What the millstone becomes, at industrial scale and on a "
                "wire."),
    "rolling_mill": ("Roll it flat",
                     "Plate is what every machine above this is skinned in. "
                     "Nothing in the first age could make a sheet."),
    "plate": ("Make some plate",
              "You will need a great deal of it. A factory is a thing you "
              "build with a factory."),
    "arc_furnace": ("Heat without fire",
                    "512 FE a tick, which is twice what a copper wire will "
                    "carry. This is the machine that teaches you voltage."),
    "steel": ("Steel",
              "The first thing the age makes for itself, and the gate to the "
              "top tier of wire, the diesel engine and the separator."),
    "transformer": ("Where the tiers meet",
                    "The one block allowed two voltages on it. Everywhere "
                    "else, the higher one destroys the lower."),
    "separator": ("Twice the metal",
                  "Ore in, twice the metal out. The reason the second age "
                  "exists."),
    "motor": ("Drive the old hall",
              "Electricity back into rotation. Your first machine hall does "
              "not stop being useful — it stops being hand-cranked."),
    "waystone": ("Stop walking",
                 "Bind two and you can jump between them for a price the grid "
                 "pays. An unwired stone fills from nothing, so the short way "
                 "home costs a minute; wire it and the minute goes away."),
    "road": ("Lay something to drive on",
             "A road is worth laying because of how much of a journey it "
             "saves, which means it is worth most to whatever is slowest. "
             "Build it before the vehicle, not after."),
    "cart": ("Something to drive on it",
             "It runs on anything that burns, carries nine stacks and loses "
             "to walking over rough ground — which is the whole reason you "
             "laid the road first."),
    "drawer": ("One thing, a lot of it",
               "A drawer holds one kind and thousands of it, costs nothing to "
               "run, and you can read how full it is across a room."),
    "steel_drawer": ("A wall of them",
                     "Four kinds and eight thousand of each. This is what a "
                     "factory's wall is made of."),
    "controller": ("Index it",
                   "Every drawer on its bus behaves as one inventory. Point a "
                   "hopper at it and it finds the right drawer."),
    "pot": ("Farm without a field",
            "One block, stackable in a room, feedable by a hopper. It wants "
            "light 9 or nothing grows."),
    "seed": ("Grow metal",
             "Slowly, on purpose: a farm is meant to lose to a mine until it "
             "is a deliberate industrial project."),
    "accelerator": ("Hurry it along",
                    "A room of pots works on torches. A room that keeps up "
                    "with a factory needs a factory."),
    "hive": ("Keep bees",
             "Two flowers a bee. Too few and it slows down rather than "
             "stopping, so go and plant something."),
    "rock": ("Make rock",
             "Everybody builds a cobblestone generator anyway. This one is a "
             "block, and it has tiers."),
    "cell": ("Keep what you make",
             "Every grid until now spent what it made the tick it made it. A "
             "cell is the first time surplus is worth having."),
    "solar": ("The sun is free",
              "Three FE a tick, which is almost nothing. A panel is not a "
              "generator — it is the reason a cell is worth building."),
    "centrifuge": ("Spin it",
                   "The hungriest machine in the age, and the only route to "
                   "reactor fuel."),
    "fuel": ("Enrich it",
             "One piece runs a reactor at full throttle for five minutes. "
             "You will want a supply before you light one."),
    "coolant": ("Plumb it first",
                "Four channels. A bare core at full throttle settles at "
                "several times meltdown; three channels is not enough."),
    "reactor": ("Light a core",
                "Prod it: it tells you the temperature now and the one this "
                "setting will reach. Do the arithmetic before you turn it up."),
    "enricher": ("Three times the metal",
                 "The last rung of the ore ladder. Nothing above this does "
                 "better."),
    "servo": ("Faster than anything below",
              "It drives an Age I line at 1024 rpm, which no shaft below "
              "ascendant survives. Rebuild the old hall in something stronger."),
    "assembly_core": ("Ask for it instead",
                      "It reads every recipe the game has loaded, works out "
                      "what a request would take, and tells you before it "
                      "touches a single item in your drawers."),
    "assembler": ("Hands for it",
                  "The core plans and builds nothing. Four assemblers is four "
                  "jobs at once; sixteen is as many as one core will run, and "
                  "every one of them is a machine on the grid."),
    "terminal": ("Hold up what you want",
                 "No screen and nothing to search: right-click it holding a "
                 "thing for a stack of that thing, crouch for one. What comes "
                 "back is how many steps and how many machine-runs, or why "
                 "not."),
    "smelter": ("Two metals at once",
                "The only machine the fourth age adds. Everything else it "
                "needs, you already built."),
    "bronze": ("Bronze",
               "Three copper and a tin. The first alloy, and the first time "
               "the chain has a step in it."),
    "invar": ("Invar",
              "Two iron and a nickel. What a second-tier machine casing is."),
    "stainless": ("Stainless",
                  "Three steel and a nickel. An alloy of an alloy, which is "
                  "where a furnace stops being enough."),
    "starsteel": ("The top of the chain",
                  "An alloy of an alloy of an alloy, and the only thing a tier "
                  "four casing can be made of. Nothing in the pack is above "
                  "it, so a starsteel machine is the last one you build."),
    "casing": ("Build the last casing",
               "A tier four machine runs on the top wire in the game. You have "
               "reached the end of the material chain."),
    "transmuter": ("Turn things into other things",
                   "Dissolve something and you get most of its worth back as "
                   "motes; spend motes and you get back anything you have "
                   "dissolved before. A round trip always loses a tenth, so "
                   "there is no loop in it worth riding."),
    "look_up": ("Look up",
                "There are five worlds besides this one, and waygates that "
                "open at ruined-portal frequency. Go and find one."),
    "luna": ("Stand on the moon",
             "No air, no weather, and a crop that grows in vacuum and nothing "
             "else."),
}

LINES = {
    "itemGroup.elysiumalmanac": "Elysium Almanac",
    "item.elysiumalmanac.almanac": "Almanac",
    "elysiumalmanac.tooltip": "What to do next.",
    "elysiumalmanac.book": "Almanac",
    "elysiumalmanac.book.status": "%s of %s done.",
    "elysiumalmanac.book.empty": "Nothing in it yet.",
    "elysiumalmanac.chapter.progress": "%s of %s",
    "elysiumalmanac.quest.done": "Done.",
    "elysiumalmanac.quest.now": "You can do this now.",
    "elysiumalmanac.quest.after": "After: %s",
    "elysiumalmanac.task.have": "Have %s x %s",
    "elysiumalmanac.task.place": "Place a %s",
    "elysiumalmanac.task.visit": "Stand in %s",
    "elysiumalmanac.task.read": "Read this.",
    "elysiumalmanac.task.missing": "%s — nothing installed makes this.",
    "elysiumalmanac.back": "Back",
    "elysiumalmanac.finished": "Almanac: %s",
}


def language(quests, chapters):
    entries = dict(LINES)
    for chapter in chapters:
        title, why = CHAPTERS[chapter]
        entries[f"{NS}.chapter.{chapter}"] = title
        entries[f"{NS}.why.chapter.{chapter}"] = why
    for quest in quests:
        title, why = QUESTS[quest]
        entries[f"{NS}.quest.{quest}"] = title
        entries[f"{NS}.why.{quest}"] = why
    write(os.path.join(ASSETS, "lang/en_us.json"), dict(sorted(entries.items())))
    return len(entries)


# ---------------------------------------------------------------------------
# The one sprite and the one recipe
# ---------------------------------------------------------------------------

CLEAR = (0, 0, 0, 0)
COVER = [(46, 34, 58), (74, 56, 92), (108, 84, 132), (150, 124, 176)]
PAGE = [(196, 188, 168), (222, 216, 198), (240, 236, 224), (252, 250, 244)]
MARK = (127, 112, 196)


def rgba(colour):
    return (colour[0], colour[1], colour[2], 255)


def book():
    image = Image.new("RGBA", (16, 16), CLEAR)
    draw = ImageDraw.Draw(image)
    draw.rectangle([2, 2, 13, 13], fill=rgba(COVER[1]), outline=rgba(COVER[0]))
    draw.rectangle([4, 3, 12, 12], fill=rgba(PAGE[2]), outline=rgba(PAGE[0]))
    draw.line([(3, 2), (3, 13)], fill=rgba(COVER[3]))
    for y in (5, 7, 9):
        draw.line([(6, y), (10, y)], fill=rgba(PAGE[0]))
    draw.line([(11, 1), (11, 6)], fill=rgba(MARK))
    return image


def files(quests, chapters):
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    book().save(os.path.join(ASSETS, "textures/item/almanac.png"))
    write(os.path.join(ASSETS, "models/item/almanac.json"),
          {"parent": "item/generated", "textures": {"layer0": f"{NS}:item/almanac"}})
    write(os.path.join(DATA, "recipe/almanac.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"item": "minecraft:book"}, {"item": "minecraft:feather"}],
        "result": {"id": f"{NS}:almanac", "count": 1},
    })
    return 3


if __name__ == "__main__":
    quests, chapters = ids()
    missing = [q for q in quests if q not in QUESTS]
    spare = [q for q in QUESTS if q not in quests]
    if missing or spare:
        print(f"the words and the graph disagree: {missing} have no words and {spare} "
              f"are words for nothing")
        sys.exit(1)
    made = files(quests, chapters)
    keys = language(quests, chapters)
    print(f"almanac: {len(chapters)} chapter(s), {len(quests)} quest(s), {made} file(s), "
          f"{keys} language key(s)")
