package com.eu.habbo.habbohotel.wired.arrays;

public final class WiredArrayReference {
    public static final int CONSTANT = 0;
    public static final int VARIABLE = 1;

    public int mode = CONSTANT;
    public String value = "0";
    public int variableType = WiredArrayVariableType.ROOM.code();
    public int variableItemId;
    public int variableSource;
    public String capturePath = "";
    public WiredArrayAddress address = new WiredArrayAddress();
}
