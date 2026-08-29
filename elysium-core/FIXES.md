# Port and fix log

The project was written against **Forge 1.20.x** APIs while declaring
**NeoForge 1.21.1**. Nothing in `src/main/java` compiled. This is what changed.

---

## 1. Hard compile failures

| Where | Problem |
|---|---|
| `Elysium.java` | `ForgeRegistries`, `RegistryObject`, `MinecraftForge.EVENT_BUS`, `FMLJavaModLoadingContext` — none exist on NeoForge. Replaced with `BuiltInRegistries`, `DeferredHolder`, and the `IEventBus` mod constructor. |
| `Elysium.java` | `FMLLoader.get().isLoaded(...)` is not a method. Now `ModList.get().isLoaded(...)`. |
| `Elysium.java` | Referenced `REFORGE_TABLE_MENU` and `REFORGE_TABLE_BLOCK_ENTITY`, neither of which was ever declared. Both are now registered. |
| `ElysiumAffix`, `ElysiumPsionicAffix`, `ElysiumAffixes` | Imported `shadows.apotheosis.*`, which was not a dependency in `build.gradle` — unbuildable with or without Apotheosis installed. Rewritten as a self-contained affix system. |
| `ElysiumSilentGear`, `ElysiumLegendaryTooltips` | Called `ModList` while importing `FMLLoader`. Import fixed. |
| `ReforgeTableMenu` | Imported `net.minecraftforge.items.*`. NeoForge's package is `net.neoforged.neoforge.items.*`. |
| `ElysiumArmorMaterials`, `NeutroniumArmorMaterials` | Implemented `ArmorMaterial` as an interface. In 1.21.1 it is a **registered record**. Both are now registered materials referenced through a `Holder`. |
| `ElysiumRarities` | `Rarity.create(...)` was a Forge extension that no longer exists; `Rarity` is a plain four-value enum. Tiers now map to vanilla rarities, with the tier itself shown in the tooltip. |
| `ElysiumArmorItem`, `ElysiumReforgeItem` | Overrode `getRarity(ItemStack)`, removed in 1.20.5. Rarity is set via `Item.Properties#rarity`. |
| `ElysiumArmorItem`, `ElysiumReforgeHandler`, `ElysiumArmorAscension` | Used `stack.getTag()` / `getOrCreateTag()` / `setTag()` / `hasTag()`, all removed in 1.20.5. Replaced by a registered data component. |
| `ElysiumArmorItem`, `ElysiumPsionicAffix` | `new AttributeModifier(UUID, String, double, Operation)` and `Operation.ADDITION` / `MULTIPLY_BASE`. 1.21 uses `(ResourceLocation, double, Operation)` with `ADD_VALUE` / `ADD_MULTIPLIED_BASE`. |
| `ReforgeTableScreen`, `ElysiumRecipeProvider` | `new ResourceLocation(...)` is private in 1.21. Now `ResourceLocation.fromNamespaceAndPath(...)`. |
| `ElysiumRecipeProvider` | `RecipeProvider(DataGenerator)` and `buildRecipes(Consumer<FinishedRecipe>)` are both 1.20 signatures. Now `(PackOutput, CompletableFuture<HolderLookup.Provider>)` and `buildRecipes(RecipeOutput)`, with the `RecipeCategory` argument the builders have required since 1.19. |
| `ElysiumBlockStateProvider` | Constructor took a `DataGenerator`; 1.21.1 takes a `PackOutput`. |
| `DataGenerators` | `@Mod.EventBusSubscriber` moved to the top-level `net.neoforged.fml.common.EventBusSubscriber`. |
| `ReforgeTableBlockEntity` | `saveAdditional(CompoundTag)` / `load(CompoundTag)` now take a `HolderLookup.Provider`. |
| `Elysium.java` | `event.accept(DEFERRED_HOLDER)` does not compile — the creative tab event takes an `ItemLike`, and a plain `DeferredHolder` is not one. Now resolves with `.get()`. |

## 2. Logic bugs that would have shipped broken

- **Reforge bonuses stacked without limit.** `applyReforgedStats` added transient
  attribute modifiers with `UUID.randomUUID()` on every armour tick, so a worn
  piece kept piling on fresh copies forever. Bonuses are now part of the item's
  attribute modifiers, computed from the stack.
- **`onArmorTick` never ran.** NeoForge removed it in 1.21. The rune effects it
  drove are now applied from a `PlayerTickEvent.Post` handler.
- **Reforging could crash.** `RANDOM.nextInt(finalPoints / 3)` throws when the
  argument is 0, reachable at low tier with a sub-1.0 grade multiplier. Guarded.
- **Reforge quality read the wrong thing.** `getRarityMultiplier` used
  `Rarity#ordinal()`, which silently became meaningless once the custom rarities
  were gone. It now reads the tier.
- **Ascension dropped data.** It rebuilt a bare stack and re-attached a copied
  tag, losing anything not explicitly carried across. It now copies the stack.
- **The Reforge Table could never open.** `ReforgeTableBlock` extended plain
  `Block`, so its block entity was never created and there was no interaction
  handler. It now implements `EntityBlock` and opens the menu.
- **No screen was registered.** Opening the menu server-side with no client
  screen registered disconnects the player. Registered via
  `RegisterMenuScreensEvent`.
- **The Reforge button did nothing** except close the screen. It now sends a
  container button click, handled in `clickMenuButton`.
- **The workstation lost its contents.** The `ItemStackHandler` lived on the
  menu, so anything left in the table vanished when the screen closed. It now
  lives on the block entity and is saved to NBT.
- **`quickMoveStack` returned `ItemStack.EMPTY` unconditionally**, which
  desyncs the client and server on shift-click. Implemented properly.
- **Five blocks were never registered** — `AetheriumOreBlock`,
  `VoidglassOreBlock`, `ReforgeTableBlock`, `RuneSocketTableBlock`,
  `AscensionForgeBlock`. All seven blocks are now registered with items, models,
  loot tables and recipes.
- **`NeutroniumArmorMaterials` was dead code** — defined but never used, while
  the recipes and loot tables referenced a neutronium armour set that did not
  exist. The set is now registered.
- **`aetherium_ingot` and `voidglass_ingot` did not exist** but were the drops
  of two ore loot tables. Both are now items.

## 3. Resources

- **Data pack folders were pre-1.21.** `recipes/` → `recipe/`,
  `loot_tables/` → `loot_table/`, `tags/blocks/` → `tags/block/`. Under the old
  names nothing loads at all.
- **Recipe JSON was pre-1.21.** Results use `"id"`, not `"item"`; ingredients are
  bare id strings.
- **Recipes pointed at items that did not exist** (`elysium:neutronium_helmet`
  and friends) — a datapack error on load. All recipes now resolve.
- **Rune-socket and ascension crafting recipes were removed.** A crafting grid
  cannot preserve item components: the player fed in a socketed, reforged piece
  and got a blank one back. Both operations live on the workstation blocks, where
  the data survives.
- **Every `placed_feature` was malformed** — they restated the ore config inline
  and omitted the required `feature` field, so all three failed to parse.
- **No biome modifiers existed**, so even a valid placed feature would never
  have been added to a biome. None of the ores generated. Added under
  `data/elysium/neoforge/biome_modifier/`.
- **No `mineable/pickaxe` tag.** Every block called `requiresCorrectToolForDrops()`
  but sat in no mineable tag, so none of them dropped anything. Added, along with
  tiered `needs_*_tool` tags.
- **The lang file was the untouched MDK template** — "Example Block", "Example
  Item", and config keys for a config that does not exist. Not one real item was
  named. Rewritten.
- **Six item models were missing** (the catalyst, the crown and four runes), so
  those items rendered as the missing-model cube.
- **Both `mods.toml` and `neoforge.mods.toml` were present.** NeoForge reads
  `neoforge.mods.toml`; the real metadata was sitting in the ignored one, while
  the one that counts still said "Example mod description". The stale file is
  deleted and the metadata moved across.
- **`loaderVersion="[52,)"`** in `mods.toml` is not an FML version and would have
  refused to load. Now `[1,)`.

## 4. Textures

Every texture in the project was a **16×16 square of one flat colour with no
alpha channel** — items would have rendered as solid blocks. On top of that, the
two armour layer textures were 16×16; they must be 64×32 to map onto the player
model, so worn armour would have rendered as garbage.

The whole set is now drawn to a defined art direction — see `TEXTURES.md`,
which is a standing objective rather than a one-off pass. 30 textures, no
placeholders, generated reproducibly from `tools/textures/`.

