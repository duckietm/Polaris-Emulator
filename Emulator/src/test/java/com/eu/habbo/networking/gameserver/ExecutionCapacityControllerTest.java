package com.eu.habbo.networking.gameserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionCapacityControllerTest {

    @Test
    void observeModeMeasuresPressureWithoutRejectingWork() {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(2, 1, ExecutionCapacityController.Mode.OBSERVE);

        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire());
        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire());
        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire());

        ExecutionCapacityController.Snapshot snapshot = controller.snapshot();
        assertEquals(3, snapshot.inFlight());
        assertEquals(3, snapshot.highWatermark());
        assertEquals(1, snapshot.wouldThrottle());
        assertEquals(0, snapshot.rejections());
    }

    @Test
    void enforceModeBoundsOccupancyAndWakesWaitersInFifoOrderAtLowWatermark() {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(3, 1, ExecutionCapacityController.Mode.ENFORCE);
        List<String> awakened = new ArrayList<>();

        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire());
        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire());
        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire());
        assertEquals(ExecutionCapacityController.Admission.SATURATED, controller.tryAcquire());
        controller.registerWaiter("first", () -> awakened.add("first"));
        controller.registerWaiter("second", () -> awakened.add("second"));

        controller.release();
        assertEquals(List.of(), awakened);
        controller.release();
        assertEquals(List.of("first"), awakened);
        controller.release();
        assertEquals(List.of("first", "second"), awakened);

        ExecutionCapacityController.Snapshot snapshot = controller.snapshot();
        assertEquals(0, snapshot.inFlight());
        assertEquals(3, snapshot.highWatermark());
        assertEquals(1, snapshot.rejections());
    }

    @Test
    void waiterRegistrationIsIdempotentAndReleaseCannotUnderflow() {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        List<String> awakened = new ArrayList<>();

        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire());
        controller.registerWaiter("same-channel", () -> awakened.add("first"));
        controller.registerWaiter("same-channel", () -> awakened.add("duplicate"));

        controller.release();

        assertEquals(List.of("first"), awakened);
        assertThrows(IllegalStateException.class, controller::release);
    }

    @Test
    void releasedCapacityIsReservedForTheOldestWaitingConnection() {
        ExecutionCapacityController controller =
                new ExecutionCapacityController(1, 0, ExecutionCapacityController.Mode.ENFORCE);
        List<String> awakened = new ArrayList<>();

        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire("active"));
        controller.registerWaiter("oldest", () -> awakened.add("oldest"));
        controller.registerWaiter("newest", () -> awakened.add("newest"));

        controller.release();

        assertEquals(List.of("oldest"), awakened);
        assertEquals(ExecutionCapacityController.Admission.SATURATED, controller.tryAcquire("active"));
        assertEquals(ExecutionCapacityController.Admission.ADMITTED, controller.tryAcquire("oldest"));
    }
}
