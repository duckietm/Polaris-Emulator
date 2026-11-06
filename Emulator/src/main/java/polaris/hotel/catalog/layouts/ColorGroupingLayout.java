package polaris.hotel.catalog.layouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;

public class ColorGroupingLayout extends CatalogPage {
    public ColorGroupingLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("default_3x3_color_grouping");
        message.appendInt(3);
        message.appendString(super.getHeaderImage());
        message.appendString(super.getTeaserImage());
        message.appendString(super.getSpecialImage());
        message.appendInt(3);
        message.appendString(super.getTextOne());
        message.appendString(super.getTextDetails());
        message.appendString(super.getTextTeaser());
    }
}
