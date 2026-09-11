package com.eu.habbo.messages.incoming.hotelview;

/**
 * A badge request code arrives from the client and is used to build a configuration key, so it is
 * kept to the shape a hotel would ever configure: letters, digits, dash and underscore.
 */
final class BadgeRequestGuard {
    static final int MAX_LENGTH = 32;

    private BadgeRequestGuard() {}

    static boolean isValidCode(String code) {
        if (code == null || code.isEmpty() || code.length() > MAX_LENGTH) {
            return false;
        }

        for (int index = 0; index < code.length(); index++) {
            char character = code.charAt(index);
            boolean allowed = Character.isLetterOrDigit(character) || character == '_' || character == '-';

            if (!allowed) {
                return false;
            }
        }

        return true;
    }
}
