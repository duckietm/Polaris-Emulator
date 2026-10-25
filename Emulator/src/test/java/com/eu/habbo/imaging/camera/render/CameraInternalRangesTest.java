package com.eu.habbo.imaging.camera.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

/**
 * The camera's sprite fetch refused only what InetAddress calls local: IPv6 unique-local, CGNAT (cloud metadata at
 * 100.100.100.200), 0.0.0.0/8, 198.18.0.0/15 and NAT64 went through, so a scene could probe internal services.
 */
class CameraInternalRangesTest {

    private static boolean internal(String literal) throws Exception {
        return CameraUrlFetcher.isInternal(InetAddress.getByName(literal));
    }

    @Test
    void theRangesInetAddressMissesAreRefused() throws Exception {
        assertTrue(internal("fd00::10"), "IPv6 unique-local");
        assertTrue(internal("100.100.100.200"), "CGNAT, cloud metadata");
        assertTrue(internal("0.1.2.3"), "this network");
        assertTrue(internal("198.18.0.1"), "benchmarking");
        assertTrue(internal("64:ff9b::a00:1"), "NAT64 of 10.0.0.1");
        assertTrue(internal("2002:a00:1::1"), "6to4 of 10.0.0.1");
        assertTrue(internal("2001::1"), "Teredo");
        assertTrue(internal("192.0.2.5"), "documentation");
    }

    @Test
    void theClassicLocalRangesStillAreAndPublicAddressesAreNot() throws Exception {
        assertTrue(internal("127.0.0.1"));
        assertTrue(internal("10.1.2.3"));
        assertTrue(internal("::1"));
        assertTrue(internal("fe80::1"));
        assertFalse(internal("93.184.216.34"));
        assertFalse(internal("2606:4700:4700::1111"));
        assertFalse(internal("100.128.0.1"), "just outside CGNAT");
    }
}
