package com.eu.habbo.habbohotel.games.snowwar;

import java.sql.Connection;
import java.sql.SQLException;

/** Opens a SnowWar persistence connection without coupling repositories to the emulator facade. */
@FunctionalInterface
interface SnowWarConnectionProvider {

    Connection openConnection() throws SQLException;
}
