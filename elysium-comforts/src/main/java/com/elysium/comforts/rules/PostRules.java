package com.elysium.comforts.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mail: a thing you send that takes time to arrive and might not.
 *
 * <h2>Why a postal system is arithmetic and not furniture</h2>
 *
 * A mailbox that hands the parcel over the moment you post it is an ender
 * chest with extra steps, and every player works that out inside an hour. A
 * mailbox that takes a week is a block nobody uses twice. Everything
 * interesting about post lives in the space between those two, and that space
 * is a function: how long a parcel takes, given how far it has to go.
 *
 * <p>So {@link #travelTicks(int)} is the centre of this file, and four rules
 * hold it down — it never returns instantly, it never returns never, it never
 * gets quicker when the distance grows, and past {@link #FURTHEST} it stops
 * growing so that a player on the far side of the world is not simply cut off
 * from the post. Each of those four is its own rule with its own control,
 * because the first draft had one rule answering all four and a control aimed
 * at the floor was quietly caught by the ceiling.
 *
 * <h2>The two things that can go wrong with a parcel</h2>
 *
 * It can be lost, and it can be a swindle. Loss is priced by
 * {@link #LOSS_IN_A_THOUSAND}: above zero, or nothing is at stake and the
 * post is just slow storage; below {@link #MOST_LOSS_IN_A_THOUSAND}, or
 * nobody posts anything worth posting. And what comes back when a parcel is
 * lost — {@link #LOST_MAIL_WORTH} — has to be worth strictly less than what
 * went in, or losing things becomes a way to make money.
 *
 * <p>The swindle is subtler and is the reason {@link #faultsInPayback} exists.
 * Payment on delivery is a promise with a clock on it: you send goods, the
 * other end has so long to pay. If that clock can run out before the goods
 * can physically arrive — which is to say, if the shortest payback window is
 * shorter than the longest delivery — then the system ships a swindle as a
 * feature, and it is invisible in every screenshot. So the payback windows
 * are measured against {@link #LATEST}, not against each other alone.
 */
public final class PostRules {

    private PostRules() {
    }

    // ==================================================================
    // What you can put in the post
    // ==================================================================

    /** Words, and nothing else. */
    public static final String LETTER = "letter";

    /** Words nobody else may read. */
    public static final String SEALED_LETTER = "sealed_letter";

    /** Things. */
    public static final String PACKAGE = "package";

    /** Things nobody else may take. */
    public static final String SEALED_PACKAGE = "sealed_package";

    /** An empty one, waiting to be filled. */
    public static final String PAPER_BOX = "paper_box";

    /** Things, sent on the understanding that they will be paid for. */
    public static final String PAYBACK_PACKAGE = "payback_package";

    /** What the payment comes back in. */
    public static final String PAYBACK_BOX = "payback_box";

    /** What arrives when something did not. */
    public static final String LOST_MAIL = "lost_mail";

    /** The eight, in the order everything walks them. */
    public static final List<String> KINDS = List.of(
            LETTER, SEALED_LETTER, PACKAGE, SEALED_PACKAGE, PAPER_BOX,
            PAYBACK_PACKAGE, PAYBACK_BOX, LOST_MAIL);

    /**
     * One kind of mail.
     *
     * @param id      what it is called
     * @param slots   how many stacks it carries; 0 for something that only
     *                carries words
     * @param postage what it costs to send, in paper
     * @param sealed  whether only the addressee may open it
     * @param pays    whether it asks for payment on delivery
     * @param posted  whether it is a thing you hand to the post at all
     */
    public record Mail(String id, int slots, int postage, boolean sealed,
                       boolean pays, boolean posted) {

        public Mail {
            if (slots < 0) {
                throw new IllegalArgumentException(id + " carries " + slots
                        + " stacks, which is not an amount of room");
            }
            if (postage < 0) {
                throw new IllegalArgumentException(id + " costs " + postage
                        + " paper to send, which is not a price");
            }
        }
    }

    /**
     * The eight, priced.
     *
     * <p>A sealed thing costs more than the same thing unsealed because the
     * seal is the service; a payback package costs more again because
     * somebody has to carry the payment home. The rules below check the shape
     * of that rather than the numbers, so the numbers can move.
     *
     * <p>Two of the eight are not posted at all. A paper box is the empty
     * container you fill — it becomes a package when you address it — and
     * lost mail is what turns up when something did not. Pricing either of
     * them as postage is how the first draft of this table ended up with a
     * box that undercut the parcel it becomes, which is to say with a postage
     * system a player skips by never addressing anything.
     */
    public static final List<Mail> MAIL = List.of(
            new Mail(LETTER, 0, 1, false, false, true),
            new Mail(SEALED_LETTER, 0, 2, true, false, true),
            new Mail(PACKAGE, 9, 4, false, false, true),
            new Mail(SEALED_PACKAGE, 9, 6, true, false, true),
            new Mail(PAPER_BOX, 9, 0, false, false, false),
            new Mail(PAYBACK_PACKAGE, 9, 8, true, true, true),
            new Mail(PAYBACK_BOX, 3, 5, true, true, true),
            new Mail(LOST_MAIL, 3, 0, false, false, false));

    /** The mail of this name, or null. */
    public static Mail mail(String id) {
        for (Mail one : MAIL) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    // ==================================================================
    // How far, and how long
    // ==================================================================

    /** Blocks a courier covers in a tick. */
    public static final double BLOCKS_A_TICK = 1.0D;

    /** Handing it over at each end, in ticks. */
    public static final int HANDLING = 600;

    /** Past this many blocks the journey stops getting longer. */
    public static final int FURTHEST = 5_000;

    /** No parcel arrives sooner than this, in ticks. */
    public static final int SOONEST = 600;

    /** And none later, in ticks. */
    public static final int LATEST = 6_000;

    /** How far apart two addresses are taken to be when nobody knows. */
    public static final int UNKNOWN_DISTANCE = 1_500;

    /**
     * How long a parcel takes to cross this many blocks.
     *
     * <p>Handling at both ends plus the journey, with the journey flat past
     * {@link #FURTHEST}. There is deliberately <em>no clamp</em> into the band
     * {@link #SOONEST} and {@link #LATEST} describe. The first draft had one,
     * and it made four rules unbreakable: an answer the function has already
     * forced into range cannot be caught out of range, so the floor and the
     * ceiling agreed with themselves whatever the constants said. The band is
     * a promise the arithmetic has to keep on its own, and the rules are what
     * check that it does.
     */
    public static int travelTicks(int blocks) {
        return travelTicks(blocks, HANDLING, FURTHEST, BLOCKS_A_TICK);
    }

    /**
     * The same, with every number handed in.
     *
     * <p>Handed in, because a rule that reads the constants it is judging
     * re-solves against the broken value and goes on passing.
     */
    public static int travelTicks(int blocks, int handling, int furthest,
                                  double blocksATick) {
        int far = Math.max(0, Math.min(blocks, furthest));
        double journey = blocksATick <= 0.0D ? 0.0D : far / blocksATick;
        return (int) (handling + Math.round(journey));
    }

    // ==================================================================
    // Who you can send it to
    // ==================================================================

    /** Somebody playing. */
    public static final String TO_PLAYER = "player";

    /** A mailbox somewhere, by its address. */
    public static final String TO_BLOCK = "block";

    /** Something the world runs rather than somebody. */
    public static final String TO_SERVICE = "service";

    /** A name written on a tag and nothing else. */
    public static final String TO_CUSTOM = "custom";

    /** The four, in the order everything walks them. */
    public static final List<String> ADDRESS_KINDS = List.of(
            TO_PLAYER, TO_BLOCK, TO_SERVICE, TO_CUSTOM);

    /**
     * Something the world will answer.
     *
     * @param id     what it is called
     * @param away   how far off it is taken to be, in blocks
     * @param hidden whether it is written on any tag a player can read
     */
    public record Service(String id, int away, boolean hidden) {

        public Service {
            if (away < 0) {
                throw new IllegalArgumentException(id + " is " + away
                        + " blocks off, which is not a distance");
            }
        }
    }

    /**
     * The three.
     *
     * <p>Each at its own distance, because distance is the only thing that
     * separates one service from another at the counter: two services the
     * same distance off cost the same and take the same time, so one of them
     * is a name with nothing behind it.
     */
    public static final List<Service> SERVICES = List.of(
            new Service("mail_service", 0, false),
            new Service("supply_service", 1_000, false),
            new Service("horse_bureau", 2_000, true));

    /** The longest an address may be, in characters. */
    public static final int LONGEST_ADDRESS = 24;

    /** What it costs to claim one, in levels. */
    public static final int ADDRESS_LEVELS = 3;

    /**
     * Whether this would do as an address.
     *
     * <p>Two guards rather than one condition, so that breaking either leaves
     * the other standing. The first draft asked {@code written == null ||
     * written.isEmpty()} in one breath: deleting that line did not report an
     * empty address, it threw on a null one, and a rule that crashes instead
     * of reporting is a rule whose message nobody ever reads.
     */
    public static boolean addressable(String written) {
        if (written == null) {
            return false;
        }
        if (written.isEmpty()) {
            return false;
        }
        if (written.length() > LONGEST_ADDRESS) {
            return false;
        }
        for (int at = 0; at < written.length(); at++) {
            char one = written.charAt(at);
            boolean fine = (one >= 'a' && one <= 'z')
                    || (one >= '0' && one <= '9') || one == '_';
            if (!fine) {
                return false;
            }
        }
        return true;
    }

    // ==================================================================
    // Payment on delivery
    // ==================================================================

    /** Ticks in a minute. */
    public static final int A_MINUTE = 1_200;

    /** The short window, in minutes. */
    public static final int SHORT_WAIT = 30;

    /** The usual one. */
    public static final int MEDIUM_WAIT = 180;

    /** And the one for somebody who is not logging in tonight. */
    public static final int LONG_WAIT = 4_320;

    /** The three, shortest first. */
    public static final List<Integer> WAITS =
            List.of(SHORT_WAIT, MEDIUM_WAIT, LONG_WAIT);

    /** That window in ticks. */
    public static long waitTicks(int minutes) {
        return (long) minutes * A_MINUTE;
    }

    // ==================================================================
    // What is lost, and what comes back
    // ==================================================================

    /** Parcels lost per thousand sent. */
    public static final int LOSS_IN_A_THOUSAND = 15;

    /** And the most that may ever be. */
    public static final int MOST_LOSS_IN_A_THOUSAND = 100;

    /** What lost mail is worth, against the parcel it came from. */
    public static final double LOST_MAIL_WORTH = 0.25D;

    // ==================================================================
    // The pigeonholes
    // ==================================================================

    /** The woods the pack builds in. */
    public static final List<String> WOODS = List.of(
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
            "mangrove", "cherry", "bamboo", "crimson", "warped");

    /** What a pigeonhole in this wood is called. */
    public static String pigeonhole(String wood) {
        return wood + "_pigeonhole";
    }

    /** Every pigeonhole, in wood order. */
    public static List<String> pigeonholes() {
        List<String> made = new ArrayList<>();
        for (String wood : WOODS) {
            made.add(pigeonhole(wood));
        }
        return List.copyOf(made);
    }

    /** The dirtiest a pigeonhole gets. */
    public static final int FOULEST = 3;

    /**
     * What scooping a pigeonhole at this level hands back.
     *
     * <p>Strictly more for a dirtier one, or there is no reason to leave a
     * pigeon in long enough to make a mess, and the mess is the point: it is
     * what turns a decoration into a farm.
     */
    public static int wasteAt(int level) {
        return wasteAt(level, FOULEST);
    }

    /**
     * The same, with the top level handed in.
     *
     * <p>Handed in, because the first draft read {@link #FOULEST} out of the
     * field it was being judged against: lowering the constant lowered the
     * answer too, and the rule that checks the climb re-solved against the
     * broken value and went on passing.
     */
    public static int wasteAt(int level, int foulest) {
        int held = Math.max(0, Math.min(level, foulest));
        return held * 2;
    }

    // ==================================================================
    // The birds
    // ==================================================================

    /**
     * One carrier.
     *
     * @param id         what it is called
     * @param home       the dimension it belongs in
     * @param becomes    what it turns into somewhere else, or null
     * @param afterTicks how long that takes
     * @param variants   how many colourings it comes in
     */
    public record Bird(String id, String home, String becomes, int afterTicks,
                       int variants) {

        public Bird {
            if (variants <= 0) {
                throw new IllegalArgumentException(id + " comes in " + variants
                        + " colourings, so it is a mob nothing can render");
            }
            if (becomes != null && afterTicks <= 0) {
                throw new IllegalArgumentException(id + " becomes " + becomes
                        + " in " + afterTicks + " ticks, which is no time at all");
            }
        }
    }

    /** The overworld and everywhere like it. */
    public static final String ABOVE = "overworld";

    /** Where it is too hot for feathers. */
    public static final String BELOW = "nether";

    /** The two. */
    public static final List<Bird> BIRDS = List.of(
            new Bird("pigeon", ABOVE, "charred_pigeon", 300, 4),
            new Bird("charred_pigeon", BELOW, "pigeon", 300, 1));

    /** The bird of this name, or null. */
    public static Bird bird(String id) {
        return among(BIRDS, id);
    }

    /** The bird of this name in this list, or null. */
    private static Bird among(List<Bird> birds, String id) {
        for (Bird one : birds) {
            if (one.id().equals(id)) {
                return one;
            }
        }
        return null;
    }

    /** How long a bird rests after a delivery, in ticks. */
    public static final int TIRED_FOR = 6_000;

    /** The least time a bird spends in a pigeonhole. */
    public static final int LEAST_INSIDE = 1_200;

    /** And the least it spends out of one. */
    public static final int LEAST_OUTSIDE = 2_400;

    // ==================================================================
    // Seals
    // ==================================================================

    /** What a seal is pressed in. */
    public static final List<String> SEAL_MATERIALS =
            List.of("red_wax", "gold", "lapis");

    /** Every symbol a stamp can carry: the alphabet and the figures. */
    public static List<String> symbols() {
        List<String> made = new ArrayList<>();
        for (char one = 'a'; one <= 'z'; one++) {
            made.add(String.valueOf(one));
        }
        for (char one = '0'; one <= '9'; one++) {
            made.add(String.valueOf(one));
        }
        return List.copyOf(made);
    }

    /** Every seal there is: a material pressed with a symbol. */
    public static List<String> seals() {
        List<String> made = new ArrayList<>();
        for (String material : SEAL_MATERIALS) {
            for (String symbol : symbols()) {
                made.add(material + "_" + symbol);
            }
        }
        return List.copyOf(made);
    }

    // ==================================================================
    // What has to be true of all of it
    // ==================================================================

    /** Everything wrong with the post. Must be empty. */
    public static List<String> faults() {
        List<String> bad = new ArrayList<>();
        bad.addAll(faultsInKinds(MAIL, KINDS));
        bad.addAll(faultsInNames(MAIL));
        bad.addAll(faultsInGaps(MAIL, KINDS));
        bad.addAll(faultsInPostage(MAIL));
        bad.addAll(faultsInPosted(MAIL));
        bad.addAll(faultsInSeals(MAIL));
        bad.addAll(faultsInFloor(SOONEST, LATEST, FURTHEST, HANDLING,
                BLOCKS_A_TICK));
        bad.addAll(faultsInBand(SOONEST, LATEST));
        bad.addAll(faultsInCeiling(SOONEST, LATEST, FURTHEST, HANDLING,
                BLOCKS_A_TICK));
        bad.addAll(faultsInClimb(FURTHEST, HANDLING, BLOCKS_A_TICK));
        bad.addAll(faultsInCap(FURTHEST, HANDLING, BLOCKS_A_TICK));
        bad.addAll(faultsInServices(SERVICES, FURTHEST));
        bad.addAll(faultsInAddresses(LONGEST_ADDRESS));
        bad.addAll(faultsInWaits(WAITS));
        bad.addAll(faultsInPayback(WAITS, LATEST));
        bad.addAll(faultsInLoss(LOSS_IN_A_THOUSAND, MOST_LOSS_IN_A_THOUSAND,
                LOST_MAIL_WORTH));
        bad.addAll(faultsInHoleNames(pigeonholes()));
        bad.addAll(faultsInHoleGaps(pigeonholes(), WOODS));
        bad.addAll(faultsInHoleWoods(pigeonholes(), WOODS));
        bad.addAll(faultsInFouling(FOULEST));
        bad.addAll(faultsInWaste(FOULEST));
        bad.addAll(faultsInTop(FOULEST));
        bad.addAll(faultsInBirds(BIRDS));
        bad.addAll(faultsInNowhere(BIRDS));
        bad.addAll(faultsInUnknown(BIRDS));
        bad.addAll(faultsInLoop(BIRDS));
        bad.addAll(faultsInSymbolNames(symbols()));
        bad.addAll(faultsInAlphabet(symbols()));
        bad.addAll(faultsInDigits(symbols()));
        bad.addAll(faultsInRosterNames(seals()));
        bad.addAll(faultsInRosterCuts(seals(), SEAL_MATERIALS, symbols()));
        bad.addAll(faultsInRosterCount(seals(), SEAL_MATERIALS, symbols()));
        return List.copyOf(bad);
    }

    /** Whether every piece of mail is one of the kinds. */
    public static List<String> faultsInKinds(List<Mail> mail, List<String> kinds) {
        List<String> bad = new ArrayList<>();
        for (Mail one : mail) {
            if (!kinds.contains(one.id())) {
                bad.add(one.id() + " is a kind of mail nothing else here has "
                        + "heard of, so it is an item with no post behind it");
            }
        }
        return bad;
    }

    /** Whether two pieces of mail are written under one name. */
    public static List<String> faultsInNames(List<Mail> mail) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        for (Mail one : mail) {
            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows "
                        + "is a set of numbers nothing reads");
            }
        }
        return bad;
    }

    /** Whether one of the kinds has nothing that is it. */
    public static List<String> faultsInGaps(List<Mail> mail, List<String> kinds) {
        List<String> bad = new ArrayList<>();
        Set<String> made = new LinkedHashSet<>();
        for (Mail one : mail) {
            made.add(one.id());
        }
        for (String kind : kinds) {
            if (!made.contains(kind)) {
                bad.add("nothing in the post is a " + kind + ", so a kind the "
                        + "rest of the module names cannot be made");
            }
        }
        return bad;
    }

    /**
     * Whether anything carries more per piece of paper than something else
     * while also being no worse in any other way.
     *
     * <p>The categorical axis is sealing: a sealed parcel and an open one are
     * not comparable, because what the seal buys is not room. Without that
     * axis this rule demands that a letter and a package be ranked, and they
     * are different things.
     */
    public static List<String> faultsInPostage(List<Mail> mail) {
        List<String> bad = new ArrayList<>();
        for (Mail one : mail) {
            if (!one.posted()) {
                continue;
            }
            for (Mail other : mail) {
                if (one == other || !other.posted()) {
                    continue;
                }
                if (one.sealed() != other.sealed() || one.pays() != other.pays()) {
                    continue;
                }
                boolean asGood = other.slots() >= one.slots()
                        && other.postage() <= one.postage();
                boolean better = other.slots() > one.slots()
                        || other.postage() < one.postage();
                if (asGood && better) {
                    bad.add(one.id() + " is beaten by " + other.id() + " on room "
                            + "and on price at once (" + one.slots() + " stacks "
                            + "for " + one.postage() + " paper against "
                            + other.slots() + " for " + other.postage()
                            + "), so nobody would ever send one");
                }
            }
        }
        return bad;
    }

    /**
     * Whether what is posted costs and what is not posted does not.
     *
     * <p>Its own rule because {@link #faultsInPostage} is written to skip
     * anything that is not posted, which makes it exactly the rule that
     * cannot notice a parcel being free.
     */
    public static List<String> faultsInPosted(List<Mail> mail) {
        List<String> bad = new ArrayList<>();
        for (Mail one : mail) {
            if (one.posted() && one.postage() <= 0) {
                bad.add(one.id() + " is handed to the post and costs "
                        + one.postage() + " paper, so postage is free and the "
                        + "price of sending buys nothing");
            }
            if (!one.posted() && one.postage() > 0) {
                bad.add(one.id() + " is never handed to the post and yet costs "
                        + one.postage() + " paper, so a player is charged for "
                        + "something they cannot send");
            }
        }
        return bad;
    }

    /**
     * Whether a seal costs anything.
     *
     * <p>Its own rule rather than a clause inside {@link #faultsInPostage},
     * because the domination rule deliberately refuses to compare a sealed
     * parcel with an open one — which means it is exactly the rule that
     * cannot notice a seal being free.
     */
    public static List<String> faultsInSeals(List<Mail> mail) {
        List<String> bad = new ArrayList<>();
        for (Mail one : mail) {
            if (!one.sealed() || !one.posted()) {
                continue;
            }
            for (Mail other : mail) {
                if (other.sealed() || !other.posted()
                        || other.slots() != one.slots()
                        || other.pays() != one.pays()) {
                    continue;
                }
                if (one.postage() <= other.postage()) {
                    bad.add(one.id() + " is sealed and costs " + one.postage()
                            + " paper against " + other.id() + "'s "
                            + other.postage() + " for the same room, so the "
                            + "seal is free and nothing would ever go unsealed");
                }
            }
        }
        return bad;
    }

    /** Whether any parcel arrives instantly. */
    public static List<String> faultsInFloor(int soonest, int latest,
                                             int furthest, int handling,
                                             double blocksATick) {
        List<String> bad = new ArrayList<>();
        if (soonest <= 0) {
            bad.add("the soonest a parcel arrives is " + soonest + " ticks, so "
                    + "the post is an ender chest with a longer recipe");
        }
        for (int blocks : List.of(0, 1, 10, furthest / 2, furthest, furthest * 2)) {
            int took = travelTicks(blocks, handling, furthest, blocksATick);
            if (took < soonest) {
                bad.add("a parcel going " + blocks + " blocks arrives in " + took
                        + " ticks, sooner than the " + soonest + " the post "
                        + "promises is the least, so a player next door has a "
                        + "chest rather than a post office");
            }
        }
        return bad;
    }

    /**
     * Whether the band has any width in it.
     *
     * <p>Its own rule rather than the first clause of the one below, because a
     * band with no width also puts every journey past the ceiling — so the
     * control aimed at this was answered by that, and this clause could have
     * been deleted without anything noticing.
     */
    public static List<String> faultsInBand(int soonest, int latest) {
        List<String> bad = new ArrayList<>();
        if (latest <= soonest) {
            bad.add("the latest a parcel arrives is " + latest + " ticks and the "
                    + "soonest is " + soonest + ", so every parcel takes exactly "
                    + "as long as every other and distance means nothing");
        }
        return bad;
    }

    /** Whether any parcel takes longer than a player will wait. */
    public static List<String> faultsInCeiling(int soonest, int latest,
                                               int furthest, int handling,
                                               double blocksATick) {
        List<String> bad = new ArrayList<>();
        for (int blocks : List.of(0, furthest, furthest * 4, Integer.MAX_VALUE / 2)) {
            int took = travelTicks(blocks, handling, furthest, blocksATick);
            if (took > latest) {
                bad.add("a parcel going " + blocks + " blocks takes " + took
                        + " ticks, past the " + latest + " the post promises is "
                        + "the most, so somebody far enough out is cut off");
            }
        }
        return bad;
    }

    /** Whether going further ever gets quicker. */
    public static List<String> faultsInClimb(int furthest, int handling,
                                             double blocksATick) {
        List<String> bad = new ArrayList<>();
        int before = travelTicks(0, handling, furthest, blocksATick);
        int step = Math.max(1, furthest / 50);
        for (int blocks = 0; blocks <= furthest * 2; blocks += step) {
            int took = travelTicks(blocks, handling, furthest, blocksATick);
            if (took < before) {
                bad.add("a parcel going " + blocks + " blocks arrives in " + took
                        + " ticks when a shorter one took " + before + ", so the "
                        + "post is quicker the further it goes");
                break;
            }
            before = took;
        }
        return bad;
    }

    /** Whether the journey ever stops growing. */
    public static List<String> faultsInCap(int furthest, int handling,
                                           double blocksATick) {
        List<String> bad = new ArrayList<>();
        int at = travelTicks(furthest, handling, furthest, blocksATick);
        int past = travelTicks(furthest * 3, handling, furthest, blocksATick);
        if (at != past) {
            bad.add("a parcel going " + (furthest * 3) + " blocks takes " + past
                    + " ticks against " + at + " at " + furthest + ", so the "
                    + "journey never stops growing and distance alone can put "
                    + "somebody out of reach");
        }
        return bad;
    }

    /** Whether the services are all somewhere, and somewhere different. */
    public static List<String> faultsInServices(List<Service> services,
                                                int furthest) {
        List<String> bad = new ArrayList<>();
        Set<Integer> at = new LinkedHashSet<>();
        for (Service one : services) {
            if (one.away() > furthest) {
                bad.add(one.id() + " is " + one.away() + " blocks off, past the "
                        + furthest + " where the journey stops growing, so it "
                        + "costs the same as anything else that far out");
            }
            if (!at.add(one.away())) {
                bad.add(one.id() + " is " + one.away() + " blocks off, the same "
                        + "as another service, so the two are the same counter "
                        + "under two names");
            }
        }
        return bad;
    }

    /** Whether the rules about what an address may say hold. */
    public static List<String> faultsInAddresses(int longest) {
        List<String> bad = new ArrayList<>();
        if (addressable("")) {
            bad.add("the empty string is an address, so a tag with nothing "
                    + "written on it addresses something");
        }
        if (addressable(null)) {
            bad.add("nothing at all is an address, so a tag nobody wrote on "
                    + "addresses something");
        }
        if (!addressable("dylan")) {
            bad.add("a plain name is not an address, so nothing can be sent");
        }
        if (addressable("a b")) {
            bad.add("an address may hold a space, and a space is how one "
                    + "address is read as two");
        }
        if (addressable("A")) {
            bad.add("an address may hold a capital, so two addresses differing "
                    + "only in case look the same written down");
        }
        if (!addressable("a".repeat(longest))) {
            bad.add("an address of " + longest + " characters is refused, though "
                    + longest + " is what the post says is the longest");
        }
        if (addressable("a".repeat(longest + 1))) {
            bad.add("an address of " + (longest + 1) + " characters is taken, "
                    + "past the " + longest + " the post says is the longest");
        }
        return bad;
    }

    /** Whether the three payback windows are actually three. */
    public static List<String> faultsInWaits(List<Integer> waits) {
        List<String> bad = new ArrayList<>();
        for (int at = 1; at < waits.size(); at++) {
            int before = waits.get(at - 1);
            int now = waits.get(at);
            if (now <= before) {
                bad.add("a payback window of " + now + " minutes follows one of "
                        + before + ", so the windows are not in order and two of "
                        + "the three are the same choice");
            }
        }
        return bad;
    }

    /**
     * Whether the shortest payback window outlasts the longest delivery.
     *
     * <p>The rule this file exists for. A window shorter than a journey is a
     * promise that cannot be kept by anybody honest, which makes the whole
     * feature a way to take somebody's goods and wait.
     */
    public static List<String> faultsInPayback(List<Integer> waits, int latest) {
        List<String> bad = new ArrayList<>();
        for (int minutes : waits) {
            long window = waitTicks(minutes);
            if (window <= latest) {
                bad.add("a payback window of " + minutes + " minutes is " + window
                        + " ticks and the longest delivery is " + latest + ", so "
                        + "the payment can be due before the goods can arrive "
                        + "and the post ships a swindle as a feature");
            }
        }
        return bad;
    }

    /** Whether losing a parcel is possible, rare, and a loss. */
    public static List<String> faultsInLoss(int inAThousand, int most,
                                            double worth) {
        List<String> bad = new ArrayList<>();
        if (inAThousand <= 0) {
            bad.add("nothing is ever lost in the post, so a sealed parcel and a "
                    + "chest are the same thing and the seal buys nothing");
        }
        if (inAThousand > most) {
            bad.add(inAThousand + " parcels in a thousand are lost, past the "
                    + most + " the post allows, so nobody sends anything twice");
        }
        if (worth >= 1.0D) {
            bad.add("lost mail is worth " + worth + " of what was posted, so "
                    + "losing a parcel is at least as good as it arriving and "
                    + "the way to play the post is to post to nowhere");
        }
        if (worth < 0.0D) {
            bad.add("lost mail is worth " + worth + " of what was posted, which "
                    + "is not an amount of anything");
        }
        return bad;
    }

    /**
     * Whether a pigeonhole is made twice.
     *
     * <p>Three rules where there was one, because the first draft's three
     * clauses shared a set: killing the duplicate check left the set empty, so
     * the gap check fired instead and the control aimed at duplicates was
     * quietly answered by a neighbour.
     */
    public static List<String> faultsInHoleNames(List<String> holes) {
        List<String> bad = new ArrayList<>();
        Set<String> made = new LinkedHashSet<>();
        for (String hole : holes) {
            if (!made.add(hole)) {
                bad.add(hole + " is made twice, so one of the two is a block "
                        + "nothing registers");
            }
        }
        return bad;
    }

    /** Whether a wood the pack builds in has no pigeonhole. */
    public static List<String> faultsInHoleGaps(List<String> holes,
                                                List<String> woods) {
        List<String> bad = new ArrayList<>();
        Set<String> made = new LinkedHashSet<>(holes);
        for (String wood : woods) {
            if (!made.contains(pigeonhole(wood))) {
                bad.add("there is no pigeonhole in " + wood + ", so a player who "
                        + "builds in " + wood + " has to build the post in "
                        + "something else");
            }
        }
        return bad;
    }

    /** Whether a pigeonhole names a wood the pack has not got. */
    public static List<String> faultsInHoleWoods(List<String> holes,
                                                 List<String> woods) {
        List<String> bad = new ArrayList<>();
        for (String hole : holes) {
            boolean known = false;
            for (String wood : woods) {
                if (hole.equals(pigeonhole(wood))) {
                    known = true;
                }
            }
            if (!known) {
                bad.add(hole + " is a pigeonhole in a wood the pack does not "
                        + "have, so it is a block with no planks to make it");
            }
        }
        return bad;
    }

    /**
     * Whether a pigeonhole ever gets dirty at all.
     *
     * <p>Its own rule, because the climb below walks {@link #wasteAt} against
     * the top level handed in — and with a top of nought the climb has nothing
     * to walk, so the control aimed here was answered by the stop-growing
     * clause instead.
     */
    public static List<String> faultsInFouling(int foulest) {
        List<String> bad = new ArrayList<>();
        if (foulest <= 0) {
            bad.add("a pigeonhole never gets dirty, so there is nothing to scoop "
                    + "and a pigeonhole is a decoration");
        }
        return bad;
    }

    /**
     * What every level of a pigeonhole hands back, nought to the top.
     *
     * <p>Named so that the two rules below can be handed a ladder rather than
     * left to build one out of {@link #wasteAt}. A rule that builds its own
     * subject cannot be given a broken one, which means it cannot be given a
     * control either — and the first draft of both of these had exactly that
     * problem: breaking the climb changed nothing anything could see.
     */
    public static List<Integer> wasteLadder(int foulest) {
        List<Integer> made = new ArrayList<>();
        for (int level = 0; level <= Math.max(0, foulest); level++) {
            made.add(wasteAt(level, foulest));
        }
        return List.copyOf(made);
    }

    /** Whether a dirtier pigeonhole is worth more to scoop. */
    public static List<String> faultsInWaste(int foulest) {
        return faultsInWaste(wasteLadder(foulest));
    }

    /** The same, over a ladder handed in. */
    public static List<String> faultsInWaste(List<Integer> ladder) {
        List<String> bad = new ArrayList<>();
        if (ladder.isEmpty()) {
            bad.add("a pigeonhole hands back nothing at any level, so there is "
                    + "no ladder to climb and nothing to scoop");
        }
        // Three independent guards rather than one that returns early. The
        // first draft returned, so deleting it did not report an empty ladder
        // -- it threw on the next line, and a rule that crashes instead of
        // reporting is a rule whose message nobody ever reads.
        if (!ladder.isEmpty() && ladder.get(0) != 0) {
            bad.add("a clean pigeonhole hands back " + ladder.get(0)
                    + ", so a player is paid for a bird that has not been there");
        }
        for (int level = 1; level < ladder.size(); level++) {
            if (ladder.get(level) <= ladder.get(level - 1)) {
                bad.add("a pigeonhole at level " + level + " hands back "
                        + ladder.get(level) + " against " + ladder.get(level - 1)
                        + " at " + (level - 1) + ", so there is no reason to "
                        + "leave a bird in it");
            }
        }
        return bad;
    }

    /** Whether the top level of dirt is the top. */
    public static List<String> faultsInTop(int foulest) {
        return faultsInTop(foulest, wasteAt(foulest, foulest),
                wasteAt(foulest + 5, foulest));
    }

    /** The same, with both amounts handed in. */
    public static List<String> faultsInTop(int foulest, int atTop, int past) {
        List<String> bad = new ArrayList<>();
        if (atTop != past) {
            bad.add("a pigeonhole past level " + foulest + " hands back " + past
                    + " against " + atTop + ", so the top level is not the top");
        }
        return bad;
    }

    /** Whether the birds are birds. */
    public static List<String> faultsInBirds(List<Bird> birds) {
        List<String> bad = new ArrayList<>();
        Set<String> named = new LinkedHashSet<>();
        Set<String> homes = new LinkedHashSet<>();
        for (Bird one : birds) {
            if (!named.add(one.id())) {
                bad.add(one.id() + " is written twice, so one of the two rows is "
                        + "a bird nothing registers");
            }
            if (!homes.add(one.home())) {
                bad.add(one.id() + " lives in " + one.home() + " and so does "
                        + "another bird, so the two never stop turning into each "
                        + "other");
            }
        }
        return bad;
    }

    /**
     * Whether a bird taken somewhere else turns into anything at all.
     *
     * <p>Three rules where there was one, and looked up inside the list handed
     * in rather than in {@link #BIRDS}. The first draft did both the other
     * way: its three clauses answered each other's controls, and because it
     * searched the module's own roster no control built out of invented birds
     * could reach the clause it was aimed at.
     */
    public static List<String> faultsInNowhere(List<Bird> birds) {
        List<String> bad = new ArrayList<>();
        for (Bird one : birds) {
            if (one.becomes() == null) {
                bad.add(one.id() + " turns into nothing wherever it is taken, so "
                        + "the dimension it does not belong in is a place it "
                        + "simply lives");
            }
        }
        return bad;
    }

    /** Whether a bird turns into something this module does not have. */
    public static List<String> faultsInUnknown(List<Bird> birds) {
        List<String> bad = new ArrayList<>();
        for (Bird one : birds) {
            if (one.becomes() == null) {
                continue;
            }
            if (among(birds, one.becomes()) == null) {
                bad.add(one.id() + " turns into " + one.becomes()
                        + ", which is not a bird this module has, so a player "
                        + "who carries one below loses it");
            }
        }
        return bad;
    }

    /** Whether a bird carried somewhere else can ever come back. */
    public static List<String> faultsInLoop(List<Bird> birds) {
        List<String> bad = new ArrayList<>();
        for (Bird one : birds) {
            if (one.becomes() == null) {
                continue;
            }
            Bird then = among(birds, one.becomes());
            if (then == null) {
                continue;
            }
            if (!one.id().equals(then.becomes())) {
                bad.add(one.id() + " turns into " + then.id() + " and " + then.id()
                        + " turns into " + then.becomes() + ", so the change is "
                        + "one-way and a bird carried below never comes back");
            }
        }
        return bad;
    }

    /**
     * Whether a symbol is cut twice.
     *
     * <p>Three rules where there was one. The first draft filled its set
     * inside the duplicate check, so killing that check emptied the set and
     * the alphabet check fired in its place — a control answered by a
     * neighbour, which is this project's commonest way of shipping a rule that
     * has quietly stopped working.
     */
    public static List<String> faultsInSymbolNames(List<String> symbols) {
        List<String> bad = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>();
        for (String one : symbols) {
            if (!have.add(one)) {
                bad.add("the symbol " + one + " is cut twice, so one of the two "
                        + "stamps is a stamp nothing presses");
            }
        }
        return bad;
    }

    /** Whether every letter of the alphabet has a stamp. */
    public static List<String> faultsInAlphabet(List<String> symbols) {
        List<String> bad = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>(symbols);
        for (char one = 'a'; one <= 'z'; one++) {
            if (!have.contains(String.valueOf(one))) {
                bad.add("there is no stamp for " + one + ", so an address "
                        + "holding it cannot be sealed");
            }
        }
        return bad;
    }

    /** And every figure. */
    public static List<String> faultsInDigits(List<String> symbols) {
        List<String> bad = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>(symbols);
        for (char one = '0'; one <= '9'; one++) {
            if (!have.contains(String.valueOf(one))) {
                bad.add("there is no stamp for the figure " + one + ", so an "
                        + "address holding it cannot be sealed");
            }
        }
        return bad;
    }

    /** Whether a seal is in the roster twice. */
    public static List<String> faultsInRosterNames(List<String> seals) {
        List<String> bad = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>();
        for (String one : seals) {
            if (!have.add(one)) {
                bad.add(one + " is in the roster twice, so one of the two is a "
                        + "seal nothing presses");
            }
        }
        return bad;
    }

    /**
     * Whether every material is cut with every symbol.
     *
     * <p>Split from the count below, because a roster short of a material's
     * worth of seals is also short on the total — so the control aimed here
     * was answered there, and this clause could have gone.
     */
    public static List<String> faultsInRosterCuts(List<String> seals,
                                                  List<String> materials,
                                                  List<String> symbols) {
        List<String> bad = new ArrayList<>();
        Set<String> have = new LinkedHashSet<>(seals);
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (String material : materials) {
            int found = 0;
            for (String symbol : symbols) {
                if (have.contains(material + "_" + symbol)) {
                    found++;
                }
            }
            counted.put(material, found);
        }
        for (Map.Entry<String, Integer> row : counted.entrySet()) {
            if (row.getValue() != symbols.size()) {
                bad.add(row.getKey() + " is cut with " + row.getValue()
                        + " of the " + symbols.size() + " symbols, so a seal a "
                        + "player asks for is one the game cannot press");
            }
        }
        return bad;
    }

    /** And whether the roster comes to what the two lists multiply to. */
    public static List<String> faultsInRosterCount(List<String> seals,
                                                   List<String> materials,
                                                   List<String> symbols) {
        List<String> bad = new ArrayList<>();
        int want = materials.size() * symbols.size();
        if (seals.size() != want) {
            bad.add("the roster holds " + seals.size() + " seals where "
                    + materials.size() + " materials and " + symbols.size()
                    + " symbols come to " + want);
        }
        return bad;
    }
}
