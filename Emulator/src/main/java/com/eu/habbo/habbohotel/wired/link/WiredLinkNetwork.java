package com.eu.habbo.habbohotel.wired.link;

/**
 * A WIRED Link network: a name its owner gives a group of Links. Only Links the owner owns join it,
 * one per room.
 */
public record WiredLinkNetwork(int id, int ownerId, String name) {
    /** The id as the official packets carry it ({@code "0"} is no network). */
    public String wireId() {
        return Integer.toString(this.id);
    }
}
