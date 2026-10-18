package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;
import java.sql.ResultSet;
import java.sql.SQLException;

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
