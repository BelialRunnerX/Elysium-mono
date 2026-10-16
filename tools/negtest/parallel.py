#!/usr/bin/env python3
"""
Run many negative-test scripts at once, each in its own copy of the tree.

    python3 tools/negtest/parallel.py negtest_quick_move.sh negtest_spawners.sh
    python3 tools/negtest/parallel.py -j 4 --list scripts.txt
    python3 tools/negtest/parallel.py --all --plan     rewrite every script, run nothing
    python3 tools/negtest/parallel.py --resume .../summary.json   run again what was not ok

Every script in tools/negtest was written to run alone, in /tmp/Elysium-mono
itself: it snapshots whole modules with tar, breaks them, puts them back, and
keeps its scratch at fixed names (/tmp/h.bak, /tmp/<module>_snap.tar, /tmp/_ns,
lock dirs). Two at once in one tree, or two that share a scratch name, would
spoil each other, so until now they ran one at a time and the corpus took more
than a day. Here each worker has a world of its own:

  C:/Users/weaks/Elysium-env/negwork/w<N>/tree      a detached git worktree of the base
                                                    (N: the lowest numbers whose junction
                                                    below is free; another run's are skipped)
  .../w<N>/tmp                                      its /tmp: emptied before every script
  .../w<N>/classes, .../w<N>/loadtest               its build outputs (a copy of the
                                                    compiled classes of the base)
  .../w<N>/site/sitecustomize.py                    see "hard-coded paths in Python" below
  %TEMP%/elysium-negw<N>  ->  .../w<N>              a junction, so that every path can be
                                                    spelt /tmp/elysium-negw<N>/...

The junction is the trick that makes the rest simple. /tmp/Elysium-mono reaches
the real tree because Git Bash's /tmp is %TEMP%, Windows Python reads /tmp as
C:/tmp, C:/tmp is a junction to %TEMP%, and %TEMP%/Elysium-mono is a junction
to the tree. A spelling under the same /tmp keeps all of that true, and it has
no colon in it: tar reads "C:/x.tar" as a file on a remote host called C and
fails, so a drive-letter path cannot stand in for a /tmp one in these scripts.

Each script is rewritten into the worker's scratch before it runs:

  /tmp/Elysium-mono (and its C:/tmp, %TEMP% and C:/Users/.../Elysium-mono spellings)
                          -> the worker's worktree
  -C /tmp ... Elysium-mono/x  (tar's two-token form)  -> -C <worker> ... tree/x
  /tmp/elysium-classes, /tmp/elysium-loadtest         -> the worker's copies
  /tmp/mc, /tmp/mcwin, /tmp/mods, /tmp/ref            -> left alone (shared, read only)
  any other /tmp/<name>, and a bare /tmp              -> the worker's scratch
  "$0", "${BASH_SOURCE[0]}"                           -> the script's path in the worktree

and it runs with ELYSIUM_ROOT and ELYSIUM_MONO at the worktree, ELYSIUM_OUT and
ELYSIUM_CLASSES at its classes, TMP, TEMP and TMPDIR at its scratch, and the
validator exports tools/setup_env.py prints. A rewritten script that still names
the tree, another worker, or a tree path the rewrite does not know is refused
before it runs.

Hard-coded paths in Python. check_loadtest.py writes to "/tmp/elysium-loadtest"
with no way to say otherwise, and the root validate.py runs it, so every script
that runs the root validator would share one build folder. The worker's
sitecustomize rewrites such literals, with the same rules, as the file is
loaded -- as the main script or as an import -- without touching the file, so
the worktree stays clean and a negtest that edits the file still edits what
runs. The files it applies to are found by scanning the base at start and are
listed in the run's header.

Gitignored inputs. A file a script names that the base does not have but the
live tree does, because git ignores it (dist/elysium-all-1.0.0.jar, which
negtest_bundle.sh reads), is copied from the live tree once at the start,
recorded by hash, put into the worktree before that script and taken out after
it. One under dist/ or repo/ that the live tree lacks too fails the script
before it runs, with the reason: it would test nothing.

After each script: the worktree must be clean (git status --porcelain), and is
put back with git checkout -- . and git clean -fdq if it is not -- reported as
"left the tree dirty"; the worker's classes must be as they were (restored if a
script recompiled them); and, under one lock for all workers, the LIVE tree is
compared with the start -- git status, and the bytes of every file it lists or
listed. A live file that changed under a path a running script names (its
modules, the files it spells, its gitignored inputs) is a leak, or somebody
editing what it tests: every running script is killed, the ones that name it
are marked "leaked", and the run stops with exit 3. A change anywhere else is
somebody else's work, and is noted. Nothing named by the script may have
appeared in the real %TEMP% or in another worker's scratch either (leaked,
exit 3). --live-ignore GLOB exempts paths outright.

A script's output decides as much as its exit status: a SETUP FAILED, STALE
ANCHOR or GENERATOR BROKE line, a setup's own Python dying (anchor not found,
pattern missing, an AssertionError, a traceback from a heredoc or a -c), or no
caught line at all, is an error; so is a validator the script's own `timeout`
killed (every worker's bash sources a BASH_ENV whose timeout() writes such a
kill down, since `out=$(timeout 1800 python3 validate.py)` throws the 124 away);
a MISSED or MISS line is a miss; RESTORE FAILED, TREE LEFT BROKEN or TREE DIRTY
is restore-failed; only then does a non-zero exit make an error.

The base itself. Before the first script, every module validator a queued
script names is run once on the base, in a worktree; a script that names a
module whose validator fails there is refused, since a case can be "caught" by
a break the base already has. A pass is remembered in negwork/base-checks.json
by base tree, module, validator jars and classes.

The compiled classes. /tmp/elysium-classes is copied to the workers only if it
provably holds a compile of the base: compile.sh writes <module>.sha, the git
blob id of every file it handed javac (hashed before javac and again after, and
written only if the two agree), and every one must be the base's. Content, not
mtimes: a tar restore puts the old bytes and the old mtime back. --compile
builds them in a worktree instead, cached in negwork/classes-<digest of the
base's Java and compile.sh>, so a later commit that touches no Java reuses
them; --trust-classes skips the check.

--base live refuses (without --force) while the live tree may be in the middle
of a negtest run the ordinary way: a bash whose command line names
tools/negtest, or anything a live-tree script keeps in /tmp (any name it
spells: a snapshot, a backup, a lock dir) next to modified tracked files of a
module that script names.

Timeouts: three times a script's last recorded time, never under 30 minutes;
--timeout-unknown (240) for a script never timed; --timeout MIN for all.
--resume SUMMARY skips what that run found ok, at the same base tree and
compiled classes (its base is the default), with the same gitignored inputs,
the same validator jars and the same rules for reading a script's output.

Exit status: 0 every script caught everything and left its tree clean; 1 a
script missed a case, errored, timed out, was refused or left its tree dirty;
2 the run was refused before it started; 3 a path a running script names
changed in the live tree, or a worker's path leaked, and everything was stopped.
"""
import argparse
import collections
import ctypes
import datetime
import fnmatch
import hashlib
import inspect
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import tarfile
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
HOME = os.path.expanduser("~")
ENV_DIR = os.path.normpath(os.environ.get("ELYSIUM_ENV", os.path.join(HOME, "Elysium-env")))
BASH = os.environ.get("NEGTEST_BASH", "C:/Program Files/Git/bin/bash.exe")
GIT = shutil.which("git") or "git"

#: The shared, read-only environment under /tmp: never rewritten.
SHARED = ("mc", "mcwin", "mods", "ref")
#: Tracked Python files that name the shared spellings on purpose and that no
#: negtest runs; the sitecustomize leaves them alone.
NOT_SHIMMED = ("tools/dryrun.py", "tools/setup_env.py", "tools/negtest/parallel.py")
#: Where a rewritten script is written inside the scratch (emptied with it).
RUN_DIR = ".negtest-run"
#: Gitignored folders that hold the product (gradlew collectJars writes dist/):
#: a file a script names there must exist, or the script tests nothing.
PRODUCT_DIRS = ("dist", "repo")
#: A script's timeout: this many times its last recorded time, and no less than
#: the floor (negtest_agronomy_fishbees.sh took 75 minutes at -j 3).
TIMEOUT_SCALE = 3
TIMEOUT_FLOOR_MINUTES = 30
#: One module validator on the base, before any script (elysium-lib's is the
#: longest; the p-style scripts give it 30 minutes a case).
BASE_CHECK_MINUTES = 45

CREATE_NEW_PROCESS_GROUP = 0x00000200


def fwd(path):
    return str(path).replace("\\", "/")


def now():
    return datetime.datetime.now().strftime("%H:%M:%S")


def say(*parts):
    print(f"[{now()}]", *parts, flush=True)


def human(seconds):
    seconds = int(round(seconds))
    if seconds < 60:
        return f"{seconds}s"
    if seconds < 3600:
        return f"{seconds // 60}m{seconds % 60:02d}s"
    return f"{seconds // 3600}h{seconds % 3600 // 60:02d}m"


class Refused(Exception):
    """A reason not to start at all (exit 2)."""


def run(args, cwd=None, env=None, input=None, binary=False, check=False):
    # A process group of its own: a console Ctrl-C reaches every process on
    # the console, and a git killed half way through taking a worktree down is
    # how a run leaves one behind. This process stops them itself.
    done = subprocess.run(args, cwd=cwd, env=env, input=input, capture_output=True,
                          text=not binary, encoding=None if binary else "utf-8",
                          errors=None if binary else "replace",
                          creationflags=CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0)
    if check and done.returncode:
        err = done.stderr if not binary else done.stderr.decode("utf-8", "replace")
        raise RuntimeError(f"{' '.join(map(str, args))} failed:\n{err.strip()}")
    return done


def git(*args, cwd=None, env=None, input=None, binary=False, check=False):
    return run([GIT, *args], cwd=cwd, env=env, input=input, binary=binary, check=check)


# ---------------------------------------------------------------------------
# The rewrite. Used for the scripts, and copied verbatim into every worker's
# sitecustomize for the Python files that hard-code a scratch path, so the two
# can never disagree. Self-contained on purpose: it imports only re.
# ---------------------------------------------------------------------------

def rewrite_text(text, cfg, shell=True):
    """Point every /tmp path in `text` at one worker; return (text, notes).

    cfg: junction (elysium-negw<N>), temp_forms (every spelling of Git Bash's
    /tmp: %TEMP% with a drive letter and as /c/..., C:/tmp, /c/tmp, /tmp),
    live_forms ([(spelling of the live tree, the same spelling of the worktree)]),
    shared (names under /tmp left alone), script (the script's path in the
    worktree, for "$0").
    """
    import re as _re
    notes = []
    junction = cfg["junction"]
    shared = set(cfg["shared"])

    for form, new in cfg["live_forms"]:
        hits = _re.compile(_re.escape(form) + r"(?![\w.+-])", _re.I)
        text, count = hits.subn(lambda _m, _new=new: _new, text)
        if count:
            notes.append(f"{count} x {form} -> {new}")

    alt = "|".join(_re.escape(p) for p in sorted(cfg["temp_forms"], key=len, reverse=True))
    # A /tmp spelling is a whole token: not glued to a word, a path, an
    # expansion or a drive before it ("$ROOT/tmp", "${M}/tmp" and "C:/tmp"'s own
    # "/tmp" are not it), nor followed by more of a name ("/tmpfoo").
    head = r"(?<![\w.}\])/:$])(?:" + alt + r")(?![\w.+-])"

    # tar's two-token form: `tar czf $SNAP -C /tmp Elysium-mono/elysium-core`
    # and the `tar xzf $SNAP -C /tmp` that puts it back. The archive holds
    # paths starting "Elysium-mono/", so -C must name the folder the worktree
    # sits in, and the bare name must become the worktree's.
    change_dir = _re.compile(r"(-C\s+)(" + alt + r")(?![\w./+-])", _re.I)
    lines = text.split("\n")
    two_token = 0
    for at, line in enumerate(lines):
        if change_dir.search(line):
            line = change_dir.sub(lambda m: m.group(1) + m.group(2) + "/" + junction, line)
            line = _re.sub(r"(?<=[\s'\"=])Elysium-mono(?=/|[\s'\";)]|$)", "tree", line)
            lines[at] = line
            two_token += 1
    text = "\n".join(lines)
    if two_token:
        notes.append(f"{two_token} line(s) of tar's -C /tmp form -> -C /tmp/{junction}, Elysium-mono/ -> tree/")

    token = _re.compile(head + r"(?:/(?P<name>[\w.+-]+))?", _re.I)
    counts = {}

    def point(m):
        whole, name = m.group(0), m.group("name")
        prefix = whole if name is None else whole[:-(len(name) + 1)]
        low = (name or "").lower()
        if name is None:
            new, kind = f"{prefix}/{junction}/tmp", "bare /tmp -> scratch"
        elif low in shared or low.startswith("elysium-negw"):
            return whole
        elif low == "elysium-mono":
            new, kind = f"{prefix}/{junction}/tree", "the tree -> worktree"
        elif low == "elysium-classes":
            new, kind = f"{prefix}/{junction}/classes", "classes -> worker copy"
        elif low == "elysium-loadtest":
            new, kind = f"{prefix}/{junction}/loadtest", "loadtest -> worker copy"
        else:
            new, kind = f"{prefix}/{junction}/tmp/{name}", "scratch -> worker scratch"
        counts[kind] = counts.get(kind, 0) + 1
        return new

    text = token.sub(point, text)
    for kind in sorted(counts):
        notes.append(f"{counts[kind]} x {kind}")

    if shell and cfg.get("script"):
        script = cfg["script"]
        before = text
        text = text.replace('"$0"', '"' + script + '"')
        text = text.replace('"${BASH_SOURCE[0]}"', '"' + script + '"')
        text = _re.sub(r"\$\{BASH_SOURCE\[0\]\}|\$BASH_SOURCE\b", lambda _m: script, text)
        text = _re.sub(r"(?<=dirname )\$0\b", lambda _m: '"' + script + '"', text)
        if text != before:
            notes.append("$0 / BASH_SOURCE -> the script's path in the worktree")
    return text, notes


