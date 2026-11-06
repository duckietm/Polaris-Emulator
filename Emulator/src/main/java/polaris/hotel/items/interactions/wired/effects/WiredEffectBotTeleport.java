package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredBotSourceUtil;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialSources;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

public class WiredEffectBotTeleport extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.BOT_TELEPORT;

    private Set<UserItem> items;
    private String botName = "";
    private int furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;

    public WiredEffectBotTeleport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new CopyOnWriteArraySet<>();
    }

    public WiredEffectBotTeleport(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new CopyOnWriteArraySet<>();
    }

    /**
     * Bot Teleport uses the same steps as a user's normal Teleport To: the room's 500 ms grid, effect 4 and the
     * landing next to a blocked target (north first). Behaviour observed in plus-emulator/PlusEMU 58a2f279,
     * reimplemented.
     */
    public static void teleportUnitToTile(RoomUnit roomUnit, RoomTile tile) {
        WiredEffectTeleport.teleportUnitToTile(roomUnit, tile, false);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        List<UserItem> itemsSnapshot = new ArrayList<>(this.items);
        Set<UserItem> items = new HashSet<>();

        for (UserItem item : itemsSnapshot) {
            if (item.getRoomId() != this.getRoomId()
                    || Emulator.getGameEnvironment()
                                    .getRoomManager()
                                    .getRoom(this.getRoomId())
                                    .getUserItem(item.getId())
                            == null) items.add(item);
        }

        for (UserItem item : items) {
            this.items.remove(item);
        }

        itemsSnapshot = new ArrayList<>(this.items);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(itemsSnapshot.size());
        for (UserItem item : itemsSnapshot) message.appendInt(item.getId());

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
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
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String botName = settings.getStringParam();
        int[] params = settings.getIntParams();
        // Official: the bot name, no int params; the furni source and the bots (user slot 0) are the
        // official source slots.
        this.furniSource = settings.furniSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.botSource =
                WiredBotSourceUtil.normalizeBotSource(settings.userSource(0, WiredBotSourceUtil.SOURCE_BOT_NAME));
        int itemsCount = settings.getFurniIds().length;

        if (itemsCount > Emulator.getConfig().getInt("hotel.wired.furni.selection.count")) {
            throw new WiredSaveException("Too many furni selected");
        }

        if (itemsCount > 0 && this.furniSource == WiredSourceUtil.SOURCE_TRIGGER) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        List<UserItem> newItems = new ArrayList<>();

        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            for (int i = 0; i < itemsCount; i++) {
                int itemId = settings.getFurniIds()[i];
                UserItem it = Emulator.getGameEnvironment()
                        .getRoomManager()
                        .getRoom(this.getRoomId())
                        .getUserItem(itemId);

                if (it == null) throw new WiredSaveException(String.format("Item %s not found", itemId));

                newItems.add(it);
            }
        }

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.items.clear();
        if (this.furniSource == WiredSourceUtil.SOURCE_SELECTED) {
            this.items.addAll(newItems);
        }
        this.botName = botName.substring(
                0, Math.min(botName.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        List<UserItem> effectiveItems = WiredSourceUtil.resolveItems(ctx, this.furniSource, this.items);

        List<UserItem> validItems = new ArrayList<>();
        for (UserItem item : effectiveItems) {
            if (item != null && item.getRoomId() != 0) validItems.add(item);
        }

        if (validItems.isEmpty()) return;
        if (room.getLayout() == null) return;

        List<Bot> bots = WiredBotSourceUtil.resolveBots(ctx, room, this.botSource, this.botName);

        if (bots.isEmpty()) {
            return;
        }

        for (Bot bot : bots) {
            UserItem targetItem = validItems.get(Emulator.getRandom().nextInt(validItems.size()));

            if (targetItem.getRoomId() == bot.getRoom().getId()) {
                RoomTile tile = room.getLayout().getTile(targetItem.getX(), targetItem.getY());
                if (tile != null) {
                    teleportUnitToTile(bot.getRoomUnit(), tile);
                }
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        ArrayList<Integer> itemIds = new ArrayList<>();

        if (this.items != null) {
            List<UserItem> itemsSnapshot = new ArrayList<>(this.items);
            for (UserItem item : itemsSnapshot) {
                if (item.getRoomId() != 0) {
                    itemIds.add(item.getId());
                }
            }
        }

        return WiredManager.getGson()
                .toJson(new JsonData(this.botName, itemIds, this.getDelay(), this.furniSource, this.botSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items = new CopyOnWriteArraySet<>();

        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(jsonData.delay));
            this.botName = WiredEffectPayloadGuard.text(jsonData.bot_name);
            this.furniSource = WiredEffectPayloadGuard.furniSource(jsonData.furniSource);
            this.botSource = (jsonData.botSource != null)
                    ? WiredBotSourceUtil.normalizeBotSource(jsonData.botSource)
                    : WiredBotSourceUtil.SOURCE_BOT_NAME;

            if (jsonData.items != null) {
                for (int itemId : jsonData.items) {
                    UserItem item = room.getUserItem(itemId);

                    if (item != null) this.items.add(item);
                }
            }
            if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && !this.items.isEmpty()) {
                this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
            }
        } else {
            String[] wiredDataSplit = wiredData != null ? wiredData.split("\t") : new String[0];

            if (wiredDataSplit.length >= 2) {
                this.setDelay(WiredEffectPayloadGuard.parseDelay(wiredDataSplit[0]));
                String[] data = wiredDataSplit[1].split(";");

                if (data.length > 1) {
                    this.botName = WiredEffectPayloadGuard.text(data[0]);

                    for (int i = 1; i < data.length; i++) {
                        int itemId = WiredEffectPayloadGuard.parseInt(data[i], 0);
                        UserItem item = itemId > 0 ? room.getUserItem(itemId) : null;

                        if (item != null) this.items.add(item);
                    }
                }
            }

            this.needsUpdate(true);
            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
            this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return WiredBotSourceUtil.requiresTriggeringUser(this.botSource);
    }

    static class JsonData {
        String bot_name;
        List<Integer> items;
        int delay;
        int furniSource;
        Integer botSource;

        public JsonData(String bot_name, List<Integer> items, int delay, int furniSource, int botSource) {
            this.bot_name = bot_name;
            this.items = items;
            this.delay = delay;
            this.furniSource = furniSource;
            this.botSource = botSource;
        }
    }
}
