"""
The mechanics, one at a time: the scenarios docs/MECHANICS.md names.

Each one exercises a mechanic the surveys of 27 September 2026 found no
live coverage for, on its own plot, and reads back the observable the
survey named. Player-only mechanics stay in the client stages.
"""
from livetest import scenario
from scenarios import _slot, _grid, _features


def _summon(t, dx, dy, dz, entity, nbt=""):
    out = t.cmd(f"summon {entity} {t.at(dx, dy, dz)} {nbt}".rstrip())
    if "Summoned" not in out:
        t.problems.append(f"could not summon {entity}: {out[:100]}")


def _count(t, selector):
    out = t.cmd(f"execute if entity {selector}")
    import re
    m = re.search(r"Test passed, count: (\d+)", out)
    return int(m.group(1)) if m else (1 if "Test passed" in out else 0)


def _entity_number(t, selector, path):
    import re
    out = t.cmd(f"data get entity {selector} {path}")
    m = re.search(r"has the following entity data: (-?\d+(?:\.\d+)?)[bsfdL]?\b", out)
    return float(m.group(1)) if m else None


# --- atomics -------------------------------------------------------------

@scenario("atomics: a reactor core burns enriched uranium between coolant channels and feeds a high-voltage enricher")
def atomics_reactor(t):
    t.setblock(4, 0, 4, "elysiumatomics:reactor_core")
    for dx, dz in ((3, 4), (5, 4), (4, 3)):
        t.setblock(dx, 0, dz, "elysiumatomics:coolant_channel")
    # the core's tier is extreme; a transformer steps it down onto steel wire
    t.setblock(4, 0, 5, "elysiumindustry:transformer")
    t.setblock(4, 0, 6, "elysiumindustry:steel_wire")
    t.setblock(4, 0, 7, "elysiumatomics:enricher")
    t.hopper_into(4, 0, 7, "minecraft:raw_copper", 4)
    t.cmd(f"data merge block {t.at(4, 0, 4)} {{notch:1,items:{{Size:1,Items:[{{Slot:0,id:\"elysiumatomics:enriched_uranium\",count:4}}]}}}}")
    t.advance(600)
    t.expect_number(4, 0, 4, "heat", 21, "the core made no heat of its uranium")
    fuel = t.number(4, 0, 4, "fuel")
    t.expect(fuel is not None and fuel < 24000, f"the core burned no fuel ({fuel})")
    t.expect_number(4, 0, 7, "share", 0.01, "the core fed no share to the enricher through the transformer")
    _slot(t, 4, 0, 7, "items", 1, "minecraft:copper_ingot", 1, "the enricher on the reactor's power made nothing")


# --- bestiary ------------------------------------------------------------

@scenario("bestiary: the mob fan pushes, the grinder saw kills and pays, the experience press pays out")
def bestiary_farm(t):
    t.setblock(2, 0, 2, "elysiumbestiary:mob_fan[facing=east]")
    _summon(t, 3, 0, 2, "minecraft:cow", "{NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"fanned\"]}")
    t.setblock(8, 0, 6, "elysiumbestiary:grinder_saw")
    t.cmd(f"fill {t.at(7, 1, 5)} {t.at(9, 3, 7)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(8, 1, 6)} {t.at(8, 2, 6)} minecraft:air")
    _summon(t, 8, 1, 6, "minecraft:husk", "{PersistenceRequired:1b,Tags:[\"ground\"]}")
    t.setblock(2, 0, 8, "elysiumbestiary:experience_press")
    t.cmd(f"data merge block {t.at(2, 0, 8)} {{ElysiumHeldOrbs:100}}")
    t.advance(140)
    # a cow with no AI keeps the push it was given without walking it off
    push = _entity_number(t, "@e[tag=fanned,limit=1]", "Motion[0]")
    t.expect(push is not None and push > 0.05, f"the fan gave the cow beside it no push east (motion {push})")
    t.expect(_count(t, "@e[tag=ground]") == 0, "the grinder saw did not kill the zombie above it")
    t.expect(_count(t, f"@e[type=minecraft:experience_orb,x={t.ox + 8},y={t.oy},z={t.oz + 6},distance=..6]") >= 1
             or _count(t, f"@e[type=minecraft:item,x={t.ox + 8},y={t.oy},z={t.oz + 6},distance=..6]") >= 1,
             "the grinder saw paid neither orbs nor drops for its kill")
    t.expect_number(2, 0, 8, "ElysiumHeldOrbs", 0, "the press lost its held orbs")
    held = t.number(2, 0, 8, "ElysiumHeldOrbs")
    t.expect(held is not None and held < 100, f"the experience press paid nothing out of its 100 orbs ({held})")
    t.cmd("kill @e[type=minecraft:experience_orb]")
    t.cmd("kill @e[tag=fanned]")


@scenario("bestiary: a herald calls its court and the court does not call a court of its own")
def bestiary_herald(t):
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(11, 4, 11)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(10, 3, 10)} minecraft:air")
    _summon(t, 6, 0, 6, "elysiumbestiary:forgecaller", "{PersistenceRequired:1b,Tags:[\"herald\"]}")
    t.advance(400)
    many = _count(t, f"@e[type=elysiumbestiary:forgecaller,x={t.ox + 6},y={t.oy},z={t.oz + 6},distance=..16]")
    t.expect(1 <= many <= 3, f"a herald with a cap of 2 stands among {many} forgecallers after 400 ticks")
    t.advance(600)
    later = _count(t, f"@e[type=elysiumbestiary:forgecaller,x={t.ox + 6},y={t.oy},z={t.oz + 6},distance=..16]")
    t.expect(later <= 3, f"the court kept growing: {later} forgecallers after 1000 ticks")
    t.cmd("kill @e[type=elysiumbestiary:forgecaller]")


@scenario("bestiary: two fenstag in love make a calf")
def bestiary_breeding(t):
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(9, 3, 9)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(8, 2, 8)} minecraft:air")
    _summon(t, 4, 0, 5, "elysiumbestiary:fenstag", "{Age:0,InLove:600,PersistenceRequired:1b,Tags:[\"stag\"]}")
    _summon(t, 6, 0, 5, "elysiumbestiary:fenstag", "{Age:0,InLove:600,PersistenceRequired:1b,Tags:[\"stag\"]}")
    t.advance(200)
    babies = _count(t, f"@e[type=elysiumbestiary:fenstag,x={t.ox + 5},y={t.oy},z={t.oz + 5},distance=..10,tag=!stag]")
    t.expect(babies >= 1, "two fenstag in love bred no calf in 200 ticks")
    t.cmd(f"kill @e[type=elysiumbestiary:fenstag,x={t.ox + 5},y={t.oy},z={t.oz + 5},distance=..12]")


# --- comforts ------------------------------------------------------------

@scenario("comforts: a parcel lands in the mailbox, a station counts its trader's work, pigeons breed")
def comforts_post(t):
    t.setblock(2, 0, 2, "elysiumcomforts:mailbox")
    t.cmd(f"data merge block {t.at(2, 0, 2)} {{ElysiumAddress:\"home\",ElysiumFlying:{{id:\"minecraft:paper\",count:1}},ElysiumArrives:0L}}")
    t.setblock(2, 0, 5, "elysiumcomforts:trading_station")
    t.cmd(f"data merge block {t.at(2, 0, 5)} {{ElysiumHeld:{{VillagerData:{{profession:\"minecraft:farmer\",level:1,type:\"minecraft:plains\"}}}}}}")
    t.cmd(f"fill {t.at(5, -1, 1)} {t.at(11, 3, 7)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(6, 0, 2)} {t.at(10, 2, 6)} minecraft:air")
    _summon(t, 7, 0, 4, "elysiumcomforts:pigeon", "{Age:0,InLove:600,PersistenceRequired:1b,Tags:[\"dove\"]}")
    _summon(t, 9, 0, 4, "elysiumcomforts:pigeon", "{Age:0,InLove:600,PersistenceRequired:1b,Tags:[\"dove\"]}")
    t.advance(200)
    t.expect_state(2, 0, 2, "elysiumcomforts:mailbox[full=true]", "the parcel never landed in the mailbox")
    t.expect_item(2, 0, 2, "minecraft:paper", 1, "the landed parcel is not in the mailbox")
    t.expect_number(2, 0, 5, "ElysiumGoes", 2, "the trading station counted no work for its trader")
    chicks = _count(t, f"@e[type=elysiumcomforts:pigeon,x={t.ox + 8},y={t.oy},z={t.oz + 4},distance=..8,tag=!dove]")
    t.expect(chicks >= 1, "two pigeons in love hatched nothing in 200 ticks")
    t.cmd(f"kill @e[type=elysiumcomforts:pigeon,x={t.ox + 8},y={t.oy},z={t.oz + 4},distance=..10]")


