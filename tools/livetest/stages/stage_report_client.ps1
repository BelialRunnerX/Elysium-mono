. "$PSScriptRoot\mc.ps1"
# The report at Dunwell (claimed, never attended before this build) and the natural listing.
MC-Place 0 0 1600 900
MC-Focus | Out-Null
Start-Sleep -Milliseconds 800
MC-Key 'ESC'; Start-Sleep -Milliseconds 800   # the pause menu opens when the window loses focus
MC-Cmd '/gamemode creative'
MC-Cmd '/time set 6000'
MC-Cmd '/tp @s -9600 84 9216 0 30'; Start-Sleep -Seconds 8
MC-Cmd '/elysium colony'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\report_1_dunwell.png" 0.5
MC-Cmd '/elysium colony natural'; Start-Sleep -Seconds 3
MC-Shot "$PSScriptRoot\report_2_natural.png" 0.5
"report stage done"