def static_problems(text, n, extra_tree_names=()):
    """Why a rewritten script would still reach outside its own worker."""
    problems = []
    for number, line in enumerate(text.split("\n"), start=1):
        if line.lstrip().startswith("#"):
            continue
        said = line.strip()[:110]
        if re.search(r"elysium-mono", line, re.I):
            problems.append(f"line {number} still names the tree: {said}")
        for m in re.finditer(r"elysium-negw(\d+)|negwork[/\\]+w(\d+)", line, re.I):
            if (m.group(1) or m.group(2)) != str(n):
                problems.append(f"line {number} names another worker: {said}")
        if re.search(r"[A-Za-z]:\\+(?:tmp|Users\\+[^\\]+\\+AppData)", line, re.I):
            problems.append(f"line {number} spells a temp path with backslashes, "
                            f"which the rewrite does not know: {said}")
        for m in re.finditer(r"Elysium-([\w.-]+)", line):
            rest = line[m.end():]
            if m.group(1) == "env":
                if not re.match(r"[/\\]+(?:mc|mcwin|mods|ref)(?![\w.-])", rest, re.I):
                    problems.append(f"line {number} names Elysium-env outside the shared "
                                    f"read-only folders: {said}")
            elif m.group(1).lower() != "mono":
                problems.append(f"line {number} names another tree, Elysium-{m.group(1)}: {said}")
        for name in extra_tree_names:
            if name in line:
                problems.append(f"line {number} names {name}: {said}")
    return problems


def scratch_names(text, temp_forms):
    """The top-level /tmp entries a script names: what a leak would look like."""
    alt = "|".join(re.escape(p) for p in sorted(temp_forms, key=len, reverse=True))
    names = set()
    for m in re.finditer(r"(?<![\w.}\])/:$])(?:" + alt + r")/([\w.+-]+)", text, re.I):
        low = m.group(1).lower()
        if low not in SHARED and low not in ("elysium-mono", "elysium-classes", "elysium-loadtest"):
            names.add(m.group(1))
    return names


ASSIGN = re.compile(r"(?:^|[\s;(&|])(?:local\s+|export\s+|readonly\s+)?([A-Za-z_]\w*)="
                    r"(\"[^\"\n]*\"|'[^'\n]*'|[^\s;&|()]+)")
PATH_WORD = re.compile(r"[\w.@+/${}*?\[\]-]+")


def script_paths(text, tops):
    """The live-tree paths a script names, relative to the root of the tree.

    Its words, with every simple VAR=value it assigns expanded (each value the
    variable is ever given, since the order of a run is not followed), kept when
    they start with an entry at the top of the tree -- elysium-agronomy,
    validate.py, tools/jarjar.py, dist/x.jar -- or follow Elysium-mono/. A
    module named bare stands for all of it: what a script reaches after a cd
    into it lies under it. tops: {top-level name: is a directory}.
    """
    body = "\n".join(l for l in text.split("\n") if not l.lstrip().startswith("#"))
    values = collections.defaultdict(set)
    for m in ASSIGN.finditer(body):
        value = m.group(2)
        if value[:1] in "\"'":
            value = value[1:-1]
        values[m.group(1)].add(value)

    def expand(word, depth=0):
        m = re.search(r"\$\{(\w+)\}|\$(\w+)", word)
        if not m:
            return [word]
        name = m.group(1) or m.group(2)
        if depth > 6 or name not in values:
            return [word[:m.start()]]
        out = []
        for value in sorted(values[name])[:8]:
            out += expand(word[:m.start()] + value + word[m.end():], depth + 1)
        return out[:64]

    found = set()
    for word in set(PATH_WORD.findall(body)):
        for path in expand(word):
            path = re.split(r"[*?\[\]{}$]", path)[0]
            if "Elysium-mono/" in path:
                path = path.split("Elysium-mono/", 1)[1]
            elif re.match(r"(?:/tmp|/[a-z]/|[A-Za-z]:)", path, re.I):
                continue
            path = path.strip("/").rstrip(".")
            first = path.split("/")[0]
            if not path or first not in tops:
                continue
            if tops[first] and "/" not in path and not first.startswith("elysium-"):
                continue    # "art" or "tools" alone is a word, not a path
            found.add(path)
    return found


def covers(paths, rel):
    """Whether `rel` is one of `paths` or lies under one."""
    return any(rel == p or rel.startswith(p + "/") for p in paths)


# ---------------------------------------------------------------------------
# What a script printed
# ---------------------------------------------------------------------------

CASE = re.compile(r"^\s*(?:(?P<a>caught|CAUGHT|MISSED|MISS|allowed)(?:\s*:\s*|\s{2,})"
                  r"|(?P<b>ok)\s*:\s*)(?P<what>\S.*?)\s*$")
SUMMARIES = [
    (re.compile(r"(\d+)\s+caught,\s+(\d+)\s+missed"), False),
    (re.compile(r"caught\s+(\d+),\s+missed\s+(\d+)"), False),
    (re.compile(r"(\d+)\s+passed,\s+(\d+)\s+missed"), False),
]
RESTORED = re.compile(r"tree restored clean|restored tree passes")
BROKEN = re.compile(r"TREE LEFT BROKEN|RESTORE FAILED|TREE DIRTY")
#: A script that could not set up a case: whatever it exits with, an error.
#: The last five are a setup's own Python dying. 72 scripts report a stale
#: anchor only as an AssertionError from their edit helper, which the shell
#: never looks at, and a heredoc's FileNotFoundError or KeyError is the same
#: case set up on nothing; a must-not-flag case after it passes, and so does
#: one whose phrase the base already prints. A validator's own traceback is
#: never one of these: it names its file, and the scripts capture it.
MARKERS = re.compile(r"SETUP FAILED|STALE ANCHOR|GENERATOR BROKE|anchor not found|pattern missing"
                     r"|AssertionError|File \"<stdin>\"|File \"<string>\"")
#: A miss however it is spelt; CASE takes the usual "MISSED : x" shape.
MISSED_ANYWHERE = re.compile(r"\bMISSED\b|^\s*MISS\b")
TROUBLE = re.compile(r"Traceback \(most recent call last\)|Error: ")
#: What a worker's bash sources first (BASH_ENV). The scripts run their
#: validators as `out=$(timeout 1800 python3 validate.py 2>&1)`, which throws
#: the 124 of a validator killed half way away, so a must-not-flag case reads
#: "nothing flagged" off a check that never finished. timeout() writes every
#: kill down for parallel.py; `|| status=$?` keeps it alive under set -e.
BASH_ENV_SHIM = r'''# Written by tools/negtest/parallel.py for one worker, and removed with it.
timeout() {
    local status=0
    command timeout "$@" || status=$?
    case "$status" in
        124|137) printf 'timeout\t%s\t%s\n' "$status" "$*" >> "${NEGTEST_EVENTS:-/dev/null}" ;;
    esac
    return "$status"
}
'''
#: A timeout of one of these is a check that never finished. Anything else under
#: timeout (negtest_wilds_portal.sh's java harness, whose flood is meant to run
#: out of time) is noted and decides nothing.
CHECK_UNDER_TIMEOUT = re.compile(r"\bpython3?(?:\.exe)?\b|\.py\b")


def parse_output(text):
    cases, caught, missed, allowed = [], 0, 0, 0
    summary, restore, trouble, markers = None, None, [], []
    for line in text.splitlines():
        m = CASE.match(line)
        if m:
            kind = m.group("a") or m.group("b")
            cases.append(line.strip())
            if kind in ("MISSED", "MISS"):
                missed += 1
            elif kind == "allowed":
                allowed += 1
            else:
                caught += 1
        elif MISSED_ANYWHERE.search(line):
            cases.append(line.strip())
            missed += 1
        for pattern, _ in SUMMARIES:
            for s in pattern.finditer(line):
                summary = (int(s.group(1)), int(s.group(2)), line.strip())
        if BROKEN.search(line):
            restore = "broken"
        elif RESTORED.search(line) and restore != "broken":
            restore = "clean"
        if MARKERS.search(line):
            markers.append(line.strip()[:160])
        elif TROUBLE.search(line) and len(trouble) < 5:
            trouble.append(line.strip()[:160])
    caught_any = caught > 0 or bool(summary and summary[0] > 0)
    return {"cases": cases, "caught_lines": caught, "missed_lines": missed,
            "allowed_lines": allowed, "summary": summary, "restore": restore,
            "trouble": trouble, "markers": markers, "caught_any": caught_any}


def timeout_kills(events):
    """(checks the script's own timeout killed, other things it killed), from the
    lines BASH_ENV_SHIM's timeout() wrote."""
    checks, others = [], []
    for line in events.splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3 or parts[0] != "timeout":
            continue
        said = f"timeout {parts[2][:120]} (status {parts[1]})"
        (checks if CHECK_UNDER_TIMEOUT.search(parts[2]) else others).append(said)
    return checks, others


def decide_status(parsed, missed, exit_code, killed, leaks, dirt, changed_inputs, killed_checks):
    """A script's status, and the problems that decided it, from what it printed and did."""
    problems = []
    if killed_checks:
        problems += [f"its own timeout killed a check, so a case read a check that never "
                     f"finished: {said}" for said in killed_checks[:3]]
    if leaks:
        return "leaked", problems
    if killed == "abort":
        return "aborted", problems
    if killed == "timeout":
        return "timeout", problems
    if parsed["markers"] or killed_checks:
        return "error", problems
    if parsed["restore"] == "broken":
        return "restore-failed", problems
    if missed:
        return "missed", problems
    if dirt or changed_inputs:
        return "dirty", problems
    if not parsed["caught_any"]:
        return "error", problems + ["printed no caught line at all"]
    if exit_code != 0:
        return "error", problems + [f"exit {exit_code} with nothing missed"]
    return "ok", problems


def rules_digest():
    """The rules a script's output is read by. A resume that carried an ok decided
    by older rules would carry exactly what a parser fix was made to stop."""
    parts = [p.pattern for p in (CASE, RESTORED, BROKEN, MARKERS, MISSED_ANYWHERE,
                                 CHECK_UNDER_TIMEOUT)]
    parts += [p.pattern for p, _ in SUMMARIES]
    parts += [BASH_ENV_SHIM] + [inspect.getsource(f) for f in (parse_output, timeout_kills,
                                                              decide_status)]
    return hashlib.sha256("\0".join(parts).encode("utf-8")).hexdigest()[:16]


# ---------------------------------------------------------------------------
# The live tree
# ---------------------------------------------------------------------------

def status_entries(porcelain_z):
    """`git status --porcelain=v1 -z` as (line, path); a rename's old path rides along."""
    parts = porcelain_z.split("\0")
    entries, at = [], 0
    while at < len(parts):
        entry = parts[at]
        at += 1
        if len(entry) < 4:
            continue
        if entry[0] in "RC" and at < len(parts):
            entries.append((entry + " <- " + parts[at], entry[3:]))
            at += 1
        else:
            entries.append((entry, entry[3:]))
    return entries


def fingerprint(root, ignore=()):
    """HEAD, git status, and the content of every file git lists as changed.

    git status alone would miss a further change to a file already modified, so
    each one is hashed too. --no-optional-locks: this must never take the live
    tree's index.lock out from under somebody else's git.
    """
    head = git("rev-parse", "HEAD", cwd=root).stdout.strip()
    raw = git("--no-optional-locks", "status", "--porcelain=v1", "-z", "--untracked-files=all",
              cwd=root).stdout
    keep, content = [], {}
    for line, rel in status_entries(raw):
        if any(fnmatch.fnmatch(rel, pattern) for pattern in ignore):
            continue
        keep.append(line)
        try:
            with open(os.path.join(root, rel), "rb") as fh:
                content[rel] = hashlib.sha256(fh.read()).hexdigest()
        except OSError:
            content[rel] = None
    return {"head": head, "status": sorted(keep), "content": content}


def what_moved(before, after):
    lines = []
    if before["head"] != after["head"]:
        lines.append(f"HEAD moved: {before['head'][:10]} -> {after['head'][:10]}")
    for entry in sorted(set(after["status"]) - set(before["status"])):
        lines.append("status now : " + entry)
    for entry in sorted(set(before["status"]) - set(after["status"])):
        lines.append("status was : " + entry)
    for rel in sorted(set(before["content"]) | set(after["content"])):
        if before["content"].get(rel) != after["content"].get(rel) and \
                rel in before["content"] and rel in after["content"]:
            lines.append("content moved: " + rel)
    return lines


def file_sha(root, rel):
    try:
        with open(os.path.join(root, rel), "rb") as fh:
            return hashlib.sha256(fh.read()).hexdigest()
    except OSError:
        return None


def files_changed(before, after, root, ignore=()):
    """The live files whose bytes differ from the start, whatever git status says.

    Status moves without a single byte moving when somebody commits or stages
    (another agent committed in the middle of the first trial run). A file
    status listed at the start must still hash as it did; any other file was
    clean then, so it must still be what the start's HEAD has.
    """
    if before == after:
        return []
    paths = set(before["content"]) | set(after["content"])
    if before["head"] != after["head"]:
        paths |= {p for p in git("diff", "--name-only", "-z", before["head"], after["head"],
                                 cwd=root).stdout.split("\0") if p}
    paths = {p for p in paths if not any(fnmatch.fnmatch(p, g) for g in ignore)}
    at_head = [p for p in paths if p not in before["content"]]
    old = read_blobs(root, before["head"], sorted(at_head), raw=True)
    changed = []
    for rel in sorted(paths):
        if rel in before["content"]:
            expected = before["content"][rel]
        else:
            expected = hashlib.sha256(old[rel]).hexdigest() if rel in old else None
        if file_sha(root, rel) != expected:
            changed.append(rel)
    return changed


