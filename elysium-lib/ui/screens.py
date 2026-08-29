#!/usr/bin/env python3
"""
Every Elysium screen, laid out and rendered so it can be looked at.

Run:  python3 ui/screens.py

These are the layouts the Java implements. The primitives and palette are
shared with the game exactly (see mock.py); the *positions* here are
transcribed into the Java by hand, which is the one place drift is possible —
so the arithmetic is kept in named constants that appear identically on both
sides, and a layout change means changing both.

The screens are drawn at 320x240 and 427x240, the two shapes that matter:
a 1080p window at GUI scale 3 gives about 320 wide, and scale 2 on a smaller
window gives about 427. Anything that only works at one of those is broken.
"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import mock as M
import palette as P
from mock import (Canvas, GAP, GAP_WIDE, LINE_HEIGHT, PANEL_PAD, ROW, ROW_TALL,
                  SLOT, TAB_HEIGHT)

OUT = pathlib.Path(__file__).resolve().parents[1] / "ui/preview"
OUT.mkdir(parents=True, exist_ok=True)

C = P.PALETTE

# The margin a panel always leaves around itself.
MARGIN = 8


def fit(preferred, available):
    """
    A panel's size: what it wants, or what there is, whichever is smaller.

    Fixed-width panels are why the picker and the codex hung off both sides of
    a 320-pixel screen. A GUI scale of 3 on a 1280x720 window gives about
    426x240; on a 960x540 window it gives 320x180. A layout that assumes the
    larger is broken for everyone on the smaller, and "it looks fine on my
    monitor" is exactly the failure this whole preview exists to prevent.
    """
    return min(preferred, available - MARGIN * 2)


# ===========================================================================
# 1. Character sheet — the tabbed view a player sees after choosing
# ===========================================================================
#
# Transcribed from ElysiumCharacterScreen.renderSheet. Every constant below
# appears under the same name on the Java side; that hand-transcription is the
# one place drift is possible in this whole pipeline, so a layout change means
# changing both, and check_fits() below is what catches it when I forget.

# The sheet uses the narrower padding, as the reforge table does. Twelve rows of
# stats are 96 pixels that cannot be negotiated, and with PANEL_PAD the panel
# measures 228 against a 224 budget.
SHEET_PAD = GAP


def sheet_body_height():
    """
    The same for every tab, deliberately.

    Sizing the panel to whichever tab is showing makes it grow and shrink as the
    player clicks along the strip, which moves the strip out from under the
    pointer: click STATS, the panel gets taller, every tab moves up, and the
    next click lands somewhere else.
    """
    rows = 6                     # twelve stats, two columns
    return LINE_HEIGHT + GAP + rows * M.STAT_ROW


STATS = [("VITALITY", 18), ("FORTITUDE", 14), ("RESILIENCE", 11),
         ("STRENGTH", 21), ("AGILITY", 9), ("ACCURACY", 13),
         ("REFLEXES", 12), ("RETRIBUTION", 24), ("INTELLECT", 8),
         ("WILLPOWER", 10), ("LUCK", 7), ("PRESENCE", 16)]

# Standing, as ElysiumStanding defines it.
NOTICE = 25
STANDING_MAX = 100


def character_sheet(width=427, height=240, tab=1, unspent=6, favor=58, suspicion=81):
    c = Canvas(width, height)
    M.scrim(c)

    pad = SHEET_PAD
    header = LINE_HEIGHT + 3 + M.BAR_HEIGHT + 2 + LINE_HEIGHT
    body = sheet_body_height()
    footer = GAP + 1 + GAP + LINE_HEIGHT
    ph = pad * 2 + header + GAP + TAB_HEIGHT + GAP + 2 + body + footer
    ph = min(ph, height - MARGIN * 2)
    pw = fit(300, width)
    px, py = (width - pw) // 2, (height - ph) // 2
    M.panel(c, px, py, pw, ph)

    x, y = px + pad, py + pad
    inner = pw - pad * 2

    c.text(M.truncate(c, "IMPERIAL ENFORCER", inner - 40), x, y, C["TEXT"])
    c.text_right("LEVEL 42", x + inner, y, C["ACCENT"])
    y += LINE_HEIGHT + 3
    progress = 0.62
    M.bar(c, x, y, inner, progress, C["ACCENT"])
    y += M.BAR_HEIGHT + 2
    c.text("380 / 1740 XP", x, y, C["TEXT_FAINT"])
    y += LINE_HEIGHT + GAP

    M.tabs(c, ["CHARACTER", "STATS", "STANDING"], x, y, inner, tab)
    y += TAB_HEIGHT + GAP + 2

    bottom = py + ph - pad - footer
    if tab == 1:
        _stats_tab(c, x, y, inner, bottom, unspent)
    elif tab == 2:
        _standing_tab(c, x, y, inner, bottom, favor, suspicion)
    else:
        _character_tab(c, x, y, inner, bottom)

    fy = py + ph - pad - LINE_HEIGHT
    M.rule(c, x, fy - GAP, inner)
    c.text("ESC TO CLOSE", x, fy, C["TEXT_FAINT"])
    if unspent:
        c.text_right("%d POINTS TO ASSIGN" % unspent, x + inner, fy, C["WARN"])
    return c


def _stats_tab(c, x, y, w, bottom, unspent):
    c.text("%d POINTS TO ASSIGN" % unspent if unspent else "NO POINTS TO ASSIGN",
           x, y, C["WARN"] if unspent else C["TEXT_FAINT"])
    y += LINE_HEIGHT + GAP

    rows = 6
    gutter = 12
    col = (w - GAP_WIDE) // 2
    valw = col - gutter

    for i, (name, value) in enumerate(STATS):
        column, row = i // rows, i % rows
        cx = x + column * (col + GAP_WIDE)
        cy = y + row * M.STAT_ROW
        if cy + LINE_HEIGHT > bottom:
            continue
        # The bar is a comparison, not a measurement: stats have no ceiling, so
        # it is drawn against 50 and clamped. Full means "very high", not "max".
        M.stat_row(c, name, str(value), cx, cy, valw, min(1.0, value / 50.0), C["TEXT"])
        M.stepper(c, cx + col - 10, cy - 1, enabled=unspent > 0)


def _standing_tab(c, x, y, w, bottom, favor, suspicion):
    mark = NOTICE / STANDING_MAX
    y = _meter(c, "FAVOR", favor, "FAVOR: FAVOURED", C["FAVOR"], mark, x, y, w)
    y += GAP_WIDE
    y = _meter(c, "SUSPICION", suspicion, "SUSPICION: HUNTED", C["SUSPICION"], mark, x, y, w)
    y += GAP
    M.wrapped(c, "BOTH ARE HELD BY CONTINUING TO ACT, AND BLEED AWAY WHEN YOU STOP. "
                 "BELOW 25 THE EMPIRE IS NOT WATCHING EITHER WAY.",
              x, y, w, C["TEXT_FAINT"], max_y=bottom)


def _meter(c, name, value, band, colour, mark, x, y, w):
    c.text(name, x, y, C["TEXT_MUTED"])
    c.text_right("%d / %d" % (value, STANDING_MAX), x + w, y, colour)
    y += LINE_HEIGHT + 2
    M.bar_with_mark(c, x, y, w, value / STANDING_MAX, mark, colour)
    y += M.BAR_HEIGHT + 2
    c.text(band, x, y, C["TEXT_FAINT"])
    return y + LINE_HEIGHT


def _character_tab(c, x, y, w, bottom):
    col = (w - GAP_WIDE) // 2
    _describe(c, "IMPERIAL",
              "STANDING INSIDE THE CODE IS ANSWERED FOR. THE EMPIRE'S OWN, AND JUDGED BY IT.",
              "SANCTIONED", "FAVOR IS EARNED FASTER AND BLEEDS AWAY SLOWER.",
              x, y, col, bottom)
    _describe(c, "ENFORCER",
              "SENT WHERE THE CODE IS NOT BEING KEPT. ARMOUR FIRST, QUESTIONS AFTER.",
              "RETRIBUTION", "REFLECTS A SHARE OF EVERY BLOW, CLIMBING WITH LEVEL.",
              x + col + GAP_WIDE, y, col, bottom)
    M.rule_vertical(c, x + col + GAP_WIDE // 2, y, bottom - y)


def _describe(c, title, body, passive, detail, x, y, w, bottom):
    y = M.section(c, title, x, y, w)
    y = M.wrapped(c, body, x, y, w, C["TEXT_MUTED"], max_y=bottom)
    y += GAP
    if y + LINE_HEIGHT > bottom:
        return
    M.chip(c, passive, x, y, C["ACCENT"])
    y += LINE_HEIGHT + 5
    M.wrapped(c, detail, x, y, w, C["TEXT_FAINT"], max_y=bottom)


# ===========================================================================
# 2. First join — the picker, which cannot be dismissed
# ===========================================================================

RACES = ["IMPERIAL", "DRUUN", "VEYLARI", "KORRATH", "LUMARI", "UNSWORN"]
CLASSES = ["MEDICAE", "FACTOR", "ARTIFICER", "ENFORCER", "PSION",
           "VOIDRUNNER", "RECLAIMER", "WARDEN", "MARKSMAN"]


def character_picker(width=427, height=240):
    c = Canvas(width, height)
    M.scrim(c)

    pad, gap = PANEL_PAD, GAP
    pw = fit(380, width)
    ph = fit(210, height)
    px, py = (width - pw) // 2, (height - ph) // 2
    M.panel(c, px, py, pw, ph)

    x, y = px + pad, py + pad
    inner = pw - pad * 2
    c.text("DECLARE YOURSELF. THIS IS RECORDED ONCE.", x, y, C["TEXT"])
    y += LINE_HEIGHT + 2
    M.accent_rule(c, x, y, 24, C["ACCENT"])
    M.rule_soft(c, x + 24, y, inner - 24)
    y += gap + 2

    colw = (inner - GAP_WIDE * 2) // 3
    list_bottom = py + ph - pad - M.BUTTON_HEIGHT - gap
    list_h = list_bottom - y

    _pick_list(c, "ORIGIN", x, y, colw, list_h, RACES, 0)
    _pick_list(c, "CALLING", x + colw + GAP_WIDE, y, colw, list_h, CLASSES, 3)

    # The description pane: what was just clicked, in words.
    dx = x + (colw + GAP_WIDE) * 2
    top = M.section(c, "ENFORCER", dx, y, colw, C["GOOD"])
    M.sunken(c, dx, top, colw, list_h - (top - y))
    pane_bottom = top + list_h - (top - y) - 4
    ty = M.wrapped(c, "SENT WHERE THE CODE IS NOT BEING KEPT. ARMOUR FIRST, QUESTIONS AFTER.",
                   dx + gap, top + 4, colw - gap * 2, C["TEXT_MUTED"], max_y=pane_bottom)
    M.wrapped(c, "RETRIBUTION - REFLECTS A SHARE OF EVERY BLOW, CLIMBING WITH LEVEL.",
              dx + gap, ty + gap, colw - gap * 2, C["TEXT_FAINT"], max_y=pane_bottom)

    bw = min(70, colw)
    M.button(c, "CONFIRM", px + pw - pad - bw,
             py + ph - pad - M.BUTTON_HEIGHT, bw, "hover")
    return c


def _pick_list(c, title, x, y, w, h, items, chosen):
    top = M.section(c, title, x, y, w)
    well_h = h - (top - y)
    M.sunken(c, x, top, w, well_h)
    row_h = ROW - 1
    ry = top + 2
    for i, name in enumerate(items):
        if ry + row_h > top + well_h:
            # Silently clipping would be a class a player cannot pick and
            # cannot see, so say how many are hidden.
            c.text("+%d MORE" % (len(items) - i), x + GAP,
                   top + well_h - LINE_HEIGHT - 2, C["TEXT_FAINT"])
            return
        if i == chosen:
            M.selection(c, x, ry, w, row_h, C["ACCENT"])
        c.text(M.truncate(c, name, w - GAP * 2), x + GAP, ry + 3,
               C["TEXT"] if i == chosen else C["TEXT_MUTED"])
        ry += row_h


# ===========================================================================
# 3. Reforge table — a container screen with the slots moved
# ===========================================================================

def reforge_table(width=427, height=240):
    c = Canvas(width, height)
    M.scrim(c)

    # Measured from the bottom up: four rows of inventory are non-negotiable,
    # so everything else gets what is left. The first mockup chose a height and
    # the inventory grid landed on top of the affix list.
    # A container screen is the tightest thing here: four rows of inventory are
    # 76 pixels that cannot be negotiated, and the whole panel has to fit a
    # 720p window at GUI scale 3, which is 240 pixels tall. So this one uses
    # the narrower padding - the alternative is a panel that is clipped at the
    # top and bottom for anyone not on a large monitor.
    pad = GAP
    inv_h = SLOT * 4 + 4
    work_h = SLOT + GAP
    rolls_h = LINE_HEIGHT + 2 + GAP + 3 * ROW
    header_h = LINE_HEIGHT + 2 + GAP
    ph = pad * 2 + header_h + work_h + 1 + GAP + rolls_h + GAP + 1 + GAP + inv_h
    pw = fit(300, width)
    px, py = (width - pw) // 2, (height - ph) // 2
    M.panel(c, px, py, pw, ph)

    hx, hy = px + pad, py + pad
    inner = pw - pad * 2
    c.text("REFORGE TABLE", hx, hy, C["TEXT"])
    c.text_right("TIER 3", hx + inner, hy, C["KINETIC"])
    hy += LINE_HEIGHT + 2
    M.accent_rule(c, hx, hy, 24, C["ACCENT"])
    M.rule_soft(c, hx + 24, hy, inner - 24)
    hy += GAP

    # Gear, catalyst, arrow, result - left to right, because that is the order
    # the operation happens in, which vanilla's scattered grids never say.
    sy = hy
    M.slot_hinted(c, hx, sy, "G", C["ACCENT"])
    M.slot_hinted(c, hx + SLOT + GAP, sy, "C", C["KINETIC"])
    c.text("→", hx + SLOT * 2 + GAP * 2 + 4, sy + 5, C["TEXT_FAINT"])
    M.slot(c, hx + SLOT * 2 + GAP * 2 + 20, sy, True)

    ix = hx + SLOT * 2 + GAP * 2 + 20 + SLOT + GAP_WIDE
    iw = inner - (ix - hx)
    c.text("CHARGES", ix, sy, C["TEXT_MUTED"])
    c.text_right("2 / 5", ix + iw, sy, C["WARN"])
    M.bar(c, ix, sy + LINE_HEIGHT + 2, iw, 0.4, C["WARN"])
    c.text("BUDGET 25", ix, sy + LINE_HEIGHT + M.BAR_HEIGHT + 4, C["TEXT_FAINT"])

    sy += work_h
    M.rule(c, hx, sy, inner)
    sy += GAP

    y = M.section(c, "CURRENT ROLLS", hx, sy, inner)
    for name, value, colour in (("ATTACK DAMAGE", "+2.4", C["PLASMA"]),
                                ("ARMOUR TOUGHNESS", "+1.8", C["DIMENSIONAL"]),
                                ("MOVEMENT SPEED", "+6%", C["NEURAL"])):
        c.text(name, hx + GAP, y, C["TEXT_MUTED"])
        c.text_right(value, hx + inner, y, colour)
        y += ROW

    invy = py + ph - pad - inv_h
    M.rule(c, hx, invy - GAP, inner)
    grid_w = SLOT * 9
    gx = px + (pw - grid_w) // 2
    for row in range(3):
        for col in range(9):
            M.slot(c, gx + col * SLOT, invy + row * SLOT)
    for col in range(9):
        M.slot(c, gx + col * SLOT, invy + SLOT * 3 + 4)
    return c


# ===========================================================================
# 4. Rune socketing — a screen that did not exist before
# ===========================================================================

def rune_socket(width=427, height=240):
    c = Canvas(width, height)
    M.scrim(c)

    pw = fit(300, width)
    ph = fit(200, height)
    px, py = (width - pw) // 2, (height - ph) // 2
    M.panel(c, px, py, pw, ph)

    hx, hy = px + PANEL_PAD, py + PANEL_PAD
    inner = pw - PANEL_PAD * 2
    c.text("RUNE SOCKET", hx, hy, C["TEXT"])
    M.chip_right(c, "KINETIC", hx + inner, hy - 2, C["KINETIC"])
    hy += LINE_HEIGHT + 2
    M.accent_rule(c, hx, hy, 24, C["ACCENT"])
    M.rule_soft(c, hx + 24, hy, inner - 24)
    hy += GAP + 4

    M.slot_hinted(c, hx, hy, "G", C["ACCENT"])
    M.slot_hinted(c, hx + SLOT + GAP, hy, "R", C["KINETIC"], True)

    # The three sockets, and whether the rune going in is aligned.
    sx = hx + SLOT * 2 + GAP + GAP_WIDE
    c.text("SOCKETS", sx, hy, C["TEXT_MUTED"])
    for i in range(3):
        filled = i < 2
        colour = C["KINETIC"] if filled else C["LINE"]
        M.chip(c, ["KINETICSURGE", "BARRIER", "EMPTY"][i],
               sx, hy + LINE_HEIGHT + 2 + i * (LINE_HEIGHT + 5), colour)

    hy += SLOT + GAP_WIDE + LINE_HEIGHT * 2
    M.rule(c, hx, hy, inner)
    hy += GAP

    # The one thing a player needs told: alignment doubles the affix.
    M.raised(c, hx, hy, inner, ROW_TALL + 4)
    c.text("ALIGNED", hx + GAP, hy + 5, C["GOOD"])
    c.text_right("AFFIX x1.5", hx + inner - GAP, hy + 5, C["GOOD"])
    hy += ROW_TALL + 4 + GAP
    c.text("A RUNE IN GEAR OF ITS OWN ELEMENT BITES HARDER.", hx, hy, C["TEXT_FAINT"])

    M.button(c, "SOCKET", px + pw - PANEL_PAD - 80, py + ph - PANEL_PAD - M.BUTTON_HEIGHT,
             80, "hover")
    M.button(c, "CANCEL", px + pw - PANEL_PAD - 80 - GAP - 60,
             py + ph - PANEL_PAD - M.BUTTON_HEIGHT, 60, "normal")
    return c


# ===========================================================================
# 5. HUD — the only thing on screen during play
# ===========================================================================
#
# Transcribed from ElysiumHud.render. Drawn over a world colour rather than a
# scrim, because that is where it actually lives and legibility against grass
# is the whole question.

HUD_EDGE = 4
HUD_WIDTH = 90
HUD_INSET = 3


def hud(width=427, height=240, favor=58, suspicion=81):
    c = Canvas(width, height, world=(0x3E, 0x52, 0x38))

    # A hotbar stand-in first, so the HUD is judged where it actually sits.
    hb_w = SLOT * 9
    hbx = (width - hb_w) // 2
    for i in range(9):
        M.slot(c, hbx + i * SLOT, height - 24)

    # A meter below the notice threshold is not drawn at all. Under 25 the
    # Empire is genuinely not paying attention either way, and a bar sitting at
    # 4/100 is three pixels of nothing holding a permanent corner of the screen.
    show_favor = favor >= NOTICE
    show_susp = suspicion >= NOTICE

    # Every block — the level line and each meter — is a line of text, a
    # one-pixel gap, a two-pixel bar and four pixels of air. So the plate is
    # that advance times the block count, less the trailing air the last block
    # does not need. Derived, because the written-down version was three pixels
    # short and the bottom bar sat exactly on the plate's edge.
    advance = LINE_HEIGHT + 5
    blocks = 1 + show_favor + show_susp
    h = HUD_INSET * 2 + advance * blocks - 4
    x = HUD_EDGE
    y = height - HUD_EDGE - h
    M.plate(c, x, y, HUD_WIDTH, h)

    tx, tw, ty = x + 5, HUD_WIDTH - 10, y + HUD_INSET
    progress = 0.62
    c.text("LEVEL 42", tx, ty, C["TEXT"])
    c.text_right("%d%%" % round(progress * 100), tx + tw, ty, C["TEXT_FAINT"])
    ty += LINE_HEIGHT + 1
    M.bar(c, tx, ty, tw, progress, C["ACCENT"], h=2)
    ty += 4

    if show_favor:
        ty = _hud_meter(c, "FAVOR", favor, C["FAVOR"], tx, ty, tw)
    if show_susp:
        _hud_meter(c, "SUSPICION", suspicion, C["SUSPICION"], tx, ty, tw)
    return c


def _hud_meter(c, name, value, colour, x, y, w):
    c.text(name, x, y, C["TEXT_FAINT"])
    c.text_right(str(value), x + w, y, colour)
    M.bar(c, x, y + LINE_HEIGHT + 1, w, value / STANDING_MAX, colour, h=2)
    return y + LINE_HEIGHT + 5


# ===========================================================================
# 6. Bestiary — driven by whatever registries are installed
# ===========================================================================

def bestiary(width=427, height=240):
    c = Canvas(width, height)
    M.scrim(c)

    pw = fit(340, width)
    ph = fit(210, height)
    px, py = (width - pw) // 2, (height - ph) // 2
    M.panel(c, px, py, pw, ph)

    hx, hy = px + PANEL_PAD, py + PANEL_PAD
    inner = pw - PANEL_PAD * 2
    c.text("IMPERIAL CODEX", hx, hy, C["TEXT"])
    hy += LINE_HEIGHT + 2
    M.tabs(c, ["RACES", "CLASSES", "ELEMENTS", "CREATURES"], hx, hy + 2, inner, 3)
    hy += TAB_HEIGHT + GAP + 4

    listw = 110
    M.sunken(c, hx, hy, listw, ph - (hy - py) - PANEL_PAD)
    entries = [("SCAVENGER", C["TEXT_MUTED"]), ("REAVER", C["TEXT_MUTED"]),
               ("WHISPER", C["TEXT_MUTED"]), ("DRONE", C["TEXT_MUTED"]),
               ("LICTOR", C["TEXT"]), ("ADEPT", C["TEXT_MUTED"]),
               ("CHOIR", C["BAD"]), ("PRAETOR", C["WARN"])]
    ry = hy + 2
    for i, (name, colour) in enumerate(entries):
        if i == 4:
            M.selection(c, hx, ry, listw, ROW - 1, C["ACCENT"])
        c.text(name, hx + GAP, ry + 3, colour)
        ry += ROW - 1

    # The detail pane.
    dx = hx + listw + GAP_WIDE
    dw = inner - listw - GAP_WIDE
    c.text("LICTOR", dx, hy, C["TEXT"])
    M.chip_right(c, "EMPIRE", dx + dw, hy - 2, C["VOID"])
    y = M.wrapped(c, "THE CODE, STANDING IN A DOORWAY. THE MOST ARMOURED THING "
                     "HERE AND THE SLOWEST. NOT CHASING ANYBODY - MAKING A ROOM "
                     "EXPENSIVE TO CROSS.",
                  dx, hy + LINE_HEIGHT + GAP, dw, C["TEXT_MUTED"])
    y += GAP
    y = M.section(c, "SUB VARIANTS", dx, y, dw)
    for name, colour in (("SANCTIONED", C["TEXT_MUTED"]), ("AEGIS", C["DIMENSIONAL"]),
                         ("CENSOR", C["VOID"]), ("CUSTODIAN", C["FAVOR"]),
                         ("INQUISITOR", C["BAD"])):
        c.text(name, dx + GAP, y, colour)
        y += ROW - 2
    return c


# ===========================================================================

SCREENS = {
    # Each tab is its own preview. A tabbed screen where only the default tab
    # was ever looked at is a screen with two thirds of it unreviewed.
    "character_tab": lambda w, h: character_sheet(w, h, tab=0),
    "character_stats": lambda w, h: character_sheet(w, h, tab=1),
    "character_standing": lambda w, h: character_sheet(w, h, tab=2),
    "character_spent": lambda w, h: character_sheet(w, h, tab=1, unspent=0),
    "character_picker": character_picker,
    # The HUD with nothing to say — the state most players are in most of the
    # time, and the one easiest to forget to look at.
    "hud_quiet": lambda w, h: hud(w, h, favor=6, suspicion=0),
    "reforge_table": reforge_table,
    "rune_socket": rune_socket,
    "hud": hud,
    "bestiary": bestiary,
}


def check_fits(name, label, canvas):
    """
    Every panel must be entirely on screen, with a margin.

    The reforge table failed this by four pixels — a panel measured correctly
    from its parts and still too tall for a 720p window at GUI scale 3. That is
    not something eyeballing a mockup reliably catches, because a panel clipped
    by four pixels looks like a panel that goes to the edge on purpose.
    """
    problems = []
    for x, y, w, h, cw, ch in M.PANELS:
        if x < 2 or y < 2 or x + w > cw - 2 or y + h > ch - 2:
            problems.append("%s (%s): panel %dx%d at (%d,%d) does not fit %dx%d"
                            % (name, label, w, h, x, y, cw, ch))
    return problems


def main():
    problems = []
    for name, builder in SCREENS.items():
        # Both shapes that matter: a 1080p window at GUI scale 3 is about
        # 427x240, and a small window at scale 3 is about 320x240. A layout
        # that only works at one of them is broken.
        for label, (w, h) in (("wide", (427, 240)), ("narrow", (320, 240))):
            M.reset_panels()
            canvas = builder(w, h)
            canvas.save(OUT / f"{name}_{label}.png", zoom=2)
            problems += check_fits(name, label, canvas)

    for problem in problems:
        print("  - " + problem)
    if problems:
        print("\n%d screen(s) do not fit" % len(problems))
        sys.exit(1)
    print("rendered %d screens x 2 widths into %s; all panels fit"
          % (len(SCREENS), OUT))


if __name__ == "__main__":
    main()
