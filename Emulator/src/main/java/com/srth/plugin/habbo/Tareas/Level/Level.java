package com.srth.plugin.habbo.Tareas.Level;

import java.sql.SQLException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import gnu.trove.map.hash.THashMap;

public class Level {

    private final int level; // Hacer final ya que no cambia
    private final int XP; // Hacer final ya que no cambia
    private final THashMap<Integer, Reward> rewards = new THashMap<>();
    private final THashMap<Integer, Reward> rewardsVip = new THashMap<>();

    // Instanciar el parser una sola vez puede ser ligeramente más eficiente
    private static final JsonParser parser = new JsonParser();

    public Level(int level_id) throws SQLException {
        this.level = level_id;
        // Se asume que getInt/getValue puede lanzar SQLException debido a la
        // dependencia
        this.XP = RoleplayWebsocketsRoomsInfo.getConfig().getInt("level_" + level_id + ".xp_required");

        // 1. Llamada al método factorizado para recompensas normales
        String normalRewardsJson = RoleplayWebsocketsRoomsInfo.getConfig()
                .getValue("level_" + level_id + ".premios_json_array");
        parseRewards(normalRewardsJson, this.rewards);

        // 2. Llamada al método factorizado para recompensas VIP
        String vipRewardsJson = RoleplayWebsocketsRoomsInfo.getConfig()
                .getValue("level_" + level_id + "_vip.premios_json_array");
        parseRewards(vipRewardsJson, this.rewardsVip);
    }

    /**
     * Parsea la cadena JSON de recompensas y las carga en el mapa de recompensas
     * proporcionado.
     * 
     * @param rewardsJson La cadena JSON que contiene el array de recompensas.
     * @param rewardMap   El mapa de destino (normal o VIP) para las recompensas.
     */
    private void parseRewards(String rewardsJson, THashMap<Integer, Reward> rewardMap) {
        // En un entorno de producción, añadiría try-catch para manejar
        // JsonSyntaxException
        // si la cadena JSON es inválida, en lugar de permitir que falle
        // silenciosamente.
        JsonArray gsonArr = parser.parse(rewardsJson).getAsJsonArray();
        int increment = 0;

        for (JsonElement obj : gsonArr) {
            increment++;

            // Verificación de nulidad y tipo puede ser útil, pero se omite por simplicidad
            JsonObject gsonObj = obj.getAsJsonObject();

            // Extracción de datos (se podría factorizar en otro método si hubiera más
            // campos)
            String nombre = gsonObj.get("nombre").getAsString();
            int item_exist = gsonObj.get("item_exist").getAsInt();
            int cantidad = gsonObj.get("cantidad").getAsInt();
            String title = gsonObj.get("title").getAsString();
            String descripcion = gsonObj.get("descripcion").getAsString();

            // Insertar
            rewardMap.put(increment, new Reward(nombre, item_exist, cantidad, title, descripcion));
        }
    }

    public int getLevel() {
        return this.level;
    }

    public int getXP() {
        return this.XP;
    }

    // 3. Recomendación: Devolver una vista inmutable para proteger el estado del
    // objeto.
    // Aunque THashMap no tiene un equivalente directo a
    // Collections.unmodifiableMap,
    // es una buena práctica considerar cómo proteger el estado.
    // Por ahora, solo cambiaré la sintaxis para ser más clara:

    public THashMap<Integer, Reward> getRewards() {
        return this.rewards;
    }

    public THashMap<Integer, Reward> getRewardsVip() {
        return this.rewardsVip;
    }

}