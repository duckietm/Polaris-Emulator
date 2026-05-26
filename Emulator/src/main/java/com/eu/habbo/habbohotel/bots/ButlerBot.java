package com.eu.habbo.habbohotel.bots;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.plugin.events.bots.BotServerItemEvent;
import com.eu.habbo.threading.runnables.RoomUnitGiveHanditem;
import com.eu.habbo.threading.runnables.RoomUnitWalkToRoomUnit;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ButlerBot extends Bot {
    private static final Logger LOGGER = LoggerFactory.getLogger(ButlerBot.class);
    public static THashMap<THashSet<String>, Integer> serveItems = new THashMap<>();
    private static final ConcurrentHashMap<Pattern, Integer> serveItemsCompiled = new ConcurrentHashMap<>();

    public ButlerBot(ResultSet set) throws SQLException {
        super(set);
    }

    public ButlerBot(Bot bot) {
        super(bot);
    }

    public static void initialise() {
        if (serveItems == null)
            serveItems = new THashMap<>();

        serveItems.clear();
        serveItemsCompiled.clear();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM bot_serves")) {
            while (set.next()) {
                String[] keys = set.getString("keys").split(";");
                THashSet<String> ks = new THashSet<>();
                Collections.addAll(ks, keys);
                serveItems.put(ks, set.getInt("item"));

                for (String key : keys) {
                    if (key != null && !key.trim().isEmpty()) {
                        try {
                            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(key.toLowerCase()) + "\\b");
                            serveItemsCompiled.put(pattern, set.getInt("item"));
                        } catch (Exception e) {
                            LOGGER.error("Failed to compile butler bot keyword pattern: {}", key, e);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public static void dispose() {
        serveItems.clear();
        serveItemsCompiled.clear();
    }

    @Override
    public void onUserSay(final RoomChatMessage message) {
        if (this.getRoomUnit().hasStatus(RoomUnitStatus.MOVE) || this.getRoom() == null) {
            return;
        }

        double distanceBetweenBotAndHabbo = this.getRoomUnit().getCurrentLocation().distance(message.getHabbo().getRoomUnit().getCurrentLocation());

        if (distanceBetweenBotAndHabbo <= Emulator.getConfig().getInt("hotel.bot.butler.commanddistance")) {

            if (message.getUnfilteredMessage() != null) {
                String unfilteredLower = message.getUnfilteredMessage().toLowerCase();
                for (Map.Entry<Pattern, Integer> entry : serveItemsCompiled.entrySet()) {
                    Pattern pattern = entry.getKey();
                    if (pattern.matcher(unfilteredLower).matches()) {
                        int itemId = entry.getValue();
                        String keyword = pattern.pattern().replace("\\b", "").replace("\\Q", "").replace("\\E", "");

                        // Enable plugins to cancel this event
                        BotServerItemEvent serveEvent = new BotServerItemEvent(this, message.getHabbo(), itemId);
                        if (Emulator.getPluginManager().fireEvent(serveEvent).isCancelled()) {
                            return;
                        }

                        // Start give handitem process
                        if (this.getRoomUnit().canWalk()) {
                            final String key = keyword;
                            final Bot bot = this;

                            // Step 1: Look at Habbo
                            bot.lookAt(serveEvent.habbo);

                            // Step 2: Prepare tasks for when the Bot (carrying the handitem) reaches the Habbo
                            final List<Runnable> tasks = new ArrayList<>();
                            tasks.add(new RoomUnitGiveHanditem(serveEvent.habbo.getRoomUnit(), serveEvent.habbo.getHabboInfo().getCurrentRoom(), serveEvent.itemId));
                            tasks.add(new RoomUnitGiveHanditem(this.getRoomUnit(), serveEvent.habbo.getHabboInfo().getCurrentRoom(), 0));

                            tasks.add(() -> {
                                if(this.getRoom() != null) {
                                    String botMessage = Emulator.getTexts()
                                            .getValue("bots.butler.given")
                                            .replace("%key%", key)
                                            .replace("%username%", serveEvent.habbo.getHabboInfo().getUsername());

                                    if (!WiredManager.triggerUserSays(this.getRoom(), this.getRoomUnit(), botMessage)) {
                                        bot.talk(botMessage);
                                    }
                                }
                            });

                            List<Runnable> failedReached = new ArrayList<>();
                            failedReached.add(() -> {
                                if (distanceBetweenBotAndHabbo <= Emulator.getConfig().getInt("hotel.bot.butler.servedistance", 8)) {
                                    for (Runnable task : tasks) {
                                        task.run();
                                    }
                                }
                            });

                            // Give bot the handitem that it's going to give the Habbo
                            Emulator.getThreading().run(new RoomUnitGiveHanditem(this.getRoomUnit(), serveEvent.habbo.getHabboInfo().getCurrentRoom(), serveEvent.itemId));

                            if (distanceBetweenBotAndHabbo > Emulator.getConfig().getInt("hotel.bot.butler.reachdistance", 3)) {
                                Emulator.getThreading().run(new RoomUnitWalkToRoomUnit(this.getRoomUnit(), serveEvent.habbo.getRoomUnit(), serveEvent.habbo.getHabboInfo().getCurrentRoom(), tasks, failedReached, Emulator.getConfig().getInt("hotel.bot.butler.reachdistance", 3)));
                            } else {
                                Emulator.getThreading().run(failedReached.get(0), 1000);
                            }
                        } else {
                            if(this.getRoom() != null) {
                                this.getRoom().giveHandItem(serveEvent.habbo, serveEvent.itemId);

                                String msg = Emulator.getTexts().getValue("bots.butler.given").replace("%key%", keyword).replace("%username%", serveEvent.habbo.getHabboInfo().getUsername());
                                if (!WiredManager.triggerUserSays(this.getRoom(), this.getRoomUnit(), msg)) {
                                    this.talk(msg);
                                }
                            }
                        }
                        return;
                    }
                }
            }
        }
    }
}