- Item sprites are shaded silhouettes with real transparency and a near-black
  outline, one focal emissive accent each.
- Rune sigils are *carved* — a dark recess cut into the tablet with a lit line
  along the bottom of the cut — rather than a glow painted on top.
- Armour layers are painted region by region against the humanoid UV map:
  helmet on the head box, chestplate on the body and arm boxes, boots on the
  lower leg box in layer 1, leggings on the leg box plus the waist in layer 2.
  `tools/textures/preview.py` composites the front faces onto a player figure
  so this can actually be checked.
- Ore blocks are crystal veins bedded into a clustered stone matrix, not
  per-pixel static.
- Added: neutronium armour layers, three workstation block textures, two
  ingots, four armour icons, and the workstation GUI — which the screen
  referenced but which never existed.

## 5. Build

- **`processResources` token expansion was missing.** `neoforge.mods.toml` is
  full of `${mod_id}`-style placeholders and nothing expanded them, so the jar
  would have shipped the literal text and failed to load.
- **`settings.gradle` had no `rootProject.name`** and no foojay toolchain
  resolver, so Gradle could not auto-provision a JDK 21.
- **`libs/parchment-1.21.1-….zip` plus a `flatDir` repository** is not how
  NeoGradle consumes Parchment. Removed; the (commented) `neogradle.subsystems`
  properties are the real switch.
- **`mavenLocal()`** removed from the repositories.
- Added run configurations (`runClient` / `runServer` / `runData`), a
  `localRuntime` configuration, `duplicatesStrategy` so `runData` cannot break a
  later `build`, and raised the Gradle heap from 1G to 2G.
- **CI now uploads the jar.** The workflow only ran `./gradlew build` and threw
  the output away. It now attaches `elysium-jar` to the run, plus build reports
  on failure, and can be triggered by hand.

## 5b. Area tools and rune alignment

Added after the first pass, and it exposed two real bugs in the existing code.

- **`ElysiumAffix.createModifier` produced colliding modifier ids.** Modifiers
  are keyed by `ResourceLocation` in 1.21, not a random UUID. Two armour pieces
  carrying the same rune therefore produced two modifiers with identical ids on
  the same attribute, and only one of them survived — a four-piece set was
  quietly giving one piece's worth of rune bonus. The id now carries the
  `EquipmentSlotGroup`. The same fix applies to the three reforge modifiers.
- **Socketing was armour-only**, which made rune alignment a half-idea: a
  Voidglass hammer with a Void affinity had nothing to align *with*. Sockets
  now live on one `ElysiumSocketable` interface implemented by armour, weapons
  and tools alike, backed by shared logic in `ElysiumSockets`. The Rune Socket
  Table's first slot accepts any of them; reforging and ascension stay
  armour-only and simply do nothing for the rest.

New behaviour:

- `ElysiumAreaBreak` — a 3×3 in the plane of the struck face, and a bounded
  flood fill up a tree (192 logs, searching `dy 0..1` so a fell takes the tree
  rather than tunnelling into the forest floor). Both are guarded against
  re-entry.
- Durability for extra blocks is spent by hand rather than through
  `hurtAndBreak`, whose signature could not be verified against this exact
  build; the tool refuses its last point rather than snapping mid-swing. The
  tradeoff is that Unbreaking does not apply to the extra blocks — swap the
  helper in once the signature can be checked against the real jar.
- The four tool classes extend the concrete `PickaxeItem` / `AxeItem` /
  `ShovelItem` / `HoeItem` rather than `DiggerItem`, and build their attack
  attributes from `ItemAttributeModifiers.builder()` rather than the vanilla
  `createAttributes` helpers — both choices made because those signatures could
  not be verified this session, and both restricted to API this project has
  already checked.
- Held Elysium gear now counts in the counter matrix and toward utility runes,
  so a hammer is a weapon in every system, not only in its damage number.

## 5c. Obtainability

An audit of whether the mod can actually be played without creative mode. It
found one real hole and one soft-lock.

- **Five armour pieces had no survival source.** The Elysium Helm, Plasma
  Carapace, Neural Leggings, Dimensional Boots and Emperor's Crown had no
  recipe and appeared in no loot table. Every one of them was registered,
  rendered, socketable, reforgeable and ascendable — which meant the entire
  defensive half of the counter matrix, plus reforging and ascension, was
  creative-only, because all three systems operate on Elysium armour. The four
  elemental pieces now craft from their material plus the rune of their
  element, the same bargain the weapons make. The Crown drops from a named
  Imperial Enforcer at 4%, and only at Hunted.
- **Favor could not be climbed out of.** Decay took a point every two minutes
  from both meters including at the bottom of the range, while an incidental
  kill was worth one point one time in five. That is a net loss at any
  realistic pace, so the first rung — Recognised, which is what turns on faction
  spawns and the loot table — was unreachable outside a mob farm. Decay now
  stops at the notice threshold, and an incidental kill pays one time in three.
  Above notice nothing changes: standing is still something you hold by
  continuing to act.

`validate.py` now proves the property rather than trusting it. It starts from
what the world gives you — ores that generate, block loot, mob loot, and the
rewards the standing handler hands out in code — then closes over the recipe
graph until nothing new appears. Anything unreached fails the build, and a
circular recipe fails it too, since a cycle simply never becomes reachable.
Two spawn eggs are declared creative-only by name; everything else must earn
its place.

Two paths deliberately cross so neither meter can dead-end the other:
Neutronium ore needs a netherite pickaxe, but Neutronium ingots also come off
Enforcers and out of the Favor table; and Suspicion bootstraps from mining
Voidglass or Aetherium, which need only iron, so you never need an Empire mob
in order to anger the Empire.

## 5d. Characters: stats, races, classes

The largest addition so far, and the one that most needed the API checked
rather than guessed. Everything client-facing here was verified against the
1.21.1 javadocs and the NeoForge networking documentation before a line of it
was written: `CustomPacketPayload.Type` and `StreamCodec.composite`,
`PayloadRegistrar#playToClient`/`playToServer`, `IPayloadContext`,
`PacketDistributor.sendToPlayer`, `PlayerEvent.PlayerLoggedInEvent`,
`Screen#init`/`render`/`addRenderableWidget`, `Button.Builder#bounds`,
`GuiGraphics#drawString`/`drawCenteredString`/`drawWordWrap`/`fill`, and
`AttributeInstance#addOrUpdateTransientModifier`/`removeModifier`.

Decisions worth recording:

- **Every proportional stat reads through `v/(v+K)`.** Reforging and ascension
  are meant to climb forever, so a percentage stat must accept any input
  without reaching 100%. The curve rises fast, flattens, and approaches its
  ceiling without arriving — so gear can grant arbitrary points and nothing
  ever divides by zero or turns a player invulnerable.
- **Attribute modifiers are transient and re-derived once a second.** A
  permanent modifier would have to be removed exactly as often as it is added,
  which is how a save ends up with four hundred stacked copies of one armour
  bonus. Transient modifiers are not saved at all, so the worst failure is a
  value that is stale for under a second.
- **The character sheet travels as one packed string.** A fifteen-field packet
  has to agree on field order between a client and a server that ship
  independently, and gains a field every time a stat does. One string survives
  a stat being added or removed, and every payload in the mod is the exact
  two-field `StreamCodec.composite` shape the documentation shows.
- **The client handler is a lambda, not a method reference.** Registration runs
  on a dedicated server too, and a direct reference to a client-only class
  would be resolved there and crash on startup. A lambda body is not resolved
  until it runs, and a client-bound packet only runs on a client.
- **Nothing a client sends is trusted.** Race and class arrive as strings and
  are looked up against the enum; anything that does not resolve is dropped.
  Point spending is clamped against the balance the server holds, never the
  number the packet supplied. Race is refused outright if one is already set,
  so a modified client cannot reroll its biology.
- **Damage reflection is guarded three ways** — living attacker only, never
  self, and dealt through a thorns source so the other party cannot reflect it
  back. A reflection loop is the classic way to freeze a server.
- **Resilience is applied last**, after the elemental matrix, so a defender's
  percentage answers the blow that is actually arriving rather than the number
  it started as.

Caps removed, all of which existed only because nothing had asked them to
climb: the tier ceiling at Unique, the three-socket ceiling, the flat
three-reforges-per-piece (now three per tier, refilled by ascension), and the
elemental advantage table's last entry.

## 5e. What the review of 5d found

The character system was reviewed adversarially after it compiled, on the basis
that "it compiles and the API is real" says nothing about whether the rules are
right. Fourteen findings; the ones that mattered:

- **Reflection re-entered the combat handler and was amplified by the
  defender's own melee stats.** `DamageSources.thorns(defender)` sets the
  defender as both the causing *and* the direct entity, so the reflected packet
  came back through `onIncomingDamage`, satisfied the melee check, and picked up
  the defender's Strength, weapon multiplier, elemental advantage and a critical
  roll. A 0.3-damage reflection became ~180. The guard had been placed around
  the reflection step; it needed to be around the whole handler.
- **Every reforge was paid twice.** The roll became character stats *and* stayed
  as the old attribute modifiers on the item, hitting the same three attributes
  from two directions, with only half of it visible on the tooltip.
- **A client could change class between one swing and the next.** `ChooseCharacter`
  honoured a repeat class choice with no cost, cooldown or proximity check —
  Factor before a kill, Warden before a hit, Marksman before a crit. Both halves
  are now set once. Changing class belongs at a workstation with a price, and
  until that exists it is not on offer.
- **Ascension never checked that the two pieces were the same tier.** The cost of
  reaching tier N was N base pieces rather than 2^N, which was the only thing
  keeping unbounded ascension honest.
- **Silk Touch made character levels free.** Break ore, place ore, break ore: six
  experience a cycle against a level track with no ceiling. Player-placed ore is
  now remembered and pays nothing.
- **The reforge budget collapsed the moment a piece went past Unique** — 25
  points at tier 5, 3 at tier 6, because the table had no case above the named
  range and fell through to its default. Exactly backwards.
- **The Imperial passive approached 100% reflection, not the documented half.**
  A missing 0.5 — subsequently resolved the other way: 100% is the intended
  ceiling, so the doc was wrong rather than the code. See 5f.
- **Four class passives were never called at all.** Artificer's durability
  saving and reforge bonus, Reclaimer's second ingot, Psion's rune resonance —
  all defined, none invoked. Three are now wired; Psion's is re-expressed as
  psionic potency, because "an aligned rune counts twice" cannot be implemented
  where rune affixes live: they are baked into an item's attribute component,
  and an item does not know who is holding it.
- **Korrath molt ran while burning.** `getLastHurtByMobTimestamp` is only written
  for damage with a living attacker, and defaults to 0 — so a new player molted
  from tick 101 without ever having been left alone.
- **Two documented balance invariants were false.** Race starting blocks claimed
  to be equal and ranged from 41 to 47; classes claimed 2 growth points a level
  and all gave 3, making class growth equal to race growth rather than less.
  Both are now 44 and 2, and `validate.py` checks both — the checks were tested
  by breaking each one and confirming it failed.

Also fixed: the attacker's elemental advantage read the item's registered tier
while the defender's read the stack's effective tier, so an ascended weapon
would never have scaled; the Codex could be farmed by relogging without
answering the picker; and two dead methods whose doc comments claimed call sites
they did not have — one of which, `counteredBy`, returned the opposite of what
its name said.

## 5f. Reflection stacking

Sanctioned Answer is meant to approach 100%, not half. Restoring that exposed
the real problem, which was never the constant: shares were being **added and
then clamped at 0.95**. Under that scheme an Imperial past level 200 was
saturated, and every point of Retribution after it bought exactly nothing —
the carefully diminishing curves upstream stopped mattering the moment the
clamp bound.

Shares now combine the way overlapping mitigation actually works:

```
total = 1 - (1 - a)(1 - b)
```

Each source contributes what the previous ones let through. 60% and 60% is 84%;
a third 60% makes it 94%. The total approaches 1.0 and cannot reach it, because
a product of factors each strictly below 1 is strictly below 1 — so the ceiling
is a property of the arithmetic rather than a clamp somebody has to remember to
apply. Both inputs are forced into [0, 1) at the combine site, so that holds
even if a future caller passes something out of range.

Verified numerically across extreme inputs (level and Retribution up to 10^9,
combined three times over): worst case 0.999999.

Warden's Bulwark changed shape as a consequence. "Retribution doubles" has no
meaning once shares are proportional — doubling 60% is 120% — so it now lays
the whole share over itself, taking 60% to 84%.

## 5g. Silent Gear materials were in the wrong format, and the wrong folder

The one integration that claimed to do something real was doing nothing.

Silent Gear changed its material format in 1.21. The files this project
inherited were 1.16-1.20 shaped — a flat `stats` block, `colors`, `tier` and
`categories` at the root, an ingredient as `{"item": ...}` — and lived under
`data/silentgear/materials/`. Silent Gear 1.21 reads
`data/<namespace>/silentgear_materials/`, so it never opened them. No error, no
warning, nothing in the log: the folder simply was not one it looks in.

Rewritten against the wiki's 1.21 example:

- moved to `data/elysium/silentgear_materials/`;
- stats moved into `properties` keyed by part type (`silentgear:main`);
- `mining_speed` renamed to `harvest_speed`;
- `armor_toughness` dropped — it is not in the current schema;
- armour split per slot as well as totalled, the way the documented iron
  example does it;
- the mining tier is now an object with `name`, `level_hint` and
  `incorrect_blocks_for_tool` rather than an integer;
- `crafting` and `display` blocks introduced, ingredient as a bare string.

**This remains the least verified thing in the mod.** Everything else is
checked against a javadoc or a compiler; this is a transcription of a wiki page
for a mod whose format has already moved once. Nothing depends on these two
files, and Silent Gear names the offending field in its log if it objects.

## 5h. The first real compile: `getTier()` collided with `TieredItem`

The first build against actual NeoForge rejected five classes:

```
error: getTier() in Hammer cannot override getTier() in TieredItem
  return type int is not compatible with Tier
```

`ElysiumSocketable` declared `int getTier()`. Every weapon and tool in the mod
descends from `TieredItem`, which already has `Tier getTier()` — same name,
incompatible return type, so the class is rejected outright. Renamed to
`getElysiumTier()`, which is the better name anyway: a vanilla `Tier` is a
mining level, an Elysium tier is a progression rank that ascension pushes past
Sovereign. Two different concepts that never should have shared a name.

**Why the stub harness missed it, and what changed.** The stubs modelled
`SwordItem extends Item` and `DiggerItem extends Item` — flattening the
hierarchy and deleting `TieredItem` from it entirely. A collision that lives in
the layer a stub removed cannot be found by that stub. `TieredItem` is now
modelled, with `SwordItem` and `DiggerItem` extending it, and the harness
reproduces all five of CI's errors exactly when the rename is reverted — tested
both directions.

The same reasoning was applied more widely: `Item` and `ArmorItem` gained the
real methods an interface default is most likely to collide with
(`getDescriptionId`, `getMaxDamage`, `getRarity`, `getType`, `getMaterial`,
`getDefense`, `getToughness`), so the next such clash surfaces locally rather
than after a seven-minute CI run.

### Two deprecation warnings, left alone deliberately

The same build reported `@EventBusSubscriber(bus = Bus.MOD)` and
`DeferredRegister.createDataComponents(String)` as deprecated for removal.
Both are warnings; neither blocks the build, and the mod works with them as
they are.

They are not bundled into this fix on purpose. The documented replacement for
the first is to drop `bus` entirely — the annotation registers to both buses —
but if that is wrong, the failure is silent: mod-bus events stop firing,
nothing registers, and the jar loads and does nothing. That is far harder to
diagnose than a warning, and it should not ride along with the fix for an
unrelated blocking error. Change it on its own, and confirm the next build
still registers.

## 6. Verified

The sources compile cleanly against a signature-accurate stub of the 1.21.1 API
surface, every JSON file parses, every model resolves to a texture that exists,
every recipe and loot table resolves to a registered item, every shaped pattern
matches its declared keys, every placed feature is reachable from a biome
modifier, every registered item is reachable in survival from worldgen, mob loot,
a code-driven grant, or a recipe chain that bottoms out in one of those.

That is not the same as a real NeoForge build — run `./gradlew build`, or push
and let CI do it.

---

# The split into a library and a content mod

Elysium is now two repositories. This section records what moved, what broke
while it was moving, and how the split is kept honest.

## 7. What changed shape, and why

Five things were enums, and an enum cannot be extended by another mod. Each is
now a small registry that freezes on first read and says so clearly if you are
late:

| Was | Is |
|---|---|
| `enum ElysiumStat` | `ElysiumStat.flat(...)` / `.curve(...)`, registered by id |
| `enum ElysiumElement` | registered, with the elements it beats **declared as ids** and resolved lazily |
| `enum ElysiumRace` / `ElysiumClass` | registered, each carrying an `ElysiumPassive` object |
| `enum RuneType` | `ElysiumRune.builder(...)`, carrying its own affix and effect |
| `enum ElysiumFaction` (classification) | still an enum of three sides, but classification is a list of rules consulted most-recent-first |

