package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.CatalogPageComposer;

public class RequestCatalogPageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int catalogPageId = this.packet.readInt();
        int offerId = this.packet.readInt();
        String mode = this.packet.readString();
        CatalogPageType requestedType = CatalogPageType.fromString(mode);

        CatalogPage page =
                Emulator.getGameEnvironment().getCatalogManager().getCatalogPage(catalogPageId, requestedType);

        if (catalogPageId > 0 && page != null) {
            boolean canSeeCatalogIds = this.client.getHabbo().hasPermission(Permission.ACC_CATALOG_IDS);
            boolean canOpen = CatalogPageAccessPolicy.canOpen(
                    page.getRank(),
                    this.client.getHabbo().getHabboInfo().getRank().getId(),
                    page.isEnabled(),
                    canSeeCatalogIds);

            if (canOpen) {
                this.client.sendResponse(new CatalogPageComposer(page, this.client.getHabbo(), offerId, mode));
            } else {
                if (!page.isVisible()) {
                    ScripterManager.scripterDetected(
                            this.client,
                            Emulator.getTexts()
                                    .getValue("scripter.warning.catalog.page")
                                    .replace(
                                            "%username%",
                                            this.client
                                                    .getHabbo()
                                                    .getHabboInfo()
                                                    .getUsername())
                                    .replace("%pagename%", page.getCaption()));
                }
            }
        }
    }
}
