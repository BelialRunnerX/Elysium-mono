#!/usr/bin/env python3
"""
Validator rules that name a call where they should name a statement.

    python3 check_rules.py

<h2>The mistake this exists because of</h2>

A per-module validator proves things about the source by looking for text in
it. A rule that looks for a <em>call</em> — {@code "ComfortRules.seedFor("} —
goes on passing the day somebody writes:

    long seed = 0L;
    if (false) seed = ComfortRules.seedFor(...);

The call is still there. The rule still matches. The behaviour it was written
to guarantee is gone, and the rule has never once failed, which is the same as
not having written it.

This project has been bitten by that **fourteen times**, every single one found
by a negative test rather than by reading. The fix each time was the same:
name the *whole statement*, not the token inside it. So that fix is now a
check.

<h2>What counts as suspect</h2>

A string required of a **body** — one method's source, or some other cut of a
file, rather than the whole file — that ends in `(` or `,`. Those are truncated
statements: whatever follows them can be anything at all.

A rule that asks whether a *declaration* exists ("is there a method called
this?") is a different kind of rule and is compared against a whole file, so it
is left alone. So is a rule that *forbids* a call (`if "readout.get(" in body:
fail(...)`): there the truncated form is the strong one, since no call at all
may appear. That distinction is the whole reason this check is worth having
rather than being noise.

<h2>What counts as a body</h2>

The validators are read as Python (`ast`), not grepped, so a rule split over
three lines, a string with a quote inside it, or a rule in a comment are all
seen for what they are. A body is any of these, assigned to a name or used in
place:

  - a call to a helper that cuts one method out of a file: `body_of`,
    `method_body`, `_method_body`, `helper_body`, `_menu_body`,
    `_beacon_body`, `_declared_body`, `body_after`, `between` (BODY_HELPER);
  - a call to any other function the validator defines that hands back a cut
    (comforts' `constant()`, agronomy's `statement_of()`), worked out from its
    `return`s rather than its name; a function that hands back a list or dict
    of cuts (check_gear's `classes_with_bodies()`) gives a body to every name a
    loop over it binds; so does one entry of any collection of bodies (lib's
    `_mb_rank, _mb_req = _rooms_found["MED_BAY"]` out of a dict of regex
    captures), column by column where its rows are tuples, so a column that
    holds a whole file is not taken for one;
  - a regex capture, `m.group(n)` — lib's `_body = _appl.group(1)`;
  - a cut of a string: `text[text.find(...):]`, `text[a:b]` with a bound that
    is not a constant, `text.split(...)[i]`, `head, body = text.split(...)`,
    and each piece a loop takes from a split (`for method in re.split(...)`);
  - anything made from a body by reshaping it: `squashed(body)`,
    `strip_comments(body)`, `re.sub(..., body)`, `body.replace(...)`,
    `" ".join(body.split())`, `prefix + body`, `body if x else ""`, a function
    the validator defines that hands back what it was given reshaped (armory's
    `_squashed`), or just another name for one (`_run = _run_body`);
  - a loop or comprehension variable whose rows hold bodies:
    `for _needed, _where, _scope, _why in (("PortRules.outbound(", ...,
    _run_body, ...), ...): if _needed not in _scope:` — read row by row, so a
    row whose third column is a whole file is not taken for a body;
  - a parameter of a helper, read at each place the helper is called with what
    that call hands it: logistics' `need(text, statement, complaint)` and
    solarpunk's `calls(source, signature, callee)`. When the helper hands the
    test back (`return callee in body`), the rule is read where the call's
    answer is tested.

Until 15 October 2026 only `x = body_of(` and `x = squashed(body_of(` were
recognised, and only double-quoted strings, so every rule that took its body
another way — or was written in single quotes — never reached the ratchet, and
half-statement rules read as zero. Helpers judged by what they return, the
pieces of a split, functions that reshape, loop rows and helper parameters were
added on 16 October 2026, after a review and then a sweep of every validator
and root check found rules in this tree written each of those ways.

A literal is a single- or double-quoted string, adjacent strings joined, or an
f-string or `+` whose pieces are literals or names bound to literals (expanded,
at most 64 ways); one with a piece that cannot be resolved is named as its
template, `KindredRules.{shorthand}`, and is suspect only if its own last
piece is. A rule is a test of an `if`, `elif`, `while`, `assert`, conditional
expression or comprehension filter; a membership test stored into a tuple or
list is a reading of the source, not a rule about it.

<h2>What counts as requiring text</h2>

`"x(" in body` is one way to say it. These say exactly the same, and until 15
October 2026 (later the same day) were not read at all, so a half-statement
rule written any of these ways never reached the ratchet:

  - a position: `body.find("x(")`, `.rfind`, `.index`, `.rindex`, compared
    with a number (`< 0`, `== -1`, `>= 0`, `!= -1`, ...), with another
    position (`body.find("a(") > body.find("b(")`, chained, through `min`,
    `max`, `+` and `-`); stored first (`_m = body.find("x(")` ... `if _m < 0:`)
    or not;
  - a count: `body.count("x(")` compared with a number (`< 2`, `!= 1`), and
    `len(re.findall(...))` likewise, behind the guard of a cut
    (`cut.count("at(") if found else 0`) or multiplied (`len(roster) * n`);
    a number this cannot work out (`len(tabs)`, `min(found)`) is taken to be
    any number at all;
  - `body.startswith("x(")` and `body.endswith(...)`, with one text or a tuple;
  - a regex search, `re.search(r"x\\(", body)`, whose pattern can match only one
    text (`\\b` and `\\s*` aside): the text is what the pattern spells, and the
    ratchet names the pattern as written; a compiled pattern searched the same
    way (lib's `GATE = re.compile(...)` ... `GATE.search(line)`) likewise;
  - a test stored first: `ok = "x(" in body` ... `if not ok: fail(...)`;
  - a string out of a list: `for needle in ("x(", "y("): if needle not in body`,
    `all(n in body for n in NEEDLES)`, `any(n not in body for n in ...)`,
    `[n for n in ... if n not in body]`, a dict's keys or items, `enumerate`,
    `zip`, or a name bound to any of those;
  - the branch that does not leave: `if "x(" in body: continue` (or `break`,
    `pass`, a bare `return`) puts the requirement on the way that goes on, so
    it reads as `if "x(" not in body: <fail>` — check_gear's
    `if "elysiumModifiers(" in body: continue`. Without this the same line
    read as a rule *forbidding* the call, and `if "x(" not in body: continue`,
    which only picks what to look at, read as one requiring it.

How a comparison reads is worked out rather than listed: a position is -1 and a
count is 0 when the text is not there, so `body.find(t) < 0` is a rule exactly
when, and in the direction that, `t not in body` would be. A comparison that
reads the same whether the text is there or not (`>= -1`) is no rule at all.
Between two positions, a text is required when there is some place for the
other text at which the comparison reads one way with it missing and the other
way with it present: `using.find("a();") > using.find("b(")` requires `b(`
(missing, `a();` after nothing fails) and does not require `a();`.

<h2>What this cannot see</h2>

This is a lint over validator source, not a proof about it. It reads the forms
listed above, which are every form a validator or root check in this tree used
on 16 October 2026: a sweep listed every `in`, find, count, startswith and
regex test whose haystack is derived from a body and looked at each one this
did not read. It cannot read every way Python can spell a test, and does not
try: an adversarial review the same day wrote thirty-four more spellings it
misses, none of them in the tree, and they were deliberately not chased. What
it does not see, among others:

  - a needle computed by a call (`"\\n".join(lines)`, `fragment.replace(...)`,
    `name.upper()`), an f-string conversion or format spec, or a placeholder
    whose values come from data rather than literals: read as a template, or
    not at all;
  - a regex that can match more than one text, compiled or not: a rule about a
    pattern is read, counted and never suspect, since it is not this check's
    business; and `re.match` / `re.fullmatch`, which are not read at all (every
    one in the tree is such a pattern);
  - a body held in an attribute, a class or a closure, or looked up in a dict
    by a computed key; a helper passed around as a value, called through
    another name, or followed more than two calls deep; a cutting helper named
    for it (BODY_HELPER), whose own searches are the cut, not a rule;
  - a test made inside `lambda`, `map`, `filter`, `operator.contains` or
    `str.__contains__`; a test whose answer is put in a tuple a helper hands
    back (agronomy's `built_on`) or in a list tested later in another shape
    (solarpunk had one — positions filtered with `>= 0`, then
    `if not _mutations:` — and it was rewritten on 16 October 2026 into plain
    forms rather than taught); `setattr`, `exec`, `eval`, a second module;
  - a failure branch it cannot tell from a passing one. A branch that leaves
    with a value (`return True`, `return []`), or an `else` that fails under an
    `if` that does not leave, reads in the default direction, as if the `if`
    branch were the failure, so `if "x(" in body: return True` reads as a rule
    forbidding `x(`, and can miss one; a `continue` that only skips what is not
    of interest (`if "/models/" in path: continue`) reads as requiring its
    text, which errs the other way.

The sweep's leftovers in this tree are the computed needles, the patterns and
the tests handed back in a tuple above, and membership tests of lists, sets and
dicts, which are not tests of text at all.

So the house rule for validator code is the other half of this check: write a
rule as `"<whole statement>" in <body>`, or one of the forms above, so this can
see it. A rule written some cleverer way may be correct and will still be
invisible to the ratchet, and an invisible rule is how half-statements came to
read as zero in the first place.
"""
import ast
import itertools
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

