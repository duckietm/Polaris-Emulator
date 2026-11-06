package polaris.hotel.catalog.layouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;

public class ClubGiftsLayout extends CatalogPage {
    public ClubGiftsLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("club_gifts");
        message.appendInt(1);
        message.appendString(super.getHeaderImage());
        message.appendInt(1);
        message.appendString(super.getTextOne());
    }
}
