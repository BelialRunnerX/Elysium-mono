# Building Elysium

One repository, six mods, one command.

**NeoForge 21.1.248 · Minecraft 1.21.1 · JDK 21**

---

## Push it and let CI build it

```bash
unzip elysium.zip -d elysium
cd elysium

# Check this first. If it does not print the path, everything is one level
# too deep and Actions will never run — see "If no workflow runs" below.
ls .github/workflows/build.yml

git init && git add . && git commit -m "Elysium: six mods, one repo"
git branch -M main
git remote add origin git@github.com:BelialRunnerX/elysium.git
git push -u origin main
```

`.github/workflows/build.yml` runs on every push. It validates all six mods,
renders the interface previews, builds everything, and uploads the six jars as
a single artifact called **elysium-jars** — at the bottom of the run's summary
page. There is one workflow, not six; a push builds the whole set in dependency
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

`checkAll` is what CI does: validate, build, collect. The jars end up in
`dist/`, and those six files are exactly what goes in a `mods` folder.

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
| `elysium-core/validate.py` | 196 material gear items with recipes; every item obtainable or explicitly excused; no orphan GUI textures |
| `elysium-dungeons/validate.py` | Nine room types across four kinds; blocks, items and JSON all present |
| `elysium-trinkets/validate.py` | Forty trinkets: model, texture and lang each; slot registered, granted and tagged; crafted has a recipe, found has a loot table, neither has both |
| `elysium-npcs/validate.py` | Five envoys: skin, writ and lang each; both standing meters covered; every `Regalia` value wired to a model part; no import of elysium-core |
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