def tracked_hash(root):
    """One digest over every tracked and untracked (not ignored) file's content.

    By file list and bytes, so a commit, which moves files from untracked to
    tracked without changing one, leaves it alone.
    """
    names = [n for n in git("ls-files", "-z", "--cached", "--others", "--exclude-standard",
                            cwd=root).stdout.split("\0") if n]
    names = sorted(set(names))
    digest = hashlib.sha256()
    for rel in sorted(names):
        digest.update(rel.encode("utf-8") + b"\0")
        try:
            with open(os.path.join(root, rel), "rb") as fh:
                digest.update(hashlib.sha256(fh.read()).digest())
        except OSError:
            digest.update(b"<missing>")
    return digest.hexdigest(), len(names)


def snapshot_live(root, negwork):
    """The live working tree as it is now -- HEAD plus every uncommitted change and
    untracked file -- as a commit nobody points at, made through a private index
    so the live index and files are not touched."""
    index = os.path.join(negwork, "snapshot.index")
    env = dict(os.environ, GIT_INDEX_FILE=index)
    try:
        git("read-tree", "HEAD", cwd=root, env=env, check=True)
        git("add", "-A", cwd=root, env=env, check=True)
        tree = git("write-tree", cwd=root, env=env, check=True).stdout.strip()
        commit = git("-c", "user.name=negtest parallel", "-c", "user.email=negtest@localhost",
                     "commit-tree", tree, "-p", "HEAD", "-m",
                     "negtest parallel.py: snapshot of the live working tree",
                     cwd=root, check=True).stdout.strip()
    finally:
        for leftover in (index, index + ".lock"):
            if os.path.exists(leftover):
                os.remove(leftover)
    return commit


# ---------------------------------------------------------------------------
# Is the live tree in the middle of a negtest? (before --base live snapshots it)
# ---------------------------------------------------------------------------

NEGTEST_COMMAND = re.compile(r"tools[\\/]+negtest(?:[\\/]+(?!parallel\.py\b|README\.md\b)[\w.-]+"
                             r"|[\\/]*(?=[\s\"';&|)]|$))", re.I)


def process_table():
    """{pid: (parent pid, name, command line)} for every process, or None."""
    if os.name == "nt":
        done = run(["powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId,Name,"
                    "CommandLine | ConvertTo-Json -Compress"])
        try:
            rows = json.loads(done.stdout or "null")
        except ValueError:
            return None
        if isinstance(rows, dict):
            rows = [rows]
        if not rows:
            return None
        return {int(p["ProcessId"]): (int(p.get("ParentProcessId") or 0), p.get("Name") or "",
                                      p.get("CommandLine") or "") for p in rows}
    done = run(["ps", "-eo", "pid=,ppid=,comm=,args="])
    if done.returncode:
        return None
    table = {}
    for line in done.stdout.splitlines():
        parts = line.split(None, 3)
        if len(parts) >= 3:
            table[int(parts[0])] = (int(parts[1]), parts[2], parts[3] if len(parts) > 3 else "")
    return table


def negtest_processes(table):
    """The bash processes that may be running a negtest in the live tree.

    A bash whose command line names tools/negtest -- a script there, or the
    folder itself -- other than this process's own ancestors, a parallel.py
    run (its scripts run in worktrees, from .negtest-run under elysium-negw<N>)
    and a dryrun.py that works in a copy (all but --in-place).
    """
    mine, pid = set(), os.getpid()
    while pid and pid not in mine:
        mine.add(pid)
        pid = table.get(pid, (0,))[0]
    found = []
    for pid, (_parent, name, command) in sorted(table.items()):
        if pid in mine or os.path.basename(name).lower() not in ("bash.exe", "sh.exe", "bash", "sh"):
            continue
        if ".negtest-run" in command or "elysium-negw" in command.lower():
            continue
        if "dryrun.py" in command and "--in-place" not in command:
            continue
        if NEGTEST_COMMAND.search(command):
            found.append((pid, command))
    return found


def snapshot_suspects(r, texts, tops):
    """What a negtest run the ordinary way keeps in the real /tmp, with the
    modified tracked files of the modules it stands for: [(path, written,
    modules, files)].

    Every top-level /tmp name a live-tree script spells counts, lock dirs
    included. Until 16 October only names with snap, .tar or .tgz in them did,
    and 79 of the 214 scripts keep their backups as something else (/tmp/nb_p2,
    /tmp/sm.bak, /tmp/_abs_backup.java, /tmp/gates_bak, a lock dir), so a tree
    snapshotted half way through negtest_p2.sh passed as whole. A tar is asked
    what it holds; anything else stands for every module the scripts that
    spell it name (a module named at all is all of it), and for the other
    paths they name exactly.
    """
    users = collections.defaultdict(set)
    for name, text in texts.items():
        for scratch in scratch_names(text, r.temp_forms):
            users[scratch].add(name)
    for entry in os.listdir(r.temp_real):
        if re.fullmatch(r"negtest_.*_snapshot\.tgz", entry):
            users.setdefault(entry, set())
    raw = git("--no-optional-locks", "status", "--porcelain=v1", "-z", "--untracked-files=no",
              cwd=r.live_root).stdout
    modified = [rel for _line, rel in status_entries(raw)]
    named = {}
    found = []
    for scratch, scripts in sorted(users.items()):
        path = os.path.join(r.temp_real, scratch)
        if not os.path.lexists(path):
            continue
        tops_held = set()
        try:
            if os.path.isfile(path) and tarfile.is_tarfile(path):
                with tarfile.open(path) as archive:
                    for member in archive.getnames():
                        parts = [p for p in fwd(member).split("/") if p not in ("", ".")]
                        if parts and parts[0] == "Elysium-mono":
                            parts = parts[1:]
                        if parts and parts[0] in tops:
                            tops_held.add(parts[0])
        except (OSError, tarfile.TarError):
            pass
        if tops_held:
            files = [rel for rel in modified if rel.split("/")[0] in tops_held]
        else:
            # not a tar: a backup, a folder of copies, a lock dir. It stands for
            # what the scripts that keep it name.
            paths = set()
            for name in scripts:
                if name not in named:
                    named[name] = script_paths(texts[name], tops)
                paths |= named[name]
            modules = {p.split("/")[0] for p in paths
                       if p.split("/")[0].startswith("elysium-") and tops.get(p.split("/")[0])}
            files = [rel for rel in modified if rel.split("/")[0] in modules or covers(paths, rel)]
            tops_held = modules | {p for p in paths if p.split("/")[0] not in modules
                                   and any(covers({p}, rel) for rel in files)}
        if files:
            written = datetime.datetime.fromtimestamp(os.lstat(path).st_mtime).strftime("%d %b %H:%M")
            found.append((fwd(path), written, sorted(tops_held), files))
    return found


def live_busy(r, texts, tops):
    """Why the live tree may be half way through a negtest run the ordinary way."""
    reasons = []
    table = process_table()
    if table is None:
        reasons.append("could not list the running processes, so a negtest running in the "
                       "live tree cannot be ruled out")
    else:
        for pid, command in negtest_processes(table):
            reasons.append(f"bash pid {pid} names tools/negtest: {' '.join(command.split())[:150]}")
    spelt = collections.defaultdict(list)
    for name, text in sorted(texts.items()):
        for scratch in scratch_names(text, r.temp_forms):
            spelt[scratch].append(name)
    for path, written, held, files in snapshot_suspects(r, texts, tops):
        by = spelt.get(os.path.basename(path), [])
        reasons.append(f"{path} (written {written}"
                       + (f"; kept by {', '.join(by[:3])}{' ...' if len(by) > 3 else ''}" if by else "")
                       + f") stands for {', '.join(held[:4])}, which has "
                       f"{len(files)} modified tracked file(s): {', '.join(files[:4])}")
    return reasons


# ---------------------------------------------------------------------------
# The compiled classes
# ---------------------------------------------------------------------------

def classes_provenance(live_root, base, classes):
    """Whether `classes` provably holds a compile of `base`'s Java.

    By what javac read, not by when anything was written. compile.sh writes
    <module>.sha beside <module>.srcs: the git blob id of every file it hands
    javac, hashed just before javac and again just after, and written only
    when the two agree. Every one must be the id the base has.

    Until 16 October this read mtimes: a file unchanged since compile.sh listed
    its module was taken as compiled the way it reads now. 122 negtest scripts
    restore with tar x, which puts back the archived mtime along with the
    bytes, so a module compiled while a negtest had it broken, then restored,
    passed as a compile of the base. A compile.sh from before then writes no
    .sha, and its classes cannot be shown to be anything.
    """
    listing = git("ls-tree", "-r", "-z", base, cwd=live_root, check=True).stdout
    java = collections.defaultdict(dict)
    for entry in listing.split("\0"):
        if not entry:
            continue
        meta, rel = entry.split("\t", 1)
        # compile.sh compiles <module>/src/main/java and nothing else
        # (elysium-lib/example/src/main/java is not a module's source).
        if rel.endswith(".java") and rel.split("/")[1:4] == ["src", "main", "java"]:
            java[rel.split("/", 1)[0]][rel] = meta.split()[2]
    differ, unknown, other = [], [], []
    for module in sorted(java):
        srcs = os.path.join(classes, module + ".srcs")
        if not os.path.isfile(srcs):
            other.append(f"{module} was never compiled there")
            continue
        listed = {fwd(l.strip()) for l in open(srcs, encoding="utf-8", errors="replace") if l.strip()}
        if listed != set(java[module]):
            extra, gone = sorted(listed - set(java[module])), sorted(set(java[module]) - listed)
            other.append(f"{module}: compiled {len(extra)} file(s) the base lacks "
                         f"{extra[:2]} and lacked {len(gone)} it has {gone[:2]}")
        log = os.path.join(classes, module + ".log")
        if os.path.isfile(log) and re.search(r"\berror:", open(log, encoding="utf-8", errors="replace").read()):
            other.append(f"{module} did not compile there (errors in {module}.log)")
        record = os.path.join(classes, module + ".sha")
        if not os.path.isfile(record):
            other.append(f"{module} has no {module}.sha, so what javac read is not known (a compile.sh "
                         f"from before 16 October, or a file changed while javac read it)")
            unknown += sorted(java[module])
            continue
        read = {}
        for line in open(record, encoding="utf-8", errors="replace"):
            sha, _, rel = line.strip().partition(" ")
            if rel:
                read[fwd(rel)] = sha
        if set(read) != listed:
            other.append(f"{module}.sha does not list what {module}.srcs does")
        for rel, sha in sorted(java[module].items()):
            if rel not in read:
                unknown.append(rel)
            elif read[rel] != sha:
                differ.append(rel)
    return differ, unknown, other


def tree_digest(folder):
    """Every file under `folder` by size and content, to notice a recompile."""
    digest = hashlib.sha256()
    for dirpath, dirs, files in os.walk(folder):
        dirs.sort()
        for name in sorted(files):
            path = os.path.join(dirpath, name)
            digest.update(fwd(os.path.relpath(path, folder)).encode() + b"\0")
            try:
                with open(path, "rb") as fh:
                    digest.update(hashlib.sha256(fh.read()).digest())
            except OSError:
                digest.update(b"?")
    return digest.hexdigest()


# ---------------------------------------------------------------------------
# Windows plumbing
# ---------------------------------------------------------------------------

def pid_alive(pid):
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except OSError:
            return False
    handle = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)
    if not handle:
        return False
    code = ctypes.c_ulong()
    ctypes.windll.kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
    ctypes.windll.kernel32.CloseHandle(handle)
    return code.value == 259


def kill_tree(proc):
    if proc is None or proc.poll() is not None:
        return
    if os.name == "nt":
        run(["taskkill", "/T", "/F", "/PID", str(proc.pid)])
    else:
        proc.kill()
    try:
        proc.wait(timeout=30)
    except subprocess.TimeoutExpired:
        pass


def is_junction(path):
    return bool(getattr(os.path, "isjunction", lambda _p: False)(path))


def same_place(link, folder):
    """Whether the junction `link` leads to `folder` (which need not exist)."""
    try:
        return os.path.normcase(os.path.realpath(link)) == os.path.normcase(os.path.realpath(folder))
    except OSError:
        return False


def make_junction(link, target):
    import _winapi
    _winapi.CreateJunction(os.path.normpath(target), os.path.normpath(link))


def remove_tree(path, attempts=8):
    """rmtree, patiently: Defender holds files open for a moment after a run."""
    def unlock(func, p, _exc):
        try:
            os.chmod(p, 0o666)
            func(p)
        except OSError:
            pass

    for attempt in range(attempts):
        if not os.path.lexists(path):
            return True
        if attempt:
            time.sleep(1.0)
        if is_junction(path):
            os.rmdir(path)
        elif sys.version_info >= (3, 12):
            shutil.rmtree(path, onexc=unlock)
        else:
            shutil.rmtree(path, onerror=unlock)
    return not os.path.lexists(path)


def empty_dir(path):
    os.makedirs(path, exist_ok=True)
    for name in os.listdir(path):
        full = os.path.join(path, name)
        if is_junction(full) or os.path.islink(full):
            os.unlink(full) if os.path.islink(full) else os.rmdir(full)
        elif os.path.isdir(full):
            remove_tree(full)
        else:
            try:
                os.remove(full)
            except OSError:
                os.chmod(full, 0o666)
                os.remove(full)


# ---------------------------------------------------------------------------
# The environment
# ---------------------------------------------------------------------------

def posix_to_windows(path):
    m = re.match(r"^/([A-Za-z])(/.*)?$", path)
    return f"{m.group(1).upper()}:{m.group(2) or '/'}" if m else path