# --- core ----------------------------------------------------------------

@scenario("core: the spawner feature places, neutronium ore seeds stone, the enforcer stands in the sun, gear carries its attributes")
def core_world_and_gear(t):
    out = t.cmd(f"place feature elysium:brutal_spawner {t.at(2, 0, 2)}")
    t.expect("Placed" in out, f"the brutal spawner feature would not place: {out[:80]}")
    t.cmd(f"fill {t.at(6, 0, 2)} {t.at(14, 6, 10)} minecraft:stone")
    out = t.cmd(f"place feature elysium:neutronium_ore {t.at(10, 3, 6)}")
    t.expect("Placed" in out, f"the neutronium ore feature would not place in stone: {out[:80]}")
    ore = t.cmd(f"fill {t.at(6, 0, 2)} {t.at(14, 6, 10)} minecraft:stone replace elysium:neutronium_ore")
    t.expect("filled" in ore, "the placed ore feature left no neutronium ore in the stone")
    t.cmd(f"fill {t.at(6, 0, 2)} {t.at(14, 6, 10)} minecraft:air")
    # the enforcer under the noon sun beside a zombie
    t.cmd("time set 6000")
    t.cmd(f"fill {t.at(17, -1, 1)} {t.at(21, 3, 3)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(18, 0, 2)} {t.at(20, 2, 2)} minecraft:air")
    t.cmd(f"fill {t.at(17, 3, 1)} {t.at(21, 3, 3)} minecraft:air")      # open to the sky
    _summon(t, 18, 0, 2, "elysium:imperial_enforcer", "{PersistenceRequired:1b,Tags:[\"enforcer\"]}")
    _summon(t, 20, 0, 2, "minecraft:zombie", "{PersistenceRequired:1b,Tags:[\"sunzombie\"]}")
    t.advance(2)
    t.cmd("item replace entity @e[tag=sunzombie,limit=1] armor.head with minecraft:air")
    # gear attributes on a zombie's hands and back
    _summon(t, 18, 0, 6, "minecraft:zombie", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"armed\"],"
            "HandItems:[{id:\"elysium:voidcut_blade\",count:1},{}],"
            "ArmorItems:[{id:\"elysium:neutronium_boots\",count:1},{id:\"elysium:neutronium_leggings\",count:1},"
            "{id:\"elysium:neutronium_chestplate\",count:1},{id:\"elysium:neutronium_helmet\",count:1}]}")
    t.advance(100)
    fire = _entity_number(t, "@e[tag=enforcer,limit=1]", "Fire")
    zfire = _entity_number(t, "@e[tag=sunzombie,limit=1]", "Fire")
    t.expect(fire is not None and fire <= 0, f"the imperial enforcer burns in the sun (Fire {fire})")
    t.expect(zfire is not None and zfire > 0, f"the control zombie did not burn, so the sun test proves nothing (Fire {zfire})")
    import re
    dmg = t.cmd("attribute @e[tag=armed,limit=1] minecraft:generic.attack_damage get")
    m = re.search(r"is (\d+(?:\.\d+)?)", dmg)
    t.expect(m is not None and float(m.group(1)) > 3.0, f"a voidcut blade gives its holder no attack damage: {dmg[:80]}")
    kb = t.cmd("attribute @e[tag=armed,limit=1] minecraft:generic.knockback_resistance get")
    m = re.search(r"is (\d+(?:\.\d+)?)", kb)
    t.expect(m is not None and float(m.group(1)) >= 0.1, f"a full neutronium set gives no set bonus: {kb[:80]}")
    t.cmd("kill @e[tag=enforcer]")
    t.cmd("kill @e[tag=sunzombie]")
    t.cmd("kill @e[tag=armed]")


@scenario("core: a crafter grinds a rune into gem dust")
def core_gem_grinding(t):
    t.setblock(2, 0, 2, "minecraft:crafter[orientation=down_north]")
    t.chest(2, -1, 2)
    t.cmd(f"item replace block {t.at(2, 0, 2)} container.4 with elysium:voidward_rune 1")
    t.setblock(3, 0, 2, "minecraft:redstone_block")
    t.advance(10)
    t.setblock(3, 0, 2, "minecraft:air")
    t.expect_item(2, -1, 2, "elysium:gem_dust", 1, "the crafter made no gem dust of the rune")


# --- dungeons ------------------------------------------------------------

@scenario("dungeons: a rift frame ring stands, and portal blocks set without a key do not")
def dungeons_frame(t):
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(5, 4, 2)} elysiumdungeons:rift_frame hollow")   # a ring, not a wall
    t.fill((3, 1, 2), (4, 3, 2), "elysiumdungeons:rift_portal[axis=x]")
    t.advance(5)
    t.expect_state(2, 2, 2, "elysiumdungeons:rift_frame", "the frame ring did not stand")
    # a portal only a key lights: blocks set by command are not registered and clear themselves
    t.expect_state(3, 1, 2, "minecraft:air", "portal blocks set without a key stayed, unregistered")


# --- enchanting ----------------------------------------------------------

@scenario("enchanting: a venom blade poisons its victim, and a vitality chestplate adds health")
def enchanting_blades(t):
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(7, 3, 7)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(6, 2, 6)} minecraft:air")
    _summon(t, 3, 0, 4, "minecraft:villager", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"victim\"]}")
    _summon(t, 5, 0, 4, "minecraft:husk", "{PersistenceRequired:1b,Tags:[\"venomous\"],"
            "HandItems:[{id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:enchantments\":{levels:{\"elysiumenchanting:venom\":1}}}},{}]}")
    _summon(t, 10, 0, 10, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"vital\"],"
            "ArmorItems:[{},{},{id:\"minecraft:iron_chestplate\",count:1,components:{\"minecraft:enchantments\":{levels:{\"elysiumenchanting:vitality\":1}}}},{}]}")
    poisoned = False
    for _ in range(15):                                  # venom lasts seconds: look while the husk fights
        t.advance(20)
        if "minecraft:poison" in t.cmd("data get entity @e[tag=victim,limit=1] active_effects"):
            poisoned = True
            break
    t.expect(poisoned, "the venom blade's victim was never poisoned in 300 ticks of fighting")
    import re
    hp = t.cmd("attribute @e[tag=vital,limit=1] minecraft:generic.max_health get")
    m = re.search(r"is (\d+(?:\.\d+)?)", hp)
    t.expect(m is not None and float(m.group(1)) > 20.0, f"a vitality chestplate adds no health: {hp[:80]}")
    for tag in ("victim", "venomous", "vital"):
        t.cmd(f"kill @e[tag={tag}]")


# --- flora ---------------------------------------------------------------

