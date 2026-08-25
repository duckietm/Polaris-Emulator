package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

/**
 * Plays a single YouTube video/sound for the whole room, driving the same room-jukebox path that the
 * client's own "play video" button uses ({@code YouTubeRoomPlayEvent}). Carries a YouTube id/url
 * {@code videoId} (stringParam) plus a single optional {@code autoStart} int (1 = start playing,
 * 0 = clear/stop), so it needs a dedicated dialog and uses the NEW client code
 * {@link WiredEffectType#PLAY_YOUTUBE} (92) with a matching Nitro {@code WiredActionLayoutCode.PLAY_YOUTUBE}
 * component.
 *
 * <p>On execute it mirrors {@code YouTubeRoomPlayEvent#handle}: it requires the room's YouTube feature
 * to be enabled, then either stores + broadcasts the video (sender name = room owner, since wired has
 * no triggering "DJ") or clears it. No new packet or core-path hook is needed — the {@link Room}
 * already exposes {@code isYoutubeEnabled()/setYoutubeVideo/clearYoutubeVideo} and the outgoing
 * {@link YouTubeRoomBroadcastComposer} already takes an arbitrary video id.</p>
 */
public class WiredEffectPlayYoutube extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.PLAY_YOUTUBE;

    private static final int MAX_VIDEO_ID_LENGTH = 100;

    private String videoId = "";
    private int autoStart = 1;

    public WiredEffectPlayYoutube(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectPlayYoutube(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        // Faithful to YouTubeRoomPlayEvent: the room must have the YouTube feature enabled.
        if (!room.isYoutubeEnabled()) {
            return;
        }

        if (this.autoStart == 0 || this.videoId.isEmpty()) {
            room.clearYoutubeVideo();
            room.sendComposer(new YouTubeRoomBroadcastComposer("", "", Collections.emptyList()).compose());
            return;
        }

        // Wired has no triggering "DJ" user — attribute the broadcast to the room owner.
        String senderName = room.getOwnerName() == null ? "" : room.getOwnerName();
        room.setYoutubeVideo(this.videoId, senderName, Collections.emptyList());
        room.sendComposer(new YouTubeRoomBroadcastComposer(this.videoId, senderName, Collections.emptyList()).compose());
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
        message.appendString(this.videoId);
        message.appendInt(1);
        message.appendInt(this.autoStart);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        this.autoStart = (params.length > 0 && params[0] == 0) ? 0 : 1;

        String id = settings.getStringParam();
        id = (id == null) ? "" : id.replace("\t", "");
        this.videoId = id.substring(0, Math.min(id.length(), MAX_VIDEO_ID_LENGTH));

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20)) {
            throw new WiredSaveException("Delay too long");
        }

        this.setDelay(delay);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.videoId, this.autoStart));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = WiredEffectPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(data.delay));
            String id = WiredEffectPayloadGuard.text(data.video_id);
            this.videoId = id.substring(0, Math.min(id.length(), MAX_VIDEO_ID_LENGTH));
            this.autoStart = (data.auto_start == 0) ? 0 : 1;
        } else {
            this.setDelay(0);
            this.videoId = "";
            this.autoStart = 1;
        }
    }

    @Override
    public void onPickUp() {
        this.videoId = "";
        this.autoStart = 1;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int delay;
        String video_id;
        int auto_start;

        public JsonData(int delay, String video_id, int auto_start) {
            this.delay = delay;
            this.video_id = video_id;
            this.auto_start = auto_start;
        }
    }
}
