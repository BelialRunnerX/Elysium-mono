#!/usr/bin/env python3
"""
Validate Elysium Ascendancy against its sources.

Run from the repo root:   python3 elysium-ascendancy/validate.py

Two things matter most for this module, and they are checked first:

  1. INDEPENDENCE. It is Elysium's own replacement for Apotheosis. Nothing in
     it may reach for that mod — no import, no mod id, no dependency, not even
     an optional one — and it depends on elysium-lib and nothing else from
     Elysium. The owner's instruction: minimise dependence on those mods.
  2. PURITY. The engine (the weighting engine) is compiled against the JDK alone, with no
     Minecraft on the classpath, and its harness is run. If the engine ever
     imports a game class, this fails at the compile step rather than
     quietly dropping the harness.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

WORK = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(WORK, "src/main/resources")
SRC = os.path.join(WORK, "src/main/java/com/elysium/ascendancy")
PURE = os.path.join(SRC, "weight")
HARNESS = os.path.join(WORK, "tools/WeightHarness.java")
HARNESS_MAIN = "com.elysium.ascendancy.weight.WeightHarness"

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text, keep_strings=False):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    if keep_strings:
        return text
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', text)


if not os.path.isdir(SRC):
    print("This is not the elysium-ascendancy tree.")
    sys.exit(1)

# ---------------------------------------------------------------------------
# 1. Independence
# ---------------------------------------------------------------------------
FOREIGN = re.compile(r"(apotheosis|apothic|shadowsoffire|placebo|silentgear|silent_gear|"
                     r"silentchaos|silentlib)", re.I)
java_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".java"):
            java_files.append(os.path.join(dirpath, name))
for path in java_files:
    raw = open(path, encoding="utf-8").read()
    code = strip_comments(raw)
    rel = os.path.relpath(path, WORK)
    # Searched with string literals kept: a soft dependency is written as
    # ModList.get().isLoaded("silentgear"), and that name is only ever in a string.
    hit = FOREIGN.search(strip_comments(raw, keep_strings=True))
    if hit:
        fail(f"{rel} refers to {hit.group(0)!r} in code. This module replaces that "
             f"mod; it may not depend on it, even softly")
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", raw):
        if imp.startswith("com.elysium.") and not (
                imp.startswith("com.elysium.lib.") or imp.startswith("com.elysium.ascendancy.")):
            fail(f"{rel} imports {imp}. This module depends on elysium-lib and "
                 f"nothing else from Elysium")
toml_path = os.path.join(RES, "META-INF/neoforge.mods.toml")
toml = open(toml_path, encoding="utf-8").read() if os.path.isfile(toml_path) else ""
toml_code = "\n".join(l for l in toml.splitlines() if not l.strip().startswith("#"))
deps = re.findall(r'modId\s*=\s*"([^"]+)"', toml_code)
extra = [d for d in deps if d not in ("${mod_id}", "neoforge", "minecraft", "elysiumlib")]
if not toml:
    fail("META-INF/neoforge.mods.toml is missing")
elif extra:
    fail(f"mods.toml declares a dependency on {extra}. The module needs the "
         f"library and nothing else")
elif "elysiumlib" not in deps:
    fail("mods.toml does not require elysiumlib, which this module is built on")
print(f"independence       : {len(java_files)} source file(s), no reference to "
      f"Apotheosis, depends on the library only")

# ---------------------------------------------------------------------------
# 2. Purity: the engine compiles on the JDK alone, and its harness runs
# ---------------------------------------------------------------------------
pure_files = []
for dirpath, _, filenames in os.walk(PURE):
    for name in filenames:
        if name.endswith(".java"):
            pure_files.append(os.path.join(dirpath, name))
for path in pure_files:
    for imp in re.findall(r"\bimport\s+(?:static\s+)?([\w.]+)\s*;", open(path, encoding="utf-8").read()):
        if not imp.startswith("java."):
            fail(f"{os.path.relpath(path, WORK)} imports {imp}; the engine imports "
                 f"nothing but the JDK, so a harness can run it without a game")
if not pure_files:
    fail(f"the engine directory {os.path.relpath(PURE, WORK)} is empty")
elif not os.path.isfile(HARNESS):
    fail(f"{os.path.relpath(HARNESS, WORK)} is missing; the engine is only read, never run")
else:
    with tempfile.TemporaryDirectory() as out:
        compiled = subprocess.run(["javac", "-nowarn", "-d", out] + pure_files + [HARNESS],
                                  capture_output=True, text=True)
        text = "\n".join(l for l in (compiled.stdout + compiled.stderr).splitlines()
                         if "JAVA_TOOL_OPTIONS" not in l)
        if compiled.returncode != 0:
            fail("the engine does not compile on the JDK alone:\n      "
                 + text.replace("\n", "\n      "))
        else:
            ran = subprocess.run(["java", "-cp", out, HARNESS_MAIN],
                                 capture_output=True, text=True)
            lines = [l for l in (ran.stdout + ran.stderr).splitlines()
                     if "JAVA_TOOL_OPTIONS" not in l]
            if ran.returncode != 0:
                fail("the WeightHarness failed:\n      " + "\n      ".join(lines))
            else:
                print(f"weighting engine   : " + (lines[0] if lines else "ran"))

# ---------------------------------------------------------------------------
# 3. All JSON parses
# ---------------------------------------------------------------------------
count = 0
for dirpath, _, filenames in os.walk(RES):
    for filename in filenames:
        if filename.endswith(".json"):
            count += 1
            try:
                json.load(open(os.path.join(dirpath, filename), encoding="utf-8"))
            except Exception as exc:
                fail(f"invalid JSON {os.path.join(dirpath, filename)}: {exc}")
print(f"json files parsed  : {count}")
print()

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print("all ascendancy checks passed")
