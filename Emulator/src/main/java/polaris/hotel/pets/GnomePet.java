package polaris.hotel.pets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;

public class GnomePet extends Pet implements IPetLook {
    private static final Logger LOGGER = LoggerFactory.getLogger(GnomePet.class);

    private final String gnomeData;

    public GnomePet(ResultSet set) throws SQLException {
        super(set);

        this.gnomeData = set.getString("gnome_data");
    }

    public GnomePet(int type, int race, String color, String name, int userId, String gnomeData) {
        super(type, race, color, name, userId);

        this.gnomeData = gnomeData;
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendInt(this.getId());
        message.appendString(this.getName());
        message.appendInt(this.petData.getType());
        message.appendInt(this.race);
        message.appendString(this.color);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(-1);
    }

    @Override
    public void run() {
        if (this.needsUpdate) {
            super.run();

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("UPDATE users_pets SET gnome_data = ? WHERE id = ? LIMIT 1")) {
                statement.setString(1, this.gnomeData);
                statement.setInt(2, this.id);
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    @Override
    public String getLook() {
        return this.getPetData().getType() + " 0 FFFFFF " + this.gnomeData;
    }

    @Override
    public void scratched(User user) {
        super.scratched(user);

        if (this.getPetData().getType() == 26) {
            if (user != null) {
                AchievementManager.progressAchievement(
                        user,
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("GnomeRespectGiver"));
            }
            AchievementManager.progressAchievement(
                    Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId()),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GnomeRespectReceiver"));
        } else if (this.getPetData().getType() == 27) {
            if (user != null) {
                AchievementManager.progressAchievement(
                        user,
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("LeprechaunRespectGiver"));
            }
            AchievementManager.progressAchievement(
                    Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId()),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("LeprechaunRespectReceiver"));
        }
    }

    @Override
    protected void levelUp() {
        super.levelUp();

        if (this.getPetData().getType() == 26) {
            AchievementManager.progressAchievement(
                    Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId()),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GnomeLevelUp"));
        } else if (this.getPetData().getType() == 27) {
            AchievementManager.progressAchievement(
                    Emulator.getGameEnvironment().getUserManager().getUser(this.getUserId()),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("LeprechaunLevelUp"));
        }
    }
}
