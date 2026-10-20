package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredRewardPolicy;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.quests.RewardTrackManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredHotelProgress;
import com.eu.habbo.habbohotel.wired.core.WiredHotelProgressPolicy;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredVariableOperand;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Habbo's reset-reward-track action: puts the selected users' tasks of a reward track back to zero.
 * Only the tasks the hotel lets wired move are touched ({@code hotel.wired.reward_tracks.allowed}),
 * and points, claimed prizes and levels already paid stay, so a reset never pays twice.
 *
 * <p>String param: the track id. Int params: {@code [user source]}.
 */
public class WiredEffectResetRewardTrack extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.RESET_REWARD_TRACK;

    private String trackId = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectResetRewardTrack(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectResetRewardTrack(
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
        message.appendString(this.trackId);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        WiredEffectProgressRewardTrack.appendInvalidTriggers(this, message, room);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        if (!WiredRewardPolicy.canConfigure(gameClient)) {
            return false;
        }

        // AIR: TextInputParam("", 100, null, -1, "^\t"), with no check that it is filled in.
        String track = WiredHotelProgress.normalizeResetRewardTrackId(settings.getStringParam());
        if (track == null) {
            return false;
        }

        int[] params = settings.getIntParams();
        this.trackId = track;
        // Official: no int params; the user source is the official source slot.
        this.userSource =
                WiredVariableOperand.normalizeUserSource(settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER));
        this.setDelay(settings.getDelay());
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        WiredHotelProgressPolicy policy = WiredHotelProgressPolicy.rewardTracks(WiredPlatform.configuration());
        if (room == null || environment == null || !policy.enabled() || this.trackId.isEmpty()) {
            return;
        }

        RewardTrackManager manager = environment.getRewardTrackManager();
        List<Habbo> users = WiredHotelProgress.presentUsers(room, WiredSourceUtil.resolveUsers(ctx, this.userSource));
        if (manager == null || users.isEmpty()) {
            return;
        }

        String track = this.trackId;
        WiredHotelProgress.dispatch(() -> WiredHotelProgress.resetRewardTrack(room, users, manager, track, policy));
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.trackId, this.getDelay(), this.userSource));
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

        this.trackId = WiredHotelProgress.loadWiredRewardTrackId(data.trackId, true);
        this.userSource = WiredVariableOperand.normalizeUserSource(data.userSource);
        this.setDelay(data.delay);
    }

    @Override
    public void onPickUp() {
        this.trackId = "";
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

    public int getUserSource() {
        return this.userSource;
    }

    static class JsonData {
        String trackId;
        int delay;
        int userSource;

        JsonData(String trackId, int delay, int userSource) {
            this.trackId = trackId;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
