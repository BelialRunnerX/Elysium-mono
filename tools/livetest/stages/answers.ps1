# How the Vale stages send their commands and read what each one answered (15 Oct; the steps, the
# new log, whose lines are whose and the game's own word for a crossing, 16 Oct; the stage's own
# typing, the line end kept across a new log, the gaps a new log leaves and the world's record read
# from the mod, round 5, 16 Oct). Dot-sourced after mc.ps1 by stage_vale_client.ps1 and
# stage_vale_footing_client.ps1; the stage defines Bad and Finish.
#
# The marker. The server answers one client's commands in the order they came, so every command a
# stage sends is followed at once by a marker of its own, /tellraw @s "elysium mark <pid> <n>", and
# the chat lines between the echo of the marker before it and the echo of its own are exactly that
# command's answers: nothing it says comes after its marker, nothing the next one says comes before.
# A command is judged only when its own marker is back. A marker not back within $AnswerSeconds of
# being typed fails its command, never passes it; the command stays open, so whatever it says late
# is still its own (shown as late), and before anything else is sent a lone marker - a fence - goes
# to shut those lines in. An echo of a later marker while an earlier one is not back means the
# earlier was lost: every command in that stretch is failed, since its answers cannot be told apart
# (a fence carries no command, so a lost fence costs nothing).
#
# One answer. A read wants exactly one line of its kind (or an error) in its window: none is a
# failure, and so are two, which is what a command run twice looks like. A step (a command whose
# answer is not read) wants an answer too: refused, or with nothing at all before its marker, it
# failed - a command typed into a screen says nothing, and nor, before its marker, does one whose
# letters came back late. Only a step sent -Quiet may say nothing: a /gamemode the player is already
# in rightly answers nothing, and /gamemode creative is the only one the stages send so.
# Player chat in a window is nobody's answer, and a garble whose text is not this command's is
# ignored. Every kind is matched whole, from the line's start to its end, and a read of the player
# names the player (round 5, PD1): an ordinary line of system chat can be shaped by names a player
# gives things, and outside the Vale a read that answers only there says nothing of its own, so a
# kind matched by its start alone took the death message of a pet renamed "ElysiumTester has the
# following entity data:" and slain by a mob named y: "elysiumwilds:vale" for the Vale's answer.
#
# Typed once. A marked command goes through Put, never MC-Cmd. MC-Cmd judges a command by
# whatever chat comes in its few seconds, which can be an earlier command's: a stale garble from one
# made it type the next one again, the doubled "Test passed" came late, and the waiting list this
# replaced gave it to the command after, whose own answer was "Test failed" - a false pass. The
# window says whose a garble is. A command typed while the chat box opens a beat late comes back
# either as "Unknown or incomplete command" with the rest of its own text before <--[HERE] (the game
# took the slash), or as this player's own chat line "<ElysiumTester> <the rest of it>" (the slash
# lost too; another player saying the same words is not it); that is a command the server did not
# run, and only then is it typed again, behind a new marker. A marker that comes back as letters is
# typed again under the same number; a second echo of a marker already back is nothing. mc.ps1 is
# left as it was: the other stages still use MC-Cmd and MC-CmdOnce.
#
# Typed with the game in front for every key (round 5, K1). Put presses what MC-CmdOnce presses (T,
# three BACKs, the letters, RETURN), but its keys before RETURN go through ElysiumKeys.Type, which
# asks which window is in front before the T, before each BACK, and before and after every letter,
# and stops at the first answer that is not the game. MC-Type asks only before the first letter, so
# a key sent while another window held the focus for a moment (a console window flashing up) went
# to that window, and RETURN, which is guarded, sent the command short of it: a coordinate short of
# one digit is still a command, about another block ("unless block 915 70 -265 #minecraft:air"
# reached the server as 915 7 -265, dirt, and passed over a hollow of cave air). The game never
# echoes a command's text, so no line read back can show it. So a stage whose typing found another
# window in front says where, and stops at once, RETURN never pressed and nothing more typed.
# RETURN goes through MC-Key, whose guard throws when the game is not in front.
#
# The log. It is read by byte offset and only up to its last whole line: the game may be half way
# through writing one, and a line read half written is a different line (the echo of marker 12, cut
# short, is marker 1's). A chat line is a line of the client's chat component and nothing else: the
# integrated server logs every chat message on its own thread, and a player can say "[CHAT] Test
# passed". The client's chat component writes a message's own line ends as the two letters \n, so
# one message is one [CHAT] line; but the integrated server logs some text as it is, line breaks and
# all (MinecraftServer.sendSystemMessage and logChatMessage, LivingEntity's "Named entity {} died:
# {}"; round 4, 16 Oct), so a custom name holding line breaks can write a whole line of its own,
# with any stamp, thread and logger, into this same log. Two guards. log4j ends every event with
# %n, which is CR LF on Windows, so in a log whose lines end CR LF a line that follows one ending in
# a bare LF is inside some message, and is read as neither a chat line nor a first-trip line (a name
# holding CR LF gets past this one). Which end the log's lines have is decided once, where reading
# starts, by whether any line in the 64 KiB before that place ends CR LF, and kept from then on, a
# new log included, since the same game writes its new log the same way (round 5, G1 and G2). Not
# by one line: the line just before where reading starts can be the inside of a message the game
# is still writing, and a new log's first line is the event that rolled the old one over, which can
# be a named mob's death ending in the bare LF inside its name; either alone turned the guard off
# for the rest of the run. A log empty where reading starts is decided by the first lines read, by
# whether any of them ends CR LF. A log of bare LFs is read without this guard. And the footing
# stage backs its first-trip line with the world's own record of first trips, which no log line can
# write (below). Every pattern here is matched case-sensitively (-cmatch): the game writes these
# words one way.
# The log is known by its first bytes and by the last bytes read: a new log (the old one rolled over
# at midnight, or the client restarted) is read from its start, whatever its length, and every
# command still open then is failed and never credited, since what it said may be in the old log,
# unread. (Its creation time is no help on Windows: a file made where one was just renamed away
# takes the old one's.) What the old log held unread is lost for every kind of line, not only for
# the answers of open commands: where each new log began among the lines read is kept ($script:Gaps,
# round 5, RZ1), and a check that rests on a line not having come (the vale stage's way out with no
# way in on record) asks GapSince first. A rollover 0.3 s after the mod's line for a way out with no
# way in on record, with no command open, left that line in the old log, and the stage passed.
#
# The world's record (round 5, OP1 and RZ1). The mod keeps in its own saved data (ValeLinks,
# elysium-wilds), which no command can write, how many first trips the world has had, the line the
# latest of them logged, and for each player how many ways out of the Vale put them at the world's
# spawn for want of a way in on record; /elysium vale trips (ValeCommand) reads them back as one line,
#   Vale trips: first N, out to spawn M, last LINE
# for the player who asks, LINE the latest first-trip line word for word, or none before the first.
# Record reads it with a marked Ask. Until round 5 the first trips were kept in command storage
# elysiumwilds:trips, which /data modify writes: the operator who can give a mob a name with line
# breaks round a copy of an old first-trip line could set that record to agree from a command block.
#
# What the stages do not stand against. An operator can move the player, set any block, and name a
# mob with CR LF in its name, which writes whole lines of any shape into this log past the bare-LF
# guard, so that a line can stand for an answer where a command said nothing. The stages are proof
# against what the game, lag, the log's rollover and ordinary players do, and the world's record of
# its trips is proof against any command; against an operator who sets out to fool them, the
# stages are not. (Asking each in-Vale read a second way, so that it answers either way and a forged
# line makes two answers, would put two commands before one marker; only an operator can forge a
# whole line, and an operator can move the player into the Vale anyway.)
#
# The game's own word for a crossing (16 Oct). A ring's first trip logs one line from the
# integrated server's thread (ValeTravel, elysium-wilds; the ElysiumWilds logger):
#   Vale first trip: player NAME ring DIMENSION X Y Z side X Y Z axis x|z built|standing
# The client's log has one of two layouts. NeoForge's own log4j2.xml (in the FML loader jar) writes
# "[ddMMMyyyy HH:mm:ss.SSS] [thread/LEVEL] [logger/marker]: message", so the line is
# "[...] [Server thread/INFO] [ElysiumWilds/]: Vale first trip: ..."; the vanilla launcher's
# client-1.12.xml, which the test client's launch.ps1 passes (-Dlog4j.configurationFile), writes
# "[HH:mm:ss] [thread/LEVEL]: message" with no logger at all, so there it is
# "[...] [Server thread/INFO]: Vale first trip: ...". Either way the message must start right after
# the "]: " of a Server thread INFO line, from the ElysiumWilds logger where a logger is written.
# A player saying the same words is no such line: the server logs chat on its thread as
# "<NAME> text" (or "[Not Secure] <NAME> text"), /say as "[NAME] text", a command's feedback as
# "[NAME: text]", all under net.minecraft.server.MinecraftServer where a logger is written, and the
# client logs it on the Render thread as [CHAT]. Nothing else is read as a crossing: the client's
# "Loaded N advancements" comes only with an advancement update packet, and a change of world sends
# none (attack_r3, 16 Oct), so it is not read at all. A first-trip line is kept with the place it
# stood among the lines read, so Stand takes only a line read after its own fence's echo. So is the
# mod's line for this player's way out with no way in on record (ValeTravel.leave: "NAME left the
# Vale by a gate with no way in on record; putting them at the world's spawn"), which the vale stage
# refuses.
$Me = 'ElysiumTester'  # the client's player (its launch.ps1's -Username): only its own chat lines are its own letters
# The kinds of answer, each matched whole (see the header): an execute test (with its count, for a
# test of entities), a data read of the player, a fill, and the mod's record of the world's trips,
# whose last line is a first-trip line in the mod's own form or none ($Matches[1..3]).
$Verdict = '^Test (passed|failed)(, count: \d+)?$'
$Data = '^' + [regex]::Escape($Me) + ' has the following entity data: \S.*$'
$Filled = '^(Successfully filled \d+ block\(s\)|No blocks were filled)$'
$RecordLine = '^Vale trips: first (\d+), out to spawn (\d+), last (none|Vale first trip: player \S+ ring \S+ -?\d+ -?\d+ -?\d+ side -?\d+ -?\d+ -?\d+ axis [xz] (?:built|standing))$'
# The errors any command can answer with in place of its own kind of answer.
$Errors = '^(That position is not loaded|Incorrect argument|Invalid|Expected|Too many blocks|Can.t find|Unknown (block|item|biome|dimension|entity|tag)|No (entity|player) was found)'
$AnswerSeconds = 10    # a marker not back this long after it was typed fails its command
$Tries = 4             # a command that came back as letters is typed again, up to this many times in all
# A line the client's chat component logged, in NeoForge's layout (the logger named) or the vanilla
# one (none): a tag such as [System] or [Not Secure], then [CHAT] and the message, $Matches[1].
$ChatLine = '^\[[^\]]+\] \[Render thread/INFO\](?: \[net\.minecraft\.client\.gui\.components\.ChatComponent/[^\]]*\])?: (?:\[(?!CHAT\])[^\]]+\] )?\[CHAT\] (.*)$'
# A ring's first trip, as the mod logs it (see the header), in either layout: $Matches[1] the player,
# [2] the ring's dimension, [3..5] its anchor, [6..8] the Vale side's anchor, [9] its axis, [10]
# built (new, for this trip) or standing (a ring the search found there).
$TripLine = '^\[[^\]]+\] \[Server thread/INFO\](?: \[ElysiumWilds/\])?: Vale first trip: player (\S+) ring (\S+) (-?\d+) (-?\d+) (-?\d+) side (-?\d+) (-?\d+) (-?\d+) axis ([xz]) (built|standing)$'
$NoWayIn = ' left the Vale by a gate with no way in on record'   # the mod's way out to the world's spawn (see the header)
$HeadBytes = 256       # how much of the log's start it is known by
$BackBytes = 64        # and how much of what was read last
$script:Open = New-Object System.Collections.ArrayList   # commands whose marker is not back yet, oldest first
$script:Marks = 0
$script:MarkLine = '^elysium mark ' + $PID + ' (\d+)$'
$script:LinesRead = [long]0   # whole lines read so far: where each line stands in the order the log has them
$script:TripsRead = New-Object System.Collections.ArrayList  # every first-trip line read, with where it stood
$script:NoWayRead = New-Object System.Collections.ArrayList  # where each line of a way out with no way in on record stood
$script:Gaps = New-Object System.Collections.ArrayList       # where each new log began among the lines read: what the old one held unread is lost
$script:LogCrlf = $null     # this log's own line end is CR LF (log4j on Windows, see the header): $null until known, then kept
$script:PrevBare = $false   # the line read last ended in a bare LF: in such a log, the next is inside a message
$script:LastFence = $null   # the last fence sent, so a caller can ask where its echo stood
$script:GateEcho = [long]::MaxValue   # where Stand's fence's echo stood: a first trip counts only if read after it (none, before one is back)
$script:Took = 0
$script:Entry = $null  # the last Ask's command, as Fill reads it
$script:Said = ''
$script:Why = ''
$script:Head = ''; $script:HeadLen = 0   # the log's first bytes as read, base64
$script:Back = ''; $script:BackLen = 0   # the bytes just before $script:At, base64

