# The NeoForge classpath, from nothing

`compile.sh --jars` type-checks all twenty-nine modules against a real NeoForge
classpath rather than against Gradle, which needs to reach repositories that not
every environment can. What it needs is a directory — `$ELYSIUM_MC_LIBS`,
default `/tmp/mc` — holding the jars and a `cp.txt` naming them.

    bash tools/classpath/bootstrap.sh [<neoforge version>] [<minecraft>]

## Why this exists

Because the working container is disposable, and when it is disposed the
classpath goes with it. Rebuilding it from memory took an hour and four wrong
turns, and each wrong turn produced a tree that looked *nearly* right:

1. **No DataFixerUpper.** NeoForge's launcher manifest lists only NeoForge's own
   libraries and inherits the rest from the vanilla manifest. Fetch one and not
   the other and twenty-eight modules build while `elysium-colonies` reports a
   hundred errors about `Codec`.

2. **No access transformers.** A dev environment's Minecraft is not vanilla
   Minecraft: NeoForge widens a few hundred members before a mod ever compiles
   against it. Skip that and `elysium-lib` reports that
   `DamageSources.source(ResourceKey)` is private — which is true of the jar and
   false of every dev environment, and reads as a bug in the module.

3. **NeoForge's data winning over vanilla's.** The order of `cp.txt` matters
   twice and in opposite directions. NeoForge's *classes* have to win over
   vanilla's, and vanilla's *data* has to win over NeoForge's, because several
   validators read the game's own recipes off the first jar on the path that
   carries any and NeoForge's carries a hundred and ninety of its own. Get the
   second one wrong and two modules report that every stone in the game has
   stopped being smeltable.

4. **No `vanilla.jar`.** Four module validators check names against the game's
   own language file — every dye, every mob, every item — and each looks for
   `$MC/vanilla.jar`. Without it they return an empty set and skip, **silently**:
   the summary line still says the module passed. Two negative tests exist purely
   to catch that, and they are the only reason it was noticed at all.

Every one of those four is the same shape of failure: a build that reports
success while a check is not running, or reports a failure that belongs to the
classpath rather than to the code. That is why this is a script and not a
paragraph of instructions.

## What it cannot fetch

Three jars. The patched Minecraft classes and NeoForge's own are produced
locally by the NeoForge installer, and are not on any public maven:

    net/minecraft/client/<mc>-*/client-<mc>-*-srg.jar   -> libs/mc-srg.jar
    net/minecraft/client/<mc>-*/client-<mc>-*-extra.jar -> libs/mc-extra.jar
    net/neoforged/neoforge/<v>/neoforge-<v>-universal.jar -> libs/neoforge-universal.jar
    net/neoforged/neoforge/<v>/neoforge-<v>-client.jar    -> libs/neoforge-client.jar

They live under `<curseforge>/minecraft/Install/libraries` on a machine with the
pack installed. The script names them and stops rather than building half a
classpath.

## And the mods the tree compiles against

`elysium-trinkets` is written against Curios and `elysium-core/compat/` against
Silent Gear, Apotheosis and the colony mods. Curios belongs on `cp.txt`; the
rest go in `$ELYSIUM_COMPAT_LIBS` (default `/tmp/mods`). `compile.sh` fails
rather than skipping when they are absent, which is deliberate — quietly
dropping a package produces a jar that is missing a feature with nothing in the
output saying so.

## A note for whoever owns the root docs

`BUILDING.md` describes `$ELYSIUM_MC_LIBS` and does not mention this script.
One line there pointing at it would be the natural place for somebody to find
it, and this file exists under `tools/` instead because `BUILDING.md` is outside
what the board's `shared` unit covers.
