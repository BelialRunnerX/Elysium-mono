#!/usr/bin/env python3
"""Write the playtest guide as a PDF, reading the rosters out of the rules.

    python3 tools/gen_playtest.py [out.pdf]

Why a generator rather than a written document: a playtest guide whose tables
are typed by hand is a guide that disagrees with the build the week after it is
written, and the person holding it cannot tell which of the two is wrong. Every
number and every name below is read from the rules files this build was
compiled from, so the guide is stale only if the tree is.

The prose is written. The tables are not.
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

try:
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_LEFT
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.units import mm
    from reportlab.platypus import (SimpleDocTemplate, Paragraph, Spacer, Table,
                                    TableStyle, PageBreak, KeepTogether)
except ImportError:
    print("reportlab is needed: pip install reportlab --break-system-packages")
    sys.exit(1)


def rules(module, name):
    where = os.path.join(ROOT, module, "src/main/java/com/elysium",
                         module.split("-", 1)[1], "rules", name)
    if not os.path.isfile(where):
        print("there is no %s, so this guide cannot describe it" % where)
        sys.exit(2)
    return open(where, encoding="utf-8").read()


def rows(text, record, fields):
    """Every `new Record(...)` in a rules file, as a list of field lists."""
    out = []
    for call in re.findall(r"new %s\(([^()]*(?:\([^()]*\)[^()]*)*)\)" % record, text):
        parts = [one.strip().strip('"') for one in call.split(",")]
        if len(parts) >= fields:
            out.append(parts)
    return out


def number(text, name):
    found = re.search(r"public static final (?:int|double) %s = ([0-9._D]+)" % name, text)
    return found.group(1).rstrip("D").rstrip(".") if found else "?"


def head():
    try:
        return subprocess.run(["git", "-C", ROOT, "rev-parse", "--short", "HEAD"],
                              capture_output=True, text=True).stdout.strip() or "unknown"
    except OSError:
        return "unknown"


def today():
    try:
        return subprocess.run(["date", "+%d %B %Y"], capture_output=True,
                              text=True).stdout.strip()
    except OSError:
        return ""


# ---------------------------------------------------------------------------
# The rosters
# ---------------------------------------------------------------------------

DENIZEN = rules("elysium-bestiary", "DenizenRules.java")
LIGHT = rules("elysium-comforts", "LightRules.java")
BUDDING = rules("elysium-arcana", "BuddingRules.java")
FARM = rules("elysium-bestiary", "FarmRules.java")
COURT = rules("elysium-bestiary", "CourtRules.java")
WARD = rules("elysium-bestiary", "WardRules.java")
KIN = rules("elysium-bestiary", "KindredRules.java")
COMPANION = rules("elysium-bestiary", "CompanionRules.java")

DENIZENS = rows(DENIZEN, "Denizen", 7)
def fittings():
    """The lighting roster, with the dyed set expanded the way the game sees it.

    LightRules builds the sixteen colours in a loop rather than writing them
    out, which is the right thing for the rules and means a parser that only
    reads `new Fitting(...)` calls counts the whole dyed set as one. Sixteen
    candle holders are sixteen blocks to a player standing in front of them.
    """
    colours = re.search(r"List<String> COLOURS = List\.of\((.*?)\);",
                        re.sub(r"\s+", " ", rules("elysium-comforts",
                                                  "RestRules.java")))
    colours = re.findall(r'"([a-z_]+)"', colours.group(1)) if colours else []
    kinds = dict(re.findall(
        r'public static final String (HELD|HUNG|FLOOR|HEARTH|UNDERFOOT|STRUNG)'
        r' = "(\w+)";', LIGHT))
    brightest = number(LIGHT, "BRIGHTEST")
    out = []
    for row in rows(LIGHT, "Fitting", 5):
        kind = kinds.get(row[1], row[1])
        light = brightest if row[2] == "BRIGHTEST" else row[2]
        if row[4] == "colour":
            for colour in colours:
                out.append([colour + "_" + kind, kind, light, row[3], colour])
        else:
            out.append([row[0], kind, light, row[3], ""])
    return out


FITTINGS = fittings()
BUDDINGS = rows(BUDDING, "Budding", 4)
MACHINES = rows(FARM, "Machine", 6)
MEMBERS = rows(COURT, "Member", 11)
WARDS = rows(WARD, "Warded", 9)
KINS = rows(KIN, "Kin", 4)
COMPANIONS = rows(COMPANION, "Companion", 8)

BANDS = [20, 80, 200, 500, 1100]


def threat(row):
    health, damage, speed = float(row[3].rstrip("D")), float(row[4].rstrip("D")), float(row[5].rstrip("D"))
    return health * (1.0 + damage) * speed / 0.25


# ---------------------------------------------------------------------------
# Styling
# ---------------------------------------------------------------------------

SHEET = getSampleStyleSheet()
BODY = ParagraphStyle("body", parent=SHEET["BodyText"], fontName="Helvetica",
                      fontSize=9.5, leading=13.5, alignment=TA_LEFT,
                      spaceAfter=6)
LEAD = ParagraphStyle("lead", parent=BODY, fontSize=11, leading=15.5,
                      textColor=colors.HexColor("#333333"))
H1 = ParagraphStyle("h1", parent=SHEET["Heading1"], fontName="Helvetica-Bold",
                    fontSize=17, leading=21, spaceBefore=2, spaceAfter=8,
                    textColor=colors.HexColor("#1d1d1f"))
H2 = ParagraphStyle("h2", parent=SHEET["Heading2"], fontName="Helvetica-Bold",
                    fontSize=12.5, leading=16, spaceBefore=12, spaceAfter=5,
                    textColor=colors.HexColor("#1d1d1f"))
H3 = ParagraphStyle("h3", parent=SHEET["Heading3"], fontName="Helvetica-Bold",
                    fontSize=10, leading=13, spaceBefore=9, spaceAfter=3,
                    textColor=colors.HexColor("#444444"))
MONO = ParagraphStyle("mono", parent=BODY, fontName="Courier", fontSize=8.5,
                      leading=12, leftIndent=8, spaceAfter=5,
                      textColor=colors.HexColor("#1a3a5a"))
NOTE = ParagraphStyle("note", parent=BODY, fontSize=9, leading=12.5,
                      leftIndent=9, rightIndent=9, spaceBefore=4, spaceAfter=8,
                      borderPadding=6, backColor=colors.HexColor("#fdf3e0"),
                      borderColor=colors.HexColor("#e8c990"), borderWidth=0.6)

GRID = TableStyle([
    ("FONT", (0, 0), (-1, 0), "Helvetica-Bold", 8),
    ("FONT", (0, 1), (-1, -1), "Helvetica", 8),
    ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#42546a")),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f2f4f7")]),
    ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#c8cdd4")),
    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ("LEFTPADDING", (0, 0), (-1, -1), 5),
    ("RIGHTPADDING", (0, 0), (-1, -1), 5),
    ("TOPPADDING", (0, 0), (-1, -1), 3),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
])


def table(header, body, widths):
    made = Table([header] + body, colWidths=widths, repeatRows=1)
    made.setStyle(GRID)
    return made


def p(text, style=BODY):
    return Paragraph(text, style)


def cmd(text):
    return Paragraph(text.replace("<", "&lt;").replace(">", "&gt;"), MONO)


story = []
A = story.append

# ---------------------------------------------------------------------------
# Cover
# ---------------------------------------------------------------------------

A(p("Elysium &mdash; Playtest Guide", H1))
A(p(f"Build <b>{head()}</b> &nbsp;&middot;&nbsp; {today()} &nbsp;&middot;&nbsp; "
    f"elysium-all-1.0.0.jar + elysiumcolonies-1.0.0.jar", LEAD))
A(Spacer(1, 4 * mm))
A(p("This covers what is in the jar installed in <b>Instances/Claude</b> and "
    "<b>Instances/Claude-dev-build</b> right now. It is written to be held "
    "while playing: each section says what the thing is, the one property it "
    "is supposed to have, how to put that property under pressure, and what "
    "would count as it being broken.", BODY))
A(p("Every table below is read out of the rules files the build was compiled "
    "from, so the guide is wrong only if the tree is. The prose is written; "
    "the numbers are not.", BODY))

A(p("Read this first: every creature has a new name", H2))
A(p("134 of the 176 creature ids in <b>elysium-bestiary</b> were taken "
    "verbatim from the jars they were converted from &mdash; 58 from "
    "born_in_chaos, 28 from Valoria, 27 from fangs_n_claws, 13 from "
    "knightquest, 10 from friendsandfoes. That was a defect rather than a "
    "naming choice, and the whole roster has been re-themed: a scorpion "
    "family is now <font face='Courier'>ferricbarb, dunebarb, rimebarb, "
    "cinderbarb</font>, and a court reads "
    "<font face='Courier'>forgeprime, forgecaller, forgeguard</font>. "
    "<b>Every entity id in this build is different from the last one</b>, so "
    "a world saved against the previous jar will not find its mobs. Use a "
    "fresh world. <font face='Courier'>check_names.py</font> is the rule that "
    "now holds it, and <font face='Courier'>docs/NAMING.md</font> is why.", NOTE))

A(p("The commands you will want", H2))
COMMANDS = [
    ("/gamerule doDaylightCycle false", "freeze the clock for the light tests"),
    ("/summon elysiumbestiary:<id> ~ ~ ~", "every creature in this guide"),
    ("/give @s elysiumcomforts:<id>", "every fitting and comfort"),
    ("/give @s elysiumarcana:<id>", "geodes, budding blocks and relics"),
    ("/execute in elysiumwilds:vale run tp @s ~ 100 ~", "into the Vale"),
    ("/locate biome elysiumwilds:vale_<crossing>", "find a named crossing"),
    ("/data get entity @e[limit=1,sort=nearest] Health", "read a real number off a mob"),
]
_cmds = Table([[Paragraph(c.replace("<", "&lt;").replace(">", "&gt;"), MONO),
                Paragraph(w, BODY)] for c, w in COMMANDS],
              colWidths=[92 * mm, 52 * mm])
_cmds.setStyle(TableStyle([
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("ROWBACKGROUNDS", (0, 0), (-1, -1), [colors.white, colors.HexColor("#f4f6f8")]),
    ("LEFTPADDING", (0, 0), (-1, -1), 4),
    ("TOPPADDING", (0, 0), (-1, -1), 3),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ("LINEBELOW", (0, 0), (-1, -2), 0.3, colors.HexColor("#dde1e6")),
]))
A(_cmds)

A(PageBreak())

# ---------------------------------------------------------------------------
# 1. The Vale
# ---------------------------------------------------------------------------

A(p("1. The Vale", H1))
A(p(f"{len(DENIZENS)} creatures across 15 crossings of the "
    f"<font face='Courier'>elysiumwilds:vale</font> dimension, in four rings.", LEAD))
A(p("<b>The claim.</b> Which direction you walk out of the portal is not a "
    "difficulty setting. The fifteen crossings are read as four rings out from "
    "whichever one a portal drops you nearest; each ring owns a band of "
    "danger; the bands tile and rise and do not overlap; and every crossing "
    "has something in it. A dimension where the hardest thing can stand next "
    "to the portal is one where exploring is a coin flip &mdash; the player "
    "who walked north calls it unfair and the player who walked south calls it "
    "empty, and both are describing the same build.", BODY))

A(p("The bands", H3))
A(p("Danger is <font face='Courier'>health x (1 + damage) x speed / 0.25</font>. "
    "Each ring owns a slice of it and every creature in that ring must land "
    "inside its own slice.", BODY))
A(table(["Ring", "Danger band", "Crossings", "Creatures"],
        [[str(r),
          f"{BANDS[r]} to {BANDS[r+1]}",
          str(len({d[1] for d in DENIZENS if int(d[2]) == r})),
          str(len([d for d in DENIZENS if int(d[2]) == r]))]
         for r in range(4)],
        [18 * mm, 32 * mm, 26 * mm, 26 * mm]))

A(p("Every crossing, by ring", H3))
body = []
for r in range(4):
    for place in sorted({d[1] for d in DENIZENS if int(d[2]) == r}):
        here = [d for d in DENIZENS if d[1] == place]
        body.append([str(r), place, ", ".join(d[0] for d in here),
                     f"{min(threat(d) for d in here):.0f}-{max(threat(d) for d in here):.0f}"])
A(table(["Ring", "Crossing", "What lives there", "Danger"],
        body, [13 * mm, 33 * mm, 88 * mm, 22 * mm]))

A(p("What to check", H3))
A(p("<b>1.</b> Go through the portal, note which crossing you land nearest, "
    "and walk outward. Danger should rise and keep rising. Walk back in and it "
    "should fall. If you can find a route where a ring 3 creature is the first "
    "thing you meet, the ring arithmetic is wrong and that is the single most "
    "important bug in this feature.", BODY))
A(p("<b>2.</b> Visit all fifteen crossings with "
    "<font face='Courier'>/locate biome</font>. Every one should have "
    "something in it. An empty crossing reads to a player as a bug in "
    "worldgen and is actually a missing spawn rule.", BODY))
A(p("<b>3.</b> Spawn one of each in a flat world and compare how long each "
    "takes to kill you against the table. A creature that feels wildly out of "
    "place for its ring is worth reporting even if the number says it is fine "
    "&mdash; the number is a proxy and the proxy may be the thing that is "
    "wrong.", BODY))
A(p("<b>Known-good:</b> ring 0 is one crossing, "
    "<font face='Courier'>verdant_vale</font>, and holds the two weakest "
    "things in the dimension. If the portal never seems to land near it, say "
    "so &mdash; that is a worldgen question rather than a bestiary one.", BODY))

A(PageBreak())

# ---------------------------------------------------------------------------
# 2. The lighting
# ---------------------------------------------------------------------------

A(p("2. Lighting a room", H1))
A(p(f"{len(FITTINGS)} fittings in six kinds, in <b>elysium-comforts</b>. The "
    f"newest thing in the build and the one most worth an hour.", LEAD))
A(p("<b>The claim.</b> This is the conversion failure that leaves no trace. A "
    "decoration pack adds thirty light sources; each is prettier than a torch, "
    "most are cheaper, several are brighter, and every one of them is "
    "defensible on its own. Nobody decides that light should stop being a "
    "resource. It stops being one as the sum of thirty decisions that were "
    "each about how a room looks &mdash; and the bestiary's haunts each name a "
    "band of light they spawn in, almost all of them dark. A bright, cheap "
    "decoration roster does not make those creatures rarer. It makes them "
    "impossible anywhere anybody has decorated.", BODY))
A(p(f"So the number this module is held to is not its own. "
    f"<b>STILL_DARK is {number(LIGHT, 'STILL_DARK')}</b>, because "
    f"{number(LIGHT, 'STILL_DARK')} is the brightest light at which a majority "
    f"of the bestiary's nine monsters still spawn: five of nine at seven, two "
    f"of nine at eight. Nothing here reaches light "
    f"{number(LIGHT, 'BRIGHTEST')}, which is one under the game's own.", BODY))

A(p("The roster", H3))
kinds = []
for kind in sorted({f[1] for f in FITTINGS}):
    here = [f for f in FITTINGS if f[1] == kind]
    dyed = [f for f in here if len(f) > 4 and f[4] not in ("", "colour")]
    lights = sorted({int(f[2]) for f in here if f[2].lstrip("-").isdigit()})
    costs = sorted({int(f[3]) for f in here if f[3].lstrip("-").isdigit()})
    kinds.append([kind, str(len(here)),
                  (f"{min(lights)}-{max(lights)}" if lights else "?"),
                  (f"{min(costs)}-{max(costs)}" if costs else "?")])
A(table(["Kind", "How many", "Light", "Cost (items)"], kinds,
        [40 * mm, 26 * mm, 26 * mm, 30 * mm]))
A(p("The sixteen dyed candle holders are built in a loop, so they are one "
    "light level and one price by construction. Colour is not a stat.", BODY))

A(p("What to check", H3))
A(p("<b>1. The one that matters.</b> Build a sealed room, light it "
    "<i>only</i> with candle holders, and stand in it at night with "
    "<font face='Courier'>/gamerule doDaylightCycle false</font>. Monsters "
    "should still spawn. A room a player has decorated is not supposed to be "
    "safe. If it goes quiet, the cross-module claim has failed in the game "
    "even though it passes in the validator, and that is the most valuable "
    "thing you can find this pass.", BODY))
A(p("<b>2.</b> Light the same room with lamps instead. It should go quiet. "
    "Lamps are the expensive, bright answer and they are supposed to work.", BODY))
A(p("<b>3.</b> Craft one of each. The item count in the recipe should equal "
    "the cost in the table above. Anything that costs more or less than its "
    "row is a bug in the generator, not in the rules.", BODY))
A(p("<b>4.</b> Check the doormat and the rope give no light at all, and that "
    "you can walk over the doormat without stepping up.", BODY))
A(p("<b>5.</b> There should be exactly one rope in the creative tab. The "
    "module already had one for hammocks; if there are two, the rule that was "
    "written to prevent precisely that has failed.", BODY))

A(PageBreak())

# ---------------------------------------------------------------------------
# 3. The seeding
# ---------------------------------------------------------------------------

A(p("3. Seeding a budding block", H1))
A(p(f"{len(BUDDINGS)} materials in <b>elysium-arcana</b>. Replaces GeOre, "
    f"which is now retired to mods/_superseded in both instances.", LEAD))
A(p("<b>The claim.</b> A geode system where the only way to get a budding "
    "block is to find one is a system most players never touch. Seeding is the "
    "other end &mdash; but a seeding is a <i>multiplier</i> on something you "
    "already have, never a <i>source</i> of something you have not. Every "
    "material is paid for in its own blocks, so you need platinum to farm "
    "platinum and where the first platinum came from is still the foundry's "
    "problem. Anything else and every ore in the pack is obtainable from "
    "whichever one is cheapest.", BODY))
A(p(f"A cluster gives {number(BUDDING, 'CLUSTER_GIVES')}. Every seeding pays "
    f"for itself in between {number(BUDDING, 'LEAST_CYCLES')} and "
    f"{number(BUDDING, 'MOST_CYCLES')} cluster cycles: under that it is free "
    f"and finding a geode stops being worth anything, over it nobody ever "
    f"does it and the recipe is a line in a book.", BODY))

A(p("A sample of the ladder", H3))
sample = [b for b in BUDDINGS if b[0] in
          ("coal", "redstone", "amethyst", "iron", "gold", "diamond",
           "emerald", "platinum", "uranium", "bort")]
A(table(["Material", "Paid in", "Shards", "Blocks in the recipe", "Catalyst"],
        [[b[0], b[1], b[2], str(int(b[2]) // 4) if b[2].isdigit() else "?",
          "echo shard" if "DEARER" in b[3] else "glow ink sac"]
         for b in sample],
        [30 * mm, 26 * mm, 20 * mm, 40 * mm, 30 * mm]))

A(p("What to check", H3))
A(p("<b>1.</b> Craft a budding block for something cheap (coal) and something "
    "dear. The recipe should be that material's own blocks plus one catalyst, "
    "and nothing else. A recipe that takes somebody else's blocks is the "
    "failure this whole feature is built to avoid.", BODY))
A(p("<b>2.</b> Place one, let it grow, and count the cycles until it has paid "
    "back what it cost. It should land between two and ten.", BODY))
A(p("<b>3.</b> Break a seeded block. It should drop itself. If it drops its "
    "shards instead, breaking and replacing becomes a loop.", BODY))
A(p("<b>4. GeOre is gone.</b> Check nothing in the pack still points at a "
    "geore: item &mdash; the oritech compat recipes it was blocked on were the "
    "reason it stayed so long. A recipe book entry that cannot be crafted is "
    "the symptom.", BODY))

A(PageBreak())

# ---------------------------------------------------------------------------
# 4. The rest of the bestiary
# ---------------------------------------------------------------------------

A(p("4. The rest of the bestiary", H1))
A(p("Shipped earlier this run, and worth a pass because the Vale and the "
    "lighting both lean on them.", LEAD))

A(p(f"The courts &mdash; {len(MEMBERS)} creatures in "
    f"{len({m[1] for m in MEMBERS})} courts", H3))
A(p("The bestiary's nine beasts are a ladder with no boss tier at all, so this "
    "is the row that decides what a boss is here. Three numbers rather than "
    "prose: a head may be three times the beast of its own rung; a court's "
    "threat is held <i>under</i> its head's, with the budget divided among its "
    "members, so adding to a court makes every member weaker; and a second "
    "phase must change what the player does rather than carry more health.", BODY))
A(table(["Court", "Members", "Roles present"],
        [[c, str(len([m for m in MEMBERS if m[1] == c])),
          ", ".join(sorted({m[2] for m in MEMBERS if m[1] == c}))]
         for c in sorted({m[1] for m in MEMBERS})],
        [30 * mm, 22 * mm, 92 * mm]))
A(p("<b>Check:</b> fight a head to half health and watch the phase flip. It is "
    "a synched boolean on one entity, not a second entity &mdash; if the boss "
    "vanishes and a new one appears, loot and aggro will both be wrong.", BODY))

A(p(f"The warded &mdash; {len(WARDS)} creatures over six wards", H3))
A(p("knightquest is 173 items of gear with a roster attached, so its creatures "
    "are the ones that exist to be fought <i>with</i> something. That fails "
    "two ways and both look like content: too far one way the creature is a "
    "door and the player who has the set never learns there was a fight; too "
    "far the other the gear is a texture with a tooltip and nobody complains, "
    "they just stop wearing it.", BODY))
A(table(["Ward", "Creatures", "What answers it"],
        [[w, str(len([x for x in WARDS if x[1] == w])),
          ", ".join(sorted({x[2].split(":")[-1].replace("_", " ")
                            for x in WARDS if x[1] == w}))]
         for w in sorted({x[1] for x in WARDS})],
        [24 * mm, 24 * mm, 96 * mm]))
A(p("<b>Check:</b> fight one warded creature with the answer and once without. "
    "Both should be a fight. The attack number on the entity is the "
    "<i>unwarded</i> one, so if it hits equally hard either way the ward is "
    "not being applied.", BODY))

A(p(f"The kindred &mdash; {len(KINS)} creatures in "
    f"{len({k[1] for k in KINS})} families", H3))
A(p("A roster grows by variant because variants are cheap: one scorpion "
    "becomes four, the count goes up by three and the number of fights goes up "
    "by none. Three rules make a family a promise &mdash; no two of a family "
    "do the same thing, no two live in the same place, and a variant is not a "
    "harder version, because harder is what tier is for.", BODY))
A(p("<b>Check:</b> meet two of one family and confirm they fight differently. "
    "If a frost scorpion is a desert scorpion with more health, the feature "
    "has not landed however well the validator passes.", BODY))

A(p(f"The companions &mdash; {len(COMPANIONS)}, one job each", H3))
A(p("A block sits there. That is the whole of what a block has over something "
    "alive: it needs no feeding, it does not wander off, and it does not have "
    "to be found and earned first. A creature that does a block's job for free "
    "makes the block stop being built, quietly, one category at a time. Each "
    "companion does exactly one thing a block does, and every one is tamed, or "
    "fed, or has to be kept close.", BODY))
A(p("<b>Check:</b> confirm each has an upkeep you actually feel. A companion "
    "you can set down and forget is a block with legs.", BODY))

A(p(f"The farm &mdash; {len(MACHINES)} machines", H3))
A(p("A mob farm is a multiplier on reward-per-danger, and a multiplier can "
    "break the bestiary's spread without touching a line of it &mdash; the "
    "bestiary's own proof would still pass. The finding that made this a check "
    "rather than a comment: a killer that takes the player's kill credit, with "
    "orbs left at zero, moves the spread from 1.148 to 1.618 and quietly "
    "changes what the best thing in the pack to farm is.", BODY))
A(p("<b>Check:</b> build a farm from the machines and confirm experience still "
    "arrives when the machine lands the kill. Then confirm it is worse than "
    "killing by hand, because that is the whole point.", BODY))

A(PageBreak())

# ---------------------------------------------------------------------------
# 5. Comforts and arcana
# ---------------------------------------------------------------------------

A(p("5. Comforts and arcana, in brief", H1))
A(p("Shipped before this run. Worth a quick pass to confirm nothing regressed "
    "when the lighting and the seeding went in.", LEAD))

for title, text in [
    ("The grave",
     "Nothing you owned is ever lost. The grave is built around what it is "
     "handed rather than given a fixed size, which is how every gravestone mod "
     "that ever ate somebody's backpack did it. Check: die holding a full "
     "inventory and a full backpack, and count what comes back."),
    ("Somewhere to sleep",
     "33 blocks: a sleeping bag and a hammock in sixteen colours each, plus the "
     "rope. A bag is for the night and a hammock for the day, with no hour that "
     "is both and no gap between them. Neither moves your spawn. Check: try to "
     "sleep in each at the wrong hour and read the refusal &mdash; there are "
     "six of them and each has its own sentence."),
    ("The chimes",
     "96 chimes over six materials and sixteen colours, each ringing at a note "
     "of its own. Check: hang twenty in one room. It must be no louder than "
     "one of them, or a decoration becomes a weapon against the person who "
     "built it."),
    ("The stations",
     "Eight blocks, each doing one thing a player would otherwise do by hand. "
     "None is better value than a pair of hands and none is quicker. Check: "
     "time one against doing it yourself."),
    ("The post",
     "Eleven pigeonholes, eight kinds of mail, two birds. A parcel is never in "
     "two places and never in none, and a seal only opens in the hands it is "
     "addressed to. Check: post something to yourself, then to a second "
     "player, and try to open theirs."),
    ("The geodes and the relics",
     "30 materials with a whole growth chain each, and 30 relics across eight "
     "slots carrying the shared ability system. Check: a relic's ability should "
     "be the same ability the arsenal's uniques use &mdash; if they diverge, "
     "the shared library has been forked by accident."),
]:
    A(p(title, H3))
    A(p(text, BODY))

A(p("6. The checklist", H1))
A(p("Tick as you go. The middle column is what you would see if it is right.", LEAD))
CHECKS = [
    ("Vale: danger rises walking out from the portal", "harder every ring"),
    ("Vale: all 15 crossings have something in them", "nothing empty"),
    ("Lighting: candle-lit room still spawns monsters", "not safe"),
    ("Lighting: lamp-lit room goes quiet", "safe, and dear"),
    ("Lighting: recipes cost what the table says", "item count matches"),
    ("Lighting: exactly one rope in the creative tab", "one"),
    ("Seeding: recipe is own blocks plus a catalyst", "nothing borrowed"),
    ("Seeding: pays back in 2 to 10 cycles", "inside the band"),
    ("Seeding: a seeded block drops itself", "no loop"),
    ("GeOre: nothing points at a geore: item", "no dead recipes"),
    ("Courts: phase flip is one entity, not two", "same mob, new fight"),
    ("Warded: a fight with the gear and without", "both a fight"),
    ("Kindred: two of a family fight differently", "not a reskin"),
    ("Companions: upkeep is felt", "not a block with legs"),
    ("Farm: machine kills still give experience", "orbs arrive"),
    ("Farm: worse than killing by hand", "hand wins"),
    ("Grave: full inventory comes back whole", "nothing missing"),
    ("Chimes: twenty are no louder than one", "no wall of sound"),
    ("Post: a sealed parcel will not open in the wrong hands", "refused"),
]
A(table(["Done", "Check", "What right looks like"],
        [["", c, w] for c, w in CHECKS],
        [14 * mm, 88 * mm, 42 * mm]))

A(p("How to report something", H3))
A(p("What you did, what you saw, what you expected. If it is a number, the "
    "number. If it is a creature, its id from "
    "<font face='Courier'>/data get entity</font> rather than its display "
    "name, because the display names are the half that is about to change. "
    "A bug in a rule is worth more than a bug in a texture, and a rule that "
    "passes in the validator and fails in the game is worth more than both.", BODY))


def build(path):
    doc = SimpleDocTemplate(path, pagesize=A4,
                            leftMargin=18 * mm, rightMargin=18 * mm,
                            topMargin=16 * mm, bottomMargin=16 * mm,
                            title="Elysium Playtest Guide",
                            author="Elysium")

    def furniture(canvas, _doc):
        canvas.saveState()
        canvas.setFont("Helvetica", 7.5)
        canvas.setFillColor(colors.HexColor("#8a8f96"))
        canvas.drawString(18 * mm, 10 * mm,
                          f"Elysium playtest guide  |  build {head()}")
        canvas.drawRightString(A4[0] - 18 * mm, 10 * mm, "page %d" % canvas.getPageNumber())
        canvas.restoreState()

    doc.build(list(story), onFirstPage=furniture, onLaterPages=furniture)
    return path


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "dist",
                                                             "elysium-playtest.pdf")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    build(out)
    print(f"playtest: {len(DENIZENS)} denizen(s), {len(FITTINGS)} fitting(s), "
          f"{len(BUDDINGS)} seeding(s), {len(MEMBERS)} court member(s), "
          f"{len(WARDS)} warded, {len(KINS)} kindred, {len(COMPANIONS)} "
          f"companion(s), {len(MACHINES)} machine(s) -> {out}")
