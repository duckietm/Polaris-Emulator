package polaris.hotel.rooms;

record RoomLoadMeasurement(int roomId, long generation, long durationNanos, int failureCount, boolean published) {}
