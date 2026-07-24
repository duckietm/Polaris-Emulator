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

    @Override
    public boolean add(int userId) {
        if (this.rights.contains(userId)) {
            return false;
        }
        return this.rights.add(userId);
    }

    @Override
    public boolean contains(int userId) {
        return this.rights.contains(userId);
    }

    @Override
    public boolean remove(int userId) {
        boolean removed = false;
        while (this.rights.rem(userId)) {
            removed = true;
        }
        return removed;
    }

    @Override
    public void clear() {
        this.rights.clear();
    }

    @Override
    public int size() {
        return new IntOpenHashSet(this.rights).size();
    }

    @Override
    public IntIterator iterator() {
        IntIterator snapshot = new IntOpenHashSet(this.rights).iterator();
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
