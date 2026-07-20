package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomLoaderTest {

    @Test
    void preservesTheEstablishedLoadDependencyOrder() {
        RecordingOperations operations = new RecordingOperations();
        RoomLoader loader = new RoomLoader(operations, () -> Runnable::run);

        loader.load(17);

        assertEquals(List.of(
                "prepare:17",
                "initialize",
                "layout",
                "promotion-check",
                "promotion",
                "items",
                "rights",
                "word-filter",
                "bots",
                "pets",
                "heightmap",
                "wired",
                "reset-idle",
                "finish:17"), operations.calls);
    }

    @Test
    void staleLoadStopsBeforeAnyDataOperation() {
        RecordingOperations operations = new RecordingOperations();
        operations.prepared = false;
        RoomLoader loader = new RoomLoader(operations, () -> Runnable::run);

        loader.load(18);

        assertEquals(List.of("prepare:18"), operations.calls);
    }

    @Test
    void childFailureIsReportedAndRetainsLegacyBestEffortCompletion() {
        RecordingOperations operations = new RecordingOperations();
        operations.failItems = true;
        RoomLoader loader = new RoomLoader(operations, () -> Runnable::run);

        loader.load(19);

        assertEquals(List.of(
                "prepare:19",
                "initialize",
                "layout",
                "promotion-check",
                "promotion",
                "items",
                "rights",
                "word-filter",
                "bots",
                "pets",
                "failure:Error waiting for items to load",
                "heightmap",
                "wired",
                "reset-idle",
                "finish:19"), operations.calls);
    }

    @Test
    void recordsLoadDurationPublicationAndFailureCount() {
        RecordingOperations operations = new RecordingOperations();
        operations.failItems = true;
        List<RoomLoadMeasurement> measurements = new ArrayList<>();
        long[] times = {100L, 175L};
        AtomicInteger timeIndex = new AtomicInteger();
        RoomLoader loader = new RoomLoader(
                operations,
                () -> Runnable::run,
                () -> times[timeIndex.getAndIncrement()],
                measurements::add);

        loader.load(20);

        assertEquals(List.of(new RoomLoadMeasurement(
                41,
                20,
                75L,
                1,
                true)), measurements);
    }

    private static final class RecordingOperations
            implements RoomLoader.Operations {
        private final List<String> calls = new ArrayList<>();
        private boolean prepared = true;
        private boolean failItems;

        @Override
        public boolean prepare(long generation) {
            this.calls.add("prepare:" + generation);
            return this.prepared;
        }

        @Override
        public void initialize() {
            this.calls.add("initialize");
        }

        @Override
        public void loadLayout() {
            this.calls.add("layout");
        }

        @Override
        public boolean shouldLoadPromotion() {
            this.calls.add("promotion-check");
            return true;
        }

        @Override
        public void loadPromotion() {
            this.calls.add("promotion");
        }

        @Override
        public void loadItems() {
            this.calls.add("items");
            if (this.failItems) {
                throw new IllegalStateException("item fixture failure");
            }
        }

        @Override
        public void loadRights() {
            this.calls.add("rights");
        }

        @Override
        public void loadWordFilter() {
            this.calls.add("word-filter");
        }

        @Override
        public void loadBots() {
            this.calls.add("bots");
        }

        @Override
        public void loadPets() {
            this.calls.add("pets");
        }

        @Override
        public void loadHeightmap() {
            this.calls.add("heightmap");
        }

        @Override
        public void loadWiredData() {
            this.calls.add("wired");
        }

        @Override
        public void resetIdleCycles() {
            this.calls.add("reset-idle");
        }

        @Override
        public boolean finish(long generation) {
            this.calls.add("finish:" + generation);
            return true;
        }

        @Override
        public void reportFailure(String message, Exception exception) {
            this.calls.add("failure:" + message);
        }

        @Override
        public int roomId() {
            return 41;
        }
    }
}
