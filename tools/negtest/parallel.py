#!/usr/bin/env python3
"""
Run many negative-test scripts at once, each in its own copy of the tree.

    python3 tools/negtest/parallel.py negtest_quick_move.sh negtest_spawners.sh
    python3 tools/negtest/parallel.py -j 4 --list scripts.txt
    python3 tools/negtest/parallel.py --all --plan     rewrite every script, run nothing

Every script in tools/negtest was written to run alone, in /tmp/Elysium-mono
itself: it snapshots whole modules with tar, breaks them, puts them back, and
keeps its scratch at fixed names (/tmp/h.bak, /tmp/<module>_snap.tar, /tmp/_ns,
lock dirs). Two at once in one tree, or two that share a scratch name, would
spoil each other, so until now they ran one at a time and the corpus took more
than a day. Here each worker has a world of its own:

  C:/Users/weaks/Elysium-env/negwork/w<N>/tree      a detached git worktree of the base
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

After each script: the worktree must be clean (git status --porcelain), and is
put back with git checkout -- . and git clean -fdq if it is not -- reported as
"left the tree dirty"; the worker's classes must be as they were (restored if a
script recompiled them); and, under one lock for all workers, the LIVE tree's
git status and the content of every file it lists modified or untracked must
match what they were when the run started, nothing named by the script may have
appeared in the real %TEMP%, and nothing named by it may have appeared in
another worker's scratch. If any of those fails, every running script is killed
and the run stops: exit 3.

Exit status: 0 every script caught everything and left its tree clean; 1 a
script missed a case, errored, timed out, was refused or left its tree dirty;
2 the run was refused before it started; 3 THE LIVE TREE CHANGED, or a worker's
path leaked, and everything was stopped.
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
    done = subprocess.run(args, cwd=cwd, env=env, input=input, capture_output=True,
                          text=not binary, encoding=None if binary else "utf-8",
                          errors=None if binary else "replace")
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
TROUBLE = re.compile(r"STALE ANCHOR|anchor not found|SETUP FAILED|GENERATOR BROKE"
                     r"|Traceback \(most recent call last\)")


def parse_output(text):
    cases, caught, missed, allowed = [], 0, 0, 0
    summary, restore, trouble = None, None, []
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
        for pattern, _ in SUMMARIES:
            for s in pattern.finditer(line):
                summary = (int(s.group(1)), int(s.group(2)), line.strip())
        if BROKEN.search(line):
            restore = "broken"
        elif RESTORED.search(line) and restore != "broken":
            restore = "clean"
        if TROUBLE.search(line) and len(trouble) < 5:
            trouble.append(line.strip()[:160])
    return {"cases": cases, "caught_lines": caught, "missed_lines": missed,
            "allowed_lines": allowed, "summary": summary, "restore": restore,
            "trouble": trouble}


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


def tracked_hash(root):
    """One digest over the working content of every tracked file (for proofs)."""
    names = [n for n in git("ls-files", "-z", cwd=root).stdout.split("\0") if n]
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
# The compiled classes
# ---------------------------------------------------------------------------

def blob_sha(data):
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def classes_provenance(live_root, base, classes):
    """Whether `classes` provably holds a compile of `base`'s Java.

    compile.sh writes <module>.srcs (the file list) just before javac reads the
    sources. A Java file whose live copy is the base's and was last written
    before that moment was compiled as the base has it. One last written after
    it may have been anything at compile time; one that differs and was not
    touched since was compiled from something else.
    """
    listing = git("ls-tree", "-r", "-z", base, cwd=live_root, check=True).stdout
    java = collections.defaultdict(dict)
    for entry in listing.split("\0"):
        if not entry:
            continue
        meta, rel = entry.split("\t", 1)
        if rel.endswith(".java") and "/src/main/java/" in rel:
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
        started = os.stat(srcs).st_mtime
        for rel, sha in java[module].items():
            path = os.path.join(live_root, rel)
            try:
                written = os.stat(path).st_mtime
                same = blob_sha(open(path, "rb").read()) == sha
            except OSError:
                unknown.append(rel)
                continue
            if written > started:
                unknown.append(rel)
            elif not same:
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


def temp_spellings():
    """Git Bash's /tmp, as each tool on this machine may spell it."""
    real = run([BASH, "-c", "cygpath -m /tmp"]).stdout.strip()
    if not real or not os.path.isdir(real):
        raise Refused(f"could not ask Git Bash where /tmp is (got {real!r})")
    posix = run([BASH, "-c", f"cygpath -u '{real}'"]).stdout.strip()
    if os.path.normcase(os.path.realpath("C:/tmp")) != os.path.normcase(os.path.realpath(real)):
        raise Refused(f"C:/tmp is not {real}: Windows Python would not see the /tmp Git Bash sees")
    forms = {real, posix, "C:/tmp", "/c/tmp", "/tmp"}
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
        self.link = os.path.join(ctx.temp_real, self.junction)
        self.j = "/tmp/" + self.junction            # every spelling the scripts get
        self.proc = None
        self.current = None                         # the script running here
        self.names = set()                          # what it names under /tmp
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
        if self.dirty():
            raise RuntimeError(f"the fresh worktree {self.tree} is not clean: {self.dirty()[:3]}")
        return built

    def discard(self):
        """Take down whatever a run before this one left here."""
        if is_junction(self.link):
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

    def run_script(self, script):
        ctx = self.ctx
        result = {"script": script, "worker": self.n, "status": None, "problems": [],
                  "exit": None, "seconds": 0.0, "log": fwd(os.path.join(ctx.logs, script + ".log"))}
        original = ctx.texts[script]
        text, notes = rewrite_text(original, self.cfg(script))
        result["rewrites"] = notes
        refused = static_problems(text, self.n)
        if refused:
            result.update(status="refused", problems=refused)
            with open(result["log"], "w", encoding="utf-8", newline="\n") as fh:
                fh.write("refused by parallel.py before it ran:\n" + "\n".join(refused) + "\n")
            return result
        with ctx.lock:
            empty_dir(self.tmp)
            self.names = scratch_names(original, ctx.temp_forms)
            self.current = script
        os.makedirs(os.path.join(self.tmp, RUN_DIR), exist_ok=True)
        copy = os.path.join(self.tmp, RUN_DIR, script)
        with open(copy, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        if run([BASH, "-n", copy]).returncode:
            result.update(status="refused", problems=["the rewritten script does not parse (bash -n)"])
            return result
        temp_before = {n: self.temp_state(n) for n in self.names}

        started = time.time()
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
                elif time.perf_counter() - t > ctx.timeout:
                    killed = "timeout"
                if killed:
                    kill_tree(self.proc)
                    break
                time.sleep(0.5)
            self.proc.wait()
        result["seconds"] = round(time.perf_counter() - t, 1)
        result["exit"] = self.proc.returncode
        self.proc = None

        with open(result["log"], "rb") as fh:
            out = fh.read().decode("utf-8", "replace")
        parsed = parse_output(out)
        result.update(cases=parsed["cases"], restore=parsed["restore"], trouble=parsed["trouble"],
                      caught_lines=parsed["caught_lines"], missed_lines=parsed["missed_lines"])
        if parsed["summary"]:
            result["summary"] = parsed["summary"][2]
            result["caught"] = parsed["summary"][0]
            result["missed"] = max(parsed["summary"][1], parsed["missed_lines"])
        else:
            result["summary"] = None
            result["caught"] = parsed["caught_lines"]
            result["missed"] = parsed["missed_lines"]

        dirt = self.dirty()
        if dirt:
            result["dirty"] = dirt[:40]
            if not self.reset():
                self.usable = False
                result["problems"].append("the worktree would not go back to the base; worker retired")
        if not self.classes_ok():
            result["problems"].append("the worker's compiled classes changed (a compile.sh run?); restored")
            self.restore_classes()

        if killed == "abort":
            result["status"] = "aborted"
        elif killed == "timeout":
            result["status"] = "timeout"
        elif result["missed"]:
            result["status"] = "missed"
        elif parsed["restore"] == "broken":
            result["status"] = "restore-failed"
        elif dirt:
            result["status"] = "dirty"
        elif result["exit"] != 0 or not (parsed["summary"] or parsed["cases"]):
            result["status"] = "error"
        else:
            result["status"] = "ok"
        if dirt:
            result["problems"].append(f"left the tree dirty ({len(dirt)} path(s))")

        ctx.guard(self, script, started, temp_before)
        with open(result["log"], "a", encoding="utf-8", newline="\n") as fh:
            fh.write(f"\n==== parallel.py: {script} on worker {self.n}, base {ctx.base[:10]}: "
                     f"{result['status']}, exit {result['exit']}, {human(result['seconds'])}, "
                     f"caught {result['caught']}, missed {result['missed']}\n")
            for line in result["rewrites"]:
                fh.write("     rewrite: " + line + "\n")
            for line in result["problems"] + [f"dirty: {d}" for d in result.get("dirty", [])]:
                fh.write("     " + line + "\n")
        return result

    def temp_state(self, name):
        path = os.path.join(self.ctx.temp_real, name)
        try:
            return os.stat(path).st_mtime
        except OSError:
            return None


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
        self.timeout = args.timeout * 60

    def guard(self, worker, script, started, temp_before):
        """After every script: nothing outside the worker moved. Fatal if it did."""
        with self.lock:
            problems = []
            now_live = fingerprint(self.live_root, self.args.live_ignore)
            if now_live != self.live_before:
                moved = what_moved(self.live_before, now_live)
                problems.append("THE LIVE TREE CHANGED:\n      " + "\n      ".join(moved[:30]))
            for name, before in temp_before.items():
                after = worker.temp_state(name)
                if after is not None and after != before:
                    problems.append(f"{fwd(os.path.join(self.temp_real, name))} was written while "
                                    f"{script} ran, and the script names it: a path escaped the rewrite")
            for other in self.workers:
                if other is worker or not os.path.isdir(other.tmp):
                    continue
                for name in worker.names - other.names:
                    if os.path.lexists(os.path.join(other.tmp, name)):
                        problems.append(f"worker {other.n}'s scratch holds {name}, which {script} "
                                        f"(worker {worker.n}) names and {other.current or 'nothing'} "
                                        f"(running there) does not")
            if problems and not self.fatal:
                self.fatal = f"after {script} on worker {worker.n}:\n    " + "\n    ".join(problems)
                self.abort.set()
            # Inside the lock: until this scratch is empty another worker's guard
            # must still see whose names these are.
            empty_dir(worker.tmp)
            worker.current, worker.names = None, set()

    def worker_loop(self, worker):
        while not self.abort.is_set() and worker.usable:
            with self.lock:
                if not self.queue:
                    return
                script = self.queue.popleft()
            say(f"w{worker.n} start {script}" + (f" (last took {human(self.hints[script])})"
                                                 if script in self.hints else ""))
            try:
                result = worker.run_script(script)
            except Exception as oops:   # one script's trouble is not the run's
                result = {"script": script, "worker": worker.n, "status": "error", "exit": None,
                          "seconds": 0.0, "problems": [f"parallel.py: {type(oops).__name__}: {oops}"],
                          "caught": 0, "missed": 0, "cases": [],
                          "log": fwd(os.path.join(self.logs, script + ".log"))}
                if worker.proc is not None:
                    kill_tree(worker.proc)
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
            kill_tree(worker.proc)


def resolve_scripts(args, live_root, base):
    present = git("ls-tree", "--name-only", f"{base}:tools/negtest", cwd=live_root, check=True).stdout.split()
    scripts = [p for p in present if p.startswith("negtest_") and p.endswith(".sh") and not p.endswith("_dry.sh")]
    wanted = []
    if args.all:
        wanted = list(scripts)
    names = list(args.scripts)
    if args.list:
        for line in open(args.list, encoding="utf-8"):
            line = line.split("#", 1)[0].strip()
            if line:
                names.append(line)
    missing = []
    for name in names:
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
    return wanted


def read_blobs(live_root, base, paths):
    """Many files at the base in one git process."""
    if not paths:
        return {}
    spec = "".join(f"{base}:{p}\n" for p in paths).encode("utf-8")
    raw = git("cat-file", "--batch", cwd=live_root, input=spec, binary=True, check=True).stdout
    out, at = {}, 0
    for path in paths:
        end = raw.index(b"\n", at)
        header = raw[at:end].decode()
        at = end + 1
        if header.endswith("missing"):
            continue
        size = int(header.split()[2])
        out[path] = raw[at:at + size].decode("utf-8", "replace")
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


def provenance_or_refuse(run_, args):
    shared = os.path.join(run_.temp_real, "elysium-classes")
    cache = os.path.join(run_.negwork, "classes-" + run_.base[:12])
    stamp = os.path.join(cache, ".negtest-base")
    if os.path.isfile(stamp) and open(stamp).read().strip() == run_.base:
        say(f"classes: compiled from {run_.base[:10]} by an earlier --compile ({fwd(cache)})")
        return cache
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
            lines.append(f"    {len(unknown)} Java file(s) were written after the compile, so what "
                         f"was compiled is unknown, e.g. {', '.join(unknown[:3])}")
        lines.append("  pass --compile to build the base's classes in a worktree (about six minutes, "
                     "kept for later runs of the same base), or --trust-classes to use these anyway")
        raise Refused("\n".join(lines))
    say(f"classes: {fwd(shared)} is a compile of {run_.base[:10]} (every Java file unchanged "
        f"since its module was compiled)")
    return real


def compile_base(run_, worker):
    """./compile.sh in the first worktree, into a cache named after the base."""
    cache = os.path.join(run_.negwork, "classes-" + run_.base[:12])
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


def print_table(results, order):
    by = {r["script"]: r for r in results}
    rows = [by[s] for s in order if s in by]
    width = max([len(r["script"]) for r in rows] + [6])
    print()
    print(f"{'script':<{width}}  {'status':<14} {'caught':>6} {'missed':>6} {'exit':>5} {'time':>8}  w")
    print("-" * (width + 50))
    for r in rows:
        print(f"{r['script']:<{width}}  {r['status']:<14} {str(r.get('caught', '')):>6} "
              f"{str(r.get('missed', '')):>6} {str(r.get('exit', '')):>5} {human(r['seconds']):>8}  "
              f"{r['worker']}")
        for problem in r.get("problems", [])[:3]:
            print(" " * (width + 2) + "! " + problem.splitlines()[0][:110])
    not_run = [s for s in order if s not in by]
    if not_run:
        print(f"not run ({len(not_run)}): {', '.join(not_run[:12])}{' ...' if len(not_run) > 12 else ''}")


def main(argv=None):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0].strip())
    ap.add_argument("scripts", nargs="*", help="negtest scripts, by name (negtest_x.sh, negtest_x or x)")
    ap.add_argument("--all", action="store_true", help="every negtest_*.sh at the base")
    ap.add_argument("--list", help="a file of script names, one per line (# comments)")
    ap.add_argument("-j", "--jobs", type=int, default=4, help="workers (default 4)")
    ap.add_argument("--base", default="HEAD",
                    help="the commit to test (default HEAD); 'live' = the live working tree as it "
                         "is now, uncommitted and untracked files included, as a throwaway commit")
    ap.add_argument("--timeout", type=float, default=90, help="minutes per script (default 90)")
    ap.add_argument("--tree", default=os.path.dirname(os.path.dirname(HERE)),
                    help="the live tree (default: the one this file is in)")
    ap.add_argument("--negwork", default=os.path.join(ENV_DIR, "negwork"))
    ap.add_argument("--logs", help="log folder (default Elysium-env/logs/negtests-<timestamp>)")
    ap.add_argument("--timings", default=os.path.join(ENV_DIR, "logs", "negtest-timings.json"),
                    help="timing hints: read to start the longest first, updated afterwards")
    ap.add_argument("--compile", action="store_true",
                    help="build the base's classes with compile.sh in a worktree (cached per base)")
    ap.add_argument("--trust-classes", action="store_true",
                    help="copy /tmp/elysium-classes without checking it is a compile of the base")
    ap.add_argument("--live-ignore", action="append", default=[], metavar="GLOB",
                    help="a live-tree path somebody else is editing, not to be read as a leak "
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


def go(r, args):
    found = git("rev-parse", "--show-toplevel", cwd=args.tree)
    if found.returncode:
        raise Refused(f"{args.tree} is not a git tree")
    r.live_root = fwd(found.stdout.strip())
    r.live_real = fwd(os.path.realpath(r.live_root))
    r.live_posix = "/" + r.live_real[0].lower() + r.live_real[2:]
    r.negwork = os.path.normpath(args.negwork)
    r.temp_real, r.temp_forms = temp_spellings()
    for name in SHARED:
        if not os.path.isdir(os.path.join(r.temp_real, name)):
            raise Refused(f"/tmp/{name} does not resolve; run python3 tools/setup_env.py first")

    if args.base == "live":
        os.makedirs(r.negwork, exist_ok=True)
        r.base = snapshot_live(r.live_root, r.negwork)
        r.base_spec = "live working tree"
    else:
        got = git("rev-parse", "--verify", args.base + "^{commit}", cwd=r.live_root)
        if got.returncode:
            raise Refused(f"{args.base} is not a commit")
        r.base, r.base_spec = got.stdout.strip(), args.base
    scripts = resolve_scripts(args, r.live_root, r.base)
    r.texts = read_blobs(r.live_root, r.base, [f"tools/negtest/{s}" for s in scripts])
    r.texts = {k.rsplit("/", 1)[1]: v for k, v in r.texts.items()}

    probe = Worker(1, r)
    r.shimmed = shim_targets(r.live_root, r.base, probe.cfg())

    if args.plan:
        bad = 0
        for script in scripts:
            text, notes = rewrite_text(r.texts[script], probe.cfg(script))
            problems = static_problems(text, 1)
            bad += bool(problems)
            print(f"{script}: {'; '.join(notes) or 'nothing to rewrite'}")
            for p in problems:
                print("    REFUSED:", p)
        print(f"\nPython files the workers' sitecustomize rewrites as they load ({len(r.shimmed)}):")
        for rel, lines in sorted(r.shimmed.items()):
            print(f"    {rel}: {lines[0][:100]}" + (f" (+{len(lines) - 1})" if len(lines) > 1 else ""))
        print(f"\n{len(scripts)} script(s), {bad} would be refused")
        return 1 if bad else 0

    lock = take_lock(r.negwork)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    r.logs = os.path.normpath(args.logs or os.path.join(ENV_DIR, "logs", f"negtests-{stamp}"))
    os.makedirs(r.logs, exist_ok=True)
    r.results, r.queue = [], collections.deque()
    previous_sigint = signal.getsignal(signal.SIGINT)
    started_at = datetime.datetime.now()
    t0 = time.perf_counter()
    interrupted = False
    proof = {}
    try:
        r.base_env = apply_exports(dict(os.environ), validator_exports(r.live_root))
        r.loadtest_shared = os.path.realpath(os.path.join(r.temp_real, "elysium-loadtest"))
        r.live_before = fingerprint(r.live_root, args.live_ignore)
        proof["live_tracked_before"], proof["tracked_files"] = tracked_hash(r.live_root)
        source = provenance_or_refuse(r, args)

        hints = {}
        if os.path.isfile(args.timings):
            try:
                hints = {k: v["seconds"] for k, v in json.load(open(args.timings, encoding="utf-8")).items()}
            except (OSError, ValueError, KeyError, TypeError):
                hints = {}
        r.hints = hints
        order = list(scripts)
        if hints:
            # Longest first; a script never timed goes ahead of them all, since
            # it may be the longest of the lot.
            order = [s for s in scripts if s not in hints] + \
                sorted((s for s in scripts if s in hints), key=lambda s: -hints[s])
        r.queue.extend(order)

        say(f"base {r.base[:10]} ({r.base_spec}); {len(order)} script(s); -j {args.jobs}; "
            f"timeout {human(r.timeout)} each; logs {fwd(r.logs)}")
        if r.shimmed:
            say("hard-coded scratch paths redirected at load time in: " + ", ".join(sorted(r.shimmed)))
        jobs = min(args.jobs, len(order))

        # Build the first worker, compile there if asked, then the rest.
        r.classes_base = os.path.join(r.negwork, "classes-base")
        if source is not None:
            remove_tree(r.classes_base)
            shutil.copytree(source, r.classes_base)
        r.classes_digest = None
        for n in range(1, jobs + 1):
            worker = Worker(n, r)
            r.workers.append(worker)
            if n == 1 and source is None:
                # --compile: the first worktree compiles the base, then is rebuilt
                worker.discard()
                os.makedirs(worker.dir)
                git("worktree", "add", "--detach", "--quiet", fwd(worker.tree), r.base,
                    cwd=r.live_root, check=True)
                make_junction(worker.link, worker.dir)
                cache = compile_base(r, worker)
                remove_tree(r.classes_base)
                shutil.copytree(cache, r.classes_base)
                os.remove(os.path.join(r.classes_base, ".negtest-base"))
            if r.classes_digest is None:
                r.classes_digest = tree_digest(r.classes_base)
            took = worker.build()
            say(f"w{n} ready: {fwd(worker.tree)} as {worker.j} (checkout {human(took)})")
        if r.live_before != fingerprint(r.live_root, args.live_ignore):
            raise Refused("the live tree changed while the workers were being built -- somebody "
                          "else is editing it; pass --live-ignore for their paths:\n    "
                          + "\n    ".join(what_moved(r.live_before, fingerprint(r.live_root, args.live_ignore))[:20]))

        def on_sigint(_sig, _frame):
            raise KeyboardInterrupt
        signal.signal(signal.SIGINT, on_sigint)
        threads = [threading.Thread(target=r.worker_loop, args=(w,), daemon=True) for w in r.workers]
        for t in threads:
            t.start()
        try:
            while any(t.is_alive() for t in threads):
                for t in threads:
                    t.join(0.5)
                if r.abort.is_set():
                    r.kill_all()
        except KeyboardInterrupt:
            interrupted = True
            say("interrupted: stopping every script, then taking the workers down")
            r.abort.set()
            r.kill_all()
            for t in threads:
                t.join(60)
    except KeyboardInterrupt:
        interrupted = True
        r.abort.set()
        r.kill_all()
    finally:
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        for worker in r.workers:
            try:
                worker.discard()
            except Exception as oops:
                print(f"!!! worker {worker.n} could not be taken down ({oops}); remove {fwd(worker.dir)} "
                      f"and the junction {fwd(worker.link)} by hand, then git worktree prune")
        git("worktree", "prune", cwd=r.live_root)
        if hasattr(r, "classes_base"):
            remove_tree(r.classes_base)
        try:
            os.remove(lock)
        except (OSError, NameError):
            pass
        signal.signal(signal.SIGINT, previous_sigint)

    seconds = time.perf_counter() - t0
    live_after = fingerprint(r.live_root, args.live_ignore)
    proof["live_tracked_after"], _ = tracked_hash(r.live_root)
    proof["live_status_unchanged"] = live_after == r.live_before
    proof["live_tracked_unchanged"] = proof["live_tracked_after"] == proof["live_tracked_before"]

    print_table(r.results, order)
    counts = collections.Counter(res["status"] for res in r.results)
    print()
    print(f"{len(r.results)} of {len(order)} run in {human(seconds)} with -j {jobs}: "
          + ", ".join(f"{v} {k}" for k, v in sorted(counts.items())))
    print(f"live tree: status and changed files {'unchanged' if proof['live_status_unchanged'] else 'CHANGED'}; "
          f"all {proof['tracked_files']} tracked files {'byte-identical' if proof['live_tracked_unchanged'] else 'NOT identical'} "
          f"before and after")
    if not proof["live_status_unchanged"]:
        for line in what_moved(r.live_before, live_after)[:20]:
            print("    " + line)

    summary = {
        "base": r.base, "base_spec": r.base_spec, "jobs": jobs, "timeout_minutes": args.timeout,
        "started": started_at.isoformat(timespec="seconds"),
        "finished": datetime.datetime.now().isoformat(timespec="seconds"),
        "seconds": round(seconds, 1), "interrupted": interrupted, "fatal": r.fatal,
        "shimmed": sorted(r.shimmed), "live_ignore": args.live_ignore, "proof": proof,
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
    for res in r.results:
        if res["status"] not in ("aborted", "refused") and res["seconds"]:
            hints[res["script"]] = {"seconds": res["seconds"], "status": res["status"],
                                    "when": summary["finished"], "base": r.base[:10]}
    try:
        with open(args.timings, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(dict(sorted(hints.items())), fh, indent=1)
    except OSError as oops:
        print(f"(timing hints not saved: {oops})")

    if r.fatal:
        print("\n!!! RUN STOPPED: " + r.fatal)
        return 3
    if interrupted:
        return 1
    bad = [res for res in r.results if res["status"] != "ok"]
    return 1 if bad or summary["not_run"] else 0


if __name__ == "__main__":
    sys.exit(main())
