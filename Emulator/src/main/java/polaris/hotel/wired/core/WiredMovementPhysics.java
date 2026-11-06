package polaris.hotel.wired.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.UserItem;

public final class WiredMovementPhysics {
    public static final WiredMovementPhysics NONE =
            new WiredMovementPhysics(false, Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    private final boolean keepAltitude;
    private final Set<Integer> passThroughFurniIds;
    private final Set<Integer> passThroughUserIds;
    private final Set<Integer> blockingFurniIds;
    /** The moving furni itself, which never blocks or is passed through; 0 for none. */
    private final int excludedFurniId;

    public WiredMovementPhysics(
            boolean keepAltitude,
            Set<Integer> passThroughFurniIds,
            Set<Integer> passThroughUserIds,
            Set<Integer> blockingFurniIds) {
        this(
                keepAltitude,
                Collections.unmodifiableSet(new HashSet<>(passThroughFurniIds)),
                Collections.unmodifiableSet(new HashSet<>(passThroughUserIds)),
                Collections.unmodifiableSet(new HashSet<>(blockingFurniIds)),
                0);
    }

    private WiredMovementPhysics(
            boolean keepAltitude,
            Set<Integer> passThroughFurniIds,
            Set<Integer> passThroughUserIds,
            Set<Integer> blockingFurniIds,
            int excludedFurniId) {
        this.keepAltitude = keepAltitude;
        this.passThroughFurniIds = passThroughFurniIds;
        this.passThroughUserIds = passThroughUserIds;
        this.blockingFurniIds = blockingFurniIds;
        this.excludedFurniId = excludedFurniId;
    }

    /** The same physics for moving this furni, sharing the sets instead of copying the room. */
    WiredMovementPhysics withoutFurni(int itemId) {
        return new WiredMovementPhysics(
                this.keepAltitude, this.passThroughFurniIds, this.passThroughUserIds, this.blockingFurniIds, itemId);
    }

    private boolean hasOtherThanExcluded(Set<Integer> ids) {
        return ids.size() > 1 || (ids.size() == 1 && !ids.contains(this.excludedFurniId));
    }

    public boolean isKeepAltitude() {
        return this.keepAltitude;
    }

    public boolean isActive() {
        return this.keepAltitude
                || hasOtherThanExcluded(this.passThroughFurniIds)
                || !this.passThroughUserIds.isEmpty()
                || hasOtherThanExcluded(this.blockingFurniIds);
    }

    public boolean hasBlockingFurni() {
        return hasOtherThanExcluded(this.blockingFurniIds);
    }

    public boolean shouldIgnoreFurni(UserItem item) {
        return item != null
                && item.getId() != this.excludedFurniId
                && this.passThroughFurniIds.contains(item.getId())
                && !this.blockingFurniIds.contains(item.getId());
    }

    public boolean isBlockingFurni(UserItem item) {
        return item != null && item.getId() != this.excludedFurniId && this.blockingFurniIds.contains(item.getId());
    }

    public boolean shouldIgnoreUser(RoomUnit roomUnit) {
        return roomUnit != null
                && roomUnit.getRoomUnitType() == RoomUnitType.USER
                && this.passThroughUserIds.contains(roomUnit.getId());
    }
}
