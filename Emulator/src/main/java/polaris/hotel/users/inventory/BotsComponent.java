package polaris.hotel.users.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;

public class BotsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotsComponent.class);

    private final Map<Integer, Bot> bots = new HashMap<>();

    public BotsComponent(User user) {
        this.loadBots(user);
    }

    private void loadBots(User user) {
        synchronized (this.bots) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "SELECT users.username AS owner_name, bots.* FROM bots INNER JOIN users ON users.id = bots.user_id WHERE user_id = ? AND room_id = 0 ORDER BY id ASC")) {
                statement.setInt(1, user.getUserInfo().getId());
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        Bot bot = Emulator.getGameEnvironment().getBotManager().loadBot(set);
                        if (bot != null) {
                            this.bots.put(set.getInt("id"), bot);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public Bot getBot(int botId) {
        return this.bots.get(botId);
    }

    public void addBot(Bot bot) {
        synchronized (this.bots) {
            this.bots.put(bot.getId(), bot);
        }
    }

    public void removeBot(Bot bot) {
        synchronized (this.bots) {
            this.bots.remove(bot.getId());
        }
    }

    public Map<Integer, Bot> getBots() {
        return this.bots;
    }

    public void dispose() {
        synchronized (this.bots) {
            for (Map.Entry<Integer, Bot> map : this.bots.entrySet()) {
                if (map.getValue().needsUpdate()) {
                    Emulator.getThreading().run(map.getValue());
                }
            }
            this.bots.clear();
        }
    }
}
