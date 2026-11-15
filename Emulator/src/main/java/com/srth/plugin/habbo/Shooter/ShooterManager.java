package com.srth.plugin.habbo.Shooter;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.events.furniture.FurnitureToggleEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Consola.TypeMessageConsola;
import com.srth.plugin.Tutorial.partes_delitos.EventosDelitosTutorial;
import com.srth.plugin.habbo.HabboRp;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;
import com.srth.plugin.websocket.communication.outgoing.messages.ShowInfoOtherHabbo;
import com.srth.plugin.websocket.communication.outgoing.messages.outworks.PlaySound;

import gnu.trove.map.hash.THashMap;

public class ShooterManager {

    private static THashMap<Habbo, ShooterHabbo> logs_asesinatos = new THashMap<Habbo, ShooterHabbo>();

    public ShooterManager() {

    }

    public static void userEnterRoom(Room room, Habbo habbo) {
        try {
            if (room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                    .getInt("global_room.category.roleplay.id")) {
                if (room.getId() == RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("medico").getRoomId()) {

                    HabboRp habboRpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
                    ShooterHabbo shooterHabboClient = habboRpClient.getShooterHabbo();

                    if (shooterHabboClient.getPvpActive() == 1) {

                        shooterHabboClient.setPvpActive(0);

                        if (shooterHabboClient.getPvpManoActive() == 1) {
                            shooterHabboClient.setPvpManoActive(0);
                        }

                        if (habboRpClient.getEnable() != 13) {
                            habboRpClient.setEnable(0);
                            room.giveEffect(habbo.getRoomUnit(), 0, -1);
                        }

                        if (habboRpClient.getHanditem() == 2002) {
                            habboRpClient.setHanditem(0);
                            room.giveHandItem(habbo.getRoomUnit(), 0);
                        }

                        ShooterHabbo asesined_by = getAsesinatos().get(habbo);
                        if (asesined_by != null) {
                            asesined_by.getDeathList().remove(habbo.getHabboInfo().getId());
                            getAsesinatos().remove(habbo);
                        }
                    }

                }
            }
        } catch (Exception e) {
        }
    }

    public void HandlerOnClickUser(Habbo habbo, HabboClient client) {
        if (habbo == null || client == null) {
            return;
        }
        // Cuando sea tu usuario
        if (habbo == client.getHabbo()) {
        } else
        // Cuando no sea tu usuario
        if (habbo != client.getHabbo()) {

            Room room = client.getHabbo().getHabboInfo().getCurrentRoom();
            try {
                if (room == null
                        || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                                .getInt("global_room.category.roleplay.id")
                        || room.getId() == RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("medico").getRoomId()) {
                    return;
                }
            } catch (Exception e) {
            }

            if (RoleplayWebsocketsRoomsInfo.getHorarioManager().getTimeHorario()
                    .equalsIgnoreCase("dia")) {
                return;
            }

            try {
                HabboRp habboRpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(client.getHabbo());
                HabboRp habboRpOther = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

                this.shooterArmasPVP(habboRpClient, habboRpOther, client, habbo, room);
                this.manoPVP(habboRpClient, habboRpOther, client, habbo, room);
            } catch (Exception e) {
            }
        }
    }

    // --- CONSTANTES DE EQUILIBRIO ---
    private static final int ENERGIA_MAXIMA = 100;
    private static final int COSTO_GOLPE = 25;
    private static final int RECUPERACION_POR_PASO = 15;
    private static final int BONUS_DISTANCIA_RECUPERACION = 10;

