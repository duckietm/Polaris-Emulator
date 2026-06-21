package com.eu.habbo.habbohotel.catalog.marketplace;

public enum MarketPlaceState {

    OPEN(1),


    SOLD(2),


    CLOSED(3);

    private final int state;

    MarketPlaceState(int state) {
        this.state = state;
    }

    public static MarketPlaceState getType(int type) {
        return switch (type) {
            case 1 -> OPEN;
            case 2 -> SOLD;
            default -> CLOSED;
        };
    }

    public int getState() {
        return this.state;
    }

}