#: Helpers that hand back one method's source. Matched on the name, because
#: every validator spells its own: body_of, method_body, _method_body,
#: helper_body, _menu_body, _beacon_body, _declared_body, body_after, between.
#: Not `method_bodies`, which hands back a dictionary of them. Any other
#: function a validator defines is judged by what it returns (Cuts).
BODY_HELPER = re.compile(r"^_?(?:\w+_)?body(?:_of|_after)?$|^between$")

#: Calls that give back the text they were handed, reshaped: a body in, a body
#: out. As a function (`squashed(body)`) or as a method (`body.replace(...)`,
#: `" ".join(body.split())`).
RESHAPES = {"squashed", "strip_comments", "strip_java_comments", "dedent", "sub", "replace",
            "strip", "lstrip", "rstrip", "lower", "upper", "removeprefix", "removesuffix",
            "expandtabs", "str", "join", "split", "rsplit", "splitlines"}

#: Calls whose pieces are cuts of the text they split.
CUTS = {"split", "rsplit", "partition", "rpartition"}

#: Calls that hand a collection back as another collection of the same things.
CARRIES = {"sorted", "list", "tuple", "set", "frozenset", "reversed", "enumerate", "zip", "dict"}

#: Calls that put one thing into a collection: the thing is the last argument.
FILLS = {"append", "add", "insert", "setdefault"}

#: The rules that already name a call and have not been rewritten yet.
#:
#: A lint introduced into a tree this size either blocks everything on the day
#: it lands or is a ratchet. This is a ratchet: what is already here is
#: recorded, a new one fails the build, and the recorded ones come out as the
#: modules they are in are touched. Regenerate with --bless, which is a
#: deliberate act and shows up in a diff.
BASELINE = os.path.join(HERE, "docs", "RULES_BASELINE.txt")

#: How many ways one helper is followed into, and how many calls deep.
CALLS_FOLLOWED = 400
CALL_DEPTH = 2

problems = []
unread = []
stale = []


def suspect(literal):
    """Whether a search string is a truncated statement."""
    trimmed = literal.rstrip()
    return trimmed.endswith("(") or trimmed.endswith(",")


def _name(func):
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        return func.attr
    return None


def _constant(bound):
    return bound is None or isinstance(bound, ast.Constant) or (
        isinstance(bound, ast.UnaryOp) and isinstance(bound.operand, ast.Constant))


def is_body(node, bodies, more=None):
    """Whether an expression is a cut of a source file rather than all of it.

    `bodies` is every name ever given one; `more` answers for a name or a call
    that set cannot: a helper the validator defines, a parameter read at a call
    site, a loop variable drawn from rows.
    """
    if isinstance(node, ast.Name):
        return node.id in bodies or (more is not None and more(node))
    if isinstance(node, ast.Call):
        name = _name(node.func)
        if name is None:
            return False
        if isinstance(node.func, ast.Name) and BODY_HELPER.match(name):
            return True
        if isinstance(node.func, ast.Attribute) and name == "group":
            return True
        if name in RESHAPES:
            operands = list(node.args)
            if isinstance(node.func, ast.Attribute):
                operands.append(node.func.value)
            if any(is_body(operand, bodies, more) for operand in operands):
                return True
        return more is not None and isinstance(node.func, ast.Name) and more(node)
    if isinstance(node, ast.Subscript):
        value = node.value
        if isinstance(value, ast.Call) and isinstance(value.func, ast.Attribute) \
                and value.func.attr in CUTS:
            return True
        if isinstance(node.slice, ast.Slice) and not (
                _constant(node.slice.lower) and _constant(node.slice.upper)):
            return True
        return is_body(value, bodies, more)
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        return is_body(node.left, bodies, more) or is_body(node.right, bodies, more)
    if isinstance(node, ast.IfExp):
        return is_body(node.body, bodies, more) or is_body(node.orelse, bodies, more)
    if isinstance(node, ast.BoolOp):
        return any(is_body(value, bodies, more) for value in node.values)
    return False


def _targets(target):
    if isinstance(target, ast.Name):
        return [target.id]
    if isinstance(target, (ast.Tuple, ast.List)):
        return [name for element in target.elts for name in _targets(element)]
    if isinstance(target, ast.Starred):
        return _targets(target.value)
    return []


SCOPES = (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda)
COMPREHENSIONS = (ast.ListComp, ast.SetComp, ast.GeneratorExp, ast.DictComp)


def _own(scope):
    """A function's nodes, without those of the functions defined inside it."""
    stack = list(ast.iter_child_nodes(scope))
    while stack:
        node = stack.pop()
        yield node
        if not isinstance(node, SCOPES):
            stack.extend(ast.iter_child_nodes(node))


def _params(scope):
    arguments = scope.args
    return {arg.arg for arg in arguments.posonlyargs + arguments.args + arguments.kwonlyargs
            + [arguments.vararg, arguments.kwarg] if arg is not None}


