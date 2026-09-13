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
[ "${1:-}" = "--jars" ] && JARS=1

if [ ! -f "$MC/cp.txt" ]; then
    echo "no Minecraft classpath at $MC/cp.txt — set ELYSIUM_MC_LIBS to a"
    echo "directory holding cp.txt and the jars it names, or use Gradle."
    exit 2
fi

CP="$(cat "$MC/cp.txt")"
[ -f "$ANNOTATIONS" ] && CP="$CP:$ANNOTATIONS"

# Dependency order. Each module is added to the classpath for the ones after
# it, which is the same graph settings.gradle declares — so a dependency
# pointing the wrong way fails here rather than at build time.
MODULES="elysium-lib elysium-core elysium-dungeons elysium-mobs elysium-npcs elysium-trinkets"

rm -rf "$OUT"
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
MERGE_INPUTS=""
for module in $MODULES; do
    id=$(grep '^mod_id=' "$module/gradle.properties" | cut -d= -f2)
    version=$(grep '^mod_version=' "$module/gradle.properties" | cut -d= -f2)
    jar="$DIST/$id-$version.jar"
    python3 tools/packjar.py "$module" "$OUT/$module" "$jar" || exit 1
    MERGE_INPUTS="$MERGE_INPUTS $jar"
done

echo
python3 tools/mergejar.py "$DIST/elysium-all-$(grep '^mod_version=' \
    elysium-lib/gradle.properties | cut -d= -f2).jar" $MERGE_INPUTS || exit 1
