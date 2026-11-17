package com.srth.plugin.ActionUsers;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class RoleplayAvatarMenuManager {
    public static final int TARGET_USER = 1;
    public static final int TARGET_BOT = 2;
    private static final String DEFAULT_BOT_MENU = "*";
    private static final String BOT_GREET_ACTION = "bot_greet";
    private static final String POLICE_SUBMENU_ACTION = "roleplay_submenu_police";
    private static final Set<String> POLICE_ACTIONS = Set.of(
            "UserActionRevisarPertenencias",
            "UserActionViewOtherExpediente",
            "UserActionArrestar",
            "LlevarUsuarioEstacionPolicial",
            "UserActionExitOtherPrision");

    private final Map<String, RoleplayMenu> botMenus = new ConcurrentHashMap<>();
    private final Map<String, BotAction> botActions = new ConcurrentHashMap<>();

    public RoleplayAvatarMenuManager() {
        this.registerBotAction(BOT_GREET_ACTION, (viewer, bot) -> bot.talk("¡Hola!"));
        this.registerBotContextMenu(
                DEFAULT_BOT_MENU,
                new RoleplayMenu("Acciones", List.of(new RoleplayMenu.Button("Saludar", BOT_GREET_ACTION, true))));
    }

    public boolean isRoleplayRoom(Room room) {
        return room != null
                && RoleplayWebsocketsRoomsInfo.getConfig() != null
                && room.getCategory()
                        == RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");
    }

    public RoleplayMenu getUserContextMenu(Habbo target, Habbo viewer) throws SQLException {
        String title = target == null ? "Acciones" : target.getHabboInfo().getUsername();
        if (target == null || viewer == null || target.equals(viewer)) {
            return new RoleplayMenu(title, List.of());
        }

        JsonArray buttons = new ManagerClickHabbos().getArrayButtonsUsedForPlayer(target, viewer);
        return buildUserMenu(title, buttons);
    }

    public Menu getUserMenu(Habbo target, Habbo viewer) throws SQLException {
        String title = target == null ? "Acciones" : target.getHabboInfo().getUsername();
        if (target == null || viewer == null || target.equals(viewer)) {
            return new Menu(title, List.of());
        }

        JsonArray buttons = new ManagerClickHabbos().getArrayButtonsUsedForPlayer(target, viewer);
        return new Menu(
                title,
                parseUserButtons(buttons).stream()
                        .map(button -> new Button(button.label(), button.actionId(), button.enabled()))
                        .toList());
    }

    static RoleplayMenu buildUserMenu(String title, JsonArray buttons) {
        if (buttons == null) return new RoleplayMenu(title, List.of());

        return new RoleplayMenu(title, parseUserButtons(buttons))
                .withSubmenuForActions(
                        "Policía", POLICE_SUBMENU_ACTION, button -> POLICE_ACTIONS.contains(button.actionId()));
    }

    private static List<RoleplayMenu.Button> parseUserButtons(JsonArray buttons) {
        if (buttons == null) return List.of();

        return buttons.asList().stream()
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(button -> button.has("use_btn_name") && button.has("event") && button.has("unlock"))
                .map(button -> new RoleplayMenu.Button(
                        button.get("use_btn_name").getAsString(),
                        button.get("event").getAsString(),
                        button.get("unlock").getAsInt() == 1))
                .toList();
    }

    public Menu getBotMenu(Bot bot) {
        RoleplayMenu menu = this.getBotContextMenu(bot);
        if (menu == null) return null;

        return new Menu(
                menu.title(),
                menu.buttons().stream()
                        .map(button -> new Button(button.label(), button.actionId(), button.enabled()))
                        .toList());
    }

    public RoleplayMenu getBotContextMenu(Bot bot) {
        if (bot == null || bot.getName() == null || bot.getName().isBlank()) return null;

        RoleplayMenu menu = this.botMenus.get(normalizeName(bot.getName()));
        if (menu == null) menu = this.botMenus.get(DEFAULT_BOT_MENU);

        return menu == null ? null : new RoleplayMenu(bot.getName(), menu.buttons());
    }

    public boolean executeBotAction(Habbo viewer, Bot bot, String actionId) {
        if (bot == null || actionId == null || actionId.isBlank()) return false;

        RoleplayMenu menu = this.getBotContextMenu(bot);
        if (menu == null || !menu.containsEnabledAction(actionId)) return false;

        BotAction action = this.botActions.get(actionId);
        if (action == null) return false;

        action.execute(viewer, bot);
        return true;
    }

    public void registerBotMenu(String botName, Menu menu) {
        Objects.requireNonNull(menu);
        this.registerBotContextMenu(
                botName,
                new RoleplayMenu(
                        menu.title(),
                        menu.buttons().stream()
                                .map(button ->
                                        new RoleplayMenu.Button(button.label(), button.actionId(), button.enabled()))
                                .toList()));
    }

    public void registerBotContextMenu(String botName, RoleplayMenu menu) {
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

    public record RoleplayMenu(String title, List<RoleplayMenu.Button> buttons) {
        private static final String NAVIGATION_PREFIX = "__polaris_menu_nav__:";
        private static final int MAX_NAVIGATION_DEPTH = 32;

        public RoleplayMenu {
            if (title == null || title.isBlank()) throw new IllegalArgumentException("Menu title must not be blank.");
            buttons = List.copyOf(buttons);
            if (buttons.size() > 100) throw new IllegalArgumentException("Menu cannot contain more than 100 buttons.");
        }

        public RoleplayMenu withSubmenuForActions(String label, String actionId, Predicate<Button> actionFilter) {
            if (label == null || label.isBlank())
                throw new IllegalArgumentException("Menu button label must not be blank.");
            if (actionId == null || actionId.isBlank())
                throw new IllegalArgumentException("Menu button action ID must not be blank.");
            Objects.requireNonNull(actionFilter, "Action filter must not be null.");

            List<Button> submenuButtons = new ArrayList<>();
            List<Button> rootButtons = new ArrayList<>();
            for (Button button : this.buttons()) {
                if (actionFilter.test(button)) {
                    submenuButtons.add(button);
                } else {
                    rootButtons.add(button);
                }
            }

            if (submenuButtons.isEmpty()) return this;

            rootButtons.add(new Button(label, actionId, true, new RoleplayMenu(label, submenuButtons)));
            return new RoleplayMenu(this.title(), rootButtons);
        }

        public RoleplayMenu page(int requestId, List<String> path) {
            if (requestId <= 0 || path == null || path.size() > MAX_NAVIGATION_DEPTH) return null;

            RoleplayMenu current = this;
            for (String actionId : path) {
                if (actionId == null || actionId.isBlank()) return null;

                Button submenuButton = current.buttons().stream()
                        .filter(button ->
                                button.enabled() && button.actionId().equals(actionId) && button.submenu() != null)
                        .findFirst()
                        .orElse(null);
                if (submenuButton == null) return null;
                current = submenuButton.submenu();
            }

            List<Button> pageButtons = new ArrayList<>(current.buttons().size() + (path.isEmpty() ? 0 : 1));
            for (Button button : current.buttons()) {
                String actionId = button.submenu() == null
                        ? button.actionId()
                        : navigationAction(requestId, append(path, button.actionId()));
                pageButtons.add(new Button(button.label(), actionId, button.enabled(), button.visibleIf(), null));
            }

            if (!path.isEmpty()) {
                pageButtons.add(new Button(
                        "Volver", navigationAction(requestId, path.subList(0, path.size() - 1)), true));
            }

            return new RoleplayMenu(current.title(), pageButtons);
        }

        public RoleplayMenu visibleTo(Habbo viewer) {
            List<Button> visibleButtons = new ArrayList<>();
            for (Button button : this.buttons()) {
                if (!button.visibleIf().test(viewer)) continue;

                RoleplayMenu submenu =
                        button.submenu() == null ? null : button.submenu().visibleTo(viewer);
                if (submenu != null && submenu.buttons().isEmpty()) continue;

                visibleButtons.add(
                        new Button(button.label(), button.actionId(), button.enabled(), button.visibleIf(), submenu));
            }

            return new RoleplayMenu(this.title(), visibleButtons);
        }

        public boolean containsEnabledAction(String actionId) {
            return this.buttons().stream()
                    .anyMatch(button -> button.enabled()
                            && (button.submenu() == null
                                    ? button.actionId().equals(actionId)
                                    : button.submenu().containsEnabledAction(actionId)));
        }

        public static boolean isNavigationAction(String actionId) {
            return actionId != null && actionId.startsWith(NAVIGATION_PREFIX);
        }

        public static Optional<Navigation> parseNavigation(String actionId) {
            if (!isNavigationAction(actionId)) return Optional.empty();

            String encodedNavigation = actionId.substring(NAVIGATION_PREFIX.length());
            int separator = encodedNavigation.indexOf(':');
            if (separator <= 0) return Optional.empty();

            try {
                int requestId = Integer.parseInt(encodedNavigation.substring(0, separator));
                if (requestId <= 0) return Optional.empty();

                String encodedPath = encodedNavigation.substring(separator + 1);
                List<String> path = new ArrayList<>();
                if (!encodedPath.isEmpty()) {
                    for (String encodedSegment : encodedPath.split("\\.", -1)) {
                        if (encodedSegment.isEmpty()) return Optional.empty();
                        path.add(new String(Base64.getUrlDecoder().decode(encodedSegment), StandardCharsets.UTF_8));
                    }
                }

                if (path.size() > MAX_NAVIGATION_DEPTH) return Optional.empty();
                return Optional.of(new Navigation(requestId, path));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        private static String navigationAction(int requestId, List<String> path) {
            String encodedPath = path.stream()
                    .map(segment -> Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(segment.getBytes(StandardCharsets.UTF_8)))
                    .reduce((left, right) -> left + "." + right)
                    .orElse("");
            return NAVIGATION_PREFIX + requestId + ":" + encodedPath;
        }

        private static List<String> append(List<String> path, String actionId) {
            List<String> result = new ArrayList<>(path.size() + 1);
            result.addAll(path);
            result.add(actionId);
            return result;
        }

        public record Button(
                String label, String actionId, boolean enabled, Predicate<Habbo> visibleIf, RoleplayMenu submenu) {
            public Button(String label, String actionId, boolean enabled) {
                this(label, actionId, enabled, habbo -> true, null);
            }

            public Button(String label, String actionId, boolean enabled, RoleplayMenu submenu) {
                this(label, actionId, enabled, habbo -> true, submenu);
            }

            public Button(String label, String actionId, boolean enabled, Predicate<Habbo> visibleIf) {
                this(label, actionId, enabled, visibleIf, null);
            }

            public Button {
                if (label == null || label.isBlank())
                    throw new IllegalArgumentException("Button label must not be blank.");
                if (actionId == null || actionId.isBlank())
                    throw new IllegalArgumentException("Action ID must not be blank.");
                if (visibleIf == null) throw new NullPointerException("Button visibility predicate must not be null.");
            }
        }

        public record Navigation(int requestId, List<String> path) {
            public Navigation {
                if (requestId <= 0) throw new IllegalArgumentException("Request ID must be positive.");
                path = List.copyOf(path);
            }
        }
    }
}
