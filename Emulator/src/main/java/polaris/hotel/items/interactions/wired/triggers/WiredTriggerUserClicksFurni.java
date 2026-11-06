package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.Emulator;
import polaris.WiredCompatibilityDiagnostics;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
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
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredTriggerSaveException;

public class WiredTriggerUserClicksFurni extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.CLICKS_FURNI;

    protected final Set<UserItem> items;
    protected int furniSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredTriggerUserClicksFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.items = new LinkedHashSet<>();
    }

    public WiredTriggerUserClicksFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.items = new LinkedHashSet<>();
    }

    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        UserItem sourceItem = event.getSourceItem().orElse(null);
        if (sourceItem == null) {
            return false;
        }

        return this.matchesSourceItem(this.resolveCandidateItems(triggerItem, event), sourceItem);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
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
                        == null) {
                    items.add(item);
                }
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
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        return this.saveData(settings, null);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        this.items.clear();
        // Official: no int params; the furni source is the official source slot.
        this.furniSource = (settings.getFurniSourceTypes().length > 0)
                ? this.normalizeFurniSource(settings.getFurniSourceTypes()[0])
                : ((settings.getFurniIds().length > 0)
                        ? WiredSourceUtil.SOURCE_SELECTED
                        : WiredSourceUtil.SOURCE_TRIGGER);

        // Picked furni mean the picks, as in the official editor, which has no furni source on a trigger.
        if (this.furniSource == WiredSourceUtil.SOURCE_TRIGGER && settings.getFurniIds().length > 0) {
            this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        }

        if (this.furniSource != WiredSourceUtil.SOURCE_SELECTED) {
            return true;
        }

        int count = settings.getFurniIds().length;
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());
        if (room == null) {
            return false;
        }

        for (int i = 0; i < count; i++) {
            UserItem item = room.getUserItem(settings.getFurniIds()[i]);
            if (item != null) {
                if (!this.isSelectableItem(item)) {
                    throw new WiredTriggerSaveException(this.getInvalidSelectionErrorKey());
                }
                this.items.add(item);
            }
        }

        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(
                        this.furniSource,
                        this.items.stream().map(UserItem::getId).collect(Collectors.toList())));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.furniSource = this.normalizeFurniSource(data.furniSource);
            for (Integer id : data.itemIds) {
                UserItem item = room.getUserItem(id);
                if (item != null) {
                    this.items.add(item);
                }
            }
        } else {
            if (wiredData.split(":").length >= 3) {
                super.setDelay(Integer.parseInt(wiredData.split(":")[0]));

                if (!wiredData.split(":")[2].equals("\t")) {
                    for (String s : wiredData.split(":")[2].split(";")) {
                        if (s.isEmpty()) {
                            continue;
                        }

                        try {
                            UserItem item = room.getUserItem(Integer.parseInt(s));

                            if (item != null) {
                                this.items.add(item);
                            }
                        } catch (Exception e) {
                            WiredCompatibilityDiagnostics.record(
                                    WiredCompatibilityDiagnostics.FailurePoint.TRIGGER_CLICK_ITEM,
                                    room.getId(),
                                    this.getId(),
                                    e);
                        }
                    }
                }
            }

            this.furniSource = this.items.isEmpty() ? WiredSourceUtil.SOURCE_TRIGGER : WiredSourceUtil.SOURCE_SELECTED;
        }
    }

    @Override
    public void onPickUp() {
        this.items.clear();
        this.furniSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        int furniSource;
        List<Integer> itemIds;

        public JsonData(int furniSource, List<Integer> itemIds) {
            this.furniSource = furniSource;
            this.itemIds = itemIds;
        }
    }

    protected boolean isSelectableItem(UserItem item) {
        return item != null;
    }

    protected String getInvalidSelectionErrorKey() {
        return "There was an error while saving that trigger";
    }

    protected int normalizeFurniSource(int value) {
        if (value == WiredSourceUtil.SOURCE_SELECTED || value == WiredSourceUtil.SOURCE_SELECTOR) {
            return value;
        }

        return WiredSourceUtil.SOURCE_TRIGGER;
    }

    private Iterable<UserItem> resolveCandidateItems(UserItem triggerItem, WiredEvent event) {
        switch (this.furniSource) {
            case WiredSourceUtil.SOURCE_SELECTED:
                return this.items;
            case WiredSourceUtil.SOURCE_SELECTOR:
                return WiredTriggerSourceUtil.resolveItems(this, event, WiredSourceUtil.SOURCE_SELECTOR, this.items);
            case WiredSourceUtil.SOURCE_TRIGGER:
            default:
                return (triggerItem != null)
                        ? java.util.Collections.singletonList(triggerItem)
                        : java.util.Collections.emptyList();
        }
    }

    private boolean matchesSourceItem(Iterable<UserItem> candidateItems, UserItem sourceItem) {
        if (candidateItems == null || sourceItem == null) {
            return false;
        }

        for (UserItem item : candidateItems) {
            if (item != null && item.getId() == sourceItem.getId()) {
                return true;
            }
        }

        return false;
    }
}
