#!/usr/bin/env bash
#
# Watch every mob family walk. One command, from the monorepo root:
#
#     elysium-mobs/tools/walk.sh [limbSwingAmount]
#
# Captures each model through the real Minecraft pipeline (PoseCapture.java),
# renders it (preview.py), and leaves strips and GIFs in elysium-mobs/tools/preview/.
#
# Nothing here reimplements the model or the animation: the generated
# ModelXxx.java is compiled, baked with Minecraft's own LayerDefinition, posed
# with its own setupAnim, and rendered through its own renderToBuffer into a
# VertexConsumer that writes vertices down instead of drawing them. A previewer
# that can disagree with the shipped code is worth less than nothing, because
# it is believed. See README-preview.md.
#
# limbSwingAmount defaults to 0.65, which is roughly a walk. 1.0 is a sprint
# and makes every gait look like a caricature; 0.3 is a shuffle.
set -euo pipefail
cd "$(dirname "$0")/../.."

AMOUNT="${1:-0.65}"
MC="${ELYSIUM_MC_LIBS:-/tmp/mc}"
CLASSES="${ELYSIUM_CLASSES:-/tmp/elysium-classes}"
OUT="elysium-mobs/tools/preview"
TEX="elysium-mobs/src/main/resources/assets/elysiummobs/textures/entity"

if [ ! -f "$MC/cp.txt" ]; then
    echo "no Minecraft classpath at $MC/cp.txt — run ./compile.sh first" >&2
    exit 1
fi
if [ ! -d "$CLASSES/elysium-mobs" ]; then
    echo "no compiled mob classes at $CLASSES/elysium-mobs — run ./compile.sh first" >&2
    exit 1
fi

CP="$(cat "$MC/cp.txt"):$CLASSES/elysium-lib:$CLASSES/elysium-mobs:$OUT/classes"
mkdir -p "$OUT/classes" "$OUT/capture"
javac -d "$OUT/classes" -nowarn -proc:none \
    -cp "$(cat "$MC/cp.txt"):$CLASSES/elysium-lib:$CLASSES/elysium-mobs" \
    elysium-mobs/tools/PoseCapture.java 2>&1 | grep -v "^Picked up" || true

# family:texture — the texture is one variant per family; they share geometry,
# so any of them shows the gait.
for pair in Adept:adept/acolyte Choir:choir/choir Drone:drone/interdictor \
            Lictor:lictor/aegis Praetor:praetor/praetor Reaver:reaver/chainbound \
            Scavenger:scavenger/blightfed Whisper:whisper/ashling \
            Harrier:harrier/flenser Bulwark:bulwark/shieldwall; do
    model="${pair%%:*}"
    texture="${pair##*:}"
    java -cp "$CP" PoseCapture "com.elysium.mobs.client.model.${model}Model" \
        16 "$OUT/capture/$model.json" --amount "$AMOUNT" 2>/dev/null
    python3 elysium-mobs/tools/preview.py "$OUT/capture/$model.json" \
        "$TEX/$texture.png" "$OUT" --scale 160 > /dev/null
done

python3 elysium-mobs/tools/sheet.py "$OUT"
echo "previews in $OUT (amount=$AMOUNT)"
