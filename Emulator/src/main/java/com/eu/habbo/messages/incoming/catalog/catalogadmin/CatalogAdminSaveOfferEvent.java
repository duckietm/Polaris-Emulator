package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class CatalogAdminSaveOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int offerId = this.packet.readInt();
        int pageId = this.packet.readInt();
        String itemIds = this.packet.readString();
        String catalogName = this.packet.readString();
        int costCredits = this.packet.readInt();
        int costPoints = this.packet.readInt();
        int pointsType = this.packet.readInt();
        int amount = this.packet.readInt();
        int clubOnly = this.packet.readInt();
        String extradata = this.packet.readString();
        boolean haveOffer = this.packet.readBoolean();
        int offerIdGroup = this.packet.readInt();
        int limitedStack = this.packet.readInt();
        int orderNumber = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());

        if (offerId <= 0) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer id"));
            return;
        }

        CatalogAdminOfferPayload payload = CatalogAdminOfferPayload.validate(pageId, itemIds, catalogName, costCredits,
                costPoints, pointsType, amount, clubOnly, extradata, haveOffer, offerIdGroup, limitedStack,
                orderNumber, pageType);
        if (payload == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Invalid offer payload"));
            return;
        }

        if (Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(payload.pageId, payload.pageType) == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + payload.pageId));
            return;
        }

        boolean updateItemIds = itemIds != null && !itemIds.trim().isEmpty();

        String sql;
        if (payload.pageType == CatalogPageType.BUILDER) {
            sql = updateItemIds
                    ? "UPDATE catalog_items_bc SET page_id = ?, item_ids = ?, catalog_name = ?, order_number = ?, extradata = ? WHERE id = ?"
                    : "UPDATE catalog_items_bc SET page_id = ?, catalog_name = ?, order_number = ?, extradata = ? WHERE id = ?";
        } else {
            sql = updateItemIds
                    ? "UPDATE catalog_items SET page_id = ?, item_ids = ?, catalog_name = ?, cost_credits = ?, cost_points = ?, points_type = ?, amount = ?, club_only = ?, extradata = ?, have_offer = ?, offer_id = ?, limited_stack = ?, order_number = ? WHERE id = ?"
                    : "UPDATE catalog_items SET page_id = ?, catalog_name = ?, cost_credits = ?, cost_points = ?, points_type = ?, amount = ?, club_only = ?, extradata = ?, have_offer = ?, offer_id = ?, limited_stack = ?, order_number = ? WHERE id = ?";
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int idx = 1;
            statement.setInt(idx++, payload.pageId);
            if (updateItemIds) {
                statement.setString(idx++, payload.itemIds);
            }
            statement.setString(idx++, payload.catalogName);

            if (payload.pageType == CatalogPageType.BUILDER) {
                statement.setInt(idx++, payload.orderNumber);
                statement.setString(idx++, payload.extradata);
                statement.setInt(idx, offerId);
            } else {
                statement.setInt(idx++, payload.costCredits);
                statement.setInt(idx++, payload.costPoints);
                statement.setInt(idx++, payload.pointsType);
                statement.setInt(idx++, payload.amount);
                statement.setString(idx++, payload.clubOnly == 1 ? "1" : "0");
                statement.setString(idx++, payload.extradata);
                statement.setString(idx++, payload.haveOffer ? "1" : "0");
                statement.setInt(idx++, payload.offerIdGroup);
                statement.setInt(idx++, payload.limitedStack);
                statement.setInt(idx++, payload.orderNumber);
                statement.setInt(idx, offerId);
            }
            if (statement.executeUpdate() == 0) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Offer not found: " + offerId));
                return;
            }
        }

        this.client.sendResponse(new CatalogAdminResultComposer(true, "Offer saved"));
    }
}
