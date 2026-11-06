package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.catalog.layouts.RecentPurchasesLayout;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.CatalogPageComposer;

public class RequestCatalogPageEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int catalogPageId = this.packet.readInt();
        int offerId = this.packet.readInt();
        String mode = this.packet.readString();
        CatalogPageType requestedType = CatalogPageType.fromString(mode);

        CatalogManager catalogManager = Emulator.getGameEnvironment().getCatalogManager();
        CatalogPage page = catalogManager.getCatalogPage(catalogPageId, requestedType);

        if (catalogPageId > 0 && page != null) {
            boolean canSeeCatalogIds = this.client.getUser().hasPermission(Permission.ACC_CATALOG_IDS);
            boolean canOpen = CatalogPageAccessPolicy.canOpen(
                    page.getRank(),
                    this.client.getUser().getUserInfo().getRank().getId(),
                    page.isEnabled(),
                    canSeeCatalogIds);

            if (canOpen) {
                if (page instanceof RecentPurchasesLayout) {
                    catalogManager.loadRecentPurchases(this.client.getUser());
                }

                this.client.sendResponse(new CatalogPageComposer(page, this.client.getUser(), offerId, mode));
            } else {
                if (!page.isVisible()) {
                    ScripterManager.scripterDetected(
                            this.client,
                            Emulator.getTexts()
                                    .getValue("scripter.warning.catalog.page")
                                    .replace(
                                            "%username%",
                                            this.client.getUser().getUserInfo().getUsername())
                                    .replace("%pagename%", page.getCaption()));
                }
            }
        }
    }
}
