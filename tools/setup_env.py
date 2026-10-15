#!/usr/bin/env python3
"""
The build and validation environment, rebuilt on this machine in one command.

    python3 tools/setup_env.py            build what is missing, link the old paths,
                                          verify every piece, print the exports
    python3 tools/setup_env.py --check    verify only; writes nothing

Run it again whenever anything below is in doubt: it keeps what is already
right, rebuilds what is not, and exits non-zero if a piece fails its check.
Windows only; a Linux container has tools/classpath/bootstrap.sh instead.

Why this exists
---------------
Every validator and compile.sh read their jars out of %TEMP%: /tmp/mc
(cp.txt, the annotations jar, vanilla.jar, libs/ with vanilla-assets.jar and
the log4j and asm helpers), C:/tmp/mcwin (the same classpath spelt for
Windows), /tmp/mods (the mods the tree compiles against) and
/tmp/ref/stellaris. Git Bash's /tmp is %TEMP%, and C:/tmp is a junction into
%TEMP% as well, so all of it was one folder.

On 11 October 2026 Windows Storage Sense deleted every file in %TEMP% that had
sat idle for a week. The folders survived, empty. Nothing failed loudly, which
is the dangerous part: the checks that need a jar mostly print "skipped" and
pass (tools/classpath/README.md, point 4, is the same failure seen earlier).

So the rule is now that nothing durable lives in %TEMP% or C:/tmp. The
environment is built under C:/Users/weaks/Elysium-env, and each old path
becomes an NTFS junction to its durable folder:

    %TEMP%/mc    -> Elysium-env/mc       which /tmp/mc and C:/tmp/mc reach
    %TEMP%/mcwin -> Elysium-env/mcwin    C:/tmp/mcwin
    %TEMP%/mods  -> Elysium-env/mods     /tmp/mods
    %TEMP%/ref   -> Elysium-env/ref      /tmp/ref/stellaris
    %TEMP%/elysium-classes  -> Elysium-env/elysium-classes
                                         compile.sh's default output, which the core,
                                         armory, space and solarpunk validators read
    %TEMP%/elysium-loadtest -> Elysium-env/elysium-loadtest
                                         check_loadtest.py's classes and ids.txt

A junction and never a copy, because a copy in %TEMP% is exactly what was
lost. The junctions exist only because scripts, defaults and harness comments
all over the tree spell the old paths. The exports this script prints name
the durable folders directly, so a junction that goes missing costs nothing
but a re-run. An old path is replaced only while it is an empty folder (the
shell Storage Sense left); one that holds a file is refused, never deleted.
The two build outputs are the exception: what a recent compile left in them
is moved into the durable folder (only while that one is empty), then linked.
compile.sh empties its output folder rather than removing it, because rm -rf
on a junction removes the link and the next mkdir puts the classes back in
%TEMP%.

One more junction is checked and never re-pointed: %TEMP%/Elysium-mono ->
C:/Users/weaks/Elysium-mono, the tree itself (/tmp/Elysium-mono in Git Bash),
which older notes and harness comments spell. A build makes it only when it
is absent.

Line endings
------------
The tree is LF and the generated-file checks compare bytes, so every clone
needs core.autocrlf false. Git for Windows' system gitconfig sets it true: a
clone restored after the E: drive was lost picked that up, checked out 37,190
files CRLF, and 19 module validators failed on "generated files differ". The
script reads the effective value for the repository it lives in (git config
--get core.autocrlf), sets it false in that clone's own .git/config when it is
anything else (never --global or --system), and counts the tracked files that
are LF in the index but CRLF on disk.

Setting the option does not touch the files already checked out, and neither
does `git checkout-index --force --all` on its own: it skips every file whose
stat data the index still trusts, which some time after a checkout is all of
them (tried on a scratch clone, 15 October 2026: six CRLF files stayed CRLF and
git status called the tree clean). The sequence that rewrites them and loses
nothing (RECHECKOUT below prints it):

    git -c core.autocrlf=true stash push      only if git status shows edits; the
                                              -c makes the stash store them as LF
    git read-tree HEAD                        reload the index with no stat data;
                                              its content stays HEAD's
    git checkout-index --force --all --index  rewrite every tracked file from the
                                              index, and record fresh stat data
    git stash pop --index                     if the first step stashed anything

`git rm --cached -r . && git reset --hard` gets there too but throws away any
edit not stashed, and `git add --renormalize .` changes the index, not the
files on disk.

What it builds, and from where
------------------------------
Everything comes off the local CurseForge install, read only, plus two build
outputs that live outside %TEMP%. D:/testingbuilds/Elysium/mods is searched
for a mod jar only when no CurseForge instance has it.

    mc/cp.txt          the compile classpath as colon-joined POSIX paths (compile.sh)
    mcwin/cp.txt       the same entries in "C:/...;" form (ELYSIUM_MC_LIBS for validators)
    mc/moddev/neoforge-21.1.251.jar
                       ModDevGradle's compile jar from the Valoria port: Minecraft and
                       NeoForge joined, in Mojang names, with the access transformers on
    mc/annotations-24.1.0.jar
                       org.jetbrains:annotations 24.1.0 from the Gradle cache
    mc/vanilla.jar and mcwin/vanilla.jar
                       Install/versions/1.21.1/1.21.1.jar. Four validators read the game's
                       own names from it and skip SILENTLY when it is missing
    mc/libs/ and mcwin/libs/
                       log4j 2.19.0 (api, core, slf4j2-impl), the five asm 9.10.1 jars and
                       commons-io 2.15.1 from Install/libraries (byte-identical to
                       curseforge/_stage, where the first copies came from), and
                       vanilla-assets.jar: the client jar's assets/, data/, version.json,
                       pack.mcmeta and pack.png only, so Bootstrap finds the language file
                       and no obfuscated class shadows a real one
    mods/              the thirteen compile-only mods, at the exact versions in MODS below
    ref/stellaris/     stellaris-1.21-neoforge-1.4.25.jar from the Techpack instance,
                       unpacked whole
    elysium-classes/ and elysium-loadtest/
                       nothing built here: the folders compile.sh and check_loadtest.py
                       write into, given a durable home behind their old paths

The classpath, in order. The order matters (tools/classpath/README.md, point 3).

    1. the ModDevGradle jar, NeoForge 21.1.251's classes. The raw launcher jars
       cannot be compiled against: NeoForge widens the StairBlock and
       ButtonBlock constructors, BlockSetType.register, DamageSources.source and
       more only at runtime, and this is the one jar on the machine with those
       widenings applied. It is copied into Elysium-env so that a
       `gradlew clean` in the Valoria tree cannot take it away.
    2. the annotations jar.
    3. the 106 libraries of the client's own launch, NeoForge 21.1.248 (FML
       4.0.43) over vanilla 1.21.1. They are read from the version JSONs under
       Install/versions, which give the same list in the same order as the
       Claude instance's logged launch line (logs/instance_audit.txt) that the
       first cp.txt was cut from on 24 September.
    4. Curios, which elysium-trinkets imports outright.

That is the classpath every validator passed against from 25 September to
11 October: classes from 21.1.251, libraries from 21.1.248 (the version the
client runs). It is rebuilt as it was, not improved.

Not rebuilt: the reference unpacks that docs/reference/*_inventory.md cite
under /tmp/ref (Silent Gear, Apotheosis, the Apothic jars). Nothing reads
them; they were the sources for those documents.
"""
import argparse
import glob
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
#: The checkout this script lives in, whose line endings it checks.
REPO = os.path.realpath(os.path.dirname(HERE))
HOME = os.environ.get("USERPROFILE") or os.path.expanduser("~")
#: The working tree that %TEMP%/Elysium-mono points at.
MONO = os.path.normpath(os.path.join(HOME, "Elysium-mono"))

