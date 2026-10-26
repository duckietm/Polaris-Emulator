package com.eu.habbo.messages.incoming.friends;

import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.friends.UserSearchResultComposer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SearchUserEvent extends MessageHandler {
    private static final long CACHE_TTL_MS = 30_000; // 30 second TTL
    private static final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Set<MessengerBuddy>> cachedResults = new ConcurrentHashMap<>();

    public static void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        cacheTimestamps.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > CACHE_TTL_MS) {
                cachedResults.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /** One database search every 3 seconds per user; cached results are served in between. */
    static final long SEARCH_INTERVAL_MS = 3000;

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        String username = this.packet.readString().replace(" ", "").toLowerCase();

        if (username.isEmpty()) return;

        if (username.length() > 15) {
            username = username.substring(0, 15);
        }

        if (this.client.getHabbo().getMessenger() != null) {
            Set<MessengerBuddy> buddies = cachedResults.get(username);

            if (buddies == null) {
                // The throttle was checked but never set, so every new prefix ran an uncached LIKE query.
                long now = System.currentTimeMillis();

                if (now - this.client.getHabbo().getHabboStats().lastUsersSearched < SEARCH_INTERVAL_MS) return;

                this.client.getHabbo().getHabboStats().lastUsersSearched = now;
                buddies = Messenger.searchUsers(username);
                cachedResults.put(username, buddies);
                cacheTimestamps.put(username, System.currentTimeMillis());
            }

            this.client.sendResponse(new UserSearchResultComposer(
                    buddies, this.client.getHabbo().getMessenger().getFriends(username), this.client.getHabbo()));
        }
    }
}
