package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxSupport;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Official Variable Add-on Import (1004, {@code wf_xtra_var_import}, AIR {@code VariableAddonImportAddon}): stacked
 * with a variable reference, it chooses which add-on categories of the referenced variable this room takes over
 * ({@code wiredfurni.params.variables.addon_import.usage_info}); a category also has to be shared by the source.
 */
public class WiredExtraVariableAddonImport extends WiredExtraVariableAddonSharing {
    public static final int CODE = 1004;

    public WiredExtraVariableAddonImport(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraVariableAddonImport(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int getCode() {
        return CODE;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        boolean saved = super.saveData(settings, gameClient);
        // Imported fx are drawn by this room's fx service, which may not be running yet.
        Room room = settings.getRoom();
        if (saved && room != null) {
            WiredVariableFxSupport.ensure(room);
        }
        return saved;
    }
}
