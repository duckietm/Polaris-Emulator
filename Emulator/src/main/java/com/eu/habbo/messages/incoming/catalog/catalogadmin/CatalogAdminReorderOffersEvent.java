package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.CatalogAdminMutationService;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;
import java.util.ArrayList;
import java.util.List;

public class CatalogAdminReorderOffersEvent extends MessageHandler {
    private static final int MAX_BATCH_SIZE = 1000;

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int count = this.packet.readInt();
        if (count <= 0 || count > MAX_BATCH_SIZE) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid reorder batch size"));
            return;
        }

        List<CatalogAdminMutationService.OfferOrder> orders = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int offerId = this.packet.readInt();
            int orderNumber = this.packet.readInt();
            orders.add(new CatalogAdminMutationService.OfferOrder(offerId, orderNumber));
        }

        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        var result = CatalogAdminMutationService.reorderOffers(orders, pageType);
        this.client.sendResponse(new CatalogAdminResultComposer(result.success(), result.message()));
    }
}
