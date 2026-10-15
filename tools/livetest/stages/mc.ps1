# Minimal desktop driver for the Minecraft dev client: focus window, screenshot, send keys / mouse via SendInput.
# Usage: . mc.ps1; MC-Focus; MC-Shot out.png; MC-Click 100 200; MC-Type "hello"; MC-Key 'RETURN'
Add-Type -AssemblyName System.Drawing, System.Windows.Forms
if(-not ([System.Management.Automation.PSTypeName]'Win32MC').Type){
Add-Type @"
using System;
using System.Runtime.InteropServices;
using System.Text;
public class Win32MC {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int x, int y, int w, int h, bool repaint);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
  [DllImport("user32.dll")] public static extern short VkKeyScan(char ch);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint uCode, uint uMapType);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
  [StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Explicit)] public struct INPUTUNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
  [StructLayout(LayoutKind.Sequential)] public struct INPUT { public uint type; public INPUTUNION u; }
  public static void Key(ushort vk, bool up) {
    INPUT[] inp = new INPUT[1]; inp[0].type = 1;
    inp[0].u.ki.wVk = vk; inp[0].u.ki.wScan = (ushort)MapVirtualKey(vk, 0);
    inp[0].u.ki.dwFlags = (uint)(0x0008 | (up ? 0x0002 : 0)); // KEYEVENTF_SCANCODE | KEYUP
    SendInput(1, inp, Marshal.SizeOf(typeof(INPUT)));
  }
  public static void Unicode(char c, bool up) {
    INPUT[] inp = new INPUT[1]; inp[0].type = 1;
    inp[0].u.ki.wVk = 0; inp[0].u.ki.wScan = c;
    inp[0].u.ki.dwFlags = (uint)(0x0004 | (up ? 0x0002 : 0)); // KEYEVENTF_UNICODE
    SendInput(1, inp, Marshal.SizeOf(typeof(INPUT)));
  }
  public static void Mouse(uint flags, int dx, int dy, uint data) {
    INPUT[] inp = new INPUT[1]; inp[0].type = 0;
    inp[0].u.mi.dx = dx; inp[0].u.mi.dy = dy; inp[0].u.mi.mouseData = data; inp[0].u.mi.dwFlags = flags;
    SendInput(1, inp, Marshal.SizeOf(typeof(INPUT)));
  }
}
"@
}
[Win32MC]::SetProcessDPIAware() | Out-Null
function MC-Place([int]$x = 0, [int]$y = 0, [int]$w = 1600, [int]$h = 900) { $p = MC-Window; [Win32MC]::MoveWindow($p.MainWindowHandle, $x, $y, $w, $h, $true) | Out-Null; Start-Sleep -Milliseconds 500 }
function MC-Window { Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | Select-Object -First 1 }
# Windows lets only the process that last received input set the foreground window, so a
# tap of ALT (which the game ignores) is sent first; without it the call fails whenever the
# Claude window, a terminal or a negtest's javac has the focus, and every key after is refused.
function MC-Focus { $p = MC-Window; if(-not $p){ throw 'no Minecraft window' }; [Win32MC]::Key([uint16]0x12, $false); Start-Sleep -Milliseconds 40; [Win32MC]::Key([uint16]0x12, $true); [Win32MC]::ShowWindow($p.MainWindowHandle, 9) | Out-Null; [Win32MC]::SetForegroundWindow($p.MainWindowHandle) | Out-Null; Start-Sleep -Milliseconds 400; $r = New-Object Win32MC+RECT; [Win32MC]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null; $r }
function MC-Rect { $p = MC-Window; $r = New-Object Win32MC+RECT; [Win32MC]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null; $r }
function MC-Shot([string]$path, [double]$scale = 1.0) {
  $r = MC-Rect; $w = $r.Right - $r.Left; $h = $r.Bottom - $r.Top
  $bmp = New-Object System.Drawing.Bitmap $w, $h; $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size); $g.Dispose()
  if($scale -ne 1.0){ $bmp2 = New-Object System.Drawing.Bitmap $bmp, ([int]($w*$scale)), ([int]($h*$scale)); $bmp.Dispose(); $bmp = $bmp2 }
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose(); "$path ($w x $h window at $($r.Left),$($r.Top))"
}
# Click at window-relative coordinates (pixels inside the window rect as captured by MC-Shot at scale 1)
function MC-Click([int]$x, [int]$y) { MC-Guard; $r = MC-Rect; [Win32MC]::SetCursorPos($r.Left + $x, $r.Top + $y) | Out-Null; Start-Sleep -Milliseconds 120; [Win32MC]::Mouse(0x0002, 0, 0, 0); Start-Sleep -Milliseconds 60; [Win32MC]::Mouse(0x0004, 0, 0, 0); Start-Sleep -Milliseconds 250 }
function MC-RightClick([int]$x, [int]$y) { MC-Guard; $r = MC-Rect; [Win32MC]::SetCursorPos($r.Left + $x, $r.Top + $y) | Out-Null; Start-Sleep -Milliseconds 120; [Win32MC]::Mouse(0x0008, 0, 0, 0); Start-Sleep -Milliseconds 60; [Win32MC]::Mouse(0x0010, 0, 0, 0); Start-Sleep -Milliseconds 250 }
function MC-Move([int]$x, [int]$y) { $r = MC-Rect; [Win32MC]::SetCursorPos($r.Left + $x, $r.Top + $y) | Out-Null }
function MC-Key([string]$name, [int]$holdMs = 60) {
  $vk = switch($name.ToUpper()){ 'RETURN'{0x0D} 'ENTER'{0x0D} 'ESC'{0x1B} 'ESCAPE'{0x1B} 'TAB'{0x09} 'SPACE'{0x20} 'BACK'{0x08} 'F1'{0x70} 'F2'{0x71} 'F3'{0x72} 'F5'{0x74} 'UP'{0x26} 'DOWN'{0x28} 'LEFT'{0x25} 'RIGHT'{0x27} 'SLASH'{0xBF} 'T'{0x54} 'E'{0x45} 'W'{0x57} 'A'{0x41} 'S'{0x53} 'D'{0x44} 'LSHIFT'{0xA0} 'LCTRL'{0xA2} default { [int][char]$name.ToUpper() } }
  MC-Guard; [Win32MC]::Key([uint16]$vk, $false); Start-Sleep -Milliseconds $holdMs; [Win32MC]::Key([uint16]$vk, $true); Start-Sleep -Milliseconds 80
}
function MC-RClick { MC-Guard; [Win32MC]::Mouse(0x0008,0,0,0); Start-Sleep -Milliseconds 100; [Win32MC]::Mouse(0x0010,0,0,0); Start-Sleep -Milliseconds 300 }
# Strict guard: never steal focus. Input is sent only if Minecraft already is the foreground window.
function MC-Guard { $p = MC-Window; if(-not $p){ throw 'no Minecraft window' }; if([Win32MC]::GetForegroundWindow() -ne $p.MainWindowHandle){ throw 'Minecraft is not the foreground window - input aborted to avoid leaking into another app' } }
function MC-Type([string]$text) { MC-Guard; foreach($c in $text.ToCharArray()){ [Win32MC]::Unicode($c, $false); Start-Sleep -Milliseconds 8; [Win32MC]::Unicode($c, $true); Start-Sleep -Milliseconds 12 } }
# The test rigs: ELYSIUM_TEST_ROOT (default C:\testingbuilds) holds the dev client (Elysium) and the
# dedicated server (ElysiumServer); every stage reads the client's log through $script:McLog. The E: drive
# that held them was lost, and on 14 October 2026 the rigs were rebuilt on C:.
$script:McRoot = if ($env:ELYSIUM_TEST_ROOT) { $env:ELYSIUM_TEST_ROOT } else { 'C:\testingbuilds' }
$script:McClient = [System.IO.Path]::Combine($script:McRoot, 'Elysium')
$script:McServer = [System.IO.Path]::Combine($script:McRoot, 'ElysiumServer')
$script:McLog = [System.IO.Path]::Combine($script:McClient, 'logs', 'latest.log')
# Open chat with T, type a command, press Enter
function MC-CmdOnce([string]$cmd) { MC-Key 'T'; Start-Sleep -Milliseconds 700; MC-Key 'BACK'; MC-Key 'BACK'; MC-Key 'BACK'; Start-Sleep -Milliseconds 80; MC-Type $cmd; Start-Sleep -Milliseconds 250; MC-Key 'RETURN'; Start-Sleep -Milliseconds 400 }
# Under lag the chat box opens late and swallows the first letters, and the rest goes out as a
# chat line "<ElysiumTester> ...". Every command in this game answers in chat, so a command is
# done only when a system chat line follows it and no player line does; otherwise try again.
function MC-Cmd([string]$cmd) {
  for ($try = 0; $try -lt 4; $try++) {
    $n = 0; try { $n = (Get-Content $script:McLog -ErrorAction Stop).Count } catch {}
    MC-CmdOnce $cmd
    $verdict = 'none'
    for ($w = 0; $w -lt 24; $w++) {
      $lines = Get-Content $script:McLog -ErrorAction SilentlyContinue
      if ($lines.Count -gt $n) {
        $tail = $lines[$n..($lines.Count-1)] | Where-Object { $_ -match '\[CHAT\]' }
        if ($tail | Where-Object { $_ -match '\[CHAT\] <ElysiumTester> ' }) { $verdict = 'leak'; break }
        if ($tail | Where-Object { $_ -match 'Unknown or incomplete command' }) { $verdict = 'garbled'; break }
        if ($tail) { $verdict = 'ok'; break }
      }
      Start-Sleep -Milliseconds 250
    }
    if ($verdict -eq 'ok') { return }
    if ($verdict -eq 'none') { Write-Host "MC-Cmd unconfirmed (no chat answer): $cmd"; return }   # a silent success, or lag; never run it twice
    # A leak or a garbled command was SENT, so the chat box has already closed;
    # an ESC here would open the pause menu and swallow every command after it.
    Write-Host "MC-Cmd retry ($verdict): $cmd"
    Start-Sleep -Milliseconds 600
  }
  Write-Host "MC-Cmd: gave up on: $cmd"
}

