package com.eu.habbo.habbohotel.rooms;

/**
 * One furni or avatar in a wired visualization packet. For furni the id is the item id, negated for
 * wall items as the official client expects; for avatars it is the room unit index.
 */
public record WiredVisualizationEntry<V>(int id, V value) {}
