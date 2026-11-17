package com.srth.plugin.ActionUsers;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class RoleplayAvatarMenuManager {
    public static final int TARGET_USER = 1;
    public static final int TARGET_BOT = 2;
    private static final String DEFAULT_BOT_MENU = "*";
    private static final String BOT_GREET_ACTION = "bot_greet";

    private final Map<String, Menu> botMenus = new ConcurrentHashMap<>();
    private final Map<String, BotAction> botActions = new ConcurrentHashMap<>();

    public RoleplayAvatarMenuManager() {
        this.registerBotAction(BOT_GREET_ACTION, (viewer, bot) -> bot.talk("¡Hola!"));
        this.registerBotMenu(
                DEFAULT_BOT_MENU, new Menu("Acciones", List.of(new Button("Saludar", BOT_GREET_ACTION, true))));
    }

    public boolean isRoleplayRoom(Room room) {
        return room != null
                && RoleplayWebsocketsRoomsInfo.getConfig() != null
                && room.getCategory()
                        == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");
    }

    public Menu getUserMenu(Habbo target, Habbo viewer) throws SQLException {
        String title = target == null ? "Acciones" : target.getHabboInfo().getUsername();
        if (target == null || viewer == null || target.equals(viewer)) {
            return new Menu(title, List.of());
        }

        JsonArray buttons = new ManagerClickHabbos().getArrayButtonsUsedForPlayer(target, viewer);
        if (buttons == null) return new Menu(title, List.of());

        List<Button> menuButtons = buttons.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(button -> button.has("use_btn_name") && button.has("event") && button.has("unlock"))
                .map(button -> new Button(
                        button.get("use_btn_name").getAsString(),
                        button.get("event").getAsString(),
                        button.get("unlock").getAsInt() == 1))
                .toList();

        return new Menu(title, menuButtons);
    }

    public Menu getBotMenu(Bot bot) {
        if (bot == null || bot.getName() == null || bot.getName().isBlank()) return null;

        Menu menu = this.botMenus.get(normalizeName(bot.getName()));
        if (menu == null) menu = this.botMenus.get(DEFAULT_BOT_MENU);

        return menu == null ? null : new Menu(bot.getName(), menu.buttons());
    }

    public boolean executeBotAction(Habbo viewer, Bot bot, String actionId) {
        if (bot == null || actionId == null || actionId.isBlank()) return false;

        Menu menu = this.getBotMenu(bot);
        if (menu == null
                || menu.buttons().stream()
                        .noneMatch(
                                button -> button.enabled() && button.actionId().equals(actionId))) {
            return false;
        }

        BotAction action = this.botActions.get(actionId);
        if (action == null) return false;

        action.execute(viewer, bot);
        return true;
    }

    public void registerBotMenu(String botName, Menu menu) {
        String key = normalizeName(botName);
        if (key.isEmpty()) throw new IllegalArgumentException("Bot name must not be blank.");

        this.botMenus.put(key, Objects.requireNonNull(menu));
    }

    public void registerBotAction(String actionId, BotAction action) {
        if (actionId == null || actionId.isBlank()) throw new IllegalArgumentException("Action ID must not be blank.");

        this.botActions.put(actionId, Objects.requireNonNull(action));
    }

    private static String normalizeName(String botName) {
        return botName == null ? "" : botName.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface BotAction {
        void execute(Habbo viewer, Bot bot);
    }

    public record Button(String label, String actionId, boolean enabled) {
        public Button {
            if (label == null || label.isBlank()) throw new IllegalArgumentException("Button label must not be blank.");
            if (actionId == null || actionId.isBlank())
                throw new IllegalArgumentException("Action ID must not be blank.");
        }
    }

    public record Menu(String title, List<Button> buttons) {
        public Menu {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Menu title must not be blank.");
            buttons = List.copyOf(buttons);
            if (buttons.size() > 100) throw new IllegalArgumentException("Menu cannot contain more than 100 buttons.");
        }
    }
}
