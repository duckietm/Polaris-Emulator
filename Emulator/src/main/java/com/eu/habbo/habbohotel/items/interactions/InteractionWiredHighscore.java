package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreClearType;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreRow;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreScoreType;
import com.eu.habbo.messages.ServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InteractionWiredHighscore extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionWiredHighscore.class);

    public WiredHighscoreScoreType scoreType;
    public WiredHighscoreClearType clearType;

    private List<WiredHighscoreRow> data;

    public InteractionWiredHighscore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name = this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
            int ctype = Integer.parseInt(this.getBaseItem().getName().split("\\*")[1]) - 1;
            this.scoreType = WiredHighscoreScoreType.valueOf(name);
            this.clearType = WiredHighscoreClearType.values()[ctype];
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.reloadData();
    }

    public InteractionWiredHighscore(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name = this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
            int ctype = Integer.parseInt(this.getBaseItem().getName().split("\\*")[1]) - 1;
            this.scoreType = WiredHighscoreScoreType.valueOf(name);
            this.clearType = WiredHighscoreClearType.values()[ctype];
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.reloadData();
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room == null || !((client != null && room.hasRights(client.getHabbo())) || (objects.length >= 2 && objects[1] instanceof WiredEffectType)))
            return;

        if (this.getExtradata() == null || this.getExtradata().isEmpty() || this.getExtradata().length() == 0) {
            this.setExtradata("0");
        }

        try {
            int state = Integer.parseInt(this.getExtradata());
            this.setExtradata(Math.abs(state - 1) + "");
            this.needsUpdate(true);
            room.updateItem(this);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        if(client != null && !(objects.length >= 2 && objects[1] instanceof WiredEffectType)) {
            WiredManager.triggerFurniStateChanged(room, client.getHabbo().getRoomUnit(), this);
        }
    }


    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(6);
        serverMessage.appendString(this.getExtradata());
        serverMessage.appendInt(this.scoreType.type);
        serverMessage.appendInt(this.clearType.type);

        if (this.data != null) {
            int size = this.data.size();
            if(size > 50) {
                size = 50;
            }
            serverMessage.appendInt(size);

            int count = 0;
            for (WiredHighscoreRow row : this.data) {
                if(count < 50) {
                    serverMessage.appendInt(row.getValue());

                    serverMessage.appendInt(row.getUsers().size());
                    for (String username : row.getUsers()) {
                        serverMessage.appendString(username);
                    }
                }
                count++;
            }
        } else {
            serverMessage.appendInt(0);
        }

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onPlace(Room room) {
        this.reloadData();
        super.onPlace(room);
    }

    @Override
    public void onPickUp(Room room) {
        if (this.data != null) {
            this.data.clear();
        }
    }

    public void reloadData() {
        this.data = Emulator.getGameEnvironment().getItemManager().getHighscoreManager().getHighscoreRowsForItem(this.getId(), this.clearType, this.scoreType);
    }
}