# Building a mod on Elysium Library

This is the guide for writing the *third* Elysium mod — the one that is neither
the library nor `elysium-core`. Everything below is something `elysium-core`
itself does, so if a passage is unclear, the working version of it is in that
repo, usually in `ElysiumContent.java`.

---

## What you get for free, and what you do not

The split is easiest to use once you accept one sentence:

> **The library owns the number. You own what it does.**

A stat you register is summed across race, class, level, spent points and gear;
saved to the player; shown on the character sheet; and spendable. All of that
without you writing a line. It will also have **no effect on the game** until
your own code reads it and acts.

That is not a gap in the design. There is no way to express "reduce incoming
fire damage by 12% but only below half health" as data without inventing a
scripting language, and a system that only does what its author anticipated is
worse than one that hands you the number and gets out of the way.

Three things are the exception, because their behaviour travels with them as an
object rather than as a number: **races**, **classes** and **runes**. Those are
genuinely complete the moment they are registered.

---

## Setting up

### 1. Depend on the library

`gradle.properties`:

```properties
elysiumlib_version=1.0.0
```

`build.gradle`:

```groovy
repositories {
    // A sibling checkout that has been `gradlew publish`ed.
    maven { url = uri("${rootDir}/../elysium-lib/repo") }
    // Or whatever `gradlew publishToMavenLocal` put in ~/.m2.
    mavenLocal()
}

dependencies {
    implementation "com.elysium:elysiumlib:${elysiumlib_version}"

    // Also needed, or runClient loads your mod against nothing.
    localRuntime "com.elysium:elysiumlib:${elysiumlib_version}"
}
```

Do **not** shade or bundle the library into your jar. Two Elysium mods each
shipping their own copy is exactly the failure this split exists to prevent —
players install `elysiumlib` once.

### 2. Declare it in `neoforge.mods.toml`

```toml
[[dependencies.${mod_id}]]
    modId="elysiumlib"
    type="required"
    versionRange="[1.0.0,)"
    ordering="AFTER"
    side="BOTH"
```

`ordering="AFTER"` matters: your constructor registers into the library's
registries, and they have to exist first. `type="required"` is what turns a
missing library into a clear message on the mods screen instead of a
`NoClassDefFoundError` in a crash report.

### 3. Register from your constructor

```java
@Mod(MyMod.MODID)
public class MyMod {
    public MyMod(IEventBus bus) {
        ITEMS.register(bus);
        MyContent.register();   // everything below
    }
}
```

**Registries freeze on first read.** First read happens once the game is
running, so a constructor is always early enough — and if you are late, the
registry throws with a message saying so rather than silently ignoring you.

---

## The extension points

### Stats

```java
public static final ElysiumStat TENACITY = ElysiumStat.curve(
        id("tenacity"), ChatFormatting.DARK_GREEN,
        60.0F,     // points at which the effect is half of its ceiling
        0.40F);    // the ceiling it approaches and never reaches
```

`curve` gives you diminishing returns for free: `proportionOf(points)` returns
`ceiling * points / (points + halfway)`. Every proportional stat in the library
uses that shape, which is why nothing ever needs a clamp — a value that
approaches its bound by construction cannot cross it, and there is no call site
that can forget to apply the limit.

Use `flat` for a stat that is not a proportion (a flat damage number, a count).

Then read it in your own handler:

```java
@SubscribeEvent
public static void onIncomingDamage(LivingIncomingDamageEvent event) {
    if (event.getEntity() instanceof Player player) {
        float share = TENACITY.proportionOf(ElysiumStats.get(player, TENACITY));
        event.setAmount(event.getAmount() * (1.0F - share));
    }
}
```

### Elements

```java
public static final ResourceLocation TIDE_ID = id("tide");

public static final ElysiumElement TIDE = ElysiumElement.register(
        TIDE_ID, ChatFormatting.BLUE,
        Set.of(ElysiumElements.PLASMA_ID, ElysiumElements.KINETIC_ID),
        List.of(ElysiumStats.AGILITY, ElysiumStats.WILLPOWER));
```

The elements an element beats are given **as ids, not as objects**, and
resolved lazily on first matchup. So registration order between mods does not
matter, and you may name an element that does not exist yet — or never will,
if the mod that defines it is not installed. It simply is not beaten.

