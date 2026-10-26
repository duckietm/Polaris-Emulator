package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.core.WiredTextPlaceholderUtil;
import com.eu.habbo.habbohotel.wired.link.WiredLinkDataframeCodec;
import com.eu.habbo.habbohotel.wired.link.WiredLinkPayload;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Official send link message (63, ours 170, {@code wf_act_send_message}, AIR {@code SendLinkMessage}): sends a
 * message through the picked WIRED Links. Int params {@code [source]}: 0 the triggering chat text or received
 * link message ("from WIRED trigger"), 1 the custom message in the string param, placeholders resolved; at most
 * 1000 bytes ({@code wiredfurni.params.link.message_too_large}).
 */
public class WiredEffectSendLinkMessage extends WiredEffectSendLinkBase {
    private static final String NO_TEXT = "null";

    public static final WiredEffectType type = WiredEffectType.SEND_LINK_MESSAGE;
    public static final int FROM_TRIGGER = 0;
    public static final int CUSTOM_MESSAGE = 1;
    public static final String MESSAGE_TOO_LARGE = "wiredfurni.params.link.message_too_large";

    private int source = FROM_TRIGGER;
    private String message = "";

    public WiredEffectSendLinkMessage(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectSendLinkMessage(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected WiredLinkPayload.Kind kind() {
        return WiredLinkPayload.Kind.MESSAGE;
    }

    @Override
    protected WiredLinkPayload payload(WiredContext ctx) {
        String text;
        if (this.source == CUSTOM_MESSAGE) {
            text = WiredTextPlaceholderUtil.applyUsernamePlaceholders(ctx, this.message);
        } else {
            WiredEvent.Type eventType = ctx.eventType();
            // From the trigger: its text, or the literal "null" for a trigger without text (observed on AIR).
            text = (eventType == WiredEvent.Type.USER_SAYS || eventType == WiredEvent.Type.LINK_MESSAGE_RECEIVED)
                    ? ctx.event().getText().orElse(NO_TEXT)
                    : NO_TEXT;
        }
        return WiredLinkPayload.message(text);
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) throws WiredSaveException {
        int[] params = settings.getIntParams();
        String text = settings.getStringParam() == null ? "" : settings.getStringParam();
        if (WiredLinkDataframeCodec.utf8Length(text) > WiredLinkPayload.MAX_MESSAGE_BYTES) {
            throw new WiredSaveException(MESSAGE_TOO_LARGE);
        }
        this.saveEndpoints(settings);
        this.saveDelay(settings);
        this.source = params.length > 0 && params[0] == CUSTOM_MESSAGE ? CUSTOM_MESSAGE : FROM_TRIGGER;
        this.message = text;
        return true;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        this.serialize(message, room, this.message, new int[] {this.source});
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson()
                .toJson(new JsonData(this.getDelay(), this.linkIds(), this.endpointSource, this.source, this.message));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        JsonData data = WiredUtilityPayloadGuard.fromJson(set.getString("wired_data"), JsonData.class);
        this.onPickUp();
        if (data == null) return;
        this.setDelay(WiredUtilityPayloadGuard.delay(data.delay));
        this.loadLinks(data.itemIds, room);
        this.endpointSource = data.endpointSource == WiredSourceUtil.SOURCE_SELECTOR
                ? WiredSourceUtil.SOURCE_SELECTOR
                : WiredSourceUtil.SOURCE_SELECTED;
        this.source = data.source == CUSTOM_MESSAGE ? CUSTOM_MESSAGE : FROM_TRIGGER;
        this.message = data.message == null
                ? ""
                : WiredLinkPayload.message(data.message).message();
    }

    @Override
    public void onPickUp() {
        super.onPickUp();
        this.source = FROM_TRIGGER;
        this.message = "";
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }

    public String getMessage() {
        return this.message;
    }

    static class JsonData {
        int delay;
        List<Integer> itemIds;
        int endpointSource;
        int source;
        String message;

        JsonData(int delay, List<Integer> itemIds, int endpointSource, int source, String message) {
            this.delay = delay;
            this.itemIds = itemIds;
            this.endpointSource = endpointSource;
            this.source = source;
            this.message = message;
        }
    }
}
