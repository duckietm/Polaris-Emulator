package com.srth.plugin.ActionItems;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.*;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.furniture.FurnitureToggleEvent;
import com.google.gson.JsonArray;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.Actions.*;
import com.srth.plugin.ActionItems.Actions.Arboles.ArbolAction;
import com.srth.plugin.ActionItems.Actions.Cajeros.hack_cajero.OpenCajero;
import com.srth.plugin.ActionItems.Actions.Pescar.Pescar;
import com.srth.plugin.ActionItems.Actions.PuertaPrision.ForzarPuertaPrision;
import com.srth.plugin.ActionItems.Actions.Rocas.RocasAction;
import com.srth.plugin.ActionItems.Actions.RocasPrision.PicarRocaPrision;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.*;
import gnu.trove.map.hash.THashMap;
import java.sql.SQLException;

public class ItemActionManager {
    private final THashMap<String, Item> type_names_interactions = new THashMap<>();
    private static final THashMap<Habbo, OpenCajero> lista_cajeros_abiertos = new THashMap<>();
    private static final THashMap<Habbo, PicarRocaPrision> lista_rocas_picando = new THashMap<>();
    private static final THashMap<Habbo, ForzarPuertaPrision> puertasPrisionForzando = new THashMap<>();
    private static final THashMap<Habbo, Pescar> lista_pescadores = new THashMap<>();

    public void loadItemsDobleClick() {
        String[] items = { "atm_cajero_byth_rp", "caja_herramientas_byth_rp", "ktchn_c15_stove_rp",
                "market_c19_selfcheckout_rp", "rp_ingred_cocina_2", "stone_police" };
        for (String name : items) {
            Item item = Emulator.getGameEnvironment().getItemManager().getItem(name);
            if (item != null) {
                type_names_interactions.put(name, item);
                ItemActionDatabase.updateItemInteractions(item, 0);
            }
        }
    }

