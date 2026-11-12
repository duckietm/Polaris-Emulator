package polaris.networking.gameserver.handlers;

/**
 * Times the keep-alive round trip of one connection: from a ping to the pong that follows it.
 *
 * <p>A ping that is still unanswered when the next one goes out keeps its own start time, so a client that takes
 * longer than the ping interval to answer is measured in full instead of from the latest ping.
 *
 * <p>Not thread safe: a connection's handler runs on its own event loop.
 */
final class PingRoundTrip {
    /** {@link #answered} when no ping was waiting for a pong. */
    static final long NOT_WAITING = -1L;

    private boolean waiting;
    private long sentAtNanos;

    /** A ping is going out at {@code nowNanos}. */
    void sent(long nowNanos) {
        if (this.waiting) {
            return;
        }

        this.waiting = true;
        this.sentAtNanos = nowNanos;
    }

    /**
     * A pong arrived at {@code nowNanos}: the round trip in nanoseconds, or {@link #NOT_WAITING} when the pong
     * answers no ping (the client also sends pongs on its own timer).
     */
    long answered(long nowNanos) {
        if (!this.waiting) {
            return NOT_WAITING;
        }

        this.waiting = false;
        return Math.max(0L, nowNanos - this.sentAtNanos);
    }
}
