package com.eu.habbo.habbohotel.rooms;

record RoomLoadMeasurement(
        int roomId,
        long generation,
        long durationNanos,
        int failureCount,
        boolean published) {
}
