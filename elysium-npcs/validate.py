#!/usr/bin/env python3
"""
Cross-check the court declared in Java against the shipped resources.

Run from the repo root:   python3 validate.py

The failure this exists to catch is the one a compiler cannot: an envoy that
registers, spawns, walks around and is a purple-and-black cube, or one whose
name renders as a raw translation key. Both are data problems in a mod whose
data is generated, and generated data goes wrong when somebody adds a row to one
table and not the other.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources/assets/elysiumnpcs")
DATA = os.path.join(WORK, "src/main/resources/data/elysiumnpcs")
JAVA = os.path.join(WORK, "src/main/java/com/elysium/npcs")

problems = []


def between(text, signature):
    """The body of one method, by brace matching."""
    start = text.find(signature)
    if start < 0:
        return ""
    brace = text.find("{", start)
    if brace < 0:
        return ""
    depth = 0
    for index in range(brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[brace:index + 1]
    return text[brace:]


def fail(msg):
    problems.append(msg)


def exists(rel):
    return os.path.exists(os.path.join(RES, rel))


def strip_comments(text):
    """The code alone: every comment blanked, strings kept."""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def squashed(text):
    """The code alone, every run of whitespace as one space.

    The rules below name whole statements, and a statement only reads the same
    in the file and in the rule once its line breaks are gone. Comments go
    first, or a rule is beaten by putting // in front of the line it names.
    """
    return " ".join(strip_comments(text).split())


def body_of(text, signature):
    """The inside of one method, by brace matching; empty if it is not there."""
    start = text.find(signature)
    if start < 0:
        return ""
    opened = text.find("{", start)
    if opened < 0:
        return ""
    depth = 0
    for index in range(opened, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[opened + 1:index]
    return text[opened + 1:]


def enclosing(code, needle):
    """The headers of the blocks the first `needle` in `code` sits inside.

    A header is whatever stands between the end of the statement before and the
    brace that opens the block, squashed: "if (!fee.isEmpty())", "else".
    """
    at = code.find(needle)
    if at < 0:
        return []
    headers = []
    start = 0
    for index in range(at):
        char = code[index]
        if char == "{":
            headers.append(" ".join(code[start:index].split()))
            start = index + 1
        elif char == "}":
            if headers:
                headers.pop()
            start = index + 1
        elif char == ";":
            start = index + 1
    return headers


def harness_trouble(output):
    """The lines of a harness run that say what went wrong, and no more."""
    lines = [line for line in output.splitlines()
             if line.strip().startswith("FAIL:") or "failure(s)" in line]
    return "\n".join(lines or output.strip().splitlines()[-12:])[:2000]


# ---------------------------------------------------------------------------
# The five, read out of the enum that declares them
# ---------------------------------------------------------------------------

kind_source = open(os.path.join(JAVA, "entity/EnvoyKind.java"), encoding="utf-8").read()
kinds = re.findall(r'^\s+[A-Z_]+\("(\w+)",\s*Meter\.(\w+),\s*([\w.]+),\s*(\d+)',
                   kind_source, re.M)
if not kinds:
    print("read no envoys out of EnvoyKind.java - the declaration shape has changed")
    sys.exit(1)

names = [k[0] for k in kinds]
if len(set(names)) != len(names):
    fail(f"envoy id declared twice: {sorted({n for n in names if names.count(n) > 1})}")

lang_path = os.path.join(RES, "lang/en_us.json")
lang = json.load(open(lang_path, encoding="utf-8")) if os.path.exists(lang_path) else {}
if not lang:
    fail("no lang file - every name in the game would render as a translation key")

for envoy, meter, band, tier in kinds:
    if not exists(f"textures/entity/envoy/{envoy}.png"):
        fail(f"{envoy} has no skin at textures/entity/envoy/{envoy}.png - it renders "
             f"as a purple-and-black person")

    # The writ that summons them, and everything it needs to not be a cube.
    if not exists(f"models/item/{envoy}_summons.json"):
        fail(f"{envoy}'s writ has no item model")
    elif not exists(f"textures/item/{envoy}_summons.png"):
        fail(f"{envoy}'s writ has a model but no texture")

    for key in (f"entity.elysiumnpcs.envoy.{envoy}",
                f"elysiumnpcs.refusal.{envoy}",
                f"item.elysiumnpcs.{envoy}_summons"):
        if key not in lang:
            fail(f"{envoy}: no lang entry for {key} - the raw key shows in game")

print(f"envoys declared    : {len(kinds)} ({', '.join(names)})")

# ---------------------------------------------------------------------------
# Both halves of the court are reachable
# ---------------------------------------------------------------------------
#
# The scheduler picks the meter a player is further up and then only considers
# envoys who read that meter. A meter with nobody on it is a whole standing
# track that never produces a visitor - which looks exactly like the scheduler
# being broken, and would not be caught by anything else here.

for meter in ("FAVOR", "SUSPICION"):
    on_meter = [k for k in kinds if k[1] == meter]
    if not on_meter:
        fail(f"no envoy reads {meter}, so a player climbing that meter is never "
             f"visited by anybody")

print(f"standing coverage  : both meters have envoys")

# ---------------------------------------------------------------------------
# The ledger each office brings to its trade screen
# ---------------------------------------------------------------------------
#
# Since the envoys grew a merchant screen, EnvoyKind decides not just WHICH
# shelf an office deals off but the whole spread of its offer list. That
# arithmetic is easy to get subtly wrong in a way nothing else would catch: a
# lowest tier above the reward tier produces an empty or inverted list, and a
# screen whose rows are not in price order is one a player cannot read.
#
# So the shape is re-derived here from the same numbers the enum declares, and
# tierForOffer's arithmetic is reimplemented rather than trusted - which is the
# point of a validator that does not share code with the thing it validates.

no_comments = re.sub(r"/\*.*?\*/", " ", kind_source, flags=re.S)
shapes = re.findall(
    r'^\s+[A-Z_]+\("(\w+)",\s*Meter\.\w+,\s*[\w.]+,\s*(\d+),\s*(\d+),\s*(\d+)',
    no_comments, re.M)

if len(shapes) != len(kinds):
    fail(f"read a trade shape for {len(shapes)} of {len(kinds)} envoys - the "
         f"EnvoyKind declaration shape has changed and the ledger checks below "
         f"are no longer checking anything")

MAX_TIER = 3
deals_at_bottom = False
total_offers = 0

for envoy, reward_tier, lowest_tier, offer_count in shapes:
    reward_tier, lowest_tier, offer_count = int(reward_tier), int(lowest_tier), int(offer_count)
    total_offers += offer_count

    if not 0 <= lowest_tier <= MAX_TIER or not 0 <= reward_tier <= MAX_TIER:
        fail(f"{envoy} deals at tiers {lowest_tier}..{reward_tier}, outside 0..{MAX_TIER}")
    if lowest_tier > reward_tier:
        fail(f"{envoy}'s lowest tier ({lowest_tier}) is above its reward tier "
             f"({reward_tier}) - its offer list would be inverted")
    if offer_count < 1:
        fail(f"{envoy} brings {offer_count} offers, so its screen is empty and "
             f"opening it is indistinguishable from a broken entity")
    if offer_count > 12:
        fail(f"{envoy} brings {offer_count} offers - past what the vanilla trade "
             f"screen shows without scrolling, so the tail is invisible")
    if lowest_tier == 0:
        deals_at_bottom = True

    # tierForOffer, reimplemented. Must stay inside the office's own range and
    # must never step backwards, or the list is not in price order.
    previous = -1
    for i in range(offer_count):
        if offer_count <= 1 or reward_tier <= lowest_tier:
            tier = reward_tier
        else:
            tier = lowest_tier + (i * (reward_tier - lowest_tier)) // (offer_count - 1)
        if not lowest_tier <= tier <= reward_tier:
            fail(f"{envoy} offer {i} lands at tier {tier}, outside its own "
                 f"{lowest_tier}..{reward_tier} range")
        if tier < previous:
            fail(f"{envoy} offer {i} is cheaper than the one before it - the "
                 f"screen is not in price order")
        previous = tier

if not deals_at_bottom:
    fail("no envoy deals at tier 0, so a player the Code has not noticed has "
         "nobody to trade with at all")

print(f"trade ledgers      : {total_offers} offers across {len(shapes)} envoys, "
      f"all in range and in price order")

# ---------------------------------------------------------------------------
# The library still ships no items
# ---------------------------------------------------------------------------
#
# Prices are a provider registry for the same reason rewards are. If the
# library ever names a modded item the whole arrangement collapses into the
# thing it exists to avoid - so the one place that is allowed to name an item
# (the vanilla emerald fallback) is checked to still be the only one.

prices_path = os.path.join(WORK, "..", "elysium-lib", "src", "main", "java",
                           "com", "elysium", "lib", "standing", "ElysiumPrices.java")
if not os.path.exists(prices_path):
    fail("elysium-lib has no ElysiumPrices - the trade screen has no way to "
         "name a price and every offer would be free")
else:
    prices_src = open(prices_path, encoding="utf-8").read()
    for forbidden in ("com.elysium.core", "com.elysium.mobs", "com.elysium.dungeons",
                      "com.elysium.trinkets", "com.elysium.npcs"):
        if forbidden in prices_src:
            fail(f"ElysiumPrices references {forbidden} - the library must not "
                 f"know what is built on top of it")
    print("price registry     : present, and names no mod's items")

# ---------------------------------------------------------------------------
# Court commissions (Fourth Edition 14.5 / 19.2)
# ---------------------------------------------------------------------------
#
# A commission is the "high-standing endgame sink" 19.2 names. Two things make
# it that rather than another errand: only top-shelf offices issue one, and
# there has to be at least one office that does. Both are easy to break by
# retuning a tier, and neither breaks loudly - the feature would simply become
# unreachable, or become available from the first envoy a new player meets.

issuers = [envoy for envoy, reward_tier, _lowest, _count in shapes if int(reward_tier) >= 3]
if not issuers:
    fail("no office issues court commissions, so 14.5's contracts can never be "
         "taken by anybody")
for envoy, reward_tier, _lowest, _count in shapes:
    if int(reward_tier) < 3 and envoy in issuers:
        fail(f"{envoy} issues commissions without dealing at the top tier")

commission_path = os.path.join(WORK, "..", "elysium-lib", "src", "main", "java",
                               "com", "elysium", "lib", "standing",
                               "ElysiumCommission.java")
if not os.path.exists(commission_path):
    fail("elysium-lib has no ElysiumCommission - the court can issue contracts "
         "that nothing persists")
else:
    commission_src = open(commission_path, encoding="utf-8").read()
    for forbidden in ("com.elysium.core", "com.elysium.mobs", "com.elysium.dungeons",
                      "com.elysium.trinkets", "com.elysium.npcs"):
        if forbidden in commission_src:
            fail(f"ElysiumCommission references {forbidden} - the library must "
                 f"not know what is built on top of it")
    # A commission that copied on death would make the deadline unfalsifiable:
    # the one failure state a player can actually hit is running out of time,
    # and dying is the ordinary way that happens.
    #
    # Checked against the CODE, not the file. The first version of this grepped
    # the whole source and fired on the Javadoc paragraph that explains why the
    # attachment deliberately does not copy on death - a validator that cannot
    # tell a call from a comment about the call will eventually be silenced by
    # somebody rather than fixed, and then it checks nothing.
    commission_code = re.sub(r"/\*.*?\*/", " ", commission_src, flags=re.S)
    commission_code = re.sub(r"//[^\n]*", " ", commission_code)
    if "copyOnDeath" in commission_code:
        fail("the commission attachment copies on death - see the note in "
             "ElysiumCommission about why it deliberately does not")

# Every commission line the court can say must exist in the lang file.
commission_keys = set(re.findall(r'"(elysiumnpcs\.commission\.[a-z_]+)"',
                                 open(os.path.join(JAVA, "entity/ImperialEnvoy.java"),
                                      encoding="utf-8").read()))
missing = sorted(k for k in commission_keys if k not in lang)
if missing:
    fail(f"commission lines with no lang entry: {missing} - they render as raw keys")

print(f"court commissions  : issued by {len(issuers)} top-shelf office(s), "
      f"{len(commission_keys)} lines all translated")

# ---------------------------------------------------------------------------
# The model draws every accessory the enum can ask for
# ---------------------------------------------------------------------------
#
# Every kind's wardrobe has to be reachable.
#
# This used to compare EnvoyKind.Regalia against the parts EnvoyModel could
# toggle, because the model had one part per regalia entry and showed the ones
# a kind wore. The five envoys are five separate costumes now — Aurelia's hair
# is five chains and Lillith's greatcoat is two hanging tails — and none of
# that is expressible as "this kind has CAPE". The enum is still the honest
# answer to what an envoy is wearing; it is no longer what draws them.
#
# So the rule moved to the mechanism that replaced it: one wardrobe array per
# kind, each shown for exactly its own kind. A kind whose array is never
# toggled renders in everybody's costume at once.
model = open(os.path.join(JAVA, "client/model/EnvoyModel.java"), encoding="utf-8").read()
kinds = sorted(set(re.findall(r"^\s*([A-Z]+)\(", kind_source, re.M)))
if not kinds:
    fail("EnvoyKind declares no kinds at all")
wardrobe_sizes = {}
for kind in kinds:
    field = kind.lower() + "Parts"
    if field not in model:
        fail(f"EnvoyModel has no wardrobe for {kind}, so that envoy renders as "
             f"a bare body")
    elif f"show(this.{field}, kind == EnvoyKind.{kind})" not in model:
        fail(f"EnvoyModel never shows {field} for exactly EnvoyKind.{kind}, so "
             f"that envoy wears nothing, or wears everybody's costume at once")
    else:
        # ...and the wardrobe has to have something in it. The wiring check
        # above passes on an empty array, which is an envoy whose costume was
        # deleted from the generator: field present, toggle present, nothing
        # drawn. A deliberate test removed every costume in the project and
        # walked straight through.
        declared = model.split(f"this.{field} = new ModelPart[] {{", 1)
        # Counted as whole elements, at("bone", "part", ...), each standing
        # between the commas of the array: a bare `at(` counts one inside a
        # comment, a string or some other expression just the same.
        wardrobe = " ".join(declared[1].split("};", 1)[0].split()) if len(declared) > 1 else ""
        worn = len(re.findall(r'(?:^|,) ?at\("[a-z_]+"(?:, "[a-z0-9_]+")+\)(?= ?,| ?$)', wardrobe))
        if worn == 0:
            fail(f"{kind}'s wardrobe is empty, so that envoy renders as a bare "
                 f"body wearing nothing at all")
        wardrobe_sizes[kind] = worn
if "static void show(" not in model and "private void show(" not in model:
    fail("EnvoyModel has no show() helper, so nothing hides the four wardrobes "
         "an envoy is not wearing")

if wardrobe_sizes:
    smallest = min(wardrobe_sizes.items(), key=lambda pair: pair[1])
    print(f"court wardrobes    : {len(kinds)} kinds, "
          f"{sum(wardrobe_sizes.values())} costume parts between them, "
          f"plainest is {smallest[0].lower()} at {smallest[1]}")

# ---------------------------------------------------------------------------
# No hard dependency on elysium-core
# ---------------------------------------------------------------------------
#
# The court pays out of ElysiumRewards precisely so that it works with whatever
# is installed. An import of elysium-core would turn its optional dependency
# into a required one, and the mods.toml would then be lying.

for dirpath, _, filenames in os.walk(JAVA):
    for filename in filenames:
        if not filename.endswith(".java"):
            continue
        text = open(os.path.join(dirpath, filename), encoding="utf-8").read()
        if "com.elysium.core" in text:
            fail(f"{filename} imports elysium-core. The court is supposed to work with "
                 f"whatever rewards are registered, and mods.toml declares core optional")

print(f"dependencies       : library only, core stays optional")

# ---------------------------------------------------------------------------
# All JSON parses
# ---------------------------------------------------------------------------

count = 0
for dirpath, _, filenames in os.walk(os.path.join(WORK, "src/main/resources")):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {filename}: {exc}")

print(f"json files parsed  : {count}")

# ---------------------------------------------------------------------------
# §27.1's amnesty row: the only way Suspicion goes down by choice
# ---------------------------------------------------------------------------
#
# Everything else in §14 pushes Suspicion up. A meter with no release valve is
# not a resource a player manages, it is a timer they endure — so each of these
# is a way for the valve to quietly close, with nothing failing to compile.
writ_path = os.path.join(JAVA, "WritOfAmnestyItem.java")
if not os.path.exists(writ_path):
    fail("WritOfAmnestyItem.java is missing — §27.1's amnesty row is the only "
         "way a player can lower Suspicion by choosing to")
else:
    writ = re.sub(r"//[^\n]*|/\*.*?\*/", " ",
                  open(writ_path, encoding="utf-8").read(), flags=re.S)

    # The floor is the design. Amnesty discharges pressure and must never touch
    # §14.2's structural floor, or claiming stops meaning anything.
    if "floorFor" not in writ:
        fail("the writ of amnesty does not read the claim floor, so it would "
             "discharge Suspicion below what §14.2 calls a permanent floor — "
             "buying your way out of having built a fortress")

    # Refused, not consumed. This is the case a player hits by accident, having
    # bought two and used one.
    if "InteractionResultHolder.fail" not in writ:
        fail("a writ used at the floor is consumed rather than refused — an "
             "item that vanishes having done nothing reads as a bug")

    for key in ("elysiumnpcs.amnesty.granted", "elysiumnpcs.amnesty.at_floor",
                "elysiumnpcs.amnesty.nothing_filed",
                "elysiumnpcs.amnesty.tooltip",
                "elysiumnpcs.amnesty.tooltip_floor",
                "item.elysiumnpcs.writ_of_amnesty"):
        if key not in lang:
            fail(f"no lang entry for {key} — the raw key shows in game")

    for rel in ("models/item/writ_of_amnesty.json",
                "textures/item/writ_of_amnesty.png"):
        if not exists(rel):
            fail(f"the writ of amnesty has no {rel} — it renders as a "
                 f"purple-and-black square")

    envoy_src = open(os.path.join(JAVA, "entity/ImperialEnvoy.java"),
                     encoding="utf-8").read()
    envoy_code = re.sub(r"//[^\n]*|/\*.*?\*/", " ", envoy_src, flags=re.S)
    if "WRIT_OF_AMNESTY" not in envoy_code:
        fail("no envoy sells a writ of amnesty, so it exists and cannot be "
             "obtained")
    # Guaranteed, not rolled. A valve that appears one visit in four is one the
    # player being besieged cannot reach for.
    if "ElysiumRewards.roll" in envoy_code and "WRIT_OF_AMNESTY" in envoy_code:
        offer_block = envoy_code.split("WRIT_OF_AMNESTY")[0]
        if "Meter.SUSPICION" not in offer_block:
            fail("the amnesty offer is not gated on a Suspicion-reading "
                 "office. §14.4 puts Lillith and Aurelia on the Favor side, "
                 "and a diaspora cannot pardon you for mining")

    print("amnesty            : sold by Suspicion offices, floors at the claim, "
          "refused rather than wasted")

# ---------------------------------------------------------------------------
# The language file is generated, and a hand edit to it is a time bomb
# ---------------------------------------------------------------------------
#
# tools/gen_npcs.py writes assets/elysiumnpcs/lang/en_us.json wholesale — it
# builds a dict and dumps it, so anything added to the JSON by hand survives
# exactly until the next person runs the generator.
#
# That is not hypothetical. Seven commission lines were added to the JSON
# directly and were silently erased the next time the script ran; the only
# reason it was noticed is that another check happened to look for those keys.
# A key added tomorrow for something nothing else checks would just quietly stop
# existing, and the symptom is a player reading a raw translation key.
#
# So the file is regenerated in memory and compared, the same way
# elysium-lib guards its generated palette.
#
# Two generators write into this one file now: gen_npcs.py builds it wholesale
# and gen_village.py adds the five professions' strings on top. That is exactly
# the arrangement the paragraph above warns about, so the guard regenerates
# *both*, in the order regen.sh runs them, and compares the result. Neither
# script owns the file alone and neither can quietly drop the other's keys.
generator = os.path.join(WORK, "tools", "gen_npcs.py")
village_generator = os.path.join(WORK, "tools", "gen_village.py")
lang_json = os.path.join(RES, "lang", "en_us.json")
if not os.path.exists(generator):
    fail("tools/gen_npcs.py is missing, so the generated lang file is unguarded")
elif not os.path.exists(lang_json):
    fail("assets/elysiumnpcs/lang/en_us.json is missing — run tools/gen_npcs.py")
else:
    import importlib.util

    spec = importlib.util.spec_from_file_location("gen_npcs", generator)
    module = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(module)
    except Exception as exc:                      # noqa: BLE001 - reported
        module = None
        fail(f"tools/gen_npcs.py could not be imported to check the lang file: {exc}")

    if module is not None:
        # write_lang() writes as a side effect and returns a count, so the
        # comparison is done on the file it produces rather than on a return
        # value: regenerate, read, and put the original back either way.
        # Read twice: `before` with its line endings folded, for the
        # comparison, and `original` exactly as it is, for the restore. Every
        # write here is newline=chr(10): a text-mode write on Windows turns
        # each LF into CRLF, and the restore below used to commit a whole-file
        # line-ending flip with no content change after every Windows run.
        with open(lang_json, encoding="utf-8") as handle:
            before = handle.read()
        with open(lang_json, encoding="utf-8", newline="") as handle:
            original = handle.read()
        try:
            module.write_lang()
            if os.path.exists(village_generator):
                village_spec = importlib.util.spec_from_file_location(
                    "gen_village", village_generator)
                village = importlib.util.module_from_spec(village_spec)
                village_spec.loader.exec_module(village)
                with open(lang_json, encoding="utf-8") as handle:
                    merged = json.load(handle)
                merged.update(village.lang_entries())
                with open(lang_json, "w", encoding="utf-8", newline=chr(10)) as handle:
                    json.dump(merged, handle, indent=2, ensure_ascii=False,
                              sort_keys=True)
                    handle.write("\n")
            else:
                fail("tools/gen_village.py is missing, so the five professions' "
                     "names are unguarded and the next gen_npcs.py run would "
                     "erase them")
            with open(lang_json, encoding="utf-8") as handle:
                after = handle.read()
        finally:
            # newline=chr(10) writes the string untranslated, so the file goes
            # back byte for byte, whatever its line endings were
            with open(lang_json, "w", encoding="utf-8", newline=chr(10)) as handle:
                handle.write(original)

        if before != after:
            missing = sorted(set(json.loads(before)) - set(json.loads(after)))
            extra = sorted(set(json.loads(after)) - set(json.loads(before)))
            detail = ""
            if missing:
                detail += f" Keys the generator does not know about: {missing}."
            if extra:
                detail += f" Keys it would add: {extra}."
            fail(f"assets/elysiumnpcs/lang/en_us.json does not match its "
                 f"generators — the next run would overwrite it.{detail} Add "
                 f"the strings to gen_npcs.py or gen_village.py, not to the "
                 f"JSON.")
        else:
            print(f"generated lang     : {len(json.loads(before))} keys, "
                  f"matches gen_npcs.py + gen_village.py")

# ---------------------------------------------------------------------------
# The five village professions
# ---------------------------------------------------------------------------
#
# A profession only works if three registrations in three different registries
# agree: a block, a point of interest naming that block's states, and a
# profession naming that point of interest. Break the middle link and the bench
# is furniture — villagers walk past it forever and nothing in any log says why.
# Break the last and the profession exists with no way into it.
#
# None of that fails to load, so none of it is caught by compiling. It is caught
# here, by reading the Java that declares all three together and then checking
# that every file each one needs is on disk.

STATIONS = os.path.join(WORK, "src/main/java/com/elysium/npcs/village/ElysiumStations.java")
VILLAGE = os.path.join(WORK, "src/main/java/com/elysium/npcs/village")
TRADES = os.path.join(VILLAGE, "ElysiumVillagerTrades.java")

if not os.path.exists(STATIONS):
    fail("ElysiumStations.java is missing — there are no village professions")
else:
    station_source = open(STATIONS, encoding="utf-8").read()
    # Comments stripped before anything is looked for in the code.
    #
    # Not fussiness: the first version of the block-state check below searched
    # the whole file, and the javadoc above the POI registration explains the
    # rule by naming the very method the check looks for. Deliberately breaking
    # the code left the sentence in the comment behind, and the check passed.
    station_code = re.sub(r"//[^\n]*|/\*.*?\*/", " ", station_source, flags=re.S)
    stations = re.findall(r'station\("(\w+)",\s*"(\w+)",', station_code)

    if not stations:
        fail("read no stations out of ElysiumStations.java — the declaration "
             "shape has changed and every check below would pass by finding "
             "nothing")
    else:
        # The three registries are written by one helper taking one call. If
        # that ever becomes three separate lists, a bench with no profession
        # attached stops being impossible.
        for registry in ("BLOCKS", "POI_TYPES", "PROFESSIONS"):
            # Only the calls that register content. Each of these registers is
            # also handed the mod bus once, in register(), and counting that as
            # a content registration was the first version of this check
            # failing on correct code.
            calls = len(re.findall(registry + r"\.register\((?!modEventBus)",
                                   station_source))
            if calls != 1:
                fail(f"{registry}.register is called {calls} times with content "
                     f"in ElysiumStations. One call each, inside station(), is "
                     f"what makes it impossible to register a bench without "
                     f"the profession that claims it")

        # A POI matches BlockStates, not Blocks. Registering the default state
        # alone works today — these benches have no properties — and silently
        # stops matching three rotations out of four the day one gains a facing.
        if "getPossibleStates()" not in station_code:
            fail("the point of interest is not registered against every block "
                 "state. It works until a bench gains a property, and then "
                 "villagers take the job only when the bench happens to face "
                 "one way")

        for role, bench in stations:
            for rel, what in (
                    (f"blockstates/{bench}.json", "blockstate"),
                    (f"models/block/{bench}.json", "block model"),
                    (f"models/item/{bench}.json", "item model"),
                    (f"textures/block/{bench}_front.png", "front texture"),
                    (f"textures/block/{bench}_side.png", "side texture"),
                    (f"textures/block/{bench}_top.png", "top texture")):
                if not os.path.exists(os.path.join(RES, rel)):
                    fail(f"{bench} has no {what} ({rel})")

            for rel, what in (
                    (f"loot_table/blocks/{bench}.json", "loot table"),
                    (f"recipe/{bench}.json", "recipe")):
                if not os.path.exists(os.path.join(DATA, rel)):
                    fail(f"{bench} has no {what} — it is "
                         + ("unobtainable" if "recipe" in rel
                            else "mined into nothing"))

            # The villager's clothing. Vanilla looks for exactly this path, and
            # a profession without one renders as the missing texture.
            overlay = os.path.join(
                RES, "textures/entity/villager/profession", role + ".png")
            if not os.path.exists(overlay):
                fail(f"the {role} has no clothing texture at "
                     f"textures/entity/villager/profession/{role}.png, so every "
                     f"villager who takes the job renders untextured")
            else:
                try:
                    from PIL import Image
                    size = Image.open(overlay).size
                    if size != (64, 64):
                        fail(f"the {role}'s clothing is {size[0]}x{size[1]}; a "
                             f"villager overlay is 64x64 and anything else maps "
                             f"to the wrong part of the model")
                except ImportError:
                    pass

            # Vanilla drops the namespace when it builds a villager's name, so
            # this key has to sit under minecraft's prefix in our own file.
            for key, what in ((f"entity.minecraft.villager.{role}", "name"),
                              (f"block.elysiumnpcs.{bench}", "bench name")):
                if key not in lang:
                    fail(f"no {what} for the {role} ({key}) — it shows as the "
                         f"raw key in game")

        # Every profession has to have something to say. A villager that takes
        # a job and offers nothing is worse than one with no job at all: the
        # player walked to it.
        if not os.path.exists(TRADES):
            fail("ElysiumVillagerTrades.java is missing — five professions "
                 "exist and none of them trades")
        else:
            trade_source = re.sub(
                r"//[^\n]*|/\*.*?\*/", " ",
                open(TRADES, encoding="utf-8").read(), flags=re.S)
            for role, _bench in stations:
                constant = role.upper()
                if f"ElysiumStations.{constant}" not in trade_source:
                    fail(f"the {role} has no trade table. It can be trained "
                         f"into and offers nothing")
            # Every real offer names a concrete item as its first argument;
            # the two helper *definitions* take an `Item` parameter and so do
            # not match. Counting bare "buy(" instead caught the helpers and
            # the wrappers, and needed three subtractions to get back to the
            # right number — which is a sign the pattern was wrong rather than
            # the arithmetic.
            listed = len(re.findall(r"\b(?:buy|sell)\(Items\.", trade_source))
            expected = len(stations) * 5 * 2
            if listed != expected:
                fail(f"the trade tables hold {listed} offers; "
                     f"{len(stations)} professions at five levels and two "
                     f"offers a level is {expected}. A level with one offer is "
                     f"a tier that looks like a bug")

            # The module depends on the library and treats core as optional, so
            # a trade naming an Elysium item would resolve to air in a pack that
            # has the NPCs and not the content.
            for hit in re.findall(r'"(elysium[\w]*):', trade_source):
                fail(f"a trade names {hit}: — every offer has to be a vanilla "
                     f"item, or it is air in a pack without that mod")

        print(f"village professions: {len(stations)} roles, each with a bench, "
              f"a point of interest, clothing and five levels of trade")

        # ------------------------------------------------------------------
        # The wandering trader (SPEC_CLONE P12.2 and P12.8)
        # ------------------------------------------------------------------
        #
        # He is the one merchant in the game who is not from here, and in
        # vanilla he sells saplings and coral — things a player already has.
        # What is checked here is that both of his pockets are filled, that the
        # rare one is actually rarer, and that the registry other modules add
        # through is still wired to the event.
        wanderer_path = os.path.join(VILLAGE, "ElysiumWandererTrades.java")
        if not os.path.exists(wanderer_path):
            fail("ElysiumWandererTrades.java is missing — the wandering trader is "
                 "vanilla's, which is to say he is a llama with some coral")
        else:
            wanderer = re.sub(r"//[^\n]*|/\*.*?\*/", " ",
                              open(wanderer_path, encoding="utf-8").read(), flags=re.S)
            if "WandererTradesEvent" not in wanderer:
                fail("the wandering trader's table is never handed to the game")
            if "getGenericTrades().addAll(" not in wanderer \
                    or "getRareTrades().addAll(" not in wanderer:
                fail("one of the wandering trader's two pockets is never filled, so "
                     "half the table is written and never offered")
            generic_block = between(wanderer, "private static List<VillagerTrades."
                                              "ItemListing> generic()")
            rare_block = between(wanderer, "private static List<VillagerTrades."
                                           "ItemListing> rare()")
            generic_offers = re.findall(r"\b(?:buy|sell)\(Items\.\w+,\s*\d+,\s*(\d+)",
                                        generic_block)
            rare_offers = re.findall(r"\bsell\(Items\.\w+,\s*\d+,\s*(\d+)", rare_block)
            if len(generic_offers) < 8:
                fail(f"the wandering trader has {len(generic_offers)} common offers. He "
                     f"shows five of them at a time, so a short list is the same trader "
                     f"every time")
            if len(rare_offers) < 5:
                fail(f"the wandering trader has {len(rare_offers)} rare offers and shows "
                     f"two, so 'rare' means 'always these'")
            if generic_offers and rare_offers \
                    and min(int(price) for price in rare_offers) \
                    <= max(int(price) for price in generic_offers):
                fail("the wandering trader's rare pocket is not dearer than his common "
                     "one, so the rare slots are two more common slots")
            if re.search(r"\bbuy\(", rare_block):
                fail("the wandering trader buys something in his rare pocket. The rare "
                     "slots are what he happens to be carrying, and a buy offer there is "
                     "a chore that sometimes exists")
            if "public static void register(" not in wanderer:
                fail("there is no way for anything else to put something in the "
                     "wandering trader's pack, which is the whole of the trade registry")
            if "new CopyOnWriteArrayList<>()" not in wanderer:
                fail("the registry's list is not safe to read from a world thread while "
                     "startup is still writing to it")
            if "for (Added extra : ADDED)" not in wanderer:
                fail("things registered from outside are never actually offered, so the "
                     "registry accepts entries and drops them")
            for hit in re.findall(r'"(elysium[\w]*):', wanderer):
                fail(f"the wandering trader offers {hit}: — every offer has to be a "
                     f"vanilla item, or it is air in a pack without that mod")
            print(f"wandering trader   : {len(generic_offers)} common and "
                  f"{len(rare_offers)} rare offers, the rare ones dearer, and a registry "
                  f"anything can add to")

# ---------------------------------------------------------------------------
# The writ of audience: every envoy sells its own, one a visit
# ---------------------------------------------------------------------------
#
# The five writs were creative-only: registered, drawn, named, and handed out
# by nothing but the creative tab. Each envoy now sells its own as a fixed row
# after the amnesty writ, priced off the office's top shelf, so each writ is
# bought where its envoy deals: with elysium-core's prices, 4-6 aetherium or
# voidglass ingots from the Sentinel, 24-32 neutronium from the Emperor or the
# Queen.
# Each rule below is one way for that to be quietly undone.
WRIT = "new ItemStack(ElysiumNpcs.summonsFor(kind).get())"
envoy_java = open(os.path.join(JAVA, "entity/ImperialEnvoy.java"), encoding="utf-8").read()
roll = squashed(body_of(envoy_java, "private MerchantOffers rollOffers()"))
refresh = squashed(body_of(envoy_java, "private void refreshSoldOut()"))
if WRIT not in roll:
    fail("no envoy sells its own writ of audience, so the five writs are creative-only "
         "again: rollOffers must add " + WRIT + " as a fixed row")
else:
    row = re.search(r"rolled\.add\(new MerchantOffer\( ?new ItemCost\((\w+)\.getItem\(\), "
                    r"(\w+)\.getCount\(\)\), " + re.escape(WRIT) + r", ([\w.]+), 0, 0\.0F\)\);",
                    roll)
    if row is None:
        fail("the writ of audience row no longer reads as rolled.add(new MerchantOffer(new "
             "ItemCost(fee...), the writ, uses, 0, 0.0F)), so nothing below can say what it "
             "costs or how many a visit sells")
    else:
        price, counted, uses = row.groups()
        if counted != price or (f"ItemStack {price} = ElysiumPrices.roll(kind.rewardTier(), "
                                f"ledger);") not in roll:
            fail("the writ of audience is not priced off the office's top shelf from the "
                 "ledger's own seed (ElysiumPrices.roll(kind.rewardTier(), ledger)): it costs "
                 "less than the best row on the screen, or changes under a player who walks "
                 "home to pay for it")
        if uses != "CourtRules.AUDIENCE_USES":
            fail(f"the writ of audience row sells {uses} a visit rather than "
                 f"CourtRules.AUDIENCE_USES, the one a visit the harness holds")
        # A braceless if in front of the row would gate it without a block.
        if roll[max(0, row.start() - 2):row.start()] not in ("{ ", "; ", "} "):
            fail("the writ of audience row is the tail of some other statement, so it is "
                 "sold only when that statement says so")

    # Every office, not one half. The row may wait on its price and on nothing
    # else; the screen already refuses below the office's band.
    roll_code = strip_comments(body_of(envoy_java, "private MerchantOffers rollOffers()"))
    gates = [header for header in enclosing(roll_code, "ElysiumNpcs.summonsFor(kind)")
             if not re.fullmatch(r"if \(!\w+\.isEmpty\(\)\)", header)]
    if gates:
        fail(f"the writ of audience row sits inside '{gates[0]}', so only some offices sell "
             f"their writ. Every office sells its own, and the screen already refuses below "
             f"the office's band")

    # Counted, and left alone by the reroll. A sold-out writ that came back as
    # a reward row would pay the player a second time for buying it.
    counted_at = roll.find("fixedRows = rolled.size();")
    if not roll.find(WRIT) < counted_at < roll.find(
            "for (int i = 0; i < kind.offerCount(); i++) {"):
        fail("rollOffers does not count its fixed rows between the writ of audience and the "
             "first roll (fixedRows = rolled.size();), so the reroll cannot tell a writ from "
             "a roll")
    if "for (int i = fixedRows; i < current.size(); i++) {" not in refresh \
            or "int tier = kind.tierForOffer(i - fixedRows);" not in refresh:
        fail("refreshSoldOut rerolls the fixed rows or numbers the rolls from the top of the "
             "screen: a writ bought out comes back as an extra reward, and every refill "
             "lands at the tier of the row below it")

    print("writs of audience  : every office sells its own, one a visit, off its top "
          "shelf, and the reroll leaves it sold")

# ---------------------------------------------------------------------------
# What mines a bench (rules/HarvestRules; the harvest audit of 29 Sep 2026)
# ---------------------------------------------------------------------------
#
# A block breaks at the speed of the tool the game lists it under, and one in
# no tool's list breaks at a fist's pace whatever a player holds. The pickaxe
# tag was a file typed by hand, five benches long, and the gear bench was not
# in it. It is written from the rules by gen_village.py now, for every bench.
# So every registered bench is in exactly one of this module's mineable tags,
# or named BY_HAND in the rules with its reason here; an exemption is read
# where the bench is registered (the one helper that registers all of them),
# not taken on trust; the tag is the benches in the order the Java declares
# them; and a tag names only blocks this module registers, because one
# unknown id makes the game refuse the whole tag.
_NS = "elysiumnpcs"
_harvest_path = os.path.join(JAVA, "rules", "HarvestRules.java")
_harvest_src = (strip_comments(open(_harvest_path, encoding="utf-8").read())
                if os.path.exists(_harvest_path) else "")
if not _harvest_src:
    fail("rules/HarvestRules.java is missing, so nothing says what mines a bench and the "
         "pickaxe tag is a file typed by hand again")
HARVEST_TOOLS = ("pickaxe", "axe", "shovel", "hoe")
_bench_tool = re.search(r'String BENCH_TOOL = "([a-z]+)";', _harvest_src)
_bench_tool = _bench_tool.group(1) if _bench_tool else ""
if _bench_tool not in HARVEST_TOOLS:
    fail(f"HarvestRules.BENCH_TOOL is {_bench_tool!r}, which is no tool the game keeps a "
         f"list for, so the benches are written into a tag nothing reads")
_by_hand_found = re.search(r"List<String> BY_HAND = List\.of\(([^;]*?)\);", _harvest_src, re.S)
_by_hand = re.findall(r'"([a-z_]+)"', _by_hand_found.group(1)) if _by_hand_found else []
_mineable_dir = os.path.join(WORK, "src/main/resources/data/minecraft/tags/block/mineable")
MINEABLE = {}
for _tool in HARVEST_TOOLS:
    _tag = os.path.join(_mineable_dir, _tool + ".json")
    if not os.path.isfile(_tag):
        continue
    _doc = json.load(open(_tag, encoding="utf-8"))
    if _doc.get("replace"):
        fail(f"mineable/{_tool} sets replace: true, which throws away every other mod's "
             f"entries in the game's own tag")
    for _one in _doc.get("values", []):
        _named = _one.get("id") if isinstance(_one, dict) else _one
        if not isinstance(_named, str):
            continue
        MINEABLE.setdefault(_named, set()).add(_tool)
        if _named.startswith(f"{_NS}:") and not exists(
                f"blockstates/{_named.split(':', 1)[1]}.json"):
            fail(f"mineable/{_tool} names {_named}, which is not a block this module "
                 f"registers, so the game refuses the whole tag and every block in it is "
                 f"back to dropping nothing")

#: The benches no tool is for, and why. HarvestRules.BY_HAND says which; a
#: bench it names with no reason here is refused, and so is a reason for a
#: bench it no longer names. Nothing, today: every bench is framed in copper.
MINE_EXEMPT = {}

#: What the game's blocks this module copies ask of a tool, read out of the
#: game's own jar. No bench copies one; a bench that starts to is looked up
#: here or refused.
ASKS_FOR_A_TOOL = {}


def asks_for_a_tool(made, what):
    """Whether a block built like this asks for the right tool, and how."""
    if not made:
        fail(f"nothing here finds how {what} is built, so nothing shows whether it asks "
             f"for the right tool")
        return None, ""
    if ".requiresCorrectToolForDrops()" in made:
        return True, "it says so where it is registered"
    for copied in re.findall(r"\bBlocks\.([A-Z_]+)", made):
        if copied not in ASKS_FOR_A_TOOL:
            fail(f"{what} copies Blocks.{copied}, and nothing here knows whether that asks "
                 f"for the right tool: look it up in the game's jar and add it")
            return None, ""
        if ASKS_FOR_A_TOOL[copied]:
            return True, f"it copies Blocks.{copied}, which does"
    return False, ""


_station_code = (strip_comments(open(STATIONS, encoding="utf-8").read())
                 if os.path.exists(STATIONS) else "")
_benches = [bench for _role, bench in re.findall(r'station\("(\w+)",\s*"(\w+)",', _station_code)]
for _name in sorted(os.path.splitext(f)[0] for f in os.listdir(os.path.join(RES, "blockstates"))
                    if f.endswith(".json")) if os.path.isdir(os.path.join(RES, "blockstates")) else []:
    if _name not in _benches:
        fail(f"{_name} has a blockstate and is no station of ElysiumStations, so nothing "
             f"here can say what mines it")
for _name in _by_hand:
    if _name not in MINE_EXEMPT:
        fail(f"HarvestRules breaks {_name} by hand and this gives no reason, so it is left "
             f"out of every tool's tag on nobody's say-so")
    if _name not in _benches:
        fail(f"HarvestRules breaks {_name} by hand and no such bench is registered")
for _name in MINE_EXEMPT:
    if _name not in _by_hand:
        fail(f"a reason is given for breaking {_name} by hand and HarvestRules no longer "
             f"does, so the exemption covers a bench the rules put in a tool's tag")
# One helper registers every bench, so one reading answers for all of them.
_station_flat = squashed(body_of(_station_code, "private static Station station("))
_start = _station_flat.find("BLOCKS.register(id,")
_made = _station_flat[_start:_station_flat.find(";", _start)] if _start >= 0 else ""
_asks, _how = asks_for_a_tool(_made, "a bench")
_tool_blocks = []
_asking_untagged = []
_untagged = []
for _name in _benches:
    _tags = MINEABLE.get(f"{_NS}:{_name}", set())
    if _asks:
        _tool_blocks.append(_name)
    if len(_tags) > 1:
        fail(f"{_name} is in {len(_tags)} mineable tags ({', '.join(sorted(_tags))}), so two "
             f"tools are right and the rules can only have named one")
    if _name in MINE_EXEMPT:
        if _asks:
            fail(f"{_name} is exempt from the mineable tags ({MINE_EXEMPT[_name]}), but it "
                 f"asks for the right tool: {_how}. With no tag no tool is right, so it "
                 f"would drop nothing")
        if _tags:
            fail(f"{_name} is exempt from the mineable tags and in one all the same; take "
                 f"it off MINE_EXEMPT or out of the tag")
        continue
    if not _tags:
        (_asking_untagged if _asks else _untagged).append(_name)
    if sorted(_tags) != [_bench_tool]:
        fail(f"the rules put {_name} under {_bench_tool} and the tags under "
             f"{' and '.join(sorted(_tags)) or 'none'}; the tags are written from the rules "
             f"by tools/gen_village.py, so one of them was edited by hand")
if _asking_untagged:
    fail(f"{len(_asking_untagged)} block(s) ask for the right tool and are in no tool's tag, "
         f"so no tool is ever right and they drop nothing however they are mined: "
         + ", ".join(_asking_untagged))
if _untagged:
    fail(f"{len(_untagged)} block(s) are in no mineable tag and not exempt, so every tool "
         f"breaks them at a fist's pace: " + ", ".join(_untagged))
# And the tag is the benches in the order the Java declares them, which is
# what tools/gen_village.py writes from its table; anything else was typed.
_tag = os.path.join(_mineable_dir, _bench_tool + ".json")
_shipped = json.load(open(_tag, encoding="utf-8")).get("values", []) if os.path.isfile(_tag) else []
_wanted = [f"{_NS}:{bench}" for bench in _benches if bench not in _by_hand]
if _benches and _shipped != _wanted:
    fail(f"mineable/{_bench_tool} is not what tools/gen_village.py writes from HarvestRules "
         f"and the stations ({len(_shipped)} named, {len(_wanted)} declared, or the order "
         f"differs)")
print(f"what mines them    : {len(_benches)} bench(es), {len(_tool_blocks)} asking for the "
      f"right tool, every one in exactly one tool's tag or broken by hand with a reason: "
      f"{len(_benches) - len(MINE_EXEMPT)} by {_bench_tool}, {len(MINE_EXEMPT)} by hand")

# ---------------------------------------------------------------------------
# Who arrives: rules/CourtRules, run over every standing by tools/CourtHarness
# ---------------------------------------------------------------------------
#
# EnvoyScheduler used to choose on its own, against a live player, and had a
# hole for as long as it did: Favor 1 to 24 over less Suspicion picked the
# Favor half, whose lowest gate is Favor 25, and Favor does not decay at 25 or
# below, so such a player was visited by nobody, the Sentinel included. The
# choice is now CourtRules.arrivals, the JDK alone, and the harness runs it over
# every Favor/Suspicion pair with the court read out of EnvoyKind and the
# meters out of ElysiumStanding. Handed over rather than copied, so raising
# the Sentinel's gate fails here as surely as breaking the rule does.
PURE = os.path.join(JAVA, "rules")
HARNESS = os.path.join(WORK, "tools", "CourtHarness.java")
HARNESS_MAIN = "com.elysium.npcs.rules.CourtHarness"

rule_files = sorted(name for name in os.listdir(PURE)
                    if name.endswith(".java")) if os.path.isdir(PURE) else []
if "CourtRules.java" not in rule_files:
    fail("rules/CourtRules.java is missing, so who arrives is decided where no harness "
         "can run it")
for name in rule_files:
    rule_code = strip_comments(open(os.path.join(PURE, name), encoding="utf-8").read())
    rule_code = re.sub(r"^\s*package [^;]+;", "", rule_code, count=1, flags=re.M)
    for forbidden in ("net.minecraft", "net.neoforged", "com.mojang", "com.elysium"):
        if forbidden in rule_code:
            fail(f"rules/{name} reaches for {forbidden}; the rules are the JDK and nothing "
                 f"else, so that a harness can run them")

standing_path = os.path.join(WORK, "..", "elysium-lib", "src", "main", "java", "com",
                             "elysium", "lib", "standing", "ElysiumStanding.java")
standing_code = strip_comments(open(standing_path, encoding="utf-8").read()) \
    if os.path.exists(standing_path) else ""
standing_ints = dict(re.findall(r"public static final int (\w+) = (\w+);", standing_code))


def standing_int(token, seen=()):
    """A number, or a constant of ElysiumStanding's followed to its number."""
    token = token.strip().replace("ElysiumStanding.", "")
    if token.isdigit():
        return int(token)
    if token in standing_ints and token not in seen:
        return standing_int(standing_ints[token], seen + (token,))
    return None


