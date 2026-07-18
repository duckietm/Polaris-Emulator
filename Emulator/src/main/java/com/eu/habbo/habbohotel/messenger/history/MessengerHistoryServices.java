package com.eu.habbo.habbohotel.messenger.history;

import com.eu.habbo.Emulator;

public final class MessengerHistoryServices {
    private MessengerHistoryServices() {
    }

    public static MessengerHistoryService create() {
        return new MessengerHistoryService(new JdbcMessengerHistoryRepository(Emulator.getDatabase().getDataSource()));
    }
}
