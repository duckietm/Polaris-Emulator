package com.eu.habbo.networking.gameserver.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class PasswordHasher {

    private PasswordHasher() {
    }

    public static String hash(String plain, int cost) {
        return BCrypt.withDefaults().hashToString(cost, plain.toCharArray());
    }

    public static boolean verify(String plain, String stored) {
        if (plain == null || stored == null || stored.isEmpty()) {
            return false;
        }

        try {
            return BCrypt.verifyer().verify(plain.toCharArray(), stored.toCharArray()).verified;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