You may beat one of the canonical five. The ring itself is the Empire's and
stays put, but nothing stops you standing outside it.

### Races and classes

A race is a starting stat block, a growth block and one passive. A class is the
same without the starting block.

```java
private static final class Undertow implements ElysiumPassive {
    @Override public float defenceScale(Player defender, DamageSource source) {
        return defender.isInWater() ? 0.7F : 1.0F;
    }
}

public static final ElysiumRace TIDEBORN = ElysiumRace.register(
        id("tideborn"), ChatFormatting.BLUE,
        ElysiumStatBlock.of(AGILITY, 8, VITALITY, 6, /* ... */),
        ElysiumStatBlock.of(AGILITY, 2, REFLEXES, 1),
        new Undertow());
```

`ElysiumPassive` is an interface with seventeen hooks and a default for every
one of them, so implement the two you need and ignore the rest. Copy
`CorePassive` from `elysium-core` if you want name-and-description handled from
a translation key.

**A convention worth keeping.** The canonical six races all start with 44
points, spread differently, and grow 3 a level; the nine classes grow 2. The
library does not enforce any of it — you may have reasons — but a race that
starts with twice everyone else's points is the only race anyone will pick, and
a class that grows as fast as a race makes one of the two concepts pointless.
`elysium-core`'s `validate.py` checks its own six and nine after every edit,
which is a habit worth copying.

**How the hooks combine.** A player has at most two passives, and both are
always consulted:

| Hook shape | Combination | Why |
|---|---|---|
| Multiplier (`attackScale`, `defenceScale`) | multiplied | two sources of +25% come to +56%, and neither can cancel the other by returning zero unless it means to |
| Proportional share (`reflectShare`) | `1 - (1-a)(1-b)` | approaches 1.0, never reaches it, no clamp needed |
| Best-of (`critMultiplier`) | the largest answer | two passives that each "make crits hurt" should give the better, not the product |
| Count (`decayRate`) | summed | |

None of them is a plain sum of shares. Sums need clamps, and a clamp is where a
carefully tuned curve goes to stop mattering.

### Runes

```java
public static final ElysiumRune TIDECALL = ElysiumRune.builder(id("tidecall"))
        .element(MyElements.TIDE)
        .affix(new ElysiumAffix("tidecall", Attributes.MOVEMENT_SPEED,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0.05F, 0.05F))
        .effect((player, gear, aligned) -> player.heal(aligned ? 1.0F : 0.5F))
        .register();
```

A rune can carry an **affix** (a flat attribute bonus, applied through the
item's own attribute component) and an **effect** (behaviour run per socketed
copy on the server tick), or either, or neither.

The rune is the *definition*. The item a player picks up is registered
separately and points at it:

```java
ITEMS.register("tidecall_rune", () -> new ElysiumRuneItem(TIDECALL));
```

That split is what lets you ship a rune with no item (a reward applied straight
to gear) or an item for somebody else's rune.

### Materials

```java
public static final ElysiumGearMaterial MYTHRIL =
        ElysiumGearMaterial.builder(id("mythril"))
                .ingredient(ResourceLocation.parse("c:ingots/mythril"))
                .element(MyElements.TIDE)
                .tier(1)
                .toolTier(Tiers.DIAMOND)
                .damageBonus(1.0F)
                .armour(new ArmourProfile(3, 6, 8, 3, 12, 2.0F, 0.0F, 28))
                .register();
```

A material is a description; the items made from it are registered separately
by whoever wants them. `elysium-core`'s `ElysiumMaterialGear` is a worked
example of doing that in a loop — four tool shapes and four armour pieces per
material — and you are free to register one item, or none, instead.

**The ingredient is a tag, never an item, and this is not negotiable.** Item
registration runs before any mod can read another mod's entries, so nothing can
decide at registration time whether mythril exists. Ask `isAvailable()` later —
after tags load — for the answer.

**Register the gear whether or not the ingredient exists.** A registry that
changes shape depending on which mods are installed is how a player loses
inventory: ids shift, saved stacks stop resolving, and removing one mod orphans
another mod's items. Register unconditionally and hide what cannot be made:

