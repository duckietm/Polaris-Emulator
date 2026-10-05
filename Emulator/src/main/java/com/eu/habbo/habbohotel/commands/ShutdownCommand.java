package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.threading.runnables.ShutdownEmulator;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShutdownCommand extends Command {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownCommand.class);

    static final int MAX_MINUTES = 1440;
    static final String NOW_KEYWORD = "now";
    static final String CANCEL_KEYWORD = "cancel";

    static final String USAGE_KEY = "commands.error.cmd_shutdown.usage";
    static final String USAGE_FALLBACK =
            "Usage: :shutdown <minutes 1-1440> [reason], :shutdown now [reason] or :shutdown cancel";
    static final String NONE_PENDING_KEY = "commands.error.cmd_shutdown.none_pending";
    static final String NONE_PENDING_FALLBACK = "There is no pending shutdown to cancel.";
    static final String CANCELLED_KEY = "commands.succes.cmd_shutdown.cancelled";
    static final String CANCELLED_FALLBACK = "The pending shutdown has been cancelled.";

    enum Action {
        SCHEDULE,
        NOW,
        CANCEL,
        USAGE
    }

    record Request(Action action, int minutes, String reason) {}

    // The countdown this command scheduled last, so ":shutdown cancel" can drop the task.
    private ScheduledFuture<?> pending;

    public ShutdownCommand() {
        super(
                "cmd_shutdown",
                Emulator.getTexts().getValue("commands.keys.cmd_shutdown").split(";"));
    }

    /**
     * Parses ":shutdown <minutes> [reason]", ":shutdown now [reason]" and ":shutdown cancel". In game an
     * explicit delay or "now" is required; only the console's bare "stop" still stops at once.
     */
    static Request parse(String[] params, boolean console) {
        if (params == null || params.length < 2) {
            return new Request(console ? Action.NOW : Action.USAGE, 0, null);
        }

        String first = params[1].trim();

        if (first.equalsIgnoreCase(CANCEL_KEYWORD)) {
            return new Request(Action.CANCEL, 0, null);
        }

        if (first.equalsIgnoreCase(NOW_KEYWORD)) {
            return new Request(Action.NOW, 0, joinReason(params, 2));
        }

        if (first.matches("-?\\d+")) {
            int minutes = first.matches("\\d{1,5}") ? Integer.parseInt(first) : -1;

            if (minutes < 1 || minutes > MAX_MINUTES) {
                return new Request(Action.USAGE, 0, joinReason(params, 2));
            }

            return new Request(Action.SCHEDULE, minutes, joinReason(params, 2));
        }

        return new Request(console ? Action.NOW : Action.USAGE, 0, joinReason(params, 1));
    }

    static String joinReason(String[] params, int from) {
        StringBuilder reason = new StringBuilder();

        for (int i = from; i < params.length; i++) {
            if (params[i].isBlank()) {
                continue;
            }

            if (reason.length() > 0) {
                reason.append(" ");
            }

            reason.append(params[i].trim());
        }

        return reason.length() == 0 ? null : reason.toString();
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        Habbo staff = gameClient == null ? null : gameClient.getHabbo();
        Request request = parse(params, staff == null);
        TextsManager texts = Emulator.getTexts();

        switch (request.action()) {
            case USAGE -> reply(staff, texts.getValue(USAGE_KEY, USAGE_FALLBACK));
            case CANCEL ->
                reply(
                        staff,
                        this.cancel()
                                ? texts.getValue(CANCELLED_KEY, CANCELLED_FALLBACK)
                                : texts.getValue(NONE_PENDING_KEY, NONE_PENDING_FALLBACK));
            default -> this.schedule(request, staff, texts);
        }

        return true;
    }

    private synchronized void schedule(Request request, Habbo staff, TextsManager texts) {
        int minutes = request.action() == Action.NOW ? 0 : request.minutes();

        ServerMessage message;
        if (request.reason() != null) {
            message = new GenericAlertComposer("<b>" + texts.getValue("generic.warning") + "</b> \r\n"
                            + texts.getValue("generic.shutdown").replace("%minutes%", minutes + "") + "\r\n"
                            + texts.getValue("generic.reason.specified") + ": <b>" + request.reason() + "</b>\r"
                            + "\r"
                            + "- "
                            + (staff == null ? "Console" : staff.getHabboInfo().getUsername()))
                    .compose();
        } else {
            message = new HotelWillCloseInMinutesComposer(minutes).compose();
        }

        if (this.pending != null) {
            this.pending.cancel(false);
        }

        int deadline = Emulator.getIntUnixTimestamp() + (60 * minutes);

        RoomTrade.TRADING_ENABLED = false;
        // A replaced countdown announces its new time; the old task sees a newer deadline and stays idle.
        ShutdownEmulator.instantiated = false;
        ShutdownEmulator.timestamp = deadline;
        this.pending = Emulator.getThreading().run(new ShutdownEmulator(message, deadline), (long) minutes * 60 * 1000);
    }

    private synchronized boolean cancel() {
        boolean wasPending = ShutdownEmulator.timestamp > 0 || (this.pending != null && !this.pending.isDone());

        if (this.pending != null) {
            this.pending.cancel(false);
            this.pending = null;
        }

        if (!wasPending) {
            return false;
        }

        // Also stops countdowns started from another instance (the console), which check the timestamp.
        ShutdownEmulator.clearPending();

        ConfigurationManager configuration = WiredPlatform.configuration();
        RoomTrade.TRADING_ENABLED = configuration == null || configuration.getBoolean("hotel.trading.enabled");
        return true;
    }

    private static void reply(Habbo staff, String text) {
        if (staff == null) {
            LOGGER.info(text);
        } else if (staff.getHabboInfo().getCurrentRoom() != null) {
            staff.whisper(text, RoomChatMessageBubbles.ALERT);
        } else {
            staff.alert(text);
        }
    }
}
