"""
Do translation keys get the number of arguments their strings ask for?

<h2>The bug this is for</h2>

`Component.translatable(key, a, b, c)` substitutes its arguments into the `%s`
placeholders of whatever the language file says, positionally, and checks
nothing. Pass four arguments to a string with two placeholders and the last two
vanish. Pass two to a string with four and the player reads a raw `%s`. Reorder
the placeholders without reordering the call and every value lands in the wrong
sentence — a set of coordinates rendered as a wave count, with no error anywhere.

None of that is visible to a compiler, to a validator that only checks the key
exists, or to anything short of triggering the message in game and reading it.
That last is the problem: these are messages for sieges, commissions and
failures, so triggering one can take a long play session to arrange.

<h2>What it can and cannot see</h2>

It reads the argument count at the call site, which means it only handles calls
whose arguments are on balanced parentheses it can count — which is all of them
in this repository, and the scan says so when it skips one rather than passing
quietly.

It checks the <b>count</b>. It cannot check the order, because nothing in the
source says what a placeholder is for. The count is what catches most of it: an
argument list that was reordered is usually also one that grew or shrank.
"""
import json
import os
import re

CALL = re.compile(r"Component\.translatable\(\s*\"([a-zA-Z0-9_.]+)\"")
COMMENT = re.compile(r"//[^\n]*|/\*.*?\*/", re.S)


def split_arguments(text):
    """
    Top-level arguments of a call, given the text just inside its parenthesis.

    Splits on commas that are not inside nested parentheses, brackets, a string
    or a char literal. Returns None when the parentheses do not balance, which
    means the call ran past the end of what was handed in.
    """
    args = []
    depth = 0
    current = []
    index = 0
    while index < len(text):
        char = text[index]
        if char == '"':
            end = index + 1
            while end < len(text):
                if text[end] == "\\":
                    end += 2
                    continue
                if text[end] == '"':
                    break
                end += 1
            current.append(text[index:end + 1])
            index = end + 1
            continue
        if char == "'":
            end = index + 1
            while end < len(text) and text[end] != "'":
                end += 2 if text[end] == "\\" else 1
            current.append(text[index:end + 1])
            index = end + 1
            continue
        if char in "([{":
            depth += 1
        elif char in ")]}":
            if depth == 0:
                args.append("".join(current))
                return [a for a in (arg.strip() for arg in args) if a]
            depth -= 1
        elif char == "," and depth == 0:
            args.append("".join(current))
            current = []
            index += 1
            continue
        current.append(char)
        index += 1
    return None


def check(source_root, lang_path, fail):
    """
    Cross-check every Component.translatable call against the language file.

    @param fail  called with a message for each problem found
    @return (calls checked, calls skipped)
    """
    with open(lang_path, encoding="utf-8") as handle:
        lang = json.load(handle)

    checked = 0
    skipped = 0
    for dirpath, _, filenames in os.walk(source_root):
        for filename in sorted(filenames):
            if not filename.endswith(".java"):
                continue
            path = os.path.join(dirpath, filename)
            with open(path, encoding="utf-8") as handle:
                text = COMMENT.sub(" ", handle.read())

            for match in CALL.finditer(text):
                key = match.group(1)
                if key not in lang:
                    continue           # the missing-key check owns this one
                args = split_arguments(text[match.end():])
                if args is None:
                    skipped += 1
                    continue
                # Not len(args) - 1: the scan resumes *after* the key literal,
                # so the key is not among what split_arguments returns. The
                # first version subtracted for it anyway and reported every
                # single call in the repository as wrong, including
                # zero-argument ones at "-1 arguments" — a check that fires on
                # everything is a check nobody can read.
                supplied = len(args)
                wanted = lang[key].count("%s") + len(
                    re.findall(r"%\d+\$s", lang[key]))
                checked += 1
                if supplied != wanted:
                    fail(f"{os.path.relpath(path, source_root)}: "
                         f"{key} is given {supplied} argument(s) but its string "
                         f"has {wanted} placeholder(s) — "
                         f"\"{lang[key][:60]}\"")
    return checked, skipped
