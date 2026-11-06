package polaris.messages.incoming.furnieditor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.editor.FurniEditorRepository;
import polaris.hotel.items.editor.FurniEditorRepository.AuditEntry;

public final class FurnidataAuditLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(FurnidataAuditLog.class);

    private FurnidataAuditLog() {}

    public static void record(
            int userId,
            String classname,
            String action,
            String oldName,
            String newName,
            String oldDesc,
            String newDesc) {
        try {
            new FurniEditorRepository(Emulator.getDatabase().getDataSource())
                    .recordAudit(new AuditEntry(
                            userId,
                            classname,
                            action,
                            oldName,
                            newName,
                            oldDesc,
                            newDesc,
                            Emulator.getIntUnixTimestamp()));
        } catch (Exception e) {
            LOGGER.error("Failed to write furnidata_edit_log", e);
        }
    }
}
