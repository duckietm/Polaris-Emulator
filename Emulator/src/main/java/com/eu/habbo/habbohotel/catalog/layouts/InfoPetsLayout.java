package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InfoPetsLayout extends CatalogPage {
    public InfoPetsLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("info_pets");
        message.appendInt(2);
        message.appendString(this.getHeaderImage());
        message.appendString(this.getTeaserImage());
        message.appendInt(3);
        message.appendString(this.getTextOne());
        message.appendString("");
        message.appendString(this.getTextTeaser());
    }
}
