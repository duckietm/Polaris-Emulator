package com.eu.habbo.habbohotel.wired.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Habbo's variable-change folding for a stack without Execute In Order: every Change Variable the
 * stack makes to one variable on one holder in one execution is collected and applied once, when the
 * execution ends, in a fixed order (set, power, multiply, divide, modulo, add, subtract, then every
 * other operation in box order), so the holder gets one change and Variable Changed fires once.
 * Set 3, add 5, multiply 2 gives 11.
 *
 * <p>A fold is open while {@link #begin()}'s scope is; folds nest, so a stack fired from inside
 * another one (a Variable Changed trigger, a signal) collects its own changes. With Execute In Order
 * no fold is opened and each box applies its own change.
 */
public final class WiredVariableChangeFold {
    /** One operation of a Change Variable box; {@code operand} is null for the operand-less ones. */
    @FunctionalInterface
    public interface Operation {
        int apply(int operation, int current, Integer operand);
    }

    /** The fixed order; anything not listed keeps the box order after these. */
    private static final int[] PRIORITY_ORDER = {0, 5, 3, 4, 6, 1, 2};

    private static final ThreadLocal<Deque<Fold>> FOLDS = ThreadLocal.withInitial(ArrayDeque::new);

    private WiredVariableChangeFold() {}

    /** Opens a fold; closing the scope applies everything it collected. */
    public static Scope begin() {
        Fold fold = new Fold();
        FOLDS.get().push(fold);
        return new Scope(fold);
    }

    public static boolean isActive() {
        return !FOLDS.get().isEmpty();
    }

    /**
     * Queues one change of the variable {@code key} names. Returns false when no fold is open, so the
     * box applies the change itself. {@code read} and {@code write} reach the holder's stored value.
     */
    public static boolean defer(
            Object key, int operation, Integer operand, Operation apply, IntSupplier read, IntConsumer write) {
        Deque<Fold> folds = FOLDS.get();
        if (folds.isEmpty()) {
            return false;
        }

        folds.peek().add(key, new Change(operation, operand), apply, read, write);
        return true;
    }

    /** The place of an operation in the fold; operations outside the fixed order share the last place. */
    public static int priority(int operation) {
        for (int i = 0; i < PRIORITY_ORDER.length; i++) {
            if (PRIORITY_ORDER[i] == operation) {
                return i;
            }
        }
        return PRIORITY_ORDER.length;
    }

    /** Applies {@code changes} (in box order) to {@code start} in the fold's fixed order. */
    public static int fold(int start, List<Change> changes, Operation apply) {
        List<Change> ordered = new ArrayList<>(changes);
        // A stable sort keeps the box order inside one place.
        ordered.sort(Comparator.comparingInt(change -> priority(change.operation())));

        int value = start;
        for (Change change : ordered) {
            value = apply.apply(change.operation(), value, change.operand());
        }
        return value;
    }

    /** One collected change. */
    public record Change(int operation, Integer operand) {}

    private static final class Pending {
        private final List<Change> changes = new ArrayList<>();
        private final Operation apply;
        private final IntSupplier read;
        private final IntConsumer write;

        private Pending(Operation apply, IntSupplier read, IntConsumer write) {
            this.apply = apply;
            this.read = read;
            this.write = write;
        }
    }

    private static final class Fold {
        private final Map<Object, Pending> pending = new LinkedHashMap<>();

        private void add(Object key, Change change, Operation apply, IntSupplier read, IntConsumer write) {
            this.pending
                    .computeIfAbsent(Objects.requireNonNull(key), ignored -> new Pending(apply, read, write))
                    .changes
                    .add(change);
        }

        private void apply() {
            for (Pending entry : this.pending.values()) {
                int current = entry.read.getAsInt();
                int next = fold(current, entry.changes, entry.apply);
                // Written even when unchanged, as a box applying its own change would; the managers
                // only announce a change that moved the value.
                entry.write.accept(next);
            }
            this.pending.clear();
        }
    }

    /** The open fold; closing it applies the collected changes once each. */
    public static final class Scope implements AutoCloseable {
        private final Fold fold;
        private boolean closed;

        private Scope(Fold fold) {
            this.fold = fold;
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;

            Deque<Fold> folds = FOLDS.get();
            folds.remove(this.fold);
            if (folds.isEmpty()) {
                FOLDS.remove();
            }
            // Applied after the fold is closed, so a stack the changes fire collects its own.
            this.fold.apply();
        }
    }
}
