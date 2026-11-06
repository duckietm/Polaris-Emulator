package polaris.hotel.items.interactions.games.freeze;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.games.GameState;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.games.freeze.FreezeGamePlayer;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionFreezeBlock extends UserItem {
    public InteractionFreezeBlock(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionFreezeBlock(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null) return;

        UserItem item = null;
        Set<UserItem> items = room.getItemsAt(room.getLayout().getTile(this.getX(), this.getY()));

        for (UserItem i : items) {
            if (i instanceof InteractionFreezeTile) {
                if (item == null || i.getZ() <= item.getZ()) {
                    item = i;
                }
            }
        }

        if (item != null) {
            FreezeGame game = (FreezeGame) room.getGame(FreezeGame.class);

            if (game == null) return;

            game.throwBall(client.getUser(), (InteractionFreezeTile) item);
        }
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        if (this.getExtradata().length() == 0) {
            this.setExtradata("0");
        }
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return this.isWalkable();
    }

    @Override
    public boolean isWalkable() {
        return !this.getExtradata().isEmpty() && !this.getExtradata().equals("0");
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.getExtradata().isEmpty() || this.getExtradata().equalsIgnoreCase("0")) return;

        FreezeGame game = (FreezeGame) room.getGame(FreezeGame.class);
        if (game == null || !game.state.equals(GameState.RUNNING)) return;

        User user = room.getUser(roomUnit);

        if (user == null || user.getUserInfo().getCurrentGame() != FreezeGame.class) return;

        FreezeGamePlayer player = (FreezeGamePlayer) user.getUserInfo().getGamePlayer();

        if (player == null) return;

        int powerUp;
        try {
            powerUp = Integer.parseInt(this.getExtradata()) / 1000;
        } catch (NumberFormatException e) {
            powerUp = 0;
        }

        if (powerUp >= 2 && powerUp <= 7) {
            if (powerUp == 6 && !player.canPickupLife()) return;

            this.setExtradata((powerUp + 10) * 1000 + "");

            room.updateItem(this);

            game.givePowerUp(player, powerUp);

            AchievementManager.progressAchievement(
                    player.getUser(),
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("FreezePowerUp"));
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }
}
