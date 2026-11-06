package polaris.messages.incoming.hotelview;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.hotelview.HotelViewSecondsUntilComposer;
import polaris.util.HotelDateTimeUtil;

public class HotelViewRequestSecondsUntilEvent extends MessageHandler {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void handle() throws Exception {
        final String date = this.packet.readString();

        try {
            LocalDateTime dt = LocalDateTime.parse(date, formatter);
            int secondsUntil = Math.max(0, (int) HotelDateTimeUtil.toEpochSecond(dt) - Emulator.getIntUnixTimestamp());
            this.client.sendResponse(new HotelViewSecondsUntilComposer(date, secondsUntil));
        } catch (DateTimeParseException ignored) {
        }
    }
}
