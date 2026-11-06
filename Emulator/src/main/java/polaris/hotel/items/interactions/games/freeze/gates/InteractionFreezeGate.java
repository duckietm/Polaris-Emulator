package polaris.hotel.items.interactions.games.freeze.gates;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.games.Game;
import polaris.hotel.games.GameState;
import polaris.hotel.games.GameTeam;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.InteractionGameGate;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

public class InteractionFreezeGate extends InteractionGameGate {
    public InteractionFreezeGate(ResultSet set, Item baseItem, GameTeamColors teamColor) throws SQLException {
        super(set, baseItem, teamColor);
    }

    public InteractionFreezeGate(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells,
            GameTeamColors teamColor) {
        super(id, userId, item, extradata, limitedStack, limitedSells, teamColor);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return room.getGame(FreezeGame.class) == null
                || ((FreezeGame) room.getGame(FreezeGame.class)).state.equals(GameState.IDLE);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean isWalkable() {
        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(this.getRoomId());

        if (room == null) return false;

        Game game = room.getGame(FreezeGame.class);

        return game == null || game.getState() == GameState.IDLE;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        FreezeGame game = (FreezeGame) room.getGame(FreezeGame.class);

        if (game == null) {
            game = FreezeGame.class.getDeclaredConstructor(Room.class).newInstance(room);
            room.addGame(game);
        }

        GameTeam team = game.getTeamForUser(room.getUser(roomUnit));

        if (team != null) {
            game.removeUser(room.getUser(roomUnit));
        } else {
            game.addUser(room.getUser(roomUnit), this.teamColor);
        }

        updateState(game, 5);

        super.onWalkOn(roomUnit, room, objects);
    }
}
