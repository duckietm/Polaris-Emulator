package com.eu.habbo.networking.gameserver.auth;

import java.util.HexFormat;

/**
 * The lowercase hex the client decodes the secure asset payload from. Kept apart from {@link NitroSecureAssetHandler},
 * whose static key needs the configuration, so it can be used and tested without it.
 */
final class NitroSecureHex {

    private NitroSecureHex() {}

    static String toHex(byte[] bytes) {
        // String.format per byte made a 16 MB asset cost millions of allocations on every request.
        return HexFormat.of().formatHex(bytes);
    }

    static byte[] fromHex(String hex) {
        String normalized = hex == null ? "" : hex.trim();
        if ((normalized.length() % 2) != 0) throw new IllegalArgumentException("Invalid encrypted hex payload.");

        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(normalized.charAt(i * 2), 16);
            int low = Character.digit(normalized.charAt((i * 2) + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid encrypted hex payload.");
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }
}
