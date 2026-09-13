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
#
# Exits non-zero on the first module that fails. Output classes are throwaway;
# this proves the source compiles, it does not build a mod.

set -u

MC="${ELYSIUM_MC_LIBS:-/tmp/mc}"
ANNOTATIONS="${ELYSIUM_ANNOTATIONS:-/opt/gradle-8.14.3/lib/annotations-24.0.1.jar}"
OUT="${ELYSIUM_OUT:-/tmp/elysium-classes}"

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
