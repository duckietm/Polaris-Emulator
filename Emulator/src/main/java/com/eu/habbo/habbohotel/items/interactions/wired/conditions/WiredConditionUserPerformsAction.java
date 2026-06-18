package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionUserPerformsAction extends InteractionWiredCondition {
    private static final String CACHE_LAST_ACTION_ID = "wired.last_user_action.id";
    private static final String CACHE_LAST_ACTION_PARAMETER = "wired.last_user_action.parameter";
    private static final String CACHE_LAST_ACTION_TIMESTAMP = "wired.last_user_action.timestamp";
    private static final long TRANSIENT_ACTION_WINDOW_MS = 5_000L;
    protected static final int DEFAULT_ACTION = WiredUserActionType.WAVE;
    protected static final int QUANTIFIER_ALL = 0;
    protected static final int QUANTIFIER_ANY = 1;

    public static final WiredConditionType type = WiredConditionType.USER_PERFORMS_ACTION;

    private int selectedAction = DEFAULT_ACTION;
    private boolean signFilterEnabled = false;
    private int signId = 0;
    private boolean danceFilterEnabled = false;
    private int danceId = 1;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int quantifier = QUANTIFIER_ALL;

    public WiredConditionUserPerformsAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserPerformsAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) {
            return false;
        }

        if (this.quantifier == QUANTIFIER_ANY) {
            return targets.stream().anyMatch(roomUnit -> this.matchesAction(ctx, roomUnit));
        }

        return targets.stream().allMatch(roomUnit -> this.matchesAction(ctx, roomUnit));
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
                this.danceId,
                this.userSource,
                this.quantifier
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.resetSettings();

        String wiredData = set.getString("wired_data");

        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException ignored) {
            this.resetSettings();
            return;
        }

        if (data == null) {
            return;
        }

        this.selectedAction = normalizeAction(data.selectedAction);
        this.signFilterEnabled = data.signFilterEnabled;
        this.signId = normalizeSignId(data.signId);
        this.danceFilterEnabled = data.danceFilterEnabled;
        this.danceId = normalizeDanceId(data.danceId);
        this.userSource = this.normalizeUserSource(data.userSource);
        this.quantifier = normalizeQuantifier(data.quantifier);
    }

    @Override
    public void onPickUp() {
        this.resetSettings();
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(true);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(7);
        message.appendInt(this.selectedAction);
        message.appendInt(this.signFilterEnabled ? 1 : 0);
        message.appendInt(this.signId);
        message.appendInt(this.danceFilterEnabled ? 1 : 0);
        message.appendInt(this.danceId);
        message.appendInt(this.userSource);
        message.appendInt(this.quantifier);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] intParams = settings.getIntParams();

        this.resetSettings();

        if (intParams.length > 0) this.selectedAction = normalizeAction(intParams[0]);
        if (intParams.length > 1) this.signFilterEnabled = (intParams[1] == 1);
        if (intParams.length > 2) this.signId = normalizeSignId(intParams[2]);
        if (intParams.length > 3) this.danceFilterEnabled = (intParams[3] == 1);
        if (intParams.length > 4) this.danceId = normalizeDanceId(intParams[4]);
        if (intParams.length > 5) this.userSource = this.normalizeUserSource(intParams[5]);
        if (intParams.length > 6) this.quantifier = normalizeQuantifier(intParams[6]);

        return true;
    }

    protected void resetSettings() {
        this.selectedAction = DEFAULT_ACTION;
        this.signFilterEnabled = false;
        this.signId = 0;
        this.danceFilterEnabled = false;
        this.danceId = 1;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.quantifier = QUANTIFIER_ALL;
    }

    protected int normalizeAction(int action) {
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

    protected int normalizeQuantifier(int value) {
        return (value == QUANTIFIER_ANY) ? QUANTIFIER_ANY : QUANTIFIER_ALL;
    }

    protected int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    protected int normalizeSignId(int value) {
        return (value < 0 || value > 17) ? 0 : value;
    }

    protected int normalizeDanceId(int value) {
        return (value < 1 || value > 4) ? 1 : value;
    }

    protected boolean matchesAction(WiredContext ctx, RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        if (this.matchesEventAction(ctx, roomUnit)) {
            return true;
        }

        if (this.matchesCurrentState(roomUnit)) {
            return true;
        }

        return this.matchesRecentAction(roomUnit);
    }

    protected boolean matchesEventAction(WiredContext ctx, RoomUnit roomUnit) {
        RoomUnit actor = ctx.actor().orElse(null);

        if (actor == null || actor.getId() != roomUnit.getId()) {
            return false;
        }

        if (ctx.eventType() != com.eu.habbo.habbohotel.wired.core.WiredEvent.Type.USER_PERFORMS_ACTION) {
            return false;
        }

        return this.matchesConfiguredAction(ctx.event().getActionId(), ctx.event().getActionParameter());
    }

    protected boolean matchesCurrentState(RoomUnit roomUnit) {
        switch (this.selectedAction) {
            case WiredUserActionType.SIT:
                return roomUnit.hasStatus(RoomUnitStatus.SIT);
            case WiredUserActionType.LAY:
                return roomUnit.hasStatus(RoomUnitStatus.LAY);
            case WiredUserActionType.RELAX:
                return roomUnit.isIdle();
            case WiredUserActionType.SIGN:
                return this.matchesSignState(roomUnit);
            case WiredUserActionType.DANCE:
                return this.matchesDanceState(roomUnit);
            default:
                return false;
        }
    }

    protected boolean matchesRecentAction(RoomUnit roomUnit) {
        Object actionValue = roomUnit.getCacheable().get(CACHE_LAST_ACTION_ID);
        Object parameterValue = roomUnit.getCacheable().get(CACHE_LAST_ACTION_PARAMETER);
        Object timestampValue = roomUnit.getCacheable().get(CACHE_LAST_ACTION_TIMESTAMP);

        if (!(actionValue instanceof Integer) || !(timestampValue instanceof Long)) {
            return false;
        }

        long timestamp = (Long) timestampValue;
        if (!WiredUserActionInputGuard.isRecentTimestamp(timestamp, System.currentTimeMillis(), TRANSIENT_ACTION_WINDOW_MS)) {
            return false;
        }

        int actionId = (Integer) actionValue;
        int parameter = (parameterValue instanceof Integer) ? (Integer) parameterValue : -1;

        return this.matchesConfiguredAction(actionId, parameter);
    }

    protected boolean matchesConfiguredAction(int actionId, int actionParameter) {
        if (actionId != this.selectedAction) {
            return false;
        }

        if (this.selectedAction == WiredUserActionType.SIGN && this.signFilterEnabled) {
            return actionParameter == this.signId;
        }

        if (this.selectedAction == WiredUserActionType.DANCE && this.danceFilterEnabled) {
            return actionParameter == this.danceId;
        }

        return true;
    }

    protected boolean matchesSignState(RoomUnit roomUnit) {
        String signStatus = roomUnit.getStatus(RoomUnitStatus.SIGN);
        if (signStatus == null) {
            return false;
        }

        if (!this.signFilterEnabled) {
            return true;
        }

        try {
            return Integer.parseInt(signStatus) == this.signId;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    protected boolean matchesDanceState(RoomUnit roomUnit) {
        int currentDance = roomUnit.getDanceType().getType();
        if (currentDance <= 0) {
            return false;
        }

        if (!this.danceFilterEnabled) {
            return true;
        }

        return currentDance == this.danceId;
    }

    protected int getUserSource() {
        return this.userSource;
    }

    protected int getQuantifier() {
        return this.quantifier;
    }

    static class JsonData {
        int selectedAction;
        boolean signFilterEnabled;
        int signId;
        boolean danceFilterEnabled;
        int danceId;
        int userSource;
        int quantifier;

        public JsonData(int selectedAction, boolean signFilterEnabled, int signId, boolean danceFilterEnabled, int danceId, int userSource, int quantifier) {
            this.selectedAction = selectedAction;
            this.signFilterEnabled = signFilterEnabled;
            this.signId = signId;
            this.danceFilterEnabled = danceFilterEnabled;
            this.danceId = danceId;
            this.userSource = userSource;
            this.quantifier = quantifier;
        }
    }
}
