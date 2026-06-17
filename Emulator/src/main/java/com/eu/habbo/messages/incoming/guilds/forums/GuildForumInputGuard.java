package com.eu.habbo.messages.incoming.guilds.forums;

final class GuildForumInputGuard {
    static final int MAX_PAGE_LIMIT = 50;
    static final int MAX_THREAD_INDEX = 1000;
    static final int MAX_MARK_READ_BATCH = 50;

    private GuildForumInputGuard() {
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    static boolean isPositiveId(int id) {
        return id > 0;
    }

    static boolean isValidPage(int index, int limit) {
        return index >= 0 && limit > 0 && limit <= MAX_PAGE_LIMIT;
    }

    static boolean isValidThreadIndex(int index) {
        return index >= 0 && index <= MAX_THREAD_INDEX;
    }

    static boolean isValidMarkReadBatch(int count) {
        return count > 0 && count <= MAX_MARK_READ_BATCH;
    }

    static boolean isSettingsState(int state) {
        return state >= 0 && state <= 3;
    }

    static boolean isThreadModerationState(int state) {
        return state == 1 || state == 10 || state == 20;
    }

    static boolean isMessageModerationState(int state) {
        return state == 1 || state == 10 || state == 20;
    }
}
