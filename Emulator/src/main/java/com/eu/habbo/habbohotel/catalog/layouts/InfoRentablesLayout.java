package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InfoRentablesLayout extends CatalogPage {
    public InfoRentablesLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        String[] data = this.getTextOne().split("\\|\\|");
        message.appendString("info_rentables");
        message.appendInt(1);
        message.appendString(this.getHeaderImage());
        message.appendInt(data.length);
        for (String d : data) {
            message.appendString(d);
        }
    }
}