    public void eventReceibedSingleClickFurni(HabboItem item, Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp == null || habboRp.getHabboClient() == null)
                return;
            if (!processItemAction(item, habboRp))
                this.OcultItemAction(habbo);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void getReceibedToggleEvent(FurnitureToggleEvent event) throws SQLException {
        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);
        Room room = event.habbo.getHabboInfo().getCurrentRoom();
        int rpCategory = RoleplayWebsocketsRoomsInfo.getConfig().getInt("global_room.category.roleplay.id");
        if (room != null && room.getCategory() == rpCategory && event.furniture != null) {
            if (processItemAction(event.furniture, habboRp))
                event.setCancelled(true);
        }
    }

    public boolean processItemAction(HabboItem item, HabboRp habboRp) throws SQLException {
        String name = item.getBaseItem().getName();
        Habbo habbo = habboRp.getHabbo();
        switch (name) {
            case "val14_door":
                new ClickPuertaCasa(item, habbo);
                return true;
            case "atm_cajero_byth_rp":
                new ClickHackCajeroAutomatico(item, habbo);
                return true;
            case "cofre_madera_rpbyth_a1":
                if (this.comprobateCerca(item, habbo.getHabboInfo().getCurrentRoom(), habbo.getClient()) == false) {
                    this.moverHabboFrontObjet(item, habbo);
                    return true;
                }
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
                RoleplayWebsocketsRoomsInfo.getCofreInventoryManager().openCofreInventory(habbo, item);
                return true;
            case "rp_arbol_talar_byth":
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
                new ArbolAction(item, habbo);
                return true;
            case "kasja_g_rock7":
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
                new RocasAction(item, habbo);
                return true;
            case "jp_c22_water":
                new ClickLagoPescar(item, habbo);
                return true;
            case "hosptl_cab1":
                new ClickGabineteHospital(item, habbo);
                return true;
            case "rp_urban_basura":
                if (this.comprobateCerca(item, habbo.getHabboInfo().getCurrentRoom(), habbo.getClient()) == false) {
                    this.moverHabboFrontObjet(item, habbo);
                }
                new ClickBasuraUrbana(item, habbo);
                return true;
            case "prison_gate":
                new ClickPuertaPrision(item, habbo);
                if (this.comprobateCerca(item, habbo.getHabboInfo().getCurrentRoom(), habbo.getClient()) == false) {
                    this.moverHabboFrontObjet(item, habbo);
                }
                return true;
            case "Habblet_sacodecafe":
                new ClickSacoDeCafe(item, habbo);
                if (this.comprobateCerca(item, habbo.getHabboInfo().getCurrentRoom(), habbo.getClient()) == false) {
                    this.moverHabboFrontObjet(item, habbo);
                }
                return true;
            case "rp_estanteria_cafe":
                new ClickEstanteriaCafe(item, habbo);
                if (this.comprobateCerca(item, habbo.getHabboInfo().getCurrentRoom(), habbo.getClient()) == false) {
                    this.moverHabboFrontObjet(item, habbo);
                }
                return true;
            case "eric18_spar_cafe":
                new ClickCafeteraCafe(item, habbo);
                if (this.comprobateCerca(item, habbo.getHabboInfo().getCurrentRoom(), habbo.getClient()) == false) {
                    this.moverHabboFrontObjet(item, habbo);
                }
                return true;
            case "stone_police":
                new ClickRocaPicarPrision(item, habbo);
                return true;
            case "ktchn_c15_stove_rp":
                new ClickHorno(item, habbo);
                return true;
            case "rp_ingred_cocina_1":
            case "rp_ingred_cocina_2":
                new ClickBuyIngredientes(item, habbo);
                return true;
            case "opti_pizzas":
            case "opti_pommes":
            case "opti_cupcak":
            case "rp_buy_foods":
                new ClickBuyPlatillosComida(item, habbo);
                return true;
            default:
                return false;
        }
    }

    public void ShowItemAction(Habbo habbo, JsonArray InfoItem, JsonArray Buttons) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            habboRp.getHabboClient().send(
                    new MessageManagerDouble("MessageManager", true, Outcoming.ButtonsClickItem, InfoItem, Buttons));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void OcultItemAction(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            HabboClient client = habboRp.getHabboClient();
            if (client != null) {
                client.send(new ShowUserActionsPrimary("ShowUserActionsPrimary", false, null));
                client.send(new MessageManagerDouble("MessageManager", false, Outcoming.ButtonsClickItem, null, null));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void getIncomingActions(GameClient client, THashMap<String, Object> data) {
        String action = (String) data.get("name");
        int itemId = 0;
        if (data.containsKey("item")) {
            itemId = data.get("item") instanceof String ? Integer.parseInt((String) data.get("item"))
                    : ((Double) data.get("item")).intValue();
        }
        ItemIncomingHandler.process(this, action, client.getHabbo(), client.getHabbo().getHabboInfo().getCurrentRoom(),
                itemId, client);
    }

    public void moverHabboFrontObjet(HabboItem item, Habbo habbo) {
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null)
            return;
        RoomTile tile = HabboItem.getSquareInFront(room.getLayout(), item);
        if (tile == null || !tile.isWalkable()) {
            for (RoomTile adj : room.getLayout().getTilesAround(room.getLayout().getTile(item.getX(), item.getY()))) {
                if (adj.isWalkable()) {
                    tile = adj;
                    break;
                }
            }
        }
        if (tile != null)
            habbo.getRoomUnit().setGoalLocation(tile);
    }

    public boolean comprobateCerca(HabboItem item, Room room, GameClient client) {
        RoomTile itemTile = room.getLayout().getTile(item.getX(), item.getY());
        RoomTile userTile = client.getHabbo().getRoomUnit().getCurrentLocation();
        return RoomLayout.tilesAdjecent(userTile, itemTile) || (userTile.x == item.getX() && userTile.y == item.getY());
    }

    // --- Getters / Setters / Deleters ---
    public static THashMap<Habbo, Pescar> getPescar() {
        return lista_pescadores;
    }

    public static void deletePescar(Habbo h) {
        lista_pescadores.remove(h);
    }

    public static OpenCajero getCajeroAbierto(Habbo h) {
        return lista_cajeros_abiertos.get(h);
    }

    public static void addCajero(Habbo h, OpenCajero o) {
        lista_cajeros_abiertos.put(h, o);
    }

    public static void deleteCajeroAbierto(Habbo h) {
        lista_cajeros_abiertos.remove(h);
    }

    public static PicarRocaPrision getPicarRocaPrision(Habbo h) {
        return lista_rocas_picando.get(h);
    }

    public static void addRoca(Habbo h, PicarRocaPrision r) {
        lista_rocas_picando.put(h, r);
    }

    public static void deletePicarRocaPrision(Habbo h) {
        lista_rocas_picando.remove(h);
    }

    public static ForzarPuertaPrision getForzarPuertaPrision(Habbo h) {
        return puertasPrisionForzando.get(h);
    }

    public static void addPuerta(Habbo h, ForzarPuertaPrision p) {
        puertasPrisionForzando.put(h, p);
    }

    public static void deleteForzarPuertaPrision(Habbo h) {
        puertasPrisionForzando.remove(h);
    }
}