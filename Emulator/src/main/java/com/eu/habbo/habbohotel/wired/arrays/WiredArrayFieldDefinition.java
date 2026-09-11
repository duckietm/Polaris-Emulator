package com.eu.habbo.habbohotel.wired.arrays;

import java.util.Objects;

public final class WiredArrayFieldDefinition {
    private final int id;
    private final String name;
    private final int order;

    @SuppressWarnings("unused")
    private WiredArrayFieldDefinition() {
        this(0, "", 0);
    }

    public WiredArrayFieldDefinition(int id, String name, int order) {
        this.id = id;
        this.name = name;
        this.order = order;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getOrder() {
        return this.order;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WiredArrayFieldDefinition field)) return false;
        return this.id == field.id && this.order == field.order && Objects.equals(this.name, field.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name, this.order);
    }
}