#: The durable home. Override with ELYSIUM_ENV only to build a second copy.
ENV = os.path.normpath(os.environ.get("ELYSIUM_ENV", os.path.join(HOME, "Elysium-env")))
CURSEFORGE = os.path.normpath(os.environ.get("ELYSIUM_CURSEFORGE",
                                             os.path.join(HOME, "curseforge")))
INSTALL = os.path.join(CURSEFORGE, "minecraft", "Install")
LIBRARIES = os.path.join(INSTALL, "libraries")
INSTANCES = os.path.join(CURSEFORGE, "minecraft", "Instances")
TEMP = os.path.normpath(os.environ.get("TEMP") or tempfile.gettempdir())

MC = os.path.join(ENV, "mc")
MCWIN = os.path.join(ENV, "mcwin")
MODS = os.path.join(ENV, "mods")
REF = os.path.join(ENV, "ref")
STELLARIS = os.path.join(REF, "stellaris")
SCRATCH = os.path.join(ENV, "scratch")
#: Build outputs, rebuilt by every compile: compile.sh's $ELYSIUM_OUT default
#: and check_loadtest.py's OUT.
CLASSES = os.path.join(ENV, "elysium-classes")
LOADTEST = os.path.join(ENV, "elysium-loadtest")

#: The client runs this NeoForge; its launch supplies the libraries.
NEOFORGE = "21.1.248"
MINECRAFT = "1.21.1"

#: ModDevGradle's compile jar (NeoForge 21.1.251, access transformers applied).
#: Only the Valoria port's copy: the Tridot port's carries different ATs.
MODDEV_NAME = "neoforge-21.1.251.jar"
MODDEV_SOURCES = [os.environ.get("ELYSIUM_MODDEV_JAR", ""),
                  "C:/ClaudeValoria/Valoria/build/moddev/artifacts/neoforge-21.1.251.jar"]
MODDEV = os.path.join(MC, "moddev", MODDEV_NAME)

