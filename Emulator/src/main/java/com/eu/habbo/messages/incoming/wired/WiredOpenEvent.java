package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredConditionDataComposer;
import com.eu.habbo.messages.outgoing.wired.WiredEffectDataComposer;
import com.eu.habbo.messages.outgoing.wired.WiredTriggerDataComposer;

/**
 * The client asking for the wired box it was told to open. It is the second half of a conversation
 * the server starts: placing a wired box announces it, and this is the client coming back for the
 * settings to fill the window with.
 *
 * <p>The rights are checked here as well as there, because a packet can arrive on its own.
 */
public class WiredOpenEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || !room.canInspectWired(this.client.getHabbo())) return;

        HabboItem item = room.getHabboItem(this.packet.readInt());

        if (item == null) return;

        if (item instanceof InteractionWiredTrigger trigger) {
            this.client.sendResponse(new WiredTriggerDataComposer(trigger, room));
        } else if (item instanceof InteractionWiredEffect effect) {
            this.client.sendResponse(new WiredEffectDataComposer(effect, room));
        } else if (item instanceof InteractionWiredCondition condition) {
            this.client.sendResponse(new WiredConditionDataComposer(condition, room));
        }
    }
}
