package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredRewardPolicy;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.quests.RewardTrackManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.habbohotel.wired.core.WiredHotelProgress;
import com.eu.habbo.habbohotel.wired.core.WiredHotelProgressPolicy;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredProgressLimiter;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredValueVariable;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialVariableIds;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableIds;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Habbo's progress-reward-track action: moves one task of a reward track for the selected users,
 * adding the amount or setting the progress to it. Task levels pay their points as usual, but only
 * once per level whatever resets happen in between.
 *
 * <p>Reward tracks are hotel-wide, so this only works when the hotel turns it on
 * ({@code hotel.wired.reward_tracks.enabled}) for a running track, or {@code track:task}, on its
 * allow-list, for users still in the room, within each user's allowance per window. Saving it needs
 * the value-granting wired permission.
 *
 * <p>String param: {@code track id \t task id}; variable ids {@code [amount variable]} (older clients sent its id as a
 * third tab-separated field of the string; still read). Int params,
 * official: {@code [add to existing, amount from a variable, amount, variable target (global or
 * context)]}; the user source is the official source slot.
 */
public class WiredEffectProgressRewardTrack extends InteractionWiredEffect implements WiredOfficialVariableIds {
    public static final WiredEffectType type = WiredEffectType.PROGRESS_REWARD_TRACK;

