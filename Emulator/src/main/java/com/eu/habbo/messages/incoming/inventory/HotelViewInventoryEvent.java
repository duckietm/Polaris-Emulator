package com.eu.habbo.messages.incoming.inventory;

/**
 * Handles the hotel-view inventory request with an independent rate-limit key.
 *
 * <p>The packet manager tracks rate limits by handler class. Keeping this packet
 * on a distinct class prevents a recent hotel-view request from suppressing the
 * separate in-room inventory request.</p>
 */
public class HotelViewInventoryEvent extends RequestInventoryItemsEvent {
}
