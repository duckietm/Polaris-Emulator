package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
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
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.wired.WiredClickSettingsComposer;

/**
 * The official {@code wf_act_click_conf} action: tells the selected users' clients what their clicks
 * on avatars and furni do from now on. Avatars can be walked behind or clicked through, furni can be
 * clicked through, so the floor under a crowd stays clickable in a game. The client applies it and
 * forgets it when the player leaves the room; the room only tells it.
 *
 * <p>Params: the user option, the furni option, the user source.
 */
public class WiredEffectClickSettings extends InteractionWiredEffect {
    private static final WiredEffectType type = WiredEffectType.CLICK_SETTINGS;

    private int userOption = WiredClickSettingsComposer.CLICK_USER_DEFAULT;
    private int furniOption = WiredClickSettingsComposer.CLICK_FURNI_DEFAULT;
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectClickSettings(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectClickSettings(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    static int normalizeUserOption(int value) {
        return (value >= WiredClickSettingsComposer.CLICK_USER_DEFAULT
                        && value <= WiredClickSettingsComposer.CLICK_USER_PASS_THROUGH)
                ? value
                : WiredClickSettingsComposer.CLICK_USER_DEFAULT;
    }

    static int normalizeFurniOption(int value) {
        return (value >= WiredClickSettingsComposer.CLICK_FURNI_DEFAULT
                        && value <= WiredClickSettingsComposer.CLICK_FURNI_PASS_THROUGH)
                ? value
                : WiredClickSettingsComposer.CLICK_FURNI_DEFAULT;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.userOption);
        message.appendInt(this.furniOption);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();

        if (params.length < 2) throw new WiredSaveException("invalid data");

        this.userOption = normalizeUserOption(params[0]);
        this.furniOption = normalizeFurniOption(params[1]);
        // Official layout: [user option, furni option]; the user source is the official slot.
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        this.setDelay(settings.getDelay());

        return true;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        WiredClickSettingsComposer composer = new WiredClickSettingsComposer(this.userOption, this.furniOption);

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            User user = room.getUser(roomUnit);

            // Bots and pets have no client to tell; a player whose client is already gone is skipped.
            if (user == null || user.getClient() == null) continue;

            user.getClient().sendResponse(composer);
        }
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.getDelay(), this.userOption, this.furniOption, this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.onPickUp();

        if (wiredData == null || !wiredData.startsWith("{")) {
            return;
        }

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);

        if (data == null) {
            return;
        }

        this.setDelay(data.delay);
        this.userOption = normalizeUserOption(data.userOption);
        this.furniOption = normalizeFurniOption(data.furniOption);
        this.userSource = data.userSource;
    }

    @Override
    public void onPickUp() {
        this.setDelay(0);
        this.userOption = WiredClickSettingsComposer.CLICK_USER_DEFAULT;
        this.furniOption = WiredClickSettingsComposer.CLICK_FURNI_DEFAULT;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    public int getUserOption() {
        return this.userOption;
    }

    public int getFurniOption() {
        return this.furniOption;
    }

    static class JsonData {
        int delay;
        int userOption;
        int furniOption;
        int userSource;

        public JsonData(int delay, int userOption, int furniOption, int userSource) {
            this.delay = delay;
            this.userOption = userOption;
            this.furniOption = furniOption;
            this.userSource = userSource;
        }
    }
}
