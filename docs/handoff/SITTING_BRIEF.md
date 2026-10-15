# The by-hand sittings: how one is run

*The brief handed to whoever runs a by-hand sitting of `docs/PLAYTEST.md`. It
was written on 3 October 2026 and kept in the session scratchpad, which
Windows emptied on 11 October 2026; it came back from the session
transcript on 14 October 2026, with the paths moved to where the tree and the
test rigs live now.*

Monorepo `C:\Users\weaks\Elysium-mono` (`C:\Users\weaks\AppData\Local\Temp\Elysium-mono`, = `/tmp/Elysium-mono`
in Git Bash, is a junction to it), branch `main`. `docs/PLAYTEST.md` has a checklist of `- [ ]` boxes (lines
~16–140) with a bracketed note on each saying what already proves it, and a section "What the by-hand run
still has to do (2 October 2026)" that groups the open boxes into six sittings and names the stage script each
starts from. Dylan has asked for the run to be finished: a box you prove on the real client, with a screenshot
or a chat line to show for it, you tick (`- [x]`) and append ` *(ticked <day> <month>: <one line of
evidence>)*`; a box you cannot prove, or that fails, you leave unticked and append what you saw. Never tick on
a guess.

## The machine

* The test rigs live under `ELYSIUM_TEST_ROOT` (default `C:\testingbuilds`). The E: drive that held them was
  lost, and on 14 October 2026 they were rebuilt on C:.
* The dev client: `C:\testingbuilds\Elysium` (mods, saves, logs\latest.log). Launch it with
  `powershell -NoProfile -ExecutionPolicy Bypass -File tools\livetest\stages\client_up.ps1` from the repo (it
  starts `elysium-run-e.ps1 -NoInstall` and waits until the player has joined; ~100 s). When
  `saves\ElysiumBoot` does not exist (the rebuilt rig's saves folder starts empty) it adds `-FreshWorld`, and
  the launcher first builds that world from `tools\livetest\stages\reference` (the test server's level.dat
  through `StripPlayer.java`, plus its serverconfig); a world that exists is left as it is.
  The dedicated server at `C:\testingbuilds\ElysiumServer` (RCON 127.0.0.1:25575, password `elysiumtest`,
  `python3 tools/livetest/livetest.py --only "<word>"`, or one command with
  `python3 tools/livetest/rcon.py "<command>"`) runs the same jar (`bash tools/livetest/install.sh` puts a new
  build on both); use it for anything a server-side command can show, and the client only for what needs a
  player.
* The driver: `tools/livetest/stages/mc.ps1` — dot-source it (`. "...\mc.ps1"`). `MC-Focus` fronts the game
  (it taps ALT first; Windows gives the foreground only to the last input's process), `MC-Cmd '/...'` types a
  command into chat and waits for the chat answer, `MC-Key 'ESC'`, `MC-Shot "<path>" 0.5` photographs the
  window, `MC-RClick`, `MC-Resume` gets the game answering (ESC if the clock gets no answer).
  `[Win32MC]::Key(vk, $up)` — the second argument is UP: press is `Key(vk,$false)`, release `Key(vk,$true)`;
  the reverse leaves a key held and the player sliding. Never `MC-Move` while the mouse is captured (it turns
  the view); aim with `/tp @s x y z yaw pitch` instead (yaw 180 = north, 0 = south, pitch + = down).
  `stage_vale_client.ps1` is the pattern to copy: it fronts the game before every step and its header lists
  the traps (a screen left open swallows every key — the compass chooser takes two ESCs; the client's
  options have autoJump off; the client stage platform at 9000,200,9000 / the test spots near 906,90,-262).
* Read a result back with `/data get entity @s ...`, `/execute if block ...`, `/execute if entity ...`, and the
  mod's own commands (`/elysium colony`, `/elysium colony people`, `/elysium standing`, `/elysium tier`,
  `/elysiumspace ...`); each answers in chat and the stage reads it from `logs\latest.log`. Screenshots go to
  `C:\testingbuilds\Elysium\handback\screenshots\<sitting>\` (make the folder; `tools/sync.sh` copies them on
  to the D: handback).
* Rules: one client at a time (you are its only user); **never run compile.sh, the root validate.py, or a
  negtest** while the client runs (a negtest's javac freezes the integrated server; a compile corrupts the
  jar it is reading); nothing in this sitting changes source — if a box fails because the pack is wrong,
  write exactly what happened (command, answer, screenshot) beside the box and move on; the lead fixes it the
  house way afterwards. Do not commit.
* A stage that misbehaves: screenshot first (`MC-Shot`), then look. "unconfirmed (no chat answer)" from every
  command means a screen is open or a key is held — two ESCs and release W/A/S/D.

## Report

Under 40 lines: boxes ticked (with the evidence line), boxes left and why (fault in the pack, could not be
driven, needs a second player, ...), the screenshots' folder, and anything the pack got wrong in plain words.