ANNOTATIONS_NAME = "annotations-24.1.0.jar"
ANNOTATIONS_SOURCES = os.path.join(HOME, ".gradle", "caches", "modules-2", "files-2.1",
                                   "org.jetbrains", "annotations", "24.1.0", "*",
                                   ANNOTATIONS_NAME)
ANNOTATIONS = os.path.join(MC, ANNOTATIONS_NAME)

#: The helper jars check_loadtest and check_construct look for by bare name in
#: $ELYSIUM_MC_LIBS/libs, each with where it sits under Install/libraries.
HELPERS = {
    "log4j-api-2.19.0.jar": "org/apache/logging/log4j/log4j-api/2.19.0",
    "log4j-core-2.19.0.jar": "org/apache/logging/log4j/log4j-core/2.19.0",
    "log4j-slf4j2-impl-2.19.0.jar": "org/apache/logging/log4j/log4j-slf4j2-impl/2.19.0",
    "asm-9.10.1.jar": "org/ow2/asm/asm/9.10.1",
    "asm-analysis-9.10.1.jar": "org/ow2/asm/asm-analysis/9.10.1",
    "asm-commons-9.10.1.jar": "org/ow2/asm/asm-commons/9.10.1",
    "asm-tree-9.10.1.jar": "org/ow2/asm/asm-tree/9.10.1",
    "asm-util-9.10.1.jar": "org/ow2/asm/asm-util/9.10.1",
    "commons-io-2.15.1.jar": "commons-io/commons-io/2.15.1",
}
STAGE = os.path.join(CURSEFORGE, "_stage")

CLIENT_JAR = os.path.join(INSTALL, "versions", MINECRAFT, MINECRAFT + ".jar")
VANILLA_ASSETS = "vanilla-assets.jar"

#: The compile-only mods, by exact file name: the set /tmp/mods held from
#: 28 September (twelve copied on the 25th, JEI added on the 28th). compile.sh
#: takes the first match of each of its globs, so a second version beside one
#: of these would quietly change what the tree is checked against; verify()
#: refuses that.
MOD_JARS = [
    "silent-gear-1.21.1-neoforge-4.2.1.1.jar",
    "silent-lib-1.21.1-neoforge-10.6.0.jar",
    "Apotheosis-1.21.1-8.8.0.jar",
    "ApothicAttributes-1.21.1-2.10.1.jar",
    "Placebo-1.21.1-9.9.2.jar",
    "curios-neoforge-9.5.1+1.21.1.jar",
    "minecolonies-1.1.1374-1.21.1-snapshot.jar",
    "structurize-1.0.832-1.21.1.jar",
    "blockui-1.0.209-1.21.1.jar",
    "domum-ornamentum-1.0.231-main.jar",
    "puffish_skills-0.18.3-1.21-neoforge.jar",
    "puffish_attributes-0.8.3-1.21-neoforge.jar",
    "jei-1.21.1-neoforge-19.44.0.413.jar",
]
#: The globs compile.sh resolves in $ELYSIUM_COMPAT_LIBS.
COMPILE_GLOBS = ["jei-*.jar", "silent-gear-*.jar", "silent-lib-*.jar", "Apotheosis-*.jar",
                 "Placebo-*.jar", "minecolonies-*.jar", "structurize-*.jar", "blockui-*.jar",
                 "domum-ornamentum-*.jar", "curios-neoforge-*.jar"]
CURIOS = "curios-neoforge-9.5.1+1.21.1.jar"

#: Where the mods are looked for, in order. Never the "V+ and Tech" instances.
MOD_DIRS = [os.path.join(INSTANCES, name, "mods")
            for name in ("Elysium Mod test", "Claude-dev-build", "Claude", "Techpack")]
#: Read only, and only for a jar no CurseForge instance has.
FALLBACK_MOD_DIRS = ["D:/testingbuilds/Elysium/mods", "D:/testingbuilds/Elysium/mods/_superseded"]

STELLARIS_NAME = "stellaris-1.21-neoforge-1.4.25.jar"
STELLARIS_SOURCES = [os.path.join(INSTANCES, name, "mods", STELLARIS_NAME)
                     for name in ("Techpack", "servertest")]
STELLARIS_MARK = os.path.join(REF, "stellaris.source.json")

#: The old %TEMP% paths, the durable folder each now points at, and whether
#: what is already in the old one may be moved across (only build outputs).
LINKS = [("mc", MC, False), ("mcwin", MCWIN, False), ("mods", MODS, False), ("ref", REF, False),
         ("elysium-classes", CLASSES, True), ("elysium-loadtest", LOADTEST, True)]
#: The tree's own junction: checked, made only when absent, never re-pointed.
TREE_LINK = ("Elysium-mono", MONO, False)

#: The re-checkout that turns a CRLF working tree back to LF (see the docstring).
RECHECKOUT = [
    ("git -c core.autocrlf=true stash push", "only if git status shows edits; the -c stores them LF"),
    ("git read-tree HEAD", "reload the index with no stat data; its content stays HEAD's"),
    ("git checkout-index --force --all --index", "rewrite every tracked file, LF, with fresh stat data"),
    ("git stash pop --index", "if the first step stashed anything"),
]

