package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FrontPageFeaturedLayout extends CatalogPage {
    public FrontPageFeaturedLayout(ResultSet set) throws SQLException {
        super(set);
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
