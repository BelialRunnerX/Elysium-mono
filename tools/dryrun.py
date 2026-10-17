#!/usr/bin/env python3
"""
Turn a negative-test script into a dry run that only checks its anchors.

    python3 tools/dryrun.py tools/negtest/negtest_<name>.sh "<ok phrase>" ["<second>"]

By default the dry copy runs inside a throwaway git worktree: a copy of the
working tree as it is right now -- HEAD, plus every uncommitted change and
every untracked file -- made one directory below %TEMP% and removed afterwards
whatever happened. Every mention of the tree in the script is pointed at the
copy first, so a mutation the patterns below did not catch lands in a copy
nobody keeps. Any validator the dry script starts answers with the ok phrase
at once (a sitecustomize on PYTHONPATH, so the validator files themselves are
untouched and an anchor into one still means something). Afterwards it says
whether the REAL tree changed at all: `git status`, the content of every
modified file and every untracked file, before and after, must be identical.

The pattern neutering is kept because it is what makes a dry run take seconds;
the worktree is what makes a hole in it harmless.

    --in-place      the old behaviour: write <name>_dry.sh beside the script,
                    check it parses, and stop. Nothing protects the tree.
    --worktree-dir  where to put the copy (default: the system temp directory)

Exit codes: 0 nothing stale; 1 a stale anchor was reported (or a traceback,
or a file that was not there); 2 the dry script does not parse; 3 THE REAL TREE
CHANGED; 4 the copy could not be built; 5 the copy could not be removed and is
still on disk; 6 the script's ROOT, or a tree path it names, is not the copy,
so it was not run at all.

Why this exists: the first hand-rolled version of this neutered edit(), the
python heredocs and restore(), and left a bare `rm` in the middle of one
script alone. It deleted a real generated file, which then went into the
snapshot the next real run took, and the tree came back from every restore
still broken -- reported as a failing test rather than as a file I had
deleted by hand. Anything that writes has to go, not only the things that
usually do. Eight holes later the lesson was that the approach was the bug:
every one was a mutation that was not at the start of a line. Hence the
worktree.
"""
import argparse
import hashlib
import os
import pathlib
import posixpath
import re
import shutil
import subprocess
import sys
import tempfile
import time

#: What every script in tools/negtest hard-codes as the tree's location.
ASSUMED_ROOT = "/tmp/Elysium-mono"

EDIT = '''edit() { python3 -c "
import os, sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
if not os.path.isfile(p):
    print('STALE: missing file ' + p)
    sys.exit(0)
s = open(p, encoding='utf-8').read()
if old not in s:
    print('STALE: ' + p.split('/')[-1] + ' :: ' + old.strip()[:80])
" "$@"; }
'''

#: A command can be split over several lines with a trailing backslash. To
#: bash that is one command; to a line-oriented regex it is several, so
#: neutering the first line leaves the rest dangling and the script stops
#: parsing -- and a dry run that will not parse prints nothing, which reads
#: exactly like a clean one. Two stale anchors went through that way. This
#: says "to the end of the logical line", and it is used by every pattern
#: below that replaces a whole command.
LOGICAL = r"(?:[^\n]*\\\n)*[^\n]*$"


def unbrace(text, name):
    """Replace a shell function with a no-op, however many lines it spans.

    Matched by counting braces rather than by a lazy `.*?}`, because three
    scripts write restore() over several lines with a brace group inside it --

        restore() {
          ...
          [ "$missed" -eq 0 ] || { echo "  RESTORE ERROR"; fail=$((fail+1)); }
          rm -rf $BAK
        }

    and a lazy match stops at that inner close and leaves the real one
    dangling. negtest_gates.sh, negtest_icons.sh and negtest_uncap.sh were all
    in that shape, and all three had a dry run that died on a syntax error and
    printed nothing for as long as they have existed.
    """
    head = re.search(r"^" + name + r"\(\)\s*\{", text, flags=re.M)
    if not head:
        return text
    depth, at = 0, head.end() - 1
    while at < len(text):
        here = text[at]
        if here == "$" and text[at:at + 2] == "${":
            at = text.find("}", at)
            if at < 0:
                return text
        elif here == "{":
            depth += 1
        elif here == "}":
            depth -= 1
            if depth == 0:
                return text[:head.start()] + f"{name}() {{ :; }}" + text[at + 1:]
        at += 1
    return text