def _positional(function):
    return [arg.arg for arg in function.args.posonlyargs + function.args.args]


def _pieces(node):
    """Whether a value is the pieces of a split: each one is a cut."""
    return isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) \
        and node.func.attr in CUTS


def _bindings(nodes):
    """(assigned, loops, fills) among some nodes: what names are given, drawn and filled with."""
    assigned = []   # (names, value, unpacked)
    loops = []      # (target, iterable)
    fills = []      # (name, value put into it)
    for node in nodes:
        if isinstance(node, ast.Assign):
            for target in node.targets:
                pairs = isinstance(target, (ast.Tuple, ast.List)) \
                    and isinstance(node.value, (ast.Tuple, ast.List)) \
                    and len(target.elts) == len(node.value.elts)
                if pairs:
                    for element, value in zip(target.elts, node.value.elts):
                        assigned.append((_targets(element), value, False))
                elif isinstance(target, ast.Subscript) and isinstance(target.value, ast.Name):
                    fills.append((target.value.id, node.value))
                else:
                    assigned.append((_targets(target), node.value,
                                     isinstance(target, (ast.Tuple, ast.List))))
        elif isinstance(node, (ast.AnnAssign, ast.AugAssign)) and node.value is not None:
            assigned.append((_targets(node.target), node.value, False))
        elif isinstance(node, ast.NamedExpr):
            assigned.append((_targets(node.target), node.value, False))
        elif isinstance(node, (ast.For, ast.AsyncFor, ast.comprehension)):
            loops.append((node.target, node.iter))
        elif isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) \
                and isinstance(node.func.value, ast.Name) and node.args:
            if node.func.attr in FILLS:
                fills.append((node.func.value.id, node.args[-1]))
            elif node.func.attr == "extend":
                fills.append((node.func.value.id, ast.List(elts=[ast.Starred(value=node.args[0])])))
    return assigned, loops, fills


def _rows(value):
    """The tuple rows of a collection written out here, or None when it holds anything else."""
    if isinstance(value, ast.Dict):
        elements = value.values
    elif isinstance(value, (ast.List, ast.Set)):
        elements = value.elts
    elif isinstance(value, (ast.ListComp, ast.SetComp, ast.GeneratorExp)):
        elements = [value.elt]
    elif isinstance(value, ast.DictComp):
        elements = [value.value]
    else:
        return None
    rows = [element for element in elements if isinstance(element, ast.Tuple) and element.elts]
    return rows if rows and len(rows) == len(elements) else None


def _row_source(iterable):
    """(collection name, keyed) for a loop over a named collection's rows, or None."""
    if isinstance(iterable, ast.Name):
        return iterable.id, False
    if isinstance(iterable, ast.Call) and isinstance(iterable.func, ast.Attribute) \
            and not iterable.args and isinstance(iterable.func.value, ast.Name) \
            and iterable.func.attr in ("items", "values"):
        return iterable.func.value.id, iterable.func.attr == "items"
    if isinstance(iterable, ast.Call) and isinstance(iterable.func, ast.Name) \
            and iterable.func.id in ("sorted", "list", "tuple", "reversed") and iterable.args:
        return _row_source(iterable.args[0])
    return None


