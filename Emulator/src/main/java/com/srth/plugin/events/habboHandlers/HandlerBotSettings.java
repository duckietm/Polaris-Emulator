package com.srth.plugin.events.habboHandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.BotSettingsComposer;

public class HandlerBotSettings implements ICallable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HandlerRedeemItemEvent.class);

    @Override
    public void call(MessageHandler event) {

        Room room = event.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null) {
            return;
        }

        int botId = event.packet.readInt();
        Bot bot = room.getBot(Math.abs(botId));

        event.client.sendResponse(new BotSettingsComposer(bot, event.packet.readInt()));

        if (room.getOwnerId() == event.client.getHabbo().getHabboInfo().getId()
                || event.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            if (bot == null) {
                return;
            }
            event.client.sendResponse(new BotSettingsComposer(bot, event.packet.readInt()));
        }

    }
}
