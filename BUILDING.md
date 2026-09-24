# Building Elysium

One repository, seven mods, one command.

**NeoForge 21.1.248 · Minecraft 1.21.1 · JDK 21**

---

## Push it and let CI build it

```bash
unzip elysium.zip -d elysium
cd elysium

# Check this first. If it does not print the path, everything is one level
# too deep and Actions will never run — see "If no workflow runs" below.
ls .github/workflows/build.yml

git init && git add . && git commit -m "Elysium: seven mods, one repo"
git branch -M main
git remote add origin git@github.com:BelialRunnerX/elysium.git
git push -u origin main
```

`.github/workflows/build.yml` runs on every push. It validates every mod,
renders the interface previews, builds everything, and uploads the jars as
a single artifact called **elysium-jars** — at the bottom of the run's summary
page. There is one workflow per repository, not one per mod; a push builds the whole set in dependency
order.

### If no workflow runs

GitHub reads workflows **only** from `.github/workflows/` at the top level of
the repository. It does not search subdirectories, and it does not warn you —
a nested workflow file is simply invisible, which looks exactly like Actions
being switched off.

So if the repo's file list shows a single `elysium` folder rather than
`README.md`, `build.gradle`, `elysium-lib`, … then everything is one level too
deep. Fix it in place:

```bash
cd <your-repo>
mv elysium/* elysium/.[!.]* .    # the dotfiles matter: .github is one of them
rmdir elysium
git add -A
git commit -m "Move project to repo root so Actions can find the workflow"
git push
```

That push triggers the build itself — the workflow is read from the commit
being pushed, so the commit that puts the file in the right place is also the
one that runs it.

Two other things that produce the same silence:

- **Uploading through the GitHub website.** The web uploader refuses to create
  files under `.github/workflows/` without the `workflow` scope, so the rest of
  the repo lands and the workflow quietly does not. Push over git instead.
- **Actions disabled for the repository.** Settings → Actions → General →
  "Allow all actions and reusable workflows".

Once the file is in the right place you can also start a run by hand — the
workflow declares `workflow_dispatch`, so the Actions tab gets a **Run
workflow** button. That is the quickest way to tell "GitHub cannot see my
workflow" apart from "GitHub sees it and the trigger did not match".

## Or build it locally

```bash
chmod +x ./gradlew
./gradlew checkAll
```

`checkAll` is what CI does: validate, build, collect, merge, check the merge.
`dist/` ends up with every mod jar and `elysium-all-<version>.jar`, which is
all of them in one file. Install either the merged jar or the individual ones, never both — the same
mod id twice is a duplicate-mod error on startup.

### Without Gradle

Gradle needs to reach the NeoForge repositories, and not every environment can.
`./compile.sh --jars` type-checks every module against a real NeoForge
classpath and produces the same seven jars in `dist/`, given a directory holding
the Minecraft and NeoForge jars plus a `cp.txt` naming them (`ELYSIUM_MC_LIBS`,
default `/tmp/mc`).

It is not a substitute for the Gradle build — no reobfuscation, no dependency
resolution, no data generation — and it says so. It packages what is already
there, which is everything the merged jar needs.

### Curios inside the jar (`--bundle`)

`elysium-trinkets` requires Curios: forty accessories with nowhere to put them
is not a degraded mod, it is a broken one. So a player who downloads one
Elysium jar and drops it in their mods folder gets a load error rather than a
game, unless Curios travels with it.

`./compile.sh --bundle` does that. It puts the Curios jar, unmodified, inside
`META-INF/jarjar/` of the merged jar, writes the index NeoForge reads, and
copies Curios' own licence files and a pointer to its source into
`licences/curios/`. Curios is LGPL-3.0-or-later; shipping it without its
licence text is the one mistake here that is not merely a bug, so
`tools/checkbundle.py` fails the build if any of it is missing, and that
checker runs on every packaged build whether or not anything was bundled.

It is **opt-in, and off by default**, for one reason: an installed copy of a
mod always wins over an embedded one, so bundling can never override what a
player already has — but a dev instance that already has Curios installed does
not need every working build to carry a second copy of it. Release builds pass
`--bundle`; the working loop does not.

The version range in the index is read from our own `neoforge.mods.toml`
rather than chosen in the packager, because the two numbers have to agree: a
bundled jar outside the range its dependent declares is a jar the loader
unpacks and then refuses anyway.

The first run downloads NeoForge and decompiles Minecraft — ten to twenty
minutes, and a few GB in `~/.gradle`. After that it is fast. If it runs out of
heap, raise `org.gradle.jvmargs` in the root `gradle.properties`; it is set to
3G because a composite build can have more than one decompile in flight.

## What has been verified, and what has not

