#!/usr/bin/env python3
"""
The conversion manifest: every jar in the pack, and what we decided about it.

    python3 tools/manifest.py <a file listing the pack's jars> > docs/MANIFEST.md

Why this is generated rather than hand-written
----------------------------------------------
Because it is two hundred rows and it has to be complete. A hand-written list
of two hundred mods is a list with three mods missing, and the three missing
ones are the ones nobody converts — which is exactly the failure the user asked
to be designed out ("document and do this in a way that avoids missing steps").

So the pack's own file listing is the input, the decisions are a table here,
and anything in the listing with no decision is reported as an error rather
than quietly dropped.

Status values
-------------
  DONE     an Elysium module already replaces it
  PLANNED  it has a target module and has not been started
  ACTIVE   being worked on now
  DROP     a library or a performance mod: nothing to convert
  KEEP     stays in the pack (a host we add on to, or a bundled dependency)
"""
import os
import re
import sys

# (regex on the jar's name, decision, target module, note)
#
# First match wins, so specific patterns come before general ones.
DECISIONS = [
    # --- already replaced ------------------------------------------------
    (r"^Apotheosis-", "DONE", "elysium-ascendancy", "loot, affixes, gems, rarities"),
    (r"^ApothicAttributes", "DONE", "elysium-lib", "the twenty attributes and the combat model"),
    (r"^ApothicEnchanting", "DONE", "elysium-enchanting", "the Arcane Table"),
    (r"^ApothicSpawners", "DONE", "elysium-spawners", "ownable, tunable spawners"),
    (r"^Placebo-", "DONE", "elysium-lib", "Apotheosis' own library"),
    (r"^silent-gear", "DONE", "elysium-armory", "modular gear, parts, materials, traits"),
    (r"^silent-lib", "DONE", "elysium-armory", "Silent Gear's library"),
    (r"^puffish_skills", "KEEP", "elysium-core", "the skill tree ships as a datapack for it"),
    (r"^puffish_attributes", "KEEP", "elysium-core", "read by the skill tree"),
    (r"^Stronger Skill Tree", "DONE", "elysium-core", "our own tree, shipped in the jar"),
    (r"^minecolonies", "KEEP", "elysium-colonies", "the host this module adds on to"),
    (r"^structurize|^blockui|^domum-ornamentum|^multipiston", "KEEP", "elysium-colonies",
     "MineColonies' own dependencies"),
    (r"^curios-", "KEEP", "elysium-trinkets", "bundled inside elysium-all (jar-in-jar)"),
    (r"^elysium", "DONE", "-", "ours already"),

    # --- age I: kinetics --------------------------------------------------
    (r"^create-1\.|^create_connected|^create-stuff|^createaddition|^createdeco",
     "PLANNED", "elysium-kinetics", "Age I: rotation, stress, contraptions"),
    (r"^create", "PLANNED", "elysium-kinetics", "Create addon: folds into Age I"),
    (r"^CreateDragonsPlus", "DROP", "-",
     "read rather than assumed: its own language file names no entity at all. "
     "61 blocks and 57 items, and every one of them is a liquid dye or a dye "
     "bucket for some other mod's palette, plus dragon breath and a blaze "
     "smithing template. The row that sent it to the bestiary was written from "
     "the mod's name"),
    (r"^copycats|^sliceanddice|^interiors", "PLANNED",
     "elysium-kinetics", "Create addon: folds into Age I"),

    # --- age II: industry -------------------------------------------------
    (r"^ImmersiveEngineering", "PLANNED", "elysium-industry", "Age II: the Reconstruction"),
    (r"^tfmg", "PLANNED", "elysium-industry", "heavy industry, oil and steel"),
    (r"^powergrid", "PLANNED", "elysium-industry", "transmission on poles"),
    (r"^oritech", "PLANNED", "elysium-industry", "machines between Ages II and III"),

    # --- age III: atomics -------------------------------------------------
    (r"^Mekanism", "PLANNED", "elysium-atomics", "Age III: chemistry and fission"),
    (r"^Applied-Mekanistics", "PLANNED", "elysium-atomics", "chemical storage in the network"),
    (r"^Powah", "PLANNED", "elysium-atomics", "reactors and energy cells"),
    (r"^ExtremeReactors2|^ZeroCore2", "PLANNED", "elysium-atomics", "multiblock reactors"),
    (r"^FluxNetworks", "PLANNED", "elysium-atomics", "wireless power"),

    (r"^industrialforegoing", "PLANNED", "elysium-atomics",
     "bulk automation: the machines that do a farm's work"),

    # --- transit ----------------------------------------------------------
    (r"^waystones", "DONE", "elysium-transit",
     "bind, jump, and a price the grid pays; sits beside the waygate rather than "
     "replacing it"),

    # --- logistics --------------------------------------------------------
    (r"^appliedenergistics2|^ExtendedAE", "PLANNED", "elysium-logistics",
     "autocrafting is done — core, assemblers, terminal, and a planner that orders "
     "what it costs; digital storage in types rather than blocks is not"),
    (r"^refinedstorage", "PLANNED", "elysium-logistics", "the simpler network"),
    (r"^StorageDrawers|^functionalstorage|^dankstorage", "DONE", "elysium-logistics",
     "three drawer tiers, a bus, and a controller that indexes the lot"),
    (r"^enderchests|^allthecompressed", "PLANNED", "elysium-logistics",
     "storage at a distance, and compression"),
    (r"^sophisticated|^modular_backpacks", "PLANNED", "elysium-logistics",
     "upgradeable containers"),
    (r"^integrateddynamics|^commoncapabilities|^cyclopscore", "PLANNED",
     "elysium-logistics", "network logic"),

    # --- agronomy ---------------------------------------------------------
    (r"^MysticalAgriculture", "PLANNED", "elysium-agronomy",
     "growing resources: 30 seeds across 5 rungs, one per material the ground "
     "gives you, with a chunk item for the 17 the foundry makes ingots of. No "
     "seed grows an alloy, which is checked against the foundry's own list. "
     "Its tools, armour, awakening altars and augments do not exist"),
    (r"^MysticalAgradditions", "PLANNED", "elysium-agronomy",
     "growing resources: the tier-6 crops and insanium are not started"),
    # Split out from one row per jar once each was actually looked at. The
    # notes say what exists and what does not, because a note that says
    # "farming, food and fishing" for six jars is a note that tells nobody
    # whether any of it has been done.
    (r"^productivebees", "PLANNED", "elysium-agronomy",
     "bees: 75 species with combs, comb blocks, nests and the breeding tree "
     "exist; the apiary machinery does not. Of its 463 bee entities only 34 "
     "name a material this pack makes"),
    (r"^tide-", "PLANNED", "elysium-agronomy",
     "fishing: 105 fish with items, buckets, eggs, five rods and a bait per "
     "habitat exist; bobbers, hooks and the fishing blocks do not"),
    (r"^Aquaculture", "PLANNED", "elysium-agronomy",
     "fishing: covered by the same 105-fish roster; the fish mounts, tackle "
     "box, worm farm and neptunium gear do not exist"),
    (r"^botanypots", "PLANNED", "elysium-agronomy",
     "pots: 165 working pots across 55 materials, all three fittings, four "
     "growth stages, 14 soils and 28 sprouts as data. Its 183 blocks come "
     "from 61 materials, so six of its materials are not covered"),
    (r"^solcarrot", "DONE", "elysium-agronomy",
     "a varied diet: thirty different foods for ten hearts, kept as a set so "
     "eating the same thing twice counts once, surviving death, applied as one "
     "attribute modifier that replaces itself, and a book that reads it"),
    (r"^FarmersDelight", "PLANNED", "elysium-agronomy",
     "cooking: 29 meals across four stations with 11 effects, 24 ingredients, "
     "13 knife cuts, 5 knives and 25 blocks, none of them dominated. Its "
     "canvas signs, mushroom colonies, rice paddies and wild crops do not "
     "exist"),
    (r"^cobblegengalore", "DONE", "elysium-agronomy",
     "seven generators, one per vein, each making its own material at a rate "
     "held under a share of what mining it is worth"),

    # --- arsenal ----------------------------------------------------------
    (r"^simplest_", "DONE", "elysium-arsenal",
     "hammer, excavator, scythe and paxel in six tiers, each proved slower than the "
     "tool it replaces on one block and faster over nine"),
    # Six melee kinds in six tiers now exist - sabre, longsword, greatsword,
    # spear, warhammer, estoc - each of them proved to be the best at
    # something and beaten on every axis by nothing. That covers melee
    # *variety*, which is what a player is missing without these jars. It does
    # not cover what any of them actually is:
    #
    #   simplyswords / mythsandlegends - uniques that carry abilities. Ours
    #     have no ability beyond a sweep and a pierce.
    #   SimplyBows - eight bows with effects. Nothing here is ranged at all.
    #   betterarcheology - digging things up, which is not a weapon mod.
    #   Reforged - three smithing hammers. Checked inside the jar rather than
    #     guessed from the name; it is an upgrade mechanic and has nothing to
    #     do with the weapons above.
    (r"^simplyswords|^mythsandlegends", "PLANNED", "elysium-arsenal",
     "six melee kinds in six tiers exist, none beaten on every axis by another; "
     "uniques that carry abilities do not"),
    (r"^SimplyBows", "PLANNED", "elysium-arsenal",
     "bows with effects; nothing in the module is ranged"),
    (r"^betterarcheology", "PLANNED", "elysium-arsenal",
     "digging things up, which needs somewhere to dig before it needs a tool"),
    (r"^Reforged", "PLANNED", "elysium-arsenal",
     "three smithing hammers - an upgrade mechanic rather than weapons, read out of "
     "the jar rather than guessed from the name"),

    # --- the Vale ---------------------------------------------------------
    #
    # Valoria is a Terraria-shaped adventure mod and by some distance the
    # largest single jar in the programme: about five hundred items, four
    # hundred and fifty blocks, fifty-two creatures, a dimension, twenty-four
    # structures and a codex. It does not get a module of its own — the map
    # here is by *kind*, not by source, and a module named after somebody
    # else's mod would be the one place that rule broke. Its gear goes to the
    # arsenal, its creatures to the bestiary, its rings and charms to the
    # trinkets register, and its realm and structures to the wilds.
    (r"^Valoria-", "PLANNED", "elysium-arsenal", "the Vale: gear, creatures, "
     "charms and a realm, split by kind across arsenal, bestiary, trinkets and wilds"),
    (r"^Tridot-", "DROP", "", "Valoria's own library; goes when Valoria does"),

    # --- bestiary ---------------------------------------------------------
    #
    # Not DONE, and for the same reason the furniture rows below are not. What
    # exists is nine hostile beasts in three families, every one of them on a
    # reward ladder the harness proves rises with danger, sits inside a stated
    # band, and contains the game's own zombie. That is a *floor* under what
    # the dark is worth, and it is the thing none of these jars has — three of
    # them put sixty mobs in the world between them and not one states what any
    # of them is worth against any other.
    #
    # It is not their catalogues. Nine is nine. Marking these DONE would take
    # sixty creatures out of the pack and put nine in their place, which is the
    # atelier mistake with fur on it.
    (r"^born_in_chaos", "PLANNED", "elysium-bestiary",
     "63 creatures and 23 variants of them, 362 items of which 69 are gear, and "
     "a set of mini-bosses — counted out of the jar's own language file rather "
     "than guessed. The bestiary's nine are a ladder with a proved reward curve "
     "and no boss tier at all, and this is the jar that decides what a boss is "
     "here"),
    (r"^fangs_n_claws", "PLANNED", "elysium-bestiary",
     "27 creatures and 7 projectiles and young, in themed families — four "
     "scorpions, three ogres, three horses, two golems, a werewolf and a "
     "werevillager. The shape this module already has; what it has not got is a "
     "mechanic apiece, which is what makes 27 creatures different from 27 sets "
     "of numbers"),
    (r"^mobs_of_mythology", "PLANNED", "elysium-bestiary",
     "8 creatures — automaton, basilisk, chupacabra, drake, kobold and its "
     "warrior, pegasus, sporeling — and 16 items. The smallest of the mob jars "
     "and the one whose creatures come nearest to one mechanic each"),
    (r"^naturalist", "SPLIT", "elysium-bestiary",
     "50 creatures, 122 items and a field guide: three different jobs in one "
     "jar, so it is split rather than given one module. See docs/SPLITS.md"),
    (r"^friendsandfoes", "PLANNED", "elysium-bestiary",
     "14 creatures, each built around exactly one mechanic — a copper golem that "
     "presses buttons, a glare that shows you where the dark is, an iceologer "
     "that drops a cloud on your head. The system that makes that possible now "
     "exists here: nine verbs over nine beasts, one each and none spare, every "
     "magnitude derived from what the beast already is. What is left is the "
     "creatures, not the idea"),
    (r"^knightquest", "SPLIT", "elysium-bestiary",
     "four mods in a trench coat: 173 of its 200 items are gear and only 13 of "
     "its keys are creatures, so the armour belongs where the armour is. See "
     "docs/SPLITS.md"),
    (r"^mob_grinding_utils", "PLANNED", "elysium-bestiary",
     "farming infrastructure rather than creatures - fans, saws, spawner tooling; "
     "it is filed here because it is about mobs and belongs with whatever ends up "
     "owning automation of them"),

    # --- atelier (decoration) ---------------------------------------------
    # Not DONE, and the distinction matters. Twelve shapes in sixteen materials
    # covers what these two are *for* — somewhere to sit, somewhere to put
    # things — and does not cover their catalogues: beds, couches, counters,
    # sinks, dishes, cupboards, drawers. A frame is not a catalogue, and
    # marking these DONE would have taken real content out of the pack with a
    # subset in its place.
    (r"^handcrafted|^mcw-furniture", "PLANNED", "elysium-atelier",
     "the core twelve shapes exist; the rest of their catalogues (beds, couches, "
     "counters, sinks, cupboards, drawers) do not"),
    # The joining kinds now exist - door, trapdoor, gate, fence, pane - but
    # only in the five materials the game has none of, plus panes in
    # everything. Thirty-six blocks. That is a floor under what a wall can be
    # made of and it is emphatically not these catalogues: mcw-doors alone has
    # about forty door styles, all of them wooden, which is the half we
    # deliberately do not duplicate. Roofs, bridges, paths and lights are
    # shapes this module does not have at all.
    (r"^mcw-|^chipped|^amendments|^supplementaries|^beautify|^bellsandwhistles",
     "PLANNED", "elysium-atelier",
     "doors, trapdoors, gates, fences and panes exist in the materials the game lacks "
     "them in (36 blocks); their catalogues, and roofs, bridges, paths and lights, do "
     "not"),
    (r"^DnDesires|^Design-n-Decor|^DustyDecorations|^cluttered|^bf_blockpack|^Clothing_Of_The_Lowlands",
     "PLANNED", "elysium-atelier", "building and decoration"),
    (r"^valhelsia_furniture|^woodworks|^fusion|^everycomp|^Diagonal|^copycat",
     "PLANNED", "elysium-atelier", "building and decoration"),
    # Five blocks whose whole content is an ambient sound, which is what
    # elysium-comforts is for rather than what a decoration module is.
    (r"^Chimes", "PLANNED", "elysium-comforts",
     "six chimes — amethyst, bamboo, carved bamboo, copper, iron and glass "
     "bells — each dyeable sixteen ways, whose whole content is a sound that "
     "plays when the wind moves. 38 blocks and one item, which is what this "
     "module is for"),

    # --- wilds ------------------------------------------------------------
    (r"^wwoo|^\[Neoforge\]ctov", "PLANNED", "elysium-wilds",
     "worldgen and finding your way about it"),
    # The Surveyor's Compass: pick a biome, it finds the nearest and the needle
    # points at it. Two things it does that the mod it replaces does not - a
    # needle rather than a coordinate in a chat line already scrolled past,
    # and a search that costs the server the same for anything however rare.
    # One thing it deliberately does not offer: a configurable radius. That is
    # the whole point rather than a shortfall; a radius a player can raise is
    # a radius that becomes a lag report nobody traces back to the compass.
    (r"^NaturesCompass", "DONE", "elysium-wilds",
     "a biome compass with a needle and a list you type into, on one bounded search "
     "that costs the same for anything; no adjustable radius, on purpose"),
    (r"^Quark", "PLANNED", "elysium-wilds", "split: worldgen here, deco to atelier"),

    # --- comforts ---------------------------------------------------------
    (r"^gravestone", "DONE", "elysium-comforts",
     "a grave sized to what a player was actually carrying, so nothing an inventory mod "
     "added is lost"),
    # NOT the quest book. "Almanac" here is a config library, and
    # letmedespawn declares it as a required dependency — which the prune
    # pre-check caught on the way to removing it. A jar named after the thing
    # you built is not the thing you built.
    (r"^Almanac", "DROP", "-", "a config library letmedespawn requires"),
    (r"^lootr", "DONE", "elysium-comforts",
     "every player their own roll of a loot container, derived rather than remembered"),
    # The Trader's Crate covers what easy-villagers is chiefly *for* - moving a
    # villager without a boat, a rail and an afternoon - and it covers the part
    # that mod gets wrong: the restock clock travels with the villager, so
    # picking one up and putting it down is not a new day. What it does not
    # cover is that mod's other six blocks: the trading station, the breeder,
    # the converter, the incubator, the farmer and the iron farm. Those are
    # automation, which is a different question from carrying.
    (r"^easy-villagers", "PLANNED", "elysium-comforts",
     "a villager can be carried, with its whole ledger and its restock clock intact; "
     "the trading station, breeder, converter, incubator, farmer and iron farm are not "
     "started"),
    (r"^comforts", "DONE", "elysium-comforts",
     "sixteen sleeping bags, sixteen hammocks and a rope and nail, and none of "
     "them moves your spawn — which is the whole of what made exploring "
     "expensive, because every player carried a bed and never placed one. The "
     "two kinds divide the clock between them: a bag is for the night, a "
     "hammock for the day, and every one of the 24,000 ticks belongs to exactly "
     "one of them, with no hour that is both and none that is neither. You "
     "always wake on the far side of that line, so the thing you just used "
     "cannot be used again until the world has moved on. Six ways it can refuse "
     "you and six sentences to say which, and every block breaks back into "
     "itself"),
    (r"^envelope", "PLANNED", "elysium-comforts",
     "a postal system rather than a convenience: 12 woods of pigeonhole, a "
     "mailbox, letters and packages that seal, address tags, payment on "
     "delivery, lost mail, and two pigeons that carry it. 281 language keys "
     "beyond its items, which is what a mod looks like when it has rules of its "
     "own"),
    (r"^towntalk", "DROP", "-",
     "2,291 recorded sound files in the minecolonies namespace and nothing else "
     "— no item, no block, no entity, one class. It is voice acting for the mod "
     "elysium-colonies adds on to; this project writes its own code and draws "
     "its own art, and does not record voices"),
    (r"^SimplyTooltips", "KEEP", "-",
     "client-only, and its language file names no item and no block: 31 keys, "
     "every one of them a keybind or a line in its own editor. Converting it "
     "would mean writing tooltip UI, and the GUI is the one thing this project "
     "is told to leave alone"),

    # --- arcana -----------------------------------------------------------
    (r"^ProjectE|^projectexpansion", "DONE", "elysium-arcana",
     "transmutation, with a price table derived from the pack's own recipes and proved "
     "to contain no arrangement that makes value out of nothing"),
    (r"^GeOre", "DONE", "elysium-arcana",
     "geodes: 210 blocks and 60 items across 30 materials — 20 the foundry "
     "mines and 10 the game digs up — each with a whole growth chain. NOT yet "
     "removable: oritech ships compat recipes naming geore items, so the third "
     "pre-check fails until those are handled"),
    (r"^relics", "PLANNED", "elysium-arcana",
     "relics: 30 relics across 8 slots, carrying all 30 abilities of the "
     "shared ability system now in elysium-lib — the same system the arsenal's "
     "uniques will use. Its 17 entities (the summons and projectiles the "
     "actives throw) do not exist yet"),

    # --- new, asked for by name -------------------------------------------
    # (no jar in the pack; tracked so the manifest is the whole plan)

    # --- libraries: nothing to convert ------------------------------------
    (r"^architectury|^balm|^bookshelf|^cloth-config|^resourcefullib|^resourcefulconfig|"
     r"^PuzzlesLib|^Zeta|^moonlight|^titanium|^Glodium|^Cucumber|^UnionLib|^knightlib|"
     r"^larion|^OctoLib|^azurelib|^geckolib|^SmartBrainLib|^kotlinforforge|"
     r"^player-animation-lib|^fzzy_config|^configuration|^prickle|^sable|^shetiphiancore|"
     r"^athena|^blueprint|^cristellib|^lithostitched|^polyeng|^stonezone|^guideme|"
     r"^ldlib2|^ritchiesprojectilelib|^trmt|^valhelsia_core|^Patchouli|^ftb-library",
     "DROP", "-", "library: goes when the mods needing it go"),

    # --- performance and rendering: explicitly excluded --------------------
    (r"^modernfix|^ferritecore|^entityculling|^ImmediatelyFast|^lithium|^sodium|^iris|"
     r"^entity_model_features|^entity_texture_features|^letmedespawn|^fastleafdecay",
     "DROP", "-", "performance: not content"),
    # LegendaryTooltips and the two libraries that exist to draw for it are
    # here for the same reason as the rest: nothing in the pack is different
    # with or without them except how a tooltip is framed.
    (r"^xaerominimap|^xaeroworldmap|^Jade|^jei|^invtweaks|^MouseTweaks|^polymorph|"
     r"^ftb-ultimine|^potentials|^LegendaryTooltips|^Iceberg-|^Prism-",
     "DROP", "-", "interface and convenience: not content"),
]

