package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;

public final class FurnidataAuditLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataAuditLog.class);
    private FurnidataAuditLog() {}

    public static void record(int userId, String classname, String action,
                              String oldName, String newName, String oldDesc, String newDesc) {
        try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement st = c.prepareStatement("""
                 INSERT INTO furnidata_edit_log (user_id, classname, action, old_name, new_name, old_description, new_description, timestamp) \
                 VALUES (?,?,?,?,?,?,?,?)""")) {
            st.setInt(1, userId);
            st.setString(2, classname);
            st.setString(3, action);
            st.setString(4, oldName == null ? "" : oldName);
            st.setString(5, newName == null ? "" : newName);
            st.setString(6, oldDesc == null ? "" : oldDesc);
            st.setString(7, newDesc == null ? "" : newDesc);
            st.setInt(8, Emulator.getIntUnixTimestamp());
            st.executeUpdate();
        } catch (Exception e) {
            LOGGER.error("Failed to write furnidata_edit_log", e);
        }
    }
}
