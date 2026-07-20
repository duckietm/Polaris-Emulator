package com.eu.habbo.habbohotel.gameclients;

import io.netty.channel.Channel;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Coalesces game-client channel flushes on the current thread while preserving
 * the timing and order of each write.
 */
public final class GameClientFlushBatch implements AutoCloseable {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private final State state;
    private final Thread owner;
    private boolean closed;

    private GameClientFlushBatch(State state) {
        this.state = state;
        this.owner = Thread.currentThread();
    }

    public static GameClientFlushBatch open() {
        State state = CURRENT.get();
        if (state == null) {
            state = new State();
            CURRENT.set(state);
        }
        state.depth++;
        return new GameClientFlushBatch(state);
    }

    static boolean deferFlush(Channel channel) {
        State state = CURRENT.get();
        if (state == null || state.depth == 0) {
            return false;
        }

        state.channels.add(channel);
        return true;
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        if (Thread.currentThread() != this.owner || CURRENT.get() != this.state) {
            throw new IllegalStateException("Flush batches must close on their opening thread");
        }

        this.closed = true;
        this.state.depth--;
        if (this.state.depth > 0) {
            return;
        }

        RuntimeException runtimeFailure = null;
        Error errorFailure = null;
        for (Channel channel : this.state.channels) {
            try {
                channel.flush();
            } catch (RuntimeException exception) {
                if (runtimeFailure == null) {
                    runtimeFailure = exception;
                } else {
                    runtimeFailure.addSuppressed(exception);
                }
            } catch (Error error) {
                if (errorFailure == null) {
                    errorFailure = error;
                } else {
                    errorFailure.addSuppressed(error);
                }
            }
        }
        this.state.channels.clear();

        if (errorFailure != null) {
            if (runtimeFailure != null) {
                errorFailure.addSuppressed(runtimeFailure);
            }
            throw errorFailure;
        }
        if (runtimeFailure != null) {
            throw runtimeFailure;
        }
    }

    private static final class State {
        private final Set<Channel> channels = Collections.newSetFromMap(new IdentityHashMap<>());
        private int depth;
    }
}
