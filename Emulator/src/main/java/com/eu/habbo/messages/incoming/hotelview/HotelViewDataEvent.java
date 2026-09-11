package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewDataComposer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AIR 13 GetCurrentTimingCode (2912) -&gt; CurrentTimingCode (1745).
 *
 * <p>The {@code widgetcontainer} hotel-view slot sends its own scheduling string
 * ({@code landing.view.dynamic.slot.&lt;n&gt;.conf}), a list of
 * {@code <yyyy-MM-dd HH:mm>,<code>} entries separated by {@code ;}. The server
 * answers with the very same string plus the code of the entry that is current;
 * {@code WidgetContainerWidget.onTimingCode} only accepts the answer when the
 * scheduling string comes back unchanged, then renders
 * {@code landing.view.&lt;code&gt;.widget}. An empty code hides the slot.
 *
 * <p>The previous body echoed the first entry of the list instead of the entry
 * that is actually due, so a schedule with more than one entry never switched.
 */
public class HotelViewDataEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotelViewDataEvent.class);
    private static final DateTimeFormatter SCHEDULE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_SCHEDULE_LENGTH = 1024;

    @Override
    public void handle() {
        String schedulingStr = this.packet.readString();

        if (schedulingStr == null) schedulingStr = "";

        this.client.sendResponse(new HotelViewDataComposer(schedulingStr, currentCode(schedulingStr)));
    }

    /** The code of the last entry whose timestamp already passed. */
    public static String currentCode(String schedulingStr) {
        if (schedulingStr == null || schedulingStr.isBlank() || schedulingStr.length() > MAX_SCHEDULE_LENGTH) {
            return "";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime best = null;
        String code = "";

        for (String entry : schedulingStr.split(";")) {
            int separator = entry.lastIndexOf(',');

            if (separator < 0) continue;

            String moment = entry.substring(0, separator).trim();
            String candidate = entry.substring(separator + 1).trim();

            if (candidate.isEmpty()) continue;

            try {
                LocalDateTime at = LocalDateTime.parse(moment, SCHEDULE_FORMAT);

                if (at.isAfter(now)) continue;

                if (best == null || at.isAfter(best)) {
                    best = at;
                    code = candidate;
                }
            } catch (DateTimeParseException ignored) {
                LOGGER.debug("Ignoring malformed hotel view timing entry '{}'", entry);
            }
        }

        return code;
    }
}
