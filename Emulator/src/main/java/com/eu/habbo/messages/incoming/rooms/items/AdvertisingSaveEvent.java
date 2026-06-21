package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionCustomValues;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomAds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import gnu.trove.map.hash.THashMap;

public class AdvertisingSaveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        if (!room.hasRights(this.client.getHabbo()))
            return;

        int itemId = this.packet.readInt();
        if (!RoomItemInputGuard.isPositiveId(itemId))
            return;

        HabboItem item = room.getHabboItem(itemId);
        if (item == null)
            return;

        if (item instanceof InteractionRoomAds && !this.client.getHabbo().hasPermission("acc_ads_background")) {
            this.client.getHabbo().alert(Emulator.getTexts().getValue("hotel.error.roomads.nopermission"));
            return;
        }
        if (item instanceof InteractionCustomValues) {
            THashMap<String, String> oldValues = new THashMap<>(((InteractionCustomValues) item).values);
            int count = this.packet.readInt();
            if (!RoomItemInputGuard.isValidCustomValueCount(count))
                return;

            for (int i = 0; i < count / 2; i++) {
                String key = RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_KEY_LENGTH);
                String value = RoomItemInputGuard.trimToMax(this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_VALUE_LENGTH);

                if (key.isEmpty())
                    continue;

                if (!Emulator.getConfig().getBoolean("camera.use.https")) {
                    value = value.replace("https://", "http://");
                }

                ((InteractionCustomValues) item).values.put(key, value);
            }

            item.setExtradata(((InteractionCustomValues) item).toExtraData());
            item.needsUpdate(true);
            Emulator.getThreading().run(item);
            room.updateItem(item);
            ((InteractionCustomValues) item).onCustomValuesSaved(room, this.client, oldValues);
        }
    }
}
