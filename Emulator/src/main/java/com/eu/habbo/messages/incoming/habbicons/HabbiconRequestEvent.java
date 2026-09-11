package com.eu.habbo.messages.incoming.habbicons;

import com.eu.habbo.habbohotel.habbicons.HabbiconService;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconInfoComposer;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconResultComposer;
import com.eu.habbo.messages.outgoing.habbicons.HabbiconShopComposer;
import com.eu.habbo.messages.outgoing.habbicons.UserHabbiconsComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HabbiconRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabbiconRequestEvent.class);

    @Override
    public void handle() {
        HabbiconService service = client.getHabbo().getHabbiconService();
        int header = packet.getMessageId();
        int id = header == Incoming.GetHabbiconShopDataEvent ? 0 : packet.readInt();
        int action = header - Incoming.BuyHabbiconEvent;
        try {
            if (header == Incoming.GetHabbiconShopDataEvent) {
                HabbiconService.Snapshot snapshot =
                        service.load(client.getHabbo().getHabboInfo().getId());
                client.sendResponse(new UserHabbiconsComposer(snapshot));
                client.sendResponse(new HabbiconShopComposer(snapshot));
                if (!snapshot.unseen().isEmpty()) {
                    client.sendResponse(new AddHabboItemComposer(
                            snapshot.unseen().stream()
                                    .mapToInt(Integer::intValue)
                                    .toArray(),
                            AddHabboItemComposer.AddHabboItemCategory.HABBICON));
                }
            } else if (header == Incoming.GetHabbiconInfoEvent) {
                client.sendResponse(new HabbiconInfoComposer(
                        service.load(client.getHabbo().getHabboInfo().getId()).requireItem(id)));
            } else if (action >= 0 && action < HabbiconService.Action.values().length) {
                service.change(client.getHabbo(), HabbiconService.Action.values()[action], id);
                client.sendResponse(new HabbiconResultComposer(action, id, 0));
            }
        } catch (HabbiconService.Rejected exception) {
            client.sendResponse(new HabbiconResultComposer(action, id, exception.code()));
        } catch (SQLException exception) {
            LOGGER.error(
                    "Unable to process Habbicon request for user {}",
                    client.getHabbo().getHabboInfo().getId(),
                    exception);
            client.sendResponse(new HabbiconResultComposer(action, id, 5));
        }
    }
}
