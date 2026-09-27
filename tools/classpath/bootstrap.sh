#!/usr/bin/env bash
# Build the NeoForge classpath compile.sh needs, from nothing.
#
# Why this exists: the working container is disposable, and when it is
# disposed the classpath goes with it. Rebuilding it from memory took an hour
# and three wrong turns, the last of which -- access transformers -- produces
# a classpath that compiles twenty-eight modules out of twenty-nine and looks
# like a bug in the twenty-ninth.
#
#     bash tools/classpath/bootstrap.sh [<neoforge version>] [<minecraft>]
#
# It needs: the two launcher manifests (fetched below), the patched Minecraft
# classes, and the mods elysium-core and elysium-trinkets are written against.
# The last two cannot be fetched from a public maven and have to come off a
# machine with the pack installed -- the script says so and names them.
set -uo pipefail
NEO="${1:-21.1.248}"
MC_VERSION="${2:-1.21.1}"
MC="${ELYSIUM_MC_LIBS:-/tmp/mc}"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$MC/libs" "$MC/at"

fetch() {  # url path
    [ -s "$MC/libs/$2" ] && return 0
    mkdir -p "$MC/libs/$(dirname "$2")"
    timeout 60 curl -sSfL -o "$MC/libs/$2" "$1" || { echo "could not fetch $1"; return 1; }
}

# --- 1. the two manifests, and every library they name -------------------
for pair in "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar installer.jar"; do
    set -- $pair
    fetch "$1" "$2" || true
done

python3 - "$MC" <<'PY' || exit 2
import json, os, sys, urllib.request, zipfile
mc = sys.argv[1]
# The NeoForge installer carries version.json, which names NeoForge's own
# libraries and inherits the rest from the vanilla manifest.
here = os.path.join(mc, "libs", "installer.jar")
if not os.path.isfile(here):
    print("no installer jar; cannot list the libraries")
    raise SystemExit(2)
with zipfile.ZipFile(here) as jar:
    version = json.loads(jar.read("version.json"))
lines = []
for one in version.get("libraries", []):
    art = (one.get("downloads") or {}).get("artifact") or {}
    if art.get("url") and art.get("path", "").endswith(".jar"):
        lines.append((art["url"], art["path"]))
# And the vanilla side.
manifest = json.load(urllib.request.urlopen(
    "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"))
want = version.get("inheritsFrom")
for entry in manifest["versions"]:
    if entry["id"] == want:
        vanilla = json.load(urllib.request.urlopen(entry["url"]))
        for one in vanilla.get("libraries", []):
            art = (one.get("downloads") or {}).get("artifact") or {}
            path = art.get("path", "")
            if art.get("url") and path.endswith(".jar") and "natives" not in path:
                lines.append((art["url"], path))
        break
with open(os.path.join(mc, "urls.txt"), "w") as out:
    for url, path in lines:
        out.write(url + " " + path + "\n")
print("  %d library(ies) to fetch" % len(lines))
PY

while read -r url path; do fetch "$url" "$path"; done < "$MC/urls.txt"

# --- 2. the patched Minecraft, which is not on any public maven ----------
#
# The installer produces it locally from vanilla plus NeoForge's binary
# patches. A machine with the pack installed already has the result, under
# <minecraft>/Install/libraries, and copying it is far cheaper than running
# the installer here.
NEEDED="libs/mc-srg.jar libs/neoforge-universal.jar libs/neoforge-client.jar"
missing=""
for one in $NEEDED; do [ -s "$MC/$one" ] || missing="$missing $one"; done
if [ -n "$missing" ]; then
    cat <<MSG
these have to be copied from a machine with the pack installed:
$missing
they live under <curseforge>/minecraft/Install/libraries as
    net/minecraft/client/$MC_VERSION-*/client-$MC_VERSION-*-srg.jar   -> libs/mc-srg.jar
    net/neoforged/neoforge/$NEO/neoforge-$NEO-universal.jar           -> libs/neoforge-universal.jar
    net/neoforged/neoforge/$NEO/neoforge-$NEO-client.jar              -> libs/neoforge-client.jar
MSG
    exit 2
fi

# --- 3. widen what NeoForge widens ---------------------------------------
#
# A dev environment's Minecraft is not vanilla Minecraft. Skipping this leaves
# a classpath that compiles most of the tree and reports, in the rest, errors
# about private access that no mod author has ever seen.
unzip -o -q "$MC/libs/neoforge-universal.jar" "META-INF/accesstransformer.cfg" -d "$MC/at"
ASM="$(find "$MC/libs/org/ow2/asm" -name 'asm-9*.jar' | head -1)"
javac -nowarn -cp "$ASM" -d "$MC/at" "$HERE/ApplyAT.java" || exit 2
java -cp "$ASM:$MC/at" ApplyAT "$MC/libs/mc-srg.jar" "$MC/libs/mc-at.jar" \
     "$MC/at/META-INF/accesstransformer.cfg" \
     "$(cd "$HERE/../.." && pwd)/elysium-lib/src/main/resources/META-INF/accesstransformer.cfg" || exit 2

