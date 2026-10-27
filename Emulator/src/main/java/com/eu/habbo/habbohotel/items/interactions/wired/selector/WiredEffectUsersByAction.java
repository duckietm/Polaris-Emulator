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
import com.eu.habbo.habbohotel.wired.core.WiredActionMemory;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSelector;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public class WiredEffectUsersByAction extends InteractionWiredEffect implements WiredOfficialSelector {
    static final int OFFICIAL_ACTION_IDS = 2;
    // A fresh box selects sitting users, as officially.
    private static final int DEFAULT_ACTION = WiredUserActionType.SIT;

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

    public WiredEffectUsersByAction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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

        result = this.applySelectorModifiers(
                result, room.getRoomUnits(), ctx.targets().users(), this.filterExisting, this.invert);

        ctx.targets().setUsers(result);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();

        this.onPickUp();

        if (params.length > 0) this.selectedAction = this.normalizeAction(params[0]);
        // Official: [action], the sign or dance extra in the string param, filter and invert in the
        // selector switches.
        int extra = WiredUserActionType.extraCode(this.selectedAction, settings.getStringParam());
        if (this.selectedAction == WiredUserActionType.SIGN && extra >= 0) {
            this.signFilterEnabled = true;
            this.signId = extra;
        }
        if (this.selectedAction == WiredUserActionType.DANCE && extra >= 0) {
            this.danceFilterEnabled = true;
            this.danceId = extra;
        }
        this.filterExisting = settings.isSelectorFilter();
        this.invert = settings.isSelectorInvert();

        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public int[] officialFurniSources() {
        return new int[0];
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
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
                .toJson(new JsonData(
                        this.selectedAction,
                        this.signFilterEnabled,
                        this.signId,
                        this.danceFilterEnabled,
                        this.danceId,
                        this.filterExisting,
                        this.invert,
                        this.getDelay()));
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

        this.selectedAction = this.normalizeAction(
                data.actionIds >= OFFICIAL_ACTION_IDS
                        ? data.selectedAction
                        : WiredUserActionType.fromLegacy(data.selectedAction));
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
        message.appendString(WiredUserActionType.extraString(
                this.selectedAction, this.signFilterEnabled, this.signId, this.danceFilterEnabled, this.danceId));
        message.appendInt(1);
        message.appendInt(this.selectedAction);
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
        return WiredUserActionType.isAction(action) ? action : DEFAULT_ACTION;
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

        return this.matchesConfiguredAction(
                ctx.event().getActionId(), ctx.event().getActionParameter());
    }

    private boolean matchesCurrentState(RoomUnit roomUnit) {
        switch (this.selectedAction) {
            case WiredUserActionType.SIT:
                return roomUnit.hasStatus(RoomUnitStatus.SIT);
            case WiredUserActionType.LAY:
                return roomUnit.hasStatus(RoomUnitStatus.LAY);
            case WiredUserActionType.RELAX:
                return roomUnit.isIdle();
            // The states the official selector also reads off the avatar.
            case WiredUserActionType.STAND:
                return !roomUnit.hasStatus(RoomUnitStatus.SIT) && !roomUnit.hasStatus(RoomUnitStatus.LAY);
            case WiredUserActionType.AWAKE:
                return !roomUnit.isIdle();
            case WiredUserActionType.WAVE:
                return roomUnit.hasStatus(RoomUnitStatus.WAVE);
            case WiredUserActionType.SIGN:
                return this.matchesSignState(roomUnit);
            case WiredUserActionType.DANCE:
                return this.matchesDanceState(roomUnit);
            default:
                return false;
        }
    }

    /** AIR remembers an action 5 s (respect 2.5 s); blow kiss, laugh and dance are never remembered. */
    private boolean matchesRecentAction(RoomUnit roomUnit) {
        int[] recent = WiredActionMemory.selectorRecent(roomUnit, System.currentTimeMillis());
        return recent != null && this.matchesConfiguredAction(recent[0], recent[1]);
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
        /** 2: the official action ids; rows saved before have none and use the old ids. */
        int actionIds = OFFICIAL_ACTION_IDS;

        int selectedAction;
        boolean signFilterEnabled;
        int signId;
        boolean danceFilterEnabled;
        int danceId;
        boolean filterExisting;
        boolean invert;
        int delay;

        JsonData(
                int selectedAction,
                boolean signFilterEnabled,
                int signId,
                boolean danceFilterEnabled,
                int danceId,
                boolean filterExisting,
                boolean invert,
                int delay) {
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
