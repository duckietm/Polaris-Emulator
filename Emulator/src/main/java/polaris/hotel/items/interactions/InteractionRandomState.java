package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.RandomStateParams;
import polaris.hotel.rooms.Room;

public class InteractionRandomState extends InteractionDefault {
    public InteractionRandomState(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRandomState(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        this.setExtradata("");
        room.updateItemState(this);
    }

    /** A roll is running: clicks meanwhile are ignored instead of each scheduling one more result and broadcast. */
    private volatile boolean rolling;

    public void onRandomStateClick(GameClient client, Room room) throws Exception {
        if (this.rolling) return;

        RandomStateParams params = new RandomStateParams(this.getBaseItem().getCustomParams());
        this.rolling = true;

        this.setExtradata("");
        room.updateItemState(this);

        int randomState = Emulator.getRandom().nextInt(params.getStates()) + 1;
        notifyStateChanged(room, client != null ? client.getUser().getRoomUnit() : null, this);

        Emulator.getThreading()
                .run(
                        () -> {
                            this.setExtradata(randomState + "");
                            room.updateItemState(this);
                            this.rolling = false;
                        },
                        params.getDelay());
    }
}
