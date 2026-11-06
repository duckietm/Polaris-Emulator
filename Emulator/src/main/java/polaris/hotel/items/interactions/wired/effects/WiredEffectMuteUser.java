package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.WiredCompatibilityDiagnostics;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;

public class WiredEffectMuteUser extends InteractionWiredEffect {
    private static final WiredEffectType type = WiredEffectType.MUTE_TRIGGER;

    // Upper bound on the wired mute duration (minutes). The client sent this
    // raw with only a floor of 1, so a room owner could set an arbitrary /
    // effectively permanent mute, and a large value overflowed the int expiry
    // math in RoomChatManager to an unpredictable timestamp.
    private static final int MAX_MUTE_MINUTES = 24 * 60;

    private int length = 5;
    private String message = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectMuteUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectMuteUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.message);
        message.appendInt(1);
        message.appendInt(this.length);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // Official layout: [minutes]; the user source is the official source slot.
        if (settings.getIntParams().length < 1) throw new WiredSaveException("invalid data");

        this.length = Math.max(0, Math.min(settings.getIntParams()[0], MAX_MUTE_MINUTES));
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.message = settings.getStringParam();

        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            User user = room.getUser(roomUnit);
            if (user == null) continue;

            if (room.hasRights(user)) continue;

            room.muteUser(user, Math.max(1, Math.min(this.length, MAX_MUTE_MINUTES)));

            // The helper skips a user whose client is already gone instead of failing the stack.
            WiredEffectUserMessage.whisper(ctx, user, this.message);
        }
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.getDelay(), this.length, this.message, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            // Save and execute clamp the length; a row written before the cap, or by hand, must not
            // get past it on load. A row with no message reads as an empty one.
            this.length = Math.max(1, Math.min(data.length, MAX_MUTE_MINUTES));
            this.message = data.message == null ? "" : data.message;
            this.userSource = data.userSource;
        } else {
            String[] data = wiredData.split("\t");

            if (data.length >= 3) {
                try {
                    this.setDelay(Integer.parseInt(data[0]));
                    this.length = Math.max(1, Math.min(Integer.parseInt(data[1]), MAX_MUTE_MINUTES));
                    this.message = data[2];
                } catch (Exception e) {
                    WiredCompatibilityDiagnostics.record(
                            WiredCompatibilityDiagnostics.FailurePoint.EFFECT_MUTE_USER_LEGACY,
                            this.getRoomId(),
                            this.getId(),
                            e);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.message = "";
        this.length = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER
                || WiredTextPlaceholderUtil.requiresActor(this.getRoom(), this);
    }

    static class JsonData {
        int delay;
        int length;
        String message;
        int userSource;

        public JsonData(int delay, int length, String message, int userSource) {
            this.delay = delay;
            this.length = length;
            this.message = message;
            this.userSource = userSource;
        }
    }
}
