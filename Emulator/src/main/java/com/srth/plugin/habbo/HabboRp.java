package com.srth.plugin.habbo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserDataComposer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.Retos.HabboRetos;
import com.srth.plugin.Tutorial.HabboTutorial;
import com.srth.plugin.habbo.Delitos.HabboDelitos;
import com.srth.plugin.habbo.Inventorys.InventoryHabboNew;
import com.srth.plugin.habbo.Jobs.Job;
import com.srth.plugin.habbo.Shooter.ShooterHabbo;
import com.srth.plugin.habbo.Tareas.Habbo.HabboTareas;
import com.srth.plugin.habbo.Tareas.Level.habbo.LevelHabbo;
import com.srth.plugin.habbo.VidaHambreDormir.VidaHambreDormir;
import com.srth.plugin.websocket.Habbo.HabboClient;
import com.srth.plugin.websocket.Habbo.HabboClientManager;
import com.srth.plugin.websocket.Habbo.SSOClientManager;
import com.srth.plugin.websocket.communication.Outcoming;
import com.srth.plugin.websocket.communication.outgoing.messages.MessageManager;

import gnu.trove.map.hash.THashMap;

public class HabboRp {
    private static final Logger LOGGER = LoggerFactory.getLogger(HabboRp.class);

    private int id;
    public Properties data;
    public boolean loaded = false;
    private boolean isLoading = false;
    private HabboClient client;

    // Información basica del habboRP
    private Integer habboId;
    private String username;
    private LevelHabbo levelHabbo;

    private Job jobHabbo;
    private Habbo habbo;
    private VidaHambreDormir vidaHambreDormir;
    private InventoryHabboNew newInventoryHabbo;
    private int timerOnline;
    private int action_progress;
    private HabboDelitos habboDelitos;
    private HabboTareas habboTareas;

    private ShooterHabbo shooterHabbo;
    private HabboRetos habboRetos;

    private int enable = 0;
    private int handitem = 0;
    private int dinero;

    private THashMap<Integer, Integer> useTeleportTiles;

    private int VIP;
    private int vip_time;

    // UPDATES
    private HabboTutorial habboTutorial;

    // NEW
    private boolean constructorSalas = false;

    public void update() {
        data.clear();
        this.loadFromDatabase(this.habboId);
    }

    public HabboRp(Integer habbo_id) {

        this.id = habbo_id;

        this.data = new Properties();
        this.loadFromDatabase(habbo_id);

        this.habboId = habbo_id;
        this.username = getHabboInfo(habbo_id).getUsername();

        this.timerOnline = this.checkSettings("global.online.time", 0);
        this.dinero = this.checkSettings("dinero_deposited", 0);

        this.constructorSalas = this.checkSettings("constructorSalas", "0").equals("0") ? false : true;

        this.checkSettings("volumen_sound", "0.8");
        this.checkSettings("ignore_higiene", "0");

        this.useTeleportTiles = new THashMap<Integer, Integer>();

        this.VIP = this.checkSettings("vip", 0);
        this.vip_time = this.checkSettings("vip_time", 0);

        try {
            if (this.habbo != null) {
                // Establecer empleo
                String name_ultimo_job = this.getValue("ultimo_job");
                Job jobActivate = RoleplayWebsocketsRoomsInfo.getJobsManager().getJob(name_ultimo_job);
                if (jobActivate != null) {
                    if (jobActivate.getEmpleado(this.habbo) == null) {
                        jobActivate.setEmpleado(this.habbo);
                        jobActivate.getEmpleado(this.habbo)
                                .changeLookEmpleado(
                                        this.getHabbo().getHabboInfo().getGender().name());
                        this.setJobActive(jobActivate);

                        RoleplayWebsocketsRoomsInfo.getJobsManager().showMenuJob(this.habbo);
                    }
                }
            }
        } catch (Exception e) {
        }

    }

    public THashMap<Integer, Integer> getUseTeleportTiles() {
        return useTeleportTiles;
    }

    public int getId() {
        return id;
    }

