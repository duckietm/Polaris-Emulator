package com.eu.habbo.habbohotel.wired.arrays;

public final class WiredArrayAddress {
    public static final int CONSTANT = 0;
    public static final int VARIABLE = 1;

    public int mode = CONSTANT;
    public long value;
    public int variableType = WiredArrayVariableType.ROOM.code();
    public int variableItemId;
    public int variableSource;
    public String capturePath = "";
    public int fieldId = WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID;
}
