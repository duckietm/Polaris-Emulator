package polaris.hotel.users.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Adds the bot unless the inventory already holds {@code maximum} bots. The check and the add are one step: checked
     * apart, two pickups at the same moment both saw room for one more and the inventory went past the maximum.
     */
    public boolean addBotWithin(Bot bot, int maximum) {
        synchronized (this.bots) {
            if (this.bots.size() >= maximum) return false;

            this.bots.put(bot.getId(), bot);
            return true;
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

    /**
     * A copy taken under the lock, for sending: the inventory composer iterated the live map while another user's
     * pickup added to it, and failed with a ConcurrentModificationException that left the inventory empty.
     */
    public List<Bot> getBotsSnapshot() {
        synchronized (this.bots) {
            return new ArrayList<>(this.bots.values());
        }
    }

    /** Takes every bot out of the inventory at once: a bot added between a copy and a clear was lost. */
    public List<Bot> takeAllBots() {
        synchronized (this.bots) {
            List<Bot> taken = new ArrayList<>(this.bots.values());
            this.bots.clear();
            return taken;
        }
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