class Cuts:
    """Which names a validator gives a body, and which of its own functions hand one back.

    `bodies` is flat — a name given a body anywhere is a body everywhere —
    which is how this check has always read names; it errs towards reading a
    rule, never towards missing one. The functions are judged on their own
    statements, so a parameter called `text` in one helper is not taken for the
    `text` some other part of the file cut out of a method.
    """

    def __init__(self, tree):
        self.defs = {}
        for node in sorted((node for node in ast.walk(tree)
                            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))),
                           key=lambda node: node.lineno):
            self.defs[node.name] = node
        self.cutters = set()    # functions that hand back a body
        self.gathers = set()    # functions that hand back a collection of bodies
        self.passes = {}        # function -> positions of the parameters it hands back reshaped
        self.bodies = set()
        self.holders = set()    # names given a collection with a body in it
        self.columns = {}       # name -> which columns of its tuple rows hold a body
        assigned, loops, fills = _bindings(ast.walk(tree))
        own = {name: _bindings(_own(function)) for name, function in self.defs.items()}
        while True:
            before = self._size()
            self._settle(assigned, loops, fills, self.bodies, self.holders)
            for name, function in self.defs.items():
                self._learn(name, function, own[name])
            if self._size() == before:
                return

    def _size(self):
        return (len(self.bodies), len(self.holders), len(self.cutters), len(self.gathers),
                sum(len(found) for found in self.passes.values()),
                sum(sum(flags) for flags in self.columns.values()))

    def more(self, node, bodies=None):
        """A call to a function of the validator's own that hands back a body."""
        if not (isinstance(node, ast.Call) and isinstance(node.func, ast.Name)):
            return False
        name = node.func.id
        if name in self.cutters:
            return True
        bodies = self.bodies if bodies is None else bodies
        return any(index < len(node.args)
                   and is_body(node.args[index], bodies, lambda inner: self.more(inner, bodies))
                   for index in self.passes.get(name, ()))

    def holds(self, value, bodies, holders):
        """Whether a value is a collection with a body in it."""
        more = lambda node: self.more(node, bodies)   # noqa: E731

        def has(element):
            if isinstance(element, ast.Starred):
                element = element.value
                return element is not None and self.holds(element, bodies, holders)
            return is_body(element, bodies, more) or self.holds(element, bodies, holders)

        if isinstance(value, (ast.List, ast.Tuple, ast.Set)):
            return any(has(element) for element in value.elts)
        if isinstance(value, ast.Dict):
            return any(has(element) for element in value.values)
        if isinstance(value, (ast.ListComp, ast.SetComp, ast.GeneratorExp)):
            return has(value.elt)
        if isinstance(value, ast.DictComp):
            return has(value.value)
        if isinstance(value, ast.Name):
            return value.id in holders
        if isinstance(value, ast.BinOp) and isinstance(value.op, ast.Add):
            return self.holds(value.left, bodies, holders) or self.holds(value.right, bodies, holders)
        if isinstance(value, ast.IfExp):
            return self.holds(value.body, bodies, holders) or self.holds(value.orelse, bodies, holders)
        if not isinstance(value, ast.Call):
            return False
        if _pieces(value):
            return True
        name = _name(value.func)
        if isinstance(value.func, ast.Name):
            if name in self.gathers:
                return True
            if name in CARRIES:
                return any(self.holds(arg, bodies, holders) for arg in value.args)
            return False
        return name in ("items", "values", "copy") and self.holds(value.func.value, bodies, holders)

    def _columns(self, name, rows, bodies, more):
        """Record which columns of a collection's tuple rows hold a body."""
        width = len(rows[0].elts)
        if any(len(row.elts) != width for row in rows):
            return
        flags = [any(is_body(row.elts[at], bodies, more) for row in rows) for at in range(width)]
        known = self.columns.get(name)
        if known is not None and len(known) == width:
            flags = [old or new for old, new in zip(known, flags)]
        self.columns[name] = flags

    def _entry(self, names, collection, unpacked, bodies, holders):
        """Names given one entry of a collection of bodies, column by column when it has rows."""
        columns = self.columns.get(collection)
        if columns is None:
            bodies.update(names)
        elif unpacked and len(columns) == len(names):
            bodies.update(name for name, body in zip(names, columns) if body)
        elif not unpacked:
            holders.update(names)

    def _settle(self, assigned, loops, fills, bodies, holders):
        more = lambda node: self.more(node, bodies)   # noqa: E731
        for names, value, unpacked in assigned:
            if (unpacked and _pieces(value)) or is_body(value, bodies, more):
                bodies.update(names)
                continue
            rows = _rows(value)
            if rows is not None and len(names) == 1:
                self._columns(names[0], rows, bodies, more)
            if isinstance(value, ast.Subscript) and not isinstance(value.slice, ast.Slice) \
                    and isinstance(value.value, ast.Name) \
                    and self.holds(value.value, bodies, holders):
                # One entry of a collection of bodies — `rank, needs =
                # rooms["MED_BAY"]` out of a dict of regex captures — is one,
                # in the columns of its rows that hold one.
                self._entry(names, value.value.id, unpacked, bodies, holders)
            elif self.holds(value, bodies, holders):
                holders.update(names)
        for target, iterable in loops:
            source = _row_source(iterable)
            if _pieces(iterable):
                bodies.update(_targets(target))
            elif source is not None and source[0] in self.columns \
                    and self.holds(ast.Name(id=source[0]), bodies, holders):
                # A loop over the rows of a collection with columns: `for key,
                # (where, made) in HOW_BUILT.items()` takes a body only where a
                # row holds one.
                row = target.elts[1] if source[1] and isinstance(target, (ast.Tuple, ast.List)) \
                    and len(target.elts) == 2 else (None if source[1] else target)
                if isinstance(row, (ast.Tuple, ast.List)):
                    self._entry([n for element in row.elts for n in _targets(element)],
                                source[0], True, bodies, holders)
                elif isinstance(row, ast.Name):
                    holders.add(row.id)
            elif isinstance(iterable, (ast.List, ast.Tuple, ast.Set)) \
                    and isinstance(target, (ast.Tuple, ast.List)):
                # Rows: a column is a body when a row holds one there.
                for row in iterable.elts:
                    if isinstance(row, (ast.Tuple, ast.List)) and len(row.elts) == len(target.elts):
                        for part, column in zip(row.elts, target.elts):
                            if is_body(part, bodies, more):
                                bodies.update(_targets(column))
            elif self.holds(iterable, bodies, holders):
                bodies.update(_targets(target))
        for name, value in fills:
            if isinstance(value, ast.Tuple) and value.elts:
                self._columns(name, [value], bodies, more)
            if is_body(value, bodies, more) or self.holds(value, bodies, holders):
                holders.add(name)

    def _learn(self, name, function, own):
        returns = [node.value for node in _own(function)
                   if isinstance(node, ast.Return) and node.value is not None]
        if not returns or name in self.cutters or name in self.gathers:
            return
        assigned, loops, fills = own
        local = set(_params(function))
        local.update(n for names, _, _ in assigned for n in names)
        local.update(n for target, _ in loops for n in _targets(target))

        def analysed(seed):
            bodies = (self.bodies - local) | set(seed)
            holders = self.holders - local
            while True:
                before = (len(bodies), len(holders))
                self._settle(assigned, loops, fills, bodies, holders)
                if (len(bodies), len(holders)) == before:
                    return bodies, holders

        bodies, holders = analysed(())
        if any(is_body(value, bodies, lambda node: self.more(node, bodies)) for value in returns):
            self.cutters.add(name)
            return
        if any(self.holds(value, bodies, holders) for value in returns):
            self.gathers.add(name)
            return
        for index, param in enumerate(_positional(function)):
            if index in self.passes.get(name, ()):
                continue
            bodies, _ = analysed((param,))
            if any(is_body(value, bodies, lambda node: self.more(node, bodies)) for value in returns):
                self.passes.setdefault(name, set()).add(index)

    def cutting(self, name):
        """Whether a function is the machinery that cuts bodies rather than a rule about one.

        Only the helpers named for it: one that cuts and also tests what it cut
        (lib's `_uses`) holds rules, and is read at each call.
        """
        return bool(BODY_HELPER.match(name))


def bodies_of(tree):
    """Every name a validator ever gives a body, followed to a fixed point."""
    return Cuts(tree).bodies


def literal(node):
    """The text of a string literal, or None for anything that is not one."""
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.JoinedStr) and all(isinstance(part, ast.Constant)
                                               for part in node.values):
        return "".join(part.value for part in node.values)
    return None


#: What a position or a count reads when the text is not there.
POSITIONS = {"find": -1, "rfind": -1, "index": -1, "rindex": -1, "count": 0}

#: The comparisons a position or a count can be put to.
ORDERS = {ast.Lt: lambda a, b: a < b, ast.LtE: lambda a, b: a <= b,
          ast.Gt: lambda a, b: a > b, ast.GtE: lambda a, b: a >= b,
          ast.Eq: lambda a, b: a == b, ast.NotEq: lambda a, b: a != b}

#: Calls a test passes through on its way to an `if`, meaning what it meant.
TRUTHS = {"bool", "any", "all"}

#: Regex calls on a body whose answer is "found" or "not found".
SEARCHES = {"search", "findall"}

#: A body's ends: true when the text is there, false when it is not.
ENDS = {"startswith", "endswith"}

#: The most texts one f-string or `+` is expanded into.
SPELLINGS = 64


def _integer(node):
    if isinstance(node, ast.Constant) and type(node.value) is int:
        return node.value
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.USub, ast.UAdd)) \
            and isinstance(node.operand, ast.Constant) and type(node.operand.value) is int:
        return -node.operand.value if isinstance(node.op, ast.USub) else node.operand.value
    return None


def _leaves_branch(statements):
    """Whether a branch only moves on: `continue`, `break`, `pass`, a bare `return`."""
    return bool(statements) and all(
        isinstance(statement, (ast.Continue, ast.Break, ast.Pass))
        or (isinstance(statement, ast.Return) and (
            statement.value is None or (isinstance(statement.value, ast.Constant)
                                        and statement.value.value is None)))
        for statement in statements)


def regex_text(pattern):
    """The one text a regex can match, or None when it can match more than one.

    `\\b` matches no text and `\\s*` none worth naming, so both are dropped, and
    `\\s+` is a space. Anything else that is not a plain character makes it a
    pattern, and a rule about a pattern is not this check's business.
    """
    out = []
    i = 0
    while i < len(pattern):
        char = pattern[i]
        if char == "\\":
            follows = pattern[i + 1:i + 2]
            if pattern.startswith("\\s*", i):
                i += 3
            elif pattern.startswith("\\s+", i):
                out.append(" ")
                i += 3
            elif follows == "b":
                i += 2
            elif follows == "" or follows.isalnum():
                return None
            else:
                out.append(follows)
                i += 2
            continue
        if char in ".^$*+?{}[]|()":
            return None
        out.append(char)
        i += 1
    return "".join(out)