@scenario("flora: a ripe loomflax drops fibre and a wild plant seeds, bone meal grows a sprout, emberwood burns long")
def flora_crops_and_wood(t):
    t.setblock(2, 0, 2, "minecraft:farmland")
    t.setblock(2, 1, 2, "elysiumflora:loomflax[age=7]")
    t.cmd(f"setblock {t.at(2, 1, 2)} minecraft:air destroy")
    t.setblock(5, 0, 2, "minecraft:grass_block")
    t.setblock(5, 1, 2, "elysiumflora:wild_loomflax")
    t.cmd(f"setblock {t.at(5, 1, 2)} minecraft:air destroy")
    t.advance(5)
    fibre = _count(t, f"@e[type=minecraft:item,x={t.ox + 2},y={t.oy + 1},z={t.oz + 2},distance=..3,nbt={{Item:{{id:\"elysiumflora:loomflax_fibre\"}}}}]")
    seeds = _count(t, f"@e[type=minecraft:item,x={t.ox + 5},y={t.oy + 1},z={t.oz + 2},distance=..3,nbt={{Item:{{id:\"elysiumflora:loomflax_seeds\"}}}}]")
    t.expect(fibre >= 1, "a ripe loomflax dropped no fibre")
    t.expect(seeds >= 1, "a wild loomflax dropped no seeds")
    t.cmd(f"kill @e[type=minecraft:item,x={t.ox + 4},y={t.oy},z={t.oz + 2},distance=..8]")
    # bone meal from a dispenser grows a young crop
    t.setblock(8, 0, 2, "minecraft:farmland")
    t.setblock(8, 1, 2, "elysiumflora:loomflax[age=0]")
    t.setblock(7, 1, 2, "minecraft:dispenser[facing=east]")
    t.cmd(f"item replace block {t.at(7, 1, 2)} container.0 with minecraft:bone_meal 32")
    for _ in range(10):
        t.setblock(6, 1, 2, "minecraft:redstone_block")
        t.advance(2)
        t.setblock(6, 1, 2, "minecraft:air")
        t.advance(2)
    t.expect(not t.state_is(8, 1, 2, "elysiumflora:loomflax[age=0]"), "ten bone meals grew the loomflax not at all")
    # emberwood charcoal burns longer than charcoal
    t.setblock(2, 0, 6, "minecraft:furnace[facing=north]")
    t.cmd(f"item replace block {t.at(2, 0, 6)} container.0 with minecraft:raw_iron 8")
    t.cmd(f"item replace block {t.at(2, 0, 6)} container.1 with elysiumflora:emberwood_charcoal 1")
    t.advance(3)
    t.expect_number(2, 0, 6, "BurnTime", 1601, "emberwood charcoal burns no longer than the game's charcoal")
    # an emberwood log smelts into emberwood charcoal
    t.setblock(5, 0, 6, "minecraft:furnace[facing=north]")
    t.cmd(f"item replace block {t.at(5, 0, 6)} container.0 with elysiumflora:emberwood_log 1")
    t.cmd(f"item replace block {t.at(5, 0, 6)} container.1 with minecraft:coal 1")
    t.advance(240)
    t.expect_item(5, 0, 6, "elysiumflora:emberwood_charcoal", 1, "an emberwood log did not smelt into emberwood charcoal")


# --- industry ------------------------------------------------------------

@scenario("industry: a turbine idles without demand, a short grid shares, an alternator feeds a crusher, a motor turns one")
def industry_grid_rules(t):
    # idle: coal untouched with nothing asking
    t.setblock(2, 0, 2, "elysiumindustry:steam_turbine")
    t.hopper_into(2, 0, 2, "minecraft:coal", 4)
    # short grid: one turbine, two rolling mills
    _grid(t, 6)
    t.setblock(6, 0, 6, "elysiumindustry:rolling_mill")
    t.hopper_into(6, 0, 6, "minecraft:copper_ingot", 4)
    t.setblock(5, 0, 7, "elysiumindustry:copper_wire")
    t.setblock(5, 0, 8, "elysiumindustry:rolling_mill")
    t.hopper_into(5, 0, 8, "minecraft:copper_ingot", 4)
    # alternator: rotation into the grid. It draws six dynamos' torque, more than one
    # engine has, so three oil engines share one brass line
    t.setblock(2, 0, 12, "elysiumkinetics:oil_engine[axis=x]")
    t.hopper_into(2, 0, 12, "minecraft:coal", 8)
    t.setblock(3, 0, 12, "elysiumkinetics:brass_shaft[axis=x]")
    t.setblock(4, 0, 12, "elysiumindustry:alternator[axis=x]")
    t.setblock(5, 0, 12, "elysiumkinetics:brass_shaft[axis=x]")
    t.setblock(6, 0, 12, "elysiumkinetics:oil_engine[axis=x]")   # a second engine at the far end of the same line
    t.hopper_into(6, 0, 12, "minecraft:coal", 8)
    t.setblock(4, 0, 13, "elysiumindustry:copper_wire")
    t.setblock(4, 0, 14, "elysiumindustry:crusher")
    t.hopper_into(4, 0, 14, "minecraft:bone_block", 4)
    # motor: the grid into rotation, down a shaft into a millstone
    # a motor draws 256, more than one turbine's 120; a supply above a copper line's 256
    # burns it, so two turbines (240) feed the motor at nearly full share
    for dz in (16, 17):
        t.setblock(2, 0, dz, "elysiumindustry:steam_turbine")
        t.hopper_into(2, 0, dz, "minecraft:coal_block", 8)
        t.setblock(3, 0, dz, "elysiumindustry:copper_wire")
    t.setblock(4, 0, 17, "elysiumindustry:copper_wire")
    t.setblock(5, 0, 17, "elysiumindustry:copper_wire")
    # a motor spins at 256 rpm, which shears every Age I shaft: it drives a steel plant directly
    t.setblock(5, 1, 17, "elysiumindustry:motor[axis=x]")
    t.setblock(6, 1, 17, "elysiumindustry:crusher")
    t.setblock(6, 2, 17, "minecraft:hopper[facing=down]")
    t.cmd(f"item replace block {t.at(6, 2, 17)} container.0 with minecraft:bone_block 4")
    t.advance(900)
    t.expect_number(2, 0, 2, "burning", 0, "burning is missing")
    burning = t.number(2, 0, 2, "burning")
    t.expect(burning is not None and burning <= 0, f"a turbine with nothing asking lit its coal anyway (burning {burning})")
    share = t.number(6, 0, 6, "share")
    t.expect(share is not None and 0.3 < share < 0.99, f"two mills on one turbine should share it short of full (share {share})")
    _slot(t, 4, 0, 14, "items", 1, "minecraft:bone_meal", 1, "the crusher on the alternator's wire crushed nothing")
    _slot(t, 6, 1, 17, "items", 1, "minecraft:bone_meal", 1, "the motor turned no crusher beside it")


@scenario("industry: a coke oven forms from its bricks and cokes coal")
def industry_multiblock(t):
    t.fill((2, 0, 2), (4, 2, 4), "elysiumindustry:coke_brick")
    t.setblock(3, 1, 3, "minecraft:air")
    t.setblock(3, 1, 2, "elysiumindustry:coke_oven")
    t.advance(30)
    t.expect_state(3, 1, 2, "elysiumindustry:coke_oven[formed=true]", "the coke oven did not form inside its bricks")
    t.cmd(f"data merge block {t.at(3, 1, 2)} {{items:{{Size:2,Items:[{{Slot:0,id:\"minecraft:coal\",count:4}}]}}}}")
    t.advance(1000)
    _slot(t, 3, 1, 2, "items", 1, "elysiumindustry:coke", 1, "the formed coke oven coked no coal")
    t.setblock(2, 0, 2, "minecraft:air")
    t.advance(30)
    t.expect_state(3, 1, 2, "elysiumindustry:coke_oven[formed=false]", "the oven stayed formed with a brick gone")


# --- the library ---------------------------------------------------------