    private String trackId = "";
    private String taskId = "";
    // Official default for a new box; saved boxes keep theirs.
    private boolean addToExisting = false;
    private int amount = 1;
    private boolean amountFromVariable = false;
    private int amountTarget = WiredValueVariable.TARGET_GLOBAL;
    private int amountVariableId = 0;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectProgressRewardTrack(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectProgressRewardTrack(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.trackId.isEmpty() && this.taskId.isEmpty() ? "" : this.trackId + "\t" + this.taskId);
        message.appendInt(4);
        message.appendInt(this.addToExisting ? 1 : 0);
        message.appendInt(this.amountFromVariable ? 1 : 0);
        message.appendInt(this.amount);
        message.appendInt(this.amountTarget);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        appendInvalidTriggers(this, message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        if (!WiredRewardPolicy.canConfigure(gameClient)) {
            return false;
        }

        String value = settings.getStringParam() == null ? "" : settings.getStringParam();
        String[] ids = value.split("\t", -1);
        // A third field is the value variable's id, a number.
        if (ids.length != 2 && !(ids.length == 3 && ids[2].trim().matches("\\d{1,10}"))) {
            return false;
        }
        // AIR: TextInputParam("", 100, null, -1, "a-zA-Z0-9_") for both ids.
        String track = WiredHotelProgress.normalizeProgressRewardTrackId(ids[0]);
        String task = WiredHotelProgress.normalizeProgressRewardTrackId(ids[1]);
        if (track.isEmpty() || task.isEmpty()) {
            return false;
        }

        int[] params = settings.getIntParams();
        this.trackId = track;
        this.taskId = task;
        int variableId = WiredVariableIds.present(settings.getVariableIds())
                ? WiredVariableIds.boxIdOf(settings.getVariableIds()[0])
                : (ids.length > 2) ? WiredValueVariable.parseId(ids[2]) : 0;
        boolean fromVariable = params.length > 1 && params[1] == 1;
        if (fromVariable && variableId <= 0) {
            return false;
        }
        this.addToExisting = params.length > 0 && params[0] != 0;
        this.amountFromVariable = fromVariable;
        this.amountVariableId = fromVariable ? variableId : 0;
        this.amount = WiredHotelProgress.normalizeAmount(params.length > 2 ? params[2] : 1);
        this.amountTarget = (params.length > 3 && params[3] == WiredValueVariable.TARGET_CONTEXT)
                ? WiredValueVariable.TARGET_CONTEXT
                : WiredValueVariable.TARGET_GLOBAL;
        this.userSource =
                WiredVariableOperand.normalizeUserSource(settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER));
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public String[] officialVariableIds() {
        return WiredVariableIds.list(
                this.amountFromVariable ? WiredVariableIds.toWire(this.amountTarget, this.amountVariableId) : "");
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        WiredEngine engine = WiredManager.getEngine();
        WiredHotelProgressPolicy policy = WiredHotelProgressPolicy.rewardTracks(WiredPlatform.configuration());
        if (room == null
                || environment == null
                || engine == null
                || !WiredHotelProgress.allowsTask(policy, this.trackId, this.taskId)) {
            return;
        }

        RewardTrackManager manager = environment.getRewardTrackManager();
        List<Habbo> users = WiredHotelProgress.presentUsers(room, WiredSourceUtil.resolveUsers(ctx, this.userSource));
        if (manager == null || users.isEmpty()) {
            return;
        }

        String track = this.trackId;
        String task = this.taskId;
        boolean add = this.addToExisting;
        int amount = this.amount;
        if (this.amountFromVariable) {
            Integer value = WiredValueVariable.read(
                    ctx,
                    room,
                    this.amountTarget,
                    this.amountVariableId,
                    WiredSourceUtil.SOURCE_TRIGGER,
                    WiredSourceUtil.SOURCE_TRIGGER);
            if (value == null || value < 1) {
                return;
            }
            amount = WiredHotelProgress.normalizeAmount(value);
        }
        int progress = amount;
        WiredProgressLimiter limiter = engine.progressLimiter();
        WiredHotelProgress.dispatch(() -> WiredHotelProgress.progressRewardTrack(
                room, users, manager, track, task, add, progress, policy, limiter));
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(
                this.trackId, this.taskId, this.addToExisting, this.amount, this.getDelay(), this.userSource);
        data.amountFromVariable = this.amountFromVariable;
        data.amountTarget = this.amountTarget;
        data.amountVariableId = this.amountVariableId;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data;
        try {
            data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        } catch (RuntimeException e) {
            return;
        }
        if (data == null) {
            return;
        }

        this.trackId = WiredHotelProgress.loadWiredRewardTrackId(data.trackId, false);
        this.taskId = WiredHotelProgress.loadWiredRewardTrackId(data.taskId, false);
        this.addToExisting = data.addToExisting;
        this.amount = WiredHotelProgress.normalizeAmount(data.amount);
        this.userSource = WiredVariableOperand.normalizeUserSource(data.userSource);
        this.setDelay(data.delay);
        this.amountVariableId = Math.max(0, data.amountVariableId);
        this.amountFromVariable = data.amountFromVariable && this.amountVariableId > 0;
        this.amountTarget = (data.amountTarget == WiredValueVariable.TARGET_CONTEXT)
                ? WiredValueVariable.TARGET_CONTEXT
                : WiredValueVariable.TARGET_GLOBAL;
    }

    @Override
    public void onPickUp() {
        this.trackId = "";
        this.taskId = "";
        this.addToExisting = false;
        this.amount = 1;
        this.amountFromVariable = false;
        this.amountTarget = WiredValueVariable.TARGET_GLOBAL;
        this.amountVariableId = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    public String getTrackId() {
        return this.trackId;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public boolean isAddToExisting() {
        return this.addToExisting;
    }

    public int getAmount() {
        return this.amount;
    }

    public int getUserSource() {
        return this.userSource;
    }

    static void appendInvalidTriggers(InteractionWiredEffect effect, ServerMessage message, Room room) {
        if (effect.requiresTriggeringUser() && room != null && room.getRoomSpecialTypes() != null) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object :
                    room.getRoomSpecialTypes().getTriggers(effect.getX(), effect.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
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

    static class JsonData {
        String trackId;
        String taskId;
        boolean addToExisting;
        int amount;
        int delay;
        int userSource;
        boolean amountFromVariable;
        int amountTarget;
        int amountVariableId;

        JsonData(String trackId, String taskId, boolean addToExisting, int amount, int delay, int userSource) {
            this.trackId = trackId;
            this.taskId = taskId;
            this.addToExisting = addToExisting;
            this.amount = amount;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
