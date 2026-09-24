#!/usr/bin/env python3
"""Port Stellaris's skies (its Sky Aesthetics files) into our own sky format.

Stellaris drew its skies with the Sky Aesthetics library; we draw them with our
own DimensionSpecialEffects (client/sky), from assets/elysiumspace/skies/<world>.json.
The fields are Sky Aesthetics's, flattened; texture ids are renamed into our
namespace and the planet art is copied to textures/environment/. Re-runnable.

Credit: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0. See CREDITS.md.
"""
import json
import os
import shutil

STELLARIS = os.environ.get("STELLARIS", "/tmp/ref/stellaris")
SRC = os.path.join(STELLARIS, "assets/stellaris/sky_aesthetics")
TEX = os.path.join(STELLARIS, "assets/stellaris/textures/environment")
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(HERE, "src/main/resources/assets/elysiumspace")
NS = "elysiumspace"
WORLDS = {"earth_orbit": "high_orbit", "moon": "pale_moon", "mars": "frostfall", "venus": "cinderveil"}
# Planet art under our names: the Overworld is what the moon and orbit look down on.
PLANETS = {"earth": "overworld", "moon": "pale_moon", "mars": "frostfall", "venus": "cinderveil"}
STAR_CAP = 40000       # Sky Aesthetics drew 100 000; a fraction looks the same at a far lower cost


def texture(tid):
    ns, path = tid.split(":", 1)
    if ns == "minecraft":
        return tid
    name = os.path.splitext(os.path.basename(path))[0]
    if "/solar_system/" in path and name in PLANETS:
        return f"{NS}:textures/environment/{PLANETS[name]}.png"
    raise SystemExit(f"unmapped sky texture {tid}")


def port(src):
    stars = src["stars"]
    out = {
        "sun": {"texture": texture(src["sun"]["texture"]), "size": src["sun"]["size"], "height": src["sun"]["height"]},
        "stars": {"count": 1500 if stars["vanilla"] else min(stars["count"], STAR_CAP),
                  "scale": 0.1 if stars["vanilla"] else stars["scale"], "color": stars["color"],
                  "always": stars["all_days_visible"], "moving": stars["moving_stars"] or stars["vanilla"]},
        "objects": [{"texture": texture(o["texture"]), "size": o["size"], "height": o["height"],
                     "rotation": o["rotation"], "static": o["rotation_type"] == "STATIC"}
                    for o in src.get("sky_objects", [])],
        "sunset": src["sky_color"].get("sunset_alpha_modifier", 1),
        "clouds": src["cloud_settings"]["cloud"],
        "cloudHeight": src["cloud_settings"]["cloud_height"],
        "weather": src["weather"],
        "brightLightmap": src.get("light_settings", {}).get("forceBrightLightmap", False),
        "ambientLight": src.get("light_settings", {}).get("constantAmbientLight", False),
    }
    if "moon" in src:
        m = src["moon"]
        out["moon"] = {"texture": texture(m["texture"]), "size": m["size"], "height": m["height"],
                       "phases": m["moon_phase"]}
    return out


def main():
    os.makedirs(os.path.join(ASSETS, "skies"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "textures/environment"), exist_ok=True)
    from reskin import reskin_file
    for theirs, ours in PLANETS.items():
        src = os.path.join(TEX, "solar_system", theirs + ".png")
        dst = os.path.join(ASSETS, "textures/environment", ours + ".png")
        if ours == "overworld":
            shutil.copyfile(src, dst)          # the Overworld is ours already: Earth stays Earth
        else:
            reskin_file(src, dst, ours)        # each world seen from afar in its own palette (S8)
    for theirs, ours in WORLDS.items():
        sky = port(json.load(open(os.path.join(SRC, theirs + ".json"))))
        json.dump(sky, open(os.path.join(ASSETS, "skies", ours + ".json"), "w"), indent=2)
        print(f"{ours:12s} sun {sky['sun']['size']:.0f}, {sky['stars']['count']} stars, "
              f"{len(sky['objects'])} objects, moon {'moon' in sky}, clouds {sky['clouds']}")


if __name__ == "__main__":
    main()
