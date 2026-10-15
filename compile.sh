#!/usr/bin/env bash
#
# Type-check every module against real NeoForge, without Gradle.
#
# Why this exists
# ---------------
# Gradle cannot reach the NeoForge repositories from this environment, so for a
# long time the only verification available was `javac` against an empty
# classpath with "cannot find symbol" filtered out of the output. That catches
# syntax errors and nothing else, and it has a specific blind spot that is
# worth naming: *any* method call on an unresolvable type type-checks. It found
# nothing wrong with `SoundEvents.VILLAGER_YES.value()`, which does not compile,
# because it could not tell that VILLAGER_YES is a SoundEvent rather than a
# Holder.
#
# A real classpath removes the blind spot entirely. The jars come from the
# user's own CurseForge install plus the deobfuscated client, which is the same
# set Gradle would assemble.
#
#   ./compile.sh            type-check everything, in dependency order
#   ./compile.sh --jars      also package each module, then merge them into one
#   ./compile.sh --bundle    as --jars, and put Curios inside the merged jar so
#                            that one download works with nothing else installed
#
# Exits non-zero if any module fails. With --jars the six module jars and the
# merged jar land in dist/, which is the same directory the Gradle build uses —
# so `./compile.sh --jars` here and `./gradlew checkAll` on a machine that can
# reach the NeoForge repositories produce the same set of files.
#
# The packaging is not a substitute for the Gradle build: there is no
# reobfuscation, no dependency resolution and no data generation. It packages
# what is already there, which is all the merged jar needs.

set -u

MC="${ELYSIUM_MC_LIBS:-/tmp/mc}"
ANNOTATIONS="${ELYSIUM_ANNOTATIONS:-/opt/gradle-8.14.3/lib/annotations-24.0.1.jar}"
OUT="${ELYSIUM_OUT:-/tmp/elysium-classes}"
DIST="${ELYSIUM_DIST:-dist}"

JARS=0
BUNDLE=0
for argument in "$@"; do
    case "$argument" in
        --jars)   JARS=1 ;;
        # Opt-in, and deliberately not the default. Bundling Curios makes one
        # jar that works on its own, which is what somebody downloading Elysium
        # for the first time wants; it also means every dev build carries a
        # second copy of a mod the dev instance already has installed. The
        # release build passes it, the working loop does not.
        --bundle) BUNDLE=1; JARS=1 ;;
        *) echo "unknown option: $argument"; exit 2 ;;
    esac
done

if [ ! -f "$MC/cp.txt" ]; then
    echo "no Minecraft classpath at $MC/cp.txt — set ELYSIUM_MC_LIBS to a"
    echo "directory holding cp.txt and the jars it names, or use Gradle."
    exit 2
fi

CP="$(cat "$MC/cp.txt")"
[ -f "$ANNOTATIONS" ] && CP="$CP:$ANNOTATIONS"

# ---------------------------------------------------------------------------
# The optional mods Elysium converts gear to and from
# ---------------------------------------------------------------------------
#
# Silent Gear and Apotheosis are compile-only. Their classes are on the
# classpath here so com/elysium/core/compat/ can be written against the real
# API and checked by javac; at runtime nothing in that package is ever loaded
# unless ModList says the mod is present, so the shipped jar runs with neither.
#
# This build FAILS rather than skipping when they are absent, and that is
# deliberate. Quietly dropping a package produces a jar that is missing a
# feature, with nothing in the output saying so — and the last person to notice
# is the one who installs it. An earlier version of this repository had exactly
# that failure in the other direction: gear code that imported
# shadows.apotheosis.* and could not build at all.
COMPAT="${ELYSIUM_COMPAT_LIBS:-/tmp/mods}"
COMPAT_JARS=""
missing_compat=""
# JEI is on the list because elysium-lib's compat/jei plugin is written against
# its API; at runtime JEI's own annotation scan is the only thing that loads the
# class, so the pack runs without JEI (docs/POLISH.md P6).
# Curios is on the list because elysium-trinkets imports its API outright (a
# hard dependency, bundled below), not behind a ModList check; before it was
# named here the type-check only passed when the Minecraft classpath happened
# to carry an installed copy, and failed the day cp.txt was rebuilt without it.
for glob in "jei-*.jar" "silent-gear-*.jar" "silent-lib-*.jar" "Apotheosis-*.jar" "Placebo-*.jar" "minecolonies-*.jar" "structurize-*.jar" "blockui-*.jar" "domum-ornamentum-*.jar" "curios-neoforge-*.jar"; do
    found="$(ls "$COMPAT"/$glob 2>/dev/null | head -1)"
    if [ -n "$found" ]; then
        COMPAT_JARS="$COMPAT_JARS:$found"
    else
        missing_compat="$missing_compat $glob"
    fi
done

