package com.srth.plugin.habbo.CraftItems.ListCrafts;

import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.CraftItems.CraftData;
import com.srth.plugin.habbo.CraftItems.CraftItem;
import com.srth.plugin.habbo.CraftItems.CraftManager;
import com.srth.plugin.habbo.CraftItems.IngredientesItem;
import com.srth.plugin.habbo.CraftItems.CraftHabbo.CraftHabbo;
import com.srth.plugin.habbo.CraftItems.CraftHabbo.CraftHabboManager;

import gnu.trove.map.hash.THashMap;

public class CraftCocineros {

  private String header;

  public CraftCocineros() {
    header = "cocina_data_craft_items";
    this.registerUtils();
  }

  public void registerCraft(CraftManager craftManager) throws SQLException {
    craftManager.setCraftManager(header);
  }

  public void registerUtils() {
    RoleplayWebsocketsRoomsInfo.getConfig().register("cocina_data_craft_items-title",
        "title_ventana:Peticion de platillos; text_button: Realizar peticion");
    RoleplayWebsocketsRoomsInfo.getConfig().register("cocina_data_craft_items-desc",
        "Esta es la lista de ingredientes que necesitas para cocinar %item_name%.");
    RoleplayWebsocketsRoomsInfo.getConfig().register("cocina_data_craft_items-config", "items:2;");

    // -----------
    // ITEM 0
    RoleplayWebsocketsRoomsInfo.getConfig().register("cocina_data_craft_items-config_item_0",
        "item_name:rp_food_ktchn; cantidad: 10; costo_cash:8; time_minutes:10; name_event:CraftPlatilloCocina;");
    RoleplayWebsocketsRoomsInfo.getConfig().register("cocina_data_craft_items-list_items_0",
        "bote_agua:3;pollo:2;vegetales_variados:3;");
    // -----------

    // -----------
    // ITEM 1
    RoleplayWebsocketsRoomsInfo.getConfig().register("cocina_data_craft_items-config_item_1",
        "item_name:rp_food_ktchn; cantidad: 5; costo_cash:2; time_minutes:5; name_event:CraftPlatilloCocina;");
    RoleplayWebsocketsRoomsInfo.getConfig().register("cocina_data_craft_items-list_items_1",
        "bote_agua:2;pollo:1;vegetales_variados:1;");
    // -----------
  }

  public void show(Habbo habbo) throws SQLException {

    CraftManager craftManager = RoleplayWebsocketsRoomsInfo.getCraftManager();

    if (craftManager.getCraft(header) != null) {
      craftManager.showCraftVentain(header, habbo);
    }

  }

  public void incoming(THashMap<String, Object> data, Habbo habbo) throws SQLException {
    String get_action = ((String) data.get("action"));

    if (get_action.equalsIgnoreCase("CraftPlatilloCocina")) {

      int number_item = (int) ((double) data.get("date"));
      CraftHabboManager craftHabboManager = new CraftHabboManager(header);
      craftHabboManager.setCraftHabbo(habbo);

      CraftHabbo craftHabbo = craftHabboManager.getHabbo(habbo.getHabboInfo().getId());
      craftHabbo.insertCraftActive(header, habbo, number_item);

      CraftData craftData = RoleplayWebsocketsRoomsInfo.getCraftManager().getCraft(header);

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      if (craftData != null) {
        CraftItem item = craftData.CraftsItems().get(number_item);
        for (IngredientesItem ingredientesInfo : item.Ingredientes().values()) {
          if (item != null) {

            habboRp.getNewInventoryHabbo().getItem(ingredientesInfo.getItem().getItemName());
            int cantidad_resta = ingredientesInfo.getCantidadRequired();
            habboRp.getNewInventoryHabbo().removeCantidadItem(ingredientesInfo.getItem().getItemName(), cantidad_resta);

          }
        }

        Item comprobateItem = Emulator.getGameEnvironment().getItemManager().getItem("ktchn_c15_stove_rp");

        new CraftCocineros().show(habbo);

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room != null) {
          for (HabboItem habboItem : room.getFloorItems()) {
            if (habboItem.getBaseItem().getName().equalsIgnoreCase("ktchn_c15_stove_rp")) {

              // Mostrar cocinando
              habboItem.setExtradata("2");
              habboItem.needsUpdate(true);
              habboItem.run();
              room.updateItemState(habboItem);
              room.updateItem(habboItem);
              Emulator.getThreading().run(habboItem);
            }
          }
        }

      }
    }

    if (get_action.equalsIgnoreCase("CraftPlatilloCocina_solicitar")) {

      HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

      int number_item = (int) ((double) data.get("date"));

      CraftHabboManager craftHabboManager = new CraftHabboManager(header);
      craftHabboManager.setCraftHabbo(habbo);
      CraftHabbo craftHabbo = craftHabboManager.getHabbo(habbo.getHabboInfo().getId());

      Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem("rp_food_ktchn");

      CraftData craftData = RoleplayWebsocketsRoomsInfo.getCraftManager().getCraft(header);
      if (craftData != null) {
        for (CraftItem craftItem : craftData.CraftsItems().values()) {

          if (craftItem.getId() == number_item) {

            for (int i = 0; i < craftItem.getCantidad(); i++) {
              HabboItem habboItem = Emulator.getGameEnvironment().getItemManager().createItem(
                  habbo.getHabboInfo().getId(),
                  baseItem,
                  0,
                  0,
                  "");
              habbo.getInventory().getItemsComponent().addItem(habboItem);
              habbo.getClient().sendResponse(new AddHabboItemComposer(habboItem));
            }

            habbo.getClient().sendResponse(new InventoryRefreshComposer());
            craftHabbo.removeCraftActive(header, habbo, number_item);

            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room != null) {
              for (HabboItem habboItem : room.getFloorItems()) {
                if (habboItem.getBaseItem().getName().equalsIgnoreCase("ktchn_c15_stove_rp")) {
                  if (habboItem.getUserId() == room.getOwnerId()) {

                    // Mostrar cocinando
                    habboItem.setExtradata("1");
                    habboItem.needsUpdate(true);
                    habboItem.run();
                    room.updateItemState(habboItem);
                    room.updateItem(habboItem);
                    Emulator.getThreading().run(habboItem);
                  }
                }
              }
            }

            // TAREA ROLEPLAY (INSERTAR)
            habboRp.getTareas().insertProgress("UseHornoRoom");

            habboRp.showMessage("RoleplayWebsocketsRoomsInfo/cocinero/rp_food_ktchn_icon.png?v=1",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_cocinar.success").replace("%br%", "\n")
                    .replace("%cantidad%", craftItem.getCantidad() + ""));

            new CraftCocineros().show(habbo);

          }
        }
      }
    }
  }
}
