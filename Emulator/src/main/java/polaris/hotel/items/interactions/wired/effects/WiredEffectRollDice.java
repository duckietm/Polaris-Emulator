package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.WiredPlatform;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDice;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomDiceDisableSupport;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.threading.runnables.RandomDiceNumber;

/**
 * "Roll dice" ({@code wf_act_roll_dice}): every resolved dice rolls as if a user next to it had
 * clicked it, so the dice-rolled trigger and the dice value follow as usual. Uses the toggle-state
 * dialog, which is a furni picker with a furni source and nothing else. One int: the furni source.
 */
public class WiredEffectRollDice extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.TOGGLE_STATE;
    private static final long ROLL_DELAY_MS = 1500L;

    /** How a dice is rolled; a seam so a test can watch which dice rolled without the threading. */
    interface DiceRoller {
        void roll(UserItem dice, Room room);
    }

    private int furniSource = WiredSourceUtil.SOURCE_SELECTED;
    private final List<Integer> furniIds = new ArrayList<>();
    private DiceRoller roller = WiredEffectRollDice::rollLikeAClick;

    public WiredEffectRollDice(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectRollDice(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    void roller(DiceRoller roller) {
        this.roller = roller;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx == null ? null : ctx.room();
        if (room == null) return;

        for (UserItem item : WiredSourceUtil.resolveItems(ctx, this.furniSource, this.selectedItems(room))) {
            if (item instanceof InteractionDice) this.roller.roll(item, room);
        }
    }

    private static void rollLikeAClick(UserItem dice, Room room) {
        if (RoomDiceDisableSupport.isActive(room)) return;
        if ("-1".equalsIgnoreCase(dice.getExtradata())) return; // already rolling

        dice.setExtradata("-1");
        room.updateItemState(dice);
        WiredPlatform.threading()
                .run(new RandomDiceNumber(dice, room, dice.getBaseItem().getStateCount()), ROLL_DELAY_MS);
    }

    private List<UserItem> selectedItems(Room room) {
        List<UserItem> items = new ArrayList<>();
        for (Integer id : this.furniIds) {
            UserItem item = room.getUserItem(id);
            if (item != null) items.add(item);
        }
        return items;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.furniSource = (params != null && params.length > 0) ? params[0] : WiredSourceUtil.SOURCE_SELECTED;
        this.furniIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) this.furniIds.add(id);
        }
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.getDelay(), this.furniSource, new ArrayList<>(this.furniIds)));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.setDelay(data.delay);
        this.furniSource = data.furniSource;
        if (data.furniIds != null) this.furniIds.addAll(data.furniIds);
    }

    @Override
    public void onPickUp() {
        this.furniSource = WiredSourceUtil.SOURCE_SELECTED;
        this.furniIds.clear();
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.furniIds.size());
        for (Integer id : this.furniIds) message.appendInt(id);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.furniSource);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    static class JsonData {
        int delay;
        int furniSource;
        List<Integer> furniIds;

        JsonData(int delay, int furniSource, List<Integer> furniIds) {
            this.delay = delay;
            this.furniSource = furniSource;
            this.furniIds = furniIds;
        }
    }
}