if [ -n "$missing_compat" ] && ls elysium-core/src/main/java/com/elysium/core/compat/*.java >/dev/null 2>&1; then
    echo "compat sources are present and these jars are not, in $COMPAT:"
    for glob in $missing_compat; do echo "    $glob"; done
    echo
    echo "com/elysium/core/compat/ is compiled against Silent Gear and Apotheosis."
    echo "Neither is needed to *run* Elysium — every entry point is behind a"
    echo "ModList check — but both are needed to type-check the bridge."
    echo "Point ELYSIUM_COMPAT_LIBS at a directory holding them."
    exit 2
fi
CP="$CP$COMPAT_JARS"

# Dependency order. Each module is added to the classpath for the ones after
# it, which is the same graph settings.gradle declares — so a dependency
# pointing the wrong way fails here rather than at build time.
MODULES="elysium-lib elysium-core elysium-dungeons elysium-mobs elysium-npcs elysium-trinkets elysium-solarpunk elysium-armory elysium-ascendancy elysium-enchanting elysium-spawners elysium-space elysium-ores elysium-flora elysium-kinetics elysium-industry elysium-atomics elysium-logistics elysium-agronomy elysium-foundry elysium-almanac elysium-transit elysium-arcana elysium-comforts elysium-atelier elysium-arsenal elysium-bestiary elysium-wilds elysium-colonies"

# Empty the output folder; never remove it. On Windows /tmp/elysium-classes is
# a junction into C:/Users/weaks/Elysium-env (tools/setup_env.py), and rm -rf
# on a junction removes the link, so the mkdir below would put the classes
# back in %TEMP%. -H lets find start inside a linked $OUT.
mkdir -p "$OUT" || exit 2
find -H "$OUT" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
failed=0

for module in $MODULES; do
    if [ ! -d "$module/src/main/java" ]; then
        echo "skip  $module (no sources)"
        continue
    fi
    mkdir -p "$OUT/$module"
    find "$module/src/main/java" -name '*.java' > "$OUT/$module.srcs"
    count=$(wc -l < "$OUT/$module.srcs")

    javac -nowarn -proc:none -d "$OUT/$module" -cp "$CP" \
          @"$OUT/$module.srcs" 2> "$OUT/$module.log"
    status=$?
    grep -v 'JAVA_TOOL_OPTIONS\|^Note:' "$OUT/$module.log"

    if [ "$status" -eq 0 ]; then
        echo "ok    $module ($count files)"
    else
        echo "FAIL  $module"
        failed=1
    fi

    CP="$CP:$OUT/$module"
done

echo
if [ "$failed" -ne 0 ]; then
    echo "compile FAILED"
    exit 1
fi
echo "all modules compile against NeoForge"

[ "$JARS" -eq 0 ] && exit 0

# --- packaging ------------------------------------------------------------
#
# The library is packaged and listed first, and the merge preserves that order.
# NeoForge does not need it — load order comes from the dependency stanzas in
# the merged toml, not from position in the file — but a reader opening the jar
# should meet the engine before the five things built on it.
echo
echo "packaging:"
mkdir -p "$DIST"
# Modules that are add-ons to a mod this repository does not own. They are
# built and shipped, and they are NOT merged into elysium-all: their host is a
# required dependency, and folding one into the combined jar would mean the
# whole of Elysium refuses to load for anybody who has not installed that host.
# An add-on ships beside the thing it adds to.
STANDALONE="elysium-colonies"

MERGE_INPUTS=""
for module in $MODULES; do
    id=$(grep '^mod_id=' "$module/gradle.properties" | cut -d= -f2)
    version=$(grep '^mod_version=' "$module/gradle.properties" | cut -d= -f2)
    jar="$DIST/$id-$version.jar"
    python3 tools/packjar.py "$module" "$OUT/$module" "$jar" || exit 1
    case " $STANDALONE " in
        *" $module "*)
            echo "  (standalone: $id is an add-on and is not merged)" ;;
        *)
            MERGE_INPUTS="$MERGE_INPUTS $jar" ;;
    esac
done

echo
MERGED="$DIST/elysium-all-$(grep '^mod_version=' \
    elysium-lib/gradle.properties | cut -d= -f2).jar"
python3 tools/mergejar.py "$MERGED" $MERGE_INPUTS || exit 1

# The only check in this repo that looks at the product rather than the source.
# Everything the mod ships for a player to use without installing anything else
# — the skill tree above all — has to actually be inside the jar, and that is a
# claim about packaging that no source validator can make.
echo
python3 tools/checkship.py "$MERGED" "$DIST/elysium-$(grep '^mod_version=' \
    elysium-core/gradle.properties | cut -d= -f2).jar" || exit 1

# --- the dependencies that travel with us ---------------------------------
#
# elysium-trinkets requires Curios, so a player who downloads one jar and drops
# it in their mods folder gets a load error rather than a game. NeoForge's
# answer is jar-in-jar, and the rule it follows is the one that makes this
# safe: an installed copy of the same mod wins over an embedded one, so
# bundling can add a dependency and can never override somebody's own.
if [ "$BUNDLE" -eq 1 ]; then
    BUNDLED=""
    for glob in "curios-neoforge-*.jar"; do
        found="$(ls "$COMPAT"/$glob 2>/dev/null | head -1)"
        if [ -z "$found" ]; then
            echo "--bundle was asked for and $glob is not in $COMPAT."
            echo "Bundling silently without it would produce a jar that claims to"
            echo "stand alone and does not, which is worse than not bundling."
            exit 2
        fi
        BUNDLED="$BUNDLED $found"
    done
    echo
    python3 tools/jarjar.py "$MERGED" $BUNDLED || exit 1
fi

# Always, bundle or no bundle: the check that nothing is carried unindexed is
# as important as the check that what is indexed is there.
echo
python3 tools/checkbundle.py "$MERGED" || exit 1
