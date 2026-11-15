package com.srth.plugin.Tutorial.eventos.ActionEvents;

import com.eu.habbo.habbohotel.users.Habbo;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.CraftItems.CraftData;
import com.srth.plugin.habbo.CraftItems.CraftItem;
import com.srth.plugin.habbo.CraftItems.CraftManager;
import com.srth.plugin.habbo.CraftItems.IngredientesItem;
import com.srth.plugin.habbo.CraftItems.ListCrafts.CraftCocineros;

public class ActionAddIngredientesHabbo {

    public ActionAddIngredientesHabbo(Habbo habbo) {

        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            CraftManager craftManager = RoleplayWebsocketsRoomsInfo.getCraftManager();
            CraftData craftData = craftManager.getCraft("cocina_data_craft_items");
            if (craftData != null) {
                CraftItem item = craftData.CraftsItems().get(0);
                for (int i = 0; i < item.Ingredientes().values().size(); i++) {

                    IngredientesItem ingredientesInfo = item.Ingredientes().get(i);

                    // Agregar ingredientes al inventario
                    if (habboRp.getNewInventoryHabbo().getItem(ingredientesInfo.getItem().getItemName()) == null) {
                        habboRp.getNewInventoryHabbo().createItem(ingredientesInfo.getItem().getItemName(),
                                ingredientesInfo.getCantidadRequired());
                    } else {
                        habboRp.getNewInventoryHabbo().addCantidadItem(ingredientesInfo.getItem().getItemName(),
                                ingredientesInfo.getCantidadRequired());
                    }

                }
            }

            new CraftCocineros().show(habbo);

        } catch (Exception e) {
        }

    }
}
