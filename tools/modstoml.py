"""
Splitting and recombining neoforge.mods.toml.

A NeoForge mod file may declare more than one mod: the loader reads every
`[[mods]]` block in `META-INF/neoforge.mods.toml` and finds the `@Mod` class for
each by scanning the jar. That is the entire mechanism behind a merged jar, and
it means merging six mods is mostly a text problem — take six files that each
have one `[[mods]]` block and produce one file that has six.

<h2>Why a hand-written splitter rather than a TOML library</h2>

Reading is easy with `tomllib`; the difficulty is *writing*. The standard
library has no TOML writer, and a round trip through a parsed dict would throw
away every comment in these files — and the comments are where the reasons live.
Each module's dependency block explains why it is ordered AFTER the library, or
why Silent Gear is optional, and a merged file that dropped all of it would be
the single worst-documented file in the repository.

So this works on text spans: it finds the boundaries of each top-level block and
carries the block across verbatim, comments and all. `tomllib` is still used, at
the end, to check that what came out parses and says what it should.
"""
import re

try:
    import tomllib
except ModuleNotFoundError:                                  # Python < 3.11
    tomllib = None

# A top-level table header: [[mods]], [[dependencies.elysium]], [something].
HEADER = re.compile(r"^\s*\[\[?[^\]]+\]\]?\s*$")


def split_blocks(text):
    """
    A mods.toml as (preamble, [(lead, header, body), ...]).

    The preamble is everything before the first table header — modLoader,
    loaderVersion, license, issueTrackerURL.

    `lead` is the run of comment lines immediately above a header. They are kept
    separate from the body rather than folded into it so that the merged file
    can put them back *above* the header, which is where they were written and
    where they read as being about the block rather than about its first key.
    Fold them into the body and every explanatory comment in these files shifts
    down one line into the table it introduces.
    """
    lines = text.splitlines()
    preamble = []
    blocks = []
    current_header = None
    current_lead = []
    current = []
    pending_comments = []

    for line in lines:
        if HEADER.match(line):
            if current_header is None:
                preamble.extend(pending_comments)
            else:
                blocks.append((current_lead, current_header, trim(current)))
            current_header = line.strip()
            current_lead = trim(pending_comments)
            current = []
            pending_comments = []
            continue

        stripped = line.strip()
        if stripped.startswith("#") or not stripped:
            pending_comments.append(line)
            continue

        if current_header is None:
            preamble.extend(pending_comments)
            pending_comments = []
            preamble.append(line)
        else:
            current.extend(pending_comments)
            pending_comments = []
            current.append(line)

    if current_header is not None:
        blocks.append((current_lead, current_header, trim(current + pending_comments)))
    else:
        preamble.extend(pending_comments)

    return trim(preamble), blocks


def trim(lines):
    """Drop leading and trailing blank lines, keeping the inside intact."""
    start, end = 0, len(lines)
    while start < end and not lines[start].strip():
        start += 1
    while end > start and not lines[end - 1].strip():
        end -= 1
    return lines[start:end]


def preamble_values(preamble):
    """The bare key=value pairs of a preamble, for agreement checks."""
    values = {}
    for line in preamble:
        stripped = line.strip()
        if stripped.startswith("#") or "=" not in stripped:
            continue
        key, _, value = stripped.partition("=")
        values[key.strip()] = value.strip().strip('"')
    return values


def merged_license(licenses, mods_by_module):
    """
    One licence line for several modules.

    All the same: that licence. Otherwise the most common one, then each
    exception with the mod ids it covers, e.g.
    "MIT; elysiumspace: CC BY-NC-SA 4.0 (...)". Every mod's licence is stated
    and none is claimed for a mod it does not cover.
    """
    distinct = sorted(set(licenses.values()))
    if len(distinct) == 1:
        return distinct[0]
    counts = {lic: sum(1 for v in licenses.values() if v == lic) for lic in distinct}
    main = max(distinct, key=lambda lic: (counts[lic], lic))
    parts = [main]
    for lic in distinct:
        if lic == main:
            continue
        ids = sorted(m for name, v in licenses.items() if v == lic
                     for m in mods_by_module.get(name, [name]))
        parts.append(", ".join(ids) + ": " + lic)
    return "; ".join(parts).replace('"', "'")


