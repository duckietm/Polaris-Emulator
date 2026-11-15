package com.srth.plugin.habbo.Tareas.Habbo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eu.habbo.Emulator;
import com.srth.plugin.RoleplayWebsocketsRoomsInfo;
import com.srth.plugin.habbo.HabboRp;

public class TopNiveles {

    Map<Integer, TopUser> niveles = new LinkedHashMap<>();

    public void load() {
        this.niveles.clear();
        try {
            try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatement = conn.prepareStatement(
                        "SELECT * FROM rp_users_settings WHERE `key` LIKE '%level_rp.xp%' ORDER BY value DESC LIMIT 100")) {
                    ResultSet set = preparedStatement.executeQuery();
                    while (set.next()) {
                        HabboRp habboRp = RoleplayWebsocketsRoomsInfo.getHabboRpManager()
                                .getHabboRpOffline(set.getInt("user_id"));
                        if (habboRp != null) {
                            this.niveles.put(set.getInt("user_id"), new TopUser(habboRp));
                        }
                    }
                } catch (SQLException exception) {
                    exception.printStackTrace();
                }
            }
        } catch (Exception e) {
        }
    }

    public Map<Integer, TopUser> getNiveles() {
        return this.niveles;
    }

    public Map<Integer, TopUser> getTopNiveles() {
        HashMap<Integer, Integer> mapaUsuarios = new HashMap<>();
        HashMap<Integer, Integer> nivelUsuarios = new HashMap<>();

        for (TopUser topUser : this.getNiveles().values()) {
            mapaUsuarios.put(topUser.getId(), topUser.getXp());
            nivelUsuarios.put(topUser.getId(), topUser.getLevel());
        }

        Comparator<Map.Entry<Integer, Integer>> comparador = (entry1, entry2) -> {
            Integer usuario1 = entry1.getKey();
            Integer usuario2 = entry2.getKey();
            Integer nivel1 = nivelUsuarios.get(usuario1);
            Integer nivel2 = nivelUsuarios.get(usuario2);
            Integer experiencia1 = entry1.getValue();
            Integer experiencia2 = entry2.getValue();

            // Ordenar primero por nivel descendente
            int comparacionNivel = nivel2.compareTo(nivel1);
            if (comparacionNivel != 0) {
                return comparacionNivel;
            }

            // Si los niveles son iguales, ordenar por experiencia descendente
            return experiencia2.compareTo(experiencia1);
        };

        List<Map.Entry<Integer, Integer>> listaUsuarios = new ArrayList<>(mapaUsuarios.entrySet());
        Collections.sort(listaUsuarios, comparador);

        Map<Integer, TopUser> listaTopOrdenada = new LinkedHashMap<>();
        int increment = 1;
        for (Map.Entry<Integer, Integer> entry : listaUsuarios) {
            if (increment >= 4) {
                continue;
            }
            TopUser topUser = this.getNiveles().get(entry.getKey());
            listaTopOrdenada.put(entry.getKey(), topUser);
            increment++;
        }

        return listaTopOrdenada;
    }

}
