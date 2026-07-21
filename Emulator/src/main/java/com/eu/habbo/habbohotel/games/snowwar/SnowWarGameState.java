package com.eu.habbo.habbohotel.games.snowwar;

/**
 * SnowWar match lifecycle (PROTOCOL.md "Game flow").
 */
public enum SnowWarGameState {
    WAITING_FOR_PLAYERS,
    PREPARING,
    RUNNING,
    ENDING,
    ENDED
}
