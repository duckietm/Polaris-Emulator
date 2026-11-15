package com.srth.plugin.ActionItems.Actions.Cafeteros;

import java.util.Random;
import com.eu.habbo.Emulator;
import com.eu.habbo.core.Scheduler;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Rooms.Cycleables.RoomCafeteria;
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;
import gnu.trove.map.hash.THashMap;

public class ColocarCafeEstanteria extends Scheduler {

    private HabboItem item;
    private Habbo habbo;
    private int roomUnitId = -1;
    private int elapsedSeconds = 0;
    private final int default_sumatoria = 10; // 10 segundos totales
    private int cafes_preparados = 3;

    // Mensajes para la acción de organizar/colocar
    public String[] mensajesEstanteria = {
            "Alineando los paquetes de café en el estante...",
            "Organizando el stock por fecha de tostado.",
            "Colocando las etiquetas de precio en la estantería.",
            "Rellenando los huecos vacíos con café recién molido.",
            "Verificando que todo el café esté a la vista del cliente."
    };

    public static final THashMap<Habbo, ColocarCafeEstanteria> users = new THashMap<>();

    public ColocarCafeEstanteria(HabboItem habboItem, Habbo habbo) {
        super(1); // Se ejecuta cada segundo
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp == null || habboItem == null || habbo == null)
                return;

            this.habbo = habbo;
            this.item = habboItem;
            Room room = habbo.getHabboInfo().getCurrentRoom();

            if (room != null) {
                room.giveEffect(habbo, 4, 9); // Efecto visual inicial
                room.makeSit(habbo); // Opcional, mantiene consistencia con la pesca

                // Ocultar tiempo anterior y mostrar la barra de proceso en pantalla
                habboRp.getVidaHambreDormir().ocultarTimeProcess();
                habboRp.getVidaHambreDormir().showTimeProcess(
                        "RoleplayWebsocketsRoomsInfo/job_cafetero/estanteria.png?v2",
                        "Iniciando organización de estantería...",
                        3, true);
                habboRp.setProgressPendiente(true);

                this.roomUnitId = habbo.getRoomUnit().getId();
                this.elapsedSeconds = 0;
                this.disposed = false;

                // Lanzar scheduler
                Emulator.getThreading().run(this, 1000);
                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(habbo);

                users.put(habbo, this);
            }
        } catch (Exception ignored) {
        }
    }

    public void reloadConfig() {
        if (this.disposed) {
            this.disposed = false;
            Emulator.getThreading().run(this, 1000);
        }
    }

    public void ocultVentana(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            if (habboRp != null) {
                habboRp.getHabboClient()
                        .send(new MessageManagerDouble("MessageManager", false, Outcoming.OpenCajero, null, null));
            }
        } catch (Exception ignored) {
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

                // 1. Verificación de distancia adyacente al item
                if (!RoomLayout.tilesAdjecent(room.getLayout().getTile(item.getX(), item.getY()),
                        currentHabbo.getRoomUnit().getCurrentLocation())) {

                    this.resetTime();
                    this.roomUnitId = -1;
                    this.setDisposed(true);

                    room.giveEffect(currentHabbo, 0, -1);
                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.setProgressPendiente(false);

                    this.stopProcess();
                    return;
                }

                // Incrementamos los segundos transcurridos
                elapsedSeconds++;

                // 2. Evento a mitad del proceso (a los 5 segundos)
                if (elapsedSeconds == 5) {
                    room.giveEffect(currentHabbo, 4, 10);

                    Random rand = new Random();
                    String msgAleatorio = mensajesEstanteria[rand.nextInt(mensajesEstanteria.length)];

                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/job_cafetero/estanteria.png?v2", msgAleatorio,
                            3, true);

                    currentHabbo.talk(msgAleatorio, RoomChatMessageBubbles.NORMAL);
                }

                // 3. Finalización (10 segundos)
                if (elapsedSeconds >= default_sumatoria) {
                    room.giveEffect(currentHabbo, 0, -1);
                    this.resetTime();

                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    String mensajeFinal = "¡Listo! He agregado " + cafes_preparados
                            + " cafés a la estantería, ya se encuentra organizada.";
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/job_cafetero/estanteria.png?v2", mensajeFinal,
                            3, true);

                    currentHabbo.shout(mensajeFinal, RoomChatMessageBubbles.NORMAL);
                    room.giveHandItem(currentHabbo, 0);

                    Emulator.getThreading().run(() -> {
                        try {
                            final HabboRp habboRpSet = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                    .getHabboRp(currentHabbo);

                            if (habboRpSet != null) {
                                habboRpSet.getVidaHambreDormir().ocultarTimeProcess();
                                habboRpSet.setProgressPendiente(false);

                                habboRpSet.showMessage("RoleplayWebsocketsRoomsInfo/job_cafetero/estanteria.png",
                                        "¡Has repuesto la estantería!");

                                RoomCafeteria.incrementCafeEstante(cafes_preparados);
                                RoleplayWebsocketsRoomsInfo.getItemActionManager().OcultItemAction(currentHabbo);

                                habboRpSet.registerCache("estanteria_reposta", "1");
                                habboRpSet.registerCache("granos_cafe_progreso", "0");
                            }

                            RoomCafeteria.comprobateStatusEstanteria(room);
                            new EventosEmpleosTutorial(currentHabbo).eventColocarCafeEstanteria();

                            this.roomUnitId = -1;
                            this.setDisposed(true);
                            this.stopProcess();

                        } catch (Exception e) {
                        }
                    }, 1000);

                    return;
                }

                // Reprogramar el siguiente segundo
                if (!this.disposed) {
                    Emulator.getThreading().run(this, 1000);
                }
            } else {
                this.setDisposed(true);
                this.stopProcess();
            }
        } catch (Exception e) {
            this.setDisposed(true);
            this.stopProcess();
        }
    }

    private void stopProcess() {
        this.roomUnitId = -1;
        if (habbo != null) {
            users.remove(habbo);
            this.ocultVentana(habbo);
        }
        this.setDisposed(true);
    }

    public boolean isDisposed() {
        return this.disposed;
    }

    public void setDisposed(boolean disposed) {
        this.disposed = disposed;
    }

    private void resetTime() {
        this.habbo = null;
        this.roomUnitId = -1;
        this.elapsedSeconds = 0;
    }
}