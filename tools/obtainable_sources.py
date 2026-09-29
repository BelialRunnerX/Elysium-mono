"""What only code hands out, declared: the sources check_obtainable.py cannot read
off a recipe or a loot table.

Seven files under tools/obtainable/, one per group of modules, each with a
`declare(declared)` that names what a piece of Java hands out, what it needs
first, the file and the exact words in it that do the handing out, and why.
The checker refuses a declaration whose words are gone from the file, so a
source that the code stops providing is a check that fails, not a claim that
lingers. Written 29 September 2026 by the groups that walked the pack for
"all content should be available and accessible via survival".
"""
import importlib.util
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GROUPS = ("agronomy", "combat", "core", "crafts", "homestead", "space", "tech")


def declare(declared):
    os.environ.setdefault("ELYSIUM_MONO", ROOT)
    for group in GROUPS:
        path = os.path.join(HERE, "obtainable", group + ".py")
        spec = importlib.util.spec_from_file_location("obtainable_" + group, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.declare(declared)
