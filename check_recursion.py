#!/usr/bin/env python3
"""
No method calls itself with nothing changed and no way out.

    python3 check_recursion.py

<h2>The bug this exists for</h2>

A shipped jar contained this:

    static ResourceLocation id(String name) {
        return id(name);
    }

Infinite recursion. It compiles without a warning, because a method calling
itself is ordinary and the compiler cannot know there is no base case. It
passed sixteen module validators and three cross-cutting checks, was merged,
built, delivered and installed, and then threw StackOverflowError inside
RegisterEvent and took the game down before the title screen.

Nothing we had could have caught it. The harnesses execute the rules package,
and this was not in the rules package; the validators read for shapes, and this
shape *is* the correct shape for a helper. The only thing that distinguishes it
from a correct method is that the call passes the same arguments back and there
is nothing else in the body.

<h2>What is flagged</h2>

Exactly that, and deliberately nothing more. A method whose entire body is one
statement, and that statement is a call to itself with argument expressions
identical to its own parameter names. Real recursion has a base case, or
changes an argument, or does something else first — every one of which puts a
second statement in the body or a different expression in the call.

So this has no judgement in it and cannot be argued with, which is the point:
a check that flags plausible recursion would flag the whole tree and be
switched off within a week.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

problems = []


def fail(msg):
    problems.append(msg)


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


#: A method declaration: modifiers, a return type, a name, a parameter list.
METHOD = re.compile(
    r"(?:public|protected|private|static|final|abstract|synchronized|default|\s)*"
    r"(?:<[^>]{0,200}>\s*)?"
    r"(?P<type>[\w.$<>\[\],?\s]{1,200}?)\s+"
    r"(?P<name>\w+)\s*\((?P<params>[^)]{0,800})\)\s*"
    r"(?:throws\s[\w.,\s]{1,200})?\{")


def parameter_names(params):
    names = []
    depth = 0
    piece = ""
    for char in params:
        if char in "<([":
            depth += 1
        elif char in ">)]":
            depth -= 1
        if char == "," and depth == 0:
            names.append(piece)
            piece = ""
        else:
            piece += char
    names.append(piece)
    out = []
    for one in names:
        one = one.strip()
        if not one:
            continue
        out.append(one.split()[-1].strip())
    return out


def body_after(text, brace_at):
    depth = 0
    for index in range(brace_at, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[brace_at + 1:index]
    return ""


def line_of(text, index):
    return text.count("\n", 0, index) + 1


def check(path, text):
    for match in METHOD.finditer(text):
        name = match.group("name")
        if name in ("if", "for", "while", "switch", "catch", "synchronized",
                    "return", "new", "record", "else", "do", "try"):
            continue
        body = body_after(text, match.end() - 1).strip()
        if not body:
            continue
        # One statement only. Anything with a second statement has somewhere
        # else to go and is not this bug.
        statements = [piece.strip() for piece in body.split(";") if piece.strip()]
        if len(statements) != 1:
            continue
        only = statements[0]
        call = re.fullmatch(r"return\s+" + re.escape(name) + r"\s*\((?P<args>[^()]*)\)", only)
        if not call:
            call = re.fullmatch(r"(?:this\.)?" + re.escape(name)
                                + r"\s*\((?P<args>[^()]*)\)", only)
        if not call:
            continue
        given = [piece.strip() for piece in call.group("args").split(",") if piece.strip()]
        wanted = parameter_names(match.group("params"))
        if given == wanted:
            fail(f"{os.path.relpath(path, HERE)}:{line_of(text, match.start('name'))}: "
                 f"{name}({', '.join(wanted)}) is its whole body calling itself with the "
                 f"same arguments — it cannot return, and the first thing that reaches it "
                 f"takes the game down with a StackOverflowError")


scanned = 0
for module in sorted(os.listdir(HERE)):
    java = os.path.join(HERE, module, "src", "main", "java")
    if not (module.startswith("elysium-") and os.path.isdir(java)):
        continue
    for dirpath, _, filenames in os.walk(java):
        for name in filenames:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            scanned += 1
            check(path, strip_comments(open(path, encoding="utf-8").read()))

# The harnesses too: they are Java that runs, and a harness that hangs is a
# validator that never reports.
for module in sorted(os.listdir(HERE)):
    tools = os.path.join(HERE, module, "tools")
    if not os.path.isdir(tools):
        continue
    for name in sorted(os.listdir(tools)):
        if name.endswith(".java"):
            path = os.path.join(tools, name)
            scanned += 1
            check(path, strip_comments(open(path, encoding="utf-8").read()))

if problems:
    print("recursion          : FAILED")
    for problem in problems:
        print("  - " + problem)
    sys.exit(1)

print(f"recursion          : {scanned} source file(s), no method whose whole body is a "
      f"call to itself with nothing changed")