The important one is the passive. Behaviour used to live in a `switch` inside
the engine's combat handler: `if (race == DRUUN) ...`. That works exactly once,
for the races the engine's author wrote. An add-on's race could be registered,
could carry stats, and would then do nothing at all — because the code deciding
what a race does had never heard of it. Behaviour now travels with the thing it
belongs to, as one of seventeen default-implemented hooks.

The element ring was ordinal arithmetic — "beats the two that precede it" — and
is now a declared graph. A script confirmed the new declarations reproduce the
old cycle exactly: **25 matchups, 0 mismatches.**

## 8. One real bug the split introduced, and how it was caught

The standing attachments (`favor`, `suspicion`) are declared in the
`elysiumlib` namespace but were still being registered from **core's** mod
event bus, because that is where the line happened to be when the file moved.
Two consequences: the ids' namespace did not match the bus they were registered
on, and the library installed on its own never registered them at all — so
every read of a player's standing would have failed.

It compiles perfectly either way. The fix was to move all three
`DeferredRegister.register(modEventBus)` calls into `ElysiumLib`'s constructor,
where the namespace and the bus agree.

Worth stating plainly, because it is the general shape of the risk in any
split: **a line of code that moves between mods keeps compiling and stops
meaning the same thing.**

## 9. How the split is kept honest

Three things, all of which run in seconds:

- **`elysium-lib/validate.py`** checks, first and above everything else, that
  no source under `com.elysium.lib` refers to a mod built on it — no import, no
  hard-coded `elysium:` id, no `isLoaded("...")` check, no content dependency
  in the manifest. It also verifies the element ring is closed (each element
  beats exactly two and is beaten by exactly two) and that every key the engine
  emits has a lang entry. All four checks were negative-tested: each invariant
  was deliberately broken and the correct message confirmed.
- **`elysium-core/validate.py`** is the original resource audit, repointed at
  the new paths, plus the race and class balance invariants — still 6 races at
  44 base and 3/level, still 9 classes at 2/level.
- **A third mod that compiles.** `elysium-lib/example/` is the worked example
  from `EXTENDING.md`, built by the same harness with `elysium-core`
  deliberately off the classpath. A guide that has never been run is a guess;
  if an extension point stops being usable from outside, this stops compiling.

## 10. A stub lesson, repeated in the other direction

The `getTier()` collision that broke the first real build got through because
the stubs modelled `SwordItem extends Item` and flattened `TieredItem` out — a
stub that flattens a hierarchy is blind to what lived in the layer it
flattened.

The split turned up the same mistake pointing the other way: `Block` was
stubbed with no supertype at all, so a perfectly correct override of
`useWithoutItem` — which lives on `BlockBehaviour` — failed here while the real
build would have been happy. A flattened hierarchy does not only hide errors;
it invents them. Both stubs are now deepened.

---

# Vanilla and modded material gear

## 11. The oversight

Elysium had gear for three materials, all of them its own, and the cheapest of
them needed Voidglass ore. So for the entire early game a player had **no
Elysium gear at all** — which meant no sockets, no reforging, no elemental
matchups and no character level requirement doing anything. The whole mod
started at the point where you found its ore.

Now every material has gear: copper, iron, gold, diamond and netherite
alongside the Empire's three, plus eighteen metals other mods commonly add.
Four tool shapes and four armour pieces each — **196 generated items across 26
materials**, on top of the 40 hand-written ones.

They participate fully. Each material has an assigned element, so the gear
sockets runes with real alignment, reforges, ascends and requires a character
level. Vanilla materials sit at Elysium tier 0, netherite at 1, against the
Empire's 0/1/2 — a real ramp that never overtakes the material you had to go
and find.

The element assignments cover all five deliberately: copper conducts (Plasma),
iron is blunt and structural (Kinetic), gold is the receptive conductor
(Neural), diamond is a lattice that bends light (Dimensional), netherite comes
back from somewhere else (Void). A player can therefore align any rune they own
long before reaching Voidglass, so the elemental system is something they learn
early rather than meet at the end.

## 12. Why "auto-detect mod ores" cannot mean what it sounds like

Item registration happens during mod loading, **before any mod can read another
mod's entries**. There is no point at which Elysium could look at what ores
exist and generate matching items — by the time that question has an answer,
the registry is frozen.

There is a second reason, and it is the stronger one. A registry that changed
shape depending on which mods were installed would be actively harmful: ids
shift, saved stacks stop resolving, and a player who removes one mod loses gear
belonging to another. Determinism is not a nicety here.

So the design is:

- **Every material's gear is always registered**, tin included, in every world,
  whether or not any mod supplies tin.
- **The ingredient is a tag**, `c:ingots/tin`, never an item. A recipe against
  an empty tag simply never resolves — ordinary vanilla behaviour, no special
  handling.
- **Availability is a runtime question.** `isAvailable()` asks whether the tag
  has entries, which is only meaningful after tags load. The creative tab and
  the vanilla tab mirrors are built from that, so a pack with no tin mod never
  shows a Tin Hammer.
- **A config adds more.** `[materials] extra_materials` takes `name`,
  `name:tier` or `name:tier:element`. It is read in the mod constructor, and a
  change needs a restart — for exactly the reason above, and the comment in the
  config says so rather than leaving the player to discover it.
- **What is missed is reported, not swallowed.** On every reload Elysium scans
  `c:ingots/*` for metals nothing covers and logs them; `/elysium materials`
  prints the same on demand along with which shipped materials actually
  resolved. Without this, a missing metal is indistinguishable from a
  deliberate omission.

## 13. Art: one rule relaxed, on purpose

`style.py` says colour never carries a material, only energy. That held for
three materials distinguishable by silhouette alone. It does not survive
twenty-six — a row of identically grey hammers is unreadable.

So material ramps are generated from a base hue at **22% saturation**, far
closer to grey than the real metal, while the element glow stays fully
saturated and remains the only bright thing on the sprite. Element is the
mechanically important fact, so it stays loudest; material only decides how good
the tool is, which the tooltip already says.

Generating the ramps rather than hand-painting them keeps all twenty-six on the
same five-step lightness curve. One bug worth recording: an achromatic base
like iron's `#c8c8c8` has hue 0 by convention — which is *red* — so forcing
saturation onto it turned iron into copper. The fix scales the allowance by the
base's own saturation, so a grey material stays grey.

## 14. What the validator now checks

Three new invariants, each negative-tested by breaking it and confirming the
message:

- **A material declared twice** in `ElysiumMaterials.java` — the second
  registration throws at load, and the thrown message names the item but not
  which declaration to delete.
- **A generated name colliding with a hand-written one.** This caught a real
  case while it was being built: giving Neutronium an `ArmourProfile` would
  have registered a second `neutronium_helmet` alongside the hand-written one.
- **A material item with no recipe.** The obtainability audit excuses gear
  gated on a modded tag, because this checkout cannot know a pack's mod list —
  and that excuse would otherwise hide an item unobtainable everywhere.

The material table is read out of the Java by both the generator and the
validator rather than duplicated, so a material added in one place and not the
other fails loudly instead of shipping a purple-and-black cube.

## 15. Every recipe in the mod silently did not exist

The first run on a real client produced a crash report and a log, and they
described the same mistake made in three places.

**The crash.** Clicking *Create New World* threw out of Silent Gear's reload
listener:

```
net.silentchaos512.gear.gear.MaterialJsonException: Error loading
"elysium:aetherium" from pack "mod/elysium":
  Failed to parse either.
  First: Not a json array: "elysium:aetherium_ingot";
  Second: Not a JSON object: "elysium:aetherium_ingot"
```

Section 5g moved these files to the folder Silent Gear 1.21 actually reads and
rewrote them in the 1.21 shape. It got everything right except the ingredient,
which it left as a bare id string.

**Why a string is wrong.** An `Ingredient` in 1.21.1 is a JSON *object* —
`{"item": id}` or `{"tag": id}` — or an array of them. The bare id string was
legal through 1.20. It is the only field in the file the vanilla codec reads,
which is why the rest of the 1.21 rewrite validated and this one field did not.

The two lines above it in the same error, `No key part_substitutes` and
`No key gear_type_blacklist`, are DFU reporting the optional fields it probed
on the way past. They are not missing keys and adding them fixes nothing.

**The same mistake, 229 more times.** The log showed every Elysium recipe
failing identically:

```
Parsing error loading recipe elysium:neutronium_leggings
com.google.gson.JsonParseException: Map entry '#' : Failed to parse either.
First: Not a json array: "elysium:neutronium_ingot"; ...
```

227 in `elysium`, 2 in `elysiumdungeons` — all of them. **Nothing in the mod was
craftable**, and unlike the material this did not crash: a recipe that fails to
parse is logged once at reload and dropped. The mod loaded, every item was in
the creative tab, and no recipe existed. That is the worse failure of the two,
because a crash gets reported and this does not.

Both generators had the assumption baked in, `gen_data.py` in a comment saying
so out loud. They now share one `ing()` helper, and the `c:` namespace is
written as a tag rather than an item — writing `c:ingots/tin` as an item would
mean the recipe worked only when nothing supplied the tag.

**And two loot tables.** `minecraft:looting_enchant` was renamed
`minecraft:enchanted_count_increase` in 1.21, and the new function will not
assume which enchantment it scales with, so `"enchantment"` is now required. An
unknown function does not skip that entry — the whole table fails, and both
Elysium mobs dropped nothing at all.

### What the validator checks now

The reason all three shipped is that `validate.py` parsed every JSON file and
checked what it *referenced*, but never checked its *shape*. Valid JSON that no
codec accepts passed every check.

- **Ingredients are objects.** Recipe keys, shapeless ingredient lists, and the
  Silent Gear `crafting.ingredient` — negative-tested by restoring the bare
  string in each of the three and confirming the message.
- **Loot functions 1.21 removed**, and `enchanted_count_increase` carrying the
  explicit `enchantment` its predecessor did not need.

One thing this repair broke on the way past, worth recording because it is the
failure mode a validator has that nothing else does: the obtainability graph
read ingredients as strings. Once they became objects it matched nothing,
found no ingredients anywhere, and would have gone on passing while checking
nothing at all. Both readers now go through one `ingredient_ids()` helper.

## 16. The same crash again, and the sentence in §15 that caused it

The next launch crashed in the same place, on the same file, with the same
exception — and with the ingredient correctly written as an object:

```
net.silentchaos512.gear.gear.MaterialJsonException: Error loading
"elysium:aetherium" from pack "mod/elysium":
  No key part_substitutes in MapLike[{"can_salvage":true,"categories":["metal"],
  "ingredient":{"item":"elysium:aetherium_ingot"}}];
  No key gear_type_blacklist in MapLike[...]
```

Section 15 called those two lines "DFU reporting the optional fields it probed
on the way past... not missing keys, and adding them fixes nothing." That was
wrong, and it was the only thing left standing between this mod and a working
world. They are required fields with no codec default. DFU had reported all
three problems in one message; fixing the one that was recognised and dismissing
the two that were not left two thirds of the fault in place.

The lesson is narrower than "read the error." All three lines were in the same
message, in the same format, and the two dismissed ones named their keys
explicitly. What made them look like noise was that `part_substitutes` and
`gear_type_blacklist` both have an obvious empty value, so it seemed
unreasonable for a codec to demand them — reasoning about what the library
*should* require rather than checking what it *does*.

### What settled it

Silent Gear's own material definitions, read out of
`silent-gear-1.21.1-neoforge-4.2.1.1.jar`. All **132** of them carry all five
crafting keys:

```json
"crafting": {
  "can_salvage": true,
  "categories": ["gem", "intermediate"],
  "gear_type_blacklist": [],
  "ingredient": { "tag": "c:gems/amethyst" },
  "part_substitutes": {}
}
```

84 of the 132 have `part_substitutes` exactly `{}` and an empty blacklist, so
the empty case is written out rather than omitted. The wiki example §15 was
written against shows three of the five.

**Where a document and the shipped artifact disagree, the artifact is the
specification.** The jar was available the whole time.

`validate.py` now checks the whole required key set rather than the ingredient
alone, with that provenance recorded next to it, and the two Elysium materials
carry `gear_type_blacklist: []` and `part_substitutes: {}`.

## 17. Ascension was a label, and two things it should have been

Five requests, and four of them turned out to be the same missing idea.

### What ascension actually did

Raised a number on the stack, and through it: the rarity colour, the tier line
on the tooltip, the socket count, the psionic affix, the reforge budget and the
character stats the piece granted. All real. **None of them the numbers a player
ascends a piece for.** An ascended chestplate had the armour it was forged
with; an ascended blade hit for what it always hit for.

Worse, the growth that did exist was three unrelated curves: the affix grew at a
flat +15% a tier, the stat grant grew linearly at `1 + tier`, and the reforge
budget grew geometrically at 1.4x. Nothing stated any of them next to any other.

Against a price that **doubles every tier** — a tier needs two pieces of the tier
below, so tier *n* costs 2^*n* base pieces — a linear reward means the fourth
ascension is the last one worth doing.

### One curve

`ElysiumAscension` is now the only answer to "what is a tier worth": geometric,
at 1.25x a tier, matching the shape of the price. Armour, toughness, attack
damage, stat weight and the psionic affix all read it. It is one constant; move
it and the whole progression moves together, which is the property that was
missing rather than any particular value.

| tier | multiplier | stat weight | was |
|---|---|---|---|
| 0 | 1.00 | 1 | 1 |
| 2 | 1.56 | 3 | 3 |
| 5 (Sovereign) | 3.05 | 9 | 6 |
| 10 | 9.31 | 34 | 11 |

The stat weight is anchored to pass through the old 1, 2, 3 at the bottom, so
early progression is untouched and only the top of the curve moves. A bare power
would have produced 1, 1, 2, 2 there — two ascensions granting the same stats,
which reads as the second one not working.

### Armour and damage

`ElysiumSocketable` gained three declarations — base armour, base toughness,
base attack damage — and one method that turns them into modifiers at the
piece's effective tier. Armour reads them off the `ArmorMaterial`; weapons and
the four tools keep the damage figure their constructor was handed. The library
scales them and deliberately cannot find them, because a trinket has no
`ArmorMaterial` and the seam has to work for both.

The modifier id carries the slot (`elysiumlib:ascension/armor/head`). Attribute
modifiers are keyed by id, so a helmet and a chestplate sharing one would have
been a single key with one of the two silently discarded — the bug vanilla
avoids by naming its own armour modifiers per slot.

### Everything ascends, and everything reforges

Both operations began by rejecting anything that was not an `ElysiumArmorItem`.
So a Neutronium Hammer or a Singularity Lance could be socketed at the reforge
table, carried an element and a tier and a socket count, and could be neither
reforged nor ascended at that same table. Nothing said so; the table just did
nothing.

There was never a reason. Everything either operation touches lives on
`ElysiumSocketable`. The gate was left over from when armour was the only thing
that had any of it. `ElysiumArmorAscension` is now `ElysiumGearAscension` and
asks for the interface — which means a trinket implementing it is ascendable and
reforgeable the day it is registered, with no change to either file.

### Two bugs found on the way

**`isArmour()` was never overridden.** The interface declares it precisely so
the engine can stop asking `instanceof ElysiumArmorItem`, and its javadoc says
so — but `ElysiumArmorItem` never implemented it, so the only live definition
was the `false` default. Consequences: armour granted no Fortitude, and
`armourProfileOf` walked every armour slot and rejected every piece, so **the
entire defensive half of the elemental counter matrix never fired**. It compiled,
it ran, and it silently did nothing.

**An ascended blade lied on its tooltip.** The combat handler read the effective
tier; `getAdvantage()` read the registered one. The weapon fought at its real
advantage and told the player it had the advantage of the day it was forged.

### check_gear.py

`applyRunes` and `elysiumModifiers` differ by exactly one thing: the second also
adds ascension. Both compile, both run, and an item that calls the wrong one
socketable correctly, ascends its tier correctly, shows the higher tier — and
gains nothing. Six classes called `applyRunes` directly; all six were correct
until ascension existed and would have been wrong the moment it landed.

That cannot be a compiler's job, because `applyRunes` is a legitimate method
that `elysiumModifiers` is written in terms of. So it is a checker, run beside
`check_lifecycle.py`, and negative-tested by reverting one tool.

## 18. Every dungeon ever generated was a level-1 dungeon

The boss having too much health at the bottom was the symptom. The cause was
larger than the boss.

`ElysiumBestiary.spawn` levels a creature with
`ElysiumScaling.levelFor(level, where, faction)` — the average level of players
within 64 blocks. `DungeonTravel` builds the entire dungeon, boss included, and
*then* teleports the player in:

```java
arrival = DungeonBuilder.build(dungeonLevel, instance, layout);
...
teleport(player, dungeonLevel, arrival);
```

