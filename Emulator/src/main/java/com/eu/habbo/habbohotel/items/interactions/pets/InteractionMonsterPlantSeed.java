package com.eu.habbo.habbohotel.items.interactions.pets;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionMonsterPlantSeed extends HabboItem {
    public InteractionMonsterPlantSeed(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        this.assignRarityWhenUnset();
    }

    public InteractionMonsterPlantSeed(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.assignRarityWhenUnset();
    }

    /**
     * The seed's extra data is its rarity plus one ("0" or empty: not rolled yet, as {@code HabboItem} stores an empty
     * extra data as "0"). A seed rolls its rarity once, the golden table for a rare seed, so the inventory, the room
     * and the planting all agree on it.
     */
    private void assignRarityWhenUnset() {
        if (rarityOf(this.getExtradata()) >= 0) return;

        boolean rare = this.getBaseItem() != null
                && this.getBaseItem().getName() != null
                && this.getBaseItem().getName().contains("rare");
        this.setExtradata("" + (rare ? randomGoldenRarityLevel() : randomRarityLevel()));
        this.needsUpdate(true);
    }

    /** The rarity level a seed's extra data holds (AIR {@code MapStuffData} "rarity"), -1 when it has none yet. */
    public static int rarityOf(String extradata) {
        if (extradata == null || extradata.isEmpty()) return -1;
        try {
            return Integer.parseInt(extradata.trim()) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The extra data of a seed of rarity {@code level} (bred seeds carry their parent's rarity). */
    public static String extradataFor(int level) {
        return String.valueOf(Math.max(0, level) + 1);
    }

    public int getRarityLevel() {
        return Math.max(0, rarityOf(this.getExtradata()));
    }

    public static int randomGoldenRarityLevel() {
        int number = Emulator.getRandom().nextInt(66);
        int count = 0;
        for (int i = 8; i < 11; i++) {
            count += 11 - i;
            if (number <= count) {
                return i;
            }
        }
        return 10;
    }

    public static int randomRarityLevel() {
        int number = Emulator.getRandom().nextInt(66);
        int count = 0;
        for (int i = 1; i < 11; i++) {
            count += 11 - i;
            if (number <= count) {
                return i;
            }
        }
        return 10;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt(1 + (this.isLimited() ? 256 : 0));
        serverMessage.appendInt(1);
        serverMessage.appendString("rarity");
        serverMessage.appendString(String.valueOf(this.getRarityLevel()));

        super.serializeExtradata(serverMessage);
    }
}
