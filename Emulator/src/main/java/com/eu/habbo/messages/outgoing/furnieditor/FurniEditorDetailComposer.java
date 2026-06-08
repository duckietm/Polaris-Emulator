package com.eu.habbo.messages.outgoing.furnieditor;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;
import java.util.Map;

public class FurniEditorDetailComposer extends MessageComposer {
    private final Map<String, Object> item;
    private final int usageCount;
    private final List<Map<String, Object>> catalogItems;
    private final String furniDataJson;
    private final String furniDataDiagnosticJson;

    public FurniEditorDetailComposer(Map<String, Object> item, int usageCount, List<Map<String, Object>> catalogItems, String furniDataJson) {
        this(item, usageCount, catalogItems, furniDataJson, "{}");
    }

    public FurniEditorDetailComposer(Map<String, Object> item, int usageCount, List<Map<String, Object>> catalogItems, String furniDataJson, String furniDataDiagnosticJson) {
        this.item = item;
        this.usageCount = usageCount;
        this.catalogItems = catalogItems;
        this.furniDataJson = furniDataJson;
        this.furniDataDiagnosticJson = furniDataDiagnosticJson;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniEditorDetailComposer);

        // 14 base fields
        this.response.appendInt((int) item.get("id"));
        this.response.appendInt((int) item.get("sprite_id"));
        this.response.appendString((String) item.getOrDefault("item_name", ""));
        this.response.appendString((String) item.getOrDefault("public_name", ""));
        this.response.appendString((String) item.getOrDefault("type", "s"));
        this.response.appendInt((int) item.getOrDefault("width", 1));
        this.response.appendInt((int) item.getOrDefault("length", 1));
        this.response.appendDouble((double) item.getOrDefault("stack_height", 0.0));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_stack", "1"))));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_walk", "0"))));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_sit", "0"))));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_lay", "0"))));
        this.response.appendString((String) item.getOrDefault("interaction_type", ""));
        this.response.appendInt((int) item.getOrDefault("interaction_modes_count", 0));

        // 13 extended fields
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_gift", "1"))));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_trade", "1"))));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_recycle", "1"))));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_marketplace_sell", "1"))));
        this.response.appendBoolean("1".equals(String.valueOf(item.getOrDefault("allow_inventory_stack", "1"))));
        this.response.appendString((String) item.getOrDefault("vending_ids", ""));
        this.response.appendString((String) item.getOrDefault("customparams", ""));
        this.response.appendInt((int) item.getOrDefault("effect_id_male", 0));
        this.response.appendInt((int) item.getOrDefault("effect_id_female", 0));
        this.response.appendString((String) item.getOrDefault("clothing_on_walk", ""));
        this.response.appendString((String) item.getOrDefault("multiheight", ""));
        this.response.appendString((String) item.getOrDefault("description", ""));

        // usage count
        this.response.appendInt(this.usageCount);

        // catalog references
        this.response.appendInt(this.catalogItems.size());
        for (Map<String, Object> ci : this.catalogItems) {
            this.response.appendInt((int) ci.get("id"));
            this.response.appendString((String) ci.getOrDefault("catalog_name", ""));
            this.response.appendInt((int) ci.getOrDefault("cost_credits", 0));
            this.response.appendInt((int) ci.getOrDefault("cost_points", 0));
            this.response.appendInt((int) ci.getOrDefault("points_type", 0));
            this.response.appendInt((int) ci.getOrDefault("page_id", -1));
            this.response.appendString((String) ci.getOrDefault("page_caption", ""));
        }

        // furnidata JSON string
        this.response.appendString(this.furniDataJson != null ? this.furniDataJson : "{}");
        this.response.appendString(this.furniDataDiagnosticJson != null ? this.furniDataDiagnosticJson : "{}");

        return this.response;
    }
}
