package polaris.messages.incoming.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.items.Item;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.CatalogProductMetadataComposer;
import polaris.messages.outgoing.catalog.CatalogProductMetadataEntry;

public class CatalogProductMetadataEvent extends MessageHandler {
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    @Override
    public void handle() throws Exception {
        int version = this.packet.readInt();
        String requestId = sanitizeRequestId(this.packet.readString());
        int pageId = this.packet.readInt();
        String catalogMode = this.packet.readString();

        if (this.client == null || this.client.getUser() == null) return;

        List<CatalogProductMetadataEntry> entries = List.of();

        if (version == CatalogProductMetadataComposer.PROTOCOL_VERSION && pageId > 0) {
            CatalogPage page = Emulator.getGameEnvironment()
                    .getCatalogManager()
                    .getCatalogPage(pageId, CatalogPageType.fromString(catalogMode));

            if (page != null && canOpen(page)) entries = collectEntries(page);
        }

        this.client.sendResponse(new CatalogProductMetadataComposer(requestId, pageId, entries));
    }

    private boolean canOpen(CatalogPage page) {
        boolean canSeeCatalogIds = this.client.getUser().hasPermission(Permission.ACC_CATALOG_IDS);

        return CatalogPageAccessPolicy.canOpen(
                page.getRank(),
                this.client.getUser().getUserInfo().getRank().getId(),
                page.isEnabled(),
                canSeeCatalogIds);
    }

    private static List<CatalogProductMetadataEntry> collectEntries(CatalogPage page) {
        List<CatalogProductMetadataEntry> entries = new ArrayList<>();
        List<CatalogItem> offers =
                Emulator.getGameEnvironment().getCatalogManager().getEffectivePageItems(page);
        offers.sort(Comparator.comparingInt(CatalogItem::getId));

        for (CatalogItem offer : offers) {
            List<Item> baseItems = new ArrayList<>(offer.getBaseItems());
            baseItems.sort(Comparator.comparingInt(Item::getId));

            for (Item item : baseItems) {
                entries.add(new CatalogProductMetadataEntry(
                        offer.getId(), item.getId(), item.getSpriteId(), item.allowTrade(), item.allowRecyle()));

                if (entries.size() == CatalogProductMetadataComposer.MAX_ENTRIES) return entries;
            }
        }

        return entries;
    }

    private static String sanitizeRequestId(String requestId) {
        if (requestId == null) return "";

        String sanitized = requestId.replaceAll("[\\p{Cntrl}]", "").trim();

        return sanitized.length() <= MAX_REQUEST_ID_LENGTH ? sanitized : sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
    }

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public String getRatelimitGroup() {
        return "catalog_product_metadata";
    }
}
