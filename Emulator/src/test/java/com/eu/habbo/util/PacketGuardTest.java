package com.eu.habbo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketGuardTest {
    @Test
    void acceptsCountsThatFitTheConfiguredAndPacketBounds() {
        assertTrue(PacketGuard.isValidIntList(0, 0, 50));
        assertTrue(PacketGuard.isValidIntList(50, 200, 50));
    }

    @Test
    void rejectsNegativeOrOversizedCountsBeforeAllocation() {
        assertFalse(PacketGuard.isValidIntList(-1, 0, 50));
        assertFalse(PacketGuard.isValidIntList(Integer.MAX_VALUE, 0, 1_000));
        assertFalse(PacketGuard.isValidIntList(1_001, 4_004, 1_000));
    }

    @Test
    void rejectsCountsThatExceedTheRemainingPacketBytes() {
        assertFalse(PacketGuard.isValidIntList(2, 4, 50));
    }
}