problems = []


def problem(msg):
    problems.append(msg)
    print("  PROBLEM " + msg)


def fwd(path):
    """A Windows path with forward slashes, the spelling cp.txt and the exports use."""
    return os.path.normpath(path).replace("\\", "/")


def posix(path):
    """C:/x -> /c/x, the spelling compile.sh and Git Bash want."""
    p = fwd(path)
    if len(p) > 2 and p[1] == ":":
        return "/" + p[0].lower() + p[2:]
    return p


def inside_env(path):
    full = os.path.normcase(os.path.abspath(path))
    return full.startswith(os.path.normcase(ENV) + os.sep)


def place(src, dst):
    """Copy src to dst unless dst already is it (same size and time)."""
    if (os.path.isfile(dst) and os.path.getsize(dst) == os.path.getsize(src)
            and int(os.path.getmtime(dst)) == int(os.path.getmtime(src))):
        return "kept  "
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    part = dst + ".part"
    shutil.copy2(src, part)
    os.replace(part, dst)
    return "copied"


def first_file(candidates):
    return next((c for c in candidates if c and os.path.isfile(c)), None)


def write_text(path, text):
    """Write LF text without a BOM, only when it changed."""
    if os.path.isfile(path) and open(path, encoding="utf-8", newline="").read() == text:
        return "kept  "
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline=chr(10)) as handle:
        handle.write(text)
    return "wrote "


# ---------------------------------------------------------------------------
# The launch libraries, from the version JSONs
# ---------------------------------------------------------------------------

def _allowed(lib):
    """A launcher library rule, for Windows (every arch: the launch lists all three natives)."""
    rules = lib.get("rules")
    if not rules:
        return True
    verdict = False
    for rule in rules:
        os_rule = rule.get("os") or {}
        if os_rule.get("name") and os_rule["name"] != "windows":
            continue
        verdict = rule.get("action") == "allow"
    return verdict


def _library_paths(version_json):
    data = json.load(open(version_json, encoding="utf-8"))
    out = []
    for lib in data.get("libraries", []):
        if not _allowed(lib):
            continue
        artifact = (lib.get("downloads") or {}).get("artifact") or {}
        path = artifact.get("path")
        if not path:
            parts = lib["name"].split(":")
            group, name, version = parts[:3]
            classifier = "-" + parts[3] if len(parts) > 3 else ""
            path = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}{classifier}.jar"
        out.append(path)
    return data, out


def launch_libraries():
    """NeoForge's libraries, then vanilla's, as the launcher puts them on -cp."""
    neo_json = os.path.join(INSTALL, "versions", f"neoforge-{NEOFORGE}", f"neoforge-{NEOFORGE}.json")
    neo, neo_libs = _library_paths(neo_json)
    parent = neo.get("inheritsFrom", MINECRAFT)
    _, vanilla_libs = _library_paths(os.path.join(INSTALL, "versions", parent, parent + ".json"))
    seen = []
    for path in neo_libs + vanilla_libs:
        if path not in seen:
            seen.append(path)
    return [fwd(os.path.join(LIBRARIES, p)) for p in seen]


def classpath_entries():
    return [fwd(MODDEV), fwd(ANNOTATIONS)] + launch_libraries() + [fwd(os.path.join(MODS, CURIOS))]


# ---------------------------------------------------------------------------
# Building
# ---------------------------------------------------------------------------

def build_vanilla_assets(dst):
    """The client jar without its classes: assets, data and the pack metadata."""
    if os.path.isfile(dst) and os.path.getmtime(dst) >= os.path.getmtime(CLIENT_JAR):
        try:
            with zipfile.ZipFile(dst) as jar:
                if "assets/minecraft/lang/en_us.json" in jar.namelist():
                    return "kept  "
        except zipfile.BadZipFile:
            pass
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    part = dst + ".part"
    with zipfile.ZipFile(CLIENT_JAR) as src, zipfile.ZipFile(part, "w", zipfile.ZIP_DEFLATED) as out:
        for info in src.infolist():
            name = info.filename
            if name.startswith(("assets/", "data/")) or name in ("version.json", "pack.mcmeta", "pack.png"):
                entry = zipfile.ZipInfo(name, info.date_time)
                entry.compress_type = zipfile.ZIP_DEFLATED
                entry.external_attr = info.external_attr
                out.writestr(entry, src.read(info))
    os.replace(part, dst)
    return "built "


