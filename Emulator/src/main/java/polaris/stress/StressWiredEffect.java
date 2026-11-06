package polaris.stress;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;
import polaris.messages.ServerMessage;

final class StressWiredEffect extends InteractionWiredEffect {
    private final AtomicLong executions;

    StressWiredEffect(int id, int userId, Item item, AtomicLong executions) {
        super(id, userId, item, "0", 0, 0);
        this.executions = executions;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return WiredEffectType.SHOW_MESSAGE;
    }

    @Override
    public void execute(WiredContext context) {
        this.executions.incrementAndGet();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return "";
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        // Synthetic wired effects have no persisted settings.
    }

    @Override
    public void onPickUp() {
        // No retained runtime state.
    }

    @Override
    public void run() {
        // Stress entities are deliberately never persisted.
    }
}
