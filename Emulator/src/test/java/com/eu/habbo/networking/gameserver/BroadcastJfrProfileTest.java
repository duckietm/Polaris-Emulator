package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.ServerMessageFrame;
import com.eu.habbo.networking.gameserver.encoders.GameServerMessageEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "polaris.profile.broadcast", matches = "true")
class BroadcastJfrProfileTest {

    private static final int RECIPIENTS = 50;
    private static final int WARMUP_BROADCASTS = 500;
    private static final int PROFILE_BROADCASTS = 5_000;
    private static volatile long blackhole;

    @Test
    void capturesRepresentativeRoomBroadcastAllocationProfile() throws Exception {
        Path profileDirectory = Path.of("target", "profiles");
        Files.createDirectories(profileDirectory);
        boolean sharedFrame = Boolean.getBoolean("polaris.profile.broadcast.shared");
        String profileName = sharedFrame ? "broadcast-shared-frame" : "broadcast-baseline";
        Path recordingPath = profileDirectory.resolve(profileName + ".jfr");
        Path summaryPath = profileDirectory.resolve(profileName + ".txt");

        EmbeddedChannel channel = new EmbeddedChannel(new GameServerMessageEncoder());
        ServerMessage message = representativeMessage();
        if (sharedFrame) {
            ServerMessageFrame.prepareBroadcast(message);
        }
        try {
            runBroadcasts(channel, message, WARMUP_BROADCASTS, sharedFrame);

            long startedAt = System.nanoTime();
            try (Recording recording = new Recording()) {
                recording.enable("jdk.ObjectAllocationSample").withStackTrace();
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10));
                recording.enable("jdk.GarbageCollection");
                recording.start();
                runBroadcasts(channel, message, PROFILE_BROADCASTS, sharedFrame);
                recording.stop();
                recording.dump(recordingPath);
            }
            long elapsedNanos = System.nanoTime() - startedAt;

            ProfileSummary summary = summarize(recordingPath, elapsedNanos);
            Files.writeString(summaryPath, summary.format());

            System.out.println("BROADCAST_JFR " + summary.format().replace(System.lineSeparator(), " "));
            assertTrue(summary.totalSampledBytes() > 0L);
            assertTrue(summary.serverMessageSampledBytes() > 0L);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static ServerMessage representativeMessage() {
        ServerMessage message = new ServerMessage(1_234);
        message.appendString("room-cycle-broadcast");
        message.appendRawBytes(new byte[1_024]);
        return message;
    }

    private static void runBroadcasts(
            EmbeddedChannel channel, ServerMessage message, int broadcasts, boolean sharedFrame) {
        for (int broadcast = 0; broadcast < broadcasts; broadcast++) {
            for (int recipient = 0; recipient < RECIPIENTS; recipient++) {
                assertTrue(
                        channel.writeOutbound(sharedFrame ? ServerMessageFrame.retainedDuplicate(message) : message));
                ByteBuf encoded = channel.readOutbound();
                blackhole += encoded.getByte(encoded.readerIndex());
                encoded.release();
            }
        }
    }

    private static ProfileSummary summarize(Path recordingPath, long elapsedNanos) throws Exception {
        long totalSampledBytes = 0L;
        long serverMessageSampledBytes = 0L;
        long allocationSamples = 0L;
        long serverMessageSamples = 0L;
        long executionSamples = 0L;
        long serverMessageExecutionSamples = 0L;
        long garbageCollections = 0L;

        List<RecordedEvent> events = RecordingFile.readAllEvents(recordingPath);
        for (RecordedEvent event : events) {
            switch (event.getEventType().getName()) {
                case "jdk.ObjectAllocationSample" -> {
                    long weight = event.getLong("weight");
                    totalSampledBytes += weight;
                    allocationSamples++;
                    if (hasServerMessageFrame(event)) {
                        serverMessageSampledBytes += weight;
                        serverMessageSamples++;
                    }
                }
                case "jdk.ExecutionSample" -> {
                    executionSamples++;
                    if (hasServerMessageFrame(event)) {
                        serverMessageExecutionSamples++;
                    }
                }
                case "jdk.GarbageCollection" -> garbageCollections++;
                default -> {}
            }
        }

        return new ProfileSummary(
                PROFILE_BROADCASTS,
                RECIPIENTS,
                elapsedNanos / 1_000_000D,
                totalSampledBytes,
                serverMessageSampledBytes,
                allocationSamples,
                serverMessageSamples,
                executionSamples,
                serverMessageExecutionSamples,
                garbageCollections);
    }

    private static boolean hasServerMessageFrame(RecordedEvent event) {
        if (event.getStackTrace() == null) {
            return false;
        }
        for (RecordedFrame frame : event.getStackTrace().getFrames()) {
            String type = frame.getMethod().getType().getName();
            if (type.equals("com.eu.habbo.messages.ServerMessage")
                    || type.equals("com.eu.habbo.messages.ServerMessageFrame")
                    || type.equals("com.eu.habbo.networking.gameserver." + "encoders.GameServerMessageEncoder")) {
                return true;
            }
        }
        return false;
    }

    private record ProfileSummary(
            int broadcasts,
            int recipients,
            double elapsedMs,
            long totalSampledBytes,
            long serverMessageSampledBytes,
            long allocationSamples,
            long serverMessageSamples,
            long executionSamples,
            long serverMessageExecutionSamples,
            long garbageCollections) {

        String format() {
            return String.format(
                    Locale.ROOT,
                    "broadcasts=%d%n"
                            + "recipients=%d%n"
                            + "encodedFrames=%d%n"
                            + "elapsedMs=%.3f%n"
                            + "totalSampledBytes=%d%n"
                            + "serverMessageSampledBytes=%d%n"
                            + "allocationSamples=%d%n"
                            + "serverMessageSamples=%d%n"
                            + "executionSamples=%d%n"
                            + "serverMessageExecutionSamples=%d%n"
                            + "garbageCollections=%d%n",
                    broadcasts,
                    recipients,
                    broadcasts * recipients,
                    elapsedMs,
                    totalSampledBytes,
                    serverMessageSampledBytes,
                    allocationSamples,
                    serverMessageSamples,
                    executionSamples,
                    serverMessageExecutionSamples,
                    garbageCollections);
        }
    }
}
