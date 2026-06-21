package com.eu.habbo.database;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Database {

    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

    private HikariDataSource dataSource;
    private DatabasePool databasePool;

    public Database(ConfigurationManager config) {
        long millis = System.currentTimeMillis();

        boolean SQLException = false;

        try {
            this.databasePool = new DatabasePool();
            if (!this.databasePool.getStoragePooling(config)) {
                LOGGER.info("Failed to connect to the database. Please check config.ini and make sure the MySQL process is running. Shutting down...");
                SQLException = true;
                return;
            }
            this.dataSource = this.databasePool.getDatabase();
        } catch (Exception e) {
            SQLException = true;
            LOGGER.error("Failed to connect to your database.", e);
        } finally {
            if (SQLException) {
                Emulator.prepareShutdown();
            }
        }

        LOGGER.info("Database -> Connected! ({} MS)", System.currentTimeMillis() - millis);
    }

    public void dispose() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }

    public HikariDataSource getDataSource() {
        return this.dataSource;
    }

    public DatabasePool getDatabasePool() {
        return this.databasePool;
    }

    public static PreparedStatement preparedStatementWithParams(Connection connection,
                                                                String query,
                                                                Map<String, Object> queryParams) throws SQLException {
        StringBuilder positional = new StringBuilder(query.length());
        List<Object> bindValues = new ArrayList<>();

        int i = 0;
        int n = query.length();

        while (i < n) {
            char c = query.charAt(i);

            if (c == '\'') {
                positional.append(c);
                i++;
                while (i < n) {
                    char inner = query.charAt(i);
                    positional.append(inner);
                    i++;
                    if (inner == '\'') {
                        if (i < n && query.charAt(i) == '\'') {
                            positional.append('\'');
                            i++;
                        } else {
                            break;
                        }
                    }
                }
                continue;
            }

            if (c == '@' && i + 1 < n && isNameStart(query.charAt(i + 1))) {
                int start = i;
                int j = i + 1;
                while (j < n && isNamePart(query.charAt(j))) {
                    j++;
                }
                String name = query.substring(start, j);
                if (!queryParams.containsKey(name)) {
                    throw new IllegalArgumentException(
                            "SQL template references parameter '" + name + "' but no value was provided");
                }
                positional.append('?');
                bindValues.add(queryParams.get(name));
                i = j;
                continue;
            }

            positional.append(c);
            i++;
        }

        PreparedStatement statement = connection.prepareStatement(positional.toString());
        for (int k = 0; k < bindValues.size(); k++) {
            statement.setObject(k + 1, bindValues.get(k));
        }
        return statement;
    }

    private static boolean isNameStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isNamePart(char c) {
        return isNameStart(c) || (c >= '0' && c <= '9');
    }
}