def neutered(hit):
    """Replace a mutating line, but leave whatever closed a block on it.

    negtest_icons.sh ends its test function on the line that does the
    cleanup --

        rm -rf $D; cp -r /tmp/_ic $D; }

    -- so replacing the whole line takes the closing brace with it and the
    script stops parsing three lines later. Anything that closes a block at
    the end of the line is kept.
    """
    line = hit.group(0)
    lead = re.match(r"\s*", line).group(0)
    tail = re.search(r"(?:;\s*)?(\}|fi|done|esac|;;)\s*$", line)
    note = "  # mutation neutered for the dry run"
    if not tail:
        return lead + "true" + note
    # The comment has to come last or it eats the thing being kept, and a
    # closing brace needs a separator in front of it that `;;` must not have.
    closer = tail.group(1)
    joint = " " if closer == ";;" else "; "
    return lead + "true" + joint + closer + note


# Any *other* helper whose body is an inline `python3 -c` that writes.
#
# The fifth hole of this kind, and the first to put a write through during a
# dry run of a script written after dryrun.py existed. negtest_names.sh
# defines two helpers beside edit() --
#
#     name() { python3 -c "... json.dump(d, open(f,'w')) ..." "$@"; }
#     drop_word() { python3 -c "... open(p,'w').writelines(keep) ..." "$@"; }
#
# -- which are not heredocs, not bare shell, and not called edit, so every
# pattern here walked past them. Its dry run wrote nine creature names into a
# language file and deleted a word from a data file, and because restore() is
# neutered for a dry run, nothing put them back. The tree stayed broken until
# the next real run refused to start.
#
# Matched by what the body does rather than by what the function is called,
# because the name is the part that keeps changing.
WRITES = re.compile(r"open\([^\n]*['\"][wa]\b|\.write\(|\.writelines\(|"
                    r"os\.remove\(|os\.unlink\(|shutil\.|json\.dump\(")


#: How a writing edit helper decides an anchor is stale, read off its own
#: body: Quill's edit() refuses unless the anchor matches once, edit_every()
#: unless it matches at least twice, and the older helpers refuse only a
#: missing one. The stand-in asks the same question, so a dry run reports
#: what the real run would stop on (17 October 2026: an anchor that matched
#: twice dry-ran clean and stopped negtest_fangs_effects.sh after 29 cases).
_COUNT_TEST = re.compile(r"if s\.count\(old\)\s*(!=|<=|<|==|>=|>)\s*(\d+)\s*:")
_PRESENCE_TEST = re.compile(r"\bif old not in s\b|\bassert old in s\b")
_EDIT_ARGS = re.compile(r"p, old, new = sys\.argv\[1\], sys\.argv\[2\], sys\.argv\[3\]"
                        r"|p, old, new = sys\.argv\[1:4\]")


def _anchor_stub(name, body):
    """A writing edit helper's dry stand-in, or None when its test cannot be read."""
    if not _EDIT_ARGS.search(body):
        return None
    counted = _COUNT_TEST.search(body)
    if counted:
        test = f"s.count(old) {counted.group(1)} {counted.group(2)}"
    elif _PRESENCE_TEST.search(body):
        test = "old not in s"
    else:
        return None
    return (f'{name}() {{ python3 -c "\n'
            "import os, sys\n"
            "p, old = sys.argv[1], sys.argv[2]\n"
            "if not os.path.isfile(p):\n"
            "    print('STALE: missing file ' + p)\n"
            "    sys.exit(0)\n"
            "s = open(p, encoding='utf-8').read()\n"
            f"if {test}:\n"
            "    print('STALE: ' + p.split('/')[-1] + ' (' + str(s.count(old)) + ' match(es)) :: '"
            " + old.strip()[:80])\n"
            '" "$@"; }  # dry: refuses as the real helper does, writes nothing\n')


def _inline_python(hit):
    name, body = hit.group(1), hit.group(0)
    if not WRITES.search(body):
        return body
    stub = _anchor_stub(name, body)
    if stub is not None:
        return stub
    if name == "edit":
        return body
    return f"{name}() {{ :; }}  # inline python that writes, neutered\n"