    public boolean getConstructorSalas() {
        return this.constructorSalas;
    }

    public void setConstructorSalas(boolean value) {
        int valor = value == true ? 1 : 0;
        this.constructorSalas = value;
        this.updateData("constructorSalas", valor + "");
    }

    public Boolean getVip() {
        if (VIP == 1) {
            return true;
        } else {
            return false;
        }
    }

    public int getVipTime() {
        return vip_time;
    }

    public void setHabboRetos(Habbo habbo) {
        this.habboRetos = new HabboRetos(habbo);
    }

    public HabboRetos getHabboRetos() {
        return habboRetos;
    }

    public HabboTutorial getHabboTutorial() {
        return this.habboTutorial;
    }

    public void setHabboTutorial(Habbo habbo) {
        this.habboTutorial = new HabboTutorial(habbo);
    }

    public void setHabbo(Habbo habbo) {
        this.habbo = habbo;
        this.enable = 0;
        this.handitem = 0;
    }

    public void setHabboDelitos(Habbo habbo) {
        this.habboDelitos = new HabboDelitos(habbo);
    }

    public HabboDelitos getDelitosHabbo() {
        return this.habboDelitos;
    }

    public InventoryHabboNew getNewInventoryHabbo() {
        return this.newInventoryHabbo;
    }

    public void setNewInventoryHabbo(Habbo habbo) {
        this.newInventoryHabbo = new InventoryHabboNew(habbo);
    }

    public void setVidaHambreDormir(Habbo habbo) throws SQLException {
        this.habbo = habbo;
        this.vidaHambreDormir = new VidaHambreDormir(habbo);
    }

    public void setJobActive(Job job) {
        if (job != null) {
            this.checkSettings("ultimo_job_active", job.getName());
            this.updateData("ultimo_job_active", job.getName() + "");
        } else {
            this.checkSettings("ultimo_job_active", "");
            this.updateData("ultimo_job_active", "");
        }
        this.jobHabbo = job;
    }

    public void setShooterHabbo(Habbo habbo) {
        this.shooterHabbo = new ShooterHabbo(habbo);
    }

    public ShooterHabbo getShooterHabbo() {
        return this.shooterHabbo;
    }

    public VidaHambreDormir getVidaHambreDormir() {
        return this.vidaHambreDormir;
    }

    public void setHabboTareas(Habbo habbo) {
        this.habboTareas = new HabboTareas(habbo);
    }

    public HabboTareas getTareas() {
        return this.habboTareas;
    }

    public Habbo getHabbo() {
        return this.habbo;
    }

    public Job getJobActive() {
        return jobHabbo;
    }

    public void allowMove(Boolean value) {
        // Congelar al keko
        this.habbo.getRoomUnit().setCanWalk(value);
    }

    public void showMessage(String imagen, String title) {

        // EJEMPLO RUTA IMAGEN = /RoleplayWebsocketsRoomsInfo/tutorial/vida.png
        // RoleplayWebsocketsRoomsInfo.getTexts().getValue("pescar.cana.break").replace("%TYPE%",
        // "intermedia")
        try {
            String imagen_convert;
            if (imagen == "") {
                imagen_convert = imagen;
            } else {
                String url_hotel_base = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global.url.hotel.base");
                imagen_convert = RoleplayWebsocketsRoomsInfo.getConfig().getValue("global_url.hotel.notifications")
                        .replace("%url.hotel.base%", url_hotel_base)
                        + imagen;
            }

            JsonArray Array = new JsonArray();
            JsonObject jsonObject;
            jsonObject = new JsonObject();
            jsonObject.addProperty("img", imagen_convert);
            jsonObject.addProperty("title", title);
            Array.add(jsonObject);
            this.getHabboClient().send(new MessageManager("MessageManager", true, Outcoming.MessagePopView, Array));

        } catch (Exception e) {
        }
    }

