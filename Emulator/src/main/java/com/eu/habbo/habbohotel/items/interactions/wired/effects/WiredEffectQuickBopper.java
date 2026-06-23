package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.TraxManager;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Starts or stops the room's jukebox/trax music. This is the wired equivalent of clicking play/stop
 * on the room's {@link com.eu.habbo.habbohotel.items.interactions.InteractionJukeBox}; the underlying
 * playback is driven through the room {@link TraxManager} ({@link TraxManager#play(int)} /
 * {@link TraxManager#stop()}), the exact same channel the jukebox UI uses.
 *
 * <p>Carries two ints — {@code [playOrStop, trackIndex]}:
 * <ul>
 *   <li>{@code playOrStop}: {@code 1} = start playback, {@code 0} = stop playback.</li>
 *   <li>{@code trackIndex}: 0-based index into the room's current playlist to start from (only used
 *       when starting). {@code TraxManager.play} wraps the index modulo the playlist size and falls
 *       back to {@code stop()} when the playlist is empty, so an out-of-range index can never crash.</li>
 * </ul>
 * Because it carries a custom int pair it needs a dedicated NEW client dialog
 * {@link WiredEffectType#QUICK_BOPPER} (93) with a matching Nitro
 * {@code WiredActionLayoutCode.QUICK_BOPPER} component.</p>
 */
public class WiredEffectQuickBopper extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.QUICK_BOPPER;

    private static final int STOP = 0;
    private static final int PLAY = 1;
    private static final int DEFAULT_PLAY_OR_STOP = PLAY;
    private static final int DEFAULT_TRACK_INDEX = 0;

    private int playOrStop = DEFAULT_PLAY_OR_STOP;
    private int trackIndex = DEFAULT_TRACK_INDEX;

    public WiredEffectQuickBopper(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectQuickBopper(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;

        TraxManager traxManager = room.getTraxManager();
        if (traxManager == null) return;

        if (this.playOrStop == STOP) {
            if (traxManager.isPlaying()) {
                traxManager.stop();
            }
        } else {
            if (traxManager.getJukeBox() == null || traxManager.getSongs().isEmpty()) {
                return;
            }
            traxManager.play(this.trackIndex);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.playOrStop);
        message.appendInt(this.trackIndex);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.playOrStop = (params.length > 0) ? clampPlayOrStop(params[0]) : DEFAULT_PLAY_OR_STOP;
        this.trackIndex = (params.length > 1) ? clampTrackIndex(params[1]) : DEFAULT_TRACK_INDEX;

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.setDelay(delay);
        return true;
    }

    private static int clampPlayOrStop(int value) {
        return (value == STOP) ? STOP : PLAY;
    }

    private static int clampTrackIndex(int value) {
        return Math.max(0, value);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.playOrStop, this.trackIndex));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = WiredUtilityPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data != null) {
            this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
            this.playOrStop = clampPlayOrStop(data.playOrStop);
            this.trackIndex = clampTrackIndex(data.trackIndex);
        } else {
            this.setDelay(0);
            this.playOrStop = DEFAULT_PLAY_OR_STOP;
            this.trackIndex = DEFAULT_TRACK_INDEX;
        }
    }

    @Override
    public void onPickUp() {
        this.playOrStop = DEFAULT_PLAY_OR_STOP;
        this.trackIndex = DEFAULT_TRACK_INDEX;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int delay;
        int playOrStop;
        int trackIndex;

        public JsonData(int delay, int playOrStop, int trackIndex) {
            this.delay = delay;
            this.playOrStop = playOrStop;
            this.trackIndex = trackIndex;
        }
    }
}
