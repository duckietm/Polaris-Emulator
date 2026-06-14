package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class CatalogAdminCreateOfferEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

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

        int newId = -1;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     (payload.pageType == CatalogPageType.BUILDER)
                             ? "INSERT INTO catalog_items_bc (page_id, item_ids, catalog_name, order_number, extradata) VALUES (?, ?, ?, ?, ?)"
                             : "INSERT INTO catalog_items (page_id, item_ids, catalog_name, cost_credits, cost_points, points_type, amount, club_only, extradata, have_offer, offer_id, limited_stack, order_number) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, payload.pageId);
            statement.setString(2, payload.itemIds);
            statement.setString(3, payload.catalogName);

            if (payload.pageType == CatalogPageType.BUILDER) {
                statement.setInt(4, payload.orderNumber);
                statement.setString(5, payload.extradata);
            } else {
                statement.setInt(4, payload.costCredits);
                statement.setInt(5, payload.costPoints);
                statement.setInt(6, payload.pointsType);
                statement.setInt(7, payload.amount);
                statement.setString(8, payload.clubOnly == 1 ? "1" : "0");
                statement.setString(9, payload.extradata);
                statement.setString(10, payload.haveOffer ? "1" : "0");
                statement.setInt(11, payload.offerIdGroup);
                statement.setInt(12, payload.limitedStack);
                statement.setInt(13, payload.orderNumber);
            }
            statement.execute();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    newId = keys.getInt(1);
                }
            }
        }

        if (newId > 0) {
            this.client.sendResponse(new CatalogAdminResultComposer(true, "Offer created: " + newId));
        } else {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Failed to create offer"));
        }
    }
}
