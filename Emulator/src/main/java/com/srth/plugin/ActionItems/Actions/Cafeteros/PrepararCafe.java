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
import com.srth.plugin.Tutorial.partes_empleos.EventosEmpleosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManagerDouble;
import gnu.trove.map.hash.THashMap;

public class PrepararCafe extends Scheduler {

    private HabboItem item;
    private Habbo habbo;
    private int roomUnitId = -1;
    private int elapsedSeconds = 0;
    private final int default_sumatoria = 30; // 30 segundos totales
    private int count = 1;

    // Mensajes realistas para la preparación (infusión)
    public String[] mensajesPreparacion = {
            "El agua ya está alcanzando la temperatura ideal...",
            "Vertiendo el agua caliente sobre el café recién molido.",
            "Esperando que el café filtre correctamente... ¡Qué aroma!",
            "Observando cómo cae el café gota a gota en la jarra.",
            "Añadiendo el toque final... esto va a estar delicioso."
    };

    public static final THashMap<Habbo, PrepararCafe> users = new THashMap<>();

    public PrepararCafe(HabboItem habboItem, Habbo habbo) {
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
                room.makeSit(habbo); // Opcional, mantiene consistencia con las otras acciones

                // Ocultar tiempo anterior y mostrar la barra de proceso en pantalla
                habboRp.getVidaHambreDormir().ocultarTimeProcess();
                habboRp.getVidaHambreDormir().showTimeProcess(
                        "RoleplayWebsocketsRoomsInfo/job_cafetero/maquina.png?v2",
                        "Comienzo a preparar una taza de café...",
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

                // 2. Progreso intermedio (a los 10 y 20 segundos)
                if (elapsedSeconds == 10 || elapsedSeconds == 20) {
                    room.giveEffect(currentHabbo, 4, 10);

                    // Evolución visual del item
                    count++;
                    item.setExtradata(count + "");
                    room.updateItemState(item);

                    Random rand = new Random();
                    String msgAleatorio = mensajesPreparacion[rand.nextInt(mensajesPreparacion.length)];

                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/job_cafetero/maquina.png?v2", msgAleatorio,
                            3, true);

                    currentHabbo.talk(msgAleatorio, RoomChatMessageBubbles.NORMAL);
                }

                // 3. Finalización (30 segundos)
                if (elapsedSeconds >= default_sumatoria) {
                    room.giveEffect(currentHabbo, 0, -1);
                    item.setExtradata("0");
                    room.updateItemState(item);
                    this.resetTime();

                    habboRp.getVidaHambreDormir().ocultarTimeProcess();
                    String mensajeFinal = "¡Perfecto! El café está servido. No hay nada como el café recién hecho.";
                    habboRp.getVidaHambreDormir().showTimeProcess(
                            "RoleplayWebsocketsRoomsInfo/job_cafetero/maquina.png?v2", mensajeFinal,
                            3, true);

                    currentHabbo.talk(mensajeFinal, RoomChatMessageBubbles.GREEN);
                    currentHabbo.getRoomUnit().setCanWalk(true);
                    room.giveHandItem(currentHabbo, 1);

                    Emulator.getThreading().run(() -> {
                        try {
                            final HabboRp habboRpSet = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                    .getHabboRp(currentHabbo);

                            if (habboRpSet != null) {
                                habboRpSet.getVidaHambreDormir().ocultarTimeProcess();
                                habboRpSet.setProgressPendiente(false);

                                habboRpSet.showMessage("RoleplayWebsocketsRoomsInfo/job_cafetero/estanteria.png",
                                        "¡Acércate a la estantería para colocar el café antes de tomártelo!");

                                habboRpSet.registerCache("granos_cafe_progreso", "2");
                            }

                            new EventosEmpleosTutorial(currentHabbo).eventPrepararCafeMaquina();

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