@scenario("library: the effects do what they say, a brew brews, a hardened spawn is marked, the ceilings are lifted")
def lib_effects(t):
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(9, 3, 9)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(8, 2, 8)} minecraft:air")
    for i, tag in enumerate(("bleeder", "salved", "sundered", "stunned_hitter", "stunned_victim", "hardened")):
        _summon(t, 2 + i, 0, 4, "minecraft:husk", f"{{NoAI:1b,PersistenceRequired:1b,Tags:[\"{tag}\"]}}")   # husks do not burn at noon
    t.advance(2)
    t.cmd("effect give @e[tag=bleeder,limit=1] elysiumlib:bleeding 10 0 true")
    t.cmd("data merge entity @e[tag=salved,limit=1] {Health:5f}")
    t.cmd("effect give @e[tag=salved,limit=1] elysiumlib:salve 5 0 true")
    t.cmd("effect give @e[tag=sundered,limit=1] elysiumlib:sundering 30 2 true")
    t.cmd("damage @e[tag=sundered,limit=1] 4 minecraft:magic")
    t.cmd("effect give @e[tag=stunned_hitter,limit=1] elysiumlib:stunned 30 0 true")
    t.cmd("damage @e[tag=stunned_victim,limit=1] 4 minecraft:mob_attack by @e[tag=stunned_hitter,limit=1]")
    t.advance(60)
    bleed = _entity_number(t, "@e[tag=bleeder,limit=1]", "Health")
    t.expect(bleed is not None and bleed < 20, f"bleeding took no health ({bleed})")
    salve = _entity_number(t, "@e[tag=salved,limit=1]", "Health")
    t.expect(salve is not None and salve > 5, f"salve healed nothing ({salve})")
    sund = _entity_number(t, "@e[tag=sundered,limit=1]", "Health")
    t.expect(sund is not None and sund < 16, f"sundering made 4 damage no bigger ({sund})")
    stunned = _entity_number(t, "@e[tag=stunned_victim,limit=1]", "Health")
    t.expect(stunned is not None and stunned >= 20, f"a stunned attacker still landed its blow ({stunned})")
    hardened = t.cmd("data get entity @e[tag=hardened,limit=1] neoforge:attachments")
    if "Test passed" in t.cmd("execute if entity @a"):        # the tier comes from players; none online means no mark
        t.expect("elysiumlib:tier_hardened" in hardened, f"a fresh spawn was not marked hardened: {hardened[:120]}")
    ceiling = t.cmd("attribute @e[tag=hardened,limit=1] minecraft:generic.max_health base set 5000")
    got = t.cmd("attribute @e[tag=hardened,limit=1] minecraft:generic.max_health base get")
    t.expect("5000" in got, f"the vanilla attribute ceiling still clamps max health: {got[:80]}")
    # a brew
    t.setblock(8, 0, 2, "minecraft:brewing_stand")
    t.cmd(f"data merge block {t.at(8, 0, 2)} {{Items:[{{Slot:0b,id:\"minecraft:potion\",count:1,components:{{\"minecraft:potion_contents\":{{potion:\"minecraft:awkward\"}}}}}},"
          f"{{Slot:3b,id:\"minecraft:shulker_shell\",count:1}},{{Slot:4b,id:\"minecraft:blaze_powder\",count:1}}]}}")
    t.advance(450)
    brew = t.cmd(f"data get block {t.at(8, 0, 2)} Items[0]")
    t.expect("elysiumlib:" in brew, f"the shulker shell brewed no potion of ours: {brew[:120]}")
    for tag in ("bleeder", "salved", "sundered", "stunned_hitter", "stunned_victim", "hardened"):
        t.cmd(f"kill @e[tag={tag}]")


# --- the eight mobs ------------------------------------------------------

@scenario("mobs: the variants' abilities run, and the Choir and the Praetor keep their phases")
def mobs_abilities_and_bosses(t):
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(21, 5, 21)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(20, 4, 20)} minecraft:air")
    _summon(t, 3, 0, 3, "elysiummobs:drone", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"bulwark\"],ElysiumVariant:\"elysiummobs:interdictor\",ElysiumLevel:1}")
    _summon(t, 6, 0, 3, "elysiummobs:harrier", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"knitting\"],ElysiumVariant:\"elysiummobs:flenser\",ElysiumLevel:1,Health:5f}")
    _summon(t, 9, 0, 3, "elysiummobs:scavenger", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"swift\"],ElysiumVariant:\"elysiummobs:scuttler\",ElysiumLevel:1}")
    _summon(t, 12, 0, 3, "elysiummobs:drone", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"relay\"],ElysiumVariant:\"elysiummobs:relay\",ElysiumLevel:1}")
    _summon(t, 14, 0, 3, "minecraft:zombie", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"aura_taker\"]}")
    _summon(t, 4, 0, 12, "elysiummobs:choir", "{PersistenceRequired:1b,Tags:[\"choir\"],Health:50f}")
    _summon(t, 14, 0, 12, "elysiummobs:praetor", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"praetor\"]}")
    t.advance(120)
    absorb = _entity_number(t, "@e[tag=bulwark,limit=1]", "AbsorptionAmount")
    t.expect(absorb is not None and absorb >= 3.9, f"the interdictor drone raised no bulwark ({absorb})")
    knit = _entity_number(t, "@e[tag=knitting,limit=1]", "Health")
    t.expect(knit is not None and knit > 5, f"the flenser harrier knitted no health ({knit})")
    swift = t.cmd("data get entity @e[tag=swift,limit=1] active_effects")
    t.expect("minecraft:speed" in swift, "the scuttler scavenger is not swift with no target")
    aura = t.cmd("data get entity @e[tag=aura_taker,limit=1] active_effects")
    t.expect("minecraft:strength" in aura, "the relay drone's standard gave the zombie beside it no strength")
    phase = _entity_number(t, "@e[tag=choir,limit=1]", "ElysiumPhase")
    t.expect(phase is not None and phase >= 1, f"the Choir at half health did not enter its next phase ({phase})")
    grunts = sum(_count(t, f"@e[type=elysiummobs:{kind},x={t.ox + 4},y={t.oy},z={t.oz + 12},distance=..7]")
                 for kind in ("scavenger", "whisper", "harrier"))
    t.expect(grunts >= 2, f"the Choir called no grunts ({grunts})")
    shield = _entity_number(t, "@e[tag=praetor,limit=1]", "AbsorptionAmount")
    t.expect(shield is not None and shield > 0, f"the Praetor regenerated no shield ({shield})")
    t.cmd(f"kill @e[x={t.ox + 11},y={t.oy},z={t.oz + 11},distance=..24,type=!minecraft:player]")


# --- agronomy ------------------------------------------------------------

@scenario("agronomy: a pot grows a carrot on farmland soil, a copper vein makes raw copper on a grid, a pot makes stock over a fire")
def agronomy_pots_veins_kitchen(t):
    # a pot with soil and a sprout, lit from above
    t.setblock(2, 0, 2, "elysiumagronomy:pot")
    t.setblock(2, 2, 2, "minecraft:glowstone")
    t.cmd(f"data merge block {t.at(2, 0, 2)} {{items:{{Size:3,Items:[{{Slot:0,id:\"minecraft:carrot\",count:1}},{{Slot:2,id:\"minecraft:farmland\",count:1}}]}}}}")
    # a copper vein generator on a low grid
    _grid(t, 6)
    t.setblock(6, 0, 6, "elysiumagronomy:copper_generator")
    # a cooking pot over a campfire, told what it is making
    t.setblock(2, 0, 10, "minecraft:campfire")
    t.setblock(2, 1, 10, "elysiumagronomy:cooking_pot")
    t.cmd(f"data merge block {t.at(2, 1, 10)} {{contents:[{{id:\"minecraft:bone\",count:1}},{{id:\"elysiumagronomy:onion\",count:1}},{{id:\"minecraft:carrot\",count:1}}],cooking:1b,making:\"elysiumagronomy:stock\",progress:0}}")
    t.advance(1400)
    _slot(t, 2, 0, 2, "items", 1, "minecraft:carrot", 2, "the pot grew no carrots out of its farmland soil")
    _slot(t, 6, 0, 6, "items", 0, "minecraft:raw_copper", 1, "the copper vein made no raw copper on a full grid")
    made = t.cmd(f"data get block {t.at(2, 1, 10)} made")
    t.expect("elysiumagronomy:stock" in made, f"the cooking pot over its fire made no stock: {made[:100]}")