def validator_exports(live_root):
    """The block setup_env.py prints for validators and negtests, as a dict."""
    done = run([sys.executable, os.path.join(live_root, "tools", "setup_env.py"), "--check"])
    if done.returncode:
        raise Refused("tools/setup_env.py --check says the environment is not ready:\n"
                      + "\n".join(done.stdout.strip().splitlines()[-12:]))
    exports, block = {}, None
    for line in done.stdout.splitlines():
        if line.startswith("# validators"):
            block = "validators"
            continue
        if line.startswith("#") or not line.strip():
            if block == "validators" and exports:
                break
            continue
        if block == "validators" and line.startswith("export "):
            import shlex
            for item in shlex.split(line[len("export "):]):
                key, _, value = item.partition("=")
                exports[key] = value
    if "ELYSIUM_MC_LIBS" not in exports:
        raise Refused("could not read the validator exports from tools/setup_env.py --check")
    return exports


def apply_exports(env, exports):
    for key, value in exports.items():
        if key == "PATH":
            dirs = [posix_to_windows(p) for p in value.split(":") if p and p != "$PATH"]
            env["PATH"] = os.pathsep.join(dirs + [env.get("PATH", "")])
        else:
            env[key] = value
    return env


def exports_digest(exports):
    """(digest, files) over what the validators read beside the tree: every file
    an export names, every file in a folder one names, and every jar a cp.txt
    among them lists. By content, so a resume across a new NeoForge jar or a
    rebuilt cp.txt does not carry results the old jars decided."""
    files = set()
    for key, value in sorted(exports.items()):
        if key == "PATH" or not value:
            continue
        place = os.path.normpath(posix_to_windows(value))
        if os.path.isfile(place):
            files.add(place)
        elif os.path.isdir(place):
            for dirpath, dirs, names in os.walk(place):
                for name in names:
                    files.add(os.path.join(dirpath, name))
    for path in sorted(files):
        if os.path.basename(path) == "cp.txt":
            try:
                listed = open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for jar in re.findall(r"(?:[A-Za-z]:)?[^;:\r\n]+?\.jar", listed):
                jar = os.path.normpath(posix_to_windows(jar.strip()))
                if os.path.isfile(jar):
                    files.add(jar)
    digest = hashlib.sha256()
    for path in sorted(files, key=lambda p: os.path.normcase(p)):
        digest.update(fwd(os.path.normcase(path)).encode("utf-8") + b"\0")
        try:
            with open(path, "rb") as fh:
                for block in iter(lambda: fh.read(1 << 20), b""):
                    digest.update(block)
        except OSError:
            digest.update(b"<unreadable>")
        digest.update(b"\0")
    return digest.hexdigest()[:16], len(files)


def temp_spellings():
    """Git Bash's /tmp, as each tool on this machine may spell it."""
    real = run([BASH, "-c", "cygpath -m /tmp"]).stdout.strip()
    if not real or not os.path.isdir(real):
        raise Refused(f"could not ask Git Bash where /tmp is (got {real!r})")
    if os.path.normcase(os.path.realpath("C:/tmp")) != os.path.normcase(os.path.realpath(real)):
        raise Refused(f"C:/tmp is not {real}: Windows Python would not see the /tmp Git Bash sees")
    # cygpath -u of %TEMP% answers "/tmp" (the mount), so the /c/... spelling is made by hand.
    posix = "/" + real[0].lower() + fwd(real)[2:] if re.match(r"^[A-Za-z]:", real) else real
    forms = {fwd(real), posix, "C:/tmp", "/c/tmp", "/tmp"}
    return fwd(real), sorted((f for f in forms if f), key=len, reverse=True)


# ---------------------------------------------------------------------------
# A worker
# ---------------------------------------------------------------------------

SHIM_HEAD = '''# Written by tools/negtest/parallel.py for worker {n} and removed with it.
#
# Points the scratch paths a few tracked Python files hard-code (check_loadtest.py's
# OUT above all) at this worker's own folders, with the same rules the scripts were
# rewritten by, as each file is loaded -- as the main script or as an import --
# without touching the file itself, so the worktree stays clean.
import importlib.machinery as _machinery
import os as _os
import sys as _sys

_CFG = {cfg!r}
_FILES = {files!r}

'''

SHIM_TAIL = '''

def _key(path):
    return _os.path.normcase(_os.path.realpath(path))


def _patched(path):
    with open(path, "rb") as fh:
        data = fh.read()
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        text = data.decode("latin-1")
    return rewrite_text(text, _CFG, shell=False)[0]


class _Loader(_machinery.SourceFileLoader):
    def get_code(self, fullname):
        path = self.get_filename(fullname)
        return compile(_patched(path), path, "exec", dont_inherit=True)


class _Finder:
    @classmethod
    def find_spec(cls, name, path=None, target=None):
        spec = _machinery.PathFinder.find_spec(name, path, target)
        if spec is not None and spec.origin and _key(spec.origin) in _FILES:
            spec.loader = _Loader(name, spec.origin)
        return spec

    @classmethod
    def invalidate_caches(cls):
        pass


_sys.meta_path.insert(0, _Finder)


def _run_main(path):
    import __main__
    import traceback
    _sys.path.insert(0, _os.path.dirname(path))
    __main__.__file__ = path
    __main__.__cached__ = None
    status = 0
    try:
        code = compile(_patched(path), path, "exec", dont_inherit=True)
        exec(code, __main__.__dict__)
    except SystemExit as stop:
        if stop.code is None:
            status = 0
        elif isinstance(stop.code, int):
            status = stop.code
        else:
            print(stop.code, file=_sys.stderr)
            status = 1
    except BaseException:
        traceback.print_exc()
        status = 1
    if "threading" in _sys.modules:
        try:
            _sys.modules["threading"]._shutdown()
        except Exception:
            pass
    try:
        import atexit
        atexit._run_exitfuncs()
    except Exception:
        pass
    for stream in (_sys.stdout, _sys.stderr):
        try:
            stream.flush()
        except Exception:
            pass
    # os._exit, not sys.exit: a SystemExit out of sitecustomize is a fatal
    # "failed to import the site module" with status 1 (tools/dryrun.py found it).
    _os._exit(status)


_main = _sys.argv[0] if _sys.argv else ""
if _main and _main not in ("-c", "-m", "-") and _os.path.isfile(_main) and _key(_main) in _FILES:
    _run_main(_os.path.abspath(_main))
'''

SELFTEST_MAIN = '''import shim_selftest_mod
print("main:" + "/tmp/elysium-loadtest")
print("mod:" + shim_selftest_mod.WHERE)
print("file:" + __file__)
raise SystemExit(7)
'''
SELFTEST_MOD = 'WHERE = "/tmp/elysium-loadtest/classes"\n'


