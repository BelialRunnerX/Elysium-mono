. "$PSScriptRoot\mc.ps1"
# The menus that need hands (POLISH.md P12): each station is placed, the things it takes are
# given, the menu is opened, the things are shift-clicked in from the hotbar, the station's
# own control is pressed, and the outcome is photographed and read back off the player's
# inventory after the menu closes (a closed menu hands its slots back).
#
# Window points: 1600x900 with guiScale 0 (auto) is GUI scale 3, so the gui is 534x300,
# leftPos is 179 and topPos is (300 - imageHeight) / 2; a gui point is three window pixels.
# Everything in the hotbar is shift-clicked in, slot by slot, so nothing here has to know
# which slot /give or a closed menu put a thing in; a shift-click on an empty slot is nothing.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = $script:McLog   # the dev client's log under ELYSIUM_TEST_ROOT, set by mc.ps1
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function Say($s) { Write-Host "$(Get-Date -Format HH:mm:ss) $s" }
# Shift-click: the shift key held around a click, which quick-moves a stack into the menu.
function MC-ShiftClick([int]$x, [int]$y) { [Win32MC]::Key([uint16]0x10, $false); Start-Sleep -Milliseconds 80; MC-Click $x $y; Start-Sleep -Milliseconds 80; [Win32MC]::Key([uint16]0x10, $true); Start-Sleep -Milliseconds 300 }
$L = 179
function Top([int]$h) { return [math]::Floor((300 - $h) / 2) }
function SlotX([int]$sx) { return 3 * ($L + $sx + 8) }
function SlotY([int]$h, [int]$sy) { return 3 * ((Top $h) + $sy + 8) }
function HotbarX([int]$i) { return 3 * ($L + 8 + 18 * $i + 8) }
function HotbarY([int]$h, [int]$inventoryY) { return 3 * ((Top $h) + $inventoryY + 58 + 8) }
function Btn([int]$h, [int]$x, [int]$y, [int]$w, [int]$hh) { MC-Click (3 * ($L + $x + [math]::Floor($w / 2))) (3 * ((Top $h) + $y + [math]::Floor($hh / 2))) }
function HotbarIn([int]$h, [int]$invY) { for ($i = 0; $i -lt 9; $i++) { MC-ShiftClick (HotbarX $i) (HotbarY $h $invY) } }
function Inventory { $n = (LogLines).Count; MC-Cmd '/data get entity @s Inventory'; Start-Sleep -Milliseconds 600; (Chat $n 'entity data') -join ' ' }
function Has($inv, $pattern) { if ($inv -match $pattern) { Write-Host "  has $pattern" } else { Write-Host "  MISSING $pattern" } }
function Lacks($inv, $pattern) { if ($inv -match $pattern) { Write-Host "  STILL HAS $pattern" } else { Write-Host "  consumed: $pattern" } }
function Dump($inv) { Write-Host ("  inventory: " + $inv.Substring(0, [Math]::Min(900, $inv.Length))) }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 91 -258.5 180 24'; Start-Sleep -Seconds 5
MC-Cmd '/fill 880 89 -280 920 89 -240 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 880 90 -280 920 97 -240 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/fill 880 98 -280 920 105 -240 minecraft:air'; Start-Sleep -Seconds 1
MC-Cmd '/time set 6000'
MC-Cmd '/gamerule doMobSpawning false'
MC-Cmd '/kill @e[type=!player,distance=..96]'
MC-Cmd '/xp add @s 40 levels'
# A room of shelves round the station square for the enchanting stations: three walls at
# two and three blocks out, the front left open so the player can see the table.
MC-Cmd '/fill 897 89 -265 903 92 -264 minecraft:bookshelf'
MC-Cmd '/fill 897 89 -265 898 92 -260 minecraft:bookshelf'
MC-Cmd '/fill 902 89 -265 903 92 -260 minecraft:bookshelf'
MC-Cmd '/setblock 900 89 -265 elysiumenchanting:hellshelf'
MC-Cmd '/setblock 900 90 -265 elysiumenchanting:sightshelf'

