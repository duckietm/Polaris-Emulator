package com.srth.plugin.Crafteos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;

import gnu.trove.map.hash.THashMap;

public class CraftItem {

    private Integer id;
    private String name;
    private String desc;
    private THashMap<Integer, RewardItem> rewards = new THashMap<Integer, RewardItem>();
    private THashMap<Integer, IngredienteItem> ingredientes = new THashMap<Integer, IngredienteItem>();

    public CraftItem(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.name = set.getString("name");
        this.desc = set.getString("desc");
    }

    public void loadItemsRewards() throws Exception {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (Connection connTarea = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatementTarea = conn.prepareStatement(
                        "SELECT * FROM rp_craft_rewards WHERE `craft_id`= ? ORDER BY `id` ASC")) {
                    preparedStatementTarea.setInt(1, this.getId());
                    ResultSet setCraftReward = preparedStatementTarea.executeQuery();
                    while (setCraftReward.next()) {

                        RewardItem rewardItem = new RewardItem(setCraftReward);
                        this.rewards.put(setCraftReward.getInt("id"), rewardItem);

                    }
                } catch (SQLException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public void loadIngredientes() throws SQLException {

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (Connection connTarea = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatementTarea = conn.prepareStatement(
                        "SELECT * FROM rp_craft_ingredientes WHERE `craft_id`= ? ORDER BY `id` ASC")) {
                    preparedStatementTarea.setInt(1, this.getId());
                    ResultSet setCraftIngredientes = preparedStatementTarea.executeQuery();
                    while (setCraftIngredientes.next()) {

                        IngredienteItem ingredientesItem = new IngredienteItem(setCraftIngredientes);
                        this.ingredientes.put(setCraftIngredientes.getInt("id"), ingredientesItem);

                    }
                } catch (SQLException exception) {
                    exception.printStackTrace();
                }
            }
        }

    }

    public THashMap<Integer, IngredienteItem> getIngredientes() {
        return this.ingredientes;
    }

    public THashMap<Integer, RewardItem> getRewards() {
        return this.rewards;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getDesc() {
        return this.desc;
    }

}