def build_stellaris(jar):
    size = os.path.getsize(jar)
    with zipfile.ZipFile(jar) as z:
        files = sum(1 for i in z.infolist() if not i.is_dir())
    mark = {"jar": fwd(jar), "size": size, "files": files}
    if os.path.isfile(STELLARIS_MARK) and os.path.isdir(STELLARIS):
        try:
            if json.load(open(STELLARIS_MARK, encoding="utf-8")) == mark:
                have = sum(len(names) for _, _, names in os.walk(STELLARIS))
                if have == files:
                    return "kept  "
        except ValueError:
            pass
    part = STELLARIS + ".part"
    for stale in (part, STELLARIS):
        if os.path.isdir(stale):
            assert inside_env(stale), stale
            shutil.rmtree(stale)
    with zipfile.ZipFile(jar) as z:
        z.extractall(part)
    os.replace(part, STELLARIS)
    write_text(STELLARIS_MARK, json.dumps(mark, indent=2) + "\n")
    return "unpacked"


def build():
    print(f"building {fwd(ENV)}")
    for folder in (MC, os.path.join(MC, "libs"), os.path.join(MC, "moddev"),
                   MCWIN, os.path.join(MCWIN, "libs"), MODS, REF, SCRATCH):
        os.makedirs(folder, exist_ok=True)
    write_text(os.path.join(ENV, "README.txt"),
               "Built by tools/setup_env.py in Elysium-mono. Re-run it rather than editing\n"
               "anything here; it rebuilds what is missing and verifies the rest.\n"
               "%TEMP%\\mc, mcwin, mods, ref, elysium-classes and elysium-loadtest are\n"
               "junctions to the folders here.\n")

    # The ModDevGradle jar: the Valoria tree's copy if it is still there,
    # otherwise the copy already kept here.
    src = first_file(MODDEV_SOURCES)
    if src:
        print(f"  {place(src, MODDEV)} {fwd(MODDEV)}  <- {fwd(src)}")
    elif os.path.isfile(MODDEV):
        print(f"  kept   {fwd(MODDEV)}  (its Valoria source is gone; this copy is the only one)")
    else:
        problem(f"no {MODDEV_NAME}: rebuild it with `gradlew compileJava` in "
                "C:/ClaudeValoria/Valoria (ModDevGradle writes build/moddev/artifacts) "
                "or point ELYSIUM_MODDEV_JAR at a copy")

    src = first_file(sorted(glob.glob(ANNOTATIONS_SOURCES)))
    if src:
        print(f"  {place(src, ANNOTATIONS)} {fwd(ANNOTATIONS)}  <- {fwd(src)}")
    elif os.path.isfile(ANNOTATIONS):
        print(f"  kept   {fwd(ANNOTATIONS)}  (not in the Gradle cache any more)")
    else:
        problem(f"no {ANNOTATIONS_NAME} in the Gradle cache; any Gradle build of the "
                "Valoria or Tridot port downloads it")

    if os.path.isfile(CLIENT_JAR):
        for folder in (MC, MCWIN):
            dst = os.path.join(folder, "vanilla.jar")
            print(f"  {place(CLIENT_JAR, dst)} {fwd(dst)}  <- {fwd(CLIENT_JAR)}")
        dst = os.path.join(MC, "libs", VANILLA_ASSETS)
        print(f"  {build_vanilla_assets(dst)} {fwd(dst)}  <- assets of {fwd(CLIENT_JAR)}")
    else:
        problem(f"no client jar at {fwd(CLIENT_JAR)}; launch 1.21.1 once from CurseForge")

    for name, rel in HELPERS.items():
        src = first_file([os.path.join(LIBRARIES, rel.replace("/", os.sep), name),
                          os.path.join(STAGE, name)])
        if not src:
            problem(f"no {name} under Install/libraries or curseforge/_stage")
            continue
        print(f"  {place(src, os.path.join(MC, 'libs', name))} mc/libs/{name}  <- {fwd(src)}")
    # mcwin/libs mirrors mc/libs: check_loadtest, check_construct and
    # check_models look for the helpers beside whichever cp.txt they were given.
    for name in sorted(os.listdir(os.path.join(MC, "libs"))):
        src = os.path.join(MC, "libs", name)
        if name.endswith(".jar") and os.path.isfile(src):
            state = place(src, os.path.join(MCWIN, "libs", name))
            if state.strip() != "kept":
                print(f"  {state} mcwin/libs/{name}")

    for name in MOD_JARS:
        src = first_file([os.path.join(d, name) for d in MOD_DIRS])
        note = ""
        if not src:
            src = first_file([os.path.join(d, name) for d in FALLBACK_MOD_DIRS])
            note = "  (from D:, no CurseForge instance has it)"
        if not src:
            if os.path.isfile(os.path.join(MODS, name)):
                print(f"  kept   mods/{name}  (no source left; this copy is the only one)")
            else:
                problem(f"no {name} in any instance's mods folder or on D:")
            continue
        print(f"  {place(src, os.path.join(MODS, name))} mods/{name}  <- {fwd(src)}{note}")

    src = first_file(STELLARIS_SOURCES)
    if src:
        print(f"  {build_stellaris(src)} {fwd(STELLARIS)}  <- {fwd(src)}")
    elif os.path.isdir(STELLARIS) and os.listdir(STELLARIS):
        print(f"  kept   {fwd(STELLARIS)}  (no Stellaris jar left in any instance)")
    else:
        problem(f"no {STELLARIS_NAME} in the Techpack or servertest instance")

    entries = classpath_entries()
    state = write_text(os.path.join(MC, "cp.txt"), ":".join(posix(e) for e in entries) + "\n")
    print(f"  {state} mc/cp.txt     {len(entries)} entries, POSIX form")
    state = write_text(os.path.join(MCWIN, "cp.txt"), ";".join(entries) + "\n")
    print(f"  {state} mcwin/cp.txt  {len(entries)} entries, Windows form")


