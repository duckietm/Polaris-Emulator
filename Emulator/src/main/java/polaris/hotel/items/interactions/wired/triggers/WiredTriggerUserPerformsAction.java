package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredTriggerType;
import polaris.hotel.wired.WiredUserActionType;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ServerMessage;

public class WiredTriggerUserPerformsAction extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.USER_PERFORMS_ACTION;
    static final int OFFICIAL_ACTION_IDS = 2;
    private static final int DEFAULT_ACTION = WiredUserActionType.WAVE;

    private int selectedAction = DEFAULT_ACTION;
    private boolean signFilterEnabled = false;
    private int signId = 0;
    private boolean danceFilterEnabled = false;
    private int danceId = 1;

    public WiredTriggerUserPerformsAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUserPerformsAction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        if (!event.getActor().isPresent()) {
            return false;
        }

        if (event.getActionId() != this.selectedAction) {
            return false;
        }

        if (this.selectedAction == WiredUserActionType.SIGN && this.signFilterEnabled) {
            return event.getActionParameter() == this.signId;
        }

        if (this.selectedAction == WiredUserActionType.DANCE && this.danceFilterEnabled) {
            return event.getActionParameter() == this.danceId;
        }

        return true;
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
                        this.selectedAction,
                        this.signFilterEnabled,
                        this.signId,
                        this.danceFilterEnabled,
                        this.danceId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSettings();

        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

            if (data == null) {
                return;
            }

            this.selectedAction = normalizeAction(
                    data.actionIds >= OFFICIAL_ACTION_IDS
                            ? data.selectedAction
                            : WiredUserActionType.fromLegacy(data.selectedAction));
            this.signFilterEnabled = data.signFilterEnabled;
            this.signId = normalizeSignId(data.signId);
            this.danceFilterEnabled = data.danceFilterEnabled;
            this.danceId = normalizeDanceId(data.danceId);
        }
    }

    @Override
    public void onPickUp() {
        this.resetSettings();
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(WiredUserActionType.extraString(
                this.selectedAction, this.signFilterEnabled, this.signId, this.danceFilterEnabled, this.danceId));
        message.appendInt(1);
        message.appendInt(this.selectedAction);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        this.resetSettings();

        if (intParams.length > 0) {
            this.selectedAction = normalizeAction(intParams[0]);
        }

        // Official: [action], the sign or dance extra in the string param.
        int extra = WiredUserActionType.extraCode(this.selectedAction, settings.getStringParam());
        if (this.selectedAction == WiredUserActionType.SIGN && extra >= 0) {
            this.signFilterEnabled = true;
            this.signId = extra;
        }
        if (this.selectedAction == WiredUserActionType.DANCE && extra >= 0) {
            this.danceFilterEnabled = true;
            this.danceId = extra;
        }

        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    private void resetSettings() {
        this.selectedAction = DEFAULT_ACTION;
        this.signFilterEnabled = false;
        this.signId = 0;
        this.danceFilterEnabled = false;
        this.danceId = 1;
    }

    private int normalizeAction(int action) {
        return WiredUserActionType.isAction(action) ? action : DEFAULT_ACTION;
    }

    private int normalizeSignId(int signId) {
        if (signId < 0 || signId > 17) {
            return 0;
        }

        return signId;
    }

    private int normalizeDanceId(int danceId) {
        if (danceId < 1 || danceId > 4) {
            return 1;
        }

        return danceId;
    }

    static class JsonData {
        /** 2: the official action ids; rows saved before have none and use the old ids. */
        int actionIds = OFFICIAL_ACTION_IDS;

        int selectedAction;
        boolean signFilterEnabled;
        int signId;
        boolean danceFilterEnabled;
        int danceId;

        public JsonData(
                int selectedAction, boolean signFilterEnabled, int signId, boolean danceFilterEnabled, int danceId) {
            this.selectedAction = selectedAction;
            this.signFilterEnabled = signFilterEnabled;
            this.signId = signId;
            this.danceFilterEnabled = danceFilterEnabled;
            this.danceId = danceId;
        }
    }
}