Everything type-checks against a stub tree reproducing the NeoForge and
Minecraft signatures each file uses, and every validator and harness passes:

| Check | What it proves |
|---|---|
| Stub compile harness | All five source sets compile, with cross-mod classpaths withheld. Not shipped in this repo — it is a scaffold that exists only because a real Gradle build could not be run, and `./gradlew buildAll` supersedes it entirely once it goes green. |
| `elysium-lib/validate.py` | Nothing in the library refers to a mod built on it; element ring closed; twelve stats; every lang key present; `ElysiumPalette.java` matches its generator |
| `elysium-core/validate.py` | Every material gear item (nine tool shapes and four armour pieces per material) with a recipe, a model and a texture; shapes read out of the Shape enum rather than listed; every item obtainable or explicitly excused |
| `elysium-dungeons/validate.py` | Nine room types across four kinds; blocks, items and JSON all present |
| `elysium-trinkets/validate.py` | Forty trinkets: model, texture and lang each; slot registered, granted and tagged; crafted has a recipe, found has a loot table, neither has both |
| `elysium-npcs/validate.py` | Five envoys: skin, writ and lang each; both standing meters covered; every `Regalia` value wired to a model part; no import of elysium-core |
| `elysium-solarpunk/validate.py` | Thirty-three blocks: blockstate, models, textures, loot, recipe, lang and mineable tag each; render type matched to the texture's alpha; no import of any other Elysium mod; the eight unfinished machines described identically in `MACHINES.md`, in `block/pending/` and on the tooltip |
| `elysium-armory/validate.py` | No reference to Silent Gear in code or mods.toml, imports limited to the library and itself; the property engine compiles on the JDK alone and `PropertyHarness` passes (averaging, operation order, floors, no ceilings) |
| `elysium-ascendancy/validate.py` | No reference to Apotheosis, Apothic or Placebo, imports limited to the library and itself; the weighting engine compiles on the JDK alone and `WeightHarness` passes (tier gaps weigh nothing, luck buys quality, exact shares over 100000 rolls) |
| `elysium-mobs/validate.py` | Eight entity types with model, renderer, attributes and spawn placement; both factions have families and a boss |
| Layout harness | 400 generated dungeons: every room placed, boss always reachable, 400 distinct layouts |
| `elysium-lib/ui/screens.py` | Every screen laid out at 427x240 and 320x240; fails if a panel does not fit |
| Composite wiring | The root scripts were run against stand-in subprojects: `validateAll`, `buildAll`, `collectJars` (including its "did I get every jar" assertion, checked by removing one), and the standalone `publish` → sibling `build` fallback |

### The first real build

It ran. All four projects configured at the time, NeoGradle decompiled Minecraft, and the
composite reached `compileJava` in about 17 minutes on a cold cache.

It produced **two errors in the whole project, both the same one**:
`VanillaGuiLayers` was imported from `net.minecraft.client.gui` when it is
`net.neoforged.neoforge.client.gui` — a NeoForge class, not a Minecraft one,
sitting in a file next to `LayeredDraw` and `DeltaTracker`, which genuinely are
vanilla. Fixed; see `elysium-lib/FIXES.md` §18 for why the stub tree could not
have caught it, and why a real build is the authority from here on.

Everything else in the library compiled clean on the first attempt. The three
content mods had not yet been reached when the library failed, so their first
compile is still ahead — their use of `AbstractContainerScreen` was checked
against the 1.21.x javadoc in the meantime, but checked is not compiled.

A stub tree answers only "is this name in this package"; what it cannot answer
is whether the package is real, because the stub tree is the world it checks
against. Expect any remaining problems to be of that shape.

## Two bugs the packaging step found

Worth knowing about, because both were invisible to every check that came
before and both would have failed a first build:

**The library published under the wrong name.** Gradle takes a publication's
`artifactId` from the *Gradle project name*, not from `base.archivesName` — so
`elysium-lib` published as `com.elysium:elysium-lib` while all three content
mods asked for `com.elysium:elysiumlib`. The sibling-checkout flow the READMEs
document would not have resolved. All of them now state their coordinates
explicitly. (Confirmed by publishing to a file repository and reading the path,
not by reasoning about it.)

**`collectJars` broke the configuration cache.** Its `doLast` reached back for
script-level variables, which Gradle refuses with "Cannot reference a Gradle
script object from a Groovy closure". Everything the action needs is now
captured at configuration time.

## Two warnings left alone

`@EventBusSubscriber(bus = Bus.MOD)` and
`DeferredRegister.createDataComponents(String)` are both deprecated in
21.1.248. Both still work, and both replacements change registration order,
which is not a thing to alter while the build itself is unverified. Once there
is one confirmed green build to compare against, they are a five-minute change.
