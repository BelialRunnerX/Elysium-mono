#!/usr/bin/env python3
"""
Turn a negative-test script into a dry run that only checks its anchors.

    python3 /tmp/dryrun.py /tmp/negtest_<name>.sh "<ok phrase>" ["<second>"]

Why this exists: the first hand-rolled version of this neutered edit(), the
python heredocs and restore(), and left a bare `rm` in the middle of one
script alone. It deleted a real generated file, which then went into the
snapshot the next real run took, and the tree came back from every restore
still broken -- reported as a failing test rather than as a file I had
deleted by hand. Anything that writes has to go, not only the things that
usually do.
"""
import pathlib
import re
import subprocess
import sys

EDIT = '''edit() { python3 -c "
import sys
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
if old not in s:
    print('STALE: ' + p.split('/')[-1] + ' :: ' + old.strip()[:80])
" "$@"; }
'''

source = pathlib.Path(sys.argv[1])
phrases = sys.argv[2:] or ["checks passed"]
s = source.read_text(encoding="utf-8")

#: A command can be split over several lines with a trailing backslash. To
#: bash that is one command; to a line-oriented regex it is several, so
#: neutering the first line leaves the rest dangling and the script stops
#: parsing -- and a dry run that will not parse prints nothing, which reads
#: exactly like a clean one. Two stale anchors went through that way. This
#: says "to the end of the logical line", and it is used by every pattern
#: below that replaces a whole command.
LOGICAL = r"(?:[^\n]*\\\n)*[^\n]*$"

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
#
# Matched by counting braces rather than by a lazy `.*?}`, because three
# scripts write restore() over several lines with a brace group inside it --
#
#     restore() {
#       ...
#       [ "$missed" -eq 0 ] || { echo "  RESTORE ERROR"; fail=$((fail+1)); }
#       rm -rf $BAK
#     }
#
# and a lazy match stops at that inner close and leaves the real one dangling.
# negtest_gates.sh, negtest_icons.sh and negtest_uncap.sh were all in that
# shape, and all three had a dry run that died on a syntax error and printed
# nothing for as long as they have existed.
def unbrace(text, name):
    """Replace a shell function with a no-op, however many lines it spans."""
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


for _name in ("restore", "regen"):
    while re.search(r"^" + _name + r"\(\)\s*\{", s, flags=re.M):
        _before = s
        s = unbrace(s, _name)
        if s == _before:
            break
s = re.sub(r"\(cd \$M && python3 tools/gen_\w+\.py[^)]*\)", ":", s)

# edit(): report a stale anchor, write nothing.
s = re.sub(r'edit\(\) \{ [^\n]*python3 -c "\n(?:.*?\n)*?" "\$@"[^\n]*\n', EDIT, s, count=1)

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


def _inline_python(hit):
    name, body = hit.group(1), hit.group(0)
    if name == "edit" or not WRITES.search(body):
        return body
    return f"{name}() {{ :; }}  # inline python that writes, neutered\n"


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

out = source.with_name(source.stem + "_dry.sh")
out.write_text(s, encoding="utf-8")
out.chmod(0o755)

# The whole value of a dry run is that silence means "every anchor matches".
# A script that will not parse is also silent, so the two have to be told
# apart here rather than by whoever reads the empty output.
parsed = subprocess.run(["bash", "-n", str(out)], capture_output=True, text=True)
if parsed.returncode != 0:
    print(f"the dry run written to {out} is not a valid script, so running it "
          f"would report nothing and that would look like a pass:\n"
          + parsed.stderr.strip()[:600])
    sys.exit(2)
print("wrote", out)