def combine(sources, issue_tracker_url, banner=None):
    """
    One mods.toml from several.

    @param sources  [(module name, expanded mods.toml text)], in load order
    @return the merged text

    Raises ValueError when the sources disagree about something that can only
    have one value in a merged file — the loader version, the licence — because
    silently taking the first would produce a jar that claims a licence five of
    its six mods are not under.
    """
    preambles = {}
    merged_blocks = []
    seen_mod_ids = []

    for name, text in sources:
        preamble, blocks = split_blocks(text)
        preambles[name] = preamble_values(preamble)
        for lead, header, body in blocks:
            merged_blocks.append((name, lead, header, body))
            if header.strip() == "[[mods]]":
                for line in body:
                    match = re.match(r'\s*modId\s*=\s*"([^"]+)"', line)
                    if match:
                        seen_mod_ids.append(match.group(1))

    # Agreement on the things a merged file can only say once. The licence is
    # not one of them any more: elysium-space is CC BY-NC-SA (it is derived
    # from Stellaris) while the rest are MIT. The merged file names every
    # licence and which mods are under it (see merged_license), rather than
    # declaring one and misdeclaring the others.
    for key in ("modLoader", "loaderVersion"):
        answers = {name: values.get(key) for name, values in preambles.items()}
        distinct = set(answers.values())
        if len(distinct) > 1:
            raise ValueError(
                f"modules disagree about {key}: {answers} — a merged jar can "
                f"only declare one, and picking silently would misdeclare five "
                f"of the six mods")
        if not distinct or None in distinct:
            raise ValueError(f"no module declares {key}")

    licenses = {name: values.get("license") for name, values in preambles.items()}
    if None in licenses.values():
        raise ValueError("a module declares no license: "
                         f"{sorted(n for n, v in licenses.items() if v is None)}")
    mods_by_module = {}
    for name, lead, header, body in merged_blocks:
        if header.strip() == "[[mods]]":
            for line in body:
                match = re.match(r'\s*modId\s*=\s*"([^"]+)"', line)
                if match:
                    mods_by_module.setdefault(name, []).append(match.group(1))

    shared = next(iter(preambles.values()))
    duplicates = sorted({m for m in seen_mod_ids if seen_mod_ids.count(m) > 1})
    if duplicates:
        raise ValueError(f"modId declared more than once: {duplicates}")

    out = []
    if banner:
        out.extend("# " + line if line else "#" for line in banner.splitlines())
        out.append("")
    out.append(f'modLoader="{shared["modLoader"]}"')
    out.append(f'loaderVersion="{shared["loaderVersion"]}"')
    out.append(f'license="{merged_license(licenses, mods_by_module)}"')
    out.append(f'issueTrackerURL="{issue_tracker_url}"')

    # Mods first, then every dependency block. The loader does not care about
    # the order, but a human opening this file wants the six mods it declares
    # before the twenty-odd dependency stanzas that qualify them.
    for wanted in (True, False):
        for name, lead, header, body in merged_blocks:
            if (header.strip() == "[[mods]]") != wanted:
                continue
            out.append("")
            out.append(f"# --- from {name} " + "-" * max(0, 58 - len(name)))
            out.extend(lead)
            out.append(header)
            out.extend(body)

    return "\n".join(out) + "\n"


def declared_mod_ids(text):
    """Every modId in a mods.toml, using a real parser."""
    if tomllib is None:
        return re.findall(r'modId\s*=\s*"([^"]+)"', text)
    parsed = tomllib.loads(text)
    return [entry["modId"] for entry in parsed.get("mods", [])]
