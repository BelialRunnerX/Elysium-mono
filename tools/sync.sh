#!/bin/bash
# After a commit on main: bring the copies of the tree up to date.
#
#     bash tools/sync.sh
#
# 1. Fast-forwards the mirror at D:/Elysium-mono (branch main) to this
#    repository's main. D: is a removable drive: when it is not plugged in,
#    the D: steps are skipped and said so.
# 2. Refreshes the handbacks, C:/testingbuilds/Elysium/handback (under
#    ELYSIUM_TEST_ROOT) and D:/testingbuilds/Elysium/handback: a bundle, a
#    patch and the commit list of everything since the agents' base bcdc0c70,
#    and the two shipped jars from dist/. New screenshots under C: are copied
#    to D: as well.
# 3. Pushes main to the remote "github" (the private BelialRunnerX/Elysium-mono).
#
# Nothing is forced: a mirror that has moved on its own refuses the
# fast-forward, and the push refuses a non-fast-forward, and both say so.
#
# It came back from the session transcript on 14 October 2026: it was the
# scratchpad's obtain/sync_mirrors.sh (written 29 September 15:55 UTC), lost
# when Windows emptied %TEMP% on 11 October. That one synced branch
# integration to E: and D:; E: is lost, the branch is main, and the push is new.
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 1
TEST_ROOT="${ELYSIUM_TEST_ROOT:-C:/testingbuilds}"
TEST_ROOT="${TEST_ROOT%/}"
BRANCH=main
BASE=bcdc0c70
NAME="agent1-$BRANCH"
MIRROR=/d/Elysium-mono
D_HANDBACK=/d/testingbuilds/Elysium/handback
C_HANDBACK="$TEST_ROOT/Elysium/handback"
g() { git -c safe.directory='*' "$@"; }

[ "$(g symbolic-ref --short -q HEAD)" = "$BRANCH" ] || echo "note: the checkout is not on $BRANCH; syncing $BRANCH as committed"
g log --oneline -1 "$BRANCH"

# 1. the D: mirror
if [ -d "$MIRROR" ] && g -C "$MIRROR" rev-parse --git-dir >/dev/null 2>&1; then
  on=$(g -C "$MIRROR" symbolic-ref --short -q HEAD)
  if [ "$on" = "$BRANCH" ]; then
    g -C "$MIRROR" fetch -q "$ROOT" "$BRANCH" \
      && g -C "$MIRROR" merge -q --ff-only FETCH_HEAD 2>&1 | grep -v 'Updating files' | tail -1
  else
    # main is not checked out there, so the ref can be fast-forwarded in place
    g -C "$MIRROR" fetch -q "$ROOT" "$BRANCH:$BRANCH"
  fi
  echo "$MIRROR: $(g -C "$MIRROR" log --oneline -1 "$BRANCH" | cut -c1-70)"
else
  echo "$MIRROR: not there (D: not plugged in?), skipped"
fi

# 2. the handbacks
handbacks="$C_HANDBACK"
if [ -d /d/testingbuilds/Elysium ]; then
  handbacks="$handbacks $D_HANDBACK"
else
  echo "$D_HANDBACK: not there, skipped"
fi
for h in $handbacks; do
  mkdir -p "$h/screenshots"
  g bundle create -q "$h/$NAME.bundle" "$BASE..$BRANCH"
  g log --oneline "$BASE..$BRANCH" > "$h/$NAME.commits.txt"
  g format-patch -q --stdout "$BASE..$BRANCH" > "$h/$NAME.patch"
  for j in elysium-all-1.0.0.jar elysiumcolonies-1.0.0.jar; do
    if [ -f "dist/$j" ]; then cp "dist/$j" "$h/"; else echo "$h: dist/$j missing, not copied"; fi
  done
  jar_time=$( [ -f "$h/elysium-all-1.0.0.jar" ] && ls -la --time-style=+%H:%M "$h/elysium-all-1.0.0.jar" | awk '{print $6}' )
  echo "$h: $(wc -l < "$h/$NAME.commits.txt") commits, jars ${jar_time:-none}"
done
if [ -d "$C_HANDBACK/screenshots" ] && [ -d "$D_HANDBACK/screenshots" ]; then
  cp -ru "$C_HANDBACK/screenshots/." "$D_HANDBACK/screenshots/"
fi

# 3. GitHub
if g push -q github "$BRANCH"; then
  echo "github: $BRANCH pushed ($(g rev-parse --short "$BRANCH"))"
else
  echo "github: the push of $BRANCH failed (see above)" >&2
fi

echo "mirror and handbacks refreshed"; g status --short | wc -l
