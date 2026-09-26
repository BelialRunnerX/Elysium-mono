package com.elysium.colonies.command;

import com.elysium.colonies.ElysiumColonies;
import com.elysium.colonies.charter.Charter;
import com.elysium.colonies.charter.Charters;
import com.elysium.colonies.citizen.ColonyMemory;
import com.elysium.colonies.colony.Colonies;
import com.elysium.colonies.rules.CharterRules;
import com.elysium.colonies.rules.CitizenRules;
import com.elysium.colonies.rules.GrowthRules;
import com.elysium.colonies.market.Market;
import com.elysium.colonies.port.PortRegistry;
import com.elysium.colonies.rules.MarketRules;
import com.elysium.colonies.rules.PortRules;
import com.elysium.colonies.rules.StewardRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.elysium.lib.stress.ElysiumStressBand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * {@code /elysium colony} — what this world is doing to your town, and what
 * your town is doing about it.
 *
 * <h2>Why this exists</h2>
 *
 * Because every number this module adds is meant to be one a player can act
 * on, and a number nobody can see is a number nobody can act on. The
 * happiness screen shows the result; this shows the working: which charter the
 * world has, how much of what a settlement needs is under its feet, what a
 * day's work is worth here, what the town has saved, and which of its people
 * are having the worst time of it.
 *
 * <p>Two levels. Bare, it reports the town you are standing in. With
 * {@code people}, it names the five most miserable, because "somebody is
 * unhappy" is not actionable and "these five have nowhere to live" is.
 */
@EventBusSubscriber(modid = ElysiumColonies.MODID)
public final class ColonyCommand {

    private ColonyCommand() {
    }