def neuter(s, phrases):
    """The pattern pass: make the script's runners instant and its writers silent.

    This is what makes a dry run fast. It is not what makes it safe -- see
    the worktree below for that -- so a shape it does not know about costs
    time in the copy, not files in the tree.
    """
    # The things that run the real checks: make them answer "fine" instantly.
    #
    # By name first, because out() and worldout() are the two this project uses
    # most. Then by what they do, because a script that named its runners lib(),
    # best() and arc() went through untouched and its "dry" run spent twenty
    # minutes running three real validators — which is not a write, so nothing was
    # harmed, but a dry run nobody has the patience for is a dry run nobody does,
    # and the stale anchors it would have found stay found by a real run instead.
    s = re.sub(r"^out\(\) \{[^\n]*\}$", 'out() { echo "%s"; }' % phrases[0], s, flags=re.M)
    if len(phrases) > 1:
        s = re.sub(r"^worldout\(\) \{[^\n]*\}$", 'worldout() { echo "%s"; }' % phrases[1],
                   s, flags=re.M)

    def _answered(match):
        """Any one-line function that runs a validator, answering instantly."""
        name = match.group(1)
        said = phrases[0] if len(phrases) == 1 else " ".join(phrases)
        return '%s() { echo "%s"; }' % (name, said)

    s = re.sub(r"^(\w+)\(\)\s*\{[^\n]*(?:validate\.py|check_\w+\.py)[^\n]*\}$",
               _answered, s, flags=re.M)

    # Any one-line function that deletes or moves a file. The bare-mutation rule
    # below only catches a line that *starts* with rm or mv, so a helper written as
    # `remove() { rm -f "$1"; }` went straight through: the definition line does not
    # start with rm, and the call site is just a word. A script that used one
    # deleted a generated texture during what was supposed to be a dry run.
    s = re.sub(r"^(\w+)\(\)\s*\{[^\n]*\b(?:rm|mv|unlink|truncate)\b[^\n]*\}$",
               r"\1() { :; }", s, flags=re.M)

    # The refusal at the top -- "do not start if the tree is already broken" --
    # is three more real validator runs and answers a question a dry run is not
    # asking.
    s = re.sub(r"^if ! \(cd .*?\n^fi$", "true  # pre-check neutered for the dry run",
               s, flags=re.M | re.S)

    # Anything that restores or regenerates: there is nothing to restore.
    for _name in ("restore", "regen"):
        while re.search(r"^" + _name + r"\(\)\s*\{", s, flags=re.M):
            _before = s
            s = unbrace(s, _name)
            if s == _before:
                break
    s = re.sub(r"\(cd \$M && python3 tools/gen_\w+\.py[^)]*\)", ":", s)

    # edit(): report a stale anchor the way the real one would, write nothing.
    def _edit(hit):
        return _anchor_stub("edit", hit.group(0)) or EDIT

    s = re.sub(r'edit\(\) \{ [^\n]*python3 -c "\n(?:.*?\n)*?" "\$@"[^\n]*\n', _edit, s, count=1)

    s = re.sub(r'^(\w+)\(\) \{ [^\n]*python3 -c "\n(?:.*?\n)*?" "\$@"[^\n]*\n',
               _inline_python, s, flags=re.M)

    # Inline python that edits files, and every bare shell mutation.
    # Any python heredoc, whatever it is tagged. This said PYX once, and a script
    # that tagged one PYZ went straight through and edited the tree during what was
    # supposed to be a dry run -- the same class of hole as the bare `rm`.
    # ...and whatever arguments sit between the dash and the heredoc: a case that
    # hands the file's path in as an argument (`python3 - "$M/x.java" <<'PYX'`)
    # is the same write with the path outside the body, and two such cases in
    # negtest_bestiary.sh ran for real on every dry run until 28 Sep.
    s = re.sub(r"python3 - [^\n<]*<<'(\w+)'.*?\n\1\n", "true\n", s, flags=re.S)
    # A bare multi-line `python3 -c "..."` at the top level, writing as it goes.
    # Not a heredoc, not inside a function, so nothing above sees it.
    # negtest_freight.sh opens a language file, pops a key and writes it back that
    # way, which its dry run did for real. Seventh hole.
    s = re.sub(r'^python3 -c "\n(?:.*?\n)*?[^\n]*"\s*$',
               "true  # inline python that writes, neutered", s, flags=re.M)

    # And a mutation that is not the first thing on its line. The patterns below
    # are anchored at the start of a line, so `keep $S/x; mv $S/x /tmp/bak` slips
    # past on the strength of the `keep`. Eighth hole, and the one that deleted
    # files during a dry run after the sixth was closed.
    s = re.sub(r";\s*(?:rm|mv)\s[^\n;]*", "; true", s)

    s = re.sub(r"^\s*(?:rm|mv|cp|truncate|install|tee|dd)\s" + LOGICAL,
               neutered, s, flags=re.M)
    s = re.sub(r"^\s*sed -i\s" + LOGICAL, neutered, s, flags=re.M)
    s = re.sub(r"^\s*\(?cd \$ROOT && tar " + LOGICAL,
               "true  # snapshot neutered for the dry run", s, flags=re.M)
    s = re.sub(r"^\(cd \$ROOT && tar " + LOGICAL, "true  # snapshot neutered", s, flags=re.M)
    # And any other tar at all. The two patterns above only knew the shape this
    # project usually writes, `(cd $ROOT && tar ...)`. negtest_affixes.sh writes
    # `tar czf $SNAP -C $ROOT ...` instead, which went through untouched -- so its
    # dry run gzipped two modules to disk every time it was asked to write
    # nothing, which is both the third hole of this kind and the reason that one
    # script took a minute while every other took under two seconds.
    s = re.sub(r"^\s*tar\s" + LOGICAL, neutered, s, flags=re.M)
    return s


