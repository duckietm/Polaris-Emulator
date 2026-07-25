package com.eu.habbo.habbohotel.wired.arrays;

public interface WiredArrayVariableDefinition {
    int getId();

    String getVariableName();

    WiredArrayVariableType getArrayVariableType();

    WiredArrayDefinition getArrayDefinition();

    boolean isArrayPermanent();

    boolean hasValue();

    default int getArrayStorageRoomId(int currentRoomId) {
        return currentRoomId;
    }

    default int getArrayStorageDefinitionItemId() {
        return this.getId();
    }

    default boolean isArrayWritable() {
        return true;
    }

    default boolean isArrayShared() {
        return false;
    }

    default boolean isArraySourceValid() {
        return true;
    }

    default boolean isArray() {
        return this.getArrayDefinition() != null;
    }
}