@scenario("agronomy: a bee flies without falling and a magma carp lives in lava; a nest and a wild patch place")
def agronomy_creatures_and_features(t):
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(9, 24, 9)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(8, 23, 8)} minecraft:air")
    _summon(t, 5, 20, 5, "elysiumagronomy:iron_bee", "{PersistenceRequired:1b,Tags:[\"bee\"]}")
    t.fill((12, 0, 2), (14, 2, 4), "minecraft:stone")
    t.fill((13, 1, 3), (13, 2, 3), "minecraft:lava")
    t.cmd(f"fill {t.at(12, 0, 2)} {t.at(14, 2, 4)} minecraft:lava replace minecraft:stone")
    t.cmd(f"fill {t.at(11, -1, 1)} {t.at(15, 3, 5)} minecraft:stone hollow")
    t.cmd(f"fill {t.at(12, 0, 2)} {t.at(14, 2, 4)} minecraft:lava")
    _summon(t, 13, 1, 3, "elysiumagronomy:magma_carp", "{PersistenceRequired:1b,Tags:[\"carp\"]}")
    t.advance(200)
    bee = _entity_number(t, "@e[tag=bee,limit=1]", "Health")
    t.expect(bee is not None and bee >= 10, f"the iron bee took fall damage or died ({bee})")
    carp = _entity_number(t, "@e[tag=carp,limit=1]", "Health")
    t.expect(carp is not None and carp > 0, f"the magma carp did not survive its lava ({carp})")
    t.cmd("kill @e[tag=bee]")
    t.cmd("kill @e[tag=carp]")
    t.cmd(f"fill {t.at(11, -1, 1)} {t.at(15, 3, 5)} minecraft:air")
    t.setblock(18, 0, 2, "minecraft:grass_block")
    out = t.cmd(f"place feature elysiumagronomy:plains_nest {t.at(18, 1, 2)}")
    t.expect("Placed" in out, f"the plains nest feature would not place: {out[:80]}")
    t.fill((18, 0, 6), (21, 0, 9), "minecraft:grass_block")
    out = t.cmd(f"place feature elysiumagronomy:wild_cabbage {t.at(19, 1, 7)}")
    t.expect("Placed" in out, f"the wild cabbage patch would not place on grass: {out[:80]}")


# --- arcana --------------------------------------------------------------

@scenario("arcana: a cluster falls when its support goes")
def arcana_geodes(t):
    # random ticks reach only chunks a player is near: the budding growth is the client stage's
    t.setblock(8, 0, 2, "minecraft:stone")
    t.setblock(8, 1, 2, "elysiumarcana:iron_cluster[facing=up]")
    t.advance(2)
    t.setblock(8, 0, 2, "minecraft:air")
    t.advance(5)
    t.expect_state(8, 1, 2, "minecraft:air", "the iron cluster stayed up with nothing under it")
    shards = _count(t, f"@e[type=minecraft:item,x={t.ox + 8},y={t.oy + 1},z={t.oz + 2},distance=..4,nbt={{Item:{{id:\"elysiumarcana:iron_shard\"}}}}]")
    t.expect(shards >= 1, "the fallen cluster dropped no shards")
    t.cmd(f"kill @e[type=minecraft:item,x={t.ox + 8},y={t.oy},z={t.oz + 2},distance=..6]")


# --- armory --------------------------------------------------------------

@scenario("armory: set_parts builds gear, bounce boots break a fall, snow-walker boots stand on powder snow")
def armory_gear(t):
    t.chest(2, 0, 2)
    t.cmd(f"item replace block {t.at(2, 0, 2)} container.0 with elysiumarmory:sword 1")
    out = t.cmd(f"item modify block {t.at(2, 0, 2)} container.0 {{function:\"elysiumarmory:set_parts\",materials:[\"elysiumarmory:iron\"]}}")
    built = t.cmd(f"data get block {t.at(2, 0, 2)} Items[0].components")
    t.expect("elysiumarmory:parts" in built and "elysiumarmory:stats" in built,
             f"set_parts built no parts and stats onto the sword: {built[:120]}")
    t.cmd(f"fill {t.at(5, -1, 1)} {t.at(13, 26, 5)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(6, 0, 2)} {t.at(12, 25, 4)} minecraft:air")
    _summon(t, 7, 22, 3, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"bouncer\"],"
            "ArmorItems:[{id:\"elysiumarmory:boots\",count:1,components:{\"elysiumarmory:traits\":{bounce:1}}},{},{},{}]}")
    _summon(t, 11, 22, 3, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"faller\"]}")
    t.fill((16, -1, 2), (16, 1, 2), "minecraft:powder_snow")
    _summon(t, 16, 2, 2, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"snowwalker\"],"
            "ArmorItems:[{id:\"elysiumarmory:boots\",count:1,components:{\"elysiumarmory:traits\":{snow_walker:1}}},{},{},{}]}")
    t.advance(80)
    bounced = _entity_number(t, "@e[tag=bouncer,limit=1]", "Health")
    fell = _entity_number(t, "@e[tag=faller,limit=1]", "Health")
    if fell is not None and fell < 19.5:
        t.expect(bounced is not None and bounced > fell + 5, f"bounce boots broke the fall no better than none ({bounced} against {fell})")
    snowy = _entity_number(t, "@e[tag=snowwalker,limit=1]", "Pos[1]")
    t.expect(snowy is not None and snowy >= t.oy + 1.9, f"snow-walker boots sank into the powder snow (y {snowy})")
    for tag in ("bouncer", "faller", "snowwalker"):
        t.cmd(f"kill @e[tag={tag}]")
    # gear assembly needs the bench's own arrangement: the validator's sample gear covers it


# --- arsenal -------------------------------------------------------------

@scenario("arsenal: totems tend a field and heal, a barbed arrow bleeds")
def arsenal_totems_and_arrows(t):
    t.setblock(7, 0, 7, "elysiumarsenal:growth_totem")
    t.cmd(f"fill {t.at(1, 0, 1)} {t.at(13, 0, 13)} minecraft:farmland")
    t.cmd(f"fill {t.at(1, 1, 1)} {t.at(13, 1, 13)} minecraft:wheat[age=0]")
    t.setblock(7, 0, 7, "elysiumarsenal:growth_totem")
    t.setblock(7, 1, 7, "minecraft:air")
    t.setblock(14, 0, 6, "elysiumarsenal:radiance_totem")
    _summon(t, 16, 0, 6, "minecraft:cow", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"lit\"],Health:4f}")
    _summon(t, 16, 0, 8, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"seared\"]}")
    t.cmd(f"fill {t.at(19, -1, 1)} {t.at(23, 8, 5)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(20, 0, 2)} {t.at(22, 7, 4)} minecraft:air")
    _summon(t, 21, 0, 3, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"stuck\"]}")
    _summon(t, 21, 5, 3, "minecraft:arrow", "{Motion:[0.0,-2.0,0.0],item:{id:\"elysiumarsenal:barbed_arrow\",count:1}}")
    t.advance(30)
    bled = t.cmd("data get entity @e[tag=stuck,limit=1] active_effects")
    hurt = _entity_number(t, "@e[tag=stuck,limit=1]", "Health")
    t.expect("elysiumlib:bleeding" in bled or (hurt is not None and hurt < 20),
             f"the barbed arrow neither bled nor hurt its target: {bled[:100]}")
    t.cmd("kill @e[tag=stuck]")
    t.advance(3970)
    out = t.cmd(f"fill {t.at(1, 1, 1)} {t.at(13, 1, 13)} minecraft:air replace minecraft:wheat[age=0]")
    import re
    m = re.search(r"filled (\d+)", out)
    left = int(m.group(1)) if m else 0
    t.expect(left < 168, f"the growth totem grew none of the 168 wheat around it in 4000 ticks ({left} still at age 0)")
    seared = _entity_number(t, "@e[tag=seared,limit=1]", "Health")
    t.expect(seared is None or seared < 20, f"the radiance totem left the husk unharmed ({seared})")
    for tag in ("lit", "seared"):
        t.cmd(f"kill @e[tag={tag}]")


# --- atelier -------------------------------------------------------------