# ---------------------------------------------------------------------------
# The net: a throwaway worktree, the script pointed at it, the real tree
# fingerprinted before and after.
# ---------------------------------------------------------------------------

class NetError(Exception):
    """The copy could not be built or the script could not be pointed at it."""


def _run(args, cwd=None, input=None, binary=False, env=None):
    kw = dict(cwd=cwd, input=input, capture_output=True, env=env)
    if not binary:
        kw.update(text=True, encoding="utf-8", errors="replace")
    return subprocess.run(args, **kw)


def _bash():
    """Git Bash, not the WSL launcher that Windows also calls bash.exe."""
    found = shutil.which("bash")
    if found and "system32" not in found.lower():
        return found
    git = shutil.which("git")
    if git:
        here = pathlib.Path(git).resolve().parent
        for top in (here.parent, here.parent.parent):
            for rel in ("usr/bin/bash.exe", "bin/bash.exe"):
                if (top / rel).is_file():
                    return str(top / rel)
    return found or "bash"


GIT = shutil.which("git") or "git"
BASH = _bash()


def _git(*args, cwd=None, binary=False, input=None):
    return _run([GIT, *args], cwd=cwd, binary=binary, input=input)


def _slash(path):
    return str(path).replace("\\", "/")


def _real_root(source):
    """The repository the script lives in, as git names it (forward slashes)."""
    found = _git("rev-parse", "--show-toplevel", cwd=str(source.parent))
    if found.returncode:
        raise NetError("not inside a git repository: " + found.stderr.strip())
    return _slash(found.stdout.strip())


def _fingerprint(root):
    """Everything git can see of the working tree, by content.

    `git status` alone would miss a dry script that changed a file that was
    already modified, so every modified and every untracked file is hashed too.
    Ignored files are outside this (and outside the copy): the proof covers what
    git covers.
    """
    status = _git("status", "--porcelain", "--untracked-files=all", cwd=root).stdout
    names = set(_git("diff", "--name-only", "-z", "HEAD", cwd=root).stdout.split("\0"))
    names |= set(_git("ls-files", "--others", "--exclude-standard", "-z", cwd=root).stdout.split("\0"))
    names.discard("")
    content = {}
    for rel in sorted(names):
        try:
            content[rel] = hashlib.sha256(pathlib.Path(root, rel).read_bytes()).hexdigest()
        except OSError:
            content[rel] = None  # deleted, or gone while we looked
    return status, content


def _say_what_moved(before, after, script=""):
    """Name what differs, and whether the script even mentions it.

    A tree other people are editing changes under a dry run for reasons that
    have nothing to do with it; a path whose module and file the script never
    names is almost certainly one of those.
    """
    def named(rel):
        parts = rel.split("/")
        # A module is named by its directory; anything else (docs/, tools/) by
        # its first two components, or `tools` alone would match every script.
        where = parts[0] if parts[0].startswith("elysium-") or len(parts) == 1 else "/".join(parts[:2])
        seen = [p for p in dict.fromkeys((where, parts[-1])) if p in script]
        return ("(the script names %s)" % " and ".join(seen) if seen
                else "(the script never names %s or %s -- another editor?)" % (where, parts[-1]))

    b_status, b_files = before
    a_status, a_files = after
    for line in sorted(set(a_status.splitlines()) - set(b_status.splitlines())):
        print("    status now :", line, named(line[3:]))
    for line in sorted(set(b_status.splitlines()) - set(a_status.splitlines())):
        print("    status was :", line, named(line[3:]))
    for rel in sorted(set(b_files) | set(a_files)):
        if b_files.get(rel) != a_files.get(rel):
            print("    content moved:", rel, named(rel))


def _written_in(wt):
    """What the dry script changed in the copy: whatever differs from the staged baseline."""
    lines = [l.replace("\t", " ", 1) for l in _git("-C", wt, "diff", "--name-status").stdout.splitlines() if l]
    lines += ["?? " + l for l in _git("-C", wt, "ls-files", "--others", "--exclude-standard").stdout.splitlines() if l]
    return lines


