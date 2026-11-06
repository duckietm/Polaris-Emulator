package polaris.hotel.items.interactions.wired.selector;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.protocol.WiredOfficialSelector;
import polaris.messages.ServerMessage;

public class WiredEffectFurniPicks extends InteractionWiredEffect implements WiredOfficialSelector {
    private static final int MAX_PICKED_FURNI = 20;

    public static final WiredEffectType type = WiredEffectType.FURNI_PICKS_SELECTOR;

    private boolean filterExisting = false;
    private boolean invert = false;
    private List<Integer> pickedFurniIds = new ArrayList<>();

    public WiredEffectFurniPicks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniPicks(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        // A picked Wired box stays selected: the builder picked it on purpose (Call Stacks through picks runs
        // that stack). Behaviour observed in plus-emulator/PlusEMU 9fa6a5cb, reimplemented.
        Set<UserItem> result = this.pickedFurniIds.stream()
                .map(room::getUserItem)
                .filter(item -> item != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        result = this.applySelectorModifiers(
                result, this.getSelectableItems(room, ctx), ctx.targets().items(), this.filterExisting, this.invert);

        ctx.targets().setItems(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();

        this.pickedFurniIds = new ArrayList<>();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                if (this.pickedFurniIds.size() >= MAX_PICKED_FURNI) break;
                this.pickedFurniIds.add(id);
            }
        }

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public boolean selectorFilter() {
        return this.filterExisting;
    }

    @Override
    public boolean selectorInvert() {
        return this.invert;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {WiredSourceUtil.SOURCE_SELECTED};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean isSelector() {
        return true;
    }

    @Override
    public boolean usesExistingSelectorTargets() {
        return this.filterExisting;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.filterExisting, this.invert, this.pickedFurniIds, this.getDelay()));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredSelectorPayloadGuard.fromJson(wiredData, JsonData.class);
        if (data == null) {
            return;
        }

        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.pickedFurniIds = (data.pickedFurniIds != null) ? data.pickedFurniIds : new ArrayList<>();
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.filterExisting = false;
        this.invert = false;
        this.pickedFurniIds = new ArrayList<>();
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(MAX_PICKED_FURNI);

        if (!this.pickedFurniIds.isEmpty()) {
            message.appendInt(this.pickedFurniIds.size());
            this.pickedFurniIds.forEach(message::appendInt);
        } else {
            message.appendInt(0);
        }

        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    static class JsonData {
        boolean filterExisting;
        boolean invert;
        List<Integer> pickedFurniIds;
        int delay;

        JsonData(boolean filterExisting, boolean invert, List<Integer> pickedFurniIds, int delay) {
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.pickedFurniIds = pickedFurniIds;
            this.delay = delay;
        }
    }
}
