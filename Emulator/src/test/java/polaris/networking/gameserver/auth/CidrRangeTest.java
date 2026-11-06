package polaris.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CidrRangeTest {

    private static boolean inRange(String cidr, String ip) {
        CidrRange range = CidrRange.parse(cidr);
        assertNotNull(range, cidr);
        return range.contains(CidrRange.parseAddress(ip));
    }

    @Test
    void matchesIpv4Blocks() {
        assertTrue(inRange("173.245.48.0/20", "173.245.48.1"));
        assertTrue(inRange("173.245.48.0/20", "173.245.63.255"));
        assertFalse(inRange("173.245.48.0/20", "173.245.64.0"));
        assertTrue(inRange("10.0.0.0/8", "10.255.255.255"));
        assertFalse(inRange("10.0.0.0/8", "11.0.0.0"));
        assertTrue(inRange("37.59.105.20/32", "37.59.105.20"));
        assertFalse(inRange("37.59.105.20/32", "37.59.105.21"));
    }

    @Test
    void matchesIpv6Blocks() {
        assertTrue(inRange("2400:cb00::/32", "2400:cb00::1"));
        assertTrue(inRange("2400:cb00::/32", "2400:cb00:ffff:ffff:ffff:ffff:ffff:ffff"));
        assertFalse(inRange("2400:cb00::/32", "2400:cb01::"));
        assertTrue(inRange("::/0", "::1"));
    }

    @Test
    void ipv4AndIpv6NeverCrossMatch() {
        assertFalse(inRange("0.0.0.0/0", "::1"));
        assertFalse(inRange("::/0", "1.2.3.4"));
    }

    @Test
    void rejectsInvalidCidrs() {
        assertNull(CidrRange.parse(null));
        assertNull(CidrRange.parse(""));
        assertNull(CidrRange.parse("10.0.0.0"));
        assertNull(CidrRange.parse("10.0.0.0/"));
        assertNull(CidrRange.parse("10.0.0.0/33"));
        assertNull(CidrRange.parse("10.0.0.0/-1"));
        assertNull(CidrRange.parse("2400:cb00::/129"));
        assertNull(CidrRange.parse("example.com/8"));
        assertNull(CidrRange.parse("10.0.0/8"));
    }

    @Test
    void addressParsingIsLiteralOnly() {
        assertNotNull(CidrRange.parseAddress("192.168.0.8"));
        assertNotNull(CidrRange.parseAddress("::1"));
        // Resolvable-looking hostnames must never parse (or trigger DNS).
        assertNull(CidrRange.parseAddress("localhost"));
        assertNull(CidrRange.parseAddress("dead.beef"));
        assertNull(CidrRange.parseAddress("1.2.3"));
        assertNull(CidrRange.parseAddress("1.2.3.4.5"));
        assertNull(CidrRange.parseAddress("256.1.1.1"));
        assertNull(CidrRange.parseAddress("01a.2.3.4"));
        assertNull(CidrRange.parseAddress(""));
        assertNull(CidrRange.parseAddress(null));
    }

    @Test
    void containsHandlesNullAndWrongFamily() {
        CidrRange range = CidrRange.parse("10.0.0.0/8");
        assertNotNull(range);
        assertFalse(range.contains(null));
        assertFalse(range.contains(CidrRange.parseAddress("2400:cb00::1")));
    }
}
