package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.StaffChatBuddy;

final class FriendInputGuard {
    static final int MAX_USERNAME_LENGTH = 15;
    static final int MAX_MESSAGE_LENGTH = 255;
    static final int MAX_RELATION_ID = 3;

    private FriendInputGuard() {}

    static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    static boolean isValidUsername(String username) {
        return username != null && !username.isBlank() && username.length() <= MAX_USERNAME_LENGTH;
    }

    static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }

        String normalized = message.trim();
        return normalized.length() > MAX_MESSAGE_LENGTH ? normalized.substring(0, MAX_MESSAGE_LENGTH) : normalized;
    }

    static boolean isValidRelation(int relationId) {
        return relationId >= 0 && relationId <= MAX_RELATION_ID;
    }

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static boolean arePositiveIds(int... ids) {
        for (int id : ids) {
            if (!isPositiveId(id)) {
                return false;
            }
        }
        return true;
    }

    static boolean isValidMessageTarget(int conversationId, int recipientId) {
        return isPositiveId(conversationId) || (conversationId == 0 && isPositiveId(recipientId));
    }

    /** The Staff Chat buddy, only for users holding its permission. */
    static boolean isStaffChatTarget(int id, boolean hasStaffChat) {
        return hasStaffChat && id == StaffChatBuddy.BUDDY_ID;
    }

    static boolean isValidConsoleTarget(int userId, boolean hasStaffChat) {
        return isPositiveId(userId) || isStaffChatTarget(userId, hasStaffChat);
    }

    static boolean isValidMessageTarget(int conversationId, int recipientId, boolean hasStaffChat) {
        return isValidMessageTarget(conversationId, recipientId)
                || (conversationId == 0 && isStaffChatTarget(recipientId, hasStaffChat));
    }
}
