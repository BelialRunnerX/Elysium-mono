#!/usr/bin/env bash
# One interface, broken one way at a time.
#
# A theme check is the easiest kind of check to write vacuously: the tokens it
# looks for are common words, and the thing it is meant to notice — a new
# screen that does its own thing — is by definition a file that did not exist
# when the rule was written. So several of these breaks add a *new* screen
# rather than editing an old one, which is the case that matters.
set -uo pipefail
ROOT=/tmp/Elysium-mono
SNAP=/tmp/ui_snap.tar
pass=0; fail=0
(cd $ROOT && tar cf $SNAP check_ui.py check_validators.py elysium-lib/ui \
    elysium-agronomy/src/main/java/com/elysium/agronomy/block/CabinetBlock.java \
    elysium-lib/src/main/java/com/elysium/lib/client \
    elysium-space/src/main/java/com/elysium/space/client \
    elysium-core/src/main/java/com/elysium/core/screen)
restore() { (cd $ROOT && tar xf $SNAP); rm -f $ROOT/elysium-space/src/main/java/com/elysium/space/client/RogueScreen.java; }
out() { (cd $ROOT && timeout 300 python3 check_ui.py 2>&1); }
expect() { local w="$2" g; g="$(out)"
  if [[ "$g" == *"$w"* ]]; then echo "  caught : $1"; pass=$((pass+1));
  else echo "  MISSED : $1"; fail=$((fail+1)); fi; restore; }
edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    sys.stderr.write('STALE ANCHOR in ' + p + ': ' + old[:60] + '\n')
    sys.exit(2)
open(p,'w',encoding='utf-8').write(s.replace(old, new, 1))
" "$@" || { echo "STALE ANCHOR"; exit 2; }; }

SPACE=$ROOT/elysium-space/src/main/java/com/elysium/space/client
LIBC=$ROOT/elysium-lib/src/main/java/com/elysium/lib/client
ROGUE=$SPACE/RogueScreen.java

newscreen() {  # $1 = the body of renderBackground
cat > $ROGUE <<JAVA
package com.elysium.space.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RogueScreen extends Screen {

    public RogueScreen() {
        super(Component.translatable("elysiumspace.rogue"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
$1
    }
}
JAVA
}

echo "a screen that does its own thing"
newscreen '        g.fill(0, 0, width, height, 0xC0101018);'
expect "a brand new screen drawing its own chrome" "never reaches ElysiumUI"

echo "vanilla chrome coming back"
edit $SPACE/WaygateScreen.java 'import com.elysium.lib.client.ElysiumChooser;' \
  'import com.elysium.lib.client.ElysiumChooser;
import net.minecraft.client.gui.components.Button;'
edit $SPACE/WaygateScreen.java '    @Override
    protected Component status() {' '    private void old() {
        addRenderableWidget(Button.builder(Component.empty(), b -> {}).build());
    }

    @Override
    protected Component status() {'
expect "a vanilla Button back in a chooser" "a vanilla widget"

edit $SPACE/MachineScreen.java '        ElysiumUI.scrim(g, width, height);' \
  '        g.blit(null, 0, 0, 0, 0, 176, 166);
        ElysiumUI.scrim(g, width, height);'
expect "a background texture blitted behind the panel" "blits a texture"

echo "a colour nobody measured"
edit $SPACE/SuitHud.java 'int colour = low ? ElysiumPalette.BAD : ElysiumPalette.GOOD;' \
  'int colour = low ? 0xFFC8423F : 0xFF3FC87A;'
expect "hand-picked colours back in the HUD" "a colour written by hand"

edit $LIBC/ElysiumPalette.java 'public static final int ACCENT = 0xFF7F70C4;' \
  'public static final int ACCENT = 0xFF7F70C5;'
expect "the generated palette edited by hand" "different colours than the game uses"

edit $LIBC/ElysiumPalette.java 'public static final int SLOT = 0xFF07060B;' \
  'public static final int SLOT = 0xFF07060B;
    public static final int MAUVE = 0xFF404040;'
expect "a colour added straight to the Java" "by hand, so nothing has ever measured it"

edit $LIBC/ElysiumPalette.java 'public static final int UNIT = 4;' \
  'public static final int UNIT = 5;'
expect "a metric drifting from the generator" "disagrees with the generator"

edit $ROOT/elysium-lib/ui/palette.py '"TEXT_MUTED":       0xFF8F89A8,' \
  '"TEXT_MUTED":       0xFF3A3648,'
expect "muted text too dark to read on the panel" "cannot be read on a dim monitor"

echo "English typed into a screen"
edit $SPACE/FreightScreen.java 'Component.translatable("elysiumspace.freight.alone")' \
  'Component.literal("No other terminal yet")'
expect "a sentence written into the screen" "rather than into the language file"

echo "the rule that reads the code and not the name"
edit $ROOT/check_ui.py '    if not is_a_screen(name):' \
  '    if not re.search(r"\bextends\s+(?:[\w.]*\.)?\w*Screen\b", body):'
g="$(out)"
if [[ "$g" == *"16 screen"* ]]; then
  echo "  MISSED : screen detection that stops at the word 'Screen'"; fail=$((fail+1))
else
  echo "  caught : screen detection that stops at the word 'Screen' (count falls)"; pass=$((pass+1))
fi
restore

edit $ROOT/check_ui.py '    match = re.search(r"\bextends\s+(?:[\w.]*\.)?(\w+)", without_generics(header))' \
  '    match = re.search(r"\bextends\s+(?:[\w.]*\.)?(\w+)", header)'
g="$(out)"
if [[ "$g" == *"16 screen"* ]]; then
  echo "  MISSED : a generic bound read as the superclass"; fail=$((fail+1))
else
  echo "  caught : a generic bound read as the superclass (count falls)"; pass=$((pass+1))
fi
restore

echo "the check that can still report"
edit $ROOT/check_ui.py 'if problems:
    print("one interface      : FAILED")' 'if False:
    print("one interface      : FAILED")'
g="$( (cd $ROOT && timeout 300 python3 check_validators.py 2>&1) )"
if [[ "$g" == *"can never fail"* || "$g" == *"after "* ]]; then
  echo "  caught : an interface check that can no longer report"; pass=$((pass+1))
else
  echo "  MISSED : an interface check that can no longer report"; fail=$((fail+1))
fi
restore

echo
echo "restoring and re-checking"
restore
echo "a box on the game's grey chest screen"
edit $ROOT/elysium-agronomy/src/main/java/com/elysium/agronomy/block/CabinetBlock.java 'return com.elysium.lib.menu.ElysiumChests.threeRows(id, inventory, this);' 'return ChestMenu.threeRows(id, inventory, this);'
expect "a cabinet back on the game's chest menu" "nothing themes"

g="$(out)"
if [[ "$g" == *"all in the kit"* ]]; then echo "  tree restored clean"
else echo "  TREE LEFT BROKEN"; fail=$((fail+1)); fi
echo "interface: $pass caught, $fail missed"
[ "$fail" -eq 0 ]
