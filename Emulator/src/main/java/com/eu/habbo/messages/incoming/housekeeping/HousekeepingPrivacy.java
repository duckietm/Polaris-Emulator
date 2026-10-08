package com.eu.habbo.messages.incoming.housekeeping;

/**
 * IP addresses in the panel are masked unless the operator holds acc_hk_view_private and asked
 * to see them; every reveal is written to the audit log.
 */
public final class HousekeepingPrivacy {
    static final String PERMISSION = "acc_hk_view_private";

    private HousekeepingPrivacy() {}

    /** 93.45.12.7 -> 93.45.x.x; 2001:db8:85a3::1 -> 2001:db8:x; anything else keeps nothing. */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "";

        String value = ip.trim();
        String[] v4 = value.split("\\.");

        if (v4.length == 4) return v4[0] + "." + v4[1] + ".x.x";

        if (value.contains(":")) {
            String[] v6 = value.split(":");

            return v6.length >= 2 ? v6[0] + ":" + v6[1] + ":x" : "x";
        }

        return "x";
    }

    static String show(String ip, boolean reveal) {
        if (ip == null) return "";

        return reveal ? ip : maskIp(ip);
    }
}
