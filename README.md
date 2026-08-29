# Elysium

**NeoForge 21.1.248 · Minecraft 1.21.1 · Java 21**

Six mods in one repository. Push once, and CI builds all six and hands you
the jars.

```
elysium/
├── elysium-lib/          the engine — registries, stats, standing, the interface toolkit
├── elysium-core/         the content — gear, materials, races, classes, runes
├── elysium-mobs/         thirty creatures across two factions, and a boss for each
├── elysium-dungeons/     the portal, the dimension, and a dungeon that regenerates
├── elysium-trinkets/     forty accessories in Curios slots: 24 found, 16 crafted
└── elysium-npcs/         the court — five named envoys who trade rather than fight
```

Every content mod depends only on `elysium-lib` — never on each other.
`elysium-trinkets` additionally requires Curios, which is the one place any of
them names a third-party mod. That is enforced, not just intended: the
compile harness builds them with the others deliberately kept off the
classpath, so a cross-mod reference fails at build time rather than at
somebody's load time.

---

## Build everything

```bash
./gradlew checkAll
```

That runs all six validators, builds all six mods, and gathers the jars into
`dist/`. The six files there are exactly what goes in a `mods` folder.

Other entry points:

| Task | What it does |
|---|---|
| `./gradlew validateAll` | Every mod's `validate.py`. Seconds, and it fails on things Gradle never looks at. Run it first. |
| `./gradlew buildAll` | Builds all six. Jars land in each mod's `build/libs`. |
| `./gradlew collectJars` | `buildAll`, then copies the jars into `dist/`. Fails if it does not end up with six. |
| `./gradlew publishAll` | Publishes all six to `~/.m2`, for consumers outside this repo. |
| `./gradlew cleanAll` | Cleans all six. |

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

## How the six builds are joined

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

All six are at `1.0.0`, against NeoForge 21.1.248 for Minecraft 1.21.1. Each
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