def _remove_copy(real_root, run_dir, wt):
    """Unregister and delete the copy, and say so if Windows would not let go of it.

    A file Defender is still reading is deleted only when its handle closes, so
    a first rmtree can leave a directory that empties itself a moment later --
    which is how one copy stayed registered after its run had printed "removed".
    """
    t = time.perf_counter()
    trouble = []
    if os.path.isdir(wt):
        gone = _git("worktree", "remove", "--force", wt, cwd=real_root)
        if gone.returncode and gone.stderr.strip():
            trouble.append("git worktree remove: " + gone.stderr.strip().splitlines()[-1])
    for attempt in range(6):
        if not os.path.exists(run_dir):
            break
        if attempt:
            time.sleep(1.0)
            for dirpath, _dirs, files in os.walk(run_dir):
                for name in files:
                    try:
                        os.chmod(os.path.join(dirpath, name), 0o666)
                    except OSError:
                        pass
        shutil.rmtree(run_dir, ignore_errors=True)
    _git("worktree", "prune", cwd=real_root)
    return time.perf_counter() - t, trouble, os.path.exists(run_dir)


def _copy_path(run_dir):
    """Where the copy goes: a basename unique to this run.

    git names a worktree's admin directory (.git/worktrees/<name>, which holds
    its index and HEAD) after the path's basename. Two dry runs side by side
    that both called their copy `tree` could end up resolving to each other's
    admin directory once one of them had been pruned, and one did: its status
    listed the other run's staged files.
    """
    return run_dir + "/copy-" + os.path.basename(run_dir).rsplit("-", 1)[-1]


def _build_copy(real_root, run_dir):
    """A detached worktree of HEAD with the working tree's state carried over."""
    wt = _copy_path(run_dir)
    t = time.perf_counter()
    made = _git("-c", "checkout.workers=0", "-c", "checkout.thresholdForParallelism=100",
                "worktree", "add", "--detach", "--quiet", wt, "HEAD", cwd=real_root)
    if made.returncode:
        raise NetError("git worktree add failed:\n" + made.stderr.strip())
    checkout = time.perf_counter() - t

    t = time.perf_counter()
    diff = _git("diff", "--binary", "HEAD", cwd=real_root, binary=True).stdout
    changed = 0
    if diff.strip():
        changed = len(_git("diff", "--name-only", "HEAD", cwd=real_root).stdout.split())
        applied = _git("-C", wt, "apply", "--whitespace=nowarn", binary=True, input=diff)
        if applied.returncode:
            raise NetError("the working tree's uncommitted changes did not apply to the copy, "
                           "so a dry run there would answer about the wrong tree:\n"
                           + applied.stderr.decode("utf-8", "replace").strip())
    copied = 0
    for rel in _git("ls-files", "--others", "--exclude-standard", "-z", cwd=real_root).stdout.split("\0"):
        if not rel:
            continue
        target = pathlib.Path(wt, rel)
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(pathlib.Path(real_root, rel), target)
            copied += 1
        except OSError as oops:
            print(f"  (untracked {rel} not carried over: {oops})")
    carry = time.perf_counter() - t
    return wt, checkout, carry, changed, copied


def _answering_validators(run_dir, phrases):
    """A sitecustomize that makes every validator this run starts answer at once.

    Loaded by every python3 the dry script launches, whatever the command line
    looks like; the validator files themselves are not touched, so a script that
    anchors into validate.py (negtest_atelier.sh does) is still telling the truth.
    Matched on the same two names the one-line rule in neuter() uses.
    """
    site = run_dir + "/site"
    os.makedirs(site, exist_ok=True)
    said = "\n".join("    print(%r)" % p for p in phrases)
    # os._exit, not sys.exit: a SystemExit raised inside sitecustomize comes
    # out as "Fatal Python error: Failed to import the site module" with status
    # 1, and a script's `if ! python3 validate.py` pre-check reads status 1 as
    # a dirty tree and stops -- which is how negtest_runes.sh's first dry run
    # through the net ended after one line.
    pathlib.Path(site, "sitecustomize.py").write_text(
        "# Written by tools/dryrun.py for one dry run and thrown away with it.\n"
        "import os, re, sys\n"
        "_me = os.path.basename(sys.argv[0]) if sys.argv and sys.argv[0] else ''\n"
        "if re.fullmatch(r'validate\\.py|check_\\w+\\.py', _me):\n"
        f"{said}\n"
        "    sys.stdout.flush()\n"
        "    os._exit(0)\n", encoding="utf-8", newline="\n")
    return site


