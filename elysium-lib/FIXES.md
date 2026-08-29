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

## 15. The interface overhaul, and what the previews caught

The old screens were vanilla `Button` widgets on flat panels. Everything is now
drawn from `ElysiumUI` — `fill` calls on integer boundaries, no textures — with
the palette generated from `ui/palette.py` and mirrored into a Pillow renderer
so the design can actually be looked at before it ships.

That preview pipeline is the only check a GUI gets; a compiler will build an
unreadable screen without complaint. Five real defects it found:

- **`PIL.rectangle` replaces alpha rather than compositing it.** The mock was
  lying: a 10% violet wash rendered near-white, and I went looking for a design
  problem that did not exist. `Canvas.fill` now crops and `alpha_composite`s.
  This is the worst class of bug in this pipeline, because the preview is the
  thing I trust.
- **A panel measured correctly from its parts and still too tall.** The reforge
  table came to 244 on a 240-pixel screen. `check_fits()` now fails the build
  for any panel that does not fit 320x240, which is a 960x540 window at GUI
  scale 3.
- **`TEXT_FAINT` at 2.89:1**, caught by the generator's own contrast floor
  before it was ever drawn. Lightened to 3.58:1.
- **The character sheet at 228 against a 224 budget** — the same overrun as the
  reforge table, found the same way. It now uses the narrower padding, and its
  Character tab is two columns rather than a stack, which was the part that did
  not fit.
- **Text that was cut did not look cut.** The picker at 320 pixels clipped a
  passive's description after two words, and the result read as a complete if
  terse sentence. `wrapped()` now measures every line before drawing any, so a
  paragraph that overflows ends in an ellipsis. Misinformed is worse than
  underinformed.

Two invariants were added to `validate.py`, both negative-tested:
`ElysiumPalette.java` must still match what `ui/palette.py` generates (a
hand-edit would silently part the game from every screenshot that was approved),
and every `elysium.*` key referenced literally in the sources must exist in the
lang file.

## 16. The HUD, and a zero that would have been a lie

Level, XP and both standing meters live in **data attachments**, and a NeoForge
attachment is server-side unless explicitly synced. A client asking the local
player for its own Favor gets the attachment default — zero — with no error and
nothing to suggest the number is fiction. A HUD is read peripherally and
believed, so this mattered more than a screen would have.

Rather than sync four attachments, Favor and Suspicion were appended to the
packed sheet string, which already travels whenever anything changes, and which
tolerates the two new fields being absent from an older packet. A second payload
`SyncCharacter` carries that string without opening a screen — `OpenCharacter`
could not be sent on a timer, since it would throw the character sheet open
mid-fight.

Two decisions worth recording:

- **The sync is a diff, not a timer.** The packed string is compared against the
  last one sent to that player and the packet goes out only on a difference. A
  character not gaining XP or moving either meter costs nothing.
- **The HUD draws nothing until the first packet arrives**, and hides a meter
  below the notice threshold of 25 entirely. A HUD that is briefly absent is
  honest; one that briefly reads `FAVOR 0` is not.

## 18. The first real build, and the one thing a stub tree cannot check

Seventeen minutes of NeoGradle later, the first build against real NeoForge
artifacts produced **two errors, both the same one**, in the whole project:

```
ElysiumHud.java:10: error: cannot find symbol
import net.minecraft.client.gui.VanillaGuiLayers;
  symbol:   class VanillaGuiLayers
  location: package net.minecraft.client.gui
```

`VanillaGuiLayers` — the table of ids for vanilla's own GUI layers — is
`net.neoforged.neoforge.client.gui.VanillaGuiLayers`. It is a NeoForge
addition, not a Minecraft class. `LayeredDraw` and `DeltaTracker`, which it sits
next to in the same file, really are vanilla, which is exactly why the wrong
guess looked right.

**This is the failure mode the stub tree structurally cannot catch, and it is
worth being precise about why.** A stub answers one question: is the name you
asked for present in the package you asked for? It cannot answer the question
that actually mattered — does this package exist in the real world, and does
this class live in it — because the stub tree *is* the world it is checking
against. Inventing `net/minecraft/client/gui/VanillaGuiLayers.java` did not
merely fail to catch the error; it *created* the error and then certified it.

Everything else in the library compiled clean, first time, against the real
thing: the payloads, the attachments, the event subscribers, the screens, the
rest of the HUD. So the harness was not worthless — it was wrong in precisely
the one place where it was writing the answer as well as marking it.

Two things changed as a result:

- The stub moved to `stubs/net/neoforged/neoforge/client/gui/`, and the harness
  now reproduces the CI error exactly when the old import is put back
  (negative-tested).
- **A real build is now the check.** Every class the stub tree describes was a
  guess until the build confirmed it; from here the CI run is the authority and
  the harness is a fast pre-filter, not evidence. Where a stub is written from a
  javadoc rather than from a build, its javadoc says so — as this one's now
  does.

## 19. The first launch: a lifecycle rule with no compile-time shape

Four jars built, the game started, and the mod died in its own constructor:

```
java.lang.IllegalStateException: Cannot get config value before config is loaded.
  at ElysiumMaterialConfig.extras(ElysiumMaterialConfig.java:97)
  at ElysiumMaterials.bootstrap(ElysiumMaterials.java:224)
  at com.elysium.core.Elysium.<init>(Elysium.java:376)
```

`container.registerConfig` registers a spec; it does not load it. A
`ModConfig.Type.COMMON` config is loaded just before `FMLCommonSetupEvent` —
after every mod constructor has run. But items must be registered *during* the
constructor, and this config decides which items exist, so it was being read at
the one moment it could not be.

`ModConfig.Type.STARTUP` is the only type read immediately on registration, and
therefore the only one whose values can decide what a mod registers.

**The cost is real and is accepted rather than worked around.** NeoForge's own
documentation advises against using STARTUP to change registered content,
because STARTUP is not synced: a client and server with different files have
different item registries and the client is kicked at login. That is inherent to
a config that decides which items exist — there is no point in the lifecycle
that is both after config load and before registration — so the mitigation is
honesty rather than cleverness, and the config's comment now says in capitals
that the file must match on every client.

`extras()` also catches the failure now and returns nothing, logging loudly. An
optional feature should degrade to being absent; it should not be able to stop
the game from starting.

### Why this one was never going to be caught before launch

This is the second category named in section 18 — the one the stub tree cannot
reach even in principle. Section 18 was a *signature* being wrong, and a real
compile found it. This is a *lifecycle* rule: `registerConfig(COMMON, spec)`
followed by `spec.get()` is perfectly typed, perfectly compiled, and wrong only
because of when the two run relative to each other. No compiler models that.

So it is asserted in `core/validate.py` instead: every `registerConfig` in the
mod class must be STARTUP. Negative-tested by reverting to COMMON and watching
it fail. A grep for other instances found exactly one config in the whole
project, which is this one.
