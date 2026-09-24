package com.elysium.colonies.event;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.charter.Charter;
import com.elysium.colonies.charter.Charters;
import com.elysium.colonies.citizen.CitizenStress;
import com.elysium.colonies.citizen.ColonyMemory;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.CharterRules;
import com.elysium.colonies.rules.CitizenRules;
import com.elysium.colonies.rules.ExchangeRules;
import com.elysium.colonies.rules.GrowthRules;
import com.elysium.colonies.rules.SiteRules;
import com.elysium.colonies.rules.ReliefRules;
import com.elysium.colonies.rules.StewardRules;
import com.elysium.colonies.exchange.FrontierExchange;
import com.elysium.colonies.port.Spaceport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The daily reckoning: a town takes stock of itself.
 *
 * <h2>Once a day, not once a tick</h2>
 *
 * Everything here is a fact that changes over days — how many people are
 * housed, how hard the world is to live on, how much the town has saved. A
 * town's colony day is MineColonies' own count, so this runs when that number
 * moves and never otherwise: on a server with twenty colonies it is twenty
 * passes a Minecraft day, not twenty every tick.
 *
 * <h2>What the pass does, in order</h2>
 *
 * <ol>
 *   <li>Makes sure the town's people carry this module's reason for being
 *       happy or unhappy, so it shows in the colony's own screen.</li>
 *   <li>Works out, for each person, what their life came to — their housing,
 *       their work, their wounds, and the world they are standing on — and
 *       remembers it.</li>
 *   <li>Turns that into a day's work, folding in what the world is worth and
 *       how practised the town's people are, and banks it.</li>
 *   <li>If the town has saved enough and has waited long enough, asks for one
 *       of its own buildings to be raised.</li>
 * </ol>
 *
 * <p>Nothing here builds anything. The town files a work order; its builder
 * still has to build it and its warehouse still has to hold the materials.
 */
@EventBusSubscriber(modid = ElysiumColonies.MODID)
public final class ColonyLife {

    private ColonyLife() {
    }

    /**
     * How often the day is checked.
     *
     * Every ten seconds. The check itself is one integer comparison per colony
     * against a number the host already keeps; the reckoning behind it happens
     * once a day. Checking more often would find nothing; checking much less
     * often would mean a town that idles through a day if the chunk was
     * unloaded at the wrong moment.
     */
    public static final int INTERVAL = 20 * 10;

    /**
     * How much this module's reason weighs against the colony's own.
     *
     * Two. MineColonies' own factors — housing, food, security — weigh one
     * each, so this is worth two of them: enough that a town on Cinderveil is
     * visibly a harder place to live than a town in a meadow, and not so much
     * that it drowns out the things a player fixes by building.
     */
    public static final double HAPPINESS_WEIGHT = 2.0D;

    /**
     * Days between one building being gone over and the next.
     *
     * Three. Over a month a town of ten walks every building it has; over a
     * year, a dozen times each. Frequent enough that damage does not
     * accumulate, rare enough that the builder spends most of its time
     * building.
     */
    public static final int CARETAKING = 3;

    /** What the Suspicion earned by trading is filed under. */
    public static final String CAUSE_EXCHANGE = "exchange";

