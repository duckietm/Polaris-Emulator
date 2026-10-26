package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.items.RoomBackgroundSettings;
import com.eu.habbo.habbohotel.items.interactions.InteractionCustomValues;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoomAds;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdvertisingSaveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;

        if (!room.hasRights(this.client.getHabbo())) return;

        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        HabboItem item = room.getHabboItem(itemId);
        if (item == null) return;

        TextsManager texts = Emulator.getTexts();

        if (item instanceof InteractionRoomAds && !this.client.getHabbo().hasPermission("acc_ads_background")) {
            this.client.getHabbo().alert(texts.getValue("hotel.error.roomads.nopermission"));
            return;
        }
        if (item instanceof InteractionCustomValues) {
            InteractionCustomValues customValues = (InteractionCustomValues) item;
            Map<String, String> oldValues = new HashMap<>(customValues.values);
            int count = this.packet.readInt();
            if (!RoomItemInputGuard.isValidCustomValueCount(count)) return;

            ConfigurationManager config = Emulator.getConfig();
            Map<String, String> received = new LinkedHashMap<>();

            for (int i = 0; i < count / 2; i++) {
                String key = RoomItemInputGuard.trimToMax(
                        this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_KEY_LENGTH);
                String value = RoomItemInputGuard.trimToMax(
                        this.packet.readString(), RoomItemInputGuard.MAX_CUSTOM_VALUE_LENGTH);

                if (key.isEmpty()) continue;

                if (!config.getBoolean("camera.use.https")) {
                    value = value.replace("https://", "http://");
                }

                received.put(key, value);
            }

            if (item instanceof InteractionRoomAds) {
                received = RoomBackgroundSettings.sanitize(
                        received, config.getValue(RoomBackgroundSettings.ALLOWED_HOSTS_SETTING, ""));

                if (received == null) {
                    this.client
                            .getHabbo()
                            .whisper(texts.getValue(
                                    "hotel.error.roomads.invalid",
                                    "The room background was not saved: check the numbers and the image address."));
                    return;
                }

                // Keys stored before the save was checked go too.
                customValues.values.keySet().removeIf(key -> !RoomBackgroundSettings.isKnownKey(key));
            }

            customValues.values.putAll(received);

            item.setExtradata(customValues.toExtraData());
            item.needsUpdate(true);
            Emulator.getThreading().run(item);
            room.updateItem(item);
            customValues.onCustomValuesSaved(room, this.client, oldValues);
        }
    }
}