class Reading:
    """One validator, read for every way it asks a body whether text is in it."""

    def __init__(self, tree, cuts=None):
        self.module = tree
        self.cuts = cuts if isinstance(cuts, Cuts) else Cuts(tree)
        self.bodies = self.cuts.bodies
        self.env = {}       # (function, parameter) -> the argument one call hands it
        self.back = {}      # function -> the call its `return` goes back to
        self.asking = set()
        self.parents = {}
        for node in ast.walk(tree):
            for child in ast.iter_child_nodes(node):
                self.parents[child] = node
        self.bound = {}   # (scope, name) -> [(where, value or None, binder)]
        self.names = {}   # name -> every read of it
        for node in ast.walk(tree):
            if isinstance(node, ast.Assign):
                for target in node.targets:
                    value = node.value if isinstance(target, ast.Name) else None
                    for name in _targets(target):
                        self._bind(node, name, value, node)
            elif isinstance(node, (ast.AnnAssign, ast.AugAssign)):
                value = node.value if isinstance(node, ast.AnnAssign) else None
                for name in _targets(node.target):
                    self._bind(node, name, value, node)
            elif isinstance(node, ast.NamedExpr):
                self._bind(node, node.target.id, node.value, node)
            elif isinstance(node, (ast.For, ast.AsyncFor)):
                for name in _targets(node.target):
                    self._bind(node.target, name, None, node)
            elif isinstance(node, (ast.With, ast.AsyncWith)):
                for item in node.items:
                    for name in _targets(item.optional_vars):
                        self._bind(item.optional_vars, name, None, node)
            elif isinstance(node, ast.ExceptHandler) and node.name:
                self._bind(node, node.name, None, node)
            elif isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load):
                self.names.setdefault(node.id, []).append(node)
        for entries in self.bound.values():
            entries.sort(key=lambda entry: entry[0])

    # -- where a name came from ---------------------------------------------

    def _bind(self, where, name, value, binder):
        key = (self.scope(where), name)
        self.bound.setdefault(key, []).append(
            ((where.end_lineno, where.end_col_offset), value, binder))

    def scope(self, node):
        here = node
        while here in self.parents:
            here = self.parents[here]
            if isinstance(here, SCOPES):
                return here
        return self.module

    def binding(self, read):
        """(value, binder) of the assignment a read of a name sees, or None."""
        at = (read.lineno, read.col_offset)
        scope = self.scope(read)
        entries = self.bound.get((scope, read.id))
        if entries:
            before = [entry for entry in entries if entry[0] <= at]
            return before[-1][1:] if before else None
        if scope is not self.module and read.id not in _params(scope):
            entries = self.bound.get((self.module, read.id))
            if entries:
                return entries[-1][1:]
        return None

    def argument(self, read):
        """What the call being followed hands a parameter, or None."""
        if not self.env:
            return None
        scope = self.scope(read)
        if (scope, read.id) not in self.env or self.bound.get((scope, read.id)):
            return None
        return self.env[(scope, read.id)]

    def loop_of(self, read):
        """(iterable, target) of the loop or comprehension that binds a name, or None."""
        here = read
        while here in self.parents:
            up = self.parents[here]
            if isinstance(up, COMPREHENSIONS):
                for generator in up.generators:
                    if read.id in _targets(generator.target):
                        return generator.iter, generator.target
            elif isinstance(up, (ast.For, ast.AsyncFor)) and here is not up.iter \
                    and here is not up.target and read.id in _targets(up.target):
                return up.iter, up.target
            elif isinstance(up, SCOPES):
                return None
            here = up
        return None

    def values(self, node, depth=0):
        """The expressions written in this file that `node` can be, or None."""
        if depth > 8:
            return None
        if isinstance(node, ast.Name):
            loop = self.loop_of(node)
            if loop is not None:
                return self.drawn(loop[0], loop[1], node.id, depth)
            bound = self.binding(node)
            if bound is not None and bound[0] is not None:
                return self.values(bound[0], depth + 1)
            argument = self.argument(node) if bound is None else None
            if argument is not None:
                return self.values(argument, depth + 1)
            return None
        if isinstance(node, ast.Subscript):
            index = _integer(node.slice)
            outer = self.values(node.value, depth + 1) if index is not None else None
            if outer is None:
                return None
            found = []
            for value in outer:
                parts = value.elts if isinstance(value, (ast.Tuple, ast.List)) else (
                    value if isinstance(value, tuple) else None)
                if parts is not None and -len(parts) <= index < len(parts) \
                        and parts[index] is not None:
                    found += self.values(parts[index], depth + 1) or []
            return found or None
        return [node]

    def drawn(self, iterable, target, name, depth):
        """What a loop variable is each time round, when the loop is over literals."""
        elements = self.elements(iterable, depth + 1)
        if elements is None:
            return None
        found = []
        for element in elements:
            part = self.pick(element, target, name)
            if isinstance(part, ast.AST):
                found += self.values(part, depth + 1) or []
            elif isinstance(part, tuple):
                found.append(part)
        return found or None

    def pick(self, element, target, name):
        if isinstance(target, ast.Name):
            return element if target.id == name else None
        if isinstance(target, (ast.Tuple, ast.List)):
            if isinstance(element, (ast.Tuple, ast.List)):
                parts = element.elts
            elif isinstance(element, tuple):
                parts = element
            else:
                return None
            if len(parts) != len(target.elts):
                return None
            for part, sub in zip(parts, target.elts):
                if name in _targets(sub):
                    return None if part is None else self.pick(part, sub, name)
        return None

    def elements(self, node, depth):
        """The elements of a collection written out in this file, or None."""
        if depth > 8:
            return None
        if isinstance(node, (ast.List, ast.Tuple, ast.Set)):
            found = []
            for element in node.elts:
                if isinstance(element, ast.Starred):
                    found += self.elements(element.value, depth + 1) or []
                else:
                    found.append(element)
            return found
        if isinstance(node, ast.Dict):
            return [key for key in node.keys if key is not None]
        if isinstance(node, ast.Name):
            bound = self.binding(node)
            if bound is not None and bound[0] is not None:
                return self.elements(bound[0], depth + 1)
            argument = self.argument(node) if bound is None else None
            if argument is not None:
                return self.elements(argument, depth + 1)
            return None
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
            left = self.elements(node.left, depth + 1)
            right = self.elements(node.right, depth + 1)
            return None if left is None and right is None else (left or []) + (right or [])
        if not isinstance(node, ast.Call):
            return None
        name = _name(node.func)
        if isinstance(node.func, ast.Attribute) and not node.args \
                and name in ("items", "keys", "values"):
            pairs = self.mapping(node.func.value, depth + 1)
            if pairs is None:
                return None
            if name == "keys":
                return [key for key, _ in pairs]
            if name == "values":
                return [value for _, value in pairs]
            return pairs
        if not isinstance(node.func, ast.Name) or not node.args:
            return None
        if name in ("sorted", "list", "tuple", "set", "frozenset", "reversed"):
            return self.elements(node.args[0], depth + 1)
        if name == "enumerate":
            inner = self.elements(node.args[0], depth + 1)
            return None if inner is None else [(None, element) for element in inner]
        if name == "zip":
            columns = [self.elements(arg, depth + 1) for arg in node.args]
            known_columns = [column for column in columns if column is not None]
            if not known_columns:
                return None
            rows = min(len(column) for column in known_columns)
            return [tuple(None if column is None else column[row] for column in columns)
                    for row in range(rows)]
        return None

    def mapping(self, node, depth):
        if isinstance(node, ast.Dict):
            return [(key, value) for key, value in zip(node.keys, node.values) if key is not None]
        if isinstance(node, ast.Name) and depth <= 8:
            bound = self.binding(node)
            if bound is not None and bound[0] is not None:
                return self.mapping(bound[0], depth + 1)
        return None

    def spelled(self, value, depth=0):
        """Every text a string expression can be, or [] when it is not a string written here.

        A literal is itself. An f-string or a `+` is expanded through the
        literals its pieces can be; a piece that cannot be resolved is named as
        `{expression}`, and one with no literal piece at all is not text.
        """
        text = literal(value)
        if text is not None:
            return [text]
        if depth > 4:
            return []
        if isinstance(value, ast.JoinedStr):
            pieces = []
            for part in value.values:
                if isinstance(part, ast.Constant):
                    pieces.append([part.value])
                elif part.conversion == -1 and part.format_spec is None:
                    pieces.append([text for _, text in self.texts(part.value, depth + 1)]
                                  or ["{" + ast.unparse(part.value) + "}"])
                else:
                    pieces.append(["{" + ast.unparse(part.value) + "}"])
            return self._joined(pieces)
        if isinstance(value, ast.BinOp) and isinstance(value.op, ast.Add):
            sides = []
            written = False
            for side in (value.left, value.right):
                texts = [text for _, text in self.texts(side, depth + 1)]
                written = written or bool(texts)
                sides.append(texts or ["{" + ast.unparse(side) + "}"])
            return self._joined(sides) if written else []
        return []

    def _joined(self, pieces):
        found = [""]
        for options in pieces:
            found = [head + option for head in found for option in options][:SPELLINGS]
        return found

    def texts(self, node, depth=0):
        """(line, text) for every string `node` can be."""
        found = []
        for value in self.values(node) or ():
            if isinstance(value, ast.AST):
                for text in self.spelled(value, depth):
                    found.append((value.lineno, text))
        return found

    # -- the readings -------------------------------------------------------

    def is_body(self, node):
        return is_body(node, self.bodies, self.more)

    def more(self, node):
        """A body the flat set of names cannot see: a helper's, a parameter's, a row's."""
        if isinstance(node, ast.Call):
            name = node.func.id
            if name in self.cuts.cutters:
                return True
            return any(index < len(node.args) and self.is_body(node.args[index])
                       for index in self.cuts.passes.get(name, ()))
        if not isinstance(node, ast.Name) or node in self.asking:
            return False
        self.asking.add(node)
        try:
            for value in self.values(node) or ():
                if isinstance(value, ast.AST) and value is not node and self.is_body(value):
                    return True
            return False
        finally:
            self.asking.discard(node)

    def compiled(self, node):
        """The pattern of the `re.compile(...)` a name is bound to, or None."""
        if not isinstance(node, ast.Name):
            return None
        bound = self.binding(node)
        call = bound[0] if bound is not None else None
        if isinstance(call, ast.Call) and isinstance(call.func, ast.Attribute) \
                and call.func.attr == "compile" and isinstance(call.func.value, ast.Name) \
                and call.func.value.id == "re" and call.args:
            return call.args[0]
        return None

    def searches(self, node, kinds=SEARCHES):
        func = node.func
        if not (isinstance(func, ast.Attribute) and func.attr in kinds):
            return False
        if isinstance(func.value, ast.Name) and func.value.id == "re":
            return len(node.args) >= 2 and self.is_body(node.args[1])
        # A compiled pattern, `GATE = re.compile(...)` ... `GATE.search(line)`.
        return len(node.args) >= 1 and self.compiled(func.value) is not None \
            and self.is_body(node.args[0])

    def searched(self, node):
        """(line, pattern, suspicious) for a regex search of a body."""
        func = node.func
        pattern = node.args[0] if isinstance(func.value, ast.Name) and func.value.id == "re" \
            else self.compiled(func.value)
        found = []
        for line, pattern in self.texts(pattern):
            text = regex_text(pattern)
            found.append((line, pattern, text is not None and suspect(text)))
        return found

    def position(self, node, depth=0):
        """([(line, text, suspicious)], reading when absent) for a position or count."""
        if depth > 8:
            return None
        if isinstance(node, ast.Name):
            bound = self.binding(node)
            if bound is not None and bound[0] is not None:
                return self.position(bound[0], depth + 1)
            return None
        if not isinstance(node, ast.Call):
            return None
        func = node.func
        if isinstance(func, ast.Attribute) and func.attr in POSITIONS and node.args \
                and self.is_body(func.value):
            found = [(line, text, suspect(text)) for line, text in self.texts(node.args[0])]
            return (found, POSITIONS[func.attr]) if found else None
        if isinstance(func, ast.Name) and func.id == "len" and len(node.args) == 1 \
                and isinstance(node.args[0], ast.Call) \
                and self.searches(node.args[0], {"findall"}):
            found = self.searched(node.args[0])
            return (found, 0) if found else None
        return None

    def probes(self, root=None):
        """(node, line, text, suspicious, absent, kind) for every reading of a body.

        `absent` is what the expression reads when the text is not there: True
        for `not in`, False for `in`. `kind` is "match" for a regex search,
        whose answer is a match or None rather than a bool.
        """
        for node in ast.walk(self.module if root is None else root):
            if isinstance(node, ast.Compare):
                operands = [node.left] + list(node.comparators)
                for index, op in enumerate(node.ops):
                    if isinstance(op, (ast.In, ast.NotIn)):
                        yield from self._member(node, operands[index], operands[index + 1],
                                                isinstance(op, ast.NotIn))
                if all(type(op) in ORDERS for op in node.ops):
                    yield from self._ordered(node, operands, node.ops)
                else:
                    for index, op in enumerate(node.ops):
                        if type(op) in ORDERS:
                            yield from self._ordered(node, operands[index:index + 2], [op])
            elif isinstance(node, ast.Call) and self.searches(node):
                for line, pattern, suspicious in self.searched(node):
                    yield node, line, pattern, suspicious, False, "match"
            elif isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) \
                    and node.func.attr in ENDS and node.args and self.is_body(node.func.value):
                for value in self.values(node.args[0]) or ():
                    choices = value.elts if isinstance(value, (ast.Tuple, ast.List)) else [value]
                    for choice in choices:
                        for line, text in self.texts(choice):
                            yield node, line, text, suspect(text), False, "test"

    def _member(self, node, left, right, negated):
        rows = self.paired(left, right)
        if rows is None:
            if self.is_body(right):
                for line, text in self.texts(left):
                    yield node, line, text, suspect(text), negated, "test"
            return
        for needles, hay in rows:
            if self.is_body(hay):
                for line, text in needles:
                    yield node, line, text, suspect(text), negated, "test"

    def paired(self, left, right):
        """[(needle texts, haystack)] row by row when both come out of one loop, or None."""
        if not (isinstance(left, ast.Name) and isinstance(right, ast.Name)):
            return None
        loop = self.loop_of(right)
        if loop is None or left.id not in _targets(loop[1]) or right.id == left.id:
            return None
        iterable, target = loop
        elements = self.elements(iterable, 1)
        if elements is None:
            return None
        rows = []
        for element in elements:
            hay = self.pick(element, target, right.id)
            needle = self.pick(element, target, left.id)
            if isinstance(hay, ast.AST) and isinstance(needle, ast.AST):
                needles = [(value.lineno, text) for value in self.values(needle) or ()
                           if isinstance(value, ast.AST) for text in self.spelled(value)]
                rows.append((needles, hay))
        return rows

    def measure(self, expr, leaves, constants, depth=0):
        """A function of the leaves' values that is what `expr` comes to, or None.

        A leaf is a position or count of text in a body; the rest may be a
        number, `min`, `max`, `+` or `-` of those, or a name for one.
        """
        if depth > 8:
            return None
        number = _integer(expr)
        if number is not None:
            constants.add(number)
            return lambda values: number
        place = self.position(expr)
        if place is not None:
            index = len(leaves)
            leaves.append(place)
            return lambda values: values[index]
        if isinstance(expr, ast.Name):
            bound = self.binding(expr)
            if bound is not None and bound[0] is not None:
                return self.measure(bound[0], leaves, constants, depth + 1)
            return None
        if isinstance(expr, ast.Call) and isinstance(expr.func, ast.Name) \
                and expr.func.id in ("min", "max") and len(expr.args) > 1 and not expr.keywords:
            parts = [self.measure(arg, leaves, constants, depth + 1) for arg in expr.args]
            if any(part is None for part in parts):
                return None
            choose = min if expr.func.id == "min" else max
            return lambda values: choose(part(values) for part in parts)
        if isinstance(expr, ast.BinOp) and isinstance(expr.op, (ast.Add, ast.Sub, ast.Mult)):
            left = self.measure(expr.left, leaves, constants, depth + 1)
            right = self.measure(expr.right, leaves, constants, depth + 1)
            if left is None and right is None:
                return None
            # One side this cannot work out (`len(roster) * found`): any number.
            if left is None:
                left = (lambda at: lambda values: values[at])(len(leaves))
                leaves.append(([], None))
            if right is None:
                right = (lambda at: lambda values: values[at])(len(leaves))
                leaves.append(([], None))
            if isinstance(expr.op, ast.Add):
                return lambda values: left(values) + right(values)
            if isinstance(expr.op, ast.Mult):
                return lambda values: left(values) * right(values)
            return lambda values: left(values) - right(values)
        if isinstance(expr, ast.IfExp):
            # `cut.count("x(") if found else 0`: the test is whether there was
            # anything to cut, and the constant is what a missing text reads
            # anyway, so the value is the branch that looks.
            start = len(leaves)
            body = self.measure(expr.body, leaves, constants, depth + 1)
            middle = len(leaves)
            orelse = self.measure(expr.orelse, leaves, constants, depth + 1)
            if body is None or orelse is None:
                return None
            if middle > start and len(leaves) == middle:
                return body
            if middle == start and len(leaves) > middle:
                return orelse
        return None

    def _ordered(self, node, operands, ops):
        leaves, constants = [], set()
        measured = []
        for operand in operands:
            part = self.measure(operand, leaves, constants)
            if part is None:
                # Some number this cannot work out (`len(tabs)`, `min(found)`):
                # any number at all, so a text is required when some value of
                # it makes the comparison turn on whether the text is there.
                part = (lambda at: lambda values: values[at])(len(leaves))
                leaves.append(([], None))
            measured.append(part)
        if not any(readings for readings, _ in leaves) or len(leaves) > 5:
            return
        compares = [ORDERS[type(op)] for op in ops]

        def holds(values):
            sides = [part(values) for part in measured]
            return all(compare(a, b) for compare, a, b in zip(compares, sides, sides[1:]))

        spread = (5, 50, 10 ** 6) if len(leaves) < 4 else (50,)
        nearby = {number + step for number in constants for step in (-1, 0, 1)}
        domains = []
        for _, missing in leaves:
            if missing is None:
                domains.append(sorted({-1, 0, 1, 2, *spread} | nearby))
            else:
                present = {missing + 1, missing + 2, *spread} | nearby
                domains.append([missing] + sorted(at for at in present if at > missing))
        for index, (readings, missing) in enumerate(leaves):
            if missing is None:
                continue
            others = [domain for at, domain in enumerate(domains) if at != index]
            absent_reads = set()
            for rest in itertools.product(*others):
                values = list(rest[:index]) + [missing] + list(rest[index:])
                without = holds(values)
                for at in domains[index][1:]:
                    values[index] = at
                    if holds(values) != without:
                        absent_reads.add(without)
                        break
                if len(absent_reads) == 2:
                    break
            for without in sorted(absent_reads):
                for line, text, suspicious in readings:
                    yield node, line, text, suspicious, without, "test"

    def _truth(self, node):
        call = self.parents.get(node)
        return isinstance(call, ast.Call) and isinstance(call.func, ast.Name) \
            and call.func.id in ("any", "all") and node in call.args

    def reads_of(self, assign):
        name = assign.targets[0].id
        return [read for read in self.names.get(name, ())
                if (self.binding(read) or (None, None))[1] is assign]

    def climb(self, node, absent, kind, seen):
        """For each test a reading reaches, whether that rule passes only with the text there."""
        here = node
        while here in self.parents:
            up = self.parents[here]
            if isinstance(up, ast.UnaryOp) and isinstance(up.op, ast.Not):
                absent, kind = not absent, "test"
            elif isinstance(up, ast.BoolOp):
                pass
            elif isinstance(up, ast.Compare):
                if kind == "match":
                    against = up.comparators[0]
                    if up.left is not here or len(up.ops) != 1 \
                            or not isinstance(up.ops[0], (ast.Is, ast.IsNot, ast.Eq, ast.NotEq)) \
                            or not (isinstance(against, ast.Constant) and against.value is None):
                        return []
                    absent, kind = isinstance(up.ops[0], (ast.Is, ast.Eq)), "test"
            elif isinstance(up, ast.Call) and isinstance(up.func, ast.Name) \
                    and up.func.id in TRUTHS and here in up.args:
                kind = "test"
            elif isinstance(up, (ast.GeneratorExp, ast.ListComp, ast.SetComp)) \
                    and up.elt is here and self._truth(up):
                pass
            elif isinstance(up, (ast.List, ast.Tuple, ast.Set)) and self._truth(up):
                pass
            elif isinstance(up, ast.NamedExpr) and up.value is here:
                pass
            elif isinstance(up, ast.If) and up.test is here:
                # The branch that only moves on is the passing one, so the
                # requirement is on the way that goes on.
                return [not absent] if _leaves_branch(up.body) else [absent]
            elif isinstance(up, (ast.While, ast.IfExp)) and up.test is here:
                return [absent]
            elif isinstance(up, ast.comprehension) and here in up.ifs:
                return [absent]
            elif isinstance(up, ast.Assert) and up.test is here:
                return [not absent]
            elif isinstance(up, ast.Return) and up.value is here \
                    and self.scope(up) in self.back:
                # A helper handing its test back: read where the call is tested.
                here = self.back[self.scope(up)]
                continue
            elif isinstance(up, ast.Assign) and up.value is here and len(up.targets) == 1 \
                    and isinstance(up.targets[0], ast.Name):
                # Stored first, tested later: `ok = "x(" in body` ... `if not ok:`.
                found = []
                for read in self.reads_of(up):
                    if read not in seen:
                        seen.add(read)
                        found += self.climb(read, absent, kind, seen)
                return found
            else:
                return []   # stored, not tested: a reading of the source
            here = up
        return []

    # -- helpers, read at each call ------------------------------------------

    def _probing(self, function):
        for node in _own(function):
            if isinstance(node, ast.Compare):
                return True
            if isinstance(node, ast.Call) and _name(node.func) in (
                    set(POSITIONS) | ENDS | SEARCHES):
                return True
        return False

    def calls(self):
        found = {}
        for node in ast.walk(self.module):
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) \
                    and node.func.id in self.cuts.defs:
                found.setdefault(node.func.id, []).append(node)
        return found

    def _bind_call(self, function, call):
        params = _positional(function)
        env = {}
        for index, arg in enumerate(call.args):
            if isinstance(arg, ast.Starred) or index >= len(params):
                break
            env[(function, params[index])] = arg
        named = set(params) | {arg.arg for arg in function.args.kwonlyargs}
        for keyword in call.keywords:
            if keyword.arg in named:
                env[(function, keyword.arg)] = keyword.value
        return env

    def environments(self):
        """(function, env, back) for each call of each helper that might hold a rule."""
        calls = self.calls()
        memo = {}

        def reached(function, depth):
            if (function, depth) in memo:
                return memo[(function, depth)]
            memo[(function, depth)] = []
            found = []
            for call in calls.get(function.name, ()):
                if self.cuts.defs.get(function.name) is not function:
                    break
                env, back = self._bind_call(function, call), {function: call}
                caller = self.scope(call)
                outer = reached(caller, depth + 1) if isinstance(caller, ast.FunctionDef) \
                    and caller is not function and depth < CALL_DEPTH else []
                for outer_env, outer_back in outer or [({}, {})]:
                    found.append(({**outer_env, **env}, {**outer_back, **back}))
                if len(found) >= CALLS_FOLLOWED:
                    break
            memo[(function, depth)] = found
            return found

        out = []
        for name, function in self.cuts.defs.items():
            if self.cuts.cutting(name) or not self._probing(function):
                continue
            for env, back in reached(function, 0):
                out.append((function, env, back))
        return out