# One station: the block placed, the things given (unless $keep), the menu opened, the
# hotbar shift-clicked in, a shot, the steps, a shot, ESC, and the inventory read back.
function Station($block, $gives, $h, $invY, $steps, $shot, $keep = $false) {
  Say "== $block"
  if (-not $keep) { MC-Cmd '/clear @s' }
  foreach ($g in $gives) { MC-Cmd "/give @s $g" }
  MC-Cmd '/setblock 900 90 -262 minecraft:air'
  MC-Cmd "/setblock 900 90 -262 $block"
  MC-Cmd '/tp @s 900.5 90 -258.5 180 24'; Start-Sleep -Seconds 2
  MC-RClick; Start-Sleep -Seconds 2
  HotbarIn $h $invY
  Start-Sleep -Milliseconds 800
  MC-Shot "$Out\$($shot)_in.png" 0.5
  foreach ($step in $steps) { & $step; Start-Sleep -Milliseconds 900 }
  MC-Shot "$Out\$shot.png" 0.5
  MC-Key 'ESC'; Start-Sleep -Seconds 1
  $inv = Inventory
  Dump $inv
  return $inv
}

# The gear smithing table: blueprint paper in, a part chosen from the list, a blueprint out.
$inv = Station 'elysiumarmory:gear_smithing_table' @('elysiumarmory:blueprint_paper 8') 182 100 @(
  { Btn 182 8 22 100 11 },                                           # the first row of the part list
  { MC-ShiftClick (SlotX 150) (SlotY 182 47) }                       # the output slot back to the bag
) 'menus_1_smithing'
Has $inv 'elysiumarmory:blueprint"'
Lacks $inv 'count: 8, Slot: 0b, id: "elysiumarmory:blueprint_paper"'

# The reforging table: armory gear and affix essence in; a rarity chosen from the choices
# puts the rolled piece on the cursor, and ESC hands it back with its affixes.
$inv = Station 'elysiumarmory:reforging_table' @('elysiumarmory:sword', 'elysiumarmory:affix_essence 8') 166 84 @(
  { MC-Click (SlotX 80) (SlotY 166 24) }                             # the first choice, onto the cursor
) 'menus_2_reforging'
Has $inv 'elysiumlib:affixes'

# The augmenting table: a piece with affixes (given as the loot injector would roll one; the
# core's reforge table writes gear_data bonuses, not affixes, and the armoury's affixed sword
# is not core gear) and a sigil in; the result slot back to the bag.
$inv = Station 'elysium:augmenting_table' @('elysium:aetherium_sword[elysiumlib:affixes={"elysiumlib:keen":1,"elysiumlib:quick":1}]', 'elysium:sigil_of_enhancement') 166 84 @(
  { MC-ShiftClick (SlotX 110) (SlotY 166 36) }
) 'menus_8_augmenting'
Has $inv 'elysiumlib:keen'
Lacks $inv 'sigil_of_enhancement'

# The reforge table, twice: gear, a material and a rune, then Forge (the rune is socketed
# first, by the table's own order); Forge again with the rune gone reforges with the material.
$inv = Station 'elysium:reforge_table' @('elysium:aetherium_sword', 'minecraft:iron_ingot 8', 'elysium:barrier_rune') 166 84 @(
  { Btn 166 58 56 60 20 },
  { MC-Shot "$Out\menus_3_reforge_socketed.png" 0.5 },
  { Btn 166 58 56 60 20 },
  { MC-ShiftClick (SlotX 30) (SlotY 166 35) }
) 'menus_3_reforge'
Has $inv 'elysium:barrier'
Has $inv 'reforge_count'

# The infusion basin: gear, an enchanted book and lapis, then the button.
$inv = Station 'elysiumenchanting:infusion_basin' @('elysium:aetherium_sword', 'minecraft:enchanted_book[stored_enchantments={levels:{"minecraft:sharpness":1}}]', 'minecraft:lapis_lazuli 32') 166 84 @(
  { Btn 166 80 46 86 24 },
  { MC-ShiftClick (SlotX 15) (SlotY 166 30) }
) 'menus_4_infusion'
Has $inv 'minecraft:sharpness'

