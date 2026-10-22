package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InfoDucketsLayout extends CatalogPage {
    public InfoDucketsLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("info_duckets");
        message.appendInt(1);
        message.appendString(this.getHeaderImage());
        message.appendInt(1);
        message.appendString(this.getTextOne());
    }
}
