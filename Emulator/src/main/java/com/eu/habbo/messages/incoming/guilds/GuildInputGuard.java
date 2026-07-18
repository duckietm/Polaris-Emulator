package com.eu.habbo.messages.incoming.guilds;

final class GuildInputGuard {
    private GuildInputGuard() {
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
}
