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

    /** True only when {@link #getArrayDefinition()} is usable, so callers may dereference it. */
    default boolean isArray() {
        return this.getArrayDefinition() != null;
    }

    /** True when a stored array schema exists but this server could not parse it. */
    default boolean isArrayUnavailable() {
        return false;
    }

    /** True when the variable is declared as an array, readable or not — never a scalar. */
    default boolean isArrayDeclared() {
        return this.isArray() || this.isArrayUnavailable();
    }
}