# The arcane table: an item and lapis, then the first offer.
$inv = Station 'elysiumenchanting:arcane_table' @('elysium:aetherium_sword', 'minecraft:lapis_lazuli 32') 182 100 @(
  { Btn 182 78 22 90 20 },
  { MC-ShiftClick (SlotX 15) (SlotY 182 64) }
) 'menus_5_arcane'
Has $inv 'minecraft:enchantments'

# The scriptorium: an enchanted book filed (shift-click on the input slot), a plain book in,
# the offer bought, the copy back to the bag. Sharpness III is four pages; a copy at II costs four.
$inv = Station 'elysiumenchanting:scriptorium' @('minecraft:enchanted_book[stored_enchantments={levels:{"minecraft:sharpness":3}}]', 'minecraft:book 8') 182 100 @(
  { MC-ShiftClick (SlotX 15) (SlotY 182 66) },                       # file what is in the input slot
  { MC-Shot "$Out\menus_6_scriptorium_filed.png" 0.5 },
  { HotbarIn 182 100 },                                              # a plain book in
  { Btn 182 44 66 90 26 },                                           # buy
  { MC-ShiftClick (SlotX 143) (SlotY 182 66) }                       # the copy back
) 'menus_6_scriptorium'
Has $inv 'stored_enchantments'

# The gem cutting table: a rune and gem dust in; the result slot back to the bag.
$inv = Station 'elysium:gem_cutting_table' @('elysium:barrier_rune', 'elysium:gem_dust 16') 166 84 @(
  { MC-ShiftClick (SlotX 110) (SlotY 166 36) }
) 'menus_7_gem_cutting'
Has $inv 'rune_tier'
Lacks $inv 'count: 16, Slot: 1b, id: "elysium:gem_dust"'

# The transmuter: used with an item in hand it dissolves the stack to motes and learns the
# item; used with the hand then empty it opens the chooser of everything learned.
Say "== elysiumarcana:transmuter"
MC-Cmd '/clear @s'
MC-Cmd '/give @s minecraft:iron_ingot 16'
MC-Cmd '/setblock 900 90 -262 minecraft:air'
MC-Cmd '/setblock 900 90 -262 elysiumarcana:transmuter'
MC-Cmd '/tp @s 900.5 90 -258.5 180 24'; Start-Sleep -Seconds 2
MC-RClick; Start-Sleep -Seconds 2
MC-Shot "$Out\menus_9_transmuter_dissolved.png" 0.5
MC-RClick; Start-Sleep -Seconds 2
MC-Shot "$Out\menus_9_transmuter_chooser.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
$inv = Inventory
Dump $inv

# A trinket worn: the band goes into the Curios screen (G) by shift-click; the sheet shows it.
Say "== a trinket worn"
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumtrinkets:aetherium_band'
MC-Cmd '/tp @s 900.5 90 -250.5 0 40'; Start-Sleep -Seconds 2
MC-Key 'G'; Start-Sleep -Seconds 2
MC-Shot "$Out\menus_10_curios_before.png" 0.5
MC-ShiftClick 584 660
Start-Sleep -Seconds 1
MC-Shot "$Out\menus_10_curios_worn.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
$inv = Inventory
Dump $inv
Has $inv 'aetherium_band'
MC-Cmd '/elysium sheet'; Start-Sleep -Seconds 2
MC-Shot "$Out\menus_10_trinket_sheet.png" 0.5
MC-Key 'ESC'; Start-Sleep -Seconds 1
$n = (LogLines).Count
MC-Cmd '/elysium stats'; Start-Sleep -Seconds 1
Write-Host ("  stats: " + ((Chat $n '.') -join ' | '))
MC-Cmd '/setblock 900 90 -262 minecraft:air'
MC-Cmd '/gamerule doMobSpawning true'
Write-Host "menus stage done"
