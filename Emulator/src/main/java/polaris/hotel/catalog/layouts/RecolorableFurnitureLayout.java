package polaris.hotel.catalog.layouts;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.catalog.CatalogPage;
import polaris.messages.ServerMessage;

/**
 * Habbo's recolourable Builders Club furni page (official layout "recolorable_furniture"): the
 * product view, the furni grid, the palette of the chosen furni and the Builders Club place button.
 * The chosen colour goes with the placement as "R,G,B".
 */
public class RecolorableFurnitureLayout extends CatalogPage {
    public RecolorableFurnitureLayout(ResultSet set) throws SQLException {
        super(set);
    }

    @Override
    public void serialize(ServerMessage message) {
        message.appendString("recolorable_furniture");
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
