package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredBotSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredEffectBotTalkToHabbo extends InteractionWiredEffect implements WiredOfficialSources {
    public static final WiredEffectType type = WiredEffectType.BOT_TALK_TO_AVATAR;

    private int mode;
    private String botName = "";
    private String message = "";
    private int userSource = WiredSourceUtil.SOURCE_TRIGGER;
    private int botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
    private int bubbleWidthOverride = RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE;
    /** Official "hide the bot name": the bubble shows the message without the bot's name. */
    private boolean hideBotName = false;

    public WiredEffectBotTalkToHabbo(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectBotTalkToHabbo(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString(this.botName + "" + ((char) 9) + "" + this.message);
        message.appendInt(3);
        message.appendInt(this.mode);
        message.appendInt(this.bubbleWidthOverride);
        message.appendInt(this.hideBotName ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());

        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger object : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!object.isTriggeredByRoomUnit()) {
                    invalidTriggers.add(object.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {};
    }

    @Override
    public int[] officialUserSources() {
        return new int[] {this.botSource, this.userSource};
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        // Official layout: [whisper / talk, bubble width, hide the bot name]; the bots are user slot 0,
        // the users talked to slot 1.
        if (settings.getIntParams().length < 1) throw new WiredSaveException("Missing mode");
        int mode = settings.getIntParams()[0];
        this.botSource =
                WiredBotSourceUtil.normalizeBotSource(settings.userSource(0, WiredBotSourceUtil.SOURCE_BOT_NAME));
        this.userSource = settings.userSource(1, WiredSourceUtil.SOURCE_TRIGGER);
        this.bubbleWidthOverride = RoomChatMessage.normalizeBubbleWidthOverride(
                (settings.getIntParams().length > 1)
                        ? settings.getIntParams()[1]
                        : RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE);
        this.hideBotName = settings.getIntParams().length > 2 && settings.getIntParams()[2] == 1;

        if (mode != 0 && mode != 1) throw new WiredSaveException("Mode is invalid");

        String[] data = WiredEffectBotTalk.splitTalkText(settings.getStringParam());

        int delay = settings.getDelay();

        if (delay > Emulator.getConfig().getInt("hotel.wired.max_delay", 20))
            throw new WiredSaveException("Delay too long");

        this.botName = data[0].substring(
                0, Math.min(data[0].length(), Emulator.getConfig().getInt("hotel.wired.message.max_length", 100)));
        this.message = data[1].substring(
                0, Math.min(data[1].length(), Emulator.getConfig().getInt("hotel.wired.bot.message.max_length", 400)));
        this.mode = mode;
        this.setDelay(delay);

        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();

        List<Bot> bots = WiredBotSourceUtil.resolveBots(ctx, room, this.botSource, this.botName);
        if (bots.isEmpty()) return;

        for (RoomUnit roomUnit : WiredSourceUtil.resolveUsers(ctx, this.userSource)) {
            Habbo habbo = room.getHabbo(roomUnit);
            if (habbo == null) continue;

            String m = this.message;
            m = m.replace(
                            Emulator.getTexts().getValue("wired.variable.username", "%username%"),
                            habbo.getHabboInfo().getUsername())
                    .replace(
                            Emulator.getTexts().getValue("wired.variable.credits", "%credits%"),
                            habbo.getHabboInfo().getCredits() + "")
                    .replace(
                            Emulator.getTexts().getValue("wired.variable.pixels", "%pixels%"),
                            habbo.getHabboInfo().getPixels() + "")
                    .replace(
                            Emulator.getTexts().getValue("wired.variable.points", "%points%"),
                            habbo.getHabboInfo()
                                            .getCurrencyAmount(
                                                    Emulator.getConfig().getInt("seasonal.primary.type")) + "")
                    .replace(Emulator.getTexts().getValue("wired.variable.owner", "%owner%"), room.getOwnerName())
                    .replace(
                            Emulator.getTexts().getValue("wired.variable.item_count", "%item_count%"),
                            room.itemCount() + "")
                    .replace(Emulator.getTexts().getValue("wired.variable.roomname", "%roomname%"), room.getName())
                    .replace(
                            Emulator.getTexts().getValue("wired.variable.user_count", "%user_count%"),
                            room.getUserCount() + "");
            m = WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, m);

            for (Bot bot : bots) {
                String botMessage =
                        m.replace(Emulator.getTexts().getValue("wired.variable.name", "%name%"), bot.getName());

                if (!WiredManager.triggerUserSays(room, bot.getRoomUnit(), botMessage)) {
                    if (this.mode == 1) {
                        bot.whisper(botMessage, habbo, this.bubbleWidthOverride, this.hideBotName);
                    } else {
                        bot.talk(
                                habbo.getHabboInfo().getUsername() + ": " + botMessage,
                                this.bubbleWidthOverride,
                                this.hideBotName);
                    }
                }
            }
        }
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    public int getBubbleWidthOverride() {
        return this.bubbleWidthOverride;
    }

    boolean isHideBotName() {
        return this.hideBotName;
    }

    @Override
    public String getWiredData() {
        JsonData data = new JsonData(
                this.botName,
                this.mode,
                this.message,
                this.getDelay(),
                this.userSource,
                this.botSource,
                this.bubbleWidthOverride);
        data.hideBotName = this.hideBotName;
        return WiredManager.getGson().toJson(data);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        JsonData jsonData = WiredEffectPayloadGuard.fromJson(wiredData, JsonData.class);
        if (jsonData != null) {
            this.setDelay(WiredEffectPayloadGuard.delay(jsonData.delay));
            this.mode = WiredEffectPayloadGuard.mode(jsonData.mode);
            this.botName = WiredEffectPayloadGuard.text(jsonData.bot_name);
            this.message = jsonData.message != null ? jsonData.message : "";
            this.userSource = WiredSourceUtil.isDefaultUserSource(jsonData.userSource)
                    ? jsonData.userSource
                    : WiredSourceUtil.SOURCE_TRIGGER;
            this.botSource = (jsonData.botSource != null)
                    ? WiredBotSourceUtil.normalizeBotSource(jsonData.botSource)
                    : WiredBotSourceUtil.SOURCE_BOT_NAME;
            this.bubbleWidthOverride = RoomChatMessage.normalizeBubbleWidthOverride(
                    (jsonData.bubbleWidthOverride != null)
                            ? jsonData.bubbleWidthOverride
                            : RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE);
            this.hideBotName = jsonData.hideBotName;
        } else {
            String[] data = wiredData != null ? wiredData.split(((char) 9) + "") : new String[0];

            if (data.length == 4) {
                this.setDelay(WiredEffectPayloadGuard.parseDelay(data[0]));
                this.mode = WiredEffectPayloadGuard.mode(WiredEffectPayloadGuard.parseInt(data[1], 0));
                this.botName = WiredEffectPayloadGuard.text(data[2]);
                this.message = data[3];
            }

            this.needsUpdate(true);
            this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
            this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        }
    }

    @Override
    public void onPickUp() {
        this.botName = "";
        this.message = "";
        this.mode = 0;
        this.userSource = WiredSourceUtil.SOURCE_TRIGGER;
        this.botSource = WiredBotSourceUtil.SOURCE_BOT_NAME;
        this.bubbleWidthOverride = RoomChatMessage.NO_BUBBLE_WIDTH_OVERRIDE;
        this.hideBotName = false;
        this.setDelay(0);
    }

    @Override
    public boolean requiresTriggeringUser() {
        return this.userSource == WiredSourceUtil.SOURCE_TRIGGER
                || WiredBotSourceUtil.requiresTriggeringUser(this.botSource)
                || WiredTextPlaceholderUtil.requiresActor(this.getRoom(), this);
    }

    static class JsonData {
        String bot_name;
        int mode;
        String message;
        int delay;
        int userSource;
        Integer botSource;
        Integer bubbleWidthOverride;
        boolean hideBotName;

        public JsonData(
                String bot_name,
                int mode,
                String message,
                int delay,
                int userSource,
                int botSource,
                int bubbleWidthOverride) {
            this.bot_name = bot_name;
            this.mode = mode;
            this.message = message;
            this.delay = delay;
            this.userSource = userSource;
            this.botSource = botSource;
            this.bubbleWidthOverride = bubbleWidthOverride;
        }
    }
}
