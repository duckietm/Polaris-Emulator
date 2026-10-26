package com.eu.habbo.habbohotel.rooms;

import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Set-compatible view of the legacy plugin-visible room rights list.
 */
final class RoomRightsSetView extends AbstractIntSet {

    private final IntList rights;

    RoomRightsSetView(IntList rights) {
        this.rights = rights;
    }

    /**
     * Every operation holds the list's lock, the same one a synchronized fastutil list uses: the
     * contains-then-add used to let two threads both add a user, and the caller then inserted a
     * second room_rights row.
     */
    @Override
    public boolean add(int userId) {
        synchronized (this.rights) {
            if (this.rights.contains(userId)) {
                return false;
            }
            return this.rights.add(userId);
        }
    }

    @Override
    public boolean contains(int userId) {
        synchronized (this.rights) {
            return this.rights.contains(userId);
        }
    }

    @Override
    public boolean remove(int userId) {
        synchronized (this.rights) {
            boolean removed = false;
            while (this.rights.rem(userId)) {
                removed = true;
            }
            return removed;
        }
    }

    @Override
    public void clear() {
        synchronized (this.rights) {
            this.rights.clear();
        }
    }

    @Override
    public int size() {
        return this.snapshot().size();
    }

    private IntOpenHashSet snapshot() {
        synchronized (this.rights) {
            return new IntOpenHashSet(this.rights);
        }
    }

    @Override
    public IntIterator iterator() {
        IntIterator snapshot = this.snapshot().iterator();
        return new IntIterator() {
            private int current;
            private boolean removable;

            @Override
            public boolean hasNext() {
                return snapshot.hasNext();
            }

            @Override
            public int nextInt() {
                this.current = snapshot.nextInt();
                this.removable = true;
                return this.current;
            }

            @Override
            public void remove() {
                if (!this.removable) {
                    throw new IllegalStateException();
                }
                RoomRightsSetView.this.remove(this.current);
                this.removable = false;
            }
        };
    }
}