meter_top = standing_int("MAX")
band_edges = re.search(r"BAND_THRESHOLDS = \{([^}]*)\}", standing_code)
band_edges = [standing_int(edge) for edge in band_edges.group(1).split(",")] if band_edges else []
court = [(envoy, meter, standing_int(gate)) for envoy, meter, gate in re.findall(
    r'^\s+[A-Z_]+\("(\w+)",\s*Meter\.(\w+),\s*([\w.]+),', strip_comments(kind_source), re.M)]

if meter_top is None or not band_edges or None in band_edges or not court \
        or any(gate is None for _envoy, _meter, gate in court):
    fail("could not read every member's gate out of EnvoyKind, or the top and the band "
         "edges of a meter out of ElysiumStanding (MAX, BAND_THRESHOLDS), so the harness "
         "has no real court to try")
elif not os.path.exists(HARNESS):
    fail("tools/CourtHarness.java is missing, so nothing runs the court's rules")
elif rule_files:
    with tempfile.TemporaryDirectory() as tmp:
        staged = os.path.join(tmp, "src")
        classes = os.path.join(tmp, "out")
        os.makedirs(staged)
        for source in [os.path.join(PURE, name) for name in rule_files] + [HARNESS]:
            with open(source, encoding="utf-8") as original, \
                    open(os.path.join(staged, os.path.basename(source)), "w",
                         encoding="utf-8", newline=chr(10)) as copy:
                copy.write(original.read())
        compiled = subprocess.run(
            ["javac", "-nowarn", "-d", classes]
            + [os.path.join(staged, name) for name in sorted(os.listdir(staged))],
            capture_output=True, text=True)
        if compiled.returncode != 0:
            fail("the court's rules and harness do not compile against the JDK alone:\n"
                 + compiled.stderr.strip()[:1200])
        else:
            ran = subprocess.run(
                ["java", "-cp", classes, HARNESS_MAIN, str(meter_top),
                 ",".join(str(edge) for edge in band_edges)]
                + [f"{envoy}:{meter}:{gate}" for envoy, meter, gate in court],
                capture_output=True, text=True)
            if ran.returncode != 0:
                fail("the court harness failed:\n" + harness_trouble(ran.stdout))
            else:
                print("who arrives        : " + ran.stdout.strip().splitlines()[-1])