# --- 4. and write the classpath ------------------------------------------
#
# Vanilla's resources first, NeoForge's patched classes next, vanilla's
# classes last. The order matters twice and in opposite directions: NeoForge's
# classes have to win over vanilla's, and vanilla's *data* has to win over
# NeoForge's, because several validators read the game's own recipes off the
# first jar on this path that carries any and NeoForge's carries a hundred and
# ninety of its own. Get that second one wrong and two modules report that
# every stone in the game has stopped being smeltable.
{
    printf '%s' "$MC/libs/mc-extra.jar:$MC/libs/neoforge-client.jar:$MC/libs/neoforge-universal.jar:$MC/libs/mc-at.jar"
    find "$MC/libs" -name '*.jar' \
        ! -name 'mc-*' ! -name 'neoforge-client.jar' ! -name 'neoforge-universal.jar' \
        ! -name 'installer.jar' ! -name 'mapped-at*' | sort | sed 's/^/:/' | tr -d '\n'
} > "$MC/cp.txt"

# --- 5. and one jar with both halves of the game in it -------------------
#
# Several generators read the game's own block textures rather than inventing
# them, and they look for one jar holding the widened classes and the
# resources together. Without it they exit with a sentence, and the module
# reports only that its generator will not run.
python3 - "$MC" <<'MERGE' || exit 2
import sys, zipfile
mc = sys.argv[1]
seen = set()
with zipfile.ZipFile(mc + "/mapped-at.jar", "w", zipfile.ZIP_DEFLATED) as out:
    for source in (mc + "/libs/mc-at.jar", mc + "/libs/mc-extra.jar"):
        with zipfile.ZipFile(source) as jar:
            for entry in jar.infolist():
                if entry.is_dir() or entry.filename in seen:
                    continue
                seen.add(entry.filename)
                out.writestr(entry.filename, jar.read(entry.filename))
print("  mapped-at.jar: %d entry(ies)" % len(seen))
MERGE

# --- 6. the game's own resources, under the name the validators look for ----
#
# Four module validators check names against the game's own language file --
# every dye, every mob, every item -- and each of them looks for
# $MC/vanilla.jar. Without it they return an empty set and skip, silently: the
# summary line still says the module passed. Two negative tests exist purely to
# catch that, and they are the only reason it was noticed.
cp "$MC/libs/mc-extra.jar" "$MC/vanilla.jar"

# And an unsigned copy of the merged jar. The signed manifest travels with the
# remapped classes, so a plain JVM refuses them with "SHA-384 digest error"
# where the game's own launcher never looks.
python3 - "$MC" <<'UNSIGN' || exit 2
import re, sys, zipfile
mc = sys.argv[1]
with zipfile.ZipFile(mc + "/mapped-at.jar") as src, \
     zipfile.ZipFile(mc + "/mapped-at-unsigned.jar", "w", zipfile.ZIP_DEFLATED) as out:
    for entry in src.infolist():
        if re.match(r"META-INF/.*\.(SF|RSA|DSA|EC)$", entry.filename, re.I):
            continue
        body = src.read(entry.filename)
        if entry.filename.upper() == "META-INF/MANIFEST.MF":
            body = b"\n".join(line for line in body.split(b"\n")
                              if not line.lower().startswith(b"name:")
                              and b"-digest" not in line.lower())
        out.writestr(entry.filename, body)
print("  mapped-at-unsigned.jar written")
UNSIGN

# The load test looks for asm and log4j by their bare names inside libs/ --
# not beside cp.txt -- and skips entirely without log4j, saying so in one line
# that is easy to read past. The cp.txt sweep above excludes mapped-at* so that
# the unsigned copy landing here does not put a second Minecraft on the path.
for one in asm asm-analysis asm-commons asm-tree asm-util; do
    found="$(find "$MC/libs/org/ow2/asm" -name "$one-9*.jar" | head -1)"
    [ -n "$found" ] && cp "$found" "$MC/libs/$(basename "$found")"
done
found="$(find "$MC/libs" -name 'commons-io-2*.jar' | head -1)"
[ -n "$found" ] && cp "$found" "$MC/libs/commons-io-2.15.1.jar"
cp "$MC/mapped-at-unsigned.jar" "$MC/libs/mapped-at-unsigned.jar"
for one in api core slf4j2-impl; do
    name="log4j-$one-2.19.0.jar"
    [ -s "$MC/libs/$name" ] && continue
    timeout 60 curl -sSfL -o "$MC/libs/$name" \
        "https://maven.neoforged.net/releases/org/apache/logging/log4j/log4j-$one/2.19.0/$name" \
        2>/dev/null || timeout 60 curl -sSfL -o "$MC/libs/$name" \
        "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-$one/2.19.0/$name" \
        2>/dev/null || echo "  could not fetch $name (Maven Central rate-limits; retry)"
done

echo "wrote $MC/cp.txt: $(tr ':' '\n' < "$MC/cp.txt" | wc -l) entry(ies)"
echo
echo "elysium-trinkets needs Curios on this classpath and elysium-core needs"
echo "Silent Gear, Apotheosis and the colony mods in \$ELYSIUM_COMPAT_LIBS."
echo "Both come off the same installed pack; see BUILDING.md."
