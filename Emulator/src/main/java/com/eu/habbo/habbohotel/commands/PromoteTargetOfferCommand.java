package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.catalog.TargetedOfferComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.MessagesForYouComposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PromoteTargetOfferCommand extends Command {

    public PromoteTargetOfferCommand() {
        super("cmd_promote_offer", Emulator.getTexts().getValue("commands.keys.cmd_promote_offer").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length <= 1) {
            gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_promote_offer.not_found"));
            return true;
        }

        String offerKey = params[1];

        if (offerKey.equalsIgnoreCase(Emulator.getTexts().getValue("commands.cmd_promote_offer.info"))) {
            Map<Integer, TargetOffer> targetOffers = Emulator.getGameEnvironment().getCatalogManager().targetOffers;
            String[] textConfig = Emulator.getTexts().getValue("commands.cmd_promote_offer.list").replace("%amount%", targetOffers.size() + "").split("<br>");

            String entryConfig = Emulator.getTexts().getValue("commands.cmd_promote_offer.list.entry");
            List<String> message = new ArrayList<>();

            for (String pair : textConfig) {
                if (pair.contains("%list%")) {
                    for (TargetOffer offer : targetOffers.values()) {
                        message.add(entryConfig.replace("%id%", offer.getId() + "").replace("%title%", offer.getTitle()).replace("%description%", offer.getDescription().substring(0, 25)));
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
                TargetOffer offer = Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(offerId);

                if (offer != null) {
                    TargetOffer.ACTIVE_TARGET_OFFER_ID = offer.getId();
                    gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.succes.cmd_promote_offer").replace("%id%", offerKey).replace("%title%", offer.getTitle()));

                    for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
                        habbo.getClient().sendResponse(new TargetedOfferComposer(habbo, offer));
                    }
                }
            } else {
                gameClient.getHabbo().whisper(Emulator.getTexts().getValue("commands.error.cmd_promote_offer.not_found"));
                return true;
            }
        }

        return true;
    }
}
