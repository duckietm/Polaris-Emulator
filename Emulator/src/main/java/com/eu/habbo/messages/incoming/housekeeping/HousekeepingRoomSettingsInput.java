package com.eu.habbo.messages.incoming.housekeeping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Limits for the room settings a staff member edits from housekeeping. Name
 * and description follow what {@code Room.setName} / {@code Room.setDescription}
 * keep, so a value that passes is stored unchanged; the other limits match the
 * owner's own settings dialog.
 */
final class HousekeepingRoomSettingsInput {
    static final int MAX_NAME_LENGTH = 50;
    static final int MAX_DESCRIPTION_LENGTH = 250;
    static final int MIN_USERS_MAX = 1;
    static final int MAX_USERS_MAX = 200;
    static final int MAX_TRADE_MODE = 2;
    static final int MAX_TAGS = 2;
    static final int MAX_TAG_LENGTH = 15;

    private HousekeepingRoomSettingsInput() {}

    static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_NAME_LENGTH;
    }

    static boolean isValidDescription(String description) {
        return description != null && description.length() <= MAX_DESCRIPTION_LENGTH;
    }

    static boolean isValidUsersMax(int usersMax) {
        return usersMax >= MIN_USERS_MAX && usersMax <= MAX_USERS_MAX;
    }

    static boolean isValidTradeMode(int tradeMode) {
        return tradeMode >= 0 && tradeMode <= MAX_TRADE_MODE;
    }

    static boolean isValidTagCount(int count) {
        return count >= 0 && count <= MAX_TAGS;
    }

    /**
     * Joins the tags the way the rooms table stores them ("a;b;"), dropping
     * blanks and duplicates. Returns null when a tag is too long or would break
     * the separator.
     */
    static String joinTags(List<String> tags) {
        Set<String> unique = new LinkedHashSet<>();

        for (String raw : tags) {
            String tag = raw == null ? "" : raw.trim();

            if (tag.isEmpty()) {
                continue;
            }

            if (tag.length() > MAX_TAG_LENGTH || tag.indexOf(';') >= 0) {
                return null;
            }

            unique.add(tag);
        }

        StringBuilder joined = new StringBuilder();

        for (String tag : unique) {
            joined.append(tag).append(';');
        }

        return joined.toString();
    }
}