So every spawn found nobody in the dimension and fell through to
`FALLBACK_LEVEL = 1`. Not the first dungeon — every dungeon, at every level of
play, for the life of the save. Reordering would not have fixed it either: the
boss room sits up to 190 blocks from the entrance, well outside the range.

The level is now decided once, from the player opening the portal, recorded on
the `DungeonInstance` (so it persists and so joiners agree), and carried through
`RoomContext` to every spawn. `spawn` gained an overload that takes the level;
the proximity one is still right for a dispatch arriving in a world someone is
standing in, and still what that path uses. An instance saved before this
records 0, which reads as "unknown" and falls back to the old behaviour rather
than becoming a dungeon built for level zero.

### And the boss multiplier

A flat 6x on top of the family's health, which made the first boss a player ever
met the hardest one relative to what they could do about it — 720 health for a
Choir against a starting character — and then grew only as fast as a square
root, so it was a formality by the time they had the damage to answer it. The
wrong shape at both ends.

The multiplier now climbs: 2x at level 1, passing the old 6x at about level 45.

| level | Choir was | now | Praetor was | now |
|---|---|---|---|---|
| 1 | 720 | **240** | 900 | **300** |
| 10 | 1368 | 866 | 1710 | 1083 |
| 45 | 2153 | 2146 | 2691 | 2682 |
| 80 | 2640 | 3226 | 3300 | 4033 |

240 and 300 put a level-1 boss in the range vanilla puts the Ender Dragon and
the Wither in.

## 19. Elysium Trinkets

Forty accessories, and almost no new machinery — which is the interesting part.

### What had to be written

Registration for forty items, one Curios adapter, and forty descriptions of what
an accessory ought to do. Nothing else. Specifically, **the library needed no
new hook**: the fifteen `ElysiumPassive` hooks written for races and classes
turned out to cover all forty, because they were written as questions the engine
asks rather than as a race-and-class feature.

The reforge table needed no change either. It accepts `ElysiumSocketable`, so a
trinket became reforgeable and ascendable the moment it implemented the
interface — with elysium-core not naming this mod and this mod not naming
elysium-core. That was §17's promise and this is the first test of it.

### One component fewer

`ElysiumTrinketData` was a second component holding a trinket's ascension tier
on its own, on the reasoning that "a trinket has no runes, no reforge rolls and
no reforge budget", so sharing `ElysiumGearData` would invite socketing a rune
into a ring.

That premise was overturned by the requirement that everything wearable
reforges and ascends like everything else. Socketing a rune into a ring is now a
feature. Two components for one idea would have meant two places to read an
ascension tier from and a live chance of reading the wrong one, so the trinket
component is gone and trinkets use the same gear data as armour.

Deleting a registered component is normally save-breaking. It was free here only
because nothing had ever written one — no trinket had shipped.

### The Curios seam

Three symbols wide: `CuriosApi.getCuriosInventory`, `ICuriosItemHandler#findCurios`,
`SlotResult#stack`. The validator enforces that only `CuriosSlots` and the item
class import anything from `top.theillusivec4`, because the library's design for
accessories rests on the claim that swapping the API out is one file, and that
claim is the sort a second import quietly ends.

The adapter caches on the player's **tick count** rather than on Curios' equip
and unequip events. An event-driven cache is stale exactly when an event is
missed, and a missed unequip means a removed trinket keeps working — a bug that
survives until someone notices a number is wrong. A tick-keyed cache cannot be
more than a tick behind anything, costs one scan per player per tick, and
depends on no event class. The expensive case was never "once a tick"; it was
"five times a swing", which is what the uncached version would have been.

The one thing here **not** verified against a shipped artifact is the Curios
signature set — the 1.21 sources were not reachable from this machine and the
mod is not in the pack to read. That is written at the top of the stub rather
than left to be discovered, and it is why the surface is three symbols.

### Two documentation bugs found while writing it

`EXTENDING.md` had no section on trinkets at all: the extension point shipped
undocumented. It has one now.

And its ore example read `ElysiumHooks.registerOre(MY_ORE.get(), true)` — the
guide teaching the exact unbound-value crash that `check_lifecycle.py` exists to
catch, in a form that does not compile either, since `registerOre` takes a
`Supplier`. Two launches of this project died to that mistake and the guide was
handing it to the next add-on author.

## 20. Bigger rooms, and mobs with more than six boxes each

### Rooms

Cells went from 16 to 24, interiors from 13x13x6 to 21x21x9 — 2.6 times the
floor and half again the headroom. Doorways widened with them (five blocks
across, four tall), because a three-wide door in a twenty-one-wide room reads as
a tunnel.

Two things had to move with the geometry rather than being left as literals:

- **`RoomContext.blocksDoorway` kept a literal 2** as the strip of floor a
  decorator must leave clear at a door. Correct for a three-wide door and
  silently a block and a half too narrow for a five-wide one, which would have
  let a pillar be placed in a doorway and seal a room the layout believes is
  connected. It now derives from `DungeonBuilder.DOOR_HALF`.
- **Every density in `DungeonRooms` was a count.** Two lanterns in a 169-tile
  room is atmosphere; two in a 441-tile room is a dark empty hall. `scatterLight`
  and `populate` now take a density relative to the size the numbers were tuned
  at, in one place, so the next size change moves all of them.

**`checkGeometry()` was never called.** The class comment says it "refuses to
build if it is ever broken again"; it was dead code, so it refused nothing. It
runs at the top of `build` now. Geometry was separately verified by modelling
two adjacent cells: no unwritten seam, doorways connect interior to interior, no
open floor tile anywhere in a doorway.

A dungeon is now 76k block writes rather than 24.5k — one synchronous burst when
the portal is used, three times what it was.

### Flavour

A shared `flavour()` pass every populated room gets: a tile course at head
height around all four walls, weathering scattered on the walls in proportion to
wall area, chiselled pilasters every six blocks (skipping doorways), and chains
hanging in the open middle where the extra headroom is actually visible.

Shared rather than per-room on purpose. A decorator says what the room *is*; this
says what the dungeon is, and the dungeon is one place with one set of builders.
Seven copies would have drifted until the crypt and the forge looked like they
came from different mods. It is all wall and ceiling work — a fraction of the
perimeter, not the volume — so a room three times the area costs about 1.7 times
this rather than three.

### Mobs

`gen_mobs.py` learned parents. A box may now name the part it hangs from, so it
becomes a child in the `ModelPart` tree and inherits its rotation: a crest turns
with the head, a pauldron swings with the arm. Bolting detail to the root would
have left a helmet crest hanging in the air while the helmet looked around,
which is the most obvious way for added detail to look worse than none.

Sixteen detail parts across the six families, each chosen to sharpen the
silhouette that family already had rather than blur them together — hood and
satchel on the scavenger, tusks and a slag ridge on the reaver, cowl and trailing
shroud on the whisper, antenna and fins on the drone, crest and pauldrons on the
lictor, mitre and collar on the adept. The bosses reuse family geometry, so both
inherited it for free.

**Palettes are untouched.** Every colour is the same hex it was; the complexity
is geometry and the shading the generator already did.

One thing worth recording because it nearly went the other way: adding fourteen
boxes per family *looked* like it needed a 128x128 sheet, and the first pass
bumped it. Checking rather than assuming showed all fourteen still fit in 64x64
with room to spare, because mirrored parts share one patch. Thirty variant
sheets at 128x128 would have been four times the texture memory for empty space.
The sheet is still 64 and the comment now says it was measured.

## 21. Vanilla proportions, measured against vanilla

"The colours are great but the shaping is a bit blobby." Correct, and the cause
turned out not to be the shapes.

### What the measurement said

Vanilla's own textures were read out of `1.21.1.jar` and compared pixel for
pixel. Two findings, and the second is the one that mattered:

**Silhouettes.** Every vanilla tool has a **three-pixel handle** stepping one
pixel a row. Ours was four, because `outline()` grows a shape by a pixel in each
direction and the haft was drawn two wide. The ingot was thirteen across and
eight tall with every edge rounded, against vanilla's sixteen by twelve with a
long flat run and hard 45-degree ends — the difference between a cast bar and a
pebble, on the most-seen sprite in the mod. Ore blobs covered 13% of a block in
six neat rectangles, against vanilla's ~34% in irregular clumps with loose
specks between them.

**Value range, which was the real problem.** Vanilla's iron ingot runs from
luminance 53 to 255 and its sword from 24 to 255. Our metal ramps stopped at
**142** — *our brightest pixel was darker than vanilla's midtone*. A sprite with
no highlight has no value structure, and a shape with no value structure reads
as a silhouette however well it is drawn. Reshaping the tools alone did not fix
it and could not have.

