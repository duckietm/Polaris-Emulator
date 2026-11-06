package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.WiredPlatform;
import polaris.hotel.commands.Command;
import polaris.hotel.commands.CommandHandler;
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

public class WiredEffectSayCommand extends InteractionWiredEffect {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEffectSayCommand.class);

    public static final WiredEffectType type = WiredEffectType.EFFECT_TEXT;

    private String command = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int configuredByUserId;

    public WiredEffectSayCommand(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSayCommand(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.command);
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
        String nextCommand = settings.getStringParam();
        nextCommand = (nextCommand == null) ? "" : nextCommand.trim();
        if (nextCommand.isEmpty()) {
            return false;
        }

        User principal = gameClient == null ? null : gameClient.getUser();
        if (principal == null || !WiredCommandPolicy.canUse(WiredCommandPolicy.resolve(nextCommand), principal)) {
            return false;
        }
        this.command = nextCommand;
        this.configuredByUserId = principal.getUserInfo().getId();

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

        String line = this.command.startsWith(":") ? this.command : ":" + this.command;
        Command resolved = WiredCommandPolicy.resolve(line);
        User principal = resolvePrincipal(room);
        if (!WiredCommandPolicy.canUse(resolved, principal)) {
            LOGGER.warn(
                    "Blocked wired command item={} room={} principal={} key={}",
                    this.getId(),
                    room.getId(),
                    this.configuredByUserId,
                    WiredCommandPolicy.commandKey(line));
            return;
        }

        for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            User user = room.getUser(unit);
            if (user == null || user.getClient() == null) continue;

            CommandHandler.handleCommand(user.getClient(), line);
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
                .toJson(new JsonData(this.command, this.getDelay(), this.userSource, this.configuredByUserId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.command = (data.command == null) ? "" : data.command;
            this.setDelay(data.delay);
            this.userSource = data.userSource;
            this.configuredByUserId = data.configuredByUserId > 0 ? data.configuredByUserId : this.getUserId();
        } else {
            wiredData = wiredData == null ? "" : wiredData;
            String[] data = wiredData.split("\t");
            this.command = "";

            if (data.length >= 2) {
                super.setDelay(Integer.parseInt(data[0]));
                this.command = data[1];
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.configuredByUserId = this.getUserId();
        }
    }

    @Override
    public void onPickUp() {
        this.command = "";
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.configuredByUserId = 0;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER;
    }

    private User resolvePrincipal(Room room) {
        int principalId = this.configuredByUserId > 0 ? this.configuredByUserId : this.getUserId();
        User principal = room.getUser(principalId);
        if (principal != null) return principal;
        if (WiredPlatform.gameEnvironment() == null) return null;
        return WiredPlatform.gameEnvironment().getUserManager().getUser(principalId);
    }

    static class JsonData {
        String command;
        int delay;
        int userSource;
        int configuredByUserId;

        public JsonData(String command, int delay, int userSource, int configuredByUserId) {
            this.command = command;
            this.delay = delay;
            this.userSource = userSource;
            this.configuredByUserId = configuredByUserId;
        }
    }
}
