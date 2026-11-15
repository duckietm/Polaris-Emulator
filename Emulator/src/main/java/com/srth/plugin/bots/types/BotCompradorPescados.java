package com.srth.plugin.bots.types;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredHandler;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.plugin.events.bots.BotServerItemEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.ConfirmEvents.confirmevents.ConfirmEventsManager;
import com.srth.plugin.habbo.Inventorys.ItemUsuario;

import gnu.trove.map.hash.THashMap;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BotCompradorPescados extends Bot {

    public static THashMap<Set<String>, Integer> serverKeywords = new THashMap<>();

    public BotCompradorPescados(ResultSet set) throws SQLException {
        super(set);
    }

    public BotCompradorPescados(Bot bot) {
        super(bot);
    }

    public static void initialise() {
        if (serverKeywords == null)
            serverKeywords = new THashMap<>();

        serverKeywords.clear();

        JsonParser parser = new JsonParser();
        JsonArray gsonArr = parser
                .parse(RoleplayWebsocketsRoomsInfo.getConfig().getValue("pescadores_keys_json_array"))
                .getAsJsonArray();

        for (JsonElement obj : gsonArr) {
            // Object of array
            JsonObject gsonObj = obj.getAsJsonObject();
            // Primitives elements of object
            String keysString = gsonObj.get("keys").getAsString();
            int id = gsonObj.get("id").getAsInt();

            // Insertar
            String[] keys = keysString.split(";");
            Set<String> ks = new HashSet<>();
            Collections.addAll(ks, keys);
            serverKeywords.put(ks, id);
        }
    }

    public static void dispose() {
        serverKeywords.clear();
    }

    @Override
    public void onUserSay(final RoomChatMessage message) {
        if (this.getRoomUnit().hasStatus(RoomUnitStatus.MOVE) || this.getRoom() == null) {
            return;
        }

        double distanceBetweenBotAndHabbo = this.getRoomUnit().getCurrentLocation()
                .distance(message.getHabbo().getRoomUnit().getCurrentLocation());
        if (distanceBetweenBotAndHabbo <= Emulator.getConfig().getInt("hotel.bot.butler.commanddistance")) {

            if (message.getUnfilteredMessage() != null) {
                for (Map.Entry<Set<String>, Integer> set : serverKeywords.entrySet()) {
                    for (String keyword : set.getKey()) {

                        // Check if the string contains a certain keyword using a regex.
                        // If keyword = tea, teapot wouldn't trigger it.
                        if (message.getUnfilteredMessage().toLowerCase().matches("\\b" + keyword + "\\b")) {

                            // Enable plugins to cancel this event
                            BotServerItemEvent serveEvent = new BotServerItemEvent(this, message.getHabbo(),
                                    set.getValue());
                            if (Emulator.getPluginManager().fireEvent(serveEvent).isCancelled()) {
                                return;
                            }

                            // Start give handitem process
                            if (this.getRoomUnit().canWalk()) {
                                final String key = keyword;
                                final Bot bot = this;

                                // Step 1: Look at Habbo
                                bot.lookAt(serveEvent.habbo);

                                // Step 2: Prepare tasks for when the Bot (carrying the handitem) reaches the
                                // Habbo
                                final List<Runnable> tasks = new ArrayList<>();

                                switch (keyword) {
                                    case "hola":
                                        tasks.add(() -> {
                                            if (this.getRoom() != null) {
                                                String botMessage = RoleplayWebsocketsRoomsInfo.getTexts()
                                                        .getValue("keys_botCompradorPescados_hola")
                                                        .replace("%username%",
                                                                serveEvent.habbo.getHabboInfo().getUsername());
                                                if (!WiredHandler.handle(WiredTriggerType.SAY_SOMETHING,
                                                        this.getRoomUnit(), this.getRoom(),
                                                        new Object[] { botMessage })) {
                                                    bot.talk(botMessage);
                                                }
                                            }
                                        });
                                        break;
                                    case "comprar":
                                        tasks.add(() -> {
                                            if (this.getRoom() != null) {
                                                String botMessage = RoleplayWebsocketsRoomsInfo.getTexts()
                                                        .getValue("keys_botCompradorPescados_comprar")
                                                        .replace("%username%",
                                                                serveEvent.habbo.getHabboInfo().getUsername());
                                                if (!WiredHandler.handle(WiredTriggerType.SAY_SOMETHING,
                                                        this.getRoomUnit(), this.getRoom(),
                                                        new Object[] { botMessage })) {
                                                    bot.talk(botMessage);
                                                }
                                            }
                                        });

                                        tasks.add(() -> {
                                            if (this.getRoom() != null) {
                                                try {
                                                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                                            .getHabboRp(serveEvent.habbo);

                                                    String estructura = "item_id:%item_id%;";
                                                    String total_string = estructura.replace("%item_id%",
                                                            bot.getId() + "");

                                                    if (habboRp.checkSettings("global_confirm_data",
                                                            total_string) != "none") {
                                                        habboRp.update("global_confirm_data", total_string);
                                                    }

                                                    String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig()
                                                            .getValue("global.url.hotel.base");
                                                    String global_url_images = RoleplayWebsocketsRoomsInfo.getConfig()
                                                            .getValue("global_url_images")
                                                            .replace("%url.hotel.base%", url_hotel_base);
                                                    String url_images = global_url_images + "/items/";

                                                    ConfirmEventsManager confirmEventsManager = new ConfirmEventsManager();
                                                    confirmEventsManager.setHabbo(serveEvent.habbo);
                                                    confirmEventsManager.showVentSelect("buy_canaspescar", 1, true,
                                                            serveEvent.habbo.getHabboInfo().getCurrencyAmount(2),
                                                            url_images, true);
                                                } catch (Exception e) {
                                                }
                                            }
                                        });
                                        break;
                                    case "vender":
                                        tasks.add(() -> {
                                            if (this.getRoom() != null) {
                                                try {

                                                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                                            .getHabboRp(serveEvent.habbo);

                                                    ItemUsuario item = habboRp.getNewInventoryHabbo().getItem("pez");
                                                    int cantidad_items = 0;
                                                    if (item != null) {
                                                        cantidad_items = item.getCantidad();
                                                    }

                                                    if (cantidad_items == 0) {
                                                        bot.talk(RoleplayWebsocketsRoomsInfo.getTexts()
                                                                .getValue("keys_botCompradorPescados_empty")
                                                                .replace("%username%",
                                                                        serveEvent.habbo.getHabboInfo().getUsername()));
                                                        return;
                                                    }
                                                    int operacion = cantidad_items / 2;

                                                    String botMessage = RoleplayWebsocketsRoomsInfo.getTexts()
                                                            .getValue("keys_botCompradorPescados_vender")
                                                            .replace("%username%",
                                                                    serveEvent.habbo.getHabboInfo().getUsername())
                                                            .replace("%cantidad%", cantidad_items + "")
                                                            .replace("%amount%",
                                                                    operacion + " " + RoleplayWebsocketsRoomsInfo
                                                                            .getTexts().getValue("seasonal.name.rol"));
                                                    if (!WiredHandler.handle(WiredTriggerType.SAY_SOMETHING,
                                                            this.getRoomUnit(), this.getRoom(),
                                                            new Object[] { botMessage })) {
                                                        bot.talk(botMessage);
                                                    }

                                                } catch (Exception e) {
                                                }
                                            }
                                        });
                                        break;
                                    case "si":
                                        tasks.add(() -> {
                                            if (this.getRoom() != null) {
                                                try {

                                                    HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                                            .getHabboRp(serveEvent.habbo);

                                                    ItemUsuario item = habboRp.getNewInventoryHabbo().getItem("pez");
                                                    int cantidad_items = 0;
                                                    if (item != null) {
                                                        cantidad_items = item.getCantidad();
                                                    }
                                                    if (cantidad_items == 0) {
                                                        return;
                                                    }
                                                    int operacion = cantidad_items / 2;

                                                    if (operacion <= 0) {
                                                        bot.talk(RoleplayWebsocketsRoomsInfo.getTexts()
                                                                .getValue("keys_botCompradorPescados_empty")
                                                                .replace("%username%",
                                                                        serveEvent.habbo.getHabboInfo().getUsername()));
                                                        return;
                                                    }

                                                    habboRp.getNewInventoryHabbo().removeCantidadItem("pez",
                                                            cantidad_items);
                                                    habboRp.givePoints(operacion);

                                                    // EVENTO DE PESCAR
                                                    RoleplayWebsocketsRoomsInfo.getTutorialHabboRpManager().getEventos()
                                                            .eventPescarPecesLago(serveEvent.habbo);

                                                    bot.talk(RoleplayWebsocketsRoomsInfo.getTexts()
                                                            .getValue("keys_botCompradorPescados_comprados"));
                                                } catch (Exception e) {
                                                }
                                            }
                                        });
                                        break;
                                    default:
                                        tasks.add(() -> {
                                            if (this.getRoom() != null) {
                                                String botMessage = Emulator.getTexts()
                                                        .getValue("bots.butler.given")
                                                        .replace("%key%", key)
                                                        .replace("%username%",
                                                                serveEvent.habbo.getHabboInfo().getUsername());
                                                if (!WiredHandler.handle(WiredTriggerType.SAY_SOMETHING,
                                                        this.getRoomUnit(), this.getRoom(),
                                                        new Object[] { botMessage })) {
                                                    bot.talk(botMessage);
                                                }
                                            }
                                        });
                                        break;
                                }

                                List<Runnable> failedReached = new ArrayList<>();
                                failedReached.add(() -> {
                                    if (distanceBetweenBotAndHabbo <= Emulator.getConfig()
                                            .getInt("hotel.bot.butler.servedistance", 8)) {
                                        for (Runnable task : tasks) {
                                            task.run();
                                        }
                                    }
                                });

                                if (distanceBetweenBotAndHabbo > Emulator.getConfig()
                                        .getInt("hotel.bot.butler.reachdistance", 3)) {

                                } else {
                                    Emulator.getThreading().run(failedReached.get(0), 500);
                                }

                            } else {
                                if (this.getRoom() != null) {

                                    String msg = "";
                                    switch (keyword) {
                                        case "hola":
                                            msg = RoleplayWebsocketsRoomsInfo.getTexts()
                                                    .getValue("keys_botCompradorPescados_hola").replace("%username%",
                                                            serveEvent.habbo.getHabboInfo().getUsername());
                                            break;
                                        default:
                                            msg = Emulator.getTexts().getValue("bots.butler.given")
                                                    .replace("%key%", keyword).replace("%username%",
                                                            serveEvent.habbo.getHabboInfo().getUsername());
                                            break;
                                    }

                                    if (!WiredHandler.handle(WiredTriggerType.SAY_SOMETHING, this.getRoomUnit(),
                                            this.getRoom(), new Object[] { msg })) {
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

    private int mensaje = 0;
    private int timer = 0;

    @Override
    public void cycle(boolean allowBotsWalk) {
        super.cycle(allowBotsWalk);

        timer++;

        if (timer >= 120) {
            timer = 0;

            if (mensaje != 0) {
                mensaje = 0;
            } else {
                mensaje = 1;
            }

            final Bot bot = this;
            String[] mensaje_aleatorio = RoleplayWebsocketsRoomsInfo.getTexts()
                    .getValue("keys_botCompradorPescados_msg_actions").split(";");
            bot.talk(mensaje_aleatorio[mensaje]);
        }
    }

    public void onUserEnter(Habbo habbo) {
        if (habbo.getHabboInfo().getCurrentRoom() != null) {
            final Bot bot = this;
            Emulator.getThreading().run(() -> {
                try {
                    bot.whisper(RoleplayWebsocketsRoomsInfo.getTexts().getValue("keys_botCompradorPescados_visitor")
                            .replace("%username%", habbo.getHabboInfo().getUsername()), habbo);
                } catch (Exception e) {
                }
            }, 1000);
        }
    }

}