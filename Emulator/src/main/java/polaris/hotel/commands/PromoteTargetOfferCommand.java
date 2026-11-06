package polaris.hotel.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.catalog.TargetOffer;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.messages.outgoing.catalog.TargetedOfferComposer;
import polaris.messages.outgoing.generic.alerts.MessagesForYouComposer;

public class PromoteTargetOfferCommand extends Command {

    public PromoteTargetOfferCommand() {
        super(
                "cmd_promote_offer",
                Emulator.getTexts().getValue("commands.keys.cmd_promote_offer").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length <= 1) {
            gameClient.getUser().whisper(Emulator.getTexts().getValue("commands.error.cmd_promote_offer.not_found"));
            return true;
        }

        String offerKey = params[1];

        if (offerKey.equalsIgnoreCase(Emulator.getTexts().getValue("commands.cmd_promote_offer.info"))) {
            Map<Integer, TargetOffer> targetOffers =
                    Emulator.getGameEnvironment().getCatalogManager().getTargetOffersSnapshot();
            String[] textConfig = Emulator.getTexts()
                    .getValue("commands.cmd_promote_offer.list")
                    .replace("%amount%", targetOffers.size() + "")
                    .split("<br>");

            String entryConfig = Emulator.getTexts().getValue("commands.cmd_promote_offer.list.entry");
            List<String> message = new ArrayList<>();

            for (String pair : textConfig) {
                if (pair.contains("%list%")) {
                    for (TargetOffer offer : targetOffers.values()) {
                        message.add(entryConfig
                                .replace("%id%", offer.getId() + "")
                                .replace("%title%", offer.getTitle())
                                .replace("%description%", listedDescription(offer.getDescription())));
                    }
                } else {
                    message.add(pair);
                }
            }

            gameClient.sendResponse(new MessagesForYouComposer(message));
        } else {
            int offerId = 0;
            try {
                offerId = Integer.parseInt(offerKey);
            } catch (Exception e) {
            }

            if (offerId > 0) {
                TargetOffer offer =
                        Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(offerId);

                if (offer != null) {
                    TargetOffer.ACTIVE_TARGET_OFFER_ID = offer.getId();
                    gameClient
                            .getUser()
                            .whisper(Emulator.getTexts()
                                    .getValue("commands.succes.cmd_promote_offer")
                                    .replace("%id%", offerKey)
                                    .replace("%title%", offer.getTitle()));

                    for (User user : Emulator.getGameEnvironment()
                            .getUserManager()
                            .getOnlineUsers()
                            .values()) {
                        user.getClient().sendResponse(new TargetedOfferComposer(user, offer));
                    }
                }
            } else {
                gameClient
                        .getUser()
                        .whisper(Emulator.getTexts().getValue("commands.error.cmd_promote_offer.not_found"));
                return true;
            }
        }

        return true;
    }

    /** The first 25 characters of a description; a shorter one is listed whole instead of throwing. */
    static String listedDescription(String description) {
        if (description == null) return "";
        return description.substring(0, Math.min(25, description.length()));
    }
}
