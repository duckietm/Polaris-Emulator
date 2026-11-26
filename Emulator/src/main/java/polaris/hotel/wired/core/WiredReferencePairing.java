package polaris.hotel.wired.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which reference furni's value each destination takes when a variable is read from several furni and "same
 * holder" is off. The pairing is random and drawn anew at every run: with at least as many reference holders as
 * destinations every destination gets the value of a different holder; with fewer the values are shared evenly,
 * and which destinations share one varies too.
 *
 * <p>Values are handed out one destination at a time: every reference value once, in a shuffled order, before any
 * is used again.
 */
public final class WiredReferencePairing {
    private final List<Long> values;
    private final Random random;
    private int next;

    public WiredReferencePairing(Collection<Long> values) {
        this(values, null);
    }

    /** With a given source of randomness, for tests. */
    public WiredReferencePairing(Collection<Long> values, Random random) {
        this.values = new ArrayList<>(values);
        this.random = random;
        this.shuffle();
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    /** The value of the next destination, or null when no reference holder has one. */
    public Long next() {
        if (this.values.isEmpty()) return null;

        if (this.next >= this.values.size()) {
            this.shuffle();
            this.next = 0;
        }
        return this.values.get(this.next++);
    }

    private void shuffle() {
        Collections.shuffle(this.values, this.random == null ? ThreadLocalRandom.current() : this.random);
    }
}
