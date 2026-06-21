package com.eu.habbo.habbohotel.items.interactions.games.freeze;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.freeze.FreezeGamePlayer;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class InteractionFreezeBlock extends HabboItem {
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
        if (client == null)
            return;

        HabboItem item = null;
        Set<HabboItem> items = room.getItemsAt(room.getLayout().getTile(this.getX(), this.getY()));

        for (HabboItem i : items) {
            if (i instanceof InteractionFreezeTile) {
                if (item == null || i.getZ() <= item.getZ()) {
                    item = i;
                }
            }
        }

        if (item != null) {
            FreezeGame game = (FreezeGame) room.getGame(FreezeGame.class);

            if (game == null)
                return;

            game.throwBall(client.getHabbo(), (InteractionFreezeTile) item);
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
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.getExtradata().isEmpty() || this.getExtradata().equalsIgnoreCase("0"))
            return;

        FreezeGame game = (FreezeGame) room.getGame(FreezeGame.class);
        if (game == null || !game.state.equals(GameState.RUNNING))
            return;

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null || habbo.getHabboInfo().getCurrentGame() != FreezeGame.class)
            return;

        FreezeGamePlayer player = (FreezeGamePlayer) habbo.getHabboInfo().getGamePlayer();

        if (player == null)
            return;

        int powerUp;
        try {
            powerUp = Integer.parseInt(this.getExtradata()) / 1000;
        } catch (NumberFormatException e) {
            powerUp = 0;
        }

        if (powerUp >= 2 && powerUp <= 7) {
            if (powerUp == 6 && !player.canPickupLife())
                return;

            this.setExtradata((powerUp + 10) * 1000 + "");

            room.updateItem(this);

            game.givePowerUp(player, powerUp);

            AchievementManager.progressAchievement(player.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("FreezePowerUp"));
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }
}
