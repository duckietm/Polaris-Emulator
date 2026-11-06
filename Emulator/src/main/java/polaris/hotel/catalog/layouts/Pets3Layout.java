package polaris.hotel.catalog.layouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;

public class Pets3Layout extends CatalogPage {
    public Pets3Layout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("pets3");
        message.appendInt(2);
        message.appendString(super.getHeaderImage());
        message.appendString(super.getTeaserImage());
        message.appendInt(4);
        message.appendString(super.getTextOne());
        message.appendString(super.getTextTwo());
        message.appendString(super.getTextDetails());
        message.appendString(super.getTextTeaser());
    }
}
