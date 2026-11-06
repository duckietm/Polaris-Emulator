package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.users.AddUserBadgeComposer;

public class GiveBadge extends RCONMessage<GiveBadge.GiveBadgeJSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiveBadge.class);

    public GiveBadge() {
        super(GiveBadgeJSON.class);
    }

    @Override
    public void handle(Gson gson, GiveBadgeJSON json) {
        if (json.user_id == -1) {
            this.status = RCONMessage.USER_NOT_FOUND;
            return;
        }

        if (json.badge.isEmpty()) {
            this.status = RCONMessage.SYSTEM_ERROR;
            return;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(json.user_id);

        String username = json.user_id + "";
        if (user != null) {
            username = user.getUserInfo().getUsername();

            for (String badgeCode : json.badge.split(";")) {
                if (user.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
                    this.status = RCONMessage.STATUS_ERROR;
                    this.message += Emulator.getTexts()
                                    .getValue("commands.error.cmd_badge.already_owned")
                                    .replace("%user%", username)
                                    .replace("%badge%", badgeCode)
                            + "\r";
                    continue;
                }

                UserBadge badge = new UserBadge(0, badgeCode, 0, user);

                badge.run();

                user.getInventory().getBadgesComponent().addBadge(badge);
                user.getClient().sendResponse(new AddUserBadgeComposer(badge));

                this.message = Emulator.getTexts()
                        .getValue("commands.succes.cmd_badge.given")
                        .replace("%user%", username)
                        .replace("%badge%", badgeCode);
            }
        } else {
            UserInfo userInfo = UserManager.getOfflineUserInfo(json.user_id);
            if (userInfo == null) {
                this.status = RCONMessage.USER_NOT_FOUND;
                return;
            }

            username = userInfo.getUsername();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                for (String badgeCode : json.badge.split(";")) {
                    int numberOfRows = 0;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(slot_id) FROM users_badges WHERE user_id = ? AND badge_code = ? LIMIT 1")) {
                        statement.setInt(1, userInfo.getId());
                        statement.setString(2, badgeCode);
                        try (ResultSet set = statement.executeQuery()) {
                            if (set.next()) {
                                numberOfRows = set.getInt(1);
                            }
                        }
                    }

                    if (numberOfRows != 0) {
                        this.status = RCONMessage.STATUS_ERROR;
                        this.message += Emulator.getTexts()
                                        .getValue("commands.error.cmd_badge.already_owns")
                                        .replace("%user%", username)
                                        .replace("%badge%", badgeCode)
                                + "\r";
                    } else {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO users_badges (`id`, `user_id`, `slot_id`, `badge_code`) VALUES (null, ?, 0, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                            statement.setInt(1, userInfo.getId());
                            statement.setString(2, badgeCode);
                            statement.execute();
                        }

                        this.message = Emulator.getTexts()
                                .getValue("commands.succes.cmd_badge.given")
                                .replace("%user%", username)
                                .replace("%badge%", badgeCode);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                this.status = RCONMessage.STATUS_ERROR;
                this.message = e.getMessage();
            }
        }
    }

    static class GiveBadgeJSON {

        @Positive(message = "invalid user")
        public int user_id = -1;

        @NotBlank(message = "invalid badge")
        @Size(max = 512, message = "invalid badge")
        @Pattern(regexp = "[A-Za-z0-9_\\-;]+", message = "invalid badge")
        public String badge;
    }
}
