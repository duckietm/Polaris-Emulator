package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Official Variable Add-on Share (1003, {@code wf_xtra_var_share}, AIR {@code VariableAddonShareAddon}): stacked with
 * a shared variable, it chooses which add-on categories of that variable reach the rooms referencing it
 * ({@code wiredfurni.params.variables.addon_share.usage_info}). Its Synchronize button pushes them out again.
 */
public class WiredExtraVariableAddonShare extends WiredExtraVariableAddonSharing {
    public static final int CODE = 1003;

    public WiredExtraVariableAddonShare(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableAddonShare(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int getCode() {
        return CODE;
    }
}
