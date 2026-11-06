package polaris.hotel.items.interactions;

import polaris.hotel.rooms.Room;

/**
 * A furni whose height follows its state through the base item's {@code multiheight} list ("1.00;1.50;2.00"):
 * the multiheight furni, and a recolourable furni that has such a list. The users on it are moved to the new height
 * when its state changes.
 */
public interface VariableHeightItem {
    void updateUnitsOnItem(Room room);
}
