package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.Game;
import polaris.hotel.games.GameState;
import polaris.hotel.games.battlebanzai.BattleBanzaiGame;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.messages.ServerMessage;

/**
 * Passes only when no BattleBanzai game is currently running in the room. A "running" game is one
 * whose {@link Game#getState()} is {@link GameState#RUNNING} (mirrors the live check in
 * {@code InteractionBattleBanzaiGate}). This is a no-config condition: it carries zero int params and
 * no string, so it reuses the no-input client dialog {@code MOVEMENT_VALIDATION}-style layout via the
 * dedicated new client code {@code WiredConditionlayout.NO_BATTLEBANZAI} (44).
 *
 * <p>Serialization mirrors {@link WiredConditionMovementValidation}: it appends {@code intParamCount=0}
 * and an empty string, so the client reads no ints and saves none, and {@link #saveData(WiredSettings)}
 * reads nothing back.</p>
 */
public class WiredConditionNoBattleBanzaiRunning extends InteractionWiredCondition {

    public static final WiredConditionType type = WiredConditionType.NO_BATTLEBANZAI;

    public WiredConditionNoBattleBanzaiRunning(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNoBattleBanzaiRunning(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return true;
        }

        Game game = room.getGame(BattleBanzaiGame.class);
        return game == null || game.getState() != GameState.RUNNING;
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
    public void loadWiredData(ResultSet set, Room room) throws SQLException {}

    @Override
    public void onPickUp() {}

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
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
    public boolean saveData(WiredSettings settings) {
        return true;
    }
}
