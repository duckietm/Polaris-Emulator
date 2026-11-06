package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.InteractionGameTimer;
import polaris.hotel.items.interactions.games.InteractionGameUpCounter;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.highscores.WiredHighscoreClearType;
import polaris.hotel.wired.highscores.WiredHighscoreManager;
import polaris.hotel.wired.highscores.WiredHighscoreRow;
import polaris.hotel.wired.highscores.WiredHighscoreScoreType;
import polaris.messages.ServerMessage;

public class InteractionWiredHighscore extends UserItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionWiredHighscore.class);

    public WiredHighscoreScoreType scoreType;
    public WiredHighscoreClearType clearType;

    private List<WiredHighscoreRow> data;

    public InteractionWiredHighscore(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name =
                    this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
            int ctype = Integer.parseInt(this.getBaseItem().getName().split("\\*")[1]) - 1;
            this.scoreType = WiredHighscoreScoreType.valueOf(name);
            this.clearType = WiredHighscoreClearType.values()[ctype];
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.reloadData();
    }

    public InteractionWiredHighscore(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.scoreType = WiredHighscoreScoreType.CLASSIC;
        this.clearType = WiredHighscoreClearType.ALLTIME;

        try {
            String name =
                    this.getBaseItem().getName().split("_")[1].toUpperCase().split("\\*")[0];
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
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (room == null
                || !((client != null && room.hasRights(client.getUser()))
                        || (objects.length >= 2 && objects[1] instanceof WiredEffectType))) return;

        if (this.getExtradata() == null
                || this.getExtradata().isEmpty()
                || this.getExtradata().length() == 0) {
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

        if (client != null && !(objects.length >= 2 && objects[1] instanceof WiredEffectType)) {
            WiredManager.triggerFurniStateChanged(room, client.getUser().getRoomUnit(), this);
        }
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        // Highscore stuff data; the limited flag tells the client the LTD numbers super appends follow.
        serverMessage.appendInt(6 | (this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());
        serverMessage.appendInt(this.scoreType.type);
        serverMessage.appendInt(this.clearType.type);

        if (this.data != null) {
            int size = this.data.size();
            if (size > 50) {
                size = 50;
            }
            serverMessage.appendInt(size);

            int count = 0;
            for (WiredHighscoreRow row : this.data) {
                if (count < 50) {
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
        this.warnIfNoGameCanEnd(room);
        super.onPlace(room);
    }

    /**
     * A board is only ever written by {@link polaris.hotel.games.Game#onEnd()}, which is
     * reached through a game timer. Placed in a room without one it stays empty forever and says
     * nothing about why, which is indistinguishable from a broken board.
     */
    private void warnIfNoGameCanEnd(Room room) {
        if (canEndAGame(room)) {
            return;
        }

        WiredManager.noteUnreachable(
                room.getId(),
                "A highscore board only fills when a game ends, and this room has no game timer",
                this.getBaseItem().getName(),
                this.getId());
    }

    /**
     * Whether anything in the room could bring a game to an end. A room with no timer and no
     * up-counter can never reach Game.onEnd(), which is the only writer a board has.
     */
    static boolean canEndAGame(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) {
            return false;
        }

        return !room.getRoomSpecialTypes()
                        .getItemsOfType(InteractionGameTimer.class)
                        .isEmpty()
                || !room.getRoomSpecialTypes()
                        .getItemsOfType(InteractionGameUpCounter.class)
                        .isEmpty();
    }

    @Override
    public void onPickUp(Room room) {
        if (this.data != null) {
            this.data.clear();
        }
    }

    /**
     * Reads the board again, first dropping the rows no reader can see any more (past periods compacted to each
     * team's best and worst score): runs when the board is loaded, after a game and at midnight.
     */
    public void reloadData() {
        WiredHighscoreManager manager =
                Emulator.getGameEnvironment().getItemManager().getHighscoreManager();

        manager.compact(this.getId(), this.clearType, this.scoreType);
        this.data = manager.getHighscoreRowsForItem(this.getId(), this.clearType, this.scoreType);
    }
}