#: Things the user asked for that have no jar in the pack. They belong in the
#: manifest anyway, because the manifest is the plan and not an inventory.
NEW = [
    ("(new) space harvestcraft", "PLANNED", "elysium-agronomy",
     "a space-themed Pam's: crops and trees for the frontier worlds"),
    ("(new) gregtech equivalent", "PLANNED", "elysium-foundry",
     "the deep material chain that ties the three ages together"),
    ("(new) automobility equivalent", "DONE", "elysium-transit",
     "vehicles, roads and what they carry"),
]


def decide(jar):
    for pattern, status, module, note in DECISIONS:
        if re.match(pattern, jar, re.IGNORECASE):
            return status, module, note
    return None


#: How far along each status is. Regenerating must never move a row backwards.
PROGRESS = {"UNDECIDED": 0, "PLANNED": 1, "ACTIVE": 2, "DROP": 3, "KEEP": 3,
            "SPLIT": 4, "DONE": 5}


def already(path):
    """What the manifest on disk says, so regenerating does not undo work.

    This file used to print the table straight out of DECISIONS, which meant
    running it threw away every status anybody had advanced by hand -- the
    header even says to edit the status column as work lands. One person
    regenerating after a rename would have silently marked two dozen finished
    conversions as PLANNED again, and the only sign would have been the work
    being done twice.
    """
    known = {}
    if not os.path.isfile(path):
        return known
    for line in open(path, encoding="utf-8"):
        row = re.match(r"\|\s*`([^`]+)`\s*\|\s*([A-Z]+)\s*\|\s*([^|]+?)\s*\|"
                       r"\s*(.*?)\s*\|\s*$", line)
        if row:
            jar, status, module, note = row.groups()
            known[jar] = (status, module, note)
    return known