def rules_in(tree, bodies=None):
    """(line, text, required, suspicious) for every literal a rule looks for in a body.

    `required` is whether the rule passes because the text is there: `not in`
    under an `if`, `in` under an `assert`, either flipped by a `not`, and every
    other way of saying the same (see the module docstring). `suspicious` is
    whether the text is half a statement. `bodies` is no longer needed and is
    accepted for the callers that still pass it.
    """
    reading = Reading(tree)
    reading.read = set()
    for function, env, back in [(None, {}, {})] + reading.environments():
        reading.env, reading.back = env, back
        for node, line, text, suspicious, absent, kind in list(reading.probes(function)):
            for required in reading.climb(node, absent, kind, set()):
                reading.read.add(node)
                yield line, text, required, suspicious
    reading.env, reading.back = {}, {}


def escaped(text):
    """One line of the baseline, whatever the literal had in it."""
    return (text.replace("\\", "\\\\").replace("\n", "\\n")
            .replace("\r", "\\r").replace("\t", "\\t"))


#: Every module validator, and the root checks, which take bodies the same way.
validators = [(name, os.path.join(HERE, name, "validate.py"))
              for name in sorted(os.listdir(HERE))
              if name.startswith("elysium-")
              and os.path.isfile(os.path.join(HERE, name, "validate.py"))]
