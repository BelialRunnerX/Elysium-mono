. "$PSScriptRoot\mc.ps1"
# The augmenting table takes a piece with affixes; the reforging table is where a piece gets
# them by hand (the loot injector is the other way). So: armory sword through the reforging
# table's first choice, then that sword and a sigil into the augmenting table.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function Say($s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }
function MC-ShiftClick([int]$x, [int]$y) { [Win32MC]::Key([uint16]0x10, $false); Start-Sleep -Milliseconds 80; MC-Click $x $y; Start-Sleep -Milliseconds 80; [Win32MC]::Key([uint16]0x10, $true); Start-Sleep -Milliseconds 300 }
$L = 179
function Top([int]$h) { return [math]::Floor((300 - $h) / 2) }
function SlotX([int]$sx) { return 3 * ($L + $sx + 8) }
function SlotY([int]$h, [int]$sy) { return 3 * ((Top $h) + $sy + 8) }
function HotbarX([int]$i) { return 3 * ($L + 8 + 18 * $i + 8) }
function HotbarY([int]$h, [int]$inventoryY) { return 3 * ((Top $h) + $inventoryY + 58 + 8) }
function HotbarIn([int]$h, [int]$invY) { for ($i = 0; $i -lt 9; $i++) { MC-ShiftClick (HotbarX $i) (HotbarY $h $invY) } }
function Inventory { $n = (LogLines).Count; MC-Cmd '/data get entity @s Inventory'; Start-Sleep -Milliseconds 600; (Chat $n 'entity data') -join ' ' }
function Dump($inv) { Write-Host ("  inventory: " + $inv.Substring(0, [Math]::Min(900, $inv.Length))) }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/clear @s'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 90 -258.5 180 24'; Start-Sleep -Seconds 3
Say "== elysium:augmenting_table"
# A piece with affixes, as the loot injector would roll one: the augmenting table takes
# Elysium gear whose affixes have room to rise.
MC-Cmd '/give @s elysium:aetherium_sword[elysiumlib:affixes={"elysiumlib:keen":1,"elysiumlib:quick":1}]'
MC-Cmd '/give @s elysium:sigil_of_enhancement'
MC-Cmd '/setblock 900 90 -262 minecraft:air'
MC-Cmd '/setblock 900 90 -262 elysium:augmenting_table'
MC-Cmd '/tp @s 900.5 90 -258.5 180 24'; Start-Sleep -Seconds 2
MC-RClick; Start-Sleep -Seconds 2
HotbarIn 166 84
Start-Sleep -Milliseconds 800
MC-Shot "$Out\menus_8_augmenting_in.png" 0.5
MC-ShiftClick (SlotX 110) (SlotY 166 36)
Start-Sleep -Milliseconds 900
MC-Shot "$Out\menus_8_augmenting.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
Dump (Inventory)
MC-Cmd '/setblock 900 90 -262 minecraft:air'
Write-Host "augment stage done"
