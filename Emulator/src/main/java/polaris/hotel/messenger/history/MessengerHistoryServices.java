package polaris.hotel.messenger.history;

import polaris.Emulator;

public final class MessengerHistoryServices {
    private MessengerHistoryServices() {}

    public static MessengerHistoryService create() {
        return new MessengerHistoryService(
                new JdbcMessengerHistoryRepository(Emulator.getDatabase().getDataSource()));
    }
}