validators += [(name, os.path.join(HERE, name)) for name in sorted(os.listdir(HERE))
               if re.fullmatch(r"check_\w+\.py", name)]

known = set()
if os.path.isfile(BASELINE):
    for line in open(BASELINE, encoding="utf-8"):
        line = line.rstrip("\n")
        if line and not line.startswith("#"):
            known.add(line)

found = set()
checked = 0
rules = 0
for module, path in validators:
    where = os.path.relpath(path, HERE).replace(os.sep, "/")
    try:
        tree = ast.parse(open(path, encoding="utf-8").read(), filename=path)
    except SyntaxError as broken:
        # A validator this cannot read is one whose rules it cannot vouch for.
        unread.append(f"{where}:{broken.lineno} does not parse ({broken.msg}), so none of "
                      f"its rules can be checked")
        continue
    # Not skipped when no name holds a body: `"x(" in body_of(...)` takes one in place.
    readings = sorted(set(rules_in(tree)))
    checked += bool(readings)
    for number, text, required, suspicious in readings:
        rules += 1
        if not required or not suspicious:
            continue
        entry = f"{module}\t{escaped(text)}"
        found.add(entry)
        if entry not in known:
            problems.append(
                f"{where}:{number} looks for {text!r} inside a "
                f"method body. That is half a statement: an edit can leave it "
                f"exactly where it is and do something else with the result. "
                f"Name the whole statement.")

