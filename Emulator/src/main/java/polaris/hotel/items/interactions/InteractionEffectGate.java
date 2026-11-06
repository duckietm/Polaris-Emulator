package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.interfaces.ConditionalGate;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.threading.runnables.CloseGate;

public class InteractionEffectGate extends InteractionDefault implements ConditionalGate {

    // List of Habboween costumes according to http://www.habboxwiki.com/Costumes
    private static final List<Integer> defaultAllowedEnables = new ArrayList<>(Arrays.asList(
            114, // Strong Arms
            115, // Ringmaster Costume
            116, // Fly Head
            117, // Executioner Hood
            118, // Evil Clown Paint
            135 // Marionette
            ));

    public InteractionEffectGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionEffectGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        if (roomUnit == null || room == null) return false;

        String customparams = this.getBaseItem().getCustomParams().trim();

        if (!customparams.isEmpty()) {
            return Arrays.asList(customparams.split(";"))
                    .contains(Integer.valueOf(roomUnit.getEffectId()).toString());
        }

        return defaultAllowedEnables.contains(roomUnit.getEffectId());
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.canWalkOn(roomUnit, room, objects)) {
            this.setExtradata("1");
            room.updateItemState(this);
        }
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);

        Emulator.getThreading().run(new CloseGate(this, room), 1000);
    }

    @Override
    public void onRejected(RoomUnit roomUnit, Room room, Object[] objects) {}
}
