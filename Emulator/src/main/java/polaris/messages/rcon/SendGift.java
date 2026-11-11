package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserManager;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

public class SendGift extends RCONMessage<SendGift.SendGiftJSON> {
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 300;

    public SendGift() {
        super(SendGiftJSON.class);
    }

    @Override
    public void handle(Gson gson, SendGiftJSON json) {
        if (json.user_id <= 0) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = Emulator.getTexts()
                    .getValue("commands.error.cmd_gift.user_not_found")
                    .replace("%username%", json.user_id + "");
            return;
        }

        if (json.itemid <= 0) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = Emulator.getTexts().getValue("commands.error.cmd_gift.not_a_number");
            return;
        }

        Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(json.itemid);
        if (baseItem == null) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = Emulator.getTexts()
                    .getValue("commands.error.cmd_gift.not_found")
                    .replace("%itemid%", json.itemid + "");
            return;
        }

        if (!baseItem.allowGift()) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = Emulator.getTexts()
                    .getValue("commands.error.cmd_gift.not_found")
                    .replace("%itemid%", json.itemid + "");
            return;
        }

        User user = Emulator.getGameEnvironment().getUserManager().getUser(json.user_id);
        UserInfo userInfo = user != null ? user.getUserInfo() : UserManager.getOfflineUserInfo(json.user_id);
        if (userInfo == null) {
            this.status = RCONMessage.USER_NOT_FOUND;
            this.message = Emulator.getTexts()
                    .getValue("commands.error.cmd_gift.user_not_found")
                    .replace("%username%", json.user_id + "");
            return;
        }

        UserItem item =
                Emulator.getGameEnvironment().getItemManager().createGiftContent(userInfo.getId(), baseItem, 0, 0, "");
        Item giftItem = this.randomGiftItem();
        if (item == null || giftItem == null) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "gift configuration unavailable";
            return;
        }

        String extraData = "1\t" + item.getId();
        // A gift sent over RCON is from the hotel: trusted.
        extraData +=
                "\t0\t0\t0\t" + sanitizeGiftMessage(json.message) + "\t0\t0" + InteractionGift.trustedSenderField(true);

        if (Emulator.getGameEnvironment().getItemManager().createGift(userInfo.getUsername(), giftItem, extraData, 0, 0)
                == null) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "failed to create gift";
            return;
        }

        this.message = Emulator.getTexts()
                .getValue("commands.succes.cmd_gift")
                .replace("%username%", userInfo.getUsername())
                .replace("%itemname%", item.getBaseItem().getName());

        if (user != null) {
            user.getClient().sendResponse(new InventoryRefreshComposer());
        }
    }

    private Item randomGiftItem() {
        Map<Integer, Integer> giftFurnis = Emulator.getGameEnvironment()
                .getCatalogManager()
                .getGiftWrappingSnapshot()
                .furniture();
        int size = giftFurnis.size();
        if (size == 0) {
            return null;
        }

        Object[] giftIds = giftFurnis.values().toArray();
        return Emulator.getGameEnvironment().getItemManager().getItem((Integer)
                giftIds[Emulator.getRandom().nextInt(size)]);
    }

    static String sanitizeGiftMessage(String message) {
        int maxLength = Emulator.getConfig().getInt("hotel.gifts.length.max", DEFAULT_MAX_MESSAGE_LENGTH);
        if (maxLength <= 0) {
            maxLength = DEFAULT_MAX_MESSAGE_LENGTH;
        }

        if (message == null) {
            return "";
        }

        String sanitized = message.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength);
        }

        return sanitized;
    }

    static class SendGiftJSON {

        @Positive(message = "invalid user")
        public int user_id = -1;

        @Positive(message = "invalid item")
        public int itemid = -1;

        public String message = "";
    }
}
