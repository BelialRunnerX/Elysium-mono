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
    (r"^CreateDragonsPlus|^copycats|^sliceanddice|^interiors", "PLANNED",
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
    (r"^born_in_chaos|^fangs_n_claws|^mobs_of_mythology", "PLANNED",
     "elysium-bestiary",
     "nine hostile beasts exist on a proved reward ladder; their catalogues "
     "(dozens of mobs each, bosses, their own gear and items) do not"),
    (r"^naturalist", "PLANNED", "elysium-bestiary",
     "passive fauna, ambience and a field guide; the bestiary is hostile mobs only "
     "and has nothing that does not attack you"),
    (r"^friendsandfoes|^knightquest", "PLANNED", "elysium-bestiary",
     "mobs built around one mechanic each, and structures and gear alongside them; "
     "the bestiary has three behaviours and no structures at all"),
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
    (r"^valhelsia_furniture|^woodworks|^fusion|^everycomp|^Diagonal|^Chimes|^copycat",
     "PLANNED", "elysium-atelier", "building and decoration"),

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
    (r"^comforts|^towntalk|^envelope|^SimplyTooltips",
     "PLANNED", "elysium-comforts", "quality of life"),

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


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    jars = [line.strip() for line in open(sys.argv[1], encoding="utf-8")
            if line.strip().endswith(".jar")]
    rows = []
    undecided = []
    for jar in sorted(jars, key=str.lower):
        found = decide(jar)
        if found is None:
            undecided.append(jar)
            rows.append((jar, "UNDECIDED", "?", "**nobody has decided about this**"))
        else:
            rows.append((jar,) + found)
    rows.extend(NEW)

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