function LogShare { [System.IO.FileShare]([System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete) }
function LogOpen { [System.IO.File]::Open($script:McLog, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, (LogShare)) }
# Where an open log's last whole line ends: the place reading starts, so a line half written now is
# read whole later, as an old one.
function LineEnd($fs) {
    $len = $fs.Length
    $from = [Math]::Max([long]0, $len - 65536)
    [void]$fs.Seek($from, [System.IO.SeekOrigin]::Begin)
    $buf = New-Object byte[] ([int]($len - $from))
    $got = 0
    while ($got -lt $buf.Length) { $k = $fs.Read($buf, $got, $buf.Length - $got); if ($k -le 0) { break }; $got += $k }
    if ($got -eq 0) { return [long]$from }
    $i = [Array]::LastIndexOf($buf, [byte]10, $got - 1)
    if ($i -ge 0) { return [long]($from + $i + 1) }
    return [long]$len
}
function LogEnd { $fs = $null; try { $fs = LogOpen; LineEnd $fs } catch { [long]0 } finally { if ($fs) { $fs.Dispose() } } }
# $n bytes of an open log from $at, as text to compare: '' when they are not all there.
function LogBytes($fs, [long]$at, [int]$n) {
    if ($n -le 0 -or $at -lt 0) { return '' }
    $buf = New-Object byte[] $n
    [void]$fs.Seek($at, [System.IO.SeekOrigin]::Begin)
    $got = 0
    while ($got -lt $n) { $k = $fs.Read($buf, $got, $n - $got); if ($k -le 0) { break }; $got += $k }
    if ($got -lt $n) { return '' }
    [Convert]::ToBase64String($buf)
}
# Whether any line of an open log in the 64 KiB before $at ends CR LF (see the header): log4j ends
# every event so on Windows, and only a message's own line breaks end in a bare LF.
function TailCrlf($fs, [long]$at) {
    $from = [Math]::Max([long]0, $at - 65536)
    $n = [int]($at - $from)
    if ($n -lt 2) { return $false }
    $buf = New-Object byte[] $n
    [void]$fs.Seek($from, [System.IO.SeekOrigin]::Begin)
    $got = 0
    while ($got -lt $n) { $k = $fs.Read($buf, $got, $n - $got); if ($k -le 0) { break }; $got += $k }
    for ($i = 1; $i -lt $got; $i++) { if ($buf[$i] -eq 10 -and $buf[$i - 1] -eq 13) { return $true } }
    $false
}
# Where reading starts, and what the log is known by, through one handle.
function LogBegin {
    $fs = $null
    try {
        $fs = LogOpen
        $at = LineEnd $fs
        $hl = [int][Math]::Min([long]$HeadBytes, $at); $head = LogBytes $fs 0 $hl
        $bl = [int][Math]::Min([long]$BackBytes, $at); $back = LogBytes $fs ($at - $bl) $bl
        $script:At = $at; $script:HeadLen = $hl; $script:Head = $head; $script:BackLen = $bl; $script:Back = $back
        # The log's line end, from every line end in its last 64 KiB (see the header), and how the line
        # just before the first one read ended: in a CR LF log, one ending in a bare LF means the first
        # line read is the rest of a message the game is still writing, inside it.
        $script:LogCrlf = if ($at -ge 1) { TailCrlf $fs $at } else { $null }
        $end = if ($at -ge 2) { LogBytes $fs ($at - 2) 2 } else { '' }
        $script:PrevBare = ($at -ge 1) -and ($end -cne [Convert]::ToBase64String([byte[]](13, 10)))
    } catch { $script:At = [long]0 } finally { if ($fs) { $fs.Dispose() } }
}
$script:At = [long]0   # bytes of the log read, always to the end of a whole line
LogBegin
# A new log: what a command still open said may be in the old one, unread, so none of them is ever
# credited. They stay open, so whatever they still say in the new one is shut in as theirs. Where the
# new log began among the lines read is kept: every line the old one held unread is lost, whatever
# its kind (see the header).
function Forget {
    Write-Host "  a new log (the old one rolled over, or the client restarted): read from its start, and every command still open is failed"
    foreach ($o in $script:Open) { $o.Gone = $true; if ($o.Cmd) { Write-Host "  never credited: $($o.Cmd)" } }
    [void]$script:Gaps.Add($script:LinesRead)
}
# Whether a new log began after the log line at $after: a line written since then may be lost with
# the old log, so no check may rest on its not having been read.
function GapSince([long]$after) { @($script:Gaps | Where-Object { $_ -ge $after }).Count -gt 0 }
# The whole lines written since the last read, each with its CR if it ended CR LF; a last line
# without its line end is left for the next.
function LogTail {
    $fs = $null
    try {
        $fs = LogOpen
        $len = $fs.Length
        $new = $len -lt $script:At
        if (-not $new -and $script:HeadLen -gt 0) { $new = (LogBytes $fs 0 $script:HeadLen) -ne $script:Head }
        if (-not $new -and $script:BackLen -gt 0) { $new = (LogBytes $fs ($script:At - $script:BackLen) $script:BackLen) -ne $script:Back }
        # A new log keeps the line end the old one had (see the header): the same game writes it.
        if ($new) { Forget; $script:At = [long]0; $script:HeadLen = 0; $script:Head = ''; $script:BackLen = 0; $script:Back = ''; $script:PrevBare = $false }
        if ($len -le $script:At) { return }
        [void]$fs.Seek($script:At, [System.IO.SeekOrigin]::Begin)
        $buf = New-Object byte[] ([int]($len - $script:At))
        $got = 0
        while ($got -lt $buf.Length) { $k = $fs.Read($buf, $got, $buf.Length - $got); if ($k -le 0) { break }; $got += $k }
        if ($got -le 0) { return }
        $end = [Array]::LastIndexOf($buf, [byte]10, $got - 1)
        if ($end -lt 0) { return }
        $at = $script:At + $end + 1
        $hl = [int][Math]::Min([long]$HeadBytes, $at); $head = LogBytes $fs 0 $hl
        $bl = [int][Math]::Min([long]$BackBytes, $at); $back = LogBytes $fs ($at - $bl) $bl
    } catch { return } finally { if ($fs) { $fs.Dispose() } }
    $script:At = $at; $script:HeadLen = $hl; $script:Head = $head; $script:BackLen = $bl; $script:Back = $back
    $parts = [System.Text.Encoding]::UTF8.GetString($buf, 0, $end + 1).Split([char]10)
    for ($i = 0; $i -lt $parts.Length - 1; $i++) { $parts[$i] }
}
# Every line come since the last read: a marker's echo shuts its window, and any other chat line
# goes to the oldest command whose marker is not back - in order, so it is that command's. A first-
# trip line is kept with the place it stood, so a caller can ask whether it came after a given echo,
# and with its message (Text), which the footing stage holds against the world's record. A line
# inside a message (one after a bare LF in a CR LF log, see the header) is neither.
function Drain {
    $batch = @(LogTail)
    # A log empty where reading started: every line end in hand says, never one line (see the header).
    if ($null -eq $script:LogCrlf -and $batch.Count -gt 0) { $script:LogCrlf = @($batch | Where-Object { $_.EndsWith([string][char]13) }).Count -gt 0 }
    foreach ($raw in $batch) {
        $script:LinesRead++
        $crlf = $raw.EndsWith([string][char]13)
        $line = $raw.TrimEnd([char]13)
        $inside = $script:LogCrlf -and $script:PrevBare
        $script:PrevBare = -not $crlf
        if ($line.Contains($Me + $NoWayIn)) { [void]$script:NoWayRead.Add($script:LinesRead) }   # this player's, wherever it stands: refusing is only safer
        if ($inside) { continue }
        if ($line -cmatch $TripLine) {
            [void]$script:TripsRead.Add(@{ At = $script:LinesRead; Player = $Matches[1]; Dim = $Matches[2]; Ring = "$($Matches[3]) $($Matches[4]) $($Matches[5])"; Side = "$($Matches[6]) $($Matches[7]) $($Matches[8])"; Axis = $Matches[9]; How = $Matches[10]; Text = $line.Substring($line.IndexOf(': Vale first trip: ', [StringComparison]::Ordinal) + 2) })
            continue
        }
        if (-not ($line -cmatch $ChatLine)) { continue }
        $text = $Matches[1]
        if ($text -cmatch $script:MarkLine) { Shut ([int]$Matches[1]); continue }
        if ($script:Open.Count -gt 0) { [void]$script:Open[0].Lines.Add($text) }
    }
}
function Shut([int]$n) {
    $at = -1
    for ($j = 0; $j -lt $script:Open.Count; $j++) { if ($script:Open[$j].Mark -eq $n) { $at = $j; break } }
    if ($at -lt 0) { return }                                   # a marker typed again, its first echo already read
    $lines = $script:Open[0].Lines                              # all that came since the last marker back
    $mixed = $false
    for ($j = 0; $j -lt $at; $j++) {
        $o = $script:Open[$j]; $o.State = 'lost'
        if ($o.Cmd) { $mixed = $true; Write-Host "  never came back: the marker after $($o.Cmd)" } else { Write-Host '  never came back: a fence' }
    }
    $e = $script:Open[$at]
    $e.Lines = $lines
    $e.State = if ($mixed) { 'mixed' } else { 'back' }
    $e.EchoAt = $script:LinesRead                                    # where its echo stood in the log
    $script:Open.RemoveRange(0, $at + 1)
    if ($e.Judged) {
        Judge $e
        $e.Late = $e.Said
        Write-Host ("  late answer to " + $e.Cmd + " -> " + $(if ($e.Said) { $e.Said } else { "($($e.Why))" }))
    }
}
function Ends([string]$whole, [string]$part) { $p = $part.Trim(); ($p.Length -ge 3) -and $whole.EndsWith($p, [StringComparison]::Ordinal) }
# How many times $typed came back as letters, by its own text: "Unknown or incomplete command" and
# the rest of it before <--[HERE], or this player's own chat line of the rest of it.
function Garbles([string]$typed, $lines) {
    $bare = $typed.TrimStart('/')
    $mine = '^<' + [regex]::Escape($Me) + '> (.+)$'
    $n = 0
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $t = [string]$lines[$i]
        if ($t -match $mine) { if (Ends $bare $Matches[1]) { $n++ } }
        elseif ($t -match '^Unknown or incomplete command' -and $i + 1 -lt $lines.Count -and ([string]$lines[$i + 1]) -match '^(?:\.\.\.|\u2026)?(.+)<--\[HERE\]$') { if (Ends $bare $Matches[1]) { $n++ } }
    }
    $n
}
# A command's window, read: $e.Said its one answer, or $e.Why why there is none to take; $e.Garbled
# when it came back as letters with no answer, so was not run. With no kind (a step), any answer but
# an error will do; none at all is a failure, unless the step was sent -Quiet ($e.Quiet).
function Judge($e) {
    $e.Said = ''; $e.Why = ''; $e.Garbled = $false
    if ($e.Gone) { $e.Why = 'the log started anew while it was open, so its answers cannot be read'; return }
    if ($e.State -eq 'open') { $e.Why = "its marker was not back after $AnswerSeconds s"; return }
    if ($e.State -eq 'lost') { $e.Why = 'its marker never came back, and a later one did'; return }
    if ($e.State -eq 'mixed') { $e.Why = 'an older marker never came back, so its answers cannot be told from an older command''s'; return }
    $own = Garbles $e.Cmd $e.Lines
    $answers = New-Object System.Collections.ArrayList
    $other = New-Object System.Collections.ArrayList
    for ($i = 0; $i -lt $e.Lines.Count; $i++) {
        $t = [string]$e.Lines[$i]
        if ($t -match '^<[^>]+> ') { continue }                                             # player chat: no answer
        if ($t -match '^Unknown or incomplete command') {                                    # a garble and its [HERE] line
            if ($i + 1 -lt $e.Lines.Count -and ([string]$e.Lines[$i + 1]) -match '<--\[HERE\]$') { $i++ }
            continue
        }
        if (($e.Kind -and $t -cmatch $e.Kind) -or $t -cmatch $Errors) { [void]$answers.Add($t) } else { [void]$other.Add($t) }
    }
    if ($own -gt 0 -and $answers.Count -eq 0) { $e.Garbled = $true; $e.Why = 'came back as letters, not run'; return }
    if ($e.Kind) {
        if ($own -gt 0) { $e.Why = "answered, and came back as letters too: $($answers -join ' | ')"; return }
        if ($answers.Count -eq 0) { $e.Why = 'no answer before its marker'; return }
        if ($answers.Count -gt 1) { $e.Why = "$($answers.Count) answers where one was due: $($answers -join ' | ')"; return }
        $e.Said = [string]$answers[0]
        return
    }
    if ($answers.Count -gt 0) { $e.Why = "refused: $($answers -join ' | ')"; return }
    if ($other.Count -gt 0) { $e.Said = [string]$other[0]; return }
    if ($e.Quiet) { $e.Said = '(no answer: sent -Quiet, so it may say nothing)'; return }
    $e.Why = 'no answer before its marker'
}
# The keys of one command before its RETURN, as MC-CmdOnce presses them (T, a pause for the chat box,
# three BACKs, the letters one at a time), with the window in front asked before the T, before each
# BACK, and before and after every letter (see the header). Type answers '' when the game was in
# front at every ask, or where it was not; nothing is pressed after the first ask it fails.
if (-not ([System.Management.Automation.PSTypeName]'ElysiumKeys').Type) {
Add-Type @"
using System;
using System.Runtime.InteropServices;
using System.Threading;
public static class ElysiumKeys {
  [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] inputs, int size);
  [DllImport("user32.dll")] static extern uint MapVirtualKey(uint code, uint type);
  [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Explicit)] struct INPUTUNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
  [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public INPUTUNION u; }
  static void Send(ushort vk, ushort scan, uint flags) {
    INPUT[] inp = new INPUT[1]; inp[0].type = 1;
    inp[0].u.ki.wVk = vk; inp[0].u.ki.wScan = scan; inp[0].u.ki.dwFlags = flags;
    SendInput(1, inp, Marshal.SizeOf(typeof(INPUT)));
  }
  // A key by its scan code, held 60 ms, as mc.ps1's MC-Key presses it.
  static void Press(ushort vk) {
    ushort scan = (ushort)MapVirtualKey(vk, 0);
    Send(vk, scan, 0x0008); Thread.Sleep(60); Send(vk, scan, 0x0008 | 0x0002); Thread.Sleep(80);
  }
  public static string Type(IntPtr game, string text) {
    if (GetForegroundWindow() != game) return "before the T that opens the chat box";
    Press(0x54); Thread.Sleep(700);
    for (int b = 1; b <= 3; b++) {
      if (GetForegroundWindow() != game) return "before BACK " + b + " of 3";
      Press(0x08);
    }
    Thread.Sleep(80);
    for (int i = 0; i < text.Length; i++) {
      if (GetForegroundWindow() != game) return "before letter " + (i + 1) + " of " + text.Length;
      Send(0, text[i], 0x0004); Thread.Sleep(8); Send(0, text[i], 0x0004 | 0x0002); Thread.Sleep(12);
      if (GetForegroundWindow() != game) return "after letter " + (i + 1) + " of " + text.Length;
    }
    Thread.Sleep(250);
    if (GetForegroundWindow() != game) return "after the last letter, before RETURN";
    return "";
  }
  // The pause after RETURN, as MC-CmdOnce's, while the chat box closes.
  public static void Rest(int ms) { Thread.Sleep(ms); }
}
"@
}
# A command typed into the game and sent (see the header). Another window in front at any ask
# stops the stage there: RETURN is not pressed, so what was typed is not sent, and nothing more is
# typed, since a key sent now may go to whatever has the focus.
function Put([string]$typed) {
    MC-Focus | Out-Null
    $w = MC-Window
    $away = if ($w) { [ElysiumKeys]::Type($w.MainWindowHandle, $typed) } else { 'with no Minecraft window at all' }
    if ($away) {
        Bad "the game's window was not in front $away while $typed was typed, so a key may have gone to another window and what reached the chat box is not known: it was not sent, and the stage stops here"
        Finish
    }
    MC-Key 'RETURN'
    [ElysiumKeys]::Rest(400)
}
function Entry([string]$cmd, [string]$kind) {
    $script:Marks++
    $text = "elysium mark $PID $($script:Marks)"
    $e = @{ Cmd = $cmd; Kind = $kind; Mark = $script:Marks; MarkCmd = ('/tellraw @s "' + $text + '"'); State = 'open'; Lines = (New-Object System.Collections.ArrayList); Sent = (Get-Date); Judged = $false; Resent = 0; Said = ''; Why = ''; Garbled = $false; Late = ''; Gone = $false; Quiet = $false; EchoAt = [long]-1 }
    [void]$script:Open.Add($e)
    $e
}
function Mark($e) { Put $e.MarkCmd; $e.Sent = Get-Date }
# Waited for: its marker back, or the time up, or a new log. A marker that came back as letters is
# typed again.
function Await($e) {
    Drain
    while ($e.State -eq 'open' -and -not $e.Gone -and ((Get-Date) - $e.Sent).TotalSeconds -lt $AnswerSeconds) {
        Start-Sleep -Milliseconds 250
        Drain
        if ($e.State -ne 'open' -or $e.Gone -or $e.Resent -ge 2 -or $script:Open.Count -eq 0) { continue }
        if ((Garbles $e.MarkCmd $script:Open[0].Lines) -gt $e.Resent) {
            $e.Resent++
            Write-Host "  the marker $($e.Mark) came back as letters: typed again"
            Mark $e
        }
    }
}
# A lone marker: everything said before its echo is shut in behind it, and the next window starts clean.
function Fence {
    $f = Entry '' ''
    $script:LastFence = $f
    Mark $f
    Await $f
    if ($f.State -eq 'open' -and -not $f.Gone) { Write-Host "  a fence not back after $AnswerSeconds s: the game is not answering" }
}
# A command typed once, behind a fence if an older marker is not back, and its own marker after it
# (held back with -Hold until the caller sends it with Mark).
function Post([string]$cmd, [string]$kind, [switch]$Hold) {
    Drain
    if ($script:Open.Count -gt 0) { Fence }
    $e = Entry $cmd $kind
    Put $cmd
    if (-not $Hold) { Mark $e }
    $e
}
# A command and its own one answer of a kind, in $script:Said ('' when it has none, and $script:Why
# says why). One not back in time is failed here; its late answer is still its own, and nobody else's.
function Ask([string]$cmd, [string]$kind) {
    for ($try = 1; $try -le $Tries; $try++) {
        $e = Post $cmd $kind
        Await $e
        Judge $e
        if (-not $e.Garbled) { break }
        Write-Host "  came back as letters, not run: $cmd (try $try of $Tries)"
    }
    if ($e.State -eq 'open') { $e.Judged = $true }
    $script:Entry = $e
    $script:Said = $e.Said; $script:Why = $e.Why
    $shown = if ($script:Said) { $script:Said } else { "($($script:Why))" }
    Write-Host ("  " + $cmd + " -> " + $shown.Substring(0, [Math]::Min(240, $shown.Length)))
}
# A command whose answer is not read but whose going through is: refused, not back in time, never
# run, or with no answer at all before its marker, it is a failure. -Quiet lets it say nothing (a
# /gamemode the player is already in answers nothing); only /gamemode creative is sent so.
function Step([string]$cmd, [switch]$Quiet) {
    for ($try = 1; $try -le $Tries; $try++) {
        $e = Post $cmd ''
        $e.Quiet = [bool]$Quiet
        Await $e
        Judge $e
        if (-not $e.Garbled) { break }
        Write-Host "  came back as letters, not run: $cmd (try $try of $Tries)"
    }
    if ($e.State -eq 'open') { $e.Judged = $true }
    if ($e.Why) { Bad "a step did not go through ($cmd): $($e.Why)" }
}
# A fill over chunks not loaded yet answers "That position is not loaded": asked again until it is
# done. Not answered in time, it is asked again too (a fill twice is the same fill), but first a fence
# lets a try still open come back: a try whose marker comes back late answered for itself - done, so
# not typed again, or refused. Any other error is final.
function Fill([string]$cmd) {
    $missed = New-Object System.Collections.ArrayList
    for ($try = 0; $try -lt 8; $try++) {
        Ask $cmd $Filled
        if ($script:Said -cmatch $Filled) { return }
        if ($script:Said -and $script:Said -notmatch '^That position is not loaded') { Bad "a fill was refused ($cmd): $($script:Said)"; return }
        if ($script:Entry.Judged) { [void]$missed.Add($script:Entry) }
        Start-Sleep -Seconds 5
        Drain
        if ($script:Open.Count -gt 0) { Fence }
        foreach ($m in $missed) {
            if ($m.Late -cmatch $Filled) { return }
            if ($m.Late -and $m.Late -notmatch '^That position is not loaded') { Bad "a fill was refused ($cmd): $($m.Late)"; return }
        }
    }
    Bad "a fill never went through ($cmd)"
}
# The first-trip lines read for this player and the ring whose anchor is $ring ("x y z") in the
# overworld, read after the log line at $after: the mod's word that this ring's first trip was made,
# and where its Vale side stands. Another player's line, another ring's, one from another dimension,
# or one read before $after, says nothing about this trip.
function TripsFor([string]$ring, [long]$after) {
    foreach ($t in $script:TripsRead) {
        if ($t.At -gt $after -and $t.Player -ceq $Me -and $t.Dim -ceq 'minecraft:overworld' -and $t.Ring -ceq $ring) { $t }
    }
}
# How many of this player's lines of a way out with no way in on record (the mod's, see the header)
# were read after the log line at $after: any at all says a gate put them at the world's spawn.
function NoWayInSince([long]$after) { @($script:NoWayRead | Where-Object { $_ -gt $after }).Count }
# Into the gate, and the ring's first trip waited for in the log rather than asked for (16 Oct): the
# mod's first-trip line for this player and the ring at $ring, read after the echo of a fence sent
# just before the step ($script:GateEcho, where that echo stood), so no line from before it - an
# earlier run's, or another trip's - can stand for this one. A fence not back is the game not
# answering, and the gate is then not stepped into at all. The step's marker is held back until the
# line is read or $seconds are up, and the landing has had ten seconds or five: letters typed while the
# world changes go to the game, and an S among them ("tellraw @s") is a step back into the return
# ring. Typed as letters (so not run), it is typed again - but never once the line has been read, at
# any time since the first try: it is looked for again after the marker is back, since it can come
# after the letters ended the wait (a tp typed again in the Vale moves the player off its landing).
# Only a ring's first trip logs the line, so a ring that has been through before waits out the whole
# hold; that is no failure here, and the caller asks the game where the player is (InVale) either
# way. It says whether the line was read, and $script:Took how long the wait took.
function Stand([string]$cmd, [int]$seconds, [string]$ring) {
    $script:GateEcho = [long]::MaxValue; $script:Took = 0
    Fence
    $f = $script:LastFence
    if ($f.State -ne 'back' -or $f.Gone) { Bad "the fence before the gate did not come back, so the game is not answering and the gate was not stepped into ($cmd)"; return $false }
    $script:GateEcho = $f.EchoAt
    $seen = $false
    for ($try = 1; $try -le $Tries; $try++) {
        $e = Post $cmd '' -Hold
        $t0 = Get-Date
        do {
            Start-Sleep -Seconds 2
            Drain
            $seen = @(TripsFor $ring $script:GateEcho).Count -gt 0
            $letters = ($script:Open.Count -gt 0) -and ((Garbles $cmd $script:Open[0].Lines) -gt 0)
        } until ($seen -or $letters -or ((Get-Date) - $t0).TotalSeconds -ge $seconds)
        $script:Took = [int]((Get-Date) - $t0).TotalSeconds
        # The line is logged just before the player is moved, so the landing has ten seconds after it (the
        # client loads the Vale round the landing; the cooldown); a hold that ran out is long past any move.
        Start-Sleep -Seconds $(if ($seen) { 10 } else { 5 })
        Mark $e
        Await $e
        Judge $e
        $seen = @(TripsFor $ring $script:GateEcho).Count -gt 0
        if ($seen) { break }                                    # its first trip read: never typed again
        if (-not $e.Garbled) { break }
        Write-Host "  came back as letters, not run: $cmd (try $try of $Tries)"
    }
    if ($e.State -eq 'open') { $e.Judged = $true }
    if ($e.Why) { Bad "the step into the gate did not go through ($cmd): $($e.Why)" }
    $seen
}
# Whether the game says the player is in the Vale, asked until it does or $seconds are up. Each ask
# is a marked read that answers only there (/execute if dimension elysiumwilds:vale run data get
# entity @s Dimension): anywhere else it says nothing, which is no, and so is any answer but this
# player's own whole line naming the Vale (round 5, PD1: a line that only ended so was a pet's
# death message, its name and its killer's shaped to look like one).
function InVale([int]$seconds) {
    $t0 = Get-Date
    while ($true) {
        Ask '/execute if dimension elysiumwilds:vale run data get entity @s Dimension' $Data
        if ($script:Said -ceq ($Me + ' has the following entity data: "elysiumwilds:vale"')) { return $true }
        if (((Get-Date) - $t0).TotalSeconds -ge $seconds) { return $false }
        Start-Sleep -Seconds 3
    }
}
# The world's record of its trips, read from the mod (see the header) with a marked Ask: First the
# first trips the world has had, Spawned this player's ways out of the Vale to the world's spawn for
# want of a way in on record, Last the latest first trip's line word for word ('' before the first);
# or $null when it was not read.
function Record {
    Ask '/elysium vale trips' $RecordLine
    if (-not ($script:Said -cmatch $RecordLine)) { return $null }
    @{ First = [int]$Matches[1]; Spawned = [int]$Matches[2]; Last = $(if ($Matches[3] -ceq 'none') { '' } else { $Matches[3] }) }
}
# MC-Resume types its clock reads unmarked; a fence after it shuts in whatever they still say.
function Resume { MC-Focus | Out-Null; [void](MC-Resume); Fence }
