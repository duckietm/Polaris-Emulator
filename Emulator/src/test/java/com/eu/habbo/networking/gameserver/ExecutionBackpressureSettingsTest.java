package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.core.ConfigurationManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionBackpressureSettingsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsToObservationWithTheApprovedTwoSecondDeadline() throws Exception {
        ConfigurationManager configuration = configuration("");

        ExecutionBackpressureSettings settings = ExecutionBackpressureSettings.from(configuration);

        assertEquals(ExecutionCapacityController.Mode.OBSERVE, settings.mode());
        assertEquals(256, settings.packetCapacity());
        assertEquals(192, settings.packetLowWatermark());
        assertEquals(32, settings.perConnectionCapacity());
        assertEquals(16, settings.perConnectionLowWatermark());
        assertEquals(16, settings.perConnectionPendingCapacity());
        assertEquals(2_000L, settings.pauseTimeoutMillis());
        assertEquals(128, settings.httpCapacity());
    }

    @Test
    void readsEnforcementGeometryAndClampsInvalidLowWatermarks() throws Exception {
        ConfigurationManager configuration = configuration("execution.backpressure.mode=enforce\n"
                + "io.packet.handler.queue.capacity=40\n"
                + "io.packet.handler.queue.low_watermark=80\n"
                + "io.packet.handler.per_connection.capacity=8\n"
                + "io.packet.handler.per_connection.low_watermark=9\n"
                + "io.packet.handler.per_connection.pending=6\n"
                + "execution.backpressure.pause.timeout_ms=1500\n"
                + "http.blocking.queue.capacity=20\n");

        ExecutionBackpressureSettings settings = ExecutionBackpressureSettings.from(configuration);

        assertEquals(ExecutionCapacityController.Mode.ENFORCE, settings.mode());
        assertEquals(40, settings.packetCapacity());
        assertEquals(30, settings.packetLowWatermark());
        assertEquals(8, settings.perConnectionCapacity());
        assertEquals(4, settings.perConnectionLowWatermark());
        assertEquals(6, settings.perConnectionPendingCapacity());
        assertEquals(1_500L, settings.pauseTimeoutMillis());
        assertEquals(20, settings.httpCapacity());
    }

    private ConfigurationManager configuration(String contents) throws Exception {
        Path file = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(file, contents);
        return new ConfigurationManager(file.toString());
    }
}
