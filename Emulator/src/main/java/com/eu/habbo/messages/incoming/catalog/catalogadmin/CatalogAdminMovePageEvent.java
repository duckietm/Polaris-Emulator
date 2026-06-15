package com.eu.habbo.messages.incoming.catalog.catalogadmin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.CatalogAdminResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class CatalogAdminMovePageEvent extends MessageHandler {

    private static final int MAX_PARENT_WALK = 64;
    private static final int ROOT_PARENT_ID = -1;

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "No permission"));
            return;
        }

        int pageId = this.packet.readInt();
        int newParentId = this.packet.readInt();
        int newIndex = this.packet.readInt();
        CatalogPageType pageType = CatalogPageType.fromString(this.packet.readString());
        String tableName = (pageType == CatalogPageType.BUILDER) ? "catalog_pages_bc" : "catalog_pages";

        CatalogPage page = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(pageId, pageType);
        if (page == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
            return;
        }

        if (newParentId == -1) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE " + tableName + " SET enabled = IF(enabled = '1', '0', '1') WHERE id = ?")) {
                statement.setInt(1, pageId);
                if (statement.executeUpdate() == 0) {
                    this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
                    return;
                }
            }
            this.client.sendResponse(new CatalogAdminResultComposer(true, "Page toggled"));
            return;
        }

        if (newParentId == -2) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE " + tableName + " SET visible = IF(visible = '1', '0', '1') WHERE id = ?")) {
                statement.setInt(1, pageId);
                if (statement.executeUpdate() == 0) {
                    this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
                    return;
                }
            }
            this.client.sendResponse(new CatalogAdminResultComposer(true, "Visibility toggled"));
            return;
        }

        if (newParentId == pageId) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "A page cannot be its own parent"));
            return;
        }

        CatalogPage parent = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(newParentId, pageType);
        if (parent == null) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Parent page not found: " + newParentId));
            return;
        }

        if (this.wouldCreateCycle(pageId, newParentId, pageType)) {
            this.client.sendResponse(new CatalogAdminResultComposer(false, "Refusing to move: that would create a cycle"));
            return;
        }

        if (newIndex < 0) newIndex = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + tableName + " SET parent_id = ?, order_num = ? WHERE id = ?")) {
            statement.setInt(1, newParentId);
            statement.setInt(2, newIndex);
            statement.setInt(3, pageId);
            if (statement.executeUpdate() == 0) {
                this.client.sendResponse(new CatalogAdminResultComposer(false, "Page not found: " + pageId));
                return;
            }
        }

        this.client.sendResponse(new CatalogAdminResultComposer(true, "Page moved"));
    }

    private boolean wouldCreateCycle(int pageId, int parentId, CatalogPageType pageType) {
        int current = parentId;
        for (int hops = 0; hops < MAX_PARENT_WALK; hops++) {
            if (current == ROOT_PARENT_ID) return false;
            if (current == pageId) return true;
            CatalogPage parent = Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(current, pageType);
            if (parent == null) return false;
            current = parent.getParentId();
        }
        return true;
    }
}
