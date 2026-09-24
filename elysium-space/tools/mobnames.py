"""Stellaris's mobs under our names: one table for every port tool.

Credit: Stellaris, (c) Exodus Studio, CC BY-NC-SA 4.0. See CREDITS.md.
"""
NS = "elysiumspace"
NAMES = {"alien": "moonfolk", "alien_zombie": "hollow_moonfolk", "star_crawler": "void_crawler",
         "martian_raptor": "frost_raptor", "mogler": "cinder_boar", "pygro": "cinderling",
         "pygro_brute": "cinderling_brute", "ice_spit": "rime_spit"}


def mob(stellaris_id):
    """stellaris:alien -> elysiumspace:moonfolk; None for anything that is not a ported mob."""
    body = str(stellaris_id).split(":", 1)[-1]
    if not str(stellaris_id).startswith("stellaris:") or body not in NAMES:
        return None
    return f"{NS}:{NAMES[body]}"