The ramps are now on a curve topping out near luminance 200, generated and
hand-written alike. This is a deliberate revision of `style.py`'s rule that
colour never carries anything but energy: the glow keeps its monopoly on
*saturation*, which is what actually carries across a screen, and no longer
keeps a monopoly on brightness, which was costing every sprite its form.

### The other thing that was rounding the shapes

`glow()` bleeds a halo outward from its core. On a head-sized shape the halo
reaches every edge, and a shape whose every pixel is glowing has no edges — so
the hammer and the axe read as lollipops no matter how they were cut. Both now
use `halo=False`, and the hammer head is a straight-sided block rather than the
rounded one an earlier pass shrank it to. The blobbiness was never the size.

### The GUI is untouched, on purpose

The interface was signed off, so `art/build.py`'s panel colours are now **pinned
to the ramp they were drawn against** rather than read from `METAL`. Reading the
live ramp would have moved a finished thing as a side effect of fixing a
different one. `lib/ui/palette.py` was already independent — it carries its own
values, taken from the *glow* ramps, which did not change.

That panel also turned out to be dead: `ReforgeTableScreen` draws its chrome in
code precisely because a GUI texture gets scaled by the player's GUI scale
setting. It was emitting `textures/gui/reforge_table.png` that nothing had
referenced since, so it now emits nothing.

The reforge table's **slot layout** is likewise left alone. Rearranging it is a
change to the menu as well as the screen — the menu decides where a click lands
— and moving one without the other gives a screen whose every click misses by
exactly the distance it was prettied up by. It was flagged as separate work
before and it stays separate.

## 22. The last two generators pointing at a tree nobody ships

`gen_data.py` and `art/build.py` both wrote into `work/` — a scratch checkout
that stopped being the shipped one at some point — while the live resources sat
in `core/` and drifted. This was flagged in §15 as "a drift hazard worth
pointing somewhere real when you next touch it". Pointing them at `core/`
uncovered what the drift had cost:

**Seventy missing translation keys.** The generator's lang table had affix
names, character-sheet strings and more that the shipped file did not, because
the shipped file was a copy taken before they were added. Players were seeing
raw keys like `elysium.affix.void` on every affix tooltip.

**And five keys that would have been lost the other way.** `gen_data.py` clears
`data/` and the asset directories before writing, and five
`elysium.command.materials.*` keys had been hand-added to the shipped lang and
were not in the table. Repointing the generator without noticing would have
deleted them and broken the `/elysium materials` report. They are in the table
now.

The clearing step is also why this could not be a one-line fix:
`gen_material_gear.py` writes 196 files into directories `gen_data.py` wipes.
The answer is ordering, and `regen.sh` is now the one place that knows it —
a rule that is written down and enforced by a script, rather than a cleverer
clean that would leave renamed files behind forever.

## 23. Bespoke boss models

`gen_mobs.py` said a boss with its own geometry "would be better and is the
obvious later improvement". The Choir and the Praetor have their own box tables
now.

The original reasoning for reusing a family's boxes was sound and survives: a
boss should be unmistakably the same kind of thing as its escort. Each boss is
still built *from* its family's silhouette. What scaling could not do is say
which one it is — at 2.2x a Scavenger is a large Scavenger, and "large" is the
one distinction a boss should not rely on, because it is also what the game does
to every mob it wants you to take seriously.

So the **Choir** is a Scavenger shape with six arms: two more pairs, smaller,
hung off the ribs rather than the shoulders, which is what makes them read as
taken rather than grown. The **Praetor** is a Lictor with a crest, oversized
pauldrons, a mantle and the standard the rest of them march under.

The Praetor's boxes do not fit a 64x64 sheet, so `smallest_sheet()` now measures
the smallest that works instead of anyone picking one: the Choir gets 64, the
Praetor 96, and the six families are untouched. A sheet size appears in two
places — the texture and the model's `LayerDefinition` — and picking it by eye
goes wrong in both directions.

`art/boxmodel.py` holds the packer, the unwrapper and the emitter now, shared by
elysium-mobs and elysium-npcs. The extraction was verified as a no-op: every mob
texture and every generated class came out byte-identical.

## 24. Elysium Court

Five named figures of the Empire, met rather than fought — the half of it you
can talk to, against Elysium Mobs' half that comes after you. Built to the
reference portraits: Elysomnion crowned and mantled, Sylphara Voss collared,
the Sentinel visored, Lillith caped, Aurelia crowned and haloed.

**One entity type, five people.** Same argument the mobs mod makes for thirty
creatures on eight types, and stronger here because all five are humanoid: five
types would be five models for people who differ in a skin and a piece of
regalia. One model carries every accessory and hides what a kind does not wear,
decided per entity in `setupAnim` — one model instance draws every envoy on
screen, so a model built for the Emperor and reused for a Sentinel would put a
crown on the Sentinel.

**Trading is a right-click, not a merchant screen.** Vanilla's `Merchant` buys a
browsable list, a price that rises with use, and a UI players know — at the cost
of a large API surface for a mod whose trades have exactly one axis, which is
how far up a standing meter you are. A tribute in the hand says the same thing
in one method and has the property that matters: **what you get is decided at
the moment you offer**, by `ElysiumRewards`, so every mod that has registered
rewards is in the pool. A fixed offer list would have been this mod's own item
table drifting against whatever else is installed.

The trade-off is real and is written down rather than glossed: you cannot see
what an envoy will give before you give them something. Deliberate for a court —
you are being received, not shopping — and a browsable list would sit on top of
this rather than replace it.

**They do not fight.** No attack goal, no target selector, no attack damage.
They are killable, and killing one costs standing, which is the correct
consequence and needed no special rule.

**Nobody arrives to refuse you.** The scheduler only considers members of the
court who would actually deal with the player it is visiting. An envoy that
turns up and refuses is indistinguishable from a broken mod.

The palettes are the livery from the portraits — black, emerald and gold
throughout, with what differs being which of the three is loudest and what a
face is doing on top of it. The Sentinel is the exception and deliberately so:
skin and armour are the same black, and the only colour on its whole sheet is
the circuitry.

## 25. Curios, read rather than remembered

§24 recorded that the Curios API was the one thing in the stub tree not checked
against a shipped artifact — the 1.21 sources were not reachable and the mod was
not in the pack being tested. It was in the *next* pack over.

Read with `javap` against `curios-neoforge-9.5.1+1.21.1`, which is the exact
version `gradle.properties` pins:

```
CuriosApi.getCuriosInventory(LivingEntity) -> Optional<ICuriosItemHandler>
ICuriosItemHandler.findCurios(Predicate<ItemStack>) -> List<SlotResult>
SlotResult  = record(SlotContext slotContext, ItemStack stack)
SlotContext = record(String identifier, LivingEntity entity, int index,
                     boolean cosmetic, boolean visible)
ICurioItem  = 0 abstract methods
```

**All five as stubbed.** The Java needed no change, and implementing
`ICurioItem` while overriding nothing is confirmed safe rather than assumed.

### The datapack was wrong, in the way that does not log

The API was right and the data was not, which is the same shape as §15 and §16:
the part a compiler checks was fine, and the part only the game reads was not.

Curios ships all seven slots this mod uses as **presets**, at
`data/curios/curios/slots/`, each with an icon and:

```json
"validators": ["curios:tag"]
```

That validator is the entire reason the item tag decides what fits. The slot
files here had been written from memory as:

```json
{ "size": 2, "operation": "SET", "order": 110, "add_cosmetic": false }
```

`SET` **replaces** the preset. It would have discarded the icon and, far worse,
the validator — leaving a slot that accepts anything or nothing depending on
load order, with nothing in any log to say why. `add_cosmetic` was invented
outright.

What a consumer mod actually writes, confirmed against Relics, which ships
against this same Curios:

```json
{ "size": 1 }
```

The entity file also said `"minecraft:player"` where Curios' own consumers write
`"player"`.

`validate.py` now refuses `"operation": "SET"` on any of Curios' ten preset
slots and rejects keys Curios does not read, negative-tested by putting the old
file back.

The lesson is the one from §16, and it has now been the right call four times:
**where a document or a memory and the shipped artifact disagree, the artifact
is the specification.** The jar was one folder away the whole time.

## 26. The stub tree stopped being the thing we compile against

The first CI run after the Court went in failed on one line:

    ImperialEnvoy.java:171: error: cannot find symbol
        player.displayClientMessage(kind.refusal().withStyle(ChatFormatting.RED), true);
      symbol:   method withStyle(ChatFormatting)
      location: interface Component

