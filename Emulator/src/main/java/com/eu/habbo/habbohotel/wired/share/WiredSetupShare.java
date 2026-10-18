package com.eu.habbo.habbohotel.wired.share;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.rooms.Room;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Wired setup share and import: a room's wired boxes as a versioned share code, and that code applied
 * to the importer's own boxes in another room.
 *
 * <p>The official client has no wired sharing (its clipboard copies one box's settings within a
 * session); this is ours. Both directions need the room's "modify wired" right, and an import only
 * ever configures boxes the importer placed or places from their own inventory.
 */
public final class WiredSetupShare {
    public static final int STATUS_OK = 0;
    public static final int STATUS_NO_RIGHTS = 1;
    public static final int STATUS_EMPTY = 2;
    public static final int STATUS_TOO_LARGE = 3;
    public static final int STATUS_TOO_MANY_BOXES = 4;
    public static final int STATUS_INVALID_CODE = 5;
    public static final int STATUS_UNSUPPORTED_VERSION = 6;
    public static final int STATUS_NO_ANCHOR = 7;

    /** The setup lands on the tiles it was exported from. */
    public static final int ANCHOR_ORIGINAL = 0;
    /** The setup's lowest corner lands on the importer's tile. */
    public static final int ANCHOR_AVATAR = 1;

    /** The report travels as one packet string (unsigned short length). */
    static final int MAX_REPORT_BYTES = 60_000;

    private static final Gson REPORT_GSON =
            new GsonBuilder().disableHtmlEscaping().create();

    private WiredSetupShare() {}

    /** An export: its status, how many boxes it holds and the share code (empty unless the status is OK). */
    public record Export(int status, int boxCount, String code) {}

    /** The share code of every wired box in {@code room}. The caller has checked the modify right. */
    public static Export export(Room room) {
        if (room == null) return new Export(STATUS_NO_RIGHTS, 0, "");
        List<InteractionWired> boxes = WiredSetupExporter.exportableBoxes(room);
        if (boxes.isEmpty()) return new Export(STATUS_EMPTY, 0, "");
        if (boxes.size() > WiredSetupDocument.MAX_BOXES) return new Export(STATUS_TOO_MANY_BOXES, boxes.size(), "");

        WiredSetupDocument document = new WiredSetupExporter(room).build(boxes);
        if (document == null) return new Export(STATUS_TOO_MANY_BOXES, boxes.size(), "");
        String code = document.encode();
        if (code.length() > WiredSetupDocument.MAX_CODE_LENGTH) {
            return new Export(STATUS_TOO_LARGE, boxes.size(), "");
        }
        return new Export(STATUS_OK, boxes.size(), code);
    }

    /**
     * Previews ({@code confirm} false) or applies a share code in {@code room} for {@code client}, and
     * returns the report as JSON. The caller has checked the modify right.
     */
    public static String importSetup(
            Room room, GameClient client, String code, int anchorMode, boolean placeFromInventory, boolean confirm) {
        WiredSetupImporter.Report report;
        try {
            WiredSetupDocument document = WiredSetupDocument.decode(code);
            report = new WiredSetupImporter(room, client, document).run(anchorMode, placeFromInventory, confirm);
        } catch (WiredSetupDocument.InvalidCodeException invalid) {
            report = new WiredSetupImporter.Report();
            report.confirmed = confirm;
            report.status = invalid.status;
        }
        return reportJson(report);
    }

    /** A report always fits one packet string: reasons go first, then the tail of the entry list. */
    static String reportJson(WiredSetupImporter.Report report) {
        String json = REPORT_GSON.toJson(report);
        if (fits(json)) return json;

        for (WiredSetupImporter.Entry entry : report.entries) entry.reason = "";
        json = REPORT_GSON.toJson(report);
        while (!fits(json) && !report.entries.isEmpty()) {
            int keep = report.entries.size() / 2;
            report.entries = new ArrayList<>(report.entries.subList(0, keep));
            json = REPORT_GSON.toJson(report);
        }
        return json;
    }

    private static boolean fits(String json) {
        return json.getBytes(StandardCharsets.UTF_8).length <= MAX_REPORT_BYTES;
    }

    /** The report of a request that was refused before reading the code. */
    public static String refusedImport(int status, boolean confirm) {
        WiredSetupImporter.Report report = new WiredSetupImporter.Report();
        report.confirmed = confirm;
        report.status = status;
        return REPORT_GSON.toJson(report);
    }
}
