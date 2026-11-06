package polaris.messages.outgoing.catalog;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.catalog.CatalogPage;
import polaris.hotel.catalog.CatalogPageType;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CatalogPagesListComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogPagesListComposer.class);

    private static final int MAX_OFFERS = 4000;
    private static final int MAX_CHILDREN = 500;
    private static final int MAX_DEPTH = 20;

    private final User user;
    private final String mode;
    private final boolean hasPermission;
    private final boolean newAdditionsAvailable;

    public CatalogPagesListComposer(User user, String mode) {
        this(user, mode, false);
    }

    /** {@code newAdditionsAvailable}: the official index flag that opens the new_additions page (AIR 15). */
    public CatalogPagesListComposer(User user, String mode, boolean newAdditionsAvailable) {
        this.user = user;
        this.mode = mode;
        this.hasPermission = this.user.hasPermission(Permission.ACC_CATALOG_IDS);
        this.newAdditionsAvailable = newAdditionsAvailable;
    }

    @Override
    protected ServerMessage composeInternal() {
        try {
            CatalogPageType requestedType = CatalogPageType.fromString(this.mode);
            List<CatalogPage> pages =
                    Emulator.getGameEnvironment().getCatalogManager().getCatalogPages(-1, this.user, requestedType);

            this.response.init(Outgoing.CatalogPagesListComposer);

            this.response.appendBoolean(true);
            this.response.appendInt(0);
            this.response.appendInt(-1);
            this.response.appendInt(-1);
            this.response.appendString("root");
            this.response.appendString("");
            this.response.appendInt(0);

            int childCount = Math.min(pages.size(), MAX_CHILDREN);
            this.response.appendInt(childCount);

            for (int idx = 0; idx < childCount; idx++) {
                this.append(pages.get(idx), 1, requestedType);
            }

            this.response.appendBoolean(this.newAdditionsAvailable);
            this.response.appendString(this.mode);

            return this.response;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return null;
    }

    private void append(CatalogPage category, int depth, CatalogPageType requestedType) {
        List<CatalogPage> pagesList = Emulator.getGameEnvironment()
                .getCatalogManager()
                .getCatalogPages(category.getId(), this.user, requestedType);

        this.response.appendBoolean(category.isVisible());
        this.response.appendInt(category.getIconImage());
        this.response.appendInt(category.isEnabled() || this.hasPermission ? category.getId() : -1);
        this.response.appendInt(category.getParentId());
        this.response.appendString(category.getPageName());
        this.response.appendString(category.getCaption() + (this.hasPermission ? " (" + category.getId() + ")" : ""));

        IntList pageOfferIds = category.getOfferIds();
        IntList offerIds = new IntArrayList(pageOfferIds.size());
        IntOpenHashSet seenOfferIds = new IntOpenHashSet();
        for (int idx = 0; idx < pageOfferIds.size(); idx++) {
            int offerId = pageOfferIds.getInt(idx);
            if (offerId > 0 && seenOfferIds.add(offerId)) offerIds.add(offerId);
        }

        int offerCount = Math.min(offerIds.size(), MAX_OFFERS);
        if (offerIds.size() > MAX_OFFERS) {
            LOGGER.warn(
                    "Catalog page {} has {} offers; limiting the index packet to {}",
                    category.getId(),
                    offerIds.size(),
                    MAX_OFFERS);
        }

        this.response.appendInt(offerCount);
        for (int idx = 0; idx < offerCount; idx++) {
            this.response.appendInt(offerIds.getInt(idx));
        }

        if (depth >= MAX_DEPTH) {
            this.response.appendInt(0);
            return;
        }

        int childCount = Math.min(pagesList.size(), MAX_CHILDREN);
        this.response.appendInt(childCount);

        for (int idx = 0; idx < childCount; idx++) {
            this.append(pagesList.get(idx), depth + 1, requestedType);
        }
    }

    public User getUser() {
        return user;
    }

    public String getMode() {
        return mode;
    }

    public boolean isNewAdditionsAvailable() {
        return newAdditionsAvailable;
    }

    public boolean isHasPermission() {
        return hasPermission;
    }
}
