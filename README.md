# Elysium

**NeoForge 21.1.248 · Minecraft 1.21.1 · Java 21**

Seven mods in one repository. Push once, and CI builds every one of them and hands you
the jars.

```
elysium/
├── elysium-lib/          the engine — registries, stats, standing, the interface toolkit
├── elysium-core/         the content — gear, materials, races, classes, runes
├── elysium-mobs/         thirty creatures across two factions, and a boss for each
├── elysium-dungeons/     the portal, the dimension, and a dungeon that regenerates
├── elysium-trinkets/     forty accessories in Curios slots: 24 found, 16 crafted
├── elysium-npcs/         the court — five named envoys who trade rather than fight
├── elysium-solarpunk/    a building set for the other future, and eight machines that do not work yet
├── elysium-armory/       our own part-built gear, in place of Silent Gear
└── elysium-ascendancy/   our own affixes, gems and loot tiers, in place of Apotheosis
```

Every content mod depends only on `elysium-lib` — never on each other — and
`elysium-solarpunk` does not even depend on that: it is blocks, it registers
into nothing, and it installs on its own.
`elysium-trinkets` additionally requires Curios, which is the one place any of
them names a third-party mod. That is enforced, not just intended: the
compile harness builds them with the others deliberately kept off the
classpath, so a cross-mod reference fails at build time rather than at
somebody's load time.

## Your existing gear already counts

There is no conversion step and nothing to feed through a machine. Any weapon,
tool or piece of armour — vanilla, Elysium's own, or another mod's — is Elysium
gear: it sockets runes, takes reforges, ascends, grants stats, and fights inside
the elemental matrix.

It is not admitted as an inert lump, either. Elysium already had an opinion
about what the metals are, and now applies it to everything made of them:

| Made of | Reads as | Aligned rune |
| --- | --- | --- |
| copper | Plasma | Plasmaforge |
| iron | Kinetic | Kineticsurge |
| gold | Neural | Neuralspike |
| diamond | Dimensional | Dimensionalshift |
| netherite | Void | Voidward |

So a plain iron sword is Kinetic at iron's tier, and a Kineticsurge rune in it
is worth 1.75× what the same rune is worth anywhere else. A material Elysium has
never heard of is still gear — just inert, at tier zero, which is a state the
stat system already handled.

What an item is made of is decided by **what repairs it**, which is the game's
own question and one other mods answer because anvils need it. Ascending keeps
the item: an enchanted, named diamond sword comes out of the forge still
enchanted, still named, a tier higher. See
`elysium-lib/src/main/java/com/elysium/lib/item/ElysiumGear.java`.

---

## Build everything

```bash
./gradlew checkAll
```

That runs every validator, builds every mod, gathers the jars into
`dist/`, and merges them into one.

`dist/` ends up with seven files:

```
dist/
  elysium-all-1.0.0.jar     <- everything, one file
  elysiumlib-1.0.0.jar      <- or install these individually
  elysium-1.0.0.jar
  elysiumdungeons-1.0.0.jar
  elysiummobs-1.0.0.jar
  elysiumnpcs-1.0.0.jar
  elysiumtrinkets-1.0.0.jar
  elysiumsolarpunk-1.0.0.jar
  elysiumarmory-1.0.0.jar
  elysiumascendancy-1.0.0.jar
```

**Install either the merged jar or the individual ones, never both** — the same mod id twice is a
duplicate-mod error on startup.

Other entry points:

| Task | What it does |
|---|---|
| `./gradlew validateAll` | Every mod's `validate.py`. Seconds, and it fails on things Gradle never looks at. Run it first. |
| `./gradlew buildAll` | Builds every mod. Jars land in each mod's `build/libs`. |
| `./gradlew collectJars` | `buildAll`, then copies every jar into `dist/`. |
| `./gradlew mergeJar` | `collectJars`, then merges them into `dist/elysium-all-<version>.jar`. |
| `./gradlew checkMergedJar` | `mergeJar`, then proves the merged jar declares and contains every mod. |
| `./gradlew publishAll` | Publishes every mod to `~/.m2`, for consumers outside this repo. |
| `./gradlew cleanAll` | Cleans every build. |

