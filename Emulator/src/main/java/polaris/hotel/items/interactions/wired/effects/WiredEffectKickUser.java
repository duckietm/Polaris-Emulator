package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.InteractionWiredTrigger;
import polaris.hotel.items.interactions.wired.WiredInputGuard;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredSourceUtil;
import polaris.hotel.wired.core.WiredTextPlaceholderUtil;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.wired.WiredSaveException;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.threading.runnables.RoomUnitKick;

public class WiredEffectKickUser extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectKickUser.class);
    public static final WiredEffectType type = WiredEffectType.KICK_USER;

    private String message = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;

    public WiredEffectKickUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectKickUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        LOGGER.debug(
                "[KickUser] targets.users().size={} usersModifiedBySelector={}",
                ctx.targets().users().size(),
                ctx.targets().isUsersModifiedBySelector());

        // Kick exceptions are whispered in the Wired bubble (34), as in Habbo.
        // Behaviour observed in plus-emulator/PlusEMU 382666b3, reimplemented.
        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            User user = room.getUser(unit);
            LOGGER.debug(
                    "[KickUser] RoomUnit id={} type={} -> User={}",
                    unit.getId(),
                    unit.getRoomUnitType(),
                    user != null ? user.getUserInfo().getUsername() : "null");
            if (user == null) continue;

            if (user.hasPermission(Permission.ACC_UNKICKABLE)) {
                user.whisper(
                        Emulator.getTexts().getValue("hotel.wired.kickexception.unkickable"),
                        RoomChatMessageBubbles.WIRED);
                continue;
            }

            if (user.getUserInfo().getId() == room.getOwnerId()) {
                user.whisper(
                        Emulator.getTexts().getValue("hotel.wired.kickexception.owner"), RoomChatMessageBubbles.WIRED);
                continue;
            }

            // AIR always whispers the kicked user first, an empty line when the box has no message, in the wired
            // style, then shows the kick effect.
            // A user whose connection is closing has no client any more; the kick still applies.
            if (user.getClient() != null) {
                String message = WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, this.message);
                user.getClient()
                        .sendResponse(new RoomUserWhisperComposer(
                                new RoomChatMessage(message, user, user, RoomChatMessageBubbles.WIRED)));
            }

            room.giveEffect(user, 4, 2);

            Emulator.getThreading().run(new RoomUnitKick(user, room, true), 2000);
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.message, this.getDelay(), this.userSource));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.setDelay(data.delay);
            this.message = data.message;
            this.userSource = data.userSource;
        } else {
            try {
                String[] data = set.getString("wired_data").split("\t");

                if (data.length >= 1) {
                    this.setDelay(Integer.parseInt(data[0]));

                    if (data.length >= 2) {
                        this.message = data[1];
                    }
                }
            } catch (Exception e) {
                this.message = "";
                this.setDelay(0);
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    @Override
    public void onPickUp() {
        this.message = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.message);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
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
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        String message = settings.getStringParam();
        int[] params = settings.getIntParams();
        // Official: no int params; the user source is the official source slot.
        this.userSource = settings.userSource(0, WiredSourceUtil.SOURCE_TRIGGER);
        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY))
            throw new WiredSaveException("Delay too long");

        this.message = message.substring(
                0, Math.min(message.length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
        this.setDelay(delay);

        return true;
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER
                || WiredTextPlaceholderUtil.requiresActor(this.getRoom(), this);
    }

    static class JsonData {
        String message;
        int delay;
        int userSource;

        public JsonData(String message, int delay, int userSource) {
            this.message = message;
            this.delay = delay;
            this.userSource = userSource;
        }
    }
}