    public void ocultMessage() throws SQLException {
        JsonArray Array = new JsonArray();
        JsonObject jsonObject;
        jsonObject = new JsonObject();
        jsonObject.addProperty("img", "");
        jsonObject.addProperty("title", "");
        Array.add(jsonObject);
        this.getHabboClient().send(new MessageManager("MessageManager", false, Outcoming.MessagePopView, Array));
    }

    public HabboClient getHabboClient() {
        this.client = HabboClientManager.getHabboClient(habbo);
        if (this.client == null) {
            if (habbo != null) {
                this.client = SSOClientManager.bindToSession(habbo);
                if (this.client != null) {
                    RoleplayWebsocketsRoomsInfo.utilities().LOGGER("HabboClient null, se ha creado uno nuevo para el habbo: "
                            + habbo.getHabboInfo().getUsername());
                }
            }
        }
        return this.client;
    }

    public HabboInfo getHabboInfo(Integer habbo_id) {
        return HabboManager.getOfflineHabboInfo(habbo_id);
    }

    public Integer getHabboId() {
        return habboId;
    }

    public String getUsername() {
        return username;
    }

    public LevelHabbo getLevelHabbo() {
        try {

            RoleplayWebsocketsRoomsInfo.getLevelHabboManager().setHabboLevel(this.getHabboId());
            levelHabbo = RoleplayWebsocketsRoomsInfo.getLevelHabboManager().getHabbo(this.getHabboId());
            return levelHabbo;

        } catch (Exception e) {
        }
        return null;
    }

    public void givePoints(int cantidad) {
        habbo.givePoints(
                RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"),
                cantidad);

        // Mostrar alerta arriba
        // THashMap<String, String> keys = new THashMap<>();
        // keys.put("display", "BUBBLE");
        // keys.put("image",
        // "${image.library.url}notifications/RoleplayWebsocketsRoomsInfo/cash/0.png");
        // keys.put("message",
        // RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_give_points_cash")
        // .replace("%AMOUNT%", cantidad + "")
        // .replace("%TYPE%",
        // RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));
        // habbo.getClient().sendResponse(new
        // BubbleAlertComposer(BubbleAlertKeys.RECEIVED_BADGE.key, keys));

        // Mostrar alerta abajo
        this.showMessage("/RoleplayWebsocketsRoomsInfo/cash/0.png",
                RoleplayWebsocketsRoomsInfo.getTexts().getValue("action_give_points_cash")
                        .replace("%AMOUNT%", cantidad + "")
                        .replace("%TYPE%", RoleplayWebsocketsRoomsInfo.getTexts().getValue("seasonal.name.rol")));
    }