def _point_at_copy(text, real_root, run_dir, wt):
    """Rewrite every mention of the tree so the script works on the copy.

    Both spellings a script on this machine might use: the Git Bash one the
    scripts hard-code and the Windows one git reports. Then tar's two-token
    form, `-C /tmp Elysium-mono/elysium-npcs`, and the `tar xzf $SNAP -C /tmp`
    that puts such a snapshot back -- the copy's parent is one directory below
    the temp directory precisely so that a stale snapshot extracted there could
    never land in the real tree.
    """
    forms = {ASSUMED_ROOT, real_root, real_root.replace("/", "\\")}
    cygpath = shutil.which("cygpath")
    if cygpath:
        posix = _run([cygpath, "-u", real_root])
        if posix.returncode == 0 and posix.stdout.strip():
            forms.add(posix.stdout.strip())
    for form in sorted(forms, key=len, reverse=True):
        text = text.replace(form, wt)

    parent, name = posixpath.split(ASSUMED_ROOT)
    at_parent = re.compile(r"(-C\s+)" + re.escape(parent) + r"(?![\w/.-])")
    bare_name = re.compile(r"(?<=[\s'\"=])" + re.escape(name) + r"/")

    def two_token(hit):
        line = at_parent.sub(lambda k: k.group(1) + run_dir, hit.group(0))
        return bare_name.sub(posixpath.basename(wt) + "/", line)

    text = re.sub(r"^.*-C\s+" + re.escape(parent) + r"(?![\w/.-]).*$", two_token, text, flags=re.M)

    left = [(n + 1, line.strip()) for n, line in enumerate(text.splitlines()) if name in line]
    return text, left


def in_place(source, phrases):
    """The old behaviour: write the dry script beside the source and stop."""
    out = source.with_name(source.stem + "_dry.sh")
    # newline="\n": on Windows write_text would otherwise hand bash a CRLF script.
    out.write_text(neuter(source.read_text(encoding="utf-8"), phrases), encoding="utf-8", newline="\n")
    out.chmod(0o755)
    # The whole value of a dry run is that silence means "every anchor matches".
    # A script that will not parse is also silent, so the two have to be told
    # apart here rather than by whoever reads the empty output.
    parsed = _run([BASH, "-n", str(out)])
    if parsed.returncode != 0:
        print(f"the dry run written to {out} is not a valid script, so running it "
              f"would report nothing and that would look like a pass:\n"
              + parsed.stderr.strip()[:600])
        return 2
    print("wrote", out)
    return 0


#: What a dry run prints when an anchor, or the file it is in, is not there.
#:
#: Tracebacks and missing files count too. A script whose every edit target
#: was missing used to print one FileNotFoundError traceback per edit from the
#: stubbed edit() above, none of which said STALE, and the run ended "no stale
#: anchors reported" with status 0 -- negtest_fangs_effects.sh printed 48 of
#: them that way and was reported clean.
STALE = re.compile(r"STALE|anchor not found|^anchor: |SETUP FAILED"
                   r"|Traceback \(most recent call last\)|FileNotFoundError|No such file")

#: The line expect() prints for a case, so a stale line can be put to the case
#: it belongs to: the edits come first, then the expect that names them.
CASE = re.compile(r"^\s*(?:caught|MISSED|allowed)\s*:\s*(.*\S)")

#: The first line that sets ROOT, and the assignments a ROOT can be built from.
ROOT_LINE = re.compile(r"^\s*(?:export\s+)?ROOT=", re.M)
ASSIGNMENT = re.compile(r"^\s*(?:export\s+)?[A-Za-z_]\w*=")

#: An absolute path, and a component that names a tree (Elysium-mono,
#: Elysium-int, ...). A path like that left after the rewrite points at a tree
#: that is not the copy.
ABSOLUTE = re.compile(r"""(?:(?<=[\s'"=:(])|^)((?:[A-Za-z]:)?[/\\][^\s'"`;|&<>(){}$]*)""", re.M)
TREE_NAME = re.compile(r"(?:^|[/\\])Elysium-[\w.-]+(?=$|[/\\])")


def _same_place(path):
    """A path in one spelling, so two names for one directory compare equal."""
    if path.startswith("/") and shutil.which("cygpath"):
        converted = _run([shutil.which("cygpath"), "-m", path])
        if converted.returncode == 0 and converted.stdout.strip():
            path = converted.stdout.strip()
    return os.path.normcase(os.path.realpath(path))


def _inside(path, wt):
    here, copy = _same_place(path), _same_place(wt)
    return here == copy or here.startswith(copy.rstrip("\\/") + os.sep)


