package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.hotelview.HotelViewScene;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class HotelViewLandingSaveSceneEvent extends MessageHandler {
    private static final int MAX_URL_LENGTH = 512;

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getHabboInfo().getRank().getId() < 7) return;

        Emulator.getGameEnvironment().getHotelViewManager().saveScene(new HotelViewScene(
                limited(this.packet.readString()),
                limited(this.packet.readString()),
                limited(this.packet.readString()),
                limited(this.packet.readString()),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readInt(),
                this.packet.readBoolean(),
                this.packet.readString(),
                this.packet.readInt(),
                java.util.List.of()
        ));
    }

    private static String limited(String value) {
        if (value == null) return "";

        String normalized = value.trim();
        return normalized.length() > MAX_URL_LENGTH ? normalized.substring(0, MAX_URL_LENGTH) : normalized;
    }
}