    @SubscribeEvent
    public static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % INTERVAL != 0L) {
            return;
        }
        if (!Colonies.present()) {
            return;
        }
        for (Colonies.Settlement town : Colonies.in(level)) {
            reckon(level, town);
        }
    }

    /** One town's day, if a day has passed. */
    private static void reckon(ServerLevel level, Colonies.Settlement town) {
        ColonyMemory memory = ColonyMemory.of(level);
        ColonyMemory.Ledger ledger = memory.ledgerOf(town.id());
        if (town.day() <= ledger.lastDay()) {
            return;                       // already counted today
        }

        Charter charter = Charters.of(town.dimension());
        if (Colonies.ensureHappiness(level, town.id(), HAPPINESS_WEIGHT)) {
            ElysiumColonies.LOGGER.info("{} now carries the frontier's reason",
                    town.name());
        }

        // Who has been here lately, recorded before anything acts on it: a
        // player standing in their own town on the morning the day rolls over
        // is present for that day's decision, not the next one's.
        if (ownerIsHere(level, town)) {
            ledger = ledger.seenOn(town.day());
        }

        List<Colonies.Person> people = Colonies.peopleOf(level, town.id());
        List<Colonies.Structure> built = Colonies.structuresOf(level, town.id());
        boolean raided = raidUnderWay(level, town);

        int local = charter.localCount(Charters.needs());
        float world = CharterRules.weighted(CharterRules.workFactor(local),
                charter.reach());

        float sum = 0.0F;
        int worst = ledger.bestStress();
        int idle = 0;
        for (Colonies.Person person : people) {
            ColonyMemory.Life life = memory.lifeOf(town.id(), person.id());
            int stress = CitizenStress.totalFor(person, town, charter, built.size(),
                    raided);
            int wounds = woundsFrom(person.health());
            // Practice is a lifetime count and never falls, even when somebody
            // changes trade: what a person has learned by doing is theirs.
            int practised = Math.max(life.practice(), person.actions());
            memory.remember(town.id(), person.id(),
                    life.withStress(stress).withWounds(wounds)
                            .practised(Math.max(0, practised - life.practice())));
            worst = Math.max(worst, stress);
            if (!person.employed() || person.idle()) {
                idle++;
            }
            sum += CitizenRules.output(stress, wounds, practised, world);
        }

        int working = GrowthRules.working(people.size(), idle);
        float average = people.isEmpty() ? 0.0F : sum / people.size();
        int earned = GrowthRules.perDay(working, average);
        long banked = GrowthRules.banked(ledger.development(), earned);

        // The watchdog, before anything else acts. What it decides is what the
        // rest of the pass is allowed to do about being stuck.
        boolean firstMeeting = ledger.foundedOn() < 0;
        ledger = ledger.foundedOn(town.day());
        if (firstMeeting && charterStyle(town) != null
                && Colonies.style(level, town.id(), charterStyle(town))) {
            ElysiumColonies.LOGGER.info("{} builds in the {} style", town.name(),
                    charterStyle(town));
        }
        boolean moved = ReliefRules.progressed(ledger.builtThen(), built.size(),
                ledger.development(), banked, ledger.citizensThen(), town.citizens());
        if (moved || ledger.lastProgress() < 0) {
            ledger = ledger.movedOn(town.day(), built.size(), town.citizens());
        }
        ReliefRules.Relief relief = ReliefRules.reliefFor(town.day(),
                ledger.lastProgress(), ledger.foundedOn());

        // The autonomy pass, in the order that unblocks the most.
        //
        // Trading first, because everything else a town does can be stopped by
        // one missing plank sitting on a list addressed to a player. Then
        // founding, then raising, then the caretaker's round — a town would
        // rather have a new building than a taller one, because breadth is
        // what makes a settlement work and height is what makes it grand.
        // The spaceport first, because a town that has one should not be
        // selling its furniture at a crisis spread every morning. What the
        // port cannot settle falls through to the exchange, which is what
        // every town without a port has always used.
        Spaceport.Run shipped = Spaceport.run(level, town, ledger.treasury());
        ledger = ledger.withTreasury(shipped.treasury());
        int traded = FrontierExchange.settle(level, town);
        if (relief == ReliefRules.Relief.GRANT) {
            // Trading has been tried for four days and the town is still
            // standing still. It is new enough to be entitled to help, so it
            // gets what it is waiting for and nothing more.
            int covered = FrontierExchange.grant(level, town);
            if (covered > 0) {
                ledger = ledger.movedOn(town.day(), built.size(), town.citizens());
            }
        }
        // Being helped does not buy silence. A town on a hundred-day charter
        // that has finished nothing for a fortnight is a town whose owner
        // needs telling, whether or not free materials are still arriving —
        // the grant went on being poured into the problem either way.
        if (relief == ReliefRules.Relief.REPORT
                || ReliefRules.alsoReport(town.day(), ledger.lastProgress())) {
            if (ReliefRules.dueToReport(town.day(), ledger.lastReported())) {
                // A settlement neither trade nor grant can unstick has a
                // problem this module did not foresee, and pretending
                // otherwise would be worse than saying so.
                if (report(level, town, ReliefRules.stuckFor(town.day(),
                        ledger.lastProgress()))) {
                    ledger = ledger.reportedOn(town.day());
                }
            }
        }
        boolean founded = false;
        int spentOn = -1;

        String wanted = Charters.plan().next(town.citizens(),
                kind -> Colonies.has(level, town.id(), kind));
        StewardRules.Steward steward = ledger.stewardship();
        boolean townDecides = StewardRules.maySite(steward, town.day(),
                ledger.lastSeen());

        if (GrowthRules.patient(town.day(), ledger.lastUpgrade())) {
            if (townDecides && wanted != null && GrowthRules.affords(banked, 1)) {
                founded = found(level, town, wanted);
            }
            if (!founded) {
                // Raising and repairing are not placement: they change what is
                // already standing where its owner put it, so they carry on
                // whoever is in charge of the map.
                spentOn = raiseSomething(level, town, built, banked);
            }
        }

        if (wanted != null && !townDecides
                && StewardRules.shouldSuggest(steward, town.day(), ledger.lastSeen())
                && StewardRules.dueToSuggest(town.day(), ledger.lastSuggested())) {
            if (suggest(level, town, wanted)) {
                ledger = ledger.suggestedOn(town.day());
            }
        }

        int lastUpgrade = ledger.lastUpgrade();
        if (founded) {
            banked = GrowthRules.spend(banked, 1);
            lastUpgrade = town.day();
        } else if (spentOn > 0) {
            banked = GrowthRules.spend(banked, spentOn);
            lastUpgrade = town.day();
            announce(level, town, spentOn);
        }

        caretake(level, town, built);

        memory.record(town.id(), ledger.after(banked, town.day(), lastUpgrade, worst));
        // Both ways of getting supplies are conspicuous, and a settlement with
        // a two-hundred-block elevator on it is the most conspicuous thing on
        // the frontier. Counted together and capped together.
        int dealings = traded + shipped.trades();
        if (dealings > 0) {
            noticed(level, town, dealings);
        }
    }

    /**
     * Puts the next thing the plan asks for somewhere sensible.
     *
     * The search is {@link SiteRules}' ring walk outward from the town hall:
     * the first square that is inside the colony, clear of everything already
     * built, on ground level enough to walk between, and with air above it.
     * If nothing in eight rings qualifies the town simply does not build
     * today, which is the right answer for a settlement wedged into a ravine.
     */
    private static boolean found(ServerLevel level, Colonies.Settlement town,
                                 String kind) {
        BlockPos hall = town.centre();
        for (int ring = 0; ring <= SiteRules.RINGS; ring++) {
            for (int index = 0; index < SiteRules.squaresIn(ring); index++) {
                BlockPos flat = new BlockPos(hall.getX() + SiteRules.blocksX(ring, index),
                        hall.getY(),
                        hall.getZ() + SiteRules.blocksZ(ring, index));
                BlockPos ground = level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        flat);
                if (!SiteRules.levelEnough(hall.getY(), ground.getY())) {
                    continue;
                }
                if (!Colonies.inside(level, town.id(), ground)) {
                    continue;
                }
                if (!Colonies.clearOf(level, town.id(), ground, SiteRules.SPACING - 1)) {
                    continue;
                }
                if (!level.getBlockState(ground).canBeReplaced()) {
                    continue;
                }
                if (level.getBlockState(ground.below()).isAir()) {
                    continue;              // nothing to stand it on
                }
                if (Colonies.found(level, town.id(), kind, ground)) {
                    ElysiumColonies.LOGGER.info("{} founded a {} at {}", town.name(),
                            kind, ground);
                    return true;
                }
                return false;              // the spot was fine; the building was not
            }
        }
        return false;
    }

    /**
     * The caretaker's round.
     *
     * Nothing in MineColonies decides that a building needs repairing — repair
     * is an instruction, not a diagnosis — so a town that looks after itself
     * simply goes over one building every {@link #CARETAKING} days, in turn.
     * Over a month every building gets walked; over a year, a dozen times. It
     * costs nothing but the builder's time, and it is the difference between a
     * town that survives a creeper and one that is quietly ruined by it.
     */
    private static void caretake(ServerLevel level, Colonies.Settlement town,
                                 List<Colonies.Structure> built) {
        if (built.isEmpty() || town.day() % CARETAKING != 0) {
            return;
        }
        List<Colonies.Structure> standing = new ArrayList<>();
        for (Colonies.Structure structure : built) {
            if (!structure.building() && structure.level() > 0) {
                standing.add(structure);
            }
        }
        if (standing.isEmpty()) {
            return;
        }
        standing.sort((one, two) -> one.at().toString().compareTo(two.at().toString()));
        Colonies.Structure turn = standing.get(
                Math.floorMod(town.day() / CARETAKING, standing.size()));
        Colonies.repair(level, town.id(), turn.at());
    }

    /** The style the world this settlement stands on builds in, or null. */
    private static String charterStyle(Colonies.Settlement town) {
        Charter charter = Charters.of(town.dimension());
        return charter.hasStyle() ? charter.style() : null;
    }

    /**
     * Tells the owner their settlement has stopped, and for how long.
     *
     * The honest end of the escalation. Everything this module can do about a
     * stuck town has been done; what is left is a fact the player needs, and
     * the alternative — going on quietly forever — is how a colony mod earns a
     * reputation for being broken.
     */
    private static boolean report(ServerLevel level, Colonies.Settlement town,
                                  int days) {
        if (town.owner() == null) {
            return false;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(town.owner());
        if (owner == null) {
            return false;
        }
        owner.displayClientMessage(Component.translatable("elysium.colony.stuck",
                        town.name(), days)
                .withStyle(ChatFormatting.RED), false);
        return true;
    }

    /**
     * Whether the owner is in their own town.
     *
     * In it, not merely online: a player who logs in for ten minutes on the
     * other side of the world has not taken charge of anything, and a town
     * that stopped developing every time they did would develop in an
     * unpredictable stutter.
     */
    private static boolean ownerIsHere(ServerLevel level, Colonies.Settlement town) {
        if (town.owner() == null) {
            return false;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(town.owner());
        if (owner == null || owner.level() != level) {
            return false;
        }
        return Colonies.inside(level, town.id(), owner.blockPosition());
    }

    /**
     * Tells the owner what their town would build if it were left to it.
     *
     * The other half of deferring to them. A town that quietly wanted a
     * warehouse for a month and never said so would have traded one kind of
     * bad behaviour for another — the player would simply never learn what it
     * needed. Said at most once every few days, and only to somebody who is
     * actually there to hear it.
     *
     * @return whether anybody was told, so a caller only records a suggestion
     *         that actually went out
     */
    private static boolean suggest(ServerLevel level, Colonies.Settlement town,
                                   String wanted) {
        if (town.owner() == null) {
            return false;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(town.owner());
        if (owner == null) {
            return false;
        }
        owner.displayClientMessage(Component.translatable("elysium.colony.wants",
                        town.name(), wanted)
                .withStyle(ChatFormatting.YELLOW), false);
        return true;
    }

    /**
     * Trading is conspicuous.
     *
     * A settlement that keeps signalling for supplies is a settlement somebody
     * is keeping a file on — the same sentence this module already says about
     * owning a town, said again about the way the town stays alive. Capped
     * hard, because being punished for a system working would be worse than
     * not having the system.
     */
    private static void noticed(ServerLevel level, Colonies.Settlement town,
                                int traded) {
        if (town.owner() == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(town.owner());
        if (owner == null) {
            return;
        }
        com.elysium.lib.standing.ElysiumStanding.addSuspicion(owner,
                ExchangeRules.noticeFor(traded), CAUSE_EXCHANGE);
    }

    /**
     * Picks the building a town would rather have, and asks for it.
     *
     * The lowest-level building it is allowed to raise, so a town brings its
     * whole settlement up together rather than running one hut to the ceiling
     * while the rest stay huts. Ties are broken by position so the choice does
     * not wander between saves.
     *
     * @return the level ordered, or -1 if nothing was
     */
    private static int raiseSomething(ServerLevel level, Colonies.Settlement town,
                                      List<Colonies.Structure> built, long banked) {
        List<Colonies.Structure> wanted = new ArrayList<>();
        for (Colonies.Structure structure : built) {
            if (structure.building()) {
                continue;
            }
            if (!GrowthRules.willRaise(structure.level(), structure.maximum())) {
                continue;
            }
            if (!GrowthRules.affords(banked, structure.level() + 1)) {
                continue;
            }
            wanted.add(structure);
        }
        if (wanted.isEmpty()) {
            return -1;
        }
        wanted.sort((one, two) -> {
            int byLevel = Integer.compare(one.level(), two.level());
            return byLevel != 0 ? byLevel
                    : one.at().toString().compareTo(two.at().toString());
        });
        Colonies.Structure chosen = wanted.get(0);
        return Colonies.raise(level, town.id(), chosen.at())
                ? chosen.level() + 1 : -1;
    }

    private static void announce(ServerLevel level, Colonies.Settlement town, int level_) {
        if (town.owner() == null) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(town.owner());
        if (owner == null) {
            return;                       // they will see the building when they come
        }
        owner.displayClientMessage(Component.translatable(
                        "elysium.colony.raised", town.name(), level_)
                .withStyle(ChatFormatting.GREEN), false);
    }

    /**
     * How many wounds somebody's health implies.
     *
     * Read from health rather than tracked separately, because MineColonies
     * heals its citizens on its own schedule and a second, disagreeing, record
     * of how hurt somebody is would be two answers to one question. A citizen
     * at full health has none; one at nothing has as many as the scale allows.
     */
    public static int woundsFrom(float health) {
        float hurt = 1.0F - Math.max(0.0F, Math.min(1.0F, health));
        return Math.round(hurt * 6.0F);
    }

    /**
     * Whether something is attacking the town.
     *
     * Asked of the world rather than of the colony, because the host's raid
     * manager speaks in its own types and this module has one file allowed to
     * do that. A raider standing in the colony is a raid, which is also the
     * only version of the question a citizen would recognise.
     */
    private static boolean raidUnderWay(ServerLevel level, Colonies.Settlement town) {
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(
                town.centre()).inflate(96.0D);
        for (net.minecraft.world.entity.Entity entity
                : level.getEntities((net.minecraft.world.entity.Entity) null, around,
                        Colonies::isRaider)) {
            if (entity.isAlive()) {
                return true;
            }
        }
        return false;
    }
}
