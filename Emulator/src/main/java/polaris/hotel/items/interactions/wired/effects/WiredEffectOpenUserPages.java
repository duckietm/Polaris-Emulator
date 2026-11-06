package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.users.InClientLinkComposer;

/**
 * Opens an in-client link / Habbo page for the resolved users (e.g. "habbopages/...", "catalog/open/...").
 * Reuses the {@link WiredEffectType#SHOW_MESSAGE} client dialog (single text field = the link path + a
 * user-source selector), so it needs no new client dialog. The link is delivered via
 * {@link InClientLinkComposer}, as proven by ClickUserEvent / InteractionInformationTerminal.
 */
public class WiredEffectOpenUserPages extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.EFFECT_TEXT;

    private String link = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectOpenUserPages(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectOpenUserPages(
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
        message.appendString(this.link);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
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

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        String value = WiredLinkPolicy.normalize(settings.getStringParam());
        if (value.isEmpty()) {
            return false;
        }

        // The link is pushed at every resolved user's client, so it is bounded by who configures it.
        User configurer = (gameClient != null) ? gameClient.getUser() : null;
        if (!WiredLinkPolicy.canUse(value, configurer)) {
            return false;
        }

        this.link = value;

        int[] params = settings.getIntParams();
        // Official: no int params; the user source is the official source slot.
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);

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

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            User user = room.getUser(unit);
            if (user == null || user.getClient() == null) continue;

            user.getClient().sendResponse(new InClientLinkComposer(this.link));
        }
    }

    @Override
    @Deprecated
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.link, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            // Normalised, but not re-gated: the save path is the boundary. Dropping a stored link here
            // would silently blank a box an ACC_SUPERWIRED user was entitled to configure.
            this.link = WiredLinkPolicy.normalize(data.link);
            this.setDelay(data.delay);
            this.userSource = data.userSource;
        } else {
            this.link = "";
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.setDelay(0);
        }
    }

    @Override
    public void onPickUp() {
        this.link = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    static class JsonData {
        String link;
        int delay;
        int userSource;

        public JsonData(String link, int delay, int userSource) {
            this.link = link;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