class Worker:
    def __init__(self, n, ctx):
        self.n = n
        self.ctx = ctx
        self.junction = f"elysium-negw{n}"
        self.dir = os.path.join(ctx.negwork, f"w{n}")
        self.tree = os.path.join(self.dir, "tree")
        self.tmp = os.path.join(self.dir, "tmp")
        self.classes = os.path.join(self.dir, "classes")
        self.loadtest = os.path.join(self.dir, "loadtest")
        self.site = os.path.join(self.dir, "site")
        self.bash_env = os.path.join(self.dir, "bash_env.sh")
        self.events = os.path.join(self.dir, "events.log")
        self.link = os.path.join(ctx.temp_real, self.junction)
        self.j = "/tmp/" + self.junction            # every spelling the scripts get
        self.proc = None
        self.stopped = False                        # killed because the run is stopping
        self.done = threading.Event()               # its thread has finished
        self.current = None                         # the script running here
        self.names = set()                          # what it names under /tmp
        self.paths = set()                          # what it names in the tree
        self.usable = True

    def cfg(self, script=None):
        tree_real = fwd(self.tree)
        posix_real = "/" + tree_real[0].lower() + tree_real[2:]
        return {
            "junction": self.junction,
            "temp_forms": self.ctx.temp_forms,
            "live_forms": [(self.ctx.live_real, tree_real), (self.ctx.live_posix, posix_real)],
            "shared": list(SHARED),
            "script": f"{self.j}/tree/tools/negtest/{script}" if script else None,
        }

    # -- building and taking down ------------------------------------------------

    def build(self):
        ctx = self.ctx
        self.discard()
        os.makedirs(self.dir, exist_ok=True)
        for folder in (self.tmp, self.site):
            os.makedirs(folder, exist_ok=True)
        if os.path.lexists(self.link):
            raise RuntimeError(f"{self.link} is in the way and is not this worker's")
        make_junction(self.link, self.dir)
        t = time.perf_counter()
        made = git("-c", "checkout.workers=0", "-c", "checkout.thresholdForParallelism=100",
                   "worktree", "add", "--detach", "--quiet", fwd(self.tree), ctx.base,
                   cwd=ctx.live_root)
        if made.returncode:
            raise RuntimeError("git worktree add failed:\n" + made.stderr.strip())
        built = time.perf_counter() - t
        shutil.copytree(ctx.classes_base, self.classes)
        if os.path.isdir(ctx.loadtest_shared):
            shutil.copytree(ctx.loadtest_shared, self.loadtest)
        else:
            os.makedirs(self.loadtest)
        self.write_shim()
        self.selftest()
        self.timeout_selftest()
        if self.dirty():
            raise RuntimeError(f"the fresh worktree {self.tree} is not clean: {self.dirty()[:3]}")
        return built

    def discard(self):
        """Take down whatever a run before this one left here.

        Only this negwork's own: a junction of that name leading anywhere else
        belongs to another run, and is left alone (the numbers are chosen so
        that it does not happen; this is the second line).
        """
        if is_junction(self.link) and same_place(self.link, self.dir):
            os.rmdir(self.link)
        if os.path.isdir(self.tree):
            git("worktree", "remove", "--force", fwd(self.tree), cwd=self.ctx.live_root)
        if os.path.lexists(self.dir):
            remove_tree(self.dir)
        git("worktree", "prune", cwd=self.ctx.live_root)

    def write_shim(self):
        files = {}
        for rel in self.ctx.shimmed:
            files[os.path.normcase(os.path.realpath(os.path.join(self.tree, rel)))] = rel
        # the selftest pair, run once below
        for name in ("shim_selftest.py", "shim_selftest_mod.py"):
            full = os.path.join(self.site, "selftest", name)
            files[os.path.normcase(os.path.realpath(full))] = "selftest/" + name
        source = (SHIM_HEAD.format(n=self.n, cfg=self.cfg(), files=files)
                  + inspect.getsource(rewrite_text) + SHIM_TAIL)
        with open(os.path.join(self.site, "sitecustomize.py"), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(source)
        with open(self.bash_env, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(BASH_ENV_SHIM)

    def timeout_selftest(self):
        """Prove the scripts' timeout() writes a kill down and keeps its status,
        or a validator killed half way would read as a check that passed."""
        open(self.events, "w").close()
        done = run([BASH, "-c", 'set -e; out=$(timeout 1 sleep 30 2>&1) || echo "status $?"; '
                                'timeout 30 true && echo "fine $?"'], env=self.env())
        said = open(self.events, encoding="utf-8", errors="replace").read()
        checks, others = timeout_kills(said)
        if done.stdout.split() != ["status", "124", "fine", "0"] or checks \
                or others != ["timeout 1 sleep 30 (status 124)"]:
            raise RuntimeError(f"worker {self.n}'s BASH_ENV timeout() did not record a kill "
                               f"(said {done.stdout.strip()!r}, wrote {said.strip()!r}, stderr "
                               f"{done.stderr.strip()[-300:]!r}); a killed validator would pass")
        open(self.events, "w").close()

    def selftest(self):
        """Prove the sitecustomize rewrites a main script and an import, and keeps exit codes."""
        folder = os.path.join(self.site, "selftest")
        os.makedirs(folder, exist_ok=True)
        for name, body in (("shim_selftest.py", SELFTEST_MAIN), ("shim_selftest_mod.py", SELFTEST_MOD)):
            with open(os.path.join(folder, name), "w", encoding="utf-8", newline="\n") as fh:
                fh.write(body)
        done = run([BASH, "-c", 'cd "$1" && python3 shim_selftest.py', "selftest", fwd(folder)],
                   env=self.env())
        want = [f"main:{self.j}/loadtest", f"mod:{self.j}/loadtest/classes"]
        got = done.stdout.splitlines()
        if done.returncode != 7 or any(w not in got for w in want):
            raise RuntimeError(f"worker {self.n}'s sitecustomize did not rewrite the hard-coded "
                               f"paths (status {done.returncode}, said {got!r}, stderr "
                               f"{done.stderr.strip()[-300:]!r}); check_loadtest.py would share a folder")
        remove_tree(folder)

    def env(self):
        env = dict(self.ctx.base_env)
        env["ELYSIUM_ROOT"] = env["ELYSIUM_MONO"] = f"{self.j}/tree"
        env["ELYSIUM_OUT"] = env["ELYSIUM_CLASSES"] = f"{self.j}/classes"
        env["ELYSIUM_LOADTEST_OUT"] = f"{self.j}/loadtest"
        env["TMPDIR"] = f"{self.j}/tmp"
        env["TMP"] = env["TEMP"] = os.path.normpath(self.tmp)
        env["PYTHONPATH"] = os.path.normpath(self.site) + (
            os.pathsep + env["PYTHONPATH"] if env.get("PYTHONPATH") else "")
        env["NEGTEST_PARALLEL_WORKER"] = str(self.n)
        # Sourced by every non-interactive bash: the scripts' own timeout().
        env["BASH_ENV"] = f"{self.j}/bash_env.sh"
        env["NEGTEST_EVENTS"] = f"{self.j}/events.log"
        return env

    def dirty(self):
        done = git("status", "--porcelain", cwd=self.tree)
        if done.returncode:
            return ["git status failed: " + done.stderr.strip()[:200]]
        return [l for l in done.stdout.splitlines() if l.strip()]

    def reset(self):
        """Put the worktree back to the base; False if it will not go."""
        git("reset", "-q", cwd=self.tree)
        head = git("rev-parse", "HEAD", cwd=self.tree).stdout.strip()
        if head != self.ctx.base:
            git("checkout", "-q", "--detach", "--force", self.ctx.base, cwd=self.tree)
        git("checkout", "--", ".", cwd=self.tree)
        git("clean", "-fdq", cwd=self.tree)
        return not self.dirty()

    def classes_ok(self):
        return tree_digest(self.classes) == self.ctx.classes_digest

    def restore_classes(self):
        remove_tree(self.classes)
        shutil.copytree(self.ctx.classes_base, self.classes)

    # -- one script --------------------------------------------------------------

    def refuse(self, result, problems):
        result.update(status="refused", problems=problems)
        with open(result["log"], "w", encoding="utf-8", newline="\n") as fh:
            fh.write("refused by parallel.py before it ran:\n" + "\n".join(problems) + "\n")
        return result

    def place_inputs(self, script):
        """Put the gitignored files the script names into the worktree, as frozen at the start."""
        placed, made = [], []
        for rel in self.ctx.inputs.get(script, ()):
            dst = os.path.join(self.tree, rel)
            folder = os.path.dirname(dst)
            missing = []
            while folder and not os.path.isdir(folder):
                missing.append(folder)
                folder = os.path.dirname(folder)
            for folder in reversed(missing):
                os.mkdir(folder)
                made.append(folder)
            shutil.copyfile(self.ctx.frozen[rel], dst)
            placed.append(rel)
            if file_sha(self.tree, rel) != self.ctx.input_sha[rel]:
                raise RuntimeError(f"the copy of {rel} in worker {self.n} is not what was frozen")
        return placed, made

    def take_inputs(self, placed, made):
        """Take them out again; say which the script changed."""
        changed = []
        for rel in placed:
            if file_sha(self.tree, rel) != self.ctx.input_sha[rel]:
                changed.append(rel)
            try:
                os.remove(os.path.join(self.tree, rel))
            except OSError:
                pass
        for folder in reversed(made):
            try:
                os.rmdir(folder)
            except OSError:
                pass
        return changed

    def run_script(self, script):
        ctx = self.ctx
        result = {"script": script, "worker": self.n, "status": None, "problems": [],
                  "exit": None, "seconds": 0.0, "log": fwd(os.path.join(ctx.logs, script + ".log")),
                  "text_sha": hashlib.sha256(ctx.texts[script].encode("utf-8")).hexdigest(),
                  "timeout_minutes": round(ctx.timeout_for(script) / 60, 1)}
        if script in ctx.extra:
            result["source"] = ctx.extra[script]
        original = ctx.texts[script]
        text, notes = rewrite_text(original, self.cfg(script))
        result["rewrites"] = notes
        refused = static_problems(text, self.n)
        if refused:
            return self.refuse(result, refused)
        if ctx.input_missing.get(script):
            return self.refuse(result, [
                f"names {rel}, which git ignores, so no worktree has it, and the live tree has "
                f"not got it either: it would test nothing (build it first, e.g. ./gradlew "
                f"collectJars for dist/)" for rel in ctx.input_missing[script]])
        with ctx.lock:
            empty_dir(self.tmp)
            self.names = scratch_names(original, ctx.temp_forms)
            self.paths = ctx.paths[script]
            self.current = script
        os.makedirs(os.path.join(self.tmp, RUN_DIR), exist_ok=True)
        copy = os.path.join(self.tmp, RUN_DIR, script)
        with open(copy, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        if run([BASH, "-n", copy]).returncode:
            with ctx.lock:
                empty_dir(self.tmp)
                self.current, self.names, self.paths = None, set(), set()
            return self.refuse(result, ["the rewritten script does not parse (bash -n)"])
        temp_before = {n: self.temp_state(n) for n in self.names}
        placed, made = [], []
        try:
            placed, made = self.place_inputs(script)
        except (OSError, RuntimeError) as oops:
            self.take_inputs(placed, made)
            with ctx.lock:
                empty_dir(self.tmp)
                self.current, self.names, self.paths = None, set(), set()
            return self.refuse(result, [f"its gitignored inputs could not be put in place: {oops}"])
        if placed:
            result["inputs"] = {rel: ctx.input_sha[rel] for rel in placed}

        timeout = ctx.timeout_for(script)
        open(self.events, "w").close()
        t = time.perf_counter()
        killed = None
        with open(result["log"], "wb") as log:
            self.proc = subprocess.Popen([BASH, f"{self.j}/tmp/{RUN_DIR}/{script}"], cwd=self.tree,
                                         env=self.env(), stdout=log, stderr=subprocess.STDOUT,
                                         stdin=subprocess.DEVNULL,
                                         creationflags=CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0)
            while self.proc.poll() is None:
                if ctx.abort.is_set():
                    killed = "abort"
                elif time.perf_counter() - t > timeout:
                    killed = "timeout"
                if killed:
                    kill_tree(self.proc)
                    break
                time.sleep(0.5)
            self.proc.wait()
            if killed is None and self.stopped:
                killed = "abort"
        result["seconds"] = round(time.perf_counter() - t, 1)
        result["exit"] = self.proc.returncode
        self.proc = None

        with open(result["log"], "rb") as fh:
            out = fh.read().decode("utf-8", "replace")
        parsed = parse_output(out)
        try:
            killed_checks, killed_other = timeout_kills(
                open(self.events, encoding="utf-8", errors="replace").read())
        except OSError:
            killed_checks, killed_other = [], []
        result.update(cases=parsed["cases"], restore=parsed["restore"], trouble=parsed["trouble"],
                      markers=parsed["markers"], caught_lines=parsed["caught_lines"],
                      missed_lines=parsed["missed_lines"], timeouts=killed_checks + killed_other)
        if parsed["summary"]:
            result["summary"] = parsed["summary"][2]
            result["caught"] = parsed["summary"][0]
            result["missed"] = max(parsed["summary"][1], parsed["missed_lines"])
        else:
            result["summary"] = None
            result["caught"] = parsed["caught_lines"]
            result["missed"] = parsed["missed_lines"]

        changed_inputs = self.take_inputs(placed, made)
        for rel in changed_inputs:
            result["problems"].append(f"changed {rel}, the gitignored input it was given")
        dirt = self.dirty()
        if dirt:
            result["dirty"] = dirt[:40]
            if not self.reset():
                self.usable = False
                result["problems"].append("the worktree would not go back to the base; worker retired")
        if not self.classes_ok():
            result["problems"].append("the worker's compiled classes changed (a compile.sh run?); restored")
            self.restore_classes()

        # The guard first: a script that leaked is "leaked", whatever it printed.
        leaks = ctx.guard(self, script, temp_before)
        with ctx.lock:
            leaks += ctx.suspects.pop(script, [])
        result["problems"] += leaks

        for line in parsed["markers"][:3]:
            result["problems"].append("printed: " + line)
        result["status"], decided = decide_status(parsed, result["missed"], result["exit"], killed,
                                                  leaks, dirt, changed_inputs, killed_checks)
        result["problems"] += decided
        if killed == "timeout" and not leaks:
            result["problems"].append(f"killed after {human(timeout)}")
        for said in killed_other[:3]:
            result["problems"].append(f"noted, decides nothing: its own timeout killed {said}")
        if dirt:
            result["problems"].append(f"left the tree dirty ({len(dirt)} path(s))")

        with open(result["log"], "a", encoding="utf-8", newline="\n") as fh:
            fh.write(f"\n==== parallel.py: {script} on worker {self.n}, base {ctx.base[:10]}: "
                     f"{result['status']}, exit {result['exit']}, {human(result['seconds'])} "
                     f"(timeout {human(timeout)}), caught {result['caught']}, missed {result['missed']}\n")
            for line in result["rewrites"]:
                fh.write("     rewrite: " + line + "\n")
            for rel, sha in result.get("inputs", {}).items():
                fh.write(f"     input: {rel} (sha256 {sha[:16]}, frozen from the live tree at the start)\n")
            for line in result["problems"] + [f"dirty: {d}" for d in result.get("dirty", [])]:
                fh.write("     " + line + "\n")
        return result

    def temp_state(self, name):
        path = os.path.join(self.ctx.temp_real, name)
        try:
            return os.stat(path).st_mtime
        except OSError:
            return None

    def check_module(self, module):
        """<module>/validate.py on the base, in this worktree, before any script runs.

        A pass is exit 0 with something said; a kill, a crash or silence is not.
        """
        ctx = self.ctx
        log = os.path.join(ctx.logs, f"base-check-{module}.log")
        t = time.perf_counter()
        with ctx.lock:
            empty_dir(self.tmp)
        with open(log, "wb") as fh:
            # exec: the external timeout, not BASH_ENV's; its 124 is read below.
            self.proc = subprocess.Popen(
                [BASH, "-c", 'cd "$1" && exec timeout "$2" python3 validate.py', "base-check",
                 f"{self.j}/tree/{module}", f"{BASE_CHECK_MINUTES}m"],
                cwd=self.tree, env=self.env(), stdout=fh, stderr=subprocess.STDOUT,
                stdin=subprocess.DEVNULL,
                creationflags=CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0)
            while self.proc.poll() is None:
                if ctx.abort.is_set():
                    kill_tree(self.proc)
                    break
                time.sleep(0.5)
            self.proc.wait()
        code, self.proc = self.proc.returncode, None
        said = open(log, encoding="utf-8", errors="replace").read()
        lines = [l for l in said.splitlines() if l.strip()]
        # What it found, when it says so: its PROBLEM(S) block, else its last lines.
        report = next((at for at, l in enumerate(lines) if re.search(r"\bPROBLEMS?\b", l)), None)
        shown = lines[report:report + 6] if report is not None else lines[-6:]
        check = {"module": module, "exit": code, "seconds": round(time.perf_counter() - t, 1),
                 "ok": code == 0 and bool(lines), "log": fwd(log), "said": [l.strip() for l in shown]}
        if code == 124:
            check["said"].append(f"(killed after {BASE_CHECK_MINUTES} minutes)")
        dirt = self.dirty()
        if dirt:
            check["dirty"] = dirt[:10]
            if not self.reset():
                self.usable = False
        if not self.classes_ok():
            self.restore_classes()
        with ctx.lock:
            empty_dir(self.tmp)
        return check


# ---------------------------------------------------------------------------
# The run
# ---------------------------------------------------------------------------

class Run:
    def __init__(self, args):
        self.args = args
        self.abort = threading.Event()
        self.fatal = None
        self.lock = threading.Lock()
        self.workers = []
        self.hints = {}
        self.live_notes = []
        self.suspects = {}          # {script still running: [why it is thought to have leaked]}
        self.paths, self.extra = {}, {}
        self.inputs, self.input_missing, self.input_sha, self.frozen = {}, {}, {}, {}

    def timeout_for(self, script):
        """Seconds: --timeout for all; else 3x the last recorded time, at least 30 minutes."""
        if self.args.timeout is not None:
            return self.args.timeout * 60
        if script in self.hints:
            return max(TIMEOUT_FLOOR_MINUTES * 60, TIMEOUT_SCALE * self.hints[script])
        return self.args.timeout_unknown * 60

    def guard(self, worker, script, temp_before):
        """After every script: nothing it, or any script still running, names moved.

        Returns the problems that are this script's; those of a script still
        running wait in self.suspects for it to finish. Any of either stops the run.
        """
        with self.lock:
            mine, others = [], collections.defaultdict(list)
            running = {w.current: w.paths for w in self.workers if w is not worker and w.current}
            now_live = fingerprint(self.live_root, self.args.live_ignore)
            if now_live != self.live_before:
                changed = files_changed(self.live_before, now_live, self.live_root, self.args.live_ignore)
                noted = []
                for rel in changed:
                    named = False
                    if covers(worker.paths, rel):
                        mine.append(f"the live {rel} changed while {script} ran, and the script names "
                                    f"it: a path escaped the rewrite, or somebody edited what it tests")
                        named = True
                    for other, paths in running.items():
                        if covers(paths, rel):
                            others[other].append(f"the live {rel} changed while {other} ran (seen after "
                                                 f"{script}), and {other} names it")
                            named = True
                    if not named:
                        noted.append(rel)
                if noted:
                    note = (f"after {script}: {len(noted)} live file(s) changed that no running script "
                            f"names, so somebody else's work: {', '.join(noted[:6])}"
                            f"{' ...' if len(noted) > 6 else ''}; carrying on")
                    self.live_notes.append(note)
                    say(note)
                elif not changed:
                    note = (f"after {script}: the live tree's git status moved "
                            f"({'; '.join(what_moved(self.live_before, now_live)[:3])} ...) "
                            f"but not one file's bytes did -- a commit or a git add; carrying on")
                    self.live_notes.append(note)
                    say(note)
                self.live_before = now_live
            # The gitignored inputs: git status cannot see them change.
            for rel, sha in self.input_sha.items():
                users = [s for s in [script, *running] if rel in self.inputs.get(s, ())]
                if users and file_sha(self.live_root, rel) != sha:
                    for user in users:
                        (mine if user == script else others[user]).append(
                            f"the live {rel} (gitignored, its input) changed while {user} ran")
                    self.input_sha[rel] = file_sha(self.live_root, rel) or ""
            for name, before in temp_before.items():
                after = worker.temp_state(name)
                if after is not None and after != before:
                    mine.append(f"{fwd(os.path.join(self.temp_real, name))} was written while "
                                f"{script} ran, and the script names it: a path escaped the rewrite")
            for other in self.workers:
                if other is worker or not os.path.isdir(other.tmp):
                    continue
                for name in worker.names - other.names:
                    if os.path.lexists(os.path.join(other.tmp, name)):
                        mine.append(f"worker {other.n}'s scratch holds {name}, which {script} "
                                    f"(worker {worker.n}) names and {other.current or 'nothing'} "
                                    f"(running there) does not")
            for other, problems in others.items():
                self.suspects.setdefault(other, []).extend(problems)
            everything = mine + [p for problems in others.values() for p in problems]
            if everything and not self.fatal:
                self.fatal = f"after {script} on worker {worker.n}:\n    " + "\n    ".join(everything)
                self.abort.set()
            # Inside the lock: until this scratch is empty another worker's guard
            # must still see whose names these are.
            empty_dir(worker.tmp)
            worker.current, worker.names, worker.paths = None, set(), set()
            return mine

    def worker_main(self, worker):
        try:
            self.worker_loop(worker)
        except BaseException as oops:
            say(f"w{worker.n} stopped: {type(oops).__name__}: {oops}")
        finally:
            worker.done.set()

    def worker_loop(self, worker):
        while not self.abort.is_set() and worker.usable:
            with self.lock:
                if not self.queue:
                    return
                script = self.queue.popleft()
            say(f"w{worker.n} start {script}" + (f" (last took {human(self.hints[script])}"
                                                 if script in self.hints else " (never timed")
                + f", timeout {human(self.timeout_for(script))})")
            try:
                result = worker.run_script(script)
            except Exception as oops:   # one script's trouble is not the run's
                result = {"script": script, "worker": worker.n, "status": "error", "exit": None,
                          "seconds": 0.0, "problems": [f"parallel.py: {type(oops).__name__}: {oops}"],
                          "caught": 0, "missed": 0, "cases": [],
                          "log": fwd(os.path.join(self.logs, script + ".log"))}
                if worker.proc is not None:
                    kill_tree(worker.proc)
                with self.lock:
                    worker.current, worker.names, worker.paths = None, set(), set()
                try:
                    if not worker.reset():
                        worker.usable = False
                except Exception:
                    worker.usable = False
            with self.lock:
                self.results.append(result)
            say(f"w{worker.n} done  {script}: {result['status']}, caught {result.get('caught')}, "
                f"missed {result.get('missed')}, {human(result['seconds'])}")

    def kill_all(self):
        for worker in self.workers:
            if worker.proc is not None:
                worker.stopped = True
            kill_tree(worker.proc)


def resolve_scripts(args, live_root, base, earlier=None):
    """The scripts to run, by name, and {name: path} for the ones given as files
    outside the base (a throwaway copy, say), which are read from there."""
    present = git("ls-tree", "--name-only", f"{base}:tools/negtest", cwd=live_root, check=True).stdout.split()
    scripts = [p for p in present if p.startswith("negtest_") and p.endswith(".sh") and not p.endswith("_dry.sh")]
    wanted, extra = [], {}
    if args.all:
        wanted = list(scripts)
    names = list(args.scripts)
    if args.list:
        for line in open(args.list, encoding="utf-8"):
            line = line.split("#", 1)[0].strip()
            if line:
                names.append(line)
    if earlier is not None and not names and not args.all:
        # --resume alone: what that run was asked to run, files outside the base included
        names = list(earlier.get("order") or [])
        for name, path in (earlier.get("extra") or {}).items():
            if name in names:
                names[names.index(name)] = path
    missing = []
    live_negtest = os.path.normcase(os.path.realpath(os.path.join(live_root, "tools", "negtest")))
    for name in names:
        if ("/" in fwd(name)) and os.path.isfile(name) and \
                os.path.normcase(os.path.dirname(os.path.realpath(name))) != live_negtest:
            base_name = os.path.basename(name)
            if not base_name.endswith(".sh") or base_name in scripts:
                raise Refused(f"{name}: a script from outside the tree needs a .sh name of its own "
                              f"(not one tools/negtest has)")
            extra[base_name] = fwd(os.path.abspath(name))
            if base_name not in wanted:
                wanted.append(base_name)
            continue
        name = os.path.basename(fwd(name))
        if not name.endswith(".sh"):
            name += ".sh"
        if not name.startswith("negtest_"):
            name = "negtest_" + name
        if name not in scripts:
            missing.append(name)
        elif name not in wanted:
            wanted.append(name)
    if missing:
        raise Refused(f"not in tools/negtest at {base[:10]}: {', '.join(missing)}")
    if not wanted:
        raise Refused("no scripts named (give names, --list FILE or --all)")
    return wanted, extra, scripts


def read_blobs(live_root, base, paths, raw=False):
    """Many files at the base in one git process (text, or bytes with raw=True)."""
    if not paths:
        return {}
    spec = "".join(f"{base}:{p}\n" for p in paths).encode("utf-8")
    data = git("cat-file", "--batch", cwd=live_root, input=spec, binary=True, check=True).stdout
    out, at = {}, 0
    for path in paths:
        end = data.index(b"\n", at)
        header = data[at:end].decode()
        at = end + 1
        if header.endswith("missing") or len(header.split()) < 3:
            continue
        size = int(header.split()[2])
        blob = data[at:at + size]
        out[path] = blob if raw else blob.decode("utf-8", "replace")
        at += size + 1
    return out


def shim_targets(live_root, base, cfg):
    """The tracked Python files whose code (not just comments) names a scratch path."""
    names = [n for n in git("ls-tree", "-r", "--name-only", "-z", base, cwd=live_root,
                            check=True).stdout.split("\0") if n.endswith(".py")]
    texts = read_blobs(live_root, base, names)
    found = {}
    for rel, text in texts.items():
        if rel in NOT_SHIMMED:
            continue
        new, _ = rewrite_text(text, cfg, shell=False)
        if new == text:
            continue
        changed = [a.strip() for a, b in zip(text.split("\n"), new.split("\n"))
                   if a != b and not a.lstrip().startswith("#")]
        if changed:
            found[rel] = changed
    return found


def java_digest(live_root, base):
    """What a compile of `base` depends on in the tree: every module's Java and compile.sh.

    Other agents commit to this tree all day; a commit that touches no Java
    leaves this unchanged, so an earlier --compile still serves.
    """
    listing = git("ls-tree", "-r", "-z", base, cwd=live_root, check=True).stdout
    lines = []
    for entry in listing.split("\0"):
        if not entry:
            continue
        meta, rel = entry.split("\t", 1)
        if (rel.endswith(".java") and rel.split("/")[1:4] == ["src", "main", "java"]) or rel == "compile.sh":
            lines.append(meta.split()[2] + " " + rel)
    return hashlib.sha256("\n".join(sorted(lines)).encode()).hexdigest()[:16]


def compile_cache(run_):
    return os.path.join(run_.negwork, "classes-" + java_digest(run_.live_root, run_.base))


def provenance_or_refuse(run_, args):
    shared = os.path.join(run_.temp_real, "elysium-classes")
    cache = compile_cache(run_)
    # A finished cache of the default negwork serves a run in another one too:
    # it is only read (copied), and finished once its stamp is written.
    default = os.path.join(ENV_DIR, "negwork", os.path.basename(cache))
    for folder in dict.fromkeys([cache, default]):
        stamp = os.path.join(folder, ".negtest-base")
        if os.path.isfile(stamp):
            said = open(stamp).read().split()
            say(f"classes: an earlier --compile of {said[0][:10] if said else '?'}, whose Java and "
                f"compile.sh are {run_.base[:10]}'s ({fwd(folder)})")
            return folder
    if args.compile:
        return None   # built once the first worktree exists
    real = os.path.realpath(shared)
    if not os.path.isdir(real) or not any(n.endswith(".srcs") for n in os.listdir(real)) \
            or not any(files for _, _, files in os.walk(real) if any(f.endswith(".class") for f in files)):
        raise Refused(f"{fwd(shared)} is empty: nothing has been compiled there. Run ./compile.sh "
                      f"at {run_.base[:10]} first, or pass --compile to have this build the "
                      f"classes of the base in a worktree")
    if args.trust_classes:
        say(f"classes: {fwd(shared)} taken as it is (--trust-classes), not checked against the base")
        return real
    differ, unknown, other = classes_provenance(run_.live_root, run_.base, real)
    if differ or unknown or other:
        lines = [f"{fwd(shared)} cannot be shown to be a compile of {run_.base[:10]}:"]
        for reason in other[:6]:
            lines.append("    " + reason)
        if differ:
            lines.append(f"    {len(differ)} Java file(s) were compiled as something other than the "
                         f"base has them, e.g. {', '.join(differ[:3])}")
        if unknown:
            lines.append(f"    {len(unknown)} Java file(s) have no record of what javac read, "
                         f"e.g. {', '.join(unknown[:3])}")
        lines.append("  pass --compile to build the base's classes in a worktree (about six minutes, "
                     "kept for later runs of the same base), or --trust-classes to use these anyway")
        raise Refused("\n".join(lines))
    say(f"classes: {fwd(shared)} is a compile of {run_.base[:10]} (every Java file javac read "
        f"there, by its .sha, is the base's)")
    return real


def compile_base(run_, worker):
    """./compile.sh in the first worktree, into a cache named after what it compiled."""
    cache = compile_cache(run_)
    remove_tree(cache)
    build = os.path.join(worker.dir, "compile-out")
    os.makedirs(build, exist_ok=True)
    env = dict(run_.base_env)
    done = run([sys.executable, os.path.join(run_.live_root, "tools", "setup_env.py"), "--check"])
    block = done.stdout.split("# compile.sh", 1)[-1].split("\n\n", 1)[0]
    exports = {}
    import shlex
    for line in block.splitlines():
        if line.startswith("export "):
            for item in shlex.split(line[len("export "):]):
                key, _, value = item.partition("=")
                exports[key] = value
    apply_exports(env, exports)
    env["ELYSIUM_OUT"] = f"{worker.j}/compile-out"
    say(f"compiling {run_.base[:10]} in {fwd(worker.tree)} (./compile.sh) ...")
    t = time.perf_counter()
    log = os.path.join(run_.logs, "compile-base.log")
    with open(log, "wb") as fh:
        code = subprocess.run([BASH, "./compile.sh"], cwd=worker.tree, env=env, stdout=fh,
                              stderr=subprocess.STDOUT).returncode
    if code:
        raise Refused(f"./compile.sh failed at {run_.base[:10]} (status {code}); see {fwd(log)}")
    if worker.dirty():
        raise Refused(f"./compile.sh left the worktree dirty: {worker.dirty()[:3]}")
    shutil.copytree(build, cache)
    remove_tree(build)
    with open(os.path.join(cache, ".negtest-base"), "w") as fh:
        fh.write(run_.base + "\n")
    say(f"compiled in {human(time.perf_counter() - t)}; kept at {fwd(cache)}")
    return cache


def module_validators(live_root, base):
    """The top-level modules the base has a validate.py for."""
    names = git("ls-tree", "-r", "--name-only", "-z", base, cwd=live_root, check=True).stdout.split("\0")
    return {n.split("/")[0] for n in names if n.count("/") == 1 and n.endswith("/validate.py")}


def script_modules(r, script):
    """The modules a script names whose validator the base check runs."""
    return sorted({p.split("/")[0] for p in r.paths.get(script, ()) if p.split("/")[0] in r.validators})


def check_base(r, workers, scripts):
    """{module: check}: every module validator the scripts name, run once on the base.

    negtest_p2.sh, run on a snapshot taken while it had iron.json broken,
    "caught" its first case off the break the base already had and passed:
    nothing a script does looks at the tree it starts from. A pass is
    remembered (negwork/base-checks.json) by base tree, module, validator jars
    and compiled classes, so a second run of the same base costs nothing.
    """
    wanted = sorted({m for s in scripts for m in script_modules(r, s)})
    cache_path = os.path.join(r.negwork, "base-checks.json")
    try:
        cache = json.load(open(cache_path, encoding="utf-8"))
    except (OSError, ValueError):
        cache = {}

    def key(module):
        return f"{r.base_tree}|{module}|{r.exports_digest}|{r.classes_digest}"

    results, todo = {}, []
    for module in wanted:
        hit = cache.get(key(module))
        if isinstance(hit, dict) and hit.get("ok"):
            results[module] = dict(hit, cached=True)
        else:
            todo.append(module)
    if results:
        say(f"the base itself: {len(results)} module validator(s) passed on this base tree before "
            f"({', '.join(sorted(results))})")
    if todo:
        say(f"the base itself: {len(todo)} module validator(s) the scripts name, run once on "
            f"{r.base[:10]} before any script: {', '.join(todo)}")
        queue = collections.deque(todo)

        def work(worker):
            while not r.abort.is_set() and worker.usable:
                with r.lock:
                    if not queue:
                        return
                    module = queue.popleft()
                check = worker.check_module(module)
                with r.lock:
                    results[module] = check
                say(f"w{worker.n} base check {module}: "
                    + (f"passes ({human(check['seconds'])})" if check["ok"] else
                       f"FAILS, exit {check['exit']} ({human(check['seconds'])}): "
                       + (" / ".join(check["said"][:2])[:120] if check["said"] else "said nothing")))

        threads = [threading.Thread(target=work, args=(w,), daemon=True) for w in workers]
        for thread in threads:
            thread.start()
        while any(thread.is_alive() for thread in threads):
            time.sleep(0.5)
        if r.abort.is_set():
            raise KeyboardInterrupt
        for module in todo:
            if module not in results:
                results[module] = {"module": module, "ok": False, "exit": None, "seconds": 0.0,
                                   "said": ["not run: no worker was left to run it"]}
        for module, check in results.items():
            if check.get("ok") and not check.get("cached"):
                cache[key(module)] = {k: v for k, v in check.items() if k != "cached"}
        try:
            with open(cache_path, "w", encoding="utf-8", newline="\n") as fh:
                json.dump(cache, fh, indent=1)
        except OSError:
            pass
    return results


def take_lock(negwork):
    path = os.path.join(negwork, "parallel.lock")
    os.makedirs(negwork, exist_ok=True)
    for _ in range(2):
        try:
            fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            os.write(fd, str(os.getpid()).encode())
            os.close(fd)
            return path
        except FileExistsError:
            try:
                pid = int(open(path).read().strip() or 0)
            except (OSError, ValueError):
                pid = 0
            if pid and pid_alive(pid):
                raise Refused(f"another parallel.py (pid {pid}) is using {fwd(negwork)}")
            os.remove(path)
    raise Refused(f"could not take {fwd(path)}")


def pick_numbers(r, count):
    """The lowest worker numbers whose %TEMP% junction is free or this negwork's own.

    The junction name is the one namespace every run on the machine shares:
    one leading anywhere else is another run's (in another --negwork), and
    that number is skipped, never taken down.
    """
    numbers, skipped, n = [], [], 1
    while len(numbers) < count and n < 200:
        link = os.path.join(r.temp_real, f"elysium-negw{n}")
        own = os.path.join(r.negwork, f"w{n}")
        if os.path.lexists(link) and not same_place(link, own):
            skipped.append(f"w{n} (its junction leads to {fwd(os.path.realpath(link))})")
        else:
            numbers.append(n)
        n += 1
    if len(numbers) < count:
        raise Refused(f"could not find {count} free worker numbers below 200")
    return numbers, skipped


def top_entries(live_root, base):
    """{name: is a folder} for the top of the base, and of the live tree (dist/ is only there)."""
    tops = {}
    for line in git("ls-tree", base, cwd=live_root, check=True).stdout.splitlines():
        meta, name = line.split("\t", 1)
        tops[name] = meta.split()[1] == "tree"
    for name in os.listdir(live_root):
        if name != ".git" and name not in tops:
            tops[name] = os.path.isdir(os.path.join(live_root, name))
    return tops


def ignored_inputs(r, scripts):
    """({script: [gitignored files it names that the live tree has]},
    {script: [ones under dist/ or repo/ that the live tree lacks too]})."""
    base_files = {p for p in git("ls-tree", "-r", "--name-only", "-z", r.base, cwd=r.live_root,
                                 check=True).stdout.split("\0") if p}
    candidates = sorted({p for s in scripts for p in r.paths[s] if p not in base_files})
    if not candidates:
        return {}, {}
    said = git("check-ignore", "--no-index", "--stdin", "-z", cwd=r.live_root,
               input="\0".join(candidates) + "\0").stdout
    ignored = {p for p in said.split("\0") if p}
    inputs, missing = {}, {}
    for script in scripts:
        for rel in sorted(r.paths[script] & ignored):
            full = os.path.join(r.live_root, rel)
            if os.path.isfile(full):
                inputs.setdefault(script, []).append(rel)
            elif rel.split("/")[0] in PRODUCT_DIRS and not os.path.exists(full):
                missing.setdefault(script, []).append(rel)
    return inputs, missing


def freeze_inputs(r):
    """Copy every gitignored input out of the live tree once, and hash the copy."""
    folder = os.path.join(r.negwork, "inputs")
    remove_tree(folder)
    for rel in sorted({rel for rels in r.inputs.values() for rel in rels}):
        dst = os.path.join(folder, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(os.path.join(r.live_root, rel), dst)
        sha = file_sha(folder, rel)
        if sha != file_sha(r.live_root, rel):
            raise Refused(f"the live {rel} changed while it was being copied; try again")
        r.frozen[rel], r.input_sha[rel] = dst, sha
        say(f"input: {rel} (gitignored) frozen from the live tree, sha256 {sha[:16]}, for "
            + ", ".join(s for s, rels in sorted(r.inputs.items()) if rel in rels))
    return folder


def print_table(results, order):
    by = {r["script"]: r for r in results}
    rows = [by[s] for s in order if s in by]
    width = max([len(r["script"]) for r in rows] + [6])
    print()
    print(f"{'script':<{width}}  {'status':<14} {'caught':>6} {'missed':>6} {'exit':>5} {'time':>8}  w")
    print("-" * (width + 50))
    for r in rows:
        status = r["status"] + (" (earlier)" if r.get("resumed_from") else "")
        worker = "-" if r.get("resumed_from") else r["worker"]
        print(f"{r['script']:<{width}}  {status:<14} {str(r.get('caught', '')):>6} "
              f"{str(r.get('missed', '')):>6} {str(r.get('exit', '')):>5} {human(r['seconds']):>8}  "
              f"{worker}")
        if r.get("resumed_from"):
            continue
        for problem in r.get("problems", [])[:3]:
            print(" " * (width + 2) + "! " + problem.splitlines()[0][:110])
    not_run = [s for s in order if s not in by]
    if not_run:
        print(f"not run ({len(not_run)}): {', '.join(not_run[:12])}{' ...' if len(not_run) > 12 else ''}")


def main(argv=None):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0].strip())
    ap.add_argument("scripts", nargs="*",
                    help="negtest scripts, by name (negtest_x.sh, negtest_x or x), or the path of a "
                         "script outside the tree (a throwaway copy), which is read from there")
    ap.add_argument("--all", action="store_true", help="every negtest_*.sh at the base")
    ap.add_argument("--list", help="a file of script names, one per line (# comments)")
    ap.add_argument("-j", "--jobs", type=int, default=4, help="workers (default 4)")
    ap.add_argument("--base", default=None,
                    help="the commit to test (default HEAD, or the --resume run's base); 'live' = the "
                         "live working tree as it is now, uncommitted and untracked files included, "
                         "as a throwaway commit")
    ap.add_argument("--force", action="store_true",
                    help="--base live even when the live tree may be in the middle of a negtest")
    ap.add_argument("--resume", metavar="SUMMARY",
                    help="a summary.json: skip the scripts that run found ok, at the same base tree "
                         "and classes (its base is the default; with no names, its list is)")
    ap.add_argument("--timeout", type=float, default=None,
                    help="minutes per script, for all of them (default: 3x each one's last recorded "
                         "time, at least 30)")
    ap.add_argument("--timeout-unknown", type=float, default=240,
                    help="minutes for a script never timed (default 240)")
    ap.add_argument("--tree", default=os.path.dirname(os.path.dirname(HERE)),
                    help="the live tree (default: the one this file is in)")
    ap.add_argument("--negwork", default=os.path.join(ENV_DIR, "negwork"))
    ap.add_argument("--logs", help="log folder (default Elysium-env/logs/negtests-<timestamp>)")
    ap.add_argument("--timings", default=os.path.join(ENV_DIR, "logs", "negtest-timings.json"),
                    help="timing hints: read to start the longest first and set timeouts, updated "
                         "afterwards")
    ap.add_argument("--compile", action="store_true",
                    help="build the base's classes with compile.sh in a worktree (cached by the "
                         "digest of the base's Java and compile.sh)")
    ap.add_argument("--trust-classes", action="store_true",
                    help="copy /tmp/elysium-classes without checking it is a compile of the base")
    ap.add_argument("--live-ignore", action="append", default=[], metavar="GLOB",
                    help="a live-tree path somebody else is editing, never to be read as a leak "
                         "(repeatable; fnmatch on the path git status prints)")
    ap.add_argument("--plan", action="store_true",
                    help="rewrite the scripts for worker 1, print what changed and what would be "
                         "refused, and stop; nothing is built or run")
    args = ap.parse_args(argv)
    if args.jobs < 1:
        ap.error("-j must be at least 1")

    r = Run(args)
    try:
        return go(r, args)
    except Refused as why:
        print(f"refused: {why}", flush=True)
        return 2


def live_script_texts(live_root):
    """Every negtest script as the live tree has it now (what may be running there)."""
    folder = os.path.join(live_root, "tools", "negtest")
    texts = {}
    for name in sorted(os.listdir(folder)):
        if name.startswith("negtest_") and name.endswith(".sh"):
            try:
                texts[name] = open(os.path.join(folder, name), encoding="utf-8", errors="replace").read()
            except OSError:
                pass
    return texts


def go(r, args):
    found = git("rev-parse", "--show-toplevel", cwd=args.tree)
    if found.returncode:
        raise Refused(f"{args.tree} is not a git tree")
    r.live_root = fwd(found.stdout.strip())
    r.live_real = fwd(os.path.realpath(r.live_root))
    r.live_posix = "/" + r.live_real[0].lower() + r.live_real[2:]
    r.negwork = os.path.normpath(args.negwork)
    r.temp_real, r.temp_forms = temp_spellings()
    r.live_warnings = []
    r.noted = set()
    for name in SHARED:
        if not os.path.isdir(os.path.join(r.temp_real, name)):
            raise Refused(f"/tmp/{name} does not resolve; run python3 tools/setup_env.py first")

    earlier = None
    if args.resume:
        try:
            earlier = json.load(open(args.resume, encoding="utf-8"))
        except (OSError, ValueError) as oops:
            raise Refused(f"cannot read {args.resume}: {oops}")
        lacking = [k for k in ("base", "base_tree", "classes_digest", "scripts") if not earlier.get(k)]
        if lacking:
            raise Refused(f"{args.resume} has no {', '.join(lacking)}: written before --resume "
                          f"existed, or by a run that stopped before its classes were ready")

    spec = args.base if args.base is not None else (None if earlier else "HEAD")
    if spec is None:
        if git("cat-file", "-e", earlier["base"] + "^{commit}", cwd=r.live_root).returncode:
            raise Refused(f"the base of {args.resume}, {earlier['base'][:10]}, is no longer in the "
                          f"repository (a --base live snapshot is garbage once git gc prunes it)")
        r.base = earlier["base"]
        r.base_spec = f"the base of {fwd(args.resume)}: {earlier.get('base_spec', '?')}"
    elif spec == "live":
        reasons = live_busy(r, live_script_texts(r.live_root), top_entries(r.live_root, "HEAD"))
        if reasons:
            print("--base live: the live tree may be in the middle of a negtest run the ordinary "
                  "way, and a snapshot of it now could hold a module half broken:")
            for reason in reasons:
                print("    " + reason)
            if not args.force and not args.plan:
                raise Refused("not snapshotting the live tree (see above); wait for it, or pass "
                              "--force if you know the tree is whole")
            print("    " + ("--force: snapshotting it anyway" if args.force else "(--plan: carrying on)"))
        r.live_warnings = reasons
        os.makedirs(r.negwork, exist_ok=True)
        r.base = snapshot_live(r.live_root, r.negwork)
        r.base_spec = "live working tree" + (" (--force)" if reasons else "")
    else:
        got = git("rev-parse", "--verify", spec + "^{commit}", cwd=r.live_root)
        if got.returncode:
            raise Refused(f"{spec} is not a commit")
        r.base, r.base_spec = got.stdout.strip(), spec
    r.base_tree = git("rev-parse", r.base + "^{tree}", cwd=r.live_root, check=True).stdout.strip()
    if earlier and earlier["base_tree"] != r.base_tree:
        raise Refused(f"{args.resume} tested the tree {earlier['base_tree'][:10]}; this base "
                      f"({r.base_spec}) is {r.base_tree[:10]}. Resume without --base to test its "
                      f"base again")

    scripts, r.extra, _ = resolve_scripts(args, r.live_root, r.base, earlier)
    in_base = [s for s in scripts if s not in r.extra]
    r.texts = read_blobs(r.live_root, r.base, [f"tools/negtest/{s}" for s in in_base])
    r.texts = {k.rsplit("/", 1)[1]: v for k, v in r.texts.items()}
    for name, path in r.extra.items():
        r.texts[name] = open(path, encoding="utf-8").read()
    tops = top_entries(r.live_root, r.base)
    r.paths = {s: script_paths(r.texts[s], tops) for s in scripts}
    r.inputs, r.input_missing = ignored_inputs(r, scripts)
    r.validators = module_validators(r.live_root, r.base)
    r.rules = rules_digest()
    r.base_checks = {}

    probe = Worker(1, r)
    r.shimmed = shim_targets(r.live_root, r.base, probe.cfg())

    if args.plan:
        bad = 0
        for script in scripts:
            text, notes = rewrite_text(r.texts[script], probe.cfg(script))
            problems = static_problems(text, 1)
            problems += [f"names {rel}, which git ignores and the live tree lacks: it would test "
                         f"nothing" for rel in r.input_missing.get(script, [])]
            bad += bool(problems)
            print(f"{script}: {'; '.join(notes) or 'nothing to rewrite'}"
                  + (f" [from {r.extra[script]}]" if script in r.extra else ""))
            for rel in r.inputs.get(script, []):
                print(f"    input: {rel} (gitignored; copied from the live tree into the worktree)")
            for p in problems:
                print("    REFUSED:", p)
        print(f"\nPython files the workers' sitecustomize rewrites as they load ({len(r.shimmed)}):")
        for rel, lines in sorted(r.shimmed.items()):
            print(f"    {rel}: {lines[0][:100]}" + (f" (+{len(lines) - 1})" if len(lines) > 1 else ""))
        named = collections.Counter(len(r.paths[s]) for s in scripts)
        print(f"\nlive-tree paths the scripts name (a change under one while it runs is fatal): "
              f"{sum(k * v for k, v in named.items())} in all, {min(named)} to {max(named)} a script; "
              f"{sum(1 for s in scripts if not r.paths[s])} script(s) name none")
        print(f"\n{len(scripts)} script(s), {bad} would be refused")
        return 1 if bad else 0

    lock = take_lock(r.negwork)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    r.logs = os.path.normpath(args.logs or os.path.join(ENV_DIR, "logs", f"negtests-{stamp}"))
    os.makedirs(r.logs, exist_ok=True)
    r.results, r.queue = [], collections.deque()
    # Ctrl-C, and Ctrl-Break (which Windows would otherwise answer by killing
    # this process outright, worktrees and junctions left behind).
    stops = [signal.SIGINT] + ([signal.SIGBREAK] if hasattr(signal, "SIGBREAK") else [])
    previous = {sig: signal.getsignal(sig) for sig in stops}

    def on_sigint(_sig, _frame):
        raise KeyboardInterrupt
    for sig in stops:
        signal.signal(sig, on_sigint)
    started_at = datetime.datetime.now()
    t0 = time.perf_counter()
    interrupted = False
    proof = {}
    order, jobs = list(scripts), 0
    threads = []
    inputs_dir = None
    r.classes_digest = None
    try:
        t = time.perf_counter()
        exports = validator_exports(r.live_root)
        r.base_env = apply_exports(dict(os.environ), exports)
        r.exports_digest, jar_count = exports_digest(exports)
        r.loadtest_shared = os.path.realpath(os.path.join(r.temp_real, "elysium-loadtest"))
        r.live_before = r.live_start = fingerprint(r.live_root, args.live_ignore)
        proof["live_tracked_before"], proof["tracked_files"] = tracked_hash(r.live_root)
        say(f"environment read and the live tree fingerprinted ({len(r.live_start['status'])} "
            f"changed path(s), {proof['tracked_files']} files hashed) in {human(time.perf_counter() - t)}; "
            f"validator jars {r.exports_digest} ({jar_count} files), output rules {r.rules}")
        if r.inputs:
            inputs_dir = freeze_inputs(r)
        source = provenance_or_refuse(r, args)

        hints = {}
        if os.path.isfile(args.timings):
            try:
                hints = {k: v["seconds"] for k, v in json.load(open(args.timings, encoding="utf-8")).items()}
            except (OSError, ValueError, KeyError, TypeError):
                hints = {}
        r.hints = hints
        numbers, skipped = pick_numbers(r, args.jobs)
        if skipped:
            say("worker numbers in use by another run, skipped: " + "; ".join(skipped))

        # The classes first (compiled in the first worker's worktree if asked),
        # since --resume needs their digest before it can say what is left.
        r.classes_base = os.path.join(r.negwork, "classes-base")
        first = None
        if source is None:
            first = Worker(numbers[0], r)
            r.workers.append(first)
            first.discard()
            os.makedirs(first.dir)
            git("worktree", "add", "--detach", "--quiet", fwd(first.tree), r.base,
                cwd=r.live_root, check=True)
            make_junction(first.link, first.dir)
            source = compile_base(r, first)
        remove_tree(r.classes_base)
        shutil.copytree(source, r.classes_base)
        if os.path.exists(os.path.join(r.classes_base, ".negtest-base")):
            os.remove(os.path.join(r.classes_base, ".negtest-base"))
        r.classes_digest = tree_digest(r.classes_base)

        remaining = list(scripts)
        if earlier:
            if earlier["classes_digest"] != r.classes_digest:
                raise Refused(f"{args.resume} ran against other compiled classes "
                              f"({earlier['classes_digest'][:12]}, these are {r.classes_digest[:12]}); "
                              f"pass the same --compile / --trust-classes choice it had")
            # An ok decided by other rules, or against other jars, says nothing
            # about this run: a resume after a parser fix carried exactly the
            # oks the fix was made to stop.
            unlike = [f"{what} {earlier.get(key) or 'unrecorded'}, now {mine}"
                      for what, key, mine in (("output rules", "rules_digest", r.rules),
                                              ("validator jars", "exports_digest", r.exports_digest))
                      if earlier.get(key) != mine]
            ok = {} if unlike else {x["script"]: x for x in earlier["scripts"] if x.get("status") == "ok"}
            if unlike:
                say(f"resume: carrying nothing from {fwd(args.resume)}, which read its scripts by "
                    f"other rules or against other jars ({'; '.join(unlike)}): every script runs again")
            input_changed = []
            for script in scripts:
                done = ok.get(script)
                sha = hashlib.sha256(r.texts[script].encode("utf-8")).hexdigest()
                frozen = {rel: r.input_sha[rel] for rel in r.inputs.get(script, ())}
                if done and done.get("text_sha") == sha and (done.get("inputs") or {}) != frozen:
                    input_changed.append(script)
                elif done and done.get("text_sha") == sha:
                    carried = dict(done)
                    carried["resumed_from"] = done.get("resumed_from") or fwd(os.path.abspath(args.resume))
                    r.results.append(carried)
                    remaining.remove(script)
            for script in input_changed:
                was = (ok[script].get("inputs") or {})
                now_ = {rel: r.input_sha[rel] for rel in r.inputs.get(script, ())}
                said = ", ".join(f"{rel} {was.get(rel, 'absent')[:8]} -> {now_.get(rel, 'absent')[:8]}"
                                 for rel in sorted(set(was) | set(now_)) if was.get(rel) != now_.get(rel))
                say(f"resume: {script} was ok against other gitignored inputs ({said}); it runs again")
            say(f"resume: {len(scripts) - len(remaining)} script(s) ok in {fwd(args.resume)} at the "
                f"same base tree {r.base_tree[:10]}, classes, inputs, jars and rules are not run "
                f"again; {len(remaining)} left")

        # Longest first; a script never timed goes ahead of them all, since it
        # may be the longest of the lot.
        queue = [s for s in remaining if s not in hints] + \
            sorted((s for s in remaining if s in hints), key=lambda s: -hints[s])
        order = [s for s in scripts if s not in remaining] + queue
        r.queue.extend(queue)

        timing = (f"timeout {human(args.timeout * 60)} each (--timeout)" if args.timeout is not None
                  else f"timeouts {TIMEOUT_SCALE}x the last recorded time, at least "
                       f"{TIMEOUT_FLOOR_MINUTES}m, {human(args.timeout_unknown * 60)} if never timed")
        say(f"base {r.base[:10]} ({r.base_spec}); {len(queue)} script(s) to run; -j {args.jobs}; "
            f"{timing}; logs {fwd(r.logs)}")
        if r.shimmed:
            say("hard-coded scratch paths redirected at load time in: " + ", ".join(sorted(r.shimmed)))
        jobs = min(args.jobs, len(queue))

        for n in numbers[:jobs]:
            worker = first if first is not None and first.n == n else Worker(n, r)
            if worker not in r.workers:
                r.workers.append(worker)
            took = worker.build()
            say(f"w{n} ready: {fwd(worker.tree)} as {worker.j} (checkout {human(took)})")

        # The base itself, before any script: a case is "caught" just as well by
        # a break the base already carries.
        if queue:
            r.base_checks = check_base(r, r.workers[:jobs], queue)
            failing = {m for m, check in r.base_checks.items() if not check.get("ok")}
            for script in list(queue):
                bad = [m for m in script_modules(r, script) if m in failing]
                if not bad:
                    continue
                problems = []
                for module in bad:
                    check = r.base_checks[module]
                    problems.append(f"the base's {module}/validate.py fails before any case is set up "
                                    f"(exit {check.get('exit')}; {check.get('log', 'no log')}), so a case "
                                    f"could be caught by a break the base already has")
                    problems += ["    " + line[:150] for line in check.get("said", [])[:4]]
                log = os.path.join(r.logs, script + ".log")
                with open(log, "w", encoding="utf-8", newline="\n") as fh:
                    fh.write("refused by parallel.py before it ran:\n" + "\n".join(problems) + "\n")
                r.results.append({"script": script, "worker": "-", "status": "refused",
                                  "problems": problems, "exit": None, "seconds": 0.0, "log": fwd(log),
                                  "caught": 0, "missed": 0, "cases": [],
                                  "text_sha": hashlib.sha256(r.texts[script].encode("utf-8")).hexdigest()})
                queue.remove(script)
                r.queue.remove(script)
                say(f"refused {script}: the base fails {', '.join(bad)} before any case")
            if failing and not queue:
                say("every script left names a module whose validator fails on the base")

        built = fingerprint(r.live_root, args.live_ignore)
        if built != r.live_before:
            # No script has run yet, so this is somebody else; the baseline the
            # guard holds every script to is the tree as it is now.
            changed = files_changed(r.live_before, built, r.live_root, args.live_ignore)
            r.noted.update(changed)
            note = ("while the workers were being built the live tree moved -- "
                    + (f"{len(changed)} file(s) edited by somebody else: {', '.join(changed[:6])}; "
                       f"a further change under a path a running script names will stop the run "
                       f"(--live-ignore GLOB exempts it)" if changed else "a commit or git add, no file changed"))
            r.live_notes.append(note)
            say(note)
            r.live_before = built

        # Not Thread.join(): in CPython 3.12 a join interrupted by Ctrl-C marks
        # the thread it was waiting on as stopped while it is still running
        # (gh-90882), and the cleanup below then took a worktree down under a
        # worker that was still checking it. Each worker says it is done itself.
        for w in r.workers[:jobs]:
            t = threading.Thread(target=r.worker_main, args=(w,), daemon=True)
            threads.append(t)
            t.start()
        while not all(w.done.is_set() for w in r.workers[:jobs]):
            time.sleep(0.5)
            if r.abort.is_set():
                r.kill_all()
    except KeyboardInterrupt:
        interrupted = True
        say("interrupted: stopping every script, then taking the workers down")
    finally:
        # From here on nothing may interrupt: a worktree taken down while its
        # worker is still checking it, or half removed, is worse than a wait.
        # (A second Ctrl-C arriving inside a handler did exactly that once.)
        for sig in stops:
            signal.signal(sig, signal.SIG_IGN)
        if interrupted or r.abort.is_set():
            r.abort.set()
        waited = time.perf_counter()
        busy = [w for w in r.workers[:len(threads)] if not w.done.is_set()]
        if busy:
            say(f"waiting for {len(busy)} worker(s) to finish their checks")
        while busy and time.perf_counter() - waited < 300:
            if r.abort.is_set():
                r.kill_all()
            time.sleep(0.5)
            busy = [w for w in busy if not w.done.is_set()]
        for w in busy:
            print(f"!!! worker {w.n} is still busy after five minutes; taking it down anyway")
        if r.workers:
            say(f"taking down {len(r.workers)} worker(s): worktrees, scratch, junctions")
        for worker in r.workers:
            try:
                worker.discard()
            except Exception as oops:
                print(f"!!! worker {worker.n} could not be taken down ({oops}); remove {fwd(worker.dir)} "
                      f"and the junction {fwd(worker.link)} by hand, then git worktree prune")
        git("worktree", "prune", cwd=r.live_root)
        if hasattr(r, "classes_base"):
            remove_tree(r.classes_base)
        if inputs_dir:
            remove_tree(inputs_dir)
        try:
            os.remove(lock)
        except (OSError, NameError):
            pass
        for sig in stops:
            signal.signal(sig, previous[sig])

    seconds = time.perf_counter() - t0
    live_after = fingerprint(r.live_root, args.live_ignore)
    if hasattr(r, "live_start"):
        proof["live_tracked_after"], _ = tracked_hash(r.live_root)
        proof["live_status_unchanged"] = live_after == r.live_start
        proof["live_files_changed"] = files_changed(r.live_start, live_after, r.live_root, args.live_ignore)
        proof["live_files_noted"] = sorted(r.noted & set(proof["live_files_changed"]))
        proof["live_tracked_unchanged"] = proof["live_tracked_after"] == proof["live_tracked_before"]

    print_table(r.results, order)
    counts = collections.Counter(res["status"] for res in r.results)
    print()
    fresh = [res for res in r.results if not res.get("resumed_from")]
    print(f"{len(r.results)} of {len(order)} accounted for ({len(fresh)} run now, "
          f"{len(r.results) - len(fresh)} ok earlier) in {human(seconds)} with -j {jobs}: "
          + ", ".join(f"{v} {k}" for k, v in sorted(counts.items())))
    if hasattr(r, "live_start"):
        print(f"live tree: git status {'unchanged' if proof['live_status_unchanged'] else 'MOVED'}; "
              f"all {proof['tracked_files']} tracked and untracked files "
              f"{'byte-identical' if proof['live_tracked_unchanged'] else 'NOT byte-identical'} "
              f"before and after the run")
        for rel in proof["live_files_changed"][:20]:
            print("    changed: " + rel + ("  (no running script named it: somebody else's)"
                                           if rel in r.noted else ""))
    for note in r.live_notes:
        print("    note: " + note)

    summary = {
        "base": r.base, "base_spec": r.base_spec, "base_tree": r.base_tree,
        "classes_digest": r.classes_digest, "jobs": jobs,
        "rules_digest": r.rules, "exports_digest": getattr(r, "exports_digest", None),
        "base_checks": r.base_checks,
        "timeout_minutes": args.timeout, "timeout_unknown_minutes": args.timeout_unknown,
        "started": started_at.isoformat(timespec="seconds"),
        "finished": datetime.datetime.now().isoformat(timespec="seconds"),
        "seconds": round(seconds, 1), "interrupted": interrupted, "fatal": r.fatal,
        "resumed_from": fwd(os.path.abspath(args.resume)) if args.resume else None,
        "live_warnings": r.live_warnings, "live_notes": r.live_notes,
        "shimmed": sorted(r.shimmed), "live_ignore": args.live_ignore, "proof": proof,
        "inputs": {rel: sha for rel, sha in r.input_sha.items()},
        "order": order, "extra": r.extra,
        "counts": dict(counts), "not_run": [s for s in order if s not in {x["script"] for x in r.results}],
        "scripts": sorted(r.results, key=lambda x: order.index(x["script"])),
    }
    path = os.path.join(r.logs, "summary.json")
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(summary, fh, indent=1)
    print(f"summary: {fwd(path)}")

    try:
        hints = json.load(open(args.timings, encoding="utf-8")) if os.path.isfile(args.timings) else {}
    except (OSError, ValueError):
        hints = {}
    for res in fresh:
        if res["status"] not in ("aborted", "refused", "leaked") and res["seconds"] \
                and res["script"] not in r.extra:
            hints[res["script"]] = {"seconds": res["seconds"], "status": res["status"],
                                    "when": summary["finished"], "base": r.base[:10]}
    try:
        with open(args.timings, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(dict(sorted(hints.items())), fh, indent=1)
    except OSError as oops:
        print(f"(timing hints not saved: {oops})")

    if r.fatal:
        print("\n!!! RUN STOPPED: " + r.fatal)
        print(f"    resume with: python3 tools/negtest/parallel.py --resume {fwd(path)}")
        return 3
    if interrupted:
        return 1
    bad = [res for res in r.results if res["status"] != "ok"]
    return 1 if bad or summary["not_run"] else 0


if __name__ == "__main__":
    sys.exit(main())
