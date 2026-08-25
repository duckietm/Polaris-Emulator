package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.GamePlayer;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredBlob extends InteractionDefault {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredBlob.class);

    enum WiredBlobState {
        ACTIVE("0"),
        USED("1");

        private String state;
        WiredBlobState(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    private int POINTS_REWARD = 0;
    private boolean RESETS_WITH_GAME = true;

    public WiredBlob(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        this.parseCustomParams();
    }

    public WiredBlob(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.parseCustomParams();
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        this.setExtradata(WiredBlobState.USED.getState());
        room.updateItem(this);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (!this.getExtradata().equals(WiredBlobState.ACTIVE.getState())) return;

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo != null) {
            GamePlayer player = habbo.getHabboInfo().getGamePlayer();

            if (player != null) {
                player.addScore(this.POINTS_REWARD, true);

                BattleBanzaiGame battleBanzaiGame = (BattleBanzaiGame) room.getGame(BattleBanzaiGame.class);

                if (battleBanzaiGame != null && battleBanzaiGame.getState() != GameState.IDLE) {
                    battleBanzaiGame.refreshCounters(habbo.getHabboInfo().getGamePlayer().getTeamColor());
                }

                this.setExtradata(WiredBlobState.USED.getState());
                room.updateItem(this);
            }
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (!this.RESETS_WITH_GAME && objects != null && objects.length == 2 && objects[1].equals(WiredEffectType.TOGGLE_STATE) && room.getGames().stream().anyMatch(game -> game.getState().equals(GameState.RUNNING) || game.getState().equals(GameState.PAUSED))) {
            this.setExtradata(this.getExtradata().equals(WiredBlobState.ACTIVE.getState()) ? WiredBlobState.USED.getState() : WiredBlobState.ACTIVE.getState());
            room.updateItem(this);
        }
    }

    public void onGameStart(Room room) {
        if (this.RESETS_WITH_GAME) {
            this.setExtradata(WiredBlobState.ACTIVE.getState());
            room.updateItem(this);
        }
    }

    public void onGameEnd(Room room) {
        this.setExtradata(WiredBlobState.USED.getState());
        room.updateItem(this);
    }

    private void parseCustomParams() {
        String[] params = this.getBaseItem().getCustomParams().split(",");

        if (params.length != 2) {
            LOGGER.error("Wired blobs should have customparams with two parameters (points,resetsWithGame)");
            return;
        }

        try {
            this.POINTS_REWARD = Integer.parseInt(params[0]);
        } catch (NumberFormatException e) {
            LOGGER.error("Wired blobs should have customparams with the first parameter being the amount of points (number)");
            return;
        }

        this.RESETS_WITH_GAME = params[1].equalsIgnoreCase("true");
    }
}
