package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;

public class InteractionPyramid extends InteractionGate {
    private int nextChange;

    public InteractionPyramid(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionPyramid(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public void change(Room room) {
        if (!(this.getExtradata().equals("0") || this.getExtradata().equals("1"))) this.setExtradata("0");

        if (room != null) {
            if (room.getUsersAt(this.getX(), this.getY()).isEmpty()) {
                int state = Integer.parseInt(this.getExtradata());
                state = Math.abs(state - 1);

                this.setExtradata(state + "");
                room.updateItemState(this);

                this.nextChange = Emulator.getIntUnixTimestamp()
                        + 1
                        + (Emulator.getRandom().nextInt(Emulator.getConfig().getInt("pyramids.max.delay")));
            }
        }
    }

    public int getNextChange() {
        return this.nextChange;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean isUsable() {
        return false;
    }
}
