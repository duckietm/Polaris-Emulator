package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogFeaturedPage;
import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class FrontPageFeaturedLayout extends CatalogPage {
    public FrontPageFeaturedLayout(ResultSet set) throws SQLException {
        super(set);
    }

    /**
     * Writes the featured-page block that follows the page on the wire. The page composer sends this
     * block itself; the method stays on the plugin surface and writes the same content.
     */
    public void serializeExtra(ServerMessage message) {
        List<CatalogFeaturedPage> featuredPages =
                Emulator.getGameEnvironment().getCatalogManager().getCatalogFeaturedPagesSnapshot();
        message.appendInt(featuredPages.size());

        for (CatalogFeaturedPage page : featuredPages) {
            page.serialize(message);
        }
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("frontpage_featured");
        String[] teaserImages = super.getTeaserImage().split(";");
        String[] specialImages = super.getSpecialImage().split(";");

        message.appendInt(1 + teaserImages.length + specialImages.length);
        message.appendString(super.getHeaderImage());
        for (String s : teaserImages) {
            message.appendString(s);
        }

        for (String s : specialImages) {
            message.appendString(s);
        }
        message.appendInt(3);
        message.appendString(super.getTextOne());
        message.appendString(super.getTextDetails());
        message.appendString(super.getTextTeaser());
    }
}
