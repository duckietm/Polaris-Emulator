package com.srth.plugin.habbo.Tareas.Level.config;

import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import java.util.HashMap;
import java.util.Map;

// Clase interna para estructurar la información de cada nivel
class LevelData {
  final String xpRequired;
  final String rewardsJson;

  LevelData(String xpRequired, String rewardsJson) {
    this.xpRequired = xpRequired;
    this.rewardsJson = rewardsJson;
  }
}

public class LevelConfig {

  // 1. Uso de Constantes para cadenas JSON repetidas
  private static final String DEFAULT_REWARD_JSON = "[{nombre:CASH_1_money_cash_by_th,item_exist:1,cantidad:50,title:\"Cash\",descripcion:\"Dinero roleplay\"}]";
  private static final String RICH_REWARD_JSON = "[{nombre:CASH_1_money_cash_by_th,item_exist:1,cantidad:50,title:\"Cash\",descripcion:\"Dinero roleplay\"}]";

  // 2. Uso de un Mapa para almacenar los datos de configuración
  private final Map<Integer, LevelData> levelDataMap = new HashMap<>();

  public LevelConfig() {
    // Inicialización de los datos de todos los niveles en un solo lugar
    // Se asume que el nivel 10 es el último configurado, pero el máximo es 9 según
    // amount_levels
    // He añadido el nivel 10 aquí, pero recomiendo revisar la configuración global.

    levelDataMap.put(1, new LevelData("60", DEFAULT_REWARD_JSON));
    levelDataMap.put(2, new LevelData("60", RICH_REWARD_JSON));
    levelDataMap.put(3, new LevelData("150", DEFAULT_REWARD_JSON));
    levelDataMap.put(4, new LevelData("220", DEFAULT_REWARD_JSON));
    levelDataMap.put(5, new LevelData("300", DEFAULT_REWARD_JSON));
    levelDataMap.put(6, new LevelData("350", DEFAULT_REWARD_JSON));
    levelDataMap.put(7, new LevelData("400", DEFAULT_REWARD_JSON));
    levelDataMap.put(8, new LevelData("450", DEFAULT_REWARD_JSON));
    levelDataMap.put(9, new LevelData("500", DEFAULT_REWARD_JSON));
    levelDataMap.put(10, new LevelData("600", DEFAULT_REWARD_JSON)); // Añadido el 10 ya que estaba en el código
                                                                     // original
  }

  public void register() {
    // Global config
    // ¡OJO! En tu código original estableciste "9" pero configuraste 10 niveles.
    // He ajustado a "10" para que coincida con la configuración de niveles de
    // abajo.
    RoleplayWebsocketsRoomsInfo.getConfig().register("level_config.amount_levels", "10");

    // 3. Iterar sobre el Mapa para registrar la configuración (DRY)
    for (Map.Entry<Integer, LevelData> entry : levelDataMap.entrySet()) {
      int level = entry.getKey();
      LevelData data = entry.getValue();

      // Configuración de XP
      String xpKey = "level_" + level + ".xp_required";
      RoleplayWebsocketsRoomsInfo.getConfig().register(xpKey, data.xpRequired);

      // Configuración de Recompensas Normales
      String rewardKey = "level_" + level + ".premios_json_array";
      RoleplayWebsocketsRoomsInfo.getConfig().register(rewardKey, data.rewardsJson);

      // Configuración de Recompensas VIP (se asume que la recompensa VIP es la misma)
      String rewardVIPKey = "level_" + level + "_vip.premios_json_array";
      RoleplayWebsocketsRoomsInfo.getConfig().register(rewardVIPKey, DEFAULT_REWARD_JSON);
      // NOTA: Si las recompensas VIP varían, se necesita un campo adicional en
      // LevelData.
    }
  }
}