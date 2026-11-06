package polaris.messages.incoming.habbicons;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.habbicons.HabbiconService;
import polaris.messages.incoming.Incoming;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.habbicons.HabbiconInfoComposer;
import polaris.messages.outgoing.habbicons.HabbiconShopDataComposer;
import polaris.messages.outgoing.habbicons.UserHabbiconsComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;

public final class HabbiconRequestEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabbiconRequestEvent.class);
    private static final int SERVER_ERROR = 5;

    @Override
    public void handle() {
        int userId = client.getUser().getUserInfo().getId();
        HabbiconService service = client.getUser().getHabbiconService();
        HabbiconService.Action action = action(packet.getMessageId());
        int id = packet.getMessageId() == Incoming.GetHabbiconShopDataEvent ? 0 : packet.readInt();
        try {
            if (action != null) {
                service.change(client.getUser(), action, id);
                if (purchase(action)) {
                    client.sendResponse(new PurchaseOKComposer());
                }
            } else if (packet.getMessageId() == Incoming.GetHabbiconInfoEvent) {
                client.sendResponse(
                        new HabbiconInfoComposer(service.load(userId).requireItem(id)));
            } else {
                HabbiconService.Snapshot snapshot = service.load(userId);
                client.sendResponse(new UserHabbiconsComposer(snapshot));
                client.sendResponse(new HabbiconShopDataComposer(snapshot));
                if (!snapshot.unseen().isEmpty()) {
                    client.sendResponse(new AddUserItemComposer(
                            snapshot.unseen().stream()
                                    .mapToInt(Integer::intValue)
                                    .toArray(),
                            AddUserItemComposer.AddUserItemCategory.HABBICON));
                }
            }
        } catch (HabbiconService.Rejected exception) {
            fail(action, exception.code());
        } catch (SQLException exception) {
            LOGGER.error("Unable to process Habbicon request for user {}", userId, exception);
            fail(action, SERVER_ERROR);
        }
    }

    private void fail(HabbiconService.Action action, int code) {
        if (purchase(action)) {
            client.sendResponse(new AlertPurchaseFailedComposer(code));
        }
    }

    private static boolean purchase(HabbiconService.Action action) {
        return action == HabbiconService.Action.BUY
                || action == HabbiconService.Action.BUY_COLLECTION
                || action == HabbiconService.Action.CLAIM;
    }

    private static HabbiconService.Action action(int header) {
        return switch (header) {
            case Incoming.BuyHabbiconEvent -> HabbiconService.Action.BUY;
            case Incoming.BuyHabbiconCollectionEvent -> HabbiconService.Action.BUY_COLLECTION;
            case Incoming.ClaimHabbiconEvent -> HabbiconService.Action.CLAIM;
            case Incoming.FavoriteHabbiconEvent -> HabbiconService.Action.FAVORITE;
            case Incoming.UnfavoriteHabbiconEvent -> HabbiconService.Action.UNFAVORITE;
            default -> null;
        };
    }
}
