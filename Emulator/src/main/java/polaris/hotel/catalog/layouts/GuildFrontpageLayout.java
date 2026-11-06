package polaris.hotel.catalog.layouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;

public class GuildFrontpageLayout extends CatalogPage {
    public GuildFrontpageLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("guild_frontpage");
        message.appendInt(2);
        message.appendString(super.getHeaderImage());
        message.appendString(super.getTeaserImage());
        message.appendInt(3);
        message.appendString(super.getTextOne());
        message.appendString(super.getTextDetails());
        message.appendString(super.getTextTeaser());
    }
}
