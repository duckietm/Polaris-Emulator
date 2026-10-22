package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleTransferResultComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectiblesInventoryUpdateComposer;
import java.util.List;

/**
 * AIR 15 {@code class_2660} transfer (1857, one string). On habbo.com the string is the external wallet
 * every collectible of the collector wallet moves to; without a blockchain it names the user who receives
 * them. The fee is {@code hotel.collectibles.transfer.fee_silver} silver per collectible. Answers
 * {@code 2595}: 0 ok, 1 nothing to transfer, 2 unknown user, 3 not enough silver, 4 yourself, 5 failed.
 */
public class TransferCollectiblesEvent extends MessageHandler {
    public static final int OK = 0;
    public static final int NOTHING = 1;
    public static final int UNKNOWN_USER = 2;
    public static final int NOT_ENOUGH_SILVER = 3;
    public static final int SELF = 4;
    public static final int FAILED = 5;

    static int fee(int count, CollectiblesSettings settings) {
        long fee = (long) count * settings.transferFeeSilver();
        return (int) Math.min(Integer.MAX_VALUE, fee);
    }

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String recipientName = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        int userId = habbo.getHabboInfo().getId();
        HabboInfo recipient = recipientName == null || recipientName.isBlank()
                ? null
                : HabboManager.getOfflineHabboInfo(recipientName.trim());
        if (recipient == null) {
            this.client.sendResponse(new CollectibleTransferResultComposer(UNKNOWN_USER));
            return;
        }
        if (recipient.getId() == userId) {
            this.client.sendResponse(new CollectibleTransferResultComposer(SELF));
            return;
        }

        int count = service.owned(userId).size();
        if (count == 0) {
            this.client.sendResponse(new CollectibleTransferResultComposer(NOTHING));
            return;
        }

        int fee = fee(count, settings);
        if (!CollectiblesSupport.pay(habbo, settings.silverType(), fee, "collectibles.transfer")) {
            this.client.sendResponse(new CollectibleTransferResultComposer(NOT_ENOUGH_SILVER));
            return;
        }

        List<Owned> moved;
        try {
            moved = service.transferAll(userId, recipient.getId());
        } catch (Exception exception) {
            CollectiblesSupport.refund(habbo, settings.silverType(), fee, "collectibles.transfer.refund");
            this.client.sendResponse(new CollectibleTransferResultComposer(FAILED));
            throw exception;
        }

        var products = service.products();
        List<Owned> removed = moved.stream()
                .map(token -> new Owned(token.assetId(), userId, token.productCode(), token.serial()))
                .toList();
        this.client.sendResponse(new CollectiblesInventoryUpdateComposer(List.of(), removed, products));
        CollectiblesSupport.sendScore(habbo, service, settings);
        CollectiblesSupport.sendBalances(habbo, settings);
        this.client.sendResponse(new CollectibleTransferResultComposer(OK));

        Habbo online = CollectiblesSupport.onlineHabbo(recipient.getId());
        if (online != null && online.getClient() != null) {
            online.getClient().sendResponse(new CollectiblesInventoryUpdateComposer(moved, List.of(), products));
            CollectiblesSupport.sendScore(online, service, settings);
        }
    }
}
