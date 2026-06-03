package com.eu.habbo.habbohotel.mentions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatType;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MentionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MentionManager.class);

    private final ConcurrentHashMap<Integer, Long> cooldowns = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return Emulator.getConfig().getInt("mentions.enabled", 1) == 1;
    }

    private Set<String> roomAliases() {
        Set<String> aliases = new HashSet<>();
        String raw = Emulator.getConfig().getValue("mentions.room.aliases", "amici,friends,all,everyone,tutti,room,stanza");
        for (String alias : raw.split(",")) {
            String trimmed = alias.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                aliases.add(trimmed);
            }
        }
        return aliases;
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
            long cooldownMs = Emulator.getConfig().getInt("mentions.cooldown.ms", 3000);
            Long last = this.cooldowns.get(senderId);
            if (last != null && (now - last) < cooldownMs) {
                return;
            }

            Set<String> aliases = this.roomAliases();
            boolean roomBroadcast = false;
            LinkedHashSet<String> directTokens = new LinkedHashSet<>();

            for (String token : message.split("\\s+")) {
                if (token.length() < 2 || token.charAt(0) != '@') {
                    continue;
                }

                String raw = token.substring(1);
                String aliasCandidate = trimTrailingPunctuation(raw).toLowerCase();

                if (!aliasCandidate.isEmpty() && aliases.contains(aliasCandidate)) {
                    roomBroadcast = true;
                } else if (!raw.isEmpty()) {
                    directTokens.add(raw);
                }
            }

            if (!roomBroadcast && directTokens.isEmpty()) {
                return;
            }

            int maxTargets = Emulator.getConfig().getInt("mentions.max.targets", 50);

            List<Habbo> targets = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            if (roomBroadcast) {
                for (Habbo habbo : room.getHabbos()) {
                    if (habbo == null || habbo.getHabboInfo().getId() == senderId) {
                        continue;
                    }
                    if (seen.add(habbo.getHabboInfo().getId())) {
                        targets.add(habbo);
                    }
                    if (targets.size() >= maxTargets) {
                        break;
                    }
                }
            } else {
                for (String token : directTokens) {
                    Habbo habbo = this.resolveHabbo(room, token);
                    if (habbo == null || habbo.getHabboInfo().getId() == senderId) {
                        continue;
                    }
                    if (seen.add(habbo.getHabboInfo().getId())) {
                        targets.add(habbo);
                    }
                    if (targets.size() >= maxTargets) {
                        break;
                    }
                }
            }

            if (targets.isEmpty()) {
                return;
            }

            this.cooldowns.put(senderId, now);

            int mentionType = roomBroadcast ? HabboMention.TYPE_ROOM : HabboMention.TYPE_DIRECT;
            int timestamp = Emulator.getIntUnixTimestamp();
            String roomName = room.getName();

            String storedMessage = message;
            if (storedMessage.length() > 255) {
                storedMessage = storedMessage.substring(0, 255);
            }

            for (Habbo target : targets) {
                this.store(target, sender, room, storedMessage, mentionType, timestamp, roomName);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process mentions.", e);
        }
    }

    private void store(Habbo target, Habbo sender, Room room, String message, int mentionType, int timestamp, String roomName) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO habbo_mentions (target_user_id, sender_user_id, sender_username, room_id, room_name, message, mention_type, timestamp, `read`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, target.getHabboInfo().getId());
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

            HabboMention mention = new HabboMention(target.getHabboInfo().getId(), generatedId, sender, room, roomName, message, mentionType, timestamp);

            if (target.getClient() != null) {
                target.getClient().sendResponse(new com.eu.habbo.messages.outgoing.mentions.MentionReceivedComposer(mention));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to store mention.", e);
        }
    }

    public List<HabboMention> getMentions(int userId, int limit) {
        List<HabboMention> mentions = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM habbo_mentions WHERE target_user_id = ? ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, limit);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    mentions.add(new HabboMention(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load mentions.", e);
        }
        return mentions;
    }

    public void markRead(int userId, int mode, int mentionId) {
        String query = mode == 1
                ? "UPDATE habbo_mentions SET `read` = 1 WHERE target_user_id = ? AND id = ?"
                : "UPDATE habbo_mentions SET `read` = 1 WHERE target_user_id = ?";
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM habbo_mentions WHERE target_user_id = ? AND id = ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, mentionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to delete mention.", e);
        }
    }

    private static final String TRAILING_PUNCTUATION = ".,!?;:)]}\"'";

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * Resolve a present room occupant from a raw mention token. Tries the token
     * verbatim first (so usernames containing allowed punctuation such as '-',
     * '.', '!' still match), then falls back to a trailing-punctuation-trimmed
     * form so a mention written as "@Bob!" still resolves the user "Bob".
     */
    private Habbo resolveHabbo(Room room, String rawToken) {
        Habbo habbo = room.getHabbo(rawToken);
        if (habbo != null) {
            return habbo;
        }
        String trimmed = trimTrailingPunctuation(rawToken);
        if (!trimmed.isEmpty() && !trimmed.equals(rawToken)) {
            return room.getHabbo(trimmed);
        }
        return null;
    }
}
