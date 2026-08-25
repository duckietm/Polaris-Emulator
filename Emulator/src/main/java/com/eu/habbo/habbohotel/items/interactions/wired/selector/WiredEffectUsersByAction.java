package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public class WiredEffectUsersByAction extends InteractionWiredEffect {
    private static final String CACHE_LAST_ACTION_ID = "wired.last_user_action.id";
    private static final String CACHE_LAST_ACTION_PARAMETER = "wired.last_user_action.parameter";
    private static final String CACHE_LAST_ACTION_TIMESTAMP = "wired.last_user_action.timestamp";
    private static final long TRANSIENT_ACTION_WINDOW_MS = 5_000L;
    private static final int DEFAULT_ACTION = WiredUserActionType.WAVE;

    public static final WiredEffectType type = WiredEffectType.USERS_BY_ACTION_SELECTOR;

    private int selectedAction = DEFAULT_ACTION;
    private boolean signFilterEnabled = false;
    private int signId = 0;
    private boolean danceFilterEnabled = false;
    private int danceId = 1;
    private boolean filterExisting = false;
    private boolean invert = false;

    public WiredEffectUsersByAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersByAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        Set<RoomUnit> result = new LinkedHashSet<>();

        for (RoomUnit roomUnit : room.getRoomUnits()) {
            if (this.matchesAction(ctx, roomUnit)) {
                result.add(roomUnit);
            }
        }

        result = this.applySelectorModifiers(result, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.onPickUp();

        if (params.length > 0) this.selectedAction = this.normalizeAction(params[0]);
        if (params.length > 1) this.signFilterEnabled = (params[1] == 1);
        if (params.length > 2) this.signId = this.normalizeSignId(params[2]);
        if (params.length > 3) this.danceFilterEnabled = (params[3] == 1);
        if (params.length > 4) this.danceId = this.normalizeDanceId(params[4]);
        if (params.length > 5) this.filterExisting = (params[5] == 1);
        if (params.length > 6) this.invert = (params[6] == 1);

        this.setDelay(settings.getDelay());
        return true;
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
        return WiredManager.getGson().toJson(new JsonData(
                this.selectedAction,
                this.signFilterEnabled,
                this.signId,
                this.danceFilterEnabled,
                this.danceId,
                this.filterExisting,
                this.invert,
                this.getDelay()
        ));
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

        this.selectedAction = this.normalizeAction(data.selectedAction);
        this.signFilterEnabled = data.signFilterEnabled;
        this.signId = this.normalizeSignId(data.signId);
        this.danceFilterEnabled = data.danceFilterEnabled;
        this.danceId = this.normalizeDanceId(data.danceId);
        this.filterExisting = data.filterExisting;
        this.invert = data.invert;
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.selectedAction = DEFAULT_ACTION;
        this.signFilterEnabled = false;
        this.signId = 0;
        this.danceFilterEnabled = false;
        this.danceId = 1;
        this.filterExisting = false;
        this.invert = false;
        this.setDelay(0);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
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
        message.appendInt(this.filterExisting ? 1 : 0);
        message.appendInt(this.invert ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
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

    private int normalizeSignId(int value) {
        return (value < 0 || value > 17) ? 0 : value;
    }

    private int normalizeDanceId(int value) {
        return (value < 1 || value > 4) ? 1 : value;
    }

    private boolean matchesAction(WiredContext ctx, RoomUnit roomUnit) {
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

    private boolean matchesEventAction(WiredContext ctx, RoomUnit roomUnit) {
        RoomUnit actor = ctx.actor().orElse(null);

        if (actor == null || actor.getId() != roomUnit.getId()) {
            return false;
        }

        if (ctx.eventType() != WiredEvent.Type.USER_PERFORMS_ACTION) {
            return false;
        }

        return this.matchesConfiguredAction(ctx.event().getActionId(), ctx.event().getActionParameter());
    }

    private boolean matchesCurrentState(RoomUnit roomUnit) {
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

    private boolean matchesRecentAction(RoomUnit roomUnit) {
        Object actionValue = roomUnit.getCacheable().get(CACHE_LAST_ACTION_ID);
        Object parameterValue = roomUnit.getCacheable().get(CACHE_LAST_ACTION_PARAMETER);
        Object timestampValue = roomUnit.getCacheable().get(CACHE_LAST_ACTION_TIMESTAMP);

        if (!(actionValue instanceof Integer) || !(timestampValue instanceof Long)) {
            return false;
        }

        long timestamp = (Long) timestampValue;
        if ((System.currentTimeMillis() - timestamp) > TRANSIENT_ACTION_WINDOW_MS) {
            return false;
        }

        int actionId = (Integer) actionValue;
        int parameter = (parameterValue instanceof Integer) ? (Integer) parameterValue : -1;

        return this.matchesConfiguredAction(actionId, parameter);
    }

    private boolean matchesConfiguredAction(int actionId, int actionParameter) {
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

    private boolean matchesSignState(RoomUnit roomUnit) {
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

    private boolean matchesDanceState(RoomUnit roomUnit) {
        int currentDance = roomUnit.getDanceType().getType();
        if (currentDance <= 0) {
            return false;
        }

        if (!this.danceFilterEnabled) {
            return true;
        }

        return currentDance == this.danceId;
    }

    static class JsonData {
        int selectedAction;
        boolean signFilterEnabled;
        int signId;
        boolean danceFilterEnabled;
        int danceId;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(int selectedAction, boolean signFilterEnabled, int signId, boolean danceFilterEnabled, int danceId, boolean filterExisting, boolean invert, int delay) {
            this.selectedAction = selectedAction;
            this.signFilterEnabled = signFilterEnabled;
            this.signId = signId;
            this.danceFilterEnabled = danceFilterEnabled;
            this.danceId = danceId;
            this.filterExisting = filterExisting;
            this.invert = invert;
            this.delay = delay;
        }
    }
}
