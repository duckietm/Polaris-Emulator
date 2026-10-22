package com.eu.habbo.habbohotel.rooms;

/**
 * The value a user or furni holds for one variable, with when it was given and last changed. A value-less
 * variable holds null.
 */
final class RoomVariableAssignment {
    private Integer value;
    private final int createdAt;
    private int updatedAt;
    private int previousUpdatedAt;

    RoomVariableAssignment(Integer value, int createdAt, int updatedAt) {
        this.value = value;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.previousUpdatedAt = updatedAt;
    }

    Integer getValue() {
        return this.value;
    }

    void setValue(Integer value, int updatedAt) {
        this.value = value;
        this.previousUpdatedAt = this.updatedAt;
        this.updatedAt = updatedAt;
    }

    int getCreatedAt() {
        return this.createdAt;
    }

    int getUpdatedAt() {
        return this.updatedAt;
    }

    /** The update time before the last value change, so a last-update-time part can compare. */
    int getPreviousUpdatedAt() {
        return this.previousUpdatedAt;
    }
}
