package polaris.messages.outgoing.users;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guilds.Guild;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.users.ProfileVisibility;
import polaris.hotel.users.User;
import polaris.hotel.users.UserCustomizationData;
import polaris.hotel.users.UserInfo;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserProfileComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserProfileComposer.class);

    private final UserInfo userInfo;
    private User user;
    private GameClient viewer;

    public UserProfileComposer(UserInfo userInfo, GameClient viewer) {
        this.userInfo = userInfo;
        this.viewer = viewer;
    }

    public UserProfileComposer(User user, GameClient viewer) {
        this.user = user;
        this.userInfo = user.getUserInfo();
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        if (this.userInfo == null) return null;

        this.response.init(Outgoing.UserProfileComposer);

        this.response.appendInt(this.userInfo.getId());
        this.response.appendString(this.userInfo.getUsername());
        this.response.appendString(this.userInfo.getLook());
        this.response.appendString(this.userInfo.getMotto());
        this.response.appendString(
                new SimpleDateFormat("dd-MM-yyyy").format(new Date(this.userInfo.getAccountCreated() * 1000L)));

        int achievementScore = 0;
        boolean profileHidden = false;
        if (this.user != null) {
            achievementScore = this.user.getUserStats().getAchievementScore();
            profileHidden = this.user.getUserStats().hideProfile;
        } else {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT achievement_score, hide_profile FROM users_settings WHERE user_id = ? LIMIT 1")) {
                statement.setInt(1, this.userInfo.getId());
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        achievementScore = set.getInt("achievement_score");
                        profileHidden = "1".equals(set.getString("hide_profile"));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
        // "Hide my profile": other users get the same layout with the counts, groups and
        // last online time left out. The owner and moderators still see it all.
        boolean hideDetails =
                ProfileVisibility.hidesDetailsFrom(this.viewer.getUser(), this.userInfo.getId(), profileHidden);

        this.response.appendInt(achievementScore);
        this.response.appendInt(
                hideDetails ? ProfileVisibility.HIDDEN_VALUE : Messenger.getFriendCount(this.userInfo.getId()));
        this.response.appendBoolean(
                this.viewer.getUser().getMessenger().getFriends().containsKey(this.userInfo.getId())); // Friend
        // Friend request sent: a pending request from the viewer to this user.
        this.response.appendBoolean(Messenger.friendRequested(
                this.userInfo.getId(), this.viewer.getUser().getUserInfo().getId()));
        this.response.appendBoolean(
                this.userInfo.isOnline() && (this.user == null || !this.user.getUserStats().hideOnline));

        List<Guild> guilds = new ArrayList<>();
        if (this.user != null) {
            List<Integer> toRemove = new ArrayList<>();
            for (int index = this.user.getUserStats().guilds.size(); index > 0; index--) {
                int i = this.user.getUserStats().guilds.get(index - 1);
                if (i == 0) continue;

                Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(i);

                if (guild != null) {
                    guilds.add(guild);
                } else {
                    toRemove.add(i);
                }
            }

            for (int i : toRemove) {
                this.user.getUserStats().removeGuild(i);
            }
        } else {
            guilds = Emulator.getGameEnvironment().getGuildManager().getGuilds(this.userInfo.getId());
        }

        if (hideDetails) guilds = new ArrayList<>();

        this.response.appendInt(guilds.size());
        for (Guild guild : guilds) {
            this.response.appendInt(guild.getId());
            this.response.appendString(guild.getName());
            this.response.appendString(guild.getBadge());
            this.response.appendString(
                    Emulator.getGameEnvironment().getGuildManager().getSymbolColor(guild.getColorOne()).valueA);
            this.response.appendString(
                    Emulator.getGameEnvironment().getGuildManager().getSymbolColor(guild.getColorTwo()).valueA);
            this.response.appendBoolean(this.user != null && guild.getId() == this.user.getUserStats().guild);
            this.response.appendInt(guild.getOwnerId());
            this.response.appendBoolean(guild.getOwnerId() == this.userInfo.getId());
        }

        this.response.appendInt(
                hideDetails
                        ? ProfileVisibility.HIDDEN_VALUE
                        : Emulator.getIntUnixTimestamp() - this.userInfo.getLastOnline()); // Secs ago.
        this.response.appendBoolean(true);

        this.response.appendInt(this.userInfo.getInfostandBg());
        this.response.appendInt(this.userInfo.getInfostandStand());
        this.response.appendInt(this.userInfo.getInfostandOverlay());
        this.response.appendInt(this.userInfo.getInfostandCardBg());
        UserCustomizationData customizationData = (this.user != null)
                ? UserCustomizationData.fromUser(this.user)
                : UserCustomizationData.fromUserId(this.userInfo.getId());
        this.response.appendString(customizationData.nickIcon);
        this.response.appendString(customizationData.prefixText);
        this.response.appendString(customizationData.prefixColor);
        this.response.appendString(customizationData.prefixIcon);
        this.response.appendString(customizationData.prefixEffect);
        this.response.appendString(customizationData.prefixFont);
        this.response.appendString(customizationData.displayOrder);
        this.response.appendInt(this.getTotalBadges());

        // Appended after the fields above so older clients, which stop reading there, keep working.
        // Presence: 1 online, 0 offline; 2 when the user hides their status and looks at their own
        // profile. Anybody else sees a hidden user as offline, as the boolean above already says.
        this.response.appendInt(this.onlineStatus());
        UserProfileLevel level = UserProfileLevel.of(achievementScore, UserProfileLevel.DEFAULT_THRESHOLDS);
        this.response.appendInt(level.level());
        this.response.appendInt(level.nextLevelStart());
        // Official isHidden: the owner hides the profile from everybody else. The hidden fields
        // above are already left out for those viewers; the client shows the hidden notice.
        this.response.appendBoolean(profileHidden);

        return this.response;
    }

    private int onlineStatus() {
        if (!this.userInfo.isOnline()) return 0;

        boolean hidden = this.user != null && this.user.getUserStats().hideOnline;

        if (!hidden) return 1;

        boolean ownProfile = this.viewer != null
                && this.viewer.getUser() != null
                && this.viewer.getUser().getUserInfo().getId() == this.userInfo.getId();

        return ownProfile ? 2 : 0;
    }

    private int getTotalBadges() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(DISTINCT badge_code) AS total_badges FROM users_badges WHERE user_id = ?")) {
            statement.setInt(1, this.userInfo.getId());

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getInt("total_badges");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception while loading total badges for extended profile", e);
        }

        return 0;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public User getUser() {
        return user;
    }

    public GameClient getViewer() {
        return viewer;
    }
}
