package com.srth.plugin.bots.shop;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.eu.habbo.Emulator;
import com.srth.plugin.Crafteos.RewardItem;

import gnu.trove.map.hash.THashMap;

public class TypeShop {

    private int id;
    private String type;
    private String title;
    private String desc;

    private THashMap<Integer, ItemShop> items = new THashMap<Integer, ItemShop>();

    public TypeShop(ResultSet set) throws SQLException {

        this.id = set.getInt("id");
        this.type = set.getString("type");
        this.title = set.getString("title");
        this.desc = set.getString("desc");

    }

    public void loadItems() throws Exception {

        this.items.clear();

        try (Connection conn = Emulator.getDatabase().getDataSource().getConnection()) {
            try (Connection connTarea = Emulator.getDatabase().getDataSource().getConnection()) {
                try (PreparedStatement preparedStatementTarea = conn.prepareStatement(
                        "SELECT * FROM rp_shop_bots_items WHERE `shop_id`= ? ORDER BY `id` ASC")) {
                    preparedStatementTarea.setInt(1, this.getId());
                    ResultSet setItems = preparedStatementTarea.executeQuery();
                    while (setItems.next()) {

                        ItemShop item = new ItemShop(setItems);
                        item.setTypeShop(this);

                        this.items.put(setItems.getInt("id"), item);

                    }
                } catch (SQLException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    public void addItem(int id, ItemShop item) {
        items.put(id, item);
    }

    public int getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDesc() {
        return desc;
    }

    public THashMap<Integer, ItemShop> getItems() {
        return items;
    }

}
