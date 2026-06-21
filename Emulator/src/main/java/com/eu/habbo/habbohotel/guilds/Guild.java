package com.eu.habbo.habbohotel.guilds;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Guild implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Guild.class);
    public boolean needsUpdate;
    public int lastRequested = Emulator.getIntUnixTimestamp();
    private int id;
    private final int ownerId;
    private String ownerName;
    private String name;
    private String description;
    private final int roomId;
    private String roomName;
    private GuildState state;
    private boolean rights;
    private int colorOne;
    private int colorTwo;
    private String badge;
    private final int dateCreated;
    private int memberCount;
    private int requestCount;
    private boolean forum = false;
    private SettingsState readForum = SettingsState.ADMINS;
    private SettingsState postMessages = SettingsState.ADMINS;
    private SettingsState postThreads = SettingsState.ADMINS;
    private SettingsState modForum = SettingsState.ADMINS;

    public Guild(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.ownerId = set.getInt("user_id");
        this.ownerName = set.getString("username");
        this.name = set.getString("name");
        this.description = set.getString("description");
        this.state = GuildState.values()[set.getInt("state")];
        this.roomId = set.getInt("room_id");
        this.roomName = set.getString("room_name");
        this.rights = set.getString("rights").equalsIgnoreCase("1");
        this.colorOne = set.getInt("color_one");
        this.colorTwo = set.getInt("color_two");
        this.badge = set.getString("badge");
        this.dateCreated = set.getInt("date_created");
        this.forum = set.getString("forum").equalsIgnoreCase("1");
        this.readForum = SettingsState.valueOf(set.getString("read_forum"));
        this.postMessages = SettingsState.valueOf(set.getString("post_messages"));
        this.postThreads = SettingsState.valueOf(set.getString("post_threads"));
        this.modForum = SettingsState.valueOf(set.getString("mod_forum"));
        this.memberCount = 0;
        this.requestCount = 0;
    }

    public Guild(int ownerId, String ownerName, int roomId, String roomName, String name, String description, int colorOne, int colorTwo, String badge) {
        this.id = 0;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.roomId = roomId;
        this.roomName = roomName;
        this.name = name;
        this.description = description;
        this.state = GuildState.OPEN;
        this.rights = false;
        this.colorOne = colorOne;
        this.colorTwo = colorTwo;
        this.badge = badge;
        this.memberCount = 0;
        this.dateCreated = Emulator.getIntUnixTimestamp();
    }

    public void loadMemberCount() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(id) as count FROM guilds_members WHERE level_id < 3 AND guild_id = ?")) {
                statement.setInt(1, this.id);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        this.memberCount = set.getInt(1);
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(id) as count FROM guilds_members WHERE level_id = 3 AND guild_id = ?")) {
                statement.setInt(1, this.id);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        this.requestCount = set.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    @Override
    public void run() {
        if (this.needsUpdate) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE guilds SET name = ?, description = ?, state = ?, rights = ?, color_one = ?, color_two = ?, badge = ?, read_forum = ?, post_messages = ?, post_threads = ?, mod_forum = ?, forum = ? WHERE id = ?")) {
                statement.setString(1, this.name);
                statement.setString(2, this.description);
                statement.setInt(3, this.state.state);
                statement.setString(4, this.rights ? "1" : "0");
                statement.setInt(5, this.colorOne);
                statement.setInt(6, this.colorTwo);
                statement.setString(7, this.badge);
                statement.setString(8, this.readForum.name());
                statement.setString(9, this.postMessages.name());
                statement.setString(10, this.postThreads.name());
                statement.setString(11, this.modForum.name());
                statement.setString(12, this.forum ? "1" : "0");
                statement.setInt(13, this.id);
                statement.execute();

                this.needsUpdate = false;
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getRoomId() {
        return this.roomId;
    }

    public String getRoomName() {
        return this.roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public GuildState getState() {
        return this.state;
    }

    public void setState(GuildState state) {
        this.state = state;
    }

    public boolean getRights() {
        return this.rights;
    }

    public void setRights(boolean rights) {
        this.rights = rights;
    }

    public int getColorOne() {
        return this.colorOne;
    }

    public void setColorOne(int colorOne) {
        this.colorOne = colorOne;
    }

    public int getColorTwo() {
        return this.colorTwo;
    }

    public void setColorTwo(int colorTwo) {
        this.colorTwo = colorTwo;
    }

    public String getBadge() {
        return this.badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public int getOwnerId() {
        return this.ownerId;
    }

    public int getDateCreated() {
        return dateCreated;
    }

    public int getMemberCount() {
        return this.memberCount;
    }

    public void increaseMemberCount() {
        this.memberCount++;
    }

    public void decreaseMemberCount() {
        this.memberCount--;
    }

    public int getRequestCount() {
        return this.requestCount;
    }

    public void increaseRequestCount() {
        this.requestCount++;
    }

    public void decreaseRequestCount() {
        this.requestCount--;
    }

    public boolean hasForum() {
        return this.forum;
    }

    public void setForum(boolean forum) {
        this.forum = forum;
    }

    public SettingsState canReadForum() {
        return this.readForum;
    }

    public boolean canHabboReadForum(int habboId, GuildMember member, boolean staff) {
        if (staff || this.getOwnerId() == habboId) {
            return true;
        }

        return switch (this.readForum) {
            case EVERYONE -> true;
            case MEMBERS -> member != null && member.getRank().type <= GuildRank.MEMBER.type;
            case ADMINS -> member != null && member.getRank().type < GuildRank.MEMBER.type;
            case OWNER -> false;
            default -> true;
        };
    }

    public void setReadForum(SettingsState readForum) {
        this.readForum = readForum;
    }

    public SettingsState canPostMessages() {
        return this.postMessages;
    }

    public void setPostMessages(SettingsState postMessages) {
        this.postMessages = postMessages;
    }

    public SettingsState canPostThreads() {
        return this.postThreads;
    }

    public void setPostThreads(SettingsState postThreads) {
        this.postThreads = postThreads;
    }

    public SettingsState canModForum() {
        return this.modForum;
    }

    public void setModForum(SettingsState modForum) {
        this.modForum = modForum;
    }
}
