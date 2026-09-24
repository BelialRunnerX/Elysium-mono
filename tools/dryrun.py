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

# The things that run the real checks: make them answer "fine" instantly.
s = re.sub(r"^out\(\) \{[^\n]*\}$", 'out() { echo "%s"; }' % phrases[0], s, flags=re.M)
if len(phrases) > 1:
    s = re.sub(r"^worldout\(\) \{[^\n]*\}$", 'worldout() { echo "%s"; }' % phrases[1],
               s, flags=re.M)

# Anything that restores or regenerates: there is nothing to restore.
s = re.sub(r"^(restore|regen)\(\) \{.*?\}$", r"\1() { :; }", s, flags=re.M | re.S)
s = re.sub(r"\(cd \$M && python3 tools/gen_\w+\.py[^)]*\)", ":", s)

# edit(): report a stale anchor, write nothing.
s = re.sub(r'edit\(\) \{ python3 -c "\n(?:.*?\n)*?" "\$@"[^\n]*\n', EDIT, s, count=1)

# Inline python that edits files, and every bare shell mutation.
# Any python heredoc, whatever it is tagged. This said PYX once, and a script
# that tagged one PYZ went straight through and edited the tree during what was
# supposed to be a dry run -- the same class of hole as the bare `rm`.
s = re.sub(r"python3 - <<'(\w+)'.*?\n\1\n", "true\n", s, flags=re.S)
s = re.sub(r"^\s*(rm|mv|cp|truncate|install|tee|dd)\s[^\n]*$",
           "true  # mutation neutered for the dry run", s, flags=re.M)
s = re.sub(r"^\s*sed -i\s[^\n]*$", "true  # mutation neutered for the dry run",
           s, flags=re.M)
s = re.sub(r"^\s*\(?cd \$ROOT && tar [^\n]*$",
           "true  # snapshot neutered for the dry run", s, flags=re.M)
s = re.sub(r"^\(cd \$ROOT && tar [^\n]*$", "true  # snapshot neutered", s, flags=re.M)

out = source.with_name(source.stem + "_dry.sh")
out.write_text(s, encoding="utf-8")
out.chmod(0o755)
print("wrote", out)
