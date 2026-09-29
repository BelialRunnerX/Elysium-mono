. "$PSScriptRoot\mc.ps1"
# The Ledger Web by hand (docs/SKILLTREE.md): levels granted, points counted, nodes taken and
# given back by command with their refusals, the sheet's Web tab opened and photographed, a
# node taken by a click on the canvas, and the stat sheet read before and after.
#
# Geometry (1600x900, GUI scale 3, panel 300 wide, the sheet's tallest tab): the tab row's
# centre is y 210 px, the fifth tab's centre x 1131; the canvas centre (the hub) sits at
# (800, 540) px and the fit zoom is 0.59 px per web unit, both read off the first runs' shots.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function Say($s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }
function Ask($cmd, $pattern) { $n = (LogLines).Count; MC-Cmd $cmd; Start-Sleep -Milliseconds 700; $got = (Chat $n $pattern) -join ' | '; Write-Host ("  " + $cmd + " -> " + $got.Substring(0, [Math]::Min(220, $got.Length))) }
# The fit zoom frames the whole web to its outer edge (ElysiumTree.R_EDGE, the frontier ring
# since 29 Sep 2026): 0.60 * 764 / 1084 = 0.423 px per web unit about the hub at (800, 540);
# the stage zooms in five steps about the hub before it hovers, which is 0.851 px per unit.
function Web([double]$wx, [double]$wy) { return @([int](800 + $wx * 0.851), [int](540 + $wy * 0.851)) }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 90 -258.5 180 24'; Start-Sleep -Seconds 3
MC-Cmd '/clear @s'
Say "== the web by command"
Ask '/elysium tree reset' 'clear|given back'
Ask '/elysium level 41' 'Level'
Ask '/elysium tree points' 'Ledger Web'
Ask '/elysium tree held' 'start_'
Ask '/elysium stats' 'Presence|Accuracy'
Ask '/elysium tree allocate dimensional_2' 'touch|Taken'                  # not adjacent yet: refused
Ask '/elysium tree allocate road_start_marksman' 'Taken'
Ask '/elysium tree allocate dimensional_2' 'Taken'
Ask '/elysium tree refund road_start_marksman' 'hang'                    # a bridge: refused
Ask '/elysium tree refund dimensional_2' 'Given back|hang'              # creative pays nothing; a leaf off the marksman start
Ask '/elysium tree allocate dimensional_2' 'Taken'
Ask '/elysium tree allocate start_imperial' 'already'
Ask '/elysium tree refund start_imperial' 'not given back'
Ask '/elysium tree allocate no_such_node' 'no such'
Ask '/elysium tree points' 'Ledger Web'
Ask '/elysium stats' 'Presence|Accuracy'

Say "== the web on the sheet"
MC-Cmd '/elysium sheet'; Start-Sleep -Seconds 2
MC-Click 1131 210; Start-Sleep -Seconds 1                                  # the Web tab
MC-Shot "$Out\web_1_tab.png" 0.5
# Zoom in five steps about the hub, so a small node's frame is its own target
# (at the fit zoom a start and its road overlap): 1.15^5 = 2.011 times the fit.
MC-Move 800 540; Start-Sleep -Milliseconds 300
for ($i = 0; $i -lt 5; $i++) { [Win32MC]::Mouse(0x0800, 0, 0, 120); Start-Sleep -Milliseconds 250 }
Start-Sleep -Milliseconds 500
$p = Web -340.0 124.2                                                      # start_marksman, held: its tooltip
MC-Move $p[0] $p[1]; Start-Sleep -Milliseconds 800
MC-Shot "$Out\web_2_hover.png" 0.5
# The search box: type a node's id, Enter centres it, Enter again takes it — the same
# payload a click sends, without a click's dependence on where the frame landed.
MC-Type 'dimensional_1'; Start-Sleep -Milliseconds 500
MC-Key 'RETURN'; Start-Sleep -Milliseconds 800                             # centred
MC-Shot "$Out\web_3_found.png" 0.5
MC-Key 'RETURN'; Start-Sleep -Seconds 2                                    # taken
MC-Shot "$Out\web_4_taken.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 500                                # clears the search
$q = Web 0 0
MC-Move 800 540; Start-Sleep -Milliseconds 800                             # the hub, hovered: its tooltip
MC-Shot "$Out\web_5_hub.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
Ask '/elysium tree held' 'dimensional_1'
Ask '/elysium tree points' 'Ledger Web'
Say "== the frontier: out from the marksman's road to the rift cluster"
Ask '/elysium tree allocate road_out_7' 'Taken'
Ask '/elysium tree allocate rift_9' 'Taken'
Ask '/elysium tree allocate rift_notable' 'touch|Taken'
Say "== survival: an erasure to pay with"
# By day and warded: the stage has hostile mobs about, and killed the tester here once.
MC-Cmd '/time set noon'
MC-Cmd '/effect give @s minecraft:resistance 120 4 true'
MC-Cmd '/gamemode survival'
Ask '/elysium tree refund dimensional_1' 'costs a Ledger Erasure'
MC-Cmd '/give @s elysium:ledger_erasure 2'
Ask '/elysium tree refund dimensional_1' 'Given back'
Ask '/elysium tree points' 'erasure'
MC-Cmd '/gamemode creative'
Ask '/elysium tree reset' 'given back'
Write-Host "web stage done"