@scenario("atelier: fences join, a door opens to redstone, a ladder holds a fall, a waterlogged balcony spreads its water")
def atelier_pieces(t):
    t.setblock(2, 0, 2, "elysiumatelier:andesite_fence")
    t.setblock(3, 0, 2, "elysiumatelier:andesite_fence")
    t.setblock(2, 0, 5, "elysiumatelier:acacia_wall")
    t.setblock(3, 0, 5, "elysiumatelier:acacia_wall")
    t.setblock(6, 0, 2, "elysiumatelier:andesite_door[half=lower,facing=north]")
    t.setblock(6, 1, 2, "elysiumatelier:andesite_door[half=upper,facing=north]")
    t.setblock(7, 0, 2, "minecraft:redstone_block")
    t.fill((10, 0, 3), (10, 20, 3), "minecraft:stone")
    t.fill((10, 0, 2), (10, 20, 2), "elysiumatelier:acacia_ladder[facing=north]")
    t.cmd(f"fill {t.at(9, -1, 1)} {t.at(11, 22, 3)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(10, 0, 3)} {t.at(10, 20, 3)} minecraft:stone")
    t.cmd(f"fill {t.at(10, 0, 2)} {t.at(10, 20, 2)} elysiumatelier:acacia_ladder[facing=north]")
    _summon(t, 10, 21, 2, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"climber\"]}")
    t.cmd(f"fill {t.at(17, -1, 1)} {t.at(19, 22, 3)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(18, 0, 3)} {t.at(18, 20, 3)} minecraft:stone")
    t.cmd(f"fill {t.at(18, 0, 2)} {t.at(18, 20, 2)} minecraft:ladder[facing=north]")
    _summon(t, 18, 21, 2, "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"vanilla_climber\"]}")
    t.setblock(14, 0, 2, "elysiumatelier:acacia_balcony[waterlogged=true]")
    t.setblock(15, 0, 2, "minecraft:stone")
    t.advance(2)
    t.setblock(15, 0, 2, "minecraft:air")           # a neighbour changes: the water should follow
    t.advance(60)
    t.expect_state(2, 0, 2, "elysiumatelier:andesite_fence[east=true]", "two andesite fences do not join")
    t.expect_state(2, 0, 5, "elysiumatelier:acacia_wall[east=low]", "two acacia walls do not join")
    t.expect_state(6, 0, 2, "elysiumatelier:andesite_door[open=true]", "the andesite door ignores redstone")
    climber = _entity_number(t, "@e[tag=climber,limit=1]", "Health")
    vanilla = _entity_number(t, "@e[tag=vanilla_climber,limit=1]", "Health")
    t.expect(climber is not None and vanilla is not None and climber >= vanilla - 0.5,
             f"our ladder held the husk worse than the game's ({climber} against {vanilla})")
    t.expect(t.state_is(15, 0, 2, "minecraft:water") or "water" in t.cmd(f"data get block {t.at(15, 0, 2)}").lower()
             or t.cmd(f"execute if block {t.at(15, 0, 2)} minecraft:water").count("passed") > 0,
             "a waterlogged balcony spread no water to the air beside it")
    t.cmd("kill @e[tag=climber]")
    t.cmd("kill @e[tag=vanilla_climber]")


# --- solarpunk, the rest --------------------------------------------------

@scenario("solarpunk: a capstan turns high up, a bioreactor makes briquettes, a kiln fires on power, a reclaimer draws water")
def solarpunk_more(t):
    t.setblock(2, 0, 2, "elysiumsolarpunk:wind_capstan")
    t.setblock(6, 0, 2, "elysiumsolarpunk:algae_bioreactor")
    t.setblock(7, 0, 2, "minecraft:water")
    t.cmd(f"data merge block {t.at(6, 0, 2)} {{energy:16000}}")
    t.setblock(10, 0, 2, "elysiumsolarpunk:ceramic_kiln")
    t.cmd(f"data merge block {t.at(10, 0, 2)} {{energy:16000,slots:{{Items:[{{Slot:0,id:\"minecraft:clay_ball\",count:8}}]}}}}")
    t.setblock(14, 0, 2, "elysiumsolarpunk:water_reclaimer")
    t.cmd(f"data merge block {t.at(14, 0, 2)} {{energy:16000}}")
    t.setblock(15, 0, 2, "minecraft:cauldron")
    t.setblock(18, 0, 2, "elysiumsolarpunk:aquaponics_tank")
    t.cmd(f"data merge block {t.at(18, 0, 2)} {{energy:16000,slots:{{Items:[{{Slot:0,id:\"minecraft:carrot\",count:4}}]}}}}")
    t.setblock(19, 0, 2, "minecraft:water")
    t.setblock(18, 2, 2, "minecraft:glowstone")
    t.advance(1200)
    t.expect_number(2, 0, 2, "energy", 1000, "a wind capstan at y 200 with four open sides made almost nothing")
    t.expect_item(6, 0, 2, "elysiumsolarpunk:biomass_briquette", 1, "the bioreactor made no briquette with sky, water and power", path="output.Items")
    _slot(t, 10, 0, 2, "slots", 2, "minecraft:brick", 1, "the kiln on power fired no brick")
    t.expect_number(14, 0, 2, "tank.Fluid.amount", 1, "the reclaimer drew no water from a clear sky")
    caul = t.cmd(f"execute if block {t.at(15, 0, 2)} minecraft:water_cauldron")
    t.expect("Test passed" in caul, "the reclaimer filled no cauldron beside it")
    _slot(t, 18, 0, 2, "slots", 2, "minecraft:cod", 1, "the tank raised no fish after a thousand powered ticks")
    t.setblock(7, 0, 2, "minecraft:air")
    t.setblock(19, 0, 2, "minecraft:air")


# --- space, the machines and the worlds -----------------------------------

@scenario("space: a separator splits water, a refinery cracks oil, a diesel generator burns diesel, a panel makes power, a distributor fills a sealed room")
def space_machines(t):
    t.setblock(2, 0, 2, "elysiumspace:water_separator")
    t.cmd(f"data merge block {t.at(2, 0, 2)} {{Energy:10000,Tank0:{{Fluid:{{id:\"minecraft:water\",amount:3000}}}}}}")
    t.setblock(2, 0, 3, "elysiumspace:fluid_tank")            # the separator pushes its oxygen into a tank beside it
    t.setblock(5, 0, 2, "elysiumspace:fuel_refinery")
    t.cmd(f"data merge block {t.at(5, 0, 2)} {{Energy:10000,Tank0:{{Fluid:{{id:\"elysiumspace:oil\",amount:1000}}}}}}")
    t.setblock(8, 0, 2, "elysiumspace:diesel_generator")
    t.cmd(f"data merge block {t.at(8, 0, 2)} {{Tank0:{{Fluid:{{id:\"elysiumspace:diesel\",amount:1000}}}}}}")
    t.setblock(11, 0, 2, "elysiumspace:solar_panel")
    t.cmd(f"fill {t.at(14, 0, 2)} {t.at(18, 4, 6)} minecraft:glass hollow")
    t.setblock(16, 1, 4, "elysiumspace:oxygen_distributor")
    t.cmd(f"data merge block {t.at(16, 1, 4)} {{Energy:20000,Tank0:{{Fluid:{{id:\"elysiumspace:oxygen\",amount:3000}}}}}}")
    t.advance(200)
    t.expect_number(2, 0, 2, "Tank2.Fluid.amount", 1, "the separator made no hydrogen of its water")
    t.expect_number(5, 0, 2, "Tank1.Fluid.amount", 1, "the refinery made no fuel of its oil")
    t.expect_number(8, 0, 2, "Energy", 100, "the diesel generator made no power of its diesel")
    t.expect_number(11, 0, 2, "Energy", 100, "the solar panel made nothing at noon")
    oxy = t.number(16, 1, 4, "Tank0.Fluid.amount")
    t.expect(oxy is not None and oxy < 3000, f"the distributor filled none of its sealed room (oxygen {oxy})")
    pushed = t.cmd(f"data get block {t.at(2, 0, 3)} Tank0")
    t.expect("elysiumspace:oxygen" in pushed, f"the separator pushed no oxygen into the tank beside it: {pushed[:80]}")