    public void changeLookUltimo(Habbo habbo) {
        try {
            HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager().getHabboRp(habbo);

            if (habboRp == null) {
                return;
            }

            String ultimoLookSave = habboRp.getValue("global_ultimo_look_save");
            habbo.getHabboInfo().setLook(ultimoLookSave);

            if (habbo.getHabboInfo().getCurrentRoom() != null) {
                habboRp.getHabboClient().getHabbo().getHabboInfo().getCurrentRoom()
                        .sendComposer(new RoomUserDataComposer(habbo).compose());
                habbo.getClient().sendResponse(new UserDataComposer(habbo));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadFromDatabase(Integer habbo_id) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT * FROM rp_users_settings WHERE user_id= ?")) {
            statement.setInt(1, habbo_id);
            if (statement.execute()) {
                try (ResultSet set = statement.getResultSet()) {
                    while (set.next()) {
                        this.data.put(set.getString("key"), set.getString("value"));
                    }
                }
                this.loaded = true;
            }
        } catch (SQLException e) {
        }
    }

    public void saveToDatabase(String value, String key) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rp_users_settings SET `value` = ? WHERE `key` = ? AND `user_id`= ? LIMIT 1")) {
            statement.setString(1, value);
            statement.setString(2, key);
            statement.setInt(3, this.getHabboId());
            statement.executeUpdate();
            this.update(key, value);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void updateData(String key, String value) {
        this.saveToDatabase(value, key);
        this.update(key, value);
    }

    public String checkSettings(String key, String default_value) {
        String value = default_value;
        // Comprueba si no existe y lo registra
        if (this.getValue(key) == "none") {
            this.register(this.getHabboId(), key, value);
            return value;
        } else {
            return value = this.getValue(key);
        }
    }

    public int checkSettings(String key, int default_value) {
        int value = default_value;
        // Comprueba si no existe y lo registra
        if (this.getValue(key) == "none") {
            this.register(this.getHabboId(), key, value + "");
            return value;
        } else {
            return value = this.getInt(key);
        }
    }

    public String getValue(String key) {
        return this.getValue(key, "none");
    }

    public String getValue(String key, String defaultValue) {
        if (this.isLoading)
            return defaultValue;

        if (!this.data.containsKey(key)) {
            return "none";
        }
        return this.data.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key) {
        return this.getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (this.isLoading)
            return defaultValue;
        try {
            return (this.getValue(key, "0").equals("1")) || (this.getValue(key, "false").equals("true"));
        } catch (Exception e) {
            LOGGER.error("Failed to parse key {} with value '{}' to type boolean.", key, this.getValue(key));
        }
        return defaultValue;
    }

    public int getInt(String key) {
        return this.getInt(key, 0);
    }

    public int getInt(String key, Integer defaultValue) {
        if (this.isLoading)
            return defaultValue;

        try {
            return Integer.parseInt(this.getValue(key, defaultValue.toString()));
        } catch (Exception e) {
            LOGGER.error("Failed to parse key {} with value '{}' to type integer.", key, this.getValue(key));
        }
        return defaultValue;
    }

    public double getDouble(String key) {
        return this.getDouble(key, 0.0);
    }

    public double getDouble(String key, Double defaultValue) {
        if (this.isLoading)
            return defaultValue;

        try {
            return Double.parseDouble(this.getValue(key, defaultValue.toString()));
        } catch (Exception e) {
            LOGGER.error("Failed to parse key {} with value '{}' to type double.", key, this.getValue(key));
        }

        return defaultValue;
    }

    public void update(String key, String value) {
        this.data.setProperty(key, value);
    }

    public void register(int habbo, String key, String value) {
        if (this.data.getProperty(key, null) != null)
            return;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("INSERT INTO rp_users_settings VALUES (?, ?, ?)")) {
            statement.setInt(1, habbo);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        this.update(key, value);
    }

    public void remove(String key) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("DELETE FROM rp_users_settings WHERE `user_id` = ? AND `key` = ?")) {
            statement.setInt(1, this.habboId);
            statement.setString(2, key);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        data.clear();
        this.loadFromDatabase(this.habboId);
    }

    public boolean getProgressPendiente() {
        Boolean value;
        if (this.checkSettings("global_progress_pendiente", 0) == 1) {
            value = true;
        } else {
            value = false;
        }
        return value;
    }

    public int setProgressPendiente(Boolean value) {
        boolean wasPending = this.getProgressPendiente();
        int valor;
        if (value == true) {
            valor = 1;
        } else {
            valor = 0;
        }
        this.action_progress = valor;
        this.update("global_progress_pendiente", valor + "");
        this.saveToDatabase(valor + "", "global_progress_pendiente");

        Room room = this.habbo == null ? null : this.habbo.getHabboInfo().getCurrentRoom();
        boolean inRoleplayRoom = room != null && room.getCategory() == RoleplayWebsocketsRoomsInfo.getConfig()
                .getInt("global_room.category.roleplay.id");
        boolean tutorialCompleted = this.habboTutorial != null && this.habboTutorial.getCompleted();

        if (value && !wasPending && inRoleplayRoom && tutorialCompleted && this.vidaHambreDormir != null
                && !this.vidaHambreDormir.getDormirActive()
                && !this.vidaHambreDormir.camillaActive) {
            int cost = Math.max(0,
                    RoleplayWebsocketsRoomsInfo.getConfig().getInt("rp.rooms.energia.action.cost"));
            if (cost > 0) {
                try {
                    this.vidaHambreDormir.restarDormir(cost);
                } catch (SQLException exception) {
                    LOGGER.error("Failed to apply roleplay energy cost for {}", this.username, exception);
                }
            }
        } else if (!value && wasPending && inRoleplayRoom && this.vidaHambreDormir != null) {
            try {
                this.vidaHambreDormir.comprobateDormir();
            } catch (SQLException exception) {
                LOGGER.error("Failed to check roleplay energy after an action for {}", this.username, exception);
            }
        }

        return this.action_progress;
    }

