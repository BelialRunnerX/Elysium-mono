. "$PSScriptRoot\mc.ps1"
# Screenshots go to $env:ELYSIUM_SHOTS when it is set, else beside this script.
$Out = if ($env:ELYSIUM_SHOTS) { $env:ELYSIUM_SHOTS } else { $PSScriptRoot }
# The player-only mechanics the 27 September survey fixed or could not reach from
# the dedicated server: a relic's passive, an axe stripping emberwood, a heavy grade
# on a held sword, the reactor throttle, a cart's hold, capturing, budding and crop
# growth under a player's random ticks, a rift key lighting a frame and the portal
# taking the player in, and a grave on death. The stage stands at y 100, under the
# altitude cold (140).
$log = 'E:\testingbuilds\Elysium\logs\latest.log'
function LogLines { Get-Content $log -ErrorAction SilentlyContinue }
function Chat($n, $pattern) { (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'CHAT\]' | Select-String $pattern | ForEach-Object { $s = $_.Line; $s.Substring([Math]::Min(95, $s.Length)) } }
function MC-SneakRClick { MC-Guard; [Win32MC]::Key([uint16]0xA0, $false); Start-Sleep -Milliseconds 400; MC-RClick; Start-Sleep -Milliseconds 200; [Win32MC]::Key([uint16]0xA0, $true); Start-Sleep -Milliseconds 300 }
function MC-LClick { MC-Guard; [Win32MC]::Mouse(0x0002, 0, 0, 0); Start-Sleep -Milliseconds 60; [Win32MC]::Mouse(0x0004, 0, 0, 0) }
function Say($s) { "$(Get-Date -Format HH:mm:ss) $s" }

MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Resume | Out-Null
$n0 = (LogLines).Count
MC-Cmd '/gamemode creative'
MC-Cmd '/time set 6000'
MC-Cmd '/weather clear'
MC-Cmd '/gamerule doImmediateRespawn true'
MC-Cmd '/gamerule keepInventory false'
MC-Cmd '/execute in minecraft:overworld run tp @s 900.5 101 900.5 0 20'; Start-Sleep -Seconds 6
MC-Cmd '/fill 880 99 880 940 99 940 minecraft:smooth_stone'; Start-Sleep -Seconds 1
MC-Cmd '/fill 880 100 880 940 130 940 minecraft:air'; Start-Sleep -Seconds 2
MC-Cmd '/kill @e[type=!player,distance=..60]'
MC-Cmd '/clear @s'

Say "== A relic passive: a wanderer's charm in hand makes its holder swift"
$n = (LogLines).Count
MC-Cmd '/item replace entity @s weapon.mainhand with elysiumarcana:wanderers_charm'; Start-Sleep -Seconds 4
MC-Cmd '/data get entity @s active_effects'; Start-Sleep -Seconds 1
MC-Shot "$Out\mech_1_relic_speed.png" 0.5
"chat:"; Chat $n 'active_effects|Replaced'
$n = (LogLines).Count
MC-Cmd '/clear @s'; Start-Sleep -Seconds 4
MC-Cmd '/data get entity @s active_effects'; Start-Sleep -Seconds 1
"after clearing (speed should run out within 45 ticks):"; Chat $n 'active_effects'

Say "== B an axe strips an emberwood log (and the game's own oak, as the control)"
$n = (LogLines).Count
MC-Cmd '/setblock 890 100 890 elysiumflora:emberwood_log'
MC-Cmd '/setblock 886 100 890 minecraft:oak_log'
MC-Cmd '/item replace entity @s weapon.mainhand with minecraft:iron_axe'
MC-Cmd '/tp @s 890.5 100 892.5 180 30'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/tp @s 886.5 100 892.5 180 30'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 1
MC-Cmd '/execute if block 890 100 890 elysiumflora:stripped_emberwood_log'; Start-Sleep -Seconds 1
MC-Cmd '/execute if block 886 100 890 minecraft:stripped_oak_log'; Start-Sleep -Seconds 1
MC-Cmd '/tp @s 888.5 101 894.5 180 25'; Start-Sleep -Seconds 3
MC-Shot "$Out\mech_2_stripped.png" 0.5
"chat (emberwood, then oak):"; Chat $n 'Test'
MC-Cmd '/clear @s'

Say "== E a heavy grade sharpens a held sword"
$n = (LogLines).Count
MC-Cmd '/attribute @s minecraft:generic.attack_damage get'; Start-Sleep -Seconds 1
MC-Cmd '/item replace entity @s weapon.mainhand with minecraft:iron_sword'; Start-Sleep -Seconds 2
MC-Cmd '/attribute @s minecraft:generic.attack_damage get'; Start-Sleep -Seconds 1
MC-Cmd '/item replace entity @s weapon.mainhand with minecraft:iron_sword[elysiumarsenal:grade="heavy"]'; Start-Sleep -Seconds 2
MC-Cmd '/attribute @s minecraft:generic.attack_damage get'; Start-Sleep -Seconds 1
MC-Shot "$Out\mech_5_grade.png" 0.5
"chat (bare, plain sword, heavy sword):"; Chat $n 'Attack Damage'
MC-Cmd '/clear @s'

Say "== D a three-cube reactor hall forms and its port's throttle cycles on a sneaking use"
$n = (LogLines).Count
MC-Cmd '/fill 910 100 888 912 102 890 elysiumatomics:reactor_casing'
MC-Cmd '/setblock 911 101 889 elysiumatomics:fuel_rod'
MC-Cmd '/setblock 911 101 890 elysiumatomics:reactor_port'
MC-Cmd '/tp @s 911.5 100 893.5 180 5'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 1
MC-SneakRClick; Start-Sleep -Seconds 1
MC-SneakRClick; Start-Sleep -Seconds 1
MC-Shot "$Out\mech_4_reactor_throttle.png" 0.5
"chat:"; Chat $n 'reactor|Reactor|hall|throttle|step|Step'

Say "== C a cart's hold takes a stack from a sneaking hand and gives it back"
$n = (LogLines).Count
MC-Cmd '/summon elysiumtransit:cart 900.5 100 902.5'
MC-Cmd '/item replace entity @s weapon.mainhand with minecraft:cobblestone 32'
MC-Cmd '/tp @s 900.5 100 905.5 180 20'; Start-Sleep -Seconds 3
MC-SneakRClick; Start-Sleep -Seconds 1
MC-Cmd '/data get entity @e[type=elysiumtransit:cart,limit=1] Cargo'; Start-Sleep -Seconds 1
MC-Shot "$Out\mech_3_cart_cargo.png" 0.5
"chat (in):"; Chat $n 'Cart has|No entity'
$n = (LogLines).Count
MC-Cmd '/clear @s'; Start-Sleep -Seconds 1
MC-SneakRClick; Start-Sleep -Seconds 1
MC-Cmd '/data get entity @s SelectedItem'; Start-Sleep -Seconds 1
"chat (out):"; Chat $n 'SelectedItem|entity data'
MC-Cmd '/clear @s'
MC-Cmd '/kill @e[type=elysiumtransit:cart]'

Say "== H budding iron and onion crops under a player's random ticks (started now, read at the end)"
$n = (LogLines).Count
MC-Cmd '/fill 890 100 910 894 100 914 elysiumarcana:budding_iron'
MC-Cmd '/fill 900 100 910 906 100 916 minecraft:farmland[moisture=7]'
MC-Cmd '/fill 900 101 910 906 101 916 elysiumagronomy:onion_crop[age=0]'
MC-Cmd '/gamerule randomTickSpeed 200'
"placed:"; Chat $n 'filled|No blocks|Could not'

Say "== F capturing: a capturing III sword kills cows for their eggs"
$n = (LogLines).Count
MC-Cmd '/item replace entity @s weapon.mainhand with minecraft:diamond_sword[minecraft:enchantments={levels:{"elysiumspawners:capturing":3}}]'
for ($i = 0; $i -lt 8; $i++) { MC-Cmd '/summon minecraft:cow 920.5 100 897.5 {NoAI:1b,Silent:1b}' }
MC-Cmd '/tp @s 920.5 100 900.0 180 12'; Start-Sleep -Seconds 3
for ($i = 0; $i -lt 24; $i++) { MC-LClick; Start-Sleep -Milliseconds 650 }
Start-Sleep -Seconds 2
MC-Cmd '/execute if entity @e[type=minecraft:item,distance=..12,nbt={Item:{id:"minecraft:cow_spawn_egg"}}]'; Start-Sleep -Seconds 1
MC-Cmd '/tp @s 920.5 101 900.5 180 40'; Start-Sleep -Seconds 3
MC-Shot "$Out\mech_6_capture.png" 0.5
"chat (eggs):"; Chat $n 'Test'
MC-Cmd '/clear @s'
MC-Cmd '/kill @e[type=minecraft:cow,distance=..20]'

Say "== I a rift key lights a frame and the portal takes the player in"
$n = (LogLines).Count
MC-Cmd '/fill 920 100 915 923 104 915 elysiumdungeons:rift_frame'
MC-Cmd '/fill 921 101 915 922 103 915 minecraft:air'
MC-Cmd '/item replace entity @s weapon.mainhand with elysiumdungeons:rift_key'
MC-Cmd '/tp @s 921.5 100 919.5 180 22'; Start-Sleep -Seconds 3
MC-RClick; Start-Sleep -Seconds 2
MC-Cmd '/execute if block 921 102 915 elysiumdungeons:rift_portal'; Start-Sleep -Seconds 1
MC-Cmd '/tp @s 921.5 100 920.5 180 5'; Start-Sleep -Seconds 3
MC-Shot "$Out\mech_10_portal.png" 0.5
"chat (portal lit):"; Chat $n 'Test|frame'
$n = (LogLines).Count
# a walk overshoots a one-block portal; the player is set in it and stands (the portal takes a few seconds)
MC-Cmd '/tp @s 921.5 101 915.5 180 0'; Start-Sleep -Seconds 12
MC-Cmd '/execute if dimension elysiumdungeons:dungeon'; Start-Sleep -Seconds 1
MC-Shot "$Out\mech_11_dungeon.png" 0.5
"chat (in the dungeon dimension):"; Chat $n 'Test'
"log since walking in:"; (LogLines)[$n..((LogLines).Count - 1)] | Select-String 'Allocated|Built|rooms|Dungeon|dungeon' | Select-String -NotMatch 'CHAT' | ForEach-Object { $_.Line.Substring([Math]::Min(60, $_.Line.Length)) } | Select-Object -Last 6
MC-Cmd '/execute in minecraft:overworld run tp @s 930.5 101 903.5 180 30'; Start-Sleep -Seconds 6
MC-Cmd '/clear @s'

Say "== G a grave keeps what a dying player carried"
$n = (LogLines).Count
MC-Cmd '/gamemode survival'
MC-Cmd '/give @s minecraft:gold_ingot 5'
MC-Cmd '/tp @s 930.5 100 900.5 180 30'; Start-Sleep -Seconds 2
MC-Cmd '/kill @s'; Start-Sleep -Seconds 6
MC-Cmd '/gamemode creative'; Start-Sleep -Seconds 2
MC-Cmd '/gamemode creative'; Start-Sleep -Seconds 1
"mode:"; Chat $n 'game mode|Set own'
MC-Cmd '/execute in minecraft:overworld run tp @s 930.5 101 903.5 180 30'; Start-Sleep -Seconds 5
MC-Cmd '/data get block 930 100 900'; Start-Sleep -Seconds 1
MC-Shot "$Out\mech_7_grave.png" 0.5
"chat:"; Chat $n 'block data|is not a block entity'
MC-Cmd '/execute if entity @e[type=minecraft:item,distance=..8]'; Start-Sleep -Seconds 1
"items lying about (a grave leaves none):"; Chat $n 'Test'

Say "== H read: what grew"
$n = (LogLines).Count
MC-Cmd '/gamerule randomTickSpeed 3'
MC-Cmd '/tp @s 897.5 104 920.5 200 40'; Start-Sleep -Seconds 4
MC-Shot "$Out\mech_8_buds_and_crops.png" 0.5
MC-Cmd '/fill 900 101 910 906 101 916 minecraft:air replace elysiumagronomy:onion_crop[age=0]'; Start-Sleep -Seconds 1
"onions still at age 0 (of 49):"; Chat $n 'filled|No blocks'
$n = (LogLines).Count
MC-Cmd '/fill 889 101 909 895 101 915 minecraft:air replace elysiumarcana:iron_small_bud'; Start-Sleep -Seconds 1
"small iron buds above the budding blocks:"; Chat $n 'filled|No blocks'
$n = (LogLines).Count
MC-Cmd '/fill 889 101 909 895 101 915 minecraft:air replace elysiumarcana:iron_medium_bud'; Start-Sleep -Seconds 1
"medium:"; Chat $n 'filled|No blocks'
MC-Cmd '/gamerule doImmediateRespawn false'
"mechanics stage done"
