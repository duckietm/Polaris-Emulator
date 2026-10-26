package com.eu.habbo.networking.gameserver.auth;

/**
 * The key a per-address limit counts a client under. An IPv4 address is one client; an IPv6 client is
 * given a whole /64, so rotating through the addresses of its own prefix - free on any IPv6 line - does
 * not reset its counters.
 */
public final class ClientAddressBucket {

    private ClientAddressBucket() {}

    /** The bucket of {@code ip}: the address itself for IPv4, its /64 for IPv6, the input when unparsable. */
    public static String of(String ip) {
        if (ip == null || ip.indexOf(':') < 0) {
            return ip;
        }
        byte[] address = CidrRange.parseAddress(ip);
        if (address == null) {
            return ip;
        }
        if (address.length == 4) { // IPv4-mapped IPv6
            return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "."
                    + (address[3] & 0xff);
        }
        StringBuilder prefix = new StringBuilder(24);
        for (int group = 0; group < 4; group++) {
            prefix.append(Integer.toHexString(((address[2 * group] & 0xff) << 8) | (address[2 * group + 1] & 0xff)))
                    .append(':');
        }
        return prefix.append(":/64").toString();
    }
}
