package polaris.hotel.wired.core;

/**
 * One budget for a whole synchronous wired chain: the stack a real event started
 * plus every stack its signals and stack calls run before it returns.
 *
 * <p>Each stack gets a fresh {@link WiredState}, so its step limit alone cannot
 * stop a chain that fans out: signals may nest up to {@code wired.signal.max.depth}
 * levels and each level can reach many antennas. The chain is otherwise only cut
 * by the room's events-per-window rate limit, which keeps dropping (and, without
 * a ban, keeps paying for) the excess. This caps the chain itself; once a cap is
 * hit every remaining stack of that chain is refused. Adapted from the shared
 * safety budget in user-cc/Habbo-Daybreak (wired2).
 *
 * <p>Used by one thread only: the chain runs synchronously on the thread that
 * started it. Delayed effects run later as a new chain with a new budget.
 */
final class WiredChainBudget {

    /** Stacks one chain may run: far beyond any real setup, low enough to stop a runaway in time. */
    static final int DEFAULT_MAX_STACKS = 500;

    /** Furni and users one chain may select in total. */
    static final int DEFAULT_MAX_TARGETS = 25_000;

    private final int maxStacks;
    private final int maxTargets;
    private int stacks;
    private long targets;
    private String exhaustedReason;
    private boolean reported;

    WiredChainBudget(int maxStacks, int maxTargets) {
        this.maxStacks = Math.max(1, maxStacks);
        this.maxTargets = Math.max(1, maxTargets);
    }

    /**
     * Charges one stack run that acts on {@code selectedTargets} furni and users.
     *
     * @return false when the chain is (or now becomes) over budget; the stack must not run
     */
    boolean tryAdmit(int selectedTargets) {
        if (this.exhaustedReason != null) {
            return false;
        }
        if (this.stacks >= this.maxStacks) {
            this.exhaustedReason = "more than " + this.maxStacks + " stacks";
            return false;
        }
        long nextTargets = this.targets + Math.max(0, selectedTargets);
        if (nextTargets > this.maxTargets) {
            this.exhaustedReason = "more than " + this.maxTargets + " selected furni and users";
            return false;
        }
        this.stacks++;
        this.targets = nextTargets;
        return true;
    }

    boolean isExhausted() {
        return this.exhaustedReason != null;
    }

    String exhaustedReason() {
        return this.exhaustedReason;
    }

    /** True the first time it is asked after the budget ran out, so a chain is reported once. */
    boolean shouldReport() {
        if (this.exhaustedReason == null || this.reported) {
            return false;
        }
        this.reported = true;
        return true;
    }

    int stacks() {
        return this.stacks;
    }

    long targets() {
        return this.targets;
    }
}
