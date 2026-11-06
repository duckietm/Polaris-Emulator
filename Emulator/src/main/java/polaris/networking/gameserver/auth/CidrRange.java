package polaris.networking.gameserver.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * An immutable IPv4/IPv6 CIDR block. Parsing is literal-only — it never
 * resolves hostnames, so attacker-influenced strings can't trigger DNS.
 */
public final class CidrRange {

    private final byte[] network;
    private final int prefixBits;
    private final String text;

    private CidrRange(byte[] network, int prefixBits, String text) {
        this.network = network;
        this.prefixBits = prefixBits;
        this.text = text;
    }

    /** Parses {@code "10.0.0.0/8"} or {@code "2400:cb00::/32"}; null when not a valid CIDR literal. */
    public static CidrRange parse(String cidr) {
        if (cidr == null) return null;
        int slash = cidr.indexOf('/');
        if (slash <= 0 || slash == cidr.length() - 1) return null;
        byte[] address = parseAddress(cidr.substring(0, slash));
        if (address == null) return null;
        int bits;
        try {
            bits = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (bits < 0 || bits > address.length * 8) return null;
        return new CidrRange(address, bits, cidr.trim());
    }

    /** The CIDR exactly as parsed, e.g. for startup logging. */
    @Override
    public String toString() {
        return text;
    }

    /** Parses an IP literal to raw bytes without ever resolving hostnames; null if invalid. */
    public static byte[] parseAddress(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        // A ':' can never appear in a hostname, so getByName treats the input
        // strictly as an IPv6 literal — no DNS possible on this branch.
        if (ip.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(ip).getAddress();
            } catch (UnknownHostException | SecurityException e) {
                return null;
            }
        }
        // Strict dotted-quad IPv4, parsed manually so DNS can never happen
        // ("dead.beef" would otherwise be a resolvable hostname).
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) return null;
            for (int c = 0; c < part.length(); c++) {
                if (part.charAt(c) < '0' || part.charAt(c) > '9') return null;
            }
            int value = Integer.parseInt(part);
            if (value > 255) return null;
            out[i] = (byte) value;
        }
        return out;
    }

    public boolean contains(byte[] address) {
        if (address == null || address.length != network.length) return false;
        int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != network[i]) return false;
        }
        int remainder = prefixBits % 8;
        if (remainder == 0) return true;
        int mask = 0xFF << (8 - remainder);
        return (address[fullBytes] & mask) == (network[fullBytes] & mask);
    }
}