def _not_the_copy(text, dry, wt, run_dir, env):
    """Why the rewritten script would not be working on the copy, if it would not.

    The rewrite only knows the spellings of this tree. A script that hard-codes
    another one -- negtest_fangs_*.sh said ROOT=/tmp/Elysium-int, a tree on
    another machine -- comes through it untouched, and its dry run edits
    nothing, checks nothing and, if that other path existed, would mutate a
    tree nobody is fingerprinting. So ROOT is worked out the way the script
    works it out, next to it, and every absolute path naming a tree is looked at.
    """
    reasons = []
    lines = text.splitlines()
    first = next((n for n, line in enumerate(lines) if ROOT_LINE.match(line)), None)
    if first is not None:
        prelude = [line for line in lines[:first] if ASSIGNMENT.match(line)] + [lines[first]]
        probe = dry.with_name(dry.stem + "_root.sh")
        probe.write_text("\n".join(prelude) + "\n"
                         'printf "%s\\n" "${ROOT:-}"\n'
                         'cd "${ROOT:-/nonexistent}" 2>/dev/null && { pwd -W 2>/dev/null || pwd; }\n',
                         encoding="utf-8", newline="\n")
        try:
            got = _run([BASH, str(probe)], cwd=wt, env=env).stdout.splitlines()
        finally:
            probe.unlink()
        raw = got[0].strip() if got else ""
        where = got[1].strip() if len(got) > 1 else ""
        said = lines[first].strip()[:100]
        if not raw:
            reasons.append(f"line {first + 1}, {said}, comes to nothing")
        elif not where:
            reasons.append(f"line {first + 1}, {said}, comes to {raw}, which does not exist "
                           f"-- it is not the copy")
        elif not _inside(where, wt):
            reasons.append(f"line {first + 1}, {said}, comes to {where}, which is not the copy")
    for number, line in enumerate(lines, start=1):
        if line.lstrip().startswith("#"):
            continue
        for path in ABSOLUTE.findall(line):
            if TREE_NAME.search(path) and not (_inside(path, wt) or _inside(path, run_dir)):
                reasons.append(f"line {number} names {path}, a tree that is not the copy")
    return reasons


