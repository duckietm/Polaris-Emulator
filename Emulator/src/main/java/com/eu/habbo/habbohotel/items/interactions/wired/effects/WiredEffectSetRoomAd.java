package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredRewardPolicy;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.navigation.EventCategory;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import com.eu.habbo.messages.outgoing.navigator.NewNavigatorEventCategoriesComposer;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Sets or refreshes the room's promotion (the "room ad" event shown in navigator promotion lists).
 * Carries a {@code stringParam} that packs {@code caption\tdescription} and a single optional int
 * {@code category}. On execute it drives the existing promotion subsystem via
 * {@link com.eu.habbo.habbohotel.rooms.RoomPromotionManager#createPromotion(String, String, int)},
 * which creates a brand-new promotion or, if one already exists, updates its title/description/
 * category and pushes the end timestamp forward (the standard 2-hour window) and persists to
 * {@code room_promotions}. This needs a dedicated dialog (caption + description + category picker),
 * so it uses the NEW client code {@link WiredEffectType#SET_ROOM_AD} (94) with a matching Nitro
 * {@code WiredActionLayoutCode.SET_ROOM_AD} component.
 */
public class WiredEffectSetRoomAd extends InteractionWiredEffect {
    public static final WiredEffectType type = WiredEffectType.SET_ROOM_AD;

    private static final int MIN_CATEGORY = 0;
    private static final int MAX_CATEGORY = 50;
    private static final int DEFAULT_CATEGORY = 0;
    private static final int DEFAULT_MAX_CAPTION_LENGTH = 60;
    private static final int DEFAULT_MAX_DESCRIPTION_LENGTH = 200;

    private String caption = "";
    private String description = "";
    private int category = DEFAULT_CATEGORY;

    public WiredEffectSetRoomAd(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSetRoomAd(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return;
        }

        if (this.caption.isEmpty() && this.description.isEmpty()) {
            return;
        }

        // createPromotion also pushes the end timestamp to now + 2 hours and writes the
        // row synchronously, so a repeater used to renew the promotion - and INSERT ...
        // ON DUPLICATE - twice a second, forever. A promotion that is already running
        // is left alone; the box starts the next one once it has lapsed.
        if (room.getPromotionManager().isPromoted()) {
            return;
        }

        room.getPromotionManager().createPromotion(this.caption, this.description, this.category);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.caption + "\t" + this.description);
        message.appendInt(1);
        message.appendInt(this.category);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(this.getDelay());
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // A room ad is value out of nothing: BuyRoomPromotionEvent charges credits and
        // points for the same two hours this box grants for free, and the title it
        // writes is shown across the whole hotel.
        if (!WiredRewardPolicy.canConfigure(gameClient)) {
            return false;
        }

        int[] params = settings.getIntParams();
        this.category = (params.length > 0) ? resolveCategory(params[0]) : DEFAULT_CATEGORY;

        String raw = settings.getStringParam();
        raw = (raw == null) ? "" : raw;
        String[] parts = raw.split("\t", -1);
        this.caption = filter(clampText(parts.length > 0 ? parts[0] : "", maxCaptionLength()), gameClient);
        this.description = filter(clampText(parts.length > 1 ? parts[1] : "", maxDescriptionLength()), gameClient);

        int delay = settings.getDelay();
        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY)) {
            throw new WiredSaveException("Delay too long");
        }

        this.setDelay(delay);
        return true;
    }

    private static int clampCategory(int value) {
        return Math.max(MIN_CATEGORY, Math.min(MAX_CATEGORY, value));
    }

    /**
     * A category the navigator actually offers. Clamping to 0..50 accepted ids that
     * exist nowhere, which the client then cannot render; the purchase path picks from
     * the same list this checks against.
     */
    private static int resolveCategory(int value) {
        int clamped = clampCategory(value);
        if (clamped == DEFAULT_CATEGORY) return DEFAULT_CATEGORY;

        for (EventCategory category : NewNavigatorEventCategoriesComposer.CATEGORIES) {
            if (category != null && category.getId() == clamped && category.isVisible()) {
                return clamped;
            }
        }
        return DEFAULT_CATEGORY;
    }

    /** The same word filter the purchased promotion text goes through. */
    private static String filter(String value, GameClient gameClient) {
        if (value == null || value.isEmpty()) return "";

        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null || environment.getWordFilter() == null) {
            return value;
        }
        return environment.getWordFilter().filter(value, gameClient == null ? null : gameClient.getHabbo());
    }

    private static String clampText(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String cleaned =
                value.replace("\t", "").replace("\r", "").replace("\n", " ").trim();
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    private static int maxCaptionLength() {
        // Reached while loading a furni, which can happen before the configuration exists.
        ConfigurationManager config = Emulator.getConfig();
        return config == null
                ? DEFAULT_MAX_CAPTION_LENGTH
                : config.getInt("hotel.wired.set_room_ad.caption_max_length", DEFAULT_MAX_CAPTION_LENGTH);
    }

    private static int maxDescriptionLength() {
        // Reached while loading a furni, which can happen before the configuration exists.
        ConfigurationManager config = Emulator.getConfig();
        return config == null
                ? DEFAULT_MAX_DESCRIPTION_LENGTH
                : config.getInt("hotel.wired.set_room_ad.description_max_length", DEFAULT_MAX_DESCRIPTION_LENGTH);
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.getDelay(), this.caption, this.description, this.category));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = WiredEffectPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        if (data != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(data.delay));
            this.caption = clampText(WiredEffectPayloadGuard.text(data.caption), maxCaptionLength());
            this.description = clampText(WiredEffectPayloadGuard.text(data.description), maxDescriptionLength());
            this.category = clampCategory(data.category);
        } else {
            this.setDelay(0);
            this.caption = "";
            this.description = "";
            this.category = DEFAULT_CATEGORY;
        }
    }

    @Override
    public void onPickUp() {
        this.caption = "";
        this.description = "";
        this.category = DEFAULT_CATEGORY;
        this.setDelay(0);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    static class JsonData {
        int delay;
        String caption;
        String description;
        int category;

        public JsonData(int delay, String caption, String description, int category) {
            this.delay = delay;
            this.caption = caption;
            this.description = description;
            this.category = category;
        }
    }
}
