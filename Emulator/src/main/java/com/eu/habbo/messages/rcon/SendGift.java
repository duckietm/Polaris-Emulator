package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.google.gson.Gson;

public class SendGift extends RCONMessage<SendGift.SendGiftJSON> {
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 300;

    public SendGift() {
        super(SendGiftJSON.class);
    }

    @Override
    public void handle(Gson gson, SendGiftJSON json) {
        if (json.user_id <= 0) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = Emulator.getTexts().getValue("commands.error.cmd_gift.user_not_found").replace("%username%", json.user_id + "");
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
            this.message = Emulator.getTexts().getValue("commands.error.cmd_gift.not_found").replace("%itemid%", json.itemid + "");
            return;
        }

        if (!baseItem.allowGift()) {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = Emulator.getTexts().getValue("commands.error.cmd_gift.not_found").replace("%itemid%", json.itemid + "");
            return;
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(json.user_id);
        HabboInfo habboInfo = habbo != null ? habbo.getHabboInfo() : HabboManager.getOfflineHabboInfo(json.user_id);
        if (habboInfo == null) {
            this.status = RCONMessage.HABBO_NOT_FOUND;
            this.message = Emulator.getTexts().getValue("commands.error.cmd_gift.user_not_found").replace("%username%", json.user_id + "");
            return;
        }

        HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(0, baseItem, 0, 0, "");
        Item giftItem = this.randomGiftItem();
        if (item == null || giftItem == null) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "gift configuration unavailable";
            return;
        }

        String extraData = "1\t" + item.getId();
        extraData += "\t0\t0\t0\t" + sanitizeGiftMessage(json.message) + "\t0\t0";

        if (Emulator.getGameEnvironment().getItemManager().createGift(habboInfo.getUsername(), giftItem, extraData, 0, 0) == null) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "failed to create gift";
            return;
        }

        this.message = Emulator.getTexts().getValue("commands.succes.cmd_gift").replace("%username%", habboInfo.getUsername()).replace("%itemname%", item.getBaseItem().getName());

        if (habbo != null) {
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }
    }

    private Item randomGiftItem() {
        synchronized (Emulator.getGameEnvironment().getCatalogManager().giftFurnis) {
            int size = Emulator.getGameEnvironment().getCatalogManager().giftFurnis.size();
            if (size == 0) {
                return null;
            }

            Object[] giftIds = Emulator.getGameEnvironment().getCatalogManager().giftFurnis.values().toArray();
            return Emulator.getGameEnvironment().getItemManager().getItem((Integer) giftIds[Emulator.getRandom().nextInt(size)]);
        }
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

        public int user_id = -1;


        public int itemid = -1;


        public String message = "";
    }
}
