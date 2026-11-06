package polaris.messages.incoming.users;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.ChatStyleRepository;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.MeMenuSettingsComposer;
import polaris.messages.outgoing.users.PurchasableChatStylesComposer;
import polaris.messages.outgoing.users.UserDataComposer;
import polaris.messages.outgoing.users.UserPerksComposer;

public class RequestUserDataEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestUserDataEvent.class);

    @Override
    public void handle() throws Exception {
        if (this.client.getUser() != null) {
            // this.client.sendResponse(new TestComposer());

            // this.client.sendResponse(new UserDataComposer(this.client.getUser()));
            // this.client.sendResponse(new HotelViewComposer());
            // this.client.sendResponse(new UserHomeRoomComposer());
            // this.client.sendResponse(new UserPermissionsComposer(this.client.getUser()));

            // this.client.sendResponse(new UserCreditsComposer(this.client.getUser()));
            // this.client.sendResponse(new UserCurrencyComposer(this.client.getUser()));
            // this.client.sendResponse(new FavoriteRoomsCountComposer());

            // this.client.sendResponse(new UserAchievementScoreComposer(this.client.getUser()));
            // this.client.sendResponse(new UserClothesComposer());
            // this.client.sendResponse(new
            // GenericAlertComposer(Emulator.getTexts().getValue("hotel.alert.message.welcome").replace("%user%",
            // this.client.getUser().getUserInfo().getUsername()), this.client.getUser()));

            //

            ArrayList<ServerMessage> messages = new ArrayList<>();

            messages.add(new UserDataComposer(this.client.getUser()).compose());
            messages.add(new UserPerksComposer(this.client.getUser()).compose());

            messages.add(new MeMenuSettingsComposer(this.client.getUser()).compose());

            // AIR 13 sends the chat bubble styles this account bought together with the
            // user data (SessionDataManager.onPurchasableChatStyles).
            messages.add(new PurchasableChatStylesComposer(ChatStyleRepository.findOwnedStyles(
                            this.client.getUser().getUserInfo().getId()))
                    .compose());

            //
            //

            //
            //
            //

            this.client.sendResponses(messages);

        } else {
            LOGGER.debug("Attempted to request user data where the user was null.");
            Emulator.getGameServer().getGameClientManager().disposeClient(this.client);
        }
    }
}