```java
if (MYTHRIL.isAvailable()) {
    output.accept(MYTHRIL_HAMMER.get());
}
```

A recipe written against an empty tag simply never resolves, which is ordinary
vanilla behaviour and needs no handling at all.

Two numbers, and they are not the same number. **Elysium tier** sets rarity,
the size of the elemental advantage and the character level the gear requires.
**Vanilla tier** sets mining level, speed and durability. A material can be
easy to mine and high tier, or the reverse.

### Factions

```java
ElysiumFaction.addRule(entity -> {
    if (entity instanceof MyPatrol) return ElysiumFaction.EMPIRE;
    return null;                       // pass
});
ElysiumFaction.addNamedCombatantRule(entity -> entity instanceof MyPatrol);
```

Rules are consulted **most recently registered first**, so yours always beats
the library's catch-all (anything hostile is Unsworn). Return null to pass.

A *named combatant* always pays standing and loot; an ordinary hostile rolls
for it.

### Dispatch — who the world sends after you

```java
ElysiumDispatch.register(new ElysiumDispatch.Dispatcher() {
    public ElysiumFaction faction() { return ElysiumFaction.EMPIRE; }
    public Class<? extends Mob> type() { return MyPatrol.class; }
    public Mob create(ServerLevel level, int band) {
        return MY_PATROL.get().create(level);
    }
    public void afterPlaced(Mob mob, ServerLevel level, Player player,
                            BlockPos pos, int band) {
        ((MyPatrol) mob).equipForBand(band);
    }
});
```

The engine owns the timing, the placement, the crowd cap and the dice. You own
the one thing it cannot know, which is what a member of your faction is.

`afterPlaced` is separate from `create` for a specific reason: the engine calls
`finalizeSpawn` between them, and `finalizeSpawn` runs the vanilla kit-out.
Equip in `create` and vanilla overwrites it.

Several mods may serve the same faction; the engine picks among them at random,
so two mods can both contribute Imperial mobs without either knowing about the
other.

### Creatures — the bestiary

```java
ElysiumBestiary.register(id("husk_reaver"), new ElysiumBestiary.Entry(
        ElysiumFaction.UNSWORN, ElysiumBestiary.Role.ELITE, 2,
        (level, where, mobLevel) -> {
            MyReaver reaver = MY_REAVER.get().create(level);
            if (reaver != null) reaver.scaleTo(mobLevel);
            return reaver;
        }));
```

Anything that needs a creature asks here — a dungeon filling a room, a boss
summoning reinforcements, your own event handler. Roles are `GRUNT`, `ELITE` and
`BOSS`, and a role is what a spawn is *for*, not what it is.

**This is how two content mods reach each other without knowing each other.**
Elysium Mobs registers thirty creatures; Elysium Dungeons asks for one of a
role; neither imports the other, and either works alone. Copy that shape rather
than depending directly on another content mod.

The factory is handed the level the creature should be built for, already worked
out — so every mod's mobs scale by the same rule and the same creature is the
same fight wherever you meet it.

```java
int mobLevel = ElysiumScaling.levelFor(level, pos, ElysiumFaction.EMPIRE);
ElysiumScaling.apply(myMob, mobLevel);
```

`levelFor` is the average character level of players within 64 blocks, adjusted
by their standing band. Use it rather than rolling your own, or your mod's
creatures will be a different difficulty from everyone else's at the same spot.

### Rewards

```java
ElysiumRewards.register((tier, random) -> switch (tier) {
    case 3 -> new ItemStack(MY_RELIC.get());
    case 2 -> new ItemStack(MY_RUNE.get());
    default -> ItemStack.EMPTY;      // decline, let someone else answer
});
```

**Favor sets the tier** (which shelf) and **Suspicion sets the amount** (how
many), so the two loops pay differently and a player can feel which one they
are on. The engine owns that arithmetic; you own the shelf.

Providers are tried in a **random order**, not registration order, so two mods
contributing rewards both get a turn rather than one being permanently
shadowed by whichever loaded first. Returning `ItemStack.EMPTY` passes.

### Ore and the codex

