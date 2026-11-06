package polaris.hotel.messenger.history;

import java.util.List;

public record MessengerHistoryPage(List<MessengerStoredMessage> messages, boolean hasMore) {
    public MessengerHistoryPage {
        messages = List.copyOf(messages);
    }
}