# The scheduler asks the rule, with the player's own meters and the court as
# EnvoyKind declares it, and sends one of whoever the rule names. Anything
# else is a choice made where the harness cannot see it.
scheduler_java = open(os.path.join(JAVA, "EnvoyScheduler.java"), encoding="utf-8").read()
pick = squashed(body_of(strip_comments(scheduler_java), "private static EnvoyKind pick("))
if "int[] chosen = CourtRules.arrivals(favor, suspicion, readsFavor, willDeal);" not in pick:
    fail("EnvoyScheduler.pick does not ask CourtRules.arrivals who may come, so the choice "
         "is arithmetic the harness never runs, which is how the dead zone went unseen")
if "int favor = ElysiumStanding.getFavor(player);" not in pick \
        or "int suspicion = ElysiumStanding.getSuspicion(player);" not in pick:
    fail("EnvoyScheduler.pick hands the rule something other than the player's own Favor "
         "and Suspicion")
if "readsFavor[i] = court[i].meter() == EnvoyKind.Meter.FAVOR;" not in pick:
    fail("EnvoyScheduler.pick does not tell the rule which members read Favor, so the "
         "halves it chooses between are not the ones EnvoyKind declares")
if "willDeal[i] = court[i].willDealWith(player);" not in pick:
    fail("EnvoyScheduler.pick does not ask each member whether they would deal "
         "(willDealWith), so an envoy can arrive only to refuse you")
if "return court[chosen[random.nextInt(chosen.length)]];" not in pick:
    fail("EnvoyScheduler.pick does not send one of the members the rule chose, drawn at "
         "random: the same envoy comes every time, or one the rule did not name")

print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  -", problem)
    sys.exit(1)

print("all court checks passed")
