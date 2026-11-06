package polaris.hotel.catalog.layouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;

public class MarketplaceLayout extends CatalogPage {
    public MarketplaceLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("marketplace");
        message.appendInt(0);
        message.appendInt(0);
    }
}