    public void setCuracionPendiente(Boolean value) {
        int estado = 0;
        if (value == true) {
            estado = 1;
        }
        this.updateData("global_curacion_pendiente", estado + "");
    }

    public boolean getCuracionPendiente() {
        Boolean estado = false;
        if (this.checkSettings("global_curacion_pendiente", 0) != 0) {
            estado = true;
        }
        return estado;
    }

    public int getTimeOnline() {
        return this.timerOnline;
    }

    public void setTimerOnline(int number) {
        int operacion = this.getTimeOnline() + number;
        this.timerOnline = operacion;
        this.update("global.online.time", operacion + "");
        this.saveToDatabase(operacion + "", "global.online.time");
    }

    public void setDineroDeposited(int value) {
        this.updateData("dinero_deposited", value + "");
        this.dinero = value;
    }

    public int getDineroDeposited() {
        return dinero;
    }

    public int getDineroMonederoAndInventory() {
        int cuenta = this.habbo.getHabboInfo()
                .getCurrencyAmount(RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"));
        int operacion = cuenta + this.getCashInventario();

        return operacion;
    }

    public int getDineroMonedero() {
        return this.habbo.getHabboInfo()
                .getCurrencyAmount(RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type"));
    }

    public int getCashInventario() {

        Map<Integer, Integer> points = new HashMap<>();
        int cuenta_cash_inventario = 0;

        for (HabboItem item : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (item.getBaseItem().getName().startsWith("CASH_")) {
                int pointsType = RoleplayWebsocketsRoomsInfo.getConfig().getInt("seasonal.rol.primary.type");
                int pointsAmount = Integer.valueOf(item.getBaseItem().getName().split("_")[1]);
                points.put(pointsType, points.getOrDefault(pointsType, 0) + pointsAmount);
            }
        }

        for (int cantidad : points.values()) {
            cuenta_cash_inventario += cantidad;
        }

        return cuenta_cash_inventario;
    }

    public void setHanditem(int number) {
        this.handitem = number;
    }

    public void setEnable(int number) {
        this.enable = number;
    }

    public int getEnable() {
        return enable;
    }

    public int getHanditem() {
        return handitem;
    }

    public void registerCache(String key, String value) {
        habbo.getHabboStats().cache.put(key, value);
    }

    public void removeCache(String key) {
        if (habbo.getHabboStats().cache.containsKey(key)) {
            habbo.getHabboStats().cache.put(key, "none");
        }
    }

    public String getCacheString(String key) {
        if (habbo.getHabboStats().cache.containsKey(key)) {
            return (String) habbo.getHabboStats().cache.get(key);
        } else {
            return "none";
        }
    }

    public int getCacheInt(String key) {
        int defaultValue = 0;

        if (habbo.getHabboStats().cache.containsKey(key)) {
            try {
                return Integer.parseInt(habbo.getHabboStats().cache.get(key).toString());
            } catch (Exception e) {
                LOGGER.error("Failed to parse key {} with value '{}' to type integer.", key, this.getValue(key));
            }
        }
        return defaultValue;
    }

    public String getValueOffline(String key) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement(
                                "SELECT * FROM rp_users_settings WHERE `user_id` = ? AND `key` = ? LIMIT 1")) {
            statement.setInt(1, this.getHabboId());
            statement.setString(2, key);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return set.getString("value");
                }
            }
        } catch (SQLException e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
        } catch (Exception e) {
            RoleplayWebsocketsRoomsInfo.utilities().LOGGER(e + "");
        }
        return "none";
    }
}
