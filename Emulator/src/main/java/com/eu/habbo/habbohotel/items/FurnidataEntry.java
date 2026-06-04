package com.eu.habbo.habbohotel.items;

/**
 * One parsed furnidata entry. {@code classname} is the raw furnidata classname
 * (may carry a {@code *N} colour-variant suffix); the provider keys on the base.
 */
public record FurnidataEntry(int id, String classname, FurnitureType type, String name, String description) {
}