# ---------------------------------------------------------------------------
# The junctions from the old %TEMP% paths
# ---------------------------------------------------------------------------

def _holds_anything(path):
    """The first file or link under path, or None if it is only empty folders."""
    with os.scandir(path) as listing:
        for entry in listing:
            if entry.is_junction() or entry.is_symlink() or not entry.is_dir(follow_symlinks=False):
                return entry.path
            found = _holds_anything(entry.path)
            if found:
                return found
    return None


def _remove_empty_tree(path):
    for base, dirs, _ in os.walk(path, topdown=False):
        for name in dirs:
            os.rmdir(os.path.join(base, name))
    os.rmdir(path)


def link(name, target, movable=False):
    """Make %TEMP%/name a junction to target.

    movable: the old folder holds build output, so what is in it may be moved
    into target (a rename, same drive) when target is absent or empty.
    """
    path = os.path.join(TEMP, name)
    want = os.path.normcase(os.path.realpath(target))
    if os.path.isjunction(path):
        if os.path.normcase(os.path.realpath(path)) == want:
            print(f"  kept   {fwd(path)} -> {fwd(target)}")
            return
        problem(f"{fwd(path)} is a junction to {fwd(os.path.realpath(path))}, not "
                f"{fwd(target)}; remove it by hand (rmdir, not del) and re-run")
        return
    if os.path.islink(path) or (os.path.exists(path) and not os.path.isdir(path)):
        problem(f"{fwd(path)} exists and is not a folder; left alone")
        return
    if os.path.isdir(path):
        held = _holds_anything(path)
        if held and movable:
            other = _holds_anything(target) if os.path.isdir(target) else None
            if other:
                problem(f"{fwd(path)} and {fwd(target)} both hold files ({fwd(held)}, "
                        f"{fwd(other)}); empty one of them and re-run")
                return
            if os.path.isdir(target):
                assert inside_env(target), target
                _remove_empty_tree(target)
            try:
                os.rename(path, target)
            except OSError as err:
                problem(f"could not move {fwd(path)} to {fwd(target)} ({err}); is a compile "
                        "or a shell using it? Re-run when it is free")
                return
            print(f"  moved  {fwd(path)} -> {fwd(target)}  (the last build's output)")
        elif held:
            problem(f"{fwd(path)} still holds {fwd(held)}; refusing to replace it. Move what "
                    f"is in it into {fwd(target)} (or delete it) and re-run")
            return
        else:
            _remove_empty_tree(path)
            print(f"  removed the empty folder {fwd(path)}")
    if inside_env(target):
        os.makedirs(target, exist_ok=True)
    elif not os.path.isdir(target):
        problem(f"{fwd(target)} does not exist; {fwd(path)} is left unlinked")
        return
    made = subprocess.run(["cmd", "/c", "mklink", "/J", os.path.normpath(path),
                           os.path.normpath(target)], capture_output=True, text=True)
    if made.returncode != 0 or not os.path.isjunction(path):
        problem(f"mklink /J {fwd(path)} failed: {(made.stdout + made.stderr).strip()}")
        return
    print(f"  linked {fwd(path)} -> {fwd(target)}")


def link_all():
    print(f"linking the old paths in {fwd(TEMP)}")
    for name, target, movable in LINKS + [TREE_LINK]:
        link(name, target, movable)


# ---------------------------------------------------------------------------
# The clone's line endings
# ---------------------------------------------------------------------------

