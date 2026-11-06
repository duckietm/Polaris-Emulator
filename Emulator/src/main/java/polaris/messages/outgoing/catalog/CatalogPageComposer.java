package polaris.messages.outgoing.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogFeaturedPage;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.layouts.FrontPageFeaturedLayout;
import polaris.hotel.catalog.layouts.FrontpageLayout;
import polaris.hotel.catalog.layouts.RecentPurchasesLayout;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CatalogPageComposer extends MessageComposer {
    private final CatalogPage page;
    private final User user;
    private final int offerId;
    private final String mode;

    public CatalogPageComposer(CatalogPage page, User user, int offerId, String mode) {
        this.page = page;
        this.user = user;
        this.offerId = offerId;
        this.mode = mode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CatalogPageComposer);
        this.response.appendInt(this.page.getId());
        this.response.appendString(this.mode);
        this.page.serialize(this.response);

        if (this.page instanceof RecentPurchasesLayout) {
            Map<Integer, CatalogItem> recentPurchases = this.user.getUserStats().getRecentPurchases();
            List<CatalogItem> recentItems;
            synchronized (recentPurchases) {
                recentItems = new ArrayList<>(recentPurchases.values());
            }

            Collections.reverse(recentItems);

            this.response.appendInt(recentItems.size());
            for (CatalogItem item : recentItems) {
                item.serialize(this.response);
            }
        } else {
            List<CatalogItem> items =
                    Emulator.getGameEnvironment().getCatalogManager().getEffectivePageItems(this.page);
            Collections.sort(items);
            this.response.appendInt(items.size());
            for (CatalogItem item : items) {
                item.serialize(this.response);
            }
        }
        this.response.appendInt(this.offerId);
        this.response.appendBoolean(false); // acceptSeasonCurrencyAsCredits

        if (this.page instanceof FrontPageFeaturedLayout || this.page instanceof FrontpageLayout) {
            this.serializeExtra(this.response);
        }

        return this.response;
    }

    public void serializeExtra(ServerMessage message) {
        List<CatalogFeaturedPage> featuredPages =
                Emulator.getGameEnvironment().getCatalogManager().getCatalogFeaturedPagesSnapshot();
        message.appendInt(featuredPages.size());

        for (CatalogFeaturedPage page : featuredPages) {
            page.serialize(message);
        }
    }

    public CatalogPage getPage() {
        return page;
    }

    public User getUser() {
        return user;
    }

    public int getOfferId() {
        return offerId;
    }

    public String getMode() {
        return mode;
    }
}
