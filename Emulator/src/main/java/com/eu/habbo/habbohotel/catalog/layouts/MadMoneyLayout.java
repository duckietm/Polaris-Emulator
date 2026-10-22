package com.eu.habbo.habbohotel.catalog.layouts;

import com.eu.habbo.habbohotel.catalog.CatalogPage;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MadMoneyLayout extends CatalogPage {

    public MadMoneyLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("mad_money");
        message.appendInt(2);
        message.appendString(super.getHeaderImage());
        message.appendString(super.getTeaserImage());
        message.appendInt(2);
        message.appendString(super.getTextOne());
        message.appendString(super.getTextTwo());
        // message.appendString("MH");
    }
}
