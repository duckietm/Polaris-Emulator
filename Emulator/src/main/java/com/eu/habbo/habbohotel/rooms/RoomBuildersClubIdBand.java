package com.eu.habbo.habbohotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * One room's Builders Club wire ids ({@link BuildersClubItemIds}). Ids are unique within the room, handed out
 * round-robin through the band and not reused before the cursor wraps, so a packet still in flight for a furni that
 * just left can never reach another one. In memory only: AIR keeps no furni id beyond the room visit.
 */
final class RoomBuildersClubIdBand {
    /** Database id by wire id of the furni in the room now. */
    private final Int2IntOpenHashMap itemByWire = new Int2IntOpenHashMap(0);
    /** Wire id by database id of the furni in the room now. */
    private final Int2IntOpenHashMap wireByItem = new Int2IntOpenHashMap(0);
    /** Database id by wire id of everything published hotel-wide from this room, gone or not. */
    private final Int2IntOpenHashMap publishedByWire = new Int2IntOpenHashMap(0);
    /** Offset of the next id to try. */
    private int cursor;

    /** The furni's wire id, handing it one when it has none. */
    synchronized int assign(int itemId) {
        int existing = this.wireByItem.get(itemId);
        if (existing != 0) {
            return existing;
        }
        for (int tried = 0; tried < BuildersClubItemIds.SIZE; tried++) {
            int wireId = BuildersClubItemIds.FIRST + this.cursor;
            this.cursor = (this.cursor + 1) % BuildersClubItemIds.SIZE;
            if (this.itemByWire.containsKey(wireId)) {
                continue;
            }
            int previous = this.publishedByWire.get(wireId);
            if (previous != 0) {
                BuildersClubItemIds.unpublish(previous, wireId);
            }
            this.itemByWire.put(wireId, itemId);
            this.wireByItem.put(itemId, wireId);
            this.publishedByWire.put(wireId, itemId);
            BuildersClubItemIds.publish(itemId, wireId);
            return wireId;
        }
        throw new IllegalStateException("The Builders Club id band of this room is full");
    }

    /** Frees the furni's wire id; composers keep translating it until the id is handed out again. */
    synchronized void release(int itemId) {
        int wireId = this.wireByItem.remove(itemId);
        if (wireId != 0) {
            this.itemByWire.remove(wireId);
        }
    }

    /** The database id of the furni in the room with this wire id, 0 when none. */
    synchronized int itemOf(int wireId) {
        return this.itemByWire.get(wireId);
    }

    /** The furni's wire id, 0 when it has none here. */
    synchronized int wireOf(int itemId) {
        return this.wireByItem.get(itemId);
    }

    synchronized int size() {
        return this.itemByWire.size();
    }

    /** Forgets every id (the room unloads or reloads its items). */
    synchronized void clear() {
        for (Int2IntMap.Entry entry : this.publishedByWire.int2IntEntrySet()) {
            BuildersClubItemIds.unpublish(entry.getIntValue(), entry.getIntKey());
        }
        this.publishedByWire.clear();
        this.itemByWire.clear();
        this.wireByItem.clear();
        this.cursor = 0;
    }
}
