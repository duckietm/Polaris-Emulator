package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "polaris.profile.wired-random", matches = "true")
class WiredRandomSelectionJfrProfileTest {

    private static final int WARMUP_SELECTIONS = 100_000;
    private static final int PROFILE_SELECTIONS = 2_000_000;
    private static volatile long blackhole;

    @Test
    void capturesRandomSelectionAllocations() throws Exception {
        Path profileDirectory = Path.of("target", "profiles");
        Files.createDirectories(profileDirectory);
        String variant = System.getProperty("polaris.profile.wired-random.variant", "baseline");
        Path recordingPath = profileDirectory.resolve("wired-random-" + variant + ".jfr");
        Path summaryPath = profileDirectory.resolve("wired-random-" + variant + ".txt");

        runSelections(WARMUP_SELECTIONS);
        long startedAt = System.nanoTime();
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ObjectAllocationInNewTLAB")
                    .withThreshold(Duration.ZERO)
                    .withStackTrace();
            recording.enable("jdk.ObjectAllocationOutsideTLAB")
                    .withThreshold(Duration.ZERO)
                    .withStackTrace();
            recording.start();
            runSelections(PROFILE_SELECTIONS);
            recording.stop();
            recording.dump(recordingPath);
        }

        ProfileSummary summary = summarize(recordingPath, System.nanoTime() - startedAt);
        Files.writeString(summaryPath, summary.format());
        System.out.println("WIRED_RANDOM_JFR "
                + summary.format().replace(System.lineSeparator(), " | "));

        assertNotEquals(0, blackhole);
    }

    private static void runSelections(int count) {
        long result = 0;
        for (int selection = 0; selection < count; selection++) {
            result += WiredEngine.selectRandomIndex(32);
        }
        blackhole = result;
    }

    private static ProfileSummary summarize(Path recordingPath, long elapsedNanos)
            throws Exception {
        long randomAllocations = 0;
        long randomAllocationBytes = 0;
        long totalAllocations = 0;
        long totalAllocationBytes = 0;

        try (RecordingFile recording = new RecordingFile(recordingPath)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (!event.getEventType().getName().startsWith("jdk.ObjectAllocation")) {
                    continue;
                }

                totalAllocations++;
                long allocationSize = event.getLong("allocationSize");
                totalAllocationBytes += allocationSize;
                RecordedClass allocatedClass = event.getClass("objectClass");
                if ("java.util.Random".equals(allocatedClass.getName())) {
                    randomAllocations++;
                    randomAllocationBytes += allocationSize;
                }
            }
        }

        return new ProfileSummary(
                PROFILE_SELECTIONS,
                elapsedNanos,
                randomAllocations,
                randomAllocationBytes,
                totalAllocations,
                totalAllocationBytes);
    }

    private record ProfileSummary(
            int selections,
            long elapsedNanos,
            long randomAllocations,
            long randomAllocationBytes,
            long totalAllocations,
            long totalAllocationBytes) {

        private String format() {
            return """
                    selections=%d
                    elapsedNanos=%d
                    randomAllocations=%d
                    randomAllocationBytes=%d
                    totalAllocations=%d
                    totalAllocationBytes=%d
                    """
                    .formatted(
                            selections,
                            elapsedNanos,
                            randomAllocations,
                            randomAllocationBytes,
                            totalAllocations,
                            totalAllocationBytes);
        }
    }
}