`withStyle` is a `MutableComponent` method. `EnvoyKind.refusal()` is typed
`Component`, so there was nothing for the call to bind to. Three lines above it
the `displayName()` call already had it right — `.copy()` first — which is the
tell that this was a slip rather than a misunderstanding.

The fix is one word. What matters is why it reached CI at all: the stub for
`Component` declared `withStyle` and `append` itself. A stub is a claim about
somebody else's artifact, and that claim was false, so *every* such call
compiled here and none of them could ever link. Moving both methods onto
`MutableComponent`, where the real class has them, reproduced the CI error
exactly and confirmed it was the only site in six mods.

That is the same failure as §18's flattened `TieredItem` and the same failure
as §15's "DFU probe noise". Three times now the local harness has agreed with
me about something the shipped artifact disagreed with, and each time the cost
was a push, a CI run, and someone waiting on it.

### The harness was checking a claim against itself

So it stopped doing that. Everything needed to compile against the real thing
was already on the machine that runs the game:

| what | where |
| --- | --- |
| the obfuscated client | `versions/1.21.1/1.21.1.jar` |
| obf → official names, TSRG2 | `neoform-1.21.1-…-mappings-merged.txt` |
| NeoForge's own remapper | `AutoRenamingTool-2.0.3-all.jar` |
| NeoForge's patched Minecraft classes, already officially named | `neoforge-21.1.248-client.jar` |
| the NeoForge API | `neoforge-21.1.248-universal.jar` |
| Curios 9.5.1 | the V+ and Tech instance's `mods/` |

Remapping the client with NeoForge's own tool and NeoForge's own mappings
produces the jar the mods are actually compiled against. `./real_jars.sh` does
it; `compile.sh` puts the result **before** `stubs` on the classpath, so every
stub a real class shadows is invisible to javac and can no longer lie about
anything. All seven modules compile clean against it.

No network was involved and none was available: Maven Central,
`maven.neoforged.net` and `libraries.minecraft.net` are all refused through the
proxy. The artifacts came off the user's own CurseForge install.

### What is still a guess, and what it was checked against instead

238 of the 245 stubs are now shadowed. The seven that are not are FML, and they
are not stubbed by choice — `fancymodloader/loader/4.0.43/loader-4.0.43.jar`
sits eight folders below the connected folder and the device bridge allows
seven.

They are still not unexamined. NeoForge's own jar calls FML constantly, and a
constant pool records the exact owner, name and descriptor of every call — the
artifact itself, not a description of it. Reading NeoForge's constant pools
confirmed all seven: `Dist` has exactly `CLIENT` and `DEDICATED_SERVER`;
`ModList.get()` and `isLoaded(String)` match; `ModConfig.Type` is exactly
`COMMON, CLIENT, SERVER, STARTUP`; `ModContainer.registerConfig` takes an
`IConfigSpec`, which `javap` confirms `ModConfigSpec` implements;
`AttachmentSync` carries `@EventBusSubscriber(modid="neoforge")`.

`check_stubs.py --audit` prints that table every run and **fails** if a stub is
uncovered by any jar *and* has no line saying what confirmed it. An unexamined
stub is now a build error rather than a thing that surfaces in CI a week later.

### One piece of dead scaffolding removed

`check_stubs.py` had been copied into the monorepo, where there is no `stubs/`
directory. It was therefore walking the mod sources instead and reporting a
"duplicate" for `ElysiumTrinkets` — the library's registry and the trinkets
mod's entry point, two different classes that are both meant to exist. A
checker in the shipped repo that fails on correct code teaches people to ignore
checkers. Removed from the monorepo; it lives in the work tree, next to the
stub tree it is about.

## 27. The four weapons that were still sticks, and a tree nobody shipped

§21 fixed the value range — the metal ramps topped out at luminance 142 against
vanilla's 255, which was most of what "blobby" meant — and reshaped the hammer,
the axe, the ingot and the ore. It left a note saying the sword still read as a
thin purple stick and the spear as a stick with a tiny tip. That note was
correct and this is the rest of it.

Rendered at 9x beside `iron_sword`, `trident`, `iron_hoe` and `iron_axe`, six of
the nine read wrong, and each for its own reason:

| | what it looked like | why |
| --- | --- | --- |
| sword | a dark stick with two beads on it | blade shaded 5/3/1 across three pixels of core, so half of it was shadow; guard drawn as two stubs with a gap where the blade crossed, which reads as two beads and not as a bar |
| lash | the same stick, slightly thinner | nothing distinguished it from the sword but two pixels of width |
| spear | a stick with a bump | a five-pixel head on a three-pixel shaft is not a big enough difference to name the object |
| scythe | a bent wire | one pixel of core everywhere; outlined that is three pixels of which two are outline |
| lance | a spoon | three overlapping rectangles that `plate()` rounded off |
| rifle | a stick with two lumps | drawn on the same 45-degree diagonal as every blade in the set |

The fixes are all shape, not colour — the palettes were signed off:

- **Blades shade 5/4/2, not 5/3/1.** Across three pixels of core the old values
  ran bright to nearly black. A blade that is half shadow reads as a dark stick
  however well it is shaped, and this one change did more than any silhouette
  edit.
- **The guard crosses the blade.** `_crossbar()` draws a continuous
  perpendicular run straight through it and is painted afterwards. Two stubs
  either side with a gap between them are two objects to the eye; the eye needs
  the run to be continuous before it will call it one bar.
- **Guard, grip and pommel each get their own value.** Vanilla separates them
  by material — steel, wood, steel — and gets three readable parts for free. A
  single-hue ramp has to do it with value: a faceted guard, a plated-then-
  engraved grip, and only the pommel bright.
- **The spear gets wings.** A narrow head on a thin shaft is a stick with a
  bump; widening the head until it stops being one turns it into a spoon. Both
  were tried and rendered. What separates a polearm from a stick at 16x16 is a
  *second silhouette crossing the first*, and boar-spear wings are that for
  four pixels. They also tell the player which end is which.
- **The scythe carries two rows of core** through the body of the blade,
  tapering to one at the point, with the back engraved so it does not read as a
  slab.
- **The lance is a long tapering spike with a fluted collar**, built from rows
  rather than from a diagonal band. One attack per turn and the highest damage
  on the board should read as reach.
- **The rifle is off the 45-degree axis.** `_slope()` runs two pixels along x
  for every one up, so the barrel and the stock meet at a bend — and it has six
  named parts (stock, receiver, magazine, grip, barrel, muzzle), because a gun
  at this size is recognised by its part count and its bend, not its outline.

One trap worth recording, because it was walked into twice. A head built by
stepping a run *perpendicular* to the blade touches the next step only at its
corners, so `outline()` floods straight through the gaps and the head renders
as a checkerboard. That is the same hazard `_axis()` documents, reached from
the other direction. Heads are built from overlapping rows.

58 sprites changed: 26 spears, 26 scythes and the six named weapons whose
shapes moved. The hammer, the axe and the maul were already right and were not
touched — verified by diffing every texture before and after rather than by
assuming it.

### The monorepo had a `core/` that nothing built

Regenerating in the monorepo turned up something worse than a sprite. There was
a `core/` directory beside `elysium-core/`, holding 54 textures and nothing
else: no Java, no build file, not in `settings.gradle`, not built, not shipped.
Every `regen.sh` in the monorepo had been writing the named sprites and the
Empire tool sprites into it.

`art/build.py` chose its output by asking whether `<module>/…/assets/elysium`
existed and taking the first hit — and `out()` creates that directory with
`makedirs`. So one run against the wrong path made the wrong path permanently
correct, and every run after it was silently correct-looking and wrong. This is
§22 again, and it came back because the test was on a directory a generator can
create rather than on something only the real module has.

It now keys on `ElysiumMaterials.java`. No generator ever writes a `.java`
file, so the test cannot bootstrap itself into being true, and `elysium-core`
is tried first. The orphan tree is deleted.

Two more pieces of the same problem went with it:

- **`gen_data.py` was never copied into the monorepo at all.** `regen.sh` line
  26 invoked `elysium-core/tools/gen_data.py`, which did not exist, so the
  repository being pushed could not regenerate its own data pack — the run died
  on its first step. Copied in; `regen.sh` now completes and produces a tree
  byte-identical to the work tree's.
- **`elysium-core/tools/art/` and `elysium-core/tools/textures/`** were two
  stale copies of the whole art package, unreferenced by anything and predating
  the entire vanilla-proportions pass. Deleted. One generator, one copy.
