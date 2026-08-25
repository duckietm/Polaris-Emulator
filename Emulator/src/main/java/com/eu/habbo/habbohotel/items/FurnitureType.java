package com.eu.habbo.habbohotel.items;

public enum FurnitureType {
    FLOOR("S"),
    WALL("I"),
    EFFECT("E"),
    BADGE("B"),
    ROBOT("R"),
    HABBO_CLUB("H"),
    PET("P");

    public final String code;

    FurnitureType(String code) {
        this.code = code;
    }

    public static FurnitureType fromString(String code) {
        switch (code.toUpperCase()) {
            case "S":
                return FLOOR;
            case "I":
                return WALL;
            case "E":
                return EFFECT;
            case "B":
                return BADGE;
            case "R":
                return ROBOT;
            case "H":
                return HABBO_CLUB;
            case "P":
                return PET;
            default:
                return FLOOR;
        }

    }
}