## The merged jar

NeoForge reads every `[[mods]]` block in `META-INF/neoforge.mods.toml` and finds
each mod's `@Mod` class by scanning the jar, so one file can carry all of them.

**The mod ids do not change.** `elysiumlib`, `elysium`, `elysiumdungeons`,
`elysiummobs`, `elysiumnpcs`, `elysiumtrinkets`, `elysiumsolarpunk`,
`elysiumarmory` and `elysiumascendancy` are all
still there, still
separate, still loaded in dependency order. Merging changes how Elysium is
*installed* and nothing about what it registers, so existing worlds carry over
untouched — which would not be true of renaming the ids, since that renames
every registry entry, attachment, lang key, texture path, recipe and loot table
with them.

Merging is not concatenation, and the reason is worth knowing before anyone adds
a file. Seven jars are seven resource packs and the game merges data packs across
them; one jar is one pack, and a path holds one file. Today exactly one path is
written by two modules — `data/minecraft/tags/block/mineable/pickaxe.json`, from
core and dungeons — and `tools/mergejar.py` unions it, which is what the game
would have computed. Any other collision is a hard failure rather than a last
writer winning, because the symptom otherwise is not a crash: it is nine Elysium
blocks quietly no longer dropping anything when mined.

`python3 check_merge.py` asks the same question at source level in under a
second, and runs as part of `validate.py`, so a colliding file is caught by
whoever adds it.

The first build downloads NeoForge and decompiles Minecraft. Expect ten to
twenty minutes and a few GB in `~/.gradle`. Everything after that is fast.

## Build one mod on its own

Each directory is still a complete, standalone Gradle build with its own
wrapper, and behaves exactly as it did when it was its own repository:

```bash
cd elysium-lib
./gradlew build
```

For a content mod on its own, the library has to exist as a jar first:

```bash
cd elysium-lib   && ./gradlew publish        # writes elysium-lib/repo
cd ../elysium-core && ./gradlew build          # resolves it from there
```

You do not need to do this for `./gradlew buildAll` at the root — see below.

---

## How the builds are joined

This is a Gradle **composite** build (`includeBuild`), not a multi-project one
(`include`). Each mod keeps its own `settings.gradle`, `gradle.properties` and
wrapper, and the root adds a coordination layer on top.

That choice is deliberate. NeoGradle's behaviour inside a shared multi-project
build is not something the NeoForge documentation covers, and a first build of
this project has never run against the real NeoForge artifacts. A coordination
layer you can step around — `cd elysium-lib && ./gradlew build` — is much safer
than a restructure you cannot.

The root `settings.gradle` substitutes the library dependency to source:

```groovy
includeBuild('elysium-lib') {
    dependencySubstitution {
        substitute module('com.elysium:elysiumlib') using project(':')
    }
}
```

So from the root, the three content mods compile against the library's
**sources**. Nothing has to be published first and there is no stale-jar
failure mode. The substitution is written out rather than left to Gradle's
automatic matching, because automatic matching keys on the Gradle project name
— `elysium-lib` — while every dependent asks for the coordinate `elysiumlib`,
so it silently would not fire.

## Versions

All seven are at `1.0.0`, against NeoForge 21.1.248 for Minecraft 1.21.1. Each
mod's ids and version live in its own `gradle.properties`; the root's holds
only daemon settings.

A mod id is the persistence key for everything it registers, so ids are
permanent once anyone has played them. The content mods declare
`versionRange="[1.0.0,)"` against the library and keep working across minor
library releases.

---

See **[BUILDING.md](BUILDING.md)** for what has and has not been verified, and
**[elysium-lib/EXTENDING.md](elysium-lib/EXTENDING.md)** for writing a mod
against the library.