problems = unread + problems

if "--bless" in sys.argv:
    if unread:
        # Blessing now would drop that validator's entries from the ratchet
        # without anybody having rewritten them.
        print("not blessing: " + "; ".join(unread))
        sys.exit(1)
    os.makedirs(os.path.dirname(BASELINE), exist_ok=True)
    with open(BASELINE, "w", encoding="utf-8", newline="\n") as handle:   # LF on Windows too, or git sees a rewrite
        handle.write("# Validator rules that name a call where they should name a "
                     "statement.\n#\n# Written by check_rules.py --bless. Every line is "
                     "a rule that can be\n# defeated by an edit that leaves the call "
                     "exactly where it is — an\n# `if (false)` in front of it, an "
                     "assignment nobody reads — and goes on\n# passing. This file is a "
                     "ratchet: a new one fails the build, and these\n# come out as the "
                     "modules they are in are next touched.\n#\n# Fewer lines here is "
                     "the only direction this file may move.\n")
        for entry in sorted(found):
            handle.write(entry + "\n")
    print(f"blessed {len(found)} existing rule(s) into {os.path.relpath(BASELINE, HERE)}")
    sys.exit(0)

stale = sorted(known - found)

print("=" * 70)
print("rules — no validator names half a statement and calls it a check")
print("=" * 70)

if problems:
    print(f"{len(problems)} PROBLEM(S):")
    for problem in problems:
        print("  - " + problem)
    print("  (these are new; docs/RULES_BASELINE.txt records the ones already here)")
    sys.exit(1)

if stale:
    print(f"validator rules    : {len(stale)} baselined rule(s) have been rewritten — "
          f"run check_rules.py --bless to take them off the list")

print(f"validator rules    : {rules} rule(s) across {checked} validator(s); "
      f"{len(found)} still name a call rather than a statement and are on the ratchet, "
      f"and no new one has been added")
