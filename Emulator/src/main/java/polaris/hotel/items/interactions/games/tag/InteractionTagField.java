package polaris.hotel.items.interactions.games.tag;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.Game;
import polaris.hotel.games.tag.TagGame;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public abstract class InteractionTagField extends UserItem {
    public Class<? extends Game> gameClazz;

    public InteractionTagField(ResultSet set, Item baseItem, Class<? extends Game> gameClazz) throws SQLException {
        super(set, baseItem);

        this.gameClazz = gameClazz;
    }

    public InteractionTagField(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells,
            Class<? extends Game> gameClazz) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.gameClazz = gameClazz;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        User user = room.getUser(roomUnit);

        if (user != null) {
            return user.getUserInfo().getCurrentGame() == null
                    || user.getUserInfo().getCurrentGame() == this.gameClazz;
        }

        return false;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        User user = room.getUser(roomUnit);

        if (user != null) {
            if (user.getUserInfo().getCurrentGame() == null) {
                TagGame game = (TagGame) room.getGame(this.gameClazz);

                if (game == null) {
                    game = (TagGame)
                            this.gameClazz.getDeclaredConstructor(Room.class).newInstance(room);
                    room.addGame(game);
                }

                game.addUser(user, null);
                user.getUserInfo().setCurrentGame(this.gameClazz);
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }
}
