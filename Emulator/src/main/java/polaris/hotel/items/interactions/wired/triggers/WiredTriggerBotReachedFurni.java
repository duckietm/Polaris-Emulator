package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredTriggerType;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredTriggerSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialSources;
import polaris.messages.ServerMessage;

public class WiredTriggerBotReachedFurni extends InteractionWiredTrigger implements WiredOfficialSources {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredTriggerBotReachedFurni.class);
    private static final int BOT_SOURCE_NAME = 100;
    private static final int BOT_SOURCE_SELECTOR = 200;

    public static final WiredTriggerType type = WiredTriggerType.BOT_REACHED_STF;

    private final Set<UserItem> items;
    private String botName = "";
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = BOT_SOURCE_NAME;

    public WiredTriggerBotReachedFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredTriggerBotReachedFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        Set<UserItem> items = new HashSet<>();

        if (Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId()) == null) {
            items.addAll(this.items);
        } else {
            for (UserItem item : this.items) {
                if (Emulator.getGameEnvironment()
                                .getRoomManager()
                                .getRoom(this.getRoomId())
                                .getUserItem(item.getId())
                        == null) items.add(item);
            }
        }

        for (UserItem item : items) {
            this.items.remove(item);
        }

        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.items.size());
        for (UserItem item : this.items) {
            message.appendInt(item.getId());
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects(this.getX(), this.getY())) {
                if (effect.requiresTriggeringUser()) {
                    invalidTriggers.add(effect.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.furniSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.botSource};
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.botName = settings.getStringParam();
        // Official: no int params; the furni and the bot source are the official source slots.
        int[] furni = settings.getFurniSourceTypes();
        int[] users = settings.getUserSourceTypes();
        this.furniSource = (furni.length > 0)
                ? this.normalizeFurniSource(furni[0])
                : ((settings.getFurniIds().length > 0)
                        ? WiredSourceUtil.SOURCE_SELECTED
                        : WiredSourceUtil.SOURCE_TRIGGER);
        this.botSource = (users.length > 0) ? this.normalizeBotSource(users[0]) : BOT_SOURCE_NAME;

        this.items.clear();
        // Picked furni mean the picks, as in the official editor, which has no furni source on a trigger.
        if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && settings.getFurniIds().length > 0) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        int count = settings.getFurniIds().length;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) return false;

        for (int i = 0; i < count; i++) {
            UserItem item = room.getUserItem(settings.getFurniIds()[i]);
            if (item != null) {
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        RoomUnit roomUnit = event.getActor().orElse(null);
        Room room = event.getRoom();

        // Get the furniture item the bot walked onto
        UserItem sourceItem = event.getSourceItem().orElse(null);
        if (sourceItem == null || roomUnit == null || room == null) {
            return false;
        }

        if (!this.matchesBotSource(event, roomUnit, room) || !isCorrectBotForName(roomUnit, room)) {
            return false;
        }

        return WiredTriggerSourceUtil.containsItemOrTile(
                room, WiredTriggerSourceUtil.resolveItems(this, event, this.furniSource, this.items), sourceItem);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.botName,
                        this.furniSource,
                        this.botSource,
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.botName = data.botName;
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            this.botSource = this.normalizeBotSource(data.botSource);
            for (Integer id : data.itemIds) {
                UserItem item = room.getUserItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }

            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] data = wiredData.split(":");

            if (data.length == 1) {
                this.botName = data[0];
            } else if (data.length == 2) {
                this.botName = data[0];

                String[] items = data[1].split(";");

                for (String id : items) {
                    try {
                        UserItem item = room.getUserItem(Integer.parseInt(id));

                        if (item != null) this.items.add(item);
                    } catch (Exception e) {
                        LOGGER.error("Caught exception", e);
                    }
                }
            }

            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.botSource = BOT_SOURCE_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.botName = "";
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = BOT_SOURCE_NAME;
    }

    static class JsonData {
        String botName;
        int furniSource;
        int botSource;
        List<Integer> itemIds;

        public JsonData(String botName, int furniSource, int botSource, List<Integer> itemIds) {
            this.botName = botName;
            this.furniSource = furniSource;
            this.botSource = botSource;
            this.itemIds = itemIds;
        }
    }

    private boolean matchesBotSource(WiredEvent event, RoomUnit roomUnit, Room room) {
        if (this.botSource == BOT_SOURCE_SELECTOR) {
            return WiredTriggerSourceUtil.containsUser(
                    WiredTriggerSourceUtil.resolveUsers(this, event, WiredSourceUtil.SOURCE_SELECTOR, null), roomUnit);
        }

        return true;
    }

    private boolean isCorrectBotForName(RoomUnit roomUnit, Room room) {
        if (this.botSource != BOT_SOURCE_NAME) {
            return true;
        }

        return room.getBots(this.botName).stream().anyMatch(bot -> bot.getRoomUnit() == roomUnit);
    }

    private int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTED || value == WiredSourceUtil.SOURCE_SELECTOR) {
            return value;
        }

        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    private int normalizeBotSource(int value) {
        return (value == BOT_SOURCE_SELECTOR) ? BOT_SOURCE_SELECTOR : BOT_SOURCE_NAME;
    }
}
