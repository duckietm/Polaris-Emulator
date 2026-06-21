package com.eu.habbo.habbohotel.modtool;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class ModToolCategory {
    private final String name;
    private final Int2ObjectMap<ModToolPreset> presets;

    public ModToolCategory(String name) {
        this.name = name;
        this.presets = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    }

    public void addPreset(ModToolPreset preset) {
        this.presets.put(preset.id, preset);
    }

    public Int2ObjectMap<ModToolPreset> getPresets() {
        return this.presets;
    }

    public String getName() {
        return this.name;
    }
}
