package polaris.hotel.catalog.layouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;

public class InfoLoyaltyLayout extends CatalogPage {
    public InfoLoyaltyLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("info_loyalty");
        message.appendInt(1);
        message.appendString(this.getHeaderImage());
        message.appendInt(1);
        message.appendString(this.getTextOne());
    }
}