function MC-Log([int]$last = 20, [string]$pattern = '/ERROR\]|/FATAL\]|Exception|Caused by|/WARN\].*(valoria|tridot)') {
  $l = Get-Content 'C:\ClaudeValoria\Valoria\run\logs\latest.log' -ErrorAction SilentlyContinue
  $l | Select-String -Pattern $pattern | Where-Object { $_.Line -notmatch 'subtitle translation|Reference map|mixin/|Class version|SpriteContents|SpriteLoader|Missing sound|Missing textures|Unable to load model' } | Select-Object -Last $last | ForEach-Object { $_.Line.Substring(0, [Math]::Min(300, $_.Line.Length)) }
}

# Get the game answering. A blind ESC is wrong half the time: it closes the pause
# menu if one is open and opens one if not. So ask the clock; if nothing answers
# within four seconds, press ESC and ask again.
function MC-Resume {
  for ($try = 0; $try -lt 4; $try++) {
    $n = 0; try { $n = (Get-Content $script:McLog -ErrorAction Stop).Count } catch {}
    MC-CmdOnce '/time query daytime'
    for ($w = 0; $w -lt 16; $w++) {
      $lines = Get-Content $script:McLog -ErrorAction SilentlyContinue
      if ($lines.Count -gt $n -and (($lines[$n..($lines.Count-1)] | Where-Object { $_ -match '\[CHAT\] The time is' }))) { return $true }
      Start-Sleep -Milliseconds 250
    }
    # a world saved with a dead player opens on the death screen, where ESC does nothing: click Respawn
    if ($try -eq 1) { MC-Click 800 494; Start-Sleep -Seconds 4 } else { MC-Key 'ESC'; Start-Sleep -Milliseconds 700 }
  }
  Write-Host "MC-Resume: the game never answered"; return $false
}
