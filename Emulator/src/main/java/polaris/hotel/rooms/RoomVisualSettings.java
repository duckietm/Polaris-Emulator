package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.awt.Color;
import polaris.hotel.items.interactions.InteractionBackgroundToner;
import polaris.hotel.users.UserItem;

final class RoomVisualSettings {

    private static final Color DEFAULT_TONER_COLOR = new Color(0, 0, 0);

    private RoomVisualSettings() {}

    static Color backgroundTonerColor(RoomItemManager itemManager) {
        Int2ObjectMap<UserItem> items = itemManager.getRoomItems();

        synchronized (items) {
            for (UserItem item : items.values()) {
                Color color = enabledTonerColor(item);
                if (color != null) {
                    return color;
                }
            }
        }

        return DEFAULT_TONER_COLOR;
    }

    private static Color enabledTonerColor(UserItem item) {
        if (!(item instanceof InteractionBackgroundToner)) {
            return null;
        }

        String[] extraData = item.getExtradata().split(":");
        if (extraData.length != 4 || !extraData[0].equalsIgnoreCase("1")) {
            return null;
        }

        return Color.getHSBColor(
                Integer.parseInt(extraData[1]), Integer.parseInt(extraData[2]), Integer.parseInt(extraData[3]));
    }
}
