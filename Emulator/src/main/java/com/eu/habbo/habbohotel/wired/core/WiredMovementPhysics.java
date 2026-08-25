package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WiredMovementPhysics {
    public static final WiredMovementPhysics NONE = new WiredMovementPhysics(false, Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    private final boolean keepAltitude;
    private final Set<Integer> passThroughFurniIds;
    private final Set<Integer> passThroughUserIds;
    private final Set<Integer> blockingFurniIds;

    public WiredMovementPhysics(boolean keepAltitude, Set<Integer> passThroughFurniIds, Set<Integer> passThroughUserIds, Set<Integer> blockingFurniIds) {
        this.keepAltitude = keepAltitude;
        this.passThroughFurniIds = Collections.unmodifiableSet(new HashSet<>(passThroughFurniIds));
        this.passThroughUserIds = Collections.unmodifiableSet(new HashSet<>(passThroughUserIds));
        this.blockingFurniIds = Collections.unmodifiableSet(new HashSet<>(blockingFurniIds));
    }

    public boolean isKeepAltitude() {
        return this.keepAltitude;
    }

    public boolean isActive() {
        return this.keepAltitude
                || !this.passThroughFurniIds.isEmpty()
                || !this.passThroughUserIds.isEmpty()
                || !this.blockingFurniIds.isEmpty();
    }

    public boolean hasBlockingFurni() {
        return !this.blockingFurniIds.isEmpty();
    }

    public boolean shouldIgnoreFurni(HabboItem item) {
        return item != null
                && this.passThroughFurniIds.contains(item.getId())
                && !this.blockingFurniIds.contains(item.getId());
    }

    public boolean isBlockingFurni(HabboItem item) {
        return item != null && this.blockingFurniIds.contains(item.getId());
    }

    public boolean shouldIgnoreUser(RoomUnit roomUnit) {
        return roomUnit != null
                && roomUnit.getRoomUnitType() == RoomUnitType.USER
                && this.passThroughUserIds.contains(roomUnit.getId());
    }
}
