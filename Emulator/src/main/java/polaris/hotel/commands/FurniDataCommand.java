package polaris.hotel.commands;

import java.util.Locale;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurniDataCommand extends Command {

    public static final String FURNIDATA_KEY = "furnidata.ison";

    private static final int EXTRADATA_PREVIEW_LENGTH = 200;

    public FurniDataCommand() {
        super(
                "cmd_furnidata",
                Emulator.getTexts().getValue("commands.keys.cmd_furnidata").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User user = gameClient.getUser();

        if (user.getUserStats().cache.remove(FURNIDATA_KEY) != null) {
            user.whisper(Emulator.getTexts().getValue("furnidata.cmd_furnidata.off"));
        } else {
            user.getUserStats().cache.put(FURNIDATA_KEY, Boolean.TRUE);
            user.whisper(Emulator.getTexts().getValue("furnidata.cmd_furnidata.on"));
        }

        return true;
    }

    public static boolean isInspecting(User user) {
        return user != null
                && user.getUserStats() != null
                && user.getUserStats().cache.containsKey(FURNIDATA_KEY)
                && user.hasPermission("cmd_furnidata");
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public static String buildItemInfo(UserItem item, int state) {
        Item base = item.getBaseItem();

        StringBuilder message = new StringBuilder("Information for item: <b>")
                .append(base.getFullName())
                .append("</b>\r\n")
                .append("<b>items_base table</b>\r\n")
                .append("- id: ")
                .append(base.getId())
                .append("\r")
                .append("- sprite_id: ")
                .append(base.getSpriteId())
                .append("\r")
                .append("- Width: ")
                .append(base.getWidth())
                .append("\r")
                .append("- Length: ")
                .append(base.getLength())
                .append("\r")
                .append("- Stack height: ")
                .append(formatDouble(base.getHeight()))
                .append("\r")
                .append("- Allow stack: ")
                .append(base.allowStack())
                .append("\r")
                .append("- Allow walk: ")
                .append(base.allowWalk())
                .append("\r")
                .append("- Allow sit: ")
                .append(base.allowSit())
                .append("\r")
                .append("- Allow lay: ")
                .append(base.allowLay())
                .append("\r")
                .append("- Allow recycle: ")
                .append(base.allowRecyle())
                .append("\r")
                .append("- Allow trade: ")
                .append(base.allowTrade())
                .append("\r")
                .append("- Allow marketplace sell: ")
                .append(base.allowMarketplace())
                .append("\r")
                .append("- Allow gift: ")
                .append(base.allowGift())
                .append("\r")
                .append("- Allow inventory stack: ")
                .append(base.allowInventoryStack())
                .append("\r")
                .append("- Interaction type: ")
                .append(base.getInteractionType().getName())
                .append("\r")
                .append("- Interaction count: ")
                .append(base.getStateCount())
                .append("\r");

        if (base.getVendingItems() != null && !base.getVendingItems().isEmpty()) {
            message.append("- Vending ids: ");
            for (int i = 0; i < base.getVendingItems().size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(base.getVendingItems().getInt(i));
            }
            message.append("\r");
        }

        message.append("- effect id male: ")
                .append(base.getEffectM())
                .append("\r")
                .append("- effect id female: ")
                .append(base.getEffectF())
                .append("\r");

        double[] multiHeights = base.getMultiHeights();
        if (multiHeights != null && multiHeights.length > 0) {
            message.append("- Multi height: ");
            for (int i = 0; i < multiHeights.length; i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append(formatDouble(multiHeights[i]));
            }
            message.append("\r");
        }

        String extradata = item.getExtradata();
        if (extradata != null && extradata.length() > EXTRADATA_PREVIEW_LENGTH) {
            extradata = extradata.substring(0, EXTRADATA_PREVIEW_LENGTH) + "...";
        }

        message.append("\r\n<b>items/room</b>\r\n")
                .append("- item id: ")
                .append(item.getId())
                .append("\r")
                .append("- user id: ")
                .append(item.getUserId())
                .append("\r")
                .append("- x: ")
                .append(item.getX())
                .append("\r")
                .append("- y: ")
                .append(item.getY())
                .append("\r")
                .append("- z: ")
                .append(formatDouble(item.getZ()))
                .append("\r")
                .append("- Rotation: ")
                .append(item.getRotation())
                .append("\r")
                .append("- Extradata: ")
                .append(extradata)
                .append("\r")
                .append("- State (clicked): ")
                .append(state)
                .append("\r");

        return message.toString();
    }
}
