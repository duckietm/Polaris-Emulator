package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.InteractionWiredLink;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraLinkSpeaker;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import com.eu.habbo.habbohotel.wired.link.WiredLinkPayload;
import com.eu.habbo.habbohotel.wired.link.WiredLinkTransport;
import com.eu.habbo.habbohotel.wired.protocol.WiredOfficialSources;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The official send link boxes: the furni source slot 0 names the WIRED Links ({@code
 * wiredfurni.params.link.endpoints}) the stack sends through, picked or from a selector. A Link
 * Speaker add-on in the stack adds its own Links. The payload goes through {@link WiredLinkTransport}.
 */
public abstract class WiredEffectSendLinkBase extends InteractionWiredEffect implements WiredOfficialSources {
    protected final Set<HabboItem> links = new LinkedHashSet<>();
    protected int endpointSource = WiredSourceUtil.SOURCE_SELECTED;

    protected WiredEffectSendLinkBase(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    protected WiredEffectSendLinkBase(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    /** The payload this box sends for the run, or null to send nothing. */
    protected abstract WiredLinkPayload payload(WiredContext ctx);

    /** Whether a Link Speaker set to speak for this kind of send adds its Links. */
    protected abstract WiredLinkPayload.Kind kind();

    @Override
    public void execute(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) return;
        List<InteractionWiredLink> endpoints = this.endpoints(ctx, room);
        if (endpoints.isEmpty()) return;
        WiredLinkPayload payload = this.payload(ctx);
        if (payload == null) return;
        int hops = ctx.event().getLinkHops();
        for (InteractionWiredLink link : endpoints) {
            WiredLinkTransport.get().send(room, link, payload, hops);
        }
    }

    /** The Links to send through: the source, then the stack's speakers, each once. */
    protected List<InteractionWiredLink> endpoints(WiredContext ctx, Room room) {
        Map<Integer, InteractionWiredLink> endpoints = new LinkedHashMap<>();
        for (HabboItem item : WiredSourceUtil.resolveItemsRaw(ctx, this.endpointSource, new ArrayList<>(this.links))) {
            if (item instanceof InteractionWiredLink link && link.getRoomId() == room.getId()) {
                endpoints.putIfAbsent(link.getId(), link);
            }
        }
        if (room.getRoomSpecialTypes() != null) {
            for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(this.getX(), this.getY())) {
                if (extra instanceof WiredExtraLinkSpeaker speaker && speaker.speaksFor(this.kind())) {
                    for (HabboItem item : speaker.getLinks()) {
                        if (item instanceof InteractionWiredLink link && link.getRoomId() == room.getId()) {
                            endpoints.putIfAbsent(link.getId(), link);
                        }
                    }
                }
            }
        }
        List<InteractionWiredLink> result = new ArrayList<>(endpoints.values());
        return result.size() > WiredManager.MAXIMUM_FURNI_SELECTION
                ? result.subList(0, WiredManager.MAXIMUM_FURNI_SELECTION)
                : result;
    }

    /** Saves the picked Links and the endpoint source; picks that are not a Link are refused. */
    protected void saveEndpoints(WiredSettings settings) throws WiredSaveException {
        int[] furniIds = settings.getFurniIds();
        if (furniIds.length > WiredManager.MAXIMUM_FURNI_SELECTION) {
            throw new WiredSaveException("Too many furni selected");
        }
        Room room = settings.getRoom();
        if (room == null && WiredPlatform.gameEnvironment() != null) {
            room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(this.getRoomId());
        }
        List<HabboItem> picked = new ArrayList<>();
        if (room != null) {
            for (int id : furniIds) {
                HabboItem item = room.getHabboItem(id);
                if (InteractionWiredLink.isLink(item)) picked.add(item);
            }
        }
        int source = settings.furniSource(0, WiredSourceUtil.SOURCE_SELECTED);
        this.endpointSource = source == WiredSourceUtil.SOURCE_SELECTOR
                ? WiredSourceUtil.SOURCE_SELECTOR
                : WiredSourceUtil.SOURCE_SELECTED;
        this.links.clear();
        this.links.addAll(picked);
    }

    protected List<Integer> linkIds() {
        List<Integer> ids = new ArrayList<>();
        for (HabboItem item : this.links) ids.add(item.getId());
        return ids;
    }

    protected void loadLinks(List<Integer> ids, Room room) {
        this.links.clear();
        if (ids == null || room == null) return;
        for (Integer id : ids) {
            if (id == null) continue;
            HabboItem item = room.getHabboItem(id);
            if (InteractionWiredLink.isLink(item)) this.links.add(item);
        }
    }

    /** Writes the legacy definition with the box's string and int params. */
    protected void serialize(ServerMessage message, Room room, String stringParam, int[] intParams) {
        this.links.removeIf(item -> item.getRoomId() != this.getRoomId() || room.getHabboItem(item.getId()) == null);
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.links.size());
        for (HabboItem item : this.links) message.appendInt(BuildersClubItemIds.toWire(item.getId()));
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(BuildersClubItemIds.toWire(this.getId()));
        message.appendString(stringParam);
        message.appendInt(intParams.length);
        for (int value : intParams) message.appendInt(value);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.getDelay());
        if (this.requiresTriggeringUser()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredTrigger trigger : room.getRoomSpecialTypes().getTriggers(this.getX(), this.getY())) {
                if (!trigger.isTriggeredByRoomUnit())
                    invalidTriggers.add(trigger.getBaseItem().getSpriteId());
            }
            message.appendInt(invalidTriggers.size());
            for (Integer sprite : invalidTriggers) message.appendInt(sprite);
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public int[] officialFurniSources() {
        return new int[] {this.endpointSource};
    }

    @Override
    public int[] officialUserSources() {
        return new int[0];
    }

    @Override
    public boolean requiresTriggeringUser() {
        return false;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public void onPickUp() {
        this.links.clear();
        this.endpointSource = WiredSourceUtil.SOURCE_SELECTED;
        this.setDelay(0);
    }

    /** Saves the delay within the hotel's limit. */
    protected void saveDelay(WiredSettings settings) throws WiredSaveException {
        int delay = settings.getDelay();
        int max = WiredPlatform.configuration() == null
                ? 20
                : WiredPlatform.configuration().getInt("hotel.wired.max_delay", WiredInputGuard.DEFAULT_MAX_DELAY);
        if (delay > max) throw new WiredSaveException("Delay too long");
        this.setDelay(Math.max(0, delay));
    }
}