    /** How many of the unhappiest are named. */
    public static final int NAMED = 5;

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        LiteralArgumentBuilder<CommandSourceStack> steward =
                Commands.literal("steward");
        for (StewardRules.Steward who : StewardRules.Steward.values()) {
            steward = steward.then(Commands.literal(who.id())
                    .executes(context -> hand(context.getSource(), who)));
        }
        return Commands.literal("elysium").then(Commands.literal("colony")
                .executes(context -> report(context.getSource(), false))
                .then(Commands.literal("people")
                        .executes(context -> report(context.getSource(), true)))
                .then(Commands.literal("join")
                        .executes(context -> join(context.getSource())))
                .then(Commands.literal("leave")
                        .executes(context -> leave(context.getSource())))
                .then(Commands.literal("market")
                        .executes(context -> market(context.getSource())))
                .then(Commands.literal("sell")
                        .executes(context -> sell(context.getSource())))
                .then(Commands.literal("claim")
                        .executes(context -> claim(context.getSource())))
                .then(Commands.literal("natural")
                        .executes(context -> natural(context.getSource())))
                .then(steward));
    }

    /**
     * Hands placement to the town, or takes it back.
     *
     * Only the owner may, and only for the town they are standing in — this is
     * a decision about somebody's own settlement, and reaching across the map
     * to change who runs a colony is not something a command should make easy.
     */
    /** A player takes the self-founded town they are standing in. */
    private static int claim(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        Colonies.Settlement town = Colonies.around(player);
        if (town == null || !com.elysium.colonies.settle.Settlements.claim(
                player.serverLevel(), player, town)) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.claim.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("elysium.colony.claimed", town.name())
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    /** Every town in this world that founded itself, nearest first. */
    private static int natural(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        List<Colonies.Settlement> towns = com.elysium.colonies.settle.Settlements.natural(
                player.serverLevel());
        if (towns.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.natural.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        towns.sort((one, two) -> Double.compare(one.centre().distSqr(player.blockPosition()),
                two.centre().distSqr(player.blockPosition())));
        source.sendSuccess(() -> Component.translatable("elysium.colony.natural.header",
                towns.size()).withStyle(ChatFormatting.GOLD), false);
        for (Colonies.Settlement town : towns) {
            int away = (int) Math.sqrt(town.centre().distSqr(player.blockPosition()));
            int waiting = Colonies.orders(player.serverLevel(), town.id()).size();
            source.sendSuccess(() -> Component.translatable("elysium.colony.natural.entry",
                            town.name(), town.centre().getX(), town.centre().getZ(), away,
                            town.citizens(), waiting, town.day())
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return towns.size();
    }

    private static int hand(CommandSourceStack source, StewardRules.Steward who) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        Colonies.Settlement town = mine(player);
        if (town == null) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ColonyMemory memory = ColonyMemory.of(player.serverLevel());
        memory.record(town.id(), memory.ledgerOf(town.id()).withSteward(who));
        source.sendSuccess(() -> Component.translatable(
                        "elysium.colony.steward." + who.id(), town.name())
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int report(CommandSourceStack source, boolean naming) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Colonies.Settlement town = mine(player);
        if (town == null) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        Charter charter = Charters.of(town.dimension());
        int local = charter.localCount(Charters.needs());
        float plain = CharterRules.workFactor(local);
        float here = CharterRules.weighted(plain, charter.reach());
        ColonyMemory.Ledger ledger = ColonyMemory.of(level).ledgerOf(town.id());
        List<Colonies.Structure> built = Colonies.structuresOf(level, town.id());

        source.sendSuccess(() -> Component.translatable("elysium.colony.header",
                        town.name(), town.citizens(), built.size())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("elysium.colony.charter",
                        town.dimension().toString(), local, Charters.needs().size(),
                        charter.reach())
                .withStyle(charter.blank() ? ChatFormatting.GRAY : ChatFormatting.AQUA),
                false);
        source.sendSuccess(() -> Component.translatable("elysium.colony.world",
                        percent(here), charter.hazard(), charter.air().id())
                .withStyle(here >= 1.0F ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                false);
        source.sendSuccess(() -> Component.translatable("elysium.colony.development",
                        ledger.development(), GrowthRules.ceiling(),
                        GrowthRules.UNAIDED_LEVEL)
                .withStyle(ChatFormatting.WHITE), false);

        boolean townDecides = StewardRules.maySite(ledger.stewardship(), town.day(),
                ledger.lastSeen());
        boolean visited = StewardRules.everVisited(ledger.lastSeen());
        int away = visited ? town.day() - ledger.lastSeen() : 0;
        // A town nobody has stood in yet says so, rather than showing the
        // never-visited sentinel as a count of days.
        String siting = (townDecides ? "elysium.colony.siting.town"
                : "elysium.colony.siting.owner") + (visited ? "" : ".never");
        source.sendSuccess(() -> Component.translatable(siting,
                        Component.translatable("elysium.colony.steward.name."
                                + ledger.stewardship().id()),
                        away)
                .withStyle(townDecides ? ChatFormatting.GREEN : ChatFormatting.GOLD),
                false);
        String wanted = Charters.plan().next(town.citizens(),
                kind -> Colonies.has(level, town.id(), kind));
        if (wanted != null) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.wants",
                            town.name(), wanted)
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        List<Colonies.Person> people = Colonies.peopleOf(level, town.id());
        ColonyMemory memory = ColonyMemory.of(level);
        people.sort((one, two) -> Integer.compare(
                memory.lifeOf(town.id(), two.id()).stress(),
                memory.lifeOf(town.id(), one.id()).stress()));

        if (!naming) {
            int worst = people.isEmpty() ? 0
                    : memory.lifeOf(town.id(), people.get(0).id()).stress();
            source.sendSuccess(() -> Component.translatable("elysium.colony.worst",
                            Component.translatable(ElysiumStressBand.of(worst)
                                    .translationKey()), worst)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return people.size();
        }

        int named = 0;
        for (Colonies.Person person : people) {
            if (named >= NAMED) {
                break;
            }
            ColonyMemory.Life life = memory.lifeOf(town.id(), person.id());
            named++;
            source.sendSuccess(() -> Component.translatable("elysium.colony.person",
                            person.name(),
                            Component.translatable(ElysiumStressBand.of(life.stress())
                                    .translationKey()),
                            life.stress(),
                            percent(CitizenRules.practised(life.practice())),
                            person.job().isEmpty() ? "-" : person.job())
                    .withStyle(ChatFormatting.WHITE), false);
        }
        return people.size();
    }

    /**
     * Joins the settlement you are standing in.
     *
     * <h3>Why this is a command and not the host's own button</h3>
     *
     * Because the host's own path for a player who wants to be attached to
     * somebody else's colony is the owner adding them, which needs the owner to
     * be online and in the menu. This is the other direction: you walk into a
     * town and ask. Whether that is reasonable is a question about the pack
     * rather than about the code, and a pack that disagrees can take the
     * permission away.
     *
     * <p>And it is additive. A player may hold a stake in as many settlements
     * as they like — MineColonies never said otherwise, its membership is a map
     * per colony with no uniqueness check anywhere — so joining one never
     * detaches you from another.
     */
    private static int join(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        Colonies.Settlement here = standingIn(player);
        if (here == null) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.nowhere")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        if (Colonies.admit(player.serverLevel(), here.id(), player,
                Colonies.RANK_FRIEND)) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.joined",
                            here.name(),
                            Colonies.membersOf(player.serverLevel(), here.id()))
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("elysium.colony.already",
                        here.name()).withStyle(ChatFormatting.GRAY), false);
        return 0;
    }

    /**
     * Leaves the settlement you are standing in, without abandoning it.
     *
     * The distinction the host does not draw for you: its own "leave" is the
     * owner abandoning a colony, which renames them, opens the permissions up
     * and can mark the place fully abandoned once the officers have gone. This
     * removes a member and does nothing else — and refuses outright for the
     * owner, because that decision has consequences a one-word command should
     * not make on somebody's behalf.
     */
    private static int leave(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        Colonies.Settlement here = standingIn(player);
        if (here == null) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.nowhere")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        if (Colonies.owns(player.serverLevel(), here.id(), player.getUUID())) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.cannot_leave",
                            here.name()).withStyle(ChatFormatting.RED), false);
            return 0;
        }
        if (Colonies.release(player.serverLevel(), here.id(), player.getUUID())) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.left",
                            here.name()).withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("elysium.colony.not_a_member",
                        here.name()).withStyle(ChatFormatting.GRAY), false);
        return 0;
    }

    /**
     * What this world pays and charges for what you are carrying.
     *
     * The player's half of the market the colonies use. It quotes rather than
     * trades, because a command that moved items would be a shop, and a shop
     * belongs to a block somebody built. What it is for is the thing a trader
     * actually needs: to know, standing on Frostfall with a hold full of coal,
     * whether it is worth taking to Cinderveil.
     */
    private static int market(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        ResourceLocation where = player.level().dimension().location();
        int day = dayOf(player);
        source.sendSuccess(() -> Component.translatable("elysium.colony.market.header",
                        where.toString(), day).withStyle(ChatFormatting.GOLD), false);
        int quoted = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ItemStack carried : player.getInventory().items) {
            if (carried.isEmpty() || quoted >= QUOTED) {
                continue;
            }
            String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries
                    .ITEM.getKey(carried.getItem()));
            if (!seen.add(id)) {
                continue;
            }
            quoted++;
            int price = Market.priceOf(where, carried, day);
            String family = Market.familyOf(carried);
            source.sendSuccess(() -> Component.translatable(
                            "elysium.colony.market.quote",
                            carried.getHoverName(),
                            MarketRules.offering(price), MarketRules.asking(price),
                            family == null ? "-" : family)
                    .withStyle(ChatFormatting.WHITE), false);
        }
        if (quoted == 0) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.market.empty")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return quoted;
    }

    /** How many things get a quote before the list becomes a wall of text. */
    public static final int QUOTED = 9;

    /**
     * The day prices are quoted for.
     *
     * A settlement's own day where there is one, so a trader and the colony
     * they are standing in read the same number off the same board. Where
     * there is no settlement, the world's own day serves.
     */
    private static int dayOf(ServerPlayer player) {
        Colonies.Settlement here = standingIn(player);
        return here != null ? here.day()
                : (int) (player.serverLevel().getDayTime() / 24000L);
    }

    /** The settlement whose ground you are standing on, member or not. */
    /**
     * Sells what you are holding through the town's spaceport.
     *
     * <h3>Why a player can do this at all</h3>
     *
     * Because the market has, until now, been something a player could read
     * and not something they could use. Knowing that coolant is dear on
     * Cinderveil is only worth knowing if you can carry coolant to Cinderveil,
     * and the port is the place where cargo turns into money. That is the
     * shape the good space games have: prices differ, you fly, you sell.
     *
     * <h3>Why the money goes to the colony</h3>
     *
     * Because a player currency would be a second economy this module has no
     * business inventing, and because the interesting version of the question
     * is not "how rich am I" but "can my settlement afford what it is waiting
     * for". A player who runs a good route funds their own town's imports.
     *
     * <h3>What stops it being infinite</h3>
     *
     * The treasury's ceiling, which is the port's own height in disguise. A
     * player can carry as many stacks up the elevator as they like and the
     * town still cannot hold more money than its port can spend — so a good
     * route fills the treasury and the next run is worth nothing until the
     * colony has spent it. Capacity is not charged, deliberately: it belongs
     * to the colony's own daily shipping, and a player carrying a crate up a
     * lift that is already standing there is not taking a slot from anyone.
     */
    private static int sell(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("elysium.colony.nobody"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Colonies.Settlement here = standingIn(player);
        if (here == null) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.nowhere")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        // Somebody else's town is somebody else's treasury. Selling into it
        // would be a gift, and a command that quietly gives your cargo away is
        // a command somebody types once.
        if (!Colonies.owns(level, here.id(), player.getUUID())
                && !memberOf(player, here)) {
            source.sendSuccess(() -> Component.translatable(
                            "elysium.colony.port.notyours", here.name())
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        PortRegistry.Berth berth = PortRegistry.of(level.getServer())
                .berth(here.dimension(), here.id());
        if (berth == null || !berth.working()) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.port.none",
                    here.name()).withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        ItemStack carried = player.getMainHandItem();
        if (carried.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("elysium.colony.port.empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        int count = carried.getCount();
        Component named = carried.getHoverName();
        long paid = MarketRules.purseFrom(count,
                Market.priceOf(here.dimension(), carried, here.day()));
        ColonyMemory memory = ColonyMemory.of(level);
        ColonyMemory.Ledger ledger = memory.ledgerOf(here.id());
        long held = PortRules.banked(ledger.treasury(), paid, berth.tether());
        memory.record(here.id(), ledger.withTreasury(held));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                ItemStack.EMPTY);
        source.sendSuccess(() -> Component.translatable("elysium.colony.port.sold",
                        count, named, paid, held).withStyle(ChatFormatting.AQUA), false);
        return count;
    }

    /** Whether this player has joined this settlement. */
    private static boolean memberOf(ServerPlayer player, Colonies.Settlement here) {
        for (Colonies.Settlement town : Colonies.memberOf(player)) {
            if (town.id() == here.id()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The player's settlement: the one they stand in when it is theirs, else
     * the first they own. A player who claimed two towns asks about the one
     * around them.
     */
    private static Colonies.Settlement mine(ServerPlayer player) {
        Colonies.Settlement here = standingIn(player);
        if (here != null && player.getUUID().equals(here.owner())) {
            return here;
        }
        return Colonies.ownedBy(player);
    }

    private static Colonies.Settlement standingIn(ServerPlayer player) {
        for (Colonies.Settlement town : Colonies.in(player.serverLevel())) {
            if (Colonies.inside(player.serverLevel(), town.id(),
                    player.blockPosition())) {
                return town;
            }
        }
        return null;
    }

    /** A factor as a readable percentage, so 1.12 reads as 112. */
    private static int percent(float factor) {
        return Math.round(factor * 100.0F);
    }
}
