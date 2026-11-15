package com.srth.plugin.Rooms.Tyles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.RoomObjet;

import gnu.trove.map.hash.THashMap;

public class RoomTylesManager {

    THashMap<RoomObjet, RoomTyleObjet> rooms = new THashMap<RoomObjet, RoomTyleObjet>();

    public RoomTylesManager() {
    }

    public THashMap<RoomObjet, RoomTyleObjet> getRoomsTyles() {
        return this.rooms;
    }

    public void load() {
        this.registerTiles("hospital");
        this.registerTiles("calles_peligrosas_1");
        this.registerTiles("calles_peligrosas_2");
        this.registerTiles("calles_peligrosas_3");
        this.registerTiles("calle_del_lago");
        this.registerTiles("centro_penitenciario");
        this.registerTiles("basurero");
        this.registerTiles("supermercado");
        this.registerTiles("cafeteria");
        this.registerTiles("calle_del_puente");
        this.registerTiles("calle_de_los_recursos");
    }

    public void registerTiles(String layout) {
        if (comprobateExist(layout)) {
            RoomObjet roomObjetLoading = RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom(layout);
            int roomId = roomObjetLoading.getRoomId();
            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);

            RoomTyleObjet roomTyleObjet = new RoomTyleObjet(roomObjetLoading);
            this.rooms.put(roomObjetLoading, roomTyleObjet);

            if (room != null) {
                if (room.getUserCount() <= 0) {
                    room.loadData();
                    room.run();
                }

                for (HabboItem item : room.getFloorItems()) {
                    String itemName = item.getBaseItem().getName().toLowerCase();

                    switch (itemName) {
                        case "room_switcher_back_left":
                            roomTyleObjet.setBackLeftFlechas(item);
                            break;
                        case "room_switcher_back_right":
                            roomTyleObjet.setBackRightFlechas(item);
                            break;
                        case "room_switcher_front_right":
                            roomTyleObjet.setFrontRightFlechas(item);
                            break;
                        case "room_switcher_front_left":
                            roomTyleObjet.setFrontLeftFlechas(item);
                            break;
                        case "room_switcher_home":
                            roomTyleObjet.setHomeFlechas(item);
                            break;
                    }
                }
            }
        }
    }

    public boolean comprobateExist(String layout) {
        if (Emulator.getGameEnvironment().getRoomManager().layoutExists("roleplay_" + layout)) {
            RoomObjet roomCreate = RoleplayWebsocketsRoomsInfo.getRoomsRoleplayManager().getRoom(layout);
            if (roomCreate != null) {
                return roomCreate.getRoomId() != 0;
            }
        }
        return false;
    }

    public void updateAllTiles() {
        TreeMap<Short, HabboItem> hospFrontLeft = null;
        TreeMap<Short, HabboItem> hospBackRight = null;

        TreeMap<Short, HabboItem> callesPeli3BackRight = null;
        TreeMap<Short, HabboItem> callesPeli3FrontLeft = null;
        TreeMap<Short, HabboItem> callesPeli3BackLeft = null;
        TreeMap<Short, HabboItem> callesPeli3FrontRight = null;

        TreeMap<Short, HabboItem> callesPeli2FrontRight = null;
        TreeMap<Short, HabboItem> callesPeli2BackLeft = null;
        TreeMap<Short, HabboItem> callesPeli1FrontRight = null;
        TreeMap<Short, HabboItem> callesPeli1BackLeft = null;

        TreeMap<Short, HabboItem> supermercadoFrontRight = null;

        TreeMap<Short, HabboItem> calleLagoFrontLeft = null;
        TreeMap<Short, HabboItem> calleLagoBackRight = null;
        TreeMap<Short, HabboItem> centroPeninFrontLeft = null;
        TreeMap<Short, HabboItem> centroPeninBackRight = null;
        TreeMap<Short, HabboItem> calleDelPuenteFrontLeft = null;
        TreeMap<Short, HabboItem> calleDelPuenteFrontRight = null;

        TreeMap<Short, HabboItem> basureroBackRight = null;
        TreeMap<Short, HabboItem> cafeteriaBackLeft = null;

        TreeMap<Short, HabboItem> calleDeLosRecursosBackLeft = null;

        for (RoomTyleObjet element : this.getRoomsTyles().values()) {
            String roomType = element.getRoomObjet().getNameType();

            if (roomType.equalsIgnoreCase("hospital")) {
                hospFrontLeft = element.getFrontLeftFlechas();
                hospBackRight = element.getBackRightFlechas();
            } else if (roomType.equalsIgnoreCase("calles_peligrosas_3")) {
                callesPeli3BackRight = element.getBackRightFlechas();
                callesPeli3FrontLeft = element.getFrontLeftFlechas();
                callesPeli3BackLeft = element.getBackLeftFlechas();
                callesPeli3FrontRight = element.getFrontRightFlechas();
            } else if (roomType.equalsIgnoreCase("calle_del_lago")) {
                calleLagoFrontLeft = element.getFrontLeftFlechas();
                calleLagoBackRight = element.getBackRightFlechas();
            } else if (roomType.equalsIgnoreCase("centro_penitenciario")) {
                centroPeninFrontLeft = element.getFrontLeftFlechas();
                centroPeninBackRight = element.getBackRightFlechas();
            } else if (roomType.equalsIgnoreCase("calle_del_puente")) {
                calleDelPuenteFrontLeft = element.getFrontLeftFlechas();
                calleDelPuenteFrontRight = element.getFrontRightFlechas();
            } else if (roomType.equalsIgnoreCase("basurero")) {
                basureroBackRight = element.getBackRightFlechas();
            } else if (roomType.equalsIgnoreCase("calles_peligrosas_2")) {
                callesPeli2FrontRight = element.getFrontRightFlechas();
                callesPeli2BackLeft = element.getBackLeftFlechas();
            } else if (roomType.equalsIgnoreCase("calles_peligrosas_1")) {
                callesPeli1FrontRight = element.getFrontRightFlechas();
                callesPeli1BackLeft = element.getBackLeftFlechas();
            } else if (roomType.equalsIgnoreCase("supermercado")) {
                supermercadoFrontRight = element.getFrontRightFlechas();
            } else if (roomType.equalsIgnoreCase("cafeteria")) {
                cafeteriaBackLeft = element.getBackLeftFlechas();
            } else if (roomType.equalsIgnoreCase("calle_de_los_recursos")) {
                calleDeLosRecursosBackLeft = element.getBackLeftFlechas();
            }

        }

        EventCreateConexionTiles connector = new EventCreateConexionTiles();

        // Conexiones estándar (Ordenadas por X)
        connectMaps(hospFrontLeft, callesPeli3BackRight, connector, "Hosp-Cali3");
        connectMaps(hospBackRight, calleLagoFrontLeft, connector, "Hosp-Lago");
        connectMaps(calleLagoBackRight, centroPeninFrontLeft, connector, "Lago-CPenitenciario");

        connectMaps(centroPeninBackRight, calleDelPuenteFrontLeft, connector, "CPenitenciario-CLago");
        connectMaps(calleDelPuenteFrontRight, calleDeLosRecursosBackLeft, connector, "CPuente-CRecursos");

        connectMaps(callesPeli3FrontLeft, basureroBackRight, connector, "CPeligrosas3-Basurero");

        connectMaps(callesPeli3FrontRight, cafeteriaBackLeft, connector, "Excep_Calles3_FrontRight_Cafeteria-BackLeft");

        // BackLeft con FrontRight (Se ordenará por Y automáticamente)
        connectMaps(callesPeli3BackLeft, callesPeli2FrontRight, connector, "Excep_BackLeft_FrontRight_CP3-CP2");
        connectMaps(callesPeli2BackLeft, callesPeli1FrontRight, connector, "Excep_BackLeft_FrontRight_CP2-CP1");
        connectMaps(callesPeli1BackLeft, supermercadoFrontRight, connector,
                "Excep_BackLeft_FrontRight_CP1-Supermercado");
    }

    /**
     * Conecta dos grupos de flechas de forma bidireccional.
     * Si el label contiene "BackLeft" y "FrontRight", ordena por el eje Y.
     */
    private void connectMaps(TreeMap<Short, HabboItem> mapA, TreeMap<Short, HabboItem> mapB,
            EventCreateConexionTiles connector, String label) {

        if (mapA == null || mapB == null || mapA.size() != mapB.size()) {
            int sizeA = (mapA != null) ? mapA.size() : 0;
            int sizeB = (mapB != null) ? mapB.size() : 0;
            RoleplayWebsocketsRoomsInfo.utilities()
                    .LOGGER("Error [" + label + "]: Mapas nulos o tamaños desiguales (" + sizeA + " vs " + sizeB + ")");
            return;
        }

        // Determinar si aplica la excepción de ejes
        boolean useYAxis = label.contains("BackLeft") && label.contains("FrontRight");

        List<HabboItem> listA = new ArrayList<>(mapA.values());
        List<HabboItem> listB = new ArrayList<>(mapB.values());

        if (useYAxis) {
            // Ordenamiento por Y para flechas diagonales inversas
            listA.sort((i1, i2) -> Short.compare(i1.getY(), i2.getY()));
            listB.sort((i1, i2) -> Short.compare(i1.getY(), i2.getY()));
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("[" + label + "] Aplicando ordenamiento por eje Y");
        } else {
            // Ordenamiento estándar por X
            listA.sort((i1, i2) -> Short.compare(i1.getX(), i2.getX()));
            listB.sort((i1, i2) -> Short.compare(i1.getX(), i2.getX()));
        }

        for (int i = 0; i < listA.size(); i++) {
            HabboItem itemA = listA.get(i);
            HabboItem itemB = listB.get(i);

            connector.setTiles(itemA.getId(), itemB.getId());
            connector.setTiles(itemB.getId(), itemA.getId());

            String coord = useYAxis ? "Y:" + itemA.getY() : "X:" + itemA.getX();
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER("[" + label + "] Sincronizado " + coord + " IDs: "
                    + itemA.getId() + "<->" + itemB.getId());
        }
    }
}