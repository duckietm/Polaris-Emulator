package com.eu.habbo.habbohotel.rooms;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@FunctionalInterface
interface RoomLoadMetrics {

    void record(RoomLoadMeasurement measurement);

    static RoomLoadMetrics flightRecorder() {
        return measurement -> {
            RoomLoadEvent event = new RoomLoadEvent();
            if (!event.isEnabled()) {
                return;
            }

            event.roomId = measurement.roomId();
            event.generation = measurement.generation();
            event.durationNanos = measurement.durationNanos();
            event.failureCount = measurement.failureCount();
            event.published = measurement.published();
            event.commit();
        };
    }

    @Name("com.eu.habbo.RoomLoad")
    @Label("Room Load")
    @Category({"Polaris", "Rooms"})
    @StackTrace(false)
    final class RoomLoadEvent extends Event {
        @Label("Room ID")
        int roomId;

        @Label("Lifecycle Generation")
        long generation;

        @Label("Duration (ns)")
        long durationNanos;

        @Label("Failure Count")
        int failureCount;

        @Label("Published")
        boolean published;
    }
}
