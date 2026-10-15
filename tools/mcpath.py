#!/usr/bin/env python3
"""
The Minecraft classpath, wherever this tree is being worked on.

    import mcpath
    parts = mcpath.classpath()          # a list of jar paths, native to this OS
    cp = mcpath.join(parts + [more])    # one -cp argument

compile.sh reads /tmp/mc/cp.txt as a colon-joined list of POSIX paths, because
it runs in a shell and a shell converts. The Python checks used to read the
same file and hand it to javac unchanged, which works only where ":" is the
separator and "/c/..." is a path - not on Windows, where every one of them
reported "the modules do not compile" about a classpath rather than a module.

So: the file is found through ELYSIUM_MC_LIBS (default /tmp/mc), split on
whichever separator it was written with, each entry made native, and joined
with os.pathsep. Nothing about which jars are on it changes.
"""
import os
import re

MC = os.environ.get("ELYSIUM_MC_LIBS", "/tmp/mc")


def libs():
    """The directory cp.txt and the helper jars live in."""
    return MC


def native(part):
    """One entry as this OS spells it: /c/x -> C:/x on Windows, unchanged elsewhere."""
    part = part.strip()
    if os.name == "nt" and re.match(r"^/[a-zA-Z]/", part):
        return part[1].upper() + ":" + part[2:]
    return part


def split(text):
    """The entries of a classpath file, whichever separator wrote it."""
    text = text.strip()
    if not text:
        return []
    if ";" in text:
        parts = text.split(";")
    elif re.fullmatch(r"[a-zA-Z]:[\\/][^:]*", text):
        parts = [text]                    # one Windows entry: "C:/x.jar" is not "C" and "/x.jar"
    elif re.search(r":(?=/)", text) or (":" in text and not re.search(r"^[a-zA-Z]:[\\/]", text)):
        parts = text.split(":")
    else:
        parts = [text]
    return [native(p) for p in parts if p.strip()]


def classpath(path=None):
    """The classpath file's entries as a list, or [] if there is no file."""
    path = path or os.path.join(MC, "cp.txt")
    if not os.path.isfile(path):
        return []
    return split(open(path, encoding="utf-8").read())


def join(parts):
    """One -cp argument for this OS."""
    return os.pathsep.join(p for p in parts if p)


def problem(path=None):
    """
    Why this classpath cannot be asked anything, or None when it can.

    For a check to FAIL on, never to skip on. Three checks spent September
    splitting this file on ":" themselves, reading "C:/..." as two entries
    neither of which exists, and reporting "0 checked" as a pass. A check that
    reads its jars through here and stops on this cannot do that quietly.
    """
    path = path or os.path.join(MC, "cp.txt")
    if not os.path.isfile(path):
        return (f"there is no classpath file at {path} (set ELYSIUM_MC_LIBS to the "
                f"folder holding cp.txt, or run tools/setup_env.py)")
    parts = classpath(path)
    if not parts:
        return f"the classpath file {path} names no jars"
    if not any(os.path.exists(part) for part in parts):
        return (f"the classpath file {path} names {len(parts)} entries and none of "
                f"them exists here (the first is {parts[0]})")
    return None
