#!/usr/bin/env python3
"""
Every clip the pack plays, rendered from the sound table.

    python3 elysium-lib/tools/gen_sounds.py

Reads sound/ElysiumSoundRules.java (the voices, hums and clicks as numbers),
synthesises each clip with art/sound.py, and writes

    assets/elysiumlib/sounds/voice/<id>/<call>.ogg
    assets/elysiumlib/sounds/hum/<id>.ogg
    assets/elysiumlib/sounds/click/<id>.ogg
    assets/elysiumlib/sounds.json

under ELYSIUM_LIB_OUT (the module by default). The event names in
sounds.json are the ones ElysiumSounds registers from the same table:
voice.<id>.<call>, hum.<id>, click.<id>. Nothing here decides a sound; a
number that is not in the rules is not in a clip.
"""
import json
import os
import pathlib
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "art"))
import sound  # noqa: E402

NS = "elysiumlib"
RULES = os.path.join(HERE, "src/main/java/com/elysium/lib/sound/ElysiumSoundRules.java")
OUT = os.environ.get("ELYSIUM_LIB_OUT", HERE)
ASSETS = pathlib.Path(OUT) / "src/main/resources/assets" / NS
SOUNDS = ASSETS / "sounds"


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def number(text):
    return float(text.rstrip("DdFf"))


def parse(source):
    """The three lists off the Java, as dicts."""
    plain = strip_comments(source)
    voices = []
    for m in re.finditer(
            r'voice\("(\w+)",\s*([-\d.]+),\s*(THROAT|HISS|CHIRP|DRONE|METAL),\s*([-\d.]+),\s*([-\d.]+),'
            r'\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+),\s*([-\d.]+),\s*(\d+)\)', plain):
        voices.append({
            "id": m.group(1), "pitch": number(m.group(2)), "timbre": m.group(3).lower(),
            "growl": number(m.group(4)), "breath": number(m.group(5)), "length": number(m.group(6)),
            "vibrato": (number(m.group(7)), number(m.group(8))), "body": number(m.group(9)),
            "seed": int(m.group(10)),
        })
    hums = [{"id": m.group(1), "kind": m.group(2), "pitch": number(m.group(3)), "seed": int(m.group(4))}
            for m in re.finditer(r'new Hum\("(\w+)",\s*"(\w+)",\s*([-\d.]+),\s*(\d+)\)', plain)]
    clicks = [{"id": m.group(1), "kind": m.group(2), "pitch": number(m.group(3)), "seed": int(m.group(4))}
              for m in re.finditer(r'new Click\("(\w+)",\s*"(\w+)",\s*([-\d.]+),\s*(\d+)\)', plain)]
    calls = re.search(r'CALLS = List\.of\(([^)]*)\)', plain)
    calls = re.findall(r'"(\w+)"', calls.group(1)) if calls else []
    return voices, hums, clicks, calls


def main():
    source = read(RULES)
    voices, hums, clicks, calls = parse(source)
    if not voices or not hums or not clicks or calls != ["ambient", "hurt", "death"]:
        sys.exit("could not read the sound table off ElysiumSoundRules.java: %d voice(s), %d hum(s), "
                 "%d click(s), calls %s" % (len(voices), len(hums), len(clicks), calls))
    entries = {}
    written = 0
    for row in voices:
        voice = sound.Voice(row["pitch"], row["timbre"], growl=row["growl"], breath=row["breath"],
                            length=row["length"], vibrato=row["vibrato"], body=row["body"])
        for call, clip in voice.clips(row["seed"]).items():
            path = SOUNDS / "voice" / row["id"] / (call + ".ogg")
            sound.write_ogg(path, clip)
            written += 1
            entries["voice.%s.%s" % (row["id"], call)] = {
                "subtitle": "subtitles.%s.voice.%s.%s" % (NS, row["id"], call),
                "sounds": ["%s:voice/%s/%s" % (NS, row["id"], call)],
            }
    for row in hums:
        clip = sound.Hum(row["kind"], row["pitch"]).render(row["seed"])
        sound.write_ogg(SOUNDS / "hum" / (row["id"] + ".ogg"), clip)
        written += 1
        entries["hum." + row["id"]] = {
            "subtitle": "subtitles.%s.hum.%s" % (NS, row["id"]),
            "sounds": [{"name": "%s:hum/%s" % (NS, row["id"]), "stream": False}],
        }
    for row in clicks:
        clip = sound.click(row["kind"], row["seed"], row["pitch"])
        sound.write_ogg(SOUNDS / "click" / (row["id"] + ".ogg"), clip)
        written += 1
        entries["click." + row["id"]] = {
            "subtitle": "subtitles.%s.click.%s" % (NS, row["id"]),
            "sounds": ["%s:click/%s" % (NS, row["id"])],
        }
    ASSETS.mkdir(parents=True, exist_ok=True)
    with open(ASSETS / "sounds.json", "w", encoding="utf-8", newline="\n") as handle:
        json.dump(dict(sorted(entries.items())), handle, indent=2)
        handle.write("\n")
    # Subtitles, on top of the committed language file (the way
    # gen_valoria_effects.py adds its own): one line per event, so a player
    # with subtitles on reads "Brute hurts" rather than nothing.
    base_path = os.path.join(HERE, "src/main/resources/assets", NS, "lang/en_us.json")
    base = json.load(open(base_path, encoding="utf-8")) if os.path.isfile(base_path) else {}
    said = {"ambient": "calls", "hurt": "hurts", "death": "dies"}
    for row in voices:
        who = row["id"].replace("_", " ")
        for call in calls:
            base["subtitles.%s.voice.%s.%s" % (NS, row["id"], call)] = "%s %s" % (who[:1].upper() + who[1:], said[call])
    for row in hums:
        base["subtitles.%s.hum.%s" % (NS, row["id"])] = "Machine hums (%s)" % row["id"]
    for row in clicks:
        base["subtitles.%s.click.%s" % (NS, row["id"])] = {"station": "Station opens", "snap": "Plate snaps on",
                                                          "switch": "Switch clicks"}[row["id"]]
    lang_dir = ASSETS / "lang"
    lang_dir.mkdir(parents=True, exist_ok=True)
    with open(lang_dir / "en_us.json", "w", encoding="utf-8", newline="\n") as handle:
        json.dump(dict(sorted(base.items())), handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print("elysium-lib sounds: %d voice(s) x %d, %d hum(s), %d click(s): %d clip(s), %d event(s)"
          % (len(voices), len(calls), len(hums), len(clicks), written, len(entries)))


if __name__ == "__main__":
    main()