def netted(source, phrases, worktree_dir):
    """Neuter, point at a throwaway copy, run there, prove the real tree is as it was."""
    real_root = _real_root(source)
    try:
        rel = source.resolve().relative_to(pathlib.Path(real_root).resolve())
    except ValueError:
        rel = pathlib.Path(source.name)

    # Two windows. The narrow one, around the dry script itself, is the proof
    # that the net held. The wide one, around the whole operation, catches a
    # tree that somebody else is editing meanwhile -- which happens, and which
    # must not be read as the net failing.
    wide_before = _fingerprint(real_root)
    before = after = None
    os.makedirs(worktree_dir, exist_ok=True)
    run_dir = _slash(tempfile.mkdtemp(prefix="elysium-dryrun-", dir=worktree_dir))
    wt = _copy_path(run_dir)
    text = ""
    code, stale, wrote, ran = 4, 0, [], 0.0
    staled = []   # (case, the stale lines printed before it)
    checkout = carry = 0.0
    try:
        wt, checkout, carry, changed, copied = _build_copy(real_root, run_dir)
        site = _answering_validators(run_dir, phrases)

        text, left = _point_at_copy(neuter(source.read_text(encoding="utf-8"), phrases),
                                    real_root, run_dir, wt)
        dry = pathlib.Path(wt, rel.parent, source.stem + "_dry.sh")
        dry.write_text(text, encoding="utf-8", newline="\n")
        dry.chmod(0o755)
        parsed = _run([BASH, "-n", str(dry)])
        if parsed.returncode != 0:
            print(f"the dry run of {source.name} is not a valid script, so running it "
                  f"would report nothing and that would look like a pass:\n"
                  + parsed.stderr.strip()[:600])
            return 2
        # ${ELYSIUM_ROOT:-...} is how three scripts find the tree. Whatever the
        # caller's shell has it set to, in a dry run it is the copy.
        env = dict(os.environ)
        env["PYTHONPATH"] = site + (os.pathsep + env["PYTHONPATH"] if env.get("PYTHONPATH") else "")
        env["ELYSIUM_ROOT"] = wt
        elsewhere = _not_the_copy(text, dry, wt, run_dir, env)
        if elsewhere:
            print(f"the dry run of {source.name} would not be working on the copy, so it was "
                  f"not run -- a dry run of nothing prints nothing, which reads exactly like "
                  f"a clean one:")
            for reason in elsewhere:
                print("   ", reason)
            print("    point it at /tmp/Elysium-mono (or ${ELYSIUM_ROOT:-...}) and dry-run it again")
            return 6
        # Stage the copy's starting state in its own index, so that afterwards
        # `git status` there lists exactly what the dry script wrote.
        _git("-C", wt, "add", "-A")

        print(f"--- dry run of {source.name} in a throwaway worktree, {wt} ---")
        print(f"    HEAD plus {changed} changed and {copied} untracked file(s) carried over; "
              f"every validator it starts answers {phrases!r} at once")
        for n, line in left:
            print(f"    still mentions the tree by name on line {n} (fine if it is a comment): {line[:100]}")
        print("---")
        before = _fingerprint(real_root)
        t = time.perf_counter()
        proc = subprocess.Popen([BASH, str(dry)], cwd=wt, env=env, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                                errors="replace", bufsize=1)
        pending, last_case = [], None
        for line in proc.stdout:
            sys.stdout.write(line)
            sys.stdout.flush()
            if STALE.search(line):
                stale += 1
                pending.append(line.strip())
                continue
            case = CASE.match(line)
            if case:
                last_case = case.group(1)
                if pending:
                    staled.append((last_case, pending))
                    pending = []
        if pending:
            staled.append(("after the last case" + (f" ({last_case})" if last_case else ""),
                           pending))
        proc.wait()
        ran = time.perf_counter() - t
        after = _fingerprint(real_root)
        print("---")

        wrote = _written_in(wt)
        if wrote:
            print(f"the dry script wrote to {len(wrote)} path(s) inside the copy -- absorbed, the real "
                  f"tree never saw them, but the patterns missed these and --in-place would not have:")
            for line in wrote[:40]:
                print("   ", line)
            if len(wrote) > 40:
                print(f"    ... and {len(wrote) - 40} more")
        else:
            print("the copy is unchanged after the dry script: nothing the patterns missed stuck "
                  "(an edit the script itself put back, as negtest_armour.sh does, would not show here)")
        code = 1 if stale else 0
    except NetError as oops:
        print("could not build the throwaway copy:", oops)
        code = 4
    finally:
        removed, trouble, left = _remove_copy(real_root, run_dir, wt)
        for line in trouble:
            print("   ", line)
        if left:
            print(f"!!! the copy could not be removed: {run_dir} -- delete it by hand, then "
                  f"`git worktree prune`")
            if code in (0, 1):
                code = 5
        wide_after = _fingerprint(real_root)

        if before is not None and after is not None:
            if after == before:
                print("the real tree is unchanged across the dry run: status, every modified file and "
                      "every untracked file are byte-identical before and after it ran")
            else:
                print("!!! THE REAL TREE CHANGED while the dry script ran -- the net did not hold, or "
                      "something else was writing to the tree at that moment:")
                _say_what_moved(before, after, text)
                code = 3
        if wide_after != wide_before and (before is None or after == before):
            print("(the tree did change while the copy was being built or removed, when no dry script "
                  "was running -- something else is editing this tree:)")
            _say_what_moved(wide_before, wide_after, text)
        if stale:
            print(f"{stale} stale-anchor line(s) above, in {len(staled)} case(s):")
            for case, said in staled:
                more = f" (and {len(said) - 1} more line(s))" if len(said) > 1 else ""
                print(f"    {case}: {said[0][:110]}{more}")
        elif code in (0, 3, 5):
            print("no stale anchors reported")
        print(f"net: copy built in {checkout + carry:.1f}s (checkout {checkout:.1f}s, carrying the "
              f"working tree's state {carry:.1f}s), dry script ran {ran:.1f}s, copy removed in {removed:.1f}s")
    return code


def main(argv=None):
    if hasattr(sys.stdout, "reconfigure"):
        # The scripts print em dashes; Git Bash reads UTF-8, and redirected
        # output on Windows would otherwise come out in the console code page.
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("script")
    ap.add_argument("phrases", nargs="*", help='what the validator says when all is well (default "checks passed")')
    ap.add_argument("--in-place", action="store_true",
                    help="write <name>_dry.sh beside the script and stop; nothing protects the tree")
    ap.add_argument("--worktree-dir", default=tempfile.gettempdir(),
                    help="where to build the throwaway copy (default: the system temp directory)")
    args = ap.parse_args(argv)
    source = pathlib.Path(args.script)
    phrases = args.phrases or ["checks passed"]
    if args.in_place:
        return in_place(source, phrases)
    return netted(source, phrases, _slash(args.worktree_dir))


if __name__ == "__main__":
    sys.exit(main())
