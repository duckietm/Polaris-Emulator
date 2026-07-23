package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.hotelview.HotelViewSlot;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class HotelViewLandingSaveEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotelViewLandingSaveEvent.class);
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_BODY_LENGTH = 500;
    private static final int MAX_URL_LENGTH = 512;
    private static final int MAX_CONFIG_LENGTH = 3000;
    private static final Set<String> SLOT_TYPES = Set.of(
            "bonus", "promotion", "catalogpromo", "catalogpromosmall", "expiringcatalogpage", "expiringcatalogpagesmall", "communitygoal", "dailyquest",
            "nextlimitedrarecountdown", "achievementcompetition_hall_of_fame", "achievementcompetition_prizes",
            "habbotalentspromo", "habbowaypromo", "safetyquizpromo", "habbomoderationpromo");

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() {
        Habbo habbo = this.client.getHabbo();

        if (habbo == null || habbo.getHabboInfo().getRank().getId() < 7) {
            LOGGER.warn("Rejected HotelView landing save from an unauthorized client");
            return;
        }

        HotelViewSlot slot = new HotelViewSlot(
                this.packet.readInt(),
                this.packet.readBoolean(),
                limited(this.packet.readString(), 48),
                limited(this.packet.readString(), MAX_TITLE_LENGTH),
                limited(this.packet.readString(), MAX_BODY_LENGTH),
                limited(this.packet.readString(), MAX_URL_LENGTH),
                limited(this.packet.readString(), MAX_TITLE_LENGTH),
                limited(this.packet.readString(), MAX_URL_LENGTH),
                this.packet.readInt(),
                limited(this.packet.readString(), MAX_TITLE_LENGTH),
                normalizeConfig(this.packet.readString())
        );

        if (!SLOT_TYPES.contains(slot.type())) {
            LOGGER.warn("Rejected HotelView landing slot {} with unknown type {}", slot.id(), slot.type());
            return;
        }

        if (!Emulator.getGameEnvironment().getHotelViewManager().saveSlot(slot)) {
            LOGGER.warn("Could not save HotelView landing slot {} for {}", slot.id(), habbo.getHabboInfo().getUsername());
            return;
        }

        LOGGER.info("Saved HotelView landing slot {} for {}", slot.id(), habbo.getHabboInfo().getUsername());
    }

    private static String limited(String value, int maxLength) {
        if (value == null) return "";

        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private static String normalizeConfig(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_CONFIG_LENGTH) return "{}";

        try {
            JsonElement parsed = JsonParser.parseString(value);
            if (!parsed.isJsonObject()) return "{}";

            parsed.getAsJsonObject().remove("voteCounts");
            return parsed.toString();
        } catch (RuntimeException ignored) {
            return "{}";
        }
    }
}
