package com.eu.habbo.habbohotel.soundboard;

public record SoundboardCatalogCommand(
        int id, String name, String classname, String url, int minRank, boolean enabled) {}
