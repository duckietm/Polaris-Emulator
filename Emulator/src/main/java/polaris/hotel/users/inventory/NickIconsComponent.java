package polaris.hotel.users.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserNickIcon;

public class NickIconsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(NickIconsComponent.class);

    private final List<UserNickIcon> nickIcons = new ArrayList<>();
    private final User user;

    public NickIconsComponent(User user) {
        this.user = user;
        this.loadNickIcons();
    }

    private void loadNickIcons() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM user_nick_icons WHERE user_id = ?")) {
            statement.setInt(1, this.user.getUserInfo().getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.nickIcons.add(new UserNickIcon(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public List<UserNickIcon> getNickIcons() {
        synchronized (this.nickIcons) {
            return new ArrayList<>(this.nickIcons);
        }
    }

    public UserNickIcon getActiveNickIcon() {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.isActive()) {
                    return nickIcon;
                }
            }
        }

        return null;
    }

    public UserNickIcon getNickIcon(int id) {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.getId() == id) {
                    return nickIcon;
                }
            }
        }

        return null;
    }

    public UserNickIcon getNickIconByKey(String iconKey) {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.getIconKey().equalsIgnoreCase(iconKey)) {
                    return nickIcon;
                }
            }
        }

        return null;
    }

    public void addNickIcon(UserNickIcon nickIcon) {
        synchronized (this.nickIcons) {
            this.nickIcons.add(nickIcon);
        }
    }

    public void setActive(int nickIconId) {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                boolean shouldBeActive = (nickIcon.getId() == nickIconId);

                if (nickIcon.isActive() != shouldBeActive) {
                    nickIcon.setActive(shouldBeActive);
                    Emulator.getThreading().run(nickIcon);
                }
            }
        }
    }

    public void deactivateAll() {
        synchronized (this.nickIcons) {
            for (UserNickIcon nickIcon : this.nickIcons) {
                if (nickIcon.isActive()) {
                    nickIcon.setActive(false);
                    Emulator.getThreading().run(nickIcon);
                }
            }
        }
    }

    public void dispose() {
        synchronized (this.nickIcons) {
            this.nickIcons.clear();
        }
    }
}
