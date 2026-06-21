package com.eu.habbo.habbohotel.modtool;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class CfhCategory {
    private final String name;
    private final Int2ObjectMap<CfhTopic> topics;

    public CfhCategory(int id, String name) {
        this.name = name;
        this.topics = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    }

    public void addTopic(CfhTopic topic) {
        this.topics.put(topic.id, topic);
    }

    public Int2ObjectMap<CfhTopic> getTopics() {
        return this.topics;
    }

    public String getName() {
        return this.name;
    }
}