def merged(jar, found, known):
    """The decision, or what the manifest already says if that is further on."""
    was = known.get(jar)
    if was is None:
        return found
    if PROGRESS.get(was[0], 0) > PROGRESS.get(found[0], 0):
        return was
    # Same status, but somebody has written a fuller note than the table's:
    # keep theirs. A note saying what was actually built is worth more than
    # a note saying what was planned.
    if was[0] == found[0] and len(was[2]) > len(found[2]):
        return (found[0], found[1], was[2])
    return found


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    jars = [line.strip() for line in open(sys.argv[1], encoding="utf-8")
            if line.strip().endswith(".jar")]
    rows = []
    undecided = []
    known = already(os.path.join(os.path.dirname(os.path.dirname(
        os.path.abspath(__file__))), "docs/MANIFEST.md"))
    for jar in sorted(jars, key=str.lower):
        found = decide(jar)
        if found is None:
            was = known.get(jar)
            if was and was[0] != "UNDECIDED":
                rows.append((jar,) + was)
                continue
            undecided.append(jar)
            rows.append((jar, "UNDECIDED", "?", "**nobody has decided about this**"))
        else:
            rows.append((jar,) + merged(jar, found, known))
    for planned in NEW:
        rows.append((planned[0],) + tuple(merged(planned[0], planned[1:], known)))

    counts = {}
    for _, status, _, _ in rows:
        counts[status] = counts.get(status, 0) + 1
    modules = {}
    for _, status, module, _ in rows:
        if status in ("PLANNED", "ACTIVE"):
            modules[module] = modules.get(module, 0) + 1

    print("# The conversion manifest")
    print()
    print("Generated by `tools/manifest.py` from the pack's own jar listing, so a mod")
    print("cannot be left out by being forgotten. **Edit the status column as work")
    print("lands** — this table is what a session with no memory reads first.")
    print()
    print("See `docs/CONVERSION.md` for the architecture and the order of work.")
    print()
    print("| count | status | means |")
    print("|---|---|---|")
    for status, means in (("DONE", "an Elysium module already replaces it"),
                          ("SPLIT", "spans several modules; see docs/SPLITS.md"),
                          ("ACTIVE", "being worked on now"),
                          ("PLANNED", "has a target module, not started"),
                          ("KEEP", "stays in the pack on purpose"),
                          ("DROP", "library or performance: nothing to convert"),
                          ("UNDECIDED", "**needs a decision**")):
        print(f"| {counts.get(status, 0)} | {status} | {means} |")
    print()
    print("## Work remaining, by module")
    print()
    print("| module | mods to absorb |")
    print("|---|---|")
    for module, count in sorted(modules.items(), key=lambda pair: -pair[1]):
        print(f"| `{module}` | {count} |")
    print()
    print("## Every jar")
    print()
    print("| jar | status | module | note |")
    print("|---|---|---|---|")
    for jar, status, module, note in rows:
        print(f"| `{jar}` | {status} | {module} | {note} |")
    if undecided:
        print()
        print(f"> **{len(undecided)} jar(s) have no decision.** Add a rule to "
              f"`tools/manifest.py` for each: "
              + ", ".join(f"`{jar}`" for jar in undecided))
    return 1 if undecided else 0


if __name__ == "__main__":
    sys.exit(main())