@scenario("space: gravity differs by world, the Cinderveil is ultrawarm, a rocket lands when loaded mid-flight")
def space_worlds(t):
    import re
    t.cmd("execute in elysiumspace:pale_moon run forceload add 0 0")
    t.cmd("execute in elysiumspace:pale_moon run fill 0 150 0 2 150 2 minecraft:barrier")
    t.cmd("execute in elysiumspace:pale_moon run summon minecraft:zombie 1 151 1 {NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"moonwalker\"]}")
    t.advance(2)
    grav = t.cmd("execute in elysiumspace:pale_moon run attribute @e[tag=moonwalker,limit=1] minecraft:generic.gravity get")
    m = re.search(r"is (\d+(?:\.\d+)?)", grav)
    t.expect(m is not None and float(m.group(1)) < 0.05, f"a zombie on the pale moon has the overworld's gravity: {grav[:80]}")
    t.cmd("execute in elysiumspace:pale_moon run kill @e[tag=moonwalker]")
    t.cmd("execute in elysiumspace:pale_moon run forceload remove 0 0")
    t.cmd("execute in elysiumspace:cinderveil run forceload add 0 0")
    t.cmd("execute in elysiumspace:cinderveil run fill 0 150 0 4 154 4 minecraft:barrier hollow")
    t.cmd("execute in elysiumspace:cinderveil run fill 1 151 1 3 153 3 minecraft:air")
    t.cmd("execute in elysiumspace:cinderveil run setblock 2 151 2 minecraft:dispenser[facing=up]")
    t.cmd("execute in elysiumspace:cinderveil run item replace block 2 151 2 container.0 with minecraft:water_bucket 1")
    t.cmd("execute in elysiumspace:cinderveil run setblock 3 151 2 minecraft:redstone_block")
    t.advance(5)
    above = t.cmd("execute in elysiumspace:cinderveil if block 2 152 2 minecraft:water")
    held = t.cmd("execute in elysiumspace:cinderveil run data get block 2 151 2 Items")
    t.expect("Test passed" not in above and "minecraft:bucket" in held and "water_bucket" not in held,
             f"water placed in the Cinderveil, or the bucket did not empty: {held[:80]}")
    t.cmd("execute in elysiumspace:cinderveil run fill 0 150 0 4 154 4 minecraft:air")
    t.cmd("execute in elysiumspace:cinderveil run forceload remove 0 0")
    _summon(t, 4, 10, 4, "elysiumspace:rocket", "{Phase:\"ASCENT\",Fuel:500,Tank:1000,Tags:[\"rocket\"]}")
    t.advance(80)
    phase = t.cmd("data get entity @e[tag=rocket,limit=1] Phase")
    t.expect("IDLE" in phase or "LANDING" in phase, f"a rocket loaded mid-flight neither landed nor idled: {phase[:60]}")
    t.cmd("kill @e[tag=rocket]")


@scenario("space: a void crawler shrugs off arrows, cinder boars stand in the overworld, moonfolk raise no golem")
def space_mobs(t):
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(9, 6, 9)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(8, 5, 8)} minecraft:air")
    _summon(t, 5, 0, 5, "elysiumspace:void_crawler", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"crawler\"]}")
    _summon(t, 5, 4, 5, "minecraft:arrow", "{Motion:[0.0,-1.5,0.0],damage:4.0d}")
    t.cmd(f"fill {t.at(11, -1, 1)} {t.at(19, 4, 9)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(12, 0, 2)} {t.at(18, 3, 8)} minecraft:air")
    t.cmd(f"kill @e[type=elysiumspace:cinder_boar,x={t.ox + 15},y={t.oy},z={t.oz + 5},distance=..12]")
    t.cmd(f"kill @e[type=minecraft:zoglin,x={t.ox + 15},y={t.oy},z={t.oz + 5},distance=..12]")
    _summon(t, 14, 0, 5, "elysiumspace:cinder_boar", "{Age:0,InLove:600,PersistenceRequired:1b,Tags:[\"boar\"]}")
    _summon(t, 16, 0, 5, "elysiumspace:cinder_boar", "{Age:0,InLove:600,PersistenceRequired:1b,Tags:[\"boar\"]}")
    t.cmd(f"fill {t.at(1, -1, 11)} {t.at(11, 4, 21)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 12)} {t.at(10, 3, 20)} minecraft:air")
    for i in range(5):
        _summon(t, 3 + i, 0, 16, "elysiumspace:moonfolk", "{PersistenceRequired:1b,Tags:[\"folk\"]}")
    _summon(t, 6, 0, 13, "minecraft:zombie", "{NoAI:1b,PersistenceRequired:1b,Tags:[\"menace\"]}")
    t.advance(400)
    crawler = _entity_number(t, f"@e[type=elysiumspace:void_crawler,x={t.ox + 5},y={t.oy},z={t.oz + 5},distance=..6,limit=1]", "Health")
    t.expect(crawler is not None and crawler >= 39.9, f"the void crawler took the arrow ({crawler})")
    # cinder boars are hoglins: they breed from a brain memory a fungus sets, not from InLove - client stage
    golems = _count(t, f"@e[type=minecraft:iron_golem,x={t.ox + 6},y={t.oy},z={t.oz + 16},distance=..16]")
    t.expect(golems == 0, f"the moonfolk raised {golems} iron golem(s)")
    boars = _count(t, f"@e[type=elysiumspace:cinder_boar,x={t.ox + 15},y={t.oy},z={t.oz + 5},distance=..8]")
    zoglins = _count(t, f"@e[type=minecraft:zoglin,x={t.ox + 15},y={t.oy},z={t.oz + 5},distance=..8]")
    t.expect(boars >= 2 and zoglins == 0, f"{boars} cinder boars and {zoglins} zoglins stand where two boars were put in the overworld")
    for tag in ("boar", "folk", "menace"):
        t.cmd(f"kill @e[tag={tag}]")
    t.cmd(f"kill @e[type=elysiumspace:void_crawler,x={t.ox + 5},y={t.oy},z={t.oz + 5},distance=..8]")


# --- npcs -----------------------------------------------------------------

@scenario("npcs: an envoy keeps its kind and its health, a passing one leaves, a gear smith trades as one")
def npcs_envoys_and_villagers(t):
    import re
    t.cmd(f"fill {t.at(1, -1, 1)} {t.at(9, 4, 9)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(2, 0, 2)} {t.at(8, 3, 8)} minecraft:air")
    _summon(t, 4, 0, 5, "elysiumnpcs:envoy", "{Kind:\"queen\",Transient:0b,Tags:[\"staying\"]}")
    _summon(t, 6, 0, 5, "elysiumnpcs:envoy", "{Kind:\"sentinel\",Transient:1b,TicksHere:23990,Tags:[\"passing\"]}")
    t.cmd("time set 3000")
    t.cmd(f"fill {t.at(11, -1, 1)} {t.at(19, 4, 9)} minecraft:barrier hollow")
    t.cmd(f"fill {t.at(12, 0, 2)} {t.at(18, 3, 8)} minecraft:air")
    t.setblock(15, 0, 5, "elysiumnpcs:gear_bench")
    # a villager takes a job site only with a player about (even the game's own smithing table goes
    # untaken here), so the profession is given and its trade table read
    _summon(t, 14, 0, 4, "minecraft:villager", "{PersistenceRequired:1b,Tags:[\"hire\"],Xp:1,VillagerData:{type:\"minecraft:plains\",profession:\"elysiumnpcs:gear_smith\",level:1}}")
    t.advance(3000)
    kind = t.cmd("data get entity @e[tag=staying,limit=1] Kind")
    t.expect('"queen"' in kind, f"the envoy lost its kind: {kind[:80]}")
    hp = t.cmd("attribute @e[tag=staying,limit=1] minecraft:generic.max_health get")
    m = re.search(r"is (\d+(?:\.\d+)?)", hp)
    t.expect(m is not None and float(m.group(1)) >= 40, f"an envoy has no envoy's health: {hp[:80]}")
    t.expect(_count(t, "@e[tag=passing]") == 0, "a passing envoy did not leave when its time was up")
    offers = t.cmd("data get entity @e[tag=hire,limit=1] Offers.Recipes")
    t.expect("copper_ingot" in offers or "stone_pickaxe" in offers, f"a gear smith offers none of the gear smith's trades: {offers[:120]}")
    t.cmd("kill @e[tag=staying]")
    t.cmd("kill @e[tag=hire]")


# --- ores -----------------------------------------------------------------