def git(*args):
    return subprocess.run(["git", "-C", REPO] + list(args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace")


def line_endings(fix):
    """core.autocrlf false in the clone this script lives in, and no file it turned CRLF."""
    print(f"checking the line endings of {fwd(REPO)}")
    if git("rev-parse", "--git-dir").returncode != 0:
        problem(f"{fwd(REPO)} is not a git checkout; core.autocrlf cannot be checked")
        return
    value = git("config", "--get", "core.autocrlf").stdout.strip()
    if value.lower() == "false":
        print("  ok     core.autocrlf is false")
    elif fix:
        # git status under the old setting, before it changes: the edits that
        # step one of RECHECKOUT has to stash.
        edits = [l for l in git("status", "--porcelain", "--untracked-files=no").stdout.splitlines() if l]
        made = git("config", "--local", "core.autocrlf", "false")
        if made.returncode != 0:
            problem(f"git config --local core.autocrlf false failed: {made.stderr.strip()}")
            return
        print(f"  set    core.autocrlf false in {fwd(REPO)}/.git/config (it was {value or 'unset'})")
        if edits:
            print(f"  note   {len(edits)} uncommitted change(s): commit them, or take step one "
                  "below, before the re-checkout")
    else:
        problem(f"core.autocrlf is {value or 'unset'} here, not false; re-run without --check "
                "to set it in this clone's own config")
    # The option only governs checkouts from now on. Count what an earlier one
    # left CRLF: files the index has as LF (an eol=crlf attribute would excuse one).
    listing = git("ls-files", "--eol")
    if listing.returncode != 0:
        problem(f"git ls-files --eol failed: {listing.stderr.strip()}")
        return
    tracked, crlf = 0, []
    for line in listing.stdout.splitlines():
        info, _, path = line.partition("\t")
        fields = info.split()
        tracked += 1
        if fields[:2] == ["i/lf", "w/crlf"] and "eol=crlf" not in info:
            crlf.append(path)
    if not crlf:
        print(f"  ok     none of the {tracked} tracked files is CRLF on disk where the index has LF")
        return
    problem(f"{len(crlf)} tracked file(s) are CRLF on disk but LF in the index (the first is "
            f"{crlf[0]}). Setting the option does not rewrite them, nor does checkout-index on "
            f"its own. In {fwd(REPO)}, with core.autocrlf false:")
    for command, why in RECHECKOUT:
        print(f"      {command:<42} {why}")
    print("    then re-run this script; `git status` should be as it was before.")


# ---------------------------------------------------------------------------
# Verifying
# ---------------------------------------------------------------------------

def _jar_ok(path, must=()):
    try:
        with zipfile.ZipFile(path) as jar:
            names = set(jar.namelist())
            if not names:
                return "is empty"
            missing = [m for m in must if m not in names]
            if missing:
                return "lacks " + ", ".join(missing)
            # Read one entry through, so a truncated copy fails here.
            jar.read(sorted(names)[0])
    except (OSError, zipfile.BadZipFile) as err:
        return f"does not open ({err})"
    return None


def check_jar(path, label, must=()):
    if not os.path.isfile(path):
        problem(f"{label}: {fwd(path)} is missing")
        return False
    why = _jar_ok(path, must)
    if why:
        problem(f"{label}: {fwd(path)} {why}")
        return False
    return True


def verify():
    print("verifying")
    sys.path.insert(0, HERE)
    import mcpath  # noqa: E402  (the same reader every check uses)

    expected = [mcpath.native(e) for e in classpath_entries()]
    for label, cp in (("mc/cp.txt", os.path.join(MC, "cp.txt")),
                      ("mcwin/cp.txt", os.path.join(MCWIN, "cp.txt"))):
        parts = mcpath.classpath(cp)
        if not parts:
            problem(f"{label} is missing or empty")
            continue
        if [fwd(p) for p in parts] != [fwd(p) for p in expected]:
            problem(f"{label} is not the classpath this script builds ({len(parts)} entries "
                    f"against {len(expected)}); re-run without --check")
        bad = [p for p in parts if _jar_ok(p)]
        for p in bad[:8]:
            problem(f"{label}: entry {fwd(p)} {_jar_ok(p) if os.path.isfile(p) else 'is missing'}")
        if not bad:
            print(f"  ok     {label}: {len(parts)} entries, every one a jar that opens")
    raw = open(os.path.join(MCWIN, "cp.txt"), encoding="utf-8").read() if os.path.isfile(
        os.path.join(MCWIN, "cp.txt")) else ""
    if raw and ("\r" in raw or ":" in raw.replace(":/", "")):
        problem("mcwin/cp.txt is not pure 'C:/...;' form (elysium-lib's validator hands it to javac raw)")

    if check_jar(MODDEV, "ModDevGradle jar", ["net/minecraft/world/damagesource/DamageSources.class",
                                              "net/neoforged/neoforge/common/NeoForge.class"]):
        print(f"  ok     {fwd(MODDEV)}")
    if check_jar(ANNOTATIONS, "annotations", ["org/jetbrains/annotations/Nullable.class"]):
        print(f"  ok     {fwd(ANNOTATIONS)}")
    for folder in (MC, MCWIN):
        jar = os.path.join(folder, "vanilla.jar")
        if check_jar(jar, "vanilla.jar", ["assets/minecraft/lang/en_us.json",
                                          "data/minecraft/recipe/furnace.json"]):
            print(f"  ok     {fwd(jar)}")
        helpers = sum(check_jar(os.path.join(folder, "libs", name), f"{os.path.basename(folder)}/libs")
                      for name in HELPERS)
        assets = os.path.join(folder, "libs", VANILLA_ASSETS)
        if check_jar(assets, f"{os.path.basename(folder)}/libs", ["assets/minecraft/lang/en_us.json"]):
            with zipfile.ZipFile(assets) as jar:
                classes = [n for n in jar.namelist() if n.endswith(".class")]
            if classes:
                problem(f"{fwd(assets)} carries {len(classes)} class file(s); it must be assets only")
            elif helpers == len(HELPERS):
                print(f"  ok     {fwd(os.path.join(folder, 'libs'))}: "
                      f"{len(HELPERS)} helpers and an assets-only {VANILLA_ASSETS}")

    good = sum(check_jar(os.path.join(MODS, name), "mods") for name in MOD_JARS)
    for pattern in COMPILE_GLOBS:
        found = sorted(glob.glob(os.path.join(MODS, pattern)))
        if len(found) != 1:
            problem(f"mods/{pattern} matches {len(found)} jar(s); compile.sh takes the first, "
                    f"so it must be exactly one: {[os.path.basename(f) for f in found]}")
    if good == len(MOD_JARS):
        print(f"  ok     {fwd(MODS)}: {good} jars, one per compile.sh glob")

    files = sum(len(names) for _, _, names in os.walk(STELLARIS)) if os.path.isdir(STELLARIS) else 0
    textures = os.path.join(STELLARIS, "assets", "stellaris", "textures")
    data = os.path.join(STELLARIS, "data", "stellaris")
    if files and os.path.isdir(textures) and os.path.isdir(data):
        print(f"  ok     {fwd(STELLARIS)}: {files} files, assets and data both present")
    else:
        problem(f"{fwd(STELLARIS)} is empty or lacks assets/stellaris/textures and data/stellaris")

    before = len(problems)
    for name, target, _ in LINKS + [TREE_LINK]:
        path = os.path.join(TEMP, name)
        if not (os.path.isjunction(path)
                and os.path.normcase(os.path.realpath(path)) == os.path.normcase(os.path.realpath(target))):
            problem(f"{fwd(path)} is not a junction to {fwd(target)}")
    # And the spellings the tree hard-codes, through C:/tmp and the junctions.
    for old in ("/tmp/mc/cp.txt", "/tmp/mc/annotations-24.1.0.jar", "C:/tmp/mc/libs/vanilla-assets.jar",
                "C:/tmp/mcwin/cp.txt", "C:/tmp/mcwin/libs/log4j-core-2.19.0.jar", "/tmp/mods/" + CURIOS,
                "/tmp/ref/stellaris/assets/stellaris/lang/en_us.json", "/tmp/elysium-classes",
                "/tmp/elysium-loadtest", "/tmp/Elysium-mono/compile.sh"):
        if not os.path.exists(old):
            problem(f"the old path {old} does not resolve")
    if len(problems) == before:
        print("  ok     the old /tmp and C:/tmp paths all resolve through the junctions, "
              "the tree's own (/tmp/Elysium-mono) among them")


def exports():
    python = posix(os.path.dirname(sys.executable))
    jdk = sorted(glob.glob("C:/Program Files/Eclipse Adoptium/jdk-21*/bin"))
    jdk = posix(jdk[-1]) if jdk else "/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot/bin"
    print()
    print("# validators (module validate.py, the root checks, negtests) - Git Bash:")
    print(f'export PATH="{python}:{jdk}:$PATH" JAVA_TOOL_OPTIONS= PYTHONIOENCODING=utf-8')
    print(f"export ELYSIUM_MC_LIBS={fwd(MCWIN)}")
    print(f"export ELYSIUM_ANNOTATIONS={fwd(ANNOTATIONS)}")
    print(f"export MC_JAR={fwd(os.path.join(MC, 'libs', VANILLA_ASSETS))}")
    print(f"export NEOFORGE_JAR={fwd(MODDEV)}")
    print(f"export ELYSIUM_COMPAT_LIBS={fwd(MODS)}")
    print(f"export STELLARIS={fwd(STELLARIS)}")
    print()
    print("# compile.sh - Git Bash, POSIX spellings (compile.sh joins with ':'):")
    print(f'export PATH="{jdk}:{python}:$PATH" JAVA_TOOL_OPTIONS=')
    print(f"export ELYSIUM_MC_LIBS={posix(MC)}")
    print(f"export ELYSIUM_ANNOTATIONS={posix(ANNOTATIONS)}")
    print(f"export ELYSIUM_COMPAT_LIBS={posix(MODS)}")
    print()
    print("# The old spellings (/tmp/mc, C:/tmp/mcwin, /tmp/mods, /tmp/ref/stellaris, and the outputs")
    print("# /tmp/elysium-classes and /tmp/elysium-loadtest) reach the same folders through the junctions.")


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().split("\n")[0])
    parser.add_argument("--check", action="store_true", help="verify only; write nothing")
    args = parser.parse_args()
    if os.name != "nt":
        print("setup_env.py builds the Windows environment; in a Linux container use "
              "tools/classpath/bootstrap.sh")
        return 2
    if not args.check:
        build()
        link_all()
    line_endings(fix=not args.check)
    verify()
    exports()
    if problems:
        print(f"\n{len(problems)} problem(s); the environment is NOT ready")
        return 1
    print("\nthe environment is ready")
    return 0


if __name__ == "__main__":
    sys.exit(main())
