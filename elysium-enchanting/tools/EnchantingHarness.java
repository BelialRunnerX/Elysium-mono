package com.elysium.enchanting.rules;

import java.util.Random;

/**
 * The enchanting table's arithmetic, executed.
 *
 * What a player feels at a table is a distribution, not a formula, so most of
 * these are about shape: that three offers are three different offers, that
 * uncertainty cuts both ways until it is paid not to, and that arcana changes
 * what is on the table without ever making it certain.
 */
public final class EnchantingHarness {

    private static int checks;
    private static int failures;

    private static void check(boolean condition, String complaint) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL: " + complaint);
        }
    }

    public static void main(String[] args) {
        stats();
        ceiling();
        offers();
        quanta();
        arcana();
        clues();
        infusion();
        library();
        anvil();

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println(checks + " checks: " + EnchantingRules.OFFERS + " offers from four "
                + "numbers, uncertainty that cuts both ways until rectification pays it off, "
                + "and arcana that never makes anything certain");
    }

    private static void stats() {
        EnchantingRules.Stats none = EnchantingRules.Stats.NONE;
        check(none.isEmpty(), "a table with nothing around it had power anyway");
        EnchantingRules.Stats bad = new EnchantingRules.Stats(-5.0F, -2.0F, -1.0F, -3.0F);
        check(bad.eterna() == 0.0F && bad.quanta() == 0.0F && bad.arcana() == 0.0F,
                "a negative number from a datapack went backwards rather than being read as none");
        check(bad.rectification() == 0.0F, "negative rectification survived");
        EnchantingRules.Stats over = new EnchantingRules.Stats(1.0F, 1.0F, 1.0F, 4.0F);
        check(over.rectification() == 1.0F,
                "rectification passed one, which would make uncertainty pay more than it costs");
        EnchantingRules.Stats sum = new EnchantingRules.Stats(2.0F, 1.0F, 3.0F, 0.1F)
                .plus(new EnchantingRules.Stats(3.0F, 2.0F, 1.0F, 0.2F));
        check(sum.eterna() == 5.0F && sum.quanta() == 3.0F && sum.arcana() == 4.0F,
                "two blocks' stats did not add up");
        check(Math.abs(sum.rectification() - 0.3F) < 1e-6, "rectification did not add up");
        EnchantingRules.Stats capped = new EnchantingRules.Stats(90.0F, 1.0F, 1.0F, 0.0F)
                .cappedAt(EnchantingRules.BASE_MAX_ETERNA);
        check(capped.eterna() == EnchantingRules.BASE_MAX_ETERNA,
                "a table built past the cap offered past the cap");
        check(capped.quanta() == 1.0F,
                "capping the power also capped the uncertainty, which is a different number");
    }

    /**
     * The two caps, which are two questions rather than one.
     *
     * The failure this pins is the one that makes a room pointless: a floor
     * under the room's cap, so a table in an empty room works anyway and every
     * block a player puts around it is decoration.
     */
    private static void ceiling() {
        check(EnchantingRules.ceiling(0.0F, EnchantingRules.BASE_MAX_ETERNA) == 0.0F,
                "a table with nothing around it could hold power anyway, which makes the "
                        + "room a decoration");
        check(EnchantingRules.ceiling(15.0F, EnchantingRules.BASE_MAX_ETERNA) == 15.0F,
                "a room of bookshelves held more than a room of bookshelves is worth");
        check(EnchantingRules.ceiling(200.0F, 50.0F) == 50.0F,
                "a room built past what its owner can carry was carried anyway");
        check(EnchantingRules.ceiling(30.0F, 500.0F) == 30.0F,
                "a master enchanter in a bookshelf room had more than a bookshelf room");
        check(EnchantingRules.ceiling(-10.0F, 50.0F) == 0.0F
                        && EnchantingRules.ceiling(30.0F, -10.0F) == 0.0F,
                "a negative cap read as a cap rather than as none");
        check(EnchantingRules.tierBonus(0) == 0.0F,
                "a player who has been nowhere was given a ceiling for it");
        check(EnchantingRules.tierBonus(5) > EnchantingRules.tierBonus(4),
                "a further world tier raised no ceiling, so the one measure every other "
                        + "system in this mod scales against stops mattering here");
        check(EnchantingRules.tierBonus(20) == EnchantingRules.tierBonus(10) * 2,
                "the world tier's contribution curves rather than climbing, which makes "
                        + "it a share; a ceiling is an amount");
        check(EnchantingRules.BASE_MAX_ETERNA + EnchantingRules.tierBonus(8)
                        > EnchantingRules.BASE_MAX_ETERNA,
                "eight world tiers added nothing to what a player may hold");
        check(EnchantingRules.ceiling(80.0F, 120.0F) > EnchantingRules.BASE_MAX_ETERNA,
                "a room and a player both built past the base could still not pass it, so "
                        + "there is nothing above the starting ceiling to build for");
        EnchantingRules.Stats held = new EnchantingRules.Stats(40.0F, 3.0F, 5.0F, 0.2F)
                .cappedAt(EnchantingRules.ceiling(15.0F, EnchantingRules.BASE_MAX_ETERNA));
        check(held.eterna() == 15.0F, "the ceiling was worked out and then not applied");
        check(held.quanta() == 3.0F && held.arcana() == 5.0F,
                "the ceiling on power also cut the other three numbers");
    }

    private static void offers() {
        EnchantingRules.Stats stats = new EnchantingRules.Stats(30.0F, 0.0F, 0.0F, 0.0F);
        int weak = EnchantingRules.offerLevel(stats, 0);
        int middle = EnchantingRules.offerLevel(stats, 1);
        int strong = EnchantingRules.offerLevel(stats, 2);
        check(weak < middle && middle < strong,
                "the three offers were not three different offers: " + weak + ", " + middle
                        + ", " + strong);
        check(strong == 30, "the strongest offer is not the table's whole power");
        check(EnchantingRules.offerLevel(EnchantingRules.Stats.NONE, 2) >= 1,
                "a table with no power offered level zero, which enchants nothing");
        check(EnchantingRules.offerLevel(stats, 99) == strong
                        && EnchantingRules.offerLevel(stats, -4) == weak,
                "a slot outside the three was not read as the nearest one");
        check(EnchantingRules.cost(0) == 1 && EnchantingRules.cost(2) == 3,
                "the price is not the slot's number, which is the price a player has always read");
        check(EnchantingRules.cost(-1) == 1 && EnchantingRules.cost(9) == EnchantingRules.OFFERS,
                "a price outside the range was not clamped into it");
    }

    private static void quanta() {
        EnchantingRules.Stats calm = new EnchantingRules.Stats(20.0F, 0.0F, 0.0F, 0.0F);
        check(EnchantingRules.rolledLevel(calm, 17, () -> 0.0D) == 17
                        && EnchantingRules.rolledLevel(calm, 17, () -> 0.999D) == 17,
                "a table with no uncertainty still rolled");
        EnchantingRules.Stats wild = new EnchantingRules.Stats(20.0F, 6.0F, 0.0F, 0.0F);
        check(EnchantingRules.rolledLevel(wild, 20, () -> 0.0D) < 20,
                "the worst roll on an uncertain table was not worse than the offer");
        check(EnchantingRules.rolledLevel(wild, 20, () -> 0.999D) > 20,
                "the best roll on an uncertain table was not better than the offer");
        EnchantingRules.Stats paid = new EnchantingRules.Stats(20.0F, 6.0F, 0.0F, 1.0F);
        check(EnchantingRules.rolledLevel(paid, 20, () -> 0.0D) == 20,
                "full rectification did not remove the downside, which is the whole of what "
                        + "it is for");
        check(EnchantingRules.rolledLevel(paid, 20, () -> 0.999D) > 20,
                "rectification removed the upside as well as the downside");
        EnchantingRules.Stats half = new EnchantingRules.Stats(20.0F, 6.0F, 0.0F, 0.5F);
        check(EnchantingRules.rolledLevel(half, 20, () -> 0.0D)
                        > EnchantingRules.rolledLevel(wild, 20, () -> 0.0D),
                "half the rectification removed none of the downside");
        Random random = new Random(20260923L);
        for (int i = 0; i < 20_000; i++) {
            check(EnchantingRules.rolledLevel(wild, 20, random::nextDouble) >= 1,
                    "an uncertain table rolled a level below one, which enchants nothing");
            if (EnchantingRules.rolledLevel(wild, 20, random::nextDouble) < 1) {
                break;
            }
        }
    }

    private static void arcana() {
        check(EnchantingRules.rarityShare(EnchantingRules.Stats.NONE) == 0.0D,
                "a table with no arcana reached for rare enchantments");
        double previous = -1.0D;
        for (int arcana = 0; arcana < 5000; arcana++) {
            EnchantingRules.Stats stats =
                    new EnchantingRules.Stats(10.0F, 0.0F, arcana, 0.0F);
            double share = EnchantingRules.rarityShare(stats);
            check(share < 1.0D, "arcana reached certainty at " + arcana);
            check(share > previous || arcana == 0, "arcana stopped rising at " + arcana);
            if (share >= 1.0D) {
                break;
            }
            previous = share;
        }
        EnchantingRules.Stats plain = new EnchantingRules.Stats(10.0F, 0.0F, 0.0F, 0.0F);
        check(EnchantingRules.available(plain, 10),
                "an ordinary enchantment was not available at a table with no arcana");
        check(!EnchantingRules.available(plain, 1),
                "the rarest enchantment was available with no arcana at all");
        EnchantingRules.Stats deep = new EnchantingRules.Stats(10.0F, 0.0F, 400.0F, 0.0F);
        check(EnchantingRules.available(deep, 1),
                "a table built entirely for arcana could still not reach the rarest thing");
        check(EnchantingRules.available(deep, 10), "arcana took the ordinary things off the table");
    }

    /**
     * The certain path, and what it costs to take it.
     *
     * The failures this pins are the two that would make one of the paths
     * pointless: infusion that reaches higher than the table can roll, and
     * infusion that is cheap.
     */
    private static void infusion() {
        check(InfusionRules.highestLevel(0.0F) == 1,
                "a room with nothing in it could not infuse even the first level of "
                        + "anything, so a found book is useless until the endgame");
        check(InfusionRules.highestLevel(50.0F) > 1,
                "the best room a player starts able to hold could infuse no more than "
                        + "a shed");
        EnchantingRules.Stats best = new EnchantingRules.Stats(
                EnchantingRules.BASE_MAX_ETERNA, 0.0F, 0.0F, 0.0F);
        int rolled = EnchantingRules.offerLevel(best, 2);
        check(InfusionRules.highestLevel(best.eterna()) < rolled,
                "the certain path reaches as high as the gamble does, so there is no "
                        + "reason left to gamble: infusion " + InfusionRules.highestLevel(
                        best.eterna()) + " against an offer of " + rolled);
        check(InfusionRules.highestLevel(-40.0F) == 1,
                "a negative room reached below the first level");
        check(InfusionRules.levelFor(5, 100.0F, 3) == 3,
                "an enchantment was infused past what its own file allows");
        check(InfusionRules.levelFor(9, 0.0F, 10) == 1,
                "a shed infused a level nine book at level nine");
        check(InfusionRules.levelFor(2, 100.0F, 10) == 2,
                "a book was infused past the level the book holds");

        check(InfusionRules.cost(1, 1.0F) >= InfusionRules.MINIMUM_COST,
                "infusing the first level of something in a fully rectified room fell "
                        + "through the floor the price is meant to have");
        check(InfusionRules.cost(5, 0.0F) > InfusionRules.cost(2, 0.0F),
                "a higher level did not cost more");
        check(InfusionRules.cost(5, 1.0F) < InfusionRules.cost(5, 0.0F),
                "rectification took nothing off the price, so the hardest of the four "
                        + "numbers does nothing on this bench at all");
        check(InfusionRules.cost(5, 1.0F) > 0,
                "a fully rectified room infused for nothing");
        check(InfusionRules.cost(5, 4.0F) == InfusionRules.cost(5, 1.0F),
                "rectification past one bought a further discount");
        int table = EnchantingRules.cost(2);
        check(InfusionRules.cost(3, 0.0F) > table,
                "certainty cost no more than the gamble did");
        check(InfusionRules.lapis(3) > InfusionRules.lapis(1) && InfusionRules.lapis(0) >= 1,
                "the lapis price did not climb with the level, or was nothing at all");

        EnchantingRules.Stats plain = new EnchantingRules.Stats(40.0F, 0.0F, 0.0F, 0.0F);
        check(InfusionRules.allows(plain, 10),
                "an ordinary enchantment could not be infused in a room with no arcana");
        check(!InfusionRules.allows(plain, 1),
                "the rarest thing in the game could be infused out of a book with no "
                        + "arcana at all, which routes straight past the one number "
                        + "that was meant to be hard");
        EnchantingRules.Stats deep = new EnchantingRules.Stats(40.0F, 0.0F, 400.0F, 0.0F);
        check(InfusionRules.allows(deep, 1),
                "a room built entirely for rarity still could not infuse a rare book");
    }

    /**
     * The library, and the one number that decides whether it is a library or
     * a photocopier.
     */
    private static void library() {
        for (int level = 2; level <= 10; level++) {
            check(LibraryRules.pages(level) == LibraryRules.pages(level - 1) * 2,
                    "a book of level " + level + " was not worth twice the one below");
        }
        check(LibraryRules.pages(0) == LibraryRules.pages(1),
                "a level below one was worth less than the first level");
        check(LibraryRules.pages(LibraryRules.MAX_LEVEL) > 0,
                "the highest level this arithmetic works at came out negative, which is "
                        + "an overflow rather than a number");
        check(LibraryRules.cost(3) == LibraryRules.pages(3) * LibraryRules.COPY_TAX,
                "a copy no longer costs the tax over what filing one is worth");
        check(LibraryRules.cost(1) > LibraryRules.pages(1),
                "a copy costs no more than filing one, so the library duplicates books "
                        + "and every enchanted book in the world is every other one");
        // Four of a level buy exactly one of the level above. This is the
        // sentence the whole economy is explained with, so it is pinned rather
        // than described.
        check(LibraryRules.pages(4) * 4 == LibraryRules.cost(5),
                "four books of a level no longer buy exactly one of the level above");
        check(LibraryRules.pages(1) * 2 == LibraryRules.cost(1),
                "two books of a level no longer buy exactly one back at that level");

        check(LibraryRules.highest(0, 5) == 0, "an empty shelf still bought a book");
        check(LibraryRules.highest(Integer.MAX_VALUE, 3) == 3,
                "a full shelf bought a copy past the enchantment's own maximum");
        check(LibraryRules.highest(LibraryRules.cost(4), 10) == 4,
                "a shelf holding exactly the price of a level could not buy it");
        check(LibraryRules.highest(LibraryRules.cost(4) - 1, 10) == 3,
                "a shelf one page short of a level bought it anyway");

        int capacity = LibraryRules.SHELF_CAPACITY;
        check(LibraryRules.deposit(0, 3, capacity) == LibraryRules.pages(3),
                "filing a book credited the wrong number of pages");
        check(!LibraryRules.room(capacity, 1, capacity),
                "a full shelf still had room");
        check(LibraryRules.deposit(capacity, 1, capacity) == capacity,
                "a full shelf took a book anyway, which is a shelf that eats what it "
                        + "has no room for");
        check(LibraryRules.deposit(capacity - 1, 5, capacity) == capacity - 1,
                "a shelf took most of a book and lost the rest");
        check(LibraryRules.withdraw(LibraryRules.cost(2), 2) == 0,
                "taking a copy did not spend the pages it cost");
        check(LibraryRules.withdraw(0, 2) == 0,
                "taking a copy from an empty shelf went into debt");

        check(LibraryRules.GRAND_CAPACITY > LibraryRules.SHELF_CAPACITY,
                "the great library holds no more than the small one, so there is "
                        + "nothing to build it for");
        check(LibraryRules.highest(LibraryRules.GRAND_CAPACITY, LibraryRules.MAX_LEVEL)
                        > LibraryRules.highest(LibraryRules.SHELF_CAPACITY,
                        LibraryRules.MAX_LEVEL),
                "the great library, filled, buys no better a book than the small one "
                        + "filled");
        check(LibraryRules.experience(4) > LibraryRules.experience(1),
                "a better copy cost no more experience than a worse one");
        check(LibraryRules.experience(0) >= 1,
                "a copy was free, so a filled shelf is a vending machine");
    }

    /**
     * The anvil, and the wall this module takes down.
     */
    private static void anvil() {
        check(AnvilRules.merged(3, 3, 10) == 4,
                "two equal enchantments did not come out one higher, which is the rule "
                        + "every player already knows");
        check(AnvilRules.merged(4, 2, 10) == 4, "the higher of two did not win");
        check(AnvilRules.merged(5, 5, 5) == 5,
                "two equal enchantments at the enchantment's own maximum went past it");
        check(AnvilRules.merged(0, 0, 5) >= 1, "a merge came out at level zero");

        check(AnvilRules.priorWorkCost(0) == 0, "an untouched item cost something to touch");
        check(AnvilRules.priorWorkCost(2) == 3 && AnvilRules.priorWorkCost(3) == 7,
                "the prior-work penalty no longer doubles, which is what makes the order "
                        + "of operations a decision");
        check(AnvilRules.priorWorkCost(40) == AnvilRules.priorWorkCost(
                        AnvilRules.PRIOR_WORK_LIMIT),
                "the prior-work penalty runs away past the limit rather than stopping at "
                        + "it, so the number shown to a player is nonsense rather than a "
                        + "price");
        check(AnvilRules.nextPriorWork(2, 3) == 4, "working on an item did not age it");

        check(AnvilRules.cost(10, 0, 0) == 10, "an operation cost more than it was worth");
        check(AnvilRules.cost(10, 3, 3) > AnvilRules.cost(10, 0, 0),
                "two well-worn items cost no more to join than two fresh ones");
        check(AnvilRules.cost(0, 0, 0) >= 1, "an anvil operation was free");
        check(AnvilRules.cost(Integer.MAX_VALUE, 6, 6) > 0,
                "a very expensive operation came out negative, which is an overflow "
                        + "rather than a price");
        // The whole point: an operation vanilla would have refused is priced
        // and offered rather than blanked out.
        int wall = AnvilRules.cost(30, 5, 5);
        check(AnvilRules.refusedByVanilla(wall),
                "the case this module exists for no longer costs enough to be refused, "
                        + "so nothing is being answered");
        check(wall > 0 && wall < Integer.MAX_VALUE,
                "the operation vanilla refuses is not priced at all here either");

        check(AnvilRules.repair(1000, 1000) < 1000, "repairing gave nothing back");
        check(AnvilRules.repair(1000, 0) == 0, "repairing an undamaged item went negative");
        check(AnvilRules.repair(100, 100) == 100 - Math.round(100 * AnvilRules.REPAIR_SHARE),
                "a repair no longer gives back its share of the item's durability");
        check(AnvilRules.forgiven() == 0,
                "the tome that clears an item's history left some of it behind");
    }

    private static void clues() {
        EnchantingRules.Stats stats = new EnchantingRules.Stats(30.0F, 0.0F, 0.0F, 0.0F);
        check(EnchantingRules.clues(stats, 0) < EnchantingRules.clues(stats, 2),
                "the cheap offer told a player as much as the expensive one, so there is "
                        + "nothing to decide");
        check(EnchantingRules.clues(stats, 0) >= 1,
                "the cheapest offer told a player nothing at all");
        check(EnchantingRules.clues(stats, 9) <= EnchantingRules.OFFERS,
                "a slot outside the three promised more clues than there are offers");
    }
}