@scenario("ores: a ruby ore drops a ruby, silk touch keeps the ore, breaking pays experience, the cordon seeds a city chest, the unify report answers")
def ores_drops(t):
    t.setblock(2, 0, 2, "elysiumores:stone_ruby_ore")
    t.cmd(f"loot spawn {t.at(2, 2, 2)} mine {t.at(2, 0, 2)} minecraft:iron_pickaxe")
    t.cmd(f"loot spawn {t.at(5, 2, 2)} mine {t.at(2, 0, 2)} minecraft:iron_pickaxe[minecraft:enchantments={{levels:{{\"minecraft:silk_touch\":1}}}}]")
    t.advance(2)
    dropped = t.cmd(f"data get entity @e[type=minecraft:item,x={t.ox + 2},y={t.oy + 2},z={t.oz + 2},distance=..6,limit=1] Item.id")
    rubies = 1 if "ruby" in dropped else 0
    ores = _count(t, f"@e[type=minecraft:item,x={t.ox + 5},y={t.oy + 2},z={t.oz + 2},distance=..6,nbt={{Item:{{id:\"elysiumores:stone_ruby_ore\"}}}}]")
    t.expect(rubies >= 1, "a ruby ore mined with iron dropped no ruby")
    t.expect(ores >= 1, "a ruby ore mined with silk touch did not drop itself")
    t.cmd(f"setblock {t.at(2, 0, 2)} minecraft:air destroy")
    t.advance(2)
    orbs = _count(t, f"@e[type=minecraft:experience_orb,x={t.ox + 2},y={t.oy},z={t.oz + 2},distance=..4]")
    t.expect(orbs >= 1, "breaking a ruby ore paid no experience")
    t.cmd(f"kill @e[type=minecraft:item,x={t.ox + 4},y={t.oy},z={t.oz + 2},distance=..6]")
    t.cmd("kill @e[type=minecraft:experience_orb]")
    t.chest(8, 0, 2)
    found = 0
    for _ in range(60):
        t.cmd(f"loot insert {t.at(8, 0, 2)} loot minecraft:chests/ancient_city")
        if "elysiumores:blight_crystal" in t.cmd(f"data get block {t.at(8, 0, 2)} Items"):
            found += 1
            break
        t.cmd(f"data merge block {t.at(8, 0, 2)} {{Items:[]}}")
    t.expect(found >= 1, "sixty ancient city chests carried no blight crystal (6% each)")
    report = t.cmd("elysium ores")
    t.expect("unified" in report.lower() or "famil" in report.lower(), f"the ores report said nothing useful: {report[:120]}")


# --- spawners -------------------------------------------------------------

@scenario("spawners: a tuned spawner writes its tuning into the cage, a smashed spawner drops its unstable parts")
def spawners_tuning(t):
    t.setblock(2, 0, 2, "elysiumspawners:spawner")
    t.cmd(f"data merge block {t.at(2, 0, 2)} {{tuning:{{count:2,delay:3,ignores_players:1b}},SpawnData:{{entity:{{id:\"minecraft:pig\"}}}}}}")
    t.advance(5)
    t.expect_number(2, 0, 2, "SpawnCount", 5, "the tuning did not raise the spawn count")
    t.expect_number(2, 0, 2, "RequiredPlayerRange", 100, "ignores_players did not widen the player range")
    t.setblock(5, 0, 2, "minecraft:spawner")
    t.cmd(f"setblock {t.at(5, 0, 2)} minecraft:air destroy")
    t.advance(2)
    parts = _count(t, f"@e[type=minecraft:item,x={t.ox + 5},y={t.oy},z={t.oz + 2},distance=..4]")
    t.expect(parts >= 1, "a smashed spawner dropped nothing")
    t.cmd(f"kill @e[type=minecraft:item,x={t.ox + 5},y={t.oy},z={t.oz + 2},distance=..6]")
    # capturing needs a player's kill: the client stage


# --- transit ---------------------------------------------------------------

@scenario("transit: a waystone trickles charge and charges faster on a grid, a cart falls to the ground, a truck destroyed by a cactus spills its cargo")
def transit_waystones_and_vehicles(t):
    t.setblock(2, 0, 2, "elysiumtransit:waystone")
    _grid(t, 6)
    t.setblock(6, 0, 6, "elysiumtransit:waystone")
    _summon(t, 10, 5, 2, "elysiumtransit:cart", "{Tags:[\"cart\"]}")
    _summon(t, 14, 0, 2, "elysiumtransit:truck", "{Fuel:100f,Tags:[\"truck\"],Cargo:{Size:27,Items:[{Slot:0,id:\"minecraft:diamond\",count:64}]}}")
    t.advance(200)
    slow = t.number(2, 0, 2, "held")
    fast = t.number(6, 0, 6, "held")
    t.expect(slow is not None and slow >= 1, f"a waystone on its own took no charge ({slow})")
    t.expect(fast is not None and slow is not None and fast > slow, f"a waystone on a grid charged no faster ({fast} against {slow})")
    y = _entity_number(t, "@e[tag=cart,limit=1]", "Pos[1]")
    t.expect(y is not None and y < t.oy + 1.5, f"the idle cart hangs in the air (y {y})")
    t.cmd("damage @e[tag=truck,limit=1] 1 minecraft:cactus")
    t.advance(2)
    t.expect(_count(t, "@e[tag=truck]") == 0, "the truck survived a cactus, which vehicles do not")
    diamonds = _count(t, f"@e[type=minecraft:item,x={t.ox + 14},y={t.oy},z={t.oz + 2},distance=..6,nbt={{Item:{{id:\"minecraft:diamond\"}}}}]")
    t.expect(diamonds >= 1, "a truck destroyed by a cactus took its sixty-four diamonds with it")
    t.cmd(f"kill @e[type=minecraft:item,x={t.ox + 14},y={t.oy},z={t.oz + 2},distance=..8]")
    t.cmd("kill @e[tag=cart]")


# --- trinkets -------------------------------------------------------------

@scenario("trinkets: a city chest carries a found trinket, and a crafter fills a potion charm")
def trinkets_found_and_filled(t):
    t.chest(2, 0, 2)
    found = False
    for _ in range(100):
        t.cmd(f"loot insert {t.at(2, 0, 2)} loot minecraft:chests/ancient_city")
        if "elysiumtrinkets:" in t.cmd(f"data get block {t.at(2, 0, 2)} Items"):
            found = True
            break
        t.cmd(f"data merge block {t.at(2, 0, 2)} {{Items:[]}}")
    t.expect(found, "a hundred ancient city chests carried no trinket (5% each)")
    t.setblock(5, 0, 2, "minecraft:crafter[orientation=down_north]")
    t.chest(5, -1, 2)
    t.cmd(f"item replace block {t.at(5, 0, 2)} container.0 with elysiumtrinkets:potion_charm 1")
    t.cmd(f"item replace block {t.at(5, 0, 2)} container.1 with minecraft:potion[minecraft:potion_contents={{potion:\"minecraft:swiftness\"}}] 1")
    t.setblock(6, 0, 2, "minecraft:redstone_block")
    t.advance(10)
    t.setblock(6, 0, 2, "minecraft:air")
    charm = t.cmd(f"data get block {t.at(5, -1, 2)} Items")
    t.expect("elysiumtrinkets:potion_charm" in charm and "swiftness" in charm, f"the crafter filled no charm with swiftness: {charm[:120]}")


# --- wilds ----------------------------------------------------------------

@scenario("wilds: a keep places its rooms, a mossy boulder places on grass")
def wilds_places(t):
    out = t.cmd(f"place structure elysiumwilds:keep {t.at(2, 0, 2)}")
    t.expect("Placed" in out or "Generated" in out, f"the keep would not place: {out[:80]}")
    t.advance(2)
    import re
    # the keep stands on the natural surface under the stage, not on the stage: look low and wide
    t.cmd(f"forceload add {t.ox - 64} {t.oz - 64} {t.ox + 80} {t.oz + 80}")
    built = 0
    for y0 in (20, 50, 80, 110):
        for bx in range(-60, 61, 30):
            for bz in range(-60, 61, 30):
                out = t.cmd(f"fill {t.ox + bx} {y0} {t.oz + bz} {t.ox + bx + 29} {y0 + 29} {t.oz + bz + 29} minecraft:air replace minecraft:stone_bricks")
                m = re.search(r"filled (\d+)", out)
                built += int(m.group(1)) if m else 0
    t.expect(built >= 1, "the placed keep laid no stone-brick floor anywhere within sixty blocks and the surface's height")
    t.cmd(f"fill {t.at(2, -1, 2)} {t.at(21, -1, 21)} minecraft:smooth_stone")
    t.fill((4, -1, 4), (10, -1, 10), "minecraft:grass_block")
    out = t.cmd(f"place feature elysiumwilds:boulder_mossy {t.at(7, 0, 7)}")
    t.expect("Placed" in out, f"the mossy boulder would not place on grass: {out[:80]}")
