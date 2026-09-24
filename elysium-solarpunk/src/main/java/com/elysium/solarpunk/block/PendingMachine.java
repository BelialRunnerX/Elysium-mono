package com.elysium.solarpunk.block;

/**
 * A block that is shaped like a machine and does not work yet.
 *
 * <h2>Why a marker with a sentence on it</h2>
 *
 * "Decorative for now" is a decision with a short half-life. Written as a
 * {@code // TODO} it survives until somebody tidies the file; written only in
 * a design document it stops being connected to the code the first time a
 * block is renamed. Either way the honest version of the set — eight machines
 * that are still props — quietly turns into eight blocks nobody remembers were
 * supposed to do anything.
 *
 * So the note is a value rather than a comment. Every unfinished machine
 * returns one sentence saying what it owes, and that same sentence is:
 *
 * <ul>
 *   <li>a row in {@code MACHINES.md} at the module root,</li>
 *   <li>the javadoc on its class, which lives in {@code block/pending/}, and</li>
 *   <li>a line on the block item's tooltip, so the <em>player</em> is told too
 *       rather than finding out by placing one and waiting.</li>
 * </ul>
 *
 * {@code validate.py} reads all of those back and fails if any of them
 * disagree, which is what stops the three drifting apart. The generator writes
 * them from one table, which is what stops anybody having to keep them in step
 * by hand.
 *
 * <h2>Finishing one</h2>
 *
 * Implement the behaviour, move the class out of {@code block/pending/}, drop
 * the machine's row from the {@code MACHINES} table in
 * {@code tools/gen_solarpunk.py}, and re-run the generator. That removes the
 * marker, the manifest row and the tooltip line together — there is no fourth
 * place to remember.
 *
 * <h2>What it deliberately is not</h2>
 *
 * Not an interface for machine behaviour. When these grow a job they will want
 * block entities, energy, inventories and probably the library, and none of
 * that should be designed here on the strength of eight names. This says one
 * thing only: not finished, and here is what finished means.
 */
public interface PendingMachine {

    /**
     * What this block should do once somebody builds it.
     *
     * <p>One sentence, present tense, describing the behaviour rather than the
     * implementation: "Fires clay and glass on less fuel than a furnace needs"
     * rather than "needs a BlockEntity". It is shown to the player, so it has
     * to read as a description of the machine and not as a work item.
     */
    String owes();
}
