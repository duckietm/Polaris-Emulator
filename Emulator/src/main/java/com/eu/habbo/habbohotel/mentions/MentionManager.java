package com.eu.habbo.habbohotel.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.messenger.MessengerBuddy;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomChatType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.mentions.MentionReceivedComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MentionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MentionManager.class);

    private static final int ROOM_NAME_MAX_LENGTH = 64;
    private static final int MESSAGE_MAX_LENGTH = 255;
    private final ConcurrentHashMap<Integer, Long> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> roomBroadcastCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> requestListCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> requestPageCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> markReadCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> markAllCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> deleteCooldowns = new ConcurrentHashMap<>();

    private volatile long lastPrune = System.currentTimeMillis();
    private static final long PRUNE_INTERVAL_MS = 5 * 60_000L;

    public boolean isEnabled() {
        return configInt("mentions.enabled", 1) == 1;
    }

    public enum BroadcastScope {
        NONE,
        ROOM,
        FRIENDS,
        EVERYONE
    }

    public static final String PERMISSION_EVERYONE = "acc_mention_everyone";
    public static final String PERMISSION_FRIENDS = "acc_mention_friends";

    /** Someone who gets the mention: always a user id, and the connected habbo when they are online. */
    private record Target(int userId, Habbo online) {}

    private Set<String> parseAliases(String configKey, String defaultValue) {
        Set<String> aliases = new HashSet<>();
        String raw = Emulator.getConfig().getValue(configKey, defaultValue);
        for (String alias : raw.split(",")) {
            String trimmed = alias.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                aliases.add(trimmed);
            }
        }
        return aliases;
    }

    private Set<String> roomAliases() {
        return parseAliases("mentions.room.aliases", "room,stanza");
    }

    private Set<String> friendsAliases() {
        return parseAliases("mentions.friends.aliases", "friends,amici");
    }

    private Set<String> everyoneAliases() {
        return parseAliases("mentions.everyone.aliases", "all,everyone,tutti");
    }

    public void process(Habbo sender, Room room, String message, RoomChatType type) {
        try {
            if (!this.isEnabled()) {
                return;
            }

            if (sender == null || room == null || message == null) {
                return;
            }

            if (message.isEmpty() || message.indexOf('@') < 0) {
                return;
            }

            int senderId = sender.getHabboInfo().getId();
            long now = System.currentTimeMillis();
            long cooldownMs = configInt("mentions.cooldown.ms", 3000);
            Long last = this.cooldowns.get(senderId);
            if (last != null && (now - last) < cooldownMs) {
                return;
            }

            MentionTokens.Parsed parsed =
                    MentionTokens.parse(message, this.everyoneAliases(), this.friendsAliases(), this.roomAliases());
            BroadcastScope broadcastScope = parsed.scope();
            LinkedHashSet<String> directTokens = parsed.directTokens();

            boolean denied = false;
            if (broadcastScope == BroadcastScope.EVERYONE && !sender.hasPermission(PERMISSION_EVERYONE)) {
                broadcastScope = BroadcastScope.NONE;
                denied = true;
            } else if (broadcastScope == BroadcastScope.FRIENDS && !sender.hasPermission(PERMISSION_FRIENDS)) {
                broadcastScope = BroadcastScope.NONE;
                denied = true;
            }

            if (denied) {
                // The line went out, the broadcast did not: say so instead of dropping it silently.
                sender.whisperLocalizedOrDefault(
                        "mentions.permission.denied",
                        "You are not allowed to mention everyone or all your friends.",
                        RoomChatMessageBubbles.ALERT);
            }

            if (broadcastScope == BroadcastScope.NONE && directTokens.isEmpty()) {
                return;
            }

            if (broadcastScope != BroadcastScope.NONE) {
                long roomCooldownMs = configInt("mentions.room.cooldown.ms", 15000);
                Long lastRoom = this.roomBroadcastCooldowns.get(senderId);
                if (lastRoom != null && (now - lastRoom) < roomCooldownMs) {
                    return;
                }
            }

            int maxTargets = configInt("mentions.max.targets", 50);
            if (maxTargets <= 0) maxTargets = 1;

            List<Target> targets = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            switch (broadcastScope) {
                case EVERYONE:
                    this.collectEveryoneTargets(senderId, targets, seen, maxTargets);
                    break;
                case FRIENDS:
                    this.collectFriendsTargets(sender, senderId, targets, seen, maxTargets);
                    break;
                case ROOM:
                    this.collectRoomTargets(room, senderId, targets, seen, maxTargets);
                    break;
                case NONE:
                default:
                    this.collectDirectTargets(room, senderId, directTokens, targets, seen, maxTargets);
                    break;
            }

            if (targets.isEmpty()) {
                return;
            }

            this.cooldowns.put(senderId, now);
            if (broadcastScope != BroadcastScope.NONE) this.roomBroadcastCooldowns.put(senderId, now);
            this.pruneCooldownsIfDue(now);

            int mentionType =
                    (broadcastScope != BroadcastScope.NONE) ? HabboMention.TYPE_ROOM : HabboMention.TYPE_DIRECT;
            int timestamp = Emulator.getIntUnixTimestamp();
            String roomName = truncate(room.getName(), ROOM_NAME_MAX_LENGTH);
            String storedMessage = truncate(message, MESSAGE_MAX_LENGTH);

            this.storeAll(targets, sender, room, storedMessage, mentionType, timestamp, roomName);
        } catch (Exception e) {
            LOGGER.error("Failed to process mentions.", e);
        }
    }

    /** Names are looked up among the people online first, then among everyone who could still read it later. */
    private void collectDirectTargets(
            Room room,
            int senderId,
            Set<String> directTokens,
            List<Target> targets,
            Set<Integer> seen,
            int maxTargets) {
        int maxDirectTokens = Math.min(directTokens.size(), maxTargets);
        List<String> unresolved = new ArrayList<>();
        int processed = 0;

        for (String token : directTokens) {
            if (processed++ >= maxDirectTokens) break;

            if (this.isBotOrPetName(room, token)
                    || this.isBotOrPetName(room, MentionTokens.trimTrailingPunctuation(token))) {
                continue;
            }

            Habbo habbo = this.resolveOnline(room, token);
            if (habbo == null) {
                unresolved.add(token);
                continue;
            }

            if (habbo.getHabboInfo().getId() == senderId || !acceptsMention(habbo, false, senderId)) {
                continue;
            }

            if (seen.add(habbo.getHabboInfo().getId())) {
                targets.add(new Target(habbo.getHabboInfo().getId(), habbo));
            }

            if (targets.size() >= maxTargets) {
                return;
            }
        }

        if (!unresolved.isEmpty() && targets.size() < maxTargets) {
            this.collectOfflineTargets(unresolved, senderId, targets, seen, maxTargets);
        }
    }

    private void collectRoomTargets(Room room, int senderId, List<Target> targets, Set<Integer> seen, int maxTargets) {
        for (Habbo habbo : room.getHabbos()) {
            if (habbo == null || habbo.getHabboInfo().getId() == senderId) continue;
            if (!acceptsMention(habbo, true, senderId)) continue;
            if (seen.add(habbo.getHabboInfo().getId()))
                targets.add(new Target(habbo.getHabboInfo().getId(), habbo));
            if (targets.size() >= maxTargets) break;
        }
    }

    private void collectFriendsTargets(
            Habbo sender, int senderId, List<Target> targets, Set<Integer> seen, int maxTargets) {
        if (sender.getMessenger() == null) return;
        HabboManager habboManager = Emulator.getGameEnvironment().getHabboManager();
        for (MessengerBuddy buddy : sender.getMessenger().getFriends().values()) {
            if (buddy == null) continue;
            int buddyId = buddy.getId();
            if (buddyId == senderId) continue;
            Habbo online = habboManager.getHabbo(buddyId);
            if (online == null) continue;
            if (!acceptsMention(online, true, senderId)) continue;
            if (seen.add(buddyId)) targets.add(new Target(buddyId, online));
            if (targets.size() >= maxTargets) break;
        }
    }

    private void collectEveryoneTargets(int senderId, List<Target> targets, Set<Integer> seen, int maxTargets) {
        for (Habbo habbo : Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .values()) {
            if (habbo == null || habbo.getHabboInfo().getId() == senderId) continue;
            if (!acceptsMention(habbo, true, senderId)) continue;
            if (seen.add(habbo.getHabboInfo().getId()))
                targets.add(new Target(habbo.getHabboInfo().getId(), habbo));
            if (targets.size() >= maxTargets) break;
        }
    }

    /**
     * Names nobody online answers to: the mention is kept for the next login, unless the user
     * switched mentions off or ignores the sender. One query for all of them.
     */
    private void collectOfflineTargets(
            List<String> tokens, int senderId, List<Target> targets, Set<Integer> seen, int maxTargets) {
        Set<String> names = new LinkedHashSet<>();
        for (String token : tokens) {
            names.add(token);
            String trimmed = MentionTokens.trimTrailingPunctuation(token);
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            placeholders.append(i == 0 ? "?" : ", ?");
        }

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT users.id FROM users LEFT JOIN users_settings ON users_settings.user_id = users.id"
                                + " WHERE users.username IN (" + placeholders + ") AND users.id <> ?"
                                + " AND COALESCE(users_settings.mentions_enabled, '1') = '1'"
                                + " AND NOT EXISTS (SELECT 1 FROM users_ignored WHERE users_ignored.user_id = users.id"
                                + " AND users_ignored.target_id = ?)")) {
            int index = 1;
            for (String name : names) {
                statement.setString(index++, name);
            }
            statement.setInt(index++, senderId);
            statement.setInt(index, senderId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next() && targets.size() < maxTargets) {
                    int userId = set.getInt(1);
                    if (seen.add(userId)) {
                        targets.add(new Target(userId, null));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to resolve offline mention targets.", e);
        }
    }

    private boolean acceptsMention(Habbo recipient, boolean isBroadcast, int senderId) {
        if (recipient == null) return false;
        if (recipient.getClient() == null) return false;
        if (recipient.getHabboStats() == null) return false;
        if (!recipient.getHabboStats().mentionsEnabled()) return false;
        if (isBroadcast && !recipient.getHabboStats().massMentionsEnabled()) return false;
        return !recipient.getHabboStats().userIgnored(senderId);
    }

    /** One connection for the whole fan-out; the people online are told as soon as their row exists. */
    private void storeAll(
            List<Target> targets,
            Habbo sender,
            Room room,
            String message,
            int mentionType,
            int timestamp,
            String roomName) {
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO habbo_mentions (target_user_id, sender_user_id, sender_username, room_id, room_name, message, mention_type, timestamp, `read`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                        Statement.RETURN_GENERATED_KEYS)) {
            for (Target target : targets) {
                try {
                    statement.setInt(1, target.userId());
                    statement.setInt(2, sender.getHabboInfo().getId());
                    statement.setString(3, sender.getHabboInfo().getUsername());
                    statement.setInt(4, room.getId());
                    statement.setString(5, roomName);
                    statement.setString(6, message);
                    statement.setInt(7, mentionType);
                    statement.setInt(8, timestamp);
                    statement.executeUpdate();

                    int generatedId = 0;
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (keys.next()) {
                            generatedId = keys.getInt(1);
                        }
                    }

                    if (generatedId > 0
                            && target.online() != null
                            && target.online().getClient() != null) {
                        HabboMention mention = new HabboMention(
                                target.userId(), generatedId, sender, room, roomName, message, mentionType, timestamp);
                        target.online().getClient().sendResponse(new MentionReceivedComposer(mention));
                    }
                } catch (SQLException e) {
                    LOGGER.error("Failed to store mention.", e);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to store mentions.", e);
        }
    }

    /** How many mentions one page holds. */
    public int pageSize() {
        return normalizePageSize(configInt("mentions.store.limit", 50));
    }

    static int normalizePageSize(int configured) {
        if (configured <= 0) return 50;
        return Math.min(configured, 200);
    }

    public List<HabboMention> getMentions(int userId, int limit) {
        return this.getMentionsPage(userId, limit, 0).mentions();
    }

    /**
     * The newest {@code limit} mentions, or the ones older than {@code beforeId} when it is
     * positive. One more row than asked is read to know whether older ones remain.
     */
    public MentionPage getMentionsPage(int userId, int limit, int beforeId) {
        int size = normalizePageSize(limit);
        List<HabboMention> mentions = new ArrayList<>();

        if (beforeId <= 0) {
            this.pruneRead(userId);
        }

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT habbo_mentions.*, users.look AS sender_figure FROM habbo_mentions LEFT JOIN users ON users.id = habbo_mentions.sender_user_id WHERE target_user_id = ? AND (? <= 0 OR habbo_mentions.id < ?) ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, beforeId);
            statement.setInt(3, beforeId);
            statement.setInt(4, size + 1);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    mentions.add(new HabboMention(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load mentions.", e);
        }

        boolean hasMore = mentions.size() > size;
        return new MentionPage(hasMore ? new ArrayList<>(mentions.subList(0, size)) : mentions, hasMore);
    }

    /** Opt-in: with {@code mentions.retention.days} above zero, the read mentions older than that go. */
    private void pruneRead(int userId) {
        int days = configInt("mentions.retention.days", 0);
        if (days <= 0) return;

        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM habbo_mentions WHERE target_user_id = ? AND `read` = 1 AND timestamp < ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, Emulator.getIntUnixTimestamp() - days * 86400);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to prune old mentions.", e);
        }
    }

    public void markRead(int userId, int mode, int mentionId) {
        if (mode != 0 && mode != 1) return;
        if (mode == 1 && mentionId <= 0) return;

        String query = mode == 1
                ? "UPDATE habbo_mentions SET `read` = 1 WHERE target_user_id = ? AND id = ? AND `read` = 0"
                : "UPDATE habbo_mentions SET `read` = 1 WHERE target_user_id = ? AND `read` = 0";
        try (Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, userId);
            if (mode == 1) {
                statement.setInt(2, mentionId);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to mark mentions as read.", e);
        }
    }

    public void delete(int userId, int mentionId) {
        if (mentionId <= 0) return;
        try (Connection connection = openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("DELETE FROM habbo_mentions WHERE target_user_id = ? AND id = ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, mentionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete mention.", e);
        }
    }

    public boolean tryAcquireRequestPage(int userId) {
        long cooldownMs = configInt("mentions.page.cooldown.ms", 300);
        return tryAcquire(this.requestPageCooldowns, userId, cooldownMs);
    }

    public boolean tryAcquireRequestList(int userId) {
        long cooldownMs = configInt("mentions.request.cooldown.ms", 2000);
        return tryAcquire(this.requestListCooldowns, userId, cooldownMs);
    }

    /**
     * Kept for plugins built against earlier releases: the packet handlers no longer use it,
     * because mark-read must always apply what the client already showed.
     */
    @Deprecated
    public boolean tryAcquireMarkRead(int userId, int mode) {
        long cooldownMs;
        ConcurrentHashMap<Integer, Long> bucket;
        if (mode == 1) {
            cooldownMs = configInt("mentions.markread.cooldown.ms", 500);
            bucket = this.markReadCooldowns;
        } else {
            cooldownMs = configInt("mentions.markall.cooldown.ms", 5000);
            bucket = this.markAllCooldowns;
        }
        return tryAcquire(bucket, userId, cooldownMs);
    }

    /** Kept for plugins built against earlier releases; the delete handler no longer uses it. */
    @Deprecated
    public boolean tryAcquireDelete(int userId) {
        long cooldownMs = configInt("mentions.delete.cooldown.ms", 500);
        return tryAcquire(this.deleteCooldowns, userId, cooldownMs);
    }

    private boolean tryAcquire(ConcurrentHashMap<Integer, Long> bucket, int userId, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = bucket.get(userId);
        if (last != null && (now - last) < cooldownMs) {
            return false;
        }
        bucket.put(userId, now);
        this.pruneCooldownsIfDue(now);
        return true;
    }

    private void pruneCooldownsIfDue(long now) {
        if (now - this.lastPrune < PRUNE_INTERVAL_MS) return;
        this.lastPrune = now;

        long mentionWindow = configInt("mentions.cooldown.ms", 3000);
        long roomWindow = configInt("mentions.room.cooldown.ms", 15000);
        long requestWindow = configInt("mentions.request.cooldown.ms", 2000);
        long markReadWindow = configInt("mentions.markread.cooldown.ms", 500);
        long markAllWindow = configInt("mentions.markall.cooldown.ms", 5000);
        long deleteWindow = configInt("mentions.delete.cooldown.ms", 500);

        prune(this.cooldowns, now, mentionWindow);
        prune(this.roomBroadcastCooldowns, now, roomWindow);
        prune(this.requestListCooldowns, now, requestWindow);
        prune(this.requestPageCooldowns, now, configInt("mentions.page.cooldown.ms", 300));
        prune(this.markReadCooldowns, now, markReadWindow);
        prune(this.markAllCooldowns, now, markAllWindow);
        prune(this.deleteCooldowns, now, deleteWindow);
    }

    private static void prune(ConcurrentHashMap<Integer, Long> bucket, long now, long windowMs) {
        Iterator<Map.Entry<Integer, Long>> it = bucket.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            Long value = entry.getValue();
            if (value == null || (now - value) >= windowMs) {
                it.remove();
            }
        }
    }

    private static int configInt(String key, int defaultValue) {
        return Emulator.getConfig().getInt(key, defaultValue);
    }

    private static Connection openConnection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, max);
    }

    private boolean isBotOrPetName(Room room, String token) {
        if (room == null || token == null || token.isEmpty()) return false;

        List<Bot> bots = room.getBots(token);
        if (bots != null && !bots.isEmpty()) return true;

        if (room.getUnitManager() != null && room.getUnitManager().getPets() != null) {
            for (Pet pet : room.getUnitManager().getPets()) {
                if (pet != null && pet.getName() != null && pet.getName().equalsIgnoreCase(token)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** The connected habbo a name belongs to, in the room first and anywhere in the hotel after. */
    private Habbo resolveOnline(Room room, String rawToken) {
        Habbo habbo = room.getHabbo(rawToken);
        if (habbo != null) {
            return habbo;
        }

        HabboManager habboManager = Emulator.getGameEnvironment().getHabboManager();
        habbo = habboManager.getHabbo(rawToken);
        if (habbo != null) {
            return habbo;
        }
        String trimmed = MentionTokens.trimTrailingPunctuation(rawToken);
        if (!trimmed.isEmpty() && !trimmed.equals(rawToken)) {
            habbo = room.getHabbo(trimmed);
            if (habbo != null) {
                return habbo;
            }
            return habboManager.getHabbo(trimmed);
        }
        return null;
    }
}
