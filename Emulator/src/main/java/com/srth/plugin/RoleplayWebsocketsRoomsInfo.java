package com.srth.plugin;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ICallable;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.rooms.RoomRelativeMapComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.srth.plugin.ActionItems.ItemActionManager;
import com.srth.plugin.ActionItems.RoleplayFurniMenuManager;
import com.srth.plugin.Consola.ConsolaManager;
import com.srth.plugin.Crafteos.CraffteoManager;
import com.srth.plugin.Horario.HorarioManager;
import com.srth.plugin.Horario.Timer.TimerCash;
import com.srth.plugin.Horario.Timer.TimerEstatus;
import com.srth.plugin.Retos.CategoriaReto;
import com.srth.plugin.Retos.RetosTutorialManager;
import com.srth.plugin.Retos.TareaCategoria;
import com.srth.plugin.Rooms.Mapa.MapaManager;
import com.srth.plugin.Rooms.Mapa.Taxi.ControllerRecorridosTaxi;
import com.srth.plugin.Rooms.RoomsManager;
import com.srth.plugin.Turnos.TurnosManager;
import com.srth.plugin.Tutorial.TutorialHabboRpManager;
import com.srth.plugin.bots.BotsManager;
import com.srth.plugin.bots.shop.ShopBotsManager;
import com.srth.plugin.events.RegisterHabboEvents;
import com.srth.plugin.events.habboHandlers.HandlerBotSettings;
import com.srth.plugin.events.habboHandlers.HandlerOnClickUser;
import com.srth.plugin.events.habboHandlers.HandlerRedeemItemEvent;
import com.srth.plugin.events.habboHandlers.HandlerRoomSettingsSaved;
import com.srth.plugin.events.habboHandlers.handledFurniClick;
import com.srth.plugin.events.habboHandlers.handlerUserClickTile;
import com.srth.plugin.habbo.CraftItems.CraftManager;
import com.srth.plugin.habbo.CraftItems.ListCrafts.CraftCocineros;
import com.srth.plugin.habbo.Delitos.PoliceTransportManager;
import com.srth.plugin.habbo.HabboRpManager;
import com.srth.plugin.habbo.Inventorys.CofreInventoryManager;
import com.srth.plugin.habbo.Inventorys.Items.ManagerItems;
import com.srth.plugin.habbo.Jobs.JobsManager;
import com.srth.plugin.habbo.Jobs.Levels.LevelManager;
import com.srth.plugin.habbo.Jobs.Tasks.TaskManager;
import com.srth.plugin.habbo.Jobs.WorkSystemLevelManager;
import com.srth.plugin.habbo.Shooter.ShooterManager;
import com.srth.plugin.habbo.Tareas.Level.LevelManagerRP;
import com.srth.plugin.habbo.Tareas.Level.habbo.LevelHabboManager;
import com.srth.plugin.habbo.Tareas.TareasManager;
import com.srth.plugin.habbo.VidaHambreDormir.Timer.TimerDormir;
import com.srth.plugin.habbo.VidaHambreDormir.Timer.TimerHambre;
import com.srth.plugin.habbo.VidaHambreDormir.Timer.TimerHigiene;
import com.srth.plugin.habbo.VidaHambreDormir.Timer.TimerOnline;
import com.srth.plugin.habbo.VidaHambreDormir.Timer.TimerVida;
import com.srth.plugin.interactions.hackercajeros.control.InteractionManagerHackerCajeros;
import com.srth.plugin.interactions.vidayhambre.control.InteractionManagerVidayHambre;
import com.srth.plugin.messages.incoming.RoleplayFurniActionEvent;
import com.srth.plugin.messages.incoming.RoleplayFurniMenuRequestEvent;
import com.srth.plugin.utilities.UtilitesRoleplay;
import com.srth.plugin.utilities.checkDatabase;
import com.srth.plugin.utilities.checkRPSettings;
import com.srth.plugin.utilities.checkRPTexts;
import com.srth.plugin.utilities.checkRPUsers;
import com.srth.plugin.utilities.loadConfig;
import com.srth.plugin.utilities.loadPlayerCommands;
import com.srth.plugin.utilities.loadTexts;
import com.srth.plugin.utilities.utils.comprobateUsePlugin;
import com.srth.plugin.websocket.Habbo.SSOClientManager;
import com.srth.plugin.websocket.WebSocketServer;
import com.srth.plugin.websocket.communication.incoming.IncomingManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleplayWebsocketsRoomsInfo extends HabboPlugin implements EventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Emulator.class);

    // HABBORP MANAGER
    private static HabboRpManager habboRpManager;

    public static RoleplayWebsocketsRoomsInfo INSTANCE = null;
    private static checkRPSettings config;
    private static checkRPTexts text;
    private static checkRPUsers data;
    private static UtilitesRoleplay checkutilities;
    public TimerHambre TimerHambre;
    public TimerVida TimerVida;
    public TimerDormir TimerDormir;
    public TimerOnline TimerOnline;
    public TimerHigiene TimerHigiene;
    public static TimerEstatus TimerEstatus;
    public TimerCash TimerCash;

    private static HorarioManager horarioManager;

    private static ManagerItems managerItems;
    private static JobsManager jobsManager;

    // Level global RP
    private static LevelManagerRP levelManagerRP;
    private static LevelHabboManager levelHabboManager;
    private static TaskManager taskManagerWork;
    private static LevelManager levelManagerWork;
    private static ItemActionManager itemActionManager;
    private static RoleplayFurniMenuManager roleplayFurniMenuManager;
    private static CraftManager craftManager;

    // NUEVO CRAFTEO MANAGER
    private static CraffteoManager craffteoManager;

    private static RoomsManager roomsRoleplayManager;

    private static MapaManager mapaManager;

    // Nuevas tareas
    private static TareasManager tareasManager;

    // UPDATES
    private static TutorialHabboRpManager tutorialHabboRpManager;
    private static ControllerRecorridosTaxi controllerRecorridosTaxi;
    private static RetosTutorialManager retosTutorialManager;

    private static CofreInventoryManager cofreInventoryManager;
    private static TurnosManager turnosManager;
    private static ConsolaManager consolaManager;
    private static ShopBotsManager shopBotsManager;

    public void onEnable() throws Exception {

        Emulator.getPluginManager().registerEvents(this, this);

        LOGGER.info("Detected Plugin `{}` by {} version {}!", this.getClass().getCanonicalName(), "SrThrone", "1.0.0");

        INSTANCE = this;
        roleplayFurniMenuManager = new RoleplayFurniMenuManager();
        Emulator.getGameServer()
                .getPacketManager()
                .registerHandler(Incoming.RoleplayFurniMenuRequestEvent, RoleplayFurniMenuRequestEvent.class);
        Emulator.getGameServer()
                .getPacketManager()
                .registerHandler(Incoming.RoleplayFurniActionEvent, RoleplayFurniActionEvent.class);

        RegisterHabboEvents registerUtils = new RegisterHabboEvents();
        registerUtils.Init();

        config = new checkRPSettings();
        config.loadFromDatabase();
        text = new checkRPTexts();
        text.loadFromDatabase();

        checkutilities = new UtilitesRoleplay();

        tutorialHabboRpManager = new TutorialHabboRpManager();
        tutorialHabboRpManager.loadPartesTutorial();

        String credits_pg = utilities()
                .encode(RoleplayWebsocketsRoomsInfo.getConfig().getValue("roleplay_websocket_plugin_credits"));
        String credits_em = utilities().encode(Emulator.getConfig().getValue("roleplay_websocket_plugin_credits"));

        if (!RoleplayWebsocketsRoomsInfo.getConfig().getBoolean("roleplay_websocket_active")
                || !credits_pg.equalsIgnoreCase("U3JUaHJvbmU=")
                || !credits_em.equalsIgnoreCase("U3JUaHJvbmU=")) {
            LOGGER.error("[ROLEPLAY WEBSOCKETS ROOM INFO] plugin its illegally, stopping emulator!");
            LOGGER.error("[ROLEPLAY WEBSOCKETS ROOM INFO] plugin its illegally, stopping emulator!");
            LOGGER.error("[ROLEPLAY WEBSOCKETS ROOM INFO] plugin its illegally, stopping emulator!");
            Emulator.prepareShutdown();
            Emulator.isShuttingDown = true;
            Emulator.isReady = false;
        }

        this.loadItemsManager();
    }

    public void onRegisterConfigs() {
        try {
            // Cargar y registrar los permisos
            checkDatabase.ICheckDatabase();

            // Cargar los comandos
            loadPlayerCommands.ILoadPlayerCommands();

            // Cargar y registrar las configuraciones
            loadConfig.ILoadConfig();

            // Cargar y registrar los textos
            loadTexts.ILoadTexts();

        } catch (Exception err) {
        }
    }

    @EventHandler
    public void onEmulatorLoadedEvent(EmulatorLoadedEvent e) throws Exception {

        this.onRegisterConfigs();

        Emulator.getConfig().register("rp_rooms_info.ws.host", "0.0.0.0");
        Emulator.getConfig().register("rp_rooms_info.ws.port", "2097");

        LOGGER.info("[ROLEPLAY WEBSOCKETS ROOM INFO] | Procesando...");

        Emulator.getGameEnvironment().getItemManager().getItemInteraction("wf_act_give_score_pp");

        // Comprobar permisos del plugin
        comprobateUsePlugin comprobateUse = new comprobateUsePlugin();
        comprobateUse.loadHotelesPermited();
        comprobateUsePlugin.comprobateLoad();

        WebSocketServer webSocketServer = new WebSocketServer();
        webSocketServer.initializePipeline();
        webSocketServer.connect();
        IncomingManager.getInstance().registerMessages();

        // BOTS
        new BotsManager();

        // INICIAR EL HABBO RP MANAGER
        habboRpManager = new HabboRpManager();
        habboRpManager.update();

        // INVENTORY MANAGER
        cofreInventoryManager = new CofreInventoryManager();

        turnosManager = new TurnosManager();
        consolaManager = new ConsolaManager();

        horarioManager = new HorarioManager();

        TimerHambre = new TimerHambre();
        Emulator.getThreading().run(this.TimerHambre);
        TimerVida = new TimerVida();
        Emulator.getThreading().run(this.TimerVida);
        TimerDormir = new TimerDormir();
        Emulator.getThreading().run(this.TimerDormir);
        TimerOnline = new TimerOnline();
        Emulator.getThreading().run(this.TimerOnline);
        TimerCash = new TimerCash();
        Emulator.getThreading().run(this.TimerCash);
        TimerHigiene = new TimerHigiene();
        Emulator.getThreading().run(this.TimerHigiene);

        // Item action manager
        itemActionManager = new ItemActionManager();
        itemActionManager.loadItemsDobleClick();

        // Manage rusers
        new WorkSystemLevelManager();

        // Updatear estados de los furnis
        this.updateItemsState("1", "rp_urban_basura");
        this.updateItemsState("0", "atm_cajero_byth_rp");
        this.updateItemsState("1", "rp_cofre_shooter");
        this.updateItemsState("0", "rp_arbol_talar_byth");

        // UPDATEAR INTERACCIONES DE LOS ITEMS
        this.updateItemBaseInteraction("rp_urban_basura", "rp_urban_basura");
        this.updateItemBaseInteraction("atm_cajero_byth_rp", "hacker_cajero_rp");
        this.updateItemBaseInteraction("rp_cofre_shooter", "shooter_cofre_rp");
        this.updateItemBaseInteraction("boutique_changing2", "ropero_rp");
        this.updateItemBaseInteraction("boutique_changing3", "ropero_rp");
        this.updateItemBaseInteraction("bathroom_bath1", "bano_rp");
        this.updateItemBaseInteraction("scanner_cafeteros", "rp_manager_job_cafetero");
        // UPDATEAR CAMA "hosptl_bed"
        this.updateItemBaseInteraction("hosptl_bed", "camilla_hospital_rp");

        SSOClientManager.clearClients();

        // Registro de handlers (eventos)
        ICallable onClickUser = new HandlerOnClickUser();
        ICallable RoomSettingsSaveEvent = new HandlerRoomSettingsSaved();
        // ICallable RequestRoomLoadEvent = new HandlerRequestRoomLoadEvent();
        ICallable HandlerRedeemItemEvent = new HandlerRedeemItemEvent();
        ICallable HandlerBotSettingsEvent = new HandlerBotSettings();

        Emulator.getGameServer().getPacketManager().registerCallable(2138, onClickUser);
        Emulator.getGameServer().getPacketManager().registerCallable(1969, RoomSettingsSaveEvent);
        // Emulator.getGameServer().getPacketManager().registerCallable(2312,
        // RequestRoomLoadEvent);
        Emulator.getGameServer().getPacketManager().registerCallable(3115, HandlerRedeemItemEvent);
        Emulator.getGameServer().getPacketManager().registerCallable(1986, HandlerBotSettingsEvent);

        ICallable onClickTile = new handlerUserClickTile();
        Emulator.getGameServer().getPacketManager().registerCallable(3320, onClickTile);

        ICallable onClickFurni = new handledFurniClick();
        Emulator.getGameServer().getPacketManager().registerCallable(3288, onClickFurni);

        // ITEMS MANAGERS INVENTARIO
        managerItems = new ManagerItems();
        managerItems.updateItems();
        // Mostrar información inventario
        LOGGER.info("[ROLEPLAY WEBSOCKETS ROOM INFO] | Items RP cargados: " + managerItems.getCantidadItems());

        // Cargar works
        jobsManager = new JobsManager();
        jobsManager.update();

        // Tareas para los trabajos
        taskManagerWork = new TaskManager();
        levelManagerWork = new LevelManager();
        taskManagerWork.updateTasks();
        levelManagerWork.updateLevels();

        // LEVEL GLOBAL
        levelManagerRP = new LevelManagerRP();
        levelManagerRP.updateLevels();

        levelHabboManager = new LevelHabboManager();
        levelHabboManager.update();

        // Mapa Manager
        mapaManager = new MapaManager();
        mapaManager.loadAll();
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER("[ROLEPLAY WEBSOCKETS ROOM INFO] | Salas RP Oficiales: "
                        + mapaManager.getListRooms().size() + "");

        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER("[ROLEPLAY WEBSOCKETS ROOM INFO] | Levels cargados: "
                        + levelManagerRP.getArrayLevels().size());

        // CraftManager (ELIMINAR PRONTO)
        craftManager = new CraftManager();
        craftManager.update();
        new CraftCocineros().registerCraft(craftManager);

        // NUEVO CRAFTEO MANAGER
        craffteoManager = new CraffteoManager();

        // Rooms roleplay
        roomsRoleplayManager = new RoomsManager();
        roomsRoleplayManager.update();

        tareasManager = new TareasManager();
        tareasManager.update();

        controllerRecorridosTaxi = new ControllerRecorridosTaxi();

        retosTutorialManager = new RetosTutorialManager();
        retosTutorialManager.load();

        int numberRetos = 0;
        for (CategoriaReto categoria : retosTutorialManager.getCategorias().values()) {
            for (TareaCategoria iterable_element : categoria.getRetos().values()) {
                numberRetos++;
            }
        }
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER("[ROLEPLAY WEBSOCKETS ROOM INFO] | CATEGORIAS DE RETOS CARGADOS: "
                        + retosTutorialManager.getCategorias().size());
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER("[ROLEPLAY WEBSOCKETS ROOM INFO] | RETOS TOTALES: " + numberRetos);

        shopBotsManager = new ShopBotsManager();
        shopBotsManager.loadShops();

        // UPDATEAR EN EMU
        Emulator.getGameEnvironment().getItemManager().loadItems();
        synchronized (Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
            for (Room room : Emulator.getGameEnvironment().getRoomManager().getActiveRooms()) {
                if (room.isLoaded() && room.getUserCount() > 0 && room.getLayout() != null) {
                    room.sendComposer(new RoomRelativeMapComposer(room).compose());
                }
            }
        }

        // String ANSI_GREEN = "\u001B[32m";
        String ANSI_GREEN = "";
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "----------------------------------------------------------------------");
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "[ROLEPLAY WEBSOCKETS ROOM INFO] | INICIALIZADO CORRECTAMENTE");
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "----------------------------------------------------------------------");
    }

    @Override
    public void onDisable() throws Exception {
        Emulator.getGameServer().getPacketManager().unregisterCallables(2138);
        Emulator.getGameServer().getPacketManager().unregisterCallables(99);
        Emulator.getGameServer().getPacketManager().unregisterCallables(210);
        Emulator.getGameServer().getPacketManager().unregisterCallables(2312);
    }

    @EventHandler
    public void onStepEvent(UserTakeStepEvent e) {
        getRoomsRoleplayManager().eventUserMove(e.habbo);
        ShooterManager.onUserMove(e);
        if (!e.isCancelled()) {
            PoliceTransportManager.onPoliceStep(e);
        }
    }

    public void loadItemsManager() {

        // Registrar interacciones
        new InteractionManagerVidayHambre().load();
        new InteractionManagerHackerCajeros().load();

        // String ANSI_GREEN = "\u001B[32m";
        String ANSI_GREEN = "";
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "----------------------------------------------------------------------");
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "[ROLEPLAY WEBSOCKETS ROOM INFO] | CARGADAS LAS INTERACCIONES ROLEPLAY");
        RoleplayWebsocketsRoomsInfo.utilities()
                .LOGGER(ANSI_GREEN + "----------------------------------------------------------------------");
    }

    public static boolean UpdateAllRoomsContentItem(String base_name, String extra_data, double z) {
        Item item = Emulator.getGameEnvironment().getItemManager().getItem(base_name);
        if (item == null) {
            return false;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM items WHERE item_id= ? AND room_id!= ?")) {
            statement.setInt(1, item.getId());
            statement.setInt(2, 0);
            if (statement.execute()) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {

                        Room room =
                                Emulator.getGameEnvironment().getRoomManager().loadRoom(set.getInt("room_id"));
                        if (room != null) {

                            if (room.getCategory()
                                    != RoleplayWebsocketsRoomsInfo.getConfig()
                                            .getInt("global_room.category.roleplay.id")) {
                                continue;
                            }

                            try (Connection conn2 =
                                    Emulator.getDatabase().getDataSource().getConnection()) {
                                try (PreparedStatement statement2 =
                                        conn2.prepareStatement("UPDATE items SET extra_data= ?, z= ? WHERE id = ?")) {
                                    statement2.setString(1, extra_data);
                                    statement2.setString(2, z + "");
                                    statement2.setInt(3, set.getInt("id"));
                                    statement2.executeUpdate();
                                }

                            } catch (SQLException exception) {
                                exception.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
        }

        return false;
    }

    public void updateItemBaseInteraction(String name_item, String interaction_type) {
        Item el_item = Emulator.getGameEnvironment().getItemManager().getItem(name_item);
        if (el_item != null) {
            int id_furni = el_item.getId();
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement statement =
                        conn.prepareStatement("UPDATE items_base SET interaction_type= ? WHERE id = ?")) {
                    statement.setString(1, interaction_type);
                    statement.setInt(2, id_furni);
                    statement.executeUpdate();
                    statement.close();
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public void updateItemsState(String extra_data, String name_item) {
        Item el_item = Emulator.getGameEnvironment().getItemManager().getItem(name_item);
        if (el_item != null) {
            int id_furni = el_item.getId();
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement statement =
                        conn.prepareStatement("UPDATE items SET extra_data= ? WHERE item_id = ?")) {
                    statement.setString(1, extra_data);
                    statement.setInt(2, id_furni);
                    statement.executeUpdate();
                    statement.close();
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
        }
    }

    public static TareasManager getTareasManager() {
        return tareasManager;
    }

    public static HabboRpManager getHabboRpManager() {
        return habboRpManager;
    }

    public boolean hasPermission(Habbo habbo, String s) {
        return false;
    }

    public static UtilitesRoleplay utilities() {
        return checkutilities;
    }

    public static checkRPSettings getConfig() {
        return config;
    }

    public static checkRPTexts getTexts() {
        return text;
    }

    public static checkRPUsers getConfigUser(int user_id) {
        RoleplayWebsocketsRoomsInfo.data = new checkRPUsers(user_id);
        return data;
    }

    public static ItemActionManager getItemActionManager() {
        return itemActionManager;
    }

    public static RoleplayFurniMenuManager getRoleplayFurniMenuManager() {
        return roleplayFurniMenuManager;
    }

    public static ManagerItems getItemsManager() {
        return managerItems;
    }

    public static JobsManager getJobsManager() {
        return jobsManager;
    }

    public static LevelManagerRP getLevelManagerRP() {
        return levelManagerRP;
    }

    public static LevelHabboManager getLevelHabboManager() {
        return levelHabboManager;
    }

    public static CraftManager getCraftManager() {
        return craftManager;
    }

    public static RoomsManager getRoomsRoleplayManager() {
        return roomsRoleplayManager;
    }

    public static TimerEstatus getTimerEstatus() {
        return TimerEstatus;
    }

    public static TutorialHabboRpManager getTutorialHabboRpManager() {
        return tutorialHabboRpManager;
    }

    public static MapaManager getMapaManager() {
        return mapaManager;
    }

    public static ControllerRecorridosTaxi getControllerRecorridosTaxi() {
        return controllerRecorridosTaxi;
    }

    public static RetosTutorialManager getRetosTutorialManager() {
        return retosTutorialManager;
    }

    public static CofreInventoryManager getCofreInventoryManager() {
        return cofreInventoryManager;
    }

    public static TurnosManager getTurnosManager() {
        return turnosManager;
    }

    public static ConsolaManager getConsolaManager() {
        return consolaManager;
    }

    public static CraffteoManager getCraffteoManager() {
        return craffteoManager;
    }

    public static ShopBotsManager getShopBotsManager() {
        return shopBotsManager;
    }

    public static HorarioManager getHorarioManager() {
        return horarioManager;
    }
}
