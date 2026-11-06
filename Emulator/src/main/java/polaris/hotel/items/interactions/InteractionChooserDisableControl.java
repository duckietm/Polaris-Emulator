package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;

/**
 * The {@code :chooser} disabler ({@code conf_chooser_disable}): while it is switched on, visitors without rights
 * cannot open the user chooser (AIR {@code ConfigurationItemStates.chooserDisabled}).
 */
public class InteractionChooserDisableControl extends InteractionRemoteSwitchControl {
    public InteractionChooserDisableControl(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionChooserDisableControl(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}
