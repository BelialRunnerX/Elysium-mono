. "$PSScriptRoot\mc.ps1"
# JEI as a non-dependency: with JEI installed beside the pack, the inventory shows its item
# panel, and a machine's uses (U over its hotbar slot) open the machine's own recipe
# category from the library's plugin (POLISH.md P6). Run with the JEI jar in mods/.
#
# Survival mode on purpose: the creative screen has a search tab of its own that swallows
# every keystroke, and a chat probe typed into it as well (28 Sep). In survival the
# inventory is plain, JEI's search box takes the click and the text, and the rhythm is
# fixed: E opens, U opens the uses, ESC returns to the inventory, ESC closes it.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
MC-Cmd '/gamemode creative'
# Survival, so nothing may be near: a beast from the creature stage slew the tester (28 Sep).
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 91 -258.5 180 10'; Start-Sleep -Seconds 3
MC-Cmd '/gamerule doMobSpawning false'
MC-Cmd '/kill @e[type=!player,distance=..96]'
MC-Cmd '/time set 6000'
MC-Cmd '/clear @s'
MC-Cmd '/give @s elysiumfoundry:alloy_smelter'
MC-Cmd '/give @s elysiumindustry:crusher'
MC-Cmd '/give @s elysiumkinetics:millstone'
MC-Cmd '/gamemode survival'
Start-Sleep -Seconds 1
MC-Key 'E'; Start-Sleep -Seconds 2
MC-Shot "$Out\jei_1_inventory.png" 0.5
MC-Key 'ESC'; Start-Sleep -Milliseconds 800
# The three machines sit in the hotbar; in the survival inventory that row's first three
# slots are at these window points (1600x900, GUI scale 2). U over a slot opens the item's
# uses, which for a machine is its own category from the library's plugin. (JEI's search
# box did not take focus from a synthetic click, so the hotbar is the way in.)
$slots = @(@(584, 660, 'jei_2_alloy_smelter_uses.png'), @(620, 660, 'jei_3_crusher_uses.png'), @(656, 660, 'jei_4_millstone_uses.png'))
foreach ($slot in $slots) {
  MC-Key 'E'; Start-Sleep -Seconds 2
  MC-Move $slot[0] $slot[1]; Start-Sleep -Milliseconds 800
  MC-Key 'U'; Start-Sleep -Seconds 2
  MC-Shot "$Out\$($slot[2])" 0.5
  MC-Key 'ESC'; Start-Sleep -Milliseconds 800
  MC-Key 'ESC'; Start-Sleep -Milliseconds 800
}
MC-Cmd '/gamemode creative'
MC-Cmd '/gamerule doMobSpawning true'
"jei stage done"