```java
ElysiumHooks.registerOre(MY_ORE, true);   // true = rich, higher Suspicion
ElysiumHooks.setCodex(() -> new ItemStack(MY_CODEX.get()));
```

Pass the `DeferredHolder` **itself**, not `MY_ORE.get()`. A holder has no value
during mod construction — the registry events have not fired — so `get()` there
throws `Trying to access unbound value` and takes your mod down on launch. The
holder already *is* a `Supplier`, which is what this takes, so the correct call
is also the shorter one. (`setCodex` takes a lambda for the same reason: the
body runs on first join, long after the registries are full.)

Ore registered this way earns character experience and Suspicion when broken,
and is covered by the Reclaimer's ore-doubling passive. The library also tracks
player-*placed* ore in a bounded LRU, so Silk Touch plus one vein is not an
unbounded experience loop.

The codex is optional. Without one the character sheet is still reachable
through `/elysium sheet`.

### Trinkets

An accessory, and what wearing it does.

```java
public static final ElysiumTrinket SIGNET = ElysiumTrinket.register(
        id("iron_signet"),
        ElysiumElement.NONE,
        "ring",          // the accessory mod's slot id
        0,               // character level required; 0 for none
        ElysiumTrinket.unique(new SignetPassive()));
```

**A trinket's behaviour is an `ElysiumPassive`** — the same interface a race or
a class answers, with the same fifteen hooks. There is deliberately no
trinket-only hook and no second effect system, so a trinket can do anything a
class can, and a hook added for one is immediately available to all three.

**Ascension is why the behaviour is a function of tier.** A passive only ever
sees the player, never the stack it came from, so it cannot discover how far the
trinket has been ascended. Instead the trinket *produces* a passive for a tier.
`unique(...)` is the shorthand for one that does not ascend; anything that does
should build one object per tier and cache it, because the factory is called on
every hook:

```java
ElysiumTrinket.register(id("band"), ElysiumElements.KINETIC, "ring", 10,
        TrinketPassive.perTier(tier -> new BandPassive(tier)));
```

#### The slots are not the library's business

The library imports nothing from any accessory mod and builds without one. A
content mod installs a provider that answers exactly one question:

```java
ElysiumTrinkets.setProvider(player -> /* what is this player wearing? */);
```

With no provider installed, nothing is worn and every trinket hook is silently
absent — which is the correct behaviour for the library running alone, and also
what a player sees if they remove the accessory mod and keep their save.

**A provider must cache.** `ElysiumCharacter.passives` runs on every hit taken,
every hit dealt and every server tick, so a provider that walks an accessory
inventory on each call puts an inventory scan several times into one swing. This
is stated rather than enforced because the library cannot see the events that
would let it cache for you. elysium-trinkets caches on the player's tick count,
which is both cheap and impossible to leave stale — see `CuriosSlots` for why
that beats caching on equip and unequip events.

#### Trinkets are gear

Make the item `ElysiumSocketable` and it is reforgeable and ascendable at
elysium-core's reforge table, takes runes, grants stats from its element and
asks for a character level — none of which you write, and none of which either
mod has to know about the other to do.

---

## What happens when your mod is removed

Nothing crashes and nothing is deleted.

A race, class, element or rune is stored on the player or the item as its
namespaced id. When the mod that registered it is gone the id stops resolving
and the getter returns null, which every consumer in the library already
handles: the character keeps its level, its spent points and its gear, and
loses that race's base stats, growth and passive until the mod comes back.

This is worth knowing because it is also what makes ids **permanent**. The id
is the persistence key. Rename `mymod:tideborn` after anyone has played it and
you have deleted their race.

---

## Two mistakes that are easy to make

**Registering nothing, because nothing touched your class.** Java only
initialises a class when something first uses it, so a file full of static
`register(...)` calls that nobody references never runs. Give it an empty
`bootstrap()` and call that from your constructor. The failure mode otherwise
is that your races exist in the source and not in the game, with no error
anywhere.

**Assuming your namespace.** `ElysiumRace.register` builds a translation key
from the id: `elysium`/`elysiumlib` ids get `elysium.race.<path>`, and yours
gets `elysium.race.<yournamespace>.<path>`. Write your lang file to match, or
players see the raw key.
