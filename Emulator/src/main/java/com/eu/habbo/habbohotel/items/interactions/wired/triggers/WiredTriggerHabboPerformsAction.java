package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerHabboPerformsAction extends InteractionWiredTrigger {
    private static final WiredTriggerType type = WiredTriggerType.USER_PERFORMS_ACTION;
    private static final int DEFAULT_ACTION = WiredUserActionType.WAVE;

    private int selectedAction = DEFAULT_ACTION;
    private boolean signFilterEnabled = false;
    private int signId = 0;
    private boolean danceFilterEnabled = false;
    private int danceId = 1;

    public WiredTriggerHabboPerformsAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerHabboPerformsAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
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
        return WiredManager.getGson().toJson(new JsonData(
                this.selectedAction,
                this.signFilterEnabled,
                this.signId,
                this.danceFilterEnabled,
                this.danceId
        ));
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

            this.selectedAction = normalizeAction(data.selectedAction);
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
        message.appendString("");
        message.appendInt(5);
        message.appendInt(this.selectedAction);
        message.appendInt(this.signFilterEnabled ? 1 : 0);
        message.appendInt(this.signId);
        message.appendInt(this.danceFilterEnabled ? 1 : 0);
        message.appendInt(this.danceId);
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

        if (intParams.length > 1) {
            this.signFilterEnabled = (intParams[1] == 1);
        }

        if (intParams.length > 2) {
            this.signId = normalizeSignId(intParams[2]);
        }

        if (intParams.length > 3) {
            this.danceFilterEnabled = (intParams[3] == 1);
        }

        if (intParams.length > 4) {
            this.danceId = normalizeDanceId(intParams[4]);
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
        switch (action) {
            case WiredUserActionType.WAVE:
            case WiredUserActionType.BLOW_KISS:
            case WiredUserActionType.LAUGH:
            case WiredUserActionType.AWAKE:
            case WiredUserActionType.RELAX:
            case WiredUserActionType.SIT:
            case WiredUserActionType.STAND:
            case WiredUserActionType.LAY:
            case WiredUserActionType.SIGN:
            case WiredUserActionType.DANCE:
            case WiredUserActionType.THUMB_UP:
                return action;
            default:
                return DEFAULT_ACTION;
        }
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
        int selectedAction;
        boolean signFilterEnabled;
        int signId;
        boolean danceFilterEnabled;
        int danceId;

        public JsonData(int selectedAction, boolean signFilterEnabled, int signId, boolean danceFilterEnabled, int danceId) {
            this.selectedAction = selectedAction;
            this.signFilterEnabled = signFilterEnabled;
            this.signId = signId;
            this.danceFilterEnabled = danceFilterEnabled;
            this.danceId = danceId;
        }
    }
}