    public void manoPVP(HabboRp habboRpClient, HabboRp habboRpOther, HabboClient client, Habbo habbo, Room room) {
        try {

            ShooterHabbo shooterHabboClient = habboRpClient.getShooterHabbo();
            ShooterHabbo shooterHabboOther = habboRpOther.getShooterHabbo();

            int handItem = habboRpClient.getHanditem();

            // Cuando tenga un arma ilegal en mano
            if (handItem == 2002) {
                return;
            }

            if (shooterHabboClient.getPvpActive() == 0 || shooterHabboClient.getPvpManoActive() == 0) {
                return;
            }

            // --- SISTEMA DE ENERGÍA (STAMINA) ---
            if (habboRpClient.getCacheString("energia_pvp") == null) {
                habboRpClient.registerCache("energia_pvp", String.valueOf(ENERGIA_MAXIMA));
            }

            int energiaActual = habboRpClient.getCacheInt("energia_pvp");

            // Verificamos si tiene energía suficiente para usar la llave
            if (energiaActual < COSTO_GOLPE) {
                habboRpClient.showMessage("RoleplayWebsocketsRoomsInfo/job_polices/work_no_accion.gif?v=1",
                        "Estás demasiado agotado para atacar. ¡Recupera aliento!");
                return;
            }

            if (getAsesinatos().get(client.getHabbo()) != null || habboRpOther.getVidaHambreDormir().getVida() <= 1) {
                return;
            }

            this.walkHabboTo(client.getHabbo(), habbo);

            double distancia = client.getHabbo().getRoomUnit().getCurrentLocation()
                    .distance(habbo.getRoomUnit().getCurrentLocation());

            if (distancia >= 2) {
                client.getHabbo().getRoomUnit().setGoalLocation(habbo.getRoomUnit().getCurrentLocation());
                return;
            }

            shooterHabboClient.setPvpActive(1);
            shooterHabboOther.setPvpActive(1);

            if (shooterHabboOther.getVida() >= 0) {
                if (getAsesinatos().get(habbo) == null) {

                    // Probabilidad de fallo (33%)
                    int asertarGolpe = Emulator.getRandom().nextInt(3);
                    if (asertarGolpe > 2) {
                        habboRpClient.registerCache("energia_pvp", String.valueOf(energiaActual - (COSTO_GOLPE / 2)));
                        return;
                    }

                    // --- LÓGICA DE GOLPES Y DAÑO SEGÚN HERRAMIENTA ---
                    int golpesRealizados = habboRpClient.getCacheInt("contador_manotazos");
                    int proximoGolpe = golpesRealizados + 1;
                    boolean esCritico = (proximoGolpe % 4 == 0);

                    int danoBase = 2;
                    int danoCritico = 6;

                    // Definimos el daño según la herramienta en mano
                    switch (handItem) {
                        case 2003: // Hacha de madera
                        case 2005: // Pico de madera
                            danoBase = 4;
                            danoCritico = 9;
                            break;

                        case 2004: // Hacha de piedra
                        case 2006: // Pico de piedra
                            danoBase = 8;
                            danoCritico = 16;
                            break;

                        case 0: // Puño / Mano limpia
                        default:
                            danoBase = 2;
                            danoCritico = 6;
                            break;
                    }

                    int dañoAplicar = esCritico ? danoCritico : danoBase;
                    int setVida = shooterHabboOther.restarVida(dañoAplicar);

                    // --- DESCUENTO DE ENERGÍA ---
                    int nuevaEnergia = energiaActual - COSTO_GOLPE;
                    habboRpClient.registerCache("energia_pvp", String.valueOf(nuevaEnergia));
                    habboRpClient.registerCache("contador_manotazos", proximoGolpe + "");

                    // --- NOMBRE DEL OBJETO SEGÚN EL HANDITEM ---
                    String nombreObjeto = "mano";
                    switch (handItem) {
                        case 2003:
                            nombreObjeto = "hacha de madera";
                            break;
                        case 2005:
                            nombreObjeto = "pico de madera";
                            break;
                        case 2004:
                            nombreObjeto = "hacha de piedra";
                            break;
                        case 2006:
                            nombreObjeto = "pico de piedra";
                            break;
                        case 0:
                        default:
                            nombreObjeto = "mano";
                            break;
                    }

                    // --- CONSTRUCCIÓN DEL MENSAJE DE ROL ---
                    String mensajeFinal = "";
                    if (esCritico) {
                        String[] criticos = ("*¡CRÍTICO! Asesta un golpe brutal con su %objeto% a %username% dejándolo aturdido*;"
                                + "*¡CLANK! Impacta su %objeto% con una fuerza devastadora sobre las costillas de %username%*;"
                                + "*Propina un golpe seco y demoledor con su %objeto% a %username%*")
                                .split(";");
                        mensajeFinal = criticos[Emulator.getRandom().nextInt(criticos.length)];
                    } else {
                        String[] normales = ("*Golpea rápidamente a %username% con su %objeto%*;"
                                + "*Le propina un golpe seco con su %objeto% a %username% en el brazo*;"
                                + "*Arremete contundentemente contra %username% usando su %objeto%*;"
                                + "*Descarga un golpe de %objeto% sobre %username%*").split(";");

                        int dadoRandom = Emulator.getRandom().nextInt(5);
                        if (golpesRealizados == 0 || dadoRandom == 1) {
                            mensajeFinal = normales[Emulator.getRandom().nextInt(normales.length)];
                        }
                    }

                    if (!mensajeFinal.isEmpty()) {
                        // Reemplazamos tanto el nombre de usuario como el objeto utilizado
                        String mensajeFormateado = mensajeFinal
                                .replace("%username%", habboRpOther.getUsername())
                                .replace("%objeto%", nombreObjeto);

                        habboRpClient.getHabbo().talk(mensajeFormateado);

                        int reproducir_sonido = Emulator.getRandom().nextInt(3);
                        if (reproducir_sonido >= 2) {
                            habboRpOther.getHabboClient()
                                    .send(new PlaySound("PlaySound", true, "vida_down.mp3", habbo));
                            for (Habbo habbos : room.getHabbos()) {
                                try {
                                    HabboRp habboRpH = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                            .getHabboRp(habbos);
                                    if (habboRpH != null) {
                                        habboRpH.getHabboClient()
                                                .send(new PlaySound("PlaySound", true, "vida_down.mp3", habbos));
                                    }
                                } catch (Exception e) {
                                }
                            }
                        }
                    }

                    // --- LÓGICA DE MUERTE ---
                    if (setVida <= 3) {
                        if (shooterHabboClient.getDeathList().get(habbo.getHabboInfo().getId()) == null) {

                            shooterHabboOther.restarVida(100);
                            habboRpOther.getVidaHambreDormir().matarHabbo();

                            // Enviar mensaje a todos
                            RoleplayWebsocketsRoomsInfo.getConsolaManager().sendMessageConsolaGlobal(
                                    TypeMessageConsola.getMessageUserToUser(habboRpClient.getHabbo(),
                                            habboRpOther.getHabbo(), "asesinó en un combate a "));

                            habboRpClient.getHabbo().shout(
                                    "*He dejado fuera de combate a %username% tras un impacto final*"
                                            .replace("%username%", habboRpOther.getUsername()));

                            new EventosDelitosTutorial().eventAsesinarPvpMano(habboRpClient.getHabbo()); 

                            habboRpClient.setHabboDelitos(habboRpClient.getHabbo());
                            habboRpClient.getDelitosHabbo().incrementDelitos(3);
                            habboRpClient.getDelitosHabbo().sendAlertToPolices(habboRpClient.getHabbo());

                            shooterHabboClient.getDeathList().put(habbo.getHabboInfo().getId(), habbo);
                            getAsesinatos().put(habbo, shooterHabboClient);

                            habboRpClient.getHabboClient()
                                    .send(new ShowInfoOtherHabbo("ShowInfoOtherHabbo", false, null));
                            habboRpClient.getHabboClient().send(
                                    new MessageManager("MessageManager", false, Outcoming.InventoryButtonsOther, null));

                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void onUserMove(UserTakeStepEvent e) {
        Room room = e.habbo.getHabboInfo().getCurrentRoom();
        try {
            if (room == null
                    || room.getCategory() != RoleplayWebsocketsRoomsInfo.getConfig()
                            .getInt("global_room.category.roleplay.id")
                    || room.getId() == RoleplayWebsocketsRoomsInfo.getJobsManager().getJob("medico").getRoomId()) {
                return;
            }

            // (Opcional) Validación de noche según tu lógica original
            if (RoleplayWebsocketsRoomsInfo.getHorarioManager().getTimeHorario()
                    .equalsIgnoreCase("dia")) {
                return;
            }

            HabboRp habboRpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(e.habbo);

            // --- LÓGICA DE RECUPERACIÓN DE ENERGÍA ---
            int energiaActual = habboRpClient.getCacheInt("energia_pvp");

            if (energiaActual < ENERGIA_MAXIMA) {
                int recuperacion = RECUPERACION_POR_PASO;

                // Buscamos si hay un oponente cerca para aplicar bonus por alejarse
                // Esto asume que el usuario está en combate si su energia < MAX
                // Podemos verificar la distancia con el último atacado si lo guardaras en caché
                // Por ahora, aplicamos una recuperación base y un bonus si se mueve

                int nuevaEnergia = Math.min(ENERGIA_MAXIMA, energiaActual + recuperacion);
                habboRpClient.registerCache("energia_pvp", String.valueOf(nuevaEnergia));

                // Si se recuperó por completo, avisar o resetear combos
                if (nuevaEnergia >= ENERGIA_MAXIMA) {
                    habboRpClient.registerCache("contador_manotazos", "0");
                    // e.habbo.whisper("¡Energía recuperada!");
                }

            }

        } catch (Exception er) {
            er.printStackTrace();
        }
    }

    public void walkHabboTo(Habbo habboFrom, Habbo habboTo) {
        RoomTile tile = habboTo.getHabboInfo().getCurrentRoom().getLayout().getTileInFront(
                habboTo.getRoomUnit().getCurrentLocation(),
                habboTo.getRoomUnit().getBodyRotation().getValue());
        if (tile != null && tile.isWalkable()) {
            if (habboTo.getHabboInfo().getCurrentRoom().getLayout().getDoorTile() == tile) {
                return;
            }
            habboFrom.getRoomUnit().setGoalLocation(tile);
        }
    }

    public void shooterArmasPVP(HabboRp habboRpClient, HabboRp habboRpOther, HabboClient client, Habbo habbo,
            Room room) {
        try {
            if (habboRpClient.getHanditem() != 2002) {
                return;
            }

            if (getAsesinatos().get(client.getHabbo()) != null || habboRpOther.getVidaHambreDormir().getVida() <= 1) {
                return;
            }

            if (client.getHabbo().getRoomUnit().getCurrentLocation()
                    .distance(habbo.getRoomUnit().getCurrentLocation()) >= 3) {
                client.getHabbo().getRoomUnit().setGoalLocation(habbo.getRoomUnit().getCurrentLocation());
                return;
            }

            ShooterHabbo shooterHabboClient = habboRpClient.getShooterHabbo();
            ShooterHabbo shooterHabboOther = habboRpOther.getShooterHabbo();

            shooterHabboClient.setPvpActive(1);
            shooterHabboOther.setPvpActive(1);

            if (shooterHabboOther.getVida() >= 0) {
                if (getAsesinatos().get(habbo) == null) {

                    int asertarDisparo = Emulator.getRandom().nextInt(3);
                    if (asertarDisparo > 2) {
                        return;
                    }

                    if (shooterHabboClient.getBalas() == 0) {
                        habboRpClient.showMessage("/RoleplayWebsocketsRoomsInfo/actions/munition.png",
                                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action.balas_empty"));
                        return;
                    }

                    // --- SISTEMA DE CRÍTICOS ---
                    int disparosRealizados = habboRpClient.getCacheInt("contador_disparos");
                    int proximoDisparo = disparosRealizados + 1;
                    boolean esCritico = (proximoDisparo % 4 == 0); // Crítico cada 4 disparos

                    shooterHabboClient.useBalas();

                    int dañoAplicar = esCritico ? 10 : 3;
                    int setVida = shooterHabboOther.restarVida(dañoAplicar);

                    // Guardamos el progreso en el caché
                    habboRpClient.registerCache("contador_disparos", String.valueOf(proximoDisparo));

                    // --- EFECTOS DE CHAT PARA CRÍTICOS ---
                    if (esCritico) {
                        String[] frasesCriticas = ("*¡HEADSHOT! El proyectil impacta directamente en la cabeza de %username%*;"
                                + "*¡IMPACTO CRÍTICO! La bala atraviesa una zona vital de %username%*;"
                                + "*Aprovecha el retroceso para encajar un disparo letal en el pecho de %username%*")
                                .split(";");

                        String mensaje = frasesCriticas[Emulator.getRandom().nextInt(frasesCriticas.length)];
                        habboRpClient.getHabbo().talk(mensaje.replace("%username%", habboRpOther.getUsername()));
                    }

                    int reproducir_sonido = Emulator.getRandom().nextInt(3);
                    if (reproducir_sonido >= 2) {
                        habboRpOther.getHabboClient().send(new PlaySound("PlaySound", true, "vida_down.mp3", habbo));
                        for (Habbo habbos : room.getHabbos()) {
                            try {
                                HabboRp habboRpH = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbos);
                                if (habboRpH != null) {
                                    habboRpH.getHabboClient()
                                            .send(new PlaySound("PlaySound", true, "/shooter/disparo.mp3", habbos));
                                }
                            } catch (Exception e) {
                            }
                        }
                    }

                    if (setVida <= 3) {
                        if (shooterHabboClient.getDeathList().get(habbo.getHabboInfo().getId()) == null) {

                            shooterHabboOther.restarVida(100);
                            habboRpOther.getVidaHambreDormir().matarHabbo();

                            // Enviar mensaje a todos
                            RoleplayWebsocketsRoomsInfo.getConsolaManager().sendMessageConsolaGlobal(
                                    TypeMessageConsola.getMessageUserToUser(habboRpClient.getHabbo(),
                                            habboRpOther.getHabbo(), "asesinó con su arma a "));

                            habboRpClient.getHabbo().talk("*Acabo de mandar al hospital a %username% de un disparo*"
                                    .replace("%username%", habboRpOther.getUsername()));

                            new EventosDelitosTutorial().eventAsesinarShooterPvp(habboRpClient.getHabbo());
                            habboRpClient.setHabboDelitos(habboRpClient.getHabbo());
                            habboRpClient.getDelitosHabbo().incrementDelitos(3);
                            habboRpClient.getDelitosHabbo().sendAlertToPolices(habboRpClient.getHabbo());

                            shooterHabboClient.getDeathList().put(habbo.getHabboInfo().getId(), habbo);
                            getAsesinatos().put(habbo, shooterHabboClient);

                            habboRpClient.getHabboClient()
                                    .send(new ShowInfoOtherHabbo("ShowInfoOtherHabbo", false, null));
                            habboRpClient.getHabboClient().send(
                                    new MessageManager("MessageManager", false, Outcoming.InventoryButtonsOther, null));

                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void equipArmaIlegal(Habbo habbo) {
        try {

            if (!RoleplayWebsocketsRoomsInfo.getHorarioManager().getTimeHorario()
                    .equalsIgnoreCase("noche")) {
                return;
            }

            HabboRp habboRpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            InventoryHabboNew inventoryHabboNew = habboRpClient.getNewInventoryHabbo();

            inventoryHabboNew.desequipAllItemsUsed();

            inventoryHabboNew.addItemUsed("arma_ilegal");
            if (inventoryHabboNew.getItem("municion") != null) {
                if (inventoryHabboNew.getItem("municion").getCantidad() >= 1) {
                    inventoryHabboNew.addItemUsed("municion");
                }
            } else {
                inventoryHabboNew.createItem("municion", 5);
                inventoryHabboNew.addItemUsed("municion");
            }

            inventoryHabboNew.showButtonsUsar(habbo);
        } catch (Exception e) {
        }
    }

    public static void desequipArmaIlegal(Habbo habbo) {
        try {

            if (!RoleplayWebsocketsRoomsInfo.getHorarioManager().getTimeHorario()
                    .equalsIgnoreCase("noche")) {
                return;
            }

            HabboRp habboRpClient = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);
            InventoryHabboNew inventoryHabboNew = habboRpClient.getNewInventoryHabbo();

            inventoryHabboNew.desequipAllItemsUsed();

            inventoryHabboNew.showButtonsUsar(habbo);
        } catch (Exception e) {
        }
    }

    public static THashMap<Habbo, ShooterHabbo> getAsesinatos() {
        return logs_asesinatos;
    }

    public void IncomingListActions(THashMap<String, Object> data, HabboClient wsclient) {

    }

    public static void getReceibedToggleEvent(FurnitureToggleEvent event) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(event.habbo);
            if (habboRp.getShooterHabbo().getPvpActive() == 1) {

                // No permitir el cambio de estado de furnis
                event.setCancelled(true);

            }
        } catch (Exception e) {
        }
    }

}
