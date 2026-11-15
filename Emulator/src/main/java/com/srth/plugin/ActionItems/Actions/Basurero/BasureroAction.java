package com.srth.plugin.ActionItems.Actions.Basurero;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.habbo.HabboRp;

import java.util.Random;

public class BasureroAction extends Scheduler {

    private HabboItem item;
    private Habbo habbo;
    private int roomUnitId = -1;
    private int elapsedSeconds = 0;
    private int default_sumatoria = 10; // Tiempo total de búsqueda en segundos
    private static final int RESET_BASURERO_TIME = 30; // Tiempo en segundos para que el basurero vuelva a estar
                                                       // disponible

    public BasureroAction(HabboItem item, Habbo habbo) {
        super(1);
        try {
            if (item == null || habbo == null) {
                return;
            }

            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp == null) {
                return;
            }

            Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room == null) {
                return;
            }

            if (room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                return;
            }

            if (item.getExtradata().equalsIgnoreCase("0")) {
                RoleplayWebsocketsRoomsInfo.utilities()
                        .LOGGER("La basura ya fue saqueada por alguien más, no puedes buscar en ella.");
                habbo.whisper("Este basurero ya está vacío, espera un momento.", RoomChatMessageBubbles.RED);
                return;
            }

            RoomTile tile = HabboItem.getSquareInFront(room.getLayout(), item);
            if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                    habbo.getRoomUnit().getCurrentLocation())) {
                if (tile != null && tile.isWalkable()) {
                    if (tile != habbo.getHabboInfo().getCurrentRoom().getLayout().getDoorTile()) {
                        habbo.getRoomUnit().setGoalLocation(tile);
                    }
                }
            } else {
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);
            }

            this.item = item;
            this.habbo = habbo;
            this.roomUnitId = habbo.getRoomUnit().getId();
            this.elapsedSeconds = 0;
            this.disposed = false;

            // Efecto de buscar / agacharse
            room.giveEffect(habbo, Integer.valueOf(4), 10);

            // Mostrar barra de progreso en la interfaz
            habboRp.getVidaHambreDormir().showTimeProcess(
                    "RoleplayWebsocketsRoomsInfo/basurero/basurero.png?v2",
                    "Revisando y buscando en el basurero...",
                    default_sumatoria, true);
            habboRp.setProgressPendiente(true);

            // Cambiar el estado del basurero a "ocupado" (extradata = "0") inmediatamente
            this.updateItemState("0");

            // PROGRAMAR EL REINICIO AUTOMÁTICO INCONDICIONAL:
            // Sin importar si el usuario termina, cancela o se desconecta, en 30 segundos
            // vuelve a "1"
            Emulator.getThreading().run(() -> {
                if (item != null && item.getExtradata().equalsIgnoreCase("0")) {
                    updateItemState("1");
                }
            }, RESET_BASURERO_TIME * 1000);

            // Iniciar el ciclo del scheduler cada 1 segundo exacto para la búsqueda del
            // usuario
            Emulator.getThreading().run(this, 1000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);
        }
    }

    @Override
    public void run() {
        super.run();

        if (this.disposed || roomUnitId == -1 || habbo == null) {
            return;
        }

        try {
            final Room room = habbo.getHabboInfo().getCurrentRoom();
            if (room == null) {
                this.setDisposed(true);
                return;
            }

            Habbo currentHabbo = room.getHabboByRoomUnitId(this.roomUnitId);

            if (currentHabbo != null && currentHabbo.isOnline()) {
                HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(currentHabbo);

                // Validación: Si el usuario se aleja del basurero, se cancela su acción
                // personal
                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                        currentHabbo.getRoomUnit().getCurrentLocation())) {
                            
                    // Restaurar el estado del basurero a "1" (disponible)
                    this.updateItemState("1"); 

                    this.resetAndClean(room, currentHabbo, habboRp);
                    currentHabbo.whisper("Te alejaste del basurero y dejaste de revisar.", RoomChatMessageBubbles.RED);
                    return;
                }

                // Incrementamos los segundos transcurridos
                elapsedSeconds++;

                // CUANDO SE COMPLETAN SEGUNDOS TOTALES DE BÚSQUEDA (10 segundos)
                if (elapsedSeconds >= default_sumatoria) {

                    // Ocultar efecto y barra de progreso
                    room.giveEffect(currentHabbo, Integer.valueOf(0), -1);
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.setProgressPendiente(false);

                    this.roomUnitId = -1;
                    this.setDisposed(true);

                    // LÓGICA DE PROBABILIDADES
                    int probabilidad = Emulator.getRandom().nextInt(100);

                    if (probabilidad < 40) {
                        currentHabbo.shout("¡Mierda! Aquí solo hay desperdicios inútiles.", RoomChatMessageBubbles.RED);
                        currentHabbo.talk("*Revisa el contenedor pero no encuentra nada de valor*");

                    } else if (probabilidad >= 40 && probabilidad < 75) {
                        String itemBasura = "basura";
                        int cantidadBasura = Emulator.getRandom().nextInt(3) + 1;

                        if (habboRp.getNewInventoryHabbo().getItem(itemBasura) == null) {
                            habboRp.getNewInventoryHabbo().createItem(itemBasura, cantidadBasura);
                        } else {
                            habboRp.getNewInventoryHabbo().addCantidadItem(itemBasura, cantidadBasura);
                        }

                        currentHabbo.shout("¡Has encontrado algo de basura!", RoomChatMessageBubbles.NORMAL);
                        currentHabbo.whisper("Encontraste " + cantidadBasura + "x de basura en el contenedor.",
                                RoomChatMessageBubbles.NORMAL);

                    } else {
                        String itemCannabis = "cannabis";
                        int cantidadCannabis = 1;

                        if (habboRp.getNewInventoryHabbo().getItem(itemCannabis) == null) {
                            habboRp.getNewInventoryHabbo().createItem(itemCannabis, cantidadCannabis);
                        } else {
                            habboRp.getNewInventoryHabbo().addCantidadItem(itemCannabis, cantidadCannabis);
                        }

                        currentHabbo.shout("Encontraste algo prohibido entre los desperdicios...",
                                RoomChatMessageBubbles.GREEN);
                        currentHabbo.whisper("¡Has obtenido 1x de Cannabis!", RoomChatMessageBubbles.GREEN);
                    }

                    return;
                }

                // Reprogramar el siguiente tick en 1 segundo exacto
                if (!this.disposed) {
                    Emulator.getThreading().run(this, 1000);
                }

            } else {
                this.setDisposed(true);
            }
        } catch (Exception e) {
            this.setDisposed(true);
        }
    }

    private void resetAndClean(Room room, Habbo habboTarget, HabboRp habboRpTarget) {
        this.roomUnitId = -1;
        this.setDisposed(true);

        if (room != null && habboTarget != null) {
            room.giveEffect(habboTarget, Integer.valueOf(0), -1);
        }

        if (habboRpTarget != null) {
            try {
                habboRpTarget.getVidaHambreDormir().ocultarTimeProcess();
                habboRpTarget.setProgressPendiente(false);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }

    private void updateItemState(String state) {
        item.setExtradata(state);
        item.needsUpdate(true);
        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room != null) {
            room.updateItem(item);
        }
    }
}