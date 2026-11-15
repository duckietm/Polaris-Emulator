package com.srth.plugin.Crafteos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;

import gnu.trove.map.hash.THashMap;

public class CraftItemType {

    public int id;
    public String type;

    private THashMap<Integer, CraftItem> itemsCraft = new THashMap<Integer, CraftItem>();

    public CraftItemType(ResultSet set) throws SQLException {
        this.id = set.getInt("id");
        this.type = set.getString("type");
    }

    public void loadCrafts() throws Exception {
        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (Connection connTarea = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatementTarea = conn.prepareStatement(
                        "SELECT * FROM rp_craft WHERE `type`= ? ORDER BY `id` ASC")) {
                    preparedStatementTarea.setInt(1, this.getId());
                    ResultSet setCraft = preparedStatementTarea.executeQuery();
                    while (setCraft.next()) {

                        CraftItem craftItem = new CraftItem(setCraft);
                        craftItem.loadIngredientes();
                        craftItem.loadItemsRewards();

                        this.itemsCraft.put(setCraft.getInt("id"), craftItem);

                    }
                } catch (SQLException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public int getId(){
        return this.id;
    }

    public String getType(){
        return this.type;
    }

    public THashMap<Integer, CraftItem> getCrafts(){
        return this.itemsCraft;
    }

}
