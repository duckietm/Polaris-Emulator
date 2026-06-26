package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.users.MeMenuSettingsComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserPerksComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class RequestUserDataEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestUserDataEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo() != null) {
            //this.client.sendResponse(new TestComposer());

            //this.client.sendResponse(new UserDataComposer(this.client.getHabbo()));
            //this.client.sendResponse(new HotelViewComposer());
            //this.client.sendResponse(new UserHomeRoomComposer());
            //this.client.sendResponse(new UserPermissionsComposer(this.client.getHabbo()));

            //this.client.sendResponse(new UserCreditsComposer(this.client.getHabbo()));
            //this.client.sendResponse(new UserCurrencyComposer(this.client.getHabbo()));
            //this.client.sendResponse(new FavoriteRoomsCountComposer());

            //this.client.sendResponse(new UserAchievementScoreComposer(this.client.getHabbo()));
            //this.client.sendResponse(new UserClothesComposer());
            //this.client.sendResponse(new GenericAlertComposer(Emulator.getTexts().getValue("hotel.alert.message.welcome").replace("%user%", this.client.getHabbo().getHabboInfo().getUsername()), this.client.getHabbo()));


            //

            var messages = new ArrayList<ServerMessage>();


            messages.add(new UserDataComposer(this.client.getHabbo()).compose());
            messages.add(new UserPerksComposer(this.client.getHabbo()).compose());

            messages.add(new MeMenuSettingsComposer(this.client.getHabbo()).compose());


//
//

//
//
//


            this.client.sendResponses(messages);


        } else {
            LOGGER.debug("Attempted to request user data where Habbo was null.");
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
        }
    }
}
